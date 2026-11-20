(ns orcpub.routes-test
  (:require
   [clojure.test :refer [deftest is testing are]]
   [clojure.set :refer [intersection]]
   [clojure.string :as s]
   [datomic.api :as d]
   [datomock.core :as dm]
   [io.pedestal.http :as http]
   [buddy.sign.jwt :as jwt]
   [environ.core :as environ]
   [orcpub.routes :as routes]
   [orcpub.routes.auth :as auth]
            [orcpub.env :as env]
   [orcpub.dnd.e5.magic-items :as mi]
   [orcpub.dnd.e5.character :as char5e]
   [orcpub.modifiers :as mod]
   [orcpub.entity :as entity]
   [orcpub.entity.strict :as se]
   [orcpub.errors :as errors]
   [orcpub.security :as security]
   [orcpub.pwned :as pwned]
   [orcpub.email :as email]
   [orcpub.outbox :as outbox]
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
                                 :orcpub.user/email "test@test.com" :orcpub.user/email-key "test@test.com"}
                                {:orcpub.user/username "testy-2"
                                 :orcpub.user/email "test-2@test.com" :orcpub.user/email-key "test-2@test.com"}])
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
          (is (= "Charry-2" (-> updated-character ::se/summary ::char5e/character-name)))))
      (testing "Saving a character deleted since it was opened answers 410 :character-gone"
        (let [saved (:body (routes/do-save-character (d/db mocked-conn) mocked-conn (test-character) {:user "testy"}))
              _ @(d/transact mocked-conn [[:db/retractEntity (:db/id saved)]])
              response (routes/do-save-character (d/db mocked-conn) mocked-conn saved {:user "testy"})]
          (is (= 410 (:status response)))
          (is (= :character-gone (-> response :body :error)))))
      (testing "Saving another player's character still answers 401"
        (let [saved (:body (routes/do-save-character (d/db mocked-conn) mocked-conn (test-character) {:user "testy"}))]
          (is (= 401 (:status (routes/do-save-character (d/db mocked-conn) mocked-conn saved {:user "testy-2"})))))))))

(deftest test-save-entity
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      @(d/transact mocked-conn schema/all-schemas)
      @(d/transact mocked-conn [{:orcpub.user/username "testy"
                                 :orcpub.user/email "test@test.com" :orcpub.user/email-key "test@test.com"}
                                {:orcpub.user/username "testy-2"
                                 :orcpub.user/email "test-2@test.com" :orcpub.user/email-key "test-2@test.com"}])

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
          claims (jwt/unsign token (env/value :signature))]
      (is (= "test@example.com" (:email claims))
          "Email should be lowercased")
      (is (= "unsubscribe" (:action claims))))))

(deftest test-unsubscribe-handler
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      @(d/transact mocked-conn schema/all-schemas)
      @(d/transact mocked-conn [{:orcpub.user/username "testy"
                                  :orcpub.user/email "test@test.com" :orcpub.user/email-key "test@test.com"
                                  :orcpub.user/send-updates? true}])

      (testing "Valid token unsubscribes user"
        (let [token (routes/unsubscribe-token "test@test.com")
              resp (routes/unsubscribe {:query-params {:token token}
                                         :db (d/db mocked-conn)
                                         :conn mocked-conn})]
          (is (= 302 (:status resp))
              "Should redirect to success page")
          (let [user (auth/user-for-email (d/db mocked-conn) "test@test.com")]
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
                                  :orcpub.user/email "test@test.com" :orcpub.user/email-key "test@test.com"
                                  :orcpub.user/send-updates? false}])

      (testing "Toggle send-updates to true"
        (let [resp (routes/update-user-preferences
                     {:transit-params {:send-updates? true}
                      :db (d/db mocked-conn)
                      :conn mocked-conn
                      :identity {:user "testy"}})]
          (is (= 200 (:status resp)))
          (let [user (auth/user-for-email (d/db mocked-conn) "test@test.com")]
            (is (true? (:orcpub.user/send-updates? user))))))

      (testing "Toggle send-updates back to false"
        (let [resp (routes/update-user-preferences
                     {:transit-params {:send-updates? false}
                      :db (d/db mocked-conn)
                      :conn mocked-conn
                      :identity {:user "testy"}})]
          (is (= 200 (:status resp)))
          (let [user (auth/user-for-email (d/db mocked-conn) "test@test.com")]
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
                                  :orcpub.user/email "test@test.com" :orcpub.user/email-key "test@test.com"
                                  :orcpub.user/send-updates? true}])
      (let [db (d/db mocked-conn)
            user (auth/user-for-email db "test@test.com")
            body (auth/user-body db user)]
        (is (true? (:send-updates? body))
            "user-body should include send-updates? field")))))

