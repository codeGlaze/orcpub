(ns orcpub.dnd.e5.portrait-effects
  "Hair ombre and cast shadows, shared by every renderer: the share card
   (Java2D), the PDF bake and the builder's drawer (both canvas).

   OMBRE is the digital-painting shortcut for colouring hair: the hair takes
   a gradient from its root colour to a tip colour instead of one flat colour
   (still multiplied, so the linework stays ink), with a soft-light depth pass
   on top that darkens the roots and lifts the ends. The gradient runs
   down from each piece's CROWN LINE -- a smooth arc along its top -- so every
   strand runs root to tip wherever it starts: a fringe from the hairline to
   the brow, long hair from the scalp to the ends. An angle can be set instead, for a straight gradient in one
   direction. With the tip the same as the root this is just a little depth.

   SHADOWS are cast by the hair pieces that hang over the face -- a fringe,
   strands across the cheek -- and only those: a cut that lies flat on the
   head touches the skin and casts nothing, so :asset/casts-shadow is set per
   piece in the registry. They land only on skin that is showing, as a soft
   band just below the hair's edge, warm-tinted the way skin shadows are.

   Worked out on the real art in the mock-ups; renderers do only the pixel
   plumbing and call these."
  (:require [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.dnd.e5.portrait-colorize :as colorize]))

;; ---------------------------------------------------------------------------
;; Ombre settings
;; ---------------------------------------------------------------------------

(def ombre-defaults
  "What the hair does when nobody has touched the ombre controls: no second
   colour, a little depth."
  {:start 0.2     ; how far along the hair the tip colour begins, 0..1
   :falloff 0.6   ; how long the blend is, 0 (a hard dip-dye line) .. 1
   :depth 0.3     ; soft-light: darker roots, lighter ends, 0..1
   :angle nil     ; nil: down from the crown line; degrees: a straight line, 0 = down
   ;; the quick-colouring tricks, each 0 (off) .. 1
   :clumps 0.45   ; streaks: the tip colour starts higher on some strands, lower
                  ; on others (0.45 chosen on the real art; 1 looked stamped)
   :light 0.0     ; depth lifted toward a light at the upper left, deepened away
   :under 0.0})   ; the hair behind the head a little darker, by how light it is

(defn- clamp01 ^double [^double x] (if (< x 0.0) 0.0 (if (> x 1.0) 1.0 x)))

(defn- new-doubles [n]
  #?(:clj (double-array n) :cljs (js/Float64Array. n)))

;; typed on the JVM, where an untyped aget over a whole frame reflects
#?(:clj  (defn- ag ^double [^doubles a ^long i] (aget a i))
   :cljs (defn- ag [a i] (aget a i)))
#?(:clj  (defn- as! [^doubles a ^long i ^double v] (aset a i v))
   :cljs (defn- as! [a i v] (aset a i v)))

(defn ombre-settings
  "The portrait's ombre settings with defaults filled in and anything out of
   range pulled back in: a stored value is checked on the way out, as colours
   and credits are, so a hand-edited portrait cannot break a render."
  [portrait]
  (let [o (let [v (:ombre portrait)] (if (map? v) v {}))
        num (fn [k] (let [v (get o k)] (if (number? v) (clamp01 (double v)) (ombre-defaults k))))]
    {:tip (let [t (:tip o)] (when (and (string? t) (colorize/hex->rgb t)) t))
     :start (num :start)
     :falloff (num :falloff)
     :depth (num :depth)
     :clumps (num :clumps)
     :light (num :light)
     :under (num :under)
     :angle (let [a (:angle o)] (when (number? a) (mod (double a) 360.0)))}))

(defn hair-layer?
  "Whether a layer is hair, and so drawn through the ombre."
  [layer-key]
  (= :hair (pa/color-slots layer-key)))

;; ---------------------------------------------------------------------------
;; Where a pixel sits between root and tip
;; ---------------------------------------------------------------------------

