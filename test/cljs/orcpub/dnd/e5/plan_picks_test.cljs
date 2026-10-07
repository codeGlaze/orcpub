(ns orcpub.dnd.e5.plan-picks-test
  "The builder's settle (events `queue-settle`, `hold-for`, `settle-picks-db`): picks that stop
   applying go to the planned hold and come back; an edit never fails because of it. And the class
   deletion that relies on it."
  (:require [cljs.test :refer-macros [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.events :as events]
            [orcpub.dnd.e5.picks :as picks]
            [orcpub.dnd.e5.subs :as subs]
            [orcpub.dnd.e5.coverage-pak :as coverage-pak]
            [orcpub.dnd.e5.event-handlers :as event-handlers]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def ^:private classes
  (delay (mapv #(% sl5e/spell-lists spells5e/spell-map (coverage-pak/plugin-subclasses-map)
                   (common/map-by-key [{:name "Common" :key :common}])
                   weapons5e/weapons-map)
               [classes5e/ranger-option classes5e/rogue-option])))

(def ^:private template
  (delay
   (t5e/template
    (t5e/template-selections
     nil nil nil weapons5e/weapons-map weapons5e/weapons
     sl5e/spell-lists spells5e/spell-map
     [] []
     @classes
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

(defn- queued
  "The db an edit leaves, through the real `queue-settle`, and whether it queued a settle."
  [db-before db-after event]
  (let [ctx ((:after events/queue-settle)
             {:coeffects {:db db-before :event [event]} :effects {:db db-after}})]
    [(get-in ctx [:effects :db]) (get-in ctx [:effects :orcpub.dnd.e5.events/settle-picks-soon])]))

(defn- edit
  "What an edit and the settle it queues leave: `queue-settle`, then `::e5/settle-picks`."
  [db-before db-after event]
  (let [[d settle?] (queued db-before db-after event)]
    (if settle? (events/settle-picks-db d) d)))

(deftest a-level-drop-holds-the-pick-and-raising-it-returns-it
  (let [at-6 (edit (db (ranger 7)) (db (ranger 6)) :some-edit)
        at-7 (edit at-6 (assoc at-6 :character
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
  (let [at-6 (edit (db (ranger 7)) (db (ranger 6)) :some-edit)
        open-7 (without-tactic (ranger 7))]
    (doseq [[label character event] [["another character" (assoc open-7 :db/id 99) :set-character]
                                     ["a new character" open-7 :reset-character]]]
      (let [after (edit at-6 (assoc at-6 :character character) event)]
        (is (not (steel-will? (:character after))) label)
        (is (= [] (:orcpub.dnd.e5/planned-picks after)) label)))))

(deftest a-draft-is-settled-when-the-template-arrives
  (let [ghost (assoc-in (ranger 7) [::entity/options :class 0 ::entity/options :levels]
                        (vec (butlast (get-in (ranger 7) [::entity/options :class 0
                                                          ::entity/options :levels]))))
        opened {:character ghost}
        cached (edit opened (merge (db ghost) opened) :cache-template)]
    (is (steel-will? ghost) "a level-6 draft that still stores Steel Will")
    (is (not (steel-will? (:character cached))) "settled once the template is cached")
    (is (= 1 (count (:orcpub.dnd.e5/planned-picks cached))))))

(deftest a-text-edit-queues-no-settle
  (let [d (db (ranger 6))]
    (is (not (events/settle-needed?
              d (assoc-in d [:character ::entity/values ::char5e/character-name] "Ilse")))
        "a name or notes edit cannot change which picks apply")
    (is (events/settle-needed? d (db (ranger 7))) "a level change can")
    (is (events/settle-needed? (dissoc d :orcpub.dnd.e5.autosave-fx/cached-template) d)
        "so can the template arriving")))

(deftest an-edit-applies-even-when-settling-fails
  (let [before (db (ranger 7))
        after (db (ranger 6))
        context {:coeffects {:db before :event [:some-edit]} :effects {:db after}}
        calls (atom 0)]
    (with-redefs [picks/update-planned (fn [& _]
                                         (swap! calls inc)
                                         (throw (ex-info "settle failed" {})))]
      (let [out ((:after events/queue-settle) context)]
        (is (= (:character after) (get-in out [:effects :db :character])) "the edit is applied")
        (is (true? (get-in out [:effects :orcpub.dnd.e5.events/settle-picks-soon]))
            "a settle is queued")
        (is (zero? @calls) "and not run inside the edit")
        (is (identical? after (events/settle-picks-db after))
            "the failed settle leaves the character as edited")
        (is (= 1 @calls))))))

(deftest nothing-changes-without-a-template-or-a-changed-character
  (let [d (db (ranger 6))]
    (is (= (select-keys d [:character :orcpub.dnd.e5/planned-picks])
           (select-keys (edit d (dissoc d :orcpub.dnd.e5.autosave-fx/cached-template) :e)
                        [:character :orcpub.dnd.e5/planned-picks])))
    (is (identical? d (edit d d :e)))))

;; As the builder passes them (character_builder `make-options-map`).
(defn- class-options [] (zipmap (map ::t/key @classes) @classes))

(def ^:private rogue-3
  {::entity/key :rogue
   ::entity/options {:multiclass-skill-proficiency [{::entity/key :athletics}]
                     :levels (vec (for [i (range 1 4)]
                                    {::entity/key (keyword (str "level-" i))}))}})

(deftest deleting-the-first-class-keeps-the-next-one
  (let [ranger-first (assoc-in (ranger 7) [::entity/options :class 1] rogue-3)
        deleted (events/delete-class ranger-first [:delete-class :ranger 0 (class-options)])
        rogue (get-in deleted [::entity/options :class 0])
        settled (edit (db ranger-first) (db deleted) :delete-class)]
    (is (= [:rogue] (mapv ::entity/key (get-in deleted [::entity/options :class]))))
    (is (= 3 (count (get-in rogue [::entity/options :levels]))) "the rogue keeps its levels")
    (is (= [:athletics] (mapv ::entity/key (get-in rogue [::entity/options
                                                           :multiclass-skill-proficiency])))
        "and its picks")
    (is (= [:leather] (mapv ::entity/key (get-in deleted [::entity/options :armor])))
        "it brings its starting equipment as the first class")
    (testing "its multiclass skill no longer applies, so the hold takes it, with the ranger's picks"
      (is (empty? (get-in settled [:character ::entity/options :class 0 ::entity/options
                                   :multiclass-skill-proficiency])))
      (is (= #{[:class :rogue :multiclass-skill-proficiency :athletics]
               [:class :ranger :levels :level-3 :ranger-archetype :hunter]}
             (set (map :address (:orcpub.dnd.e5/planned-picks settled))))))))

(deftest a-page-settles-again-only-when-its-character-or-template-changes
  (let [settle (subs/page-settler)
        calls (atom 0)
        real picks/update-planned
        other (without-tactic (ranger 7))
        ghost (assoc-in (ranger 7) [::entity/options :class 0 ::entity/options :levels]
                        (vec (butlast (get-in (ranger 7) [::entity/options :class 0
                                                          ::entity/options :levels]))))]
    (with-redefs [picks/update-planned (fn [& args] (swap! calls inc) (apply real args))]
      (is (not (steel-will? (settle @template ghost))) "the page leaves out the closed pick")
      (settle @template ghost)
      (settle @template (into {} ghost))
      (is (= 1 @calls) "the same character, by value, does not settle again")
      (settle @template other)
      (is (= 2 @calls) "a changed one does"))
    (with-redefs [picks/update-planned (fn [& _] (throw (ex-info "settle failed" {})))]
      (is (= ghost ((subs/page-settler) @template ghost))
          "a failure shows the character as stored"))))

(deftest a-save-right-after-an-edit-sends-the-settled-character
  (let [ghost (assoc-in (ranger 7) [::entity/options :class 0 ::entity/options :levels]
                        (vec (butlast (get-in (ranger 7) [::entity/options :class 0
                                                          ::entity/options :levels]))))
        fx (events/save-character-fx {:db (db ghost)}
                                     [:save-character (entity/build ghost @template)])]
    (is (steel-will? ghost) "edited to 6, its settle not yet run")
    (is (some? (:http fx)) "the save goes out")
    (is (not (re-find #"steel-will" (pr-str (get-in fx [:http :transit-params]))))
        "without the pick that stopped applying")
    (is (not (steel-will? (get-in fx [:db :character]))) "and the builder holds it")))

(deftest a-first-save-keeps-the-hold-and-a-new-character-empties-it
  (let [at-6 (edit (db (ranger 7)) (db (ranger 6)) :some-edit)
        saved-6 (assoc (:character at-6) :db/id 7)
        saved (:db (events/character-save-success-fx
                    {:db at-6} [:character-save-success {:body (char5e/to-strict saved-6)}]))
        reloaded (edit saved (assoc saved :character saved-6) :set-character)]
    (is (= 1 (count (:orcpub.dnd.e5/planned-picks at-6))) "unsaved, at 6, Steel Will held")
    (is (= 1 (count (:orcpub.dnd.e5/planned-picks reloaded)))
        "still held once the first save gives the character an id")
    (is (= [] (:orcpub.dnd.e5/planned-picks
               (edit reloaded (assoc reloaded :character (assoc (ranger 7) :db/id 99))
                     :set-character)))
        "opening another saved character empties it")
    (is (= [] (:orcpub.dnd.e5/planned-picks
               (:db (events/new-character-fx {:db at-6} [:new-character]))))
        "so does a new character")))

(defn- to-level [d n]
  (assoc d :character (event-handlers/set-class-level (:character d) [:set-class-level 0 n])))

(deftest a-drop-below-the-subclass-holds-it-even-in-one-burst-of-edits
  (let [at-7 (db (ranger 7))
        at-2 (edit at-7 (to-level at-7 2) :set-class-level)
        back (edit at-2 (to-level at-2 7) :set-class-level)
        [burst-2 _] (queued at-7 (to-level at-7 2) :set-class-level)
        burst-7 (edit burst-2 (to-level burst-2 7) :set-class-level)]
    (is (not (steel-will? (:character at-2))) "at 2 Hunter and Steel Will are gone")
    (is (= 1 (count (:orcpub.dnd.e5/planned-picks at-2))) "held: Hunter, Steel Will inside it")
    (is (steel-will? (:character back)) "raised to 7, they return")
    (is (= [] (:orcpub.dnd.e5/planned-picks back)))
    (testing "7 -> 2 -> 7 before any settle runs"
      (is (steel-will? (:character burst-7)))
      (is (= {:set-aside [] :restored [] :retired []}
             (:orcpub.dnd.e5/picks-change burst-7))
          "nothing to tell: the picks never left"))))

(def ^:private archetype
  [:character ::entity/options :class 0 ::entity/options :levels 2
   ::entity/options :ranger-archetype])

(defn- lantern-step? [character]
  (= :lantern-step (get-in character [::entity/options :class 0 ::entity/options :levels 2
                                      ::entity/options :ranger-archetype ::entity/options
                                      :wayfarer-tricks ::entity/key])))

(deftest switching-between-two-real-subclasses-holds-each-ones-picks
  ;; Hunter (SRD) and Wayfarer (coverage-pak.orcbrew, through the app's homebrew conversion).
  (let [at-7 (db (ranger 7))
        to-wayfarer (edit at-7 (assoc-in at-7 archetype
                                         {::entity/key :wayfarer
                                          ::entity/options {:wayfarer-tricks
                                                            {::entity/key :lantern-step}}})
                          :select-option)
        to-hunter (edit to-wayfarer (assoc-in to-wayfarer archetype {::entity/key :hunter})
                        :select-option)
        wayfarer-again (edit to-hunter (assoc-in to-hunter archetype {::entity/key :wayfarer})
                             :select-option)]
    (is (and (lantern-step? (:character to-wayfarer)) (not (steel-will? (:character to-wayfarer))))
        "Wayfarer with its trick; Hunter's Steel Will held")
    (is (and (steel-will? (:character to-hunter)) (not (lantern-step? (:character to-hunter))))
        "back to Hunter: Steel Will returns, the trick is held")
    (is (lantern-step? (:character wayfarer-again)) "Wayfarer again: the trick returns")))
