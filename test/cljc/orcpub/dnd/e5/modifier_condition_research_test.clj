(ns orcpub.dnd.e5.modifier-condition-research-test
  "Pins how a predicate attached to a modifier's ::mods/conditions behaves in a real build:
   enforcement (M1), ordering against :classes (M2), sharing a fn with ::t/prereq-fn (M3),
   deferred modifiers (M4), and add-mod-total-levels-prereq's sequential branch (M5)."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.set :as sets]
            [orcpub.entity :as entity]
            [orcpub.entity-spec :as es]
            [orcpub.modifiers :as mods]
            [orcpub.template :as t]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def ^:private language-map (common/map-by-key [{:name "Common" :key :common}]))

(def ^:private abilities
  {::char5e/str 15 ::char5e/dex 15 ::char5e/con 14
   ::char5e/int 10 ::char5e/wis 10 ::char5e/cha 10})

(defn- class-opt [f]
  (f sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map))

(defn- make-template [class-options]
  (t5e/template
   (t5e/template-selections
    nil nil nil weapons5e/weapons-map weapons5e/weapons
    sl5e/spell-lists spells5e/spell-map [] [] class-options [] language-map)))

(def ^:private base-template
  (delay (make-template [(class-opt classes5e/fighter-option)
                         (class-opt classes5e/rogue-option)])))

(defn- lvl [n] {::entity/key (keyword (str "level-" n))
                ::entity/options {:hit-points {::entity/key :average ::entity/value 5}}})

(def ^:private fighter {::entity/key :fighter ::entity/options {:levels [(lvl 1)]}})
(def ^:private rogue {::entity/key :rogue ::entity/options {:levels [(lvl 1)]}})

(defn- raw [classes]
  {::entity/options
   {:ability-scores {::entity/key :standard-roll ::entity/value abilities}
    :class classes}})

(defn- build [tmpl classes] (entity/build (raw classes) tmpl))

