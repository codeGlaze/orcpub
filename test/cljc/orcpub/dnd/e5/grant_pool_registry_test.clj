(ns orcpub.dnd.e5.grant-pool-registry-test
  "The pool registry and the grant hook, against the decided design
   (docs/kb/content-extensibility-direction.md, \"The spine\" + \"Maintainability\").

   The gating test is `registering-a-pool-is-one-entry`. The direction doc makes it the pass/fail
   criterion for the whole retooling:

     \"exposing a SECOND pool in a builder must be a ~1-line registration — shown in a commit. If it
      isn't trivially cheap, the retooling failed its own purpose; STOP and reassess.\"

   Everything else here pins the two disciplines that keep it from rotting: `grant` stays a thin
   compiler (no pool-kind branches), and each pool's own definition absorbs its own irregularity.
   JVM/clojure.test."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.template :as t]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.grant-pools :as gp]))

(def ^:private no-plugins [])

(deftest registering-a-pool-is-one-entry
  (testing "every registered pool assembles with no code outside its own entry"
    (let [assembled (gp/assemble no-plugins)]
      (is (= (set (keys gp/pools)) (set (keys assembled)))
          "assemble must be uniform — it never inspects or skips a pool")
      (doseq [[k {:keys [name options]}] assembled]
        (is (string? name) (str k " has no display name"))
        (is (seq options) (str k " assembled to no options"))
        (is (every? ::t/key options)
            (str k " has options with no ::t/key — :key and :filter address entries by it")))))
  (testing "and every registered pool differs ONLY in its own :options-fn"
    (is (= #{:languages :fighting-styles :skills :skill-expertise :tools :skills-or-tools
             :weapons :armor :damage-resistances :damage-immunities}
           (set (keys gp/pools))))
    (doseq [[k d] gp/pools]
      (is (fn? (:options-fn d)) (str k " must own its shape as a fn, not describe it with keys"))
      (is (set? (:offerable-by d)) (str k " is missing its scoping metadata")))))

(deftest each-pool-absorbs-its-own-irregularity
  (testing "the pools disagree about what a built-in is; nothing outside them can tell"
    (let [a (gp/assemble no-plugins)]
      ;; languages: built-ins are RAW data run through a constructor
      (is (contains? (set (map ::t/key (get-in a [:languages :options]))) :deep-speech))
      ;; fighting styles: built-ins are ALREADY option-cfgs, constructor applies to homebrew only
      (is (contains? (set (map ::t/name (get-in a [:fighting-styles :options]))) "Archery"))
      ;; skills: closed — no homebrew half at all, and it ignores plugin-vals.
      ;; Compared by ::t/key: an option carries closures (prereq-fn, modifier fns) that are fresh
      ;; objects on every call, so the cfgs themselves never compare equal.
      (is (= (map ::t/key (get-in a [:skills :options]))
             (map ::t/key (get-in (gp/assemble [{:orcpub.dnd.e5/spells {:x {}}}])
                                  [:skills :options])))
          "a closed pool must not vary with plugin content"))))

(defn- sel [grant]
  (opt5e/grant-selection grant (gp/assemble no-plugins)))

(deftest decided-vocabulary
  (testing ":pool / :count is the canonical spelling"
    (let [s (sel {:pool :languages :count 2})]
      (is (= 2 (::t/min s)))
      (is (= 2 (::t/max s)))))
  (testing ":key is FIXED and never a selection — grant-selection is the CHOICE compiler only;
            fixed grants compile to modifiers in compile-grants (fixed-grant-emits-modifiers-not-a-pick)"
    (is (nil? (sel {:pool :languages :key :elvish}))))
  (testing ":filter offers a creator-chosen subset"
    (is (= #{:elvish :dwarvish}
           (set (map ::t/key (::t/options (sel {:pool :languages :count 1
                                                :filter #{:elvish :dwarvish}}))))))))

(deftest one-spelling-only
  (testing ":from / :choose are NOT accepted. Both are already taken by the starting-equipment
            vocabulary for other things, and an alias for a shape introduced on this branch would be
            tech debt from birth — a grant must say :pool."
    (is (nil? (sel {:from :fighting-styles :choose 2})))))

(deftest filtering-is-graceful-never-an-error
  (testing "a filter naming nothing present yields an empty offer, not a throw (the direction doc's
            'absent metadata -> simply isn't offered' rule)"
    (is (empty? (::t/options (sel {:pool :languages :filter #{:klingon}})))))
  (testing "an unregistered pool yields no selection at all"
    (is (nil? (sel {:pool :not-a-pool :count 1})))))

(deftest count-defaults-to-one
  (is (= 1 (::t/max (sel {:pool :skills})))))

(deftest pools-carry-their-tags
  (testing "D30: a grant carries its pool's selection tags, so it lands on the same TAB as the bespoke
            choice it replaces (the character builder routes by tag)"
    (is (= #{:grant :languages :profs :language-profs} (::t/tags (sel {:pool :languages :count 1})))
        "a granted language must carry :profs/:language-profs like language-selection-aux")
    (is (= #{:grant :skills :profs :skill-profs} (::t/tags (sel {:pool :skills :count 1}))))
    (is (= #{:grant :fighting-styles} (::t/tags (sel {:pool :fighting-styles :count 1})))
        "a pool that declares no tags keeps only the generic pair")))


(deftest monster-is-offered-nothing
  (testing "a stat block grants nothing; every option-* widget it shares with the character silos
            compiles to display text there (builder-disposition-audit.md)"
    (is (empty? (gp/offerable-pools :monster)))
    (is (= 10 (count (gp/offerable-pools :feat))))))

(deftest damage-type-is-one-vocabulary
  (testing "options.cljc's damage-types is the damage_types.cljc def, not a second copy"
    (is (identical? opt5e/damage-types orcpub.dnd.e5.damage-types/damage-types)))
  (testing "and the two damage pools are spokes over it with different primitives"
    (let [a (gp/assemble [])
          res (first (filter #(= :cold (::t/key %)) (get-in a [:damage-resistances :options])))
          imm (first (filter #(= :cold (::t/key %)) (get-in a [:damage-immunities  :options])))]
      (is (some? res)) (is (some? imm))
      (is (not= (map :orcpub.modifiers/key (::t/modifiers res))
                (map :orcpub.modifiers/key (::t/modifiers imm)))))))

(deftest fixed-grant-emits-modifiers-not-a-pick
  (testing "D4 / direction doc line 72: {:pool p :key k} is the entry's modifiers, no selection"
    (let [{:keys [modifiers selections]} (opt5e/compile-grants [{:pool :skills :key :athletics}]
                                                               (gp/assemble []))]
      (is (empty? selections) "a fixed grant must not create a dropdown-of-one")
      (is (= 1 (count modifiers)))
      (is (= :skill-profs (:orcpub.modifiers/key (first modifiers))))))
  (testing "a choice grant is a selection, and both halves merge in one pass"
    (let [{:keys [modifiers selections]} (opt5e/compile-grants [{:pool :skills :key :athletics}
                                                               {:pool :languages :count 2}
                                                               {:pool :nope :key :x}
                                                               {:pool :skills :key :not-a-skill}]
                                                               (gp/assemble []))]
      (is (= 1 (count modifiers)))
      (is (= ["Language"] (map ::t/name selections)))
      (is (= 2 (::t/max (first selections)))))))
