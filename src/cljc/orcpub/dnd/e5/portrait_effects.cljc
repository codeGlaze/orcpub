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
   :under 0.0     ; the hair behind the head a little darker, by how light it is
   :shine 0.0     ; a highlight across the crown, broken along the strands
   :split false}) ; the tips colour on one side of a line through the crown

(defn- clamp01 ^double [^double x] (if (< x 0.0) 0.0 (if (> x 1.0) 1.0 x)))

(defn- new-doubles [n]
  #?(:clj (double-array n) :cljs (js/Float64Array. n)))

;; typed on the JVM, where an untyped aget over a whole frame reflects
#?(:clj  (defn- ag ^double [^doubles a ^long i] (aget a i))
   :cljs (defn- ag [a i] (aget a i)))
#?(:clj  (defn- as! [^doubles a ^long i ^double v] (aset a i v))
   :cljs (defn- as! [a i v] (aset a i v)))

(def bangs-dye
  "How far along the bangs hold the root colour, for each choice in the
   Bangs control. Picked on the real art: tipped is about where the fringe
   reaches the eyes."
  {:roots 1.0 :tipped 0.45 :dyed 0.0})

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
     :shine (num :shine)
     :split (true? (:split o))
     ;; a split is its own way of running; an angle left over from before is not
     :angle (let [a (:angle o)] (when (and (number? a) (not (true? (:split o)))) (mod (double a) 360.0)))
     ;; nil: each bangs piece does what its calibration says
     :bangs (let [b (:bangs o)
                  b (when (or (keyword? b) (string? b)) (keyword (name b)))]
              (when (contains? bangs-dye b) b))}))

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

(defn run-mode
  "How the tips colour runs, for `gradient-frame`: nil (down from the crown
   line), :split, or an angle in degrees."
  [settings]
  (if (:split settings) :split (:angle settings)))

(defn gradient-frame
  "How to place a piece's pixels between root (0) and tip (1), by `mode`
   (`run-mode`):

   - nil: down from the piece's own crown line (`arc-frame`), so every strand
     runs root to tip wherever it starts.
   - an angle: along a straight line, measured from the crown.
   - :split: across a line down through the crown, root colour on one side
     and tips on the other.

   The angle and the split are measured from the CROWN and on the frame's
   scale, never from the piece: every piece has to agree where the line is.
   Measured per piece, each put it across its own middle, so the front hair
   split in one place and the bangs in another.

   `with-arc?` attaches the crown-line frame as :arc to an angle or a split
   too, for the shine, which always follows the curve of the head."
  ([alpha-at w h crown mode] (gradient-frame alpha-at w h crown mode false))
  ([alpha-at w h crown mode with-arc?]
   (if (nil? mode)
     (arc-frame alpha-at w h)
     (let [[cx cy] (or crown [(/ w 2.0) (/ h 10.0)])
           arc (when with-arc? (arc-frame alpha-at w h))
           frame (if (= mode :split)
                   ;; narrow, so Starts moves the line and Blend softens it
                   {:kind :split :cx (double cx) :span (* 0.25 h) :h (double h)}
                   (let [a (* (double mode) (/ Math/PI 180.0))]
                     ;; the crown about a third of the way along, the whole
                     ;; head and shoulders within the span
                     {:kind :angle :cx (double cx) :cy (double cy) :ux (Math/sin a) :uy (Math/cos a)
                      :lo (* -0.25 h) :span (* 0.8 h)}))]
       (cond-> (assoc frame :x0 0.0 :x1 (double w))
         arc (assoc :arc arc))))))

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
  (cond
    (= kind :split)
    (let [cx (double cx) span (double span)]
      (fn ^double [^long x ^long _y]
        (clamp01 (+ 0.5 (/ (- (double x) cx) span)))))

    (= kind :arc)
    (let [^doubles env env reach (double reach)]
      (fn ^double [^long x ^long y]
        (clamp01 (/ (- (double y) (aget env x)) reach))))

    :else
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

(defn hash01
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
  "Streak values for one hair piece, -1..1, on a grid a few pixels square:
   {:gw :gh :w :h :v doubles}, for art `w` x `h`. `rgb-at` gives the drawn
   0xRRGGBB and `alpha-at` the alpha (0..255) at a pixel index of the art at
   its own size; `seed` keeps neighbouring pieces from streaking alike.

   Depends on nothing but the art -- not the head, not the colours -- so it
   is worked out once per piece, ahead of time (orcpub.portrait-pack.strands
   writes it beside the art), and only computed live when that file is
   missing."
  [rgb-at alpha-at w h seed]
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
      (let [;; where the strands start: the top of the piece, centred on it
            [cx cy] (loop [g 0 x0 gw x1 -1 y0 gh]
                      (if (= g n)
                        (if (neg? x1) [(/ gw 2.0) 0.0] [(/ (+ x0 x1) 2.0) (+ y0 (/ gh 40.0))])
                        (if (> (ag cov g) 0.05)
                          (let [x (mod g gw)]
                            (recur (inc g) (min x0 x) (max x1 x) (min y0 (quot g gw))))
                          (recur (inc g) x0 x1 y0))))
            cx (double cx) cy (double cy)
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
          {:gw gw :gh gh :w w :h h :v v})))))