(deftest test-dark-button-text-preference
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      @(d/transact mocked-conn schema/all-schemas)
      @(d/transact mocked-conn [{:orcpub.user/username "testy"
                                  :orcpub.user/email "test@test.com" :orcpub.user/email-key "test@test.com"
                                  :orcpub.user/send-updates? true}])
      (let [put! (fn [params] (routes/update-user-preferences
                               {:transit-params params :db (d/db mocked-conn)
                                :conn mocked-conn :identity {:user "testy"}}))
            body (fn [] (let [db (d/db mocked-conn)]
                          (auth/user-body db (auth/user-for-email db "test@test.com"))))]
        (testing "never chosen: absent, so the browser's own choice is not overwritten at login"
          (is (not (contains? (body) :dark-button-text?))))
        (testing "turning it on saves it and returns it"
          (let [resp (put! {:dark-button-text? true})]
            (is (= 200 (:status resp)))
            (is (true? (get-in resp [:body :dark-button-text?]))))
          (is (true? (:dark-button-text? (body)))))
        (testing "turning it off is a choice too, and is returned as false"
          (put! {:dark-button-text? false})
          (is (false? (:dark-button-text? (body)))))
        (testing "saving it leaves the email preference alone"
          (is (true? (:send-updates? (body)))))))))


(deftest test-pdf-file-name-preference
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      @(d/transact mocked-conn schema/all-schemas)
      @(d/transact mocked-conn [{:orcpub.user/username "testy"
                                  :orcpub.user/email "test@test.com" :orcpub.user/email-key "test@test.com"}])
      (let [put! (fn [params] (routes/update-user-preferences
                               {:transit-params params :db (d/db mocked-conn)
                                :conn mocked-conn :identity {:user "testy"}}))
            body (fn [] (let [db (d/db mocked-conn)]
                          (auth/user-body db (auth/user-for-email db "test@test.com"))))]
        (testing "never chosen: absent"
          (is (not (contains? (body) :pdf-file-name))))
        (testing "a choice is saved and comes back with the user"
          (let [resp (put! {:pdf-file-name {:player? false :level-style :prefixed}})]
            (is (= {:player? false :level-style :prefixed} (get-in resp [:body :pdf-file-name]))))
          (is (= {:player? false :level-style :prefixed} (:pdf-file-name (body)))))
        (testing "unknown keys and wrong kinds are dropped before saving"
          (put! {:pdf-file-name {:player? "yes" :level-style :huge :evil "x" :class? false}})
          (is (= {:class? false} (:pdf-file-name (body)))))))))

(deftest requested-pdf-filename-prefers-the-players-choices
  (testing "the client's :file-name is used, made file-safe"
    (is (= "Hero - L5 Wizard.pdf"
           (routes/requested-pdf-filename {:file-name "Hero - L5 Wizard" :character-name "Other"}))))
  (testing "a blank :file-name (every part unticked) falls back to character.pdf"
    (is (= "character.pdf" (routes/requested-pdf-filename {:file-name ""}))))
  (testing "a :file-name with nothing file-safe left falls back to the names and class level"
    (is (= "Fighter (5).pdf"
           (routes/requested-pdf-filename {:file-name "🙂" :character-name "🙂" :class-level "Fighter (5)"}))))
  (testing "no :file-name (an older client) builds it from the names"
    (is (= "Hero - Fighter (5).pdf"
           (routes/requested-pdf-filename {:character-name "Hero" :class-level "Fighter (5)"})))))

