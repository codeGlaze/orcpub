(ns orcpub.dnd.e5.signin-fetch-test
  "Does signing in actually go and fetch the user's content?

   The API-backed subscriptions gate their fetch on a token:

     (reg-sub-raw ::char5e/characters
       (fn [app-db _]
         (when (:token (:user-data @app-db))   ; <- runs ONCE, on cache miss
           (go ... http/get ...))
         (ra/make-reaction #(get @app-db ::char5e/characters []))))

   The claim under test: because a reg-sub-raw handler runs only when the
   subscription is CREATED, dereferencing the chain while signed out caches a
   reaction with no fetch behind it, and signing in afterwards never triggers
   one.

   Nothing here is read off the source. http/get is replaced for the whole
   namespace and every URL recorded, and each test measures the DELTA in
   matching requests around one action:

     * go blocks are asynchronous, so the request has not been made at the
       instant a subscription is dereferenced — assertions wait a turn.
     * other namespaces in the suite leave requests in flight that land during
       that wait, so each test first DRAINS and clears the recorder — measured
       empirically: ten unrelated requests arrived mid-assertion before this
       was added.

   `a-signed-in-subscription-does-fetch` is the control. If it fails, the
   harness is lying and nothing else in this file means anything."
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures async]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [cljs-http.client :as http]
            [cljs.core.async :as a]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.events]
            [orcpub.dnd.e5.subs]
            [orcpub.dnd.e5.equipment-subs]))

(defonce ^:private urls (atom []))
(defonce ^:private real-get (atom nil))

(defn- install-stub! []
  (reset! real-get http/get)
  (set! http/get (fn [url & _]
                   (swap! urls conj (str url))
                   ;; a channel that never delivers: nothing downstream runs
                   (a/chan))))

(defn- restore-stub! []
  (when @real-get (set! http/get @real-get)))

(use-fixtures :once {:before install-stub! :after restore-stub!})

(def ^:private signed-in {:user-data {:username "kaylee" :token "t"}})

(defn- hits
  "How many recorded requests so far match re."
  [re]
  (count (filter #(re-find re %) @urls)))

(defn- after-tick
  "Run f once queued go blocks have had a chance to start."
  [f]
  (js/setTimeout f 25))

(defn- from-quiet
  "Let requests still in flight from earlier tests land, then clear the
   recorder so what follows measures only this test's traffic."
  [f]
  (js/setTimeout (fn [] (reset! urls []) (f)) 80))

;; ---------------------------------------------------------------------------
;; Control
;; ---------------------------------------------------------------------------

(deftest a-signed-in-subscription-does-fetch
  (testing "signed in from the start, the characters subscription issues a request"
    (async done
      (from-quiet
       (fn []
         (reset! app-db signed-in)
         (rf/clear-subscription-cache!)
         @(rf/subscribe [::char5e/characters])
         (after-tick
          (fn []
            (is (= 1 (hits #"character-summaries"))
                "the stub and the timing can observe a real fetch")
            (done))))))))

;; ---------------------------------------------------------------------------
;; The bug, on a subscription this branch did NOT fix
;; ---------------------------------------------------------------------------

(deftest characters-are-never-fetched-if-the-chain-was-read-signed-out
  (async done
    (from-quiet
     (fn []
       (reset! app-db {})
       (rf/clear-subscription-cache!)
       ;; Signed out, something reads the chain — the builder's pickers do.
       @(rf/subscribe [::char5e/characters])
       (after-tick
        (fn []
          (is (= 0 (hits #"character-summaries"))
              "no token, so no request — correct so far")
          (reset! urls [])
          ;; The user signs in. The subscription is already cached, so its
          ;; handler — and its token check, and its fetch — never run again.
          (swap! app-db merge signed-in)
          @(rf/subscribe [::char5e/characters])
          (after-tick
           (fn []
             (is (= 0 (hits #"character-summaries"))
                 "signing in triggered no fetch — the character list stays
                  empty for the whole session until the page is reloaded")
             (done)))))))))

;; ---------------------------------------------------------------------------
;; The same shape, and the fix, on the item subscription
;; ---------------------------------------------------------------------------

(deftest signing-in-fetches-the-items-anyway
  (testing "::mi/fetch-custom-items issues the request the subscription will not"
    (async done
      (from-quiet
       (fn []
         (reset! app-db {})
         (rf/clear-subscription-cache!)
         @(rf/subscribe [::mi/custom-items])
         (after-tick
          (fn []
            (is (= 0 (hits #"/items"))
                "the item subscription has the same flaw — it is not special")
            (reset! urls [])
            (swap! app-db merge signed-in)
            (rf/dispatch-sync [::mi/fetch-custom-items])
            (after-tick
             (fn []
               (is (= 1 (hits #"/items"))
                   "signing in fetches the item library — this is the fix")
               (done))))))))))

(deftest fetching-without-a-token-issues-no-request
  (testing "safe to dispatch unconditionally"
    (async done
      (from-quiet
       (fn []
         (reset! app-db {})
         (rf/dispatch-sync [::mi/fetch-custom-items])
         (after-tick
          (fn []
            (is (= 0 (hits #"/items")))
            (done))))))))
