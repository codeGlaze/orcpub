(ns orcpub.dnd.e5.magic-items-test
  (:require [clojure.test :refer [testing deftest is]]
            [clojure.spec.alpha :as spec]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.modifiers :as mod]
            [orcpub.dnd.e5.modifiers :as mod5e]))

(deftest test-to-internal-item
  (testing "Ability override modifier"
    (let [item {::mi/modifiers [{::mod/key :ability-override
                                 ::mod/args [{::mod/keyword-arg ::char/str}
                                             {::mod/int-arg 1}]}]}
          expected-item {::mi/internal-modifiers {:ability {::char/str {:value 1
                                                                        :type :becomes-at-least}}}}
          internal-item (mi/to-internal-item item)]
      (is (= internal-item expected-item))))
  (testing "Ability bonus modifier"
    (let [item {::mi/modifiers [{::mod/key :ability
                                 ::mod/args [{::mod/keyword-arg ::char/str}
                                             {::mod/int-arg 1}]}]}
          expected-item {::mi/internal-modifiers {:ability {::char/str {:value 1
                                                                        :type :increases-by}}}}
          internal-item (mi/to-internal-item item)]
      (is (= internal-item expected-item))))
  (testing "Save modifier"
    (let [item {::mi/modifiers [{::mod/key :saving-throw-bonus
                                 ::mod/args [{::mod/keyword-arg ::char/str}
                                             {::mod/int-arg 1}]}]}
          expected-item {::mi/internal-modifiers {:save {::char/str {:value 1}}}}
          internal-item (mi/to-internal-item item)]
      (is (= internal-item expected-item))))
  (testing "Resistance modifier"
    (let [item {::mi/modifiers [{::mod/key :damage-resistance
                                 ::mod/args [{::mod/keyword-arg :fire}]}
                                {::mod/key :damage-resistance
                                 ::mod/args [{::mod/keyword-arg :necrotic}]}]}
          expected-item {:orcpub.dnd.e5.magic-items/internal-modifiers {:damage-resistance {:fire true, :necrotic true}}}
          internal-item (mi/to-internal-item item)]
      (is (= internal-item expected-item))))
  (testing "Speed modifier"
    (let [item {::mi/modifiers [{::mod/key :flying-speed-equal-to-walking}
                                {::mod/key :swimming-speed-override
                                 ::mod/args [{::mod/int-arg 10}]}]}
          expected-item {:orcpub.dnd.e5.magic-items/internal-modifiers {:flying-speed {:type :equals-walking-speed}, :swimming-speed {:type :becomes-at-least :value 10}}}
          internal-item (mi/to-internal-item item)]
      (is (= internal-item expected-item)))))

(deftest test-from-internal-item
  (testing "Default ability modifier"
    (let [internal-item {::mi/internal-modifiers {:ability {::char/str {:value 1}}}}
          expected-item {::mi/modifiers [{::mod/key :ability-override
                                          ::mod/args [{::mod/keyword-arg ::char/str}
                                                      {::mod/int-arg 1}]}]}
          item (mi/from-internal-item internal-item)]
      (is (= item expected-item))))
  (testing "Ability bonus modifier"
    (let [internal-item {::mi/internal-modifiers {:ability {::char/str {:value 1
                                                                        :type :increases-by}}}}
          expected-item {::mi/modifiers [{::mod/key :ability
                                          ::mod/args [{::mod/keyword-arg ::char/str}
                                                      {::mod/int-arg 1}]}]}
          item (mi/from-internal-item internal-item)]
      (is (= item expected-item))))
  (testing "Ability override modifier"
    (let [internal-item {::mi/internal-modifiers {:ability {::char/str {:value 1
                                                                        :type :becomes-at-least}}}}
          expected-item {::mi/modifiers [{::mod/key :ability-override
                                          ::mod/args [{::mod/keyword-arg ::char/str}
                                                      {::mod/int-arg 1}]}]}
          item (mi/from-internal-item internal-item)]
      (is (= item expected-item))))
  (testing "Save modifier"
    (let [internal-item {::mi/internal-modifiers {:save {::char/str {:value 1}}}}
          expected-item {::mi/modifiers [{::mod/key :saving-throw-bonus
                                          ::mod/args [{::mod/keyword-arg ::char/str}
                                                      {::mod/int-arg 1}]}]}
          item (mi/from-internal-item internal-item)]
      (is (= item expected-item))))
  (testing "Resistance modifier"
    (let [internal-item {:orcpub.dnd.e5.magic-items/internal-modifiers {:damage-resistance {:fire true, :necrotic true}}}
          expected-item {::mi/modifiers [{::mod/key :damage-resistance
                                          ::mod/args [{::mod/keyword-arg :fire}]}
                                         {::mod/key :damage-resistance
                                          ::mod/args [{::mod/keyword-arg :necrotic}]}]}
          item (mi/from-internal-item internal-item)]
      (is (= item expected-item))))
  (testing "Speed modifier"
    (let [internal-item {:orcpub.dnd.e5.magic-items/internal-modifiers {:flying-speed {:type :equals-walking-speed}, :swimming-speed {:value 10}}}
          expected-item {::mi/modifiers [{::mod/key :flying-speed-equal-to-walking}
                                         {::mod/key :swimming-speed-override
                                          ::mod/args [{::mod/int-arg 10}]}]}
          item (mi/from-internal-item internal-item)]
      (is (= item expected-item)))))

