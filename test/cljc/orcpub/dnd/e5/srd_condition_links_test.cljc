(ns orcpub.dnd.e5.srd-condition-links-test
  "Condition links in spell, item and monster text: only where the text names the condition."
  (:require [clojure.test :refer [deftest is]]
            [orcpub.dnd.e5.srd-conditions :as conditions]))

(deftest links-where-the-text-names-a-condition
  (is (= ["If it fails the saving throw, it is " [:a {:kind :condition :key :charmed} "charmed"] " by you."]
         (conditions/link-conditions "If it fails the saving throw, it is charmed by you.")))
  (is (= ["or be " [:a {:kind :condition :key :paralyzed} "paralyzed"] " for 1 minute"]
         (conditions/link-conditions "or be paralyzed for 1 minute")))
  (is (= ["gains one level of " [:a {:kind :condition :key :exhaustion} "exhaustion"]]
         (conditions/link-conditions "gains one level of exhaustion")))
  (is (= ["A " [:a {:kind :condition :key :frightened} "Frightened"] " creature flees."]
         (conditions/link-conditions "A Frightened creature flees.")))
  (is (= ["the " [:a {:kind :condition :key :invisible} "INVISIBLE"] " condition"]
         (conditions/link-conditions "the INVISIBLE condition"))
      "a capital I folds the same on every locale"))

(deftest leaves-ordinary-words-alone
  (is (= ["Other elemental creatures include azers and invisible stalkers."]
         (conditions/link-conditions "Other elemental creatures include azers and invisible stalkers.")))
  (is (= ["a poisoned needle"] (conditions/link-conditions "a poisoned needle")))
  (is (= [] (conditions/link-conditions nil))))
