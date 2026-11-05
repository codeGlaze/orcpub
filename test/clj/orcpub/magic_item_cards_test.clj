(ns orcpub.magic-item-cards-test
  "Covers the magic item card text: the line under the name, and attunement."
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.pdf.cards :as cards]
            [orcpub.dnd.e5.magic-items :as mi]))

(deftest attunement-reads-the-way-the-books-write-it
  (testing "an item needing none says nothing"
    (is (nil? (cards/attunement-phrase nil nil)))
    (is (nil? (cards/attunement-phrase [] nil))))
  (testing "anyone may attune"
    (is (= "(requires attunement)" (cards/attunement-phrase [:any] nil))))
  (testing "one class"
    (is (= "(requires attunement by a warlock)" (cards/attunement-phrase [:warlock] nil))))
  (testing "the article goes on the first name only, as the books set it"
    (is (= "(requires attunement by a sorcerer or wizard)"
           (cards/attunement-phrase [:sorcerer :wizard] nil)))
    (is (= "(requires attunement by a bard, cleric, or druid)"
           (cards/attunement-phrase [:bard :cleric :druid] nil))))
  (testing "an alignment is a creature of that alignment, not 'a good'"
    (is (= "(requires attunement by a creature of good alignment)"
           (cards/attunement-phrase [:good] nil))))
  (testing "a spelled-out condition wins over the list"
    (is (= "(requires attunement outdoors at night)"
           (cards/attunement-phrase [:any] "requires attunement outdoors at night")))))

(deftest the-subtitle-names-kind-and-rarity
  (testing "in that order, comma separated"
    (is (= "Weapon, very rare"
           (cards/magic-item-subtitle {::mi/type :weapon
                                     ::mi/rarity :very-rare}))))
  (testing "attunement is left to the foot of the card, not repeated here"
    (is (= "Weapon, very rare"
           (cards/magic-item-subtitle {::mi/type :weapon
                                     ::mi/rarity :very-rare
                                     ::mi/attunement [:sorcerer :warlock :wizard]}))
        "the long clause clipped this line, and the card already prints it below"))
  (testing "a subtype is parenthesised after the kind"
    (is (= "Weapon (sword), rare"
           (cards/magic-item-subtitle {::mi/type :weapon
                                     ::mi/subtype :sword
                                     ::mi/rarity :rare}))))
  (testing "varies is spelled out, since 'varies' alone reads as a rarity name"
    (is (= "Wondrous item, rarity varies"
           (cards/magic-item-subtitle {::mi/type :wondrous-item
                                     ::mi/rarity :varies}))))
  (testing "missing pieces are left out rather than rendered blank"
    (is (= "Potion" (cards/magic-item-subtitle {::mi/type :potion})))
    (is (= "" (cards/magic-item-subtitle {})))))

(deftest every-shipped-item-produces-a-subtitle
  (testing "no item in the data throws or renders nil"
    (let [subtitles (map cards/magic-item-subtitle (vals mi/magic-item-map))]
      (is (= 805 (count subtitles)))
      (is (every? string? subtitles))
      (is (every? #(not (re-find #"null|clojure\.lang|\{" %)) subtitles)
          "no keyword or object leaked into the text"))))

(deftest charges-are-read-off-the-item-text
  (testing "a plain count"
    (is (= 3 (cards/item-charges "This gem has 3 charges. As an action...")))
    (is (= 7 (cards/item-charges "The staff has 7 charges."))))
  (testing "a die expression takes its maximum, so the best roll still has a circle"
    (is (= 9 (cards/item-charges "The sword has 1d8 + 1 charges.")))
    (is (= 8 (cards/item-charges "It has 1d8 charges."))))
  (testing "line breaks in the description do not hide the number"
    (is (= 4 (cards/item-charges "The wand\nhas 4 charges and regains them at dawn."))))
  (testing "nothing to track draws nothing"
    (is (nil? (cards/item-charges nil)))
    (is (nil? (cards/item-charges "You gain a +2 bonus to attack rolls.")))
    (is (nil? (cards/item-charges "It has 3 charge levels"))
        "'charges' must be the word, not a prefix"))
  (testing "a big pool is still read; the card writes it rather than ticking it"
    (is (= 50 (cards/item-charges "The staff has 50 charges.")))
    (is (= 12 (cards/item-charges "It has 12 charges."))))
  (testing "past 99 is parse noise, not a charge pool"
    (is (nil? (cards/item-charges "It has 500 charges.")))))

(deftest charge-parsing-survives-every-shipped-item
  (testing "no description throws, and every count is a plausible pool"
    (let [counts (keep #(cards/item-charges (::mi/description %)) (vals mi/magic-item-map))]
      (is (seq counts) "some items do have charges")
      (is (every? #(<= 1 % 99) counts))
      (is (= 55 (count counts))
          "the five staves and cubes with big pools are read, not skipped")))
  (testing "'charged with magic' is a turn of phrase, not a charge pool"
    (let [tomes (->> (vals mi/magic-item-map)
                     (filter #(re-find #"words are charged with magic"
                                       (str (::mi/description %)))))]
      (is (seq tomes))
      (is (every? #(nil? (cards/item-charges (::mi/description %))) tomes)))))
