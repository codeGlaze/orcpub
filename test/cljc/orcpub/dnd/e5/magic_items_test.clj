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
  (testing "toggles off an existing subtype, unless it is the last one"
    ;; This used to yield #{}. An item with no subtype has no damage die, no
    ;; proficiency category and no base AC of its own, and unticking the last
    ;; box was the only route back into that state once a type had been
    ;; chosen -- with no signal until a "d+2" turned up on a character sheet.
    (let [two {::mi/subtypes #{:sword :axe}}]
      (is (= #{:axe} (::mi/subtypes (mi/apply-subtype-toggle two :sword)))
          "one of several still comes off"))
    (let [one {::mi/subtypes #{:sword}}]
      (is (= #{:sword} (::mi/subtypes (mi/apply-subtype-toggle one :sword)))
          "the last one stays")))
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
                                          ::mi/magical-attack-bonus 0
                                          ::mi/magical-damage-bonus 0
                                          ::mi/magical-ac-bonus 0}))))))

(deftest empty-collections-are-not-evidence
  (is (= :mundane (mi/classify (custom {::mi/name "Rope"
                                        ::mi/type :other
                                        ::mi/attunement #{}
                                        ::mi/modifiers []})))))

(deftest modifiers-count-as-evidence-in-either-shape
  (testing "the builder's internal shape is classified the same as the wire shape"
    ;; to-internal-item unconditionally moves ::modifiers to
    ;; ::internal-modifiers, and the builder ALWAYS holds the internal shape.
    ;; Reading only ::modifiers meant a user could add a modifier, switch Type
    ;; to Weapon, and watch the item silently classify itself mundane --
    ;; discarding the grid they had just filled in.
    (let [wire (custom {::mi/name "Strongarm Blade"
                        ::mi/type :weapon
                        ::mi/rarity :common
                        ::mi/modifiers [{::mod/key :ability
                                         ::mod/args [{::mod/keyword-arg ::char/str}
                                                     {::mod/int-arg 1}]}]})
          internal (mi/to-internal-item wire)]
      (is (not (contains? internal ::mi/modifiers))
          "precondition: the internal shape really has moved the key")
      (is (seq (::mi/internal-modifiers internal))
          "precondition: the modifiers survived the move")
      (is (= :magical (mi/classify wire)))
      (is (= :magical (mi/classify internal))
          "the same item must not change classification by changing shape"))))

(deftest infers-mundane-for-ordinary-gear
  (testing "the case the whole change exists to fix"
    (doseq [t [:weapon :armor :other]]
      (is (= :mundane (mi/classify (custom {::mi/name "Homemade Thing"
                                            ::mi/type t})))
          (str t " with no magic in it is ordinary gear"))))
  (testing "no rarity recorded at all is still ordinary gear"
    (is (= :mundane (mi/classify (custom {::mi/name "Bastard Sword"
                                          ::mi/type :weapon})))))
  (testing "the item builder's long-standing namespaced armor type is recognised"
    ;; The Type dropdown writes ::mi/armor for "Armor" rather than :armor.
    ;; Stored items carry it, so classification has to know about it.
    (is (= :mundane (mi/classify (custom {::mi/name "Boiled Leather"
                                          ::mi/type ::mi/armor}))))))

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

;; ---------------------------------------------------------------------------
;; Suppressing magical mechanics on a mundane item
;;
;; Unticking "Magic item" hides the magical fields and stops them applying. It
;; must never delete them: a checkbox that destroys data on a mis-click is a
;; worse bug than the one being fixed.
;; ---------------------------------------------------------------------------

(def ^:private enchanted-blade
  (custom {::mi/name "Rimefang"
           ::mi/type :weapon
           ::mi/rarity :rare
           ::mi/attunement #{:any}
           ::mi/magical-attack-bonus 1
           ::mi/magical-damage-bonus 2
           ::mi/magical-ac-bonus 0
           ::mi/modifiers [{::mod/key :damage-resistance
                            ::mod/args [{::mod/keyword-arg :cold}]}]
           ::mi/description "Cold to the touch."
           ::mi/magical-properties "Frost creeps along the blade when drawn."}))

