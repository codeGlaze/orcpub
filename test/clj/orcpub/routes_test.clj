(ns orcpub.routes-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [clojure.set :refer [intersection]]
   [datomic.api :as d]
   [datomock.core :as dm]
   [io.pedestal.http :as http]
   [buddy.sign.jwt :as jwt]
   [environ.core :as environ]
   [orcpub.routes :as routes]
   [orcpub.dnd.e5.magic-items :as mi]
   [orcpub.dnd.e5.character :as char5e]
   [orcpub.modifiers :as mod]
   [orcpub.entity :as entity]
   [orcpub.entity.strict :as se]
   [orcpub.errors :as errors]
   [orcpub.db.schema :as schema])
  (:import [java.util UUID]))

#_(def service
  (::http/service-fn (http/create-servlet {::http/routes routes/routes
                                           ::http/type :jetty
                                           ::http/port 8080})))

#_(deftest test-index
  (let [response (response-for service :get "/")]
    (prn "RESPONSE" response)
    (is (= (:status response)
           200))))

(defmacro with-conn [conn-binding & body]
  `(let [uri# (str "datomic:mem:orcpub-test-" (UUID/randomUUID))
         ~conn-binding (do
                         (d/create-database uri#)
                         (d/connect uri#))]
     (try ~@body
          (finally (d/delete-database uri#)))))

(defn test-character []
  {::se/selections
   [{::se/key :ability-scores
     ::se/option
     {::se/key :standard-scores
      ::se/map-value
      {::char5e/str 15
       ::char5e/dex 14
       ::char5e/con 13
       ::char5e/int 12
       ::char5e/wis 10
       ::char5e/cha 8}}}
    {::se/key :class
     ::se/options
     [{::se/key :barbarian
       ::se/selections
       [{::se/key :levels
         ::se/options [{::se/key :level-1}]}]}]}
    {::se/key :weapons
     ::se/options
     [{::se/key :javelin
       ::se/map-value {:orcpub.dnd.e5.character.equipment/quantity 4
                                        :orcpub.dnd.e5.character.equipment/equipped? true
                                        :orcpub.dnd.e5.character.equipment/class-starting-equipment? true}}]}]
   ::se/summary
   {::char5e/character-name "Charry"
    ::char5e/classes
    [{::char5e/class-name "Barbarian"
      ::char5e/level 1}]}})

(deftest test-do-save-character
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      @(d/transact mocked-conn schema/all-schemas)
      @(d/transact mocked-conn [{:orcpub.user/username "testy"
                                 :orcpub.user/email "test@test.com"}
                                {:orcpub.user/username "testy-2"
                                 :orcpub.user/email "test-2@test.com"}])
      (testing "Save new character"
        (let [character (test-character)
              saved-character (:body (routes/do-save-character (d/db mocked-conn) mocked-conn character {:user "testy"}))]
          (is (= "testy" (::se/owner saved-character)))))
      (testing "Update character"
        (let [character (test-character)
              saved-character (:body (routes/do-save-character (d/db mocked-conn) mocked-conn character {:user "testy"}))
              updated-character (:body (routes/do-save-character
                                        (d/db mocked-conn)
                                        mocked-conn
                                        (assoc-in saved-character [::se/summary ::char5e/character-name] "Charry-2")
                                        {:user "testy"}))]
          (is (= "testy" (::se/owner updated-character)))
          (is (= "Charry" (-> saved-character ::se/summary ::char5e/character-name)))
          (is (= "Charry-2" (-> updated-character ::se/summary ::char5e/character-name))))))))

(deftest test-save-entity
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      @(d/transact mocked-conn schema/all-schemas)
      @(d/transact mocked-conn [{:orcpub.user/username "testy"
                                 :orcpub.user/email "test@test.com"}
                                {:orcpub.user/username "testy-2"
                                 :orcpub.user/email "test-2@test.com"}])

      (testing "Save new entity"
        (let [entity {::mi/name "Cool Item"}
              saved-entity (routes/save-entity mocked-conn "testy" entity ::mi/owner)]
          (is (= "testy" (::mi/owner saved-entity)))
          (is (int? (:db/id saved-entity)))
          (is (= "Cool Item" (::mi/name saved-entity)))))

      (testing "Create and update entity"
        (let [entity {::mi/modifiers [{::mod/key :saving-throw-bonus
                                       ::mod/args [{::mod/keyword-arg ::char5e/str}
                                                   {::mod/int-arg 1}]}]}
              saved-entity (routes/save-entity mocked-conn "testy" entity ::mi/owner)]
          (is (= "testy" (::mi/owner saved-entity)))
          (is (int? (:db/id saved-entity)))
          (is (int? (get-in saved-entity [::mi/modifiers 0 ::mod/args 1 :db/id])))
          (is (= saved-entity (routes/save-entity mocked-conn "testy" saved-entity ::mi/owner)))))

      (testing "Update other user's entity"
        (let [entity {::mi/modifiers [{::mod/key :saving-throw-bonus
                                       ::mod/args [{::mod/keyword-arg ::char5e/str}
                                                   {::mod/int-arg 1}]}]}
              saved-entity (routes/save-entity mocked-conn "testy" entity ::mi/owner)
              saved-entity-2 (routes/save-entity mocked-conn "testy-2" entity ::mi/owner)]
          (is (not= saved-entity saved-entity-2))
          (is (thrown? Throwable (routes/save-entity mocked-conn "testy" (update saved-entity :db/id (:db/id saved-entity-2)) ::mi/owner)))))

      (testing "Removal of orphans"
        (let [entity {::mi/modifiers [{::mod/key :saving-throw-bonus
                                       ::mod/args [{::mod/keyword-arg ::char5e/str}
                                                   {::mod/int-arg 1}]}]}
              saved-entity (routes/save-entity mocked-conn "testy" entity ::mi/owner)
              root-id (:db/id saved-entity)
              child-ids (disj (entity/db-ids saved-entity) root-id)
              update-entity (assoc-in saved-entity [::mi/modifiers 0 :db/id] nil)
              updated-entity (routes/save-entity mocked-conn "testy" update-entity ::mi/owner)
              updated-entity-ids (entity/db-ids updated-entity)]
          (is (= root-id (:db/id updated-entity)))
          (is (empty? (intersection updated-entity-ids child-ids)))))

      (testing "Removal of non-children ids"
        (let [entity {::mi/modifiers [{::mod/key :saving-throw-bonus
                                       ::mod/args [{::mod/keyword-arg ::char5e/str}
                                                   {::mod/int-arg 1}]}]}
              saved-entity (routes/save-entity mocked-conn "testy" entity ::mi/owner)
              root-id (:db/id saved-entity)
              child-ids (disj (entity/db-ids saved-entity) root-id)
              saved-entity-2 (routes/save-entity mocked-conn "testy-2" entity ::mi/owner)
              update-entity (assoc-in saved-entity [::mi/modifiers 0 :db/id] (:db/id saved-entity-2))
              updated-entity (routes/save-entity mocked-conn "testy" update-entity ::mi/owner)
              updated-entity-ids (entity/db-ids updated-entity)]
          (is (= root-id (:db/id updated-entity)))
          (is (empty? (intersection updated-entity-ids child-ids)))
          (is (not (updated-entity-ids (:db/id saved-entity-2)))))))))

(deftest test-db-ids
  (let [e-1 {:db/id 1
             :x {:db/id 2
                 :y [{:db/id 3
                      :z {:db/id 4}}]}}]
    (is (= (entity/db-ids e-1) #{1 2 3 4}))
    (is (= (entity/db-ids e-1 (routes/diff-branch #{1 2})) #{1 2 3}))))

(deftest test-remove-specific-ids
  (let [e {:db/id 2
           :y {:db/id 3
               :z {:db/id 4}
               :x [{:db/id 5} {:db/id 6 :xx 2}]}}]
    (is (= (entity/remove-specific-ids e #{4 6})
           {:db/id 2
            :y {:db/id 3
                :z {}
                :x [{:db/id 5} {:xx 2}]}}))))

(deftest test-remove-ids
  (let [e-1 {:db/id 1
             :s "sere"
             :v 12324
             :x {:db/id 2
                 :y [{:db/id 3
                      :s "xx"
                      :z {:db/id 4}}]
                 :yy [{:db/id 5
                       :v 34
                       :zz {:db/id 6
                            :v 78
                            :zzz [{:db/id 7
                                   :s "String"}]}}]}}
        e-2 {:s "sere"
             :v 12324
             :x {:y [{:s "xx"
                      :z {}}]
                 :yy [{:v 34
                       :zz {:v 78
                            :zzz [{:s "String"}]}}]}}]
    (is (= e-2 (entity/remove-ids e-1)))))

(deftest test-remove-orphan-ids
  (let [e-1 {:db/id 1
             :s "sere"
             :v 12324
             :x {:db/id 2
                 :y [{:db/id 3
                      :s "xx"
                      :z {:db/id 4}}]
                 :yy [{:db/id 5
                       :v 34
                       :zz {:db/id 6
                            :v 78
                            :zzz [{:db/id 7
                                   :s "String"}]}}]}}
        e-2 {:db/id 1
             :s "sere"
             :v 12324
             :x {:y [{:db/id 3
                      :s "xx"
                      :z {:db/id 4}}]
                 :yy [{:db/id 5
                       :v 34
                       :zz {:db/id 6
                            :v 78
                            :zzz [{:db/id 7
                                   :s "String"}]}}]}}
        e-3 {:db/id 1
             :s "sere"
             :v 12324
             :x {:db/id 2
                 :y [{:s "xx"
                      :z {:db/id 4}}]
                 :yy [{:db/id 5
                       :v 34
                       :zz {:v 78
                            :zzz [{:db/id 7
                                   :s "String"}]}}]}}]
    (is (= (entity/remove-orphan-ids e-1) e-1))
    (is (= (entity/remove-orphan-ids e-2) {:db/id 1
                                           :s "sere"
                                           :v 12324
                                           :x {:y [{:s "xx"
                                                    :z {}}]
                                               :yy [{:v 34
                                                     :zz {:v 78
                                                          :zzz [{:s "String"}]}}]}}))
    (is (= (entity/remove-orphan-ids e-3) {:db/id 1
                                           :s "sere"
                                           :v 12324
                                           :x {:db/id 2
                                               :y [{:s "xx"
                                                    :z {}}]
                                               :yy [{:db/id 5
                                                     :v 34
                                                     :zz {:v 78
                                                          :zzz [{:s "String"}]}}]}}))))

;; ─── Unsubscribe Token Tests ────────────────────────────────────────

(deftest test-unsubscribe-token-roundtrip
  (testing "Token encodes email and action, verifiable with signature"
    (let [token (routes/unsubscribe-token "Test@Example.com")
          claims (jwt/unsign token (environ/env :signature))]
      (is (= "test@example.com" (:email claims))
          "Email should be lowercased")
      (is (= "unsubscribe" (:action claims))))))

(deftest test-unsubscribe-handler
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      @(d/transact mocked-conn schema/all-schemas)
      @(d/transact mocked-conn [{:orcpub.user/username "testy"
                                  :orcpub.user/email "test@test.com"
                                  :orcpub.user/send-updates? true}])

      (testing "Valid token unsubscribes user"
        (let [token (routes/unsubscribe-token "test@test.com")
              resp (routes/unsubscribe {:query-params {:token token}
                                         :db (d/db mocked-conn)
                                         :conn mocked-conn})]
          (is (= 302 (:status resp))
              "Should redirect to success page")
          (let [user (routes/user-for-email (d/db mocked-conn) "test@test.com")]
            (is (false? (:orcpub.user/send-updates? user))
                "send-updates? should be false after unsubscribe"))))

      (testing "Idempotent — unsubscribing twice succeeds"
        (let [token (routes/unsubscribe-token "test@test.com")
              resp (routes/unsubscribe {:query-params {:token token}
                                         :db (d/db mocked-conn)
                                         :conn mocked-conn})]
          (is (= 302 (:status resp)))))

      (testing "Tampered token returns 400"
        (let [resp (routes/unsubscribe {:query-params {:token "tampered.token.here"}
                                         :db (d/db mocked-conn)
                                         :conn mocked-conn})]
          (is (= 400 (:status resp)))))

      (testing "Missing token returns 400"
        (let [resp (routes/unsubscribe {:query-params {}
                                         :db (d/db mocked-conn)
                                         :conn mocked-conn})]
          (is (= 400 (:status resp)))))

      (testing "Unknown email returns 400"
        (let [token (routes/unsubscribe-token "nobody@test.com")
              resp (routes/unsubscribe {:query-params {:token token}
                                         :db (d/db mocked-conn)
                                         :conn mocked-conn})]
          (is (= 400 (:status resp))))))))

(deftest test-update-user-preferences
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      @(d/transact mocked-conn schema/all-schemas)
      @(d/transact mocked-conn [{:orcpub.user/username "testy"
                                  :orcpub.user/email "test@test.com"
                                  :orcpub.user/send-updates? false}])

      (testing "Toggle send-updates to true"
        (let [resp (routes/update-user-preferences
                     {:transit-params {:send-updates? true}
                      :db (d/db mocked-conn)
                      :conn mocked-conn
                      :identity {:user "testy"}})]
          (is (= 200 (:status resp)))
          (let [user (routes/user-for-email (d/db mocked-conn) "test@test.com")]
            (is (true? (:orcpub.user/send-updates? user))))))

      (testing "Toggle send-updates back to false"
        (let [resp (routes/update-user-preferences
                     {:transit-params {:send-updates? false}
                      :db (d/db mocked-conn)
                      :conn mocked-conn
                      :identity {:user "testy"}})]
          (is (= 200 (:status resp)))
          (let [user (routes/user-for-email (d/db mocked-conn) "test@test.com")]
            (is (false? (:orcpub.user/send-updates? user))))))

      (testing "Unknown user returns 400"
        (let [resp (routes/update-user-preferences
                     {:transit-params {:send-updates? true}
                      :db (d/db mocked-conn)
                      :conn mocked-conn
                      :identity {:user "nonexistent"}})]
          (is (= 400 (:status resp))))))))

(deftest test-user-body-includes-send-updates
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      @(d/transact mocked-conn schema/all-schemas)
      @(d/transact mocked-conn [{:orcpub.user/username "testy"
                                  :orcpub.user/email "test@test.com"
                                  :orcpub.user/send-updates? true}])
      (let [db (d/db mocked-conn)
            user (routes/user-for-email db "test@test.com")
            body (routes/user-body db user)]
        (is (true? (:send-updates? body))
            "user-body should include send-updates? field")))))

;; ---------------------------------------------------------------------------
;; Clearing an attribute has to actually clear it
;;
;; Datomic retracts nothing for an attribute simply missing from the transacted
;; map, and update-entity's own retractions come from the :db/id diff, which
;; only reaches nested component entities. So "remove for good" dissoc'd the
;; magical properties, saved, and got them back in the response -- the values
;; had never left the database.
;; ---------------------------------------------------------------------------

(defn- magical-item []
  {::mi/name "Retired Blade"
   ::mi/type :weapon
   ::mi/rarity :rare
   ::mi/magical? true
   ::mi/attunement #{:any}
   ::mi/magical-attack-bonus 1
   ::mi/magical-damage-bonus 2
   ::mi/magical-ac-bonus 3})

(deftest removing-magical-properties-for-good-actually-removes-them
  (with-conn conn
    (let [c (dm/fork-conn conn)]
      @(d/transact c schema/all-schemas)
      @(d/transact c [{:orcpub.user/username "kaylee"
                       :orcpub.user/email "kaylee@example.com"}])
      (let [saved (routes/save-entity c "kaylee" (magical-item) ::mi/owner
                                      mi/clearable-attributes)]
        (testing "the properties are stored in the first place"
          (is (= 1 (::mi/magical-attack-bonus saved)))
          (is (= #{:any} (set (::mi/attunement saved)))))

        (testing "saving the item without them takes them out of the database"
          ;; Exactly what ::mi/clear-magical-properties produces: the keys are
          ;; dissoc'd, not emptied.
          (let [cleared (routes/save-entity
                         c "kaylee"
                         (assoc (mi/without-magical-properties (magical-item))
                                :db/id (:db/id saved))
                         ::mi/owner mi/clearable-attributes)]
            (is (nil? (::mi/magical-attack-bonus cleared)))
            (is (nil? (::mi/magical-damage-bonus cleared)))
            (is (nil? (::mi/magical-ac-bonus cleared)))
            (is (empty? (::mi/attunement cleared)))
            (testing "and the item itself survives"
              (is (= "Retired Blade" (::mi/name cleared))))
            (testing "so a fresh read agrees -- the response is not just filtered"
              (let [pulled (d/pull (d/db c) '[*] (:db/id saved))]
                (is (nil? (::mi/magical-attack-bonus pulled)))
                (is (empty? (::mi/attunement pulled)))))))))))

(deftest an-omitted-attribute-is-still-kept-when-it-is-not-clearable
  (testing "the never-retract default is unchanged for everything else"
    ;; Only attributes a caller names are clearable. Without this, any partial
    ;; save would silently wipe the fields it happened not to include.
    (with-conn conn
      (let [c (dm/fork-conn conn)]
        @(d/transact c schema/all-schemas)
        @(d/transact c [{:orcpub.user/username "kaylee"
                         :orcpub.user/email "kaylee@example.com"}])
        (let [saved (routes/save-entity c "kaylee" (magical-item) ::mi/owner nil)
              resaved (routes/save-entity
                       c "kaylee"
                       {:db/id (:db/id saved) ::mi/name "Retired Blade"}
                       ::mi/owner nil)]
          (is (= 1 (::mi/magical-attack-bonus resaved))
              "no clearable attrs passed, so nothing is retracted"))))))
