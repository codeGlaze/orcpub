(ns orcpub.dnd.e5.portrait-face
  "Eye and skin effects, shared by every renderer: the share card (Java2D),
   the PDF bake and the builder's drawer (both canvas).

   EYES are coloured through their luminance inside the region placed in the
   Loom (portrait-colorize). On top of that, here:
   - a second eye colour, for the eye on the right of the picture;
   - light from below: the lower part of the iris lifted in its own colour,
     the way light comes through a real iris. The art already draws the
     catchlight, so nothing is added up top -- a second reflection read as
     salt on the real art;
   - snake and goat pupils, painted over the drawn round pupil. They stand
     upright (snake) and level (goat) on screen whatever rotation the iris
     oval was placed at: several eyes were placed with the oval turned 90
     degrees, and a slit that followed that came out sideways.

   SKIN takes blush and freckles: multiplied, so the linework stays ink, and
   confined to skin that is showing, the same mask the cast shadows use.
   Placed from the eyes, so no piece needs a cheek or a nose marked on it.

   All of it is per-pixel maths, so the three renderers agree exactly; they
   only supply the pixels and the masks."
  (:require [orcpub.dnd.e5.portrait-colorize :as colorize]
            [orcpub.dnd.e5.portrait-effects :as fx]))

;; ---------------------------------------------------------------------------
;; Settings
;; ---------------------------------------------------------------------------

(def pupil-kinds [:round :snake :goat])

(def blush-colours
  "Rose, coral, berry, a cool fey pink."
  ["#e0606a" "#e8826a" "#c4557a" "#c87ab4"])

(def face-defaults
  {:pupil :round        ; as drawn
   :second-eye nil      ; nil: both eyes take the Eyes colour
   :blush 0.0           ; 0..1
   :blush-colour (first blush-colours)
   :freckles 0.0        ; how many, 0..1
   :freckle-place 0.5   ; 0: over the near cheek .. 1: both cheeks and the nose
   :freckle-strength 0.5 ; how dark and how large, 0..1
   :shade 0.0})          ; soft shade on the skin from the hair over it, 0..1

(defn- clamp01 ^double [^double x] (if (< x 0.0) 0.0 (if (> x 1.0) 1.0 x)))

