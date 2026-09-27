(ns orcpub.dnd.e5.library
  "The one gate every write to the homebrew library passes: `commit`. See
   docs/kb/homebrew-keys-design.md §4."
  (:require [orcpub.common :as common]
            [orcpub.dnd.e5.library-links :as links]))

(defn- content-groups [plugins]
  (for [[src plugin] plugins
        :when (map? plugin)
        [ct items] plugin
        :when (and (qualified-keyword? ct) (map? items))]
    [src ct items]))

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

(defn commit
  "What storing `new` in place of `old` does: {:plugins (normalize new) :broken [...]}, or
   {:refused {:broken [...] :invalid [...]}} when it would add a key the loader sets aside or
   leave a link pointing at nothing. `deleting?` lets a deliberate removal through and reports
   the links it broke; `retargeting` is a collection of [type key] whose links are being moved."
  [old new {:keys [deleting? retargeting]}]
  (let [stored (normalize new)
        broken (vec (broken-links old stored (set retargeting)))
        invalid (vec (invalid-keys old stored))]
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
