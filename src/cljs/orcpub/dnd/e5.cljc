(ns orcpub.dnd.e5
  (:require #?(:cljs [cljs.spec.alpha :as spec])
            #?(:clj [clojure.spec.alpha :as spec])
            #?(:cljs [cljs.reader :as reader])
            #?(:clj [clojure.edn :as edn])
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.languages :as languages]
            [orcpub.common :as common]))

(spec/def ::spells (spec/map-of common/keyword-starts-with-letter?
                                ::spells/homebrew-spell))

(spec/def ::content-keyword (fn [v] (or (= v :disabled?)
                                        (and (qualified-keyword? v)
                                             (common/keyword-starts-with-letter? v)
                                             (= (namespace v) "orcpub.dnd.e5")))))

(spec/def ::option-pack string?)

(spec/def ::homebrew-item (spec/keys :req-un [::option-pack]))

(spec/def ::homebrew-items (spec/map-of common/keyword-starts-with-letter?
                                        ::homebrew-item))

(spec/def ::plugin (spec/map-of ::content-keyword
                                (spec/or :items ::homebrew-items
                                         :bool boolean?)))

(spec/def ::plugins (spec/map-of string? ::plugin))


(defn salvage-plugins
  "Partition a loaded multi-plugin map into `{:kept … :rejected …}` so one corrupt
   source can't make the loader discard the whole homebrew library.

   `valid-plugin?` is injected (db.cljs passes `#(spec/valid? ::plugin %)`) to keep
   this pure and JVM-testable without spec machinery. A non-map input yields two
   empty maps; the caller preserves the raw string in that case."
  [valid-plugin? plugins]
  (if (map? plugins)
    (reduce-kv (fn [acc plugin-name plugin]
                 (update acc
                         (if (valid-plugin? plugin) :kept :rejected)
                         assoc plugin-name plugin))
               {:kept {} :rejected {}}
               plugins)
    {:kept {} :rejected {}}))


(defn content-section?
  "A key naming a content type (`:orcpub.dnd.e5/spells`), as opposed to `:disabled?`."
  [k]
  (and (qualified-keyword? k) (= "orcpub.dnd.e5" (namespace k))))

(def default-option-source
  "The built-in source that content with no source of its own goes to."
  "Default Option Source")

