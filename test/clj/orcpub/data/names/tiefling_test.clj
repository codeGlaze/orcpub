(ns orcpub.data.names.tiefling-test
  (:require [clojure.test :refer :all]
            [clojure.set :as set]
            [orcpub.data.names.tiefling :as tiefling]
            [orcpub.data.names.parts :as parts]))

(deftest virtues-merged-into-given-names
  (let [male-kw (keyword "orcpub.data.names.tiefling" "male")
        female-kw (keyword "orcpub.data.names.tiefling" "female")
        male-list (set (get tiefling/tiefling-names male-kw))
        female-list (set (get tiefling/tiefling-names female-kw))
        virtues (set (concat parts/virtue-fortunate parts/virtue-ironic))]
    (is (seq (set/intersection male-list virtues)) "male names should include at least one virtue term")
    (is (seq (set/intersection female-list virtues)) "female names should include at least one virtue term")))

(deftest merge-parts-by-tone-honors-use-parts-flag
  (let [local-pre ["Ra"]
        local-post ["th"]
        result (parts/merge-parts-by-tone local-pre local-post :tone :ironic :use-parts? false)]
    (is (= (:pre result) local-pre) "when use-parts? false, pre should be unchanged")
    (is (= (:post result) local-post) "when use-parts? false, post should be unchanged")))
