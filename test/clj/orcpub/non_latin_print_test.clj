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

(deftest the-templates-own-sizes-survive-the-font-swap
  ;; 25 widgets in style 1 carry their own DA with a fixed size (24 at 8pt, one at 10).
  ;; PDVariableText.setDefaultAppearance copies the FIELD's DA over every kid widget's
  ;; (PDFBOX-5797), so swapping the font through it turned those into auto-size: a save
  ;; box holding +7 printed at 4.89pt instead of 8.
  (let [sizes (fn [doc]
                (into {} (for [f (iterator-seq (.iterator (.getFieldTree (.getAcroForm (.getDocumentCatalog doc)))))
                               :when (instance? org.apache.pdfbox.pdmodel.interactive.form.PDTextField f)
                               [i w] (map-indexed vector (.getWidgets f))
                               :let [da (.getString (.getCOSObject w) org.apache.pdfbox.cos.COSName/DA)]
                               :when da]
                           [[(.getFullyQualifiedName f) i] (second (re-find #"/\S+ ([\d.]+) Tf" da))])))]
    (with-open [before (template 1) after (template 1)]
      (pdf/write-fields! after {:int-save "+7" :character-name wide} false {})
      (let [b (sizes before) a (sizes after)
            fixed (filter (fn [[_ sz]] (not= "0" sz)) b)]
        (is (= 25 (count fixed)) "the premise: what the template sets")
        (is (= b (select-keys a (keys b))) "every widget keeps the size it came with")
        ;; Merged field/widget dictionaries that only INHERITED a DA now carry their
        ;; own, naming Nimbus. Still auto-size, which is what they inherited.
        (is (every? #{"0"} (vals (apply dissoc a (keys b)))))))))

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

(deftest every-sheet-field-uses-nimbus-sans
  ;; One font for the whole form, not Helvetica with Nimbus where Helvetica fails. The
  ;; widths are identical either way; embedding also means every viewer draws the same
  ;; glyphs, where standard-14 Helvetica is whatever lookalike the viewer substitutes.
  ;; BLANK fields too: a player filling the PDF in their viewer gets Nimbus's glyphs.
  (with-open [doc (template 1)]
    (pdf/write-fields! doc {:character-name latin} false {})
    (let [form (.getAcroForm (.getDocumentCatalog doc))
          texts (filter #(instance? org.apache.pdfbox.pdmodel.interactive.form.PDTextField %)
                        (iterator-seq (.iterator (.getFieldTree form))))
          off (remove #(str/starts-with? (str (.getDefaultAppearance %)) "/NmS") texts)]
      (is (= latin (.getValueAsString (field-of doc "character-name"))))
      (is (< 100 (count texts)) "the guard reaches the whole form, not one field")
      (is (empty? off) (str (count off) " field(s) not on Nimbus, e.g. "
                            (some-> (first off) .getFullyQualifiedName))))))

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
