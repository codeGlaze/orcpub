(ns orcpub.magic-item-cards-test
  "Covers the magic item card text: the line under the name, and attunement."
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.pdf :as pdf]
            [orcpub.dnd.e5.magic-items :as mi]))

(deftest attunement-reads-the-way-the-books-write-it
  (testing "an item needing none says nothing"
    (is (nil? (pdf/attunement-phrase nil nil)))
    (is (nil? (pdf/attunement-phrase [] nil))))
  (testing "anyone may attune"
    (is (= "(requires attunement)" (pdf/attunement-phrase [:any] nil))))
  (testing "one class"
    (is (= "(requires attunement by a warlock)" (pdf/attunement-phrase [:warlock] nil))))
  (testing "two are joined with or, three or more with a serial comma"
    (is (= "(requires attunement by a sorcerer or a wizard)"
           (pdf/attunement-phrase [:sorcerer :wizard] nil)))
    (is (= "(requires attunement by a bard, a cleric, or a druid)"
           (pdf/attunement-phrase [:bard :cleric :druid] nil))))
  (testing "an alignment is a creature of that alignment, not 'a good'"
    (is (= "(requires attunement by a creature of good alignment)"
           (pdf/attunement-phrase [:good] nil))))
  (testing "a spelled-out condition wins over the list"
    (is (= "(requires attunement outdoors at night)"
           (pdf/attunement-phrase [:any] "requires attunement outdoors at night")))))

(deftest the-subtitle-names-kind-rarity-and-attunement
  (testing "in that order, comma separated"
    (is (= "Weapon, very rare (requires attunement)"
           (pdf/magic-item-subtitle {::mi/type :weapon
                                     ::mi/rarity :very-rare
                                     ::mi/attunement [:any]}))))
  (testing "a subtype is parenthesised after the kind"
    (is (= "Weapon (sword), rare"
           (pdf/magic-item-subtitle {::mi/type :weapon
                                     ::mi/subtype :sword
                                     ::mi/rarity :rare}))))
  (testing "varies is spelled out, since 'varies' alone reads as a rarity name"
    (is (= "Wondrous item, rarity varies"
           (pdf/magic-item-subtitle {::mi/type :wondrous-item
                                     ::mi/rarity :varies}))))
  (testing "missing pieces are left out rather than rendered blank"
    (is (= "Potion" (pdf/magic-item-subtitle {::mi/type :potion})))
    (is (= "" (pdf/magic-item-subtitle {})))))

(deftest every-shipped-item-produces-a-subtitle
  (testing "no item in the data throws or renders nil"
    (let [subtitles (map pdf/magic-item-subtitle (vals mi/magic-item-map))]
      (is (= 805 (count subtitles)))
      (is (every? string? subtitles))
      (is (every? #(not (re-find #"null|clojure\.lang|\{" %)) subtitles)
          "no keyword or object leaked into the text"))))
