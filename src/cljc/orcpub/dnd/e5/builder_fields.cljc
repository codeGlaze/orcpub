(ns orcpub.dnd.e5.builder-fields
  "Utilities over a builder FIELD SCHEMA — the declarative description of a homebrew type's
   fields. The form side renders these (views/render-builder-field); this ns derives the
   save-validation spec from the same data, so a type's fields are described ONCE.

   A field spec:
     :key        a single key or a PATH vector into the item (e.g. [:breath-weapon :damage-type])
     :type       :enum | :number | :text
     :label      form label
     :options    (:enum) [{:value <stored value, any type> :title <label>} …]
     :required?  default FALSE (optional). Optional-by-default is deliberate: it is the
                 friendlier UX AND keeps existing orcbrew content valid (D9 backward-compat).
     :when       (item -> bool, optional) — form-only conditional display.

   Pure/leaf: requires spec only."
  (:require #?(:clj  [clojure.spec.alpha :as spec])
            #?(:cljs [cljs.spec.alpha :as spec])
            [orcpub.common :as common]))

(defn field-value-pred
  "Predicate a field's STORED value must satisfy WHEN PRESENT. :enum → the set of its option
   values, so a value that isn't one of the declared options (e.g. a damage type outside the
   ten) is rejected — validation the old hand-written specs never did."
  [{:keys [type options]}]
  (case type
    :enum   (set (map :value options))
    ;; a SET of declared option values — "which of these apply". Rejects a value outside the
    ;; declared options exactly as :enum does, elementwise.
    :multi-enum (let [allowed (set (map :value options))]
                  (fn [v] (and (coll? v) (every? allowed v))))
    :number number?
    :text   string?
    (constantly true)))

;; CONVERGENCE NOTE — boolean/toggle field type (deferred, do NOT build a parallel mechanism).
;; A hardened toggle needs BOTH halves; each branch built one, so the merged primitive combines them:
;;   - path-safe traversal + self-heal of a collapsed intermediate  (claude/custom-class-source-error-2k5ykd:
;;     common/toggle-in / common/toggle-flag) — a toggle whose path lands on a MAP must NOT `(not map)`
;;     it (collapse); a stray false/nil intermediate heals into a map instead of crashing.
;;   - defensive leaf read `(not (true? v))` + `:boolean → boolean?` save-validation (this branch,
;;     backed out here to avoid a parallel fn) — nil/absent/garbage read as OFF; a present non-boolean
;;     is rejected at save. Collection-preservation alone still reads garbage as "on"; leaf-read alone
;;     still collapses a map — you need both.
;; Plus `strip-export-blanks` (theirs) keeps exports terse, and the save ⊆ load guard (theirs: anything
;; that SAVES must LOAD). When the branches meet: add a `:boolean` type here + in render-builder-field
;; routing through the ONE combined primitive above — never a fresh toggle fn, never a second validator.

;; Universal homebrew fields as NAMED specs so fields->spec composes via spec/keys — its explain-data
;; then names :name/:key/:option-pack in the :in path (diagnosable banners), matching develop's
;; per-type specs. :key rejects the keyword-trap ("9 Lives" -> :9-lives).
(spec/def ::name string?)
(spec/def ::key (spec/and keyword? common/keyword-starts-with-letter?))
(spec/def ::option-pack string?)

;; ── Shared :props field fragments ─────────────────────────────────────────────────────────────
;; The :props vocabulary compiles into SEVEN silos through one function (races, subraces, classes,
;; subclasses, draconic ancestries, feats, fighting styles), so a fragment defined once can be
;; dropped into any of their builders' extra-fields and that silo can author the prop. The compiler
;; is already shared; only the form fields were missing.

(def fighting-style-class-options
  ;; Only the classes that HAVE a fighting-style feature. Offering Wizard here would let an author
  ;; write a restriction that can never be satisfied.
  [{:value :fighter :title "Fighter"}
   {:value :paladin :title "Paladin"}
   {:value :ranger  :title "Ranger"}])

(def fighting-style-classes-field
  ;; The authoring half of the divvying rule (`fighting-style-authoring.md`): pick none and the
  ;; style is open to every class that has the feature — the documented fallback, which is why
  ;; this is not :required?.
  [{:key :classes
    :type :multi-enum
    :label "Classes that may take this style"
    :options fighting-style-class-options}])

