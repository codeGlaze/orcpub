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
        (-> (d/pull (d/db conn) '[* {::mi/casts [*]}] (:db/id body))
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
