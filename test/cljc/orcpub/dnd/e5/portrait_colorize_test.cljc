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

(defn- disc
  "A w x w coverage array holding a hard-edged disc of radius r."
  [w r]
  (let [a #?(:clj (double-array (* w w)) :cljs (js/Float64Array. (* w w)))
        c (/ w 2.0)]
    (dotimes [i (* w w)]
      (let [x (mod i w) y (quot i w)]
        (aset a i (if (<= (+ (Math/pow (- x c) 2) (Math/pow (- y c) 2)) (* r r)) 255.0 0.0))))
    a))

(deftest the-feather-fades-inward-only
  (let [w 60 src (disc w 20) out (c/feather src w w 6)
        at (fn [a x y] (aget a (+ x (* y w))))]
    (is (every? true? (map #(<= (aget out %) (aget src %)) (range (* w w))))
        "never covers more than the shape did: no colour spills past the ring")
    (is (== 255.0 (at out 30 30)) "the middle is untouched")
    (is (< 0 (at out 30 11) 255) "just inside the edge, part coloured: the fade")
    (is (zero? (at out 30 5)) "outside stays uncoloured")
    (is (identical? src (c/feather src w w 0)) "radius 0 is a no-op")))

(deftest the-feather-scales-with-the-drawing
  (is (= 6 (c/feather-radius 1500)) "6px at 1500px tall: the setting chosen on the real art")
  (is (= 3 (c/feather-radius 750)) "half that on the share card's 750")
  (is (<= 2 (c/feather-radius 638) 3) "and on the pack's own 638"))

(deftest the-developer-alternative-only-moves-one-iris
  (let [eyes (first (pa/assets-for-layer :eyes))
        alt (pa/with-iris-alternative eyes)]
    (is (= 0.72 (get-in alt [:asset/iris 1 :cx])))
    (is (= (get-in eyes [:asset/iris 0]) (get-in alt [:asset/iris 0])) "the near iris is left alone")
    (is (= (second (pa/assets-for-layer :eyes)) (pa/with-iris-alternative (second (pa/assets-for-layer :eyes))))
        "other eye styles are untouched")
    (is (= 0.72166 (get-in eyes [:asset/iris 1 :cx])) "the registry keeps the artist's placement")))
