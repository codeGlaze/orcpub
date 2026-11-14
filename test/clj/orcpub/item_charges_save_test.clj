(ns orcpub.item-charges-save-test
  "A custom magic item saves with its charges, how they come back, and the spells it casts. Each
   field is its own attribute in the schema, so one missing there makes the whole save fail."
  (:require [clojure.test :refer [deftest is testing]]
            [datomic.api :as d]
            [orcpub.routes :as routes]
            [orcpub.db.schema :as schema]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.units :as units5e])
  (:import [java.util UUID]))

(defn with-conn
  "Calls `f` with a connection to a fresh in-memory database holding the schema and one user,
   alice; deletes the database after. Returns what `f` returns."
  [f]
  (let [uri (str "datomic:mem:item-charges-" (UUID/randomUUID))
        conn (do (d/create-database uri) (d/connect uri))]
    (try
      @(d/transact conn schema/all-schemas)
      @(d/transact conn [{:orcpub.user/username "alice" :orcpub.user/email "alice@test.com"}])
      (f conn)
      (finally (d/delete-database uri)))))

(def wand
  "A custom wand as the item builder holds it: 7 charges, 1d6+1 back at dawn, two spells."
  {::mi/name "Wand of Testing" ::mi/type :wand ::mi/rarity :rare
   ::mi/charges 7 ::mi/charges-recharge ::units5e/dawn ::mi/charges-regain "1d6+1"
   ::mi/casts [{::mi/cast-spell :fireball ::mi/cast-level 3 ::mi/cast-cost 1
                ::mi/cast-per-level 1 ::mi/cast-max-level 9 ::mi/save-dc 15}
               {::mi/cast-spell :burning-hands ::mi/cast-level 1 ::mi/cast-cost 1}]})

(defn saved
  "Saves the builder item `item` as alice, sent as ::mi/save-item sends it (from-internal-item),
   and reads it back without ids. Returns the item, or the response or exception on a failure."
  [conn item]
  (try
    (let [{:keys [status body] :as response}
          (routes/save-item {:db (d/db conn) :conn conn :transit-params (mi/from-internal-item item) :identity {:user "alice"}})]
      (if (= 200 status)
        (-> (d/pull (d/db conn) '[* {::mi/casts [*]} {::mi/extra-damage [*]} {::mi/effects [*]} {::mi/toggles [*]}] (:db/id body))
            (dissoc :db/id ::mi/owner)
            (update ::mi/casts #(mapv (fn [c] (dissoc c :db/id)) %)))
        response))
    (catch Exception e e)))

(deftest a-custom-item-keeps-its-charges-and-casts
  (with-conn
   (fn [conn]
     (let [item (saved conn wand)]
       (testing "charges and their recharge"
         (is (= [7 ::units5e/dawn "1d6+1"]
                (when (map? item) ((juxt ::mi/charges ::mi/charges-recharge ::mi/charges-regain) item)))
             (pr-str item)))
       (testing "the spells it casts, with their prices"
         (is (= (set (::mi/casts wand)) (set (::mi/casts item)))))))))

(deftest a-rolled-start-saves-in-place-of-a-number
  (with-conn
   (fn [conn]
     (let [item (saved conn (-> wand (dissoc ::mi/charges) (assoc ::mi/charges-start "1d8+1")))]
       (is (= "1d8+1" (::mi/charges-start item)) (pr-str item))))))

(def flaming-blade
  "A custom weapon with a power of each kind, and one extra damage row left half filled."
  {::mi/name "Flaming Blade" ::mi/type :weapon ::mi/rarity :rare
   ::mi/extra-damage [{:orcpub.dnd.e5.weapons/damage-die 6 :orcpub.dnd.e5.weapons/damage-die-count 2
                       :orcpub.dnd.e5.weapons/damage-type :fire ::mi/on :crit}
                      {::mi/on :miss}]
   ::mi/effects [{::mi/effect-type :save ::mi/text "Knocked down." ::mi/save-ability :str ::mi/save-dc 15
                  ::mi/save-fail :prone ::mi/recharge ::units5e/dawn ::mi/charge-cost 1}]})

(deftest a-custom-weapon-keeps-its-powers
  (with-conn
   (fn [conn]
     (let [item (saved conn flaming-blade)
           strip (fn [rows] (mapv #(dissoc % :db/id) rows))]
       (testing "the complete extra damage row, and only it"
         (is (= [(first (::mi/extra-damage flaming-blade))] (strip (::mi/extra-damage item))) (pr-str item)))
       (testing "the effect with its save, use and cost"
         (is (= (::mi/effects flaming-blade) (strip (::mi/effects item)))))))))

(def blazing-blade
  "A custom weapon with a switch, and powers that use every kind of condition."
  {::mi/name "Blazing Blade" ::mi/type :weapon ::mi/rarity :rare
   ::mi/toggles [{::mi/toggle-id :switch-0 ::mi/toggle-name "Ablaze"}]
   ::mi/extra-damage [{:orcpub.dnd.e5.weapons/damage-die 6 :orcpub.dnd.e5.weapons/damage-die-count 2
                       :orcpub.dnd.e5.weapons/damage-type :fire ::mi/while :switch-0
                       ::mi/against #{:undead} ::mi/attack-type :melee}]
   ::mi/effects [{::mi/effect-type :condition ::mi/text "Again!" ::mi/next-attack :adv
                  ::mi/extra-attack :same ::mi/recharge ::units5e/turn}]})

(deftest a-custom-weapon-keeps-its-switches-and-conditions
  (with-conn
   (fn [conn]
     (let [item (saved conn blazing-blade)
           clean (fn [rows] (mapv #(cond-> (dissoc % :db/id) (::mi/against %) (update ::mi/against set)) rows))]
       (is (= (::mi/toggles blazing-blade) (clean (::mi/toggles item))) (pr-str item))
       (is (= (::mi/extra-damage blazing-blade) (clean (::mi/extra-damage item))))
       (is (= (::mi/effects blazing-blade) (clean (::mi/effects item))))))))

