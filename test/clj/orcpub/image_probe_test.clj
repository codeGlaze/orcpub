(ns orcpub.image-probe-test
  "The endpoint the builder asks before exporting, for a picture the browser was
   not allowed to read.

   It answers a boolean and never the picture. It needs no login, so what it must
   not become is a general-purpose fetcher: every address rule that guards the
   export guards this too, and the answer carries nothing but yes or no."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.routes :as routes]))

(defn- probe [url]
  (routes/image-probe {:transit-params {:url url}}))

(deftest an-address-the-server-may-not-fetch-answers-no
  (testing "not a URL at all"
    (is (= "false" (:body (probe nil))))
    (is (= "false" (:body (probe ""))))
    (is (= "false" (:body (probe "not a url")))))
  (testing "schemes the export refuses without a lookup"
    (is (= "false" (:body (probe "file:///etc/passwd"))))
    (is (= "false" (:body (probe "ftp://example.com/x.png"))))
    (is (= "false" (:body (probe "jar:file:///x.jar!/y.png")))))
  (testing "addresses that are not the public internet"
    ;; The same rule that keeps the export off cloud metadata and the private
    ;; network. Answering for these would make the endpoint a port scanner whose
    ;; result is one boolean per request.
    (is (= "false" (:body (probe "http://169.254.169.254/latest/meta-data/"))))
    (is (= "false" (:body (probe "http://127.0.0.1:9/x.png"))))
    (is (= "false" (:body (probe "http://10.0.0.1/x.png"))))
    (is (= "false" (:body (probe "http://192.168.1.1/x.png"))))))

(deftest the-answer-is-always-a-plain-200
  ;; A picture that cannot be had is an ordinary answer, not an error: the builder
  ;; reads the body, and a 500 here would show up as a broken app rather than as a
  ;; picture it has to ask about.
  (doseq [url [nil "" "file:///etc/passwd" "http://127.0.0.1:9/x.png"]]
    (is (= 200 (:status (probe url))) (str "for " (pr-str url))))
  (is (every? #{"true" "false"}
              (map #(:body (probe %))
                   [nil "http://127.0.0.1:9/x.png" "file:///etc/passwd"]))
      "the body is only ever the boolean, never a message or the bytes"))
