(ns orcpub.dnd.e5.picks-closed-test
  "`picks/disqualified` and `picks/overflow` on the real fighter, ranger and rogue templates."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.picks :as picks]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def ^:private language-map (common/map-by-key [{:name "Common" :key :common}]))

(def ^:private template
  (delay
   (t5e/template
    (t5e/template-selections
     nil nil nil weapons5e/weapons-map weapons5e/weapons
     sl5e/spell-lists spells5e/spell-map
     [] []
     (mapv #(% sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)
           [classes5e/fighter-option classes5e/ranger-option classes5e/rogue-option])
     []
     language-map))))

(defn- lvl
  "Level `n`; a hit-point pick from level 2 on (level 1's is offered only to a later class)."
  [n & [extra]]
  {::entity/key (keyword (str "level-" n))
   ::entity/options (merge (when (> n 1) {:hit-points {::entity/key :average ::entity/value 5}})
                           extra)})

(defn- class-entry
  "Class `cls` at level `n`; `at-3` is stored in level 3's options; `opts` on the class itself."
  [cls n & [at-3 opts]]
  {::entity/key cls
   ::entity/options (merge opts {:levels (vec (for [i (range 1 (inc n))]
                                                (lvl i (when (= i 3) at-3))))})})

(defn- character [classes & [extra]]
  (merge {::entity/options
          {:ability-scores {::entity/key :standard-roll
                            ::entity/value {::char5e/str 15 ::char5e/dex 15 ::char5e/con 14
                                            ::char5e/int 13 ::char5e/wis 13 ::char5e/cha 10}}
           :class classes}}
         extra))

(defn- closed [ch]
  (let [built (entity/build ch @template)]
    {:disqualified (set (picks/disqualified @template ch built))
     :overflow (set (picks/overflow @template ch built))}))

(defn- hunter [tactic] {:ranger-archetype {::entity/key :hunter
                                           ::entity/options {:defensive-tactics
                                                             {::entity/key tactic}}}})

(def ^:private steel-will
  [:class :ranger :levels :level-3 :ranger-archetype :hunter :defensive-tactics :steel-will])

(deftest a-subclass-level-pick-is-disqualified-below-its-level
  (is (= #{steel-will}
         (:disqualified (closed (character [(class-entry :ranger 6 (hunter :steel-will))]))))
      "Defensive Tactics is a level-7 feature")
  (is (= #{} (:disqualified (closed (character [(class-entry :ranger 7 (hunter :steel-will))])))))
  (testing "an option the template does not offer is never reported"
    (is (= #{}
           (:disqualified (closed (character [(class-entry :ranger 6 (hunter :tidebreaker))])))))))

(def ^:private champion {:martial-archetype {::entity/key :champion}})
(def ^:private two-styles {:fighting-style [{::entity/key :defense} {::entity/key :archery}]})

(deftest the-newest-pick-over-a-shared-slot-is-overflow
  (let [at-9 (character [(class-entry :fighter 9 champion two-styles)])]
    (is (= #{[:class :fighter :fighting-style :archery]} (:overflow (closed at-9)))
        "two styles, room for one below Champion 10: the newest goes")
    (is (= #{} (:disqualified (closed at-9)))
        "the shared slot is still open, so nothing in it is disqualified")
    (testing "a waived limit (the homebrew override) reports none"
      (is (= #{} (:overflow (closed (assoc at-9 ::entity/homebrew-paths
                                            {[:class :fighter :fighting-style] true})))))))
  (is (= #{} (:overflow (closed (character [(class-entry :fighter 10 champion two-styles)]))))))

(def ^:private rogue-first-class-skills
  {:skill-proficiency
   (mapv #(hash-map ::entity/key %) [:stealth :acrobatics :insight :perception])})

(deftest first-class-picks-of-a-second-class-are-disqualified
  (let [ch (character [(class-entry :fighter 1)
                       (class-entry :rogue 1 nil rogue-first-class-skills)])]
    (is (= (set (for [k [:stealth :acrobatics :insight :perception]]
                  [:class :rogue :skill-proficiency k]))
           (:disqualified (closed ch))))))

(deftest a-character-whose-picks-all-apply-reports-nothing
  (doseq [ch [(character [(class-entry :ranger 7 (hunter :steel-will))])
              (character [(class-entry :fighter 10 champion two-styles)])
              (character [(class-entry :rogue 1 nil rogue-first-class-skills)
                          (class-entry :fighter 1)])]]
    (is (= {:disqualified #{} :overflow #{}} (closed ch)))))

(deftest a-level-1-hit-point-roll-on-the-first-class-is-disqualified
  (let [rolled-1 (assoc-in (class-entry :rogue 1)
                           [::entity/options :levels 0 ::entity/options :hit-points]
                           {::entity/key :average ::entity/value 5})]
    (is (= #{[:class :rogue :levels :level-1 :hit-points :average]}
           (:disqualified (closed (character [rolled-1 (class-entry :fighter 1)]))))
        "the first class takes maximum hit points at level 1")
    (is (= #{} (:disqualified (closed (character [(class-entry :fighter 1) rolled-1]))))
        "a later class rolls its level 1")))

