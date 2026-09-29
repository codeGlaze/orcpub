(ns orcpub.comment-discipline-test
  "Tripwire for the comment rule (AGENTS.md, \"Comments and Docstrings\"): flags history phrasing,
   docstrings over `max-docstring-lines` and comment blocks over `max-comment-lines` in `src/` and
   `web/`. Every hit must be fixed or reviewed. See `verdict` for what fails, `baseline-file` for
   the recorded debt and the reviewed exceptions.
   Also keeps every `FIELD NOTE (id):` as recorded (see `note-verdict`).
   Remove cleaned entries: `COMMENT_BASELINE=prune lein test :only orcpub.comment-discipline-test`.
   GOTCHA: an entry names the exact text by hash; editing the text needs a new review."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [rewrite-clj.parser :as p]
            [rewrite-clj.node :as n])
  (:import (java.security MessageDigest)))

(def roots ["src" "web"])
(def baseline-file "test/comment-baseline.edn")
(def max-docstring-lines 6)
(def max-comment-lines 4)

(def history-phrase
  "History or decision narrative, matched case-insensitively."
  #"(?i)\b(used to be|(?:this|it|they|we|that|which) used to|(?:was|were) once|originally|previously|until recently|historically|at one point|back when|owner'?s decision|regression window|this (?:fix|change|commit) (?:is|was|makes|made))\b")

(def benign-phrase
  "Wording that matches `history-phrase` but describes a state: \"a previously-saved character\"."
  #"(?i)\bpreviously[- ][a-z]+ed\b")

(def placeholder-reason
  "A reason that has not been written yet."
  #"(?i)^\s*(review|todo|tbd|fixme)\b")

(def ^:private doc-forms
  "Forms whose third element is a docstring when more follows it."
  #{'defn 'defn- 'defmacro 'defmulti 'ns 'def 'defprotocol 'defrecord 'deftest})

;; ── Finding hits ─────────────────────────────────────────────────────────────

(defn- sources []
  (->> roots
       (mapcat #(file-seq (io/file %)))
       (filter #(re-find #"\.clj[cs]?$" (.getName %)))
       (map #(.getPath %))
       sort))

(defn- normalize [s] (str/trim (str/replace s #"\s+" " ")))

(defn- snippet [s] (let [t (normalize s)] (subs t 0 (min 70 (count t)))))

(defn text-hash
  "First 12 hex digits of the SHA-256 of `s`, whitespace-normalized."
  [s]
  (let [d (.digest (MessageDigest/getInstance "SHA-256") (.getBytes (normalize s) "UTF-8"))]
    (subs (apply str (map #(format "%02x" %) d)) 0 12)))

(defn- comment-text [node] (str/replace (str/trimr (n/string node)) #"^;+\s?" ""))

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

(defn- note-spans
  "[id start end] for each `FIELD NOTE (id):` in `block` (comment texts), `end` exclusive. A note
   runs from its marker line to a blank comment line, the next note or a GOTCHA."
  [block]
  (keep (fn [[i line]]
          (when-let [id (second (re-find #"FIELD NOTE \(([^)]+)\):" line))]
            [id i (+ i 1 (count (take-while #(and (not (str/blank? %))
                                                   (not (re-find #"^\s*(FIELD NOTE \(|GOTCHA)" %)))
                                             (drop (inc i) block))))]))
        (map-indexed vector block)))

(defn- sexpr [node] (try (n/sexpr node) (catch Exception _ nil)))

(defn- docstring
  "[name doc] of a docstring-bearing form node, or nil. The doc is the string after the name, the
   name's `^{:doc ...}` metadata, or an attr-map's `:doc`."
  [node]
  (when (= :list (n/tag node))
    (let [[head nm doc & more] (remove n/whitespace-or-comment? (n/children node))
          name-form (some-> nm sexpr)]
      (when (and head nm (contains? doc-forms (sexpr head)))
        (when-let [d (or (let [d (some-> doc sexpr)] (when (and (string? d) (seq more)) d))
                         (let [d (:doc (meta name-form))] (when (string? d) d))
                         (let [m (some-> doc sexpr)] (when (and (map? m) (string? (:doc m))) (:doc m))))]
          [(str (sexpr head) " " (or name-form "?")) d])))))

(defn- walk [node f]
  (f node)
  (when (n/inner? node)
    (doseq [c (n/children node)] (walk c f))))

(defn- line-of [src text]
  (if-let [i (str/index-of src text)]
    (inc (count (re-seq #"\n" (subs src 0 i))))
    0))

(defn- history [text]
  (some-> (re-find history-phrase (str/replace text benign-phrase "")) first str/lower-case))

(defn hits-in
  "Hits in source text `src` of file `file`, each {:file :signal :text :hash :line}. `:signal` is
   :history-phrase, :long-docstring or :long-comment; `:text` names the hit; `:hash` is of its
   whole comment block or docstring."
  [file src]
  (let [out (atom [])
        add! (fn [signal text whole locate]
               (swap! out conj {:file file :signal signal :text text :hash (text-hash whole)
                                :line (line-of src locate)}))]
    (walk (p/parse-string-all src)
          (fn [node]
            (when (n/inner? node)
              (doseq [block (comment-blocks (n/children node))
                      :let [whole (str/join "\n" block)
                            first-line (first (filter counted-line? block))]
                      :when first-line]
                (when-let [m (history whole)]
                  (add! :history-phrase (str (snippet first-line) " | " m) whole first-line))
                (let [in-note (set (mapcat (fn [[_ a b]] (range a b)) (note-spans block)))
                      rest-lines (keep-indexed #(when-not (in-note %1) %2) block)
                      counted (filter counted-line? rest-lines)]
                  (when (< max-comment-lines (count counted))
                    (add! :long-comment (snippet (first counted)) (str/join "\n" rest-lines) (first counted))))))
            (when-let [[nm doc] (docstring node)]
              (when-let [m (history doc)]
                (add! :history-phrase (str nm " | " m) doc nm))
              (when (< max-docstring-lines (count (str/split-lines (str/trim doc))))
                (add! :long-docstring nm doc nm)))))
    @out))

(defn field-notes-in
  "Each `;; FIELD NOTE (id):` in source text `src`, as {:file :id :hash :line} (see `note-spans`)."
  [file src]
  (let [out (atom [])]
    (walk (p/parse-string-all src)
          (fn [node]
            (when (n/inner? node)
              (doseq [block (comment-blocks (n/children node))
                      [note-id a b] (note-spans block)
                      :let [body (subvec block a b)]]
                (swap! out conj {:file file :id note-id :hash (text-hash (str/join "\n" body))
                                 :line (line-of src (first body))})))))
    @out))

(defn- all-hits [] (mapcat #(hits-in % (slurp %)) (sources)))

(defn- all-notes [] (mapcat #(field-notes-in % (slurp %)) (sources)))

;; ── Deciding ─────────────────────────────────────────────────────────────────

(defn id [h] [(:file h) (:signal h) (:text h) (:hash h)])

(defn verdict
  "What `hits` owe, given the recorded `baseline` ([id ...], debt from before the check) and
   `allowed` ({id reason}, reviewed exceptions):
   {:listed :allowed :new :gone :stale-allowed :unreasoned}. Fails unless the last four are empty."
  [hits {:keys [baseline allowed]}]
  (let [allowed-ids (set (keys allowed))
        counted (remove #(allowed-ids (id %)) hits)
        want (frequencies baseline)
        have (frequencies (map id counted))
        found (set (map id hits))]
    {:listed (filter #(contains? want (id %)) counted)
     :allowed (filter #(allowed-ids (id %)) hits)
     :new (filter #(> (get have (id %)) (get want (id %) 0)) counted)
     :gone (vec (for [[k c] want, _ (range (- c (get have k 0)))] k))
     :stale-allowed (vec (remove found allowed-ids))
     :unreasoned (vec (for [[k reason] allowed
                            :when (or (not (string? reason)) (str/blank? reason)
                                      (re-find placeholder-reason reason))]
                        k))}))

(defn note-verdict
  "What field `notes` owe, given `recorded` ({[file id] hash}): {:unrecorded :gone :duplicate}, each
   failing unless empty. A note is unrecorded when it is new or its text changed."
  [notes recorded]
  (let [found (group-by (juxt :file :id) notes)]
    {:unrecorded (vec (remove #(= (:hash %) (get recorded [(:file %) (:id %)])) notes))
     :gone (vec (remove found (keys recorded)))
     :duplicate (vec (keep (fn [[k ns]] (when (< 1 (count ns)) k)) found))}))

;; ── The baseline file ────────────────────────────────────────────────────────

(defn- read-baseline []
  (let [f (io/file baseline-file)]
    (when (.exists f) (edn/read-string (slurp f)))))

(defn- write-baseline! [{:keys [allowed baseline field-notes]}]
  (spit baseline-file
        (binding [*print-length* nil *print-level* nil]
          (with-out-str
            (println ";; Written by orcpub.comment-discipline-test. An entry is [file signal text hash].")
            (println ";; :baseline is debt from before the check: clean it, then prune.")
            (println ";; :allowed is reviewed exceptions, each with the reason it is spec.")
            (println ";; :field-notes is {[file id] hash} per FIELD NOTE; record a new or edited one by hand.")
            (pprint/pprint {:allowed (into (sorted-map) allowed) :baseline (vec (sort baseline))
                            :field-notes (into (sorted-map) field-notes)})))))

(defn- remove-once
  "`v` without one occurrence of each of `ks`."
  [v ks]
  (reduce (fn [v k] (let [i (.indexOf ^java.util.List v k)]
                      (if (neg? i) v (into (subvec v 0 i) (subvec v (inc i))))))
          (vec v) ks))

(defn- report [title hs]
  (when (seq hs)
    (println (str "\n" title " (" (count hs) "):"))
    (doseq [[file fhs] (sort-by key (group-by :file (sort-by (juxt :file :line) hs)))]
      (println " " file)
      (doseq [{:keys [line signal text]} fhs]
        (println (format "    %5d  %-15s %s" line (name signal) text))))))

(deftest comments-and-docstrings-are-spec
  (let [hits (all-hits)
        recorded (read-baseline)
        mode (System/getenv "COMMENT_BASELINE")]
    (cond
      (and (= "seed" mode) (nil? recorded))
      (do (write-baseline! {:allowed {} :baseline (map id hits)
                            :field-notes (into {} (map (juxt (juxt :file :id) :hash)) (all-notes))})
          (println "seeded" (count hits) "entries to" baseline-file))

      (and (= "prune" mode) recorded)
      (let [{:keys [gone stale-allowed]} (verdict hits recorded)]
        (write-baseline! {:allowed (apply dissoc (:allowed recorded) stale-allowed)
                          :baseline (remove-once (:baseline recorded) gone)
                          :field-notes (:field-notes recorded)})
        (println "pruned" (count gone) "cleaned entries and" (count stale-allowed) "stale approvals"))

      :else
      (let [{:keys [listed allowed gone stale-allowed unreasoned] fresh :new} (verdict hits recorded)]
        (is (some? recorded) (str baseline-file " is missing"))
        (report "Existing debt, recorded in the baseline: clean these" listed)
        (report "Reviewed exceptions (:allowed)" allowed)
        (testing "every hit is fixed or reviewed"
          (report "NEW: fix each, or review it and add it to :allowed" fresh)
          (when (seq fresh)
            (println "\nTo approve after review, add to :allowed with a real reason:")
            (doseq [h fresh] (println (str "  " (pr-str (id h)) " \"REVIEW: why this is spec\""))))
          (is (empty? fresh) "shorten to spec and move history to docs/kb/, or review and approve it"))
        (testing "a cleaned entry leaves the baseline"
          (is (empty? gone) (str "no longer found; run COMMENT_BASELINE=prune: " (pr-str gone))))
        (testing "an approval names text that still exists unchanged"
          (is (empty? stale-allowed) (str "approved text changed or was removed; review again: "
                                          (pr-str stale-allowed))))
        (testing "every approval has a written reason"
          (is (empty? unreasoned) (str "reason missing or a placeholder: " (pr-str unreasoned))))
        (let [{:keys [unrecorded gone duplicate]} (note-verdict (all-notes) (:field-notes recorded))]
          (testing "every FIELD NOTE is recorded as it stands"
            (when (seq unrecorded)
              (println "\nFIELD NOTEs new or changed. Check each still states a verified fact, then record:")
              (doseq [n unrecorded]
                (println (str "  " (pr-str [(:file n) (:id n)]) " " (pr-str (:hash n)) "   ; line " (:line n)))))
            (is (empty? unrecorded) "a field note is new or was edited: check it, then record its hash")
            (is (empty? gone) (str "recorded field notes no longer in the source; delete by hand only if "
                                   "the fact no longer holds: " (pr-str gone)))
            (is (empty? duplicate) (str "two field notes share an id in one file: " (pr-str duplicate)))))))))

;; ── The check's own tests ────────────────────────────────────────────────────

(def ^:private fixture
  (str "(ns x)\n"
       ";; This used to drop entries here.\n"
       "(defn short \"Spec.\" [] 1)\n"
       "(defn tall \"One.\n Two.\n Three.\n Four.\n Five.\n Six.\n Seven.\" [] 1)\n"
       ";; one\n;; two\n;; three\n;; four\n;; five\n(def y 1)\n"
       ";; FIELD NOTE (x): one\n;; two\n;; three\n;; four\n;; five\n(def z 1)\n"
       ";; Heals a previously-corrupted spot.\n(def w 1)\n"
       "(def data \"Previously, a dragon slept here.\")\n"))

(deftest blocks-and-notes-end-where-they-should
  (let [signals #(frequencies (map :signal (hits-in "x.clj" %)))]
    (is (nil? (:long-comment (signals ";; one\n;; two\n;; three\n\n;; four\n;; five\n;; six\n(def y 1)\n")))
        "a blank line ends a comment block")
    (is (nil? (:long-comment (signals "(defn f []\n  ;; one\n  ;; two\n  ;; three\n\n  ;; four\n  ;; five\n  1)\n")))
        "inside a form too")
    (is (= 1 (:long-comment (signals ";; one\n;; two\n;; three\n;; four\n;; five\n;; FIELD NOTE (n): fact\n(def y 1)\n")))
        "a note exempts only its own lines, not a long comment beside it")
    (is (nil? (:long-comment (signals ";; Intro.\n;; FIELD NOTE (n): one\n;; two\n;; three\n;; four\n;; five\n(def y 1)\n")))
        "a long note beside a short comment is exempt")
    (is (= (map :hash (field-notes-in "x.clj" ";; FIELD NOTE (n): fact\n(def y 1)\n"))
           (map :hash (field-notes-in "x.clj" ";; FIELD NOTE (n): fact\n\n;; after a blank line\n(def y 1)\n")))
        "a note does not absorb text after a blank line")))

(deftest metadata-docstrings-are-checked
  (let [hs #(set (map (juxt :signal :text) (hits-in "x.clj" %)))]
    (is (contains? (hs "(ns ^{:doc \"This used to load.\"} x.y)\n") [:history-phrase "ns x.y | this used to"]))
    (is (contains? (hs "(defn f {:doc \"It used to\\nfail.\"} [] 1)\n") [:history-phrase "defn f | it used to"]))
    (is (contains? (hs "(def ^{:doc \"1\\n2\\n3\\n4\\n5\\n6\\n7\"} v 1)\n") [:long-docstring "def v"]))))

(deftest a-field-note-is-kept-as-recorded
  (let [src (str ";; Intro.\n;;\n;; FIELD NOTE (a): fact one\n;; continues\n;;\n"
                 ";; FIELD NOTE (b): fact two\n;; GOTCHA: not part of b\n(def z 1)\n")
        notes (field-notes-in "x.clj" src)
        recorded (into {} (map (juxt (juxt :file :id) :hash)) notes)
        hash-of (fn [s note-id] (:hash (first (filter #(= note-id (:id %)) (field-notes-in "x.clj" s)))))]
    (is (= ["a" "b"] (map :id notes)))
    (is (= {:unrecorded [] :gone [] :duplicate []} (note-verdict notes recorded)) "recorded: passes")
    (is (not= (hash-of src "a") (hash-of (str/replace src "continues" "goes on") "a"))
        "the note's continuation lines are part of it")
    (is (= (hash-of src "b") (hash-of (str/replace src "not part of b" "changed") "b"))
        "a GOTCHA after it is not")
    (is (= ["a"] (map :id (:unrecorded (note-verdict (field-notes-in "x.clj" (str/replace src "fact one" "fact 1")) recorded))))
        "edited: must be recorded again")
    (is (= [["x.clj" "b"]] (:gone (note-verdict (field-notes-in "x.clj" (str/replace src "FIELD NOTE (b)" "NOTE (b)")) recorded)))
        "removed: fails")
    (is (= ["c"] (map :id (:unrecorded (note-verdict (field-notes-in "x.clj" (str src ";; FIELD NOTE (c): new\n(def q 1)\n")) recorded))))
        "new: must be recorded")
    (is (= [["x.clj" "a"]] (:duplicate (note-verdict (field-notes-in "x.clj" (str src ";; FIELD NOTE (a): again\n(def q 1)\n")) recorded)))
        "a repeated id fails")))

(defn- tall-in [src] (first (filter #(= :long-docstring (:signal %)) (hits-in "x.clj" src))))

(deftest the-signals-fire-on-what-they-describe
  (let [signals (frequencies (map :signal (hits-in "x.clj" fixture)))]
    (is (= 1 (:history-phrase signals))
        "the comment's history; not a data string, not a state (\"previously-corrupted\")")
    (is (= 1 (:long-docstring signals)))
    (is (= 1 (:long-comment signals)) "a FIELD NOTE block is exempt from length")
    (is (= "defn tall" (:text (tall-in fixture))))
    (is (not= (:hash (tall-in fixture)) (:hash (tall-in (str/replace fixture "Seven." "Seven!"))))
        "editing the text changes its hash")))

(deftest every-hit-needs-a-fix-or-a-review
  (let [hs (hits-in "x.clj" fixture)
        tall (tall-in fixture)
        others (map id (remove #{tall} hs))
        edited (tall-in (str/replace fixture "Seven." "Seven!"))
        approved {(id tall) "Each line is an argument."}]
    (is (= [tall] (:new (verdict hs {:baseline others :allowed {}}))) "unrecorded: new")
    (is (empty? (:new (verdict hs {:baseline others :allowed approved}))) "reviewed with a reason: passes")
    (is (= [(id tall)] (:unreasoned (verdict hs {:baseline others :allowed {(id tall) "REVIEW: why"}})))
        "a placeholder reason fails")
    (let [v (verdict (cons edited (remove #{tall} hs)) {:baseline others :allowed approved})]
      (is (= [edited] (:new v)) "edited after approval: new again")
      (is (= [(id tall)] (:stale-allowed v)) "and the old approval is stale"))
    (is (= [(id tall)] (:gone (verdict (remove #{tall} hs) {:baseline (map id hs) :allowed {}})))
        "cleaned debt must leave the baseline")))
