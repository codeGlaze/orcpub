(ns orcpub.dnd.e5.content-reconciliation
  "Detects homebrew content (classes, races, etc.) a character references that is not loaded, and
   suggests similar loaded content. Keys are extracted from the entity options with the same
   get-in paths the rest of the app uses, not a generic tree walk."
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [orcpub.entity :as entity]
            [orcpub.common :as common]
            [orcpub.template :as t]
            [orcpub.dnd.e5.library-links :as links]
            [orcpub.dnd.e5.library :as library]
            [orcpub.dnd.e5.classes :as class5e]))

;; ============================================================================
;; Content Type Definitions
;; ============================================================================

(def subclass-selection-keys
  "Subclass selection keys vary by class. Each class names its archetype
   selection differently (e.g. Fighter uses :martial-archetype, Rogue uses
   :roguish-archetype). This set covers all known variants including homebrew."
  #{:martial-archetype :roguish-archetype :sorcerous-origin
    :otherworldly-patron :arcane-tradition :bardic-college
    :divine-domain :druid-circle :monastic-tradition
    :sacred-oath :ranger-archetype :primal-path
    :artificer-specialist :artificer-specialization
    :blood-hunter-order})

(def content-type->field
  "Maps content type keywords to their field names in available-content."
  {:class :classes
   :subclass :subclasses
   :race :races
   :subrace :subraces
   :background :backgrounds
   :feat :feats})

;; ============================================================================
;; Key Extraction — direct get-in on entity options
;; ============================================================================

(defn- extract-race-keys
  "Extract race and subrace keys from a character."
  [options]
  (let [race-opt (get options :race)]
    (cond-> []
      (::entity/key race-opt)
      (conj {:key (::entity/key race-opt) :content-type :race :content-label "Race" :path [:race]})

      (get-in race-opt [::entity/options :subrace ::entity/key])
      (conj {:key (get-in race-opt [::entity/options :subrace ::entity/key])
             :content-type :subrace :content-label "Subrace" :path [:race :subrace]}))))

(defn- extract-background-key
  "Extract background key from a character."
  [options]
  (when-let [k (get-in options [:background ::entity/key])]
    [{:key k :content-type :background :content-label "Background" :path [:background]}]))

(defn- extract-class-keys
  "Extract class and subclass keys from a character.
   Each class entry may have a subclass under a class-specific selection key
   (e.g. :martial-archetype for Fighter, :sacred-oath for Paladin)."
  [options]
  (let [classes (get options :class)]
    (when (sequential? classes)
      (mapcat
       (fn [class-opt]
         (let [class-key (::entity/key class-opt)
               class-opts (::entity/options class-opt)
               ;; Find the subclass by checking each known archetype selection key
               subclass-key (some (fn [sel-key]
                                    (get-in class-opts [sel-key ::entity/key]))
                                  subclass-selection-keys)]
           (cond-> []
             class-key
             (conj {:key class-key :content-type :class :content-label "Class" :path [:class]})

             subclass-key
             (conj {:key subclass-key :content-type :subclass :content-label "Subclass"}))))
       classes))))

(defn- extract-feat-keys
  "Extract feat keys from the top-level :feats selection.
   Only extracts direct children — nested sub-selections (ability scores,
   language choices, etc.) under a feat are not content references."
  [options]
  (let [feats (get options :feats)]
    (when (sequential? feats)
      (keep (fn [feat-opt]
              (when-let [k (::entity/key feat-opt)]
                {:key k :content-type :feat :content-label "Feat" :path [:feats]}))
            feats))))

(defn extract-content-keys
  "Extract all content keys from a character's options.
   Returns a seq of {:key :content-type :content-label :path}; `:path` is the selection path read."
  [character]
  (let [options (::entity/options character)]
    (concat
     (extract-race-keys options)
     (extract-background-key options)
     (extract-class-keys options)
     (extract-feat-keys options))))

;; ============================================================================
;; Content Availability Checking
;; ============================================================================

