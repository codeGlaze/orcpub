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
            [orcpub.dnd.e5.folder :as folder5e]
            ;; Side effect: registers subscriptions. Aliased as `subs` so
            ;; tests can reach the named helper fns extracted for testability
            ;; (user-sub-on-401-actions, etc.).
            [orcpub.dnd.e5.subs :as subs]))

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

;; ---------------------------------------------------------------------------
;; ::folder5e/folders — token guard
;; ---------------------------------------------------------------------------

(deftest folders-no-token-returns-empty
  (testing "without auth token, subscription returns [] without HTTP call"
    (reset! app-db {})
    (let [result @(rf/subscribe [::folder5e/folders])]
      (is (= [] result))
      (is (nil? (:loading @app-db))
          "No loading state should be set without token"))))

(deftest folders-with-token-returns-default
  (testing "with token but no cached data, returns []"
    (reset! app-db {:user-data {:token "test-token"}})
    (let [result @(rf/subscribe [::folder5e/folders])]
      (is (= [] result)))))

;; ---------------------------------------------------------------------------
;; :user sub — compound on-401 handler (P5 high-risk refactor target)
;;
;; The :user sub's 401 handler is the most complex of the five API-backed
;; subs: it dispatches BOTH :set-user-data (to clear login credentials)
;; AND conditionally dispatches :route-to-login (when the subscription
;; was invoked with required?=true).
;;
;; When P5 migrated :user to reg-api-sub, the compound logic was
;; extracted to `user-sub-on-401-actions` (pure) and `user-sub-on-401`
;; (side-effecting wrapper) so it could be unit-tested without stubbing
;; dispatch or mocking HTTP. The tests below pin the pure fn's
;; behavior in place across future refactors.
;; ---------------------------------------------------------------------------

(deftest user-sub-on-401-actions-required-clears-and-routes
  (testing "when required?=true, produces both :set-user-data and :route-to-login"
    (let [user-data {:token "abc"
                     :user-data {:username "alice" :email "a@example.com"}
                     :theme "dark-theme"}
          actions (subs/user-sub-on-401-actions user-data [:user true])]
      (is (= 2 (count actions))
          "Required 401 should produce exactly two dispatches")
      (is (= :set-user-data (ffirst actions))
          "First dispatch should clear login credentials")
      (is (= [:route-to-login] (second actions))
          "Second dispatch should bounce to login route"))))

(deftest user-sub-on-401-actions-required-set-user-data-preserves-theme
  (testing "the :set-user-data payload strips :token and :user-data but
            preserves other keys (e.g. :theme)"
    (let [user-data {:token "abc"
                     :user-data {:username "alice"}
                     :theme "dark-theme"}
          [[_ payload]] (subs/user-sub-on-401-actions user-data [:user true])]
      (is (nil? (:token payload)) ":token must be dropped")
      (is (nil? (:user-data payload)) "nested :user-data must be dropped")
      (is (= "dark-theme" (:theme payload))
          ":theme must survive the login clear"))))

(deftest user-sub-on-401-actions-not-required-clears-only
  (testing "when required?=false (or absent), only :set-user-data is produced
            — this is the whole point of the required? query arg"
    (let [user-data {:token "abc" :theme "dark-theme"}]
      (is (= 1 (count (subs/user-sub-on-401-actions user-data [:user false])))
          "required?=false should suppress the :route-to-login dispatch")
      (is (= 1 (count (subs/user-sub-on-401-actions user-data [:user nil])))
          "required?=nil should suppress the :route-to-login dispatch")
      (is (= 1 (count (subs/user-sub-on-401-actions user-data [:user])))
          "missing required? query arg should suppress the :route-to-login dispatch"))))

(deftest user-sub-on-401-actions-empty-user-data
  (testing "works with minimal user-data (defensive: nothing to strip)"
    (let [actions (subs/user-sub-on-401-actions {} [:user true])]
      (is (= 2 (count actions)))
      (is (= [:set-user-data {}] (first actions)))
      (is (= [:route-to-login] (second actions))))))

(deftest user-sub-on-401-actions-query-v-destructuring
  (testing "destructures [sub-key required?] shape regardless of sub-key"
    ;; The pure fn doesn't care about the first element of query-v —
    ;; it only cares about the second (required?). This test pins that
    ;; contract so a future refactor that changes the query-v shape
    ;; will flag here first.
    (let [user-data {:theme "dark-theme"}]
      (is (= (subs/user-sub-on-401-actions user-data [:user true])
             (subs/user-sub-on-401-actions user-data [:anything-else true]))
          "Only the second element of query-v (required?) affects output"))))
