(ns orcpub.image-probe-test
  "The endpoint the builder asks before exporting, for a picture the browser was
   not allowed to read.

   It answers WHY, not just whether, because a 404 and a 2 MB photograph are the
   same blank space on the sheet otherwise and the person is left guessing. It
   never answers with the picture: it needs no login, so returning fetched bytes
   would make it a general-purpose proxy.

   Every address rule that guards the export guards this too, and all of them
   collapse to one code -- an endpoint that distinguished `private range` from
   `no such host` would be a network map with one request per answer."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.routes :as routes]))

(def ^:private reasons
  #{"ok" "blocked-address" "not-found" "refused" "redirect" "host-error"
    "too-large" "too-many-pixels" "timeout" "not-an-image" "unreachable"
    "rate-limited" "unknown"})

(defn- probe [url]
  (:body (routes/image-probe {:transit-params {:url url}})))

(deftest an-address-the-server-may-not-fetch-says-so-and-no-more
  (testing "not a URL at all"
    (is (= "blocked-address" (probe nil)))
    (is (= "blocked-address" (probe "")))
    (is (= "blocked-address" (probe "not a url"))))
  (testing "schemes the export refuses without a lookup"
    (is (= "blocked-address" (probe "file:///etc/passwd")))
    (is (= "blocked-address" (probe "ftp://example.com/x.png")))
    (is (= "blocked-address" (probe "jar:file:///x.jar!/y.png"))))
  (testing "addresses that are not the public internet"
    ;; One code for all of them. Telling a private range apart from an unknown
    ;; host here would answer questions about the network this runs in.
    (is (= "blocked-address" (probe "http://169.254.169.254/latest/meta-data/")))
    (is (= "blocked-address" (probe "http://127.0.0.1:9/x.png")))
    (is (= "blocked-address" (probe "http://10.0.0.1/x.png")))
    (is (= "blocked-address" (probe "http://192.168.1.1/x.png")))))

(deftest the-answer-is-always-a-plain-200-and-a-known-code
  ;; A picture that cannot be had is an ordinary answer, not an error: the builder
  ;; reads the body, and a 500 here would look like a broken app rather than a
  ;; picture it has to ask about.
  (doseq [url [nil "" "file:///etc/passwd" "http://127.0.0.1:9/x.png"]]
    (is (= 200 (:status (routes/image-probe {:transit-params {:url url}})))
        (str "for " (pr-str url))))
  (doseq [url [nil "http://127.0.0.1:9/x.png" "file:///etc/passwd" "not a url"]]
    (is (contains? reasons (probe url))
        (str "unknown code for " (pr-str url) ": " (probe url)))))

(deftest nothing-that-is-not-a-reason-code-comes-back
  ;; No message, no URL, no bytes -- the builder maps the code to its own wording,
  ;; and anything else here would be something the server said leaking into the UI.
  (doseq [url [nil "http://10.0.0.1/x.png" "file:///etc/passwd"]]
    (let [body (probe url)]
      (is (re-matches #"[a-z-]+" body) (str "not a bare code: " (pr-str body)))
      (is (not (re-find #"http|/|\." body)) (str "leaks detail: " (pr-str body))))))
