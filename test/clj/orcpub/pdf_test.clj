(ns orcpub.pdf-test
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [orcpub.pdf :as pdf])
  (:import (org.apache.pdfbox Loader)
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
  (testing "Non-widget annotations are left alone — the `instance?
            PDAnnotationWidget` filter keeps the helper from rewriting /P on
            links, highlights, stamps, etc., which could have their own
            semantics. Encoded as a real invariant (DA untouched) rather than
            just 'does not throw'."
    (with-open [doc (PDDocument.)]
      (let [page-a (PDPage.)
            page-b (PDPage.)
            link (PDAnnotationLink.)]
        (.addPage doc page-a)
        (.addPage doc page-b)
        ;; Explicitly point the link's /P at page-b then attach it to page-a's
        ;; annotations. A naive helper that drops the instance? check would
        ;; overwrite /P to page-a; a correct one leaves it alone.
        (.setPage link page-b)
        (.addAnnotation page-a link)
        (is (identical? page-b (.getPage link))
            "precondition: link's /P points to page-b")
        (fix-widget-page-refs! doc)
        (is (identical? page-b (.getPage link))
            "non-widget annotation /P must not be modified")))))

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

(deftest write-fields-interactive-round-trips-values
  (testing "flatten=false: values passed to write-fields! are actually written
            into their target fields AND the form stays fillable. Asserts
            `.getValue` on a specific field, not just 'form still has fields'
            — the weaker assertion would pass even if every setValue silently
            dropped its argument."
    (with-open [doc (load-template)]
      ;; character-name is a text field on every bundled template.
      (pdf/write-fields! doc {:character-name "Testy McTestface"} false {})
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            field (.getField form "character-name")]
        (is (some? form) "AcroForm must still exist (not flattened)")
        (is (seq (.getFields form))
            "form must still have fields after non-flatten write")
        (is (some? field) "character-name field must exist on the template")
        (is (= "Testy McTestface" (.getValue field))
            "the value passed in must be retrievable via getValue")))))

(deftest write-fields-silently-skips-unknown-fields
  (testing "Unknown field names are a silent no-op (per write-fields! contract)"
    (with-open [doc (load-template)]
      ;; Mix of valid + nonsense keys; the call must not throw.
      (is (nil?
           (pdf/write-fields! doc
                              {:character-name "A"
                               :this-field-does-not-exist "B"
                               :and-neither-does-this false}
                              false
                              {})))
      ;; And the valid one still landed.
      (let [form (.getAcroForm (.getDocumentCatalog doc))]
        (is (= "A" (.getValue (.getField form "character-name"))))))))

(deftest write-fields-checkbox-on-off
  (testing "Checkbox fields: truthy => \"Yes\", falsey => \"Off\" (the
            actual PDFBox check-box values expected by AcroForm)."
    (with-open [doc (load-template)]
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            ;; Find any checkbox in the template to test against.
            checkbox-name (some (fn [f]
                                  (when (instance?
                                         org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox
                                         f)
                                    (.getFullyQualifiedName f)))
                                (.getFields form))]
        (if-not checkbox-name
          (is false "template must contain at least one checkbox")
          (do
            (pdf/write-fields! doc {(keyword checkbox-name) true} false {})
            (is (= "Yes" (.getValue (.getField form checkbox-name)))
                "truthy should set the checkbox to \"Yes\"")
            (pdf/write-fields! doc {(keyword checkbox-name) false} false {})
            (is (= "Off" (.getValue (.getField form checkbox-name)))
                "falsey should set the checkbox to \"Off\"")))))))

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
;; Regression test: the /P fix must render the flatten-time warning impossible.
;;
;; The earlier approach captured System.err and grepped for "missing /P entry",
;; which is fragile: it depends on slf4j-simple being the active backend AND
;; on it writing to stderr. Swap in logback (common) and the capture goes
;; silent — the test passes vacuously.
;;
;; Better: assert the *data invariant* that PDFBox's flatten path is looking
;; for. When `.flatten` encounters a widget whose `.getPage` is nil, it warns
;; (and falls back to a slow scan). If every widget has a non-nil page ref
;; going into flatten, the warning cannot fire. So we:
;;   1. Load the raw template and assert that at least one widget IS missing
;;      its /P — this is the positive control. If the template is ever
;;      re-authored to include /P on every widget, the control fires and
;;      someone has to rethink the test rather than it silently becoming a
;;      no-op.
;;   2. Run fix-widget-page-refs! and assert that ZERO widgets are missing
;;      their /P afterward.
;; =============================================================================

(defn- orphan-widgets [^PDDocument doc]
  (for [page (.getPages doc)
        ann (.getAnnotations page)
        :when (and (instance? PDAnnotationWidget ann)
                   (nil? (.getPage ^PDAnnotationWidget ann)))]
    ann))

(deftest fix-widget-page-refs-eliminates-flatten-warnings
  (testing "Positive control: the bundled template contains widgets with no
            /P entry — which is exactly the condition PDFBox.flatten warns
            about."
    (with-open [doc (load-template)]
      (is (pos? (count (orphan-widgets doc)))
          "template must contain at least one widget missing /P;
           otherwise the /P fix is unnecessary and this test is vacuous")))
  (testing "After fix-widget-page-refs!, every widget in the document has a
            page reference, making the flatten-time warning impossible."
    (with-open [doc (load-template)]
      (fix-widget-page-refs! doc)
      (is (zero? (count (orphan-widgets doc)))
          "fix-widget-page-refs! must populate /P on every orphaned widget"))))

;; =============================================================================
;; Template sanity: every bundled sheet style × spell-count combination must
;; load as a valid PDF with an AcroForm. Catches a resource being corrupted,
;; missing, or the template generator producing an unparseable file.
;; =============================================================================

(defn- all-template-names []
  (for [style (range 1 5)
        spells (range 0 7)]
    (str "fillable-char-sheetstyle-" style "-" spells "-spells.pdf")))

(deftest all-bundled-templates-load-with-acroform
  (doseq [resource-name (all-template-names)]
    (testing (str "template loads: " resource-name)
      (with-open [in (.openStream (io/resource resource-name))
                  doc (Loader/loadPDF (.readAllBytes in))]
        (is (pos? (.getNumberOfPages doc))
            "template must have at least one page")
        (let [form (.getAcroForm (.getDocumentCatalog doc))]
          (is (some? form) "template must expose an AcroForm")
          (is (seq (.getFields form))
              "AcroForm must have at least one field"))))))
