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
  (:import (org.apache.pdfbox.pdmodel PDDocument)
           (org.apache.pdfbox Loader)
           (org.apache.pdfbox.pdfparser PDFStreamParser)
           (org.apache.pdfbox.contentstream.operator Operator)
           (org.apache.pdfbox.cos COSNumber COSString COSName)
           (java.io ByteArrayOutputStream)))

(def baseline-file "test/card-geometry-baseline.edn")

(def ^:private card-back-logo "public/image/dmv-mark-black.png")

(def ^:private page-names
  "The 8 pages render-cards produces, in order."
  ["spell fronts 1" "spell backs 1" "spell fronts 2" "spell backs 2"
   "item fronts 1" "item backs 1" "item fronts 2" "item backs 2"])

(defn- render-cards
  "Both kinds of card page, through the real routes, reloaded from the saved bytes.
   12 of each: enough to fill one sheet of 9 and spill onto a second."
  []
  (let [out (ByteArrayOutputStream.)]
    (with-open [doc (PDDocument.)]
      (let [fonts (pdf/load-fonts doc)
            img (pdf/make-image-loader doc)
            known {:wizard (vec (for [k (take 12 (keys spells/spell-map))]
                                  {:key k :class "Wizard"}))}]
        (routes/add-spell-cards! doc fonts img known {"Wizard" 15} {"Wizard" 7}
                                 nil false card-back-logo false false)
        (routes/add-magic-item-cards! doc fonts img
                                      (vec (take 12 magic-items/magic-items))
                                      card-back-logo false false))
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
      (is (= 8 (count pages)))
      (is (every? pos? (map count pages)))
      (testing "and fronts carry more than backs, so the names are not swapped"
        (is (> (count (nth pages 0)) (count (nth pages 1))))
        (is (> (count (nth pages 4)) (count (nth pages 5))))))))
