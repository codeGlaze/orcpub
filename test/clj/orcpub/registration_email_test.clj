(ns orcpub.registration-email-test
  "Registration and its verification email are saved together: the account and the queued email
   (orcpub.outbox) are one transaction, so a mail server that is down at sign-up costs a delay, not
   an account. A resend replaces the key and queues a new email; an unknown address gets nothing.
   With no mail server configured, registration verifies on creation instead: see
   registration_no_email_test."
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [datomic.api :as d]
   [orcpub.email :as email]
   [orcpub.errors :as errors]
   [orcpub.fork.branding :as branding]
   [orcpub.outbox :as outbox]
   [orcpub.routes :as routes]
   [orcpub.routes.auth :as auth]
   [orcpub.security :as security]
   [buddy.hashers :as hashers]
   [orcpub.db.schema :as schema])
  (:import [java.util Date UUID]))

(use-fixtures :each
  ;; Sign-ups are counted per connection in memory for the whole JVM, and these tests all register
  ;; from a nil address; this file tests the email, not the throttle.
  (fn [f]
    (reset! @#'security/sightings {})
    (reset! @#'security/notice-state {})
    (with-redefs [email/configured? (constantly true)
                  branding/app-url "https://site.example"]
      (binding [errors/*error-prefix* "TEST_ERROR:"]
        (f)))
    (reset! @#'security/sightings {})))

(defn- fresh-conn
  "A connection to a new in-memory database with the full schema."
  []
  (let [uri (str "datomic:mem:registration-email-test-" (UUID/randomUUID))]
    (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn schema/all-schemas)
      conn)))

(defn- find-user
  "The user named `username` in `db`, as a map, or nil."
  [db username]
  (when-let [e (d/q '[:find ?e . :in $ ?u :where [?e :orcpub.user/username ?u]] db username)]
    (d/pull db '[*] e)))

(defn- queued
  "The queued emails in `conn`'s database, as {:kind :to :status :message} maps."
  [conn]
  (for [e (d/q '[:find [?e ...] :where [?e :orcpub.outbox/id]] (d/db conn))
        :let [x (d/entity (d/db conn) e)]]
    {:kind (:orcpub.outbox/kind x) :to (:orcpub.outbox/to x) :status (:orcpub.outbox/status x)
     :message (edn/read-string (:orcpub.outbox/message x))}))

(defn- body
  "The text of postal message `m`'s parts, joined."
  [m]
  (str/join " " (map :content (:body m))))

(defn- register-request
  "A registration request for newcomer@test.com against `conn`."
  [conn]
  {:conn conn :db (d/db conn) :scheme :https :headers {"host" "example.test"}
   ;; verify-email must match :email, or register returns 400 before reaching do-verification.
   :json-params {:username "newcomer" :email "newcomer@test.com" :verify-email "newcomer@test.com"
                 :password "hunter2hunter2" :send-updates? false}})

(defn- re-verify-request
  "A resend-verification request for `address` against `conn`."
  [conn address]
  {:conn conn :db (d/db conn) :scheme :https :headers {"host" "example.test"}
   :query-params {:email address}})

(deftest sign-up-saves-the-account-and-its-email-together
  (let [conn (fresh-conn)
        resp (auth/register (register-request conn))
        user (find-user (d/db conn) "newcomer")
        [email & more] (queued conn)]
    (is (= 200 (:status resp)))
    (is (false? (:orcpub.user/verified? user)) "awaiting verification")
    (is (and email (empty? more)) "one email queued")
    (is (= [:verification "newcomer@test.com" :pending] [(:kind email) (:to email) (:status email)]))
    (is (str/includes? (body (:message email)) (:orcpub.user/verification-key user))
        "carrying the link for the key that was stored")))

(deftest sign-up-works-while-the-mail-server-is-down
  (let [conn (fresh-conn)
        down (fn [_] (throw (javax.mail.MessagingException. "Connection refused")))]
    (is (= 200 (:status (auth/register (register-request conn)))) "the person is not turned away")
    (outbox/send-due! conn down #(Date.))
    (is (some? (find-user (d/db conn) "newcomer")) "the account stays")
    (is (= :pending (:status (first (queued conn)))) "and its email waits for another try")
    (let [delivered (atom nil)
          later #(Date. (+ (System/currentTimeMillis) (* 10 60 1000)))]
      (outbox/send-due! conn #(reset! delivered %) later)
      (is (= "newcomer@test.com" (:to @delivered)) "it goes out once the server is back")
      (is (empty? (queued conn)) "and is gone from the database after"))))

(deftest a-resend-replaces-the-key-and-queues-a-new-email
  (let [conn (fresh-conn)]
    (auth/register (register-request conn))
    (outbox/send-due! conn (constantly true) #(Date.))
    (let [before (find-user (d/db conn) "newcomer")
          resp (auth/re-verify (re-verify-request conn "newcomer@test.com"))
          after (find-user (d/db conn) "newcomer")
          [email] (queued conn)]
      (is (= 200 (:status resp)))
      (is (= (:orcpub.user/password before) (:orcpub.user/password after)) "the account is untouched")
      (is (not= (:orcpub.user/verification-key before) (:orcpub.user/verification-key after))
          "a new key")
      (is (str/includes? (body (:message email)) (:orcpub.user/verification-key after))
          "and the queued email carries it"))))

(deftest a-resend-to-an-unknown-address-creates-nothing
  ;; re-verify is unauthenticated, so it answers like a resend and reveals nothing.
  (let [conn (fresh-conn)
        resp (auth/re-verify (re-verify-request conn "nobody@test.com"))]
    (is (= 200 (:status resp)) "answers like any resend")
    (is (nil? (d/q '[:find (count ?e) . :where [?e :orcpub.user/verification-key]] (d/db conn)))
        "no record was created for an unknown address")
    (is (empty? (queued conn)) "and no email was queued for it")))

;; ---- Sign-up never says an address is taken: the owner is mailed instead ----

(defn- sign-up
  "A sign-up as `username` with `address`, against `conn`."
  [conn username address]
  (-> (register-request conn)
      (assoc-in [:json-params :username] username)
      (assoc-in [:json-params :email] address)
      (assoc-in [:json-params :verify-email] address)
      auth/register))

(defn- accounts-with [conn address]
  (d/q '[:find [?e ...] :in $ ?a :where [?e :orcpub.user/email ?a]] (d/db conn) address))

(deftest a-taken-address-gets-the-same-answer-as-a-new-one
  (reset! @#'security/sightings {})
  (let [conn (fresh-conn)
        _ (sign-up conn "newcomer" "newcomer@test.com")
        fresh (sign-up conn "second" "fresh@test.com")
        taken (sign-up conn "third" "newcomer@test.com")]
    (is (= {:status 200} fresh))
    (is (= fresh taken) "same status, same body")
    (is (= 1 (count (accounts-with conn "newcomer@test.com"))) "no second account")
    (is (nil? (find-user (d/db conn) "third")) "and nothing created under the new username")))

(deftest the-owner-is-mailed-once-with-nothing-to-replay
  (reset! @#'security/sightings {})
  (let [conn (fresh-conn)
        _ (sign-up conn "newcomer" "newcomer@test.com")
        key (:orcpub.user/verification-key (find-user (d/db conn) "newcomer"))
        _ (outbox/send-due! conn (constantly true) #(Date.))
        _ (sign-up conn "intruder" "newcomer@test.com")
        _ (sign-up conn "intruder2" "newcomer@test.com")
        notices (filter #(= :signup-attempt (:kind %)) (queued conn))
        text (body (:message (first notices)))]
    (is (= 1 (count notices)) "two attempts close together, one notice")
    (is (= "newcomer@test.com" (:to (first notices))))
    (is (str/includes? text "https://site.example/pages/login-page") "a way to sign in, on the configured site")
    (is (str/includes? text "send-password-reset") "and to reset the password")
    (doseq [secret ["hunter2hunter2" key "newcomer" "intruder"]]
      (is (not (str/includes? text secret)) (str "no " secret)))
    (testing "a different address has its own allowance"
      (sign-up conn "other" "other@test.com")
      (sign-up conn "other2" "other@test.com")
      (is (= 2 (count (filter #(= :signup-attempt (:kind %)) (queued conn))) )
          "only the one taken address added a notice"))))

(deftest a-taken-address-goes-through-the-same-checks-and-costs-the-same
  (reset! @#'security/sightings {})
  (let [conn (fresh-conn)
        _ (sign-up conn "newcomer" "newcomer@test.com")
        hashed (atom 0)
        spent #(count (get @@#'security/sightings [:register-per-host nil]))]
    (reset! @#'security/sightings {})
    (with-redefs [hashers/encrypt (fn [& _] (swap! hashed inc) "hash")]
      (sign-up conn "fresh" "fresh@test.com")
      (is (= [1 1] [@hashed (spent)]) "a new sign-up")
      (sign-up conn "third" "newcomer@test.com")
      (is (= [2 2] [@hashed (spent)])
          "a taken one is counted against the host and hashes the password too"))
    (testing "a failed challenge refuses a taken address exactly as a new one"
      (with-redefs [security/turnstile-enabled? (constantly true)
                    security/turnstile-verify (constantly :fail)]
        (let [before (spent)
              new-resp (sign-up conn "fresh2" "fresh2@test.com")
              taken-resp (sign-up conn "third" "newcomer@test.com")]
          (is (= 400 (:status taken-resp)))
          (is (= new-resp taken-resp))
          (is (= before (spent)) "and spends no allowance"))))))

(deftest the-address-check-route-is-gone
  (is (not-any? #(= "/check-email" (:path %)) routes/routes))
  (is (nil? (resolve 'orcpub.routes.auth/check-email))))

(deftest the-notice-links-to-the-configured-site-not-the-request-host
  (let [conn (fresh-conn)
        _ (sign-up conn "newcomer" "newcomer@test.com")
        forged (-> (register-request conn)
                   (assoc :headers {"host" "evil.example" "x-forwarded-proto" "http"})
                   (assoc-in [:json-params :username] "intruder")
                   (assoc-in [:json-params :email] "newcomer@test.com")
                   (assoc-in [:json-params :verify-email] "newcomer@test.com"))
        _ (auth/register forged)
        text (body (:message (first (filter #(= :signup-attempt (:kind %)) (queued conn)))))]
    (is (str/includes? text "https://site.example/"))
    (is (not (str/includes? text "evil.example")) "nothing the sender controls reaches the links")))

(deftest no-configured-site-means-no-notice
  (let [conn (fresh-conn)]
    (sign-up conn "newcomer" "newcomer@test.com")
    (with-redefs [branding/app-url ""]
      (is (= {:status 200} (sign-up conn "intruder" "newcomer@test.com")) "the answer is unchanged"))
    (is (empty? (filter #(= :signup-attempt (:kind %)) (queued conn))))))

(deftest a-failed-enqueue-does-not-spend-the-notice
  (let [conn (fresh-conn)]
    (sign-up conn "newcomer" "newcomer@test.com")
    (with-redefs [outbox/enqueue! (fn [& _] (throw (ex-info "db down" {})))]
      (is (= {:status 200} (sign-up conn "intruder" "newcomer@test.com"))))
    (sign-up conn "intruder2" "newcomer@test.com")
    (is (= 1 (count (filter #(= :signup-attempt (:kind %)) (queued conn))))
        "the next attempt still gets the notice the failed one could not send")))

(deftest a-resend-does-not-unverify-an-account-verified-meanwhile
  (let [conn (fresh-conn)
        _ (auth/register (register-request conn))
        id (:db/id (find-user (d/db conn) "newcomer"))
        stale {:db/id id :orcpub.user/verified? false}]
    @(d/transact conn [[:db/add id :orcpub.user/verified? true]])
    (outbox/send-due! conn (constantly true) #(Date.))
    ;; the handler read `stale` at request start; the account verified before it wrote
    (with-redefs [auth/user-for-email (constantly stale)]
      (is (= {:status 200} (auth/re-verify (re-verify-request conn "newcomer@test.com")))))
    (let [after (find-user (d/db conn) "newcomer")]
      (is (true? (:orcpub.user/verified? after)) "still verified")
      (is (empty? (filter #(= :verification (:kind %)) (queued conn))) "and no mail was queued"))))

(deftest the-verified-write-is-conditional-on-the-value-read
  (let [conn (fresh-conn)
        _ (auth/register (register-request conn))
        id (:db/id (find-user (d/db conn) "newcomer"))
        reads (atom 0)
        real-entity d/entity]
    ;; verification lands between the fresh read inside do-verification and its transaction
    (with-redefs [d/entity (fn [db e]
                             (let [r (real-entity db e)]
                               (when (= 1 (swap! reads inc))
                                 @(d/transact conn [[:db/add id :orcpub.user/verified? true]]))
                               r))]
      (is (= {:status 200} (auth/do-verification (re-verify-request conn "newcomer@test.com")
                                                 {} conn {:db/id id}))))
    (is (true? (:orcpub.user/verified? (find-user (d/db conn) "newcomer"))) "the swap refused to un-verify")))
