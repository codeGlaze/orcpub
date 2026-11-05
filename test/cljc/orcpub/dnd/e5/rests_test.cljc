(ns orcpub.dnd.e5.rests-test
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.rests :as rests]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.units :as units]))

(defn dice-fn
  "A die fn that returns these numbers in order, whatever the sides."
  [& results]
  (let [left (atom results)]
    (fn [_] (let [r (first @left)] (swap! left rest) r))))

(defn item
  "The SRD magic item with this name."
  [n]
  (some #(when (= n (::mi/name %)) %) mi/magic-items))

(def items-by-key
  "The test character's items by key."
  {:wand-of-fireballs (item "Wand of Fireballs") :robe-of-stars (item "Robe of Stars")
   :gem-of-brightness (item "Gem of Brightness")})

(defn used
  "The charges records for `pairs` of [position spent]."
  [& pairs]
  (mapv (fn [[i n]] {::char-equip/charge-index i ::char-equip/charges-used n}) (partition 2 pairs)))

(def character
  "Two wands (2 and 7 spent), a robe of stars (4 spent), a once-per-day and a once-per-dawn use
  marked, a long-rest use, spent slots and lost hit points."
  {::entity/options
   {:other-magic-items [{::entity/key :wand-of-fireballs
                         ::entity/value {::char-equip/quantity 2 ::char-equip/charges (used 0 2 1 7)}}
                        {::entity/key :robe-of-stars
                         ::entity/value {::char-equip/quantity 1 ::char-equip/charges (used 0 4)}}]}
   ::entity/values {::char5e/features-used {::units/day #{"Dragon Scale Mail"} ::units/dawn #{"Javelin"}
                                            ::units/long-rest #{"Second Wind"}}
                    ::spells/slots-used {:slot-1 #{0}}
                    ::char5e/current-hit-points 3}})

(def items (rests/carried character items-by-key))

(defn spent-after
  "{item-key {position spent}} in a character."
  [ch]
  (into {} (for [{:keys [item-key entry]} (rests/carried ch items-by-key)]
             [item-key (into {} (map (juxt ::char-equip/charge-index ::char-equip/charges-used)) (::char-equip/charges entry))])))

(deftest a-long-rest-restores-the-rest-but-not-dawn
  (let [{ch :character rec :recharged} (rests/apply-actions character items {:rest :long} {} (dice-fn 1))]
    (is (= {::units/day #{"Dragon Scale Mail"} ::units/dawn #{"Javelin"}}
           (get-in ch [::entity/values ::char5e/features-used])))
    (is (nil? (get-in ch [::entity/values ::spells/slots-used])) "slots back")
    (is (nil? (get-in ch [::entity/values ::char5e/current-hit-points])) "hit points back")
    (is (= [] rec) "no item recharges on a rest")
    (is (= {:wand-of-fireballs {0 2 1 7} :robe-of-stars {0 4}} (spent-after ch)))))

(deftest dawn-brings-back-dawn-things-and-day-uses
  (let [{ch :character rec :recharged} (rests/apply-actions character items {:times #{:dawn}} {} (dice-fn 3 6))]
    (is (= {::units/long-rest #{"Second Wind"}} (get-in ch [::entity/values ::char5e/features-used]))
        "dawn and day uses cleared, the long-rest use kept")
    (is (= [["Wand of Fireballs" 0 4 "1d6+1"] ["Wand of Fireballs" 1 7 "1d6+1"]]
           (map (juxt :name :i :amount :dice) rec)) "each wand rolls its own 1d6+1")
    (is (= {:wand-of-fireballs {0 0 1 0} :robe-of-stars {0 4}} (spent-after ch)) "never past the maximum")
    (is (some? (get-in ch [::entity/values ::spells/slots-used])) "dawn is not a rest")))

(deftest a-typed-amount-replaces-the-roll
  (let [{ch :character rec :recharged} (rests/apply-actions character items {:times #{:dusk}}
                                                            {[:robe-of-stars 0] 2} (dice-fn 6))]
    (is (= [["Robe of Stars" 2 nil]] (map (juxt :name :amount :dice) rec)))
    (is (= {0 2} (:robe-of-stars (spent-after ch))))))

(deftest a-rest-with-dawn-does-both
  (let [{ch :character} (rests/apply-actions character items {:rest :long :times #{:dawn :dusk}} {} (dice-fn 6 6 6))]
    (is (empty? (get-in ch [::entity/values ::char5e/features-used])))
    (is (= {:wand-of-fireballs {0 0 1 0} :robe-of-stars {0 0}} (spent-after ch)))))

(deftest the-question-is-asked-only-when-it-matters
  (is (rests/ask-time-of-day? character items {:rest :short}))
  (is (not (rests/ask-time-of-day? character items {:rest :short :times #{:dawn}})) "a time was chosen")
  (is (not (rests/ask-time-of-day? character items {:times #{:dawn}})) "no rest: Now")
  (let [fresh {::entity/options {:other-magic-items [{::entity/key :wand-of-fireballs ::entity/value 1}]}}]
    (is (not (rests/ask-time-of-day? fresh (rests/carried fresh items-by-key) {:rest :long}))
        "nothing spent: nothing to ask about"))
  (let [plain {::entity/options {:other-magic-items [{::entity/key :gem-of-brightness ::entity/value 1}]}}]
    (is (not (rests/has-time-of-day? plain (rests/carried plain items-by-key))) "charges that never come back")))

(deftest the-hint-counts-what-would-come-back
  (is (= {:items ["Wand of Fireballs"] :uses 2} (rests/would-recharge character items :dawn)))
  (is (= {:items ["Robe of Stars"] :uses 0} (rests/would-recharge character items :dusk))))
