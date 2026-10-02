(ns orcpub.dnd.e5.portrait-face-test
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [orcpub.dnd.e5.portrait-face :as face]))

(deftest settings-default-and-clamp
  (is (= {:pupil :round :second-eye nil :blush 0.0 :blush-colour "#e0606a" :freckles 0.0
          :freckle-place 0.5 :freckle-strength 0.5}
         (face/face-settings {})))
  (is (= {:pupil :snake :second-eye "#3d5c8f" :blush 1.0 :blush-colour "#e0606a" :freckles 0.0
          :freckle-place 0.5 :freckle-strength 0.5}
         (face/face-settings {:face {:pupil "snake" :second-eye "#3d5c8f" :blush 4 :blush-colour "pink" :freckles -1}}))
      "strings read as keywords, numbers clamp, junk falls back")
  (is (= :round (:pupil (face/face-settings {:face {:pupil :cat}}))) "only pupils we draw")
  (is (not (face/marks? (face/face-settings {}))))
  (is (face/marks? (face/face-settings {:face {:freckles 0.2}}))))

(defn- eye [cx rot]
  ;; an iris 20 wide, 30 tall on screen, pupil 45% of it, no lid
  {:iris {:cx cx :cy 50.0 :rx (if (zero? rot) 10.0 15.0) :ry (if (zero? rot) 15.0 10.0) :rot rot}
   :pupil {:cx cx :cy 50.0 :rx (if (zero? rot) 4.5 6.75) :ry (if (zero? rot) 6.75 4.5) :rot rot}
   :lid nil})

(def grey [128 128 128])

(deftest each-eye-takes-its-colour
  (let [f (face/eye-pixel-fn [(eye 120.0 0) (eye 40.0 0)] [200 40 40] [40 40 200] 1.0 :round)
        [lr _ lb] (f 40 45 grey 1.0)
        [rr _ rb] (f 120 45 grey 1.0)]
    (is (> lr lb) "the left eye takes the Eyes colour, wherever it was listed")
    (is (> rb rr) "the right eye takes the second colour"))
  (let [f (face/eye-pixel-fn [(eye 40.0 0) (eye 120.0 0)] [200 40 40] nil 1.0 :round)]
    (is (= (f 40 45 grey 1.0) (f 120 45 grey 1.0)) "no second colour: both alike")))

(deftest light-comes-from-below
  (let [f (face/eye-pixel-fn [(eye 40.0 0)] [40 120 80] nil 1.0 :round)
        lum (fn [[r g b]] (+ r g b))]
    (is (> (lum (f 40 62 grey 1.0)) (lum (f 40 50 grey 1.0))) "the bottom of the iris is lifted")
    (is (= (f 40 50 grey 1.0) (f 40 40 grey 1.0)) "nothing above the centre line")
    (is (= grey (f 200 62 grey 0.0)) "and nothing outside the iris")))

(deftest pupils-stand-upright-whatever-the-oval-rotation
  (doseq [rot [0 (/ Math/PI 2)]]
    (let [dark? (fn [[r g b]] (< (+ r g b) 100))
          snake (face/eye-pixel-fn [(eye 40.0 rot)] [40 120 80] nil 1.0 :snake)
          goat (face/eye-pixel-fn [(eye 40.0 rot)] [40 120 80] nil 1.0 :goat)
          drawn [10 10 10]]
      (testing (str "rot " rot)
        (is (dark? (snake 40 38 drawn 0.0)) "the snake slit runs up the iris")
        (is (not (dark? (snake 45 50 drawn 0.0))) "and is narrow: the drawn round pupil beside it is painted over")
        (is (dark? (goat 48 50 drawn 0.0)) "the goat bar runs across")
        (is (not (dark? (goat 40 44 drawn 0.0))) "and is short top to bottom")))))

(deftest round-pupils-are-left-as-drawn
  (let [f (face/eye-pixel-fn [(eye 40.0 0)] [40 120 80] nil 1.0 :round)]
    (is (= [10 10 10] (f 40 50 [10 10 10] 0.0)))))