(deftest a-failed-send-does-not-mark-a-reset-address-as-registered
  ;; Swallowed on purpose: only a real account can fail to be emailed, so an error response would
  ;; reopen the membership test the uniform 200 closed. (Everything else about the reset form's
  ;; limits and answers is read off security/auth-guards in security_test.clj.)
  (with-redefs [auth/user-for-email (constantly {:db/id 17})
                auth/do-send-password-reset (fn [& _] (throw (ex-info "smtp is down" {})))]
    (is (= {:status 200} (auth/send-password-reset {:query-params {:email "someone@example.com"}
                                                    :db nil :conn nil :remote-addr "9.9.9.9"})))))

(defn- await-count
  "Waits up to `timeout-ms` for `posted` to hold `n` messages (the notice is queued on another
   thread), polling rather than sleeping a fixed time. Returns the messages."
  [posted n timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (when (and (< (count @posted) n) (< (System/currentTimeMillis) deadline))
        (Thread/sleep 5)
        (recur)))
    @posted))

(defn- fail-from [ip typed request]
  (auth/bad-credentials-response nil typed ip request))

(deftest the-owner-is-told-when-one-account-is-tried-from-several-places
  (reset! @#'security/sightings {})
  (reset! @#'security/notice-state {})
  (let [posted (atom [])
        request {:scheme :https :headers {"host" "example.test"}}
        account {:db/id 17 :orcpub.user/email "kaylee@serenity.example"
                 :orcpub.user/first-and-last-name "Kaylee"}]
    (with-redefs [outbox/enqueue! (fn [_conn _kind message] (swap! posted conj message))]
      (testing "a spread of addresses that never happened sends nothing"
        (with-redefs [auth/find-user-by-username-or-email (constantly account)]
          (fail-from "1.1.1.1" "kaylee" request)
          (fail-from "2.2.2.2" "kaylee" request)
          (is (empty? (await-count posted 1 100)) "nothing arrives inside a short bounded wait")))
      (testing "an address with no account here is told nothing at all"
        (with-redefs [auth/find-user-by-username-or-email (constantly nil)]
          (dotimes [i 3] (fail-from (str "3.3.3." i) "nobody" request))
          (is (empty? (await-count posted 1 100)) "nothing arrives inside a short bounded wait")))
      (testing "three places at once reach the address on the account; the answer is bad credentials either way"
        (with-redefs [auth/find-user-by-username-or-email (constantly account)]
          (is (= errors/bad-credentials (-> (fail-from "3.3.3.3" "kaylee" request) :body :error)))
          (let [message (first (await-count posted 1 5000))]
            (is (= "kaylee@serenity.example" (:to message)))
            (is (re-find #"https://example\.test" (pr-str (:body message))) "linking to this site")
            (is (not (re-find #"more attempts" (pr-str (:body message)))) "the first says nothing of a count"))
          (fail-from "4.4.4.4" "kaylee" request)
          (is (= 1 (count (await-count posted 2 200))) "a further attempt inside the gap sends nothing more"))))))

(deftest a-follow-up-notice-says-how-many-attempts-came-in
  (doseq [[label message]
          [["sign-in" (email/sign-in-attempts-message "https://example.test" {:email "a@b.c" :since-last 7})]
           ["sign-up" (email/signup-attempt-message "https://example.test" {:email "a@b.c" :since-last 7})]]]
    (let [text (pr-str (:body message))]
      (is (re-find #"7 more attempts" text) label)
      (is (not (re-find #"more attempts"
                        (pr-str (:body (email/signup-attempt-message "https://example.test" {:email "a@b.c"}))))))
      (is (not (re-find #"token|key=" text)) "no credential, only pages"))))


(deftest a-user-agent-is-described-the-way-a-person-would
  ;; Order matters in describe-browser: Edge and Opera both claim to be Chrome,
  ;; and Chrome claims to be Safari, so the checks run most-specific first.
  (are [expected ua] (= expected (email/describe-browser ua))
    "Chrome on Windows"       "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/120.0 Safari/537.36"
    "Edge on Windows"         "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/120.0 Safari/537.36 Edg/120.0"
    "Safari on iOS"           "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Version/17.0 Safari/604.1"
    "Firefox on Linux"        "Mozilla/5.0 (X11; Linux x86_64; rv:121.0) Firefox/121.0"
    "an unrecognised browser" "curl/8.4.0"
    "an unrecognised browser" ""
    "an unrecognised browser" nil))


(deftest the-reset-key-is-stored-as-a-digest
  (let [key "0a5f8e2c-1111-2222-3333-444455556666"
        digest (auth/hash-reset-key key)]
    (is (= 64 (count digest)) "SHA-256 as hex")
    (is (re-matches #"[0-9a-f]{64}" digest))
    (is (not= key digest) "the emailed secret is not what the table holds")
    (is (= digest (auth/hash-reset-key key)) "and the lookup can reproduce it")
    (is (not= digest (auth/hash-reset-key (str key "x"))))))


(deftest the-corpus-refuses-only-the-egregious
  ;; The corpus counts commonness, not danger to this person. A password seen a
  ;; handful of times leaked in somebody else's dump; one seen four figures of
  ;; times is in every cracking wordlist. Only the second is refused -- the
  ;; first is the strength meter's to argue with while someone is still typing.
  (doseq [[n refused?] {0 false, 1 false, 297 false, 999 false, 1000 true, 52372427 true}]
    (with-redefs [pwned/check (constantly n)]
      (is (= refused? (some? (#'auth/breach-errors "irrelevant")))
          (str n " appearances"))))
  (testing "a service that could not answer is not an objection"
    (with-redefs [pwned/check (constantly :unknown)]
      (is (nil? (#'auth/breach-errors "irrelevant")))))
  (testing "the verdict is keyed for the METER, not as a field fault"
    (with-redefs [pwned/check (constantly 52372427)]
      (let [answer (#'auth/breach-errors "irrelevant")]
        ;; :password would render it as a red error above the input, directly
        ;; over a meter reporting UNCOMMON in green about the same password.
        (is (nil? (:password answer)) "not keyed with the rule faults")
        (let [message (first (:password-common answer))]
          (is (= "A few words strung together are harder to guess and easier to remember."
                 message))
          (is (not (re-find #"\d" message)) "no count, no breach language")
          (is (not (re-find #"(?i)too common" message))
              "the badge says Too common; the line under the bar does not repeat it"))))))


(deftest the-password-changed-notice-says-what-happened-and-what-to-do
  ;; The one signal that makes an unauthorised reset visible. Without it a
  ;; takeover is silent until the owner cannot log in, and then they learn only
  ;; that they cannot.
  (let [html (-> (email/password-changed-email-html
                  "Kaylee Frye" "https://example.test/pages/send-password-reset-page"
                  "Firefox on Linux" "3 March 2026 at 09:12 UTC")
                 pr-str)]
    (is (re-find #"password on your account was just changed" html))
    (is (re-find #"Firefox on Linux" html) "where from")
    (is (re-find #"3 March 2026" html) "and when")
    (is (re-find #"send-password-reset-page" html) "and a way to undo it")
    (is (not (re-find #"(?i)\bkey=|token=" html))
        "the link carries no credential: an unexpected mail holding a working one
         teaches people to click exactly what a phishing mail sends them")))

(deftest the-email-change-notice-goes-to-the-address-losing-the-account
  (let [html (-> (email/email-change-notice-html
                  "Kaylee Frye" "someone-else@example.test" "3 March 2026 at 09:12 UTC")
                 pr-str)]
    (is (re-find #"someone-else@example.test" html) "names where it is going")
    (is (re-find #"Nothing has moved yet" html) "and that it has not happened yet")
    (is (re-find #"(?i)change your password now" html) "and what to do if it was not them")))

(deftest a-reset-link-is-a-one-time-permission-not-a-sign-in
  ;; The key in the link authorises one thing, setting a new password, once. Opening the link
  ;; used to mint an hour-long session for the account, so anyone holding the mail was signed
  ;; in without changing the password, and the owner was never told. OWASP Forgot Password.
  (with-conn conn
    (let [c (dm/fork-conn conn)
          reset-key "the-key-in-the-emailed-link"
          sent (java.util.Date.)
          request (fn [params] {:json-params params :db (d/db c) :conn c
                                :headers {"host" "example.test" "user-agent" "test"}
                                :scheme :https})]
      @(d/transact c schema/all-schemas)
      @(d/transact c [{:orcpub.user/username "resetter"
                       :orcpub.user/email "resetter@test.com" :orcpub.user/email-key "resetter@test.com"
                       :orcpub.user/password "old-hash"
                       :orcpub.user/verified? true
                       :orcpub.user/password-reset-key (auth/hash-reset-key reset-key)
                       :orcpub.user/password-reset-sent sent}])
      (with-redefs-fn {#'auth/breach-errors (constantly nil)}
        (fn []
          (testing "opening the link grants no session"
            (let [resp (auth/reset-password-page {:query-params {:key reset-key} :db (d/db c)
                                                    :headers {"host" "example.test"}
                                                    :uri "/reset-password-page"})]
              (is (= 200 (:status resp)))
              (is (not (contains? (:cookies resp) "token")) "the link minted a sign-in session")
              (is (= "resetter" (get-in resp [:cookies "reset-username" :value])))
              (is (= "no-referrer" (get-in resp [:headers "Referrer-Policy"]))
                  "the key in the URL must not leak through the Referer of anything the page loads")))
          (testing "a submit without the key, or with a wrong one, changes nothing"
            (doseq [k [nil "" "not-the-key"]]
              (let [resp (auth/reset-password (request {:password "a fresh long passphrase"
                                                          :verify-password "a fresh long passphrase"
                                                          :key k}))]
                (is (= 400 (:status resp)) (str "key " (pr-str k)))
                (is (seq (get-in resp [:body :general])))))
            (is (= "old-hash" (:orcpub.user/password (auth/first-user-by (d/db c) auth/username-query "resetter")))))
          (testing "the key sets the password once, and is then spent"
            (is (= 200 (:status (auth/reset-password (request {:password "a fresh long passphrase"
                                                                 :verify-password "a fresh long passphrase"
                                                                 :key reset-key})))))
            (let [user (auth/first-user-by (d/db c) auth/username-query "resetter")]
              (is (not= "old-hash" (:orcpub.user/password user)))
              (is (nil? (:orcpub.user/password-reset-key user)) "the key was not retired"))
            (is (= #{[:password-changed "resetter@test.com"]}
                   (d/q '[:find ?k ?to :where [?e :orcpub.outbox/kind ?k] [?e :orcpub.outbox/to ?to]] (d/db c)))
                "the password-changed notice is queued with the change")
            (is (= 400 (:status (auth/reset-password (request {:password "another long passphrase"
                                                                 :verify-password "another long passphrase"
                                                                 :key reset-key}))))
                "a used link worked twice")))))))

(deftest two-submits-of-one-reset-link-set-one-password
  ;; Both read the link as usable before either wrote. The second must fail, not overwrite the first.
  (with-conn conn
    (let [c (dm/fork-conn conn)
          reset-key "raced-key"]
      @(d/transact c schema/all-schemas)
      @(d/transact c [{:orcpub.user/username "racer" :orcpub.user/email "racer@test.com" :orcpub.user/email-key "racer@test.com"
                       :orcpub.user/password "old-hash"
                       :orcpub.user/password-reset-key (auth/hash-reset-key reset-key)
                       :orcpub.user/password-reset-sent (java.util.Date.)}])
      (with-redefs-fn {#'auth/breach-errors (constantly nil)}
        (fn []
          (let [before (d/db c)
                submit (fn [pw] (auth/reset-password {:json-params {:password pw :verify-password pw :key reset-key}
                                                        :db before :conn c :headers {"host" "example.test"}}))
                first-resp (submit "the first long passphrase")
                second-resp (submit "the second long passphrase")]
            (is (= 200 (:status first-resp)))
            (is (= 400 (:status second-resp)) "the same link set a second password")))))))

(deftest an-expired-reset-link-sets-nothing
  (with-conn conn
    (let [c (dm/fork-conn conn)
          reset-key "an-old-key"]
      @(d/transact c schema/all-schemas)
      @(d/transact c [{:orcpub.user/username "late"
                       :orcpub.user/email "late@test.com" :orcpub.user/email-key "late@test.com"
                       :orcpub.user/password "old-hash"
                       :orcpub.user/password-reset-key (auth/hash-reset-key reset-key)
                       :orcpub.user/password-reset-sent (java.util.Date. (- (System/currentTimeMillis)
                                                                            (* 3 3600 1000)))}])
      (with-redefs-fn {#'auth/breach-errors (constantly nil)}
        (fn []
          (let [resp (auth/reset-password {:json-params {:password "a fresh long passphrase"
                                                           :verify-password "a fresh long passphrase"
                                                           :key reset-key}
                                             :db (d/db c) :conn c :headers {"host" "example.test"}})]
            (is (= 400 (:status resp)))
            (is (= "old-hash" (:orcpub.user/password
                               (auth/first-user-by (d/db c) auth/username-query "late"))))))))))

(deftest pdf-filename-names-the-sheet
  (testing "blank parts are left out, so no gap and no unnamed file"
    (are [player character expected] (= expected (routes/pdf-filename player character "Fighter (5)"))
      "Player" "Character" "Player - Character - Fighter (5).pdf"
      nil      "Character" "Character - Fighter (5).pdf"
      "Player" nil         "Player - Fighter (5).pdf"
      "  "     " Hero "    "Hero - Fighter (5).pdf"
      nil      nil         "Fighter (5).pdf"
      "   "    "   "       "Fighter (5).pdf"))
  (testing "letters of any script stay; emoji, the characters Windows refuses, invisible
            characters and a leading dot or dash (a hidden file on Linux and Mac) go"
    (are [player character expected] (= expected (routes/pdf-filename player character "Fighter (5)"))
      "Player 🙂"  "龍 Dragon"         "Player - 龍 Dragon - Fighter (5).pdf"
      "Zoë"        "D’Artagnan"        "Zoë - D’Artagnan - Fighter (5).pdf"
      "A<b>:c"     "Ash/Oak\\Elm|?*"  "Abc - AshOakElm - Fighter (5).pdf"
      "Bob \"Bold\"" "Hero..."        "Bob Bold - Hero - Fighter (5).pdf"
      "..hidden"   "-dash"            "hidden - dash - Fighter (5).pdf"
      "Kira\u200B" "fdp\u202Eexe"     "Kira - fdpexe - Fighter (5).pdf"
      "🙂🙂"        nil                "Fighter (5).pdf"))
  (testing "accents are stored composed (NFC), as Windows and Linux expect, whatever form arrives"
    (is (= "Zo\u00EB - Fighter (5).pdf" (routes/pdf-filename nil "Zoe\u0308" "Fighter (5)"))))
  (testing "a long name fits the 255-byte limit of Linux and Android, with no trailing space or dot"
    (doseq [unit ["Long. " "龍龍 "]]
      (let [n (routes/pdf-filename nil (apply str (repeat 120 unit)) nil)]
        (is (<= (count (.getBytes ^String n "UTF-8")) 255) n)
        (is (re-matches #"[^ .].*[^ .]\.pdf" n) n))))
  (testing "character.pdf only when nothing names the sheet, or the name is reserved on Windows"
    (is (= "character.pdf" (routes/pdf-filename nil " " nil)))
    (is (= "character.pdf" (routes/pdf-filename nil "Con" nil)))
    (is (= "character.pdf" (routes/pdf-filename "🙂" nil nil)))))

(deftest content-disposition-carries-any-name
  (testing "filename*= keeps every character; filename= is plain ASCII with no raw quote or break"
    (are [filename star ascii] (let [v (routes/content-disposition filename)]
                                 (and (s/includes? v (str "filename*=UTF-8''" star))
                                      (s/includes? v (str "filename=\"" ascii "\""))))
      "Zoë - D’Artagnan.pdf" "Zo%C3%AB%20-%20D%E2%80%99Artagnan.pdf" "Zo_ - D_Artagnan.pdf"
      "龍 Dragon.pdf"        "%E9%BE%8D%20Dragon.pdf"                "_ Dragon.pdf"
      "Bob \"Bold\"*.pdf"    "Bob%20%22Bold%22%2A.pdf"               "Bob _Bold_*.pdf"
      "Line\r\nBreak.pdf"    "Line%0D%0ABreak.pdf"                   "Line__Break.pdf")))
