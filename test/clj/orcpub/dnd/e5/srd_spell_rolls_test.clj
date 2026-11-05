(ns orcpub.dnd.e5.srd-spell-rolls-test
  "The SRD spells' rolling data in resources/public/srd/2014/spell-rolls.edn names only spells the
   app has, in the shape its generator documents, and every dice string in it is in that spell's
   own text in the app."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [orcpub.dnd.e5.spells :as spells]))

(defn rolls-file
  "The served 2014 spell rolling data, parsed, or nil when absent."
  []
  (some-> (io/resource "public/srd/2014/spell-rolls.edn") slurp edn/read-string))

(def damage-types
  "The SRD's thirteen damage types."
  #{:acid :bludgeoning :cold :fire :force :lightning :necrotic :piercing :poison :psychic
    :radiant :slashing :thunder})

(def known-text-gaps
  "Spells whose app text lacks a value the SRD has, so the text check skips them. Branding
  Smite's text is a PHB summary without its At Higher Levels line (srd-improvements.md,
  \"Spell rolling data\")."
  #{:branding-smite})

(defn parts
  "Every damage, healing and temporary hit point part of one spell's record."
  [rec]
  (concat (:damage rec) (keep rec [:heal :temp-hp :per-slot])))

(defn squash
  "`t` without spaces, so \"1d4 + 1\" meets \"1d4+1\"."
  [t]
  (s/replace (or t "") #"\s" ""))

(deftest every-spell-is-one-the-app-has
  (let [rolls (rolls-file)]
    (is (< 150 (count rolls)) "the file is there and full")
    (is (empty? (remove spells/spell-map (keys rolls))))))

(deftest records-have-the-documented-shape
  (doseq [[k rec] (rolls-file)]
    (testing (name k)
      (is (every? #{:attack :save :save-dc :save-half? :area :damage :heal :temp-hp :per-slot :count}
                  (keys rec)))
      (when (:attack rec) (is (#{:melee :ranged} (:attack rec))))
      (when (:save rec) (is (#{:str :dex :con :int :wis :cha} (:save rec))))
      (when (:area rec) (is (#{:sphere :cone :cube :cylinder :line} (get-in rec [:area :shape]))))
      (doseq [p (parts rec)]
        (is (re-matches #"\d+(d\d+)?( \+ \d+)?" (:dice p)) (:dice p))
        (is (every? damage-types (keep :type [p])))
        (is (every? damage-types (:types p)))))))

(deftest every-dice-string-is-in-the-spells-text
  (doseq [[k rec] (rolls-file)
          :when (not (known-text-gaps k))
          p (parts rec)
          d (keep p [:dice :per-slot])]
    (is (s/includes? (squash (:description (spells/spell-map k))) (squash d))
        (str (name k) ": " d))))
