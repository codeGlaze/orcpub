(ns orcpub.dnd.e5.import-validation
  "Comprehensive validation for orcbrew file import/export.

  Provides detailed error messages and progressive validation to help users
  identify and fix issues with their orcbrew files."
  (:require [cljs.spec.alpha :as spec]
            [cljs.reader :as reader]
            [clojure.string :as str]
            [orcpub.dnd.e5 :as e5]
            [orcpub.common :as common]))

;; ============================================================================
;; Error Message Formatting
;; ============================================================================

(defn format-spec-problem
  "Converts a spec problem into a human-readable error message."
  [{:keys [path pred val via in]}]
  (let [location (if (seq in)
                   (str "at " (str/join " > " (map name in)))
                   "at root")]
    (str "  • " location ": "
         (cond
           (and (seq? pred) (= 'clojure.core/fn (first pred)))
           "Invalid value format"

           (and (seq? pred) (= 'clojure.spec.alpha/keys (first pred)))
           (str "Missing required field: " (second pred))

           :else
           (str "Failed validation: " pred))
         (when val
           (str "\n    Got: " (pr-str (if (> (count (pr-str val)) 50)
                                        (str (subs (pr-str val) 0 47) "...")
                                        val)))))))

(defn format-validation-errors
  "Formats spec validation errors into user-friendly messages."
  [explain-data]
  (when explain-data
    (let [problems (:cljs.spec.alpha/problems explain-data)]
      (str "Validation errors found:\n"
           (str/join "\n" (map format-spec-problem problems))))))

;; ============================================================================
;; Parse Error Detection
;; ============================================================================

(defn parse-edn
  "Attempts to parse EDN text with detailed error reporting.

  Returns:
    {:success true :data <parsed-data>} on success
    {:success false :error <error-msg> :line <line-number>} on failure"
  [edn-text]
  (try
    (let [result (reader/read-string edn-text)]
      {:success true :data result})
    (catch js/Error e
      (let [msg (.-message e)
            line-match (re-find #"line (\d+)" msg)
            line-num (when line-match (js/parseInt (second line-match)))]
        {:success false
         :error msg
         :line line-num
         :hint (cond
                 (str/includes? msg "Unmatched delimiter")
                 "Check for missing or extra brackets/braces/parentheses"

                 (str/includes? msg "EOF")
                 "File appears to be incomplete or corrupted"

                 (str/includes? msg "Invalid token")
                 "File contains invalid characters or syntax"

                 :else
                 "Check the file syntax and ensure it's valid EDN format")}))))

;; ============================================================================
;; Progressive Validation
;; ============================================================================

(defn validate-item
  "Validates a single homebrew item.

  Returns:
    {:valid true} if valid
    {:valid false :errors [...]} if invalid"
  [item-key item]
  (if (spec/valid? ::e5/homebrew-item item)
    {:valid true}
    {:valid false
     :item-key item-key
     :errors (format-validation-errors (spec/explain-data ::e5/homebrew-item item))}))

(defn validate-content-group
  "Validates a group of homebrew content (e.g., all spells, all races).

  Returns map of:
    :valid-count - number of valid items
    :invalid-count - number of invalid items
    :invalid-items - vector of {:key <key> :errors <errors>} for invalid items"
  [content-key items]
  (let [results (map (fn [[k v]] (assoc (validate-item k v) :key k)) items)
        valid (filter :valid results)
        invalid (remove :valid results)]
    {:content-type content-key
     :valid-count (count valid)
     :invalid-count (count invalid)
     :invalid-items (mapv #(select-keys % [:key :errors]) invalid)}))

(defn validate-plugin-progressive
  "Validates a plugin progressively, identifying which specific items are invalid.

  Returns map with:
    :valid - true if entire plugin is valid
    :content-groups - validation results for each content type
    :valid-items-count - total valid items
    :invalid-items-count - total invalid items"
  [plugin]
  (let [content-groups (filter
                        (fn [[k _]] (and (qualified-keyword? k)
                                        (= (namespace k) "orcpub.dnd.e5")))
                        plugin)
        validations (mapv
                     (fn [[k v]]
                       (if (map? v)
                         (validate-content-group k v)
                         {:content-type k :valid-count 1 :invalid-count 0 :invalid-items []}))
                     content-groups)
        total-valid (reduce + 0 (map :valid-count validations))
        total-invalid (reduce + 0 (map :invalid-count validations))]
    {:valid (zero? total-invalid)
     :content-groups validations
     :valid-items-count total-valid
     :invalid-items-count total-invalid}))

;; ============================================================================
;; Pre-Export Validation
;; ============================================================================

(defn validate-before-export
  "Validates plugin data before export to catch bugs early.

  Returns:
    {:valid true :warnings [...]} if exportable
    {:valid false :errors [...]} if not exportable"
  [plugin-data]
  (let [warnings (atom [])]

    ;; Check for nil values
    (doseq [[k v] plugin-data]
      (when (nil? v)
        (swap! warnings conj (str "Found nil value for key: " k))))

    ;; Check for empty option-pack strings
    (doseq [[content-key items] plugin-data]
      (when (and (qualified-keyword? content-key) (map? items))
        (doseq [[item-key item] items]
          (when (and (map? item)
                    (or (nil? (:option-pack item))
                        (= "" (:option-pack item))))
            (swap! warnings conj
                   (str "Item " (name content-key) "/" (name item-key)
                        " has missing option-pack"))))))

    ;; Run full spec validation
    (if (spec/valid? ::e5/plugin plugin-data)
      {:valid true
       :warnings @warnings}
      {:valid false
       :errors (format-validation-errors (spec/explain-data ::e5/plugin plugin-data))
       :warnings @warnings})))

;; ============================================================================
;; Import Strategies
;; ============================================================================

(defn import-all-or-nothing
  "Traditional import: all content must be valid or none is imported."
  [plugin]
  (cond
    (spec/valid? ::e5/plugin plugin)
    {:success true
     :strategy :single-plugin
     :data plugin}

    (spec/valid? ::e5/plugins plugin)
    {:success true
     :strategy :multi-plugin
     :data plugin}

    :else
    {:success false
     :errors [(str "Invalid plugin structure\n\n"
                   (format-validation-errors (spec/explain-data ::e5/plugin plugin))
                   "\n\nIf this is a multi-plugin file:\n"
                   (format-validation-errors (spec/explain-data ::e5/plugins plugin)))]}))

(defn remove-invalid-items
  "Removes invalid items from a content group, keeping only valid ones."
  [content-key items]
  (into {}
        (filter (fn [[k v]]
                  (:valid (validate-item k v)))
                items)))

(defn import-progressive
  "Progressive import: imports valid items and reports invalid ones.

  Returns:
    {:success true
     :data <cleaned-plugin>
     :imported-count <number>
     :skipped-count <number>
     :skipped-items [...]}

  This allows users to recover as much data as possible from corrupted files."
  [plugin]
  (if (map? plugin)
    (let [validation (validate-plugin-progressive plugin)
          cleaned-plugin (into {}
                               (map (fn [[k v]]
                                      (if (and (qualified-keyword? k) (map? v))
                                        [k (remove-invalid-items k v)]
                                        [k v]))
                                    plugin))
          invalid-items (mapcat :invalid-items (:content-groups validation))]
      {:success true
       :data cleaned-plugin
       :imported-count (:valid-items-count validation)
       :skipped-count (:invalid-items-count validation)
       :skipped-items invalid-items
       :had-errors (pos? (:invalid-items-count validation))})
    {:success false
     :errors ["Plugin is not a valid map structure"]}))

;; ============================================================================
;; Data-Level Cleaning (after parse)
;; ============================================================================

;; Fields where nil should be replaced with a default value
(def nil-replace-defaults
  {:disabled? false
   :option-pack "Unnamed Content"})

;; Fields where nil is semantically meaningful and should be preserved
(def nil-preserve-fields
  #{:spell-list-kw :spellcasting :ability :class-key})

;; Fields where nil should be removed entirely (inside nested maps)
;; These are typically numeric fields where nil is accidental
(def nil-remove-in-maps
  #{:str :dex :con :int :wis :cha  ; ability scores
    :ac :hp :speed                  ; stats
    :level :modifier :die :die-count}) ; numeric fields

(defn clean-nil-in-map
  "Removes nil values for specific keys in a map."
  [m]
  (if (map? m)
    (into {}
          (keep (fn [[k v]]
                  (cond
                    ;; Replace with default if in replace list
                    (and (nil? v) (contains? nil-replace-defaults k))
                    [k (get nil-replace-defaults k)]

                    ;; Preserve nil if in preserve list
                    (and (nil? v) (contains? nil-preserve-fields k))
                    [k v]

                    ;; Remove nil if in remove list
                    (and (nil? v) (contains? nil-remove-in-maps k))
                    nil

                    ;; Recurse into nested maps/vectors
                    (map? v)
                    [k (clean-nil-in-map v)]

                    (vector? v)
                    [k (mapv #(if (map? %) (clean-nil-in-map %) %) v)]

                    ;; Keep everything else as-is
                    :else
                    [k v]))
                m))
    m))

(defn fix-empty-option-pack
  "Fixes empty string option-pack values in items."
  [data]
  (if (map? data)
    (into {}
          (map (fn [[k v]]
                 (cond
                   ;; Fix empty option-pack in homebrew items
                   (and (= k :option-pack) (= v ""))
                   [k "Unnamed Content"]

                   ;; Recurse into nested structures
                   (map? v)
                   [k (fix-empty-option-pack v)]

                   (vector? v)
                   [k (mapv #(if (map? %) (fix-empty-option-pack %) %) v)]

                   :else
                   [k v]))
               data))
    data))

(defn rename-empty-plugin-key
  "Renames empty string plugin key to a unique name."
  [data]
  (if (and (map? data) (contains? data ""))
    (let [base-name "Unnamed Content"
          ;; Find a unique name if base-name already exists
          unique-name (if (contains? data base-name)
                        (loop [n 2]
                          (let [candidate (str base-name " " n)]
                            (if (contains? data candidate)
                              (recur (inc n))
                              candidate)))
                        base-name)]
      (-> data
          (assoc unique-name (get data ""))
          (dissoc "")))
    data))

(defn clean-data
  "Applies all data-level cleaning transformations."
  [data]
  (-> data
      rename-empty-plugin-key
      fix-empty-option-pack
      clean-nil-in-map))

;; ============================================================================
;; Main Validation Entry Point
;; ============================================================================

(defn validate-import
  "Main validation function for orcbrew file imports.

  Options:
    :strategy - :strict (all-or-nothing) or :progressive (import valid items)
    :auto-clean - whether to apply automatic cleaning fixes

  Returns detailed validation results with user-friendly error messages."
  [edn-text {:keys [strategy auto-clean] :or {strategy :progressive auto-clean true}}]

  ;; Step 1: String-level cleaning (syntax fixes only)
  (let [cleaned-text (if auto-clean
                       (-> edn-text
                           ;; Fix disabled? nil -> disabled? false (common toggle artifact)
                           (str/replace #"disabled\?\s+nil" "disabled? false")
                           ;; Clean up trailing commas before closing braces/brackets
                           (str/replace #",\s*\}" "}")
                           (str/replace #",\s*\]" "]"))
                       edn-text)]

    ;; Step 2: Parse EDN
    (let [parse-result (parse-edn cleaned-text)]
      (if (:success parse-result)

        ;; Step 3: Data-level cleaning (semantic fixes)
        (let [cleaned-data (if auto-clean
                            (clean-data (:data parse-result))
                            (:data parse-result))]

          ;; Step 4: Validate structure based on strategy
          (if (= strategy :strict)
            (import-all-or-nothing cleaned-data)
            (import-progressive cleaned-data)))

        ;; Parse failed - return detailed error
        {:success false
         :parse-error true
         :error (:error parse-result)
         :line (:line parse-result)
         :hint (:hint parse-result)}))))

;; ============================================================================
;; User-Friendly Error Messages
;; ============================================================================

(defn format-import-result
  "Formats validation result into a user-friendly message."
  [result]
  (cond
    ;; Parse error
    (:parse-error result)
    (str "⚠️ Could not read file\n\n"
         "Error: " (:error result) "\n"
         (when (:line result)
           (str "Line: " (:line result) "\n"))
         "\n" (:hint result)
         "\n\nThe file may be corrupted or incomplete. "
         "Try exporting a fresh copy if you have the original source.")

    ;; Validation error (strict mode)
    (and (not (:success result)) (:errors result))
    (str "⚠️ Invalid orcbrew file\n\n"
         (str/join "\n\n" (:errors result))
         "\n\nTo recover data from this file, you can:"
         "\n1. Try progressive import (imports valid items, skips invalid ones)"
         "\n2. Check the browser console for detailed validation errors"
         "\n3. Export a fresh copy if you have the original source")

    ;; Progressive import with some items skipped
    (:had-errors result)
    (str "⚠️ Import completed with warnings\n\n"
         "Imported: " (:imported-count result) " valid items\n"
         "Skipped: " (:skipped-count result) " invalid items\n\n"
         "Invalid items were skipped. Check the browser console for details.\n\n"
         "To be safe, export all content now to create a clean backup.")

    ;; Successful import
    (:success result)
    (str "✅ Import successful\n\n"
         (when (:imported-count result)
           (str "Imported " (:imported-count result) " items\n\n"))
         "To be safe, export all content now to create a clean backup.")

    ;; Unknown result
    :else
    "❌ Unknown import result"))
