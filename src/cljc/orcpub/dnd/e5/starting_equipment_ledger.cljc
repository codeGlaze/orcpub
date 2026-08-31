(ns orcpub.dnd.e5.starting-equipment-ledger
  "Express a homebrew class's starting equipment as an SRD base plus a small delta, so a user
   who just tweaks a class doesn't save a full copy. Addressing is by identifiers that ALREADY
   exist in the data — no minted keys:

     - fixed grants  (:weapons/:armor/:equipment, {item-key qty}) diff by ITEM KEY: changed
       or added items in :fixed :set, removed items in :fixed :del.
     - choice groups (:equipment-selections) match by their existing :name; a group the user
       TOUCHED is stored WHOLE in :groups :set (the nested OR-menus are rare to edit and not
       worth a sub-diff), a removed group's name goes in :groups :del.

   resolve-delta applies a delta over the resolved base to get the full form back; that full
   form is the plain :equipment-selections the existing class-option functions already consume
   (this namespace adds no new runtime shape). Resolution is inherently fail-soft — a delta
   entry that doesn't line up with the base is simply appended or ignored, never a crash.
   See docs/kb/starting-equipment-override-ledger.md.")

(def ^:private buckets [:weapons :armor :equipment])

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
