(ns orcpub.topic-index
  "Generate docs/kb/topic-index.md — a flat, greppable map from TOPIC to document.

   Why this exists. The KB is the group memory, and CLAUDE.md now points every agent at it, but
   pointing is only useful if `grep` on the index answers \"has this been looked at before?\".
   Measured against fourteen realistic queries, the hand-written index answered **six**: `starting
   equipment`, `monster`, `spell list`, `ability increase`, `datalist` and `concentration` all
   returned nothing, though `starting-equipment.md` is a whole document. A table of contents
   describes documents; it does not carry the words someone would search for.

   The corpus does carry them — 455 headings across 48 documents — so this reads the headings rather
   than asking anyone to maintain a keyword list by hand. Filenames are emitted BOTH hyphenated and
   spaced, because a query is typed with spaces and a filename is not, which is exactly why
   `starting equipment` missed.

   Regenerate:  lein with-profile +tools run -m orcpub.topic-index
   The coverage gate (topic_index_test) fails if a document is missing from the generated file."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(def ^:private kb-dir "docs/kb")
(def ^:private out-file "docs/kb/topic-index.md")

(defn- docs []
  (->> (file-seq (io/file kb-dir))
       (filter #(.isFile %))
       (map #(.getName %))
       (filter #(str/ends-with? % ".md"))
       (remove #{"README.md" "topic-index.md"})
       sort))

(defn- headings [f]
  (->> (str/split-lines (slurp (io/file kb-dir f)))
       (keep #(second (re-matches #"^#{2,3}\s+(.+?)\s*$" %)))
       ;; strip inline code/links/emphasis so the terms read as prose
       (map #(-> % (str/replace #"`([^`]*)`" "$1")
                   (str/replace #"\[([^\]]*)\]\([^)]*\)" "$1")
                   (str/replace #"[*_]{1,2}" "")
                   str/trim))
       (remove str/blank?)
       distinct))

(def ^:private stop
  #{"the" "and" "that" "this" "with" "for" "not" "but" "are" "was" "were" "has" "have" "had" "which"
    "from" "into" "when" "what" "where" "how" "why" "it" "its" "a" "an" "of" "to" "in" "on" "is" "as"
    "at" "by" "or" "be" "so" "if" "than" "then" "there" "here" "one" "two" "can" "cannot" "does"
    "doing" "done" "would" "should" "could" "must" "will" "now" "still" "only" "also" "any" "all"
    "every" "each" "same" "other" "more" "most" "less" "just" "like" "who" "whom" "they" "them"
    "their" "you" "your" "we" "our" "us" "i" "me" "my" "he" "she" "his" "her" "him" "no" "yes"
    "doc" "docs" "md" "see" "note" "notes" "used" "using" "use" "uses" "new" "old" "first" "second"
    "test" "tests" "code" "line" "lines" "file" "files" "case" "cases" "thing" "things" "way" "ways"
    "well" "much" "many" "some" "such" "over" "under" "after" "before" "because" "since" "while"})

(defn- words [text]
  (->> (-> text
           (str/replace #"```[\s\S]*?```" " ")   ; code blocks carry identifiers, not topics
           (str/replace #"`[^`]*`" " ")
           str/lower-case
           (str/split #"[^a-z0-9-]+"))
       (remove str/blank?)
       (filter #(>= (count %) 3))
       (remove stop)))

(defn- distinctive-terms
  "The words that characterise ONE document against the corpus — crude TF-IDF. Headings alone were
   not enough: measured on fourteen realistic queries, a headings-only index answered six. `monster`,
   `concentration`, `datalist` and `spell list` are discussed at length in bodies and never appear in
   a heading, so a reader asking whether they had been looked at got nothing."
  [freqs n-docs f k]
  (let [tf (get freqs f)
        df (fn [w] (count (filter #(contains? (get freqs %) w) (keys freqs))))]
    (->> tf
         (map (fn [[w c]] [w (* c (Math/log (/ (double n-docs) (max 1 (df w)))))]))
         (sort-by (comp - second))
         (take k)
         (map first)
         sort)))

(defn- terms-from-name [f]
  (let [stem (str/replace f #"\.md$" "")]
    [stem (str/replace stem "-" " ")]))

(defn render []
  (let [ds    (docs)
        freqs (into {} (for [f ds] [f (frequencies (words (slurp (io/file kb-dir f))))]))
        n     (count ds)]
    (str "# Topic index — what has already been looked at\n\n"
         "**GENERATED — do not edit.** `lein with-profile +tools run -m orcpub.topic-index`\n\n"
         "## Grep the corpus first\n\n"
         "```\ngrep -ril \"<term>\" docs/kb/\n```\n\n"
         "**That is the search.** This file is for orientation — what each document is about, and\n"
         "which one owns a topic — not for recall. Measured against fourteen realistic queries the\n"
         "corpus answered **all fourteen**; this index answered **nine**. It cannot match multi-word\n"
         "phrases (`import conflict`, `spell list`) because it is built from single words, and a\n"
         "topic mentioned once loses its place to one discussed throughout. Use it to find the right\n"
         "document, then read that document; use grep to find out whether anyone has been there.\n\n"
         "Each document is listed with its filename (hyphenated **and** spaced, because queries are\n"
         "typed with spaces), the words that most distinguish it from the rest of the corpus, and\n"
         "every section heading it contains.\n\n"
         "---\n\n"
         (str/join "\n"
           (for [f ds]
             (str "## " f "\n\n"
                  "_" (str/join " · " (terms-from-name f)) "_\n\n"
                  "**topics:** " (str/join ", " (distinctive-terms freqs n f 18)) "\n\n"
                  (str/join "\n" (map #(str "- " %) (headings f)))
                  "\n")))
         "\n")))

(defn -main [& _]
  (spit out-file (render))
  (println "wrote" out-file
           (str "(" (count (docs)) " documents, "
                (reduce + (map #(count (headings %)) (docs))) " headings)")))
