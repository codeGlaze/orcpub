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

(deftest a-switched-field-uses-the-helvetica-clone
  ;; Nimbus Sans, not Vollkorn: a serif among sans fields stood out, and Nimbus's widths
  ;; are Helvetica's, so the field looks and fits like its neighbours.
  (with-open [doc (template 1)]
    (pdf/write-fields! doc {:character-name wide} false {})
    (let [f (field-of doc "character-name")
          dr (.getDefaultResources (.getAcroForm (.getDocumentCatalog doc)))]
      (is (str/starts-with? (.getDefaultAppearance f) "/NmS"))
      (is (str/includes? (.getName (.getFont dr (org.apache.pdfbox.cos.COSName/getPDFName "NmS")))
                         "NimbusSans")))))

(deftest nimbus-sans-is-helvetica-width
  ;; The reason it was chosen, pinned: replacing it with a "lookalike" that is not
  ;; metric-compatible would change how every switched field fits.
  (with-open [doc (PDDocument.)]
    (let [nimbus (with-open [in (.openStream (io/resource draw/nimbus-file))]
                   (org.apache.pdfbox.pdmodel.font.PDType0Font/load doc in))
          off (for [cp (concat (range 0x20 0x7F) (range 0xA1 0x100))
                    :let [c (str (char cp))
                          d (Math/abs (- (.getStringWidth draw/HELVETICA c) (.getStringWidth nimbus c)))]
                    :when (> d 0.5)]
                (str (format "U+%04X" cp) " off by " (Math/round (double d))))]
      (is (empty? off) (str/join ", " off)))))

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
