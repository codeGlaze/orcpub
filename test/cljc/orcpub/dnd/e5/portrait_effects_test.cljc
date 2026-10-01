(ns orcpub.dnd.e5.portrait-effects-test
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [orcpub.dnd.e5.portrait-effects :as fx]
            [orcpub.dnd.e5.portrait-assets :as pa]))

(deftest settings-default-and-clamp
  (is (= {:tip nil :start 0.2 :falloff 0.6 :depth 0.3 :clumps 0.0 :light 0.0 :under 0.0 :angle nil}
         (fx/ombre-settings {})))
  (is (= {:tip "#2f7f9a" :start 1.0 :falloff 0.0 :depth 0.3 :clumps 0.0 :light 1.0 :under 0.0 :angle 90.0}
         (fx/ombre-settings {:ombre {:tip "#2f7f9a" :start 7 :falloff -1 :depth "x" :angle 450 :light 3}}))
      "out-of-range numbers are pulled back in, junk falls back to the default")
  (is (nil? (:tip (fx/ombre-settings {:ombre {:tip "teal"}}))) "only #rrggbb tips"))

(deftest the-colour-runs-root-to-tip
  (let [s (assoc (fx/ombre-settings {}) :depth 0.0)
        white [255 255 255]
        brown [92 58 30] teal [47 127 154]]
    (is (= brown (fx/ombre-rgb 255 255 255 brown teal 0.0 s)) "white art at the root takes the root colour")
    (is (= teal (fx/ombre-rgb 255 255 255 brown teal 1.0 s)) "and the tip colour at the tip")
    (is (= [0 0 0] (fx/ombre-rgb 0 0 0 brown teal 0.7 s)) "linework stays ink")
    (is (= brown (fx/ombre-rgb 255 255 255 brown brown 0.9 s))
        "with no second colour and no depth it is today's flat multiply")
    (testing "depth darkens the roots and lifts the ends"
      (let [d (assoc s :depth 0.5)]
        (is (< (first (fx/ombre-rgb 200 200 200 white white 0.0 d))
               (first (fx/ombre-rgb 200 200 200 white white 1.0 d))))))
    (testing "falloff 0 is a hard dip-dye line at the start point"
      (let [hard (assoc s :start 0.5 :falloff 0.0)]
        (is (= brown (fx/ombre-rgb 255 255 255 brown teal 0.49 hard)))
        (is (= teal (fx/ombre-rgb 255 255 255 brown teal 0.52 hard)))))))

(deftest position-down-from-the-crown-line-or-along-an-angle
  (let [w 100 h 100
        ;; a strand running from (50,10) down to (50,90): a column of pixels
        alpha (fn [i] (if (and (= 50 (mod i w)) (<= 10 (quot i w) 90)) 255 0))
        crown [50.0 10.0]
        roots (fx/gradient-frame alpha w h crown nil)
        down (fx/gradient-frame alpha w h crown 0)]
    (is (= :arc (:kind roots)))
    (is (< (fx/position roots 50 12) 0.1) "near the top is root")
    (is (> (fx/position roots 50 88) 0.9) "the far end is tip")
    (is (< (fx/position down 50 12) 0.1))
    (is (> (fx/position down 50 88) 0.9))
    (let [up (fx/gradient-frame alpha w h crown 180)]
      (is (> (fx/position up 50 12) 0.9) "an angle of 180 turns it upside down"))))

