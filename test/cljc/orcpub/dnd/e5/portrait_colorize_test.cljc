(ns orcpub.dnd.e5.portrait-colorize-test
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [orcpub.dnd.e5.portrait-colorize :as c]
            [orcpub.dnd.e5.portrait-assets :as pa]))

(deftest the-ramp
  (let [blue [40 90 200]
        at (fn [grey gamma] (apply c/colorize-rgb grey grey grey (conj blue gamma 1.0)))]
    (testing "the darkest ink keeps the floor's share of the colour, not black"
      (is (= (mapv #(int (* % c/floor)) blue) (at 0 1.0))))
    (testing "the middle of the ramp is the colour itself"
      (is (= blue (at 127.5 1.0))))
    (testing "the highlight stays a highlight"
      (is (= [255 255 255] (at 255 1.0))))
    (testing "a gamma below 1 lifts the dark body of an iris toward the colour"
      (is (> (first (at 60 0.5)) (first (at 60 1.0)))))
    (testing "no coverage leaves the drawing alone; that is what fades the edge"
      (is (= [10 20 30] (c/colorize-rgb 10 20 30 40 90 200 0.5 0.0))))))

(deftest hex
  (is (= [58 123 213] (c/hex->rgb "#3a7bd5")))
  (is (nil? (c/hex->rgb "blue"))))

(deftest the-region-follows-the-asset-into-the-frame
  (let [asset {:asset/pupil 0.5
               :asset/iris [{:cx 0.5 :cy 0.5 :rx 0.1 :ry 0.05 :rot 0.3 :px 0.2
                             :lid {:x0 0.4 :y0 0.45 :mx 0.5 :my 0.4 :x1 0.6 :y1 0.45}}]}
        [{:keys [iris pupil lid]}] (c/iris-shapes asset [10 20 200 400])]
    (is (= {:cx 110.0 :cy 220.0 :rx 20.0 :ry 20.0 :rot 0.3} iris) "radii scale by width and height separately")
    (is (= 10.0 (:rx pupil)) "the pupil is its fraction of the oval")
    (is (< 110.0 (:cx pupil)) "and can sit off centre")
    (is (= 90.0 (:x0 lid)))
    (is (neg? (:top lid)) "everything above the lid runs off the top of the frame")))

(deftest every-eye-style-has-its-placed-irises
  (doseq [a (pa/assets-for-layer :eyes)]
    (is (= 2 (count (:asset/iris a))) (str (:asset/id a) " has both irises placed"))
    (is (every? :lid (:asset/iris a)) (str (:asset/id a) " has both lid curves"))
    (is (= :colorize (pa/render-mode :eyes a)))))

(deftest only-the-lips-are-coloured-in-the-mouth
  (let [by-id (into {} (map (juxt :asset/id identity)) (pa/assets-for-layer :mouth))]
    (is (= :colorize (pa/render-mode :mouth (by-id :l8-mouth-02))) "lips")
    (is (= :as-drawn (pa/render-mode :mouth (by-id :l8-mouth-01))) "a closed line")
    (is (= :as-drawn (pa/render-mode :mouth (by-id :l8-mouth-03))) "teeth stay white")))

(deftest brightness-moves-the-gamma-around-the-settled-look
  (let [eyes (first (pa/assets-for-layer :eyes))
        at (fn [depth] (pa/effective-gamma {:tweaks {:eyes {:depth depth}}} :eyes eyes))]
    (is (= (pa/tint-gamma :eyes eyes) (at 0) (pa/effective-gamma {} :eyes eyes))
        "0, or no tweak at all, is exactly the settled look")
    (is (< (at 3) (at 1) (at 0) (at -1) (at -3)) "up lifts the colour, down deepens it")
    (is (= (at 3) (at 99)) "clamped to the slider's range")
    (is (= (at 0) (pa/effective-gamma {:tweaks {:eyes {:depth "junk"}}} :eyes eyes))
        "a malformed stored value reads as 0")
    (is (= [:eyes] (pa/tweaked-layers-in-slot {:tweaks {:eyes {:depth 2}}} :eyes))
        "a brightness tweak counts as a tweak, for the badge and the clear button")))