(defn- key-similarity
  "Calculate similarity between two keywords (0-1 scale).
   Uses prefix matching and common-base comparison."
  [k1 k2]
  (let [s1 (name k1)
        s2 (name k2)
        exact (if (= s1 s2) 1.0 0.0)
        prefix (if (or (str/starts-with? s1 s2)
                       (str/starts-with? s2 s1))
                 0.7 0.0)
        base-match (if (= (common/kw-base k1) (common/kw-base k2)) 0.8 0.0)]
    (max exact prefix base-match)))

(defn- infer-source-from-key
  "Try to infer the source name from a key's suffix.
   E.g., :artificer-kibbles-tasty → \"Kibbles Tasty\""
  [key]
  (let [parts (str/split (name key) #"-")]
    (when (> (count parts) 1)
      (str/join " " (map str/capitalize (rest parts))))))

(defn find-similar-content
  "Find content similar to a missing key.
   Returns seq of {:key :name :source :similarity} sorted by similarity."
  [missing-key content-type available-content]
  (let [inferred-source (infer-source-from-key missing-key)
        missing-base (common/kw-base missing-key)]
    (->> available-content
         (filter #(and (map? %) (keyword? (:key %))))
         (map (fn [{:keys [key] :as content}]
                (let [similarity (key-similarity missing-key key)
                      content-name (:name content)
                      name-match? (and (string? content-name)
                                       (not (str/blank? content-name))
                                       (= (str/lower-case missing-base)
                                          (common/kw-base (common/name-to-kw content-name))))]
                  (assoc content
                         :similarity (if name-match?
                                       (max similarity 0.6)
                                       similarity)
                         :inferred-source inferred-source))))
         (filter #(> (:similarity %) 0.3))
         (sort-by :similarity >)
         (take 5))))

;; ============================================================================
;; Built-in (SRD) Content — excluded from missing-content warnings
;; ============================================================================

;; DEPRECATED 2026-09-27, remove after 2026-12: superseded by `offered-keys`, which reads what
;; the builder actually offers instead of a hand-kept copy of it. See homebrew-keys-design.md.
;; Only SRD content belongs here. Non-SRD PHB content (Battle Master,
;; Folk Hero, etc.) comes from plugins and SHOULD be flagged when removed.

#_(def ^:private builtin-races
  #{:dwarf :elf :halfling :human :dragonborn :gnome
    :half-elf :half-orc :tiefling})

;; Built-in subraces: PHB subrace keys auto-generated from their names via
;; common/name-to-kw. Human cultural variants (Calishite etc.) are defined
;; in spell_subs.cljs with only :name, so their keys are derived from the name.
#_(def ^:private builtin-subraces
  #{;; Dwarf
    :hill-dwarf :mountain-dwarf
    ;; Elf
    :high-elf :wood-elf :drow
    ;; Halfling
    :lightfoot :stout
    ;; Gnome
    :forest-gnome :rock-gnome
    ;; Human cultural variants (spell_subs.cljs human-option-cfg :subraces)
    :calishite :chondathan :damaran :illuskan
    :mulan :rashemi :shou :tethyrian :turami
    ;; Human variant selection options
    :standard-human :variant-human})

;; Only Acolyte is hardcoded (spell_subs.cljs:538).
#_(def ^:private builtin-backgrounds #{:acolyte})

;; SRD subclasses — one per class, hardcoded in classes.cljc.
#_(def ^:private builtin-subclasses
  #{:champion :berserker :lore :life :land :open-hand
    :devotion :hunter :thief :draconic :fiend :evocation})

;; Grappler is the only SRD feat (feats5e/feats-plugin, hardcoded).
#_(def ^:private builtin-feats #{:grappler})

#_(defn- builtin?
  "True if this key is SRD built-in content that won't appear in plugin subs."
  [k content-type]
  (case content-type
    :class (contains? class5e/base-class-keys k)
    :subclass (contains? builtin-subclasses k)
    :race (contains? builtin-races k)
    :subrace (contains? builtin-subraces k)
    :background (contains? builtin-backgrounds k)
    :feat (contains? builtin-feats k)
    false))

