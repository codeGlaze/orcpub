(ns orcpub.dnd.e5.library-links-test
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.library-links :as links]
            [orcpub.dnd.e5.share-bundle :as sb]))

;; One holder per kind of link: the content type holding it, an item holding it, and the target
;; the item names. Shapes are the ones the builders write (reference_web_test uses the same).
(def ^:private holders
  {:subclass->class      [:orcpub.dnd.e5/subclasses {:class :caster} :caster]
   :subrace->race        [:orcpub.dnd.e5/subraces {:race :folk} :folk]
   :spell->class-list    [:orcpub.dnd.e5/spells {:spell-lists {:caster true :other false}} :caster]
   :class->borrowed-list [:orcpub.dnd.e5/classes {:spellcasting {:spell-list-kw :caster}} :caster]
   :class->own-list      [:orcpub.dnd.e5/classes {:spellcasting {:spell-list {1 #{:bolt}}}} :bolt]
   :paladin-spells       [:orcpub.dnd.e5/subclasses {:paladin-spells {1 {0 :bolt}}} :bolt]
   :cleric-spells        [:orcpub.dnd.e5/subclasses {:cleric-spells {1 {0 :bolt}}} :bolt]
   :warlock-spells       [:orcpub.dnd.e5/subclasses {:warlock-spells {1 {0 :bolt}}} :bolt]
   :level-modifier-spell [:orcpub.dnd.e5/classes
                          {:level-modifiers [{:type :spell :level 1 :value {:key :bolt}}
                                             {:type :skill :value {:key :bolt}}]} :bolt]
   :granted-spell        [:orcpub.dnd.e5/races {:spells [{:level 1 :value {:key :bolt}}]} :bolt]
   :level-selection      [:orcpub.dnd.e5/classes {:level-selections [{:type :tricks :level 1}]} :tricks]
   :race-prerequisite    [:orcpub.dnd.e5/feats {:path-prereqs {:race {:folk true}}} :folk]
   :granted-language     [:orcpub.dnd.e5/races {:props {:language {:cant true}}} :cant]
   :language-by-name     [:orcpub.dnd.e5/races {:languages #{"Cant"}} "Cant"]
   :encounter->monster   [:orcpub.dnd.e5/encounters
                          {:creatures [{:type :monster :creature {:monster :wolf :num 2}}
                                       {:type :character :creature {:monster :wolf}}]} :wolf]
   :language-choice      [:orcpub.dnd.e5/races
                          {:profs {:language-options {:choose 1 :options {:common true :orcish false}}}}
                          :common]})

(deftest every-link-has-a-holder-here
  ;; A new entry in `links` without a row above fails here, so it cannot go untested.
  (is (= (set (map :id links/links)) (set (keys holders)))))

(deftest each-link-is-read-and-retargeted
  (doseq [link links/links
          :let [[ctype item target] (get holders (:id link))]]
    (testing (name (:id link))
      (is (links/holds? link ctype) "held by the content type the builders put it on")
      (is (= [target] (links/targets link item)) "reads exactly the one target")
      (is (= item (links/retarget link item :not-a-target :x)) "a key it does not name: untouched")
      (if (= :name (:by link))
        (is (= item (links/retarget link item target :x)) "a name link: a key change leaves it")
        (is (= [:x] (links/targets link (links/retarget link item target :x)))
            "retargeted to the new key")))))

(deftest a-link-is-not-read-from-an-item-that-does-not-hold-it
  (doseq [link links/links]
    (is (= [] (links/targets link {:name "Plain"})) (name (:id link)))
    (is (= {:name "Plain"} (links/retarget link {:name "Plain"} :a :b)) (name (:id link)))))

(deftest a-toggled-off-choice-is-not-a-link
  ;; A `:*true-keys` path must not report a key whose value has gone `false` (checkbox toggled off).
  (let [race-prereq (first (filter #(= :race-prerequisite (:id %)) links/links))
        granted-lang (first (filter #(= :granted-language (:id %)) links/links))
        lang-choice (first (filter #(= :language-choice (:id %)) links/links))]
    (is (= [] (links/targets race-prereq {:path-prereqs {:race {:folk false}}})))
    (is (= [] (links/targets granted-lang {:props {:language {:cant false}}})))
    (is (= [] (links/targets lang-choice {:profs {:language-options {:options {:orcish false}}}})))))

(deftest retargeting-keeps-collection-types
  (let [link (first (filter #(= :class->own-list (:id %)) links/links))]
    (is (set? (get-in (links/retarget link {:spellcasting {:spell-list {1 #{:bolt :zap}}}} :bolt :x)
                      [:spellcasting :spell-list 1])))))

;; ── The share link follows every link in the list ──────────────────────────

(def ^:private borrowing-plugins
  {"Pak"
   {:orcpub.dnd.e5/classes {:borrower {:name "Borrower"
                                       :spellcasting {:spell-list-kw :lender
                                                      :spell-list {1 #{:own-spell}}}}
                            :lender   {:name "Lender"}}
    :orcpub.dnd.e5/spells  {:own-spell    {:name "Own Spell"}
                            :lender-spell {:name "Lender Spell" :spell-lists {:lender true}}
                            :unlisted     {:name "Unlisted" :spell-lists {:lender false}}}}})

(deftest a-share-link-carries-a-borrowed-list-and-a-class-own-list
  (let [bundle (sb/extract-bundle {::entity/options {:class [{::entity/key :borrower}]}}
                                  borrowing-plugins)
        classes (set (keys (get-in bundle ["Pak" :orcpub.dnd.e5/classes])))
        spells (set (keys (get-in bundle ["Pak" :orcpub.dnd.e5/spells])))]
    (is (= #{:borrower :lender} classes) "the class it borrows its list from comes along")
    (is (contains? spells :own-spell) "so do the spells on its own list")
    (is (contains? spells :lender-spell) "and the spells on the borrowed list")
    (is (not (contains? spells :unlisted)) "but not a spell whose entry for that list is off")))

(deftest a-share-link-carries-a-language-a-race-names-even-when-its-key-is-tagged
  (let [plugins {"Tide Pak" {:orcpub.dnd.e5/races {:tidefolk {:name "Tidefolk" :languages #{"Tidetongue"}}}
                             :orcpub.dnd.e5/languages {:tidetongue-tp {:name "Tidetongue"}}}}
        bundle (sb/extract-bundle {::entity/options {:race {::entity/key :tidefolk}}} plugins)]
    (is (contains? (get-in bundle ["Tide Pak" :orcpub.dnd.e5/languages]) :tidetongue-tp))))
