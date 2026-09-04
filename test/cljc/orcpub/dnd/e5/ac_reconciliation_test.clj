(ns orcpub.dnd.e5.ac-reconciliation-test
  "Tests for AC reconciliation, in four parts:

   SECTION 1 — BASELINE: builds real characters and pins the AC the LIVE engine
     (template_base.cljc) produces today. Guards against regression if the reconciler is
     replaced.
   SECTION 2 — natural-armor + unarmored-defense. This WAS a real stacking bug (18 where the
     rules give 15); fixed on integration with a symmetric tie-break and pinned here at 15.
     Also records that two natural-armor sources do NOT stack — an earlier claim that they
     did was a fixture artifact (see verification-discipline.md).
   SECTION 3 — unit tests for the REPLACEMENT reconciler, orcpub.dnd.e5.armor-class, which is
     NOT wired into the app yet. Pure functions, so they run without the character-build
     machinery. Passing here says nothing about app behaviour; SECTION 1 covers that.
   SECTION 4 — placeholder for backward-compat shims, should a public homebrew AC key
     (:lizardfolk-ac, :tortle-ac, :two-weapon-ac-1, :medium-armor-max-dex-3) be deprecated.
     Empty until that happens.

   JVM/clojure.test so it runs under the enforced `lein test` gate."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.armor-class :as ac]
            [orcpub.modifiers :as mod]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

;; ===========================================================================
;; SECTION 1 — BASELINE FIXTURES + CURRENT BEHAVIOR (pinned)
;; ===========================================================================

(def language-map (common/map-by-key [{:name "Common" :key :common}]))

(defn class-opt [opt-fn]
  (opt-fn sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map))

;; A minimal synthetic class that grants ?natural-ac-bonus 3 the SAME way the
;; Draconic Bloodline subclass does (classes.cljc:2279 `(mod/modifier ?natural-ac-bonus 3)`)
;; — class-root :modifiers, exactly where Barbarian puts its unarmored-defense mods.
;; Lets us put natural-ac + unarmored-defense on ONE character with built-in mechanics,
;; without the Draconic subclass+ancestry selection ceremony.
(defn natural-armor-class
  "Synthetic class granting ?natural-ac-bonus `val` via the SAME mechanism all real content
   uses — `mod/modifier` (a SET, classes.cljc:2279 / options.cljc:3607,3614), NOT the cum-sum
   constructor. This matters: two SET sources last-win (no stacking); an earlier version of
   this fixture used mod5e/natural-ac-bonus (cum-sum) and manufactured a fake 'A3 stacking bug'."
  [key val]
  {:name (name key) :key key :hit-die 8 :ability-increase-levels [4 8 12 16 19]
   :subclass-title "Origin" :subclass-level 3 :subclasses [] :profs {}
   :modifiers [(mod/modifier ?natural-ac-bonus val)]})

(defn feat-class
  "Synthetic class carrying arbitrary modifiers, so a feat/prop effect can be put on a build."
  [key modifiers]
  {:name (name key) :key key :hit-die 8 :ability-increase-levels [4 8 12 16 19]
   :subclass-title "Origin" :subclass-level 3 :subclasses [] :profs {}
   :modifiers modifiers})

;; Medium Armor Master raises the medium Dex cap to 3 (options.cljc:1461).
(def mam-class (feat-class :mam- [opt5e/medium-armor-master-max-bonus]))
;; The live homebrew natural-armor prop, exactly as a homebrew race would carry it.
(def lizardfolk-prop-class (feat-class :liz- (vec (opt5e/plugin-modifiers {:lizardfolk-ac true} :liz-))))
;; The other bespoke natural-armor prop. It behaves differently from :lizardfolk-ac — it REPLACES
;; ?armor-class-with-armor outright instead of taking a max against it — so it needs its own pins.
(def tortle-prop-class     (feat-class :tor- (vec (opt5e/plugin-modifiers {:tortle-ac true} :tor-))))

;; Step 2 of the refactor: mod5e/ac-formula opens ?ac-fns, which had no writers at all.
;; A homebrew "your AC = 19 while unarmored" calculation, registered the way homebrew would.
(def homebrew-ac-class
  (feat-class :hb-ac- [(mod5e/ac-formula (fn [armor _shield] (if armor 0 19)))]))
;; A flat +1 that should land on whichever calculation wins.
(def hb-bonus-class
  (feat-class :hb-bonus- [(mod5e/ac-bonus-fn (fn [_armor _shield] 1))]))

;; Step 3: authored AC written as :props data, compiled by make-feat-modifiers. These go
;; through plugin-modifiers exactly as a homebrew race/class/feat's :props would.
(defn props-class [key props]
  (feat-class key (vec (opt5e/plugin-modifiers props key))))

(def p-natural  (props-class :p-nat-   {:ac {:ac 13 :abilities [:dex] :armor? false}}))
(def p-monk     (props-class :p-monk-  {:ac {:ac 10 :abilities [:dex :wis] :armor? false :shield? false}}))
(def p-barb     (props-class :p-barb-  {:ac {:ac 10 :abilities [:dex :con] :armor? false}}))
(def p-floor    (props-class :p-floor- {:ac {:ac 16 :abilities []}}))            ; no :armor? = either
(def p-nat-any  (props-class :p-natany- {:ac {:ac 13 :abilities [:dex]}}))  ; no :armor? = either
;; a Ring/Cloak of Protection-style +1: the ?magical-ac-bonus scalar, which lives INSIDE the base
(def ring-class (feat-class :ring- [(mod/cum-sum-mod ?magical-ac-bonus 1)]))
;; construct-style: plating that only helps while the shield is deployed. Nothing in SRD does this;
;; the vocabulary must express it regardless — homebrew flexibility is the point.
(def p-shieldonly (props-class :p-shonly- {:ac {:ac 16 :abilities [] :shield? true}}))
;; Bracers of Defense: "+2 to AC if you are wearing no armor and using no shield" — shipped as
;; (mod5e/unarmored-ac-bonus 2), i.e. it writes ONLY the no-shield channel.
(def bracers-class (feat-class :bracers- [(mod5e/unarmored-ac-bonus 2)]))
;; The two halves :tortle-ac used to weld together, separately authorable.
(def p-flat17   (props-class :p-flat17- {:ac {:ac 17 :abilities []}}))   ; flat natural AC, no restriction
(def p-noarmor  (props-class :p-noarm-  {:armor-gives-no-ac true}))      ; worn armor stops counting
(def p-bonus    (props-class :p-bonus- {:ac-bonus {:ac-bonus 1}}))
(def p-armorbon (props-class :p-abon-  {:ac-bonus {:ac-bonus 1 :armor? true}}))

(def natural-armor-class-full (natural-armor-class :nat-armor- 3))
(def natural-armor-class-b    (natural-armor-class :nat-armor-b- 3))  ; a SECOND natural source

(def test-template
  (t5e/template
   (t5e/template-selections
    nil nil nil
    weapons5e/weapons-map weapons5e/weapons
    sl5e/spell-lists spells5e/spell-map
    [] []                                            ; backgrounds, races
    [(class-opt classes5e/monk-option)
     (class-opt classes5e/barbarian-option)
     (class-opt classes5e/fighter-option)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map natural-armor-class-full)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map natural-armor-class-b)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map mam-class)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map lizardfolk-prop-class)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map tortle-prop-class)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map p-flat17)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map p-noarmor)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map homebrew-ac-class)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map hb-bonus-class)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map p-natural)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map p-monk)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map p-barb)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map p-floor)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map p-nat-any)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map ring-class)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map p-shieldonly)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map bracers-class)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map p-bonus)
     (opt5e/class-option sl5e/spell-lists spells5e/spell-map {} language-map
                         weapons5e/weapons-map p-armorbon)]
    [] language-map)))

