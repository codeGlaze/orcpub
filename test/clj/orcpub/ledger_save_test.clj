(ns orcpub.ledger-save-test
  "The character data page saves through the app's own save route (`routes/do-save-character`):
   a removal there retracts exactly the removed pick and keeps everything else."
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is]]
            [datomic.api :as d]
            [orcpub.db.schema :as schema]
            [orcpub.entity :as entity]
            [orcpub.entity.strict :as se]
            [orcpub.dnd.e5.ledger :as ledger]
            [orcpub.dnd.e5.picks :as picks]
            [orcpub.routes :as routes])
  (:import [java.util UUID]))

(def tide-pak
  "The Tide Pak wizard as the app first saves it: no ids yet, with a summary."
  {::se/values {:orcpub.dnd.e5.character/character-name "Beren Dale"}
   ::se/summary {:orcpub.dnd.e5.character/character-name "Beren Dale"
                 :orcpub.dnd.e5.character/race-name "Tidefolk"}
   ::se/selections
   [{::se/key :class
     ::se/options [{::se/key :wizard
                    ::se/selections [{::se/key :wizard-spells-known
                                      ::se/options [{::se/key :brine-lash} {::se/key :magic-missile}]}]}]}
    {::se/key :race ::se/option {::se/key :tidefolk}}
    {::se/key :feats ::se/options [{::se/key :tidebreaker}]}]})

(defn- fresh-conn
  "A connection to a new in-memory database with the app's schema and users kaylee and zoe."
  []
  (let [uri (str "datomic:mem:ledger-save-" (UUID/randomUUID))]
    (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn schema/all-schemas)
      @(d/transact conn [{:orcpub.user/username "kaylee" :orcpub.user/email "kaylee@example.com"}
                         {:orcpub.user/username "zoe" :orcpub.user/email "zoe@example.com"}])
      conn)))

(defn- page-save
  "What the page sends after removing `address` from the character stored at `id`: the stored
   map read back as text, the pick removed, `ledger/save-data`."
  [conn id address]
  (let [text (pr-str (d/pull (d/db conn) '[*] id))
        character (:character (ledger/read-character text))]
    (ledger/save-data (ledger/read-stored text) (:character (picks/remove-at character address)))))

(deftest a-removal-saved-through-the-save-route-drops-only-that-pick
  (let [conn (fresh-conn)
        id (:db/id (routes/create-new-character conn tide-pak "kaylee"))
        before (d/pull (d/db conn) '[*] id)
        {:keys [status body]} (routes/do-save-character (d/db conn) conn
                                                        (page-save conn id [:feats :tidebreaker])
                                                        {:user "kaylee"})
        after (d/pull (d/db conn) '[*] id)
        removed (set/difference (entity/db-ids before) (entity/db-ids after))]
    (is (= 200 status))
    (is (= id (:db/id body)) "same character, same id")
    (is (not (re-find #"tidebreaker" (pr-str after))))
    (is (re-find #"brine-lash" (pr-str after)))
    (is (= "Tidefolk" (get-in after [::se/summary :orcpub.dnd.e5.character/race-name])) "the summary stays")
    (is (= 1 (count removed)) "the feat's option, nothing else; the emptied list stays, as unticking leaves it")
    (is (= (:orcpub.dnd.e5.character/character-name (::se/values before))
           (:orcpub.dnd.e5.character/character-name (::se/values after))))))

(deftest only-the-owner-can-save-a-removal
  (let [conn (fresh-conn)
        id (:db/id (routes/create-new-character conn tide-pak "kaylee"))
        {:keys [status]} (routes/do-save-character (d/db conn) conn
                                                   (page-save conn id [:feats :tidebreaker])
                                                   {:user "zoe"})]
    (is (= 401 status))
    (is (re-find #"tidebreaker" (pr-str (d/pull (d/db conn) '[*] id))) "nothing changed")))
