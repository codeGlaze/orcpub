(ns orcpub.dnd.e5.portrait-effects-test
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [orcpub.dnd.e5.portrait-effects :as fx]
            [orcpub.dnd.e5.portrait-assets :as pa]))

(deftest settings-default-and-clamp
  (is (= {:tip nil :start 0.2 :falloff 0.6 :depth 0.3 :clumps 0.45 :light 0.0 :under 0.0 :angle nil :bangs nil
          :shine 0.0 :split false}
         (fx/ombre-settings {})))
  (is (= {:tip "#2f7f9a" :start 1.0 :falloff 0.0 :depth 0.3 :clumps 0.45 :light 1.0 :under 0.0 :angle 90.0 :bangs nil
          :shine 0.0 :split false}
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
    (testing "an angle runs from the crown on the frame's scale"
      (is (< (fx/position down 50 12) (fx/position down 50 50) (fx/position down 50 88)))
      (is (< (Math/abs (- (fx/position down 50 10) 0.3125)) 1e-9) "the crown sits a third of the way along")
      (let [up (fx/gradient-frame alpha w h crown 180)]
        (is (> (fx/position up 50 12) (fx/position up 50 88)) "an angle of 180 turns it upside down")))))

(deftest every-piece-agrees-where-a-split-or-angle-falls
  ;; two pieces of very different extent: measured per piece, each put the
  ;; line across its own middle
  (let [w 200 h 200 crown [100.0 20.0]
        wide (fn [i] (if (< 20 (mod i w) 180) 255 0))
        narrow (fn [i] (if (< 120 (mod i w) 160) 255 0))]
    (doseq [mode [:split 90 30]]
      (let [a (fx/gradient-frame wide w h crown mode) b (fx/gradient-frame narrow w h crown mode)]
        (is (= (fx/position a 140 90) (fx/position b 140 90)) (str mode))))
    (let [s (fx/gradient-frame wide w h crown :split)]
      (is (= :split (:kind s)))
      (is (< (fx/position s 60 90) 0.5 (fx/position s 140 90)) "root colour on one side of the crown, tips on the other")
      (is (= 0.5 (fx/position s 100 5)) "the line runs down through the crown"))))

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
        base (assoc (fx/ombre-settings {}) :depth 0.0 :clumps 0.0)
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
        base (assoc (fx/ombre-settings {}) :depth 0.0 :clumps 0.0)
        at (fn [asset y] ((fx/pixel-fn brown teal (fx/piece-settings base asset) frame :bangs) 50 y 0xffffff))
        packed (fn [[r g b]] (bit-or (bit-shift-left r 16) (bit-shift-left g 8) b))]
    (is (= (packed teal) (at {} 88)) "an ordinary piece reaches the tip colour")
    (is (= (packed brown) (at {:asset/tips-from 1.0} 88)) "1.0: the fringe stays in the root colour")
    (is (= (packed brown) (at {:asset/tips-from 0.7} 60)) "0.7: root colour until 70% along")
    (is (not= (packed brown) (at {:asset/tips-from 0.7} 89)) "and only the ends take the tips")))

(deftest streaks-never-touch-hair-with-one-colour
  (testing "the default streaks move only the tip colour, so a portrait with
            no tips colour is exactly as it was before streaks existed"
    (let [w 100 h 100
          alpha (fn [i] (let [x (mod i w) y (quot i w)] (if (and (<= 10 x 90) (<= 10 y 90)) 255 0)))
          frame (fx/gradient-frame alpha w h [50.0 10.0] nil)
          brown [92 58 30]
          s (fx/ombre-settings {})
          with (fn [settings x y] ((fx/pixel-fn brown brown settings frame :hair-front) x y 0xc8c8c8))]
      (is (= 0.45 (:clumps s)))
      (is (every? true? (for [x (range 12 89 7) y (range 12 89 11)]
                          (= (with s x y) (with (assoc s :clumps 0.0) x y))))))))

(defn- lined
  "A fully opaque `n` x `n` white piece with ink lines where `line?` of x, y."
  [n line?]
  {:rgb (fn [i] (if (line? (mod i n) (quot i n)) 0x000000 0xffffff))
   :alpha (fn [_] 255)})

