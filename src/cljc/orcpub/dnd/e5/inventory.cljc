(ns orcpub.dnd.e5.inventory
  "The character's inventory beyond single picks: gear quantities across picks, and kits (a chosen
   equipment pack turned into ordinary picks plus how much of each item the kit holds, stored under
   [::entity/options :kits], targets from the pack's definition). The home for inventory rules:
   containers and the rest go here, not in files of their own. plan-inventory-kits.md on
   agents/develop."
  (:require [clojure.string :as s]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [orcpub.dnd.e5.equipment :as equipment5e]
            [orcpub.dnd.e5.picks :as picks]))

(def choice-groups
  "Pack entries that stand for a choice, as group key -> the set of item keys that answer it, from
   `equipment5e/groups`."
  (update-vals equipment5e/groups #(set (map :key (:items %)))))

(def ^:private pack-by-key
  (into {} (map (juxt :key identity)) equipment5e/packs))

(defn pack
  "The equipment pack whose key is `k`, or nil when `k` is not a pack."
  [k]
  (pack-by-key k))

(defn kits
  "Every kit stored on `character`, removed ones included, in kit order. Returns a vector of picks."
  [character]
  (vec (get-in character [::entity/options :kits])))

(defn removed?
  "Whether `kit` was removed by the player and is kept only so its pack is not made into a kit again."
  [kit]
  (boolean (seq (get-in kit [::entity/options :removed]))))

(defn active-kits
  "The kits on `character` the player sees, in kit order. Returns a vector of picks."
  [character]
  (filterv (complement removed?) (kits character)))

(defn held
  "How many of each item the kit `kit` holds. Returns a map of item key -> count."
  [kit]
  (into {} (map (juxt ::entity/key ::entity/value)) (get-in kit [::entity/options :held])))

(defn kit-count
  "How many packs the kit `kit` stands for: its stored number, else 1."
  [kit]
  (let [v (::entity/value kit)] (if (pos-int? v) v 1)))

(defn targets
  "What the kit `kit` holds when full, as [[item-key count]] in the pack's order: the pack's
   amounts times `kit-count`. A choice group stands for the member the kit holds; an unanswered
   group is left out."
  [kit]
  (let [h (held kit)
        c (kit-count kit)]
    (into []
          (keep (fn [[k n]]
                  (if-let [members (choice-groups k)]
                    (when-let [m (some #(when (members %) %) (keys h))] [m (* n c)])
                    [k (* n c)])))
          (:items (pack (::entity/key kit))))))

(defn- equipment-picks
  "The character's stored Equipment picks. Returns a vector."
  [character]
  (vec (get-in character [::entity/options :equipment])))

(defn- pick-qty
  "The quantity of a stored Equipment pick: a map value's quantity, a bare number, else 1."
  [pick]
  (let [v (::entity/value pick)]
    (cond (map? v) (or (::char-equip/quantity v) 1)
          (number? v) v
          :else 1)))

(defn total
  "How many of gear `k` `character` owns, from its Equipment picks. Returns a number."
  [character k]
  (reduce + 0 (map pick-qty (filter #(= k (::entity/key %)) (equipment-picks character)))))

(defn spare
  "How many of gear `k` no kit holds: the total less every active kit's held count. Never negative."
  [character k]
  (max 0 (- (total character k) (reduce + 0 (keep #(get (held %) k) (active-kits character))))))

(defn add-item
  "`character` with `n` of gear `k` added to its Equipment picks: added to an existing pick's
   quantity (a missing quantity counts as 1), else a new carried pick. Returns the character."
  [character k n]
  (let [ps (equipment-picks character)
        i (first (keep-indexed (fn [i e] (when (= k (::entity/key e)) i)) ps))]
    (assoc-in character [::entity/options :equipment]
              (if i
                (update ps i (fn [p] (assoc p ::entity/value
                                            (let [v (::entity/value p)]
                                              (assoc (if (map? v) v {::char-equip/equipped? true})
                                                     ::char-equip/quantity (+ (pick-qty p) n))))))
                (conj ps {::entity/key k
                          ::entity/value {::char-equip/quantity n ::char-equip/equipped? true}})))))

(defn- remove-items
  "`character` with `n` of gear `k` taken off its Equipment picks; a pick reaching 0 is removed."
  [character k n]
  (let [ps (equipment-picks character)
        i (first (keep-indexed (fn [i e] (when (= k (::entity/key e)) i)) ps))]
    (if-not i
      character
      (let [left (- (pick-qty (ps i)) n)]
        (assoc-in character [::entity/options :equipment]
                  (if (pos? left)
                    (assoc-in ps [i ::entity/value]
                              (assoc (let [v (::entity/value (ps i))] (if (map? v) v {}))
                                     ::char-equip/quantity left))
                    (into (subvec ps 0 i) (subvec ps (inc i)))))))))

(defn- kit-index
  "Index of the kit for pack `pack-key` in `character`'s stored kits, or nil."
  [character pack-key]
  (first (keep-indexed (fn [i kit] (when (= pack-key (::entity/key kit)) i)) (kits character))))

(defn- set-held
  "`kit` holding `n` of gear `k`."
  [kit k n]
  (let [hs (vec (get-in kit [::entity/options :held]))
        i (first (keep-indexed (fn [i e] (when (= k (::entity/key e)) i)) hs))]
    (assoc-in kit [::entity/options :held]
              (if i (assoc-in hs [i ::entity/value] n) (conj hs {::entity/key k ::entity/value n})))))

(defn- update-kit
  "`character` with `f` applied to the kit for `pack-key`."
  [character pack-key f]
  (if-let [i (kit-index character pack-key)]
    (assoc-in character [::entity/options :kits] (update (kits character) i f))
    character))

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

(defn- sourced
  "The set of pack keys `character` has chosen."
  [character]
  (set (map :pack (chosen-packs character))))

(declare deficits resolve-orphan tracked)

(defn source-count
  "How many of pack `pack-key` `character` has: one per starting-equipment choice of it, plus the
   quantity of its Equipment picks."
  [character pack-key]
  (+ (count (for [{pk :pack a :address} (chosen-packs character)
                  :when (and (= pk pack-key) (not= :equipment (peek (pop a))))]
              a))
     (total character pack-key)))

(defn set-kit-count
  "`character` with the kit for `pack-key` standing for `n` packs. Going up adds each extra pack's
   items and holds them; going down lowers the targets, and what the kit held above them becomes
   unbundled. A pack's choice (holy symbol) follows the member the kit already holds."
  [character pack-key n]
  (let [kit (get (kits character) (kit-index character pack-key))
        from (kit-count kit)
        h (held kit)
        per-pack (keep (fn [[k q]]
                         (if-let [members (choice-groups k)]
                           (when-let [m (some #(when (members %) %) (keys h))] [m q])
                           [k q]))
                       (:items (pack pack-key)))
        grown (if (> n from)
                (reduce (fn [c [k q]]
                          (let [more (* q (- n from))]
                            (-> (add-item c k more)
                                (update-kit pack-key #(set-held % k (+ (get (held %) k 0) more))))))
                        character per-pack)
                (reduce (fn [c [k q]]
                          (update-kit c pack-key #(set-held % k (min (get (held %) k 0) (* q n)))))
                        character per-pack))]
    (update-kit grown pack-key #(assoc % ::entity/value n))))

(defn offered-packs
  "The packs the character's first class grants or offers in its starting equipment, read from the
   built `template`: its fixed items, and every option under its `:starting-equipment` selections.
   Returns a set of pack keys; empty without a template or a class."
  [template character]
  (let [first-class (::entity/key (first (get-in character [::entity/options :class])))
        class-opt (some (fn [sel]
                          (when (= :class (::t/key sel))
                            (some #(when (= first-class (::t/key %)) %) (::t/options sel))))
                        (::t/selections template))
        walk (fn walk [node]
               (for [sel (::t/selections node)
                     :when (contains? (::t/tags sel) :starting-equipment)
                     o (::t/options sel)
                     k (cons (::t/key o) (walk o))]
                 k))
        fixed (for [assoc-opt (::t/associated-options class-opt)
                    [_ ps] assoc-opt
                    p ps]
                (::entity/key p))]
    (into #{} (filter pack) (when class-opt (concat fixed (walk class-opt))))))

(defn- open-offer
  "`offered` while the character's starting-equipment pack choice is still unanswered; empty once a
   pack is chosen there, since the choice no longer stands for any other pack."
  [character offered]
  (if (some #(s/starts-with? (name (peek (pop (:address %)))) "starting-equipment")
            (chosen-packs character))
    #{}
    (or offered #{})))

(defn untouched?
  "Whether the kit for `pack-key` still holds everything its pack gave: nothing used, lost or
   restocked differently."
  [character pack-key]
  (empty? (deficits character pack-key)))

(defn- drop-quiet-orphans
  "`character` without the untouched kits whose pack is no longer chosen and not in `offered`,
   their items taken off as `resolve-orphan` :remove does. Such a kit never asks."
  [character offered]
  (let [chosen (sourced character)
        offered (open-offer character offered)]
    (reduce (fn [c kit]
              (let [pk (::entity/key kit)]
                (if (and (not (chosen pk)) (not (offered pk)) (untouched? c pk))
                  (resolve-orphan c pk :remove)
                  c)))
            character
            (active-kits character))))

(defn repair
  "`character` with every chosen pack made a kit (a pack's choice claimed when one answer is owned,
   else left to `questions`), each kit's pack count kept to its sources (`set-kit-count`), removed
   kits whose pack is gone dropped, and with `offered` (`offered-packs`) untouched kits whose pack is
   gone and not offered dropped quietly. Returns {:character c :ask [{:pack :group :options}]};
   idempotent."
  [character & [offered]]
  (let [{c :character :as acc}
        (reduce
         (fn [{:keys [character] :as acc} pk]
           (if-let [i (kit-index character pk)]
             (let [kit (get (kits character) i)
                   n (source-count character pk)]
               (cond-> acc
                 (and (not (removed? kit)) (pos? n) (not= n (kit-count kit)))
                 (assoc :character (set-kit-count character pk n))))
             (let [groups (filter choice-groups (keys (:items (pack pk))))
                   answers (into {} (map (juxt identity #(group-choice character %))) groups)
                   opts {:chosen (into {} (for [[g a] answers :when (:one a)] [g (:one a)]))
                         :owned (set (keep :one (vals answers)))
                         :skipped (set (for [[g a] answers :when (not (:one a))] g))}]
               {:character (let [made (materialize character pk opts)
                                 n (source-count made pk)]
                             (cond-> made (> n 1) (set-kit-count pk n)))
                :ask (into (:ask acc) (for [[g a] answers :when (:many a)]
                                        {:pack pk :group g :options (:many a)}))})))
         {:character character :ask []}
         (distinct (map :pack (chosen-packs character))))
        chosen (sourced c)
        stale? #(and (removed? %) (not (chosen (::entity/key %))))]
    (cond-> acc
      (some stale? (kits c))
      (assoc :character (assoc-in c [::entity/options :kits] (vec (remove stale? (kits c)))))
      offered
      (update :character drop-quiet-orphans offered))))

(defn questions
  "What `character`'s kits need the player to answer, in kit order: a kit the player has used whose
   pack is no longer chosen nor offered by a pack choice still open (`offered`, a set from
   `offered-packs`) ({:kind :orphan :pack k}), and
   a pack's unanswered choice ({:kind :choice :pack k :group g :options ks}; the owned answers, else
   every member)."
  [character & [offered]]
  (let [chosen (sourced character)
        offered (open-offer character offered)]
    (vec (mapcat
          (fn [kit]
            (let [pk (::entity/key kit)
                  h (held kit)]
              (concat
               (when-not (or (chosen pk) (offered pk) (untouched? character pk))
                 [{:kind :orphan :pack pk}])
               (for [g (filter choice-groups (keys (:items (pack pk))))
                     :let [members (choice-groups g)]
                     :when (not-any? members (keys h))
                     :let [owned (filterv members (map ::entity/key (equipment-picks character)))]]
                 {:kind :choice :pack pk :group g
                  :options (if (seq owned) owned (vec (sort members)))}))))
          (active-kits character)))))

(defn answer-choice
  "`character` with pack `pack-key`'s choice group `group` answered by gear `k`: an owned `k` is
   claimed, else one is added. Returns the character."
  [character pack-key group k]
  (let [n (* (get-in (pack pack-key) [:items group] 1)
             (kit-count (some #(when (= pack-key (::entity/key %)) %) (kits character))))
        c (if (pos? (spare character k)) character (add-item character k n))]
    (update-kit c pack-key #(set-held % k n))))

(defn- drop-pack-picks
  "`character` without any Equipment pick of pack `pack-key`."
  [character pack-key]
  (assoc-in character [::entity/options :equipment]
            (filterv #(not= pack-key (::entity/key %)) (equipment-picks character))))

(defn- drop-kit
  "`character` without the stored kit for `pack-key`."
  [character pack-key]
  (assoc-in character [::entity/options :kits]
            (filterv #(not= pack-key (::entity/key %)) (kits character))))

(defn resolve-orphan
  "`character` after the player answers a kit whose pack is no longer chosen: `:remove` takes off
   what the kit still holds and drops it; `:keep` keeps it as a kit the player added."
  [character pack-key answer]
  (case answer
    :remove (drop-kit (reduce (fn [c [k n]] (remove-items c k n))
                              character
                              (some #(when (= pack-key (::entity/key %)) (held %)) (kits character)))
                      pack-key)
    :keep (update-in character [::entity/options :equipment] (fnil conj [])
                     {::entity/key pack-key
                      ::entity/value {::char-equip/quantity 1 ::char-equip/equipped? true}})))

(defn take-from-kits
  "`character` after `n` of gear `k` were used or lost: kits give them up in kit order, top first;
   anything left comes from spare stock. Returns the character."
  [character k n]
  (first
   (reduce
    (fn [[c left] kit]
      (let [h (get (held kit) k 0)
            t (min h left)]
        (if (pos? t)
          [(update-kit c (::entity/key kit) #(set-held % k (- h t))) (- left t)]
          [c left])))
    [character n]
    (active-kits character))))

(defn clear-item
  "`character` with gear `k` held by no kit, as after its inventory row is removed."
  [character k]
  (reduce (fn [c kit] (if (contains? (held kit) k) (update-kit c (::entity/key kit) #(set-held % k 0)) c))
          character
          (kits character)))

(defn deficits
  "What the kit for `pack-key` is short of, as [[item-key missing]] in the pack's order."
  [character pack-key]
  (let [kit (get (kits character) (kit-index character pack-key))
        h (held kit)]
    (vec (for [[k t] (targets kit) :let [m (- t (get h k 0))] :when (pos? m)] [k m]))))

(defn refill
  "`character` with the kit for `pack-key` claiming spare stock up to its targets. Returns
   {:character c :moved [[item-key n]]}."
  [character pack-key]
  (reduce
   (fn [{c :character :as acc} [k missing]]
     (let [n (min missing (spare c k))]
       (if (pos? n)
         {:character (update-kit c pack-key #(set-held % k (+ (get (held %) k 0) n)))
          :moved (conj (:moved acc) [k n])}
         acc)))
   {:character character :moved []}
   (deficits character pack-key)))

(defn reorganize
  "`character` with every active kit, top first, claiming spare stock up to its targets. Returns
   {:character c :moved [[pack-key [[item-key n]]]]}, only kits that moved anything."
  [character]
  (reduce
   (fn [{c :character :as acc} kit]
     (let [{c' :character moved :moved} (refill c (::entity/key kit))]
       {:character c' :moved (cond-> (:moved acc) (seq moved) (conj [(::entity/key kit) moved]))}))
   {:character character :moved []}
   (active-kits character)))

(defn restock
  "`character` with the kit for `pack-key` filled: `:mine-first` refills from spare stock, then
   adds the rest; `:all-new` adds everything missing. Returns {:character c :moved [[k n]]
   :added [[k n]]}."
  [character pack-key mode]
  (let [{c :character moved :moved} (if (= mode :mine-first)
                                       (refill character pack-key)
                                       {:character character :moved []})
        missing (deficits c pack-key)]
    {:character (reduce (fn [c [k n]]
                          (-> (add-item c k n)
                              (update-kit pack-key #(set-held % k (+ (get (held %) k 0) n)))))
                        c missing)
     :moved moved
     :added missing}))

(defn restock-could-use-spare
  "The spare stock that could fill the kit for `pack-key`, as [[item-key n]]; empty when none."
  [character pack-key]
  (vec (for [[k m] (deficits character pack-key)
             :let [n (min m (spare character k))]
             :when (pos? n)]
         [k n])))

(defn fillable
  "Spare stock some active kit is short of, as [[item-key n]] in first-seen order: what Reorganize
   would move."
  [character]
  (let [wanted (reduce (fn [m kit] (reduce (fn [m [k n]] (update m k (fnil + 0) n))
                                           m (deficits character (::entity/key kit))))
                       (array-map) (active-kits character))]
    (vec (for [[k n] wanted :let [f (min n (spare character k))] :when (pos? f)] [k f]))))

(defn use-first
  "`character` with the kit for `pack-key` moved to the top of the kit order."
  [character pack-key]
  (let [ks (kits character)
        kit (some #(when (= pack-key (::entity/key %)) %) ks)]
    (cond-> character
      kit (assoc-in [::entity/options :kits] (into [kit] (remove #(= pack-key (::entity/key %))) ks)))))

(defn held-items
  "What the kit for `pack-key` holds of the items it shows (`tracked`), as [[item-key n]] in the
   order its summary line lists them: low items emptiest first, then the full ones. Durable gear it
   does not show is left out, so \"Remove kit and items\" leaves it."
  [character pack-key]
  (let [rows (tracked (some #(when (= pack-key (::entity/key %)) %) (kits character)))]
    (mapv (juxt :key :held)
          (concat (sort-by #(/ (:held %) (:target %)) (filter :low? rows))
                  (remove :low? rows)))))

(defn remove-kit
  "`character` without the kit for `pack-key`; its items stay, as spare stock. A pack chosen in
   starting equipment keeps a removed marker so the kit is not made again."
  [character pack-key]
  (let [c (drop-pack-picks character pack-key)]
    (if ((sourced c) pack-key)
      (update-kit c pack-key (fn [_] {::entity/key pack-key
                                      ::entity/options {:held [] :removed [{::entity/key :removed}]}}))
      (drop-kit c pack-key))))

(defn add-kit
  "`character` with pack `pack-key` added as a kit: its items added to the inventory and a kit
   holding them. Returns {:character c :ask [...]}, as `repair`."
  [character pack-key]
  (let [c (cond-> character
            (some-> (kit-index character pack-key) (#(removed? (get (kits character) %))))
            (drop-kit pack-key))
        c (cond-> c
            (not-any? #(= pack-key (::entity/key %)) (equipment-picks c))
            (update-in [::entity/options :equipment] (fnil conj [])
                       {::entity/key pack-key
                        ::entity/value {::char-equip/quantity 1 ::char-equip/equipped? true}}))]
    (repair c)))

(defn tracked
  "The items the kit `kit` shows: every item a pack holds more than one of, and any it holds less
   of than its target. Returns [{:key k :held n :target t :low? b}] in the pack's order."
  [kit]
  (let [h (held kit)
        c (kit-count kit)]
    (vec (for [[k t] (targets kit)
               :let [n (get h k 0)]
               :when (or (> t c) (< n t))]
           {:key k :held n :target t :low? (< n t)}))))

(defn shares
  "How gear `k` is split among `character`'s active kits that show it, plus spare stock. Returns
   {:kits [{:pack pk :held n :target t :low? b}] :spare n}, or nil when no kit shows `k`."
  [character k]
  (let [ks (vec (for [kit (active-kits character)
                      :let [row (some #(when (= k (:key %)) %) (tracked kit))]
                      :when row]
                  (assoc row :pack (::entity/key kit))))]
    (when (seq ks) {:kits ks :spare (spare character k)})))

(defn- pluralize
  "English plural of the lower-case word `w`."
  [w]
  (cond (re-find #"(s|x|ch|sh)$" w) (str w "es")
        (re-find #"[^aeiou]y$" w) (str (subs w 0 (dec (count w))) "ies")
        :else (str w "s")))

(defn count-label
  "`n` of gear `k` in words: \"3 torches\", \"6 days of rations\", \"2 flasks of oil\"."
  [k n]
  (let [{nm :name container :sell-container} (equipment5e/equipment-map k)
        base (s/lower-case (or nm (name k)))
        one? (= 1 n)
        n' (str n)]
    (cond
      (= k :rations-1-day) (str n' (if one? " day" " days") " of rations")
      container (let [c (name container)] (str n' " " (if one? c (pluralize c)) " of " base))
      :else (str n' " " (if one? base (pluralize base))))))

(defn list-label
  "[[item-key n]] in words, joined \" · \": \"3 torches · 2 days of rations\"."
  [items]
  (s/join " · " (map (fn [[k n]] (count-label k n)) items)))

(defn unbundled-label
  "The Re-bundle strip's line for unbundled `items` ([[item-key n]]): \"Unbundled: 3 torches · 2 days
   of rations\", the two largest then \"N more\" past three. Non-breaking spaces keep each item, and
   the space before each dot, on one line."
  [items]
  (let [nb #(s/replace % " " "\u00a0")
        shown (if (> (count items) 3) (take 2 (sort-by (comp - second) items)) items)
        parts (cond-> (mapv (fn [[k n]] (nb (count-label k n))) shown)
                (> (count items) 3) (conj (nb (str (- (count items) 2) " more"))))]
    (str "Unbundled: " (s/join "\u00a0· " parts))))

(defn item-short-name
  "Gear `k`'s name for kit lines, without its parenthetical or comma qualifier: \"Rations\" for
   \"Rations (1 day)\", \"Rope\" for \"Rope, hempen\"."
  [k]
  (-> (:name (equipment5e/equipment-map k) (name k))
      (s/replace #"\s*\(.*\)$" "")
      (s/replace #",.*$" "")))

(defn held-label
  "[[item-key n]] as kit counts: \"Torch 7 · Rations 10\"."
  [items]
  (s/join " · " (map (fn [[k n]] (str (item-short-name k) " " n)) items)))

(defn contents-line
  "What a kit's summary line shows from `rows` (`tracked`'s) within about `budget`
   characters (40 at 390px): low items, emptiest first once they would not fit, then folded counts.
   Returns {:low rows :more-low n :more-full n}, or {:full rows} when nothing is low."
  [rows budget]
  (let [low (filter :low? rows)
        full (remove :low? rows)
        label #(str (item-short-name (:key %)) " " (:held %) "/" (:target %))
        full-tag (when (seq full) (str "+" (count full) " full"))
        cost (fn [rows more] (+ (reduce + (map #(inc (count (label %))) rows))
                                (if (pos? more) (inc (count (str "+" more " low"))) 0)
                                (count (or full-tag ""))))]
    (if (empty? low)
      {:full (vec full)}
      (let [shown (if (<= (cost low 0) budget)
                    (vec low)
                    (let [by-emptiest (sort-by #(/ (:held %) (:target %)) low)]
                      (loop [n 1]
                        (if (and (< n (count low))
                                 (<= (cost (take (inc n) by-emptiest) (- (count low) (inc n))) budget))
                          (recur (inc n))
                          (vec (take n by-emptiest))))))]
        {:low shown :more-low (- (count low) (count shown)) :more-full (count full)}))))

(defn short-name
  "The kit for pack `pack-key` named for its line under an item: \"Explorer's\"."
  [pack-key]
  (s/replace (:name (pack pack-key) (name pack-key)) #" Pack$" ""))

(defn act
  "`character` after the kit action `action` with `args`, as the Kits block applies it, and the
   message its Undo line shows (nil for actions with no Undo). Returns {:character c :message m}."
  [character action & args]
  ;; Kit copy: action "Re-bundle" / status "Unbundled".
  ;; Fallbacks, in order: "Rebundle" (Wiktionary only, not in Merriam-Webster or Oxford),
  ;;   then "Reorganize" / "Unassigned".
  ;; "Reorganize" is RESERVED for future moves between camp/containers. Don't use it for kits.
  ;; Don't use "Reorganize" or "Re-bundle" for sorting; the player's preferred sort order is a separate setting.
  ;; Rejected: "Spare"/"Extra" (false when a kit is low), "Loose" (implies kits are bags),
  ;;   "Untracked" (sounds like the items are missing from inventory), "Use spares" (reads as "spend").
  ;; Hyphenated "Re-bundle" chosen for non-native English readers.
  (let [kit-name #(:name (pack %))
        moved-msg (fn [pk moved] (str (kit-name pk) ": +" (list-label moved) " re-bundled"))]
    (case action
      :rebundle (let [{c :character moved :moved} (reorganize character)]
                  {:character c :message (s/join " · " (map (fn [[pk m]] (moved-msg pk m)) moved))})
      :restock (let [[pk mode] args
                     {c :character moved :moved added :added} (restock character pk mode)]
                 {:character c
                  :message (s/join " · " (cond-> []
                                           (seq moved) (conj (moved-msg pk moved))
                                           (seq added) (conj (str "+" (list-label added) " added"))))})
      :add (let [[pk] args]
             {:character (:character (add-kit character pk))
              :message (str (kit-name pk) " added")})
      :move-to-top {:character (apply use-first character args)}
      :remove-one (let [[pk] args
                        c (remove-items character pk 1)]
                    {:character (set-kit-count c pk (max 1 (source-count c pk)))
                     :message (str "Removed one " (kit-name pk) ". Items stay in inventory.")})
      :remove (let [[pk] args]
                {:character (remove-kit character pk)
                 :message (str "Removed " (kit-name pk) ". Items stay in inventory.")})
      :remove-with-items (let [[pk] args
                               h (vec (filter (comp pos? second) (held-items character pk)))]
                           {:character (remove-kit (reduce (fn [c [k n]] (remove-items c k n)) character h) pk)
                            :message (str "Removed " (kit-name pk) (when (seq h) (str " and " (held-label h))) ".")})
      :answer-choice {:character (apply answer-choice character args)}
      :resolve-orphan {:character (apply resolve-orphan character args)})))
