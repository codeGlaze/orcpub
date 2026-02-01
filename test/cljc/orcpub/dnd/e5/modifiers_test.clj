(ns orcpub.dnd.e5.modifiers_test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.test.alpha :as stest]
            [orcpub.dnd.e5.modifiers :as dnd5-mods]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [orcpub.modifiers :as mods]))

(deftest add-bonus-nil-handling
  (testing "add-bonus treats nil as 0"
    (is (= 5 (dnd5-mods/add-bonus nil 5)))
    (is (= 3 (dnd5-mods/add-bonus 3 nil)))
    (is (= 0 (dnd5-mods/add-bonus nil nil)))
    (is (= 7 (dnd5-mods/add-bonus 3 4)))))

(deftest enough-levels?-logic
  (testing "nil level always passes"
    (is (true? (dnd5-mods/enough-levels? nil 1 {} nil))))
  (testing "checks total-levels when class-key is nil"
    (is (true? (dnd5-mods/enough-levels? nil 5 {} 3)))
    (is (false? (dnd5-mods/enough-levels? nil 2 {} 3))))
  (testing "checks class-level map when class-key is provided"
    (let [class-level {:fighter 5 :wizard 3}]
      (is (true? (dnd5-mods/enough-levels? :fighter 8 class-level 5)))
      (is (false? (dnd5-mods/enough-levels? :fighter 8 class-level 6)))
      (is (true? (dnd5-mods/enough-levels? :wizard 8 class-level 3)))
      (is (false? (dnd5-mods/enough-levels? :wizard 8 class-level 4))))))

(deftest equipment-cfg-conversion
  (testing "integer is converted to quantity map"
    (let [result (dnd5-mods/equipment-cfg 3)]
      (is (= 3 (::char-equip/quantity result)))
      (is (true? (::char-equip/equipped? result)))))
  (testing "map is passed through unchanged"
    (let [cfg {::char-equip/quantity 2 ::char-equip/equipped? false}]
      (is (= cfg (dnd5-mods/equipment-cfg cfg))))))

(deftest resistance-cfg-structure
  (testing "resistance-cfg creates correct shape"
    (is (= {:value :fire :qualifier nil}
           (dnd5-mods/resistance-cfg :fire nil)))
    (is (= {:value :cold :qualifier "nonmagical"}
           (dnd5-mods/resistance-cfg :cold "nonmagical")))))

(deftest add-spell-accumulation
  (testing "add-spell inserts into spells-known structure"
    (let [cfg {:class :wizard :key :magic-missile}
          result (dnd5-mods/add-spell {} 1 cfg)]
      (is (= cfg (get-in result [1 [:wizard :magic-missile]])))))
  (testing "add-spell merges into existing spell level"
    (let [existing {1 {[:wizard :shield] {:class :wizard :key :shield}}}
          cfg {:class :wizard :key :fireball}
          result (dnd5-mods/add-spell existing 1 cfg)]
      (is (some? (get-in result [1 [:wizard :shield]])))
      (is (some? (get-in result [1 [:wizard :fireball]]))))))

(deftest spell-data-structure
  (testing "spell-data creates correct map"
    (is (= {:key :fireball
            :ability :int
            :qualifier nil
            :class :wizard}
           (dnd5-mods/spell-data :fireball :int nil :wizard)))))

(deftest mods-map-completeness
  (testing "mods-map contains expected modifier types"
    (is (fn? (:ability dnd5-mods/mods-map)))
    (is (fn? (:damage-resistance dnd5-mods/mods-map)))
    (is (fn? (:darkvision dnd5-mods/mods-map)))
    (is (fn? (:speed dnd5-mods/mods-map)))
    (is (fn? (:flying-speed-bonus dnd5-mods/mods-map)))
    (is (fn? (:swimming-speed dnd5-mods/mods-map)))
    (is (fn? (:climbing-speed dnd5-mods/mods-map)))))

(deftest build-modifiers-filters-nil
  (testing "build-modifiers removes nil results from unknown keys"
    (let [plain-mod (mods/mod-f "test" "+1" identity :test-key [])
          result (dnd5-mods/build-modifiers [plain-mod])]
      (is (= 1 (count result)))
      (is (= "test" (::mods/name (first result)))))))