(deftest a-fringe-beside-the-crown-still-reaches-its-tips
  (testing "the bug a single crown point had: on a turned head, strands growing
            right beside it never counted as far from the roots"
    (let [w 120 h 120
          ;; a cap across the top (y 10-30), with two strands hanging from it:
          ;; one at x=20, one at x=100, both down to y=80
          alpha (fn [i] (let [x (mod i w) y (quot i w)]
                          (if (or (<= 10 y 30) (and (#{20 100} x) (<= 10 y 80))) 255 0)))
          f (fx/gradient-frame alpha w h [100.0 12.0] nil)]
      (is (> (fx/position f 20 79) 0.9) "the strand far from where the crown point was")
      (is (> (fx/position f 100 79) 0.9) "and the one right beside it, equally"))))

(deftest the-crown-sits-atop-the-head
  (let [w 100 h 150
        ;; a disc for a head, top at y=20
        alpha (fn [i] (let [x (mod i w) y (quot i w)] (if (< (+ (Math/pow (- x 50) 2) (Math/pow (- y 60) 2)) 1600) 255 0)))
        [cx cy] (fx/crown alpha w h)]
    (is (< 45 cx 55) "centred")
    (is (< 20 cy 35) "just below the top")
    (is (nil? (fx/crown (constantly 0) w h)) "no head, no crown")))

(defn- doubles-of [n f]
  (let [a #?(:clj (double-array n) :cljs (js/Float64Array. n))]
    (dotimes [i n] (aset a i (double (f i))))
    a))

(deftest shadows-land-on-skin-only-and-near-the-edge
  (let [w 60 h 120 n (* w h)
        caster (doubles-of n #(if (< (quot % w) 40) 255 0))       ; hair down to y=40
        skin (doubles-of n (constantly 255))                      ; face everywhere
        hair (doubles-of n #(if (< (quot % w) 40) 255 0))
        k (fx/shadow-map caster skin hair w h)
        at (fn [y] (aget k (+ 30 (* y w))))]
    (is (zero? (at 20)) "never on the hair itself")
    (is (pos? (at 41)) "just below the hair's edge, shaded")
    (is (> (at 41) (at 47)) "fading with distance")
    (is (zero? (at 100)) "and gone further down")
    (is (<= (reduce max (for [i (range n)] (aget k i))) fx/shadow-strength) "never more than the chosen strength")
    (let [no-skin (fx/shadow-map caster (doubles-of n (constantly 0)) hair w h)]
      (is (zero? (reduce + (for [i (range n)] (aget no-skin i)))) "nothing where there is no skin"))))

(deftest only-the-overhanging-bangs-cast
  (let [by-id (into {} (map (juxt :asset/id identity)) (pa/assets-for-layer :bangs))]
    (is (fx/casts-shadow? (by-id :l9-bangs-02)))
    (is (fx/casts-shadow? (by-id :l9-bangs-03)))
    (is (not (fx/casts-shadow? (by-id :l9-bangs-01))) "swept back, lies on the head")
    (is (not-any? fx/casts-shadow? (pa/assets-for-layer :hair-front)))))

(deftest the-quick-colouring-tricks
  (let [w 100 h 100
        ;; a block of hair, x 10..90, y 10..90
        alpha (fn [i] (let [x (mod i w) y (quot i w)] (if (and (<= 10 x 90) (<= 10 y 90)) 255 0)))
        frame (fx/gradient-frame alpha w h [50.0 10.0] nil)
        white 0xffffff brown [92 58 30] teal [47 127 154]
        base (assoc (fx/ombre-settings {}) :depth 0.0)
        at (fn [settings x y] ((fx/pixel-fn brown teal settings frame :hair-front) x y white))]
    (testing "off, the line where the tips start is level across the piece"
      (is (= (at base 20 50) (at base 80 50))))
    (testing "clumps waver it: some strands further along than others at the same height"
      (let [s (assoc base :clumps 1.0)]
        (is (> (count (distinct (for [x (range 12 89 4)] (at s x 50)))) 3))))
    (testing "light lifts the near (left) side and deepens the far side"
      (let [s (assoc base :light 1.0 :depth 0.6)
            lum (fn [c] (+ (bit-and (bit-shift-right c 16) 0xff) (bit-and (bit-shift-right c 8) 0xff) (bit-and c 0xff)))]
        (is (> (lum (at s 15 50)) (lum (at s 85 50))))))
    (testing "under darkens the hair behind the head, light hair more than dark"
      (let [s (assoc base :under 1.0)
            [blonde-back] (fx/layer-colours :hair-back [230 200 120] [230 200 120] s)
            [dark-back] (fx/layer-colours :hair-back [40 30 30] [40 30 30] s)
            [front] (fx/layer-colours :hair-front [230 200 120] [230 200 120] s)]
        (is (< (first blonde-back) 230))
        (is (> (/ (first dark-back) 40.0) (/ (first blonde-back) 230.0)) "dark hair barely changes")
        (is (= [230 200 120] front) "only the hair behind the head")))))

(deftest a-piece-can-hold-the-root-colour
  (let [w 100 h 100
        alpha (fn [i] (let [x (mod i w) y (quot i w)] (if (and (<= 10 x 90) (<= 10 y 90)) 255 0)))
        frame (fx/gradient-frame alpha w h [50.0 10.0] nil)
        brown [92 58 30] teal [47 127 154]
        base (assoc (fx/ombre-settings {}) :depth 0.0)
        at (fn [asset y] ((fx/pixel-fn brown teal (fx/piece-settings base asset) frame :bangs) 50 y 0xffffff))
        packed (fn [[r g b]] (bit-or (bit-shift-left r 16) (bit-shift-left g 8) b))]
    (is (= (packed teal) (at {} 88)) "an ordinary piece reaches the tip colour")
    (is (= (packed brown) (at {:asset/tips-from 1.0} 88)) "1.0: the fringe stays in the root colour")
    (is (= (packed brown) (at {:asset/tips-from 0.7} 60)) "0.7: root colour until 70% along")
    (is (not= (packed brown) (at {:asset/tips-from 0.7} 89)) "and only the ends take the tips")))
