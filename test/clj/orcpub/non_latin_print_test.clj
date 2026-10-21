(ns orcpub.non-latin-print-test
  "A value the embedded Vollkorn can draw must print as itself, not as `?`.

   The cards always did, through font-safe. Three paths did not: the character sheet's
   form fields (Helvetica, a WinAnsi-only font), the overflow pages behind them, and
   the spellbook -- all through normalize-text, which turned anything past U+00FF into
   `?`. So a character named Łukasz printed as ?ukasz, for English-speaking users too."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [orcpub.pdf :as pdf]
            [orcpub.pdf.draw :as draw]
            [orcpub.spellbook :as sb])
  (:import (org.apache.pdfbox Loader)
           (org.apache.pdfbox.pdmodel PDDocument)
           (org.apache.pdfbox.text PDFTextStripper)))

(def ^:private wide
  "Polish, Turkish, Russian, Greek: all four Vollkorn faces draw every one of these."
  "Łukasz Şahin Дмитрий Ζωή")

(def ^:private latin "Zoë Muñoz")

(defn- template [style]
  (let [{:keys [file]} (get pdf/sheet-masters style)]
    (with-open [in (.openStream (io/resource file))]
      (Loader/loadPDF (.readAllBytes in)))))

(defn- field-of [doc nm]
  (.getField (.getAcroForm (.getDocumentCatalog doc)) nm))

(deftest the-spellbook-keeps-what-vollkorn-can-draw
  (is (= wide (#'sb/clean wide)))
  (testing "bullets still become middle dots; the em dash is no longer flattened to --"
    (is (= "· a — b" (#'sb/clean "• a — b")))))

(deftest a-sheet-field-prints-a-non-latin-value
  (with-open [doc (template 1)]
    (pdf/write-fields! doc {:character-name wide} false {})
    (is (= wide (.getValueAsString (field-of doc "character-name"))))))

(deftest a-latin-1-sheet-is-unchanged
  ;; The switch is per field and only when Helvetica would have printed `?`, so every
  ;; export that works today keeps its font, its appearance streams and its file size.
  (with-open [doc (template 1)]
    (pdf/write-fields! doc {:character-name latin} false {})
    (let [f (field-of doc "character-name")]
      (is (= latin (.getValueAsString f)))
      (is (str/starts-with? (.getDefaultAppearance f) "/Helv")))))

(deftest a-flattened-sheet-draws-a-non-latin-value
  ;; End to end: the value has to survive into the page content, not just the field.
  (with-open [doc (template 1)]
    (pdf/write-fields! doc {:character-name wide} true {})
    (let [text (.getText (doto (PDFTextStripper.) (.setStartPage 1) (.setEndPage 1)) doc)]
      (is (str/includes? text "Łukasz"))
      (is (str/includes? text "Дмитрий")))))

(deftest an-overflow-page-keeps-a-non-latin-value
  (with-open [doc (template 1)]
    (let [backstory (str/join " " (repeat 400 wide))]
      (pdf/spill-overflow! doc {:backstory backstory})
      (let [over (field-of doc "overflow-1")]
        (is (some? over) "the value spilled, so the guard below is not vacuous")
        (is (and over (str/includes? (.getValueAsString over) "Łukasz")))))))

(deftest the-cards-already-did
  (with-open [doc (PDDocument.)]
    (is (= wide (draw/font-safe (:plain (draw/load-fonts doc)) wide)))))
