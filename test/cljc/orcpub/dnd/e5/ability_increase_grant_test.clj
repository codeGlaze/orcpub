(ns orcpub.dnd.e5.ability-increase-grant-test
  "Layer 1 of the floating-ASI vertical (roadmap A4): `compile-ability-increases` turns a DATA
   allotment list into modifiers (fixed/creator-chosen) + selections (floating/user-chosen), and the
   combination lands on a built character. Proven bottom-up under the JVM gate before any UI work.

   Allotment data shape:
     [{:ability :cha :amount 2}                                         ; FIXED  -> race-ability modifier
      {:select {:from #{:str :dex :con} :num 1 :amount 1 :different? true}}] ; FLOATING -> ability-increase-selection-2
   Fixed compiles via mod5e/race-ability (-> ?ability-increases); floating via the existing
   ability-increase-selection-2 with a level-ability-increase modifier-fn (-> ?level-ability-increases);
   both sum into the final ability (template_base.cljc:97-101). This is the user-choice (`select`) half
   of the grant layer — the thing Variant Human/Tasha's do that creator-fixed ASI doesn't.

   compile-ability-increases lives here as a proven prototype (like compile-feature); layer 2 wires it
   into the real race/feat assembly. JVM/clojure.test."
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
(def ^:private Ch :orcpub.dnd.e5.character/cha)

;; compile-ability-increases now lives in production: opt5e/compile-ability-increases (options.cljc),
;; so the cljs race/feat assembly can call it (layer 2). This test exercises the production fn.

;; "+2 CHA (fixed) and +1 to any martial stat (floating)" — the user's worked example
(def sample-allotments
  [{:ability Ch :amount 2}
   {:select {:from :martial :num 1 :amount 1 :different? true}}])

(defn- origin-option [allotments]
  (let [{:keys [modifiers selections]} (opt5e/compile-ability-increases allotments)]
    (t/option-cfg {:name "Test Origin" :key :test-origin :modifiers modifiers :selections selections})))

(defn- template-with [allotments]
  (t5e/template
   (concat
    (t5e/template-selections nil nil nil weapons5e/weapons-map weapons5e/weapons
                             sl5e/spell-lists spells5e/spell-map [] [] [] [] (common/map-by-key [{:name "Common" :key :common}]))
    [(t/selection-cfg {:name "Origin" :key :origin :tags #{:race}
                       :options [(origin-option allotments)] :min 1 :max 1})])))

(def ^:private base-10 (zipmap char5e/ability-keys (repeat 10)))

(defn- raw-entity [allotments chosen-floating]
  {:orcpub.entity/options
   {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value base-10}
    :origin {:orcpub.entity/key :test-origin
             :orcpub.entity/options (when chosen-floating
                                      {:asi {:orcpub.entity/key chosen-floating}})}}})

(defn- build [allotments chosen-floating]
  (entity/build (raw-entity allotments chosen-floating) (template-with allotments)))

(deftest ^:diagnostic dump-asi
  (println "\n=== compile-ability-increases (observe) ===")
  (let [{:keys [modifiers selections]} (opt5e/compile-ability-increases sample-allotments)]
    (println "modifier count =" (count modifiers) " selection count =" (count selections))
    (println "floating selection key =" (::t/key (first selections)))
    (println "floating offered =" (pr-str (map ::t/key (::t/options (first selections))))))
  (println "fixed-only abilities =" (pr-str (char5e/ability-values (build [{:ability Ch :amount 2}] nil))))
  (println "fixed+floating(dex) abilities =" (pr-str (char5e/ability-values (build sample-allotments D)))))

;; ---------------------------------------------------------------------------
;; Baseline (captured from dump-asi): the compile shape + the fixed/floating combination on a real build
;; ---------------------------------------------------------------------------
(deftest compile-shape-and-restriction
  (let [{:keys [modifiers selections]} (opt5e/compile-ability-increases sample-allotments)]
    (is (= 2 (count modifiers)) "fixed +2 CHA compiles to race-ability's two modifiers")
    (is (= 1 (count selections)) "one floating selection")
    (testing "the floating choice is RESTRICTED to the named subset (martial = str/dex/con) — not all six"
      (is (= #{S D C} (set (map ::t/key (::t/options (first selections)))))))))

(deftest fixed-and-floating-compose-on-a-built-character
  (testing "FIXED alone — +2 CHA applies with no player choice"
    (let [a (char5e/ability-values (build [{:ability Ch :amount 2}] nil))]
      (is (= 12 (Ch a))) (is (= 10 (S a)))))
  (testing "FIXED + FLOATING together — +2 CHA AND a user-chosen +1 (DEX) both land"
    (let [a (char5e/ability-values (build sample-allotments D))]
      (is (= 12 (Ch a)) "fixed CHA +2")
      (is (= 11 (D a))  "floating +1 went to the chosen DEX")
      (is (= 10 (S a))  "the unchosen martial stat is unchanged")
      (is (= 10 (C a)))))
  (testing "the floating +1 follows the USER's choice — CON instead of DEX"
    (let [a (char5e/ability-values (build sample-allotments C))]
      (is (= 11 (C a))) (is (= 10 (D a))) (is (= 12 (Ch a)))))
  (testing "amount is honored — a Tasha's-style floating +2 lands as +2"
    (let [a (char5e/ability-values (build [{:select {:from #{S D} :num 1 :amount 2}}] S))]
      (is (= 12 (S a))) (is (= 10 (D a))))))

;; ---------------------------------------------------------------------------
;; Layer 5 (character half): the user's floating choice survives save/load.
;; A character stores its pick as the chosen option's ::entity/key under the
;; :floating-asi-0 selection; this proves char5e/to-strict->from-strict (the real
;; localStorage/server path, db.cljs:171/281) preserves it AND that the rebuilt
;; character still applies the increase — not just that a key matches.
;; ---------------------------------------------------------------------------
;; ---------------------------------------------------------------------------
;; Exact spreads (the "+2/+1", "+3/+2/+1" bag): compile to (ability,amount) options the player
;; assigns to DISTINCT abilities. Reuses the existing per-option modifier mechanism, so the
;; compiled output lands on a built character with the right per-ability bonuses. (Uniqueness +
;; exact composition are enforced by the assign-from-bag widget — exercised in the rendered E2E.)
;; ---------------------------------------------------------------------------
(deftest bag-spread-compiles-to-stat-amount-options
  (let [{:keys [selections]} (opt5e/compile-ability-increases [{:select {:from :martial :amounts [2 1]}}])
        sel (first selections)]
    (is (= :asi (::t/key sel)) "one selection, keyed :asi so the builder renders it")
    (is (= [2 1] (::t/amounts sel)) "carries the bag for the assign-from-bag widget")
    (is (= 2 (::t/min sel))) (is (= 2 (::t/max sel)))
    (testing "martial pool (3 stats) x distinct amounts {2,1} -> 6 (stat,amount) options"
      (is (= 6 (count (::t/options sel))))
      (is (contains? (set (map ::t/key (::t/options sel))) :str-plus-2))
      (is (contains? (set (map ::t/key (::t/options sel))) :con-plus-1)))))

(deftest bag-spread-assigns-exact-amounts-to-distinct-abilities
  (testing "the player assigns the +2 to STR and the +1 to DEX (distinct) -> both land"
    (let [tmpl (template-with [{:select {:from :any :amounts [2 1]}}])
          ent  {:orcpub.entity/options
                {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value base-10}
                 :origin {:orcpub.entity/key :test-origin
                          :orcpub.entity/options {:asi [{:orcpub.entity/key :str-plus-2}
                                                        {:orcpub.entity/key :dex-plus-1}]}}}}
          a (char5e/ability-values (entity/build ent tmpl))]
      (is (= 12 (S a)) "STR got the +2")
      (is (= 11 (D a)) "DEX got the +1")
      (is (= 10 (C a)) "an unchosen stat is unchanged")
      (is (= 10 (Ch a))))))

(deftest character-floating-choice-survives-save-load
  (let [loaded (-> (raw-entity sample-allotments D) char5e/to-strict char5e/from-strict)
        chosen (get-in loaded [:orcpub.entity/options :origin
                               :orcpub.entity/options :asi :orcpub.entity/key])
        a      (char5e/ability-values (entity/build loaded (template-with sample-allotments)))]
    (testing "the chosen ability key round-trips through strict serialization"
      (is (= D chosen) "the user's floating pick (DEX) survived save/load"))
    (testing "the rebuilt character still applies fixed + the chosen floating increase"
      (is (= 11 (D a))  "+1 still on the chosen DEX")
      (is (= 12 (Ch a)) "fixed +2 CHA still applies")
      (is (= 10 (C a))  "the unchosen martial stat is unchanged"))))