;; ============================================================================
;; What the builder offers
;; ============================================================================

(def offered-keys library/offered-keys)

;; ============================================================================
;; Missing Content Detection
;; ============================================================================

;; Keys that never mean missing content: every "Custom" option (background/race/subrace/subclass)
;; becomes :custom and keeps its data INLINE on the entity (::entity/value + ::entity/options), and
;; :none is an explicit "no selection". Mirrors the #{:none :custom} guard in events.cljs; checked
;; here so one guard covers every inline-custom type.
(def ^:private inline-content-sentinels #{:custom :none})

(defn check-content-availability
  "Entries of `character-keys` (from `extract-content-keys`) the builder does not offer, each with
   `:missing? true`, `:suggestions` drawn from `available-content` ({:classes [...] ...}) and
   `:inferred-source`. An entry whose `:path` is a `library/pick-homes` path of a type
   `offered-by-type` lists is looked up in that type's keys; any other in `offered` (from
   `offered-keys`). `offered` nil means not yet known: nothing is
   reported."
  [character-keys available-content offered & [offered-by-type]]
  (when (some? offered)
    (keep
     (fn [{:keys [key content-type path] :as entry}]
       (when-not (or (contains? inline-content-sentinels key)
                     (library/offers? (get offered-by-type (library/pick-homes path) offered) key))
         (let [field (get content-type->field content-type)
               suggestions (find-similar-content
                            key content-type
                            (get available-content field []))]
           (assoc entry
                  :missing? true
                  :suggestions suggestions
                  :inferred-source (infer-source-from-key key)))))
     character-keys)))

