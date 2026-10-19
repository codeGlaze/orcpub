(ns orcpub.dnd.e5.srd-text-hygiene-test
  "Our served SRD files and notes carry none of the text defects we have corrected in the source
   data: PDF debris, escaped line breaks, run-in spacing. Each check is a defect kind that once
   reached a file (open5e-upstream-notes.md); this keeps the next one out."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def served-files
  "Every file under resources/public that holds SRD text or our notes on it."
  ["public/srd/2014/rules.edn" "public/srd/2014/conditions.edn" "public/srd/2014/equipment.edn"
   "public/notes/rules-clarifications.edn"])

(defn strings
  "Every string in the parsed EDN `data`, at any depth."
  [data]
  (filter string? (tree-seq coll? seq data)))

(def defects
  "Each defect kind: a name and a predicate over one string."
  [["an escaped line break (backslash-n written as text)" #(re-find #"\\n" %)]
   ["a carriage return" #(re-find #"\r" %)]
   ["a soft hyphen (U+00AD)" #(re-find #"­" %)]
   ["a typographic hyphen (U+2010/U+2011)" #(re-find #"[‐‑]" %)]
   ["a modifier-letter apostrophe (U+02BC)" #(re-find #"ʼ" %)]
   ["two spaces inside a sentence" #(re-find #"\S  +\S" %)]
   ["a kept line-break hyphen (\"move- ment\")" #(re-find #"\b[a-z]{2,}- (?!or\b|and\b|to\b)[a-z]{2,}\b" %)]])

(deftest served-srd-text-is-clean
  (doseq [path served-files
          :let [data (some-> (io/resource path) slurp edn/read-string)]]
    (testing path
      (is (some? data) "the file is served")
      (doseq [[defect bad?] defects]
        (let [hits (filter bad? (strings data))]
          (is (empty? hits) (str defect ": " (pr-str (take 3 hits)))))))))