(defn- update-option-mods
  "Template with `f` applied to the flattened ::t/modifiers of option `opt-key` of top-level
   selection `sel-key`."
  [tmpl sel-key opt-key f]
  (update tmpl ::t/selections
          (fn [sels]
            (mapv (fn [s]
                    (if (= sel-key (::t/key s))
                      (update s ::t/options
                              (fn [os]
                                (mapv (fn [o]
                                        (if (= opt-key (::t/key o))
                                          (update o ::t/modifiers #(f (flatten %)))
                                          o))
                                      os)))
                      s))
                  sels))))

(defn- conj-cond-where [pred-mod cnd]
  (fn [ms] (map (fn [m] (if (pred-mod m) (update m ::mods/conditions conj cnd) m)) ms)))

(defn- key= [k] (fn [m] (= k (::mods/key m))))

(defn- mod-order
  "The key order apply-options applies modifiers in, replicated from entity/apply-options."
  [tmpl classes]
  (let [r (raw classes)
        ms (sort-by ::mods/order
                    (entity/collect-modifiers-2 r (entity/flatten-options (::entity/options r)) tmpl))
        deps (reduce (fn [m {k ::mods/key d ::mods/deps}] (if (seq d) (update m k sets/union d) m))
                     {} ms)
        all-deps (merge-with sets/union deps (::es/deps (::t/base tmpl)))]
    {:order (vec (rseq (entity/kahn-sort all-deps))) :mods ms}))

;; ---------------------------------------------------------------- M1

(deftest m1-added-condition-is-enforced
  ;; M1 CONFIRMED: full entity/build; the rogue's :saving-throws modifier, predicate conj-ed.
  (let [flag (atom true)
        tmpl (update-option-mods @base-template :class :rogue
                                 (conj-cond-where (key= :saving-throws) (fn [_] @flag)))
        saves #(set (char5e/saving-throws (build tmpl [rogue])))]
    (is (= #{::char5e/dex ::char5e/int} (set (char5e/saving-throws (build @base-template [rogue]))))
        "control: unmodified template")
    (reset! flag true)
    (is (= #{::char5e/dex ::char5e/int} (saves)) "predicate true: effect present")
    (reset! flag false)
    (is (= #{} (saves)) "predicate false: effect absent")))

;; ---------------------------------------------------------------- M2

(defn- rogue-first? [e] (= :rogue (first (es/entity-val e :classes))))

(defn- flag-mod [deps seen]
  (mods/mod-f nil nil (fn [e] (assoc e :research-flag true)) :research-flag deps
              [(fn [e] (swap! seen conj (es/entity-val e :classes)) (rogue-first? e))]))

(deftest m2-condition-reading-classes-needs-the-dep
  ;; M2 CONFIRMED (dep needed): a key outside the dep graph sorts first, before :classes is set.
  (testing "fresh modifier, ::deps #{} — condition sees :classes before any cls modifier ran"
    (let [seen (atom [])
          tmpl (update-option-mods @base-template :class :rogue #(concat % [(flag-mod #{} seen)]))
          built (build tmpl [rogue])]
      (is (= [:rogue] (char5e/classes built)) "the built character IS rogue-first")
      (is (= [[]] @seen) "but the condition read :classes as the base []")
      (is (nil? (:research-flag built)) "so the wrong answer: effect absent")))
  (testing "same modifier, ::deps #{:classes} — ordered after :classes, right answer"
    (let [seen (atom [])
          tmpl (update-option-mods @base-template :class :rogue
                                   #(concat % [(flag-mod #{:classes} seen)]))
          built (build tmpl [rogue])]
      (is (= [[:rogue]] @seen))
      (is (true? (:research-flag built)))))
  (testing "a key with no :classes dep is ordered by the graph, so the answer is positional"
    (let [{:keys [order]} (mod-order @base-template [rogue])
          idx (zipmap order (range))]
      (is (some? (idx :classes)) ":classes is a graph node (other modifiers depend on it)")
      (is (nil? (idx :research-flag)) "an unknown key gets no index: order-modifiers sorts nil first"))))

(deftest m2-real-modifier-without-classes-dep-is-positional
  ;; M2 order-dependence CONFIRMED on real rogue modifiers: :traits sorts before :classes.
  (let [{:keys [order]} (mod-order @base-template [rogue])
        idx (zipmap order (range))
        with-cond (fn [k] (build (update-option-mods @base-template :class :rogue
                                                     (conj-cond-where (key= k) rogue-first?))
                                 [rogue]))
        plain (build @base-template [rogue])]
    (is (< (idx :traits) (idx :classes) (idx :tool-profs)))
    (testing ":traits runs before :classes — condition false for a rogue-first rogue"
      (is (< (count (es/entity-val (with-cond :traits) :traits))
             (count (es/entity-val plain :traits)))))
    (testing ":tool-profs runs after :classes — same condition true"
      (is (= (es/entity-val plain :tool-profs) (es/entity-val (with-cond :tool-profs) :tool-profs)))
      (is (contains? (es/entity-val plain :tool-profs) :thieves-tools)))))
;; ---------------------------------------------------------------- M3

(defn- first-class-is [kw] (fn [c] (= kw (first (char5e/classes c)))))

(defn- spy [f log] (fn [c] (let [r (f c)] (swap! log conj {:arg c :result r}) r)))

(deftest m3-same-fn-as-prereq-and-condition
  ;; M3 CONFIRMED when the modifier depends on :classes; REFUTED for a modifier with no :classes dep.
  (doseq [kw [:fighter :rogue]]
    (let [classes [fighter rogue]
          cond-log (atom [])
          nodep-log (atom [])
          pre-log (atom [])
          f (first-class-is kw)
          tmpl (-> @base-template
                   (update-option-mods :class :fighter
                                       (conj-cond-where (key= :saving-throws) (spy f cond-log)))
                   (update-option-mods :class :rogue
                                       #(concat % [(mods/mod-f nil nil identity :research-flag #{}
                                                               [(spy f nodep-log)])])))
          built (build tmpl classes)
          _ (doall (entity/remove-disqualified-selections [{::t/prereq-fn (spy f pre-log)}] built))
          pre (first @pre-log)
          cnd (first @cond-log)
          nodep (first @nodep-log)]
      (testing (str "first class is " kw)
        (is (= [:fighter :rogue] (char5e/classes built)))
        (is (identical? built (:arg pre)) "prereq-fn receives the built character itself")
        (is (map? (:arg cnd)) "a condition receives the mid-build entity map")
        (is (not (identical? built (:arg cnd))))
        (is (= [:fighter :rogue] (es/entity-val (:arg cnd) :classes))
            ":saving-throws depends on :classes, so :classes is final when its condition runs")
        (is (= (:result pre) (:result cnd)) "prereq and :classes-dependent condition agree")
        (is (= (= kw :fighter) (:result pre)))
        (is (= [] (es/entity-val (:arg nodep) :classes)) "no-dep modifier: :classes not yet set")
        (is (false? (:result nodep)) "so it answers false even when the prereq answers true")))))

;; ---------------------------------------------------------------- M4

(deftest m4-condition-on-deferred-modifier-is-lost
  ;; M4 CONFIRMED: collect-modifiers-2 replaces the deferred record with (deferred-fn value).
  (let [deferred? #(some? (::mods/deferred-fn %))
        tmpl (update-option-mods @base-template :ability-scores :standard-roll
                                 (conj-cond-where deferred? (constantly false)))
        std-roll (some #(when (= :standard-roll (::t/key %)) %)
                       (::t/options (some #(when (= :ability-scores (::t/key %)) %)
                                          (::t/selections tmpl))))
        dmod (first (filter deferred? (flatten (::t/modifiers std-roll))))]
    (is (= 1 (count (::mods/conditions dmod))) "the condition IS on the deferred record")
    (is (empty? (::mods/conditions ((::mods/deferred-fn dmod) abilities)))
        "the modifier the deferred-fn builds carries none")
    (is (= abilities (es/entity-val (build tmpl [rogue]) :base-abilities))
        "a (constantly false) condition did not stop the abilities being applied")))

;; ---------------------------------------------------------------- M5

(def ^:private m (mod5e/num-attacks 2))

(deftest m5-sequential-branch-throws-when-realized
  ;; M5 CONFIRMED with a qualifier: lazy, so it throws on realization, not on the call.
  (is (= 1 (count (::mods/conditions (opt5e/add-mod-total-levels-prereq 3 {:key :rogue} m))))
      "map branch: one condition added")
  (let [r (opt5e/add-mod-total-levels-prereq 3 {:key :rogue} [m m])]
    (is (seq? r) "the call itself returns a lazy seq without throwing")
    (is (thrown-with-msg? IllegalArgumentException #"Don't know how to create ISeq from: java.lang.Long"
                          (doall r))))
  (testing "through subclass-option: a nested vector in a level's :modifiers"
    (let [opt (opt5e/subclass-option sl5e/spell-lists spells5e/spell-map language-map {:key :rogue}
                                     {:name "Research" :levels {3 {:modifiers [[m m]]}}})]
      (is (thrown? IllegalArgumentException (doall (flatten (::t/modifiers opt))))))))

(defn- walk-options [sels f]
  (doseq [s sels o (::t/options s)]
    (f s o)
    (walk-options (::t/selections o) f)))

(deftest m5-built-in-subclass-data-never-reaches-the-branch
  ;; M5 reachability: realizing every option's modifiers across all 12 classes does not throw.
  (let [tmpl (make-template
              (concat (map class-opt [classes5e/barbarian-option classes5e/bard-option
                                      classes5e/cleric-option classes5e/druid-option
                                      classes5e/fighter-option classes5e/monk-option
                                      classes5e/paladin-option classes5e/ranger-option
                                      classes5e/rogue-option classes5e/sorcerer-option
                                      classes5e/wizard-option])
                      [(classes5e/warlock-option sl5e/spell-lists spells5e/spell-map {} language-map
                                                 weapons5e/weapons-map [] [])]))
        subclasses (atom 0)
        mods-seen (atom 0)]
    (walk-options (::t/selections tmpl)
                  (fn [s o]
                    (when (contains? (::t/tags s) :subclass) (swap! subclasses inc))
                    (swap! mods-seen + (count (doall (flatten (::t/modifiers o)))))))
    (is (= 24 @subclasses) "every built-in subclass option was visited")
    (is (pos? @mods-seen))))

(deftest m5-homebrew-level-modifier-constructors-return-maps
  ;; M5 reachability, homebrew: every constructor spell_subs.cljs level-modifier/make-levels uses.
  (doseq [[nm x] {"weapon-proficiency" (mod5e/weapon-proficiency :longsword)
                  "num-attacks" (mod5e/num-attacks 2)
                  "damage-resistance" (mod5e/damage-resistance :fire)
                  "damage-immunity" (mod5e/damage-immunity :fire)
                  "saving-throw-advantage" (mod5e/saving-throw-advantage [:charmed])
                  "skill-proficiency" (mod5e/skill-proficiency :athletics)
                  "armor-proficiency" (mod5e/armor-proficiency :light)
                  "tool-proficiency" (mod5e/tool-proficiency :thieves-tools)
                  "flying-speed-override" (mod5e/flying-speed-override 30)
                  "swimming-speed-override" (mod5e/swimming-speed-override 30)
                  "flying-speed-equal-to-walking" (mod5e/flying-speed-equal-to-walking)
                  "spells-known" (mod5e/spells-known 1 :shield ::char5e/int "Rogue")
                  "cleric-spell" (opt5e/cleric-spell 1 :bless 1)
                  "paladin-spell" (opt5e/paladin-spell 1 :bless)}]
    (is (and (map? x) (not (sequential? x))) nm)))
