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
            #?(:cljs [cljs.spec.alpha :as spec])))

(defn field-value-pred
  "Predicate a field's STORED value must satisfy WHEN PRESENT. :enum → the set of its option
   values, so a value that isn't one of the declared options (e.g. a damage type outside the
   ten) is rejected — validation the old hand-written specs never did."
  [{:keys [type options]}]
  (case type
    :enum    (set (map :value options))
    :number  number?
    :text    string?
    ;; a present boolean field MUST be a real boolean — catches a nil/absent value that leaked in as
    ;; though set (the 'false became nil on repeated clicking' class of bug). Off = absent or false.
    :boolean boolean?
    (constantly true)))

(defn toggle-next
  "Next value for a boolean toggle. ALWAYS a literal boolean, never nil — this is what designs the
   'false → nil on repeated clicking' bug out: reads defensively (only literal `true` is 'on', so
   nil/absent/garbage read as off) and returns `(not …)`, which is always `true`/`false`. So no click
   sequence, stale read, or malformed prior value can produce nil. Every generated toggle (the
   render-builder-field :boolean case) writes through this; hand-rolled per-field toggles are what let
   nil slip in, so route boolean fields through the field schema instead."
  [v]
  (not (true? v)))

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
    (fn [item]
      (and (map? item)
           (string? (:name item))
           (keyword? (:key item))
           (string? (:option-pack item))
           (every? #(% item) checks)))))

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
