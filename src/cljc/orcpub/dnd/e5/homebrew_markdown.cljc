(ns orcpub.dnd.e5.homebrew-markdown
  "A stored homebrew source ({content-type {item-key item}}) written as printable, 5e-style
   Markdown, for sharing a class or subclass for feedback. Pure string building, shared by the
   browser and the JVM. Every field is read as untrusted: a wrong type or a missing key skips
   its line and never throws. Design: docs/kb/plan-homebrew-markdown-export.md on agents/develop."
  (:require [clojure.string :as s]))

;; ---------------------------------------------------------------------------------------------
;; Escaping

(defn- escape-inline
  "`x` (a one-line string placed mid-line) with only the characters that act anywhere made literal:
   backslash, `*`, `_`, backtick, `<`, `[` and `]`. Returns a string."
  [x]
  (s/replace x #"[\\*_`<\[\]]" (fn [c] (str "\\" c))))

(defn- escape-line
  "One line of `line` with Markdown syntax made literal: backslash, `*`, `_`, backtick, `<`, `[`
   and `]` anywhere; a leading `#`, `>`, `~`, `-`, `+` or `=` (headings, quotes, fences, lists,
   underlines); and the `.` or `)` after a leading number (ordered lists). Returns a string."
  [line]
  (-> line
      escape-inline
      (s/replace #"^(\s*)([#>~+=-])" (fn [[_ ws c]] (str ws "\\" c)))
      (s/replace #"^(\s*\d+)([.)])" (fn [[_ n c]] (str n "\\" c)))))

(defn escape-md
  "`s` (any value; nil gives \"\") as text that cannot alter the formatting around it. Line
   endings become \"\\n\", each line loses trailing spaces, and line breaks are kept."
  [s]
  (if (nil? s)
    ""
    (->> (s/split (s/replace (str s) #"\r\n?" "\n") #"\n")
         (map (comp escape-line s/trimr))
         (s/join "\n"))))

(defn escape-cell
  "`s` as the text of one table cell: `escape-md`, line breaks turned into spaces, and `|` made
   literal. Returns a string."
  [s]
  (-> (str (when (some? s) s))
      (s/replace #"\s*[\r\n]+\s*" " ")
      escape-md
      (s/replace "|" "\\|")))

;; ---------------------------------------------------------------------------------------------
;; Reading untrusted values

(defn- text
  "`x` trimmed when it is a string with content, else nil."
  [x]
  (when (string? x)
    (let [t (s/trim x)]
      (when-not (s/blank? t) t))))

(defn- finite-num
  "`x` when it is an ordinary number (not NaN, not infinite, not absurdly large), else nil."
  [x]
  (when (and (number? x) (== x x) (< -1e15 x 1e15))
    x))

(defn- whole
  "`x` as a long when it is a whole number, else nil."
  [x]
  (when (finite-num x)
    (when (== x (Math/floor (double x)))
      (long x))))

(defn- level-of
  "`x` as a character level when it is a whole number from 1 to 20, else nil."
  [x]
  (when-let [n (whole x)]
    (when (<= 1 n 20) n)))

(defn- fmt-num
  "A finite number `n` as text without a trailing \".0\"."
  [n]
  (if (whole n) (str (whole n)) (str n)))

(defn- maps-of
  "The maps in `x` as a vector: a lone map is one item, a list or set is filtered to its maps,
   anything else gives []."
  [x]
  (cond
    (map? x) [x]
    (or (sequential? x) (set? x)) (filterv map? x)
    :else []))

(defn- sorted-entries
  "The [k v] pairs of `m` in a fixed order (by the text of the key); nil when `m` is not a map."
  [m]
  (when (map? m)
    (sort-by (comp str key) m)))

(defn- set-keys
  "The keys of `m` whose value is truthy, in a fixed order; [] when `m` is not a map."
  [m]
  (vec (keep (fn [[k v]] (when v k)) (sorted-entries m))))

(defn- one-line
  "`x` as a single trimmed line of text (runs of whitespace collapsed), or nil when blank."
  [x]
  (some-> (text x) (s/replace #"\s+" " ")))

(defn- label
  "A person-readable name for `x`: a keyword (:cartographers-tools) becomes \"Cartographers
   Tools\", a string stays as written; nil for anything else or blank."
  [x]
  (cond
    (keyword? x) (not-empty (->> (s/split (name x) #"[-_]+")
                                 (remove s/blank?)
                                 (map s/capitalize)
                                 (s/join " ")))
    (string? x) (one-line x)
    :else nil))

(def ^:private ability-names
  "The last part of an ability key -> its name."
  {"str" "Strength" "dex" "Dexterity" "con" "Constitution"
   "int" "Intelligence" "wis" "Wisdom" "cha" "Charisma"})

(defn- ability-label
  "The name of the ability key `x` (:orcpub.dnd.e5.character/wis gives \"Wisdom\"); other keys
   are labelled as they are. Nil when `x` is not readable as a name."
  [x]
  (or (when (keyword? x) (get ability-names (name x)))
      (label x)))

(defn- ordinal
  "The whole number `n` as \"1st\", \"2nd\", \"11th\"."
  [n]
  (str n (if (<= 11 (mod n 100) 13)
           "th"
           (case (mod n 10) 1 "st" 2 "nd" 3 "rd" "th"))))

(defn- esc-join
  "`xs` (strings; blanks dropped) escaped and joined with \", \"; nil when nothing is left."
  [xs]
  (not-empty (s/join ", " (map escape-md (remove s/blank? xs)))))

(defn- kv
  "A bullet \"- **k:** v\" where `v` is already escaped text; nil when `v` is blank."
  [k v]
  (when (and (string? v) (not (s/blank? v)))
    (str "- **" k ":** " v)))

;; ---------------------------------------------------------------------------------------------
;; Level modifiers

(def modifier-labels
  "Level-modifier :type -> the name the class and subclass builders show for it."
  {:weapon-prof "Weapon Proficiency"
   :armor-prof "Armor Proficiency"
   :tool-prof "Tool Proficiency"
   :skill-prof "Skill Proficiency"
   :num-attacks "Number of Attacks"
   :damage-resistance "Damage Resistance"
   :damage-immunity "Damage Immunity"
   :saving-throw-advantage "Saving Throw Advantage"
   :flying-speed "Flying Speed"
   :flying-speed-equals-walking-speed "Flying Speed Equals Walking Speed"
   :swimming-speed "Swimming Speed"
   :spell "Spell"})

(declare value-text)

(defn- speed-text
  "A speed value in feet (\"30 ft.\"); nil when `v` is not a number."
  [v]
  (some-> (finite-num v) fmt-num (str " ft.")))

(defn- weapon-prof-text
  "A weapon-proficiency value: :simple and :martial name a whole category, other keys a weapon."
  [v]
  (case v
    :simple "All simple weapons"
    :martial "All martial weapons"
    (value-text v)))

(defn- spell-text
  "A spell grant {:key :guidance :ability ::char/wis :level 0} as \"Guidance (cantrip, Wisdom)\";
   a value that is not a spell map is shown as plain text."
  [v]
  (if (map? v)
    (let [lvl (whole (:level v))
          notes (remove nil? [(when lvl (if (zero? lvl) "cantrip" (str "level " lvl)))
                              (ability-label (:ability v))])]
      (when-let [nm (label (:key v))]
        (str nm (when (seq notes) (str " (" (s/join ", " notes) ")")))))
    (value-text v)))

(def ^:private modifier-value-formats
  "Level-modifier :type -> the function that writes its :value; other types use `value-text`."
  {:weapon-prof weapon-prof-text
   :flying-speed speed-text
   :swimming-speed speed-text
   :spell spell-text})

(defn- value-text
  "Any modifier value as text: keywords and strings by name, numbers as numbers, maps as
   \"key: value\" pairs and lists joined by commas. Nil when nothing readable is left."
  [v]
  (cond
    (keyword? v) (label v)
    (string? v) (one-line v)
    (finite-num v) (fmt-num v)
    (boolean? v) (if v "Yes" "No")
    (map? v) (not-empty (s/join ", " (keep (fn [[k x]]
                                             (when-let [t (value-text x)]
                                               (if-let [kl (label k)] (str kl ": " t) t)))
                                           (sorted-entries v))))
    (coll? v) (not-empty (s/join ", " (keep value-text (if (set? v) (sort-by str v) v))))
    :else nil))

(defn modifier->text
  "One level modifier {:type :weapon-prof :value :martial} as \"Weapon Proficiency: All martial
   weapons\". A type missing from `modifier-labels` is written \"Type: value\" from its own name.
   Returns nil when the modifier is not a map or has no readable type."
  [modifier]
  (when (map? modifier)
    (let [t (:type modifier)
          head (or (get modifier-labels t) (label t))
          v ((get modifier-value-formats t value-text) (:value modifier))]
      (when head
        (if v (str head ": " v) head)))))

;; ---------------------------------------------------------------------------------------------
;; Levelled entries: what a class or subclass gives, level by level

(def ^:private trait-types
  "A feature's :type -> the activation shown after its name; :other and unknown types show none."
  {:action "Action" :b-action "Bonus Action" :reaction "Reaction"})

(defn- feature-block
  "A feature as `***Name.*** text` (name and text already escaped). Further paragraphs of the
   text follow after blank lines. Returns a string."
  [title activation desc]
  (let [[first-para & more] (remove s/blank? (map s/trim (s/split (or desc "") #"\n")))
        head (str "***" title (when activation (str " (" activation ")")) ".***")]
    (s/join "\n\n" (cons (if first-para (str head " " first-para) head) more))))

(defn- entry-level
  "The level key of a stored :level `raw`: a missing level is 1 (a feature with no level is a
   first-level feature), a level that is not 1 to 20 gives nil."
  [raw]
  (if (nil? raw) 1 (level-of raw)))

(defn- trait-entries
  "The features in `traits` (cards with a :name, :description and :level) as entries
   {:level :cell :block}; cards with neither name nor text are dropped."
  [traits]
  (vec
   (for [t (maps-of traits)
         :let [nm (one-line (:name t))
               desc (or (text (:description t)) (text (:summary t)))]
         :when (or nm desc)]
     {:level (entry-level (:level t))
      :cell (escape-cell (or nm "Unnamed feature"))
      :block (feature-block (escape-md (or nm "Unnamed feature"))
                            (get trait-types (:type t))
                            (escape-md desc))})))

(defn- option-bullet
  "One option of a choice as a bullet \"- ***Name.*** text\", or nil when it has no name."
  [option]
  (when-let [nm (one-line (:name option))]
    (let [desc (some-> (or (text (:description option)) (text (:summary option)))
                       escape-md
                       (s/replace #"\n+" " "))]
      (str "- ***" (escape-md nm) ".***" (when desc (str " " desc))))))

(defn- selection-entries
  "The level-selections {:type :wayfarer-tricks :level 3 :num 1} as entries. A selection that
   `ctx` (:selections, key -> selection item) knows lists its options; an unknown one is named
   from its key. Returns a vector."
  [selections ctx]
  (vec
   (for [sel (maps-of selections)
         :let [item (get-in ctx [:selections (:type sel)])
               item (when (map? item) item)
               title (or (one-line (:name item)) (label (:type sel)))
               n (max 1 (or (whole (:num sel)) 1))]
         :when title]
     {:level (entry-level (:level sel))
      :cell (str (escape-cell title) " (choose " n ")")
      :block (let [options (keep option-bullet (maps-of (:options item)))
                   head (str "***" (escape-md title) ".*** Choose " n (when (seq options) ":"))]
               (if (seq options)
                 (str head "\n\n" (s/join "\n" options))
                 head))})))

(defn- modifier-entries
  "The level-modifiers as entries {:level :bullet} (no table cell: the level table names features
   only); unreadable ones are dropped."
  [modifiers]
  (vec
   (for [m (maps-of modifiers)
         :let [phrase (modifier->text m)]
         :when phrase]
     {:level (entry-level (:level m))
      :bullet (str "- " (escape-md phrase))})))

(defn- level-entries
  "All of `item`'s levelled entries, features first, then choices, then granted modifiers."
  [item ctx]
  (vec (concat (trait-entries (:traits item))
               (selection-entries (:level-selections item) ctx)
               (modifier-entries (:level-modifiers item)))))

(defn- heading
  "A Markdown heading of `depth` (1 to 6) with `title`."
  [depth title]
  (str (apply str (repeat (min 6 depth) "#")) " " title))

(defn- table-block
  "A `| Level | Features |` table from [level-number cell-text] rows. Returns a string."
  [rows]
  (s/join "\n"
          (concat ["| Level | Features |" "| --- | --- |"]
                  (for [[lvl cell] rows]
                    (str "| " (ordinal lvl) " | " (if (s/blank? cell) "-" cell) " |")))))

(defn- progression-rows
  "Table rows [level cell] for the levels in `levels`, each cell the names of the level's features
   and choices joined. A level that only grants benefits (modifiers) says \"See below\"; a level
   with nothing has an empty cell."
  [entries levels]
  (let [by-level (group-by :level entries)]
    (for [lvl levels
          :let [es (get by-level lvl)
                names (keep :cell es)]]
      [lvl (cond (seq names) (s/join ", " names)
                 (seq es) "See below"
                 :else "")])))

(defn- features-blocks
  "The `Features` section of a class or subclass: a heading, then for each level (in order)
   a sub-heading, its features and choices, and a list of what it grants. Entries with no
   valid level come last. Returns a vector of blocks, [] when there is nothing to show."
  [entries depth]
  (let [by-level (group-by :level entries)
        sections (for [lvl (sort-by #(or % 99) (keys by-level))
                       :let [es (get by-level lvl)
                             features (keep :block (remove :bullet es))
                             grants (keep :bullet es)]
                       :when (or (seq features) (seq grants))]
                   (concat [(heading (inc depth) (if lvl
                                                   (str (ordinal lvl) " Level")
                                                   "Features without a valid level"))]
                           features
                           (when (seq grants)
                             ["*Gained at this level:*" (s/join "\n" grants)])))]
    (if (seq sections)
      (into [(heading depth "Features")] (apply concat sections))
      [])))

;; ---------------------------------------------------------------------------------------------
;; Proficiencies, equipment and spellcasting

(def ^:private prof-flag-fields
  "The :profs keys holding a set of flags -> [heading label-fn]."
  [[:armor "Armor" label]
   [:weapon "Weapons" label]
   [:tool "Tools" label]
   [:save "Saving Throws" ability-label]
   [:skill "Skills" label]])

(def ^:private prof-choice-fields
  "The :profs keys holding a choose-N -> heading."
  [[:skill-options "Skill Choices"]
   [:skill-expertise-options "Expertise Choices"]
   [:weapon-proficiency-options "Weapon Choices"]
   [:tool-options "Tool Choices"]
   [:language-options "Language Choices"]])

(defn- choice-text
  "A choose-N {:choose 2 :options {:a true} or {:any true}} as \"Choose 2 from A, B\"; nil when
   `c` is not a map or lists nothing."
  [c]
  (when (map? c)
    (let [n (whole (:choose c))
          opts (if (map? (:options c)) (:options c) (dissoc c :choose :options))
          anything? (get opts :any)
          names (when-not anything? (remove nil? (map label (set-keys opts))))
          head (str "Choose" (when n (str " " n)))]
      (cond
        anything? (str head " (any)")
        (seq names) (str head " from " (esc-join names))))))

(defn- profs-lines
  "Bullets for a :profs map (flags, then choices). Returns a vector, [] when nothing is readable."
  [profs]
  (if (map? profs)
    (vec (concat
          (for [[k head f] prof-flag-fields
                :let [v (esc-join (keep f (set-keys (get profs k))))]
                :when v]
            (kv head v))
          (for [[k head] prof-choice-fields
                :let [v (choice-text (get profs k))]
                :when v]
            (kv head v))))
    []))

(defn- count-list
  "A map of key -> count ({:dagger 2 :rope 1}) as \"Dagger (2), Rope\"; nil when it lists nothing."
  [m]
  (esc-join
   (keep (fn [[k v]]
           (when (and v (not (zero? (or (whole v) 1))))
             (when-let [l (label k)]
               (let [n (whole v)]
                 (if (and n (> n 1)) (str l " (" n ")") l)))))
         (sorted-entries m))))

(defn- choice-lines
  "Bullets for a list of equipment choices [{:name \"Weapon\" :options {:dagger 1}}]; `fallback`
   names a choice that has no name."
  [choices fallback]
  (for [c (maps-of choices)
        :let [opts (count-list (:options c))]
        :when opts]
    (str "- **" (escape-md (or (one-line (:name c)) fallback)) ":** choose one of " opts)))

(def ^:private subchoice-labels
  "An equipment option's :choose :from -> what the player picks (as the class builder names it)."
  {:simple "any simple weapon" :simple-melee "any simple melee weapon" :martial "any martial weapon"
   :any-weapon "any weapon" :holy-symbol "a holy symbol" :arcane-focus "an arcane focus"
   :druidic-focus "a druidic focus" :musical-instrument "a musical instrument"
   :pack "an equipment pack"})

(defn- grant-text
  "One equipment grant {:kind :weapon :key :longsword :qty 2} as \"Longsword (2)\"; nil when
   it names nothing."
  [{:keys [key qty]}]
  (when-let [l (or (get {:simple "Any simple weapon" :martial "Any martial weapon"} key)
                   (label key))]
    (let [n (whole qty)]
      (if (and n (> n 1)) (str l " (" n ")") l))))

(defn- equipment-option-text
  "One option of a rich equipment choice, {:name :grants [...] :choose [{:from :martial}]}, as
   escaped text: its name in italics, then what it gives in brackets. Nil when it says nothing."
  [{:keys [name grants choose]}]
  (let [nm (one-line name)
        gives (concat (keep grant-text (maps-of grants))
                      (keep #(some->> (get subchoice-labels (:from %) (label (:from %)))
                                      (str "plus "))
                            (maps-of choose)))
        gives (esc-join gives)]
    (cond
      (and nm gives) (str "*" (escape-md nm) "* (" gives ")")
      nm (str "*" (escape-md nm) "*")
      :else gives)))

(defn- equipment-selection-lines
  "Bullets for a class's rich equipment choices (:equipment-selections, the builder's groups of
   options, each a bundle of items and further picks), one per group."
  [groups]
  (for [g (maps-of groups)
        :let [opts (keep equipment-option-text (maps-of (:options g)))]
        :when (seq opts)]
    (str "- **" (escape-md (or (one-line (:name g)) "Equipment choice")) ":** choose one of "
         (s/join "; " opts))))

(defn- equipment-lines
  "Bullets for a class's starting weapons, armor and equipment and the choices among them.
   Returns a vector, [] when there are none."
  [item]
  (vec (concat
        (keep (fn [[k head]] (kv head (count-list (get item k))))
              [[:weapons "Weapons"] [:armor "Armor"] [:equipment "Equipment"]])
        (choice-lines (:weapon-choices item) "Weapon choice")
        (choice-lines (:armor-choices item) "Armor choice")
        (choice-lines (:equipment-choices item) "Equipment choice")
        (equipment-selection-lines (:equipment-selections item)))))

(defn- raw-selection-lines
  "Bullets for an item's raw template choices (:selections, written by hand into a source), each
   \"- **Name:** choose N from A, B\"; a choice with no name is skipped."
  [selections]
  (for [sel (maps-of selections)
        :let [nm (one-line (:name sel))
              n (or (whole (:num sel)) (whole (:min sel)) 1)
              opts (esc-join (keep #(one-line (:name %)) (maps-of (:options sel))))]
        :when nm]
    (str "- **" (escape-md nm) ":** choose " n (when opts (str " from " opts)))))

;; ---------------------------------------------------------------------------------------------
;; Ability-score and saving-throw grants (spreads, as for races, feats and subclasses)

(defn- pool-text
  "An ability pool as text: an ability key (\"Strength\"), :any, :martial or :mental, or a list of
   ability keys (\"Strength or Dexterity\"). Nil when unreadable."
  [pool]
  (cond
    (= :any pool) "any ability"
    (= :martial pool) "Strength, Dexterity or Constitution"
    (= :mental pool) "Intelligence, Wisdom or Charisma"
    (keyword? pool) (ability-label pool)
    (string? pool) (ability-label (keyword pool))
    (or (sequential? pool) (set? pool))
    (let [names (keep #(ability-label (if (string? %) (keyword %) %)) (sort-by str pool))]
      (when (seq names)
        (if (next names)
          (str (s/join ", " (butlast names)) " or " (last names))
          (first names))))
    :else nil))

(defn- one-of?
  "Whether `pool` offers a choice (a group or more than one ability) rather than one ability."
  [pool]
  (or (#{:any :martial :mental} pool)
      (and (coll? pool) (next (seq pool)))))

(defn- increase-text
  "An ability increase as text: a spread entry [amount pool] or [amount pool :save] (\"+2 to
   Strength\", \"+1 to one of ...\", with the saving throw when :save rides along), or the released
   set form #{ability ... :saves?}. Returns a list of strings."
  [ai]
  (cond
    (set? ai)
    (let [saves? (contains? ai :saves?)
          pool (disj ai :saves?)]
      (when-let [p (pool-text pool)]
        [(str "+1 to " (when (one-of? pool) "one of ") p
              (when saves? ", and proficiency in that saving throw"))]))
    (sequential? ai)
    (for [e ai
          :when (vector? e)
          :let [[amount pool rider] e
                n (finite-num amount)
                p (pool-text pool)]
          :when (and n p)]
      (str (when (pos? n) "+") (fmt-num n) " to " (when (one-of? pool) "one of ") p
           (when (= :save rider) ", and proficiency in that saving throw")))
    :else nil))

(defn- save-grant-text
  "Saving-throw grants [[count pool] ...] as text (\"Wisdom\", \"choose 1 of Strength or
   Dexterity\"). Returns a list of strings."
  [sps]
  (for [e (when (sequential? sps) sps)
        :when (vector? e)
        :let [[n pool] e
              n (whole n)
              p (pool-text pool)]
        :when (and n (pos? n) p)]
    (if (one-of? pool) (str "choose " n " of " p) p)))

(defn- grant-lines
  "Bullets for an item's :ability-increases and :save-proficiencies; nils for none."
  [item]
  (let [join #(not-empty (s/join ", " (map escape-inline %)))]
    [(kv "Ability Score Increase" (join (increase-text (:ability-increases item))))
     (kv "Saving Throw Proficiency" (join (save-grant-text (:save-proficiencies item))))]))

(def ^:private caster-types
  "A spellcasting :level-factor -> what it means."
  {1 "Full caster" 2 "Half caster" 3 "Third caster"})

(def ^:private known-modes
  "A spellcasting :known-mode -> how the class gets its spells."
  {:all "Prepares from the whole class spell list"
   :schedule "Knows a fixed number of spells for its level"
   :acquire "Learns spells into a spellbook"})

(defn- by-level-text
  "A map of class level -> number ({3 2 10 3}) as \"2 at 3rd level, 3 at 10th level\"; nil when
   it holds no readable pair."
  [m]
  (not-empty
   (s/join ", " (for [[lvl n] (sort-by (comp #(or % 0) whole key) (when (map? m) m))
                      :let [lvl (level-of lvl) n (whole n)]
                      :when (and lvl n)]
                  (str n " at " (ordinal lvl) " level")))))

(defn- spell-list-lines
  "Bullets for a class's own spell list {spell-level #{spell-key}}, in spell-level order."
  [spell-list]
  (for [[lvl spells] (sort-by (comp #(or % 99) whole key)
                              (filter (comp whole key) (when (map? spell-list) spell-list)))
        :let [names (esc-join (sort (keep label (when (or (set? spells) (sequential? spells))
                                                  spells))))]
        :when (and names (<= 0 (whole lvl) 9))]
    (kv (if (zero? (whole lvl)) "Cantrip List" (str (ordinal (whole lvl)) "-Level Spell List"))
        names)))

(defn- spellcasting-lines
  "Bullets summarising a :spellcasting map: ability, caster type, how spells are known, the
   cantrips and spells known by level, and the spell list. Returns a vector, [] when empty."
  [sc]
  (if (map? sc)
    (vec (remove nil?
                 (concat
                  [(kv "Spellcasting Ability" (some-> (ability-label (:ability sc)) escape-md))
                   (kv "Caster Type"
                       (when-let [f (whole (:level-factor sc))]
                         (get caster-types f (str "Level factor " f))))
                   (kv "Spell List" (some-> (label (:spell-list-kw sc)) escape-md))
                   (kv "Spells" (get known-modes (:known-mode sc)))
                   (kv "Cantrips Known" (by-level-text (:cantrips-known sc)))
                   (kv "Spells Known" (by-level-text (when (map? (:spells-known sc))
                                                       (:spells-known sc))))]
                  (spell-list-lines (:spell-list sc)))))
    []))

(def ^:private expanded-spell-fields
  "A subclass's always-available spell fields -> [heading note]."
  [[:cleric-spells "Domain Spells" "always prepared"]
   [:paladin-spells "Oath Spells" "always prepared"]
   [:warlock-spells "Expanded Spell List" "added to the warlock spell list"]])

(defn- expanded-spell-lines
  "Bullets for an item's cleric, paladin and warlock spell tables {spell-level {index spell}},
   one per spell level in order. Returns a lazy sequence."
  [item]
  (for [[k head note] expanded-spell-fields
        [lvl spells] (sort-by (comp #(or % 99) whole key)
                              (filter (comp whole key) (when (map? (get item k)) (get item k))))
        :let [names (esc-join (keep label (cond
                                            (map? spells) (map val (sorted-entries spells))
                                            (sequential? spells) spells)))]
        :when (and names (<= 0 (whole lvl) 9))]
    (kv (str head ", " (if (zero? (whole lvl)) "cantrips" (str (ordinal (whole lvl)) " level"))
             " (" note ")")
        names)))

;; ---------------------------------------------------------------------------------------------
;; Classes and subclasses

(defn- paragraphs
  "`x` (an author's text) as escaped paragraphs, each line of it its own paragraph; [] when blank."
  [x]
  (if-let [t (text x)]
    [(s/join "\n\n" (map escape-md (remove s/blank? (map s/trim (s/split t #"\n")))))]
    []))

(defn- bullets
  "`lines` (nils dropped) as one list block, or [] when there are none."
  [lines]
  (if-let [ls (seq (remove nil? lines))]
    [(s/join "\n" ls)]
    []))

(defn- section
  "A `title` heading at `depth` followed by the list block of `lines`; [] when no line."
  [depth title lines]
  (if-let [b (seq (bullets lines))]
    (into [(heading depth title)] b)
    []))

(defn- item-title
  "The display name of `item`: its :name, else its key `k`, else `fallback`."
  [item k fallback]
  (or (one-line (:name item)) (label k) (label (:key item)) fallback))

(declare subclass-blocks)

(defn- class-blocks
  "The Markdown blocks of one class `item` (stored under key `k`), headings at `depth`;
   `ctx` carries the source's selections and classes. Returns a vector of strings."
  [item k ctx depth]
  (let [title (item-title item k "Unnamed class")
        sub-level (level-of (:subclass-level item))
        sub-title (one-line (:subclass-title item))
        hit-die (let [d (:hit-die item)] (when (and (finite-num d) (pos? d)) (fmt-num d)))
        ability-levels (keep level-of (when (sequential? (:ability-increase-levels item))
                                        (:ability-increase-levels item)))
        extra (concat
               (for [l (distinct ability-levels)]
                 {:level l :cell "Ability Score Improvement"})
               (when sub-level
                 [{:level sub-level :cell (escape-cell (or sub-title "Subclass feature"))}]))
        entries (level-entries item ctx)
        subclasses (sort-by (juxt #(s/lower-case (or (one-line (:name %)) "")) #(str (:key %)))
                            (maps-of (:subclasses item)))]
    (vec
     (concat
      [(heading depth (escape-md title)) "*Class*"]
      (paragraphs (:help item))
      (bullets (concat
                [(kv "Hit Die" (some->> hit-die (str "d")))]
                (profs-lines (:profs item))
                [(kv "Subclass" (when sub-level
                                  (str "chosen at " (ordinal sub-level) " level"
                                       (when sub-title (str ": " (escape-md sub-title))))))]))
      (paragraphs (:subclass-help item))
      (section (inc depth) "Equipment" (equipment-lines item))
      (section (inc depth) "Other Choices" (raw-selection-lines (:selections item)))
      (section (inc depth) "Spellcasting" (spellcasting-lines (:spellcasting item)))
      [(heading (inc depth) "Level Progression")
       (table-block (progression-rows (concat entries extra) (range 1 21)))]
      (features-blocks entries (inc depth))
      (mapcat (fn [sc]
                (let [nm (item-title sc (:key sc) "Unnamed subclass")]
                  (subclass-blocks sc (:key sc) ctx (inc depth)
                                   (str "Subclass: " (escape-md nm)) title)))
              subclasses)))))

(defn- subclass-blocks
  "The Markdown blocks of one subclass `item` (stored under key `k`), headings at `depth`.
   `heading-text` replaces the title when given (a nested subclass's \"Subclass: Name\");
   `class-name` names its class when the source does not hold it. Returns a vector of strings."
  [item k ctx depth heading-text class-name]
  (let [title (item-title item k "Unnamed subclass")
        parent (or (one-line (get-in ctx [:classes (:class item) :name]))
                   class-name
                   (label (:class item)))
        entries (level-entries item ctx)
        levels (sort (distinct (keep :level entries)))]
    (vec
     (concat
      [(heading depth (or heading-text (escape-md title)))
       (str "*" (some-> parent escape-md (str " ")) "subclass*")]
      (bullets (concat (profs-lines (:profs item))
                       (grant-lines item)
                       (spellcasting-lines (:spellcasting item))
                       (expanded-spell-lines item)))
      (section (inc depth) "Other Choices" (raw-selection-lines (:selections item)))
      (when (seq levels)
        [(heading (inc depth) "Level Progression")
         (table-block (progression-rows entries levels))])
      (features-blocks entries (inc depth))))))

;; ---------------------------------------------------------------------------------------------
;; Tidy-up and the public functions

(defn- tidy
  "`blocks` joined by blank lines into one string: \"\\n\" endings, no trailing spaces, one
   final newline."
  [blocks]
  (str (->> (s/join "\n\n" (remove s/blank? blocks))
            s/split-lines
            (map s/trimr)
            (s/join "\n"))
       "\n"))

(defn class->markdown
  "One stored class as Markdown (`ctx` optional: {:selections {key item} :classes {key item}} from
   the source, so level choices list their options). Returns a string ending in a newline."
  ([item] (class->markdown item nil))
  ([item ctx]
   (let [item (if (map? item) item {})]
     (tidy (class-blocks item (:key item) ctx 1)))))

(defn subclass->markdown
  "One stored subclass as Markdown; `ctx` as for `class->markdown`. Returns a string ending in a
   newline."
  ([item] (subclass->markdown item nil))
  ([item ctx]
   (let [item (if (map? item) item {})]
     (tidy (subclass-blocks item (:key item) ctx 1 nil nil)))))

(def formatters
  "The content types this export writes, in the order they are written, each with its formatter
   (item key ctx depth) -> blocks. The simple types are added here."
  [[:orcpub.dnd.e5/classes
    (fn [item k ctx depth] (class-blocks item k ctx depth))]
   [:orcpub.dnd.e5/subclasses
    (fn [item k ctx depth] (subclass-blocks item k ctx depth nil nil))]])

(defn- sorted-items
  "The [key item] pairs of `items` whose item is a map, ordered by name then key; [] otherwise."
  [items]
  (if (map? items)
    (sort-by (fn [[k v]] [(s/lower-case (or (one-line (:name v)) "")) (str k)])
             (filter (comp map? val) items))
    []))

(defn- source-title
  "The name of `source`: the first (in text order) :option-pack its items carry, else \"Homebrew\"."
  [source]
  (or (->> (vals source)
           (mapcat #(when (map? %) (vals %)))
           (keep #(when (map? %) (one-line (:option-pack %))))
           sort
           first)
      "Homebrew"))

(defn- library-selections
  "Every selection (key -> item) in `library` ({source-name source}), for level choices that name a
   selection stored in another source. Sources are merged in name order; {} when none."
  [library]
  (reduce (fn [acc [_ src]]
            (let [sels (when (map? src) (:orcpub.dnd.e5/selections src))]
              (if (map? sels) (merge acc sels) acc)))
          {}
          (sort-by (comp str key) (when (map? library) library))))

(defn- used-selection-keys
  "The selection keys the classes and subclasses of `source` choose from (their :level-selections)."
  [source]
  (set (for [[ct _] formatters
             [_ item] (sorted-items (get source ct))
             sel (maps-of (:level-selections item))
             :when (some? (:type sel))]
         (:type sel))))

(defn left-out
  "What `source` holds that this export does not write, as [[type-name count] ...] in name order
   (\"Feats\" 2). Selections a class or subclass chooses from count as written."
  [source]
  (let [source (if (map? source) source {})
        covered (set (map first formatters))
        used (used-selection-keys source)]
    (vec (sort (for [[ct items] source
                     :when (not (covered ct))
                     :let [ks (map key (sorted-items items))
                           ks (if (= :orcpub.dnd.e5/selections ct) (remove used ks) ks)
                           n (count ks)
                           nm (when (keyword? ct) (label (keyword (name ct))))]
                     :when (and nm (pos? n))]
                 [nm n])))))

(defn- left-out-text
  "`left-out` pairs as \"Feats (2), Spells (1)\", or nil when there are none."
  [pairs]
  (not-empty (s/join ", " (for [[nm n] pairs] (str nm " (" n ")")))))

(defn source->markdown
  "One stored source {content-type {item-key item}} as Markdown: a title, a line naming what the
   file leaves out (see `left-out`), then each content type with a formatter (see `formatters`) in
   that order, items ordered by name. `title` names the source (default: the :option-pack its
   items carry); `library` ({source-name source}) lends selections the source does not hold.
   Returns a string ending in a newline."
  ([source] (source->markdown source nil nil))
  ([source title] (source->markdown source title nil))
  ([source title library]
   (let [source (if (map? source) source {})
         own (when (map? (:orcpub.dnd.e5/selections source)) (:orcpub.dnd.e5/selections source))
         ctx {:selections (merge (library-selections library) own)
              :classes (when (map? (:orcpub.dnd.e5/classes source))
                         (:orcpub.dnd.e5/classes source))}
         omitted (left-out-text (left-out source))
         blocks (for [[ct f] formatters
                      [k item] (sorted-items (get source ct))]
                  (f item k ctx 2))]
     (tidy (concat [(heading 1 (escape-md (or (one-line title) (source-title source))))]
                   (when omitted
                     [(str "*This file covers classes and subclasses only. Not included: "
                           (escape-inline omitted) ".*")])
                   (if (seq blocks)
                     (apply concat blocks)
                     ["*Nothing to export yet.*"]))))))

(def ^:private covered-types-notice
  "Said when a source holds nothing the writer can format yet."
  "Nothing to export as Markdown yet: the first version covers classes and subclasses only.")

(defn export-file
  "The download for exporting `source` ({content-type {item-key item}}) as Markdown, titled
   `title` (default: its :option-pack); `library` ({source-name source}, optional) lends
   selections. Returns {:filename \"<title>.md\", :content-type, :text (see `source->markdown`),
   :notice}: :notice says only classes and subclasses are covered when the source holds none of
   them or holds other content (naming it), else nil."
  ([source title] (export-file source title nil))
  ([source title library]
   (let [source (if (map? source) source {})
         title (or (one-line title) (source-title source))
         omitted (left-out-text (left-out source))]
     {:filename (str title ".md")
      :content-type "text/markdown;charset=utf-8"
      :text (source->markdown source title library)
      :notice (cond
                (not (some (fn [[ct _]] (seq (sorted-items (get source ct)))) formatters))
                covered-types-notice
                omitted
                (str "The Markdown file covers classes and subclasses only. Not included: "
                     omitted "."))})))