(defn- correlation
  "How alike the field is `dx` `dy` cells apart, over the middle of it."
  [{:keys [gw gh v]} dx dy]
  (let [at (fn [x y] (aget v (+ x (* y gw))))
        pts (for [y (range 30 (- gh 30) 3) x (range 30 (- gw 30) 3)] [x y])]
    (/ (reduce + (map (fn [[x y]] (* (at x y) (at (+ x dx) (+ y dy)))) pts))
       (reduce + (map (fn [[x y]] (* (at x y) (at x y))) pts)))))

(deftest streaks-follow-the-drawn-strands
  (let [n 375]
    (testing "lines drawn straight down make streaks straight down"
      (let [{:keys [rgb alpha]} (lined n (fn [x _] (zero? (mod x 9))))
            f (fx/strand-field rgb alpha n n 3)]
        (is (= [375 375 375 375] ((juxt :gw :gh :w :h) f)) "one cell a pixel, for art this size")
        (is (> (correlation f 0 5) 0.8) "alike along the lines")
        (is (< (correlation f 5 0) 0.3) "and not across them")))
    (testing "lines drawn on a slant make slanted streaks, whichever way they lean"
      (doseq [[line? along across] [[(fn [x y] (zero? (mod (- x y) 9))) [4 4] [4 -4]]
                                    [(fn [x y] (zero? (mod (+ x y) 9))) [-4 4] [4 4]]]]
        (let [{:keys [rgb alpha]} (lined n line?)
              f (fx/strand-field rgb alpha n n 3)]
          (is (> (apply correlation f along) 0.75))
          (is (< (apply correlation f across) 0.3)))))
    (testing "values stay in -1..1 and nothing lands outside the piece"
      (let [f (fx/strand-field (fn [_] 0xffffff) (fn [i] (if (< (mod i n) 100) 255 0)) n n 3)
            v (:v f)]
        (is (every? #(<= -1.0 % 1.0) (seq v)))
        (is (every? zero? (for [y (range 0 n 10) x (range 120 n 10)] (aget v (+ x (* y (:gw f)))))))))))

(deftest the-streak-noise-is-the-same-in-every-renderer
  ;; whole 16-bit steps, so the JVM and JS cannot round them differently
  (is (= [0.2340886549172198 0.43485160601205464 0.5107194628824292
          9.613183794918746E-4 0.1523460746166171 0.4545815213244831]
         (mapv #(#'fx/hash01 % (* 3 %) 7) [0 1 2 50 399 -3]))))

(deftest streaks-follow-a-field-when-given-one
  (let [s (assoc (fx/ombre-settings {:ombre {:tip "#ffffff"}}) :depth 0.0 :start 0.3 :falloff 0.4)
        frame {:kind :arc :env (double-array 10 0.0) :reach 10.0 :x0 0.0 :x1 10.0}
        field (fn [x] {:gw 10 :gh 10 :w 10 :h 10 :v (double-array 100 x)})
        at (fn [f] ((fx/pixel-fn [0 0 0] [255 255 255] s frame :hair-back f) 5 5 0xffffff))]
    (is (< (at (field -1.0)) (at (field 0.0)) (at (field 1.0)))
        "where the field is high the tip colour comes in sooner")))

(deftest a-stored-field-reads-back-as-it-was
  (let [n 375
        f (fx/strand-field (fn [i] (if (zero? (mod (mod i n) 9)) 0 0xffffff)) (fn [_] 255) n n 3)
        bytes (fx/field->bytes f)
        back (fx/bytes->field (fn [g] (bit-and (aget bytes g) 0xff)) (:gw f) (:gh f) n n)]
    (is (every? #(< (Math/abs (double %)) (/ 0.5 127.0))
                (map - (seq (:v f)) (seq (:v back))))
        "within half a step of 1/127")
    (is (= "/image/portraits/bangs/l9_bangs_03.strands.png"
           (fx/strands-url "/image/portraits/bangs/l9_bangs_03.png")))
    (is (nil? (fx/strands-url "/image/portraits/x.svg")) "only beside raster art")))

(deftest a-placed-field-maps-frame-pixels-to-the-art
  (let [v (double-array 4 0.0)
        _ (aset v 3 1.0)   ;; bottom-right cell of a 2x2 field
        f {:gw 2 :gh 2 :w 100 :h 100 :v v}
        at (fn [field x y] ((#'fx/field-fn field) x y))]
    (is (= 1.0 (at f 90 90)))
    (is (= 0.0 (at f 10 10)))
    (is (= (at f 90 90) (at (assoc f :rect [100 100 200 200]) 280 280))
        "art drawn at 2x, 100px in: frame (280,280) is art (90,90)")
    (is (= (at f 10 10) (at (assoc f :rect [100 100 200 200]) 120 120)))))

(deftest the-bangs-can-keep-the-root-tip-or-dye
  (let [fringe {:asset/tips-from 1.0}   ;; Bangs 03's calibration
        spiky {}                        ;; Bangs 02's: none
        s (fn [o] (fx/ombre-settings {:ombre (merge {:tip "#ffffff"} o)}))]
    (testing "untouched, each piece does what its calibration says"
      (is (= 1.0 (:tips-from (fx/piece-settings (s {}) fringe :bangs))))
      (is (nil? (:tips-from (fx/piece-settings (s {}) spiky :bangs))))
      (is (= [:roots :dyed] (map fx/bangs-choice [fringe spiky]))))
    (testing "a choice wins over the calibration, either way"
      (is (nil? (:tips-from (fx/piece-settings (s {:bangs :dyed}) fringe :bangs))) "the long fringe dyed")
      (is (= 0.45 (:tips-from (fx/piece-settings (s {:bangs :tipped}) fringe :bangs))))
      (is (= 1.0 (:tips-from (fx/piece-settings (s {:bangs :roots}) spiky :bangs))) "the spiky one kept dark"))
    (testing "only on the bangs"
      (is (nil? (:tips-from (fx/piece-settings (s {:bangs :roots}) spiky :hair-back)))))
    (testing "stored as a keyword or a string; anything else is no choice"
      (is (= :tipped (:bangs (s {:bangs "tipped"}))))
      (is (nil? (:bangs (s {:bangs :purple}))))
      (is (nil? (:bangs (s {:bangs 3})))))))

(deftest a-split-edge-wavers-alike-on-every-piece
  (let [s (fx/ombre-settings {:ombre {:tip "#ffffff" :split true :start 0.5 :falloff 0.05 :clumps 0.8}})
        frame {:kind :split :cx 100.0 :span 50.0 :h 200.0 :x0 0.0 :x1 200.0}
        f (fn [k] (fx/pixel-fn [0 0 0] [255 255 255] s frame k {:gw 2 :gh 2 :w 200 :h 200 :v (double-array [1.0 -1.0 -1.0 1.0])}))]
    (is (= (map #((f :hair-front) 104 % 0xffffff) (range 0 200 9))
           (map #((f :bangs) 104 % 0xffffff) (range 0 200 9)))
        "the hair behind the bangs and the bangs put the edge in the same place")
    (is (not= 1 (count (distinct (map #((f :bangs) 104 % 0xffffff) (range 0 200 9))))) "and it does waver"))
  (testing "a split ignores holding the bangs in the root colour"
    (let [s (fx/ombre-settings {:ombre {:tip "#ffffff" :split true}})]
      (is (nil? (:tips-from (fx/piece-settings s {:asset/tips-from 1.0} :bangs))))
      (is (= :split (fx/run-mode s)))
      (is (nil? (:angle (fx/ombre-settings {:ombre {:split true :angle 90}}))) "a split is not also an angle"))))

(deftest shine-catches-the-crown-on-the-fill-only
  (let [env (double-array 100 0.0)
        frame {:kind :arc :env env :reach 100.0 :x0 0.0 :x1 100.0}
        s (assoc (fx/ombre-settings {:ombre {:shine 1.0}}) :depth 0.0)
        off (assoc s :shine 0.0)
        root [92 58 30]
        px (fn [settings k x y rgb] ((fx/pixel-fn root root settings frame k) x y rgb))
        lum (fn [c] (+ (bit-and (bit-shift-right c 16) 0xff) (bit-and (bit-shift-right c 8) 0xff) (bit-and c 0xff)))]
    (is (> (lum (px s :hair-front 50 22 0xffffff)) (+ 30 (lum (px off :hair-front 50 22 0xffffff))))
        "a band across the top of the head lightens")
    (is (= (px s :hair-front 50 70 0xffffff) (px off :hair-front 50 70 0xffffff)) "but not down the lengths")
    (is (= (px s :hair-front 50 22 0x000000) (px off :hair-front 50 22 0x000000)) "the ink stays ink")
    (is (= (px s :hair-back 50 22 0xffffff) (px off :hair-back 50 22 0xffffff)) "and the hair behind the head takes none")))
