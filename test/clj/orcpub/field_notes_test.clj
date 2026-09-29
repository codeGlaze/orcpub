(ns orcpub.field-notes-test
  "Keeps the source's `FIELD NOTE (id):` comments. A field note records a verified fact about data or
   behaviour that a reader would otherwise get wrong. A cleanup pass keeps it; change one only when
   the fact changes, and change its entry in `protected` with it."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]))

(def protected
  "{file {note-id [phrases the note must still contain]}}."
  {"src/cljs/orcpub/dnd/e5/content_reconciliation.cljs"
   {"heal-sites" ["reconcile-former-keys" "events/set-character" "autosave-fx/cache-template"
                  "subs ::char5e/character"]
    "heal-typed" ["unique only within its type" "library/pick-homes" "flat index"
                  "unknown, never empty"]
    "spell-selection-orphans" ["another" "prefix"]}
   "src/cljc/orcpub/dnd/e5/options.cljc"
   {"background-name-key" ["common/name-to-kw name" "former key"]}})

(defn- note-text
  "The comment lines of the note `id` in `src`, joined, or nil."
  [src id]
  (let [lines (str/split-lines src)
        start (first (keep-indexed #(when (str/includes? %2 (str "FIELD NOTE (" id "):")) %1) lines))]
    (when start
      (->> (drop start lines)
           (take-while #(str/starts-with? (str/triml %) ";;"))
           (take-while (let [first? (atom true)]
                         #(or (compare-and-set! first? true false)
                              (not (re-find #";;\s*(FIELD NOTE|GOTCHA)" %)))))
           (map #(str/replace % #"^\s*;+\s?" ""))
           (str/join " ")
           (#(str/replace % #"\s+" " "))))))

(deftest every-protected-field-note-is-kept-with-its-facts
  (doseq [[file notes] protected
          :let [src (slurp file)]
          [id phrases] notes
          :let [text (note-text src id)]]
    (testing (str file " FIELD NOTE (" id ")")
      (is (some? text) "the note is gone")
      (doseq [p phrases]
        (is (and text (str/includes? text p)) (str "the note no longer says: " p))))))
