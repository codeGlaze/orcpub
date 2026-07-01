(ns orcpub.dnd.e5.ability-increase-grant-test
  "Layer 1 of the floating-ASI vertical (roadmap A4): `compile-ability-increases` turns a terse
   :ability-increases SPREAD into modifiers (fixed) + one selection (floating), and the combination
   lands on a built character. Proven bottom-up under the JVM gate before any UI work.

   A spread is a list of [amount pool] pairs; the whole list is the unit of the 'different abilities'
   rule. A pool is :any | :martial | :mental | #{:wis :con} (explicit) | :con (single stat = FIXED).
     [[2 :cha] [1 :martial]]  ; +2 CHA (fixed), +1 to any martial (floating)
   Fixed -> race-ability modifier (always applies); floating -> a player-chosen slot whose options are
   keyed asi-<idx>-<ability> and carry level-ability-increase. Distinctness/exact-shape are enforced by
   the assign-from-bag widget (rendered E2E); here we prove compile + apply. See
   docs/kb/ability-increase-spreads.md. JVM/clojure.test."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def ^:private S :orcpub.dnd.e5.character/str)
(def ^:private D :orcpub.dnd.e5.character/dex)
(def ^:private C :orcpub.dnd.e5.character/con)
(def ^:private W :orcpub.dnd.e5.character/wis)
(def ^:private Ch :orcpub.dnd.e5.character/cha)

(defn- origin-option [spread]
  (let [{:keys [modifiers selections]} (opt5e/compile-ability-increases spread)]
    (t/option-cfg {:name "Test Origin" :key :test-origin :modifiers modifiers :selections selections})))

(defn- template-with [spread]
  (t5e/template
   (concat
    (t5e/template-selections nil nil nil weapons5e/weapons-map weapons5e/weapons
                             sl5e/spell-lists spells5e/spell-map [] [] [] [] (common/map-by-key [{:name "Common" :key :common}]))
    [(t/selection-cfg {:name "Origin" :key :origin :tags #{:race}
                       :options [(origin-option spread)] :min 1 :max 1})])))

(def ^:private base-10 (zipmap char5e/ability-keys (repeat 10)))

;; picks: the player's floating slot choices, as option keys (asi-<idx>-<ability>) under the :asi selection.
(defn- raw-entity [picks]
  {:orcpub.entity/options
   {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value base-10}
    :origin {:orcpub.entity/key :test-origin
             :orcpub.entity/options (when (seq picks)
                                      {:asi (mapv (fn [k] {:orcpub.entity/key k}) picks)})}}})

(defn- abilities [spread picks]
  (char5e/ability-values (entity/build (raw-entity picks) (template-with spread))))

(deftest fixed-only-spread-applies-with-no-choice
  (testing "[[2 :str] [1 :con]] — both single-stat (fixed): apply with no player picks, no selection"
    (let [{:keys [selections]} (opt5e/compile-ability-increases [[2 :str] [1 :con]])
          a (abilities [[2 :str] [1 :con]] nil)]
      (is (empty? selections) "no floating slots -> no selection")
      (is (= 12 (S a))) (is (= 11 (C a))) (is (= 10 (D a))))))