(defn crown
  "Where the hair grows from: a little below the top of the head, centred on
   it. `alpha-at` gives the head's alpha (0..255) at a pixel index of a `w` x
   `h` image. nil when there is no head."
  [alpha-at w h]
  (let [top (loop [i 0] (cond (>= i (* w h)) nil
                              (> (alpha-at i) 60) (quot i w)
                              :else (recur (inc i))))]
    (when top
      (let [row (min (dec h) (+ top (quot h 75)))
            xs (filter #(> (alpha-at (+ % (* row w))) 60) (range w))]
        (when (seq xs)
          [(/ (+ (first xs) (last xs)) 2.0) (+ top (/ h 25.0))])))))

(defn- percentile [xs q]
  (let [v (vec (sort xs))]
    (when (seq v) (nth v (int (* q (dec (count v))))))))

(declare arc-frame)

(defn gradient-frame
  "How to place a piece's pixels between root (0) and tip (1): down from the
   piece's crown line (`arc-frame`), or along an angle from the crown.
   Measured over the piece's own pixels, ignoring the outermost 3% so a stray
   wisp does not set the length."
  [alpha-at w h crown angle]
  (let [samples (for [i (range 0 (* w h) 5) :when (> (alpha-at i) 60)] i)
        [cx cy] (or crown
                    ;; no head chosen: the piece's own top, centred
                    (let [ys (map #(quot % w) samples)]
                      [(/ w 2.0) (double (or (percentile ys 0.03) 0))]))]
    (if angle
      (let [a (* angle (/ Math/PI 180.0))
            ux (Math/sin a) uy (Math/cos a)
            proj (fn [i] (+ (* (- (mod i w) cx) ux) (* (- (quot i w) cy) uy)))
            ps (map proj samples)
            lo (or (percentile ps 0.03) 0.0) hi (or (percentile ps 0.97) 1.0)]
        {:kind :angle :cx cx :cy cy :ux ux :uy uy :lo lo :span (max 1.0 (- hi lo))
         :x0 (double (or (percentile (map #(mod % w) samples) 0.0) 0))
         :x1 (double (or (percentile (map #(mod % w) samples) 1.0) w))})
      (arc-frame alpha-at w h))))

(defn arc-frame
  "Root to tip measured down from a smooth arc along the top of the piece --
   its crown line -- and scaled the same across the whole piece.

   A single crown point failed on a turned head: strands growing right beside
   it never counted as far from the roots, so half a fringe stayed root
   colour. Measuring down each column from that column's own top fixed the
   fringe but streaked, because a piece's top edge jumps between columns. The
   arc is the upper envelope of the piece (the highest point within a wide
   window either side), smoothed, so it follows the curve of the head without
   the jumps; every strand then runs from it to its tip."
  [alpha-at w h]
  (let [tops (int-array w h)]
    (dotimes [i (* w h)]
      (when (> (alpha-at i) 60)
        (let [x (mod i w) y (quot i w)]
          (when (< y (aget tops x)) (aset tops x y)))))
    (let [covered (filterv #(< (aget tops %) h) (range w))]
      (if (empty? covered)
        {:kind :arc :env (double-array w 0.0) :reach 1.0 :x0 0.0 :x1 (double w)}
        (let [win (max 2 (quot w 8))
              lo (first covered) hi (peek covered)
              ;; upper envelope over a wide window, then a moving average
              env0 (double-array w)
              _ (dotimes [x w]
                  (let [xs (filter #(< (aget tops %) h) (range (max lo (- x win)) (inc (min hi (+ x win)))))]
                    (aset env0 x (double (if (seq xs) (apply min (map #(aget tops %) xs))
                                             (aget tops (if (< x lo) lo hi)))))))
              sm (max 1 (quot w 40))
              env (double-array w)
              _ (dotimes [x w]
                  (let [xs (range (max 0 (- x sm)) (inc (min (dec w) (+ x sm))))]
                    (aset env x (/ (reduce + (map #(aget env0 %) xs)) (count xs)))))
              drops (for [i (range 0 (* w h) 5) :when (> (alpha-at i) 60)]
                      (- (quot i w) (aget env (mod i w))))]
          {:kind :arc :env env :reach (max 1.0 (or (percentile drops 0.97) 1.0))
           :x0 (double lo) :x1 (double hi)})))))

(defn position-fn
  "A function of pixel `x` `y` giving 0 at the root and 1 at the tip. Made
   once per piece and called per pixel, so the frame is unpacked here and not
   on every call."
  [{:keys [kind cx cy ux uy lo span reach env]}]
  (if (= kind :arc)
    (let [^doubles env env reach (double reach)]
      (fn ^double [^long x ^long y]
        (clamp01 (/ (- (double y) (aget env x)) reach))))
  (let [cx (double cx) cy (double cy)]
    (if (= kind :angle)
      (let [ux (double ux) uy (double uy) lo (double lo) span (double span)]
        (fn ^double [^long x ^long y]
          (clamp01 (/ (- (+ (* (- (double x) cx) ux) (* (- (double y) cy) uy)) lo) span))))
      (let [reach (double reach)]
        (fn ^double [^long x ^long y]
          (let [dx (- (double x) cx) dy (- (double y) cy)]
            (clamp01 (/ (Math/sqrt (+ (* dx dx) (* dy dy))) reach)))))))))

(defn position
  "0 at the root, 1 at the tip, for the pixel at `x` `y`."
  [frame x y]
  ((position-fn frame) x y))

;; ---------------------------------------------------------------------------
;; The colour
;; ---------------------------------------------------------------------------

(defn- smoothstep ^double [^double e0 ^double e1 ^double x]
  (let [t (clamp01 (/ (- x e0) (max 1e-6 (- e1 e0))))]
    (* t t (- 3.0 (* 2.0 t)))))

(defn- soft-light ^double [^double b ^double s]
  (if (<= s 0.5)
    (- b (* (- 1.0 (* 2.0 s)) b (- 1.0 b)))
    (+ b (* (- (* 2.0 s) 1.0)
            (- (if (<= b 0.25) (* (+ (* (- (* 16.0 b) 12.0) b) 4.0) b) (Math/sqrt b)) b)))))

(defn- channel ^long [^double base ^double s]
  (long (Math/round (* 255.0 (clamp01 (soft-light base s))))))

(defn ombre-fn
  "A function of a hair pixel's drawn colour (packed 0xRRGGBB), its position `t` and
   the position the depth pass reads `td` (the same, unless lit from a side)
   giving its colour packed as 0xRRGGBB: the drawing multiplied by the colour
   between root and tip, then the depth pass. `root` and `tip` are [r g b]
   0..255. Made once per piece and called per pixel, so it is kept free of
   allocation."
  [root tip {:keys [start falloff depth]}]
  (let [start (double start) depth (double depth)
        end (+ start (* (max 0.02 (double falloff)) (- 1.0 start)))
        r0 (/ (double (nth root 0)) 255.0) g0 (/ (double (nth root 1)) 255.0) b0 (/ (double (nth root 2)) 255.0)
        r1 (/ (double (nth tip 0)) 255.0) g1 (/ (double (nth tip 1)) 255.0) b1 (/ (double (nth tip 2)) 255.0)]
    (fn ^long [^long rgb ^double t ^double td]
      (let [r (bit-and (bit-shift-right rgb 16) 0xff)
            g (bit-and (bit-shift-right rgb 8) 0xff)
            b (bit-and rgb 0xff)
            m (smoothstep start end t)
            s (clamp01 (+ 0.5 (* depth (- td 0.45))))
            cr (channel (* (/ (double r) 255.0) (+ r0 (* (- r1 r0) m))) s)
            cg (channel (* (/ (double g) 255.0) (+ g0 (* (- g1 g0) m))) s)
            cb (channel (* (/ (double b) 255.0) (+ b0 (* (- b1 b0) m))) s)]
        (bit-or (bit-shift-left cr 16) (bit-shift-left cg 8) cb)))))

(defn- luminance [[r g b]] (/ (+ (* 0.299 r) (* 0.587 g) (* 0.114 b)) 255.0))

(defn layer-colours
  "[root tip] for a hair layer, both [r g b]: as given, except that the hair
   BEHIND the head (:under) is darkened in proportion to how light it is --
   dark hair is dark already, and darkening it further only muddies it."
  [layer-key root tip {:keys [under]}]
  (if (and (pos? under) (#{:hair-back :hair-bits} layer-key))
    (let [f (fn [c] (let [k (- 1.0 (* under 0.28 (luminance c)))]
                      (mapv #(int (Math/round (* k %))) c)))]
      [(f root) (f tip)])
    [root tip]))

(defn piece-seed
  "A number per hair layer, so neighbouring pieces do not streak in step."
  [layer-key]
  (inc (.indexOf pa/layer-order layer-key)))

(defn- clump-wave
  "A fixed wave across a piece, -1..1: three sines at unrelated frequencies,
   the same in every renderer and on every render. `seed` keeps neighbouring
   pieces from waving in step."
  [seed]
  (let [s (double seed)]
    (fn ^double [^double u]
      (/ (+ (Math/sin (+ (* u 43.98) (* s 1.7)))
            (* 0.6 (Math/sin (+ (* u 81.68) (* s 2.9))))
            (* 0.35 (Math/sin (+ (* u 144.5) (* s 4.3)))))
         1.95))))

;; ---------------------------------------------------------------------------
;; Streaks that follow the strands
;; ---------------------------------------------------------------------------
;;
;; A wave across the piece made every streak a straight vertical stripe, which
;; on a cut drawn swept and curled reads as paint on glass. The art already
;; says which way the hair runs: its lines are drawn along the strands. So the
;; strand direction is read from the linework (the smoothed structure tensor of
;; the ink -- the direction the ink changes LEAST), turned to point away from
;; the crown, and filled in from 'away from the crown' where no lines are
;; drawn. Noise is then averaged along that direction (line integral
;; convolution): a blob smeared along the strands becomes a streak that bends
;; as they bend. Worked on a grid a few pixels coarse, the streaks being far
;; softer than that; it depends only on the art, so renderers keep one per
;; piece and a colour change costs nothing more.

;; 32-bit integer maths that gives the same bits on the JVM and in JS
#?(:clj  (defn- mul32 ^long [^long a ^long b] (long (unchecked-multiply-int (unchecked-int a) (unchecked-int b))))
   :cljs (defn- mul32 [a b] (.imul js/Math a b)))
#?(:clj  (defn- ushr32 ^long [^long h ^long n] (unsigned-bit-shift-right (bit-and h 0xffffffff) n))
   :cljs (defn- ushr32 [h n] (unsigned-bit-shift-right h n)))

(defn- hash01
  "An integer hash of a grid point to 0..1, identical in every renderer."
  ^double [^long a ^long b ^long seed]
  (let [h (bit-xor (mul32 a 374761393) (mul32 b 668265263) (mul32 seed 1103515245))
        h (mul32 (bit-xor h (ushr32 h 13)) 1274126177)
        h (bit-xor h (ushr32 h 16))]
    (/ (double (bit-and h 0xffff)) 65535.0)))

(defn- value-noise ^double [^double x ^double y ^long seed]
  (let [xi (Math/floor x) yi (Math/floor y)
        u (- x xi) v (- y yi)
        u (* u u (- 3.0 (* 2.0 u))) v (* v v (- 3.0 (* 2.0 v)))
        a (long xi) b (long yi)
        n00 (hash01 a b seed) n10 (hash01 (inc a) b seed)
        n01 (hash01 a (inc b) seed) n11 (hash01 (inc a) (inc b) seed)]
    (+ (* (- 1.0 v) (+ n00 (* u (- n10 n00))))
       (* v (+ n01 (* u (- n11 n01)))))))

(defn- box-pass!
  "One box blur of radius `r` along `lines` lines of `len` samples: sample k
   of line l is at l*`lstride` + k*`kstride`."
  [src dst len lines lstride kstride r]
  (let [len (long len) lines (long lines) lstride (long lstride) kstride (long kstride) r (long r)
        norm (/ 1.0 (inc (* 2 r)))
        last-k (dec len)]
    (dotimes [l lines]
      (let [base (* l lstride)]
        (loop [k 0
               acc (loop [j (- r) acc 0.0]
                     (if (> j r) acc
                         (recur (inc j) (+ acc (ag src (+ base (* kstride (max 0 (min last-k j)))))))))]
          (when (< k len)
            (as! dst (+ base (* k kstride)) (* acc norm))
            (recur (inc k)
                   (+ (- acc (ag src (+ base (* kstride (max 0 (min last-k (- k r)))))))
                      (ag src (+ base (* kstride (max 0 (min last-k (+ k r 1))))))))))))))

(defn- box-blur!
  "`a` (gw x gh doubles) blurred in place by a box of radius `r`, twice
   over -- near enough a gaussian for smoothing a direction field."
  [a gw gh r]
  (let [tmp (new-doubles (* gw gh))]
    (dotimes [_ 2]
      (box-pass! a tmp gw gh gw 1 r)
      (box-pass! tmp a gh gw 1 gw r))
    a))

(defn strand-field
  "Streak values for one hair piece, -1..1, on a grid `cell` pixels square:
   {:cell :gw :gh :v doubles}. `rgb-at` gives the drawn 0xRRGGBB and
   `alpha-at` the alpha (0..255) at a pixel index of the `w` x `h` image;
   `crown` is [x y]; `seed` keeps neighbouring pieces from streaking alike."
  [rgb-at alpha-at w h crown seed]
  (let [w (long w) h (long h)
        cell (max 1 (long (Math/round (/ (double h) 375.0))))
        gw (long (Math/ceil (/ (double w) cell))) gh (long (Math/ceil (/ (double h) cell)))
        n (* gw gh)
        ink (new-doubles n) cov (new-doubles n)
        area (double (* cell cell))]
    ;; ink (dark, opaque) and coverage per cell
    (dotimes [i (* w h)]
      (let [al (long (alpha-at i))]
        (when (pos? al)
          (let [g (+ (quot (mod i w) cell) (* (quot (quot i w) cell) gw))
                a (/ (double al) (* 255.0 area))
                c (long (rgb-at i))
                lum (/ (+ (* 0.299 (bit-and (bit-shift-right c 16) 0xff))
                          (* 0.587 (bit-and (bit-shift-right c 8) 0xff))
                          (* 0.114 (bit-and c 0xff)))
                       255.0)]
            (as! cov g (+ (ag cov g) a))
            (as! ink g (+ (ag ink g) (* a (- 1.0 lum))))))))
    (let [jxx (new-doubles n) jxy (new-doubles n) jyy (new-doubles n)]
      (loop [gy 1]
        (when (< gy (dec gh))
          (loop [gx 1]
            (when (< gx (dec gw))
              (let [g (+ gx (* gy gw))
                    dx (- (ag ink (inc g)) (ag ink (dec g)))
                    dy (- (ag ink (+ g gw)) (ag ink (- g gw)))]
                (as! jxx g (* dx dx)) (as! jxy g (* dx dy)) (as! jyy g (* dy dy)))
              (recur (inc gx))))
          (recur (inc gy))))
      (doseq [a [jxx jxy jyy]] (box-blur! a gw gh 3))
      (let [[cx cy] crown
            cx (/ (double cx) cell) cy (/ (double cy) cell)
            dirx (new-doubles n) diry (new-doubles n)]
        (dotimes [g n]
          (let [a (ag jxx g) b (ag jxy g) c (ag jyy g)
                tr (+ a c)
                coh (if (> tr 1e-12) (/ (Math/sqrt (+ (* (- a c) (- a c)) (* 4.0 b b))) tr) 0.0)
                ;; along the lines: perpendicular to the strongest change
                th (+ (* 0.5 (Math/atan2 (* 2.0 b) (- a c))) (/ Math/PI 2.0))
                lx (Math/cos th) ly (Math/sin th)
                rx (- (double (mod g gw)) cx) ry (- (double (quot g gw)) cy)
                rl (max 1e-6 (Math/sqrt (+ (* rx rx) (* ry ry))))
                rx (/ rx rl) ry (/ ry rl)
                flip (if (neg? (+ (* lx rx) (* ly ry))) -1.0 1.0)
                k (min 1.0 (* 1.5 coh))
                dx (+ (* k flip lx) (* (- 1.0 k) rx))
                dy (+ (* k flip ly) (* (- 1.0 k) ry))
                dl (max 1e-6 (Math/sqrt (+ (* dx dx) (* dy dy))))]
            (as! dirx g (/ dx dl)) (as! diry g (/ dy dl))))
        (let [v (new-doubles n)
              steps 18
              freq (/ 1.0 3.5)
              seed (long seed)
              ;; the noise once per cell; the walk reads it 36 times over
              nz (let [a (new-doubles n)]
                   (dotimes [g n]
                     (when (> (ag cov g) 0.0)
                       (as! a g (value-noise (* (+ 0.5 (mod g gw)) freq) (* (+ 0.5 (quot g gw)) freq) seed))))
                   a)
              walk (fn ^double [^double x ^double y ^double sgn]
                     (loop [s 0 x x y y acc 0.0 px 0.0 py 0.0]
                       (let [ix (long x) iy (long y)]
                         (if (or (= s steps) (< x 0.0) (< y 0.0) (>= ix gw) (>= iy gh))
                           acc
                           (let [g (+ ix (* iy gw))
                                 dx (ag dirx g) dy (ag diry g)
                                 ;; a direction field has no sign: keep the heading of the last step
                                 f (if (neg? (+ (* dx px) (* dy py))) -1.0 1.0)
                                 dx (* f dx) dy (* f dy)]
                             (recur (inc s) (+ x (* sgn dx)) (+ y (* sgn dy))
                                    (+ acc (ag nz g))
                                    dx dy))))))]
          (dotimes [g n]
            (when (> (ag cov g) 0.05)
              (let [x (+ 0.5 (mod g gw)) y (+ 0.5 (quot g gw))]
                (as! v g (/ (+ (walk x y 1.0) (walk x y -1.0)) (* 2.0 steps))))))
          ;; centre and scale over the piece so every piece streaks as strongly
          (let [[sum sq cnt] (loop [g 0 sum 0.0 sq 0.0 cnt 0]
                               (if (= g n) [sum sq cnt]
                                   (if (> (ag cov g) 0.05)
                                     (let [x (ag v g)] (recur (inc g) (+ sum x) (+ sq (* x x)) (inc cnt)))
                                     (recur (inc g) sum sq cnt))))
                mean (if (pos? cnt) (/ sum cnt) 0.0)
                sd (if (pos? cnt) (Math/sqrt (max 1e-12 (- (/ sq cnt) (* mean mean)))) 1.0)]
            (dotimes [g n]
              (as! v g (if (> (ag cov g) 0.05)
                         (max -1.0 (min 1.0 (/ (- (ag v g) mean) (* 2.0 sd))))
                         0.0))))
          {:cell cell :gw gw :gh gh :v v})))))

(defn- field-fn
  "A function of pixel `x` `y` giving the strand field's value there,
   interpolated between cells. Unpacked once per piece, like `position-fn`."
  [{:keys [cell gw gh v]}]
  (let [cell (double cell) gw (long gw) gh (long gh)]
    (fn ^double [^long x ^long y]
      (let [fx (max 0.0 (- (/ (double x) cell) 0.5)) fy (max 0.0 (- (/ (double y) cell) 0.5))
            x0 (min (dec gw) (long fx)) y0 (min (dec gh) (long fy))
            x1 (min (dec gw) (inc x0)) y1 (min (dec gh) (inc y0))
            u (- fx x0) t (- fy y0)
            a (ag v (+ x0 (* y0 gw))) b (ag v (+ x1 (* y0 gw)))
            c (ag v (+ x0 (* y1 gw))) d (ag v (+ x1 (* y1 gw)))]
        (+ (* (- 1.0 t) (+ a (* u (- b a)))) (* t (+ c (* u (- d c)))))))))

(defn piece-settings
  "The ombre settings for one piece: the portrait's, with the piece's own
   calibration applied (pieces.edn). :asset/tips-from keeps a piece in the
   root colour until that far along it -- how colourists treat bangs in an
   ombre, which stay in the darkest shade while the lengths lighten -- so a
   long fringe gets at most tipped ends instead of a band across it."
  [settings asset]
  (if-let [f (:asset/tips-from asset)]
    (assoc settings :tips-from (clamp01 (double f)))
    settings))

(defn pixel-fn
  "The per-pixel function for one hair piece: (x, y, drawn 0xRRGGBB) -> the
   coloured 0xRRGGBB. Wraps `position-fn` and `ombre-fn` with the quick-
   colouring tricks: :clumps wavers where the tip colour starts along the
   piece, :light lifts the depth toward the upper left. With a `field`
   (`strand-field`) the streaks follow the strands; without one they fall
   back to a wave across the piece."
  ([root tip settings frame layer-key] (pixel-fn root tip settings frame layer-key nil))
  ([root tip settings frame layer-key field]
  (let [pos (position-fn frame)
        colour (ombre-fn root tip settings)
        {:keys [clumps light tips-from]} settings
        tips-from (double (or tips-from 0.0))
        x0 (double (or (:x0 frame) 0.0))
        span (max 1.0 (- (double (or (:x1 frame) 1.0)) x0))
        wave (clump-wave (piece-seed layer-key))
        strand (when field (field-fn field))
        amp (* 0.14 (double clumps))
        light (double light)]
    (fn ^long [^long x ^long y ^long rgb]
      (let [u (/ (- (double x) x0) span)
            t0 (pos x y)
            ;; streaks move where the tip colour starts, not the depth: hair
            ;; with no second colour looks exactly as it did
            t (if (pos? amp)
                (clamp01 (+ t0 (* amp (if strand (strand x y) (wave u)))))
                t0)
            ;; a piece held in the root colour until `tips-from` along it
            t (if (pos? tips-from)
                (if (>= tips-from 1.0) 0.0 (clamp01 (/ (- t tips-from) (- 1.0 tips-from))))
                t)
            ;; light at the upper left: shift the depth curve's position so the
            ;; near side reads as further along (lighter), the far side less
            td (if (pos? light) (clamp01 (+ t0 (* light 0.35 (- 0.5 u)))) t0)]
        (colour rgb t td))))))

(defn ombre-rgb
  "A hair pixel's colour as [r g b] (see `ombre-fn`), for one pixel."
  [r g b root tip t settings]
  (let [c ((ombre-fn root tip settings) (bit-or (bit-shift-left r 16) (bit-shift-left g 8) b) t t)]
    [(bit-and (bit-shift-right c 16) 0xff) (bit-and (bit-shift-right c 8) 0xff) (bit-and c 0xff)]))

;; ---------------------------------------------------------------------------
;; Cast shadows
;; ---------------------------------------------------------------------------

(def shadow-strength
  "Chosen as 'subtle' on the real art; 'medium' (0.45) read as dirty skin."
  0.28)

(defn shadow-offset
  "How far below the hair's edge the shadow falls, for a drawing `h` tall."
  [h] (int (Math/round (* h (/ 12.0 1500.0)))))

(defn shadow-radius
  "How soft the shadow is, for a drawing `h` tall."
  [h] (max 1 (int (Math/round (* h (/ 10.0 1500.0))))))

(defn casts-shadow?
  "Whether this piece hangs over the face and so throws a shadow onto it."
  [asset]
  (boolean (:asset/casts-shadow asset)))

(defn shadow-map
  "How much each pixel is shaded, 0..`shadow-strength`, for a `w` x `h`
   frame. `caster` is the combined alpha (0..255, doubles) of the pieces that
   cast; `skin` of the head and ears; `hair` of every hair piece in front of
   the face -- the shadow never lands on hair, only on skin that shows."
  ([caster skin hair w h] (shadow-map caster skin hair w h 0.0))
  ([caster skin hair w h light]
  (let [n (* w h)
        dy (shadow-offset h)
        ;; lit from the upper left, the shadow falls a little to the right too
        dx (long (Math/round (* (shadow-offset h) 0.8 (double light))))
        shifted (new-doubles n)]
    (dotimes [i n]
      (let [y (+ (quot i w) dy) x (+ (mod i w) dx)]
        (when (and (< y h) (< x w))
          (as! shifted (+ x (* y w)) (ag caster i)))))
    (let [blurred (colorize/blur shifted w h (shadow-radius h))
          out (new-doubles n)]
      (dotimes [i n]
        (let [sk (/ (ag skin i) 255.0)
              cover (/ (ag hair i) 255.0)]
          (when (and (pos? sk) (< cover 1.0))
            (as! out i (* shadow-strength sk (- 1.0 cover) (/ (ag blurred i) 255.0))))))
      out))))

(defn shadow-factors
  "What a pixel's r g b are multiplied by under shade `k`: skin shadow runs
   warm, so blue and green drop more than red."
  [k]
  [(- 1.0 (* k 0.75)) (- 1.0 k) (- 1.0 (* k 0.95))])
