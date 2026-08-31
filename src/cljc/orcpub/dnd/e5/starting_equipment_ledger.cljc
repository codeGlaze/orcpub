(ns orcpub.dnd.e5.starting-equipment-ledger
  "Override ledger: represent a homebrew class's starting equipment as a reference to an SRD
   base plus a minimal diff, instead of a full copy. The user still edits the full
   materialized form in the builder (see srd-starting-equipment / builder-equipment); on
   save the app DERIVES a ledger by diffing the edited form against the resolved base, and
   on load RESOLVES base + ledger back to the full form.

   Addressing is by STABLE KEY, never name or position: a group/option key is minted
   deterministically from its name (common/name-to-kw, de-duplicated within its parent), so
   the same name always yields the same key and matched items line up across base and edit
   regardless of order. Keys are COMPUTED here, not persisted in the builder's working data —
   the only place they appear is inside the ledger's op paths. See
   docs/kb/starting-equipment-override-ledger.md for the full design.

   v1 granularity: fixed grants by item-key; groups added/removed whole; group name; options
   added/removed/replaced whole (an edited option is stored entire — this captures a
   quantity-only change, and is coarser than grant-level addressing, which is a noted future
   refinement). Order is not meaningful: retained items keep base order, adds append."
  (:require [orcpub.common :as common]))

;; ---------------------------------------------------------------------------
;; Key minting (computed, deterministic, de-duplicated within a parent)
;; ---------------------------------------------------------------------------

(defn- assign-keys
  "Add a stable :key to each map in `items`, minted from :name via name-to-kw and
   de-duplicated within this collection (-2, -3, …). Blank/nil names fall back through
   name-to-kw's own stable placeholder, then the same dedup."
  [items]
  (loop [items items, used #{}, out []]
    (if-let [it (first items)]
      (let [base (or (common/name-to-kw (:name it)) :unnamed)
            k    (loop [k base, n 2]
                   (if (contains? used k)
                     (recur (keyword (str (name base) "-" n)) (inc n))
                     k))]
        (recur (rest items) (conj used k) (conj out (assoc it :key k))))
      out)))

(defn- key-selections
  "Mint :key on every group (de-duped across groups) and every option (de-duped within its
   group) of an :equipment-selections vector."
  [selections]
  (mapv (fn [group] (update group :options (comp vec assign-keys)))
        (assign-keys (vec selections))))

