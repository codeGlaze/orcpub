(ns orcpub.pdf-test
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [orcpub.pdf :as pdf])
  (:import (java.io ByteArrayOutputStream PrintStream)
           (org.apache.pdfbox Loader)
           (org.apache.pdfbox.cos COSName)
           (org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream)
           (org.apache.pdfbox.pdmodel.interactive.annotation
             PDAnnotationLink PDAnnotationWidget)
           (org.apache.pdfbox.pdmodel.interactive.form PDTextField)))

;; =============================================================================
;; Existing font tests
;; =============================================================================

(deftest fonts-test
  (testing "Font creation and ability to print latin and cyrillic characters"
    (let [^PDDocument doc (PDDocument.)
          ^PDPage page (PDPage.)
          fonts (pdf/load-fonts doc)
          required-keys [:plain :bold :italic :bold-italic]]
      (is (every? some? (map fonts required-keys)))
      (.addPage doc page)
      ;; Single content stream tests all fonts - more efficient than 4 separate streams
      (is (with-open [cs (PDPageContentStream. doc page)]
            (doseq [font-type required-keys]
              (doto cs
                (.beginText)
                (.setFont (font-type fonts) 14)
                (.newLineAtOffset (float 72) (float 700))
                (.showText (str (name font-type) ": abcABC012_?%абвАБВ"))
                (.endText)))
            true))
      (.close doc))))

;; =============================================================================
;; Helpers / fixtures
;; =============================================================================

(def ^:private template-resource
  "A bundled fillable character sheet template. Style 1, 0 spell pages."
  "fillable-char-sheetstyle-1-0-spells.pdf")

(defn- load-template ^PDDocument []
  (with-open [in (.openStream (io/resource template-resource))]
    (Loader/loadPDF (.readAllBytes in))))

