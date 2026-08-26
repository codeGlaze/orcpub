(ns orcpub.dnd.e5.item-flow-test
  "End-to-end-ish exercises of the custom item flows, driven through the real
   events and subscriptions rather than the DOM.

   Two reported symptoms are pinned here:

   1. \"If they don't touch the menus a default isn't propagated.\" A controlled
      <select> whose value matches no option still SHOWS an option, so the user
      believes they have a value when the item has none.
   2. \"New magic items take a page refresh to appear in the equipment
      dropdown.\" That is the reactive chain
      ::mi/custom-items -> expanded -> effective -> sorted-items -> options ->
      template going stale somewhere."
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.template :as t]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.db :as db]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.character :as char5e]
            ;; Side effects: register events + subscriptions
            [orcpub.dnd.e5.events :as events]
            [orcpub.dnd.e5.subs]
            [orcpub.dnd.e5.equipment-subs]))

(defn- reset-db! []
  (reset! app-db {})
  (rf/clear-subscription-cache!))

(use-fixtures :each {:before reset-db!})

(defn- saved-payload
  "Exactly what ::mi/save-item POSTs for the current builder item. Kept in step
   with the handler by hand — if this drifts, the tests below stop testing the
   real save path."
  []
  (mi/from-internal-item
   (mi/resolve-classification
    (mi/with-displayed-defaults (::mi/builder-item @app-db)))))

;; ---------------------------------------------------------------------------
;; 1. Untouched dropdowns
;; ---------------------------------------------------------------------------

(deftest a-brand-new-item-saves-the-defaults-it-displays
  (testing "type, rarity and magical? are all recorded without touching a menu"
    ;; The three dropdowns on the item builder all show a value on a new item.
    ;; If any of them is only SHOWN and not stored, the item saves incomplete
    ;; and renders as e.g. \", very rare\" with a missing type.
    (rf/dispatch-sync [::mi/set-item db/default-item])
    (let [payload (saved-payload)]
      (is (= :wondrous-item (::mi/type payload)))
      (is (= :common (::mi/rarity payload)))
      (is (true? (::mi/magical? payload))))))

(deftest a-legacy-item-with-no-rarity-saves-a-rarity
  (testing "opening and saving a legacy item records the rarity it displays"
    ;; The Rarity <select> has no blank option, so an item with no ::mi/rarity
    ;; still shows one. Saving must not leave the item without the value the
    ;; user was just looking at.
    (rf/dispatch-sync [::mi/set-item (mi/to-internal-item
                                      {::mi/name "Old Trinket"
                                       ::mi/type :wondrous-item
                                       ::mi/owner "kaylee"})])
    (let [payload (saved-payload)]
      (is (some? (::mi/rarity payload))
          "a magic item that displays a rarity must save one"))))

(deftest a-legacy-item-with-no-type-saves-a-type
  (testing "same hole, same fix, for the Type dropdown"
    (rf/dispatch-sync [::mi/set-item (mi/to-internal-item
                                      {::mi/name "Old Trinket"
                                       ::mi/rarity :common
                                       ::mi/owner "kaylee"})])
    (is (some? (::mi/type (saved-payload))))))

(deftest a-mundane-item-is-not-given-a-rarity-it-does-not-show
  (testing "the Rarity field is hidden for mundane items, so nothing is invented"
    ;; The inverse trap: back-filling a displayed default must not fabricate a
    ;; value for a field the user cannot see.
    (rf/dispatch-sync [::mi/set-item {::mi/name "Bastard Sword"
                                      ::mi/type :weapon
                                      ::mi/owner "kaylee"
                                      ::mi/magical? false}])
    (let [payload (saved-payload)]
      (is (false? (::mi/magical? payload)))
      (is (nil? (::mi/rarity payload))
          "no rarity is shown for a mundane item, so none should be stored"))))

(deftest an-inferred-classification-is-recorded-on-save
  (testing "the Magic Item? dropdown shows Yes for an inferred item, and saving agrees"
    ;; classify says :magical from the attunement, so the dropdown reads \"Yes\"
    ;; even though nothing is stored. Saving has to write down what was shown.
    (let [inferred {::mi/name "Old Ring" ::mi/type :ring ::mi/owner "kaylee"}]
      (is (not (contains? inferred ::mi/magical?)))
      (is (mi/magical? inferred))
      (rf/dispatch-sync [::mi/set-item (mi/to-internal-item inferred)])
      (is (true? (::mi/magical? (saved-payload)))))))

;; ---------------------------------------------------------------------------
;; 2. A newly saved item reaching the equipment pickers
;; ---------------------------------------------------------------------------

(def ^:private saved-response
  "What the server echoes back after a successful save."
  {:body {:db/id 9001
          ::mi/name "Sunfire Blade"
          ::mi/type :weapon
          ::mi/rarity :rare
          ::mi/magical? true
          ::mi/magical-attack-bonus 1
          ::mi/owner "kaylee"}})

(defn- magic-weapon-option-keys []
  (set (map ::t/key @(rf/subscribe [::mi/magic-weapon-options]))))