;; str10 dex14(+2) con16(+3) int10 wis16(+3) cha10 — same as ac_characterization_test
(def abilities {:orcpub.dnd.e5.character/str 10 :orcpub.dnd.e5.character/dex 14
                :orcpub.dnd.e5.character/con 16 :orcpub.dnd.e5.character/int 10
                :orcpub.dnd.e5.character/wis 16 :orcpub.dnd.e5.character/cha 10})

(defn- level-1 [class-key]
  {:orcpub.entity/key class-key
   :orcpub.entity/options
   {:levels [{:orcpub.entity/key :level-1
              :orcpub.entity/options
              {:hit-points {:orcpub.entity/key :average :orcpub.entity/value 4}}}]}})

(defn entity-of [& class-keys]
  {:orcpub.entity/options
   {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value abilities}
    :class (mapv level-1 class-keys)}})

(defn unarmored-ac [& class-keys]
  (let [built (entity/build (apply entity-of class-keys) test-template)]
    ((char5e/armor-class-with-armor built) nil nil)))

(deftest baseline-single-class-unarmored
  (testing "current pinned unarmored AC (matches ac_characterization_test)"
    (is (= 15 (unarmored-ac :monk))      "Monk: 10 + Dex(2) + Wis(3)")
    (is (= 15 (unarmored-ac :barbarian)) "Barbarian: 10 + Dex(2) + Con(3)")
    (is (= 12 (unarmored-ac :fighter))   "Fighter: 10 + Dex(2)")))

;; ---------------------------------------------------------------------------
;; SECTION 1b — the interactions a reconciler rewrite must preserve. These are the
;; cases where the current engine's behaviour is easy to break by accident, so they are
;; pinned from real character builds BEFORE any refactor. Probes print the live numbers.
;; ---------------------------------------------------------------------------

(def dex16-abilities (assoc abilities :orcpub.dnd.e5.character/dex 16))  ; +3, so a cap of 3 is visible

(defn entity-with [abils class-keys]
  {:orcpub.entity/options
   {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value abils}
    :class (mapv level-1 class-keys)}})

(defn ac-fn-for [abils & class-keys]
  (char5e/armor-class-with-armor (entity/build (entity-with abils class-keys) test-template)))

