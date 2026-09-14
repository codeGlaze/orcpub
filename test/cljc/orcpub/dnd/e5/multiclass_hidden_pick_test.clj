(ns orcpub.dnd.e5.multiclass-hidden-pick-test
  "The real-world case of `hidden-selection-picks.md`: a skill picked from a class's MULTICLASS
   options, after that class becomes the character's first class.

   `class-option` offers two mutually exclusive skill selections per class —
     :skill-proficiency            gated `first-class?`              (choose 4, for a rogue)
     :multiclass-skill-proficiency gated `(complement first-class?)` (choose 1)
   Both are always in the template; the gate only decides which the builder SHOWS.

   Reorder the classes and the gate flips. The question is what happens to the pick already
   stored under the arm that just lost its gate."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def ^:private language-map (common/map-by-key [{:name "Common" :key :common}]))

(def ^:private abilities
  {::char5e/str 15 ::char5e/dex 15 ::char5e/con 14
   ::char5e/int 10 ::char5e/wis 10 ::char5e/cha 10})

(def ^:private the-template
  (delay
   (t5e/template
    (t5e/template-selections
     nil nil nil weapons5e/weapons-map weapons5e/weapons
     sl5e/spell-lists spells5e/spell-map
     []                                                    ; backgrounds
     []                                                    ; races
     [(classes5e/fighter-option sl5e/spell-lists spells5e/spell-map {} language-map
                                weapons5e/weapons-map)
      (classes5e/rogue-option sl5e/spell-lists spells5e/spell-map {} language-map
                              weapons5e/weapons-map)]
     []                                                    ; feats
     language-map))))

(defn- lvl [n] {:orcpub.entity/key (keyword (str "level-" n))
                :orcpub.entity/options {:hit-points {:orcpub.entity/key :average
                                                     :orcpub.entity/value 5}}})

;; The rogue level, carrying a MULTICLASS skill pick — what a player stores when rogue is the
;; second class.
(def ^:private rogue-multiclassed
  {:orcpub.entity/key :rogue
   :orcpub.entity/options {:multiclass-skill-proficiency [{:orcpub.entity/key :athletics}]
                           :levels [(lvl 1)]}})

(def ^:private fighter-first
  {:orcpub.entity/key :fighter
   :orcpub.entity/options {:skill-proficiency [{:orcpub.entity/key :survival}
                                               {:orcpub.entity/key :intimidation}]
                           :levels [(lvl 1)]}})

(defn- build [classes]
  (entity/build
   {:orcpub.entity/options
    {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value abilities}
     :class classes}}
   @the-template))

(defn- offered
  "The skill-proficiency selections the builder would SHOW — after prereq filtering."
  [classes]
  (let [char {:orcpub.entity/options
              {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value abilities}
               :class classes}}]
    (->> (entity/get-all-selections-2 @the-template (entity/make-path-map char) (build classes))
         (filter #(contains? (::t/tags %) :skill-profs))
         (map (fn [s] [(::t/name s) (::entity/path s)])))))

(deftest ^:diagnostic report-multiclass-reorder
  (println "\n=== a rogue's MULTICLASS skill pick, before and after rogue becomes first class ===")
  (doseq [[label classes] [["fighter first, rogue multiclassed" [fighter-first rogue-multiclassed]]
                           ["fighter dropped, rogue is first"   [rogue-multiclassed]]]]
    (println "\n" label)
    (println "   skills: " (pr-str (sort (keys (char5e/skill-proficiencies (build classes))))))
    (doseq [[nm path] (offered classes)]
      (println (format "   offered: %-34s at %s" nm (pr-str path))))))

(defn- offered-names [classes] (set (map first (offered classes))))
(defn- offered-paths [classes] (set (map second (offered classes))))

(deftest a-multiclass-skill-pick-survives-becoming-the-first-class
  (let [multiclassed [fighter-first rogue-multiclassed]
        rogue-only   [rogue-multiclassed]]

    (testing "multiclassed: the rogue offers its MULTICLASS skill pick, and it lands"
      (is (contains? (offered-paths multiclassed) [:class :rogue :multiclass-skill-proficiency]))
      (is (= [:athletics :intimidation :survival]
             (sort (keys (char5e/skill-proficiencies (build multiclassed)))))))

    (testing "drop the fighter and the FIGHTER's skills go, correctly — the class left the template"
      (is (not (contains? (set (keys (char5e/skill-proficiencies (build rogue-only))))
                          :intimidation))))

    (testing "but the rogue's multiclass pick stays, because the rogue did not leave"
      (is (contains? (set (keys (char5e/skill-proficiencies (build rogue-only)))) :athletics)))

    (testing "and the control that holds it is GONE — nothing on screen can remove Athletics"
      (is (not (contains? (offered-paths rogue-only)
                          [:class :rogue :multiclass-skill-proficiency]))
          "the multiclass selection is filtered out by its (complement first-class?) prereq"))

    (testing "meanwhile the first-class selection opens, on top of the skill already held"
      (is (contains? (offered-paths rogue-only) [:class :rogue :skill-proficiency])
          "a rogue chooses 4 skills as a first class — so this character can reach 5"))))
