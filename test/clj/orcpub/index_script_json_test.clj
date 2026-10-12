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
