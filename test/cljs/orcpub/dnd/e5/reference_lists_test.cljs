(ns orcpub.dnd.e5.reference-lists-test
  "The reference list rows (orcpub.dnd.e5.views.reference) drawn as hiccup against a set app-db:
   item rows offer Edit and Delete only to their owner, item rows expand by key, and the source
   tags cover a shared character's sources."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.views.reference :as reference]))

(defn- texts
  "Every string in the hiccup `h`, nested vectors and seqs included, as a set."
  [h]
  (set (filter string? (tree-seq coll? seq h))))

(deftest item-rows-offer-edit-only-to-their-owner
  (testing "logged out, a built-in item (no owner) offers no Edit or Delete"
    ;; opened by key and by name, so the row is open whichever the code expands by
    (reset! app-db {:expanded-items {:longsword true "Longsword" true}})
    (let [t (texts (reference/item-list-item {:key :longsword :name "Longsword"}))]
      (is (contains? t "view") "the row is open")
      (is (not (contains? t "edit")))
      (is (not (contains? t "delete")))))
  (testing "its owner, logged in, gets both"
    (reset! app-db {:expanded-items {7 true "Bob's Sword" true}
                    :user-data {:user-data {:username "bob"}}})
    (let [t (texts (reference/item-list-item {:db/id 7 :name "Bob's Sword" ::mi/owner "bob"}))]
      (is (contains? t "edit"))
      (is (contains? t "delete")))))

(deftest same-named-items-open-one-at-a-time
  (reset! app-db {:expanded-items {:sword-a true}})
  (is (contains? (texts (reference/item-list-item {:key :sword-a :name "Sword"})) "view"))
  (is (not (contains? (texts (reference/item-list-item {:key :sword-b :name "Sword"})) "view"))
      "the other Sword stays closed"))

(deftest source-tags-cover-shared-sources
  (reset! app-db {:plugins {"Local Source" {:abbreviation "LOC"}}
                  :shared-plugins {"Shared Source" {}}})
  (rf/clear-subscription-cache!)
  (let [tags @(rf/subscribe [::e5/source-tags])]
    (is (= "LOC" (get tags "Local Source")))
    (is (string? (get tags "Shared Source")) "a shared character's source gets a tag too")))
