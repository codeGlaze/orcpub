(ns orcpub.features-used-save-test
  "A saved character keeps what it has used: a use under every reset period a weapon's recharge can
   name, and each carried item's spent and rolled charges. Each is its own attribute in the schema,
   so one missing there makes the whole save fail."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.alpha :as spec]
            [datomic.api :as d]
            [orcpub.routes :as routes]
            [orcpub.db.schema :as schema]
            [orcpub.entity.strict :as se]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.units :as units5e])
  (:import [java.util UUID]))

(defn with-conn
  "Calls `f` with a connection to a fresh in-memory database holding the schema and one user,
   alice; deletes the database after. Returns what `f` returns."
  [f]
  (let [uri (str "datomic:mem:features-used-" (UUID/randomUUID))
        conn (do (d/create-database uri) (d/connect uri))]
    (try
      @(d/transact conn schema/all-schemas)
      @(d/transact conn [{:orcpub.user/username "alice" :orcpub.user/email "alice@test.com"}])
      (f conn)
      (finally (d/delete-database uri)))))

(defn character-with-use
  "A level 1 fighter whose values mark the use \"Javelin of Lightning\" under `period`."
  [period]
  {::se/selections
   [{::se/key :ability-scores
     ::se/option {::se/key :standard-scores
                  ::se/map-value {::char5e/str 15 ::char5e/dex 14 ::char5e/con 13
                                  ::char5e/int 12 ::char5e/wis 10 ::char5e/cha 8}}}
    {::se/key :class
     ::se/options [{::se/key :fighter
                    ::se/selections [{::se/key :levels ::se/options [{::se/key :level-1}]}]}]}]
   ::se/values {::char5e/features-used {period #{"Javelin of Lightning"}}}
   ::se/summary {::char5e/character-name "Test"
                 ::char5e/classes [{::char5e/class-name "Fighter" ::char5e/level 1}]}})

(defn saved-uses
  "Saves `character` as alice and reads back its marked uses. Returns the features-used map, or
   the exception the save threw."
  [conn character]
  (try
    (let [id (:db/id (:body (routes/do-save-character (d/db conn) conn character {:user "alice"})))]
      (-> (d/pull (d/db conn) [{::se/values [{::char5e/features-used ['*]}]}] id)
          ::se/values ::char5e/features-used (dissoc :db/id)))
    (catch Exception e e)))

(deftest every-recharge-period-can-be-saved
  (doseq [period (sort (spec/form ::mi/recharge))]
    (testing (name period)
      (with-conn
       (fn [conn]
        (let [uses (saved-uses conn (character-with-use period))]
          (is (= {period ["Javelin of Lightning"]} (when (map? uses) (update-vals uses vec)))
              (if (instance? Exception uses) (.getMessage ^Exception uses) (pr-str uses)))))))))

(defn character-with-wands
  "A level 1 fighter carrying two Wands of Fireballs, the first with 2 charges spent, the second
   with all 7, and a Necklace of Fireballs rolled at 6 beads."
  []
  (-> (character-with-use ::units5e/long-rest)
      (update ::se/selections conj
              {::se/key :other-magic-items
               ::se/options [{::se/key :wand-of-fireballs
                              ::se/map-value {::char-equip/quantity 2 ::char-equip/equipped? true
                                              ::char-equip/charges [{::char-equip/charge-index 0 ::char-equip/charges-used 2}
                                                                    {::char-equip/charge-index 1 ::char-equip/charges-used 7}]}}
                             {::se/key :necklace-of-fireballs
                              ::se/map-value {::char-equip/quantity 1 ::char-equip/equipped? true
                                              ::char-equip/charges [{::char-equip/charge-index 0 ::char-equip/charges-max 6}]}}]})))

(deftest carried-charges-are-saved
  (with-conn
   (fn [conn]
     (let [id (try (:db/id (:body (routes/do-save-character (d/db conn) conn (character-with-wands) {:user "alice"})))
                   (catch Exception e (.getMessage e)))
           items (when (number? id)
                   (->> (d/pull (d/db conn) [{::se/selections [::se/key {::se/options [::se/key {::se/map-value ['* {::char-equip/charges ['*]}]}]}]}] id)
                        ::se/selections (some #(when (= :other-magic-items (::se/key %)) (::se/options %)))
                        (into {} (map (juxt ::se/key ::se/map-value)))))]
       (is (number? id) (str id))
       (is (= {0 2 1 7} (into {} (map (juxt ::char-equip/charge-index ::char-equip/charges-used))
                              (get-in items [:wand-of-fireballs ::char-equip/charges]))) "one record per wand")
       (is (= [6] (map ::char-equip/charges-max (get-in items [:necklace-of-fireballs ::char-equip/charges])))
           "the rolled maximum")))))
