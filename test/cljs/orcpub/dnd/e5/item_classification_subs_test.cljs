(ns orcpub.dnd.e5.item-classification-subs-test
  "Where a custom item is offered, once it knows whether it is magical.

   The hard requirement here is not that mundane items move — it is that
   nothing gets stranded when they do. Every custom item stays resolvable in
   the magic-item selections it has always lived in, because characters that
   already picked one hold a bare key: drop the option and their gear stops
   applying and disappears off the sheet. So mundane items are ADDED to the
   ordinary lists and merely stop being OFFERED in the magic ones."
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.template :as t]
            [orcpub.dnd.e5.magic-items :as mi5e]
            ;; Side effect: registers the equipment subscriptions
            [orcpub.dnd.e5.equipment-subs]))

(def ^:private homemade-sword
  {::mi5e/name "Bastard Sword" ::mi5e/type :weapon ::mi5e/rarity :common
   ::mi5e/magical? false ::mi5e/owner "kaylee"})

(def ^:private flame-tongue
  {::mi5e/name "Flame Brand" ::mi5e/type :weapon ::mi5e/rarity :rare
   ::mi5e/magical-attack-bonus 1 ::mi5e/owner "kaylee"})

(def ^:private legacy-trinket
  ;; The item builder's default shape — nothing to classify it by.
  {::mi5e/name "Old Trinket" ::mi5e/type :wondrous-item ::mi5e/rarity :common
   ::mi5e/owner "kaylee"})

(def ^:private homemade-lantern
  {::mi5e/name "Bullseye Lantern" ::mi5e/type :other ::mi5e/rarity :common
   ::mi5e/magical? false ::mi5e/owner "kaylee"})

(defn- with-items! [items]
  (reset! app-db {::mi5e/custom-items items})
  (rf/clear-subscription-cache!))

(use-fixtures :each {:before #(with-items! [])})

(defn- option-keys [options]
  (set (map ::t/key options)))

(defn- offerable-keys
  "The options a picker would actually show — legacy-only options are hidden."
  [options]
  (option-keys (remove ::t/legacy-only? options)))

(deftest mundane-custom-items-reach-the-ordinary-lists
  (with-items! [homemade-sword flame-tongue homemade-lantern])
  (testing "a homemade sword is offered under Weapons"
    (is (contains? (offerable-keys @(rf/subscribe [::mi5e/mundane-weapon-options]))
                   :bastard-sword)))
  (testing "a homemade lantern is offered under Equipment"
    (is (contains? (offerable-keys @(rf/subscribe [::mi5e/mundane-equipment-options]))
                   :bullseye-lantern)))
  (testing "a genuinely magical weapon is not"
    (is (not (contains? (option-keys @(rf/subscribe [::mi5e/mundane-weapon-options]))
                        :flame-brand)))))

(deftest magic-lists-keep-resolving-every-custom-item
  (with-items! [homemade-sword flame-tongue])
  (let [options @(rf/subscribe [::mi5e/magic-weapon-options])]
    (testing "a reclassified item is STILL an option — this is the data-safety guarantee"
      ;; A character that picked :bastard-sword back when every custom item was
      ;; filed as magical stores that key under :magic-weapons. Removing the
      ;; option would orphan it.
      (is (contains? (option-keys options) :bastard-sword)))
    (testing "but it is no longer offered"
      (is (not (contains? (offerable-keys options) :bastard-sword))))
    (testing "while a real magic weapon is offered as before"
      (is (contains? (offerable-keys options) :flame-brand)))))

(deftest unreviewed-legacy-items-behave-exactly-as-before
  (with-items! [legacy-trinket])
  (testing "still offered among the magic items, as it always was"
    (let [options @(rf/subscribe [::mi5e/other-magic-item-options])]
      (is (contains? (offerable-keys options) :old-trinket))))
  (testing "and does not appear among ordinary gear"
    (is (not (contains? (option-keys @(rf/subscribe [::mi5e/mundane-equipment-options]))
                        :old-trinket)))))

(deftest lookup-maps-still-contain-mundane-items
  (with-items! [homemade-sword homemade-lantern])
  (testing "the weapon map keeps resolving a mundane custom weapon"
    ;; The attack table, AC and sheet all read these maps. Classification
    ;; changes where an item is LISTED, never whether it can be found.
    (is (some? (get @(rf/subscribe [::mi5e/all-weapons-map]) :bastard-sword))))
  (testing "the equipment map resolves a mundane custom item by name"
    (is (some? (get @(rf/subscribe [::mi5e/all-equipment-map]) :bullseye-lantern))))
  (testing "and the magic item map does too, for characters that stored it there"
    (is (some? (get @(rf/subscribe [::mi5e/all-magic-items-map]) :bastard-sword)))))

(def ^:private demoted-item
  "A magic item whose owner has since ticked Mundane. Only a human can create
   this shape — classification never calls an item with magical mechanics
   mundane on its own."
  {::mi5e/name "Retired Blade" ::mi5e/type :weapon ::mi5e/rarity :rare
   ::mi5e/magical? false
   ::mi5e/attunement #{:any}
   ::mi5e/magical-attack-bonus 1
   ::mi5e/owner "kaylee"})

(deftest mundane-items-do-not-carry-magic-into-the-app
  (with-items! [demoted-item])
  (testing "the effective view has the magical mechanics suppressed"
    (let [effective (first @(rf/subscribe [::mi5e/effective-custom-items]))]
      (is (some? effective))
      (is (not (mi5e/has-magical-properties? effective)))
      (is (= "Retired Blade" (::mi5e/name effective)))))
  (testing "and so does the weapon map the attack table reads"
    (let [weapon (get @(rf/subscribe [::mi5e/all-weapons-map]) :retired-blade)]
      (is (some? weapon) "the weapon itself must still resolve")
      (is (nil? (::mi5e/magical-attack-bonus weapon))))))

(deftest the-edit-path-still-sees-the-whole-item
  (with-items! [demoted-item])
  (testing "opening the item in the builder must load it exactly as stored"
    ;; If this ever reads the effective view, unticking Magic item once would
    ;; make the suppression permanent on the next save.
    (let [raw (first (vals @(rf/subscribe [::mi5e/custom-item-map])))]
      (is (some? raw))
      (is (mi5e/has-magical-properties? raw))
      (is (= 1 (::mi5e/magical-attack-bonus raw)))
      (is (= #{:any} (::mi5e/attunement raw))))))

(deftest no-custom-items-is-not-a-special-case
  (with-items! [])
  (is (empty? @(rf/subscribe [::mi5e/mundane-weapon-options])))
  (is (empty? @(rf/subscribe [::mi5e/mundane-armor-options])))
  (is (empty? @(rf/subscribe [::mi5e/mundane-equipment-options]))))
