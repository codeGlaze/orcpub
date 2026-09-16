(ns orcpub.entity-spec
  "Character attributes as a spreadsheet. Each attribute is a cell; a `?other-attr` reference in
   its body is an input; the references decide what has to be computed first.

   READ a value with `entity-val` (or the `q` macro) — never plain `get`. Cells hold functions
   until forced.
   WRITE one with `modifier`, or `vec-mod` / `set-mod` / `map-mod` / `cum-sum-mod` for the
   accumulating kinds. `dependencies` and `conditions` are what those macros use internally to
   declare a cell's inputs and its guards.

   THE REWRITE IS LOAD-BEARING. `?foo` becomes `(entity-val entity :foo)` at MACROEXPANSION, not
   at runtime. So a `?`-ref exists only where a macro literally wrote one: no ordinary function,
   and no form assembled from data at runtime, can reference an attribute. `requirements.cljc` is
   the worked way around it — predicates over a plain-data context, never condition forms.

   GOTCHA: `deps` declares a cell's inputs by scanning for literal `?` symbols. Reach an
   attribute any other way and it declares no input, so evaluation can be ordered wrong — which
   surfaces as a wrong number, never an error.

   docs/kb/built-character-representation.md"
  (:require [clojure.string :as s]
            [clojure.set :as sets]))

(defn entity-val
  "Read field `k` from a built entity/character. Built entities are MAPS whose derived
   values are deferred functions tagged with :entity-fn? metadata; this realizes them.
   A plain `get` on a deferred key returns the FUNCTION, not the value — use this instead.
   The built character is NOT a flat map; don't spec/keys it.
   See docs/kb/built-character-representation.md."
  [entity k]
  (let [v (entity k)
        entity-fn? (:entity-fn? (meta v))]
    (if entity-fn?
      (v entity)
      v)))

(defn ref-sym-to-kw
  "The symbol `?base-ac` -> the keyword `:base-ac`. Drops the leading `?`, nothing else."
  [sym]
  (keyword (subs (str sym) 1)))

(defmacro q
  "Read attribute `query` (a `?ref` symbol) off `entity`. Sugar for `entity-val` with the keyword
   spelled out: `(q c ?base-ac)` == `(entity-val c :base-ac)`."
  [entity query]
  `(entity-val
    ~entity
    ~(ref-sym-to-kw query)))

(defn ref-to-kw
  "One rewrite step: a `?`-prefixed symbol becomes `(entity-val entity :that-kw)`; anything else
   is returned untouched. Called during macroexpansion, never at runtime."
  [s entity]
  (if (and (symbol? s)
           (s/starts-with? (str s) "?"))
    `(entity-val ~entity ~(ref-sym-to-kw s))
    s))

(defn replace-refs
  "Walk `body` and rewrite every `?`-ref into an `entity-val` lookup against `entity`. Recurses
   through maps, vectors and seqs, and rewrites map KEYS as well as values."
  [entity body]
  (cond
    (map? body)
    (into
     {}
     (reduce-kv
      (fn [m k v]
        (assoc m (ref-to-kw k entity) (replace-refs entity v)))
      {}
      body))
    (vector? body)
    (mapv #(replace-refs entity %) body)
    (sequential? body)
    (map #(replace-refs entity %) body)
    :else (ref-to-kw body entity)))

(defn deps
  "The set of attribute keywords `body` reads, minus `k` itself — the inputs `apply-options`
   topologically sorts on. Found by scanning the form for literal `?` symbols; see the ns
   docstring's GOTCHA for what that misses."
  [k body]
  (let [nodes (tree-seq coll? seq body)]
    (into
     #{}
     (comp
      (filter
       #(and (symbol? %)
             (not= k %)
             (s/starts-with? (name %) "?")))
      (map ref-sym-to-kw))
     nodes)))

(defmacro dependencies
  "The set of attribute keywords `body` reads — `k`'s inputs. Emitted at macroexpansion; the
   modifier macros use it so `apply-options` can order evaluation."
  [k body]
  (deps k body))

(defmacro entity-dependencies
  "`dependencies` over a whole `{?attr body}` map: attribute keyword -> the set it reads."
  [body]
  (reduce-kv
   (fn [m k v]
     (let [kw (ref-sym-to-kw k)]
       (assoc
        m
        kw
       (deps k v))))
   {}
   `~body))

(defmacro make-entity
  "Build a base entity from `{?attr body}`. Each body becomes a 1-arg fn tagged `:entity-fn? true`
   and is NOT evaluated until read — that is why `entity-val` exists and plain `get` returns the
   function. Also emits `::deps`, the input map `apply-options` sorts on."
  [body]
  (reduce-kv
   (fn [m k v]
     (let [arg (gensym "e")
           replaced (replace-refs arg v)
           kw (ref-sym-to-kw k)]
       (assoc
        (update m ::deps (fn [d] (update d kw #(sets/union % (deps k v)))))
        kw
        `(with-meta
           ~(concat `(fn [~arg])
                    [replaced])
           {:entity-fn? true}))))
   {}
   `~body))

(defmacro condition
  "`body` as a predicate over a built entity, with its `?refs` rewritten. Captures the FORM, so a
   condition can only be written literally in source — see the ns docstring."
  [body]
  (let [arg (gensym "e")
        replaced (replace-refs arg body)]
    `(fn [~arg] ~replaced)))

(defmacro conditions
  "A vector of `condition`s. A modifier applies only when all of them hold."
  [conds]
  (mapv
   (fn [cond]
     `(condition ~cond))
   conds))

(defmacro modifier
  "REPLACE attribute `k` with `body`. `body` may read other attributes as `?refs`, including `k`
   itself for a read-modify-write.

   This is the base the other mod macros expand to; reach for those when accumulating, since two
   `modifier`s on one attribute means the last to apply wins."
  [k body]
  (let [arg (gensym "e")
        replaced (replace-refs arg body)]
    `(with-meta
       ~(concat
        `(fn [~arg])
        `((update ~arg ~(ref-sym-to-kw k) (fn [_#] ~replaced))))
       {:entity-fn? true})))

(defmacro vec-mod
  "ACCUMULATE: conj `val` onto vector attribute `k`, treating absent as `[]`. Order-preserving,
   duplicates kept."
  [k val]
  `(modifier ~k (conj (or ~k []) ~val)))

(defmacro set-mod
  "ACCUMULATE: conj `val` into set attribute `k`, treating absent as `#{}`. Deduplicating — this
   is why granting a language twice is a no-op rather than an error."
  [k val]
  `(modifier ~k (conj (if (seq ~k) ~k #{}) ~val)))

(defmacro map-mod
  "ACCUMULATE: assoc `key`->`val` into map attribute `k`. Later sources overwrite the same key."
  [k key val]
  `(modifier ~k (assoc ~k ~key ~val)))

(defn default-to-zero [k]
  (if (number? k)
    k
    0))

(defmacro cum-sum-mod
  "ACCUMULATE: add `bonus` to numeric attribute `k`, treating absent or non-numeric as 0."
  [k bonus]
  `(modifier ~k (+ (default-to-zero ~k) ~bonus)))

(defmacro modifiers
  "A vector of `modifier`s from bare `[?attr body]` pairs — `(modifiers [?speed 30] [?size :medium])`."
  [& mods]
  (mapv
   (fn [mod]
     (cons `modifier mod))
   mods))