(deftest blush-and-freckles-land-on-showing-skin
  (let [w 300 h 300
        shapes [(assoc-in (eye 110.0 0) [:iris :cy] 120.0) (assoc-in (eye 190.0 0) [:iris :cy] 120.0)]
        ;; skin everywhere except a strip of hair down the left
        skin (let [a #?(:clj (double-array (* w h)) :cljs (js/Float64Array. (* w h)))]
               (dotimes [i (* w h)] (aset a i (if (< (mod i w) 60) 0.0 1.0)))
               a)
        m (face/marks (face/face-settings {:face {:blush 0.6 :freckles 0.8}}) shapes skin w h)
        at (fn [k x y] (aget (get m k) (+ x (* y w))))]
    (is (> (at :blush 105 170) 0.2) "under the left eye")
    (is (zero? (at :blush 150 20)) "not on the forehead")
    (is (zero? (at :blush 50 170)) "not where the hair covers")
    (is (some pos? (for [y (range 140 190) x (range 110 190)] (at :freckles x y))) "freckles across the nose")
    (testing "both cheeks and the nose take freckles"
      (let [m2 (face/marks (face/face-settings {:face {:freckles 1.0 :freckle-place 1.0}}) shapes skin w h [140 140 170 200])
            any-in (fn [x0 x1 y0 y1] (some pos? (for [y (range y0 y1) x (range x0 x1)] (aget (:freckles m2) (+ x (* y w))))))]
        (is (any-in 75 140 150 200) "under the left eye")
        (is (any-in 175 230 150 200) "under the right eye")
        (is (any-in 140 170 140 175) "across the bridge of the nose")
        (is (= [140 140 170 200] (face/alpha-box (let [a (#?(:clj double-array :cljs (fn [n] (js/Float64Array. n))) (* w h))]
                                                     (doseq [y (range 140 201) x (range 140 171)] (aset a (+ x (* y w)) 255.0)) a) w h)))))
    (is (every? zero? (for [y (range 0 300 7) x (range 0 60 3)] (at :freckles x y))))
    (is (nil? (face/marks (face/face-settings {:face {:blush 0.5}}) [(first shapes)] skin w h))
        "one eye is not enough to place a face from")
    (is (= [1.0 1.0 1.0] (face/mark-factors 0.0 0.0 [224 96 106])))
    (let [[r g b] (face/mark-factors 0.5 0.0 [224 96 106])] (is (and (> r g) (< g 1.0))) "blush warms")))

(deftest freckle-controls
  (let [w 300 h 300
        shapes [(assoc-in (eye 110.0 0) [:iris :cy] 120.0) (assoc-in (eye 190.0 0) [:iris :cy] 120.0)]
        skin (let [a #?(:clj (double-array (* w h)) :cljs (js/Float64Array. (* w h)))] (dotimes [i (* w h)] (aset a i 1.0)) a)
        f (fn [o] (:freckles (face/marks (face/face-settings {:face (merge {:freckles 0.8} o)}) shapes skin w h [140 140 170 200])))
        total (fn [a] (reduce + (seq a)))
        darkest (fn [a] (reduce max (seq a)))
        right-of-nose (fn [a] (reduce + (for [y (range 140 200) x (range 172 240)] (aget a (+ x (* y w))))))]
    (is (< (total (f {:freckles 0.3})) (total (f {:freckles 0.9}))) "Freckles: more of them")
    (is (< (darkest (f {:freckle-strength 0.1})) (darkest (f {:freckle-strength 0.9}))) "Prominence: darker")
    (is (< (total (f {:freckle-strength 0.1})) (total (f {:freckle-strength 0.9}))) "and larger")
    (is (< (right-of-nose (f {:freckle-place 0.0})) (right-of-nose (f {:freckle-place 1.0})))
        "Placement: toward 1 more of them reach the far cheek")))
