(ns orcpub.dnd.e5.content-reconciliation
  "Detects missing content references in characters and suggests fixes.

   When a character references homebrew content (classes, races, etc.) that
   isn't currently loaded, this module helps identify what's missing and
   suggests similar content that might be a match."
  (:require [clojure.string :as str]
            [orcpub.entity :as entity]
            [orcpub.common :as common]))

;; =============================================================================
;; Version: 0.05 - Add built-in content exclusions, fix subclass patterns
;; =============================================================================

;; ============================================================================
;; Content Type Definitions
;; ============================================================================

(def content-type-paths
  "Maps option paths to their content types for lookup.
   The path indicates where in the character options the key is stored."
  {[:race]                    {:type :race      :label "Race"}
   [:race :subrace]           {:type :subrace   :label "Subrace"}
   [:class]                   {:type :class     :label "Class"}
   [:background]              {:type :background :label "Background"}})

(def subclass-path-patterns
  "Subclass paths vary by class, so we detect them by pattern.
   Path like [:class 0 :martial-archetype] indicates a subclass."
  #{:martial-archetype :roguish-archetype :sorcerous-origin
    :otherworldly-patron :arcane-tradition :bardic-college
    :divine-domain :druid-circle :monastic-tradition
    :sacred-oath :ranger-archetype :primal-path
    :artificer-specialist :artificer-specialization  ;; Both variants used
    :blood-hunter-order})

(def content-type->field
  "Maps content type keywords to their field names in available-content."
  {:class :classes
   :subclass :subclasses
   :race :races
   :subrace :subraces
   :background :backgrounds})

;; ============================================================================
;; Key Extraction from Character
;; ============================================================================

(defn- extract-keys-from-option
  "Recursively extract all ::entity/key values from an option tree.
   Returns a seq of {:path [...] :key :keyword}."
  [option path]
  (when (map? option)
    (let [current-key (::entity/key option)
          nested-options (::entity/options option)
          current (when current-key
                    [{:path path :key current-key}])]
      (concat
       current
       (when (map? nested-options)
         (common/traverse-nested extract-keys-from-option nested-options path))))))

(defn- annotate-content-type
  "Add content type info to a key entry based on its path.

   Only flags keys at content-relevant path depths:
     [:class]              → class
     [:class :archetype]   → subclass (where archetype in subclass-path-patterns)
     [:race]               → race
     [:race :subrace]      → subrace
     [:background]         → background

   Deeper paths (level-up choices, fighting styles, skills, HP method,
   feats, etc.) are left as :unknown and filtered out downstream."
  [{:keys [path] :as entry}]
  (let [direct-match (get content-type-paths (vec (take 2 path)))
        ;; Subclass: exactly 2-deep under :class, last element is an archetype key
        is-subclass? (and (= :class (first path))
                          (= 2 (count path))
                          (contains? subclass-path-patterns (second path)))
        content-type (cond
                       is-subclass? {:type :subclass :label "Subclass"}
                       direct-match direct-match
                       :else {:type :unknown :label "Content"})]
    (assoc entry
           :content-type (:type content-type)
           :content-label (:label content-type))))

(defn extract-content-keys
  "Extract all content keys from a character's options.
   Returns a seq of {:path [...] :key :keyword :content-type :type}."
  [character]
  (let [options (::entity/options character)
        raw-keys (common/traverse-nested extract-keys-from-option options [])]
    (map annotate-content-type raw-keys)))

;; ============================================================================
;; Content Availability Checking
;; ============================================================================

(defn- key-similarity
  "Calculate similarity between two keywords (0-1 scale).
   Uses simple prefix/suffix matching and common substring detection."
  [k1 k2]
  (let [s1 (name k1)
        s2 (name k2)
        ;; Exact match
        exact (if (= s1 s2) 1.0 0.0)
        ;; One is prefix of other
        prefix (if (or (str/starts-with? s1 s2)
                       (str/starts-with? s2 s1))
                 0.7 0.0)
        ;; Share common base (before any dash suffix)
        base-match (if (= (common/kw-base k1) (common/kw-base k2)) 0.8 0.0)]
    (max exact prefix base-match)))

