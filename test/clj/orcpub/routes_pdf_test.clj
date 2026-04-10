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

(deftest character-pdf-2-truthy-non-boolean-flattens
  (testing "Non-boolean truthy :flatten? follows Clojure truthiness => flattened"
    (let [resp (routes/character-pdf-2
                (make-req (minimal-fields {:flatten? "yes"})))]
      (is (= 200 (:status resp)))
      (with-open [doc (load-response-pdf resp)]
        (let [form (.getAcroForm (.getDocumentCatalog doc))]
          (is (or (nil? form) (empty? (.getFields form)))
              "any truthy value should result in flatten=true"))))))

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
