(ns orcpub.pedestal-test
  "Pins the two defects that made every /assets/* request return 200 with an
   empty body on a non-English machine.

   1. parse-date must read the English HTTP date regardless of JVM locale.
      HTTP dates are always English (RFC 7231); an unlocalised
      DateTimeFormatter parses them with the default locale, so \"Mon\" is not
      a day name on a Spanish machine and it throws.

   2. The ETag interceptor's catch must return the CONTEXT. Returning
      log/error's value instead discards the response: Pedestal then has
      nothing to write and Jetty emits a bare 200. That is what made defect 1
      silent -- the exception was logged, but the response was already gone."
  (:require [io.pedestal.http]
            [io.pedestal.test]
            [orcpub.system]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [orcpub.pedestal :as pedestal])
  (:import [java.time ZonedDateTime]
           [java.time.format DateTimeFormatter]
           [java.util Locale]))

;; A real Last-Modified from the Font Awesome webjar, and the ETag the English
;; locale has always produced for it. Pinning the exact value matters: if it
;; ever changes, every cached ETag in the wild is invalidated.
(def ^:private sample-date "Mon, 06 Jul 2020 14:41:47 GMT")
(def ^:private expected-etag "1594046507000-58935")

(use-fixtures :once (fn [t] (let [saved (Locale/getDefault)]
                              (try (t) (finally (Locale/setDefault saved))))))

(deftest parse-date-produces-the-expected-etag
  (testing "the value is exactly what English-locale servers have always produced"
    ;; A value regression guard, NOT a guard against the locale defect. See
    ;; the next test for why an in-process test cannot catch that one.
    (is (= expected-etag (pedestal/parse-date sample-date 58935)))))

(deftest the-locale-hazard-this-fix-removes
  (testing "an unlocalised formatter really does throw on a valid HTTP date"
    ;; Why this is demonstrated rather than asserted against rfc822-formatter:
    ;; that formatter is a top-level def, so its locale is fixed when the
    ;; namespace LOADS -- before any test can call Locale/setDefault. A unit
    ;; test in this JVM therefore cannot reproduce the original failure, and a
    ;; test claiming to would pass whether or not the fix were present.
    ;;
    ;; The real guard is running this suite under a non-English JVM:
    ;;   lein test with -Duser.language=es -Duser.country=ES
    ;; which is green. What this test pins is that the HAZARD is real, so
    ;; nobody "simplifies" Locale/ENGLISH back out of the formatter.
    (let [saved (Locale/getDefault)]
      (try
        (Locale/setDefault (Locale/forLanguageTag "es-ES"))
        (let [unlocalised (DateTimeFormatter/ofPattern "EEE, dd MMM yyyy HH:mm:ss Z")
              localised   (DateTimeFormatter/ofPattern "EEE, dd MMM yyyy HH:mm:ss Z" Locale/ENGLISH)
              text        "Mon, 06 Jul 2020 14:41:47 +0000"]
          (is (thrown? Exception (ZonedDateTime/parse text unlocalised))
              "if this stops throwing, the hazard is gone and this test can go")
          (is (some? (ZonedDateTime/parse text localised))
              "pinning the locale is what makes it parse"))
        (finally (Locale/setDefault saved))))))

(deftest parse-date-passes-through-nil
  (testing "no Last-Modified header means no etag, not an exception"
    (is (nil? (pedestal/parse-date nil 123)))))

(defn- leave [ctx]
  ((:leave pedestal/etag-interceptor) ctx))

(deftest etag-interceptor-returns-context-when-it-throws
  (testing "a response is never discarded, whatever the interceptor hits"
    ;; An unparseable Last-Modified makes parse-date throw, which is exactly
    ;; what a non-English locale used to do with a perfectly valid header.
    (let [ctx {:request  {:headers {}}
               :response {:status 200
                          :body "hello"
                          :headers {"Last-Modified" "not a date at all"
                                    "Content-Length" "5"}}}
          out (leave ctx)]
      (is (map? out) "the catch must return the context, not log/error's value")
      (is (= 200 (get-in out [:response :status])))
      (is (= "hello" (get-in out [:response :body]))
          "the body survived: discarding it is what produced an empty 200"))))

(deftest etag-interceptor-sets-an-etag-on-a-good-response
  (testing "the normal path still works"
    (let [ctx {:request  {:headers {}}
               :response {:status 200
                          :body "hello"
                          :headers {"Last-Modified" sample-date
                                    "Content-Length" "58935"}}}
          out (leave ctx)]
      (is (= expected-etag (get-in out [:response :headers "etag"]))))))

(deftest etag-interceptor-honours-if-none-match
  (testing "a matching if-none-match yields 304 with no body"
    (let [ctx {:request  {:headers {"if-none-match" expected-etag}}
               :response {:status 200
                          :body "hello"
                          :headers {"Last-Modified" sample-date
                                    "Content-Length" "58935"}}}
          out (leave ctx)]
      (is (= 304 (get-in out [:response :status])))
      (is (nil? (get-in out [:response :body]))))))

(defn- referrer-leave
  "The :leave of orcpub.pedestal's referrer-policy interceptor, or nil when there is none."
  []
  (some-> (ns-resolve 'orcpub.pedestal 'referrer-policy-interceptor) deref :leave))

(deftest every-page-sends-only-its-origin-to-other-sites
  (let [leave (or (referrer-leave) identity)]
    (testing "a page that says nothing gets strict-origin-when-cross-origin: other sites see the domain"
      (is (= "strict-origin-when-cross-origin"
             (get-in (leave {:response {:status 200 :headers {}}}) [:response :headers "Referrer-Policy"]))))
    (testing "a page that set its own keeps it (the password-reset page sends nothing at all)"
      (is (= "no-referrer"
             (get-in (leave {:response {:status 200 :headers {"Referrer-Policy" "no-referrer"}}})
                     [:response :headers "Referrer-Policy"]))))))

(defn- service-response
  "The response of a minimal service built the way system.clj builds the real one (default
   interceptors, then the referrer policy first), for a GET of `path`."
  [path]
  (let [with-policy (or (some-> (ns-resolve 'orcpub.system 'with-referrer-policy) deref) identity)
        service (-> {:io.pedestal.http/routes #{["/here" :get (fn [_] {:status 200 :body "ok"}) :route-name ::here]}
                     :io.pedestal.http/resource-path "/public"
                     :io.pedestal.http/type :jetty}
                    io.pedestal.http/default-interceptors
                    with-policy
                    io.pedestal.http/create-servlet
                    :io.pedestal.http/service-fn)]
    (io.pedestal.test/response-for service :get path)))

(deftest static-files-and-404s-carry-the-referrer-policy-too
  (doseq [path ["/favicon.ico" "/no-such-page"]]
    (is (= "strict-origin-when-cross-origin" (get-in (service-response path) [:headers "Referrer-Policy"])) path)))