(defn- form-field-names [^PDDocument doc]
  (let [form (.getAcroForm (.getDocumentCatalog doc))]
    (when form
      (into #{}
            (map #(.getFullyQualifiedName %))
            (.getFields form)))))

(defn- widgets-on-page [^PDPage page]
  (filter #(instance? PDAnnotationWidget %) (.getAnnotations page)))

;; =============================================================================
;; fix-widget-page-refs! unit tests
;;
;; fix-widget-page-refs! is private — access via the var.
;; =============================================================================

(def ^:private fix-widget-page-refs!
  @#'orcpub.pdf/fix-widget-page-refs!)

(deftest fix-widget-page-refs-sets-missing-page
  (testing "Widgets with no /P entry get their owning page set"
    (with-open [doc (PDDocument.)]
      (let [page (PDPage.)
            widget (PDAnnotationWidget.)]
        (.addPage doc page)
        ;; Explicitly clear any /P entry so getPage returns nil.
        (.removeItem (.getCOSObject widget) COSName/P)
        (.addAnnotation page widget)
        (is (nil? (.getPage widget))
            "precondition: widget has no page reference")
        (fix-widget-page-refs! doc)
        (is (some? (.getPage widget))
            "widget's page should be populated after fix")
        (is (identical? page (.getPage widget)))))))

(deftest fix-widget-page-refs-leaves-existing-untouched
  (testing "Widgets that already have /P are not modified"
    (with-open [doc (PDDocument.)]
      (let [page-a (PDPage.)
            page-b (PDPage.)
            widget (PDAnnotationWidget.)]
        (.addPage doc page-a)
        (.addPage doc page-b)
        ;; Point widget's /P at page-b, then attach it to page-a's annotations.
        ;; The helper must NOT reassign /P to page-a.
        (.setPage widget page-b)
        (.addAnnotation page-a widget)
        (is (identical? page-b (.getPage widget)))
        (fix-widget-page-refs! doc)
        (is (identical? page-b (.getPage widget))
            "existing /P should not be overwritten")))))

(deftest fix-widget-page-refs-ignores-non-widget-annotations
  (testing "Non-widget annotations are not touched and do not throw"
    (with-open [doc (PDDocument.)]
      (let [page (PDPage.)
            link (PDAnnotationLink.)]
        (.addPage doc page)
        (.addAnnotation page link)
        ;; Should be a no-op for this page, but must not NPE or throw.
        (is (nil? (fix-widget-page-refs! doc)))))))

(deftest fix-widget-page-refs-walks-all-pages
  (testing "Fix is applied across every page of the doc"
    (with-open [doc (PDDocument.)]
      (let [pages (repeatedly 3 #(PDPage.))
            widgets (repeatedly 3 #(PDAnnotationWidget.))]
        (doseq [p pages] (.addPage doc p))
        (doseq [[p w] (map vector pages widgets)]
          (.removeItem (.getCOSObject w) COSName/P)
          (.addAnnotation p w))
        (fix-widget-page-refs! doc)
        (doseq [[p w] (map vector pages widgets)]
          (is (identical? p (.getPage w))
              "every widget on every page should have /P populated"))))))

;; =============================================================================
;; write-fields! integration tests against a bundled template PDF
;; =============================================================================

(deftest write-fields-interactive-preserves-form
  (testing "flatten=false: form remains fillable and values are populated"
    (with-open [doc (load-template)]
      (let [names (form-field-names doc)]
        ;; Sanity-check that the bundled template has at least one field.
        (is (seq names) "template PDF should expose form fields"))
      ;; Pick a couple of fields that plausibly exist on every style-1 template.
      ;; Use names the write-fields! function actually iterates — it will silently
      ;; no-op on missing fields, so this stays safe across template revisions.
      (let [fields-to-write (->> (form-field-names doc)
                                 (take 3)
                                 (map (fn [n] [(keyword n) "TESTVAL"]))
                                 (into {}))]
        (pdf/write-fields! doc fields-to-write false {})
        (let [form (.getAcroForm (.getDocumentCatalog doc))]
          (is (some? form) "AcroForm must still exist (not flattened)")
          (is (seq (.getFields form))
              "form must still have fields after non-flatten write"))))))

(deftest write-fields-interactive-preserves-auto-sizing
  (testing "flatten=false must NOT overwrite the template's `0 Tf` auto-size
            default appearance on long-text fields, even when font-sizes is
            populated. Regression guard: an earlier revision decoupled the
            font-size override from the flatten flag, which clobbered the
            template's auto-sizing in interactive mode."
    (with-open [doc (load-template)]
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            ^PDTextField field (.getField form "personality-traits")]
        ;; Precondition: the bundled template uses `0 Tf` auto-size on this
        ;; field. If this fails, the template changed and the test needs
        ;; rethinking, not the production code.
        (is (some? field) "template must contain personality-traits")
        (is (re-find #"\b0\s+Tf\b" (.getDefaultAppearance field))
            (str "precondition: template's personality-traits should use "
                 "`0 Tf` auto-size; got " (.getDefaultAppearance field))))
      ;; Pass a non-trivial font-sizes map mirroring what routes.clj sends.
      (pdf/write-fields! doc
                         {:personality-traits "lorem ipsum"}
                         false ;; interactive
                         {:personality-traits 8})
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            ^PDTextField field (.getField form "personality-traits")]
        (is (re-find #"\b0\s+Tf\b" (.getDefaultAppearance field))
            (str "interactive write must preserve `0 Tf` auto-size, got "
                 (.getDefaultAppearance field)))))))

(deftest write-fields-flatten-removes-form
  (testing "flatten=true: AcroForm fields are flattened into page content"
    (with-open [doc (load-template)]
      (let [fields-to-write (->> (form-field-names doc)
                                 (take 3)
                                 (map (fn [n] [(keyword n) "TESTVAL"]))
                                 (into {}))]
        (pdf/write-fields! doc fields-to-write true {})
        (let [form (.getAcroForm (.getDocumentCatalog doc))]
          ;; After flatten, PDFBox leaves the form object but clears its fields.
          (is (empty? (.getFields form))
              "AcroForm should have no fields after flatten"))))))

;; =============================================================================
;; Regression test: flatten path must not log "missing /P entry" WARN lines.
;; PDFBox uses SLF4J; slf4j-simple writes to System.err, so we capture err.
;; =============================================================================

(defn- with-captured-err [f]
  (let [baos (ByteArrayOutputStream.)
        original System/err]
    (try
      (System/setErr (PrintStream. baos true "UTF-8"))
      (f)
      (finally
        (System/setErr original)))
    (.toString baos "UTF-8")))

(deftest flatten-does-not-emit-missing-p-warnings
  (testing "write-fields! with flatten=true does not warn about missing /P entries"
    (let [captured
          (with-captured-err
            (fn []
              (with-open [doc (load-template)]
                (pdf/write-fields! doc {} true {}))))]
      (is (not (re-find #"(?i)missing\s*/P\s*entry" captured))
          (str "Expected no 'missing /P entry' warnings, got:\n" captured)))))