(defn- template-option-keys
  "The keys the character builder's Magic Weapons picker would actually offer —
   read out of the built template, which is the thing the picker renders from."
  []
  (let [selections @(rf/subscribe [::char5e/template-selections])
        magic-weapons (first (filter #(= :magic-weapons (::t/key %)) selections))]
    (set (map ::t/key (entity/selection-options magic-weapons)))))

(deftest a-saved-item-reaches-the-options-without-a-refetch
  (reset! app-db {:user-data {:token "t" :username "kaylee"}
                  ::mi/custom-items []})
  (rf/clear-subscription-cache!)
  (testing "the picker starts without it"
    (is (not (contains? (magic-weapon-option-keys) :sunfire-blade))))
  (testing "saving puts it there with no page reload and no second fetch"
    ;; :item-save-success merges the server's echo into ::mi/custom-items. If
    ;; the chain below it is reactive, the option appears immediately.
    (rf/dispatch-sync [:item-save-success saved-response])
    (is (contains? (magic-weapon-option-keys) :sunfire-blade))))

(deftest a-saved-item-reaches-the-built-template
  ;; The options subscription being right is not enough — the picker renders
  ;; from the template, one more link down the chain. This is the step a page
  ;; refresh would have papered over.
  (reset! app-db {:user-data {:token "t" :username "kaylee"}
                  ::mi/custom-items []})
  (rf/clear-subscription-cache!)
  (is (not (contains? (template-option-keys) :sunfire-blade)))
  (rf/dispatch-sync [:item-save-success saved-response])
  (is (contains? (template-option-keys) :sunfire-blade)
      "a newly saved item must be selectable without reloading the page"))

(deftest editing-an-item-updates-it-in-place
  (testing "a re-save replaces the item rather than adding a second copy"
    (reset! app-db {:user-data {:token "t" :username "kaylee"}
                    ::mi/custom-items [(:body saved-response)]})
    (rf/clear-subscription-cache!)
    (rf/dispatch-sync [:item-save-success
                       {:body (assoc (:body saved-response)
                                     ::mi/name "Sunfire Blade")}])
    (is (= 1 (count (::mi/custom-items @app-db))))))

(deftest reclassifying-moves-an-item-between-pickers-live
  (testing "marking an item mundane takes it out of the magic picker at once"
    (reset! app-db {:user-data {:token "t" :username "kaylee"}
                    ::mi/custom-items [(:body saved-response)]})
    (rf/clear-subscription-cache!)
    (let [offered #(set (map ::t/key (remove ::t/legacy-only?
                                             @(rf/subscribe [::mi/magic-weapon-options]))))]
      (is (contains? (offered) :sunfire-blade))
      (rf/dispatch-sync [:item-save-success
                         {:body (assoc (:body saved-response)
                                       ::mi/magical? false
                                       ::mi/magical-attack-bonus 0)}])
      (testing "gone from the magic picker"
        (is (not (contains? (offered) :sunfire-blade))))
      (testing "but still resolvable there, so existing characters keep it"
        (is (contains? (magic-weapon-option-keys) :sunfire-blade)))
      (testing "and now offered under ordinary Weapons"
        (is (contains? (set (map ::t/key @(rf/subscribe [::mi/mundane-weapon-options])))
                       :sunfire-blade))))))

(deftest deleting-an-item-removes-it-from-the-pickers-live
  (reset! app-db {:user-data {:token "t" :username "kaylee"}
                  ::mi/custom-items [(:body saved-response)]})
  (rf/clear-subscription-cache!)
  (is (contains? (magic-weapon-option-keys) :sunfire-blade))
  (rf/dispatch-sync [::mi/delete-custom-item 9001])
  (is (not (contains? (magic-weapon-option-keys) :sunfire-blade))
      "a deleted item must not linger in the picker until a reload"))

;; ---------------------------------------------------------------------------
;; 3. Signing in has to go and get the items
;; ---------------------------------------------------------------------------

(deftest signing-in-asks-for-the-item-library
  (testing ":login-success dispatches a fetch, not just a route change"
    (let [{:keys [dispatch-n db]}
          (events/login-success {:db {}}
                                [:login-success false
                                 {:body {:token "t" :username "kaylee"}}])]
      (is (= "t" (get-in db [:user-data :token]))
          "the token lands in app-db")
      (is (some #(= [::mi/fetch-custom-items] %) dispatch-n)
          "and the item library is asked for straight away")
      (is (some #(= :route (first %)) dispatch-n)
          "without dropping the route change it already did"))))

(deftest fetching-without-a-token-is-a-no-op
  (testing "safe to dispatch unconditionally — no request, no crash"
    (reset! app-db {})
    (rf/dispatch-sync [::mi/fetch-custom-items])
    (is (nil? (:loading @app-db))
        "no request was started, so no loading state was set")
    (is (= [] @(rf/subscribe [::mi/custom-items])))))

(deftest set-custom-items-refreshes-the-pickers
  (testing "the fetch's response event lands in the pickers with no reload"
    (reset! app-db {:user-data {:token "t" :username "kaylee"}
                    ::mi/custom-items []})
    (rf/clear-subscription-cache!)
    (is (not (contains? (magic-weapon-option-keys) :sunfire-blade)))
    (rf/dispatch-sync [::mi/set-custom-items [(:body saved-response)]])
    (is (contains? (magic-weapon-option-keys) :sunfire-blade))))
