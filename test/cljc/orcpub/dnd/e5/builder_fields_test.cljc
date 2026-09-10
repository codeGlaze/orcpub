(ns orcpub.dnd.e5.builder-fields-test
  "Pure tests for the field-schema validators (fields->spec + validate-fields). JVM-runnable.
   These are the SINGLE validators the form, the save spec, and import/export verification share."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as spec]
            [orcpub.common :as common]
            [orcpub.dnd.e5.builder-fields :as bf]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.requirements :as reqs]
            [orcpub.dnd.e5.races :as races]
            [orcpub.dnd.e5.classes :as classes]))

(def fields
  [{:key [:bw :damage-type] :type :enum :required? true :label "Damage Type"
    :options [{:value :acid} {:value :fire}]}
   {:key [:bw :width] :type :number :label "Width"}                 ; optional
   {:key :note :type :text :label "Note"}])                         ; optional, top-level

(def ok-item {:name "X" :key :x :option-pack "P" :bw {:damage-type :acid}})

(deftest validate-fields-flags-only-real-problems
  (testing "a valid item (required present, no bad values) → no problems"
    (is (empty? (bf/validate-fields fields ok-item))))
  (testing "missing a REQUIRED field → one labeled problem"
    (is (= ["Damage Type is required"]
           (bf/validate-fields fields (update ok-item :bw dissoc :damage-type)))))
  (testing "missing an OPTIONAL field → no problem (optional-by-default)"
    (is (empty? (bf/validate-fields fields (dissoc ok-item :note)))))
  (testing "a bad enum value → invalid-value problem"
    (is (= ["Damage Type has an invalid value"]
           (bf/validate-fields fields (assoc-in ok-item [:bw :damage-type] :banana)))))
  (testing "a bad number value → invalid-value problem"
    (is (seq (bf/validate-fields fields (assoc-in ok-item [:bw :width] "wide"))))))

(deftest fields->spec-matches-validate-fields
  (testing "the generated spec agrees with validate-fields on valid/invalid"
    (let [s (bf/fields->spec fields)]   ; now a spec (spec/keys + field checks), not a bare predicate
      (is (spec/valid? s ok-item))
      (is (not (spec/valid? s (update ok-item :bw dissoc :damage-type))))   ; missing required
      (is (not (spec/valid? s (assoc-in ok-item [:bw :damage-type] :banana)))) ; bad enum
      (is (spec/valid? s (dissoc ok-item :note))))))                        ; missing optional ok

;; ── The shared :props fragments ───────────────────────────────────────────────────────────────
;; A form field that writes a path the :props compiler doesn't read is the silent failure here:
;; the builder looks right, saves fine, and produces no mechanical effect. These pin the two ends
;; against each other.

(def ^:private ac-spec (bf/fields->spec bf/ac-bonus-fields))
(def ^:private base {:name "X" :key :x :option-pack "Pack"})

(deftest ac-bonus-fields-validate-what-the-form-can-produce
  (testing "a Defense-shaped style: +1 AC while wearing armor"
    (is (spec/valid? ac-spec (assoc base :props {:ac-bonus {:bonus 1 :armor? true}}))))
  (testing "the tags are optional — absent means either way"
    (is (spec/valid? ac-spec (assoc base :props {:ac-bonus {:bonus 1}}))))
  (testing "and the whole fragment is optional, so existing content stays valid (D9)"
    (is (spec/valid? ac-spec base)))
  (testing "a non-number bonus is rejected"
    (is (not (spec/valid? ac-spec (assoc base :props {:ac-bonus {:bonus "one"}})))))
  (testing "a tag value outside the declared options is rejected"
    (is (not (spec/valid? ac-spec (assoc base :props {:ac-bonus {:bonus 1 :armor? "yes"}}))))))