(defn generate-missing-content-report
  "Report of the content `character` references that the builder does not offer:
   {:has-missing? bool :missing-count n :items [{:key :content-type :content-label
   :inferred-source :suggestions [{:key :name :similarity}]} ...]}. Also lists spell and language
   picks no longer offered when `choice-tags` is given. `offered` nil reports nothing."
  [character available-content offered & [choice-tags offered-by-type]]
  (let [char-keys (extract-content-keys character)
        known (set (map :key char-keys))
        missing (concat
                 (check-content-availability char-keys available-content offered offered-by-type)
                 ;; Spells and languages the character picked that the builder no longer offers.
                 (when (and (some? offered) choice-tags)
                   (for [{:keys [key tag]} (library/missing-picks character offered choice-tags
                                                                  #{:spells :language-profs})
                         :when (not (contains? known key))]
                     {:key key :content-type (if (= :spells tag) :spell :language)
                      :content-label (if (= :spells tag) "Spell" "Language")
                      :missing? true :suggestions [] :inferred-source (infer-source-from-key key)})))]
    {:has-missing? (boolean (seq missing))
     :missing-count (count missing)
     :items (vec missing)}))

;; ============================================================================
;; Spell Selection Key Reconciliation
;; ============================================================================
;;
;; A class's spell selections are keyed :<class-key>-cantrips-known and :<class-key>-spells-known.
;; FIELD NOTE (spell-selection-orphans): saved characters exist with the suffix under another
;; prefix (:cleric-source-cantrips-known, :artificer-cantrips-known under :artificer-kibbles-tasty).
;; Their selections render as nothing until rewritten.

(def ^:private spell-selection-suffix-re
  #"^.+?-(cantrips-known|spells-known)$")

(defn- spell-selection-suffix
  "Returns the trailing suffix (\"cantrips-known\" or \"spells-known\")
   for a spell-selection-shaped key, else nil."
  [k]
  (when (keyword? k)
    (when-let [match (re-matches spell-selection-suffix-re (name k))]
      (second match))))

(defn- class->expected-spell-keys
  "Set of canonical spell-selection keys for a class entry with this :key.
   Mirrors options/spell-selection-key."
  [class-key]
  (when class-key
    #{(keyword (str (name class-key) "-cantrips-known"))
      (keyword (str (name class-key) "-spells-known"))}))

(defn- reconcile-class-entry-options
  "One class entry's `options` with each orphaned spell-selection key moved to the one key of
   `expected-keys` sharing its suffix, as {:options :rewrote}. Anything else passes through."
  [class-key options expected-keys]
  (reduce-kv
   (fn [acc k v]
     (let [suffix (spell-selection-suffix k)]
       (cond
         (nil? suffix)
         (update acc :options assoc k v)

         (contains? expected-keys k)
         (update acc :options assoc k v)

         :else
         (let [candidates (filter #(= suffix (spell-selection-suffix %))
                                  expected-keys)]
           (if (= 1 (count candidates))
             (-> acc
                 (update :options assoc (first candidates) v)
                 (update :rewrote conj
                         {:class-key class-key
                          :from k
                          :to (first candidates)}))
             (update acc :options assoc k v))))))
   {:options {} :rewrote []}
   options))

(defn reconcile-spell-selection-keys
  "`character` with orphaned spell-selection keys rewritten under each class entry whose :key is
   in `loaded-class-keys` (built-ins plus enabled plugin classes), as {:character :rewrote
   [{:class-key :from :to}]}. A key moves only when exactly one expected key shares its suffix.
   Entries of classes not loaded pass through; the missing-content report covers them."
  [character loaded-class-keys]
  (let [known-keys (set loaded-class-keys)
        class-entries (get-in character [::entity/options :class])]
    (if (sequential? class-entries)
      (let [{:keys [entries rewrote]}
            (reduce
             (fn [acc class-entry]
               (let [class-key (::entity/key class-entry)
                     expected (when (contains? known-keys class-key)
                                (class->expected-spell-keys class-key))]
                 (if (empty? expected)
                   (update acc :entries conj class-entry)
                   (let [opts (or (::entity/options class-entry) {})
                         {:keys [options rewrote]}
                         (reconcile-class-entry-options class-key opts expected)]
                     (-> acc
                         (update :entries conj
                                 (assoc class-entry ::entity/options options))
                         (update :rewrote into rewrote))))))
             {:entries [] :rewrote []}
             class-entries)]
        {:character (assoc-in character [::entity/options :class] entries)
         :rewrote rewrote})
      {:character character :rewrote []})))

;; ── Former keys ─────────────────────────────────────────────────────────────
;; An item whose key changed lists its old keys in :former-keys. A "heal" rewrites a character's
;; picks of an old key to the current one. It is in memory until the character is saved.
;; Long form and tracing: character-heals.md.
;;
;; FIELD NOTE (heal-sites): every heal goes through reconcile-former-keys, from three places:
;;   events/set-character          the builder's character, on every load
;;   autosave-fx/cache-template    re-dispatches :set-character once, when the offered-key list
;;                                 first exists (before it, the indexes are empty)
;;   subs ::char5e/character       saved characters as pages read them; never stored
;; A pick healed in the builder but not on a page (or the reverse) means a path skips one of these.
;;
;; FIELD NOTE (heal-typed): a key is unique only within its type. The built-in Dragonborn "Blue"
;; ancestry and a background picked as :blue share :blue. A pick at a library/pick-homes path uses
;; its own type's index; every other pick uses the flat index. A type with no offered list is
;; unknown, never empty. typed-keys.md.

;; GOTCHA: heal the character, not the matching. t/option-cfg drops fields it does not know, so
;; :former-keys never reaches the template.

(def former-key-cap links/former-key-cap)
(def former-keys links/former-keys)
(def record-former-key links/record-former-key)

(defn- items-with-formers
  "[{:type :key :formers}] for every item in `plugins`. A background's name-derived key counts as
   a former key."
  [plugins]
  (for [[_ plugin] plugins
        :when (map? plugin)
        [ct content] plugin
        :when (map? content)
        [k item] content
        :when (map? item)]
    ;; a background was offered under its name's key until the stored one was used
    {:type ct
     :key k
     :formers (cond-> (vec (former-keys item))
                (and (= ct :orcpub.dnd.e5/backgrounds) (string? (:name item)))
                (conj (common/name-to-kw (:name item))))}))

(defn- claimed-once
  "{former-key -> current-key} of `items`' formers claimed by exactly one item and not in `live`."
  [items live]
  (let [claims (reduce (fn [acc {:keys [key formers]}]
                         (reduce (fn [acc former]
                                   (cond-> acc
                                     (not= former key)
                                     (update former (fnil conj #{}) key)))
                                 acc
                                 formers))
                       {}
                       items)]
    (into {}
          (keep (fn [[former targets]]
                  (when (and (= 1 (count targets))
                             (not (contains? live former)))
                    [former (first targets)])))
          claims)))

(defn former-key-index
  "{former-key -> current-key} across every source and content type in `plugins`, for picks of
   no known type. `offered` is from `offered-keys`; nil means not yet known, and the index is
   empty.
   GOTCHA: drops a former key claimed by more than one item, held by any library item (disabled
   ones included), or in `offered` (built-in content included). Such a key still answers."
  [plugins offered]
  (if (nil? offered)
    {}
    (let [items (items-with-formers plugins)]
      (claimed-once items (into offered (map :key) items)))))

(defn typed-former-key-index
  "{content-type {former-key -> current-key}} for each `library/pick-homes` type that
   `offered-by-type` (from `library/offered-by-type`) lists; a type it does not list is absent.
   Per type, the same rule as `former-key-index`, against that type's items and offered keys only."
  [plugins offered-by-type]
  (let [by-type (group-by :type (items-with-formers plugins))]
    (into {}
          (keep (fn [t]
                  (when-let [offered (get offered-by-type t)]
                    (let [items (get by-type t)]
                      [t (claimed-once items (into offered (map :key) items))]))))
          (set (vals library/pick-homes)))))

(defn former-key-indexes
  "What a heal reads: {:flat (former-key-index plugins offered)
   :typed (typed-former-key-index plugins offered-by-type)}."
  [plugins offered offered-by-type]
  {:flat (former-key-index plugins offered)
   :typed (typed-former-key-index plugins offered-by-type)})

(defn- walk-entries
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

(defn- walk-picks
  "`opts` (::entity/options) with `f` applied to every pick that could be of `content-type`
   (`library/pick-types`); `content-type` nil: every pick."
  [opts content-type f]
  (walk-entries opts (fn [path e]
                       (if (or (nil? content-type) (seq (library/pick-types path [content-type])))
                         (f e)
                         e))))

(defn picks-of
  "Set of `character`'s pick keys that could be of `content-type` (see `walk-picks`)."
  [character content-type]
  (let [ks (atom #{})]
    (walk-picks (::entity/options character) content-type
                #(do (when-let [k (::entity/key %)] (swap! ks conj k)) %))
    @ks))

(defn relink-picks
  "`character` with each pick of `from` that could be of `content-type` rewritten to `to`, as
   {:character :rewrote} (see `reconcile-former-keys`). `content-type` nil: every pick."
  [character content-type from to]
  (let [rewrote (atom [])
        opts (walk-picks (::entity/options character) content-type
                         #(if (= from (::entity/key %))
                            (do (swap! rewrote conj {:from from :to to}) (assoc % ::entity/key to))
                            %))]
    {:character (cond-> character (seq @rewrote) (assoc ::entity/options opts))
     :rewrote @rewrote}))

(defn relink-to-ask
  "The index into `relinks` (db/pending-relinks) of the first rename to ask `character` about, or
   nil: a saved character not yet asked, holding the renamed item's old key as a pick of its type,
   while `plugins` holds both the renamed item (`:to`) and the item now under the old key."
  [relinks character plugins]
  (let [id (:db/id character)
        holds? (fn [ct k] (some #(some? (get-in % [ct k])) (vals plugins)))]
    (when id
      (first (keep-indexed (fn [i {:keys [content-type from to asked]}]
                             (when (and (not (contains? asked id))
                                        (contains? (picks-of character content-type) from)
                                        (holds? content-type to) (holds? content-type from))
                               i))
                           relinks)))))

(defn reconcile-former-keys
  "`character` with its picks rewritten through `indexes` (from `former-key-indexes`), as
   {:character :rewrote [{:from :to}]}. A pick at a `library/pick-homes` path whose type `:typed`
   lists uses that type's index only; any other pick uses `:flat`."
  [character {:keys [flat typed] :as indexes}]
  {:pre [(contains? indexes :typed)]}
  (if (or (and (empty? flat) (every? empty? (vals typed))) (nil? (::entity/options character)))
    {:character character :rewrote []}
    (let [rewrote (atom [])
          opts (walk-entries
                (::entity/options character)
                (fn [path e]
                  (let [t (library/pick-homes path)
                        idx (if (contains? typed t) (get typed t) flat)]
                    (if-let [to (get idx (::entity/key e))]
                      (do (swap! rewrote conj {:from (::entity/key e) :to to})
                          (assoc e ::entity/key to))
                      e))))]
      {:character (assoc character ::entity/options opts)
       :rewrote @rewrote})))

;; ── Class binding report ────────────────────────────────────────────────────
;; Reports, never repairs: classes that do not bind, and subclasses filed under the wrong class.
;; GOTCHA: an unbound class resets every choice below it, so the report names the class; the
;; missing-content report alone does not say which.

(defn- class-entry-subclass
  "The [selection-key subclass-key] a class entry carries, or nil. Mirrors the
   scan in extract-class-keys rather than re-deriving it differently."
  [class-opts]
  (some (fn [sel-key]
          (when-let [k (get-in class-opts [sel-key ::entity/key])]
            [sel-key k]))
        subclass-selection-keys))

(defn class-binding-report
  "{:unbound-classes [{:class-key :subclass-key?}] :subclass-mismatches [{:class-key :subclass-key
   :belongs-to :selection-key}]} for `character`. `loaded-class-keys`: the classes that exist now
   (the class dropdown's set). `subclass->class`: {subclass-key class-key}, as far as known.
   GOTCHA: a subclass absent from `subclass->class` is never a mismatch."
  [character loaded-class-keys subclass->class]
  (let [known (set loaded-class-keys)
        entries (get-in character [::entity/options :class])]
    (if-not (sequential? entries)
      {:unbound-classes [] :subclass-mismatches []}
      (reduce
       (fn [acc class-entry]
         (let [class-key (::entity/key class-entry)
               [sel-key subclass-key] (class-entry-subclass (::entity/options class-entry))
               owner (get subclass->class subclass-key)]
           (cond-> acc
             (and class-key (not (contains? known class-key)))
             (update :unbound-classes conj
                     (cond-> {:class-key class-key}
                       subclass-key (assoc :subclass-key subclass-key)))

             ;; Also when the class is loaded: a misfiled subclass binds without error and grants
             ;; the wrong features.
             (and class-key subclass-key owner (not= owner class-key))
             (update :subclass-mismatches conj
                     {:class-key class-key
                      :subclass-key subclass-key
                      :belongs-to owner
                      :selection-key sel-key}))))
       {:unbound-classes [] :subclass-mismatches []}
       entries))))

(defn subclass->class-index
  "{subclass-key -> class-key} from the :class of each loaded subclass. rename-key-in-plugin
   rewrites that field, so the index survives a conflict resolution. A subclass claimed by two
   classes across sources is dropped, not guessed, as former-key-index drops a contested claim."
  [plugins]
  (let [claims (for [[_ plugin] plugins
                     :when (map? plugin)
                     [k item] (:orcpub.dnd.e5/subclasses plugin)
                     :when (and (map? item) (:class item))]
                 [k (:class item)])]
    (->> (group-by first claims)
         (keep (fn [[k pairs]]
                 (let [owners (set (map second pairs))]
                   (when (= 1 (count owners)) [k (first owners)]))))
         (into {}))))
