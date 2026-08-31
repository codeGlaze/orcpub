(ns orcpub.dnd.e5.starting-equipment-ledger
  "Stores a homebrew class's starting equipment as an SRD base class plus the changes made to
   it, instead of a full copy. Items match by identifiers already in the data — no invented
   keys: fixed grants (:weapons/:armor/:equipment, {item-key qty}) match by item key (changed
   or added in :fixed :set, removed in :fixed :del); choice groups (:equipment-selections)
   match by their :name, and a group the user changed is stored whole in :groups :set (the
   nested choice menus are rarely edited and not worth a finer diff), removed group names in
   :groups :del.

   resolve-delta turns a base plus its changes back into the plain :equipment-selections the
   existing class-option functions consume — this namespace adds no new runtime shape. A change
   that doesn't line up with the base is appended or ignored, never a crash."
  (:require [orcpub.dnd.e5.srd-starting-equipment :as srd]))

(def ^:private buckets [:weapons :armor :equipment])
(def ^:private full-equipment-keys (conj buckets :equipment-selections))
(def ^:private legacy-choice-keys [:weapon-choices :armor-choices :equipment-choices])

;; ---------------------------------------------------------------------------
;; derive: (base, edited) -> minimal delta {:fixed {:set :del} :groups {:set :del}}
;; ---------------------------------------------------------------------------

(defn- diff-fixed [base edited]
  (reduce
   (fn [acc b]
     (let [bm (get base b {}), em (get edited b {})
           changed (into {} (remove (fn [[k q]] (= q (get bm k))) em)) ; added or qty-changed
           removed (into #{} (remove #(contains? em %) (keys bm)))]
       (cond-> acc
         (seq changed) (assoc-in [:set b] changed)
         (seq removed) (assoc-in [:del b] removed))))
   {} buckets))

(defn- by-name [groups] (into {} (map (juxt :name identity)) groups))

(defn- diff-groups [base-groups edited-groups]
  (let [bm (by-name base-groups), em (by-name edited-groups)
        touched (into {} (remove (fn [[n g]] (= g (get bm n))) em)) ; changed or new, stored whole
        removed (into #{} (remove #(contains? em %) (keys bm)))]
    (cond-> {}
      (seq touched) (assoc :set touched)
      (seq removed) (assoc :del removed))))

(defn derive-delta
  "The minimal delta of `edited` starting equipment against `base` (both {:weapons.. :armor..
   :equipment.. :equipment-selections ..}). Empty when they are equal."
  [base edited]
  (let [fixed  (diff-fixed base edited)
        groups (diff-groups (:equipment-selections base) (:equipment-selections edited))]
    (cond-> {}
      (seq fixed)  (assoc :fixed fixed)
      (seq groups) (assoc :groups groups))))

;; ---------------------------------------------------------------------------
;; resolve: (base, delta) -> full edited form
;; ---------------------------------------------------------------------------

(defn- apply-fixed [base {:keys [set del]}]
  (reduce
   (fn [m b]
     (let [bucket (apply dissoc (merge (get m b {}) (get set b)) (get del b))]
       (if (seq bucket) (assoc m b bucket) (dissoc m b))))
   (select-keys base buckets)
   buckets))

(defn- apply-groups [base-groups {:keys [set del]}]
  (let [set (or set {}), del (or del #{})
        base-names (into #{} (map :name) base-groups)
        kept (->> base-groups
                  (remove #(contains? del (:name %)))
                  (mapv (fn [g] (get set (:name g) g))))       ; replace touched in place
        added (->> set
                   (remove (fn [[n _]] (contains? base-names n)))
                   (mapv val))]                                 ; genuinely-new groups append
    (vec (concat kept added))))

(defn resolve-delta
  "Apply `delta` over `base` starting equipment; return the full plain form. Ignores a stray
   :base key on the delta (the export carries it; resolution doesn't need it)."
  [base {:keys [fixed groups]}]
  (let [fixed'  (apply-fixed base (or fixed {}))
        groups' (apply-groups (vec (:equipment-selections base)) (or groups {}))]
    (cond-> {}
      (:weapons fixed')   (assoc :weapons (:weapons fixed'))
      (:armor fixed')     (assoc :armor (:armor fixed'))
      (:equipment fixed') (assoc :equipment (:equipment fixed'))
      (seq groups')       (assoc :equipment-selections groups'))))

;; ---------------------------------------------------------------------------
;; Whole-class transforms — the serialization boundary. The delta lives ONLY in exported
;; files: everything live (app-db, localStorage, the builder) holds the full form, so the
;; consumption path is untouched. collapse-class runs on export, expand-class on import.
;; ---------------------------------------------------------------------------

(defn collapse-class
  "Export: a class filled from an SRD base (carrying :starting-equipment-base) has its full
   equipment replaced by a compact {:starting-equipment {:base <kw> …delta}}. Left unchanged
   (and the internal base marker dropped) if there's no base, the base is unknown, or the
   class still uses the legacy :*-choices shorthand (which the delta doesn't model)."
  [class]
  (let [base-kw (:starting-equipment-base class)
        base    (when base-kw (srd/builder-equipment base-kw))]
    (if (and base (not-any? #(contains? class %) legacy-choice-keys))
      (let [delta (derive-delta base (select-keys class full-equipment-keys))]
        (-> (apply dissoc class :starting-equipment-base full-equipment-keys)
            (assoc :starting-equipment (assoc delta :base base-kw))))
      (dissoc class :starting-equipment-base))))

(defn expand-class
  "Import/load: a class carrying {:starting-equipment {:base <kw> …delta}} is resolved back to
   full equipment keys, with the base recorded as :starting-equipment-base so it round-trips
   and the UI can show what it's based on. Unknown base -> left as-is (never drop equipment)."
  [class]
  (if-let [{:keys [base] :as se} (:starting-equipment class)]
    (if-let [base-eq (and base (srd/builder-equipment base))]
      (-> (dissoc class :starting-equipment)
          (merge (resolve-delta base-eq (dissoc se :base)))
          (assoc :starting-equipment-base base))
      class)
    class))
