(ns orcpub.dnd.e5.fighting-style-class-eligibility-test
  "The other half of the fighting-style work: a homebrew style has to be pickable by the CLASS
   that has the feature, not only grantable by a feat. Pins the divvying rule from
   `fighting-style-authoring.md` — `:classes` names the eligible classes, absent means all —
   and the two things that must NOT change while it does: the built-in per-class lists, and the
   `:ref` the character stores its pick under.

   The companion `fighting_style_class_characterization_test` pins the no-homebrew case."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.template :as t]
            [orcpub.dnd.e5.options :as opt5e]))

(defn- offered-keys [sel] (set (map ::t/key (::t/options sel))))

(def paladin-set #{:defense :dueling :great-weapon-fighting :protection})
(def ranger-set  #{:archery :defense :dueling :two-weapon-fighting})

;; raw orcbrew entries — what the builder saves and an import restores
(def open-style    {:name "Bulwark"  :key :bulwark  :description "Stand fast."
                    :props {:ac-bonus {:bonus 1}}})
(def paladin-only  {:name "Oathkeep" :key :oathkeep :classes #{:paladin}})
(def two-classes   {:name "Mariner"  :key :mariner  :classes #{:fighter :ranger}})
(def entries [open-style paladin-only two-classes])

(deftest a-style-with-no-classes-is-offered-to-every-fighting-style-class
  (doseq [[cls restrictions] [[:fighter nil] [:paladin paladin-set] [:ranger ranger-set]]]
    (is (contains? (offered-keys (opt5e/fighting-style-selection cls restrictions entries))
                   :bulwark)
        (str "an unrestricted homebrew style must reach " cls))))

(deftest a-style-that-names-classes-is-offered-to-exactly-those
  (let [offered (fn [cls restrictions]
                  (offered-keys (opt5e/fighting-style-selection cls restrictions entries)))]
    (is (contains? (offered :paladin paladin-set) :oathkeep))
    (is (not (contains? (offered :fighter nil) :oathkeep))
        "Fighter is not in :classes, so Oathkeep is not on its list")
    (is (not (contains? (offered :ranger ranger-set) :oathkeep)))
    (testing "and a style naming two classes reaches both, but not a third"
      (is (contains? (offered :fighter nil) :mariner))
      (is (contains? (offered :ranger ranger-set) :mariner))
      (is (not (contains? (offered :paladin paladin-set) :mariner))))))

(deftest homebrew-adds-to-the-built-in-lists-and-never-edits-them
  (testing "every built-in style each class had before is still offered"
    (is (= (set (map ::t/key opt5e/fighting-style-options))
           (clojure.set/intersection
            (offered-keys (opt5e/fighting-style-selection :fighter nil entries))
            (set (map ::t/key opt5e/fighting-style-options)))))
    (is (= paladin-set
           (clojure.set/intersection
            (offered-keys (opt5e/fighting-style-selection :paladin paladin-set entries))
            (set (map ::t/key opt5e/fighting-style-options))))
        "Paladin's whitelist is untouched — homebrew is additive, not a replacement")))

(deftest the-ref-survives-the-threading
  ;; A cross-silo grant deliberately carries no top-level :ref; a class's own selection must keep
  ;; one or the character has nowhere to store the pick (D30, and a footgun a test already caught).
  (let [sel (opt5e/fighting-style-selection :fighter nil entries)]
    (is (= [:class :fighter :fighting-style] (::t/ref sel)))
    (is (contains? (::t/tags sel) :class))))

(deftest an-offered-homebrew-style-carries-its-authored-mechanics
  (let [opt (->> (::t/options (opt5e/fighting-style-selection :fighter nil entries))
                 (filter #(= :bulwark (::t/key %)))
                 first)]
    (is (some? opt) "the style is on the list")
    (is (seq (::t/modifiers opt))
        "and it arrives compiled — :props and the description became modifiers, so picking it
         does something rather than just naming itself")))

(deftest no-homebrew-is-the-same-as-before
  ;; nil and [] must both behave as "no homebrew" — the class path passes whatever the sub yields,
  ;; and an empty pack yields an empty seq.
  (doseq [empty-pool [nil []]]
    (is (= (offered-keys (opt5e/fighting-style-selection :paladin paladin-set))
           (offered-keys (opt5e/fighting-style-selection :paladin paladin-set empty-pool)))
        (str "empty pool " (pr-str empty-pool) " changes nothing"))))
