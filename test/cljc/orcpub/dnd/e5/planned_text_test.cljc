(ns orcpub.dnd.e5.planned-text-test
  (:require [clojure.test :refer [deftest is]]
            [orcpub.dnd.e5.planned-text :as pt]))

(def ^:private archery {:name "Archery" :returns {:class-level 10 :class :fighter}})
(def ^:private stealth {:name "Stealth" :returns {:first-class :rogue}})

(deftest the-marker-names-one-and-counts-several
  (is (nil? (pt/marker [])))
  (is (= "Planned: Archery (L10)" (pt/marker [archery])))
  (is (= "Planned: Stealth (if Rogue first)" (pt/marker [stealth])))
  (is (= "Planned: Lantern Step" (pt/marker [{:name "Lantern Step" :returns nil}])))
  (is (= "2 planned" (pt/marker [archery stealth]))))

(deftest auto-fills-says-what-brings-it-back
  (is (= "Archery auto-fills at level 10." (pt/auto-fills archery)))
  (is (= "Stealth auto-fills if Rogue is your starting class." (pt/auto-fills stealth)))
  (is (= "Hit Points auto-fills if Rogue is not your starting class."
         (pt/auto-fills {:name "Hit Points" :returns {:not-first-class :rogue}})))
  (is (= "Steel Will auto-fills if you choose Hunter."
         (pt/auto-fills {:name "Steel Will" :returns {:choice [:class :ranger] :name "Hunter"}})))
  (is (= "Expertise auto-fills if you add Rogue again."
         (pt/auto-fills {:name "Expertise" :returns {:class :rogue}})))
  (is (= "Lantern Step auto-fills when it applies again."
         (pt/auto-fills {:name "Lantern Step" :returns nil}))))

(deftest a-list-line-names-it-and-says-when
  (is (= "Archery · auto-fills at level 10" (pt/list-line archery))))

(deftest the-class-line-says-why-when-the-reasons-agree
  (let [steel {:name "Steel Will" :returns {:class-level 7 :class :ranger}}
        hunter {:name "Hunter" :returns {:class-level 3 :class :ranger}}
        acro {:name "Acrobatics" :returns {:first-class :rogue}}]
    (is (= "Planned: Steel Will (L7)" (pt/class-line [steel])))
    (is (= "2 planned at higher levels" (pt/class-line [steel hunter])))
    (is (= "2 planned if Rogue is your starting class" (pt/class-line [stealth acro])))
    (is (= "2 planned" (pt/class-line [steel stealth])))))
