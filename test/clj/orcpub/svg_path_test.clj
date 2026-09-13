(ns orcpub.svg-path-test
  "Covers the SVG path grammar the card icons are drawn from."
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [orcpub.pdf :as pdf]))

(defn- close-to?
  "Path arithmetic is floating point; compare shapes, not bit patterns."
  [expected actual]
  (and (= (count expected) (count actual))
       (every? true?
               (map (fn [e a]
                      (cond (keyword? e) (= e a)
                            (number? e) (< (Math/abs (- (double e) (double a))) 1e-6)
                            :else (close-to? e a)))
                    expected actual))))

(deftest absolute-and-relative-agree
  (testing "the same shape written either way parses to the same points"
    (is (close-to? (pdf/svg-path-ops "M10 10 L30 10 L30 30 Z")
                   (pdf/svg-path-ops "m10 10 l20 0 l0 20 z")))))

(deftest coordinate-pairs-after-a-move-are-implicit-lines
  (testing "M with three pairs is a move and two lines, not three moves"
    (is (close-to? [[:move 1.0 2.0] [:line 3.0 4.0] [:line 5.0 6.0]]
                   (pdf/svg-path-ops "M1 2 3 4 5 6"))))
  (testing "and a relative m continues as relative l"
    (is (close-to? [[:move 1.0 1.0] [:line 3.0 3.0] [:line 6.0 6.0]]
                   (pdf/svg-path-ops "m1 1 2 2 3 3")))))

(deftest horizontal-and-vertical-hold-the-other-axis
  (is (close-to? [[:move 5.0 5.0] [:line 20.0 5.0] [:line 20.0 30.0]]
                 (pdf/svg-path-ops "M5 5 H20 V30")))
  (is (close-to? [[:move 5.0 5.0] [:line 25.0 5.0] [:line 25.0 15.0]]
                 (pdf/svg-path-ops "M5 5 h20 v10"))))

(deftest close-returns-to-the-subpath-start
  (testing "a relative command after Z is relative to where the subpath began,
            not to the last point drawn"
    (is (close-to? [[:move 10.0 10.0] [:line 20.0 20.0] [:close] [:line 15.0 15.0]]
                   (pdf/svg-path-ops "M10 10 L20 20 Z l5 5")))))

(deftest smooth-curves-reflect-the-previous-control-point
  (testing "S takes its first control point by mirroring C's second through the
            current point -- (20,0) mirrored through (30,0) is (40,0)"
    (is (close-to? [[:move 0.0 0.0]
                    [:curve 10.0 10.0 20.0 0.0 30.0 0.0]
                    [:curve 40.0 0.0 50.0 10.0 60.0 0.0]]
                   (pdf/svg-path-ops "M0 0 C10 10 20 0 30 0 S50 10 60 0"))))
  (testing "with no curve to reflect, the control point is the current point"
    (is (close-to? [[:move 0.0 0.0] [:curve 0.0 0.0 10.0 10.0 20.0 0.0]]
                   (pdf/svg-path-ops "M0 0 S10 10 20 0")))))

(deftest quadratics-are-raised-to-cubics
  (testing "PDF has no quadratic operator, so Q becomes the equivalent C"
    (is (close-to? [[:move 0.0 0.0]
                    [:curve (/ 20.0 3) 0.0 10.0 (/ 10.0 3) 10.0 10.0]]
                   (pdf/svg-path-ops "M0 0 Q10 0 10 10")))))

(deftest number-forms-the-glyphs-actually-use
  (testing "a negative number needs no separator before it"
    (is (close-to? [[:move 10.0 -20.0] [:line 5.0 -1.0]]
                   (pdf/svg-path-ops "M10-20L5-1"))))
  (testing "a leading-dot decimal, and two of them run together"
    (is (close-to? [[:move 0.5 0.25]] (pdf/svg-path-ops "M.5.25"))))
  (testing "exponent notation"
    (is (close-to? [[:move 1.5 0.002]] (pdf/svg-path-ops "M1.5 2e-3")))))

(deftest arcs-are-sampled-rather-than-dropped
  (let [ops (pdf/svg-path-ops "M100 100 A50 50 0 0 1 200 100")]
    (testing "an A produces a run of points, not nothing"
      (is (> (count ops) 5)))
    (testing "that ends where the arc was told to end"
      (let [[_ x y] (last ops)]
        (is (< (Math/abs (- 200.0 x)) 1e-6))
        (is (< (Math/abs (- 100.0 y)) 1e-6))))))

(deftest the-glyph-is-taken-not-the-background
  (testing "game-icons wraps each glyph in a transparent square; filling that
            instead would print a solid black box"
    (is (= "M9 9h4v4H9z"
           (pdf/last-svg-path
            (str "<svg><path d=\"M0 0h512v512H0z\" opacity=\"0\"></path>"
                 "<g><path fill=\"#fff\" d=\"M9 9h4v4H9z\"></path></g></svg>")))))
  (testing "single-quoted attributes too -- the black/ icons were saved that way"
    (is (= "M9 9h4v4H9z"
           (pdf/last-svg-path
            (str "<svg><path opacity='0' d='M0 0h512v512H0z'/>"
                 "<g><path d='M9 9h4v4H9z'/></g></svg>")))))
  (testing "a file with no path at all is nil rather than an exception"
    (is (nil? (pdf/last-svg-path "<svg></svg>")))))

(deftest a-missing-icon-is-nil-not-a-throw
  (is (nil? (pdf/load-svg-icon "no-such-icon-exists"))))

(deftest the-card-icons-parse
  (doseq [icon ["magic-swirl" "arrow-dunk" "shiny-purse" "sands-of-time"
                "clockwise-rotation"]]
    (testing (str icon " is vendored as an SVG and yields a drawable path")
      (let [ops (pdf/load-svg-icon icon)]
        (is (seq ops) "the spell cards fall back to a 32px raster without it")
        (is (= :move (ffirst ops)) "a path has to open with a move")))))

(deftest every-vendored-svg-parses
  ;; A net rather than a spot check: if a later icon uses a command the parser
  ;; does not cover, this fails on the file rather than in a silently blank card.
  (let [dir (io/file (io/resource "public/image"))
        svgs (filter #(.endsWith (.getName %) ".svg") (file-seq dir))
        broken (remove #(let [ops (some-> (slurp %) pdf/last-svg-path pdf/svg-path-ops)]
                          (and (seq ops) (= :move (ffirst ops))))
                       svgs)]
    (is (seq svgs) "the icons should be on the classpath")
    (is (empty? broken)
        (str "unparsed: " (mapv #(.getName %) broken)))))