(deftest ac-bonus-field-paths-match-what-the-props-compiler-reads
  (testing "every field path is [:props :ac-bonus <k>], and <k> is a key ac-bonus-modifiers reads.
            If these drift, the form writes data the compiler ignores and the feature silently
            does nothing."
    (doseq [{:keys [key]} bf/ac-bonus-fields]
      (is (= [:props :ac-bonus] (vec (take 2 key))) (str key " must live under :props :ac-bonus"))
      ;; Reads the shared requirements registry rather than repeating the tag list. That list used
      ;; to live in THREE places — the predicate's destructure, the field fragment, and this set —
      ;; so adding one meant three edits and forgetting the predicate silently did nothing. The
      ;; engine's vocabulary is now one registry and this test asks it. Legacy :armor?/:shield? are
      ;; still valid in DATA (D9); the form writes the canonical names.
      (is (contains? (into #{:bonus :armor? :shield?} (keys reqs/requirements)) (last key))
          (str (last key) " must be a key the :ac-bonus prop compiler understands — either :bonus or
               a tag in opt5e/ac-conditions. The value key is :bonus, matching :attack-bonus and
               :damage-bonus; :ac-bonus is read as a legacy alias but the FORM must write the
               canonical one.")))))

(deftest ac-conditions-table-behaves-exactly-like-the-hand-written-predicate
  (testing "CHARACTERIZATION — ac-applies? was a two-clause `and` over :armor?/:shield?. The table
            must agree with it on every combination of authored spec and equipped state, or this
            refactor changed a shipped AC number."
    (let [reference (fn [{:keys [armor? shield?]} armor shield]     ; the code as it was
                      (and (or (nil? armor?)  (= armor?  (some? armor)))
                           (or (nil? shield?) (= shield? (some? shield)))))
          table-fn  #'orcpub.dnd.e5.options/ac-applies?]
      (doseq [armor?  [nil true false]
              shield? [nil true false]
              armor   [nil {:name "Plate"}]
              shield  [nil {:name "Shield"}]]
        (let [spec {:bonus 1 :armor? armor? :shield? shield?}]
          (is (= (reference spec armor shield) (table-fn spec armor shield))
              (str "disagreed for " (pr-str spec) " armor=" (some? armor) " shield=" (some? shield)))))))
  (testing "and an unknown tag is IGNORED, not failed — a pack from a build that knows more
            conditions than this one still applies its bonus"
    (is (true? (#'orcpub.dnd.e5.options/ac-applies? {:bonus 1 :not-a-requirement true} nil nil)))))

(deftest every-shipped-schema-uses-a-known-field-type
  (testing "a typo'd :type silently degrades to (constantly true) in field-value-pred, so the field
            is never validated — that is how fighting styles shipped with :string, which is not a
            declared type, and went unchecked. Walk every schema the app actually uses."
    (doseq [[label schema] [["draconic-ancestry" races/draconic-ancestry-fields]
                            ["fighting-style"    classes/fighting-style-fields]
                            ["ac-bonus"          bf/ac-bonus-fields]
                            ["attack-bonus"      bf/attack-bonus-fields]
                            ["damage-bonus"      bf/damage-bonus-fields]
                            ["fs-classes"        bf/fighting-style-classes-field]]
            {:keys [type key]} (bf/flatten-fields schema)]
      (is (contains? #{:text :number :enum :multi-enum :boolean :combo} type)
          (str label " field " key " has unknown :type " (pr-str type))))))

(deftest multi-enum-validates-every-element-against-the-declared-options
  ;; The whole point of a declared option list is that a value outside it is rejected. :enum does
  ;; that for one value; :multi-enum has to do it for each.
  (let [pred (bf/field-value-pred (first bf/fighting-style-classes-field))]
    (is (pred #{}) "no classes chosen is legal — it means 'open to all' (the fallback)")
    (is (pred #{:fighter}))
    (is (pred #{:fighter :paladin :ranger}))
    (is (not (pred #{:fighter :wizard})) "a class with no fighting-style feature is not a valid restriction")
    (is (not (pred :fighter)) "a bare value is not a set")
    (is (not (pred "fighter")) "a string is what a careless widget would store; reject it")))

(deftest a-fighting-style-with-no-classes-still-saves
  ;; Absent :classes is the documented fallback, so the field must be optional. If it were
  ;; required the builder would refuse to save the most common case.
  (let [spec-pred (bf/validate-fields bf/fighting-style-classes-field
                                      {:name "Bulwark" :key :bulwark})]
    (is (empty? spec-pred) (str "unexpected problems: " (pr-str spec-pred)))))

(deftest short-forms-are-additions-not-replacements
  ;; The grouped layout drops the words its header already says. That must be a COMPACT-ONLY
  ;; variant: the fragments are advertised as droppable into any builder's flat extra-fields, and
  ;; a bare "Armor" above a select with no header above it says nothing.
  (let [armor (first (filter #(= [:props :ac-bonus :armor?] (:key %)) bf/ac-bonus-fields))]
    (is (= "Armor requirement" (:label armor)) "the long label is still the default")
    (is (= "Armor" (:short-label armor))       "and the short one is available to a grouped form"))
  (testing "weapon tags derive the short option title by dropping the word the header supplies"
    (let [ranged (first (filter #(= [:props :attack-bonus :ranged?] (:key %)) bf/attack-bonus-fields))
          yes    (first (filter #(= true (:value %)) (:options ranged)))]
      (is (= "Ranged weapons only" (:title yes)))
      (is (= "Ranged only" (:short-title yes)))))
  (testing "the unset option is never given a short form — it must stay the explicit first option"
    (doseq [fields [bf/ac-bonus-fields bf/attack-bonus-fields bf/damage-bonus-fields]
            f      (filter :options fields)
            :let   [nil-opt (first (filter #(nil? (:value %)) (:options f)))]]
      (is (some? nil-opt) (str (:key f) " must offer the explicit nil option"))
      (is (nil? (:short-title nil-opt)) "\"Both\" is already short and must not vary by context"))))

(deftest boolean-field-type-validates-and-only-true-is-on
  ;; The type the convergence note deferred until both halves existed. Both are asserted here so
  ;; neither can be quietly dropped: the leaf reads only `true` as ON, and a collection at the path
  ;; is left alone rather than collapsed.
  (let [pred (bf/field-value-pred {:type :boolean})]
    (is (pred true))
    (is (pred false))
    (is (not (pred "true")) "a string is what a careless widget stores; reject it at save")
    (is (not (pred 1))))
  (testing "toggle-flag: only true is ON, so garbage and nil turn ON with the first click"
    (is (= false (common/toggle-flag true)))
    (is (= true  (common/toggle-flag false)))
    (is (= true  (common/toggle-flag nil))    "absent reads as OFF")
    (is (= true  (common/toggle-flag "false")) "garbage reads as OFF, not as ON")
    (is (= {:a 1} (common/toggle-flag {:a 1})) "a map is left alone, never collapsed to false"))
  (testing "toggle-in: heals a collapsed intermediate instead of crashing"
    (is (= {:props {:x true}}  (common/toggle-in {} [:props :x])))
    (is (= {:props {:x false}} (common/toggle-in {:props {:x true}} [:props :x])))
    (is (= {:props {:x true}}  (common/toggle-in {:props false} [:props :x]))
        "a stray false intermediate from the old collapse bug heals into a map")))

(deftest authored-dual-wielding-requirement-matches-the-hardcoded-feat
  (testing "THE POINT of the requirements registry: `+1 AC while wielding two weapons` was
            unauthorable — the declarative predicate only ever saw armor and shield, so Dual
            Wielder was hand-written as dual-wield-ac-mod. mod5e/ac-bonus-meeting assembles the
            wielded weapons into the contributor's own context, so it is now one authored prop.

            Compared against the reference the hand-written modifier implements: +1 only when BOTH
            hands hold a weapon."
    (let [;; what the author now writes
          spec      {:bonus 1 :dual-wielding? true}
          reference (fn [{:keys [main-hand off-hand]}]        ; = dual-wield-ac-mod's condition
                      (if (and (some? main-hand) (some? off-hand)) 1 0))
          sword     {:name "Shortsword"}]
      (doseq [main-hand [nil sword]
              off-hand  [nil sword]]
        (let [ctx {:armor nil :shield nil :main-hand main-hand :off-hand off-hand}]
          (is (= (pos? (reference ctx)) (reqs/meets-all? spec ctx))
              (str "main-hand=" (some? main-hand) " off-hand=" (some? off-hand)))))))
  (testing "and its inverse — Dueling's `one hand and no other weapons` — is the same one entry"
    (let [sword {:name "Longsword"}]
      (is (true?  (reqs/meets-all? {:bonus 2 :one-handed? true}
                                   {:main-hand sword :off-hand nil})))
      (is (false? (reqs/meets-all? {:bonus 2 :one-handed? true}
                                   {:main-hand sword :off-hand sword}))))))

(deftest requirements-registry-invariants
  (testing "every entry carries the phrasing that reaches the sheet and the PDF — :props emits
            mechanics only, so without :text a mechanic reaches neither"
    (doseq [[k {:keys [text]}] reqs/requirements]
      (is (string? text) (str k " has no :text"))))
  (testing "a :build or :toggle gate computes, so it needs a predicate; a :text gate is a TRIGGER
            and must NOT have one — the absence is what stops anything claiming to compute a moment"
    (doseq [[k {:keys [gate pred]}] reqs/requirements]
      (is (contains? #{:build :toggle :text} gate) (str k " has unknown gate " gate))
      (if (= :text gate)
        (is (nil? pred) (str k " is a trigger and must carry no :pred"))
        (is (fn? pred)  (str k " gate " gate " must carry a :pred")))))
  (testing "every entry today is a :build gate — :toggle and :text are declared in the vocabulary
            but have no entries, so nothing here is speculative structure with a test propping it up"
    (is (= #{:build} (set (map :gate (vals reqs/requirements)))))))
