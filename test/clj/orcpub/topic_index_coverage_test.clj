(ns orcpub.topic-index-coverage-test
  "The KB is the group memory, so a document that no index mentions is a document nobody finds.

   This does NOT check the generated index is byte-current — headings change constantly and an
   exact-match gate would cry wolf until someone deleted it. It checks the thing that actually
   breaks memory: a document exists and neither index knows about it.

   Regenerate:  lein with-profile +tools run -m orcpub.topic-index"
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defn- kb-docs []
  (->> (file-seq (io/file "docs/kb"))
       (filter #(.isFile %))
       (map #(.getName %))
       (filter #(str/ends-with? % ".md"))
       (remove #{"README.md" "topic-index.md"})
       sort))

(deftest every-kb-document-is-reachable
  (let [readme (slurp (io/file "docs/kb/README.md"))
        topics (slurp (io/file "docs/kb/topic-index.md"))]
    (doseq [d (kb-docs)]
      (testing d
        (is (str/includes? readme (str "(" d ")"))
            (str d " is not linked from docs/kb/README.md — an unlinked document is one nobody finds"))
        (is (str/includes? topics (str "## " d))
            (str d " is missing from docs/kb/topic-index.md — regenerate it: "
                 "lein with-profile +tools run -m orcpub.topic-index"))))))

(deftest the-index-does-not-point-at-documents-that-are-gone
  (let [present (set (kb-docs))
        linked  (->> (re-seq #"\]\(([a-z0-9._-]+\.md)\)" (slurp (io/file "docs/kb/README.md")))
                     (map second)
                     (remove #{"README.md" "topic-index.md"})
                     set)]
    (doseq [l (sort linked)]
      (is (contains? present l)
          (str "docs/kb/README.md links " l ", which does not exist")))))
