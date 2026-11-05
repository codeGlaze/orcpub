(ns orcpub.dnd.e5.event-utils-test
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.modifiers :as mod]
            [orcpub.dnd.e5.event-utils :as eu]))

;; -- get-auth-token --
;;
;; Canonical access for the JWT auth token at [:user-data :token].
;; Dual use: retrieval (as a string value for HTTP headers) and predicate
;; (nil-is-falsy, used directly under `when` in reg-sub-raw guards).
;; See event_utils.cljc docstring for the full rationale.

(deftest get-auth-token-test
  (testing "returns token string when present at canonical path"
    (is (= "abc123"
           (eu/get-auth-token {:user-data {:token "abc123"}}))))

  (testing "returns nil for empty db"
    (is (nil? (eu/get-auth-token {}))))

  (testing "returns nil when user-data is present but token is absent"
    ;; This is the 'theme persists after logout' case — :user-data still
    ;; has :theme but :token has been dissoced by :clear-login.
    (is (nil? (eu/get-auth-token {:user-data {:theme "dark-theme"}}))))

  (testing "returns nil when user-data is an empty map"
    (is (nil? (eu/get-auth-token {:user-data {}}))))

  (testing "ignores stale db[:user] key (regression guard for 45ef969 typo)"
    ;; db[:user] is populated by :follow-user / :unfollow-user with a
    ;; {:following [...]} shape. It has NEVER contained a :token key.
    ;; get-auth-token must read from [:user-data :token], not [:user :token].
    (is (nil? (eu/get-auth-token {:user {:following ["alice"]}}))))

  (testing "as a predicate under `when`: nil is falsy (logged-out)"
    (is (nil? (when (eu/get-auth-token {}) :guarded-action))))

  (testing "as a predicate under `when`: token is truthy (logged-in)"
    (is (= :guarded-action
           (when (eu/get-auth-token {:user-data {:token "t"}})
             :guarded-action)))))

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
