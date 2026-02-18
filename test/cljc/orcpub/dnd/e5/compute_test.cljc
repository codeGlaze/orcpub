(ns orcpub.dnd.e5.compute-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as s]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.compute :as compute]))

;; -- compute-plugin-vals --

(deftest compute-plugin-vals-empty-input
  (testing "empty map returns empty seq"
    (is (empty? (compute/compute-plugin-vals {}))))
  (testing "nil returns empty seq"
    (is (empty? (compute/compute-plugin-vals nil)))))

(deftest compute-plugin-vals-filters-disabled-plugins
  (let [plugins {:p1 {:name "Active"}
                 :p2 {:name "Disabled" :disabled? true}
                 :p3 {:name "Also Active"}}
        result (compute/compute-plugin-vals plugins)
        names (set (map :name result))]
    (is (= 2 (count result)))
    (is (contains? names "Active"))
    (is (contains? names "Also Active"))
    (is (not (contains? names "Disabled")))))

(deftest compute-plugin-vals-filters-disabled-entries
  (let [plugins {:p1 {::e5/spells {:fireball {:name "Fireball"}
                                    :sleep {:name "Sleep" :disabled? true}
                                    :shield {:name "Shield"}}}}
        result (compute/compute-plugin-vals plugins)
        spells (::e5/spells (first result))]
    (is (= #{:fireball :shield} (set (keys spells))))
    (is (= "Fireball" (get-in spells [:fireball :name])))
    (is (= "Shield" (get-in spells [:shield :name])))))

(deftest compute-plugin-vals-preserves-non-collection-values
  ;; Non-map values like strings, numbers, booleans should pass through unchanged
  (let [plugins {:p1 {:name "Plugin" :version 2 :active true}}
        result (first (compute/compute-plugin-vals plugins))]
    (is (= "Plugin" (:name result)))
    (is (= 2 (:version result)))
    (is (true? (:active result)))))

(deftest compute-plugin-vals-multiple-plugins-with-mixed-entries
  ;; Two plugins, each with some disabled entries — verify cross-plugin isolation
  (let [plugins {:p1 {::e5/spells {:a {:name "A"} :b {:name "B" :disabled? true}}}
                 :p2 {::e5/spells {:c {:name "C" :disabled? true} :d {:name "D"}}}}
        result (compute/compute-plugin-vals plugins)
        all-spell-keys (set (mapcat (comp keys ::e5/spells) result))]
    (is (= #{:a :d} all-spell-keys))))

;; -- filter-spells --

(def sample-spells
  [{:name "Fireball" :key :fireball}
   {:name "Fire Bolt" :key :fire-bolt}
   {:name "Shield" :key :shield}
   {:name "Magic Missile" :key :magic-missile}
   {:name "Cure Wounds" :key :cure-wounds}])

(deftest filter-spells-partial-match
  (let [result (compute/filter-spells "fire" sample-spells)]
    (is (= #{"Fireball" "Fire Bolt"} (set (map :name result))))))

(deftest filter-spells-case-insensitive
  ;; Uppercase input should still match lowercase names
  (is (= #{"Fireball" "Fire Bolt"}
         (set (map :name (compute/filter-spells "FIRE" sample-spells))))))

(deftest filter-spells-sorts-alphabetically
  (let [result (compute/filter-spells "fire" sample-spells)]
    (is (= ["Fire Bolt" "Fireball"] (mapv :name result)))))

(deftest filter-spells-all-match-on-empty-text
  (is (= 5 (count (compute/filter-spells "" sample-spells)))))

(deftest filter-spells-no-match
  (is (empty? (compute/filter-spells "nonexistent" sample-spells))))

(deftest filter-spells-single-char-still-matches
  ;; Even a single character should filter
  (let [result (compute/filter-spells "s" sample-spells)]
    ;; "Shield", "Magic Missile", "Cure Wounds" all contain 's'
    (is (= #{"Shield" "Magic Missile" "Cure Wounds"}
           (set (map :name result))))))

;; -- filter-items --

(def sample-items
  [{::mi/name "Bag of Holding" :key :bag-of-holding}
   {::mi/name "Bag of Tricks" :key :bag-of-tricks}
   {::mi/name "Cloak of Protection" :key :cloak-of-protection}
   {::mi/name "Deck of Many Things" :key :deck-of-many-things}])

(deftest filter-items-partial-match
  (let [result (compute/filter-items "bag" sample-items)]
    (is (= #{"Bag of Holding" "Bag of Tricks"}
           (set (map mi/name-key result))))))

(deftest filter-items-case-insensitive
  (is (= 2 (count (compute/filter-items "BAG" sample-items)))))

(deftest filter-items-sorts-by-name-key
  (let [result (compute/filter-items "bag" sample-items)]
    (is (= ["Bag of Holding" "Bag of Tricks"]
           (mapv mi/name-key result)))))

(deftest filter-items-no-match
  (is (empty? (compute/filter-items "vorpal" sample-items))))

;; -- compute-sorted-spells --

(deftest compute-sorted-spells-includes-known-static-spells
  ;; "Acid Splash" and "Bane" are in the static spells data.
  ;; If this fails, the static data loading or merge is broken.
  (let [result (compute/compute-sorted-spells {:plugins {}})
        names (set (map :name result))]
    (is (contains? names "Acid Splash"))
    (is (contains? names "Bane"))
    (is (contains? names "Fireball"))))

(deftest compute-sorted-spells-alphabetical-order
  ;; Verify case-insensitive alphabetical sort by checking known relative positions
  (let [result (compute/compute-sorted-spells {:plugins {}})
        names (vec (map :name result))
        idx-of (fn [n] (.indexOf names n))]
    ;; "Acid Splash" should come before "Bane" which should come before "Fireball"
    (is (< (idx-of "Acid Splash") (idx-of "Bane")))
    (is (< (idx-of "Bane") (idx-of "Fireball")))))

(deftest compute-sorted-spells-static-spells-have-no-edit-event
  ;; Static (non-plugin) spells should NOT have :edit-event
  (let [result (compute/compute-sorted-spells {:plugins {}})
        fireball (first (filter #(= "Fireball" (:name %)) result))]
    (is (some? fireball))
    (is (not (contains? fireball :edit-event)))))

(deftest compute-sorted-spells-plugin-spells-get-edit-event
  (let [db {:plugins {:test {::e5/spells {:zap {:name "Zap" :key :zap}}}}}
        result (compute/compute-sorted-spells db)
        zap (first (filter #(= "Zap" (:name %)) result))]
    (is (some? zap) "Plugin spell should appear in result")
    (is (vector? (:edit-event zap)) "Plugin spells must have :edit-event vector")
    (is (= :orcpub.dnd.e5.spells/edit-spell (first (:edit-event zap))))))

(deftest compute-sorted-spells-plugin-spells-sorted-among-static
  ;; A plugin spell named "Aaa" should appear before "Acid Splash"
  (let [db {:plugins {:test {::e5/spells {:aaa {:name "Aaa" :key :aaa}}}}}
        result (compute/compute-sorted-spells db)
        names (vec (map :name result))]
    (is (= "Aaa" (first names))
        "Plugin spell 'Aaa' should sort first alphabetically")))

(deftest compute-sorted-spells-disabled-plugin-excluded
  (let [db {:plugins {:test {:disabled? true
                              ::e5/spells {:zap {:name "Zap" :key :zap}}}}}
        result (compute/compute-sorted-spells db)
        names (set (map :name result))]
    (is (not (contains? names "Zap"))
        "Spells from disabled plugins must not appear")))

;; -- compute-sorted-items --

(deftest compute-sorted-items-includes-known-static-items
  ;; "Alchemy Jug" is a wondrous item in static data.
  (let [result (compute/compute-sorted-items {})
        names (set (map mi/name-key result))]
    (is (contains? names "Alchemy Jug"))
    (is (contains? names "Amulet of Health"))))

(deftest compute-sorted-items-count-increases-with-custom
  ;; Adding a custom item should increase the total count
  (let [base-count (count (compute/compute-sorted-items {}))
        with-custom (count (compute/compute-sorted-items
                            {::mi/custom-items [{::mi/name "Test Ring"
                                                  ::mi/type :wondrous-item}]}))]
    (is (> with-custom base-count)
        "Custom items should increase total item count")))

(deftest compute-sorted-items-custom-items-present-in-output
  (let [result (compute/compute-sorted-items
                {::mi/custom-items [{::mi/name "Custom Sword"
                                      ::mi/type :wondrous-item}]})
        names (set (map mi/name-key result))]
    (is (contains? names "Custom Sword")
        "Custom items should appear by name in the output")))

(deftest compute-sorted-items-empty-custom-returns-static-only
  ;; With no custom items, result should equal the static items
  (let [result-nil (compute/compute-sorted-items {})
        result-empty (compute/compute-sorted-items {::mi/custom-items []})]
    (is (= (count result-nil) (count result-empty))
        "Empty custom items should produce same count as nil")))
