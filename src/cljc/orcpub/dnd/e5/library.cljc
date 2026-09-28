(ns orcpub.dnd.e5.library
  "The one gate every write to the homebrew library passes: `commit`. See
   docs/kb/homebrew-keys-design.md §4."
  (:require [orcpub.common :as common]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.languages :as langs]
            [orcpub.dnd.e5.library-links :as links]
            [orcpub.dnd.e5.monsters :as monsters]
            [orcpub.dnd.e5.spells :as spells]))

(defn- content-groups [plugins]
  (for [[src plugin] plugins
        :when (map? plugin)
        [ct items] plugin
        :when (and (qualified-keyword? ct) (map? items))]
    [src ct items]))

(defn empty-library?
  "True when `plugins` holds no items."
  [plugins]
  (not-any? (fn [[_ _ items]] (seq items)) (content-groups plugins)))

(defn normalize
  "`plugins` with every item's `:key` and `:option-pack` set to the key and source it is stored
   under."
  [plugins]
  (reduce (fn [p [src ct items]]
            (assoc-in p [src ct]
                      (reduce-kv (fn [m k item]
                                   (assoc m k (if (map? item) (assoc item :key k :option-pack src) item)))
                                 {} items)))
          plugins
          (content-groups plugins)))

(defn- held
  "{content-type #{key}} across every source of `plugins`."
  [plugins]
  (reduce (fn [acc [_ ct items]] (update acc ct (fnil into #{}) (keys items)))
          {} (content-groups plugins)))

(defn broken-links
  "Links in `new` that name an item `old` held and `new` does not, as
   [{:source :type :key :name :target :target-type}]. Links to a [type key] in `retargeting` are
   being moved elsewhere and are not counted."
  [old new retargeting]
  (let [was (held old) now (held new)]
    (for [[src ct items] (content-groups new)
          [k item] items
          :when (map? item)
          link links/links
          :when (and (= :key (:by link)) (links/holds? link ct))
          target (links/targets link item)
          :let [to (:to link)]
          :when (and (contains? (get was to) target)
                     (not (contains? (get now to) target))
                     (not (contains? retargeting [to target])))]
      {:source src :type ct :key k :name (:name item) :target target :target-type to})))

(defn- invalid-keys
  "[source type key] of every item `new` stores under a key the loader would set aside, that
   `old` did not already hold there."
  [old new]
  (for [[src ct items] (content-groups new)
        k (keys items)
        :when (and (not (common/keyword-starts-with-letter? k))
                   (not (contains? (get-in old [src ct]) k)))]
    [src ct k]))

(def ^:private built-in-names
  "{content-type #{name}} of built-in content a `:by :name` link can name."
  {:orcpub.dnd.e5/languages (into #{} (map :name) langs/languages)})

(defn- follow-renamed-names
  "`new` with each `:by :name` link naming an item `old` knew by another name renamed to match,
   when nothing in `new` or built-in content still answers to the old name."
  [old new]
  (let [names (reduce (fn [acc [_ ct items]]
                        (update acc ct (fnil into #{}) (keep :name) (filter map? (vals items))))
                      built-in-names (content-groups new))
        renames (for [[src ct items] (content-groups new)
                      [k item] items
                      :let [was (get-in old [src ct k :name])]
                      :when (and (string? was) (string? (:name item)) (not= was (:name item))
                                 (not (contains? (get names ct) was)))]
                  [ct was (:name item)])
        name-links (filter #(= :name (:by %)) links/links)]
    (reduce (fn [p [ct was now]]
              (reduce (fn [p [src ict items]]
                        (assoc-in p [src ict]
                                  (reduce-kv (fn [m k item]
                                               (assoc m k (reduce #(if (and (= ct (:to %2)) (links/holds? %2 ict))
                                                                     (links/rename-target %2 %1 was now)
                                                                     %1)
                                                                  item name-links)))
                                             {} items)))
                      p (content-groups p)))
            new renames)))

(defn commit
  "What storing `new` in place of `old` does: {:plugins (normalize new) :broken [...]}, or
   {:refused {:broken [...] :invalid [...]}} when it would add a key the loader sets aside or
   leave a link pointing at nothing. `deleting?` lets a deliberate removal through and reports
   the links it broke; `retargeting` is a collection of [type key] whose links are being moved;
   `restoring?` lets keys the loader sets aside through (it sets them aside again)."
  [old new {:keys [deleting? retargeting restoring?]}]
  (let [stored (follow-renamed-names old (normalize new))
        broken (vec (broken-links old stored (set retargeting)))
        invalid (if restoring? [] (vec (invalid-keys old stored)))]
    (if (or (seq invalid) (and (seq broken) (not deleting?)))
      {:refused {:broken broken :invalid invalid}}
      {:plugins stored :broken broken})))

(defn overwritten
  "Items `old` and `new` both hold at the same source, type and key, whose content differs, as
   [{:source :type :key :name}]. The name is the one `new` gives it."
  [old new]
  (let [o (normalize old) n (normalize new)]
    (for [[src ct items] (content-groups n)
          [k item] items
          :let [was (get-in o [src ct k])]
          :when (and (some? was) (not= was item))]
      {:source src :type ct :key k :name (:name item)})))

(defn- entries
  "`plugins` as {path value}: [source] for each source, [source field] for a source's own fields,
   [source type key] for each item."
  [plugins]
  (into {}
        (for [[src plugin] plugins
              :when (map? plugin)
              e (cons [[src] ::source]
                      (for [[k v] plugin
                            e (if (and (qualified-keyword? k) (map? v))
                                (for [[ik item] v] [[src k ik] item])
                                [[[src k] v]])]
                        e))]
          e)))

(defn- rebuild [es]
  (reduce (fn [p [path v]]
            (if (= 1 (count path)) (update p (first path) #(or % {})) (assoc-in p path v)))
          {}
          (sort-by (comp count key) es)))

(defn three-way
  "`mine` applied onto `theirs`, both descended from `base`: every entry (a source, a source's own
   field, an item) that `mine` added, changed or removed since `base` is applied to `theirs`, and
   the rest of `theirs` stands. Returns {:plugins :conflicts}, a conflict being the path of an
   entry both changed, differently."
  [base mine theirs]
  (let [b (entries (normalize base)) m (entries (normalize mine)) t (entries (normalize theirs))
        at (fn [es p] (get es p ::none))
        changed (filter #(not= (at b %) (at m %)) (into #{} (concat (keys b) (keys m))))
        conflicts (filter #(and (not= (at b %) (at t %)) (not= (at m %) (at t %))) changed)]
    {:plugins (rebuild (reduce (fn [acc p] (if (= ::none (at m p)) (dissoc acc p) (assoc acc p (at m p))))
                               t changed))
     :conflicts (vec (sort-by str conflicts))}))

(def built-in-keys
  "{content-type #{key}} of the built-in content links can name that is fixed data."
  {:orcpub.dnd.e5/spells (set (keys spells/spell-map))
   :orcpub.dnd.e5/languages (into #{} (map :key) langs/languages)
   :orcpub.dnd.e5/monsters (set (keys monsters/monster-map))})

(defn offered-by-type
  "{content-type #{key}} a link can resolve to outside the library: the options of `template`'s
   top-level :class and :race choices, and `built-in-keys`."
  [template]
  (let [under (fn [k] (into #{} (keep ::t/key)
                            (some #(when (= k (::t/key %)) (::t/options %)) (::t/selections template))))]
    (merge-with into
                {:orcpub.dnd.e5/classes (under :class) :orcpub.dnd.e5/races (under :race)}
                built-in-keys)))

(defn dangling
  "{[source type key] [{:link :target}]} for every item in `plugins` with a link to nothing:
   neither `offered` (from `offered-by-type`) nor a library item of the link's type holds the
   target. A `:by :name` link to a language is found by name first. `offered` nil means not yet
   known: {}."
  [plugins offered]
  (if (nil? offered)
    {}
    (let [lib (held plugins)
          answers? (fn [to k] (or (contains? (get offered to) k) (contains? (get lib to) k)))
          names (reduce (fn [acc [_ ct items]]
                          (update acc ct (fnil into #{}) (keep :name) (filter map? (vals items))))
                        built-in-names (content-groups plugins))]
      (reduce (fn [acc [src ct items]]
                (reduce-kv
                 (fn [acc k item]
                   (let [missing (for [link links/links
                                       :when (and (map? item) (links/holds? link ct))
                                       target (links/targets link item)
                                       :when (if (= :name (:by link))
                                               (not (or (contains? (get names (:to link)) target)
                                                        (answers? (:to link) (common/name-to-kw (str target)))))
                                               (not (answers? (:to link) target)))]
                                   {:link (:id link) :to (:to link) :target target})]
                     (cond-> acc (seq missing) (assoc [src ct k] (vec missing)))))
                 acc items))
              {} (content-groups plugins)))))

(defn links-to
  "Every `:by :key` link naming `k` of content type `to`, from items in `plugins` outside the
   sources in `except`, as [{:source :type :key :name :link :to :target}]."
  [plugins to k except]
  (for [[src ct items] (content-groups plugins)
        :when (not (contains? except src))
        [ik item] items
        :when (map? item)
        link links/links
        :when (and (= :key (:by link)) (= to (:to link)) (links/holds? link ct)
                   (some #{k} (links/targets link item)))]
    {:source src :type ct :key ik :name (:name item) :link (:id link) :to to :target k}))

(defn linking
  "Items in `plugins` outside the sources in `except` with a `:by :key` link naming `k` of
   content type `to`, as [{:source :type :key :name}]."
  [plugins to k except]
  (distinct (map #(select-keys % [:source :type :key :name]) (links-to plugins to k except))))

(defn repoint-offer
  "The repairs (as `suggested-repairs`) that point links to `from`, of content type `to`, at
   `to-key` instead: every such link in `plugins` outside `source` and outside any source that
   holds its own `from`."
  [plugins to from to-key source]
  (let [holders (into #{source} (for [[s p] plugins :when (some? (get-in p [to from]))] s))]
    (vec (for [r (links-to plugins to from holders)] (assoc r :to-key to-key :reason :rekeyed)))))

(defn dropped
  "[source type key] of every item `before` holds and `after` does not."
  [before after]
  (for [[src ct items] (content-groups before)
        k (keys items)
        :when (nil? (get-in after [src ct k]))]
    [src ct k]))

(defn- former-index
  "{[type former-key] [[source key] ...]} from every item's :former-keys."
  [plugins]
  (reduce (fn [acc [src ct items]]
            (reduce-kv (fn [acc k item]
                         (reduce #(update %1 [ct %2] (fnil conj []) [src k]) acc (links/former-keys item)))
                       acc items))
          {} (content-groups plugins)))

(defn suggested-repairs
  "Links a past rename left behind, each with the key it most likely meant, as
   [{:source :type :key :name :link :to :target :to-key :reason}], `:reason` `:missing`: nothing
   of the link's type answers the target and exactly one item lists it among its :former-keys.
   `offered` as `dangling`; nil means not yet known: []."
  [plugins offered]
  (if (nil? offered)
    []
    (let [lib (held plugins)
          formers (former-index plugins)]
      (vec
       (for [[src ct items] (content-groups plugins)
             [k item] items
             :when (map? item)
             link links/links
             :when (and (= :key (:by link)) (links/holds? link ct))
             target (links/targets link item)
             :let [to (:to link)
                   claims (get formers [to target])
                   [_ to-key] (first claims)]
             :when (and (not (contains? (get lib to) target))
                        (not (contains? (get offered to) target))
                        (= 1 (count claims)))]
         {:source src :type ct :key k :name (:name item) :link (:id link) :to to
          :target target :to-key to-key :reason :missing})))))

(defn apply-repairs
  "`plugins` with each repair from `suggested-repairs` made."
  [plugins repairs]
  (let [by-id (into {} (map (juxt :id identity)) links/links)]
    (reduce (fn [p {:keys [source type key link target to-key]}]
              (cond-> p
                (map? (get-in p [source type key]))
                (update-in [source type key] #(links/retarget (by-id link) % target to-key))))
            plugins repairs)))

(defn offered-keys
  "Set of every selection and option `::t/key` in `template` (`::char5e/template`): the keys the
   builder can offer right now, built-in and homebrew. Walks selections, options,
   `::t/selections` and `::t/associated-options` only."
  [template]
  (letfn [(selection [acc s] (reduce option (conj acc (::t/key s)) (::t/options s)))
          (option [acc o] (reduce selection
                                  (reduce option (conj acc (::t/key o)) (::t/associated-options o))
                                  (::t/selections o)))]
    (disj (reduce selection #{} (::t/selections template)) nil)))

(defn offers?
  "True when `offered` holds `k`, matching the way a character's key is looked up: exactly, or
   with a stray trailing separator trimmed (`common/canonical-key`)."
  [offered k]
  (or (contains? offered k) (contains? offered (common/canonical-key k))))

(defn choice-tags
  "{selection-key tags} for every selection in `template`, walked as `offered-keys` walks it."
  [template]
  (letfn [(selection [acc s] (reduce option
                                     (cond-> acc (::t/key s) (update (::t/key s) (fnil into #{}) (::t/tags s)))
                                     (::t/options s)))
          (option [acc o] (reduce selection
                                  (reduce option acc (::t/associated-options o))
                                  (::t/selections o)))]
    (reduce selection {} (::t/selections template))))

(defn missing-picks
  "Picks in `character`'s options, under a choice tagged with one of `tags` (per `choice-tags`),
   that `offered` lacks, as [{:key :selection :tag}]. Skips entries carrying their own
   `::entity/value`."
  [character offered choice-tags tags]
  (letfn [(walk [opts]
            (for [[sel v] opts
                  e (if (sequential? v) v [v])
                  :when (map? e)
                  x (cons (let [k (::entity/key e)
                                tag (some tags (get choice-tags sel))]
                            (when (and tag (keyword? k)
                                       (not (contains? e ::entity/value))
                                       (not (offers? offered k)))
                              {:key k :selection sel :tag tag}))
                          (walk (::entity/options e)))
                  :when x]
              x))]
    (vec (distinct (walk (::entity/options character))))))