;; Real armor carries an explicit magical-ac-bonus 0: the armored branch does
;; (+ ... (::mi5e/magical-ac-bonus armor) ...), which is nil on non-magical armor — fine in
;; cljs (nil is 0 in +) but an NPE on the JVM. Same workaround as ac_characterization_test.
(def scale-mail {:base-ac 14 :type :medium :orcpub.dnd.e5.magic-items/magical-ac-bonus 0})
(def plate      {:base-ac 18 :type :heavy  :orcpub.dnd.e5.magic-items/magical-ac-bonus 0})
(def leather    {:base-ac 11 :type :light  :orcpub.dnd.e5.magic-items/magical-ac-bonus 0})
;; custom "weird material": heavy AC that declares its own Dex allowance
(def custom-heavy {:base-ac 16 :type :heavy :max-dex-mod 2
                   :orcpub.dnd.e5.magic-items/magical-ac-bonus 0})
(def shield {:type :shield})
;; A +1 shield, so the shield contributes 3 rather than 2. Needed wherever a test must tell the
;; shield's contribution APART from some other +2 — with a plain shield the arithmetic collides.
(def magic-shield {:type :shield :orcpub.dnd.e5.magic-items/magical-ac-bonus 1})

(deftest shield-interactions-with-unarmored-defense
  (testing "Monk cannot use Unarmored Defense with a shield; Barbarian can"
    (let [monk ((ac-fn-for abilities :monk) nil shield)
          barb ((ac-fn-for abilities :barbarian) nil shield)]
      (is (= 14 monk)
          "Monk holding a shield cannot use Unarmored Defense: 10 + Dex(2) + shield(2) = 14 (NOT 15)")
      (is (= 17 barb)
          "Barbarian may use a shield with Unarmored Defense: 10 + Dex(2) + Con(3) + shield(2) = 17"))))

