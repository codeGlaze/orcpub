(ns orcpub.dnd.e5.plan-picks-test
  "events/plan-picks-db: the builder's character settled against the planned hold."
  (:require [cljs.test :refer-macros [deftest is]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.events :as events]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def ^:private template
  (delay
   (t5e/template
    (t5e/template-selections
     nil nil nil weapons5e/weapons-map weapons5e/weapons
     sl5e/spell-lists spells5e/spell-map
     [] []
     [(classes5e/ranger-option sl5e/spell-lists spells5e/spell-map {}
                               (common/map-by-key [{:name "Common" :key :common}])
                               weapons5e/weapons-map)]
     []
     (common/map-by-key [{:name "Common" :key :common}])))))

(defn- ranger [n]
  {::entity/options
   {:ability-scores {::entity/key :standard-roll
                     ::entity/value {::char5e/str 15 ::char5e/dex 15 ::char5e/con 14
                                     ::char5e/int 13 ::char5e/wis 13 ::char5e/cha 10}}
    :class [{::entity/key :ranger
             ::entity/options
             {:levels (vec (for [i (range 1 (inc n))]
                             {::entity/key (keyword (str "level-" i))
                              ::entity/options
                              (when (= i 3)
                                {:ranger-archetype
                                 {::entity/key :hunter
                                  ::entity/options {:defensive-tactics
                                                    {::entity/key :steel-will}}}})}))}}]}})

(defn- db [character & [hold]]
  {:character character
   :orcpub.dnd.e5.autosave-fx/cached-template @template
   :orcpub.dnd.e5/planned-picks (or hold [])})

(defn- steel-will? [character]
  (some? (get-in character [::entity/options :class 0 ::entity/options :levels 2
                            ::entity/options :ranger-archetype ::entity/options
                            :defensive-tactics])))

(deftest a-level-drop-holds-the-pick-and-raising-it-returns-it
  (let [at-6 (events/plan-picks-db (db (ranger 7)) (db (ranger 6)) :some-edit)
        at-7 (events/plan-picks-db at-6 (assoc at-6 :character
                                               (update-in (:character at-6)
                                                          [::entity/options :class 0
                                                           ::entity/options :levels]
                                                          conj (last (get-in (ranger 7)
                                                                             [::entity/options
                                                                              :class 0
                                                                              ::entity/options
                                                                              :levels]))))
                                   :some-edit)]
    (is (not (steel-will? (:character at-6))) "at 6 Steel Will is out of the character")
    (is (= 1 (count (:orcpub.dnd.e5/planned-picks at-6))) "and held")
    (is (steel-will? (:character at-7)) "back at 7")
    (is (= [] (:orcpub.dnd.e5/planned-picks at-7)))
    (is (= 1 (count (get-in at-7 [:orcpub.dnd.e5/picks-change :restored]))))))

(defn- without-tactic [character]
  (update-in character [::entity/options :class 0 ::entity/options :levels 2 ::entity/options
                        :ranger-archetype ::entity/options]
             dissoc :defensive-tactics))

(deftest the-hold-does-not-follow-into-another-character
  (let [at-6 (events/plan-picks-db (db (ranger 7)) (db (ranger 6)) :some-edit)
        open-7 (without-tactic (ranger 7))]
    (doseq [[label character event] [["another character" (assoc open-7 :db/id 99) :set-character]
                                     ["a new character" open-7 :reset-character]]]
      (let [after (events/plan-picks-db at-6 (assoc at-6 :character character) event)]
        (is (not (steel-will? (:character after))) label)
        (is (= [] (:orcpub.dnd.e5/planned-picks after)) label)))))

(deftest a-draft-is-settled-when-the-template-arrives
  (let [ghost (assoc-in (ranger 7) [::entity/options :class 0 ::entity/options :levels]
                        (vec (butlast (get-in (ranger 7) [::entity/options :class 0
                                                          ::entity/options :levels]))))
        opened {:character ghost}
        cached (events/plan-picks-db opened (merge (db ghost) opened) :cache-template)]
    (is (steel-will? ghost) "a level-6 draft that still stores Steel Will")
    (is (not (steel-will? (:character cached))) "settled once the template is cached")
    (is (= 1 (count (:orcpub.dnd.e5/planned-picks cached))))))

(deftest nothing-changes-without-a-template-or-a-changed-character
  (let [d (db (ranger 6))]
    (is (= (dissoc d :orcpub.dnd.e5.autosave-fx/cached-template)
           (events/plan-picks-db d (dissoc d :orcpub.dnd.e5.autosave-fx/cached-template) :e)))
    (is (identical? d (events/plan-picks-db d d :e)))))
