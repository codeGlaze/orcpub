(ns orcpub.dnd.e5.srd-rules-test
  "The 2014 rules file the Rules pages read, every link in it and in the conditions file, and
   the lookups the Orcacle and the link previews use."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.srd-conditions :as conditions]
            [orcpub.dnd.e5.srd-rules :as rules]))

(defn served
  "The served SRD file at `path` (a URL under resources/public), parsed, or nil when absent."
  [path]
  (some-> (io/resource (str "public" path)) slurp edn/read-string))

(defn links
  "Every link's attributes in `data`'s bodies, as {:kind ...} maps."
  [data]
  (for [x (tree-seq coll? seq (concat (:sections data) (:conditions data)))
        :when (and (vector? x) (= :a (first x)))]
    (second x)))

(defn broken-links
  "The links in `data` that point at nothing: no such spell, condition or rule anchor.

   Args: `data`, a parsed SRD file; `rules-data`, the parsed rules file, for rule anchors.
   Returns: a seq of the broken links' attributes."
  [data rules-data]
  (let [anchors (set (map (juxt :section :anchor) (rules/headings rules-data)))]
    (remove (fn [{:keys [kind key section anchor]}]
              (case kind
                :spell (spells/spell-map key)
                :condition (conditions/condition-keys key)
                :rule (anchors [section anchor])
                false))
            (links data))))

(deftest the-rules-file-is-served-and-complete
  (let [data (served rules/data-path)]
    (is (some? data) "resources/public/srd/2014/rules.edn is on the classpath")
    (is (= 43 (count (:sections data))))
    (testing "every section is in exactly one group"
      (is (= (sort (map :key (:sections data)))
             (sort (mapcat :sections (:groups data))))))
    (is (every? :pages (:sections data)) "every section cites its SRD pages")
    (testing "the rules open5e lacked are there"
      (is (rules/find-rule data "Death Saving Throws"))
      (is (rules/find-rule data "Short Rest"))
      (is (rules/find-rule data "Squeezing into a Smaller Space")))
    (is (re-find #"System Reference Document 5\.1" (str (:srd/attribution data))))))

(deftest every-link-points-at-something
  (let [rules-data (served rules/data-path)
        conditions-data (served conditions/data-path)]
    (is (< 150 (count (links rules-data))) "the rules carry their links")
    (is (= [] (broken-links rules-data rules-data)))
    (is (= [] (broken-links conditions-data rules-data)))))

(deftest lookups
  (let [data (served rules/data-path)]
    (is (= {:section :damage-and-healing :anchor :dropping-to-0-hit-points}
           (select-keys (rules/find-rule data "  dropping to 0 hit points ") [:section :anchor])))
    (is (nil? (rules/find-rule data "dropping")))
    (is (= :interacting-with-objects-around-you
           (:anchor (rules/find-rule data "INTERACTING WITH OBJECTS AROUND YOU")))
        "a capital I folds the same on every locale")
    (is (some #(= "Grappling" (:name %)) (rules/matching data "grapp")))
    (is (empty? (rules/matching data "gr")) "under three characters matches nothing")
    (testing "a rule's blocks stop at the next rule and keep its own subheadings"
      (let [blocks (rules/rule-blocks data :damage-and-healing :dropping-to-0-hit-points)
            heads (set (keep (fn [[tag _ text]] (when (= :h tag) text)) blocks))]
        (is (contains? heads "Death Saving Throws"))
        (is (not (contains? heads "Knocking a Creature Out")))))
    (is (seq (rules/rule-blocks data :movement nil)) "a section's opening text")))
