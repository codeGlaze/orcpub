(ns orcpub.dnd.e5.conditional-selection-ref-test
  "GATE for decision-already-held-resolution.md. A resolution's replacement pick is a selection
   that exists only while the duplicate does. Before building any of that, pin whether such a
   selection is addressable and whether a pick stored in it survives.

   Exercised through `background-skills-cfg`, the one place this shape already ships, so the
   answers describe real behaviour rather than a prototype."
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

(def ^:private language-map (common/map-by-key [{:name "Common" :key :common}]))
(def ^:private pools (gp/assemble []))

(def ^:private abilities
  {:orcpub.dnd.e5.character/str 10 :orcpub.dnd.e5.character/dex 10 :orcpub.dnd.e5.character/con 10
   :orcpub.dnd.e5.character/int 10 :orcpub.dnd.e5.character/wis 10 :orcpub.dnd.e5.character/cha 10})

;; A background that grants Athletics — background-skills-cfg gives it the conditional modifier
;; and the replacement selection.
(def ^:private bg
  {:name "Testbound" :key :testbound :profs {:skill {:athletics true}}})

;; A race that ALSO grants Athletics, compiled the way ::races5e/plugin-races compiles it.
(defn- rival-race [grant?]
  (let [r {:name "Testfolk" :key :testfolk}]
    (if grant?
      (assoc r :modifiers (opt5e/plugin-modifiers {:skill-prof {:athletics true}} :testfolk))
      r)))

(defn- template-for [grant?]
  (t5e/template
   (t5e/template-selections nil nil nil weapons5e/weapons-map weapons5e/weapons
                            sl5e/spell-lists spells5e/spell-map
                            [bg] [(rival-race grant?)] [] [] language-map pools)))

(defn- build [grant? opts]
  (entity/build
   {:orcpub.entity/options
    (merge {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value abilities}
            :race {:orcpub.entity/key :testfolk}
            :background {:orcpub.entity/key :testbound}}
           opts)}
   (template-for grant?)))

(defn- offered-skill-selections
  "The skill-proficiency selections the builder would SHOW for this character — i.e. after
   `remove-disqualified-selections` has dropped the ones whose prereq-fn fails."
  [grant? opts]
  (let [char  {:orcpub.entity/options
               (merge {:ability-scores {:orcpub.entity/key :standard-roll
                                        :orcpub.entity/value abilities}
                       :race {:orcpub.entity/key :testfolk}
                       :background {:orcpub.entity/key :testbound}}
                      opts)}
        built (build grant? opts)]
    (->> (entity/get-all-selections-2 (template-for grant?)
                                      (entity/make-path-map char)
                                      built)
         (filter #(contains? (::t/tags %) :skill-profs))
         (map (fn [sel] [(::t/name sel) (::entity/path sel)])))))

(deftest ^:diagnostic report-conditional-selection-shape
  (println "\n=== the replacement selection: does it appear only on a duplicate? ===")
  (doseq [g [false true]]
    (println (format "rival race grants athletics: %-6s" g))
    (println "   profs:      " (pr-str (char5e/skill-proficiencies (build g {}))))
    (doseq [[nm path] (offered-skill-selections g {})]
      (println (format "   offered:     %-28s at %s" nm (pr-str path))))))

;; The pick the replacement selection stores, at the path the diagnostic above reports.
(def ^:private replacement-pick
  {:background {:orcpub.entity/key :testbound
                :orcpub.entity/options {:skill-proficiency [{:orcpub.entity/key :insight}]}}})

(deftest the-replacement-selection-is-nested-not-a-ref
  (testing "no duplicate: the background grants its skill, sourced to itself, and offers nothing"
    (is (= {:athletics {"Testbound" true}} (char5e/skill-proficiencies (build false {}))))
    (is (empty? (offered-skill-selections false {}))))

  (testing "duplicate: the background's own grant is suppressed and a replacement pick appears"
    (is (= {:athletics {nil true}} (char5e/skill-proficiencies (build true {}))))
    (is (= [["Skill Proficiency" [:background :testbound :skill-proficiency]]]
           (offered-skill-selections true {}))))

  (testing "the path is NESTED under the owning background — not a top-level :ref"
    (is (= [:background :testbound :skill-proficiency]
           (second (first (offered-skill-selections true {})))))))

(deftest a-pick-in-the-replacement-survives-the-duplicate-going-away
  (testing "with the duplicate present, the pick lands on the sheet"
    (is (= #{:athletics :insight}
           (set (keys (char5e/skill-proficiencies (build true replacement-pick)))))))

  (testing "remove the duplicate and the SAME saved character still builds"
    ;; The selection is gone from the builder (its prereq now fails) but the stored pick is
    ;; untouched — remove-disqualified-selections filters what is OFFERED, not what is SAVED.
    (let [built (build false replacement-pick)]
      (is (some? built) "the character must still build")
      (is (contains? (char5e/skill-proficiencies built) :athletics)
          "the background's own grant comes back")))

  (testing "and what the now-hidden pick does is the question a designer must answer"
    (println "\n=== a pick saved in a replacement selection, after the duplicate is removed ===")
    (println "  with duplicate:   " (pr-str (char5e/skill-proficiencies (build true replacement-pick))))
    (println "  duplicate removed:" (pr-str (char5e/skill-proficiencies (build false replacement-pick))))))
