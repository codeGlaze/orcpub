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

(defn- class-entry [k & [options]]
  {::entity/key k ::entity/options (merge {:levels [{::entity/key :level-1}]} options)})

(deftest making-another-class-the-starting-class-moves-its-first-class-picks
  (let [skills #{:acrobatics :deception :perception :stealth}
        start (db (assoc-in (ranger 1) [::entity/options :class]
                            [(class-entry :rogue {:skill-proficiency
                                                  (mapv #(hash-map ::entity/key %) (sort skills))})
                             (class-entry :ranger)]))
        swap (fn [d k] (edit d (update d :character events/make-starting-class
                                       [::char5e/make-starting-class k {}])
                             ::char5e/make-starting-class))
        rogue-skills (fn [d] (->> (get-in d [:character ::entity/options :class])
                                  (some #(when (= :rogue (::entity/key %)) %))
                                  ::entity/options :skill-proficiency (map ::entity/key) set))
        ranger-first (swap start :ranger)
        rogue-first (swap ranger-first :rogue)]
    (is (= [:ranger :rogue] (map ::entity/key (get-in ranger-first [:character ::entity/options :class]))))
    (is (empty? (rogue-skills ranger-first)) "the Rogue's starting-class skills leave it")
    (is (= 4 (count (:orcpub.dnd.e5/planned-picks ranger-first))) "and are held")
    (is (= skills (rogue-skills rogue-first)) "made the starting class again, they return")
    (is (= start (update start :character events/make-starting-class
                         [::char5e/make-starting-class :rogue {}]))
        "the starting class already first: nothing changes")))

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

(deftest a-save-before-the-settle-summarizes-what-it-sends
  (let [at-2 (edit (db (ranger 3)) (db (ranger 2)) :some-edit)
        bare-3 (assoc-in (ranger 3) [::entity/options :class 0 ::entity/options :levels 2
                                     ::entity/options] nil)
        [raised _] (queued at-2 (assoc at-2 :character bare-3) :some-edit)
        fx (events/save-character-fx {:db raised} [:save-character (entity/build bare-3 @template)])
        summary (get-in fx [:http :transit-params :orcpub.entity.strict/summary])]
    (is (= 1 (count (:orcpub.dnd.e5/planned-picks at-2))) "at 2, Hunter held")
    (is (= ["Hunter"] (map ::char5e/subclass-name (::char5e/classes summary)))
        "raised to 3 and saved at once: Hunter comes back, and the summary names it")))

(deftest random-starts-the-hold-over
  (let [at-6 (edit (db (ranger 7)) (db (ranger 6)) :some-edit)
        [d _] (queued (assoc at-6 :orcpub.dnd.e5/replacing? true)
                      (assoc at-6 :orcpub.dnd.e5/replacing? true :character (ranger 1))
                      :set-character)]
    (is (= 1 (count (:orcpub.dnd.e5/planned-picks at-6))) "unsaved at 6, Steel Will held")
    (is (= [[] []] ((juxt :orcpub.dnd.e5/planned-picks :orcpub.dnd.e5/settle-trail) d))
        "a random character, unsaved too, starts with no hold and no trail to compare against")
    (is (not (contains? d :orcpub.dnd.e5/replacing?))))
  (testing "a random result with the same choices (every part locked) still starts it over"
    (let [at-6 (edit (db (ranger 7)) (db (ranger 6)) :some-edit)
          marked (assoc at-6 :orcpub.dnd.e5/replacing? true)
          [d _] (queued marked (assoc-in marked [:character ::entity/values ::char5e/character-name] "R")
                        :set-character)]
      (is (= [] (:orcpub.dnd.e5/planned-picks d)))
      (is (not (contains? d :orcpub.dnd.e5/replacing?))))))

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
    (let [noticed (assoc at-6 :orcpub.dnd.e5/restore-notice {:restored [{:address [:x]}]}
                         :orcpub.dnd.e5/open-notice {:held [{:address [:y]}]})
          fresh (:db (events/new-character-fx {:db noticed} [:new-character]))]
      (is (= [] (:orcpub.dnd.e5/planned-picks fresh)) "so does a new character")
      (is (= [nil nil []] ((juxt :orcpub.dnd.e5/restore-notice :orcpub.dnd.e5/open-notice
                                 :orcpub.dnd.e5/settle-trail) fresh))
          "and it shows none of the previous character's notices, nor settles against it"))))

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

(def ^:private rogue-4
  {::entity/key :rogue
   ::entity/options {:skill-proficiency (mapv #(hash-map ::entity/key %)
                                              [:stealth :acrobatics :insight :perception])
                     :levels [{::entity/key :level-1}]}})

(defn- as-class [d entry] (assoc-in d [:character ::entity/options :class 0] entry))

(deftest picks-that-come-back-build-a-notice-with-undo-and-redo
  (let [level-1 {:levels [{::entity/key :level-1}]}
        rogue (db (assoc-in (ranger 1) [::entity/options :class 0] rogue-4))
        ranger-now (edit rogue (as-class rogue {::entity/key :ranger ::entity/options level-1})
                         :set-class)
        back (edit ranger-now (as-class ranger-now {::entity/key :rogue ::entity/options level-1})
                   :set-class)
        skills #(set (map ::entity/key (get-in % [:character ::entity/options :class 0
                                                  ::entity/options :skill-proficiency])))
        undone (events/undo-restore-db back)
        redone (events/redo-restore-db undone)]
    (is (= #{:stealth :acrobatics :insight :perception} (skills back))
        "rogue again, its skills return")
    (is (= 4 (count (get-in back [:orcpub.dnd.e5/restore-notice :restored])))
        "and the notice has them")
    (is (= #{} (skills undone)) "Undo takes them out")
    (is (= [] (:orcpub.dnd.e5/planned-picks undone)) "without holding them again")
    (is (= 4 (count (get-in undone [:orcpub.dnd.e5/restore-notice :undone]))))
    (is (= #{:stealth :acrobatics :insight :perception} (skills redone)) "Redo puts them back")
    (is (nil? (:orcpub.dnd.e5/restore-notice
               (edit back (assoc back :character (assoc (ranger 7) :db/id 5)) :set-character)))
        "another character clears it")))

(def ^:private ghost-6
  "A level-6 ranger still storing Steel Will: saved before the hold existed."
  (assoc-in (ranger 7) [::entity/options :class 0 ::entity/options :levels]
            (vec (butlast (get-in (ranger 7) [::entity/options :class 0
                                              ::entity/options :levels])))))

(deftest opening-a-character-whose-choices-no-longer-apply
  (let [before (db (ranger 1))
        open (fn [character]
               (let [[d _] (queued before (assoc before :character character) :set-character)]
                 (events/settle-picks-fx {:db d} [:orcpub.dnd.e5/settle-picks])))
        saved (open (assoc ghost-6 :db/id 42))
        draft (open ghost-6)]
    (testing "a saved character"
      (is (= 1 (count (get-in saved [:db :orcpub.dnd.e5/open-notice :held]))) "the notice has it")
      (is (nil? (:orcpub.dnd.e5.character/save-character-throttled saved))
          "but opening it saves nothing: the saved copy changes when the player saves")
      (is (nil? (get-in saved [:db :orcpub.dnd.e5.character/character-map 42]))))
    (testing "an unsaved draft: the notice, nothing to save"
      (is (= 1 (count (get-in draft [:db :orcpub.dnd.e5/open-notice :held]))))
      (is (nil? (:orcpub.dnd.e5.character/save-character-throttled draft))))
    (testing "an ordinary level drop is not an opening"
      (is (nil? (:orcpub.dnd.e5/open-notice
                 (edit (db (ranger 7)) (db (ranger 6)) :set-class-level)))))))
