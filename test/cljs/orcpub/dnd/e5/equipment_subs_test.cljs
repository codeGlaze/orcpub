(ns orcpub.dnd.e5.equipment-subs-test
  "Tests for equipment-subs.cljs and the filter reactivity chain.

   Covers:
   - `::mi5e/custom-items` token-guard behavior (pre-existing coverage gap)
   - `::char5e/filtered-items` / `::char5e/filtered-spells` reactive
     recomputation when underlying data changes (#669 regression tests)
   - `::char5e/filter-items` / `::char5e/filter-spells` events: verify
     they only store filter text, not a frozen snapshot

   PATTERN for these tests: pure db -> sub. No HTTP, no DB. Pre-populate
   app-db, dispatch/update, assert on sub output.

   Why separate from subs-test.cljs: the subs under test span both
   equipment_subs.cljs and subs.cljs, and these tests specifically
   target the P1 filter-reactivity fix and the P2 custom-items guard
   gap — keeping them together makes the intent clear."
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.magic-items :as mi]
            ;; Side effect: registers subscriptions + events
            [orcpub.dnd.e5.subs]
            [orcpub.dnd.e5.equipment-subs]
            [orcpub.dnd.e5.events]))

;; ---------------------------------------------------------------------------
;; Fixtures
;; ---------------------------------------------------------------------------

(defn reset-db!
  []
  (reset! app-db {})
  (rf/clear-subscription-cache!))

(use-fixtures :each {:before reset-db!})

;; ---------------------------------------------------------------------------
;; ::mi5e/custom-items — token-guard behavior (coverage gap fill)
;; ---------------------------------------------------------------------------

(deftest custom-items-no-token-returns-empty
  (testing "without auth token, subscription returns [] without HTTP call"
    (reset! app-db {})
    (let [result @(rf/subscribe [::mi/custom-items])]
      (is (= [] result))
      (is (nil? (:loading @app-db))
          "No loading state should be set without token"))))

(deftest custom-items-with-token-returns-cached
  (testing "with token and cached data, subscription returns the cached vector"
    (reset! app-db {:user-data {:token "test-token"}
                    ::mi/custom-items [{:db/id 1 ::mi/name "Test Item"
                                        ::mi/type :wondrous-item}]})
    (let [result @(rf/subscribe [::mi/custom-items])]
      (is (= 1 (count result)))
      (is (= "Test Item" (::mi/name (first result)))))))

(deftest custom-items-stale-user-key-still-guarded
  (testing "stale `:user` key in db doesn't accidentally pass the guard"
    ;; Regression guard for the 45ef969 typo that was fixed by a0e20a8.
    (reset! app-db {:user {:following ["someone"]}})  ; no :user-data/:token
    (let [result @(rf/subscribe [::mi/custom-items])]
      (is (= [] result)))))