(deftest test-expand-armor
  (testing "retains name for expanded items with only 1 base type"
    (let [glamoured-studded-leather {
                                     mi/name-key "Glamoured Studded Leather"
                                     ::mi/type :armor
                                     ::mi/item-subtype :studded
                                     ::mi/rarity :rare
                                     ::mi/magical-ac-bonus 1
                                     ::mi/modifiers [(mod5e/bonus-action
                                                      {:name "Glamoured Studded Leather"
                                                       :page 172
                                                       :source :dmg
                                                       :summary "change the armor to assume the appearance of normal clothing or some other armor"})]
                                     ::mi/description "While wearing this armor, you gain a +1 bonus to AC. You can also use a bonus action to speak the armor’s command word and cause the armor to assume the appearance of a normal set of clothing or some other kind of armor. You decide what it looks like, including color, style, and accessories, but the armor retains its normal bulk and weight. The illusory appearance lasts until you use this property again or remove the armor."
                                     }
          expanded (mi/expand-armor glamoured-studded-leather)
          first-expanded (first expanded)]
      (is (sequential? expanded))
      (is (= 1 (count expanded)))
      (is (= (mi/name-key glamoured-studded-leather)
             (:name first-expanded)))
      (is (= 12 (:base-ac first-expanded)))))
  (testing "multiple subtypes expand to multiple items"
    (let [item {mi/name-key "My Item"
                ::mi/type :armor
                ::mi/subtypes [:plate :chain-mail]}
          expansion (mi/expand-armor item)
          names (set (map :name expansion))]
      (is (= 2 (count expansion)))
      (is (names "My Item, Plate"))
      (is (names "My Item, Chain mail"))))
  (testing "function subtype matches the proper subtypes"
    (let [item {mi/name-key "My Item"
                ::mi/type :armor
                ::mi/item-subtype (fn [{:keys [type]}]
                                    (= :light type))
                ::mi/subtypes [:plate :chain-mail]}
          expansion (mi/expand-armor item)
          names (set (map :name expansion))]
      (is (names "My Item, Padded"))
      (is (names "My Item, Leather"))
      (is (names "My Item, Studded"))))
  (testing "throws if no items matched"
    (let [item {mi/name-key "My Item"
                ::mi/type :armor
                ::mi/item-subtype (constantly false)}]
      (is (thrown? IllegalArgumentException (mi/expand-armor item))))))

;; -- compute-all-weapons-map --

(deftest compute-all-weapons-map-includes-phb-weapons
  (testing "static PHB weapons are always present"
    (let [result (mi/compute-all-weapons-map nil)]
      (is (contains? result :longsword))
      (is (contains? result :dagger))
      (is (contains? result :handaxe)))))

(deftest compute-all-weapons-map-includes-magic-weapons
  (testing "static magic weapons from raw-magic-items are merged in"
    (let [result (mi/compute-all-weapons-map nil)]
      ;; magic-weapon-map has specific keys; verify they survive the merge
      (doseq [k (take 3 (keys mi/magic-weapon-map))]
        (is (contains? result k)
            (str "magic weapon " k " missing from all-weapons-map"))))))

(deftest compute-all-weapons-map-nil-equals-empty
  (testing "nil custom items and empty vector produce same result"
    (is (= (mi/compute-all-weapons-map nil)
           (mi/compute-all-weapons-map [])))))