(deftest magical-properties-reports-only-what-is-recorded
  (let [props (mi/magical-properties enchanted-blade)]
    (is (= #{::mi/attunement ::mi/modifiers ::mi/magical-properties
             ::mi/magical-attack-bonus ::mi/magical-damage-bonus}
           (set (keys props)))
        "a zero AC bonus is the builder's empty state, not a recorded property"))
  (testing "ordinary gear has none"
    (is (not (mi/has-magical-properties?
              (custom {::mi/name "Club" ::mi/type :weapon}))))
    (is (not (mi/has-magical-properties?
              (custom {::mi/name "Club" ::mi/type :weapon
                       ::mi/attunement #{} ::mi/modifiers []}))))))

(deftest effective-item-suppresses-magic-on-a-mundane-item
  (let [mundane (assoc enchanted-blade ::mi/magical? false)
        effective (mi/effective-item mundane)]
    (testing "no magical mechanics survive into what the app builds from"
      (is (not (mi/has-magical-properties? effective)))
      (doseq [k mi/magical-property-keys]
        (is (not (contains? effective k)) (str k " must not apply"))))
    (testing "everything else is untouched"
      (is (= "Rimefang" (::mi/name effective)))
      (is (= :weapon (::mi/type effective)))
      (is (= "Cold to the touch." (::mi/description effective)))
      (is (= :rare (::mi/rarity effective))
          "rarity is inert, so it rides along and comes back if re-ticked"))
    (testing "and it stays mundane after stripping, so this is stable"
      (is (mi/mundane? effective))
      (is (= effective (mi/effective-item effective))))))

(deftest effective-item-leaves-magic-items-alone
  (testing "an explicitly magical item passes through untouched"
    (is (identical? enchanted-blade (mi/effective-item enchanted-blade))))
  (testing "so does an unreviewed legacy item — it is still treated as magical"
    (let [legacy (custom {::mi/name "Old Trinket"
                          ::mi/type :wondrous-item
                          ::mi/rarity :common
                          ::mi/attunement #{:any}})]
      (is (identical? legacy (mi/effective-item legacy))))))

(deftest suppression-is-not-deletion
  (testing "the stored item keeps everything, so re-ticking restores it"
    ;; effective-item is a read-time view. Nothing writes it back.
    (let [stored (assoc enchanted-blade ::mi/magical? false)]
      (mi/effective-item stored)
      (is (mi/has-magical-properties? stored))
      (let [re-ticked (assoc stored ::mi/magical? true)]
        (is (= (dissoc enchanted-blade ::mi/magical?)
               (dissoc (mi/effective-item re-ticked) ::mi/magical?))
            "ticking Magic item again brings back exactly what was there")))))

(deftest clearing-is-explicit-and-total
  (testing "without-magical-properties is the deliberate destructive version"
    (let [cleared (mi/without-magical-properties enchanted-blade)]
      (is (not (mi/has-magical-properties? cleared)))
      (is (= "Rimefang" (::mi/name cleared)))
      (is (= "Cold to the touch." (::mi/description cleared))))))

(deftest a-confidently-mundane-item-never-has-anything-to-suppress
  (testing "the backfill can only ever mark evidence-free items mundane"
    ;; This is why the automatic path cannot suppress anyone's mechanics: an
    ;; item classify calls :mundane on its own has no magical properties by
    ;; definition. Only a human ticking the box can create that situation.
    (doseq [item [(custom {::mi/name "Rope" ::mi/type :other})
                  (custom {::mi/name "Club" ::mi/type :weapon})
                  (custom {::mi/name "Leather" ::mi/type ::mi/armor})]]
      (is (mi/mundane? item))
      (is (not (mi/has-magical-properties? item)))
      (is (= item (mi/effective-item item))))))

;; ---------------------------------------------------------------------------
;; How long are suspended properties kept?
;;
;; The answer has to be "until the owner says otherwise", with no expiry and no
;; event that quietly drops them — otherwise the UI's promise ("kept, not
;; applied") becomes a lie at some later date. There is exactly one write path
;; for an item (the item builder's save), so these tests pin that path.
;; ---------------------------------------------------------------------------

(defn- save-round-trip
  "What the item builder actually does: resolve the classification, serialize
   for the API, and read the stored result back into the builder."
  [builder-item]
  (mi/to-internal-item
   (mi/from-internal-item (mi/resolve-classification builder-item))))

(deftest every-magical-property-survives-the-save-path
  (testing "each key is whitelisted in from-internal-item"
    ;; from-internal-item drops anything not on its whitelist. If a magical
    ;; property is ever added without being whitelisted, it would vanish on the
    ;; next save and "kept, not applied" would silently stop being true.
    (let [saved (mi/from-internal-item
                 (mi/to-internal-item (assoc enchanted-blade ::mi/magical? false)))]
      (doseq [k mi/magical-property-keys]
        ;; ::internal-modifiers is the builder's working shape; it is carried
        ;; by ::modifiers on the stored side.
        (when (not= k ::mi/internal-modifiers)
          (is (contains? saved k)
              (str k " must survive a save on a mundane item"))))
      (is (mi/has-magical-properties? saved)))))

(deftest suspended-properties-survive-repeated-saves
  (testing "they are still there after ten open-and-save cycles"
    ;; No decay, no drift: the answer to \"how long are they kept\" is
    ;; \"indefinitely\", and re-saving is what would erode them if anything did.
    (let [mundane (mi/to-internal-item (assoc enchanted-blade ::mi/magical? false))
          final (nth (iterate save-round-trip mundane) 10)]
      (is (mi/mundane? final))
      (is (mi/has-magical-properties? final))
      (is (= #{:any} (::mi/attunement final)))
      (is (= 1 (::mi/magical-attack-bonus final)))
      (is (= 2 (::mi/magical-damage-bonus final)))
      (is (seq (get-in final [::mi/internal-modifiers :damage-resistance]))))))

(deftest switching-back-restores-everything
  (testing "mundane → magical returns the item to exactly where it started"
    (let [started (mi/to-internal-item enchanted-blade)
          there-and-back (-> started
                             (assoc ::mi/magical? false)
                             save-round-trip
                             (assoc ::mi/magical? true)
                             save-round-trip)]
      (is (mi/magical? there-and-back))
      (is (= (dissoc started ::mi/magical? ::mi/owner)
             (dissoc there-and-back ::mi/magical? ::mi/owner))))))

(deftest only-an-explicit-clear-removes-them
  (testing "nothing but without-magical-properties takes them away"
    (let [mundane (assoc enchanted-blade ::mi/magical? false)]
      (is (mi/has-magical-properties? (mi/resolve-classification mundane)))
      (is (mi/has-magical-properties? (mi/ensure-classified mundane)))
      (is (mi/has-magical-properties? (save-round-trip (mi/to-internal-item mundane))))
      ;; effective-item hides them from the app, but it is a read-time view —
      ;; it is never what gets saved.
      (is (not (mi/has-magical-properties? (mi/effective-item mundane))))
      (is (not (mi/has-magical-properties? (mi/without-magical-properties mundane)))))))

;; ---------------------------------------------------------------------------
;; Magical Properties
;;
;; Prose about what the magic DOES, stored apart from ::description so the two
;; can be told apart. Plenty of 5e magic items -- Moon-Touched Sword being the
;; stock example -- carry no mechanical bonus at all and live entirely here.
;; ---------------------------------------------------------------------------

(deftest magical-properties-survive-the-save-round-trip
  (testing "the field is whitelisted, so a save does not silently drop it"
    ;; from-internal-item's select-keys is a whitelist: an attribute missing
    ;; from it vanishes on every save. ::magical? was already caught by this
    ;; once on this branch.
    (let [item {::mi/name "Moon-Touched Sword"
                ::mi/type :weapon
                ::mi/rarity :common
                ::mi/magical? true
                ::mi/magical-properties "Sheds dim light in a 5-foot radius."
                ::mi/owner "kaylee"}]
      (is (= "Sheds dim light in a 5-foot radius."
             (::mi/magical-properties (mi/from-internal-item item)))))))

(deftest magical-properties-are-suspended-with-the-rest-of-the-magic
  (testing "marking an item mundane switches the prose off along with the bonuses"
    (let [item {::mi/name "Moon-Touched Sword"
                ::mi/type :weapon
                ::mi/magical? false
                ::mi/magical-properties "Sheds dim light in a 5-foot radius."
                ::mi/owner "kaylee"}]
      (is (mi/has-magical-properties? item)
          "the prose alone counts as magic held in reserve")
      (is (nil? (::mi/magical-properties (mi/effective-item item)))
          "and nothing downstream sees it while the item is mundane")
      (testing "but the stored item keeps it, so the switch is reversible"
        (is (= "Sheds dim light in a 5-foot radius."
               (::mi/magical-properties item)))))))

(deftest an-item-whose-only-magic-is-prose-is-still-valid
  (testing "no mechanical bonus is required for a magic item to be well-formed"
    (is (spec/valid? ::mi/magic-item
                     {::mi/name "Moon-Touched Sword"
                      ::mi/type :weapon
                      ::mi/rarity :common
                      ::mi/magical? true
                      ::mi/magical-properties "Sheds dim light."}))))

;; ---------------------------------------------------------------------------
;; Length bounds
;;
;; Items live in the database rather than in an .orcbrew file the owner keeps,
;; so an unbounded text field is a route for pushing arbitrary bytes into
;; storage. The bounds sit above the client's own caps on purpose: one that
;; merely matched the UI would reject content the UI allowed to be created
;; before the cap existed, stranding an owner who could no longer save.
;; ---------------------------------------------------------------------------

(defn- item-of-length [k n]
  {::mi/name "Longwinded Blade"
   ::mi/type :weapon
   ::mi/magical? true
   k (apply str (repeat n "x"))})

(deftest prose-fields-are-bounded
  (doseq [k [::mi/description ::mi/magical-properties]]
    (testing (str k " accepts ordinary prose")
      (is (spec/valid? ::mi/magic-item (item-of-length k 5000))))
    (testing (str k " accepts content up to the ceiling")
      (is (spec/valid? ::mi/magic-item (item-of-length k mi/max-prose-length))))
    (testing (str k " rejects a payload past it")
      (is (not (spec/valid? ::mi/magic-item
                            (item-of-length k (inc mi/max-prose-length))))))))

(deftest the-bound-is-above-the-clients-own-cap
  (testing "so nothing the textarea allowed can be rejected on save"
    ;; branding/field-limits caps the textareas at 50,000. If the server bound
    ;; ever drops below that, the UI can produce an item it cannot save.
    (is (> mi/max-prose-length 50000))))

(deftest names-are-bounded
  ;; Expressed against the constant, not a literal. This test asserted a
  ;; 200-character name was fine, which was true only while the bound was 500.
  (is (spec/valid? ::mi/magic-item
                   {::mi/name (apply str "a" (repeat (- mi/max-name-length 20) "a"))})
      "a comfortably ordinary name")
  (is (spec/valid? ::mi/magic-item
                   {::mi/name (apply str "a" (repeat (dec mi/max-name-length) "a"))})
      "exactly at the limit")
  (is (not (spec/valid? ::mi/magic-item
                        {::mi/name (apply str (repeat (inc mi/max-name-length) "a"))})))
  (testing "the existing starts-with-letter rule still applies"
    (is (not (spec/valid? ::mi/magic-item {::mi/name "9 Lives"})))))

(deftest an-ordinary-item-is-unaffected
  (testing "the bounds do not disturb normal content"
    (is (spec/valid? ::mi/magic-item
                     {::mi/name "Moon-Touched Sword"
                      ::mi/type :weapon
                      ::mi/rarity :common
                      ::mi/magical? true
                      ::mi/description "A plain-looking longsword."
                      ::mi/magical-properties "Sheds dim light in a 5-foot radius."}))))

;; ---------------------------------------------------------------------------
;; :common carries no information
;;
;; Rarity is a magic-item property in 5e -- mundane gear has none -- so a
;; rarity of any kind is not evidence of an ordinary object. And :common is a
;; real magic rarity: a Moon-Touched Sword is a common magic weapon whose whole
;; effect is that it glows, with no attunement and no bonus, so it reaches
;; classify looking exactly like plain gear. It was also the builder's default
;; before this branch. Noise in both directions, so it decides nothing.
;; ---------------------------------------------------------------------------

(deftest common-rarity-decides-nothing-on-its-own
  (testing "an evidence-free item with :common is asked about, not guessed at"
    (doseq [t [:weapon :armor :other]]
      (is (= :unreviewed (mi/classify (custom {::mi/name "Old Thing"
                                               ::mi/type t
                                               ::mi/rarity :common})))
          (str t " with :common and nothing else must go to its owner"))))

  (testing "a Moon-Touched Sword is not talked out of being magical"
    ;; The published item: common, a weapon, no attunement, no bonus, magic
    ;; entirely in prose. The old rule called this ordinary gear.
    (is (= :unreviewed
           (mi/classify (custom {::mi/name "Moon-Touched Sword"
                                 ::mi/type :weapon
                                 ::mi/rarity :common}))))
    (is (mi/magical? (custom {::mi/name "Moon-Touched Sword"
                              ::mi/type :weapon
                              ::mi/rarity :common}))
        "and unreviewed still behaves as magical, exactly as before"))

  (testing "with no rarity at all it is still ordinary gear"
    (is (= :mundane (mi/classify (custom {::mi/name "Bastard Sword"
                                          ::mi/type :weapon})))))

  (testing "and :common is not evidence of magic either"
    (is (not (contains? mi/magical-rarities :common)))))

;; ---------------------------------------------------------------------------
;; What must be answered before an item can be saved
;;
;; A weapon or armour item with no type chosen carries none of the fields the
;; app reads from it, and the sheet renders a damage die with no size. The fix
;; is one click on the same screen, so the save waits rather than storing
;; something broken.
;; ---------------------------------------------------------------------------

(deftest a-weapon-needs-a-weapon-type
  (let [reasons (mi/incomplete-reasons {::mi/name "Untyped Blade" ::mi/type :weapon})]
    (is (= 1 (count reasons)))
    (is (re-find #"Weapon Type" (first reasons)))
    (is (not (mi/ready-to-save? {::mi/name "Untyped Blade" ::mi/type :weapon}))))
  (testing "and is ready once one is chosen"
    (is (mi/ready-to-save? {::mi/name "Untyped Blade"
                            ::mi/type :weapon
                            ::mi/subtypes #{:other}}))))

(deftest armor-needs-an-armor-type
  ;; Armour degrades more quietly than a weapon -- base-ac defaults to 10 at
  ;; the render site, so a typeless breastplate shows a believable number and
  ;; is worth nothing. That is asked for on the same grounds: quietly wrong is
  ;; harder to notice than visibly broken.
  (doseq [t [:armor ::mi/armor]]
    (is (not (mi/ready-to-save? {::mi/name "Untyped Mail" ::mi/type t}))
        (str t " with no subtype is not ready"))
    (is (mi/ready-to-save? {::mi/name "Untyped Mail" ::mi/type t
                            ::mi/subtypes #{:chain-mail}}))))

(deftest other-item-types-need-no-subtype
  (testing "a wondrous item, ring or potion has no type list to choose from"
    (doseq [t [:wondrous-item :ring :wand :rod :scroll :potion :other]]
      (is (mi/ready-to-save? {::mi/name "Odd Trinket" ::mi/type t})
          (str t " must not be blocked")))))

(deftest an-item-needs-a-name
  (is (not (mi/ready-to-save? {::mi/type :wondrous-item})))
  (is (not (mi/ready-to-save? {::mi/name "   " ::mi/type :wondrous-item})))
  (testing "the message says so plainly"
    (is (re-find #"name" (first (mi/incomplete-reasons {::mi/type :wondrous-item}))))))

(deftest a-complete-item-is-ready
  (is (mi/ready-to-save? {::mi/name "Moon-Touched Sword"
                          ::mi/type :weapon
                          ::mi/subtypes #{:longsword}
                          ::mi/magical? true})))

;; ---------------------------------------------------------------------------
;; A weapon or armour item always has a type
;; ---------------------------------------------------------------------------

(deftest choosing-weapon-as-the-type-seeds-a-usable-base
  (let [item (mi/with-default-subtype {::mi/name "Fresh Blade" ::mi/type :weapon})]
    (is (= #{:other} (::mi/subtypes item)) "Custom is the default")
    (testing "and it carries real values, not an empty shell"
      (is (= 1 (:orcpub.dnd.e5.weapons/damage-die-count item)))
      (is (= 4 (:orcpub.dnd.e5.weapons/damage-die item)))
      (is (= :simple (:orcpub.dnd.e5.weapons/type item)))
      (is (= :bludgeoning (:orcpub.dnd.e5.weapons/damage-type item))))
    (testing "so it is immediately saveable"
      (is (mi/ready-to-save? item)))))

(deftest a-default-never-overrides-a-real-choice
  (let [chosen {::mi/name "Blade" ::mi/type :weapon ::mi/subtypes #{:longsword}}]
    (is (= #{:longsword} (::mi/subtypes (mi/with-default-subtype chosen))))))

(deftest types-with-no-list-to-choose-from-are-left-alone
  (doseq [t [:wondrous-item :ring :potion :other]]
    (is (nil? (::mi/subtypes (mi/with-default-subtype {::mi/name "Trinket" ::mi/type t})))
        (str t " has no subtype list, so nothing is seeded"))))

(deftest the-last-weapon-type-cannot-be-unticked
  (testing "unticking the only choice leaves it in place"
    ;; This was the one way back into the broken state once a type had been
    ;; chosen: untick the last box and the item silently loses its damage die.
    (let [one {::mi/name "Blade" ::mi/type :weapon ::mi/subtypes #{:longsword}}]
      (is (= #{:longsword} (::mi/subtypes (mi/apply-subtype-toggle one :longsword))))))
  (testing "but one of several can still be removed"
    (let [two {::mi/name "Blade" ::mi/type :weapon
               ::mi/subtypes #{:longsword :shortsword}}]
      (is (= #{:shortsword}
             (::mi/subtypes (mi/apply-subtype-toggle two :longsword))))))
  (testing "and a different one can still be added"
    (let [one {::mi/name "Blade" ::mi/type :weapon ::mi/subtypes #{:longsword}}]
      (is (= #{:longsword :shortsword}
             (::mi/subtypes (mi/apply-subtype-toggle one :shortsword)))))))

;; ---------------------------------------------------------------------------
;; The cue map and the notice come from one source
;; ---------------------------------------------------------------------------

(deftest incomplete-fields-matches-incomplete-reasons
  (testing "a field is flagged exactly when there is a reason to flag it"
    ;; Two views of the same answer: the reasons are read, the fields are
    ;; coloured. If they could disagree, the builder would highlight a field it
    ;; had nothing to say about, or say something about a field it left plain.
    (doseq [item [{}
                  {::mi/type :weapon}
                  {::mi/name "Blade" ::mi/type :weapon}
                  {::mi/name "Blade" ::mi/type :weapon ::mi/subtypes #{:other}}
                  {::mi/name "Mail" ::mi/type :armor}
                  {::mi/name "Trinket" ::mi/type :wondrous-item}]]
      (is (= (empty? (mi/incomplete-reasons item))
             (empty? (mi/incomplete-fields item)))
          (str "disagreement for " (pr-str item))))))

(deftest incomplete-fields-uses-the-status-the-cue-expects
  (is (= {:name :missing} (mi/incomplete-fields {::mi/type :wondrous-item})))
  (is (= {:subtypes :missing}
         (mi/incomplete-fields {::mi/name "Blade" ::mi/type :weapon})))
  (is (= {:name :missing :subtypes :missing}
         (mi/incomplete-fields {::mi/type :weapon})))
  (is (= {} (mi/incomplete-fields {::mi/name "Blade" ::mi/type :weapon
                                   ::mi/subtypes #{:other}}))))

(deftest a-name-that-the-spec-will-reject-is-caught-in-the-builder
  (testing "a name not starting with a letter blocks the save"
    ;; ::mi/name requires starts-with-letter?. Without this the builder let it
    ;; through and the only feedback was an opaque 400 from the server.
    (let [item {::mi/name "9 Lives" ::mi/type :wondrous-item}]
      (is (not (mi/ready-to-save? item)))
      (is (re-find #"start with a letter" (first (mi/incomplete-reasons item))))
      (testing "flagged invalid, not missing — the field has a value"
        (is (= {:name :invalid} (mi/incomplete-fields item))))
      (testing "and the spec agrees it would have been rejected"
        (is (not (spec/valid? ::mi/magic-item item))))))
  (testing "correcting it clears the block"
    (is (mi/ready-to-save? {::mi/name "Nine Lives" ::mi/type :wondrous-item}))))

(deftest an-over-long-name-is-explained-not-just-rejected
  (testing "the builder catches it, with the numbers in the message"
    ;; A legacy name over the bound is the one case someone hits without having
    ;; just typed it, so the message has to be actionable on its own.
    (let [long-name (apply str "A" (repeat mi/max-name-length "a"))
          item {::mi/name long-name ::mi/type :wondrous-item}]
      (is (> (count long-name) mi/max-name-length))
      (is (not (mi/ready-to-save? item)))
      (let [reason (first (filter #(re-find #"characters" %)
                                  (mi/incomplete-reasons item)))]
        (is (some? reason) "a reason mentioning the length is present")
        (is (re-find (re-pattern (str (count long-name))) reason)
            "it names the actual length")
        (is (re-find (re-pattern (str mi/max-name-length)) reason)
            "and the limit"))
      (testing "flagged invalid — the field has a value, it is just too long"
        (is (= {:name :invalid} (mi/incomplete-fields item))))
      (testing "and the spec agrees, so the server would reject it too"
        (is (not (spec/valid? ::mi/magic-item item))))))
  (testing "a name at the limit is fine"
    (is (mi/ready-to-save?
         {::mi/name (apply str "A" (repeat (dec mi/max-name-length) "a"))
          ::mi/type :wondrous-item}))))

(deftest the-name-bound-is-proportionate
  (testing "long enough for any real name, short enough to mean something"
    (is (<= 60 mi/max-name-length 150))
    (is (spec/valid? ::mi/magic-item
                     {::mi/name "Adamantine Armor, Chain Shirt"
                      ::mi/type :armor ::mi/subtypes #{:chain-mail}}))))
