(ns orcpub.index-script-json-test
  (:require [clojure.test :refer [deftest is testing]]
            [cheshire.core :as cheshire]
            [orcpub.fork.branding :as branding]
            [orcpub.index :as index]))

(def hostile "Ann</script><script>alert(1)</script> & co ")

(deftest an-operator-setting-cannot-close-the-branding-script
  (testing "operator settings are still written into the page, so they are escaped there"
    (with-redefs [branding/client-config (constantly {:app-name hostile})]
      (let [page (index/index-page {:nonce "n"})]
        (is (not (re-find #"alert\(1\)</script>" page)))
        (is (not (.contains page "Ann</script>")))
        (is (.contains page "Ann\\u003c/script\\u003e"))))))

(deftest script-json-reads-back-as-the-same-value
  (testing "the escapes are JSON escapes, so a parser sees the original text"
    (is (= {:name hostile}
           (cheshire/parse-string (index/script-json {:name hostile}) true))))
  (is (not (re-find #"[<>&  ]" (index/script-json {:name hostile})))))

(deftest the-home-page-text-does-not-read-as-french
  ;; Chrome reads "5e" as French "fifth"; the splash buttons draw their "D&D 5e" prefix in CSS.
  (let [page (index/index-page {:nonce "n"} true)]
    (is (<= (count (re-seq #">[^<>]*\b5e\b[^<>]*<" page)) 1) "only the <title> carries 5e as text")
    (is (.contains page "<html lang=\"en\""))
    (is (not (.contains page "notranslate")))))

(deftest the-home-page-carries-a-whats-new-link
  (is (re-find #"What.{1,6}s New" (index/index-page {:nonce "n"} true))))