(def ac-bonus-fields
  "Authors {:ac-bonus {:bonus N :armor? b :shield? b}} — a flat bonus applied to whichever AC
  calculation wins, rather than to a particular one. The two tags are THREE-state: pick a value to
  require or forbid that equipment, or leave blank for either way. Defense fighting style is
  exactly {:bonus 1 :armor? true}."
  (let [has-bonus? #(get-in % [:props :ac-bonus :bonus])]
    [{:key [:props :ac-bonus :bonus] :type :number :label "AC Bonus"}
     {:key [:props :ac-bonus :armor?] :type :enum :label "Armor requirement" :when has-bonus?
      :options [{:value nil   :title "Both"}
                {:value true  :title "Only while wearing armor"}
                {:value false :title "Only while NOT wearing armor"}]}
     {:key [:props :ac-bonus :shield?] :type :enum :label "Shield requirement" :when has-bonus?
      :options [{:value nil   :title "Both"}
                {:value true  :title "Only while wielding a shield"}
                {:value false :title "Only while NOT wielding a shield"}]}]))

(defn- weapon-tag-field
  "One three-state weapon tag as an :enum field. Shown only once a bonus has been entered, since a
  tag on no bonus means nothing."
  [prop-key tag label yes no]
  {:key [:props prop-key tag] :type :enum :label label
   :when #(get-in % [:props prop-key :bonus])
   ;; An explicit nil option FIRST. A <select> with no matching value shows its first option, so
   ;; without this the form displays "Melee weapons only" for a field that is actually unset — it
   ;; would lie about a three-state value in a two-option control.
   :options [{:value nil :title "Both"} {:value true :title yes} {:value false :title no}]})

(defn- weapon-bonus-fields
  "Fields for one conditional weapon bonus: the number, then the tags that gate it. The tags are
  three-state — pick a value to require or forbid the property, leave blank for either way."
  [prop-key number-label]
  (into [{:key [:props prop-key :bonus] :type :number :label number-label}]
        ;; The predicate supports every weapon flag (weapons/tag->flag); the form exposes the ones
        ;; authors actually reach for. The rest stay hand-authorable in an .orcbrew file.
        [(weapon-tag-field prop-key :melee?      "Melee"    "Melee weapons only"  "Exclude melee")
         (weapon-tag-field prop-key :ranged?     "Ranged"   "Ranged weapons only" "Exclude ranged")
         (weapon-tag-field prop-key :heavy?      "Heavy"    "Heavy weapons only"  "Exclude heavy")
         (weapon-tag-field prop-key :thrown?     "Thrown"   "Thrown weapons only" "Non-thrown only")
         (weapon-tag-field prop-key :finesse?    "Finesse"  "Finesse weapons only" "Non-finesse only")
         (weapon-tag-field prop-key :light?      "Light"    "Light weapons only"   "Non-light only")
         (weapon-tag-field prop-key :two-handed? "Handedness" "Two-handed only"    "One-handed only")]))

(def attack-bonus-fields
  "Authors {:attack-bonus {:bonus N <tags>}} — Archery is {:bonus 2 :ranged? true}."
  (weapon-bonus-fields :attack-bonus "Attack Bonus"))

(def damage-bonus-fields
  "Authors {:damage-bonus {:bonus N <tags>}} — Thrown Weapon Fighting is {:bonus 2 :thrown? true}."
  (weapon-bonus-fields :damage-bonus "Damage Bonus"))

(defn effect-rows
  "The `:rows` node for authored MECHANICS. Instead of every field of every effect stacked flat, an
  author adds the effects the content actually has, and each arrives as a titled group carrying its
  own tags — so \"Melee\" under *Attack Bonus* can only mean one thing.

  `:at` is the subtree a row occupies; presence in the data is what makes a row appear, so nothing
  new is stored and an item authored by the flat form renders unchanged (D9). The kinds' fields are
  the SAME shared fragments the flat form used, unedited.

  Removing a row clears its subtree immediately — no confirm. The undo is retyping one number, and
  a modal on every ✕ costs more than the mistake does."
  []
  [{:rows      :effects
    :title     "Effects"
    :add-label "Add an effect"
    :kinds     [{:kind :ac-bonus     :title "AC Bonus"     :at [:props :ac-bonus]
                 :hint "added to whichever AC calculation wins"
                 :tag-header "Applies when"
                 :fields ac-bonus-fields}
                {:kind :attack-bonus :title "Attack Bonus" :at [:props :attack-bonus]
                 :hint "to attack rolls with matching weapons"
                 :tag-header "Only with weapons that are"
                 :fields attack-bonus-fields}
                {:kind :damage-bonus :title "Damage Bonus" :at [:props :damage-bonus]
                 :hint "to damage rolls with matching weapons"
                 :tag-header "Only with weapons that are"
                 :fields damage-bonus-fields}]}])

(defn flatten-fields
  "A schema is a vector of NODES. Today every node is a field, so this is identity; once group nodes
  land (docs/kb/builder-form-schemas.md) a group contributes its lead field plus its tags.

  Everything that walks a schema for its FIELDS — save-spec construction, import verification,
  drift tests — goes through here, so adding a node kind does not mean hunting down every walker."
  [schema]
  (mapcat (fn [node]
            (cond
              ;; a :rows node contributes every field of every kind it can hold. The kinds' fields
              ;; carry absolute paths, so validation is unchanged by the grouping — which is the
              ;; point: :rows is an arrangement, not a second storage model.
              (:rows node)  (mapcat :fields (:kinds node))
              (:group node) (cons (:lead node) (:tags node))
              :else         [node]))
          schema))

(defn fields->spec
  "Build a save-validation spec (a predicate) from a field schema. The universal
   name/key/option-pack are required (unchanged from the prior hand-written specs); every other
   field is OPTIONAL unless :required?. A present value must satisfy its type predicate. :key may
   be a nested path, so nested sub-maps (e.g. :breath-weapon) validate without registering a
   spec per key.

   ⚠️ HIGH-PRIORITY TODO — CONDITIONAL-REQUIRED (:required-when) IS NOT ENFORCED YET. A field
   that is required only given another field's value (e.g. line-width is required when shape =
   line, but meaningless for a cone) is currently treated as plain optional. This is a known gap;
   see docs/kb/content-extensibility-direction.md PINS. DO NOT let it get lost."
  [fields]
  (let [checks (mapv (fn [{:keys [key required?] :as f}]
                       (let [path (if (sequential? key) key [key])
                             pred (field-value-pred f)]
                         (fn [item]
                           (if-some [v (get-in item path)]
                             (boolean (pred v))
                             (not required?)))))
                     fields)]
    ;; spec/keys makes name/key/option-pack failures DIAGNOSABLE (explain-data :in names the field);
    ;; the trailing predicate enforces the declared type fields (nested paths, optional-by-default).
    (spec/and
     (spec/keys :req-un [::name ::key ::option-pack])
     (fn [item] (every? #(% item) checks)))))

(defn validate-fields
  "Return a vector of human-readable problems for `item` against a field schema (empty = valid).
   Same rules as fields->spec but LABELED — the single validator for form feedback AND
   import/export verification, so all three surfaces agree. (Universal name/key/option-pack are
   handled by the spec / structural validation; this covers the type's own fields.)

   ⚠️ Conditional-required (:required-when) NOT enforced yet — see fields->spec's TODO."
  [fields item]
  (reduce (fn [problems {:keys [key label required?] :as f}]
            (let [path (if (sequential? key) key [key])
                  v    (get-in item path)
                  pred (field-value-pred f)
                  nm   (or label (pr-str key))]
              (cond
                (and required? (nil? v))      (conj problems (str nm " is required"))
                (and (some? v) (not (pred v))) (conj problems (str nm " has an invalid value"))
                :else                          problems)))
          []
          fields))

(defn fields->required-entries
  "Auto-generate import/export `required-fields`-style entries for a schema's :required? fields,
   so the import/export verification stays SYNCED with the schema (no hand-duplication, no
   drift — change the schema and import/export follows). Each entry carries the field's type
   :check-fn and a sensible :dummy, so a missing one fills to keep content loadable — consistent
   with the existing table's behavior. Keyed by the field's :key (a single key OR a path vector).
   This covers the TYPE's fields; universal name/key/option-pack stay in the hand table."
  [fields]
  (into {}
        (for [{:keys [key required? type options] :as f} fields
              :when required?]
          [key {:check-fn (field-value-pred f)
                :dummy    (case type
                            :enum   (:value (first options))
                            :number 0
                            "")}])))
