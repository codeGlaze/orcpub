(ns orcpub.dnd.e5.portrait-layout-test
  "Runs on BOTH the JVM and in ClojureScript, which is the point: these numbers
   used to be written once per renderer and had already drifted. A .cljc test is
   the only kind that can assert the two agree."
  (:require [clojure.test :refer [deftest testing is are]]
            [orcpub.dnd.e5.portrait-layout :as layout]))

;; ---------------------------------------------------------------------------
;; contain-rect -- the DOM's `mask-size: contain`, which the other two must match
;; ---------------------------------------------------------------------------

(deftest taller-art-is-limited-by-height
  (testing "8:11 art in a 4:5 frame: fits the height, leaves side margins.
            Stretching to fill instead is the bug that shipped every face 10%
            too wide."
    (let [[x y w h] (layout/contain-rect 800 1100 600 750)]
      (is (= 750 h) "the limiting edge is used fully")
      (is (< w 600) "and the other is inset, not stretched")
      (is (= 0 y))
      (is (pos? x) "centred, so the inset is split")
      ;; An odd remainder cannot be halved into whole pixels, so the margins can
      ;; differ by one. Asserting exact equality is asserting something false.
      (is (<= (Math/abs (- 600 (+ x w x))) 1)
          "left and right margins match to the pixel"))))

(deftest wider-art-is-limited-by-width
  (let [[x y w h] (layout/contain-rect 1200 600 600 750)]
    (is (= 600 w))
    (is (< h 750))
    (is (= 0 x))
    (is (<= (Math/abs (- 750 (+ y h y))) 1))))

(deftest the-exact-rect-for-the-shipping-frame
  (testing "the numbers, not just the shape: 192x264 source art in the 600x750
            frame the share card and the sheet both use. These assertions used
            to sit in a .clj test claiming they held 'in both runtimes' while
            only ever running in one; now they do."
    (let [[x y w h] (layout/contain-rect 192 264 600 750)]
      (is (= 750 h) "fills the short axis")
      (is (= 545 w))
      (is (= 28 x) "(600-545)/2 rounds up")
      (is (= 0 y))
      (is (< (Math/abs (- (/ (double w) h) (/ 192.0 264))) 0.005)
          "source aspect preserved")))
  (testing "and the other orientation"
    (let [[x y w h] (layout/contain-rect 800 400 600 750)]
      (is (= 600 w))
      (is (= 300 h))
      (is (= 0 x))
      (is (= 225 y) "centred vertically"))))

(deftest matching-aspect-fills-exactly
  (is (= [0 0 600 750] (layout/contain-rect 800 1000 600 750))))

(deftest aspect-ratio-survives
  (testing "the whole purpose: the shape on the share card is the shape in the
            drawer"
    (let [[_ _ w h] (layout/contain-rect 800 1100 600 750)]
      (is (< (Math/abs (- (/ (double w) h) (/ 800.0 1100))) 0.01)))))

(deftest a-degenerate-asset-fills-the-frame-rather-than-dividing-by-zero
  (are [sw sh] (= [0 0 600 750] (layout/contain-rect sw sh 600 750))
    0 1100
    800 0
    0 0))

;; ---------------------------------------------------------------------------
;; the credit band
;; ---------------------------------------------------------------------------

(deftest the-credit-is-rounded-not-truncated
  (testing "at a 750px frame this is 19.5px. The browser rounded it to 20 with a
            2px halo and the server truncated it to 19 with a 1px halo, so the
            caption in a PDF was not the caption on a share card. One answer
            now, and it is the browser's, because that is what someone watched
            while they picked."
    (let [{:keys [size halo]} (layout/credit-layout 600 750)]
      (is (= 20 size))
      (is (= 2 halo)))))

(deftest the-credit-stays-legible-in-a-tiny-frame
  (testing "a summary thumbnail is small enough that 2.6% would vanish"
    (let [{:keys [size halo]} (layout/credit-layout 96 120)]
      (is (= 9 size) "floored, not scaled away")
      (is (= 1 halo) "and it keeps an outline, or it cannot be read on white"))))

(deftest the-credit-sits-inside-the-frame
  (are [w h] (let [{:keys [baseline center-x]} (layout/credit-layout w h)]
               (and (< baseline h) (pos? baseline)
                    (= (/ (double w) 2) center-x)))
    600 750
    96 120
    1200 1500))

(deftest the-credit-is-dark-on-light-so-either-background-reads
  (let [{[_ _ _ oa] :outline [fr fg fb fa] :fill} (layout/credit-layout 600 750)]
    (is (> oa 128) "the halo is opaque enough to carry the glyph on a dark client")
    (is (> fa oa) "and the fill is stronger still")
    (is (every? #(< % 64) [fr fg fb]) "fill is dark; the outline is what lifts it")))

;; ---------------------------------------------------------------------------
;; the site mark, which must not share an edge with her name
;; ---------------------------------------------------------------------------

(deftest the-site-mark-keeps-off-the-credits-edge
  (testing "the separation is the whole point: two marks in one band let a crop
            take the artist's name along with the advertising"
    (let [{:keys [baseline]} (layout/credit-layout 600 750)
          {:keys [x size]} (layout/site-mark-layout 600 750)]
      (is (> x (* 0.9 600)) "the mark hugs the right edge")
      (is (< x 600) "but stays inside the frame")
      (is (< size 20) "and is smaller than the credit")
      (is (pos? baseline)))))

(deftest the-site-mark-is-fainter-than-the-credit
  (testing "it is ours and she is the one being credited"
    (let [{[_ _ _ credit-fill] :fill} (layout/credit-layout 600 750)
          {[_ _ _ mark-fill] :fill} (layout/site-mark-layout 600 750)]
      (is (< mark-fill credit-fill)))))

;; ---------------------------------------------------------------------------
;; the canvas conversion
;; ---------------------------------------------------------------------------

(deftest alpha-converts-to-the-unit-a-canvas-wants
  (is (= 1.0 (layout/alpha->unit [0 0 0 255])))
  (is (= 0.0 (layout/alpha->unit [0 0 0 0])))
  (let [a (layout/alpha->unit (:fill (layout/credit-layout 600 750)))]
    (is (< 0.9 a 1.0) "235/255")))
