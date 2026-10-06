(ns orcpub.dnd.e5.picks
  "A character's stored picks (the entries under `::entity/options`): walking, reading, rewriting,
   removing and putting back. Pure data, shared by the browser and the server. A selection stores
   its picks as a vector when it is `::t/multiselect?` (kept, empty, after its last pick goes),
   otherwise as the one entry map; an absent selection was never chosen.
   GOTCHA: never depend on the iteration order of a set or map here; it differs between the JVM
   and the browser (`testing-infrastructure.md`)."
  (:require [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.library :as library]))

(defn walk
  "`opts` (::entity/options) with `(f path entry)` applied to every chosen entry, where `path` is
   the vector of selection keys from the root down to it."
  [opts f]
  (letfn [(entry [path e]
            (if (map? e)
              (let [e' (f path e)]
                (cond-> e' (map? (::entity/options e')) (update ::entity/options #(walk path %))))
              e))
          (walk [path o]
            (into {} (map (fn [[sel v]]
                            (let [p (conj path sel)]
                              [sel (if (sequential? v) (mapv #(entry p %) v) (entry p v))])))
                  o))]
    (if (map? opts) (walk [] opts) opts)))

(defn- walk-typed
  "`opts` (::entity/options) with `f` applied to every pick that could be of `content-type`
   (`library/pick-types`); `content-type` nil: every pick."
  [opts content-type f]
  (walk opts (fn [path e]
               (if (or (nil? content-type) (seq (library/pick-types path [content-type])))
                 (f e)
                 e))))

(defn keys-of
  "Set of `character`'s pick keys that could be of `content-type` (see `walk-typed`)."
  [character content-type]
  (let [ks (atom #{})]
    (walk-typed (::entity/options character) content-type
                #(do (when-let [k (::entity/key %)] (swap! ks conj k)) %))
    @ks))

(defn relink
  "`character` with each pick of `from` that could be of `content-type` rewritten to `to`, as
   {:character :rewrote} (see `content-reconciliation/reconcile-former-keys`). `content-type` nil:
   every pick."
  [character content-type from to]
  (let [rewrote (atom [])
        opts (walk-typed (::entity/options character) content-type
                         #(if (= from (::entity/key %))
                            (do (swap! rewrote conj {:from from :to to}) (assoc % ::entity/key to))
                            %))]
    {:character (cond-> character (seq @rewrote) (assoc ::entity/options opts))
     :rewrote @rewrote}))

;; An address names one pick: [selection key, chosen entry's key] pairs from the root, the format
;; of the app's option paths (`entity/get-entity-path`), e.g. [:class :ranger :levels :level-7].
;; A key, never an index: removing one pick must not move another's address.

(defn- entry-index [v k]
  (first (keep-indexed (fn [i e] (when (= k (::entity/key e)) i)) v)))

(defn- options-path
  "The `get-in` path to the ::entity/options map holding the last pair of `address`, or nil when
   an entry on the way is not stored."
  [character address]
  (loop [path [::entity/options] [[sel k] & more] (partition 2 address)]
    (if (empty? more)
      path
      (let [v (get-in character (conj path sel))
            step (if (sequential? v)
                   (when-let [i (entry-index v k)] [sel i])
                   (when (= k (::entity/key v)) [sel]))]
        (when step (recur (into path (conj step ::entity/options)) more))))))

(defn remove-at
  "`character` without the pick at `address`, as {:character :removed}; `:removed` is
   {:address :entry :multiselect?}, what `put-at` takes, or nil when nothing is stored there.
   A multiselect keeps its vector, emptied if this was its last pick, as unticking does; a single
   pick's selection is dissoc'd. `:multiselect?` records which, since neither shows it after."
  [character address]
  {:pre [(even? (count address)) (seq address)]}
  (let [sel (nth address (- (count address) 2))
        k (last address)
        opath (options-path character address)
        v (when opath (get-in character (conj opath sel)))]
    (cond
      (and (sequential? v) (entry-index v k))
      {:character (assoc-in character (conj opath sel)
                            (with-meta (vec (remove #(= k (::entity/key %)) v)) (meta v)))
       :removed {:address address :entry (nth v (entry-index v k)) :multiselect? true}}

      (and (map? v) (= k (::entity/key v)))
      {:character (update-in character opath dissoc sel)
       :removed {:address address :entry v :multiselect? false}}

      :else {:character character :removed nil})))

(defn put-at
  "`character` with `removed` (from `remove-at`) stored again at its address: appended to a
   `:multiselect?` vector (created if absent), else as the selection's entry. Nil when nothing is
   written: an entry on the way is gone, the vector already holds its key, or the one-pick
   selection already holds a pick. Never overwrites or duplicates a pick.
   GOTCHA: checks no rule; whether it still fits is the caller's question."
  [character {:keys [address entry multiselect?]}]
  (let [sel (nth address (- (count address) 2))
        opath (options-path character address)
        v (when opath (get-in character (conj opath sel)))]
    (cond
      (nil? opath) nil
      multiselect? (cond (not (sequential? v))
                         (assoc-in character (conj opath sel) [entry])

                         (entry-index v (::entity/key entry)) nil

                         :else
                         (assoc-in character (conj opath sel)
                                   (with-meta (conj (vec v) entry) (meta v))))
      (some? v) nil
      :else (assoc-in character (conj opath sel) entry))))

(defn addresses
  "Every pick stored in `character` as [address entry], where `address` is what `remove-at` and
   `put-at` take. A parent comes before its children; otherwise the order is the options maps' own.
   An entry with no `::entity/key` has no address, so it and its children are left out."
  [character]
  (letfn [(entries [address opts]
            (mapcat (fn [[sel v]]
                      (mapcat #(entry (conj address sel) %)
                              (cond (sequential? v) v (map? v) [v] :else [])))
                    opts))
          (entry [prefix e]
            (when-let [k (when (map? e) (::entity/key e))]
              (let [address (conj prefix k)]
                (cons [address e]
                      (when (map? (::entity/options e))
                        (entries address (::entity/options e)))))))]
    (vec (entries [] (::entity/options character)))))

;; Which stored picks no longer apply, by the builder's own pipeline (`random-character` in
;; events.cljs), so one rule decides what is shown and what applies. decision-gate-hidden-picks.md

(defn- stored-entries
  "The entry maps stored at selection `path` (a vector, or the one map)."
  [template character path]
  (let [v (entity/get-option template character path)]
    (cond (sequential? v) (filter map? v) (map? v) [v] :else [])))

(defn- drop-nested
  "`addresses` without those under another address in it."
  [addresses]
  (let [under? (fn [a b] (and (< (count b) (count a)) (= b (subvec a 0 (count b)))))]
    (remove (fn [a] (some #(under? a %) addresses)) addresses)))

(defn disqualified
  "Addresses of `character`'s picks under a selection whose gate is closed in `built`: reachable
   from its stored picks but not available. A slot an open selection shares is `overflow`'s.
   GOTCHA: a pick the template does not offer (homebrew not loaded) is never reported."
  [template character built]
  (let [reachable (remove nil? (entity/get-all-selections-aux-2 template
                                                                (entity/make-path-map character)))
        open-paths (set (map (comp vec entity/actual-path)
                             (entity/remove-disqualified-selections reachable built)))
        addresses (distinct
                   (for [s reachable
                         :let [path (vec (entity/actual-path s))]
                         :when (not (open-paths path))
                         :let [offered (set (map ::t/key (entity/selection-options s)))]
                         e (stored-entries template character path)
                         :when (offered (::entity/key e))]
                     (conj path (::entity/key e))))]
    (vec (drop-nested (vec addresses)))))

(defn overflow
  "Addresses of the newest picks over an available selection's limit in `built`, per
   `entity/count-remaining` (selections sharing a slot combined; a waived limit reports none)."
  [template character built]
  (vec
   (for [s (entity/combine-selections (entity/available-selections character built template))
         :let [over (- (entity/count-remaining template character s))]
         :when (pos? over)
         :let [path (vec (entity/actual-path s))
               counted (filter (partial entity/option-selected? (::t/require-value? s))
                               (stored-entries template character path))]
         e (take-last over counted)]
     (conj path (::entity/key e)))))

;; The planned hold: `remove-at` records of picks that stopped applying, kept in memory only and
;; returned to the character once they fit again. decision-gate-hidden-picks.md, "Current decision".

(defn to-planned
  "`character` without its `disqualified` and `overflow` picks under `template`, as
   {:character :set-aside}; `:set-aside` holds their `remove-at` records."
  [template character]
  (let [built (entity/build character template)
        addresses (concat (disqualified template character built)
                          (overflow template character built))]
    (reduce (fn [{:keys [character] :as acc} address]
              (let [{c :character r :removed} (remove-at character address)]
                (cond-> (assoc acc :character c) r (update :set-aside conj r))))
            {:character character :set-aside []}
            addresses)))

(defn from-planned
  "`character` with each record in `planned` put back by `put-at`, as
   {:character :put-back :retired}: a record `put-at` cannot write is retired."
  [character planned]
  (reduce (fn [{:keys [character] :as acc} record]
            (if-let [c (put-at character record)]
              (-> acc (assoc :character c) (update :put-back conj record))
              (update acc :retired conj record)))
          {:character character :put-back [] :retired []}
          planned))

(defn- requirements-met?
  "Whether the option `record` holds meets its own `::t/prereqs` on `built`, the character without
   it (\"You already have this skill\" and the like). A selection not open in `built` passes here;
   `put-at` and `to-planned` decide it."
  [open-by-path built {:keys [address]}]
  (let [option (some #(when (= (peek address) (::t/key %)) %)
                     (some-> (open-by-path (pop address)) entity/selection-options))]
    (every? (fn [{:keys [::t/prereq-fn]}] (or (nil? prereq-fn) (prereq-fn built)))
            (::t/prereqs option))))

(defn- waiting
  "The records in `planned` that `put-at` could place but whose option does not meet its own
   prereqs on `character` yet. A record `put-at` refuses is left to retire."
  [template character planned]
  (when (seq planned)
    (let [built (entity/build character template)
          open (into {} (map (juxt (comp vec entity/actual-path) identity))
                     (entity/available-selections character built template))]
      (filterv #(and (put-at character %) (not (requirements-met? open built %))) planned))))

(def ^:private max-passes 8)

(defn update-planned
  "`character` and its hold `planned` once every held pick that fits is back and every pick that
   no longer applies is held, as {:character :planned :set-aside :restored :retired}; the last
   three are records, against the `planned` passed in. A held pick whose own prereqs fail waits.
   Throws ex-info if it does not settle."
  [template character planned]
  (let [addresses #(set (map :address %))
        initial (vec planned)
        before (addresses initial)]
    (loop [character character planned (vec planned) retired [] n 0]
      (when (= n max-passes)
        (throw (ex-info "Picks did not settle" {:passes n :planned (mapv :address planned)})))
      (let [wait (waiting template character planned)
            waits (addresses wait)
            trying (remove #(waits (:address %)) planned)
            {c1 :character r :retired} (from-planned character trying)
            {c2 :character held :set-aside} (to-planned template c1)
            held (into (vec wait) held)
            retired (into retired r)]
        (if (and (= c2 character) (= (addresses held) (addresses planned)))
          (let [after (addresses held)]
            {:character c2
             :planned held
             :set-aside (filterv #(not (before (:address %))) held)
             :restored (filterv #(not (or (after (:address %)) ((addresses retired) (:address %))))
                                initial)
             :retired retired})
          (recur c2 held retired (inc n)))))))
