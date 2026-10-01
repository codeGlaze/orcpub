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
   :angle nil})   ; nil: down from the crown line; degrees: a straight line, 0 = down

(defn- clamp01 ^double [^double x] (if (< x 0.0) 0.0 (if (> x 1.0) 1.0 x)))

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
        {:kind :angle :cx cx :cy cy :ux ux :uy uy :lo lo :span (max 1.0 (- hi lo))})
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
        {:kind :arc :env (double-array w 0.0) :reach 1.0}
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
          {:kind :arc :env env :reach (max 1.0 (or (percentile drops 0.97) 1.0))})))))

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
  "A function of a hair pixel's drawn `r g b` (0..255) and its position `t`
   giving its colour packed as 0xRRGGBB: the drawing multiplied by the colour
   between root and tip, then the depth pass. `root` and `tip` are [r g b]
   0..255. Made once per piece and called per pixel, so it is kept free of
   allocation."
  [root tip {:keys [start falloff depth]}]
  (let [start (double start) depth (double depth)
        end (+ start (* (max 0.02 (double falloff)) (- 1.0 start)))
        r0 (/ (double (nth root 0)) 255.0) g0 (/ (double (nth root 1)) 255.0) b0 (/ (double (nth root 2)) 255.0)
        r1 (/ (double (nth tip 0)) 255.0) g1 (/ (double (nth tip 1)) 255.0) b1 (/ (double (nth tip 2)) 255.0)]
    (fn ^long [^long r ^long g ^long b ^double t]
      (let [m (smoothstep start end t)
            s (clamp01 (+ 0.5 (* depth (- t 0.45))))
            cr (channel (* (/ (double r) 255.0) (+ r0 (* (- r1 r0) m))) s)
            cg (channel (* (/ (double g) 255.0) (+ g0 (* (- g1 g0) m))) s)
            cb (channel (* (/ (double b) 255.0) (+ b0 (* (- b1 b0) m))) s)]
        (bit-or (bit-shift-left cr 16) (bit-shift-left cg 8) cb)))))

(defn ombre-rgb
  "A hair pixel's colour as [r g b] (see `ombre-fn`), for one pixel."
  [r g b root tip t settings]
  (let [c ((ombre-fn root tip settings) r g b t)]
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

(defn- new-doubles [n]
  #?(:clj (double-array n) :cljs (js/Float64Array. n)))

;; typed on the JVM, where an untyped aget over a whole frame reflects
#?(:clj  (defn- ag ^double [^doubles a ^long i] (aget a i))
   :cljs (defn- ag [a i] (aget a i)))
#?(:clj  (defn- as! [^doubles a ^long i ^double v] (aset a i v))
   :cljs (defn- as! [a i v] (aset a i v)))

(defn shadow-map
  "How much each pixel is shaded, 0..`shadow-strength`, for a `w` x `h`
   frame. `caster` is the combined alpha (0..255, doubles) of the pieces that
   cast; `skin` of the head and ears; `hair` of every hair piece in front of
   the face -- the shadow never lands on hair, only on skin that shows."
  [caster skin hair w h]
  (let [n (* w h)
        dy (shadow-offset h)
        shifted (new-doubles n)]
    (dotimes [i n]
      (let [y (+ (quot i w) dy)]
        (when (< y h)
          (as! shifted (+ (mod i w) (* y w)) (ag caster i)))))
    (let [blurred (colorize/blur shifted w h (shadow-radius h))
          out (new-doubles n)]
      (dotimes [i n]
        (let [sk (/ (ag skin i) 255.0)
              cover (/ (ag hair i) 255.0)]
          (when (and (pos? sk) (< cover 1.0))
            (as! out i (* shadow-strength sk (- 1.0 cover) (/ (ag blurred i) 255.0))))))
      out)))

(defn shadow-factors
  "What a pixel's r g b are multiplied by under shade `k`: skin shadow runs
   warm, so blue and green drop more than red."
  [k]
  [(- 1.0 (* k 0.75)) (- 1.0 k) (- 1.0 (* k 0.95))])