(deftest medium-armor-master-raises-the-dex-cap
  (testing "Dex 16 (+3) in scale mail: cap 2 normally, 3 with Medium Armor Master"
    (let [plain ((ac-fn-for dex16-abilities :fighter) scale-mail nil)
          mam   ((ac-fn-for dex16-abilities :fighter :mam-) scale-mail nil)]
      (is (= 16 plain) "scale mail 14 + min(cap 2, Dex 3) = 16")
      (is (= 17 mam)
          "Medium Armor Master raises the cap to 3: 14 + min(3, Dex 3) = 17. A rewrite that reads
           the armor's own :max-dex-mod (2) INSTEAD of this channel would silently give 16."))))

(deftest custom-armor-declaring-its-own-dex-cap
  (testing "heavy armor carrying :max-dex-mod 2 — does the engine read the field today?"
    (let [custom ((ac-fn-for abilities :fighter) custom-heavy nil)
          heavy  ((ac-fn-for abilities :fighter) plate nil)]
      ;; FLIPPED 2026-09: was pinned at 16. ?armor-dex-bonus now takes the more permissive of the
      ;; type cap and the armor's own :max-dex-mod, so custom heavy that declares a Dex allowance
      ;; gets it.
      (is (= 18 custom)
          "16 + min(:max-dex-mod 2, Dex 2) = 18 — the armor's own cap beats heavy's default 0")
      (is (= 18 heavy) "plain plate: 18 + 0 Dex"))))

(deftest character-magic-reaches-every-calculation
  (testing "a Ring of Protection must add to whichever calculation wins, prop or authored"
    (let [prop     ((ac-fn-for abilities :fighter :liz- :ring-) nil nil)
          authored ((ac-fn-for abilities :fighter :p-natany- :ring-) nil nil)]
      ;; FLIPPED 2026-09: was pinned at prop 16 / authored 15. ?magical-ac-bonus used to live
      ;; inside ?base-armor-class, so only calculations that read the base picked it up and an
      ;; authored calculation — a bare value — silently lost the ring. It is now an entry in
      ;; ?ac-bonus-fns, summed onto the winner, so both forms get it.
      (is (= 16 prop)     "13 + Dex(2) + ring(1)")
      (is (= 16 authored) "same number by the same route — no longer a migration hazard"))))

(deftest natural-armor-applies-while-armored-and-the-authored-form-matches
  (testing ":lizardfolk-ac keeps applying when armor IS worn, taking the better — and an authored
            {:ac 13 :abilities [:dex]} with NO :armor? tag reproduces it exactly.

            This is the migration-equivalence check. Tagging natural armor :armor? false would
            return 0 while armored and regress leather from 15 to 13."
    (is (= 15 ((ac-fn-for abilities :fighter :liz-) leather nil))
        "prop, leather: natural 13+Dex(2) beats leather's 13")
    (is (= 18 ((ac-fn-for abilities :fighter :liz-) plate nil))
        "prop, plate: plate's 18 beats natural 15")
    (is (= 15 ((ac-fn-for abilities :fighter :p-natany-) leather nil))
        "authored form, leather: same 15")
    (is (= 18 ((ac-fn-for abilities :fighter :p-natany-) plate nil))
        "authored form, plate: same 18")))

(deftest homebrew-natural-armor-prop-on-a-barbarian
  (testing "the live :lizardfolk-ac prop (13 + Dex) on a Barbarian — the reachable stacking shape"
    (let [ac ((ac-fn-for abilities :barbarian :liz-) nil nil)]
      (is (= 15 ac)
          "the reachable homebrew shape: natural 13+Dex vs 10+Dex+Con, take the better = 15"))))


;; ---------------------------------------------------------------------------
;; SECTION 1c — homebrew AC, via the new mod5e/ac-formula constructor. Before this,
;; ?ac-fns had no writers anywhere in src/, so no content could add an AC calculation at
;; all; the only way was to replace ?armor-class-with-armor wholesale (:lizardfolk-ac,
;; :tortle-ac). These build real characters through the live engine.
;; ---------------------------------------------------------------------------

(deftest homebrew-ac-formula-competes-in-the-live-engine
  (testing "a homebrew 'AC = 19 unarmored' calculation beats the 10 + Dex default"
    (is (= 12 ((ac-fn-for abilities :fighter) nil nil))
        "control: Fighter alone is 10 + Dex(2)")
    (is (= 19 ((ac-fn-for abilities :fighter :hb-ac-) nil nil))
        "the homebrew calculation wins the max")
    (is (= 18 ((ac-fn-for abilities :fighter :hb-ac-) plate nil))
        "wearing plate, the unarmored calculation returns 0 and plate's 18 wins — no stacking")))

(deftest bonuses-land-on-a-winning-homebrew-formula
  (testing "a flat bonus applies to the homebrew calculation that won, not just to the base"
    (is (= 20 ((ac-fn-for abilities :fighter :hb-ac- :hb-bonus-) nil nil))
        "19 from the homebrew calculation + 1 bonus = 20")))


;; ---------------------------------------------------------------------------
;; SECTION 1d — AC authored as :props data (step 3). One shape covers every case:
;;   {:ac N :abilities [...] :armor? bool-or-absent :shield? bool}   -> competes, best wins
;;   {:ac-bonus N :armor? ... :shield? ...}                          -> sums onto the winner
;; Compiled by make-feat-modifiers, so it reaches every silo that carries :props.
;; ---------------------------------------------------------------------------

(deftest authored-calculation-competes-and-yields-to-armor
  (testing "{:ac 13 :abilities [:dex] :armor? false} — natural-armor shaped"
    (is (= 15 ((ac-fn-for abilities :fighter :p-nat-) nil nil))
        "13 + Dex(2) beats the 10 + Dex default")
    (is (= 18 ((ac-fn-for abilities :fighter :p-nat-) plate nil))
        ":armor? false — wearing plate it contributes nothing and plate's 18 wins")))

(deftest authored-shield-tag-disqualifies-rather-than-skipping-the-bonus
  (testing ":shield? false must remove the calculation entirely, not just omit the shield bonus"
    (is (= 15 ((ac-fn-for abilities :fighter :p-monk-) nil nil))
        "no shield: 10 + Dex(2) + Wis(3) = 15")
    (is (= 14 ((ac-fn-for abilities :fighter :p-monk-) nil shield))
        "with a shield it is disqualified, so 10 + Dex(2) + shield(2) = 14 — NOT 15")
    ;; FLIPPED 2026-09: was pinned at 15. The shield's +2 used to be added inside
    ;; ?armor-class-with-armor-base, so a calculation that beat the base lost it (15 vs the
    ;; with-shield base's 14, max picks 15). The shield is now an ?ac-bonus-fns entry, summed
    ;; onto the winner. Note the Monk assertion above is unchanged at 14: Monk never wrote
    ;; ?unarmored-with-shield-ac-bonus, so its base is 10 + Dex = 12 and the shield adds 2.
    (is (= 17 ((ac-fn-for abilities :fighter :p-barb-) nil shield))
        "10 + Dex(2) + Con(3) + shield(2) — the rules answer")))

(deftest authored-shield-required-is-expressible
  (testing ":shield? true = only while wielding a shield — the construct/golem shape"
    (is (= 12 ((ac-fn-for abilities :fighter :p-shonly-) nil nil))
        "no shield: the calculation does not apply, so plain 10 + Dex(2)")
    ;; FLIPPED 2026-09 with the shield move: 16 was the calculation alone, which used to
    ;; swallow the shield. The shield is a bonus now, so it stacks onto the winner.
    (is (= 18 ((ac-fn-for abilities :fighter :p-shonly-) nil shield))
        "shield held: the calculation (16) wins and the shield adds its 2")))

(deftest authored-abilities-sum
  (testing ":abilities adds every listed modifier (Barbarian takes Dex AND Con)"
    (is (= 15 ((ac-fn-for abilities :fighter :p-barb-) nil nil)) "10 + Dex(2) + Con(3)")))

(deftest authored-floor-applies-with-or-without-armor
  (testing "omitting :armor? means the calculation applies either way — a Barkskin-style floor"
    (is (= 16 ((ac-fn-for abilities :fighter :p-floor-) leather nil))
        "leather would be 13; the floor of 16 wins")
    (is (= 18 ((ac-fn-for abilities :fighter :p-floor-) plate nil))
        "plate's 18 beats the floor — it lifts, it does not cap")))

(deftest authored-bonuses-and-their-conditions
  (testing "{:ac-bonus N} sums onto the winner; :armor? gates it"
    (is (= 13 ((ac-fn-for abilities :fighter :p-bonus-) nil nil))
        "unconditional +1 on 10 + Dex(2)")
    (is (= 12 ((ac-fn-for abilities :fighter :p-abon-) nil nil))
        ":armor? true and no armor worn — the bonus does not apply")
    (is (= 19 ((ac-fn-for abilities :fighter :p-abon-) plate nil))
        ":armor? true and wearing plate — 18 + 1 = 19")))


;; ===========================================================================
;; SECTION 1e — MIGRATION PARITY SWEEP
;; ===========================================================================
;; Every mechanism being replaced, against its authored replacement, across every equipment
;; state, compared in ONE run. Any divergence is a migration hazard: it means deprecating the
;; old form would change a real character's AC. Divergences are printed together rather than
;; discovered one at a time.

(def contexts
  [["unarmored"        nil     nil]
   ["unarmored+shield" nil     shield]
   ["leather"          leather nil]
   ["leather+shield"   leather shield]
   ["scale"            scale-mail nil]
   ["plate"            plate   nil]
   ["plate+shield"     plate   shield]])

;; [label, classes using the OLD mechanism, classes using the AUTHORED replacement]
(def migration-pairs
  [["natural armor 13+Dex"        [:liz-]        [:p-natany-]]
   ["natural armor + magic ring"  [:liz- :ring-] [:p-natany- :ring-]]
   ["unarmored defense (Con)"     [:barbarian]   [:p-barb-]]
   ["unarmored defense (Wis, no shield)" [:monk] [:p-monk-]]])

(defn- sweep-divergences []
  (for [[label old new] migration-pairs
        [ctx armor shld] contexts
        :let [a ((apply ac-fn-for abilities old) armor shld)
              b ((apply ac-fn-for abilities new) armor shld)]
        :when (not= a b)]
    (format "  %-36s %-18s old=%-3s authored=%-3s" label ctx a b)))

(deftest bracers-no-shield-clause-holds-via-channel-omission
  (testing "Bracers of Defense: '+2 if wearing no armor AND using no shield'. It writes only
            ?unarmored-ac-bonus, never ?unarmored-with-shield-ac-bonus — the omission IS the
            no-shield clause. So the with-shield channel is load-bearing even though only
            Barbarian writes it; collapsing the two naively would make Bracers apply with a shield.

            Measured as the DELTA against the same character without the bracers. Absolute numbers
            cannot prove this: with a plain shield the bracers' +2 and the shield's +2 are the same
            number, so 14 with a shield is equally consistent with the bracers applying and the
            shield being dropped. A +1 shield (contributing 3) breaks that tie."
    (let [with-    (fn [a s] ((ac-fn-for abilities :fighter :bracers-) a s))
          without- (fn [a s] ((ac-fn-for abilities :fighter) a s))
          delta    (fn [a s] (- (with- a s) (without- a s)))]
      (is (= 12 (without- nil nil))         "control, unarmored: 10 + Dex(2)")
      (is (= 15 (without- nil magic-shield)) "control, +1 shield: 10 + Dex(2) + 3")
      (is (= 13 (without- leather nil))     "control, leather: 11 + Dex(2)")
      (is (= 2 (delta nil nil))          "unarmored, no shield: the bracers apply, +2")
      (is (= 0 (delta nil magic-shield)) "shield held: the bracers do NOT apply — 15, not 17")
      (is (= 0 (delta leather nil))      "armor worn: the bracers do NOT apply")
      (is (= 0 (delta plate magic-shield)) "neither clause satisfied"))))

(deftest diagnostic-tables
  (testing "DIAGNOSTIC (no assertions): numbers for two open questions"
    (println "\n[Q1] Bracers of Defense — does the no-shield clause actually hold?")
    (doseq [[ctx a sh] contexts]
      (println (format "     %-18s %s" ctx ((ac-fn-for abilities :fighter :bracers-) a sh))))
    (println "\n[Q2] natural armor: :armor? false  vs  no :armor? tag  vs  the shipped prop")
    (println (format "     %-18s %-10s %-10s %s" "context" "armor?false" "no-tag" "prop"))
    (doseq [[ctx a sh] contexts]
      (println (format "     %-18s %-10s %-10s %s" ctx
                       ((ac-fn-for abilities :fighter :p-nat-) a sh)
                       ((ac-fn-for abilities :fighter :p-natany-) a sh)
                       ((ac-fn-for abilities :fighter :liz-) a sh))))
    (println "")
    (is true)))

(deftest tortle-ac-prop-characterization
  (testing "CHARACTERIZATION of :tortle-ac as shipped — pinning what IS, before the shim.
            Unlike :lizardfolk-ac it does not take a max against ?armor-class-with-armor; it
            REPLACES the function with (+ 17 shield), so worn armor cannot beat it and Dex never
            applies. That matches the rules ('base AC 17, your Dex modifier does not affect this
            number') but it also means the ?natural-ac-bonus 7 it writes alongside is inert here —
            the replacement never consults ?base-armor-class."
    (doseq [[ctx armor shld expected]
            [["unarmored"        nil        nil    17]
             ["unarmored+shield" nil        shield 19]
             ["leather"          leather    nil    17]   ; armor is ignored entirely
             ["leather+shield"   leather    shield 19]
             ["plate"            plate      nil    17]   ; even plate's 18 cannot win
             ["plate+shield"     plate      shield 19]]]
      (is (= expected ((ac-fn-for abilities :fighter :tor-) armor shld))
          (str ":tortle-ac / " ctx)))))

(deftest tortle-decomposes-into-a-calculation-and-a-restriction
  (testing ":tortle-ac welded two separable things together: a flat natural AC, and \"worn armor
            gives no AC\". Each half must stand alone, and composing them must reproduce the welded
            prop exactly — that equivalence is what makes the split safe.

            The second half is the AC consequence of a tortle's shell, not the rules restriction:
            nothing here prevents equipping armor, and armor's other effects (stealth
            disadvantage) still apply. The old ceiling had the same gap; the split does not
            widen it."
    (testing "the flat AC alone — armor is still allowed, so good armor can beat it"
      (is (= 17 ((ac-fn-for abilities :fighter :p-flat17-) nil nil))   "flat 17, Dex ignored")
      (is (= 17 ((ac-fn-for abilities :fighter :p-flat17-) leather nil)) "leather's 13 loses")
      (is (= 18 ((ac-fn-for abilities :fighter :p-flat17-) plate nil))
          "plate's 18 WINS — the whole point of not baking in a ceiling"))
    (testing "suppression alone — no AC of its own, worn armor just stops counting"
      (is (= 12 ((ac-fn-for abilities :fighter :p-noarm-) nil nil))  "10 + Dex(2)")
      (is (= 12 ((ac-fn-for abilities :fighter :p-noarm-) plate nil)) "plate contributes nothing")
      (is (= 14 ((ac-fn-for abilities :fighter :p-noarm-) plate shield))
          "the shield still counts — it is not armor"))
    (testing "composed, they equal the shipped :tortle-ac in every equipment state"
      (doseq [[ctx armor shld] contexts]
        (is (= ((ac-fn-for abilities :fighter :tor-) armor shld)
               ((ac-fn-for abilities :fighter :p-flat17- :p-noarm-) armor shld))
            (str "composed vs welded / " ctx))))))

(deftest bracers-plus-natural-armor-the-overloaded-channel
  (testing "REGRESSION GUARD. ?unarmored-ac-bonus carried two different meanings: Barbarian and
            Monk write an ABILITY MODIFIER that competes as a calculation (subject to the tie-break
            against ?natural-ac-bonus), while Bracers of Defense writes a FLAT +2 that ought to be
            a bonus stacking on whatever wins. Both land in the same scalar and go through the same
            tie-break, so pinning what that actually does to a natural-armor character carrying
            bracers — before the channel is trimmed."
    (let [with-    ((ac-fn-for abilities :fighter :nat-armor- :bracers-) nil nil)
          without- ((ac-fn-for abilities :fighter :nat-armor-) nil nil)]
      (println (format "\n[OVERLOAD] natural armor 3, unarmored: %d without bracers, %d with (delta %d)"
                       without- with- (- with- without-)))
      (is (= 15 without-) "natural armor: 10 + Dex(2) + 3")
      (is (= 17 with-)
          "FIXED. natural armor 15 + a flat +2 that stacks on the winner.

           A REAL SHIPPED DEFECT, not one this branch introduced: origin/integration returns 15
           here too — verified by running this test against that branch's template_base. The
           tie-break in ?unarmored-armor-class zeroes the whole ?unarmored-ac-bonus channel when
           natural armor wins, and Bracers' flat +2 was sitting in that channel. Correct for its
           target case (Barbarian + Draconic stacked to 18 when RAW is 15), wrong for flat bonuses.

           mod5e/unarmored-ac-bonus now emits an ?ac-bonus-fns entry stating both clauses, which is
           also the first channel the trim retires."))))

(deftest effective-dex-cap-combines-type-armor-and-features
  (testing "the cap is the MORE PERMISSIVE of the armor type's and the armor's own, so a feature
            that raises the type cap is never undone by the item's printed number, and a custom
            item that allows more than its type is never held back by the type"
    ;; dex16 (+3) throughout, so a cap of 3 is distinguishable from a cap of 2. At Dex 14 the
    ;; feat's 3 and the armor's 2 both clamp to 2 and the test would prove nothing.
    (let [plain (ac-fn-for dex16-abilities :fighter)
          mam   (ac-fn-for dex16-abilities :fighter :mam-)]      ; Medium Armor Master: medium cap 3
      (testing "shipped armor is unchanged — every medium says :max-dex-mod 2, every heavy 0"
        (is (= 16 (plain scale-mail nil)) "scale 14 + min(2, Dex 3) = 16 — capped at the armor's 2")
        (is (= 18 (plain plate nil))      "plate 18 + 0")
        (is (= 14 (plain leather nil))    "light is uncapped: 11 + Dex 3"))
      (testing "Medium Armor Master raises the medium cap and the armor's printed 2 does not veto it"
        (is (= 17 (mam scale-mail nil))
            "scale 14 + min(max(MAM 3, armor's 2), Dex 3) = 17. Reading :max-dex-mod alone would
             cap at 2 and silently disable the feat."))
      (testing "custom armor may allow MORE than its type does"
        (is (= 18 (plain custom-heavy nil))
            "heavy's default is 0, the item declares 2: 16 + min(2, Dex 3) = 18"))
      (testing "and a feature does not leak across types"
        (is (= 18 (mam plate nil)) "MAM raises MEDIUM only — plate is still 18 + 0")))))

(deftest migration-parity-sweep
  (testing "every old mechanism vs its authored replacement, across every equipment state"
    (let [divergences (sweep-divergences)]
      (println (format "\n[PARITY SWEEP] %d divergence(s) across %d pairs x %d contexts:"
                       (count divergences) (count migration-pairs) (count contexts)))
      (doseq [d divergences] (println d))
      (println "")
      ;; 7 -> 2 -> 0 (2026-09). Moving the shield and ?magical-ac-bonus into ?ac-bonus-fns closed
      ;; 5; compiling :lizardfolk-ac down to the universal :ac shape closed the last 2. Every old
      ;; mechanism now returns exactly what its authored replacement returns, in every equipment
      ;; state, so deprecating the old forms cannot change a saved character's AC.
      ;; This must STAY 0. A non-zero count is a regression, not a number to update.
      (is (= 0 (count divergences))
          "every old mechanism must equal its authored replacement in every equipment state"))))

;; ===========================================================================
;; SECTION 2 — FIXED: natural-armor + unarmored-defense no longer stack
;; ===========================================================================
;; History (kept so the fix is legible): ?base-armor-class (template_base.cljc:38-41) only
;; ZEROED natural when unarmored won the tie-break; it never zeroed unarmored when natural
;; won, because unarmored-ac-bonus was added UNCONDITIONALLY in ?unarmored-armor-class.
;; So natural >= unarmored STACKED. Confirmed by build: Barbarian (Con 3) + NatArmor
;; (natural 3) came out 18; RAW is max(13+Dex, 10+Dex+Con) = 15.
;;
;; FIX (fix/ac-unarmored-natural-stacking, off integration): the unarmored addition is now
;; conditional too — the two `if`s are the two halves of one symmetric max. Cherry-picked
;; here from that neutral branch; must also land on refactor/content-extensibility and
;; contrib/summer-fixes (Summer Patch). This assertion is the visible 18 -> 15 diff.

(deftest natural-plus-unarmored-no-stacking
  (testing "natural-armor + unarmored-defense take the BETTER, never both (18 -> 15 after the fix)"
    (let [ac (unarmored-ac :barbarian :nat-armor-)]
      (println (format "\n[AC-FIX PROBE] Barbarian + NatArmor unarmored AC = %s  (RAW-correct = 15)\n" ac))
      (is (= 15 ac)
          "FIXED: natural(3) and unarmored(Con 3) no longer stack; max(13+Dex, 10+Dex+Con) = 15"))))

;; ---------------------------------------------------------------------------
;; Two natural-armor sources do NOT stack. CORRECTION: an earlier version of
;; this test claimed they stacked to 18 — that was a FIXTURE ARTIFACT. The synthetic classes
;; used the cum-sum constructor (mod5e/natural-ac-bonus), which sums; but ALL real content sets
;; ?natural-ac-bonus with mod/modifier — a SET (es/modifier replaces, does not accumulate). With
;; the fixture now matching real content, two SET sources last-win (3) -> 10+Dex+3 = 15. There is
;; NO natural-stacking bug in integration; nothing to patch. (Residual, barely reachable: two
;; natural sources of DIFFERENT value last-win rather than max — no built-in combo produces it,
;; and the formulas-in-max refactor makes it correct for free. Not a shipping issue.)
;; ---------------------------------------------------------------------------
(deftest two-natural-sources-do-not-stack
  (testing "two natural-armor sources via the REAL set mechanism don't stack"
    (let [ac (unarmored-ac :nat-armor- :nat-armor-b-)]
      (println (format "\n[A3] two natural(3) SET sources unarmored AC = %s  (no stacking)\n" ac))
      (is (= 15 ac)
          "two SET natural(3) sources -> 3 (last-wins), 10+Dex(2)+3 = 15; they do NOT sum to 18"))))

;; ===========================================================================
;; SECTION 3 — PROPOSED reconciler (orcpub.dnd.e5.armor-class/reconcile-ac)
;; ===========================================================================
;; Unit tests on the real replacement functions (not a stand-in copy): a formula returns its
;; 'AC = ...' value, or 0 when it doesn't apply; the best formula wins (max); bonuses are summed
;; onto the winner. These use plain functions, so they do NOT show the app behaving correctly —
;; nothing calls armor-class yet. SECTION 1 is what covers app behaviour.
;;
;; Formula/bonus stand-ins use Dex 14 (+2), so "10 + Dex" = 12, "16 + Dex" = 18, etc.

(deftest reconcile-formulas-take-the-max
  (testing "competing formulas reconcile by max — best rises, nothing stacks"
    (let [base   (fn [_ _] 12)     ; SRD unarmored 10 + Dex(2)
          hb-nat (fn [_ _] 18)]    ; homebrew natural armor 16 + Dex(2)
      (is (= 18 (ac/reconcile-ac {:other-formulas [base hb-nat]} nil nil))
          "homebrew formula beats the base and wins")
      (is (= 12 (ac/reconcile-ac {:other-formulas [base (fn [_ _] 0)]} nil nil))
          "a non-applicable formula (returns 0) never drags the winner down"))))

(deftest reconcile-bonuses-reach-the-winning-formula   ; a bonus must not be lost when a formula beats the base
  (testing "bonuses are summed ONTO the winning formula — not trapped in the base"
    (let [base   (fn [_ _] 12)
          hb-nat (fn [_ _] 18)     ; wins the max
          ring   (fn [_ _] 1)      ; Ring of Protection — applies whatever formula wins
          shield (fn [_ _] 2)]     ; shield — applies whatever formula wins
      (is (= 19 (ac/reconcile-ac {:other-formulas [base hb-nat] :bonuses [ring]} nil nil))
          "ring reaches the WINNING homebrew formula (old engine dropped it: buried in base)")
      (is (= 21 (ac/reconcile-ac {:other-formulas [base hb-nat] :bonuses [ring shield]} nil nil))
          "several bonuses all land on the winner"))))

(deftest reconcile-floor-is-a-constant-formula        ; e.g. Barkskin
  (testing "a floor/set-AC is just a constant formula — max gives 'at least N' for free"
    (let [worn  (fn [_ _] 13)      ; light armor 11 + Dex(2)
          floor (fn [_ _] 16)]     ; Barkskin: AC can't be less than 16
      (is (= 16 (ac/reconcile-ac {:other-formulas [worn floor]} nil nil))
          "floored up to 16 when the real AC is lower")
      (is (= 18 (ac/reconcile-ac {:other-formulas [(fn [_ _] 18) floor]} nil nil))
          "and NOT capped: 18 > 16 stays 18"))))

(deftest reconcile-unarmored-formula-excludes-when-armored
  (testing "a formula opts OUT by returning 0 for a context it doesn't apply to"
    (let [armored   (fn [armor _] (if armor 16 0))    ; e.g. scale mail 14 + capped Dex 2
          unarmored (fn [armor _] (if armor 0 15))]   ; 10 + Dex + Con, only while no armor
      (is (= 16 (ac/reconcile-ac {:other-formulas [armored unarmored]} :scale nil))
          "armored context -> armored formula wins, unarmored excludes itself")
      (is (= 15 (ac/reconcile-ac {:other-formulas [armored unarmored]} nil nil))
          "no-armor context -> unarmored formula wins, armored excludes itself"))))

(deftest reconcile-shield-permission-is-self-exclusion
  (testing "per-formula shield permission = whether the formula returns 0 when a shield is held"
    (let [base   (fn [_ _] 12)                          ; plain 10 + Dex(2)
          barb   (fn [_ _] 15)                          ; shield-OK: value regardless of shield
          monk   (fn [_ shield] (if shield 0 15))       ; shield-FORBIDDEN: 0 when a shield is held
          shield (fn [_ s] (if s 2 0))]                 ; the shield bonus
      (is (= 17 (ac/reconcile-ac {:other-formulas [base barb] :bonuses [shield]} nil :s))
          "Barbarian keeps its formula with a shield: 15 + 2 = 17")
      (is (= 14 (ac/reconcile-ac {:other-formulas [base monk] :bonuses [shield]} nil :s))
          "Monk self-excludes with a shield -> base(12) wins + shield(2) = 14 (loses Wis, per RAW)")
      (is (= 15 (ac/reconcile-ac {:other-formulas [base monk] :bonuses [shield]} nil nil))
          "no shield -> Monk formula(15) wins"))))

;; ===========================================================================
;; SECTION 4 — BACKWARD-COMPAT SHIMS (deprecated public homebrew vars)
;; ===========================================================================
;; If :lizardfolk-ac / :tortle-ac / a ?-channel is deprecated in favor of a new form,
;; an assertion here proves the OLD public form still yields the correct AC via the shim.
;; (empty until a public var is actually deprecated)
