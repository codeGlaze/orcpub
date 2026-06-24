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

(deftest ^:diagnostic dump-asi
  (println "\n=== compile-ability-increases [[2 :cha] [1 :martial]] ===")
  (let [{:keys [modifiers selections]} (opt5e/compile-ability-increases [[2 :cha] [1 :martial]])]
    (println "modifier count =" (count modifiers) " selection count =" (count selections))
    (println "floating option keys =" (pr-str (map ::t/key (::t/options (first selections)))))))

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
