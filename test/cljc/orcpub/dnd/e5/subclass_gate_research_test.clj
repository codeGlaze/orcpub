(ns orcpub.dnd.e5.subclass-gate-research-test
  "Research pins for the gates in `subclass-option` (options.cljc). Each deftest asserts what was
   OBSERVED on this code, and names the claim it tests. See `hidden-selection-picks.md`."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.set :as set]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def ^:private language-map (common/map-by-key [{:name "Common" :key :common}]))

(def ^:private abilities
  {::char5e/str 15 ::char5e/dex 15 ::char5e/con 14
   ::char5e/int 13 ::char5e/wis 13 ::char5e/cha 10})

;; A synthetic subclass whose :spellcasting carries known-spell schedules, so
;; `spell-selections` in `subclass-option` is non-empty. No live subclass reaches it.
(def ^:private test-caster
  {:name "Test Caster"
   :key :test-caster
   :spellcasting {:level-factor 3
                  :ability ::char5e/int
                  :cantrips-known {3 2 10 1}
                  :spells-known {3 2 4 1 7 1}}})

(def ^:private spell-lists
  (assoc sl5e/spell-lists :test-caster (sl5e/spell-lists :wizard)))

(def ^:private the-template
  (delay
   (t5e/template
    (t5e/template-selections
     nil nil nil weapons5e/weapons-map weapons5e/weapons
     spell-lists spells5e/spell-map
     [] []
     [(classes5e/fighter-option spell-lists spells5e/spell-map {:fighter [test-caster]}
                                language-map weapons5e/weapons-map)
      (classes5e/ranger-option spell-lists spells5e/spell-map {} language-map
                               weapons5e/weapons-map)
      (classes5e/rogue-option spell-lists spells5e/spell-map {} language-map
                              weapons5e/weapons-map)]
     []
     language-map))))

(defn- lvl [n & [extra]]
  {::entity/key (keyword (str "level-" n))
   ::entity/options (merge {:hit-points {::entity/key :average ::entity/value 5}} extra)})

(defn- class-entry
  "A class taken to level `n`, with `subclass` stored under `archetype-sel` at level 3."
  [cls-kw n & [archetype-sel subclass extra-opts]]
  {::entity/key cls-kw
   ::entity/options (merge extra-opts
                           {:levels (vec (for [i (range 1 (inc n))]
                                           (lvl i (when (and archetype-sel (= i 3))
                                                    {archetype-sel subclass}))))})})

(defn- raw [classes]
  {::entity/options
   {:ability-scores {::entity/key :standard-roll ::entity/value abilities}
    :class classes}})

(defn- build [classes] (entity/build (raw classes) @the-template))

(defn- shown
  "Selections the builder would show, after prereq filtering."
  [classes]
  (entity/get-all-selections-2 @the-template (entity/make-path-map (raw classes)) (build classes)))

(defn- shown-paths [classes] (set (map ::entity/path (shown classes))))

(defn- trait-names [built] (set (map :name (char5e/traits built))))

;; ---------------------------------------------------------------------------
;; S1 — a pick in a subclass LEVEL selection, after the class level drops below that level
;; ---------------------------------------------------------------------------

(def ^:private hunter-steel-will
  {::entity/key :hunter
   ::entity/options {:defensive-tactics {::entity/key :steel-will}}})

(def ^:private defensive-tactics-path
  [:class :ranger :levels :level-3 :ranger-archetype :hunter :defensive-tactics])

(defn- frightened-advantage? [built]
  (boolean (some #(some #{:frightened} (:types %))
                 (char5e/get-prop built :saving-throw-advantage))))
(deftest s1-subclass-level-selection-pick-outlives-its-gate
  ;; S1 CONFIRMED: the selection hides below ranger 7, its stored pick keeps applying.
  (testing "ranger 7: the selection is shown and the pick applies"
    (let [cs [(class-entry :ranger 7 :ranger-archetype hunter-steel-will)]]
      (is (contains? (shown-paths cs) defensive-tactics-path))
      (is (contains? (trait-names (build cs)) "Steel Will"))
      (is (frightened-advantage? (build cs)))))
  (doseq [[label cs] [["ranger 6" [(class-entry :ranger 6 :ranger-archetype hunter-steel-will)]]
                      ["ranger 3" [(class-entry :ranger 3 :ranger-archetype hunter-steel-will)]]
                      ["fighter 4 + ranger 3, total 7"
                       [(class-entry :fighter 4)
                        (class-entry :ranger 3 :ranger-archetype hunter-steel-will)]]]]
    (testing (str label ": the selection is hidden, the pick still applies")
      (is (not (contains? (shown-paths cs) defensive-tactics-path)))
      (is (contains? (trait-names (build cs)) "Steel Will"))
      (is (frightened-advantage? (build cs))))))

(def ^:private champion {::entity/key :champion})

(defn- initiative [built] (char5e/get-prop built :initiative))

(deftest s1-control-direct-level-modifiers-stop-below-their-level
  ;; S1 control CONFIRMED: Champion's level-7 DIRECT modifiers carry the total-levels-prereq-2
  ;; condition, so they stop applying below fighter 7, including when total level is still 7.
  (let [f7  (build [(class-entry :fighter 7 :martial-archetype champion)])
        f6  (build [(class-entry :fighter 6 :martial-archetype champion)])
        f6r (build [(class-entry :fighter 6 :martial-archetype champion) (class-entry :rogue 1)])]
    (is (contains? (trait-names f7) "Remarkable Athlete"))
    (is (not (contains? (trait-names f6) "Remarkable Athlete")))
    (is (not (contains? (trait-names f6r) "Remarkable Athlete")))
    (is (> (initiative f7) (initiative f6))
        "the level-7 initiative bonus also drops (DEX 15 both ways)")))

(def ^:private fighting-style-path [:class :fighter :levels :level-3 :martial-archetype :champion :fighting-style])

(defn- shown-fighting-style-max [classes]
  (->> (entity/combine-selections (shown classes))
       (filter #(= [:class :fighter :fighting-style] (::t/ref %)))
       (map ::t/max)))

(deftest s1-ref-variant-champion-fighting-style
  ;; S1 CONFIRMED (ref variant): Champion's level-10 Fighting Style shares the ref
  ;; [:class :fighter :fighting-style] with fighter level 1, so both picks live in one pool.
  ;; Below fighter 10 the pool shown shrinks to max 1, and both stored styles still apply.
  (let [two-styles {:fighting-style [{::entity/key :defense} {::entity/key :archery}]}
        f10 [(class-entry :fighter 10 :martial-archetype champion two-styles)]
        f9  [(class-entry :fighter 9 :martial-archetype champion two-styles)]]
    (is (contains? (shown-paths f10) fighting-style-path))
    (is (not (contains? (shown-paths f9) fighting-style-path)))
    (is (= [2] (shown-fighting-style-max f10)))
    (is (= [1] (shown-fighting-style-max f9)))
    (is (= #{"Defense Fighting Style" "Archery Fighting Style"}
           (set/intersection (trait-names (build f9))
                                     #{"Defense Fighting Style" "Archery Fighting Style"})))
    (is (= 1 (char5e/get-prop (build f9) :armored-ac-bonus)))))

;; ---------------------------------------------------------------------------
;; S2 — the spell-selections gate (>= lvl total-levels)
;; ---------------------------------------------------------------------------

(def ^:private test-caster-path [:class :fighter :levels :level-3 :martial-archetype :test-caster])

(defn- tc-entry [n] (class-entry :fighter n :martial-archetype {::entity/key :test-caster}))

(defn- shown-spell-selections [classes]
  (->> (shown classes)
       (filter #(= test-caster-path (vec (butlast (::entity/path %)))))
       (filter #(contains? (::t/tags %) :spells))
       (map (juxt ::t/key ::t/min))
       sort))

(defn- gated-spell-selections
  "[lvl selection] for each of the subclass option's spell selections, in build order."
  []
  (let [tmpl (opt5e/spellcasting-template spell-lists spells5e/spell-map
                                          (assoc (:spellcasting test-caster) :class-key :test-caster)
                                          test-caster)
        lvls (for [[l sels] (:selections tmpl) _ sels] l)
        opt  (opt5e/subclass-option spell-lists spells5e/spell-map language-map
                                    {:key :fighter :name "Fighter"} test-caster)
        sels (filter #(and (::t/prereq-fn %) (contains? (::t/tags %) :spells)) (::t/selections opt))]
    (map vector lvls sels)))
(deftest s2-spell-selection-gate-is-inverted
  ;; S2 CONFIRMED: lvl is the class level the spells are learned at; the gate shows a selection
  ;; only while total level <= lvl, the reverse of `total-levels-prereq`.
  (let [lvl->pass (fn [n] (set (for [[l s] (gated-spell-selections)
                                     :when ((::t/prereq-fn s) (build [(tc-entry n)]))]
                                 l)))]
    (is (= #{3 4 7 10} (set (map first (gated-spell-selections)))))
    (is (= #{3 4 7 10} (lvl->pass 3)) "fighter 3 already sees the level 4, 7 and 10 selections")
    (is (= #{4 7 10} (lvl->pass 4)))
    (is (= #{7 10} (lvl->pass 7)))
    (is (= #{} (lvl->pass 12)) "fighter 12 sees none of them")
    (testing "and the builder shows exactly that"
      (is (seq (shown-spell-selections [(tc-entry 3)])))
      (is (empty? (shown-spell-selections [(tc-entry 12)]))))))

;; ---------------------------------------------------------------------------
;; S3 — total-levels-prereq vs total-levels-prereq-2
;; ---------------------------------------------------------------------------

(defn- outcome [f c]
  (try (boolean (f c)) (catch Throwable e (symbol (.getSimpleName (class e))))))

(defn- outcome-raw [f c]
  (try (f c) (catch Throwable e (symbol (.getSimpleName (class e))))))

(def ^:private s3-chars
  (delay
   {"fighter 5"           (build [(class-entry :fighter 5)])
    "fighter 3 + rogue 2" (build [(class-entry :fighter 3) (class-entry :rogue 2)])
    "rogue 1"             (build [(class-entry :rogue 1)])}))

(deftest s3-prereqs-agree-on-real-characters-with-a-present-class
  ;; S3 CONFIRMED equivalent on real characters with a numeric level and a class the
  ;; character has, or no class-key; REFUTED off that domain (see next test).
  (doseq [[label c] @s3-chars
          level [0 1 2 3 5 6 20]
          ck (cons nil (char5e/classes c))]
    (is (= (outcome (opt5e/total-levels-prereq level ck) c)
           (outcome (opt5e/total-levels-prereq-2 level ck) c))
        (str label " level " level " class " ck))))

(deftest s3-prereqs-differ-off-the-domain
  ;; S3 REFUTED as full equivalence: prereq throws where prereq-2 returns false or nil.
  (let [c (@s3-chars "fighter 5")]
    (testing "class-key the character lacks"
      (is (= 'NullPointerException (outcome (opt5e/total-levels-prereq 1 :wizard) c)))
      (is (false? (outcome (opt5e/total-levels-prereq-2 1 :wizard) c)))
      (is (= 'NullPointerException (outcome (opt5e/total-levels-prereq 0 :wizard) c)))
      (is (true? (outcome (opt5e/total-levels-prereq-2 0 :wizard) c))))
    (testing "nil level"
      (is (= 'NullPointerException (outcome (opt5e/total-levels-prereq nil) c)))
      (is (nil? (outcome-raw (opt5e/total-levels-prereq-2 nil) c))))
    (testing "nil character"
      (is (= 'NullPointerException (outcome (opt5e/total-levels-prereq 1) nil)))
      (is (nil? (outcome-raw (opt5e/total-levels-prereq-2 1) nil))))))
