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
    :enum   (set (map :value options))
    :number number?
    :text   string?
    (constantly true)))

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
