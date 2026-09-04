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
                         weapons5e/weapons-map lizardfolk-prop-class)]
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
;; custom "weird material": heavy AC that declares its own Dex allowance
(def custom-heavy {:base-ac 16 :type :heavy :max-dex-mod 2
                   :orcpub.dnd.e5.magic-items/magical-ac-bonus 0})
(def shield {:type :shield})

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
      (is (= 16 custom)
          "CURRENT: the armor's own :max-dex-mod is IGNORED — heavy is capped at 0 by :type, so
           16 + 0 = 16. Honouring the field would make this 18; that flip is the visible diff.")
      (is (= 18 heavy) "plain plate: 18 + 0 Dex"))))

(deftest homebrew-natural-armor-prop-on-a-barbarian
  (testing "the live :lizardfolk-ac prop (13 + Dex) on a Barbarian — the reachable stacking shape"
    (let [ac ((ac-fn-for abilities :barbarian :liz-) nil nil)]
      (is (= 15 ac)
          "the reachable homebrew shape: natural 13+Dex vs 10+Dex+Con, take the better = 15"))))

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
