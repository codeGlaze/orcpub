(ns orcpub.character-report-test
  "The 'character could not be loaded' support email: what the server adds (the Data View repair
   address and the saved character's stored choices) and where it sits in the body."
  (:require [clojure.string :as s]
            [clojure.test :refer [deftest testing is]]
            [datomic.api :as d]
            [orcpub.db.schema :as schema]
            [orcpub.email :as email]
            [orcpub.entity.strict :as se]
            [orcpub.routes :as routes])
  (:import [java.util UUID]))

(defn- fresh-conn
  "A connection to a new in-memory database with the app's schema and the user kaylee."
  []
  (let [uri (str "datomic:mem:character-report-" (UUID/randomUUID))]
    (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn schema/all-schemas)
      @(d/transact conn [{:orcpub.user/username "kaylee" :orcpub.user/email "kaylee@example.com"}])
      conn)))

(def tide-pak
  "A strict character with a name, a race and a feat."
  {::se/values {:orcpub.dnd.e5.character/character-name "Beren Dale"}
   ::se/selections [{::se/key :race ::se/option {::se/key :tidefolk}}
                    {::se/key :feats ::se/options [{::se/key :tidebreaker}]}]})

(defn- body
  "The text body of the report email for `report`."
  [report]
  (get-in (email/character-report-message report) [:body 0 :content]))

(deftest the-server-adds-the-repair-address-and-the-stored-choices
  (let [conn (fresh-conn)
        id (:db/id (routes/create-new-character conn tide-pak "kaylee"))
        {:keys [repair-url stored-choices]} (routes/character-report-details (d/db conn) "https://dmv.test" (str id))]
    (is (= (str "https://dmv.test/pages/dnd/5e/characters/" id "/repair") repair-url))
    (is (= [(str "Character " id " (Beren Dale)") "Race › Tidefolk" "Feats › Tidebreaker"]
           (s/split-lines stored-choices)))
    (testing "a number from the client works the same as text"
      (is (= repair-url (:repair-url (routes/character-report-details (d/db conn) "https://dmv.test" id)))))))

(deftest nothing-is-added-for-an-id-that-is-not-a-saved-character
  (let [conn (fresh-conn)
        user-id (d/q '[:find ?u . :where [?u :orcpub.user/username "kaylee"]] (d/db conn))]
    (doseq [char-id ["17592186045999" "abc" "1; drop" nil (str user-id)]]
      (is (= {:repair-url nil :stored-choices nil}
             (routes/character-report-details (d/db conn) "https://dmv.test" char-id))
          (pr-str char-id)))))

(deftest the-email-puts-them-between-the-error-and-the-raw-data
  (let [text (body {:char-id "7" :user-email "kaylee@example.com" :error "EOF" :raw "{:a"
                    :repair-url "https://dmv.test/pages/dnd/5e/characters/7/repair"
                    :stored-choices "Character 7 (Beren Dale)\nRace › Tidefolk"})
        at #(s/index-of text %)]
    (is (< (at "Error:") (at "Repair it in Data View") (at "--- stored choices ---") (at "--- raw character data ---")))
    (is (s/includes? text "https://dmv.test/pages/dnd/5e/characters/7/repair\n"))
    (is (s/includes? text "--- stored choices ---\nCharacter 7 (Beren Dale)\nRace › Tidefolk\n")))
  (testing "without them the email is as it was"
    (let [text (body {:char-id "7" :user-email "kaylee@example.com" :error "EOF" :raw "{:a"})]
      (is (not (s/includes? text "Repair it")))
      (is (not (s/includes? text "stored choices")))
      (is (s/includes? text "Error:        EOF\n\n--- raw character data ---\n{:a")))))
