;; Saving from the item builder sends the whole item back, and update-entity deletes every nested
;; entity (a modifier, a weapon's range) the save did not send. These pin that an edit loses
;; nothing the builder can show, and that removing a modifier in the builder does delete it.
(ns orcpub.routes.item-save-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.walk :as walk]
            [datomic.api :as d]
            [orcpub.routes :as routes]
            [orcpub.db.schema :as schema]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.weapons :as weapon5e])
  (:import [java.util UUID]))

(defmacro with-conn [conn-binding & body]
  `(let [uri# (str "datomic:mem:item-save-test-" (UUID/randomUUID))
         ~conn-binding (do
                         (d/create-database uri#)
                         (d/connect uri#))]
     (try ~@body
          (finally (d/delete-database uri#)))))

(defn- setup! [conn]
  @(d/transact conn schema/all-schemas)
  @(d/transact conn [{:orcpub.user/username "alice" :orcpub.user/email "alice@test.com"}]))

(defn- save! [conn item]
  (let [{:keys [status body]} (routes/save-item {:conn conn :db (d/db conn)
                                                 :identity {:user "alice"} :transit-params item})]
    (is (= 200 status) (pr-str body))
    body))

(defn- edit-and-save!
  "What the builder does: ::mi/edit-custom-item loads the item through to-internal-item, and
   ::mi/save-item sends from-internal-item of the builder's copy."
  [conn saved change]
  (save! conn (-> saved mi5e/to-internal-item change mi5e/from-internal-item)))

(defn- as-the-builder-shows [item]
  (walk/postwalk #(if (map? %) (dissoc % :db/id) %) (mi5e/to-internal-item item)))

(def ^:private every-modifier-kind
  {:damage-resistance  {:fire true :cold true}
   :condition-immunity {:frightened true}
   :ability            {:str {:value 2 :type :increases-by}
                        :con {:value 19 :type :becomes-at-least}}
   :save               {:dex {:value 1}}
   :speed              {:type :increases-by :value 10}
   :flying-speed       {:type :equals-walking-speed}
   :swimming-speed     {:type :becomes-at-least :value 30}
   :climbing-speed     {:type :increases-by :value 5}})

(deftest saving-an-unchanged-item-keeps-everything
  (with-conn conn
    (setup! conn)
    (testing "a wondrous item with every kind of modifier the builder writes"
      (let [saved   (save! conn {::mi5e/name "Belt of Everything" ::mi5e/type :wondrous-item
                                 ::mi5e/rarity :very-rare ::mi5e/description "Every modifier kind."
                                 ::mi5e/attunement [:any] ::mi5e/magical-ac-bonus 1
                                 ::mi5e/modifiers (mi5e/from-internal-modifiers every-modifier-kind)})
            resaved (edit-and-save! conn saved identity)]
        (is (= (:db/id saved) (:db/id resaved)) "the second save updated the item")
        (is (= every-modifier-kind (::mi5e/internal-modifiers (as-the-builder-shows resaved))))
        (is (= (as-the-builder-shows saved) (as-the-builder-shows resaved)))))
    (testing "a weapon, whose range and versatile damage are nested entities"
      (let [saved   (save! conn {::mi5e/name "Spear of Reach" ::mi5e/type :weapon ::mi5e/rarity :rare
                                 ::mi5e/subtypes [:spear]
                                 ::mi5e/magical-attack-bonus 1 ::mi5e/magical-damage-bonus 1
                                 ::weapon5e/type :simple ::weapon5e/damage-type :piercing
                                 ::weapon5e/damage-die 6 ::weapon5e/damage-die-count 1
                                 ::weapon5e/thrown? true
                                 ::weapon5e/range {::weapon5e/min 20 ::weapon5e/max 60}
                                 ::weapon5e/versatile {::weapon5e/damage-die 8
                                                       ::weapon5e/damage-die-count 1}})
            resaved (edit-and-save! conn saved identity)]
        (is (= {::weapon5e/min 20 ::weapon5e/max 60} (dissoc (::weapon5e/range resaved) :db/id)))
        (is (= (as-the-builder-shows saved) (as-the-builder-shows resaved)))))))

(deftest removing-a-modifier-in-the-builder-deletes-it
  (with-conn conn
    (setup! conn)
    (let [saved   (save! conn {::mi5e/name "Ring of Two" ::mi5e/type :ring ::mi5e/rarity :rare
                               ::mi5e/modifiers (mi5e/from-internal-modifiers
                                                 {:save  {:dex {:value 1}}
                                                  :speed {:type :increases-by :value 10}})})
          resaved (edit-and-save! conn saved #(update % ::mi5e/internal-modifiers dissoc :save))]
      (is (= {:speed {:type :increases-by :value 10}}
             (::mi5e/internal-modifiers (as-the-builder-shows resaved)))))))