(deftest compute-all-weapons-map-custom-weapon-appears
  (testing "custom weapon items get expanded and merged"
    (let [custom [{mi/name-key "Homebrew Blade"
                   ::mi/type :weapon
                   ::mi/item-subtype :longsword
                   ::mi/rarity :rare
                   ::mi/magical-attack-bonus 2
                   ::mi/magical-damage-bonus 2}]
          result (mi/compute-all-weapons-map custom)
          custom-keys (set (keys result))
          ;; expand-weapon generates keys like :homebrew-blade-longsword
          has-custom? (some #(re-matches #".*homebrew-blade.*" (name %))
                           custom-keys)]
      (is has-custom?
          "custom weapon should appear after expansion"))))

(deftest compute-all-weapons-map-custom-overrides-base
  (testing "custom magic longsword has higher attack bonus than base longsword"
    (let [custom [{mi/name-key "Vorpal Sword"
                   ::mi/type :weapon
                   ::mi/item-subtype :longsword
                   ::mi/rarity :legendary
                   ::mi/magical-attack-bonus 3
                   ::mi/magical-damage-bonus 3}]
          result (mi/compute-all-weapons-map custom)
          base-longsword (get result :longsword)]
      ;; base longsword still present (custom gets a different key)
      (is (some? base-longsword) "base longsword should survive custom additions")
      ;; custom weapon gets its own key, doesn't clobber base
      (is (> (count result) (count (mi/compute-all-weapons-map nil)))
          "adding custom items should increase total weapon count"))))

;; -- weapon property tests (from develop) --

(deftest test-remove-custom-weapon-fields
  (testing "strips all weapon-specific keys"
    (let [item {::mi/name "Test Weapon"
                ::weapons5e/finesse? true
                ::weapons5e/versatile? true
                ::weapons5e/reach? false
                ::weapons5e/two-handed? true
                ::weapons5e/thrown? false
                ::weapons5e/heavy? true
                ::weapons5e/light? false
                ::weapons5e/ammunition? false
                ::weapons5e/special? true
                ::weapons5e/loading? true
                ::weapons5e/damage-die-count 2
                ::weapons5e/damage-die 6
                ::weapons5e/versatile 8
                ::weapons5e/melee? true
                ::weapons5e/ranged? false
                ::weapons5e/type :martial
                ::weapons5e/range [20 60]
                ::weapons5e/damage-type :slashing}
          result (mi/remove-custom-weapon-fields item)]
      (is (= {::mi/name "Test Weapon"} result))))
  (testing "preserves item when no weapon keys present"
    (let [item {::mi/name "Ring" ::mi/type :ring}
          result (mi/remove-custom-weapon-fields item)]
      (is (= item result)))))

