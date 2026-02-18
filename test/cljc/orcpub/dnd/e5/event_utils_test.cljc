(ns orcpub.dnd.e5.event-utils-test
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.modifiers :as mod]
            [orcpub.dnd.e5.event-utils :as eu]))

;; -- auth-headers --

(deftest auth-headers-test
  (testing "returns Authorization header when token present"
    (is (= {"Authorization" "Token abc123"}
           (eu/auth-headers {:user-data {:token "abc123"}}))))

  (testing "returns empty map when token is nil"
    (is (= {} (eu/auth-headers {:user-data {:token nil}}))))

  (testing "returns empty map when user-data is missing"
    (is (= {} (eu/auth-headers {}))))

  (testing "returns empty map for empty db"
    (is (= {} (eu/auth-headers nil)))))

;; -- show-generic-error --

(deftest show-generic-error-test
  (testing "returns dispatch vector"
    (let [[event-kw body] (eu/show-generic-error)]
      (is (= :show-error-message event-kw))
      (is (vector? body))
      (is (= :div (first body))))))

;; -- mod-cfg --

(deftest mod-cfg-test
  (testing "builds modifier config map"
    (is (= {::mod/key :ability ::mod/args '(:str)}
           (eu/mod-cfg :ability :str))))

  (testing "supports multiple args"
    (is (= {::mod/key :skill ::mod/args '(:athletics :proficiency)}
           (eu/mod-cfg :skill :athletics :proficiency))))

  (testing "no args produces nil args"
    (is (= {::mod/key :speed ::mod/args nil}
           (eu/mod-cfg :speed)))))

;; -- mod-key --

(deftest mod-key-test
  (testing "ability modifier key includes first arg"
    (is (= [:ability :str]
           (eu/mod-key (eu/mod-cfg :ability :str)))))

  (testing "ability-override modifier key includes first arg"
    (is (= [:ability-override :dex]
           (eu/mod-key (eu/mod-cfg :ability-override :dex)))))

  (testing "default modifier key uses all args"
    (is (= [:speed '(30)]
           (eu/mod-key (eu/mod-cfg :speed 30))))))

;; -- compare-mod-keys --

(deftest compare-mod-keys-test
  (testing "same keys are equal"
    (is (zero? (eu/compare-mod-keys
                (eu/mod-cfg :ability :str)
                (eu/mod-cfg :ability :str)))))

  (testing "different ability args sort correctly"
    (is (neg? (eu/compare-mod-keys
               (eu/mod-cfg :ability :con)
               (eu/mod-cfg :ability :str)))))

  (testing "different key types sort correctly"
    (is (neg? (eu/compare-mod-keys
               (eu/mod-cfg :ability :str)
               (eu/mod-cfg :speed 30))))))

;; -- default-mod-set --

(deftest default-mod-set-test
  (testing "converts plain set to sorted-set"
    (let [items #{(eu/mod-cfg :ability :str)
                  (eu/mod-cfg :ability :dex)}
          result (eu/default-mod-set items)]
      (is (sorted? result))
      (is (set? result))
      (is (= 2 (count result)))))

  (testing "preserves existing sorted-set"
    (let [sorted (into (sorted-set-by eu/compare-mod-keys)
                       [(eu/mod-cfg :ability :str)])]
      (is (identical? sorted (eu/default-mod-set sorted)))))

  (testing "converts vector to sorted-set"
    (let [items [(eu/mod-cfg :ability :str)
                 (eu/mod-cfg :ability :dex)]
          result (eu/default-mod-set items)]
      (is (sorted? result))
      (is (set? result))
      (is (= 2 (count result))))))
