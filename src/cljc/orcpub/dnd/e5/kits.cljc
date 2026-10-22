(ns orcpub.dnd.e5.kits
  "Kits: a chosen equipment pack turned into ordinary inventory picks plus a record of how much of
   each item the kit holds. Stored as picks under [::entity/options :kits]; targets come from the
   pack's definition. plan-inventory-kits.md on agents/develop."
  (:require [clojure.string :as s]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [orcpub.dnd.e5.equipment :as equipment5e]
            [orcpub.dnd.e5.picks :as picks]))

(def choice-groups
  "Pack entries that stand for a choice, as group key -> the set of item keys that answer it."
  {:holy-symbol (set (map :key equipment5e/holy-symbols))})

(def ^:private pack-by-key
  (into {} (map (juxt :key identity)) equipment5e/packs))

(defn pack
  "The equipment pack whose key is `k`, or nil when `k` is not a pack."
  [k]
  (pack-by-key k))

(defn kits
  "The kits stored on `character`, in kit order. Returns a vector of kit picks."
  [character]
  (vec (get-in character [::entity/options :kits])))

(defn held
  "How many of each item the kit `kit` holds. Returns a map of item key -> count."
  [kit]
  (into {} (map (juxt ::entity/key ::entity/value)) (get-in kit [::entity/options :held])))

(defn- equipment-picks
  "The character's stored Equipment picks. Returns a vector."
  [character]
  (vec (get-in character [::entity/options :equipment])))

(defn add-item
  "`character` with `n` of gear `k` added to its Equipment picks: added to an existing pick's
   quantity (a missing quantity counts as 1), else a new carried pick. Returns the character."
  [character k n]
  (let [ps (equipment-picks character)
        i (first (keep-indexed (fn [i e] (when (= k (::entity/key e)) i)) ps))]
    (assoc-in character [::entity/options :equipment]
              (if i
                (update-in ps [i ::entity/value ::char-equip/quantity] #(+ (or % 1) n))
                (conj ps {::entity/key k
                          ::entity/value {::char-equip/quantity n ::char-equip/equipped? true}})))))

(defn group-choice
  "Which item of the choice group `group` (a key of `choice-groups`) `character` already owns
   among its Equipment picks. Returns {:one k}, {:many ks} or {:none true}."
  [character group]
  (let [owned (filterv (choice-groups group) (map ::entity/key (equipment-picks character)))]
    (case (count owned)
      0 {:none true}
      1 {:one (first owned)}
      {:many owned})))

(defn materialize
  "`character` with pack `pack-key`'s items added to its Equipment picks and a kit for it appended,
   holding every item it adds. `chosen`: choice group -> the item key answering it; `owned`: a set of
   item keys the kit claims without adding, because the character already has them; `skipped`: a
   set of choice groups left unanswered (added and held as nothing). Returns the character."
  [character pack-key {:keys [chosen owned skipped]}]
  (let [items (:items (pack pack-key))
        step (fn [[c hold] [k n]]
               (let [k' (get chosen k k)]
                 (cond
                   (contains? skipped k) [c hold]
                   (contains? owned k') [c (conj hold [k' n])]
                   :else [(add-item c k' n) (conj hold [k' n])])))
        [c hold] (reduce step [character []] items)]
    (update-in c [::entity/options :kits] (fnil conj [])
               {::entity/key pack-key
                ::entity/options {:held (mapv (fn [[k n]] {::entity/key k ::entity/value n}) hold)}})))

(defn- pack-pick?
  "Whether the pick at `address` chose a pack: a starting-equipment choice, or a pack stored as an
   Equipment pick."
  [address]
  (let [k (peek address)
        sel (peek (pop address))]
    (and (pack k)
         (or (= :equipment sel)
             (s/starts-with? (name sel) "starting-equipment")))))

(defn chosen-packs
  "The packs `character` has chosen, as [{:pack k :address a}], in pick order."
  [character]
  (vec (for [[address _] (picks/addresses character)
             :when (pack-pick? address)]
         {:pack (peek address) :address address})))

(defn repair
  "`character` with a kit materialized for every chosen pack that has none, applying the owner's
   rule for a pack's own choice: the one owned answer is claimed, none adds the generic entry,
   several is left for the player. A pack stored as an Equipment pick is replaced by its items.
   Returns {:character c :ask [{:pack k :group g :options ks}]}; idempotent."
  [character]
  (reduce
   (fn [{:keys [character] :as acc} {pk :pack address :address}]
     (if (some #(= pk (::entity/key %)) (kits character))
       acc
       (let [groups (filter choice-groups (keys (:items (pack pk))))
             answers (into {} (map (juxt identity #(group-choice character %))) groups)
             opts {:chosen (into {} (for [[g a] answers :when (:one a)] [g (:one a)]))
                   :owned (set (keep :one (vals answers)))
                   :skipped (set (for [[g a] answers :when (:many a)] g))}
             c (cond-> character
                 (= :equipment (peek (pop address))) (-> (picks/remove-at address) :character))]
         {:character (materialize c pk opts)
          :ask (into (:ask acc) (for [[g a] answers :when (:many a)]
                                  {:pack pk :group g :options (:many a)}))})))
   {:character character :ask []}
   (chosen-packs character)))
