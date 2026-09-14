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
(declare compiled-race)
(def ^:private skilled-race   (delay (compiled-race {:name "Testfolk" :key :testfolk :props {:skill-prof {:athletics true}}})))
(def ^:private lingual-race   {:name "Testfolk" :key :testfolk :languages ["Elvish"]})
(def ^:private tooled-race    {:name "Testfolk" :key :testfolk :profs {:tool {:smiths-tools true}}})

(deftest ^:diagnostic report-duplicate-grants
  (println "\n=== granted something the character ALREADY has ===")
  (doseq [[label race feat read]
          [["skill  — legacy :props"  @skilled-race {:props {:skill-prof {:athletics true}}}   char5e/skill-proficiencies]
           ["skill  — pool :key"      @skilled-race {:grants [{:pool :skills :key :athletics}]} char5e/skill-proficiencies]
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
;; RETRACTED 2026-09-14, same day it was written.
;;
;; This block asserted that a race's `:props` were read by nothing, because `race-option`
;; destructures no `:props` and a grep of `options.cljc` found only two `plugin-modifiers`
;; callers. The grep was scoped to ONE FILE and reported as a fact about the codebase. There are
;; seven callers; five are in `spell_subs.cljs` — race (`:362`), subrace (`:379`), subclass
;; (`:685`), class (`:721`).
;;
;; A homebrew race's props are compiled in the cljs SUBSCRIPTION layer, before the race ever
;; reaches `race-option`:
;;
;;   (assoc race :modifiers (concat (opt5e/plugin-modifiers (:props race) (:key race)) …))
;;
;; So the race builder's checkboxes work. The fixture was broken, not the app — `build` below
;; hands `template-selections` a raw race map and skips that step.
;;
;; The trap is already documented: `grant_vocabulary_characterization_test` notes that vocabulary
;; B lives in cljs and "is NOT reachable from this JVM gate". Any JVM fixture standing in for
;; plugin content must apply the cljs compile step itself, as `compiled-race` does.
;; ---------------------------------------------------------------------------

(defn- compiled-race
  "A plugin race as `race-option` actually receives it — props already compiled to modifiers by
   `::races5e/plugin-races` (spell_subs.cljs:362). A JVM fixture must do this itself."
  [race]
  (assoc race :modifiers (concat (:modifiers race)
                                 (opt5e/plugin-modifiers (:props race) (:key race)))))

(deftest race-props-compile-through-the-cljs-subscription-layer
  (testing "a raw race map grants nothing — the props are still uncompiled"
    (is (nil? (char5e/skill-proficiencies
               (build {:name "Testfolk" :key :testfolk
                       :props {:skill-prof {:athletics true}}} nil)))))

  (testing "the same race, compiled the way the sub compiles it, grants the skill"
    (is (= {:athletics {nil true}}
           (char5e/skill-proficiencies
            (build (compiled-race {:name "Testfolk" :key :testfolk
                                   :props {:skill-prof {:athletics true}}}) nil)))))

  (testing "and damage resistance the same way"
    (is (= #{{:value :fire :qualifier nil}}
           (char5e/damage-resistances
            (build (compiled-race {:name "Testfolk" :key :testfolk
                                   :props {:damage-resistance {:fire true}}}) nil))))))

(deftest a-duplicate-grant-is-silently-wasted
  (testing "a language the character already has: the second grant changes nothing"
    (let [race {:name "Testfolk" :key :testfolk :languages ["Elvish"]}]
      (is (= (char5e/languages (build race nil))
             (char5e/languages (build race {:grants [{:pool :languages :key :elvish}]}))))))

  (testing "a skill the character already has, through the compiled race path"
    (let [race (compiled-race {:name "Testfolk" :key :testfolk
                               :props {:skill-prof {:athletics true}}})]
      (is (= (char5e/skill-proficiencies (build race nil))
             (char5e/skill-proficiencies (build race {:grants [{:pool :skills :key :athletics}]}))))))

  (testing "and a tool proficiency behaves the same"
    (let [race {:name "Testfolk" :key :testfolk :profs {:tool {:smiths-tools true}}}]
      (is (= (char5e/tool-proficiencies (build race nil))
             (char5e/tool-proficiencies (build race {:grants [{:pool :tools :key :smiths-tools}]})))))))
