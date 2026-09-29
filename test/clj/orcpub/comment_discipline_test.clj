(ns orcpub.comment-discipline-test
  "Flags comments and docstrings in `src/` and `web/` that break the comment rule (AGENTS.md,
   \"Comments and Docstrings\"): history phrasing, docstrings over `max-docstring-lines`, comment
   blocks over `max-comment-lines`. Hits recorded in `baseline-file` are listed on every run; a hit
   not recorded fails, and so does a recorded hit that is gone. `:allowed` entries need a reason.
   Re-record: `RECORD_COMMENT_BASELINE=1 lein test :only orcpub.comment-discipline-test`.
   GOTCHA: a hit's identity is its file, signal and text, not its line, so moving code keeps it."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [rewrite-clj.parser :as p]
            [rewrite-clj.node :as n]))

(def roots ["src" "web"])
(def baseline-file "test/comment-baseline.edn")
(def max-docstring-lines 6)
(def max-comment-lines 4)

(def history-phrase
  "History or decision narrative. Matched case-insensitively against comment and docstring text."
  #"(?i)\b(used to be|(?:this|it|they|we|that|which) used to|(?:was|were) once|originally|previously|until recently|historically|at one point|back when|owner'?s decision|regression window|this (?:fix|change|commit) (?:is|was|makes|made))\b")

(def ^:private doc-forms
  "Forms whose third element is a docstring when more follows it."
  #{'defn 'defn- 'defmacro 'defmulti 'ns 'def 'defprotocol 'defrecord 'deftest})

(defn- sources []
  (->> roots
       (mapcat #(file-seq (io/file %)))
       (filter #(re-find #"\.clj[cs]?$" (.getName %)))
       (map #(.getPath %))
       sort))

(defn- normalize [s] (str/trim (str/replace s #"\s+" " ")))

(defn- snippet [s] (let [t (normalize s)] (subs t 0 (min 70 (count t)))))

(defn- comment-text
  "A comment node's text without its semicolons."
  [node]
  (str/replace (str/trimr (n/string node)) #"^;+\s?" ""))

(defn- counted-line?
  "True for a comment line with words: not blank, not a banner of rule characters."
  [line]
  (boolean (re-find #"[A-Za-z]{2}" line)))

(defn- comment-blocks
  "Runs of comment lines in `children` with no blank line or code between them, each a vector of
   texts."
  [children]
  (loop [[c & more :as cs] children, run [], blocks []]
    (cond
      (empty? cs) (cond-> blocks (seq run) (conj run))
      (= :comment (n/tag c)) (recur more (conj run (comment-text c)) blocks)
      (= :whitespace (n/tag c)) (recur more run blocks)
      :else (recur more [] (cond-> blocks (seq run) (conj run))))))

(defn- docstring
  "[name doc] of a docstring-bearing form node, or nil."
  [node]
  (when (= :list (n/tag node))
    (let [[head nm doc & more] (remove n/whitespace-or-comment? (n/children node))]
      (when (and head nm doc (seq more) (contains? doc-forms (try (n/sexpr head) (catch Exception _ nil))))
        (let [d (try (n/sexpr doc) (catch Exception _ nil))]
          (when (string? d)
            [(str (n/sexpr head) " " (try (n/sexpr nm) (catch Exception _ "?"))) d]))))))

(defn- walk [node f]
  (f node)
  (when (n/inner? node)
    (doseq [c (n/children node)] (walk c f))))

(defn- line-of [src text]
  (if-let [i (str/index-of src text)]
    (inc (count (re-seq #"\n" (subs src 0 i))))
    0))

(defn hits
  "Hits in one source file, each {:file :signal :text :line}. `:signal` is :history-phrase,
   :long-docstring or :long-comment; `:text` identifies the hit."
  [file]
  (let [src (slurp file)
        root (p/parse-string-all src)
        out (atom [])
        add! (fn [signal text locate]
               (swap! out conj {:file file :signal signal :text text :line (line-of src locate)}))]
    (walk root
          (fn [node]
            (when (n/inner? node)
              (doseq [block (comment-blocks (n/children node))
                      :let [joined (str/join " " block)
                            first-line (first (filter counted-line? block))]
                      :when first-line]
                (when-let [m (re-find history-phrase joined)]
                  (add! :history-phrase (str (snippet first-line) " | " (str/lower-case (first m))) first-line))
                (when (and (< max-comment-lines (count (filter counted-line? block)))
                           (not (some #(str/includes? % "FIELD NOTE (") block)))
                  (add! :long-comment (snippet first-line) first-line))))
            (when-let [[nm doc] (docstring node)]
              (when-let [m (re-find history-phrase doc)]
                (add! :history-phrase (str nm " | " (str/lower-case (first m))) nm))
              (when (< max-docstring-lines (count (str/split-lines (str/trim doc))))
                (add! :long-docstring nm nm)))))
    @out))

(defn- id [{:keys [file signal text]}] [file signal text])

(defn- read-baseline []
  (let [f (io/file baseline-file)]
    (if (.exists f) (edn/read-string (slurp f)) {:allowed {} :baseline []})))

(defn- report [title hs]
  (when (seq hs)
    (println (str "\n" title " (" (count hs) "):"))
    (doseq [[file fhs] (sort-by key (group-by :file (sort-by (juxt :file :line) hs)))]
      (println " " file)
      (doseq [{:keys [line signal text]} fhs]
        (println (format "    %5d  %-15s %s" line (name signal) text))))))

(deftest comments-and-docstrings-are-spec
  (let [all (mapcat hits (sources))
        {:keys [allowed baseline]} (read-baseline)
        allowed-ids (set (keys allowed))
        counted (remove #(allowed-ids (id %)) all)]
    (if (System/getenv "RECORD_COMMENT_BASELINE")
      (do (spit baseline-file
                (binding [*print-length* nil *print-level* nil]
                  (with-out-str
                  (println ";; Recorded by orcpub.comment-discipline-test. Each entry is [file signal text].")
                  (println ";; Clean an entry, then delete it here. :allowed needs a reason for each.")
                  (pprint/pprint {:allowed allowed :baseline (vec (sort (map id counted)))}))))
          (println "recorded" (count counted) "hits to" baseline-file))
      (let [want (frequencies baseline)
            have (frequencies (map id counted))
            new-ids (set (keep (fn [[k c]] (when (> c (get want k 0)) k)) have))
            gone (keep (fn [[k c]] (when (> c (get have k 0)) k)) want)]
        (report "Existing hits, recorded in the baseline: clean these" (filter #(and (contains? want (id %)) (not (new-ids (id %)))) counted))
        (report "Allowed, with a reason in the baseline file" (filter #(allowed-ids (id %)) all))
        (testing "no new narrative, long docstring or long comment block"
          (report "NEW hits (not in the baseline)" (filter #(new-ids (id %)) counted))
          (is (empty? new-ids) "shorten it to spec and move history to docs/kb/, or add it to :allowed with a reason"))
        (testing "a cleaned hit is removed from the baseline"
          (is (empty? gone) (str "no longer found; delete from " baseline-file ": " (pr-str (vec gone))))))))
  (testing "every allowed entry has a reason"
    (is (every? (fn [[_ reason]] (and (string? reason) (not (str/blank? reason))))
                (:allowed (read-baseline))))))

(deftest the-signals-fire-on-what-they-describe
  (let [f (java.io.File/createTempFile "comment-discipline" ".clj")]
    (spit f (str "(ns x)\n"
                 ";; This used to drop entries here.\n"
                 "(defn short \"Spec.\" [] 1)\n"
                 "(defn tall \"One.\n Two.\n Three.\n Four.\n Five.\n Six.\n Seven.\" [] 1)\n"
                 ";; one\n;; two\n;; three\n;; four\n;; five\n(def y 1)\n"
                 ";; FIELD NOTE (x): one\n;; two\n;; three\n;; four\n;; five\n(def z 1)\n"
                 "(def data \"Previously, a dragon slept here.\")\n"))
    (let [hs (hits (.getPath f))
          signals (frequencies (map :signal hs))]
      (is (= 1 (:history-phrase signals)) "the comment's history, not the data string's")
      (is (= 1 (:long-docstring signals)))
      (is (= 1 (:long-comment signals)) "a FIELD NOTE block is exempt from length")
      (is (= "defn tall" (:text (first (filter #(= :long-docstring (:signal %)) hs))))))
    (.delete f)))
