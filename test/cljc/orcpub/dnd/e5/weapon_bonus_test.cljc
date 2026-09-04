(ns orcpub.dnd.e5.weapon-bonus-test
  "The conditional weapon-bonus vocabulary: weapons/matches? (the three-state tag predicate) and
   the :attack-bonus / :damage-bonus props built on it.

   Reproduces two published fighting styles as authored data — Archery and Thrown Weapon Fighting —
   which is the point of the vocabulary: the same props work in a race, a subclass or a feat."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.dnd.e5.builder-fields :as bf]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.template-base :as tb]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.modifiers :as mods]
            [orcpub.entity-spec :as es]))

(def longbow  {::weapons5e/melee? false})
(def handaxe  {::weapons5e/melee? true  ::weapons5e/thrown true ::weapons5e/light? true})
(def longsword{::weapons5e/melee? true})
(def greatsword {::weapons5e/melee? true ::weapons5e/two-handed? true})
(def rapier   {::weapons5e/melee? true ::weapons5e/finesse? true})

(deftest matches-is-three-state
  (testing "true requires the property"
    (is (weapons5e/matches? {:melee? true} longsword))
    (is (not (weapons5e/matches? {:melee? true} longbow))))
  (testing "false FORBIDS it — this is how 'ranged' is said, since the data models only ::melee?"
    (is (weapons5e/matches? {:melee? false} longbow))
    (is (not (weapons5e/matches? {:melee? false} longsword))))
  (testing "absent means either way"
    (is (weapons5e/matches? {} longbow))
    (is (weapons5e/matches? {} longsword)))
  (testing "a missing flag reads as false, not as absent"
    (is (weapons5e/matches? {:thrown? false} longsword) "longsword has no ::thrown key at all")
    (is (not (weapons5e/matches? {:thrown? true} longsword))))
  (testing "tags AND together"
    (is (weapons5e/matches? {:melee? true :thrown? true :light? true} handaxe))
    (is (not (weapons5e/matches? {:melee? true :thrown? true :two-handed? true} handaxe))))
  (testing "an unknown tag is ignored rather than failing the match, so content authored against a
            newer build still applies its bonus here"
    (is (weapons5e/matches? {:melee? true :sentient? true} longsword))))

;; ── the props, as published fighting styles ──────────────────────────────────────────────────
;; Measured through the real engine as a DELTA: build the weapon's modifier with and without the
;; prop applied and subtract. That isolates the bonus without having to stand up proficiency,
;; ability scores and the rest, and it exercises the actual ?attack-modifier-fns /
;; ?damage-bonus-fns channels rather than a stand-in.
(def ^:private abilities
  (mods/modifier ?abilities {:orcpub.dnd.e5.character/str 10 :orcpub.dnd.e5.character/dex 10}))

(defn- entity-with [prop-key spec]
  (mods/apply-modifiers tb/template-base
                        (into [abilities] (opt5e/make-feat-modifiers prop-key spec :test))))

(defn- bonus-for [prop-key spec weapon]
  (let [attack? (= prop-key :attack-bonus)
        read    (fn [e] (if attack?
                          ((es/entity-val e :weapon-attack-modifier) weapon false)
                          ((es/entity-val e :weapon-damage-modifier) weapon false)))]
    (- (read (entity-with prop-key spec))
       (read (mods/apply-modifiers tb/template-base [abilities])))))

(deftest archery-is-authorable
  (testing "Archery: +2 to attack rolls with ranged weapons"
    (let [archery {:bonus 2 :melee? false}]
      (is (= 2 (bonus-for :attack-bonus archery longbow)))
      (is (= 0 (bonus-for :attack-bonus archery longsword)) "melee weapons get nothing"))))

(deftest thrown-weapon-fighting-is-authorable
  (testing "Thrown Weapon Fighting: +2 damage with thrown weapons"
    (let [thrown {:bonus 2 :thrown? true}]
      (is (= 2 (bonus-for :damage-bonus thrown handaxe)))
      (is (= 0 (bonus-for :damage-bonus thrown longsword)))
      (is (= 0 (bonus-for :damage-bonus thrown longbow)) "ranged but not thrown"))))

(deftest untagged-bonus-applies-to-everything
  (testing "no tags = every weapon, which is what a plain '+1 to damage' feature wants"
    (doseq [w [longbow handaxe longsword greatsword rapier]]
      (is (= 1 (bonus-for :damage-bonus {:bonus 1} w))))))

(deftest a-bonus-with-no-number-produces-no-modifier
  (testing "tags without a bonus are meaningless and must not emit a modifier"
    (is (nil? (opt5e/make-feat-modifiers :attack-bonus {:melee? true} :test)))))

(deftest field-paths-match-what-the-compiler-reads
  (testing "the same drift risk as the AC fields: a field writing a path the compiler ignores looks
            right, saves fine and does nothing"
    (doseq [[prop fields] [[:attack-bonus bf/attack-bonus-fields]
                           [:damage-bonus bf/damage-bonus-fields]]]
      (doseq [{:keys [key]} fields]
        (is (= [:props prop] (vec (take 2 key))))
        (let [k (last key)]
          (is (or (= :bonus k) (contains? weapons5e/tag->flag k))
              (str k " must be :bonus or a tag weapons/matches? understands")))))))
