(ns orcpub.dnd.e5.magic-items-test
  (:require [clojure.test :refer [testing deftest is]]
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
      (is (= (:base-ac 12 first-expanded)))))
  (testing "multiple subtypes expand to multiple items"
    (let [item {mi/name-key "My Item"
                ::mi/type :armor
                ::mi/subtypes [:plate :chain-mail]}
          expansion (mi/expand-armor item)
          names (set (map :name expansion))]
      (prn "NAMES" names)
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
      (is (= 1 (::mi/damage-die-count result)))
      (is (= 4 (::mi/damage-die result)))
      (is (= :simple (::mi/weapon-type result)))
      (is (= :melee (::mi/melee-ranged result)))
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
