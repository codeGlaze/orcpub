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
            [cljs-http.client :as http]
            [cljs.core.async :as async]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.party :as party5e]
            [orcpub.dnd.e5.folder :as folder5e]
            [orcpub.dnd.e5.api-subs :as api-subs]
            ;; Side effect: registers subscriptions. Aliased as `subs` so
            ;; tests can reach user-sub-on-401.
            [orcpub.dnd.e5.subs :as subs]
            ;; Registers :clear-login and :set-user-data.
            [orcpub.dnd.e5.events]))

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
  (testing "a stale :user key with no token must NOT trigger an HTTP fetch"
    ;; REGRESSION GUARD, rewritten to assert the right thing. The HTTP fetch is
    ;; gated on [:user-data :token] (canonical, same as auth-headers); a leftover
    ;; [:user] key (no :token) must not slip past it. The OLD assertion
    ;; `(= [] result)` was WRONG: the :user sub passes through `(get db :user [])`,
    ;; so a populated :user returns that map, not [] — it never tested the guard.
    ;; Assert the guard DIRECTLY: http/get is never called.
    (reset! app-db {:user {:name "stale-user"}})
    (let [called? (atom false)]
      (with-redefs [http/get (fn [& _] (reset! called? true) (async/chan))]
        (let [result @(rf/subscribe [:user])]
          (is (false? @called?) "no token → no HTTP, despite the stale :user key")
          (is (= {:name "stale-user"} result) "sub passes the db value through unchanged"))))))

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
;; A loader's 401: the server no longer accepts the token
;; ---------------------------------------------------------------------------

(defn- dispatched-by
  "The events f dispatches, captured instead of queued."
  [f]
  (let [seen (atom [])]
    (with-redefs [rf/dispatch #(swap! seen conj %)]
      (f))
    @seen))

(deftest a-rejected-token-logs-out-then-routes-to-login
  (reset! app-db {:user-data {:token "t0ken"}})
  (is (= [[:clear-login] [:route-to-login]]
         (dispatched-by #(api-subs/rejected-token! app-db "t0ken" nil [::char5e/characters])))
      "a sub with no :on-401 routes to login, after logging out"))

(deftest a-rejected-token-logs-out-before-the-subs-own-on-401
  (reset! app-db {:user-data {:token "t0ken"}})
  (let [query (atom nil)]
    (is (= [[:clear-login]]
           (dispatched-by #(api-subs/rejected-token! app-db "t0ken" (fn [q] (reset! query q)) [:user true]))))
    (is (= [:user true] @query) "the sub's :on-401 still gets its query")))

(deftest a-401-for-a-replaced-token-leaves-the-new-login-alone
  (reset! app-db {:user-data {:token "new"}})
  (let [ran? (atom false)]
    (is (= [] (dispatched-by #(api-subs/rejected-token! app-db "old" (fn [_] (reset! ran? true)) [:user])))
        "the request went out before the new login, so its 401 says nothing about the new token")
    (is (false? @ran?))))

(deftest clear-login-drops-the-token-and-account-and-keeps-the-theme
  (reset! app-db {:user-data {:token "t0ken" :user-data {:username "kaylee"} :theme "dark-theme"}})
  (rf/dispatch-sync [:set-user-data (dissoc (:user-data @app-db) :user-data :token)])
  (is (= "t0ken" (get-in @app-db [:user-data :token]))
      "what the :user sub's 401 used to do: a merge cannot remove the token")
  (rf/dispatch-sync [:clear-login])
  (is (= {:theme "dark-theme"} (:user-data @app-db))))

(deftest the-user-sub-routes-to-login-only-when-required
  (is (= [[:route-to-login]] (dispatched-by #(subs/user-sub-on-401 [:user true]))))
  (is (= [] (dispatched-by #(subs/user-sub-on-401 [:user false]))))
  (is (= [] (dispatched-by #(subs/user-sub-on-401 [:user])))))
