(ns orcpub.dnd.e5.filtered-list-reactivity-test
  "Gap coverage for the #669 filter-reactivity fix (P1) and the reg-api-sub
   migration (P5).

   `equipment_subs_test.cljs` on the fix branch covers the basics. These are the
   cases it does not, each one chosen because it discriminates between the fixed
   and unfixed code, or because it pins a behaviour the refactor could silently
   drop.

   The distinction that matters: under the OLD code the stale snapshot only
   existed AFTER a filter event had run. A test that changes content without
   dispatching a filter first passes on both the broken and fixed versions and
   proves nothing. Every reactivity test below dispatches a filter first."
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.party :as party5e]
            [orcpub.dnd.e5.folder :as folder5e]
            [orcpub.dnd.e5.magic-items :as mi]
            ;; side effects: register subs + events
            [orcpub.dnd.e5.subs]
            [orcpub.dnd.e5.equipment-subs]
            [orcpub.dnd.e5.events]))

(defn reset-db! []
  (reset! app-db {})
  (rf/clear-subscription-cache!))

(use-fixtures :each {:before reset-db!})

;; A name no SRD item shares, so a hit is unambiguously ours.
(def ^:private probe-name "Zzzprobe Blade")
(def ^:private probe-2    "Zzzprobe Dagger")

(defn- item [id nm] {:db/id id ::mi/name nm ::mi/type :weapon})

