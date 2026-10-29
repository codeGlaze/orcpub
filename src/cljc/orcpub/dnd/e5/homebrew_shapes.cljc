(ns orcpub.dnd.e5.homebrew-shapes
  "The shape of every homebrew field the app reads, and `clean`, which makes an entry fit them.
   Homebrew is untrusted: an imported, hand-edited or injected entry can hold anything anywhere.
   Code that reads homebrew assumes these shapes, so the seams run entries through here first.
   `read-structure` comes first, for a library as stored: a library, source or section kept as
   text, a section kept as a list. Requires only orcpub.common. See homebrew-guard.md."
  (:require [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])
            #?(:cljs [cljs.reader :as reader])
            [orcpub.common :as common]))

;; ---------------------------------------------------------------------------------------------
;; Shapes are data: :string, :num, [:list s], [:map {field s}] and so on. What each form means,
;; and what `clean` fixes for each, is in homebrew-guard.md, "The shape forms".

(defn fits?
  "Does `v` fit `shape` as it is? Allocates nothing, so a sound entry costs one walk."
  [shape v]
  (if (keyword? shape)
    (case shape
      :any true
      :string (string? v)
      :number (number? v)
      :int (integer? v)
      :keyword (keyword? v)
      :boolean (boolean? v)
      :map (map? v)
      :num (number? v)
      :key (keyword? v)
      false)
    (let [[op a b] shape]
      (case op
        :enum (contains? a v)
        :or (boolean (some #(fits? % v) (rest shape)))
        (:list :cards) (and (sequential? v) (every? #(fits? a %) v))
        :coll (and (or (sequential? v) (set? v)) (every? #(fits? a %) v))
        :set (and (set? v) (every? #(fits? a %) v))
        :map-of (and (map? v) (every? (fn [[k x]] (and (fits? a k) (fits? b x))) v))
        :flags (and (map? v) (every? (fn [[k x]] (and (fits? a k) (boolean? x))) v))
        :counts (and (map? v) (every? (fn [[k x]] (and (fits? a k) (number? x))) v))
        :indexed (and (map? v) (every? (fn [[k x]] (and (integer? k) (fits? a x))) v))
        ;; a field holding nil is absent (see clean-map)
        :map (and (map? v) (every? (fn [[k s]] (let [x (get v k)] (or (nil? x) (fits? s x)))) a))
        false))))

(defn- describe
  "A value's kind, in a reader's words."
  [v]
  (cond (nil? v) "empty" (string? v) "text" (number? v) "a number" (keyword? v) "a keyword"
        (boolean? v) (str v) (map? v) "a map" (set? v) "a set" (sequential? v) "a list"
        :else "something else"))

(defn shape-name
  "A shape in a reader's words: \"a list of text\", \"a map\"."
  [shape]
  (if (keyword? shape)
    (case shape
      :any "anything" :string "text" :number "a number" :int "a whole number"
      :keyword "a keyword" :boolean "true or false" :map "a map" :num "a number"
      :key "a keyword" (name shape))
    (let [[op a b] shape]
      (case op
        :or (str/join " or " (map shape-name (rest shape)))
        (:list :coll) (str "a list of " (shape-name a))
        :set (str "a set of " (shape-name a))
        :map-of (str "a map of " (shape-name a) " to " (shape-name b))
        :enum (str "one of " (str/join ", " (map pr-str (sort-by str a))))
        :flags (str "a map of " (shape-name a) " to true or false")
        :counts (str "a map of " (shape-name a) " to a number")
        :indexed (str "a numbered map of " (shape-name a))
        :map "a map"
        (pr-str shape)))))

(defn- finding
  "A finding record: the `path` it is at, the `action` taken (:fixed or :unreadable), a
   description of the found value `v`, and the name of the wanted `shape`."
  [path action v shape]
  {:path path :action action :found (describe v) :wanted (shape-name shape)})

(defn- parse-number
  "The number `s` spells, or nil: \"3\" and \"0.5\" read, \"three\" does not."
  [s]
  (when (re-matches #"\s*-?\d+(\.\d+)?\s*" s)
    #?(:clj (edn/read-string (str/trim s)) :cljs (js/parseFloat s))))

(defn- text->key
  "The keyword text stands for, or nil for blank text: \"Medium\" and \":medium\" read as :medium."
  [s]
  (let [t (-> s str/trim (str/replace #"^:" "") str/lower-case (str/replace #"\s+" "-"))]
    (when-not (str/blank? t) (keyword t))))

(declare clean-value)

;; Each clean-* returns {:value v' :findings [...]}, or only {:findings [...]} when nothing of the
;; value can be read; `v` has already been found not to fit.

(defn- clean-each
  "Each element of `xs` cleaned against `shape` (by `clean-x`, clean-value by default); one that
   cannot be read is left out."
  [shape xs path & [clean-x]]
  (reduce (fn [{:keys [value findings]} [i x]]
            (let [r ((or clean-x clean-value) shape x (conj path i))]
              {:value (if (contains? r :value) (conj value (:value r)) value)
               :findings (into findings (:findings r))}))
          {:value [] :findings []}
          (map-indexed vector xs)))

(defn- unreadable
  "The result for a value `v` at `path` that cannot be made to fit `shape`: only an :unreadable
   finding, no :value."
  [path v shape]
  {:findings [(finding path :unreadable v shape)]})

(defn- fixed
  "`r`, the cleaned inside of `v`, as a fix of `v` at `path`."
  [r path v shape]
  (update r :findings #(into [(finding path :fixed v shape)] %)))

(defn- lone
  "True when `v` is a single value (not nil, not a collection), which a list or set shape wraps."
  [v]
  (and (some? v) (not (coll? v))))

(defn- clean-scalar
  "Text `v` at `path` read as the scalar `shape` (:num or :key), as a :fixed result; any other
   shape or unreadable text gives an :unreadable result."
  [shape v path]
  (let [parsed (case shape
                 :num (when (string? v) (parse-number v))
                 :key (when (string? v) (text->key v))
                 nil)]
    (if (some? parsed)
      {:value parsed :findings [(finding path :fixed v shape)]}
      (unreadable path v shape))))

(defn- clean-list
  "`v` cleaned as a list of shape `a`: a sequence is cleaned element by element; a lone value or
   map is a list of one (a :fixed result); anything else is :unreadable."
  [a v path shape]
  (cond
    (sequential? v) (clean-each a v path)
    (or (map? v) (lone v)) (let [r (clean-value a v (conj path 0))]
                             (if (contains? r :value)
                               (fixed {:value [(:value r)] :findings (:findings r)} path v shape)
                               (unreadable path v shape)))
    :else (unreadable path v shape)))

(defn- card-or-text
  "A card cleaned against `a`; text where a card belongs is a card with that name (a trait listed
   by name)."
  [a x path]
  (if (and (string? x) (not (str/blank? x)))
    {:value {:name x} :findings [(finding path :fixed x a)]}
    (clean-value a x path)))

(defn- clean-cards
  "[:cards card]: a list of cards, where text names a card; a lone card, or lone text, is a list
   of one."
  [a v path shape]
  (cond
    (sequential? v) (clean-each a v path card-or-text)
    (or (map? v) (string? v)) (let [r (card-or-text a v (conj path 0))]
                                (if (contains? r :value)
                                  (fixed {:value [(:value r)] :findings (:findings r)} path v shape)
                                  (unreadable path v shape)))
    :else (unreadable path v shape)))

(defn- clean-set
  "`v` cleaned as a set of shape `a`: a set element by element; a sequence or a lone value becomes
   a set (a :fixed result); anything else is :unreadable."
  [a v path shape]
  (cond
    (set? v) (update (clean-each a v path) :value set)
    (sequential? v) (fixed (update (clean-each a v path) :value set) path v shape)
    (lone v) (let [r (clean-value a v (conj path 0))]
               (if (contains? r :value)
                 (fixed {:value #{(:value r)} :findings (:findings r)} path v shape)
                 (unreadable path v shape)))
    :else (unreadable path v shape)))

(defn- clean-entries
  "A map's entries, each key checked against `ks` and each value cleaned by `clean-v`."
  [ks clean-v v path]
  (reduce-kv (fn [{:keys [value findings]} k x]
               (let [p (conj path k)]
                 (if-not (fits? ks k)
                   {:value value :findings (into findings (:findings (unreadable p k ks)))}
                   (let [r (clean-v x p)]
                     {:value (if (contains? r :value) (assoc value k (:value r)) value)
                      :findings (into findings (:findings r))}))))
             {:value {} :findings []}
             v))

(defn- clean-marks
  "[:flags k] or [:counts k]: a map is cleaned entry by entry; a set or list of keys, or a lone
   key, marks each one (true, or 1)."
  [op a v path shape]
  (let [mark (if (= op :flags) true 1)]
    (cond
      (map? v)
      (clean-entries a (fn [x p]
                         (cond
                           (if (= op :flags) (boolean? x) (number? x)) {:value x :findings []}
                           ;; a flag holding any other value counts as set when it is truthy
                           (= op :flags) {:value (boolean x)
                                          :findings [(finding p :fixed x :boolean)]}
                           :else (clean-scalar :num x p)))
                     v path)
      (or (set? v) (sequential? v) (fits? a v))
      (let [ks (if (coll? v) (vec v) [v])]
        (fixed {:value (zipmap (filter #(fits? a %) ks) (repeat mark))
                :findings (vec (for [[i k] (map-indexed vector ks) :when (not (fits? a k))]
                                 (finding (conj path i) :unreadable k a)))}
               path v shape))
      :else (unreadable path v shape))))

(defn- clean-map
  "`v`'s listed `fields` made to fit their shapes; the rest pass untouched. A field holding nil
   reads as absent, as every reader takes it: the builders store some nils on purpose (a class's
   :spell-list-kw, a spellcasting :ability), so nil is never a finding."
  [fields v path]
  (reduce-kv (fn [{:keys [value findings] :as acc} k s]
               (if (nil? (get value k))
                 acc
                 (let [r (clean-value s (get value k) (conj path k))]
                   {:value (if (contains? r :value) (assoc value k (:value r)) (dissoc value k))
                    :findings (into findings (:findings r))})))
             {:value v :findings []}
             fields))

(defn clean-value
  "`v` made to fit `shape` at `path`: {:value v' :findings [...]}, or only {:findings [...]} when
   nothing of it can be read. A value that already fits comes back as the identical object with no
   findings. A finding is {:path :action :found :wanted}; :action is :fixed when the meaning was
   clear (\"3\" for 3, a lone item for a list of one) and :unreadable when it was not."
  [shape v path]
  (cond
    (fits? shape v) {:value v :findings []}
    (keyword? shape) (clean-scalar shape v path)
    :else
    (let [[op a b] shape]
      (case op
        ;; the first alternative something can be read from
        :or (or (first (filter #(contains? % :value) (map #(clean-value % v path) (rest shape))))
                (unreadable path v shape))
        :list (clean-list a v path shape)
        :cards (clean-cards a v path shape)
        :coll (if (or (sequential? v) (set? v))
                (cond-> (clean-each a v path) (set? v) (update :value set))
                (unreadable path v shape))
        :set (clean-set a v path shape)
        :map-of (if (map? v)
                  (clean-entries a #(clean-value b %1 %2) v path)
                  (unreadable path v shape))
        (:flags :counts) (clean-marks op a v path shape)
        :indexed (cond
                   (map? v) (clean-entries :int #(clean-value a %1 %2) v path)
                   (sequential? v) (let [r (clean-each a v path)]
                                     (fixed {:value (zipmap (range) (:value r))
                                             :findings (:findings r)}
                                            path v shape))
                   :else (unreadable path v shape))
        :map (if (map? v) (clean-map a v path) (unreadable path v shape))
        (unreadable path v shape)))))

;; ---------------------------------------------------------------------------------------------
;; The table. Each field a reader assumes a shape of, as the code reads it. Where the code or the
;; builders accept more than one shape the field allows all of them: loading is never stricter
;; than saving. Where a field's meaning is unsettled it is left as :any, not guessed.

(def ^:private card
  "A trait, an option or an action: what `traits-modifiers` and the sheets read."
  [:map {:name :string :description :string :summary :string :level :num :type :key}])

(def ^:private cards [:cards card])

(def ^:private choice
  "A choose-N: {:choose 2 :options {:athletics true}} (or {:any true})."
  [:map {:choose :num :options [:flags :keyword] :order :num}])

(def ^:private flags [:flags :keyword])
(def ^:private counts [:counts :keyword])

(def ^:private equipment-choices [:list [:map {:name :string :options counts}]])

(def ^:private profs
  [:map {:skill flags :skill-options choice :skill-expertise-options choice
         :tool flags :tool-options counts :language-options choice
         :armor flags :weapon flags :save flags :weapon-proficiency-options choice
         :multiclass-skill-options choice :multiclass-tool-options counts}])

(def ^:private props
  "What `make-feat-modifiers` reads; other keys pass untouched."
  [:map {:initiative :num :speed :num :flying-speed :num :swimming-speed :num :max-hp-bonus :num
         :language flags :saving-throw-advantage flags :armor-prof flags :weapon-prof flags
         :tool-prof-or-expertise flags :skill-prof flags :skill-prof-or-expertise flags
         :damage-resistance flags :damage-immunity flags
         :weapon-prof-choice :num :language-choice :num :skill-tool-choice :num}])

(def ^:private abilities [:map-of :keyword :num])

(def ^:private known-spells
  "A race's or subrace's spells: [{:level 3 :value {:key :light :ability ... :level 0}}]."
  [:list [:map {:level :num :value [:map {:key :keyword :ability :keyword :level :num}]}]])

(def ^:private level-modifiers [:list [:map {:type :key :level :num}]])
(def ^:private level-selections [:list [:map {:type :key :level :num :num :num}]])

(def ^:private expanded-spells
  "A subclass's always-prepared spells: {1 {0 :bless 1 :cure-wounds} ...}."
  [:map-of :int [:indexed :keyword]])

(def ^:private raw-structures
  "Template selections and modifiers spliced in as they are; only a list of maps can be."
  [:list :map])

(def shapes
  "Content type -> the shape of one entry."
  {:orcpub.dnd.e5/spells
   [:map {:name :string :level :num :school :string :spell-lists flags
          :components [:map {:verbal :any :somatic :any :material :any :material-component :string}]
          :description :string :summary :string}]

   :orcpub.dnd.e5/monsters
   [:map {:name :string :size :key :type :key :subtypes [:set :key] :challenge :num
          :hit-points [:map {:die :num :die-count :num :modifier :num :mean :num}]
          :saving-throws [:map-of :keyword :num] :skills [:map-of :keyword :num]
          :condition-immunities :string
          :props [:map {:damage-resistance flags :damage-immunity flags
                        :damage-vulnerability flags :condition-immunity flags :language flags}]
          :traits cards :actions cards :reactions cards
          :legendary-actions [:map {:description :string :actions cards}]}]

   :orcpub.dnd.e5/encounters
   [:map {:name :string
          :creatures [:list [:map {:type :key
                                   :creature [:map {:monster :keyword :num :num
                                                    :character :num}]}]]}]

   :orcpub.dnd.e5/backgrounds
   [:map {:name :string :profs profs :traits cards
          :equipment-choices equipment-choices :weapon-choices equipment-choices
          :armor-choices equipment-choices
          :equipment counts :weapons counts :armor counts :treasure counts
          :custom-equipment [:map-of :string :num] :custom-treasure [:map-of :string :num]
          :selections raw-structures :modifiers raw-structures}]

   :orcpub.dnd.e5/languages [:map {:name :string}]
   :orcpub.dnd.e5/invocations [:map {:name :string}]
   :orcpub.dnd.e5/boons [:map {:name :string}]

   :orcpub.dnd.e5/selections [:map {:name :string :options cards}]

   :orcpub.dnd.e5/feats
   ;; :ability-increases is a set of ability keys (released form) or a list of [amount pool]
   ;; pairs (a spread, as for races: options.cljc compile-ability-increases); the pool is
   ;; checked there, so it is :any here.
   [:map {:name :string :description :string
          :ability-increases [:or [:set :keyword] [:list [:list :any]]]
          :prereqs [:set :keyword] :path-prereqs [:map {:race flags}] :props props}]

   :orcpub.dnd.e5/races
   [:map {:name :string :size :key :speed :num :darkvision :num :abilities abilities
          :languages [:set :string] :profs profs :props props :spells known-spells :traits cards
          :subraces [:list :map] :selections raw-structures}]

   :orcpub.dnd.e5/subraces
   [:map {:race :key :name :string :size :key :speed :num :darkvision :num :abilities abilities
          :profs profs :props props :spells known-spells :traits cards
          :selections raw-structures}]

   :orcpub.dnd.e5/subclasses
   ;; :spellcasting :spell-list is a keyword or a map; which the code means is unsettled
   [:map {:class :key :name :string :level-modifiers level-modifiers
          :level-selections level-selections
          :spellcasting [:map {:level-factor :num :known-mode :keyword :spell-list-kw :keyword}]
          :cleric-spells expanded-spells
          :paladin-spells [:map-of [:enum #{1 2 3 4 5}] [:indexed :keyword]]
          :warlock-spells expanded-spells
          :profs profs :props props :traits cards :selections raw-structures}]

   :orcpub.dnd.e5/classes
   [:map {:name :string :hit-die :num :subclass-level :num :subclass-title :string
          :ability-increase-levels [:list :num] :level-modifiers level-modifiers
          :level-selections level-selections :traits cards :profs profs :props props
          :spellcasting [:map {:level-factor :num :known-mode :keyword :ability :keyword
                               :spells-known [:map-of :num :any]
                               :cantrips-known [:map-of :num :num]
                               :spell-list [:map-of :num [:set :keyword]]
                               :spell-list-kw :keyword}]
          :weapons counts :armor counts :equipment counts
          :weapon-choices equipment-choices :armor-choices equipment-choices
          :equipment-choices equipment-choices :equipment-selections [:list :map]
          :subclasses [:list :map] :selections raw-structures}]})

(def ^:private entry-fields
  "What every entry of every type holds."
  {:former-keys [:list :keyword]})

(def ^:private required
  "Fields an entry cannot be used without, by type: what it is built inside. Missing, the entry
   cannot be read, rather than silently never shown."
  {:orcpub.dnd.e5/subraces {:race :keyword}
   :orcpub.dnd.e5/subclasses {:class :keyword}})

(defn clean-entry
  "`entry` of `content-type` made to fit the table: {:entry e' :findings [...]}, or only
   {:findings [...]} when the entry is not a map. A sound entry comes back identical."
  [content-type entry]
  (let [shape (get shapes content-type [:map {}])
        [_ fields] shape
        r (clean-value [:map (merge entry-fields fields)] entry [])
        missing (when (contains? r :value)
                  (for [[field wanted] (get required content-type)
                        :when (nil? (get (:value r) field))]
                    (finding [field] :unreadable nil wanted)))]
    (if (contains? r :value)
      {:entry (:value r) :findings (into (:findings r) missing)}
      {:findings (:findings r)})))

(defn- key-like?
  "A keyword starting with a letter: what an entry's :key, and the key it is filed under, are."
  [k]
  (and (keyword? k) (some? (re-find #"^[A-Za-z]" (name k)))))

(def ^:private a-section
  "A content section: entries by key."
  [:map-of :keyword :map])

(defn- clean-section
  "One content section: each entry cleaned, except one identical to the same entry in `before`.
   Returns {:section s' :findings [...]}, `s'` identical when nothing changed."
  [content-type section before]
  (reduce-kv (fn [{:keys [section findings] :as acc} k entry]
               (let [r (if (identical? entry (get before k))
                         {:entry entry}
                         (let [restore? (and (map? entry) (contains? entry :key)
                                             (not (key-like? (:key entry))) (key-like? k))
                               r (clean-entry content-type (cond-> entry restore? (assoc :key k)))]
                           ;; a :key that is not a key takes the one the entry is filed under
                           (cond-> r
                             restore? (update :findings conj
                                              (finding [:key] :fixed (:key entry) :keyword)))))
                     fs (map #(assoc % :key k) (:findings r))]
                 (cond
                   (not (contains? r :entry)) {:section (dissoc section k)
                                               :findings (into findings fs)}
                   (identical? entry (:entry r)) (update acc :findings into fs)
                   :else {:section (assoc section k (:entry r)) :findings (into findings fs)})))
             {:section section :findings []}
             section))

(defn clean-library
  "A {source-name source} library with every entry the app reads made to fit the table:
   {:library l' :findings [{:source :content-type :key :path :action :found :wanted}]}. A section
   stored as true or false (the format allows it) reads as empty; any other section that is not a
   map, and any entry that is not, cannot be read and is left out. A sound library comes back
   identical, and so does each sound source and section. With `before`, the library as it was,
   only what is not identical to it is walked: a change to one entry walks one entry."
  [library & [before]]
  (if-not (map? library)
    {:library library :findings []}
    (reduce-kv
     (fn [acc src source]
       (if (or (not (map? source)) (identical? source (get before src)))
         acc
         (let [{:keys [source findings]}
               (reduce-kv
                (fn [{:keys [source findings] :as acc} ct section]
                  (let [tag #(assoc % :source src :content-type ct)]
                    (cond
                      (not (contains? shapes ct)) acc
                      (identical? section (get-in before [src ct])) acc
                      (boolean? section) {:source (assoc source ct {}) :findings findings}
                      (not (map? section))
                      {:source (dissoc source ct)
                       :findings (conj findings (tag (finding [] :unreadable section a-section)))}
                      :else (let [r (clean-section ct section (get-in before [src ct]))]
                              ;; unchanged, it may still have findings (a required field missing)
                              {:source (cond-> source
                                         (not (identical? section (:section r)))
                                         (assoc ct (:section r)))
                               :findings (into findings (map tag (:findings r)))}))))
                {:source source :findings []}
                source)]
           (cond-> (update acc :findings into findings)
             (not (identical? source (get library src))) (assoc-in [:library src] source)))))
     {:library library :findings []}
     library)))

(defn split-unreadable
  "`cleaned` (from clean-library) without each entry `findings` could not read, and those entries
   as `library` held them: {:library l :set-aside {source {content-type {key entry}}}}. A section
   that could not be read is set aside whole, as written."
  [cleaned library findings]
  (reduce (fn [acc {:keys [source content-type key action]}]
            (cond
              (not= :unreadable action) acc
              key (-> acc
                      (update-in [:library source content-type] dissoc key)
                      (assoc-in [:set-aside source content-type key]
                                (get-in library [source content-type key])))
              :else (assoc-in acc [:set-aside source content-type]
                              (get-in library [source content-type]))))
          {:library cleaned :set-aside {}}
          findings))

(defn fix-library
  "`library` with every fix the table makes, and each entry it cannot read left exactly as written:
   the shape check sets that one aside, with its original, when the library is stored. For an
   import, which shows the fixes before anything is stored. {:library l :findings [...]}."
  [library]
  (let [{cleaned :library findings :findings} (clean-library library)
        {kept :library aside :set-aside} (split-unreadable cleaned library findings)]
    {:library (reduce-kv (fn [l src sections]
                           (reduce-kv (fn [l ct v]
                                        (if (map? v)
                                          (update-in l [src ct] merge v)
                                          (assoc-in l [src ct] v)))
                                      l sections))
                         kept aside)
     :findings findings}))

;; ---------------------------------------------------------------------------------------------
;; How a library is stored, before its fields: a library, a source or a section kept as text, a
;; section kept as a list. Repairs are {:source :section :repair}, worded for the import log by
;; orcbrew-validation/repair-description.

(defn- read-back
  "Text read back as data, or nil when it does not read."
  [s]
  (try #?(:clj (edn/read-string s) :cljs (reader/read-string s))
       (catch #?(:clj Exception :cljs :default) _ nil)))

(defn- content-section?
  "A key naming a content type (`:orcpub.dnd.e5/spells`), as opposed to `:disabled?`."
  [k]
  (and (qualified-keyword? k) (= "orcpub.dnd.e5" (namespace k))))

(defn- list->entries
  "Entries stored as a list, keyed the way the library keys them: each entry's own :key, or the
   key its :name derives. nil when any element is not an entry, has neither, or repeats a key:
   guessing there would invent or silently lose content."
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

(defn read-section
  "A content section as stored, in the shape the library uses: {:section v}, {:section v :repair
   how}, {:dropped :empty-section}, or {:damaged v} when nothing readable is left (the loader sets
   that aside whole). Left alone, a section stored as text or as a list imported as successful,
   loaded silently, and made export refuse the source."
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

(defn read-source
  "`read-section` over one source's sections: {:source s :repairs [{:section :repair}]}. A section
   nothing can be read from stays where it is, for the loader to set aside."
  [source]
  (reduce-kv
   (fn [acc k v]
     (if-not (content-section? k)
       acc
       (let [{:keys [section repair dropped]} (read-section v)]
         (cond
           dropped (-> acc (update :source dissoc k) (update :repairs conj {:section k :repair dropped}))
           repair (-> acc (assoc-in [:source k] section) (update :repairs conj {:section k :repair repair}))
           :else acc))))
   {:source source :repairs []}
   source))

(defn read-library
  "A {source-name source} library as stored, in the shape clean-library reads: the whole library,
   or a source, read back from text, then `read-source` over each source. Text counts as a library
   only when it reads as one. {:library l :repairs [{:source :section :repair}]}."
  [library]
  (let [from-text (when (string? library) (read-back library))
        whole? (and (map? from-text) (every? string? (keys from-text)))
        library (if whole? from-text library)]
    (if-not (map? library)
      {:library library :repairs []}
      (cond-> (reduce-kv
               (fn [acc src source]
                 (let [from-text (when (string? source) (read-back source))
                       source (if (map? from-text) from-text source)]
                   (if (map? source)
                     (let [{s :source rs :repairs} (read-source source)]
                       (-> acc
                           (assoc-in [:library src] s)
                           (update :repairs into
                                   (cond->> (map #(assoc % :source src) rs)
                                     (map? from-text) (cons {:source src :repair :source-from-text})))))
                     (assoc-in acc [:library src] source))))
               {:library {} :repairs []}
               library)
        whole? (update :repairs #(into [{:repair :library-from-text}] %))))))

(defn read-import
  "An imported file's data as read: a whole file kept as text read back, then a single source or a
   {source-name source} library put in shape. {:data d :repairs [...]}; `d` is not a map only when
   nothing could be read."
  [data]
  (let [from-text (when (string? data) (read-back data))
        data (if (map? from-text) from-text data)
        whole (when (map? from-text) [{:repair :file-from-text}])]
    (cond
      (not (map? data)) {:data data :repairs []}
      (and (seq data) (every? string? (keys data)))
      (let [{l :library rs :repairs} (read-library data)]
        {:data l :repairs (into (vec whole) rs)})
      :else
      (let [{s :source rs :repairs} (read-source data)]
        {:data s :repairs (into (vec whole) rs)}))))
