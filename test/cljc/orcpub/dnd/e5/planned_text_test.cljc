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

(deftest the-returns-line-says-what-brings-it-back
  (is (= "auto-fills at level 10" (pt/returns-line archery)))
  (is (= "auto-fills if Rogue is your starting class" (pt/returns-line stealth)))
  (is (= "auto-fills if Rogue is not your starting class"
         (pt/returns-line {:name "Hit Points" :returns {:not-first-class :rogue}})))
  (is (= "auto-fills if you choose Hunter"
         (pt/returns-line {:name "Steel Will" :returns {:choice [:class :ranger] :name "Hunter"}})))
  (is (= "auto-fills if you add Rogue again"
         (pt/returns-line {:name "Expertise" :returns {:class :rogue}})))
  (is (= "auto-fills when it applies again"
         (pt/returns-line {:name "Lantern Step" :returns nil}))))

(deftest the-panel-rows-leave-out-what-the-line-says
  (let [acro {:name "Acrobatics" :returns {:first-class :rogue}}
        steel {:name "Steel Will" :returns {:class-level 7 :class :ranger}}
        shown (fn [rows] (map (juxt :label :aside) rows))]
    (is (= [[nil "auto-fills at level 10"]] (shown (pt/panel-rows [archery] true)))
        "one pick: the line names it, the row says when")
    (is (= [["Archery" "level 10"] ["Steel Will" "level 7"]]
           (shown (pt/panel-rows [archery steel] false))))
    (is (= [["Stealth" nil] ["Acrobatics" nil]] (shown (pt/panel-rows [stealth acro] true)))
        "a reason the line states is not repeated")
    (is (= [["Stealth" "auto-fills if Rogue is your starting class"]
            ["Acrobatics" "auto-fills if Rogue is your starting class"]]
           (shown (pt/panel-rows [stealth acro] false)))
        "a line that only counts leaves the reason to the rows")
    (is (= [["Archery" "auto-fills at level 10"]
            ["Stealth" "auto-fills if Rogue is your starting class"]]
           (shown (pt/panel-rows [archery stealth] true))))))

(deftest the-class-line-says-why-when-the-reasons-agree
  (let [steel {:name "Steel Will" :returns {:class-level 7 :class :ranger}}
        hunter {:name "Hunter" :returns {:class-level 3 :class :ranger}}
        acro {:name "Acrobatics" :returns {:first-class :rogue}}]
    (is (= "Planned: Steel Will (L7)" (pt/class-line [steel])))
    (is (= "2 planned at higher levels" (pt/class-line [steel hunter])))
    (is (= "2 planned if Rogue is your starting class" (pt/class-line [stealth acro])))
    (is (= "2 planned" (pt/class-line [steel stealth])))))

(deftest the-restore-notice-names-one-or-groups-by-class
  (is (= "Steel Will restored" (pt/restored [{:name "Steel Will" :class :ranger}])))
  (is (= "Rogue picks restored (2)"
         (pt/restored [{:name "Stealth" :class :rogue} {:name "Acrobatics" :class :rogue}])))
  (is (= "2 picks restored" (pt/restored [{:name "Stealth" :class :rogue} {:name "Hill Dwarf"}]))))

(deftest the-open-notice-counts-the-choices
  (is (= "1 choice doesn't apply right now" (pt/opened 1)))
  (is (= "3 choices don't apply right now" (pt/opened 3))))

(deftest only-an-optional-unlimited-section-has-a-count-that-says-nothing
  (is (pt/count-says-nothing? 0 nil []) "select any number: always complete")
  (is (not (pt/count-says-nothing? 0 3 [])) "select up to 3 says something")
  (is (not (pt/count-says-nothing? 1 1 [])) "select 1 says something")
  (is (not (pt/count-says-nothing? 2 nil [])) "select at least 2 says something")
  (is (not (pt/count-says-nothing? 0 nil [archery])) "held picks keep the line for the Planned marker"))
