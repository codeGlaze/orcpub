(ns orcpub.index-script-json-test
  (:require [clojure.test :refer [deftest is testing]]
            [cheshire.core :as cheshire]
            [orcpub.dnd.e5.portrait-assets :as portrait-assets]
            [orcpub.index :as index]))

(def hostile "Ann</script><script>alert(1)</script> & co ")

(deftest an-artist-credit-cannot-close-the-branding-script
  (let [before (portrait-assets/current-overrides)]
    (try
      (portrait-assets/set-artist-overrides! {:house-pack {:artist/name hostile}})
      (let [page (index/index-page {:nonce "n"})]
        (is (not (re-find #"alert\(1\)</script>" page)))
        (is (not (.contains page "Ann</script>")))
        (is (.contains page "Ann\\u003c/script\\u003e")))
      (finally
        (portrait-assets/set-artist-overrides! before)))))

(deftest script-json-reads-back-as-the-same-value
  (testing "the escapes are JSON escapes, so a parser sees the original text"
    (is (= {:name hostile}
           (cheshire/parse-string (index/script-json {:name hostile}) true))))
  (is (not (re-find #"[<>&  ]" (index/script-json {:name hostile})))))
