(ns orcpub.dnd.e5.srd-data-shape-test
  "Every key on a shipped SRD entry is one its readers know. A misnamed key is not an
   error anywhere else: the reader skips it and the value silently never shows."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.dnd.e5.monsters :as monsters]
            [orcpub.dnd.e5.equipment :as equipment]))

(def monster-keys
  "Keys a monster entry may carry: what `monster-component` reads, the six ability
   scores, the generated `:key`, and `:reactions`, which is valid data the stat block
   does not yet show."
  #{:key :name :description :size :type :subtypes :hit-points :alignment :armor-class
    :armor-notes :speed :saving-throws :skills :damage-vulnerabilities
    :damage-resistances :damage-immunities :condition-immunities :senses :languages
    :challenge :traits :actions :legendary-actions :reactions :source :page
    :str :dex :con :int :wis :cha})

(def equipment-keys
  "Keys an adventuring gear, tool or pack entry may carry. Ammunition is listed with the
   gear and keeps its weapon type."
  #{:key :name :cost :weight :sell-qty :sell-container :icon :items :type :speed
    :carrying-capacity :orcpub.dnd.e5.weapons/type})

(defn unknown-keys
  "Maps each entry's name to the keys it carries outside `allowed`.

   Args: `entries`, a seq of maps with `:name`; `allowed`, a set of keys.
   Returns: a map of name to the sorted unknown keys, for entries that have any."
  [entries allowed]
  (into (sorted-map)
        (keep (fn [e]
                (let [bad (remove allowed (keys e))]
                  (when (seq bad) [(:name e) (sort bad)]))))
        entries))

(deftest monsters-carry-only-known-keys
  (testing "a skill or trait outside :skills / :traits never reaches the stat block"
    (is (= {} (unknown-keys monsters/monsters monster-keys)))))

(deftest equipment-carries-only-known-keys
  (is (= {} (unknown-keys equipment/equipment equipment-keys))))
