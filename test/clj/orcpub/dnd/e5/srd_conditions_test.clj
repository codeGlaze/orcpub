(ns orcpub.dnd.e5.srd-conditions-test
  "The 2014 conditions file the conditions pages and the Orcacle read, the lookups over it, and
   monster reactions, which the stat block now shows."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [orcpub.dnd.e5.monsters :as monsters]
            [orcpub.dnd.e5.srd-conditions :as conditions]))

(defn conditions-file
  "The served 2014 conditions file, parsed.

   Returns: the file's map, or nil when the file is absent."
  []
  (some-> (io/resource (str "public" conditions/data-path)) slurp edn/read-string))

(deftest the-file-is-served-and-complete
  (let [data (conditions-file)]
    (is (some? data) "resources/public/srd/2014/conditions.edn is on the classpath")
    (is (= 15 (count (:conditions data))))
    (is (= conditions/condition-keys (set (keys (conditions/by-key data))))
        "the stat block's link list matches the file")
    (is (every? #(and (keyword? (:key %)) (string? (:name %)) (#{358 359} (:page %)) (seq (:body %)))
                (:conditions data)))
    (testing "CC BY 4.0 needs the attribution shown with the text"
      (is (re-find #"System Reference Document 5\.1" (str (:srd/attribution data)))))))

(deftest lookups
  (let [data (conditions-file)]
    (is (= :blinded (:key (conditions/find-condition data "  Blinded "))))
    (is (nil? (conditions/find-condition data "blind")))
    (testing "a capital I folds the same on every locale (a Turkish JVM makes it a dotless ı)"
      (is (= :invisible (:key (conditions/find-condition data "Invisible"))))
      (is (= [{:text "Incapacitated" :key :incapacitated}]
             (conditions/immunity-parts "Incapacitated" conditions/condition-keys))))
    (is (= ["Poisoned"] (map :name (conditions/matching data "poi"))))
    (is (empty? (conditions/matching data "po")) "under three characters matches nothing")
    (is (= [{:text "charmed" :key :charmed} {:text "Exhaustion" :key :exhaustion}
            {:text "sleep" :key nil}]
           (conditions/immunity-parts "charmed, Exhaustion,sleep"
                                      (set (keys (conditions/by-key data))))))))

(deftest monster-reactions
  (is (= ["Parry"] (map :name (monsters/reactions (monsters/monster-map :marilith)))))
  (is (= ["Riposte"] (map :name (monsters/reactions {:traits [{:name "Bite" :type :action}
                                                                {:name "Riposte" :type :reaction}]})))
      "a builder trait typed :reaction counts")
  (is (empty? (monsters/reactions (monsters/monster-map :goblin)))))