(deftest fixed-plus-floating-compose
  (testing "[[2 :cha] [1 :martial]] — fixed +2 CHA + a floating +1 the player puts on DEX"
    (let [{:keys [modifiers selections]} (opt5e/compile-ability-increases [[2 :cha] [1 :martial]])
          sel (first selections)]
      (is (= 2 (count modifiers)) "fixed +2 CHA -> race-ability's two modifiers")
      (is (= :asi (::t/key sel))) (is (= 1 (::t/min sel))) (is (= 1 (::t/max sel)))
      (testing "the floating slot (idx 1) offers only the martial pool"
        (is (= #{:asi-1-str :asi-1-dex :asi-1-con} (set (map ::t/key (::t/options sel)))))))
    (let [a (abilities [[2 :cha] [1 :martial]] [:asi-1-dex])]
      (is (= 12 (Ch a)) "fixed CHA +2") (is (= 11 (D a)) "floating +1 on chosen DEX")
      (is (= 10 (S a))) (is (= 10 (C a))))))

(deftest multi-floating-spread-distinct
  (testing "[[2 :any] [1 :any]] — two floating slots, player picks STR(+2) and DEX(+1)"
    (let [a (abilities [[2 :any] [1 :any]] [:asi-0-str :asi-1-dex])]
      (is (= 12 (S a))) (is (= 11 (D a))) (is (= 10 (C a))))))

(deftest per-increment-pools
  (testing "[[2 #{:wis :con}] [1 #{:str :cha}]] — each slot has its OWN pool"
    (let [sel (first (:selections (opt5e/compile-ability-increases [[2 #{:wis :con}] [1 #{:str :cha}]])))]
      (is (= #{:asi-0-wis :asi-0-con :asi-1-str :asi-1-cha} (set (map ::t/key (::t/options sel))))
          "slot 0 offers wis/con; slot 1 offers str/cha"))
    (let [a (abilities [[2 #{:wis :con}] [1 #{:str :cha}]] [:asi-0-wis :asi-1-cha])]
      (is (= 12 (W a)) "+2 to chosen WIS") (is (= 11 (Ch a)) "+1 to chosen CHA")
      (is (= 10 (S a))) (is (= 10 (C a))))))

(deftest compile-is-crash-safe-at-the-fan-out
  (testing "nil / no field / a malformed entry compile to nothing (never throw) — the races sub maps
            this over every homebrew race, so one bad entry can't break the whole list"
    (doseq [x [nil [] [{:junk true}] [:nonsense]]]
      (is (= {:modifiers [] :selections []} (opt5e/compile-ability-increases x))
          (str "no-op for: " (pr-str x))))))

;; ─── Feat-path reconciliation (D34): feat-option-from-cfg reads BOTH formats ────────────────────
;; A feat's :ability-increases is read by SHAPE: a vector is the new cross-silo spread (routed through
;; compile-ability-increases); a set is the LEGACY feat format (+1 to one stat, optional :saves?
;; granting a save proficiency). The legacy path must be untouched (no regression); the spread path
;; is new reach. We drop the feat option's compiled modifiers/selections into the same origin harness
;; and build a character, so these are behavioral (ability values / save proficiency), not structural.
(defn- feat-option [ability-increases]
  (opt5e/feat-option-from-cfg nil nil nil weapons5e/weapons nil nil
                              {:name "Test Feat" :key :test-feat :description "t"
                               :ability-increases ability-increases}))

(defn- template-with-feat [ability-increases]
  (let [opt (feat-option ability-increases)]
    (t5e/template
     (concat
      (t5e/template-selections nil nil nil weapons5e/weapons-map weapons5e/weapons
                               sl5e/spell-lists spells5e/spell-map [] [] [] [] (common/map-by-key [{:name "Common" :key :common}]))
      [(t/selection-cfg {:name "Origin" :key :origin :tags #{:race}
                         ;; key :test-origin to match raw-entity's origin pick (reuses the harness)
                         :options [(t/option-cfg {:name "Test Feat" :key :test-origin
                                                  :modifiers (::t/modifiers opt)
                                                  :selections (::t/selections opt)})]
                         :min 1 :max 1})]))))

(defn- feat-abilities [ability-increases picks]
  (char5e/ability-values (entity/build (raw-entity picks) (template-with-feat ability-increases))))

(defn- feat-built [ability-increases picks]
  (entity/build (raw-entity picks) (template-with-feat ability-increases)))

(deftest feat-legacy-set-fixed-still-applies
  (testing "LEGACY #{:str} (singleton) → fixed +1 STR, unchanged by the reconciliation"
    (let [a (feat-abilities #{:orcpub.dnd.e5.character/str} nil)]
      (is (= 11 (S a)) "feat's +1 still lands on STR")
      (is (= 10 (C a))))))

(deftest feat-legacy-set-choice-still-offers-a-selection
  (testing "LEGACY #{:str :con} (multi) → a choose-one :asi selection keyed by ability (not asi-<idx>-)"
    (let [sel (first (filter #(= :asi (::t/key %)) (::t/selections (feat-option #{:orcpub.dnd.e5.character/str
                                                                                 :orcpub.dnd.e5.character/con}))))]
      (is (some? sel) "the multi-ability set yields a selection")
      (is (= #{:orcpub.dnd.e5.character/str :orcpub.dnd.e5.character/con}
             (set (map ::t/key (::t/options sel))))
          "options keyed by the namespaced ability (the legacy shape), not asi-<idx>-<ability>"))
    (testing "the chosen +1 applies on a built character"
      (let [a (feat-abilities #{:orcpub.dnd.e5.character/str :orcpub.dnd.e5.character/con}
                              [:orcpub.dnd.e5.character/con])]
        (is (= 11 (C a)) "+1 on the chosen CON") (is (= 10 (S a)))))))

(deftest feat-legacy-saves-marker-grants-save-proficiency
  (testing "LEGACY #{:str :saves?} → fixed +1 STR AND a STR saving-throw proficiency (the spread can't
            model saves, so this stays on the legacy path)"
    (let [built (feat-built #{:orcpub.dnd.e5.character/str :saves?} nil)
          a (char5e/ability-values built)]
      (is (= 11 (S a)) "+1 STR still applies alongside the save")
      (is (contains? (set (char5e/saving-throws built)) :orcpub.dnd.e5.character/str)
          ":saves? granted STR saving-throw proficiency"))))

(deftest feat-new-spread-format-compiles
  (testing "NEW [[2 :cha] [1 :martial]] on a FEAT → compile-ability-increases path (fixed + floating)"
    (let [sel (first (filter #(= :asi (::t/key %)) (::t/selections (feat-option [[2 :cha] [1 :martial]]))))]
      (is (= #{:asi-1-str :asi-1-dex :asi-1-con} (set (map ::t/key (::t/options sel))))
          "the floating slot is the spread shape (asi-<idx>-<ability>), restricted to martial"))
    (let [a (feat-abilities [[2 :cha] [1 :martial]] [:asi-1-dex])]
      (is (= 12 (Ch a)) "fixed +2 CHA from the feat spread")
      (is (= 11 (D a)) "floating +1 on the chosen DEX"))))

(deftest feat-new-spread-fixed-only-has-no-selection
  (testing "NEW [[1 :str]] on a feat → fixed +1, no floating selection"
    (is (empty? (filter #(= :asi (::t/key %)) (::t/selections (feat-option [[1 :str]]))))
        "single-stat spread is fully fixed -> no :asi selection")
    (is (= 11 (S (feat-abilities [[1 :str]] nil))) "+1 STR applies")))

;; ─── Save proficiencies: the rider (on the spread) + the standalone tool ────────────────────────
;; Two orthogonal concerns over ONE save primitive (modifiers/saving-throws):
;;   - rider: [amount pool :save] — the save follows the bump's (fixed or chosen) ability.
;;   - standalone :save-proficiencies [[count pool]] — saves with no bump (or on different stats).
;; Both proven by building a character and reading char5e/saving-throws (the ?saving-throws set).
(defn- origin-with [{:keys [ability-increases save-proficiencies]}]
  (let [ai (opt5e/compile-ability-increases ability-increases)
        sp (opt5e/compile-save-proficiencies save-proficiencies)]
    (t/option-cfg {:name "Test Origin" :key :test-origin
                   :modifiers (concat (:modifiers ai) (:modifiers sp))
                   :selections (concat (:selections ai) (:selections sp))})))

(defn- template-saves [cfg]
  (t5e/template
   (concat
    (t5e/template-selections nil nil nil weapons5e/weapons-map weapons5e/weapons
                             sl5e/spell-lists spells5e/spell-map [] [] [] [] (common/map-by-key [{:name "Common" :key :common}]))
    [(t/selection-cfg {:name "Origin" :key :origin :tags #{:race}
                       :options [(origin-with cfg)] :min 1 :max 1})])))

;; picks-map: selection-key -> [option-keys], e.g. {:asi [:asi-0-dex] :save-prof-0 [:save-0-wis]}
(defn- built-saves [cfg picks-map]
  (entity/build
   {:orcpub.entity/options
    {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value base-10}
     :origin {:orcpub.entity/key :test-origin
              :orcpub.entity/options (into {} (for [[sel ks] picks-map]
                                                [sel (mapv (fn [k] {:orcpub.entity/key k}) ks)]))}}}
   (template-saves cfg)))

(defn- saves-of [built] (set (char5e/saving-throws built)))

(deftest save-rider-fixed
  (testing "[[1 :str :save]] → +1 STR AND a STR save proficiency"
    (let [b (built-saves {:ability-increases [[1 :str :save]]} {})]
      (is (= 11 (S (char5e/ability-values b))) "the bump still applies")
      (is (contains? (saves-of b) S) "the rider granted a STR save"))))

(deftest save-rider-floating-rides-the-choice
  (testing "[[1 :martial :save]] → the save follows the CHOSEN martial stat, not the others"
    (let [b (built-saves {:ability-increases [[1 :martial :save]]} {:asi [:asi-0-dex]})]
      (is (= 11 (D (char5e/ability-values b))) "+1 on chosen DEX")
      (is (contains? (saves-of b) D) "DEX save granted (rode the choice)")
      (is (not (contains? (saves-of b) S)) "STR save NOT granted (wasn't the pick)"))))

(deftest save-rider-is-opt-in
  (testing "[[1 :str]] (no :save) → bump only, NO save (default is bump-only)"
    (is (empty? (saves-of (built-saves {:ability-increases [[1 :str]]} {}))))))

(deftest standalone-fixed-save-no-bump
  (testing ":save-proficiencies [[1 :con]] → a CON save, NO ability bump"
    (let [b (built-saves {:save-proficiencies [[1 :con]]} {})]
      (is (contains? (saves-of b) C) "fixed CON save granted")
      (is (= 10 (C (char5e/ability-values b))) "no bump — saves are independent of ASI"))))

(deftest standalone-floating-save-choice
  (testing ":save-proficiencies [[1 :mental]] → choose 1 mental save (own selection, save-<idx>- keys)"
    (let [sel (first (:selections (opt5e/compile-save-proficiencies [[1 :mental]])))]
      (is (= :save-prof-0 (::t/key sel)))
      (is (= #{:save-0-int :save-0-wis :save-0-cha} (set (map ::t/key (::t/options sel))))
          "options are the mental pool, keyed save-0-<ability> (distinct from the :asi keys)"))
    (let [b (built-saves {:save-proficiencies [[1 :mental]]} {:save-prof-0 [:save-0-wis]})]
      (is (contains? (saves-of b) W) "the chosen WIS save applies"))))

(deftest standalone-save-count
  (testing ":save-proficiencies [[2 :any]] → ONE selection, choose 2 distinct saves"
    (let [sel (first (:selections (opt5e/compile-save-proficiencies [[2 :any]])))]
      (is (= 2 (::t/min sel))) (is (= 2 (::t/max sel)))
      (is (true? (::t/different? sel)) "the 2 picks must be distinct")
      (is (= 6 (count (::t/options sel))) "all six abilities offered"))))

(deftest compile-save-proficiencies-crash-safe
  (testing "nil / empty / junk → {} (additive + fan-out safe, like compile-ability-increases)"
    (doseq [x [nil [] [:junk] [{:a 1}]]]
      (is (= {:modifiers [] :selections []} (opt5e/compile-save-proficiencies x))
          (str "no-op for: " (pr-str x))))))

(deftest rider-and-standalone-compose
  (testing "a feat can bump+save one stat AND grant an unrelated save: rider + standalone together"
    (let [b (built-saves {:ability-increases [[1 :str :save]] :save-proficiencies [[1 :wis]]} {})
          saves (saves-of b)]
      (is (= 11 (S (char5e/ability-values b))) "+1 STR")
      (is (contains? saves S) "STR save from the rider")
      (is (contains? saves W) "WIS save from the standalone tool (a stat that got no bump)"))))

;; ─── The '+ save prof' rider toggle is safe by construction (keyword-in-vector, not a map flag) ─────
(deftest toggle-increment-save-stays-canonical
  (testing "toggling the rider only ever yields [amount pool] <-> [amount pool :save]"
    (is (= [1 :martial :save] (opt5e/toggle-increment-save [1 :martial])))
    (is (= [1 :martial]       (opt5e/toggle-increment-save [1 :martial :save])))
    (is (= [2 :cha :save]     (opt5e/toggle-increment-save [2 :cha])))
    (testing "self-heals a malformed longer increment back to canonical (no crash, no garbage)"
      (is (= [1 :martial]     (opt5e/toggle-increment-save [1 :martial :save :junk]))))))

(deftest toggle-increment-save-hammer-never-corrupts
  (testing "50 rapid toggles never lose amount/pool or produce a non-canonical increment (the class of
            bug the map-flag toggle had — here it can't happen: we rebuild, never (not <collection>))"
    (loop [inc [1 :martial], n 50]
      (when (pos? n)
        (is (contains? #{[1 :martial] [1 :martial :save]} inc) "stays canonical")
        (is (and (= 1 (first inc)) (= :martial (second inc))) "amount+pool preserved every toggle")
        (recur (opt5e/toggle-increment-save inc) (dec n))))))

;; ─── Authoring-time save-coverage guidance (no mechanics; the builder warns-and-explains) ──────────
(deftest save-coverage-clean-when-no-overlap
  (testing "distinct fixed + a non-overlapping choice -> no warnings"
    (is (empty? (opt5e/save-coverage-warnings
                 {:ability-increases [[1 :str :save]] :save-proficiencies [[1 :con] [1 :mental]]})))))

(deftest save-coverage-flags-duplicate-fixed
  (testing "a fixed rider save + a fixed standalone save on the SAME stat -> a redundancy warning"
    (let [w (opt5e/save-coverage-warnings
             {:ability-increases [[1 :str :save]] :save-proficiencies [[1 :str]]})]
      (is (= 1 (count w)))
      (is (re-find #"STR save is granted more than once" (first w))))))

(deftest save-coverage-flags-fixed-reachable-from-a-choice
  (testing "a fixed CON save + a choice pool that CONTAINS con -> 'could pick CON and duplicate' note"
    (let [w (opt5e/save-coverage-warnings
             {:save-proficiencies [[1 :con] [1 :martial]]})]   ; martial = str/dex/con
      (is (some #(re-find #"could pick CON" %) w)))))

(deftest save-coverage-flags-overlapping-choice-pools
  (testing "two choice pools that overlap (rider martial + standalone any) -> overlap note"
    (let [w (opt5e/save-coverage-warnings
             {:ability-increases [[1 :martial :save]] :save-proficiencies [[1 :any]]})]
      (is (some #(re-find #"overlapping pools" %) w)))))

(deftest save-coverage-ignores-non-save-riders
  (testing "an ASI increment WITHOUT :save is not a save source: a +1 WIS bump (no :save) alongside a
            fixed WIS save must NOT read as a duplicate. (If the :save guard regressed, the bump would
            be counted as a second WIS save and this would warn — so the SAME stat is used on purpose.)"
    (is (empty? (opt5e/save-coverage-warnings
                 {:ability-increases [[1 :wis]] :save-proficiencies [[1 :wis]]})))))

;; Layer 5 (character half): the floating pick survives save/load AND still applies on rebuild.
(deftest character-floating-choice-survives-save-load
  (let [spread [[2 :cha] [1 :martial]]
        loaded (-> (raw-entity [:asi-1-dex]) char5e/to-strict char5e/from-strict)
        picked (->> (get-in loaded [:orcpub.entity/options :origin :orcpub.entity/options :asi])
                    (map :orcpub.entity/key) set)
        a      (char5e/ability-values (entity/build loaded (template-with spread)))]
    (testing "the chosen slot option round-trips through strict serialization"
      (is (contains? picked :asi-1-dex) "the user's floating pick survived save/load"))
    (testing "the rebuilt character still applies fixed + the chosen floating increase"
      (is (= 11 (D a)) "+1 still on the chosen DEX")
      (is (= 12 (Ch a)) "fixed +2 CHA still applies"))))
