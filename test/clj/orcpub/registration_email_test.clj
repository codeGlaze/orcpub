(ns orcpub.registration-email-test
  "Registration and its verification email are saved together: the account and the queued email
   (orcpub.outbox) are one transaction, so a mail server that is down at sign-up costs a delay, not
   an account. A resend replaces the key and queues a new email; an unknown address gets nothing.
   With no mail server configured, registration verifies on creation instead: see
   registration_no_email_test."
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.test :refer [deftest is use-fixtures]]
   [datomic.api :as d]
   [orcpub.email :as email]
   [orcpub.errors :as errors]
   [orcpub.outbox :as outbox]
   [orcpub.routes :as routes]
   [orcpub.security :as security]
   [orcpub.db.schema :as schema])
  (:import [java.util Date UUID]))

(use-fixtures :each
  ;; registration-allowed? counts sign-ups per address in memory for the whole JVM, and these
  ;; tests all register from a nil address; this file tests the email, not the throttle.
  (fn [f]
    (with-redefs [security/registration-allowed? (constantly true)
                  email/configured? (constantly true)]
      (binding [errors/*error-prefix* "TEST_ERROR:"]
        (f)))))

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
        resp (routes/register (register-request conn))
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
    (is (= 200 (:status (routes/register (register-request conn)))) "the person is not turned away")
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
    (routes/register (register-request conn))
    (outbox/send-due! conn (constantly true) #(Date.))
    (let [before (find-user (d/db conn) "newcomer")
          resp (routes/re-verify (re-verify-request conn "newcomer@test.com"))
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
        resp (routes/re-verify (re-verify-request conn "nobody@test.com"))]
    (is (= 200 (:status resp)) "answers like any resend")
    (is (nil? (d/q '[:find (count ?e) . :where [?e :orcpub.user/verification-key]] (d/db conn)))
        "no record was created for an unknown address")
    (is (empty? (queued conn)) "and no email was queued for it")))