(defn face-settings
  "The portrait's eye and skin effects with defaults filled in and anything
   out of range pulled back in, so a hand-edited portrait cannot break a
   render."
  [portrait]
  (let [o (let [v (:face portrait)] (if (map? v) v {}))
        num (fn [k] (let [v (get o k)] (if (number? v) (clamp01 (double v)) (face-defaults k))))
        hex (fn [k] (let [v (get o k)] (when (and (string? v) (colorize/hex->rgb v)) v)))
        pupil (let [p (:pupil o) p (when (or (keyword? p) (string? p)) (keyword (name p)))]
                (if (some #{p} pupil-kinds) p :round))]
    {:pupil pupil
     :second-eye (hex :second-eye)
     :blush (num :blush)
     :blush-colour (or (hex :blush-colour) (:blush-colour face-defaults))
     :freckles (num :freckles)
     :freckle-place (num :freckle-place)
     :freckle-strength (num :freckle-strength)
     :shade (num :shade)}))

(defn marks?
  "Whether the skin carries anything (blush or freckles) to draw."
  [{:keys [blush freckles]}]
  (or (pos? blush) (pos? freckles)))

;; ---------------------------------------------------------------------------
;; Geometry
;; ---------------------------------------------------------------------------

(defn- extent
  "Half width and half height on screen of an oval {:rx :ry :rot}."
  [{:keys [rx ry rot]}]
  (let [t (double (or rot 0.0)) c (Math/cos t) s (Math/sin t)]
    [(Math/sqrt (+ (* rx c rx c) (* ry s ry s)))
     (Math/sqrt (+ (* rx s rx s) (* ry c ry c)))]))

(defn- oval-distance
  "How far `x` `y` is from the oval's centre, 1 on its edge."
  ^double [{:keys [cx cy rx ry rot]} ^double x ^double y]
  (let [t (double (or rot 0.0)) c (Math/cos t) s (Math/sin t)
        dx (- x cx) dy (- y cy)
        u (/ (+ (* dx c) (* dy s)) rx)
        v (/ (- (* dy c) (* dx s)) ry)]
    (Math/sqrt (+ (* u u) (* v v)))))

(defn- below-lid
  "How much of the pixel at `x` `y` is below the upper lid's curve, 0..1
   (1 where there is no lid over it)."
  ^double [lid ^double x ^double y]
  (if-not lid
    1.0
    (let [{:keys [x0 y0 mx my x1 y1]} lid
          a (+ (- x0 (* 2 mx)) x1) b (* 2 (- mx x0)) c (- x0 x)
          t (if (< (Math/abs (double a)) 1e-9)
              (when-not (zero? b) (/ (- c) b))
              (let [disc (- (* b b) (* 4 a c))]
                (when-not (neg? disc)
                  (let [r (Math/sqrt disc)
                        t1 (/ (+ (- b) r) (* 2 a)) t2 (/ (- (- b) r) (* 2 a))]
                    (if (<= 0.0 t1 1.0) t1 t2)))))]
      (if (and t (<= 0.0 t 1.0))
        (let [u (- 1.0 t)
              yc (+ (* u u y0) (* 2 u t my) (* t t y1))]
          (clamp01 (+ 0.5 (- y yc))))
        1.0))))

(defn- in-iris
  "How much of the pixel is inside the iris oval and below the lid."
  ^double [{:keys [iris lid]} ^double x ^double y]
  (let [d (oval-distance iris x y)
        [hw hh] (extent iris)
        edge (clamp01 (+ 0.5 (* (- 1.0 d) (min hw hh))))]
    (if (pos? edge) (* edge (below-lid lid x y)) 0.0)))

(defn- nearest-eye
  "The index into `eyes` of the iris nearest `x` `y`."
  ^long [eyes ^double x ^double y]
  (if (< (count eyes) 2)
    0
    (let [d0 (oval-distance (:iris (nth eyes 0)) x y)
          d1 (oval-distance (:iris (nth eyes 1)) x y)]
      (if (<= d0 d1) 0 1))))

(defn ordered-eyes
  "The placed eyes (portrait-colorize/iris-shapes), left to right on screen:
   the second eye colour goes to the one on the right."
  [shapes]
  (vec (sort-by #(get-in % [:iris :cx]) shapes)))

;; ---------------------------------------------------------------------------
;; Pupils
;; ---------------------------------------------------------------------------

(defn- smooth ^double [^double e0 ^double e1 ^double x]
  (let [t (clamp01 (/ (- x e0) (- e1 e0)))] (* t t (- 3.0 (* 2.0 t)))))

(defn- slit
  "How much of the pixel the new pupil covers."
  ^double [kind {:keys [iris pupil]} ^double x ^double y]
  (let [[_ ihh] (extent iris)
        [ihw _] (extent iris)
        [phw phh] (extent pupil)
        dx (- x (:cx pupil)) dy (- y (:cy pupil))]
    (case kind
      ;; a vesica: two arcs meeting at points top and bottom, across most of
      ;; the iris's height
      :snake (let [hs (* 0.88 ihh) ws (* 0.22 phw)]
               (if (< (Math/abs dy) hs)
                 (let [half (* ws (- 1.0 (* (/ dy hs) (/ dy hs))))]
                   (clamp01 (+ 0.5 (- half (Math/abs dx)))))
                 0.0))
      ;; a level bar with rounded ends, most of the iris wide
      :goat (let [w (* 1.45 ihw) r (* 0.31 phh)
                  half (max 0.0 (- (/ w 2.0) r))
                  ex (max 0.0 (- (Math/abs dx) half))
                  d (Math/sqrt (+ (* ex ex) (* dy dy)))]
              (clamp01 (+ 0.5 (- r d))))
      0.0)))

(defn- patch
  "How much of the pixel is painted over to hide the drawn round pupil."
  ^double [{:keys [pupil]} ^double x ^double y]
  (let [[phw phh] (extent pupil)
        u (/ (- x (:cx pupil)) phw) v (/ (- y (:cy pupil)) phh)
        d (Math/sqrt (+ (* u u) (* v v)))]
    (- 1.0 (smooth 1.0 1.28 d))))

;; ---------------------------------------------------------------------------
;; The eye, per pixel
;; ---------------------------------------------------------------------------

(defn- screen ^long [^long base ^long c ^double k]
  (long (Math/round (+ base (* k (- (- 255.0 (/ (* (- 255.0 base) (- 255.0 c)) 255.0)) base))))))

(defn- mix ^long [^long a ^long b ^double k]
  (long (Math/round (+ a (* k (- b a))))))

(def bounce-strength
  "Light from below, chosen on the real art: enough to read as glassy, not
   so much the iris looks lit from inside."
  0.5)

(defn eye-pixel-fn
  "The per-pixel function for an eye piece: (x y [r g b] k) -> [r g b], for
   every opaque pixel of the eye art, where `k` (0..1) is how much the
   feathered iris region covers it. `shapes` are the placed eyes in the same
   pixels (portrait-colorize/iris-shapes); `colour` and `second` are [r g b]
   (second nil for both eyes alike)."
  [shapes colour second gamma pupil]
  (let [eyes (ordered-eyes shapes)
        colours [colour (or second colour)]
        lifted (mapv (fn [c] (mapv #(+ % (* 0.6 (- 255 %))) c)) colours)
        ;; the iris's own middle tone, for painting over the drawn pupil
        mids (mapv (fn [[er eg eb]] (colorize/colorize-rgb 110 110 110 er eg eb gamma 1.0)) colours)
        extents (mapv (comp extent :iris) eyes)
        gamma (double gamma)
        new-pupil? (and (seq eyes) (not= pupil :round))]
    (fn [x y rgb k]
      (if (empty? eyes)
        rgb
        (let [x (double x) y (double y) k (double k)
              n (nearest-eye eyes x y)
              e (nth eyes n)
              [er eg eb] (nth colours n)
              [r g b] (if (pos? k) (colorize/colorize-rgb (nth rgb 0) (nth rgb 1) (nth rgb 2) er eg eb gamma k) rgb)
              ;; light from below: nothing at the centre line, most at the
              ;; bottom of the iris
              [lr lg lb] (nth lifted n)
              [_ hh] (nth extents n)
              bk (* bounce-strength k (clamp01 (/ (- y (get-in e [:iris :cy])) (* 0.85 hh))))
              [r g b] (if (pos? bk) [(screen r lr bk) (screen g lg bk) (screen b lb bk)] [r g b])]
          (if-not new-pupil?
            [r g b]
            (let [dom (in-iris e x y)]
              (if-not (pos? dom)
                [r g b]
                (let [[mr mg mb] (nth mids n)
                      ;; a touch darker toward the middle, as an iris is
                      [phw phh] (extent (:pupil e))
                      d (min 1.0 (Math/sqrt (+ (Math/pow (/ (- x (get-in e [:pupil :cx])) (* 1.2 phw)) 2)
                                               (Math/pow (/ (- y (get-in e [:pupil :cy])) (* 1.2 phh)) 2))))
                      tone (+ 0.8 (* 0.2 d))
                      f (* dom (patch e x y))
                      r (mix r (long (* mr tone)) f) g (mix g (long (* mg tone)) f) b (mix b (long (* mb tone)) f)
                      s (* dom (slit pupil e x y))]
                  [(mix r 16 s) (mix g 12 s) (mix b 14 s)])))))))))

;; ---------------------------------------------------------------------------
;; Skin: blush and freckles
;; ---------------------------------------------------------------------------

(defn- face-frame
  "The two eyes' centres, left to right, and the distance between them."
  [shapes]
  (let [[a b] (ordered-eyes shapes)]
    (when (and a b)
      (let [ax (get-in a [:iris :cx]) ay (get-in a [:iris :cy])
            bx (get-in b [:iris :cx]) by (get-in b [:iris :cy])]
        {:a [ax ay] :b [bx by] :sep (Math/abs (double (- bx ax)))}))))

(def freckle-colour [150 92 62])

(defn- new-doubles [n]
  #?(:clj (double-array n) :cljs (js/Float64Array. n)))

#?(:clj  (defn- ag ^double [^doubles a ^long i] (aget a i))
   :cljs (defn- ag [a i] (aget a i)))
#?(:clj  (defn- as! [^doubles a ^long i ^double v] (aset a i v))
   :cljs (defn- as! [a i v] (aset a i v)))

(defn marks
  "How much blush and how much freckle each pixel takes, 0..1:
   {:blush doubles :freckles doubles}, either nil when off. `skin` is how
   much of each pixel is skin that shows (0..1, `w` x `h`), `shapes` the
   placed eyes in the same pixels, `nose` the nose art's box [x0 y0 x1 y1]
   in them (`alpha-box`), or nil to estimate it. nil when there are no two
   eyes to place from."
  ([settings shapes skin w h] (marks settings shapes skin w h nil))
  ([{:keys [blush freckles freckle-place freckle-strength]} shapes skin w h nose]
  (when-let [{:keys [a b sep]} (face-frame shapes)]
    (let [w (long w) h (long h) sep (double sep)
          blush-map
          (when (pos? blush)
            (let [out (new-doubles (* w h))
                  ;; under each eye and a little out toward the ear; the far
                  ;; cheek a little narrower
                  cheeks [[(- (first a) (* 0.12 sep)) (+ (second a) (* 0.62 sep)) 0.9]
                          [(+ (first b) (* 0.10 sep)) (+ (second b) (* 0.62 sep)) 1.0]]
                  strength (* 0.7 (double blush))
                  ry (* 0.24 sep)
                  x0 (max 0 (long (- (ffirst cheeks) (* 1.2 sep)))) x1 (min (dec w) (long (+ (first (second cheeks)) (* 1.2 sep))))
                  y0 (max 0 (long (- (second (first cheeks)) (* 3 ry)))) y1 (min (dec h) (long (+ (second (second cheeks)) (* 3 ry))))]
              (doseq [y (range y0 (inc y1)) x (range x0 (inc x1))]
                (let [i (+ x (* y w)) sv (ag skin i)]
                  (when (pos? sv)
                    (let [cheek (fn [[cx cy s]]
                                  (let [dx (/ (- x cx) (* 0.40 sep s)) dy (/ (- y cy) ry)]
                                    (Math/exp (* -2.2 (+ (* dx dx) (* dy dy))))))
                          k (* strength sv (max (cheek (first cheeks)) (cheek (second cheeks))))]
                      (when (> k 0.002) (as! out i k))))))
              out))
          freckle-map
          (when (pos? freckles)
            (let [out (new-doubles (* w h))
                  [ax ay] a [bx by] b
                  ;; the nose, measured from its art when given, else
                  ;; estimated between the eyes
                  [nx0 ny0 nx1 ny1] (or nose [(- bx (* 0.55 sep)) (+ (max ay by) (* 0.25 sep))
                                              (+ bx (* 0.05 sep)) (+ (max ay by) (* 0.95 sep))])
                  nw (max 1.0 (- nx1 nx0)) nh (max 1.0 (- ny1 ny0))
                  ;; three groups, each [cx cy rx ry share]: the near cheek
                  ;; under the left eye, the bridge of the nose, and the far
                  ;; cheek under the right eye, narrower as the face turns
                  ;; away. A single oval between the eyes put nearly all of
                  ;; them on the near cheek.
                  groups [[ax (+ ay (* 0.55 sep)) (* 0.42 sep) (* 0.17 sep) 0.45]
                          [(/ (+ nx0 nx1) 2.0) (+ ny0 (* 0.32 nh)) (* 0.55 nw) (* 0.2 nh) 0.25]
                          [(+ bx (* 0.12 sep)) (+ by (* 0.52 sep)) (* 0.26 sep) (* 0.15 sep) 0.3]]
                  ;; the first layout: one oval across the face between the eyes
                  ox (/ (+ ax bx) 2.0) oy (+ (/ (+ ay by) 2.0) (* 0.42 sep))
                  place (double (or freckle-place 0.5))
                  strength (double (or freckle-strength 0.5))
                  n (long (* 150 (double freckles)))
                  scale (* (/ h 1500.0) (+ 0.75 (* 0.5 strength)))
                  darkness (* 0.6 (+ 0.4 (* 1.2 strength)))]
              (dotimes [j n]
                (let [h0 (fx/hash01 j 7 5) h1 (fx/hash01 j 11 5) h2 (fx/hash01 j 23 5) h3 (fx/hash01 j 37 5)
                      [gx gy grx gry] (cond (< h0 (nth (nth groups 0) 4)) (nth groups 0)
                                            (< h0 (+ (nth (nth groups 0) 4) (nth (nth groups 1) 4))) (nth groups 1)
                                            :else (nth groups 2))
                      ;; spread evenly over the oval, thinning to its edge. Each
                      ;; freckle has a place in both layouts and Placement
                      ;; slides it between them, so they move rather than jump
                      ang (* 2.0 Math/PI h1) rr (Math/sqrt h2)
                      c (Math/cos ang) sn (Math/sin ang)
                      fx* (+ (* (- 1.0 place) (+ ox (* rr c 0.95 sep))) (* place (+ gx (* rr c grx))))
                      fy* (+ (* (- 1.0 place) (+ oy (* rr sn 0.30 sep))) (* place (+ gy (* rr sn gry))))
                      r (* scale (+ 1.6 (* 1.8 h3)))
                      k (* (- 1.0 (* 0.6 rr)) (+ 0.45 (* 0.55 h3)))]
                  (doseq [yy (range (long (- fy* r 2)) (long (+ fy* r 3)))
                          xx (range (long (- fx* r 2)) (long (+ fx* r 3)))]
                    (when (and (< -1 xx w) (< -1 yy h))
                      (let [d (Math/sqrt (+ (Math/pow (- xx fx*) 2) (Math/pow (- yy fy*) 2)))
                            c (* k (clamp01 (- (+ r 0.5) d)))
                            i (+ xx (* yy w))]
                        (when (> c (ag out i)) (as! out i c)))))))
              (dotimes [i (* w h)]
                (let [v (ag out i)] (when (pos? v) (as! out i (min 1.0 (* darkness v (ag skin i)))))))
              out))]
      (when (or blush-map freckle-map)
        {:blush blush-map :freckles freckle-map})))))

(defn alpha-box
  "[x0 y0 x1 y1] of where `alpha` (0..255 doubles, `w` x `h`) is solid, or
   nil when it is empty: where a piece of art actually sits in the frame."
  [alpha w h]
  (let [w (long w) n (* w (long h))]
    (loop [i 0 x0 w y0 h x1 -1 y1 -1]
      (if (= i n)
        (when (>= x1 0) [x0 y0 x1 y1])
        (if (> (ag alpha i) 100.0)
          (let [x (mod i w) y (quot i w)]
            (recur (inc i) (min x0 x) (min y0 y) (max x1 x) (max y1 y)))
          (recur (inc i) x0 y0 x1 y1))))))

(defn mark-factors
  "What a pixel's r g b are multiplied by for blush `kb` in `blush-rgb` and
   freckle `kf`: [fr fg fb], 1 where there is neither."
  [kb kf blush-rgb]
  (let [f (fn [c k ch] (+ 1.0 (* k (- (/ (double (nth c ch)) 255.0) 1.0))))]
    [(* (f blush-rgb kb 0) (f freckle-colour kf 0))
     (* (f blush-rgb kb 1) (f freckle-colour kf 1))
     (* (f blush-rgb kb 2) (f freckle-colour kf 2))]))

(defn shade-map
  "The soft shade the hair over the face throws onto the skin, 0..1 per
   pixel: `hair` (0..255, the scalp, front hair and bangs) blurred wide, on
   skin that shows (`skin`, 0..1). Not a cast shadow -- that is the narrow
   band under an overhanging fringe -- but the general dimness near hair.
   nil when off."
  [{:keys [shade]} skin hair w h]
  (when (pos? (or shade 0.0))
    (let [n (* w h)
          ao (colorize/blur hair w h (max 1 (long (Math/round (* 40.0 (/ h 1500.0))))))
          out (new-doubles n)
          k (* 0.24 (/ (double shade) 0.5))]
      (dotimes [i n]
        (let [v (* k (ag skin i) (/ (ag ao i) 255.0))]
          (when (> v 0.002) (as! out i (min 0.6 v)))))
      out)))
