(ns orcpub.card-geometry-test
  "Pins the drawing operators of every card page, so a refactor that moves a card
   by a sixteenth of an inch fails here instead of shipping.

   The cards' positions were tuned by hand over several rounds. Nothing else in the
   suite would notice them moving: the other card tests assert structure (fonts
   embedded once, a site stamp present), not geometry.

   Re-seed after a DELIBERATE card change: CARD_GEOMETRY=seed lein test :only
   orcpub.card-geometry-test — then read the diff before committing it."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [orcpub.pdf :as pdf]
            [orcpub.routes :as routes]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.magic-items :as magic-items])
  (:import (org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream)
           (org.apache.pdfbox Loader)
           (org.apache.pdfbox.pdfparser PDFStreamParser)
           (org.apache.pdfbox.contentstream.operator Operator)
           (org.apache.pdfbox.cos COSNumber COSString COSName)
           (java.io ByteArrayOutputStream)))

(def baseline-file "test/card-geometry-baseline.edn")

(def ^:private card-back-logo "public/image/dmv-mark-black.png")

(def ^:private sheet-pages
  "The 8 pages render-cards produces per printing mode, in order."
  ["spell fronts 1" "spell backs 1" "spell fronts 2" "spell backs 2"
   "item fronts 1" "item backs 1" "item fronts 2" "item backs 2"])

(def ^:private print-modes
  "Colour and the two printer-friendly renderings. draw-spell-field and the card
   frame each branch three ways on these, so one mode pins a third of the paths."
  [[:colour false false] [:bw true false] [:bw-faded true true]])

(def ^:private page-names
  (vec (for [[mode _ _] print-modes, nm sheet-pages] (str nm " / " (name mode)))))

