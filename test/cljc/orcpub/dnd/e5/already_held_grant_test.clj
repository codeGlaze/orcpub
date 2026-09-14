(ns orcpub.dnd.e5.already-held-grant-test
  "What happens when a character is granted something they ALREADY have.

   D&D answers this one way — backgrounds say 'if you already have this proficiency, choose a
   different one' — and this app answers it four ways depending on which path the grant took.
   Pinned here per pool, because the divergence is the argument for lifting the rule into the
   grant compiler rather than porting `background-skills-cfg` per silo.

   Characterization only: asserts what happens today, including where that is wrong."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.grant-pools :as gp]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def ^:private languages
  [{:name "Common" :key :common} {:name "Elvish" :key :elvish} {:name "Dwarvish" :key :dwarvish}])
(def ^:private language-map (common/map-by-key languages))
(def ^:private pools (gp/assemble []))

(def ^:private abilities
  {:orcpub.dnd.e5.character/str 10 :orcpub.dnd.e5.character/dex 10 :orcpub.dnd.e5.character/con 10
   :orcpub.dnd.e5.character/int 10 :orcpub.dnd.e5.character/wis 10 :orcpub.dnd.e5.character/cha 10})

(defn- build
  "A character with `race-cfg` as its race and, when given, `feat-cfg` as a bonus feat."
  [race-cfg feat-cfg]
  (let [feat (when feat-cfg
               (opt5e/feat-option-from-cfg language-map spells5e/spell-map sl5e/spell-lists
                                           weapons5e/weapons-map {} pools
                                           (merge {:name "Second Source" :key :second-source} feat-cfg)))]
    (entity/build
     {:orcpub.entity/options
      (cond-> {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value abilities}
               :race {:orcpub.entity/key (:key race-cfg)}}
        feat (assoc :bonus-feat {:orcpub.entity/key :second-source}))}
     (t5e/template
      (concat
       (t5e/template-selections nil nil nil weapons5e/weapons-map weapons5e/weapons
                                sl5e/spell-lists spells5e/spell-map [] [race-cfg] [] []
                                language-map pools)
       (when feat
         [(t/selection-cfg {:name "Bonus Feat" :key :bonus-feat :tags #{:feats}
                            :options [feat] :min 1 :max 1})]))))))

;; A race that already grants the thing, by the legacy :props path every silo shares.
(def ^:private skilled-race   {:name "Testfolk" :key :testfolk :props {:skill-prof {:athletics true}}})
(def ^:private lingual-race   {:name "Testfolk" :key :testfolk :languages ["Elvish"]})
(def ^:private tooled-race    {:name "Testfolk" :key :testfolk :profs {:tool {:smiths-tools true}}})

(deftest ^:diagnostic report-duplicate-grants
  (println "\n=== granted something the character ALREADY has ===")
  (doseq [[label race feat read]
          [["skill  — legacy :props"  skilled-race {:props {:skill-prof {:athletics true}}}   char5e/skill-proficiencies]
           ["skill  — pool :key"      skilled-race {:grants [{:pool :skills :key :athletics}]} char5e/skill-proficiencies]
           ["lang   — legacy :props"  lingual-race {:props {:language {:elvish true}}}         char5e/languages]
           ["lang   — pool :key"      lingual-race {:grants [{:pool :languages :key :elvish}]} char5e/languages]
           ["tool   — legacy :profs"  tooled-race  {:profs {:tool {:smiths-tools true}}}       char5e/tool-proficiencies]
           ["tool   — pool :key"      tooled-race  {:grants [{:pool :tools :key :smiths-tools}]} char5e/tool-proficiencies]]]
    (let [without (read (build race nil))
          with    (read (build race feat))]
      (println (format "%-26s %s" label
                       (if (= without with)
                         "NO CHANGE — the second grant is wasted"
                         (str "changed: " (pr-str without) " -> " (pr-str with))))))))

;; ---------------------------------------------------------------------------
;; Found while building the fixture above: the race fixture granted nothing.
;;
;; `toggle-race-map-prop` writes `[:props <k> <v>]` on the race (events.cljs:3887), and the race
;; builder routes six widgets through it — skill, weapon and armor proficiency, damage resistance
;; and immunity, languages. `race-option` destructures
;;   [name icon key help abilities size speed darkvision subraces modifiers selections traits
;;    source languages language-options armor-proficiencies weapon-proficiencies profs plugin?
;;    grants edit-event]
;; with no `:props`, and nothing else compiles a race's props — `plugin-modifiers` has exactly two
;; callers, `feat-modifiers` and the fighting-style option. `subrace-option` is the same.
;;
;; So those checkboxes save, reload into the form, export to `.orcbrew` — and do nothing to a
;; character.
;; ---------------------------------------------------------------------------

(deftest race-props-are-written-by-the-builder-and-read-by-nothing
  (testing "a race granting a skill through :props grants no skill proficiency"
    (is (nil? (char5e/skill-proficiencies
               (build {:name "Testfolk" :key :testfolk
                       :props {:skill-prof {:athletics true}}} nil)))))

  (testing "the same key on a FEAT does grant it — the vocabulary works, the race never reads it"
    ;; the source is nil: make-feat-modifiers passes option-key only on the prof-or-expertise arm,
    ;; and plain :skill-prof calls (modifiers/skill-proficiency %) with one argument.
    (is (= {:athletics {nil true}}
           (char5e/skill-proficiencies
            (build {:name "Testfolk" :key :testfolk}
                   {:props {:skill-prof {:athletics true}}})))))

  (testing "damage resistance through a race's :props is dropped the same way"
    (is (empty? (char5e/damage-resistances
                 (build {:name "Testfolk" :key :testfolk
                         :props {:damage-resistance {:fire true}}} nil))))))

(deftest a-duplicate-grant-is-silently-wasted
  (testing "a language the character already has: the second grant changes nothing"
    (let [race {:name "Testfolk" :key :testfolk :languages ["Elvish"]}]
      (is (= (char5e/languages (build race nil))
             (char5e/languages (build race {:grants [{:pool :languages :key :elvish}]}))))))

  (testing "and a tool proficiency behaves the same"
    (let [race {:name "Testfolk" :key :testfolk :profs {:tool {:smiths-tools true}}}]
      (is (= (char5e/tool-proficiencies (build race nil))
             (char5e/tool-proficiencies (build race {:grants [{:pool :tools :key :smiths-tools}]})))))))
