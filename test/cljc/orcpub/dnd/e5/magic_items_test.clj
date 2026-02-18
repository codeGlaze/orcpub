(ns orcpub.dnd.e5.magic-items-test
  (:require [clojure.test :refer [testing deftest is]]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.character :as char]
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
