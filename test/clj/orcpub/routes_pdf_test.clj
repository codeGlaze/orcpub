(ns orcpub.routes-pdf-test
  "Tests for the character-pdf-2 HTTP handler focused on the :flatten?
   request-param round-trip (replaces the old user-agent-sniffing path)."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.routes :as routes])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream)
           (org.apache.pdfbox Loader)
           (org.apache.pdfbox.pdmodel PDDocument)))

;; -----------------------------------------------------------------------------
;; Request / response helpers
;; -----------------------------------------------------------------------------

(defn- minimal-fields
  "Smallest fields map character-pdf-2 will accept. Uses style 1 / 0 spells
   which maps to fillable-char-sheetstyle-1-0-spells.pdf — a bundled resource."
  ([] (minimal-fields {}))
  ([overrides]
   (merge {:print-character-sheet-style? 1
           :character-name "Testy"
           :class-level "Barbarian 1"
           :player-name "Alice"}
          overrides)))

(defn- make-req
  "Build a fake Pedestal req map containing a form-params :body EDN string,
   exactly how the production handler consumes it."
  [fields]
  {:form-params {:body (pr-str fields)}})

(defn- response->bytes ^bytes [resp]
  (let [^ByteArrayInputStream in (:body resp)
        out (ByteArrayOutputStream.)]
    (.transferTo in out)
    (.toByteArray out)))

(defn- load-response-pdf ^PDDocument [resp]
  (Loader/loadPDF (response->bytes resp)))

(defn- has-fillable-form? [^PDDocument doc]
  (let [form (.getAcroForm (.getDocumentCatalog doc))]
    (boolean (and form (seq (.getFields form))))))

;; -----------------------------------------------------------------------------
;; :flatten? round-trip tests
;; -----------------------------------------------------------------------------

(deftest character-pdf-2-default-is-interactive
  (testing "Missing :flatten? defaults to interactive (fillable) PDF"
    (let [resp (routes/character-pdf-2 (make-req (minimal-fields)))]
      (is (= 200 (:status resp)))
      (with-open [doc (load-response-pdf resp)]
        (is (has-fillable-form? doc)
            "default path should leave the AcroForm fields intact")))))

(deftest character-pdf-2-explicit-false-is-interactive
  (testing ":flatten? false => interactive PDF"
    (let [resp (routes/character-pdf-2
                (make-req (minimal-fields {:flatten? false})))]
      (is (= 200 (:status resp)))
      (with-open [doc (load-response-pdf resp)]
        (is (has-fillable-form? doc))))))

(deftest character-pdf-2-true-is-flattened
  (testing ":flatten? true => AcroForm is flattened"
    (let [resp (routes/character-pdf-2
                (make-req (minimal-fields {:flatten? true})))]
      (is (= 200 (:status resp)))
      (with-open [doc (load-response-pdf resp)]
        (let [form (.getAcroForm (.getDocumentCatalog doc))]
          (is (or (nil? form) (empty? (.getFields form)))
              "flatten=true must leave no remaining form fields"))))))

(deftest character-pdf-2-nil-is-interactive
  (testing "Explicit :flatten? nil behaves like missing — interactive"
    (let [resp (routes/character-pdf-2
                (make-req (minimal-fields {:flatten? nil})))]
      (is (= 200 (:status resp)))
      (with-open [doc (load-response-pdf resp)]
        (is (has-fillable-form? doc))))))

(deftest character-pdf-2-truthy-non-boolean-does-not-flatten
  (testing "Non-boolean truthy :flatten? values (\"yes\", 1, {}, etc.) must
            NOT trigger flatten. The handler uses strict `(true? flatten?)`
            so a malformed client payload falls through to the safer
            interactive default rather than silently locking the sheet."
    (doseq [garbage ["yes" 1 "true" [] {} :true]]
      (testing (str ":flatten? " (pr-str garbage))
        (let [resp (routes/character-pdf-2
                    (make-req (minimal-fields {:flatten? garbage})))]
          (is (= 200 (:status resp)))
          (with-open [doc (load-response-pdf resp)]
            (is (has-fillable-form? doc)
                (str "non-boolean :flatten? " (pr-str garbage)
                     " must not flatten"))))))))

(deftest character-pdf-2-ignores-user-agent
  (testing "User-Agent header has no effect on flatten behavior (regression for removed UA sniff)"
    (let [firefox-req (-> (make-req (minimal-fields))
                          (assoc-in [:headers "user-agent"]
                                    "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0"))
          resp (routes/character-pdf-2 firefox-req)]
      (is (= 200 (:status resp)))
      (with-open [doc (load-response-pdf resp)]
        (is (has-fillable-form? doc)
            "Firefox UA must no longer force flatten=true")))))

(deftest character-pdf-2-invalid-edn-throws-ex-info
  (testing "Malformed EDN body raises the documented ex-info (not a raw parser error)"
    (let [req {:form-params {:body "{{{not edn"}}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Invalid character data format"
                            (routes/character-pdf-2 req))))))

;; -----------------------------------------------------------------------------
;; Handler-level value round-trip: the posted character-name must appear in
;; the response PDF's corresponding AcroForm field. This exercises the full
;; pipeline (edn parse -> write-fields! -> save -> load) and would catch a
;; regression that pdf_test.clj's unit tests cannot: e.g. the handler passing
;; the wrong `fields` map to write-fields!, or a ByteArrayOutputStream/save
;; bug that produces an unreadable PDF.
;; -----------------------------------------------------------------------------

(deftest character-pdf-2-character-name-round-trips
  (testing "Value posted in :character-name appears in the generated PDF"
    (let [resp (routes/character-pdf-2
                (make-req (minimal-fields {:character-name "Sir Round-Trip"})))]
      (is (= 200 (:status resp)))
      (with-open [doc (load-response-pdf resp)]
        (let [form (.getAcroForm (.getDocumentCatalog doc))
              field (.getField form "character-name")]
          (is (some? field)
              "character-name field must exist on the response PDF")
          (is (= "Sir Round-Trip" (.getValue field))
              "character-name value must round-trip through the full
               handler"))))))

;; -----------------------------------------------------------------------------
;; Smoke test: every sheet-style × spell-count combination must produce a
;; loadable PDF. Catches template resource renames, incomplete style rollouts,
;; and template-specific crashes in write-fields! (e.g. a field that exists
;; on style 1 but not style 3).
;; -----------------------------------------------------------------------------

(defn- fields-with-style [style spell-count]
  (let [base (minimal-fields {:print-character-sheet-style? style})]
    (if (zero? spell-count)
      base
      ;; Presence of :spellcasting-class-N drives template selection in the
      ;; handler (routes.clj:644-650). Value can be anything; write-fields!
      ;; silently skips unknown fields.
      (assoc base (keyword (str "spellcasting-class-" spell-count)) {}))))

(deftest character-pdf-2-smoke-test-all-styles-and-spell-counts
  (doseq [style (range 1 5)
          spell-count (range 0 7)]
    (testing (str "style=" style " spell-count=" spell-count)
      (let [resp (routes/character-pdf-2
                  (make-req (fields-with-style style spell-count)))]
        (is (= 200 (:status resp)))
        (with-open [doc (load-response-pdf resp)]
          (is (pos? (.getNumberOfPages doc))
              "response PDF must have at least one page")
          (is (has-fillable-form? doc)
              "response PDF must be fillable (default)"))))))
