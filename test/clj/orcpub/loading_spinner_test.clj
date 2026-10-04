(ns orcpub.loading-spinner-test
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.loading-spinner :as spinner]
            [orcpub.index :as index]))

(deftest the-request-picks-only-a-known-spinner
  (doseq [k spinner/kinds]
    (is (= k (spinner/pick (name k)))))
  (testing "anything else is a random known one, never what was asked"
    (doseq [junk [nil "" "bogus" "<script>" "../d20"]]
      (is (some #{(spinner/pick junk)} spinner/kinds) (pr-str junk))))
  (is (= (set spinner/kinds) (set (repeatedly 200 #(spinner/pick nil))))
      "random reaches all three"))

(deftest each-spinner-renders-its-own-shape
  (is (re-find #"<svg class=\"ls-spiral\"" (spinner/markup :spiral)))
  (is (= 20 (count (re-seq #"matrix3d\(" (spinner/markup :d20)))) "a d20 has twenty faces")
  (is (re-find #"ls-spell-outer.*ls-spell-inner.*ls-spell-core" (spinner/markup :spell)))
  (testing "numbers use a decimal point whatever the server locale"
    (doseq [k spinner/kinds]
      (is (not (re-find #"\d,\d+[ L\"Z]" (spinner/markup k))) (name k)))))

(deftest the-page-carries-the-spinner-not-the-gif
  (let [page (index/index-page {:nonce "n" :spinner-kind :d20})]
    (is (re-find #"data-spinner=\"d20\"" page) "the markup is inserted, not escaped")
    (is (not (re-find #"spiral\.gif" page)))
    (is (re-find #"@keyframes ls-roll" page) "and its styles are in the head")))
