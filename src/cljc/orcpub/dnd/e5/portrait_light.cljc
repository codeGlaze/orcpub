(ns orcpub.dnd.e5.portrait-light
  "Light over the finished portrait, shared by every renderer: glowing eyes
   and mood light.

   Both are worked out as two overlays over the whole picture, applied in
   order: MULTIPLY by `:mul`, then SCREEN with `:add`. That is exactly what a
   canvas does with two layers set to mix-blend multiply and screen, and what
   the share card does per pixel, so the three renderers agree. Screen is
   associative, so several lights fold into one :add.

   Renderers supply masks of the whole picture, all `w` x `h`:
   - :alpha       how opaque the finished portrait is, 0..255
   - :ink         how light the art itself is, 0..1 (the drawing, uncoloured),
                  so light lands on the fill and the linework stays ink
   - :iris        the placed iris region, feathered, 0..255
   - :hair-cover  the hair over the face (scalp, front hair, bangs), 0..255

   Mood light comes in three styles, all chosen on the real art:
   - :edge  a lit edge along the side facing the light, with a light cast;
   - :cast  the lit side warmed or cooled and the far side shaded, no edge;
   - :rim   :cast plus a rim on the OUTER edge of the whole portrait facing
            the light. Worked out per piece, it lit the inside edges too and
            drew the head's outline and a hairline across the face."
  (:require [orcpub.dnd.e5.portrait-colorize :as colorize]))

(def moods
  "Each mood's light colour and the side it comes from unless one is chosen."
  {:torch  {:colour [255 176 96]  :from :left}
   :moon   {:colour [168 200 255] :from :right}
   :arcane {:colour [192 128 255] :from :below}})

(def styles [:edge :cast :rim])

(def sides
  "Which way the light comes from, as [x y] pointing toward it."
  {:left [-0.8 -0.6] :right [0.85 -0.5] :above [0.0 -1.0] :below [0.3 0.95]})

(def glow-colours
  "Fiendish, celestial, undead, fey, arcane."
  ["#ff8a3a" "#fff0b0" "#7af6ff" "#8cff9a" "#c890ff"])

(defn- clamp01 ^double [^double x] (if (< x 0.0) 0.0 (if (> x 1.0) 1.0 x)))

(defn- kw [v options]
  (let [k (when (or (keyword? v) (string? v)) (keyword (name v)))]
    (when (contains? options k) k)))

(defn light-settings
  "The portrait's light, with anything unknown or out of range dropped, so a
   hand-edited portrait cannot break a render."
  [portrait]
  (let [o (let [v (:light portrait)] (if (map? v) v {}))
        mood (kw (:mood o) moods)]
    {:mood mood
     :style (or (kw (:style o) (set styles)) :cast)
     :from (or (kw (:from o) sides) (get-in moods [mood :from]) :left)
     :glow (let [g (:glow o)] (when (and (string? g) (colorize/hex->rgb g)) g))
     :glow-strength (let [v (:glow-strength o)] (if (number? v) (clamp01 (double v)) 0.6))}))

(defn lit?
  "Whether there is any light to draw."
  [{:keys [mood glow]}]
  (boolean (or mood glow)))

(defn- new-doubles [n]
  #?(:clj (double-array n) :cljs (js/Float64Array. n)))

#?(:clj  (defn- ag ^double [^doubles a ^long i] (aget a i))
   :cljs (defn- ag [a i] (aget a i)))
#?(:clj  (defn- as! [^doubles a ^long i ^double v] (aset a i v))
   :cljs (defn- as! [a i v] (aset a i v)))

(defn- smooth ^double [^double e0 ^double e1 ^double x]
  (let [t (clamp01 (/ (- x e0) (- e1 e0)))] (* t t (- 3.0 (* 2.0 t)))))

(defn- factor
  "What a channel is multiplied by to multiply toward colour channel `c` by `k`."
  ^double [^double c ^double k]
  (+ 1.0 (* k (- (/ c 255.0) 1.0))))

(defn- scr ^double [^double a ^double b]
  (- (+ a b) (/ (* a b) 255.0)))

(def ^:private shade-colour [60.0 70.0 105.0])

