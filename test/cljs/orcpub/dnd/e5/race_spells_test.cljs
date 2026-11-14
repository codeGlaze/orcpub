(ns orcpub.dnd.e5.race-spells-test
  "A race's spells reach a built character with how they are cast (plan-spell-uses-by-source.md)."
  (:require [cljs.test :refer-macros [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.common :as common]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.spell-subs :as spell-subs]
            [orcpub.dnd.e5.weapons :as weapons5e]))

(def ^:private languages (common/map-by-key [{:name "Common" :key :common}]))

(def ^:private template
  (delay
   (t5e/template
    (t5e/template-selections
     nil nil nil weapons5e/weapons-map weapons5e/weapons
     sl5e/spell-lists spells5e/spell-map
     [] [spell-subs/tiefling-option-cfg]
     [(classes5e/ranger-option sl5e/spell-lists spells5e/spell-map {} languages weapons5e/weapons-map)]
     []
     languages))))

(defn- tiefling-ranger
  "Stored Tiefling ranger of level `n`. Returns the character map."
  [n]
  {::entity/options
   {:ability-scores {::entity/key :standard-roll
                     ::entity/value {::char5e/str 12 ::char5e/dex 15 ::char5e/con 14
                                     ::char5e/int 10 ::char5e/wis 13 ::char5e/cha 14}}
    :race {::entity/key :tiefling}
    :class [{::entity/key :ranger
             ::entity/options
             {:levels (vec (for [i (range 1 (inc n))]
                             {::entity/key (keyword (str "level-" i))}))}}]}})

(deftest tiefling-spells-are-free-once-per-long-rest
  (let [known (char5e/spells-known (entity/build (tiefling-ranger 5) @template))
        lr {:units :orcpub.dnd.e5.units/long-rest :amount 1}]
    (testing "Hellish Rebuke: once per long rest, as a 2nd-level spell, no slots (SRD 5.1)"
      (is (= {:free (assoc lr :level 2) :slots :none}
             (get-in known [1 ["Tiefling" :hellish-rebuke] :casting]))
          (str (get known 1))))
    (testing "Darkness: once per long rest, no slots"
      (is (= {:free lr :slots :none} (get-in known [2 ["Tiefling" :darkness] :casting]))
          (str (get known 2))))))
