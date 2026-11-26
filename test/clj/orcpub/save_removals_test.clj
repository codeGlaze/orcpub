(ns orcpub.save-removals-test
  "A save stores what the player removed: a value or one item of a list left out of a record the
   save keeps is removed in storage, under the version check. Saves go through
   `routes/do-save-character` after the client's own from-strict / to-strict round trip, as a load
   and then a save do. tracked-issues.md, #295."
  (:require [clojure.data :as data]
            [clojure.test :refer [deftest is testing]]
            [datomic.api :as d]
            [orcpub.routes :as routes]
            [orcpub.features-used-save-test :as fu]
            [orcpub.entity.strict :as se]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.units :as units5e]))

(defn- save!
  "Saves strict `c` as alice; returns the response."
  [conn c]
  (routes/do-save-character (d/db conn) conn c {:user "alice"}))

(defn- loaded
  "The stored character `id` as a client holds it after a load, back in strict form for a save,
   with the list summary the client rebuilds and sends on every save."
  [conn id]
  (let [stored (d/pull (d/db conn) '[*] id)]
    (-> stored char5e/from-strict char5e/to-strict
        (assoc :db/id id ::se/summary (dissoc (::se/summary stored) :db/id)))))

(defn- stored-values
  "The values record of character `id` as stored."
  [conn id]
  (::se/values (d/pull (d/db conn) '[*] id)))

(def played
  "A level 1 fighter mid-session: 3 HP, 300 XP, used features, two used first-level slots, two
   prepared spells, a portrait and notes, and two Wands of Fireballs with spent charges."
  (assoc (fu/character-with-wands)
         ::se/values {::char5e/current-hit-points 3
                      ::char5e/xps 300
                      ::char5e/notes "Owes the ferryman"
                      ::char5e/image-url "https://example.com/bree.png"
                      ::char5e/features-used {::units5e/long-rest #{"Second Wind" "Action Surge"}
                                              ::units5e/rest #{"Channel Divinity"}}
                      ::spells5e/slots-used {::spells5e/slots-1 #{0 1}}
                      ::char5e/prepared-spells-by-class [{::char5e/class-name "Wizard"
                                                          ::char5e/prepared-spells #{:shield :sleep}}]}))

(defn- saved-id
  "Saves `played` as a new character for alice; returns its id."
  [conn]
  (:db/id (:body (save! conn played))))

(defn- edit-values
  "The loaded copy of `id` with `f` applied to its values."
  [conn id f]
  (update (loaded conn id) ::se/values f))

(deftest removed-values-are-removed-in-storage
  (fu/with-conn
   (fn [conn]
     (let [id (saved-id conn)
           status (:status (save! conn (edit-values conn id
                                                    #(-> %
                                                         (dissoc ::char5e/current-hit-points ::char5e/xps)
                                                         (update-in [::spells5e/slots-used ::spells5e/slots-1]
                                                                    (fn [s] (vec (remove #{1} s))))))))
           after (stored-values conn id)]
       (is (= 200 status))
       (testing "HP left out (a long rest) is removed, so the sheet shows max"
         (is (nil? (::char5e/current-hit-points after))))
       (testing "XP left out is removed"
         (is (nil? (::char5e/xps after))))
       (testing "one unticked slot is removed, the other kept"
         (is (= #{0} (set (get-in after [::spells5e/slots-used ::spells5e/slots-1])))))))))

(deftest removals-that-already-worked-still-do
  (fu/with-conn
   (fn [conn]
     (let [id (saved-id conn)
           _ (save! conn (edit-values conn id
                                      #(-> %
                                           (update ::char5e/features-used
                                                   (fn [fu] (-> fu (dissoc ::units5e/rest)
                                                                (update ::units5e/long-rest (fn [s] (vec (remove #{"Action Surge"} s)))))))
                                           (update ::char5e/prepared-spells-by-class
                                                   (fn [cs] (mapv (fn [c] (update c ::char5e/prepared-spells (fn [s] (vec (remove #{:sleep} s))))) cs)))
                                           (dissoc ::spells5e/slots-used))))
           after (stored-values conn id)]
       (is (nil? (get-in after [::char5e/features-used ::units5e/rest])) "a rest group")
       (is (= #{"Second Wind"} (set (get-in after [::char5e/features-used ::units5e/long-rest]))) "one feature")
       (is (= #{:shield} (set (mapcat ::char5e/prepared-spells (::char5e/prepared-spells-by-class after)))) "one spell")
       (is (nil? (::spells5e/slots-used after)) "all slots, as a long rest clears them")))))

(deftest an-unrelated-save-keeps-everything-else
  (fu/with-conn
   (fn [conn]
     (let [id (saved-id conn)
           before (d/pull (d/db conn) '[*] id)
           _ (save! conn (edit-values conn id #(assoc % ::char5e/notes "Paid the ferryman")))
           after (d/pull (d/db conn) '[*] id)
           strip (fn strip [x] (cond (map? x) (into {} (keep (fn [[k v]] (when-not (#{:db/id ::se/version} k) [k (strip v)]))) x)
                                     (sequential? x) (set (map strip x))
                                     :else x))
           expected (assoc-in (strip before) [::se/values ::char5e/notes] "Paid the ferryman")]
       (is (= expected (strip after))
           (str "only the notes changed; lost, then gained: " (pr-str (take 2 (data/diff expected (strip after))))))))))

(deftest a-rest-then-a-level-up-leaves-hp-at-max
  (fu/with-conn
   (fn [conn]
     (let [id (saved-id conn)
           _ (save! conn (edit-values conn id #(dissoc % ::char5e/current-hit-points)))
           levelled (update (loaded conn id) ::se/selections
                            (fn [sels] (mapv #(if (= :class (::se/key %))
                                                (assoc-in % [::se/options 0 ::se/selections 0 ::se/options]
                                                          [{::se/key :level-1} {::se/key :level-2}])
                                                %)
                                             sels)))]
       (is (= 200 (:status (save! conn levelled))))
       (is (nil? (::char5e/current-hit-points (stored-values conn id)))
           "no HP stored, so the sheet shows the new maximum")))))

(deftest a-stale-copy-removes-nothing
  (fu/with-conn
   (fn [conn]
     (let [id (saved-id conn)
           old-copy (loaded conn id)
           _ (save! conn (edit-values conn id #(assoc % ::char5e/notes "Newer")))
           {:keys [status]} (save! conn (update old-copy ::se/values dissoc ::char5e/current-hit-points ::char5e/xps))
           after (stored-values conn id)]
       (is (= 409 status))
       (is (= 3 (::char5e/current-hit-points after)))
       (is (= 300 (::char5e/xps after)))
       (is (= "Newer" (::char5e/notes after)))))))
