(ns orcpub.tools.orcbrew
  "Command-line tools for inspecting and debugging orcbrew files.

   Usage:
     lein prettify-orcbrew <file.orcbrew>           - Pretty-print EDN
     lein prettify-orcbrew <file.orcbrew> --analyze - Show potential issues

   Version: 0.01"
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(def version "0.02")

;;; ============================================================
;;; Analysis functions - detect potential issues WITHOUT fixing them
;;; ============================================================

(defn find-nil-nil-patterns
  "Find {nil nil, ...} patterns in raw string content."
  [content]
  (let [matches (re-seq #"nil\s+nil\s*," content)]
    {:count (count matches)
     :pattern "nil nil,"
     :description "Spurious nil key-value pairs (e.g., {nil nil, :key :foo})"}))

(def problematic-unicode
  "Map of problematic Unicode characters and their descriptions."
  {;; Quotation marks
   \u2018 "left single quote"
   \u2019 "right single quote"
   \u201A "single low-9 quote"
   \u201B "single high-reversed-9 quote"
   \u201C "left double quote"
   \u201D "right double quote"
   \u201E "double low-9 quote"
   \u201F "double high-reversed-9 quote"
   \u2032 "prime (feet)"
   \u2033 "double prime (inches)"
   ;; Dashes
   \u2010 "hyphen"
   \u2011 "non-breaking hyphen"
   \u2012 "figure dash"
   \u2013 "en-dash"
   \u2014 "em-dash"
   \u2015 "horizontal bar"
   ;; Spaces
   \u00A0 "non-breaking space"
   \u2002 "en space"
   \u2003 "em space"
   \u2009 "thin space"
   \u200A "hair space"
   \u200B "zero-width space"
   \u202F "narrow no-break space"
   ;; Other
   \u2026 "ellipsis"
   \u2022 "bullet"
   \u2212 "minus sign"
   \u00D7 "multiplication sign"
   \u00F7 "division sign"
   \u00AE "registered trademark"
   \u00A9 "copyright"
   \u2122 "trademark"})

(defn find-problematic-unicode
  "Find all problematic Unicode characters that should be ASCII."
  [content]
  (let [found (for [[char desc] problematic-unicode
                    :let [pattern (re-pattern (java.util.regex.Pattern/quote (str char)))
                          matches (re-seq pattern content)]
                    :when (seq matches)]
                {:char char
                 :code (int char)
                 :description desc
                 :count (count matches)})]
    found))

(defn find-other-non-ascii
  "Find any non-ASCII characters not in our known problematic set."
  [content]
  (let [known-chars (set (keys problematic-unicode))
        non-ascii (filter #(and (> (int %) 127)
                                (not (known-chars %)))
                          content)
        grouped (frequencies non-ascii)]
    (when (seq grouped)
      (for [[char cnt] grouped]
        {:char char
         :code (int char)
         :description "unknown non-ASCII"
         :count cnt}))))

(defn find-disabled-entries
  "Find :disabled? patterns which indicate commented-out content."
  [content]
  (let [matches (re-seq #":disabled\?\s+true" content)]
    {:count (count matches)
     :pattern ":disabled? true"
     :description "Entries marked as disabled (typically errors in original)"}))

(defn analyze-traits
  "Check traits for missing :name fields in parsed data."
  [data path]
  (let [results (atom [])]
    (letfn [(check-traits [m current-path]
              (when (map? m)
                (doseq [[k v] m]
                  (cond
                    ;; Found a :traits vector
                    (and (= k :traits) (vector? v))
                    (doseq [[idx trait] (map-indexed vector v)]
                      (when (and (map? trait)
                                 (:description trait)
                                 (not (:name trait)))
                        (swap! results conj
                               {:path (conj current-path :traits idx)
                                :issue "Trait missing :name field"
                                :description (subs (:description trait) 0
                                                   (min 50 (count (:description trait))))})))

                    ;; Recurse into maps
                    (map? v)
                    (check-traits v (conj current-path k))

                    ;; Recurse into map values that are maps
                    :else nil))))]
      (check-traits data []))
    @results))

(defn analyze-content
  "Analyze raw content for potential issues (before parsing)."
  [content]
  (println "\n=== Content Analysis ===")
  (println (str "File size: " (count content) " bytes"))

  ;; nil nil patterns
  (let [{:keys [count pattern description]} (find-nil-nil-patterns content)]
    (when (pos? count)
      (println (str "\n[WARNING] Found " count " '" pattern "' patterns"))
      (println (str "  " description))))

  ;; Problematic Unicode (known replaceable)
  (let [unicode-issues (find-problematic-unicode content)]
    (when (seq unicode-issues)
      (let [total (reduce + (map :count unicode-issues))]
        (println (str "\n[WARNING] Found " total " problematic Unicode characters (will be auto-fixed on import):"))
        (doseq [{:keys [description code count]} (sort-by :count > unicode-issues)]
          (println (str "  - " description " (U+" (format "%04X" code) "): " count " occurrences"))))))

  ;; Unknown non-ASCII (not in our replacement map)
  (let [unknown (find-other-non-ascii content)]
    (when (seq unknown)
      (let [total (reduce + (map :count unknown))]
        (println (str "\n[WARNING] Found " total " unknown non-ASCII characters (may need manual review):"))
        (doseq [{:keys [char code count]} (take 10 (sort-by :count > unknown))]
          (println (str "  - U+" (format "%04X" code) " '" char "': " count " occurrences")))
        (when (> (clojure.core/count unknown) 10)
          (println (str "  ... and " (- (clojure.core/count unknown) 10) " more unique characters"))))))

  ;; Disabled entries
  (let [{:keys [count]} (find-disabled-entries content)]
    (when (pos? count)
      (println (str "\n[INFO] Found " count " disabled entries (previously errored content)")))))

(defn analyze-data
  "Analyze parsed data structure for potential issues."
  [data]
  (println "\n=== Structure Analysis ===")

  ;; Check if multi-plugin format
  (let [multi? (and (map? data)
                    (every? string? (keys data)))]
    (if multi?
      (do
        (println (str "Format: Multi-plugin (" (count data) " sources)"))
        (doseq [source (keys data)]
          (println (str "  - \"" source "\""))))
      (println "Format: Single-plugin")))

  ;; Check for traits without names
  (let [missing-names (analyze-traits data [])]
    (when (seq missing-names)
      (println (str "\n[WARNING] Found " (count missing-names) " traits missing :name field:"))
      (doseq [{:keys [path description]} (take 10 missing-names)]
        (println (str "  - " (pr-str path)))
        (println (str "    \"" description "...\"")))
      (when (> (count missing-names) 10)
        (println (str "  ... and " (- (count missing-names) 10) " more"))))))

;;; ============================================================
;;; Main functions
;;; ============================================================

(defn prettify-file
  "Read an orcbrew file and pretty-print it."
  [filepath]
  (let [content (slurp filepath)
        data (edn/read-string content)]
    (pp/pprint data)))

(defn prettify-to-file
  "Read an orcbrew file and write prettified version to output file."
  [input-path output-path]
  (let [content (slurp input-path)
        data (edn/read-string content)]
    (with-open [w (io/writer output-path)]
      (pp/pprint data w))
    (println (str "Wrote prettified output to: " output-path))))

(defn analyze-file
  "Analyze an orcbrew file for potential issues without modifying it."
  [filepath]
  (println (str "Analyzing: " filepath))
  (println (str "Tool version: " version))
  (let [content (slurp filepath)]
    ;; Analyze raw content first
    (analyze-content content)

    ;; Parse and analyze structure
    (try
      (let [data (edn/read-string content)]
        (analyze-data data))
      (catch Exception e
        (println (str "\n[ERROR] Failed to parse EDN: " (.getMessage e)))))))

(defn -main
  "Entry point for lein run."
  [& args]
  (let [filepath (first args)
        analyze? (some #{"--analyze" "-a"} args)
        output (some #(when (str/starts-with? % "--output=")
                        (subs % 9)) args)]
    (cond
      (nil? filepath)
      (do
        (println "Usage: lein prettify-orcbrew <file.orcbrew> [options]")
        (println "")
        (println "Options:")
        (println "  --analyze, -a        Analyze file for potential issues")
        (println "  --output=<file>      Write prettified output to file")
        (println "")
        (println "Examples:")
        (println "  lein prettify-orcbrew my-content.orcbrew")
        (println "  lein prettify-orcbrew my-content.orcbrew --analyze")
        (println "  lein prettify-orcbrew my-content.orcbrew --output=pretty.edn")
        (throw (ex-info "No filepath provided" {:type :usage-error})))

      (not (.exists (io/file filepath)))
      (do
        (println (str "Error: File not found: " filepath))
        (throw (ex-info (str "File not found: " filepath) {:type :file-not-found :filepath filepath})))

      analyze?
      (analyze-file filepath)

      output
      (prettify-to-file filepath output)

      :else
      (prettify-file filepath))))
