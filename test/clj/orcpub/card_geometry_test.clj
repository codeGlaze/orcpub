(ns orcpub.card-geometry-test
  "Pins the drawing operators of every card page, so a refactor that moves a card by a
   sixteenth of an inch fails here instead of shipping.

   The cards' positions were tuned by hand over several rounds, and nothing else in the
   suite reads a coordinate: card_export_test asserts structure, pdf_test asserts fields.

   Re-seed after a DELIBERATE change: CARD_GEOMETRY=seed lein test, then read the diff."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.pdf-ops :as ops]
            [orcpub.pdf :as pdf]
            [orcpub.pdf.draw :as draw]
            [orcpub.routes :as routes]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.magic-items :as magic-items])
  (:import (org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream)
           (org.apache.pdfbox Loader)
           (java.io ByteArrayOutputStream)))

(def sheet-baseline-file "test/card-geometry-baseline.edn")
(def ornament-baseline-file "test/card-ornament-baseline.edn")

(def ^:private card-back-logo "public/image/dmv-mark-black.png")

(def ^:private print-modes
  "Colour and the two printer-friendly renderings. draw-spell-field and the card frame
   each branch three ways on these, so one mode pins a third of the paths."
  [[:colour false false] [:bw true false] [:bw-faded true true]])

(def ^:private sheet-pages
  "The 8 pages render-cards produces per printing mode, in order."
  ["spell fronts 1" "spell backs 1" "spell fronts 2" "spell backs 2"
   "item fronts 1" "item backs 1" "item fronts 2" "item backs 2"])

(def ^:private sheet-labels
  (vec (for [[mode _ _] print-modes, nm sheet-pages] (str nm " / " (name mode)))))

(defn- render-cards
  "Both kinds of card page in every printing mode, through the real routes, reloaded from
   the saved bytes. 12 of each kind: fills one sheet of 9 and spills onto a second."
  []
  (let [out (ByteArrayOutputStream.)]
    (with-open [doc (PDDocument.)]
      (let [fonts (draw/load-fonts doc)
            img (pdf/make-image-loader doc)
            known {:wizard (vec (for [k (take 12 (keys spells/spell-map))]
                                  {:key k :class "Wizard"}))}]
        (doseq [[_ bw? faded?] print-modes]
          (routes/add-spell-cards! doc fonts img known {"Wizard" 15} {"Wizard" 7}
                                   nil false card-back-logo bw? faded?)
          (routes/add-magic-item-cards! doc fonts img
                                        (vec (take 12 magic-items/magic-items))
                                        card-back-logo bw? faded?)))
      (.save doc out))
    (Loader/loadPDF (.toByteArray out))))

(defn- sheet-pages-ops []
  (with-open [doc (render-cards)] (ops/doc-ops doc)))

(deftest card-geometry-is-unchanged
  (ops/check-baseline! sheet-baseline-file (sheet-pages-ops) sheet-labels "Card sheet"))

(deftest the-pages-are-what-we-think-they-are
  (testing "the baseline covers real output, not an empty document"
    (let [pages (sheet-pages-ops)]
      (is (= 24 (count pages)) "8 pages x 3 printing modes")
      (is (= (count sheet-labels) (count pages)))
      (is (every? pos? (map count pages)))
      (testing "fronts carry more than backs, so the names are not swapped"
        (doseq [base (range 0 24 8)]
          (is (> (count (nth pages base)) (count (nth pages (inc base)))))
          (is (> (count (nth pages (+ base 4))) (count (nth pages (+ base 5)))))))
      (testing "the three modes differ, so bw is really being exercised"
        (is (not= (nth pages 0) (nth pages 8)))
        (is (not= (nth pages 8) (nth pages 16)))))))

;; ── The ornament matrix ──────────────────────────────────────────────────────
;; The sheets above go through the real route, which fixes the flourish at its default.
;; draw-flourish! is a case over three families crossed with five rarity ranks.

(def ^:private rarity-cases
  "One item per rarity, named rather than taken by position, so a data edit cannot quietly
   shrink what the matrix covers. `expect` is asserted before use."
  [{:name "Potion of Climbing"                 :expect :common}
   {:name "Adamantine Armor, Chain Shirt"      :expect :uncommon}
   {:name "Armor of Resistance (Acid), Padded" :expect :rare}
   {:name "Arrow +3"                           :expect :very-rare}
   {:name "Apparatus of the Crab"              :expect :legendary}
   {:name "Belt of Dwarvenkind"                :expect :varies}
   ;; Not rarities. Both charge paths: 36 is past tickable-charges so it prints a number,
   ;; 3 prints circles. Then a name long enough to force the shrink.
   {:name "Cube of Force"                      :expect :rare       :charges 36}
   {:name "Eyes of Charming"                   :expect :uncommon   :charges 3}
   {:name "Tome of Leadership and Influence (read with modifiers)" :expect :very-rare}])

(defn- item-named [nm]
  (first (filter #(= nm (:name %)) magic-items/magic-items)))

(def ^:private ornament-labels
  (vec (for [family pdf/card-flourishes, [mode _ _] print-modes]
         (str (name family) " / " (name mode)))))

(defn- ornament-pages
  "One page per flourish family x printing mode, each carrying every rarity case."
  []
  (let [items (mapv (comp item-named :name) rarity-cases)
        out (ByteArrayOutputStream.)]
    (with-open [doc (PDDocument.)]
      (let [fonts (draw/load-fonts doc)
            img (pdf/make-image-loader doc)]
        (doseq [family pdf/card-flourishes
                [_ bw? faded?] print-modes]
          (let [page (PDPage.)]
            (.addPage doc page)
            (with-open [cs (PDPageContentStream. doc page)]
              (pdf/print-items cs doc fonts img 2.5 3.5 items 0 bw? faded?
                               {:flourish family})))))
      (.save doc out))
    (with-open [doc (Loader/loadPDF (.toByteArray out))] (ops/doc-ops doc))))

(deftest the-matrix-covers-what-it-claims
  (testing "every named item exists and still has the rarity the matrix assumes"
    (doseq [{:keys [name expect]} rarity-cases]
      (let [item (item-named name)]
        (is (some? item) (str "no magic item named " (pr-str name)))
        (when item
          (is (= expect (:orcpub.dnd.e5.magic-items/rarity item))
              (str (pr-str name) " changed rarity: the matrix no longer covers " expect))))))
  (testing "all five ranks, plus :varies"
    (is (= #{:common :uncommon :rare :very-rare :legendary :varies}
           (set (map :expect rarity-cases)))))
  (testing "both charge paths, either side of tickable-charges"
    (doseq [{:keys [name charges]} (filter :charges rarity-cases)]
      (is (= charges (pdf/item-charges
                      (:orcpub.dnd.e5.magic-items/description (item-named name))))
          (str (pr-str name) " no longer reads as " charges " charges")))
    (is (= #{true false}
           (set (map #(<= % @#'pdf/tickable-charges) (keep :charges rarity-cases))))
        "the matrix needs one item each side of tickable-charges"))
  (testing "an attunement badge and a name that must shrink are both on the page"
    (is (some #(seq (str (:orcpub.dnd.e5.magic-items/attunement (item-named (:name %)))))
              rarity-cases))
    (is (some #(< 40 (count (:name %))) rarity-cases)))
  (testing "every flourish family and printing mode"
    (is (= 9 (count ornament-labels)))))

(deftest ornament-geometry-is-unchanged
  (ops/check-baseline! ornament-baseline-file (ornament-pages) ornament-labels "Ornament"))