;; ---------------------------------------------------------------------------
;; ::char5e/filtered-items — P1 reactivity regression tests
;;
;; BUG (#669): filtered-items sub returned a frozen snapshot from db once
;; the filter-items event had been dispatched. Adding/editing/deleting
;; custom items did not refresh the list until a hard reload.
;;
;; FIX: filtered-items becomes a pure reactive composition of
;; sorted-items + item-text-filter. The filter event no longer writes
;; a snapshot to db.
;; ---------------------------------------------------------------------------

(deftest filtered-items-reacts-to-custom-items-change
  (testing "filtered-items recomputes when ::mi/custom-items changes"
    (reset! app-db {})
    (let [initial @(rf/subscribe [::char5e/filtered-items])
          initial-count (count initial)]
      ;; Add a new custom item after the initial subscription
      (swap! app-db assoc ::mi/custom-items
             [{:db/id 1
               ::mi/name "Regression Sword"
               ::mi/type :weapon}])
      (let [updated @(rf/subscribe [::char5e/filtered-items])
            names (set (map #(or (:name %) (::mi/name %)) updated))]
        (is (= (inc initial-count) (count updated))
            "New custom item should appear in filtered-items (no stale snapshot)")
        (is (contains? names "Regression Sword")
            "New item must be present in the filtered list")))))

(deftest filtered-items-reacts-to-filter-text-change
  (testing "filtered-items recomputes when filter text changes"
    (reset! app-db {::mi/custom-items
                    [{:db/id 1 ::mi/name "Alpha Sword" ::mi/type :weapon}
                     {:db/id 2 ::mi/name "Beta Sword"  ::mi/type :weapon}]})
    ;; Empty filter: both items visible
    (let [all @(rf/subscribe [::char5e/filtered-items])
          names (set (map #(or (:name %) (::mi/name %)) all))]
      (is (contains? names "Alpha Sword"))
      (is (contains? names "Beta Sword")))
    ;; Apply filter "alpha": only matching items
    (rf/dispatch-sync [::char5e/filter-items "alpha"])
    (let [filtered @(rf/subscribe [::char5e/filtered-items])
          names (set (map #(or (:name %) (::mi/name %)) filtered))]
      (is (contains? names "Alpha Sword"))
      (is (not (contains? names "Beta Sword"))))))

(deftest filter-items-event-stores-only-filter-text
  (testing "filter-items event does not write a snapshot to db"
    ;; The P1 fix: the event only stores ::char5e/item-text-filter.
    ;; It must NOT write ::char5e/filtered-items into db.
    (reset! app-db {})
    (rf/dispatch-sync [::char5e/filter-items "something"])
    (is (= "something" (::char5e/item-text-filter @app-db))
        "Filter text should be stored in db")
    (is (nil? (::char5e/filtered-items @app-db))
        "The filtered-items snapshot must NOT be written to db —
         it's a reactive sub, not cached state. Storing it here was
         the root cause of #669's stale-list bug.")))

(deftest filtered-items-updates-after-simulated-save
  (testing "filtered-items picks up a newly-saved item (simulates #669 flow)"
    ;; User has an existing item list, touches the filter (pre-P1 this
    ;; froze the snapshot), then creates a new item. Post-P1, the new
    ;; item must appear without a reload.
    (reset! app-db {::mi/custom-items
                    [{:db/id 1 ::mi/name "Existing Item" ::mi/type :wondrous-item}]})
    ;; Touch the filter (the trigger for the old snapshot bug)
    (rf/dispatch-sync [::char5e/filter-items ""])
    ;; Simulate item-save-success: a new item is appended
    (swap! app-db update ::mi/custom-items
           conj {:db/id 2 ::mi/name "Newly Saved Item" ::mi/type :wondrous-item})
    (let [result @(rf/subscribe [::char5e/filtered-items])
          names (set (map #(or (:name %) (::mi/name %)) result))]
      (is (contains? names "Newly Saved Item")
          "The new item must appear in filtered-items without a page reload"))))

;; ---------------------------------------------------------------------------
;; ::char5e/filtered-spells — same P1 regression shape
;; ---------------------------------------------------------------------------

(deftest filter-spells-event-stores-only-filter-text
  (testing "filter-spells event does not write a snapshot to db"
    ;; Same bug shape as filter-items, same fix.
    (reset! app-db {})
    (rf/dispatch-sync [::char5e/filter-spells "fire"])
    (is (= "fire" (::char5e/spell-text-filter @app-db))
        "Filter text should be stored in db")
    (is (nil? (::char5e/filtered-spells @app-db))
        "The filtered-spells snapshot must NOT be written to db —
         same reactivity pattern as filtered-items.")))

(deftest filtered-spells-reacts-to-filter-text-change
  (testing "filtered-spells recomputes when filter text changes"
    (reset! app-db {})
    (let [unfiltered @(rf/subscribe [::char5e/filtered-spells])
          all-count (count unfiltered)]
      (is (pos? all-count) "SRD spells should be present")
      ;; Apply a narrow filter
      (rf/dispatch-sync [::char5e/filter-spells "fire bolt"])
      (let [filtered @(rf/subscribe [::char5e/filtered-spells])]
        (is (<= (count filtered) all-count)
            "Filtered result should be a subset of unfiltered")
        (is (pos? (count filtered))
            "Fire Bolt is a known SRD spell and should match")))))
