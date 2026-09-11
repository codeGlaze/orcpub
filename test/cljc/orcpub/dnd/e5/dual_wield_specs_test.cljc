(ns orcpub.dnd.e5.dual-wield-specs-test
  "What counts as dual-wieldable is DATA — a vector of tag specs, any of which qualifies — rather
   than a predicate fn. These pin the three things that change gives us: it composes, it is
   authorable from :props, and a divergence from the rules text is visible.

   Rules text quoted from the feats themselves, for comparison against what we implement.
   JVM/clojure.test."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.modifiers :as mods]
            [orcpub.entity-spec :as es]
            [orcpub.dnd.e5.template-base :as tb]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.dnd.e5.options :as opt5e]))

(def ^:private w (into {} (map (juxt :key identity)) weapons5e/weapons))
(def ^:private shortsword    (w :shortsword))       ; light melee
(def ^:private greatsword    (w :greatsword))       ; two-handed melee
(def ^:private longsword     (w :longsword))        ; one-handed melee, not light
(def ^:private hand-crossbow (w :crossbow-hand))    ; light RANGED — the Crossbow Expert case

(defn- specs-for [modifiers]
  (es/entity-val (mods/apply-modifiers tb/template-base (vec modifiers))
                 :dual-wield-weapon-specs))

(defn- wieldable? [modifiers weapon]
  (weapons5e/matches-any? (specs-for modifiers) weapon))

(deftest the-base-rule-is-light-melee
  (testing "2014: \"a light melee weapon that you're holding in one hand\""
    (is (wieldable? [] shortsword))
    (is (not (wieldable? [] longsword))     "one-handed but not light")
    (is (not (wieldable? [] greatsword))    "two-handed")
    (is (not (wieldable? [] hand-crossbow)) "light, but ranged")))

(deftest the-feat-adds-a-way-rather-than-replacing-the-rule
  (testing "Dual Wielder: \"use two-weapon fighting even when the one-handed melee weapons you are
            wielding aren't light\" — the light-melee rule must SURVIVE alongside it"
    (let [feat [opt5e/dual-wield-weapon-mod]]
      (is (wieldable? feat longsword)   "the feat's own case")
      (is (wieldable? feat shortsword)  "REGRESSION GUARD: the base rule still applies. A predicate
                                         override replaced it; a spec vector adds to it.")
      (is (not (wieldable? feat greatsword)) "still two-handed")))
  (testing "and it keeps the MELEE half of its own text — the predecessor was (not two-handed?),
            which silently let a hand crossbow qualify off a melee feat"
    (is (not (wieldable? [opt5e/dual-wield-weapon-mod] hand-crossbow)))))

(deftest two-sources-compose
  (testing "Dual Wielder and Crossbow Expert both apply. With a predicate the second would have
            silently replaced the first; each adds one way to qualify."
    (let [both [opt5e/dual-wield-weapon-mod
                (mods/vec-mod ?dual-wield-weapon-specs {:light? true :ranged? true})]]
      (is (wieldable? both longsword)     "from the feat")
      (is (wieldable? both hand-crossbow) "from Crossbow Expert")
      (is (wieldable? both shortsword)    "from the base rule"))))

(deftest homebrew-can-author-the-ridiculous
  (testing "a homebrew feat granting two two-handed weapons — one greatsword in each hand — is one
            authored :props entry, no code. The engine gates the off-hand dropdown on this spec and
            nothing else, so the whole capability is the data."
    (let [titan (opt5e/plugin-modifiers {:dual-wield-weapons {:two-handed? true :melee? true}} :titan)]
      (is (wieldable? titan greatsword)     "the point")
      (is (wieldable? titan shortsword)     "and the base rule is untouched")
      (is (not (wieldable? titan hand-crossbow)) "melee only, as authored"))))

(deftest every-tag-in-a-shipped-spec-is-a-real-tag
  (testing "matches? ends in :else true — an unrecognised tag is IGNORED, which is right for
            forward compatibility and fails OPEN. A typo'd tag would silently qualify every weapon,
            so every tag we ship must be one the vocabulary knows."
    (let [known (set (keys weapons5e/tag->flag))]
      (doseq [modifiers [[] [opt5e/dual-wield-weapon-mod]]
              spec      (specs-for modifiers)
              tag       (keys spec)]
        (is (contains? known tag)
            (str tag " is not in weapons/tag->flag — it would be ignored, matching everything"))))))