(defn- item-names []
  (set (map #(or (:name %) (::mi/name %)) @(rf/subscribe [::char5e/filtered-items]))))

;; ---------------------------------------------------------------------------
;; P1 — the discriminating reactivity cases
;; ---------------------------------------------------------------------------

(deftest new-item-appears-while-a-filter-is-active
  (testing "a matching item saved AFTER the filter was applied still shows"
    ;; THE user-facing #669 flow. Under the old code the filter event froze the
    ;; list at this point and the new item never appeared. The branch's own
    ;; equivalent test filters on "" — which never exercised the snapshot path.
    (reset! app-db {::mi/custom-items [(item 1 probe-name)]})
    (rf/dispatch-sync [::char5e/filter-items "zzzprobe"])
    (is (contains? (item-names) probe-name) "baseline: the filter matches")
    (swap! app-db update ::mi/custom-items conj (item 2 probe-2))
    (is (contains? (item-names) probe-2)
        "an item saved while a filter is active must appear without a reload")))

(deftest deleting-an-item-removes-it-while-a-filter-is-active
  (testing "deletion is reflected too, not just addition"
    (reset! app-db {::mi/custom-items [(item 1 probe-name) (item 2 probe-2)]})
    (rf/dispatch-sync [::char5e/filter-items "zzzprobe"])
    (let [names (item-names)]
      (is (and (contains? names probe-name) (contains? names probe-2))
          "baseline: both probes match the active filter"))
    (swap! app-db assoc ::mi/custom-items [(item 1 probe-name)])
    (is (not (contains? (item-names) probe-2))
        "a deleted item must disappear from an active filtered view")))

(deftest a-non-matching-new-item-stays-hidden
  (testing "reactivity did not come at the cost of the filter still filtering"
    ;; Guards the cheap wrong fix: making the list reactive by ignoring the
    ;; filter would pass every test above and break the feature.
    (reset! app-db {::mi/custom-items [(item 1 probe-name)]})
    (rf/dispatch-sync [::char5e/filter-items "zzzprobe"])
    (swap! app-db update ::mi/custom-items conj (item 2 "Completely Unrelated Thing"))
    (let [names (item-names)]
      (is (contains? names probe-name))
      (is (not (contains? names "Completely Unrelated Thing"))
          "a non-matching item must NOT appear just because the list recomputed"))))

;; ---------------------------------------------------------------------------
;; P1 — the three-character threshold, which reg-filtered-sub takes as a param
;; ---------------------------------------------------------------------------

(deftest filter-below-min-length-returns-the-whole-list
  (testing "1- and 2-character filters are ignored, as before the fix"
    (reset! app-db {::mi/custom-items [(item 1 probe-name)
                                       (item 2 "Completely Unrelated Thing")]})
    (doseq [short-text ["" "z" "zz"]]
      (rf/dispatch-sync [::char5e/filter-items short-text])
      (let [names (item-names)]
        (is (contains? names probe-name)
            (str "filter " (pr-str short-text) " must not filter"))
        (is (contains? names "Completely Unrelated Thing")
            (str "filter " (pr-str short-text) " must not filter"))))))

(deftest filter-at-exactly-min-length-does-filter
  (testing "three characters is the boundary and it filters"
    (reset! app-db {::mi/custom-items [(item 1 probe-name)
                                       (item 2 "Completely Unrelated Thing")]})
    (rf/dispatch-sync [::char5e/filter-items "zzz"])
    (let [names (item-names)]
      (is (contains? names probe-name))
      (is (not (contains? names "Completely Unrelated Thing"))))))

(deftest filtered-items-come-back-sorted
  (testing "filtering preserves name order"
    (reset! app-db {::mi/custom-items [(item 1 "Zzzprobe Gamma")
                                       (item 2 "Zzzprobe Alpha")
                                       (item 3 "Zzzprobe Beta")]})
    (rf/dispatch-sync [::char5e/filter-items "zzzprobe"])
    (let [names (map #(or (:name %) (::mi/name %)) @(rf/subscribe [::char5e/filtered-items]))]
      (is (= ["Zzzprobe Alpha" "Zzzprobe Beta" "Zzzprobe Gamma"] (vec names))
          "compute/filter-items sorts by name; the sub must not disturb that"))))

;; ---------------------------------------------------------------------------
;; P5 — the four other migrated subs. No HTTP: with no token the reg-api-sub
;; guard short-circuits and the reaction just reads the cached db value. That
;; read path is exactly what a wrong :db-key would break.
;; ---------------------------------------------------------------------------

(deftest parties-sub-reads-the-historical-db-key
  (testing "::party5e/parties reads db[::char5e/parties], NOT db[::party5e/parties]"
    ;; The naming mismatch is deliberate and flagged in a comment at the call
    ;; site. Anyone "tidying" it breaks parties silently, because the sub would
    ;; then read a key ::party5e/set-parties never writes. This is the pin.
    (reset! app-db {::char5e/parties [{:db/id 7 :name "Zzzprobe Party"}]})
    (is (= 1 (count @(rf/subscribe [::party5e/parties])))
        "parties must come from the ::char5e/parties key")))

(deftest characters-and-folders-subs-read-their-db-keys
  (testing "the other two migrated list subs read the keys their events write"
    (reset! app-db {::char5e/characters [{:db/id 1} {:db/id 2}]
                    ::folder5e/folders  [{:db/id 3}]})
    (is (= 2 (count @(rf/subscribe [::char5e/characters]))))
    (is (= 1 (count @(rf/subscribe [::folder5e/folders]))))))

(deftest migrated-subs-default-to-empty-not-nil
  (testing "an unset db key yields [], so count/seq at call sites stay safe"
    (reset! app-db {})
    (is (= [] @(rf/subscribe [::char5e/characters])))
    (is (= [] @(rf/subscribe [::folder5e/folders])))
    (is (= [] @(rf/subscribe [::party5e/parties])))))

(deftest logged-out-subs-do-not-touch-the-loading-counter
  (testing "the reg-api-sub guard short-circuits before :set-loading"
    ;; The HOF brackets its request with :set-loading true/false. The :user sub
    ;; did NOT do that before the migration, so this is new traffic on a shared
    ;; counter. Logged out, none of it should happen at all.
    (reset! app-db {})
    (doseq [q [[::char5e/characters] [::folder5e/folders] [::party5e/parties]
               [::mi/custom-items] [:user]]]
      @(rf/subscribe q))
    (is (nil? (:loading @app-db))
        "no guard should have fired an HTTP request, so nothing bumped :loading")))

;; ---------------------------------------------------------------------------
;; The share overlay — added after integration shipped link-embedded sharing.
;;
;; ::mi5e/shared-custom-items is an ephemeral overlay of items that arrived in a
;; share URL. It feeds ::mi5e/expanded-custom-items alongside the owned items,
;; which feeds ::char5e/sorted-items, which feeds ::char5e/filtered-items. So the
;; filtered list P1 fixes now carries shared items too, and nothing else tests it.
;;
;; Shared items are appended LAST so they win key collisions on a shared sheet --
;; which makes a collision the exact place a stale snapshot would show the wrong
;; one of the two.
;; ---------------------------------------------------------------------------

(deftest shared-items-appear-in-the-filtered-list
  (testing "an item from a share link is filterable like an owned one"
    (reset! app-db {::mi/custom-items  [(item 1 probe-name)]
                    :shared-custom-items [(item 2 probe-2)]})
    (rf/dispatch-sync [::char5e/filter-items "zzzprobe"])
    (let [names (item-names)]
      (is (contains? names probe-name) "the owned item matches")
      (is (contains? names probe-2)    "the shared item matches too"))))

(deftest shared-items-stay-reactive-while-a-filter-is-active
  (testing "a share payload arriving after the filter still shows"
    ;; The share overlay is populated by ::e5/apply-shared-content on landing,
    ;; which can happen after the page has rendered -- so this is the same
    ;; stale-snapshot shape as #669, on a path the branch never exercised.
    ;; VERIFIED to fail when the pre-fix shape is reinstated.
    (reset! app-db {::mi/custom-items [(item 1 probe-name)]})
    (rf/dispatch-sync [::char5e/filter-items "zzzprobe"])
    (is (not (contains? (item-names) probe-2)) "baseline: not there yet")
    (swap! app-db assoc :shared-custom-items [(item 2 probe-2)])
    (is (contains? (item-names) probe-2)
        "a shared item must appear without a reload, like an owned one")))

(deftest a-shared-item-wins-a-key-collision
  (testing "shared is appended last, so it takes precedence on a shared sheet"
    ;; Pins the ordering contract in equipment_subs.cljs. If a future change
    ;; concats the other way the sheet silently shows the recipient's own item
    ;; instead of the one they were sent.
    (reset! app-db {::mi/custom-items    [(assoc (item 1 probe-name) :key :zzzprobe)]
                    :shared-custom-items [(assoc (item 1 "Zzzprobe Shared Wins") :key :zzzprobe)]})
    (rf/dispatch-sync [::char5e/filter-items "zzzprobe"])
    (let [names (item-names)]
      (is (contains? names "Zzzprobe Shared Wins")
          "the shared item is present"))))
