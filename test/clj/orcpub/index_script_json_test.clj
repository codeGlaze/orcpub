(ns orcpub.index-script-json-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.data.json :as data-json]
            [orcpub.fork.branding :as branding]
            [orcpub.dnd.e5.views-shared :as views-shared]
            [orcpub.index :as index]))

(def hostile "Ann</script><script>alert(1)</script> & co ")

(deftest an-operator-setting-cannot-close-the-branding-script
  (testing "operator settings are still written into the page, so they are escaped there"
    (with-redefs [index/branding-client-config (constantly {:app-name hostile})]
      (let [page (index/index-page {:nonce "n"})]
        (is (not (re-find #"alert\(1\)</script>" page)))
        (is (not (.contains page "Ann</script>")))
        (is (.contains page "Ann\\u003c/script\\u003e"))))))

(deftest script-json-reads-back-as-the-same-value
  (testing "the escapes are JSON escapes, so a parser sees the original text"
    (is (= {:name hostile}
           (data-json/read-str (index/script-json {:name hostile}) :key-fn keyword))))
  (is (not (re-find #"[<>&  ]" (index/script-json {:name hostile})))))

(deftest script-json-round-trips-the-values-libraries-write-differently
  (let [v {:name "Zoë Ångström 日本 / back\\slash \"quoted\""
           :separators "line\u2028para\u2029end"
           :whole 42 :decimal 1.5 :yes true :none nil
           :nested {:some-key ["a" 1 {:deep "x"}]}}
        out (index/script-json v)]
    (is (= v (data-json/read-str out :key-fn keyword)))
    (is (not (re-find #"[<>&\u2028\u2029]" out)))))

(deftest the-home-page-text-does-not-read-as-french
  ;; Chrome reads "5e" as French "fifth". The splash buttons keep "D&D 5e" as text, for search
  ;; engines, marked as English that is not to be translated; the page itself stays translatable.
  (let [page (index/index-page {:nonce "n"} true)
        written (re-seq #"<span[^>]*>D&amp;D 5e </span>" page)]
    (is (= 14 (count written)) "every splash button writes D&D 5e as text")
    (is (every? #(and (.contains % "translate=\"no\"") (.contains % "lang=\"en\"")) written))
    (is (.contains page "<html lang=\"en\""))
    (is (not (.contains page "notranslate")))))

(deftest the-home-page-carries-a-whats-new-link
  (is (re-find #"What.{1,6}s New" (index/index-page {:nonce "n"} true))))

(deftest the-support-pills-show-only-the-links-a-site-sets
  (let [labels #(->> % flatten (filter string?) set)]
    (testing "each pill only when its link is set; What's New only where asked for"
      (with-redefs [branding/social-links {:patreon "https://patreon.example" :dev-tip ""}]
        (is (= #{"Support the Server"} (labels (views-shared/support-pills))))
        (is (= #{"What's New" "Support the Server"} (labels (views-shared/support-pills :whats-new? true)))))
      (with-redefs [branding/social-links {:patreon "" :dev-tip "https://tip.example"}]
        (is (= #{"Fuel the Dev"} (labels (views-shared/support-pills))))))
    (testing "a site with neither link shows nothing in the footer"
      (with-redefs [branding/social-links {:patreon "" :dev-tip ""}]
        (is (nil? (views-shared/support-pills)))))))

(deftest the-load-rescue-notice-stays-on-a-short-screen
  (let [html (str (index/load-rescue "n"))]
    (is (.contains html "top:max(8px,min(404px,calc(100vh - 140px)))"))
    (is (not (.contains html "top:404px")))))
