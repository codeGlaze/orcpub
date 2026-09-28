(ns orcpub.dnd.e5.library-links
  "Every kind of link one homebrew item holds to another, as data, and the two operations on a
   link: read its targets, and rewrite one. See docs/kb/homebrew-keys-design.md §2.

   A link's `:path` is a sequence of steps into the item:
     a keyword   get that key (absent: no link)
     :*vals      each value of a map
     :*keys      each key of a map (last step only)
     :*true-keys each key of a map whose value is truthy (last step only); rewriting renames
                 every key, truthy or not
     :*each      each element of a vector, list or set
     [:when k v] continue only where the map's k is v")

(def links
  "One entry per kind of link.
   :from    the set of content types that hold it, or :any for a field that means one thing
            wherever it appears
   :to      the content type it names
   :path    where the target sits in the holder (see the ns doc)
   :by      :key, or :name for a link that names its target by display name
   :bundle  how a share link treats it: :follow (the holder needs the target), :reverse (the
            target needs the holder) or :none"
  [{:id :subclass->class :from #{:orcpub.dnd.e5/subclasses} :to :orcpub.dnd.e5/classes
    :path [:class] :by :key :bundle :follow}
   {:id :subrace->race :from #{:orcpub.dnd.e5/subraces} :to :orcpub.dnd.e5/races
    :path [:race] :by :key :bundle :follow}
   {:id :spell->class-list :from #{:orcpub.dnd.e5/spells} :to :orcpub.dnd.e5/classes
    :path [:spell-lists :*true-keys] :by :key :bundle :reverse}
   {:id :class->borrowed-list :from #{:orcpub.dnd.e5/classes :orcpub.dnd.e5/subclasses}
    :to :orcpub.dnd.e5/classes
    :path [:spellcasting :spell-list-kw] :by :key :bundle :follow}
   {:id :class->own-list :from #{:orcpub.dnd.e5/classes :orcpub.dnd.e5/subclasses}
    :to :orcpub.dnd.e5/spells
    :path [:spellcasting :spell-list :*vals :*each] :by :key :bundle :follow}
   {:id :paladin-spells :from :any :to :orcpub.dnd.e5/spells
    :path [:paladin-spells :*vals :*vals] :by :key :bundle :follow}
   {:id :cleric-spells :from :any :to :orcpub.dnd.e5/spells
    :path [:cleric-spells :*vals :*vals] :by :key :bundle :follow}
   {:id :warlock-spells :from :any :to :orcpub.dnd.e5/spells
    :path [:warlock-spells :*vals :*vals] :by :key :bundle :follow}
   {:id :level-modifier-spell :from :any :to :orcpub.dnd.e5/spells
    :path [:level-modifiers :*each [:when :type :spell] :value :key] :by :key :bundle :follow}
   {:id :granted-spell :from :any :to :orcpub.dnd.e5/spells
    :path [:spells :*each :value :key] :by :key :bundle :follow}
   {:id :level-selection :from :any :to :orcpub.dnd.e5/selections
    :path [:level-selections :*each :type] :by :key :bundle :follow}
   {:id :race-prerequisite :from :any :to :orcpub.dnd.e5/races
    :path [:path-prereqs :race :*keys] :by :key :bundle :none}
   {:id :granted-language :from :any :to :orcpub.dnd.e5/languages
    :path [:props :language :*keys] :by :key :bundle :follow}
   {:id :language-by-name :from :any :to :orcpub.dnd.e5/languages
    :path [:languages :*each] :by :name :bundle :follow}
   {:id :encounter->monster :from #{:orcpub.dnd.e5/encounters} :to :orcpub.dnd.e5/monsters
    :path [:creatures :*each [:when :type :monster] :creature :monster] :by :key :bundle :follow}])

(defn holds?
  "True when items of `content-type` can hold `link`."
  [link content-type]
  (let [from (:from link)]
    (or (= :any from) (contains? from content-type))))

(defn- collect [x [step & more :as steps]]
  (cond
    (empty? steps) (when (some? x) [x])
    (vector? step) (let [[_ k v] step]
                     (when (and (map? x) (= v (get x k))) (collect x more)))
    (= :*vals step) (when (map? x) (mapcat #(collect % more) (vals x)))
    (= :*keys step) (when (map? x) (keys x))
    (= :*true-keys step) (when (map? x) (keep (fn [[k v]] (when v k)) x))
    (= :*each step) (when (and (coll? x) (not (map? x))) (mapcat #(collect % more) x))
    :else (when (map? x) (collect (get x step) more))))

(defn targets
  "The values `link` names in `item`: keys, or names for a `:by :name` link. Empty when the item
   does not hold it."
  [link item]
  (vec (collect item (:path link))))

(defn- rewrite [x [step & more :as steps] f]
  (cond
    (empty? steps) (f x)
    (vector? step) (let [[_ k v] step]
                     (if (and (map? x) (= v (get x k))) (rewrite x more f) x))
    (= :*vals step) (if (map? x)
                      (into (empty x) (map (fn [[k v]] [k (rewrite v more f)])) x)
                      x)
    (#{:*keys :*true-keys} step) (if (map? x)
                      (into (empty x) (map (fn [[k v]] [(f k) v])) x)
                      x)
    (= :*each step) (cond
                      (or (vector? x) (set? x)) (into (empty x) (map #(rewrite % more f)) x)
                      (seq? x) (doall (map #(rewrite % more f) x))
                      :else x)
    :else (if (and (map? x) (contains? x step))
            (update x step rewrite more f)
            x)))

(defn retarget
  "`item` with every target of `link` equal to `old` replaced by `new`. Leaves the item unchanged
   where it does not hold the link.
   GOTCHA: a `:by :name` link is left alone; renaming a key does not change a name."
  [link item old new]
  (if (= :name (:by link))
    item
    (rewrite item (:path link) #(if (= % old) new %))))

(defn rename-target
  "`item` with every name `old` that the `:by :name` `link` holds replaced by `new`."
  [link item old new]
  (if (= :name (:by link))
    (rewrite item (:path link) #(if (= % old) new %))
    item))

(def former-key-cap
  "Former keys kept per item. A repair aid, not an archive."
  4)

(defn former-keys
  "`item`'s former keys, oldest first. Reads the singular `:former-key` as a one-entry history."
  [item]
  (or (:former-keys item)
      (some-> (:former-key item) vector)
      []))

(defn record-former-key
  "Append `old-key` to `item`'s `:former-keys`, capped at `former-key-cap`. Drops `:former-key`.

   Entry 0 is the prime key, minted at creation, and is never evicted — kept on the assumption
   that the oldest characters point at the oldest key. Overflow is taken from the middle.
   Six renames: `[:one :three :four :five]` under `:six` — `:two` gave way, `:one` stays."
  [item old-key]
  (let [prior (former-keys item)
        ks    (if (some #{old-key} prior) prior (conj (vec prior) old-key))]
    (-> item
        (dissoc :former-key)
        (assoc :former-keys (if (<= (count ks) former-key-cap)
                              ks
                              (into [(first ks)] (take-last (dec former-key-cap) ks)))))))

(defn repoint
  "`plugin` (one source, {content-type {key item}}) with every link naming `old` in `to-type`
   retargeted to `new`, in every item that can hold it."
  [plugin to-type old new]
  (let [relevant (filter #(= to-type (:to %)) links)]
    (reduce-kv
     (fn [p ct items]
       (if (and (qualified-keyword? ct) (map? items))
         (assoc p ct (reduce-kv
                      (fn [m k item]
                        (assoc m k (reduce #(if (and (map? %1) (holds? %2 ct))
                                              (retarget %2 %1 old new)
                                              %1)
                                           item relevant)))
                      {} items))
         (assoc p ct items)))
     {} plugin)))