(deftest test-apply-subtype-toggle--custom
  (testing ":other sets custom defaults and strips weapon fields"
    (let [item {::mi/name "My Sword"
                ::weapons5e/finesse? true}
          result (mi/apply-subtype-toggle item :other)]
      (is (= #{:other} (::mi/subtypes result)))
      (is (= 1 (::weapons5e/damage-die-count result)))
      (is (= 4 (::weapons5e/damage-die result)))
      (is (= :simple (::weapons5e/type result)))
      (is (= :bludgeoning (::weapons5e/damage-type result)))
      (is (true? (::weapons5e/melee? result)))
      (is (false? (::weapons5e/ranged? result)))
      (is (nil? (::weapons5e/finesse? result))
          "base weapon keys should be stripped")))
  (testing ":other is idempotent"
    (let [item {}
          r1 (mi/apply-subtype-toggle item :other)
          r2 (mi/apply-subtype-toggle r1 :other)]
      (is (= r1 r2)))))

(deftest test-apply-subtype-toggle--all
  (testing ":all sets subtypes to #{:all}"
    (let [result (mi/apply-subtype-toggle {} :all)]
      (is (= #{:all} (::mi/subtypes result))))))

(deftest test-apply-subtype-toggle--named
  (testing "adds a named subtype"
    (let [result (mi/apply-subtype-toggle {} :sword)]
      (is (= #{:sword} (::mi/subtypes result)))))
  (testing "toggles off an existing subtype"
    (let [item {::mi/subtypes #{:sword}}
          result (mi/apply-subtype-toggle item :sword)]
      (is (= #{} (::mi/subtypes result)))))
  (testing "clears :other/:all when toggling a named subtype"
    (let [item {::mi/subtypes #{:other}}
          result (mi/apply-subtype-toggle item :sword)]
      (is (= #{:sword} (::mi/subtypes result)))
      (is (not (contains? (::mi/subtypes result) :other))))))

(deftest test-custom-weapon-round-trip
  (testing "custom weapon defaults survive from-internal-item serialization"
    (let [builder-item (-> {::mi/name "Test Blade"
                            ::mi/type :weapon
                            ::mi/rarity :uncommon}
                           (mi/apply-subtype-toggle :other))
          serialized (mi/from-internal-item builder-item)]
      (is (= "Test Blade" (::mi/name serialized)))
      (is (= :weapon (::mi/type serialized)))
      (is (= :uncommon (::mi/rarity serialized)))
      (is (= #{:other} (::mi/subtypes serialized)))
      (is (= 1 (::weapons5e/damage-die-count serialized))
          "damage die count must survive serialization")
      (is (= 4 (::weapons5e/damage-die serialized))
          "damage die must survive serialization")
      (is (= :simple (::weapons5e/type serialized))
          "weapon type must survive serialization")
      (is (= :bludgeoning (::weapons5e/damage-type serialized))
          "damage type must survive serialization")
      (is (true? (::weapons5e/melee? serialized))
          "melee flag must survive serialization")))
  (testing "custom weapon with user overrides round-trips correctly"
    (let [builder-item (-> {::mi/name "Fire Lance"
                            ::mi/type :weapon
                            ::mi/rarity :rare}
                           (mi/apply-subtype-toggle :other)
                           (assoc ::weapons5e/damage-die-count 2)
                           (assoc ::weapons5e/damage-die 8)
                           (assoc ::weapons5e/type :martial)
                           (assoc ::weapons5e/damage-type :fire)
                           (assoc ::weapons5e/melee? false)
                           (assoc ::weapons5e/ranged? true))
          serialized (mi/from-internal-item builder-item)]
      (is (= 2 (::weapons5e/damage-die-count serialized)))
      (is (= 8 (::weapons5e/damage-die serialized)))
      (is (= :martial (::weapons5e/type serialized)))
      (is (= :fire (::weapons5e/damage-type serialized)))
      (is (true? (::weapons5e/ranged? serialized)))))
  (testing "special and loading properties survive round-trip"
    (let [builder-item (-> {::mi/name "Net Launcher"
                            ::mi/type :weapon
                            ::mi/rarity :common}
                           (mi/apply-subtype-toggle :other)
                           (assoc ::weapons5e/special? true)
                           (assoc ::weapons5e/loading? true))
          serialized (mi/from-internal-item builder-item)]
      (is (true? (::weapons5e/special? serialized))
          "special? must survive serialization")
      (is (true? (::weapons5e/loading? serialized))
          "loading? must survive serialization"))))

;; ---------------------------------------------------------------------------
;; Magical vs. mundane classification
;;
;; The guarantee these tests exist to protect: an item saved before ::magical?
;; existed keeps behaving exactly as it did, and nothing about it is rewritten
;; or dropped in the course of classifying it.
;; ---------------------------------------------------------------------------

(defn- custom
  "A user-built item. Real custom items always carry ::mi/owner — the server
   stamps it on save — and classification reads it to tell them apart from the
   built-in magic item list, so the fixtures carry it too."
  [item]
  (merge {::mi/owner "kaylee"} item))

(deftest built-in-items-are-magical-by-definition
  (testing "the shipped list IS the magic item list, whatever an item looks like"
    ;; Without this, an SRD item whose mechanics live only in its prose could be
    ;; inferred mundane and quietly dropped out of the magic item sections.
    (is (= :magical (mi/classify {::mi/name "Plain Looking Blade"
                                  ::mi/type :weapon
                                  ::mi/rarity :common}))))
  (testing "every item actually shipped classifies as magical"
    (is (every? #(= :magical (mi/classify %)) mi/magic-items))))

(deftest explicit-flag-always-wins
  (testing "a stored true is honoured even with no magical evidence at all"
    (let [item (custom {::mi/name "Plain Looking Sword"
                        ::mi/type :weapon
                        ::mi/rarity :common
                        ::mi/magical? true})]
      (is (= :magical (mi/classify item)))
      (is (mi/magical? item))
      (is (not (mi/mundane? item)))))
  (testing "a stored false is honoured even when the item looks magical"
    ;; A user who calls their attunement-requiring, stat-boosting heirloom
    ;; mundane is allowed to be wrong. Inference never overrides them.
    (let [item (custom {::mi/name "Family Heirloom"
                        ::mi/type :ring
                        ::mi/rarity :legendary
                        ::mi/attunement #{:any}
                        ::mi/magical? false})]
      (is (= :mundane (mi/classify item)))
      (is (mi/mundane? item))
      (is (not (mi/magical? item)))))
  (testing "and a stored false is honoured on a built-in item too"
    (is (= :mundane (mi/classify {::mi/name "Shipped Rope" ::mi/magical? false})))))

(deftest infers-magical-from-evidence
  (testing "attunement"
    (is (= :magical (mi/classify (custom {::mi/name "Band"
                                          ::mi/type :other
                                          ::mi/attunement #{:any}})))))
  (testing "modifiers"
    (is (= :magical (mi/classify (custom {::mi/name "Boots"
                                          ::mi/type :other
                                          ::mi/modifiers [{::mod/key :speed}]})))))
  (testing "attack bonus"
    (is (= :magical (mi/classify (custom {::mi/name "Sword"
                                          ::mi/type :weapon
                                          ::mi/magical-attack-bonus 1})))))
  (testing "damage bonus"
    (is (= :magical (mi/classify (custom {::mi/name "Sword"
                                          ::mi/type :weapon
                                          ::mi/magical-damage-bonus 2})))))
  (testing "AC bonus"
    (is (= :magical (mi/classify (custom {::mi/name "Plate"
                                          ::mi/type :armor
                                          ::mi/magical-ac-bonus 1})))))
  (testing "a type that has no mundane form"
    (doseq [t [:ring :wand :rod :scroll :potion]]
      (is (= :magical (mi/classify (custom {::mi/name "Thing" ::mi/type t})))
          (str t " has no mundane form"))))
  (testing "a rarity only a magic item can carry"
    (doseq [r [:uncommon :rare :very-rare :legendary :varies]]
      (is (= :magical (mi/classify (custom {::mi/name "Thing"
                                            ::mi/type :weapon
                                            ::mi/rarity r})))
          (str r " implies a magic item")))))

(deftest zero-bonuses-are-not-evidence
  (testing "an explicit 0 bonus is the builder's empty state, not a magic bonus"
    (is (= :mundane (mi/classify (custom {::mi/name "Club"
                                          ::mi/type :weapon
                                          ::mi/rarity :common
                                          ::mi/magical-attack-bonus 0
                                          ::mi/magical-damage-bonus 0
                                          ::mi/magical-ac-bonus 0}))))))

(deftest empty-collections-are-not-evidence
  (is (= :mundane (mi/classify (custom {::mi/name "Rope"
                                        ::mi/type :other
                                        ::mi/rarity :common
                                        ::mi/attunement #{}
                                        ::mi/modifiers []})))))

(deftest infers-mundane-for-ordinary-gear
  (testing "the case the whole change exists to fix"
    (doseq [t [:weapon :armor :other]]
      (is (= :mundane (mi/classify (custom {::mi/name "Homemade Thing"
                                            ::mi/type t
                                            ::mi/rarity :common})))
          (str t " with no magic in it is ordinary gear"))))
  (testing "no rarity recorded at all is still ordinary gear"
    (is (= :mundane (mi/classify (custom {::mi/name "Bastard Sword"
                                          ::mi/type :weapon})))))
  (testing "the item builder's long-standing namespaced armor type is recognised"
    ;; The Type dropdown writes ::mi/armor for "Armor" rather than :armor.
    ;; Stored items carry it, so classification has to know about it.
    (is (= :mundane (mi/classify (custom {::mi/name "Boiled Leather"
                                          ::mi/type ::mi/armor
                                          ::mi/rarity :common}))))))

(deftest ambiguous-legacy-items-are-left-unreviewed
  (testing "the item builder's default shape tells us nothing either way"
    (let [item (custom {::mi/name "Some Old Item"
                        ::mi/type :wondrous-item
                        ::mi/rarity :common})]
      (is (= :unreviewed (mi/classify item)))
      (is (mi/unreviewed? item))))
  (testing "an unreviewed item is still TREATED as magical"
    ;; This is the behaviour-preservation guarantee: until someone says
    ;; otherwise, an unclassifiable legacy item acts exactly as it always has.
    (let [item (custom {::mi/name "Some Old Item"
                        ::mi/type :wondrous-item
                        ::mi/rarity :common})]
      (is (mi/magical? item))
      (is (not (mi/mundane? item))))))

(deftest ensure-classified-only-ever-adds
  (testing "an item that already has the flag is returned untouched"
    (let [item (custom {::mi/name "Thing" ::mi/type :weapon ::mi/magical? true})]
      (is (identical? item (mi/ensure-classified item)))
      (is (identical? item (mi/ensure-classified item false)))))
  (testing "a confidently classified item gets the flag it was already acting on"
    (is (true? (::mi/magical? (mi/ensure-classified
                               (custom {::mi/name "Wand" ::mi/type :wand})))))
    (is (false? (::mi/magical? (mi/ensure-classified
                                (custom {::mi/name "Club" ::mi/type :weapon}))))))
  (testing "an unreviewed item is left alone unless a fallback is supplied"
    (let [item (custom {::mi/name "Old" ::mi/type :wondrous-item ::mi/rarity :common})]
      (is (not (contains? (mi/ensure-classified item) ::mi/magical?)))
      (is (true? (::mi/magical? (mi/ensure-classified item true))))
      (is (false? (::mi/magical? (mi/ensure-classified item false))))))
  (testing "no other attribute is disturbed"
    (let [item (custom {::mi/name "Old" ::mi/type :wondrous-item ::mi/rarity :common
                        ::mi/description "grandfathered"
                        :db/id 4242})]
      (is (= item (dissoc (mi/ensure-classified item true) ::mi/magical?))))))

(deftest resolve-classification-preserves-behaviour
  (testing "stamps exactly what the item was already being treated as"
    (doseq [item [(custom {::mi/name "Old" ::mi/type :wondrous-item ::mi/rarity :common})
                  (custom {::mi/name "Club" ::mi/type :weapon})
                  (custom {::mi/name "Wand" ::mi/type :wand})]]
      (is (= (mi/magical? item)
             (::mi/magical? (mi/resolve-classification item)))
          "resolving must not change what the item is")))
  (testing "resolving is idempotent"
    (let [once (mi/resolve-classification (custom {::mi/name "Old" ::mi/type :wondrous-item}))]
      (is (= once (mi/resolve-classification once))))))

(deftest magical-flag-survives-the-save-path
  (testing "from-internal-item must not drop a mundane classification"
    ;; from-internal-item whitelists keys, so an un-whitelisted ::magical?
    ;; would be silently discarded and the item would revert to unreviewed on
    ;; every single save.
    (let [serialized (mi/from-internal-item (custom {::mi/name "Rope"
                                                     ::mi/type :other
                                                     ::mi/magical? false}))]
      (is (contains? serialized ::mi/magical?))
      (is (false? (::mi/magical? serialized)))))
  (testing "and must not drop a magical classification"
    (let [serialized (mi/from-internal-item (custom {::mi/name "Wand of Wonder"
                                                     ::mi/type :wand
                                                     ::mi/magical? true}))]
      (is (true? (::mi/magical? serialized)))))
  (testing "resolving BEFORE serializing is what keeps the answer right"
    ;; from-internal-item drops ::mi/owner, and classification needs it to know
    ;; the item is user-built. Resolve first, serialize second — the other order
    ;; would stamp every mundane item magical on save.
    (let [mundane (custom {::mi/name "Bastard Sword" ::mi/type :weapon})]
      (is (false? (::mi/magical? (mi/from-internal-item
                                  (mi/resolve-classification mundane)))))))
  (testing "an item with no flag still serializes without one"
    (is (not (contains? (mi/from-internal-item
                         (custom {::mi/name "Old" ::mi/type :wondrous-item}))
                        ::mi/magical?)))))

(deftest classification-flag-is-a-valid-magic-item
  (testing "the save-path spec accepts both values"
    (is (spec/valid? ::mi/magic-item {::mi/name "Rope" ::mi/magical? false}))
    (is (spec/valid? ::mi/magic-item {::mi/name "Wand" ::mi/magical? true}))
    (is (not (spec/valid? ::mi/magic-item {::mi/name "Rope" ::mi/magical? "no"})))))
