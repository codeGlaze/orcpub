(ns orcpub.dnd.e5.portrait-light-test
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [orcpub.dnd.e5.portrait-light :as light]))

(defn- arr [n f] (let [a #?(:clj (double-array n) :cljs (js/Float64Array. n))] (dotimes [i n] (aset a i (double (f i)))) a))

(deftest settings
  (is (= {:mood nil :style :cast :from :left :glow nil :glow-strength 0.6} (light/light-settings {})))
  (is (= :right (:from (light/light-settings {:light {:mood :moon}}))) "a mood comes from its own side")
  (is (= :below (:from (light/light-settings {:light {:mood "torch" :from "below"}}))) "unless one is chosen")
  (is (nil? (:mood (light/light-settings {:light {:mood :disco}}))))
  (is (not (light/lit? (light/light-settings {}))))
  (is (nil? (light/light-maps (light/light-settings {}) {} 10 10)) "no light, no overlays"))

(deftest a-mood-lights-one-side-and-shades-the-other
  (let [w 100 h 100 n (* w h)
        masks {:alpha (arr n (constantly 255)) :ink (arr n (constantly 1.0))}
        lum (fn [maps x] (reduce + (light/apply-pixel maps (+ x (* 50 w)) 128.0 128.0 128.0)))]
    (doseq [style light/styles]
      (let [maps (light/light-maps (light/light-settings {:light {:mood :torch :style style :from :left}}) masks w h)]
        (is (> (lum maps 10) (lum maps 90)) (str style ": brighter toward the light"))))
    (testing "the rim lights only the outer edge of the whole picture"
      ;; at a real portrait's height, where the softening has room: a block of
      ;; art inside an empty frame, with a seam of ink down its middle
      (let [w 300 h 1500 n (* w h)
            inside (fn [i] (let [x (mod i w) y (quot i w)] (and (< 60 x 240) (< 300 y 1200))))
            masks {:alpha (arr n #(if (inside %) 255 0)) :ink (arr n #(if (= 150 (mod % w)) 0.0 1.0))}
            rim (light/light-maps (light/light-settings {:light {:mood :moon :style :rim :from :left}}) masks w h)
            cast (light/light-maps (light/light-settings {:light {:mood :moon :style :cast :from :left}}) masks w h)
            extra (fn [x] (- (aget (first (:add rim)) (+ x (* 750 w))) (aget (first (:add cast)) (+ x (* 750 w)))))]
        (is (> (extra 64) 20) "the outer edge facing the light")
        (is (< (Math/abs (double (extra 145))) 1) "not the inside of the picture, seam or no seam")
        (is (< (Math/abs (double (extra 236))) 1) "nor the edge facing away")))))

(deftest glowing-eyes
  (let [w 300 h 1500 n (* w h)
        iris (arr n (fn [i] (let [x (mod i w) y (quot i w)] (if (< (+ (* (- x 150) (- x 150)) (* (- y 750) (- y 750))) 625) 255 0))))
        hair (arr n (fn [i] (if (< (mod i w) 60) 255 0)))
        maps (light/light-maps (light/light-settings {:light {:glow "#ff8a3a"}}) {:alpha (arr n (constantly 255)) :iris iris :hair-cover hair} w h)
        r (fn [x y] (aget (first (:add maps)) (+ x (* y w))))]
    (is (nil? (:mul maps)) "a glow only lightens")
    (is (> (r 150 750) 150) "the iris glows")
    (is (> (r 150 800) 10) "and so does the skin round it")
    (is (< (r 40 750) 0.5) "but not the hair over the face")))
