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
      "a capital I folds the same on every locale")
  (is (= ["The creature is " [:a {:kind :condition :key :blinded} "blinded"] " and "
          [:a {:kind :condition :key :deafened} "deafened"] "."]
         (conditions/link-conditions "The creature is blinded and deafened."))
      "a condition continuing a named one is linked too")
  (is (= ["it is " [:a {:kind :condition :key :charmed} "charmed"] ", "
          [:a {:kind :condition :key :frightened} "frightened"] ", or "
          [:a {:kind :condition :key :poisoned} "poisoned"]]
         (conditions/link-conditions "it is charmed, frightened, or poisoned")))
  (is (= ["is " [:a {:kind :condition :key :blinded} "blinded"] ", "
          [:a {:kind :condition :key :poisoned} "poisoned"] " creature"]
         (conditions/link-conditions "is blinded, poisoned creature"))
      "a word matched two ways is linked once")
  (is (= ["is " [:a {:kind :condition :key :blinded} "blinded"] " and the other"]
         (conditions/link-conditions "is blinded and the other"))))

(deftest leaves-ordinary-words-alone
  (is (= ["Other elemental creatures include azers and invisible stalkers."]
         (conditions/link-conditions "Other elemental creatures include azers and invisible stalkers.")))
  (is (= ["a poisoned needle"] (conditions/link-conditions "a poisoned needle")))
  (is (= [] (conditions/link-conditions nil))))
