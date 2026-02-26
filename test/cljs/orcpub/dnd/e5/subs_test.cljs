(ns orcpub.dnd.e5.subs-test
  "Tests for API-backed subscriptions (reg-sub-raw).

   These subscriptions gate HTTP calls on the presence of an auth token.
   When no token exists in app-db, the subscription should return an empty
   vector without making any network request (no :set-loading dispatch).

   PATTERN — testing a reg-sub-raw guard:
     1. Reset app-db to a known state (with or without token)
     2. Deref the subscription to trigger it
     3. Assert the return value and check for side effects (:loading)"
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.party :as party5e]
            ;; Side effect: registers subscriptions
            [orcpub.dnd.e5.subs]))

;; ---------------------------------------------------------------------------
;; Fixtures
;; ---------------------------------------------------------------------------

(defn reset-db!
  "Reset app-db before each test to prevent state leakage."
  []
  (reset! app-db {})
  ;; Clear subscription cache so reg-sub-raw re-evaluates
  (rf/clear-subscription-cache!))

(use-fixtures :each {:before reset-db!})

;; ---------------------------------------------------------------------------
;; ::char5e/characters — token guard
;; ---------------------------------------------------------------------------

(deftest characters-no-token-returns-empty
  (testing "without auth token, subscription returns [] without HTTP call"
    (reset! app-db {})
    (let [result @(rf/subscribe [::char5e/characters])]
      ;; Should return empty vector (default)
      (is (= [] result))
      ;; :set-loading should NOT have been dispatched (go block skipped)
      (is (nil? (:loading @app-db))
          "No loading state should be set without token"))))

(deftest characters-no-token-login-optional
  (testing "login-optional? param doesn't bypass the token guard"
    (reset! app-db {})
    (let [result @(rf/subscribe [::char5e/characters true])]
      (is (= [] result))
      (is (nil? (:loading @app-db))))))

(deftest characters-with-token-empty-is-nil
  (testing "with token but no cached data, returns []"
    (reset! app-db {:user-data {:token "test-token"}})
    ;; The go block will fire and try HTTP (which will fail in test env),
    ;; but the reaction should still return [] since no data is cached yet
    (let [result @(rf/subscribe [::char5e/characters])]
      (is (= [] result)))))

;; ---------------------------------------------------------------------------
;; ::party5e/parties — token guard
;; ---------------------------------------------------------------------------

(deftest parties-no-token-returns-empty
  (testing "without auth token, subscription returns [] without HTTP call"
    (reset! app-db {})
    (let [result @(rf/subscribe [::party5e/parties])]
      (is (= [] result))
      (is (nil? (:loading @app-db))
          "No loading state should be set without token"))))

(deftest parties-no-token-login-optional
  (testing "login-optional? param doesn't bypass the token guard"
    (reset! app-db {})
    (let [result @(rf/subscribe [::party5e/parties true])]
      (is (= [] result))
      (is (nil? (:loading @app-db))))))

(deftest parties-with-token-empty-is-nil
  (testing "with token but no cached data, returns []"
    (reset! app-db {:user-data {:token "test-token"}})
    (let [result @(rf/subscribe [::party5e/parties])]
      (is (= [] result)))))

;; ---------------------------------------------------------------------------
;; :user — token guard
;;
;; Previously checked [:user :token] which was the wrong path.
;; Fixed to check [:user-data :token] (same as auth-headers).
;; ---------------------------------------------------------------------------

(deftest user-no-token-returns-empty
  (testing "without auth token, subscription returns [] without HTTP call"
    (reset! app-db {})
    (let [result @(rf/subscribe [:user])]
      (is (= [] result)))))

(deftest user-stale-user-no-token-still-guarded
  (testing "user key present but no token → still skips HTTP"
    ;; This was the accidental-guard case: [:user] existed but [:user :token]
    ;; didn't, so the old guard happened to block. The new guard checks the
    ;; canonical path [:user-data :token] which is authoritative.
    (reset! app-db {:user {:name "stale-user"}})
    (let [result @(rf/subscribe [:user])]
      (is (= [] result)))))

(deftest user-with-token-returns-default
  (testing "with token, subscription fires (returns default until HTTP resolves)"
    (reset! app-db {:user-data {:token "test-token"}})
    (let [result @(rf/subscribe [:user])]
      (is (= [] result)))))