(defn- infer-source-from-key
  "Try to infer the source name from a renamed key.
   E.g., :artificer-kibbles-tasty -> 'kibbles tasty' or 'Kibbles' Tasty'"
  [key]
  (let [key-str (name key)
        ;; Look for pattern: base-source-name
        parts (str/split key-str #"-")
        ;; If more than one part, the suffix might be the source
        source-parts (when (> (count parts) 1)
                       (rest parts))]
    (when (seq source-parts)
      (str/join " " (map str/capitalize source-parts)))))

(defn find-similar-content
  "Find content similar to a missing key.
   Returns seq of {:key :name :source :similarity} sorted by similarity."
  [missing-key content-type available-content]
  (let [inferred-source (infer-source-from-key missing-key)
        missing-base (common/kw-base missing-key)]
    (->> available-content
         ;; Filter to only valid content with keys
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
;; Missing Content Detection
;; ============================================================================

(defn- get-content-list
  "Get the content list for a given content type from available-content."
  [available-content content-type]
  (get available-content (get content-type->field content-type) []))

(defn check-content-availability
  "Check which content keys from a character are missing.

   Parameters:
   - character-keys: seq from extract-content-keys
   - available-content: map of {:classes [...] :races [...] :subclasses [...] ...}

   Returns seq of missing content with suggestions:
   {:path [...] :key :foo :content-type :class :suggestions [...]}"
  [character-keys available-content]
  (let [;; Build lookup sets for each content type using content-type->field mapping
        available-keys (into {}
                             (map (fn [[ct field]]
                                    [ct (set (map :key (get available-content field)))]))
                             content-type->field)
        ;; Internal keys to skip - these are built-in options, not homebrew
        internal-key-patterns #{"level-" "hit-points-" "ability-scores"}
        internal-keys #{:standard-scores :point-buy :average :manual-entry
                        :hit-points :starting-equipment :equipment-pack}
        ;; Built-in content keys - these are not homebrew, don't flag as missing
        builtin-classes #{:barbarian :bard :cleric :druid :fighter :monk
                          :paladin :ranger :rogue :sorcerer :warlock :wizard}
        builtin-races #{:dwarf :elf :halfling :human :dragonborn :gnome
                        :half-elf :half-orc :tiefling :hill-dwarf :mountain-dwarf
                        :high-elf :wood-elf :drow :lightfoot :stout :forest-gnome
                        :rock-gnome}
        builtin-backgrounds #{:acolyte :charlatan :criminal :entertainer
                              :folk-hero :guild-artisan :hermit :noble :outlander
                              :sage :sailor :soldier :urchin}
        ;; Built-in subclasses - all PHB subclasses
        builtin-subclasses #{:champion :battle-master :eldritch-knight  ;; Fighter
                             :berserker :totem-warrior                   ;; Barbarian
                             :lore :valor                                ;; Bard
                             :knowledge :life :light :nature :tempest :trickery :war  ;; Cleric
                             :land :moon                                 ;; Druid
                             :open-hand :shadow :four-elements           ;; Monk
                             :devotion :ancients :vengeance              ;; Paladin
                             :hunter :beast-master                       ;; Ranger
                             :thief :assassin :arcane-trickster          ;; Rogue
                             :draconic :wild-magic                       ;; Sorcerer
                             :archfey :fiend :great-old-one              ;; Warlock
                             :abjuration :conjuration :divination :enchantment
                             :evocation :illusion :necromancy :transmutation}  ;; Wizard
        is-builtin? (fn [k ct]
                      (case ct
                        :class (contains? builtin-classes k)
                        :subclass (contains? builtin-subclasses k)
                        :race (contains? builtin-races k)
                        :subrace (contains? builtin-races k)
                        :background (contains? builtin-backgrounds k)
                        false))]
    (keep
     (fn [{:keys [key content-type] :as entry}]
       (when (and (keyword? key)
                  ;; Only check known content types (skip :unknown)
                  (contains? content-type->field content-type))
         (let [key-name (name key)
               type-keys (get available-keys content-type #{})
               is-internal? (or (contains? internal-keys key)
                                (some #(str/starts-with? key-name %) internal-key-patterns))
               is-missing? (and (not (contains? type-keys key))
                                (not is-internal?)
                                (not (is-builtin? key content-type)))]
           (when is-missing?
             (let [suggestions (find-similar-content
                                key
                                content-type
                                (get-content-list available-content content-type))]
               (assoc entry
                      :missing? true
                      :suggestions suggestions
                      :inferred-source (infer-source-from-key key)))))))
     character-keys)))

(defn generate-missing-content-report
  "Generate a user-friendly report of missing content.

   Returns:
   {:has-missing? bool
    :missing-count int
    :items [{:key :foo
             :label \"Class\"
             :inferred-source \"Kibbles' Tasty\"
             :suggestions [{:key :bar :name \"Similar\" :similarity 0.8}]}]}"
  [character available-content]
  (let [char-keys (extract-content-keys character)
        missing (check-content-availability char-keys available-content)]
    {:has-missing? (boolean (seq missing))
     :missing-count (count missing)
     :items (vec missing)}))
