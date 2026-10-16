(ns orcpub.dnd.e5.homebrew-guard-test
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.homebrew-guard :as guard]))

(deftest a-failing-entry-is-skipped-and-reported
  (let [reports (atom [])]
    (guard/set-reporter! #(swap! reports conj %))
    (try
      (is (= {:ok 1} (guard/guard-entry :t {:key :fine} (fn [_] {:ok 1}))))
      (is (nil? (guard/guard-entry :orcpub.dnd.e5/races {:key :bad :option-pack "P" :name "Bad"}
                                   (fn [_] (throw (ex-info "boom" {}))))))
      (is (= [[:orcpub.dnd.e5/races :bad "P"]] (map (juxt :content-type :key :option-pack) @reports)))
      (testing "a failure inside a lazy sequence is caught by guard-option, not later"
        (is (nil? (guard/guard-option :orcpub.dnd.e5/feats {:key :lazy}
                                      (fn [_] {:modifiers (map (fn [_] (throw (ex-info "later" {}))) [1])}))))
        (is (= :lazy (:key (last @reports)))))
      (finally
        (guard/set-reporter! nil)))))

(deftest a-scoped-reporter-is-put-back
  (let [outer (atom 0) inner (atom 0)]
    (guard/set-reporter! (fn [_] (swap! outer inc)))
    (try
      (guard/call-with-reporter (fn [_] (swap! inner inc))
                                #(guard/guard-entry :t {:key :x} (fn [_] (throw (ex-info "boom" {})))))
      (guard/guard-entry :t {:key :y} (fn [_] (throw (ex-info "boom" {}))))
      (is (= [1 1] [@inner @outer]))
      (finally
        (guard/set-reporter! nil)))))

