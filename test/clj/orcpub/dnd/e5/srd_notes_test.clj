(ns orcpub.dnd.e5.srd-notes-test
  "Our clarification notes: each names a rule that exists, and lands after that rule's text."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [orcpub.dnd.e5.srd-rules :as rules]))

(defn served
  "The served file at `path` (a URL under resources/public), parsed, or nil when absent."
  [path]
  (some-> (io/resource (str "public" path)) slurp edn/read-string))

(deftest every-note-names-a-rule
  (let [notes (:notes (served rules/notes-path))
        anchors (set (map (juxt :section :anchor) (rules/headings (served rules/data-path))))]
    (is (= 2 (count notes)))
    (doseq [{:keys [section anchor title source]} notes]
      (testing title
        (is (anchors [section anchor]))
        (is (re-find #"^https://" (str (:url source))) "links where Wizards published the ruling")))))

(deftest notes-land-after-their-rule
  (let [data (served rules/data-path)
        note {:anchor :rolling-1-or-20 :title "t"}
        body (rules/with-notes (:body (rules/section data :attacking)) [note])
        i-note (.indexOf body [:note note])
        i-rule (some (fn [[i [tag _ _ a]]] (when (and (= :h tag) (= a :rolling-1-or-20)) i)) (map-indexed vector body))
        i-next (some (fn [[i [tag l]]] (when (and (> i i-rule) (= :h tag) (<= l 2)) i)) (map-indexed vector body))]
    (is (< i-rule i-note) "after the rule's heading")
    (is (= i-next (inc i-note)) "right before the next rule")
    (is (= 1 (count (filter #(= :note (first %)) body))))))
