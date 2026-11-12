(ns orcpub.spell-cards-test
  "The spell card front's layout, checked as measured boxes: every SRD spell fits without
   overlap, the longest content follows the card's rules, and concentration and ritual show."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.pdf.cards :as cards]
            [orcpub.pdf.draw :as draw]
            [orcpub.dnd.e5.spells :as spells])
  (:import (org.apache.pdfbox.pdmodel PDDocument)))

(defn- with-fonts
  "Calls `f` with a draw/load-fonts map on a scratch document."
  [f]
  (with-open [doc (PDDocument.)]
    (f (draw/load-fonts doc))))

(defn- card
  "A card map for `spell` as a Wizard's, DC and attack printed, merged with `extra`."
  [spell & [extra]]
  (merge {:spell spell :class-nm "Wizard" :dc 15 :attack-bonus 7 :print-dc-mod? true} extra))

(defn- of-kind
  "The elements of `layout` of `kind`."
  [layout kind]
  (filter #(= kind (:kind %)) (:elements layout)))

(def ^:private stress-spell
  "Every field at its SRD maximum: the widest name, the longest casting time, range and material,
   concentration and ritual, 9th level."
  (let [all (vals spells/spell-map)
        longest (fn [f] (apply max-key #(count (str (f %))) all))]
    {:name "Protection from Evil and Good" :level 9 :school "transmutation" :ritual true
     :casting-time (:casting-time (longest :casting-time))
     :range (:range (longest :range))
     :duration "Concentration, up to 10 minutes"
     :components {:verbal true :somatic true :material true
                  :material-component (get-in (longest #(get-in % [:components :material-component]))
                                              [:components :material-component])}
     :description (:description (longest :description))}))

(deftest every-srd-spell-lays-out-clean
  (with-fonts
    (fn [fonts]
      (doseq [spell (vals spells/spell-map)]
        (is (empty? (cards/spell-card-problems (cards/spell-card-layout fonts (card spell))))
            (:name spell))))))

(deftest the-stress-card-lays-out-clean
  (with-fonts
    (fn [fonts]
      (let [l (cards/spell-card-layout fonts (card stress-spell {:class-nm "Half-Elf (Variant) · Magic Initiate (Wizard)"
                                                                  :dc 19 :attack-bonus 11 :cast-at 2
                                                                  :uses {:n 6 :label "LONG REST"}}))
            level-line (some #(when (#{"9th Level"} (:text %)) (:y %)) (of-kind l :text))]
        (is (empty? (cards/spell-card-problems l)))
        (testing "the name wraps, which drops the level line below the ribbons: the uses fit on it"
          (is (= 6 (count (of-kind l :use))))
          (is (every? #(< (Math/abs (double (- (:cy %) (- level-line 1.7)))) 0.01) (of-kind l :use))))))))

(deftest uses-take-their-own-row-when-the-level-line-is-full
  (with-fonts
    (fn [fonts]
      (let [spell (assoc (spells/spell-map :detect-magic) :level 9 :school "transmutation")
            l (cards/spell-card-layout fonts (card spell {:uses {:n 6 :label "LONG REST"}}))
            level-line (some #(when (= "9th Level" (:text %)) (:y %)) (of-kind l :text))]
        (is (empty? (cards/spell-card-problems l)))
        (is (= 6 (count (of-kind l :use))))
        (is (every? #(> (:cy %) level-line) (of-kind l :use))
            "beside a one-line name the C and R ribbons leave too little room on the level line")))))

(deftest concentration-and-ritual-show-as-ribbons
  (with-fonts
    (fn [fonts]
      (let [ribbons #(set (map :k (of-kind (cards/spell-card-layout fonts (card (spells/spell-map %))) :ribbon)))]
        (is (= #{:conc :rit} (ribbons :detect-magic)))
        (is (= #{:conc} (ribbons :darkness)))
        (is (= #{} (ribbons :fireball)))))))
