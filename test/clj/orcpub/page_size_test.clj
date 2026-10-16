(ns orcpub.page-size-test
  "Proves binding draw/*page* actually changes the output.

   The previous attempt at this replaced a literal 11 with a table lookup that always
   returned 11, and every baseline stayed byte-identical -- correctly, because nothing
   had changed. Nothing tested a non-Letter page, so nothing caught it. This does."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.pdf-ops :as ops]
            [orcpub.pdf.draw :as draw]
            [orcpub.pdf.cards :as cards]
            [orcpub.spellbook :as spellbook])
  (:import (org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream)
           (org.apache.pdfbox.pdmodel.common PDRectangle)))

(deftest page-sizes-are-iso-216
  (testing "to the hundredth of a point, not rounded to the nearest point"
    ;; 210x297mm and 148x210mm at 72pt/inch over 25.4mm/inch.
    (is (= [595.28 841.89] (draw/page-size :a4)))
    (is (= [419.53 595.28] (draw/page-size :a5)))
    (is (= [612.0 792.0] (draw/page-size :letter))))
  (testing "A5 is half an A4 only to the millimetre, NOT exactly"
    ;; ISO 216 halves the previous size and rounds DOWN to a whole mm: half of A4's
    ;; 297mm is 148.5, but A5 is 148. So 2-up A5 on A4 leaves a 0.5mm = 1.4175pt
    ;; sliver. Fine to print; not fine to assert as exact, which this test caught.
    (let [gap (- (/ (second (draw/page-size :a4)) 2)
                 (first (draw/page-size :a5)))]
      (is (< 1.41 gap 1.42) "half of 297mm less 148mm, in points")
      (is (pos? gap) "A5 is the narrower of the two, never wider"))))

(deftest accessors-follow-the-binding
  (testing "page-width and page-height read *page*, in the caller's unit"
    (is (= 612.0 (draw/page-width 1)))
    (is (= 11.0 (draw/page-height 72)))
    (binding [draw/*page* :a4]
      (is (= 595.28 (draw/page-width 1)))
      (is (= 841.89 (draw/page-height 1))))
    (binding [draw/*page* :a5]
      (is (= 419.53 (draw/page-width 1))))))

(deftest the-flip-follows-the-binding
  (testing "pt-from-top is the choke point every y conversion goes through"
    (is (= (float 792.0) (draw/pt-from-top 72 0)))
    (binding [draw/*page* :a4]
      (is (= (float 841.89) (draw/pt-from-top 72 0)))
      ;; An inch down from the top of a taller page is lower in PDF space.
      (is (= (float (- 841.89 72)) (draw/pt-from-top 72 1))))
    (binding [draw/*page* :a5]
      (is (= (float 595.28) (draw/pt-from-top 1 0))))))

(deftest the-spellbook-follows-the-binding
  (testing "page-w/page-h were load-time defs, which no binding could reach"
    (is (= 792.0 (spellbook/page-h)))
    (binding [draw/*page* :a4]
      (is (= 841.89 (spellbook/page-h)))
      (is (= 595.28 (spellbook/page-w))))))

(defn- grid-ops
  "The cut grid for 2.5x3.5 cards on `page`, as drawing operators."
  [page]
  (binding [draw/*page* page]
    (with-open [doc (PDDocument.)]
      (let [pg (PDPage. (PDRectangle. (draw/page-width 1) (draw/page-height 1)))]
        (.addPage doc pg)
        (with-open [cs (PDPageContentStream. doc pg)]
          (draw/draw-grid cs 2.5 3.5))
        (ops/page-ops pg)))))

(deftest the-card-grid-resizes
  (testing "9 up on Letter and A4, 4 up on A5 -- the grid is computed, not assumed"
    ;; floor(8.5/2.5)=3 x floor(11/3.5)=3; A4 3.30x3.34 -> 3x3; A5 2.33x2.36 -> 2x2.
    ;; Each boundary line is moveTo + lineTo + stroke, so 3 ops a line.
    (let [lines #(/ (count (filter (fn [o] (re-find #" l$" o)) %)) 1)]
      (is (= 8 (lines (grid-ops :letter))) "4 vertical + 4 horizontal for 3x3")
      (is (= 8 (lines (grid-ops :a4))) "A4 also fits 3x3, at different margins")
      (is (= 6 (lines (grid-ops :a5))) "3 + 3 for 2x2")))
  (testing "and A4's grid is not Letter's grid: same box count, different margins"
    (is (not= (grid-ops :letter) (grid-ops :a4)))))

(deftest a-card-sheet-drawn-on-a4-is-not-the-letter-sheet
  (testing "end to end through print-items, the real export entry"
    (let [sheet (fn [page]
                  (binding [draw/*page* page]
                    (with-open [doc (PDDocument.)]
                      (let [pg (PDPage. (PDRectangle. (draw/page-width 1) (draw/page-height 1)))]
                        (.addPage doc pg)
                        (with-open [cs (PDPageContentStream. doc pg)]
                          (let [fonts (draw/load-fonts doc)
                                img (draw/make-image-loader doc)]
                            (cards/print-items cs doc fonts img 2.5 3.5
                                               [{:orcpub.dnd.e5.magic-items/name "Probe"
                                                 :orcpub.dnd.e5.magic-items/type :wondrous-item
                                                 :orcpub.dnd.e5.magic-items/rarity :rare
                                                 :orcpub.dnd.e5.magic-items/description "A short item."}]
                                               0 false false nil)))
                        (ops/page-ops pg)))))]
      (is (not= (sheet :letter) (sheet :a4))
          "if these match, the page is being consulted and discarded again"))))
