(ns orcpub.page-size-test
  "Proves binding draw/*page* actually changes the output.

   The previous attempt at this replaced a literal 11 with a table lookup that always
   returned 11, and every baseline stayed byte-identical -- correctly, because nothing
   had changed. Nothing tested a non-Letter page, so nothing caught it. This does."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [orcpub.errors :as errors]
            [orcpub.pdf :as pdf]
            [orcpub.routes :as routes]
            [orcpub.pdf-ops :as ops]
            [orcpub.pdf.draw :as draw]
            [orcpub.pdf.cards :as cards]
            [orcpub.spellbook :as spellbook])
  (:import (org.apache.pdfbox Loader)
           (org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream)
           (org.apache.pdfbox.pdmodel.common PDRectangle)))

(deftest page-sizes-are-iso-216
  (testing "to the hundredth of a point, not rounded to the nearest point"
    ;; 210x297mm at 72pt/inch over 25.4mm/inch.
    (is (= [595.28 841.89] (draw/page-size :a4)))
    (is (= [612.0 792.0] (draw/page-size :letter))))
  (testing "Letter and A4 are the two papers designed for; nothing else is offered"
    (is (= #{:letter :a4} (set (keys draw/page-sizes))))))

(deftest accessors-follow-the-binding
  (testing "page-width and page-height read *page*, in the caller's unit"
    (is (= 612.0 (draw/page-width 1)))
    (is (= 11.0 (draw/page-height 72)))
    (binding [draw/*page* :a4]
      (is (= 595.28 (draw/page-width 1)))
      (is (= 841.89 (draw/page-height 1))))))

(deftest the-flip-follows-the-binding
  (testing "pt-from-top is the choke point every y conversion goes through"
    (is (= (float 792.0) (draw/pt-from-top 72 0)))
    (binding [draw/*page* :a4]
      (is (= (float 841.89) (draw/pt-from-top 72 0)))
      ;; An inch down from the top of a taller page is lower in PDF space.
      (is (= (float (- 841.89 72)) (draw/pt-from-top 72 1))))))

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
  (testing "9 up on Letter and A4 -- the grid is computed, not assumed"
    ;; floor(8.5/2.5)=3 x floor(11/3.5)=3; A4 3.30x3.34 -> 3x3.
    ;; Each boundary line is moveTo + lineTo + stroke, so 3 ops a line.
    (let [lines #(/ (count (filter (fn [o] (re-find #" l$" o)) %)) 1)]
      (is (= 8 (lines (grid-ops :letter))) "4 vertical + 4 horizontal for 3x3")
      (is (= 8 (lines (grid-ops :a4))) "A4 also fits 3x3, at different margins")))
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

(deftest the-request-option-is-validated
  (testing "the two we design for are accepted"
    (doseq [p [:letter :a4]]
      (is (= p (#'routes/one-of routes/valid-page-sizes p :letter)))))
  (testing "A5 is no longer offered: a request for it gets Letter, not an error"
    (is (= :letter (#'routes/one-of routes/valid-page-sizes :a5 :letter))))
  (testing "a string names the same page, as the client sends it"
    (is (= :a4 (#'routes/one-of routes/valid-page-sizes "a4" :letter))))
  (testing "anything else falls back to Letter rather than throwing.

            A typo in an option must not cost someone their sheet -- the same rule
            valid-sheet-styles follows for an unknown style id."
    (doseq [v [:a3 :tabloid "nonsense" nil 7 {:a 1}]]
      (is (= :letter (#'routes/one-of routes/valid-page-sizes v :letter))
          (str "unexpected acceptance of " (pr-str v))))))

(deftest page-size-is-stripped-before-the-fields-are-written
  (testing "it names no box, so it must be in pdf-option-keys.

            Without it write-fields! reports it unplaceable on EVERY request -- and
            those reports now reach the user on the notice page, so every single
            export would carry a spurious warning."
    (is (contains? @#'routes/pdf-option-keys :page-size))))

(deftest an-export-option-never-reaches-the-form
  ;; pdf-option-keys exists because write-fields! reports whatever it cannot place, and
  ;; an option is not a field. The portrait pair arrived with the layered-portrait work
  ;; and was not added, so every composed-portrait export drew a notice page saying two
  ;; values had been left out -- of a sheet that had lost nothing.
  (let [option-keys @#'routes/pdf-option-keys]
    (testing "the keys the export reads for itself are all declared"
      (doseq [k [:portrait-png :portrait-credit :page-size :spellbook :flatten?]]
        (is (contains? option-keys k) (str k " is read by the export but not declared"))))
    (testing "and stripping them leaves write-fields! nothing to complain about"
      (let [{:keys [file]} (get pdf/sheet-masters 1)
            fields (zipmap option-keys (repeat "x"))]
        (with-open [in (.openStream (io/resource file))
                    doc (Loader/loadPDF (.readAllBytes in))]
          (binding [errors/*notices* (atom [])]
            (is (empty? (pdf/write-fields! doc (apply dissoc fields option-keys) false {}))
                "no option is mistaken for a field")
            (is (empty? (errors/notices)) "so no notice page")))))))
