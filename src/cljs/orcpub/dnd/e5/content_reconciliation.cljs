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
   :background :backgrounds
   :feat :feats})

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

   traverse-nested produces indexed paths like [:class 0], [:race 0 :subrace 0].
   We strip numeric indices to match against content-type-paths which uses
   keyword-only paths like [:class], [:race :subrace].

   Detection by path shape (after stripping indices):
     [:class]                            → class
     [:class :martial-archetype]         → subclass (archetype in subclass-path-patterns)
     [:race]                             → race
     [:race :subrace]                    → subrace
     [:background]                       → background
     [:class :levels :asi-or-feat :feats] → feat (deep path, detected by :feats keyword)

   Other paths (level-up choices, fighting styles, skills, HP method,
   etc.) are left as :unknown and filtered out downstream."
  [{:keys [path] :as entry}]
  ;; Strip vector indices from path for matching: [:class 0] → [:class]
  (let [kw-path (vec (remove integer? path))
        direct-match (get content-type-paths (vec (take 2 kw-path)))
        ;; Subclass: exactly 2 keywords deep under :class, second is an archetype key
        is-subclass? (and (= :class (first kw-path))
                          (= 2 (count kw-path))
                          (contains? subclass-path-patterns (second kw-path)))
        ;; Feat: :feats appears as the immediate parent in the kw-path.
        ;; Feats are nested deep (e.g. [:class :levels :asi-or-feat :feats])
        ;; so we match by the parent keyword rather than fixed depth.
        is-feat? (= :feats (last (butlast kw-path)))
        content-type (cond
                       is-subclass? {:type :subclass :label "Subclass"}
                       is-feat? {:type :feat :label "Feat"}
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
        ;; Built-in (SRD) content keys — hardcoded in the app, not from plugins.
        ;; available-content uses plugin-* subs which don't include these,
        ;; so we must exclude them here to avoid false positives.
        ;; NOTE: Only SRD content belongs here. Non-SRD PHB content comes
        ;; from plugins and SHOULD be flagged when plugins are removed.
        builtin-classes #{:barbarian :bard :cleric :druid :fighter :monk
                          :paladin :ranger :rogue :sorcerer :warlock :wizard}
        builtin-races #{:dwarf :elf :halfling :human :dragonborn :gnome
                        :half-elf :half-orc :tiefling :hill-dwarf :mountain-dwarf
                        :high-elf :wood-elf :drow :lightfoot :stout :forest-gnome
                        :rock-gnome}
        ;; Only Acolyte is hardcoded (spell_subs.cljs:538). All other
        ;; backgrounds come from plugins.
        builtin-backgrounds #{:acolyte}
        ;; SRD subclasses — one per class, hardcoded in classes.cljc.
        ;; Non-SRD subclasses (Battle Master, Totem Warrior, etc.) are
        ;; reader-discarded (#_) and come from plugins.
        builtin-subclasses #{:champion      ;; Fighter
                             :berserker     ;; Barbarian
                             :lore          ;; Bard
                             :life          ;; Cleric
                             :land          ;; Druid
                             :open-hand     ;; Monk
                             :devotion      ;; Paladin
                             :hunter        ;; Ranger
                             :thief         ;; Rogue
                             :draconic      ;; Sorcerer
                             :fiend         ;; Warlock
                             :evocation}    ;; Wizard
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
