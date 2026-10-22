(ns orcpub.dnd.e5.picks
  "A character's stored picks (the entries under `::entity/options`): walking, reading, rewriting,
   removing and putting back. Pure data, shared by the browser and the server. A selection stores
   its picks as a vector when it is `::t/multiselect?` (kept, empty, after its last pick goes),
   otherwise as the one entry map; an absent selection was never chosen.
   GOTCHA: never depend on the iteration order of a set or map here; it differs between the JVM
   and the browser (`testing-infrastructure.md`)."
  (:require [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.common :as common]
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
  "`paths` without those under another of them."
  [paths]
  (let [under? (fn [a b] (and (< (count b) (count a)) (= b (subvec a 0 (count b)))))]
    (remove (fn [a] (some #(under? a %) paths)) paths)))

(defn disqualified
  "Addresses of `character`'s picks under a selection whose gate is closed in `built`: reachable
   from its stored picks but not available. A slot an open selection shares is `overflow`'s.
   GOTCHA: a pick the template does not offer (homebrew not loaded) is never reported."
  [template character built]
  (let [reachable (remove nil? (entity/get-all-selections-aux-2 template
                                                                (entity/make-path-map character)))
        open-paths (set (map (comp vec entity/actual-path)
                             (entity/remove-disqualified-selections reachable built)))
        found (distinct
                   (for [s reachable
                         :let [path (vec (entity/actual-path s))]
                         :when (not (open-paths path))
                         :let [offered (set (map ::t/key (entity/selection-options s)))]
                         e (stored-entries template character path)
                         :when (offered (::entity/key e))]
                     (conj path (::entity/key e))))]
    (vec (drop-nested (vec found)))))

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

;; The Choices list: every pick a character stores, so none is trapped where the builder cannot
;; show it (homebrew not loaded). decision-gate-hidden-picks.md, "Agreed in review".

(def ^:private structural
  "Selections whose entries are the character's frame, not choices: a class's levels, the values
   the builder fills (hit points), and kits (plan-inventory-kits.md)."
  #{:levels :hit-points :kits})

(defn choices
  "Every pick `character` stores, as [{:address :section :name :status}]: `:status` :active when
   its selection offers it, :missing when `template` does not (homebrew not loaded here). Leaves
   out picks under a missing one, the class frame (`structural`) and entries carrying a value."
  [template character]
  (let [by-path (into {} (map (fn [s] [(vec (entity/actual-path s)) s]))
                      (remove nil? (entity/get-all-selections-aux-2
                                    template (entity/make-path-map character))))
        rows (for [[address e] (addresses character)
                   :let [path (pop address)
                         s (by-path path)
                         opt (some #(when (= (peek address) (::t/key %)) %)
                                   (some-> s entity/selection-options))]
                   :when (not (or (structural (peek path)) (contains? e ::entity/value)))]
               {:address address
                :section (or (some-> s ::t/name) (common/kw-to-name (peek path) true))
                :name (or (::t/name opt) (common/kw-to-name (peek address) true))
                :status (if opt :active :missing)})
        missing (map :address (filter #(= :missing (:status %)) rows))
        under? (fn [a b] (and (< (count b) (count a)) (= b (subvec a 0 (count b)))))]
    (vec (remove (fn [{a :address}] (some #(under? a %) missing)) rows))))

;; The planned hold: `remove-at` records of picks that stopped applying, kept in memory only and
;; returned to the character once they fit again. decision-gate-hidden-picks.md, "Current decision".

(defn to-planned
  "`character` without its `disqualified` and `overflow` picks under `template`, as
   {:character :set-aside}; `:set-aside` holds their `remove-at` records."
  [template character]
  (let [built (entity/build character template)
        closed (concat (disqualified template character built)
                          (overflow template character built))]
    (reduce (fn [{:keys [character] :as acc} address]
              (let [{c :character r :removed} (remove-at character address)]
                (cond-> (assoc acc :character c) r (update :set-aside conj r))))
            {:character character :set-aside []}
            closed)))

(defn removed-with-container
  "`remove-at` records, taken from `before`, of the picks `after` lost along with the entry holding
   them: a level or class entry removed, a subclass replaced. Not a pick the player removed itself
   (its entry is still there), nor a `:levels` entry, whose return would raise a level."
  [before after]
  (let [now (set (map first (addresses after)))
        lost (for [[address _] (addresses before)
                   :let [parent (subvec address 0 (- (count address) 2))]
                   :when (and (seq parent)
                              (not (now address))
                              (not (now parent))
                              (not= :levels (peek (pop address))))]
               address)]
    (vec (keep #(:removed (remove-at before %)) (drop-nested (vec lost))))))

(defn from-planned
  "`character` with each record in `planned` put back by `put-at`, as
   {:character :put-back :waiting :retired}: a record whose entry on the way is not stored (yet)
   waits; one `put-at` refuses where it is stored (the slot holds a newer pick) is retired."
  [character planned]
  (reduce (fn [{:keys [character] :as acc} record]
            (if-let [c (put-at character record)]
              (-> acc (assoc :character c) (update :put-back conj record))
              (if (options-path character (:address record))
                (update acc :retired conj record)
                (update acc :waiting conj record))))
          {:character character :put-back [] :waiting [] :retired []}
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
   three are records, against the `planned` passed in. A held pick whose own prereqs fail, or
   whose entry is not stored yet, waits. Throws ex-info if it does not settle."
  [template character planned]
  (let [address-set #(set (map :address %))
        initial (vec planned)
        before (address-set initial)]
    (loop [character character planned (vec planned) retired [] n 0]
      (when (= n max-passes)
        (throw (ex-info "Picks did not settle" {:passes n :planned (mapv :address planned)})))
      (let [wait (waiting template character planned)
            waits (address-set wait)
            trying (remove #(waits (:address %)) planned)
            {c1 :character r :retired absent :waiting} (from-planned character trying)
            {c2 :character held :set-aside} (to-planned template c1)
            held (-> (vec wait) (into absent) (into held))
            retired (into retired r)]
        (if (and (= c2 character) (= (address-set held) (address-set planned)))
          (let [after (address-set held)]
            {:character c2
             :planned held
             :set-aside (filterv #(not (before (:address %))) held)
             :restored (filterv #(not (or (after (:address %))
                                          ((address-set retired) (:address %))))
                                initial)
             :retired retired})
          (recur c2 held retired (inc n)))))))

;; What the builder says about held picks: each one's name and what brings it back.

(defn- entry-addresses
  "The addresses of the entries on the way to `address`, outermost first; not `address` itself."
  [address]
  (for [n (range 2 (count address) 2)] (subvec address 0 n)))

(defn- level-of
  "The number in a level key such as `:level-7`, or nil when `k` is not one."
  [k]
  (some->> (name k) (re-matches #"level-(\d+)") second #?(:clj Long/parseLong :cljs js/parseInt)))

(defn- option-name
  "The name the template gives option `k` of the selection at `path`, from `selections`."
  [selections path k]
  (some (fn [s] (when (= path (vec (entity/actual-path s)))
                  (some #(when (= k (::t/key %)) (::t/name %)) (entity/selection-options s))))
        selections))

(defn describe-held
  "Each record in `planned` as {:record :name :returns}, `:returns` being what brings it back:
   {:class-level n :class k}, {:total-level n}, {:first-class k}, {:not-first-class k},
   {:class k} (its class is gone), {:choice address :name s} (the choice it sat under was
   replaced), or nil. One build of `character` for all of them, or `built` when given."
  [template character planned & [built]]
  (when (seq planned)
    (let [built (or built (entity/build character template))
          reachable (remove nil? (entity/get-all-selections-aux-2 template
                                                                  (entity/make-path-map character)))
          now (set (map first (addresses character)))
          gate-of (fn [path]
                    (some (fn [s] (when (and (= path (vec (entity/actual-path s)))
                                             (some-> (::t/prereq-fn s) (#(not (% built)))))
                                    (some-> (::t/prereq-fn s) meta ::t/gate)))
                          reachable))
          named (fn [address]
                  (or (option-name reachable (pop address) (peek address))
                      (common/kw-to-name (peek address) true)))]
      (mapv (fn [{:keys [address] :as record}]
              (let [missing (first (remove now (entry-addresses address)))
                    recorded (:returns record)
                    later (fn [{:keys [class-level class] :as r}]
                            ;; A pick held for its own level keeps it when its entry goes too:
                            ;; Steel Will needs level 7 even once level 3 brings Hunter back.
                            (if (and class-level (:class-level recorded)
                                     (= class (:class recorded)))
                              (assoc r :class-level (max class-level (:class-level recorded)))
                              (or r recorded)))]
                {:record record
                 :name (named address)
                 :returns (later (cond
                            (nil? missing) (gate-of (pop address))
                            (= :levels (peek (pop missing))) {:class-level (level-of (peek missing))
                                                              :class (second missing)}
                            (and (= 2 (count missing)) (= :class (first missing)))
                            {:class (second missing)}
                            :else {:choice missing :name (named missing)}))}))
            planned))))

(defn with-returns
  "`planned` with each record that has no `:returns` given one (`describe-held`) while what holds
   it can still be read, so a later edit that removes its entry does not lose it."
  [template character planned & [built]]
  (let [fresh (remove #(contains? % :returns) planned)
        returns (into {} (map (juxt (comp :address :record) :returns))
                      (describe-held template character fresh built))]
    (mapv #(if (contains? % :returns) % (assoc % :returns (returns (:address %)))) planned)))
