(ns orcpub.dnd.e5.bard-test
  "Bard features built on a real template: Font of Inspiration (level 5) lets Bardic Inspiration
   come back on a short rest as well as a long one (Orcpub/orcpub#310)."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.units :as units5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.dnd.e5.warlock-test :as wt]))

(def ^:private bard-template
  "The warlock test's template with the Bard as its only class."
  (t5e/template
   (t5e/template-selections
    nil nil nil weapons5e/weapons-map weapons5e/weapons sl5e/spell-lists spells5e/spell-map
    [wt/spy-bg-cfg] [wt/elf-race-cfg]
    [(classes5e/bard-option sl5e/spell-lists spells5e/spell-map {} wt/language-map weapons5e/weapons-map)]
    [] wt/language-map)))

(defn- bard-at
  "A built Bard of `level`, with only the choices a build needs (race, scores, background)."
  [level]
  (entity/build
   {::entity/options
    {:race {::entity/key :elf}
     :ability-scores {::entity/key :standard-roll ::entity/value wt/base-abilities}
     :background {::entity/key :spy}
     :class [{::entity/key :bard
              ::entity/options {:levels (mapv #(hash-map ::entity/key (keyword (str "level-" %)))
                                              (range 1 (inc level)))}}]}}
   bard-template))

(defn- inspiration-frequency
  "The :frequency of the built character's Bardic Inspiration bonus action, or nil."
  [built]
  (some #(when (= "Bardic Inspiration" (:name %)) (:frequency %)) (char5e/bonus-actions built)))

(deftest bardic-inspiration-returns-on-a-short-rest-from-level-5
  (testing "level 4: long rest only"
    (is (= ::units5e/long-rest (get-in (inspiration-frequency (bard-at 4)) [:units]))))
  (testing "level 5 (Font of Inspiration): any rest"
    (is (= ::units5e/rest (get-in (inspiration-frequency (bard-at 5)) [:units])))))
