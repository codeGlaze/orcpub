(ns orcpub.dnd.e5.ac-reconciliation-test
  "SINGLE SOURCE OF TRUTH for AC reconciliation across the refactor. Structure:

   SECTION 1 — BASELINE: fixtures + the CURRENT behavior, pinned. Characterization
     style: pin what the code ACTUALLY does today, including any bug (pin what IS,
     not what SHOULD be), so a deliberate fix shows up as a visible diff.
   SECTION 2 — SUSPECTED LATENT BUG: natural-armor + unarmored-defense stacking.
   SECTION 3 — PROPOSED efficient rewrite: assertions added AS DEVISED. Each must
     reproduce SECTION 1 exactly, OR flip a pinned bug on purpose (with the diff called out).
   SECTION 4 — BACKWARD-COMPAT SHIMS: if a public-facing (homebrew) var/key is
     deprecated, an assertion here proves the OLD form still produces the right AC.

   JVM/clojure.test so it runs under the enforced `lein test` gate.

   Original reconciliation code being pinned (template_base.cljc:35-88):
     ?base-armor-class = 10 + Dex
                         + (if (> ?unarmored-ac-bonus ?natural-ac-bonus) 0 ?natural-ac-bonus) ; PAIRWISE tie-break
                         + ?magical-ac-bonus
     ?unarmored-armor-class = ?base-armor-class + ?unarmored-ac-bonus + ?ac-bonus
     ?armor-class-with-armor = (apply max base + each ?ac-fn) + (sum each ?ac-bonus-fn)
   Public-facing homebrew AC vars (candidates for deprecate-with-shim):
     :lizardfolk-ac  :tortle-ac  :two-weapon-ac-1  :medium-armor-max-dex-3  (options.cljc make-feat-modifiers)
     and the ?-channels ?natural-ac-bonus / ?unarmored-ac-bonus / ?armored-ac-bonus / ?ac-bonus-fns / ?ac-fns."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.classes :as classes5e]
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
(def natural-armor-class-full
  {:name "NatArmor"
   :key :nat-armor-
   :hit-die 8
   :ability-increase-levels [4 8 12 16 19]
   :subclass-title "Origin"
   :subclass-level 3
   :subclasses []
   :profs {}
   :modifiers [(mod5e/natural-ac-bonus 3)]})

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
                         weapons5e/weapons-map natural-armor-class-full)]
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

;; ===========================================================================
;; SECTION 3 — PROPOSED efficient rewrite (assertions added AS DEVISED)
;; ===========================================================================
;; When the reconciler is rewritten, its assertions go here. Rules:
;;   * every SECTION 1 number must reproduce exactly (no silent regression), and
;;   * SECTION 2 should FLIP to 15 on purpose — that flip IS the bug fix, called out here.
;; (empty until the rewrite lands)

;; ===========================================================================
;; SECTION 4 — BACKWARD-COMPAT SHIMS (deprecated public homebrew vars)
;; ===========================================================================
;; If :lizardfolk-ac / :tortle-ac / a ?-channel is deprecated in favor of a new form,
;; an assertion here proves the OLD public form still yields the correct AC via the shim.
;; (empty until a public var is actually deprecated)
