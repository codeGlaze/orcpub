(ns orcpub.dnd.e5.legacy-shim-equivalence-test
  "Characterization for the shim registry (handoff-grant-rows.md step 2; D34).

   Per fixed-class legacy key, builds one character on the legacy `:props` shape and one on the
   `:grants` shape the shim would normalize it to, and compares the BUILT SHEET. A row that
   matches is safe to normalize at import; a row that does not is a behaviour change to record.

   Why the built sheet and not the modifiers: a modifier's source is captured inside its opaque
   `::mods/fn`, so two modifiers that write different sheet entries project identically. The
   modifier-level comparison reports MATCH for `:skill-prof-or-expertise`, which is wrong.

   Written before the shim exists, so the shim's arrival shows as a diff in expected values."
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

(defn- build-feat
  "A character whose one bonus feat carries `feat-cfg`'s mechanics."
  [feat-cfg]
  (let [feat (opt5e/feat-option-from-cfg language-map spells5e/spell-map sl5e/spell-lists
                                         weapons5e/weapons-map {} pools
                                         (merge {:name "Shim Probe" :key :shim-probe} feat-cfg))]
    (entity/build
     {:orcpub.entity/options
      {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value abilities}
       :bonus-feat {:orcpub.entity/key :shim-probe}}}
     (t5e/template
      (concat
       (t5e/template-selections nil nil nil weapons5e/weapons-map weapons5e/weapons
                                sl5e/spell-lists spells5e/spell-map [] [] [] [] language-map)
       [(t/selection-cfg {:name "Bonus Feat" :key :bonus-feat :tags #{:feats}
                          :options [feat] :min 1 :max 1})])))))

;; The fixed-class rows of builder-disposition-audit.md's 35-table that have a registered pool.
;; {label {:props … :grants […] :read (fn [built] …)}}
(def ^:private rows
  {"skill-prof (3,12)"
   {:props {:skill-prof {:athletics true}}
    :grants [{:pool :skills :key :athletics}]
    :read char5e/skill-proficiencies}

   "weapon-prof (5,14)"
   {:props {:weapon-prof {:longsword true}}
    :grants [{:pool :weapons :key :longsword}]
    :read char5e/weapon-proficiencies}

   "armor-prof (7,15,29)"
   {:props {:armor-prof {:medium true}}
    :grants [{:pool :armor :key :medium}]
    :read char5e/armor-proficiencies}

   "language (11)"
   {:props {:language {:elvish true}}
    :grants [{:pool :languages :key :elvish}]
    :read char5e/languages}

   "damage-resistance (9,30)"
   {:props {:damage-resistance {:fire true}}
    :grants [{:pool :damage-resistances :key :fire}]
    :read char5e/damage-resistances}

   "damage-immunity (10,18)"
   {:props {:damage-immunity {:poison true}}
    :grants [{:pool :damage-immunities :key :poison}]
    :read char5e/damage-immunities}

   "skill-prof-or-expertise (31)"
   {:props {:skill-prof-or-expertise {:athletics true}}
    :grants [{:pool :skill-expertise :key :athletics}]
    :read (juxt char5e/skill-proficiencies char5e/skill-expertise)}

   ;; The case row 31 actually turns on: the skill is ALSO proficient from somewhere else, which
   ;; is when "proficiency, or expertise if you already have it" is supposed to give expertise.
   "skill-prof-or-expertise, already proficient"
   {:props {:skill-prof {:athletics true} :skill-prof-or-expertise {:athletics true}}
    :grants [{:pool :skills :key :athletics} {:pool :skill-expertise :key :athletics}]
    :read (juxt char5e/skill-proficiencies char5e/skill-expertise)}})

(defn- legacy-vs-grant [{:keys [props grants read]}]
  [(read (build-feat {:props props})) (read (build-feat {:grants grants}))])

(deftest ^:diagnostic report-per-row-equivalence
  (println "\n=== legacy :props vs :grants — BUILT SHEET, per fixed-class row ===")
  (doseq [[label row] (sort-by key rows)]
    (let [[l g] (legacy-vs-grant row)]
      (println (format "%-42s %s" label (if (= l g) "MATCH" "DIFFERS")))
      (when (not= l g)
        (println "   legacy:" (pr-str l))
        (println "   grant :" (pr-str g))))))

(def ^:private safe-to-normalize
  ["skill-prof (3,12)" "weapon-prof (5,14)" "armor-prof (7,15,29)" "language (11)"
   "damage-resistance (9,30)" "damage-immunity (10,18)"])

(deftest six-fixed-class-rows-normalize-identically
  (testing "the legacy :props shape and its :grants replacement build the same sheet entry"
    (doseq [label safe-to-normalize]
      (let [[l g] (legacy-vs-grant (rows label))]
        (is (= l g) (str label " — normalizing this key changes the built character"))))))

;; ---------------------------------------------------------------------------
;; Row 31 is NOT safe, and the reason is a live defect in the pool, not in the shim.
;;
;; `skill-proficiency` writes `?skill-profs [skill-kw source] true` — source is the second level
;; of the key. The legacy arm passes the granting item's key (`make-feat-modifiers` is called with
;; `option-key`); the `:skill-expertise` pool passes `nil`, because `:options-fn` takes only
;; `plugin-vals` and never learns which item is granting.
;;
;; So the pool's own proficiency and any other nil-sourced proficiency collapse into ONE entry,
;; and `skill-prof-or-expertise`'s predicate — "is some source other than me already granting
;; this?" — can never see a second source. The result is that the pool's expertise half never
;; fires. Measured below, not inferred.
;; ---------------------------------------------------------------------------

(deftest skill-expertise-pool-loses-the-granting-source
  (testing "legacy records the granting item as the source; the pool records nil"
    (let [[l g] (legacy-vs-grant (rows "skill-prof-or-expertise (31)"))]
      (is (= [{:athletics {:shim-probe true}} nil] l))
      (is (= [{:athletics {nil true}} nil] g))))

  (testing "and so the pool never grants the expertise half, where legacy does"
    (let [[l g] (legacy-vs-grant (rows "skill-prof-or-expertise, already proficient"))]
      (is (= [{:athletics {nil true, :shim-probe true}} #{:athletics}] l)
          "legacy: a second source is visible, so proficiency upgrades to expertise")
      (is (= [{:athletics {nil true}} nil] g)
          "pool: both grants collapse to the nil source, so nothing upgrades"))))