(defn- source-name? [x]
  (and (string? x) (some? (re-find #"\S" x))))

(defn declared-source
  "The one source a plugin's entries name in :option-pack, or nil when they name none
   or disagree."
  [plugin]
  (let [packs (distinct
               (for [[k items] plugin
                     :when (and (content-section? k) (map? items))
                     [_ item] items
                     :when (map? item)
                     :let [p (:option-pack item)]
                     :when (source-name? p)]
                 p))]
    (when (= 1 (count packs))
      (first packs))))

(defn source-for
  "Where an entry with no source goes: the source it is filed under when that has a
   name, else the one source its sibling entries name, else the default."
  [source-key plugin]
  (if (source-name? source-key)
    source-key
    (or (declared-source plugin) default-option-source)))

(defn fill-missing-sources
  "Give each entry with no usable :option-pack (absent, nil, blank, not text) the source
   `source`. Returns {:plugin p :filled [{:section :key}]}."
  [plugin source]
  (reduce-kv
   (fn [acc k items]
     (if (and (content-section? k) (map? items))
       (reduce-kv (fn [acc item-key item]
                    (if (and (map? item) (not (source-name? (:option-pack item))))
                      (-> acc
                          (assoc-in [:plugin k item-key :option-pack] source)
                          (update :filled conj {:section k :key item-key}))
                      acc))
                  acc
                  items)
       acc))
   {:plugin plugin :filled []}
   plugin))

(defn fill-library-sources
  "fill-missing-sources over a {source-name plugin} library, each source filling from
   source-for. Returns {:library l :filled [{:source :section :key :to}]}."
  [library]
  (if-not (map? library)
    {:library library :filled []}
    (reduce-kv
     (fn [acc src plugin]
       (if (map? plugin)
         (let [to (source-for src plugin)
               {p :plugin filled :filled} (fill-missing-sources plugin to)]
           (-> acc
               (assoc-in [:library src] p)
               (update :filled into (map #(assoc % :source src :to to) filled))))
         (assoc-in acc [:library src] plugin)))
     {:library {} :filled []}
     library)))

(defn damaged-section?
  "A content section holding neither entries nor the true/false the format allows --
   text, a list, a number. It cannot load as it is."
  [k v]
  (and (content-section? k) (not (map? v)) (not (boolean? v))))

(defn- read-back
  "Text read back as data, or nil when it does not read."
  [s]
  (try #?(:clj (edn/read-string s) :cljs (reader/read-string s))
       (catch #?(:clj Exception :cljs :default) _ nil)))

(defn- list->entries
  "Entries stored as a list, keyed the way the library keys them: each entry's own
   :key, or the key its :name derives. nil when any element is not an entry, has
   neither, or repeats a key -- guessing there would invent or silently lose content."
  [xs]
  (reduce (fn [m item]
            (let [k (when (map? item)
                      (or (:key item)
                          (when (string? (:name item)) (common/name-to-kw (:name item)))))]
              (if (and (keyword? k) (not (contains? m k)))
                (assoc m k (assoc item :key k))
                (reduced nil))))
          {}
          xs))

(defn mend-section
  "Put a content section that still holds its data back in the shape the library uses.
   Returns {:section v} unchanged, {:section v :repair how}, {:dropped how} for an empty
   section, or {:damaged v} when nothing readable is left for code to recover.

   These are the shapes real damage takes. Left alone, a section stored as text or as
   a list imported as \"successful\", loaded silently, and made export refuse the source."
  [v]
  (cond
    (or (map? v) (boolean? v)) {:section v}
    (or (nil? v)
        (and (coll? v) (empty? v))
        (and (string? v) (re-matches #"\s*" v))) {:dropped :empty-section}
    (string? v) (let [x (read-back v)]
                  (cond
                    (map? x) {:section x :repair :section-from-text}
                    (and (sequential? x) (seq x)) (if-let [m (list->entries x)]
                                                    {:section m :repair :section-from-text}
                                                    {:damaged v})
                    :else {:damaged v}))
    (sequential? v) (if-let [m (list->entries v)]
                      {:section m :repair :section-from-list}
                      {:damaged v})
    :else {:damaged v}))

(defn mend-cards
  "Put a :traits or :options value back into a list of cards (maps). Text in the list
   becomes a card with that name and anything else in it is dropped; a lone card or a
   lone name becomes a list of one; any other value is removed. Returns {:value v}
   when nothing changed, {:value v :repair :cards-mended :named n :dropped n}, or
   {:remove? true :repair :cards-not-a-list}."
  [v]
  (cond
    (nil? v) {:value v}
    (map? v) {:value [v] :repair :cards-mended :named 0 :dropped 0}
    (source-name? v) {:value [{:name v}] :repair :cards-mended :named 1 :dropped 0}
    (sequential? v)
    (let [named (count (filter source-name? v))
          cards (vec (keep #(cond (map? %) % (source-name? %) {:name %}) v))
          dropped (- (count v) (count cards))]
      (if (and (vector? v) (zero? named) (zero? dropped))
        {:value v}
        {:value cards :repair :cards-mended :named named :dropped dropped}))
    :else {:remove? true :repair :cards-not-a-list}))

(defn- entry-key? [k]
  (and (keyword? k) (common/keyword-starts-with-letter? k)))

(defn mend-entry
  "Repair damage inside one entry, filed under `entry-key` in section `section-key`. A
   :key that is not a letter-first keyword takes the key the entry is filed under;
   :traits, and a selection's :options, go through mend-cards; a spell's :spell-lists
   that is not a map of classes is removed, leaving the spell on no class list. A sound
   entry comes back untouched. Returns {:entry e :repairs [{:field :repair ...}]}."
  [section-key entry-key entry]
  (if-not (map? entry)
    {:entry entry :repairs []}
    (let [restore-key? (and (contains? entry :key)
                            (not (entry-key? (:key entry)))
                            (entry-key? entry-key))
          drop-spell-lists? (and (= section-key ::spells)
                                 (some? (:spell-lists entry))
                                 (not (map? (:spell-lists entry))))
          fields (cond-> [:traits] (= section-key ::selections) (conj :options))]
      (reduce (fn [{e :entry :as acc} field]
                (if-not (contains? e field)
                  acc
                  (let [{:keys [value repair remove?] :as r} (mend-cards (get e field))]
                    (cond
                      remove? (-> acc
                                  (update :entry dissoc field)
                                  (update :repairs conj {:field field :repair repair}))
                      repair (-> acc
                                 (assoc-in [:entry field] value)
                                 (update :repairs conj (-> r (dissoc :value) (assoc :field field))))
                      :else acc))))
              {:entry (cond-> entry
                        restore-key? (assoc :key entry-key)
                        drop-spell-lists? (dissoc :spell-lists))
               :repairs (cond-> []
                          restore-key? (conj {:field :key :repair :key-restored})
                          drop-spell-lists? (conj {:field :spell-lists :repair :spell-lists-not-a-map}))}
              fields))))

(defn- mend-entries [section-key section]
  (reduce-kv (fn [acc entry-key entry]
               (let [{e :entry rs :repairs} (mend-entry section-key entry-key entry)]
                 (cond-> acc
                   (seq rs) (-> (assoc-in [:section entry-key] e)
                                (update :repairs into (map #(assoc % :key entry-key) rs))))))
             {:section section :repairs []}
             section))

(defn mend-plugin
  "`mend-section` over one source, then `mend-entry` over each section's entries.
   Returns {:plugin p :repairs [{:section k :repair how}]}; an entry repair also names
   its :key and :field. A healthy source has no repairs, so nothing is written back.
   A section nothing can be read from is left where it is, for salvage to set aside."
  [plugin]
  (reduce-kv
   (fn [acc k v]
     (if-not (content-section? k)
       acc
       (let [{:keys [section repair dropped]} (mend-section v)]
         (if dropped
           (-> acc (update :plugin dissoc k) (update :repairs conj {:section k :repair dropped}))
           (let [{entries :section entry-repairs :repairs} (when (map? section)
                                                              (mend-entries k section))]
             (cond-> acc
               repair (-> (assoc-in [:plugin k] section)
                          (update :repairs conj {:section k :repair repair}))
               (seq entry-repairs) (-> (assoc-in [:plugin k] entries)
                                       (update :repairs into (map #(assoc % :section k) entry-repairs)))))))))
   {:plugin plugin :repairs []}
   plugin))

(defn mend-library
  "`mend-plugin` over every source of a {source-name source} library, reading back the
   whole library, or a source, stored as text first. Text only counts as a library when
   it reads as one: a map keyed by source names. Returns {:library l :repairs [{:source
   :section :repair}]}. Runs before salvage on load, so each repair is written back once."
  [library]
  (let [from-text (when (string? library) (read-back library))
        whole? (and (map? from-text) (every? string? (keys from-text)))
        library (if whole? from-text library)]
    (if-not (map? library)
      {:library library :repairs []}
      (cond-> (reduce-kv
               (fn [acc src plugin]
                 (let [from-text (when (string? plugin) (read-back plugin))
                       plugin (if (map? from-text) from-text plugin)]
                   (if (map? plugin)
                     (let [{p :plugin rs :repairs} (mend-plugin plugin)]
                       (-> acc
                           (assoc-in [:library src] p)
                           (update :repairs into
                                   (cond->> (map #(assoc % :source src) rs)
                                     (map? from-text) (cons {:source src :repair :source-from-text})))))
                     (assoc-in acc [:library src] plugin))))
               {:library {} :repairs []}
               library)
        whole? (update :repairs #(into [{:repair :library-from-text}] %))))))

(defn mend-import-data
  "Repair freshly read import data before anything else walks it: a whole file stored
   as text, then either a single source or a {source-name source} library.
   Returns {:data d :repairs [...]}; `d` is not a map only when nothing could be read."
  [data]
  (let [from-text (when (string? data) (read-back data))
        data (if (map? from-text) from-text data)
        whole (when (map? from-text) [{:repair :file-from-text}])]
    (cond
      (not (map? data))
      {:data data :repairs []}

      (and (seq data) (every? string? (keys data)))
      (let [{l :library rs :repairs} (mend-library data)]
        {:data l :repairs (into (vec whole) rs)})

      :else
      (let [{p :plugin rs :repairs} (mend-plugin data)]
        {:data p :repairs (into (vec whole) rs)}))))

(defn salvage-plugin-items
  "Per-ENTRY salvage of ONE source. Walks each content group and splits its items
   by `valid-item?` (a fn of [content-type item-key item]) — valid items go to
   :kept, invalid to :rejected. Non-content entries (e.g. `:disabled?`, or a
   content group that is a boolean) stay with :kept. Returns {:kept <plugin>
   :rejected <plugin>}; a content-type key is absent on a side that has nothing.

   This is what lets ONE bad entry be siloed WITHOUT quarantining its whole source:
   the source keeps its valid items, only the broken ones are set aside for repair.
   `valid-item?` is injected (content-specs supplies the load-floor version) so this
   stays pure/JVM-testable. Non-map input yields two empty maps."
  [valid-item? plugin]
  (if (map? plugin)
    (reduce-kv
     (fn [acc k v]
       (cond
         (and (qualified-keyword? k) (map? v))
         (reduce-kv
          (fn [a ik iv]
            (assoc-in a [(if (valid-item? k ik iv) :kept :rejected) k ik] iv))
          acc
          v)
         ;; A content section that is text, a list, a number cannot load. Kept, it
         ;; loaded silently and made export refuse the whole source; set aside, the
         ;; needs-attention panel can export or discard it. mend-section has already
         ;; recovered whatever was readable before this runs.
         (damaged-section? k v)
         (assoc-in acc [:rejected k] v)
         ;; non-content-group entry (or boolean content group) — keep as-is
         :else
         (assoc-in acc [:kept k] v)))
     {:kept {} :rejected {}}
     plugin)
    {:kept {} :rejected {}}))

(defn salvage-library-items
  "Per-ENTRY salvage across a whole library `{source-name plugin}`. Returns
   {:kept {name plugin} :rejected {name plugin}}: each source's valid items in
   :kept, its invalid items (a partial plugin) in :rejected. A source is absent
   from a side when it has nothing there. Non-map input yields two empty maps."
  [valid-item? plugins]
  (if (map? plugins)
    (reduce-kv
     (fn [acc name plugin]
       (if-not (map? plugin)
         ;; A whole source that is not a map cannot load. Set it aside as it is --
         ;; dropped from both sides, a write-back of the library would erase it.
         (assoc-in acc [:rejected name] plugin)
         (let [{:keys [kept rejected]} (salvage-plugin-items valid-item? plugin)]
           (cond-> acc
             (seq kept)     (assoc-in [:kept name] kept)
             (seq rejected) (assoc-in [:rejected name] rejected)))))
     {:kept {} :rejected {}}
     plugins)
    {:kept {} :rejected {}}))

(defn reconcile-rejected
  "Maintain the name-keyed quarantine map (`plugins:rejected`) across loads: merge
   this load's rejected sources into the already-quarantined ones (latest-wins per
   name, so nothing accumulates), then drop any whose name reappears in `kept` — a
   repaired source clears itself. Returns the cleaned `{name → bad-source}` map
   (caller removes the storage key when empty).

   Pure/dependency-free for JVM tests. Non-map `old-rejected` is treated as empty."
  [old-rejected new-rejected kept]
  (let [old (if (map? old-rejected) old-rejected {})
        incoming (if (map? new-rejected) new-rejected {})
        merged (merge old incoming)]
    (apply dissoc merged (keys kept))))


(defn- distinct-key
  "Pick `base` if free in `taken` (a map/set of used keys), else append -2, -3, …
   until free — so repair never drops an item when two names derive the same key."
  [taken base]
  (if-not (contains? taken base)
    base
    (loop [n 2]
      (let [candidate (keyword (str (name base) "-" n))]
        (if (contains? taken candidate) (recur (inc n)) candidate)))))

(defn rekey-content-group
  "Re-key only items whose CURRENT key is invalid (the keyword trap — a key not
   starting with a letter, e.g. `:9-lives`): move to the key derived from the
   corrected `:name` and sync `:key`. Already-valid keys are left untouched (don't
   disturb existing references); collisions get a numeric suffix; an item with no
   usable `:name` keeps its original key (validation still flags it).

   Pure so the JVM suite can cover re-key/collision/no-name."
  [items]
  ;; Reserve the already-valid keys. distinct-key is seeded with these plus the
  ;; keys emitted so far, so a re-keyed item can't collide with — and be clobbered
  ;; by — a valid sibling processed later.
  (let [reserved (into #{} (comp (map key)
                                 (filter common/keyword-starts-with-letter?))
                       items)]
    (reduce (fn [acc [k item]]
              (if-let [derived (and (not (common/keyword-starts-with-letter? k))
                                    (string? (:name item))
                                    (common/name-to-kw (:name item)))]
                ;; invalid key + a usable name → move to the name-derived key
                (let [new-key (distinct-key (into reserved (keys acc)) derived)]
                  (assoc acc new-key (assoc item :key new-key)))
                ;; valid key, or no name to derive from → leave the item untouched
                (assoc acc k item)))
            {}
            items)))

(defn rekey-plugin
  "Apply `rekey-content-group` to every content group in a source map
   (`{content-type {item-key item}}`); non-content-group entries (e.g. `:disabled?`)
   pass through. The re-key half of a quarantine repair: after the user fixes a
   trapped item's name, sync its map key so the source can pass `::plugin`."
  [plugin]
  (reduce-kv (fn [acc k v]
               (assoc acc k (if (and (qualified-keyword? k) (map? v))
                              (rekey-content-group v)
                              v)))
             {}
             plugin))


(defn invalid-keyed-items
  "For a source map (`{content-type {item-key item}}`), return a seq of
   `{:content-type :item-key :name}` for items whose KEY is invalid — the
   keyword-trap cases that a rename can repair. Drives the quarantine repair UI."
  [plugin]
  (for [[ct items] plugin
        :when (and (qualified-keyword? ct) (map? items))
        [k item] items
        :when (not (common/keyword-starts-with-letter? k))]
    {:content-type ct :item-key k :name (:name item)}))


(defn merge-plugins
  "Merge two sources section by section. Where either side is not a map -- a damaged
   section, or a damaged source, set aside on an earlier load -- the newer value wins:
   `merge` on text throws, and this runs while the app is starting."
  [plugin-1 plugin-2]
  (if (and (map? plugin-1) (map? plugin-2))
    (merge-with (fn [a b] (if (and (map? a) (map? b)) (merge a b) b))
                plugin-1
                plugin-2)
    plugin-2))

(defn merge-all-plugins [all-plugins-1 all-plugins-2]
  (merge-with
   merge-plugins
   all-plugins-1
   all-plugins-2))

(defn reconcile-rejected-items
  "Per-ENTRY analog of `reconcile-rejected`. Merge this load's rejected entries
   into the already-quarantined ones (latest-wins per item), then drop any item
   that now appears in `kept` (repaired or re-imported valid), and drop content
   groups / sources left empty. Keeps an item from being BOTH live and quarantined,
   and lets a fixed entry self-clear. Returns the cleaned {source partial-plugin}."
  [old-rejected new-rejected kept]
  (let [merged (merge-all-plugins (if (map? old-rejected) old-rejected {})
                                  (if (map? new-rejected) new-rejected {}))]
    (reduce-kv
     (fn [acc src plugin]
       (if-not (map? plugin)
         (assoc acc src plugin) ; a damaged source stays set aside as it is
       (let [kept-src (get kept src)
             cleaned (reduce-kv
                      (fn [p ct items]
                        (if (and (qualified-keyword? ct) (map? items))
                          (let [kept-items (get kept-src ct)
                                remaining (into {} (remove (fn [[ik _]]
                                                             (contains? kept-items ik))
                                                           items))]
                            (if (seq remaining) (assoc p ct remaining) p))
                          (assoc p ct items)))
                      {}
                      plugin)]
         (if (seq cleaned) (assoc acc src cleaned) acc))))
     {}
     merged)))