(defn- strip-keys [selections]
  (mapv (fn [group]
          (-> (dissoc group :key)
              (update :options (fn [opts] (mapv #(dissoc % :key) opts)))))
        selections))

(defn- strip-keys-group [group] (first (strip-keys [group])))

(defn- by-key [coll k] (first (filter #(= k (:key %)) coll)))

;; ---------------------------------------------------------------------------
;; Derive: (base, edited) -> ledger of ops. An op is {:op :add|:replace|:remove
;; :path [...] (:value …)}. :op is a readable label (add = key absent from base,
;; replace = present, remove = gone from edit); resolve keys off :value presence.
;; ---------------------------------------------------------------------------

(def ^:private buckets [:weapons :armor :equipment])

(defn- diff-fixed [ops bucket base edited]
  (let [b (get base bucket {}) e (get edited bucket {})]
    (as-> ops $
      ;; added or changed quantity
      (reduce-kv (fn [ops k qty]
                   (if (= qty (get b k))
                     ops
                     (conj ops {:op (if (contains? b k) :replace :add)
                                :path [:fixed bucket k] :value qty})))
                 $ e)
      ;; removed
      (reduce-kv (fn [ops k _]
                   (if (contains? e k) ops (conj ops {:op :remove :path [:fixed bucket k]})))
                 $ b))))

(defn- diff-options [ops gk base-opts edited-opts]
  (as-> ops $
    ;; added / changed options
    (reduce (fn [ops eo]
              (let [bo (by-key base-opts (:key eo))]
                (cond
                  (nil? bo)   (conj ops {:op :add     :path [:group gk :option (:key eo)]
                                         :value (dissoc eo :key)})
                  (= bo eo)   ops
                  :else       (conj ops {:op :replace :path [:group gk :option (:key eo)]
                                         :value (dissoc eo :key)}))))
            $ edited-opts)
    ;; removed options
    (reduce (fn [ops bo]
              (if (by-key edited-opts (:key bo))
                ops
                (conj ops {:op :remove :path [:group gk :option (:key bo)]})))
            $ base-opts)))

(defn- diff-selections [ops base-groups edited-groups]
  (as-> ops $
    ;; added groups (whole) and changes within matched groups
    (reduce (fn [ops eg]
              (let [bg (by-key base-groups (:key eg))]
                (if (nil? bg)
                  (conj ops {:op :add :path [:group (:key eg)] :value (strip-keys-group eg)})
                  (cond-> ops
                    (not= (:name bg) (:name eg))
                    (conj {:op :replace :path [:group (:key eg) :name] :value (:name eg)})
                    :always
                    (diff-options (:key eg) (:options bg) (:options eg))))))
            $ edited-groups)
    ;; removed groups
    (reduce (fn [ops bg]
              (if (by-key edited-groups (:key bg))
                ops
                (conj ops {:op :remove :path [:group (:key bg)]})))
            $ base-groups)))

(defn derive-ledger
  "Diff the edited equipment form against the base (both {:weapons.. :armor.. :equipment..
   :equipment-selections ..}); return a vector of ops. Empty when they are equal."
  [base edited]
  (let [base-groups   (key-selections (:equipment-selections base))
        edited-groups (key-selections (:equipment-selections edited))]
    (-> []
        (as-> ops (reduce (fn [ops b] (diff-fixed ops b base edited)) ops buckets))
        (diff-selections base-groups edited-groups))))

;; ---------------------------------------------------------------------------
;; Resolve: (base, ledger) -> full edited form. Fail-soft — an op whose target's
;; parent is absent from the base is skipped and surfaced in :warnings.
;; ---------------------------------------------------------------------------

(defn- upsert-by-key [coll item]
  (if (some #(= (:key item) (:key %)) coll)
    (mapv #(if (= (:key %) (:key item)) item %) coll)
    (conj (vec coll) item)))

(defn- remove-by-key [coll k] (vec (remove #(= k (:key %)) coll)))

(defn- apply-op [{:keys [groups] :as acc} op]
  (let [{:keys [path value]} op
        has-val? (contains? op :value)
        warn (fn [msg] (update acc :warnings conj (assoc op :warning msg)))]
    (case (first path)
      :fixed
      (let [[_ bucket k] path]
        (assoc-in acc [:fixed bucket k]
                  ;; represented in :fixed; removal marks the key for cleanup below
                  (if has-val? value ::drop)))

      :group
      (let [[_ gk seg opt-or-name] path]
        (cond
          ;; whole-group add/remove
          (nil? seg)
          (if has-val?
            (assoc acc :groups (upsert-by-key groups (assoc value :key gk)))
            (assoc acc :groups (remove-by-key groups gk)))

          ;; group :name
          (= seg :name)
          (if-let [g (by-key groups gk)]
            (assoc acc :groups (upsert-by-key groups (assoc g :name value)))
            (warn "group absent from base"))

          ;; option add/remove/replace
          (= seg :option)
          (if-let [g (by-key groups gk)]
            (let [ok opt-or-name
                  opts (:options g)
                  opts' (if has-val?
                          (upsert-by-key opts (assoc value :key ok))
                          (remove-by-key opts ok))]
              (assoc acc :groups (upsert-by-key groups (assoc g :options opts'))))
            (warn "group absent from base"))

          :else (warn "unrecognised group path")))

      (warn "unrecognised op path"))))

(defn resolve-ledger
  "Apply `ledger` over `base` (the resolved SRD base equipment). Returns
   {:equipment {…full form…} :warnings [ops that could not apply]}."
  [base ledger]
  (let [base-groups (key-selections (:equipment-selections base))
        init {:fixed (select-keys base buckets)
              :groups base-groups
              :warnings []}
        {:keys [fixed groups warnings]}
        (reduce apply-op init ledger)
        ;; drop fixed entries flagged for removal
        fixed' (reduce-kv (fn [m bucket items]
                            (let [items' (into {} (remove (fn [[_ v]] (= v ::drop)) items))]
                              (if (seq items') (assoc m bucket items') m)))
                          {} fixed)]
    {:equipment (cond-> {}
                  (:weapons fixed')   (assoc :weapons (:weapons fixed'))
                  (:armor fixed')     (assoc :armor (:armor fixed'))
                  (:equipment fixed') (assoc :equipment (:equipment fixed'))
                  (seq groups)        (assoc :equipment-selections (strip-keys groups)))
     :warnings warnings}))
