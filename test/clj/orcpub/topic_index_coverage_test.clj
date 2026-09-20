(ns orcpub.topic-index-coverage-test
  "The KB is the group memory, so a document that no index mentions is a document nobody finds.

   This does NOT check the generated index is byte-current — headings change constantly and an
   exact-match gate would cry wolf until someone deleted it. It checks the thing that actually
   breaks memory: a document exists and neither index knows about it.

   Reachability is TRANSITIVE and by RELATIVE PATH. docs/kb/rescued/ is linked from
   rescued/README.md, which docs/kb/README.md links -- reachable, just not directly. The
   original checked direct linkage by BASENAME, so it went red on those three files the day
   they were added (2026-09-12) and stayed red, which is what a false failure does to a guard.

   Regenerate:  docs/kb/tools/topic-index.sh   (picks python3, clojure or lein)"
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(def ^:private kb-dir "docs/kb")

(defn- kb-docs
  "Every KB document, keyed by path RELATIVE to docs/kb, sorted.

   The relative path matters: file-seq recurses, so taking (.getName) collapsed
   docs/kb/rescued/x.md to x.md -- which matches neither the index entry
   (## rescued/x.md) nor anything on disk."
  []
  (let [root (.toPath (io/file kb-dir))]
    (->> (file-seq (io/file kb-dir))
         (filter #(.isFile %))
         (filter #(str/ends-with? (.getName %) ".md"))
         (remove #(contains? #{"README.md" "topic-index.md"} (.getName %)))
         (map #(str (.relativize root (.toPath %))))
         sort)))

(defn- reachable-docs
  "Documents reachable from docs/kb/README.md by following relative .md links,
   transitively. An archival doc linked only from its own manifest is still
   findable, and treating it as orphaned trains people to ignore this test."
  []
  ;; Canonicalise the root too. relativize cannot compare a relative path
  ;; against an absolute one -- it throws, and an earlier version of this
  ;; swallowed that in the catch below, so every link resolved to nil and the
  ;; traversal found nothing but its own starting point.
  (let [root (.getCanonicalFile (io/file kb-dir))]
    (loop [seen #{} queue ["README.md"]]
      (if-let [rel (first queue)]
        (if (contains? seen rel)
          (recur seen (rest queue))
          (let [f (io/file root rel)
                links (when (.isFile f)
                        (->> (re-seq #"\]\(([^)#]+\.md)[^)]*\)" (slurp f))
                             (map second)
                             (remove #(re-find #"^(https?:|/)" %))
                             (keep (fn [t]
                                     (let [parent (.getParent (io/file rel))
                                           target (if parent (str parent "/" t) t)]
                                       (try
                                         (str (.relativize (.toPath root)
                                                           (.toPath (.getCanonicalFile
                                                                     (io/file root target)))))
                                         (catch Exception _ nil)))))))]
            (recur (conj seen rel) (concat (rest queue) links))))
        seen))))

(deftest every-kb-document-is-reachable
  (let [topics (slurp (io/file kb-dir "topic-index.md"))
        reach  (reachable-docs)]
    (doseq [d (kb-docs)]
      (testing d
        (is (contains? reach d)
            (str d " is not reachable from docs/kb/README.md by any chain of links — "
                 "a document nobody can navigate to is a document nobody finds"))
        (is (str/includes? topics (str "## " d))
            (str d " is missing from docs/kb/topic-index.md — regenerate it: "
                 "docs/kb/tools/topic-index.sh"))))))

(deftest the-index-does-not-point-at-documents-that-are-gone
  (let [present (set (kb-docs))
        linked  (->> (re-seq #"\]\(([a-z0-9._/-]+\.md)\)" (slurp (io/file kb-dir "README.md")))
                     (map second)
                     (remove #{"README.md" "topic-index.md"})
                     (remove #(str/ends-with? % "/README.md"))
                     set)]
    (doseq [l (sort linked)]
      (is (contains? present l)
          (str "docs/kb/README.md links " l ", which does not exist")))))