(defn light-maps
  "The two overlays for `settings` (`light-settings`) over a `w` x `h`
   picture with `masks` (see the namespace doc): {:mul [r g b] :add [r g b]},
   each channel a doubles array; :mul is nil when nothing darkens. nil when
   there is no light."
  [{:keys [mood style from glow glow-strength] :as settings} {:keys [alpha ink iris hair-cover]} w h]
  (when (lit? settings)
    (let [w (long w) h (long h) n (* w h)
          s (/ (double h) 1500.0)
          radius (fn [r] (max 1 (long (Math/round (* r s)))))
          mul (when mood [(new-doubles n) (new-doubles n) (new-doubles n)])
          add [(new-doubles n) (new-doubles n) (new-doubles n)]]
      (when mul (doseq [a mul] (dotimes [i n] (as! a i 1.0))))
      ;; ---- mood ----
      (when mood
        (let [[cr cg cb] (mapv double (get-in moods [mood :colour]))
              [lx ly] (sides from)
              lx (double lx) ly (double ly)
              ;; 1 on the side the light comes from, 0 on the far side
              along (fn ^double [^long i]
                      (let [x (double (mod i w)) y (double (quot i w))]
                        (clamp01 (+ 0.5 (* 0.5 (+ (* lx (/ (- x (/ w 2.0)) (* 0.375 w)))
                                                  (* ly (/ (- y (/ h 2.0)) (* 0.433 h)))))))))
              [mr mg mb] mul [ar ah ab] add
              edge-rim (when (= style :edge)
                         ;; the picture's alpha slid toward the light and
                         ;; blurred: where it is thin, an edge faces the light
                         (let [d (* 10.0 s) shifted (new-doubles n)]
                           (dotimes [i n]
                             (let [x (+ (mod i w) (long (Math/round (* d lx))))
                                   y (+ (quot i w) (long (Math/round (* d ly))))]
                               (when (and (< -1 x w) (< -1 y h))
                                 (as! shifted i (ag alpha (+ x (* y w)))))))
                           (colorize/blur shifted w h (radius 6))))
              outer (when (= style :rim)
                      ;; the outline of the WHOLE portrait, softened, and
                      ;; which way it faces
                      (colorize/blur alpha w h (radius 14)))]
          (dotimes [i n]
            (when (> (ag alpha i) 0.0)
              (let [t (along i)
                    fill (if ink (smooth 0.3 0.7 (ag ink i)) 1.0)]
                (case style
                  :edge
                  (let [cast 0.25
                        k-lit (* cast t) k-shade (* cast 0.6 (- 1.0 t))
                        rim (* 0.9 (/ (ag alpha i) 255.0) (clamp01 (- 1.0 (/ (ag edge-rim i) 255.0))))]
                    (as! mr i (* (factor cr k-lit) (factor (shade-colour 0) k-shade)))
                    (as! mg i (* (factor cg k-lit) (factor (shade-colour 1) k-shade)))
                    (as! mb i (* (factor cb k-lit) (factor (shade-colour 2) k-shade)))
                    (let [k (min 1.0 rim)]
                      (as! ar i (* cr k)) (as! ah i (* cg k)) (as! ab i (* cb k))))
                  ;; :cast and :rim
                  (let [k-shade (* 0.32 (- 1.0 t) fill)
                        k-lit (* 0.3 t fill)]
                    (as! mr i (* (+ 0.88 (* 0.12 (/ cr 255.0))) (factor (shade-colour 0) k-shade)))
                    (as! mg i (* (+ 0.88 (* 0.12 (/ cg 255.0))) (factor (shade-colour 1) k-shade)))
                    (as! mb i (* (+ 0.88 (* 0.12 (/ cb 255.0))) (factor (shade-colour 2) k-shade)))
                    (let [k (if (and outer (> (ag alpha i) 128.0))
                              (let [x (mod i w) y (quot i w)
                                    at (fn ^double [^long xx ^long yy]
                                         (ag outer (+ (max 0 (min (dec w) xx)) (* (max 0 (min (dec h) yy)) w))))
                                    nx (- (- (at (inc x) y) (at (dec x) y)))
                                    ny (- (- (at x (inc y)) (at x (dec y))))
                                    nl (Math/sqrt (+ (* nx nx) (* ny ny)))
                                    edge (clamp01 (- 1.0 (/ (ag outer i) 255.0)))
                                    facing (if (< nl 1e-6) 0.0 (max 0.0 (/ (+ (* nx lx) (* ny ly)) nl)))
                                    rim (min 0.9 (* 1.1 fill facing (Math/pow (* 2.0 edge) 1.5)))]
                                (scr (* 255.0 k-lit) (* 255.0 rim)))
                              (* 255.0 k-lit))
                          k (/ k 255.0)]
                      (as! ar i (* cr k)) (as! ah i (* cg k)) (as! ab i (* cb k))))))))))
      ;; ---- glowing eyes, over the mood ----
      (when (and glow iris)
        (let [[cr cg cb] (mapv double (colorize/hex->rgb glow))
              [hr hg hb] (mapv #(+ % (* 0.55 (- 255.0 %))) [cr cg cb])
              near (colorize/blur iris w h (radius 12))
              far (colorize/blur iris w h (radius 44))
              strength (/ (double glow-strength) 0.6)
              [ar ah ab] add]
          (dotimes [i n]
            (let [core (/ (ag iris i) 255.0)
                  halo (* strength
                          (+ (* 2.2 (/ (ag near i) 255.0)) (* 2.6 (/ (ag far i) 255.0)))
                          ;; never onto the hair over the face
                          (if hair-cover (- 1.0 (/ (ag hair-cover i) 255.0)) 1.0))]
              (when (or (pos? core) (> halo 0.01))
                (let [kc (min 1.0 (* 0.55 core strength)) kh (min 0.9 halo)]
                  (as! ar i (scr (ag ar i) (scr (* hr kc) (* cr kh))))
                  (as! ah i (scr (ag ah i) (scr (* hg kc) (* cg kh))))
                  (as! ab i (scr (ag ab i) (scr (* hb kc) (* cb kh))))))))))
      {:mul mul :add add})))

(defn apply-pixel
  "One pixel [r g b] through the overlays at index `i`: multiply, then screen."
  [{:keys [mul add]} i r g b]
  (let [[mr mg mb] mul [ar ah ab] add
        m (fn [c a] (if a (* c (ag a i)) c))]
    [(scr (m r mr) (ag ar i)) (scr (m g mg) (ag ah i)) (scr (m b mb) (ag ab i))]))