(defn- render-cards
  "Both kinds of card page in every printing mode, through the real routes, reloaded
   from the saved bytes. 12 of each kind: fills one sheet of 9 and spills onto a second."
  []
  (let [out (ByteArrayOutputStream.)]
    (with-open [doc (PDDocument.)]
      (let [fonts (pdf/load-fonts doc)
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

(defn- show-string
  "A drawn string as ASCII. Card text goes through an embedded Type0 font, so its
   bytes are 2-byte CIDs, not characters: decoding them gives control bytes that
   make the baseline a binary blob to git and kill the diff it exists for. Printable
   ASCII prints as itself; anything else goes to hex."
  [^COSString cs]
  (let [bs (.getBytes cs)]
    (if (every? #(<= 32 % 126) (map #(bit-and % 0xff) bs))
      (pr-str (String. bs "US-ASCII"))
      (str "#hex:" (apply str (map #(format "%02x" (bit-and % 0xff)) bs))))))

(defn- token-str [t]
  (cond
    ;; 2dp of a point is 1/3600 inch: finer than any hand tweak, coarse enough that
    ;; re-associating the same arithmetic does not flap the baseline.
    (instance? COSNumber t) (format "%.2f" (.floatValue ^COSNumber t))
    (instance? COSString t) (show-string t)
    (instance? COSName t) (str "/" (.getName ^COSName t))
    :else (str t)))

(defn page-ops
  "Every operator on `page` as \"arg arg op\", in order: the page's geometry as
   PDFBox will write it."
  [page]
  (loop [[t & more] (seq (.parse (PDFStreamParser. page))), args [], out []]
    (if (nil? t)
      out
      (if (instance? Operator t)
        (recur more [] (conj out (str/join " " (conj args (.getName ^Operator t)))))
        (recur more (conj args (token-str t)) out)))))

(defn- current []
  (with-open [doc (render-cards)]
    (mapv #(page-ops (.getPage doc %)) (range (.getNumberOfPages doc)))))

(defn- first-diffs
  "Up to `n` [index was now] where two op sequences disagree."
  [was now n]
  (->> (map vector (range) (concat was (repeat nil)) (concat now (repeat nil)))
       (take (max (count was) (count now)))
       (remove (fn [[_ a b]] (= a b)))
       (take n)
       (mapv (fn [[i a b]] [i a b]))))

(deftest card-geometry-is-unchanged
  (let [pages (current)
        recorded (when (.exists (io/file baseline-file))
                   (edn/read-string (slurp baseline-file)))]
    (if (= "seed" (System/getenv "CARD_GEOMETRY"))
      (do (spit baseline-file
                (with-out-str
                  (println ";; Written by orcpub.card-geometry-test. Drawing operators per card page.")
                  (println ";; A diff here is a card moving. Read it before committing it.")
                  (binding [*print-length* nil *print-level* nil]
                    (pprint/pprint pages))))
          (println "seeded" (reduce + (map count pages)) "operators across"
                   (count pages) "pages to" baseline-file))
      (do
        (is (some? recorded) (str baseline-file " is missing: run CARD_GEOMETRY=seed"))
        (when recorded
          (testing "the same pages, in the same order"
            (is (= (count recorded) (count pages))))
          ;; `now`, not `is`: a binding named `is` shadows clojure.test/is and the
          ;; assertion below silently becomes a vector lookup.
          (doseq [[i was now nm] (map vector (range) recorded pages page-names)]
            (testing (str "page " i " (" nm ")")
              (when-not (= was now)
                (println (format "%nPage %d (%s): %d ops recorded, %d now"
                                 i nm (count was) (count now)))
                (doseq [[j a b] (first-diffs was now 12)]
                  (println (format "  op %4d  was %-34s now %s" j (pr-str a) (pr-str b)))))
              (is (= was now)
                  "a card moved; re-seed only if the move was intended"))))))))

(deftest the-pages-are-what-we-think-they-are
  (testing "the baseline covers real output, not an empty document"
    (let [pages (current)]
      (is (= 24 (count pages)) "8 pages x 3 printing modes")
      (is (= (count page-names) (count pages)))
      (is (every? pos? (map count pages)))
      (testing "and fronts carry more than backs, so the names are not swapped"
        (doseq [base (range 0 24 8)]
          (is (> (count (nth pages base)) (count (nth pages (inc base)))))
          (is (> (count (nth pages (+ base 4))) (count (nth pages (+ base 5)))))))
      (testing "and the three modes differ, so bw is really being exercised"
        (is (not= (nth pages 0) (nth pages 8)))
        (is (not= (nth pages 8) (nth pages 16)))))))

;; ── The ornament matrix ──────────────────────────────────────────────────────
;; The two sheets above go through the real route, which fixes the flourish at its
;; default and the printing mode to colour. draw-flourish! is a case over three
;; families crossed with five rarity ranks, and bw? / bw-faded? are separate paths.

(def ornament-baseline-file "test/card-ornament-baseline.edn")

(def ^:private rarity-cases
  "One item per rarity, named rather than taken by position, so a data edit cannot
   quietly shrink what the matrix covers. `expect` is asserted before use."
  [{:name "Potion of Climbing"                 :expect :common}
   {:name "Adamantine Armor, Chain Shirt"      :expect :uncommon}
   {:name "Armor of Resistance (Acid), Padded" :expect :rare}
   {:name "Arrow +3"                           :expect :very-rare}
   {:name "Apparatus of the Crab"              :expect :legendary}
   {:name "Belt of Dwarvenkind"                :expect :varies}
   ;; Not rarities. Both charge paths: 36 is past tickable-charges so it prints a
   ;; number, 3 prints circles. Then a name long enough to force the shrink.
   {:name "Cube of Force"                      :expect :rare       :charges 36}
   {:name "Eyes of Charming"                   :expect :uncommon   :charges 3}
   {:name "Tome of Leadership and Influence (read with modifiers)" :expect :very-rare}])

(defn- item-named [nm]
  (first (filter #(= nm (:name %)) magic-items/magic-items)))

(defn- ornament-pages
  "One page per flourish family x printing mode, each carrying every rarity case."
  []
  (let [items (mapv (comp item-named :name) rarity-cases)
        out (ByteArrayOutputStream.)
        labels (atom [])]
    (with-open [doc (PDDocument.)]
      (let [fonts (pdf/load-fonts doc)
            img (pdf/make-image-loader doc)]
        (doseq [family pdf/card-flourishes
                [mode bw? faded?] print-modes]
          (let [page (PDPage.)]
            (.addPage doc page)
            (swap! labels conj (str (name family) " / " (name mode)))
            (with-open [cs (PDPageContentStream. doc page)]
              (pdf/print-items cs doc fonts img 2.5 3.5 items 0 bw? faded?
                               {:flourish family})))))
      (.save doc out))
    [@labels (with-open [doc (Loader/loadPDF (.toByteArray out))]
               (mapv #(page-ops (.getPage doc %))
                     (range (.getNumberOfPages doc))))]))

(deftest the-matrix-covers-what-it-claims
  (testing "every named item exists and still has the rarity the matrix assumes"
    (doseq [{:keys [name expect]} rarity-cases]
      (let [item (item-named name)]
        (is (some? item) (str "no magic item named " (pr-str name)))
        (when item
          (is (= expect (:orcpub.dnd.e5.magic-items/rarity item))
              (str (pr-str name) " changed rarity: the matrix no longer covers "
                   expect))))))
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
    (is (= 9 (* (count pdf/card-flourishes) (count print-modes))))))

(deftest ornament-geometry-is-unchanged
  (let [[labels pages] (ornament-pages)
        recorded (when (.exists (io/file ornament-baseline-file))
                   (edn/read-string (slurp ornament-baseline-file)))]
    (if (= "seed" (System/getenv "CARD_GEOMETRY"))
      (do (spit ornament-baseline-file
                (with-out-str
                  (println ";; Written by orcpub.card-geometry-test. Flourish family x printing mode.")
                  (println ";; A diff here is an ornament moving. Read it before committing it.")
                  (binding [*print-length* nil *print-level* nil]
                    (pprint/pprint pages))))
          (println "seeded" (reduce + (map count pages)) "operators across"
                   (count pages) "ornament pages to" ornament-baseline-file))
      (do
        (is (some? recorded) (str ornament-baseline-file " is missing: run CARD_GEOMETRY=seed"))
        (when recorded
          (is (= (count recorded) (count pages)))
          (doseq [[i was now nm] (map vector (range) recorded pages labels)]
            (testing (str "page " i " (" nm ")")
              (when-not (= was now)
                (println (format "%nOrnament page %d (%s): %d ops recorded, %d now"
                                 i nm (count was) (count now)))
                (doseq [[j a b] (first-diffs was now 12)]
                  (println (format "  op %4d  was %-34s now %s" j (pr-str a) (pr-str b)))))
              (is (= was now)
                  "an ornament moved; re-seed only if the move was intended"))))))))