(defn strands-url
  "Where a piece's precomputed field lives: beside its art, as
   <name>.strands.png -- with the art, never committed with the code."
  [art-url]
  (when (and (string? art-url) (re-find #"(?i)\.png$" art-url))
    (str (subs art-url 0 (- (count art-url) 4)) ".strands.png")))

(defn field->bytes
  "The field as one byte per cell, 0..255 with 128 for 0, for storing beside
   the art as a greyscale image `gw` x `gh`."
  [{:keys [gw gh v]}]
  (let [n (* gw gh)
        out #?(:clj (byte-array n) :cljs (js/Uint8Array. n))]
    (dotimes [g n]
      (let [b (long (Math/round (+ 128.0 (* 127.0 (ag v g)))))]
        (aset out g #?(:clj (unchecked-byte b) :cljs b))))
    out))

(defn bytes->field
  "A field back from `field->bytes`' bytes (`byte-at` gives cell g's 0..255),
   for art `w` x `h`."
  [byte-at gw gh w h]
  (let [n (* gw gh) v (new-doubles n)]
    (dotimes [g n] (as! v g (/ (- (double (byte-at g)) 128.0) 127.0)))
    {:gw gw :gh gh :w w :h h :v v}))

(defn- field-fn
  "A function of pixel `x` `y` giving the strand field's value there,
   interpolated between cells; `x` `y` are pixels of the art at its own size,
   or of a frame it is placed in at :rect [x y w h].
   Unpacked once per piece, like `position-fn`."
  [{:keys [gw gh w h v rect]}]
  (let [[rx ry dw dh] (or rect [0 0 w h])
        gw (long gw) gh (long gh) rx (double rx) ry (double ry)
        sx (/ (double gw) dw) sy (/ (double gh) dh)]
    (fn ^double [^long x ^long y]
      (let [fx (max 0.0 (- (* (- (double x) rx) sx) 0.5)) fy (max 0.0 (- (* (- (double y) ry) sy) 0.5))
            x0 (min (dec gw) (long fx)) y0 (min (dec gh) (long fy))
            x1 (min (dec gw) (inc x0)) y1 (min (dec gh) (inc y0))
            u (- fx x0) t (- fy y0)
            a (ag v (+ x0 (* y0 gw))) b (ag v (+ x1 (* y0 gw)))
            c (ag v (+ x0 (* y1 gw))) d (ag v (+ x1 (* y1 gw)))]
        (+ (* (- 1.0 t) (+ a (* u (- b a)))) (* t (+ c (* u (- d c)))))))))

(defn bangs-choice
  "The bangs choice this piece makes when the portrait has not made one:
   from its calibration (pieces.edn), dyed like the rest otherwise."
  [asset]
  (let [f (:asset/tips-from asset)]
    (cond (nil? f) :dyed
          (>= f 1.0) :roots
          (pos? f) :tipped
          :else :dyed)))

(defn piece-settings
  "The ombre settings for one piece: the portrait's, with the piece's own
   calibration applied (pieces.edn). :asset/tips-from keeps a piece in the
   root colour until that far along it -- how colourists often treat bangs
   in an ombre, which stay in the darkest shade while the lengths lighten --
   so a long fringe gets at most tipped ends instead of a band across it.
   That is only the default: on the bangs layer the portrait's own :bangs
   choice (root colour, tipped or dyed) wins."
  ([settings asset] (piece-settings settings asset nil))
  ([settings asset layer-key]
   (let [f (if-let [choice (and (= :bangs layer-key) (:bangs settings))]
             (bangs-dye choice)
             (:asset/tips-from asset))]
     ;; holding a piece in the root colour is about root to tip; a split or
     ;; an angle runs across the head, and the bangs carry it like the rest
     (if (and f (pos? f) (nil? (run-mode settings)))
       (assoc settings :tips-from (clamp01 (double f)))
       (dissoc settings :tips-from)))))

(def shine-layers
  "The pieces over the top of the head, where light catches: not the hair
   behind it or the bits hanging below."
  #{:scalp :hair-front :bangs})

(defn- split-wobble
  "A slow wave down the frame, -1..1, the SAME for every piece: the split's
   edge wavers with it. Each piece's own strand field would have put the edge
   in a different place on each piece, leaving a wedge of the root colour
   where the hair behind the bangs shows."
  ^double [^double v]
  (/ (+ (Math/sin (+ (* v 17.0) 0.6)) (* 0.55 (Math/sin (+ (* v 41.0) 2.1))) (* 0.3 (Math/sin (+ (* v 83.0) 4.4))))
     1.85))

(defn- smoothstep* ^double [^double e0 ^double e1 ^double x]
  (let [t (clamp01 (/ (- x e0) (- e1 e0)))] (* t t (- 3.0 (* 2.0 t)))))

(defn- shine-band
  "How much highlight a pixel `t` along its piece's crown line takes, with
   strand field value `f` (0 without one): one clean ring across the piece,
   crisp edged, barely wavering with the strands. Chosen on the real art. A
   band broken into strand-shaped pieces read as salt drying on cloth, and
   one ellipse across the whole head arched over every piece like a halo:
   each piece takes its own ring."
  ^double [^double t ^double f]
  (let [dt (Math/abs (- t (+ 0.22 (* 0.02 f))))]
    (* 0.55 (- 1.0 (smoothstep* 0.035 0.055 dt)))))

(defn- shine-colour
  "The highlight: the hair colour a little more saturated, then lifted most
   of the way toward white -- light in the hair's own hue, not grey."
  [[r g b]]
  (let [m (/ (+ r g b) 3.0)]
    (mapv #(min 255.0 (+ (* (+ m (* 1.25 (- % m))) 0.55) (* 255.0 0.45))) [r g b])))

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
        {:keys [clumps light tips-from shine]} settings
        split? (= :split (:kind frame))
        frame-h (double (or (:h frame) 1.0))
        shine (if (shine-layers layer-key) (double (or shine 0.0)) 0.0)
        root-shine (shine-colour root)
        tip-shine (when (not= root tip) (shine-colour tip))
        arc-pos (when (pos? shine) (if (= :arc (:kind frame)) pos (some-> (:arc frame) position-fn)))
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
                (clamp01 (+ t0 (* amp (cond split? (split-wobble (/ (double y) frame-h))
                                            strand (strand x y)
                                            :else (wave u)))))
                t0)
            ;; a piece held in the root colour until `tips-from` along it
            t (if (pos? tips-from)
                (if (>= tips-from 1.0) 0.0 (clamp01 (/ (- t tips-from) (- 1.0 tips-from))))
                t)
            ;; light at the upper left: shift the depth curve's position so the
            ;; near side reads as further along (lighter), the far side less
            td (if (pos? light) (clamp01 (+ t0 (* light 0.35 (- 0.5 u)))) t0)
            out (colour rgb t td)]
        (if (and arc-pos (pos? shine))
          ;; on the fill only, and on the fill flattened: the art's paper
          ;; grain must not turn into speckle
          (let [lum (/ (+ (bit-and (bit-shift-right rgb 16) 0xff) (bit-and (bit-shift-right rgb 8) 0xff) (bit-and rgb 0xff)) 765.0)
                ta (arc-pos x y)
                k (* shine (smoothstep* 0.55 0.8 lum) (shine-band ta (if strand (strand x y) 0.0)))]
            (if (> k 0.004)
              (let [[cr cg cb] (if (and tip-shine (> ta 0.5)) tip-shine root-shine)
                    ch (fn ^long [^long c ^double l]
                         (let [sc (- 255.0 (/ (* (- 255.0 c) (- 255.0 l)) 255.0))]
                           (long (Math/round (+ c (* k (- sc c)))))))]
                (bit-or (bit-shift-left (ch (bit-and (bit-shift-right out 16) 0xff) cr) 16)
                        (bit-shift-left (ch (bit-and (bit-shift-right out 8) 0xff) cg) 8)
                        (ch (bit-and out 0xff) cb)))
              out))
          out))))))

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
