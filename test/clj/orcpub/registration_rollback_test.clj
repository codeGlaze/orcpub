(ns orcpub.registration-rollback-test
  "Registration must not leave an account behind when the verification email fails.

   do-verification transacts the user and THEN sends the email. Datomic does not
   roll back, so a failed send left a committed, unverified account -- and since
   register validates against existing username/email, the retry it tells the
   user to make then fails with \"already taken\".

   SCOPE, measured rather than assumed. The account is NOT unrecoverable: the
   resend-verification route works on exactly this state (verified against the
   pre-fix code -- resend returns 200 and stores a fresh key), and it is wired to
   a button in the UI. So the real symptom is a confusing dead end that a user
   can escape if they find the resend link, not a permanent lockout. An earlier
   version of this docstring and of `git show agents/develop:docs/kb/blank-env-values.md` said \"can never
   be verified\"; that was wrong.

   Which is why seven years of production never surfaced it: live SMTP works, so
   this branch only runs on a transient send failure, and the handful of users it
   hits report \"it says my email already exists\" -- indistinguishable from
   someone who forgot they had an account.

   It is still worth fixing. A failed send should not leave a half-created
   account, and \"please try again\" is the wrong advice when retrying cannot
   work.

   The sibling flow already solved this: request-email-change transacts, sends,
   and retracts on failure, covered by email_change_test/test-email-send-failure-
   rolls-back. These tests are that one's mirror for registration.

   Every test here stubs email/configured? true: these cover the path taken when
   a deployment HAS SMTP and the send fails. With it unconfigured, registration
   verifies on creation instead and never sends -- that is
   registration_no_email_test.

   See `git show agents/develop:docs/kb/blank-env-values.md`."
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [datomic.api :as d]
   [datomock.core :as dm]
   [orcpub.email :as email]
   [orcpub.errors :as errors]
   [orcpub.routes :as routes]
   [orcpub.db.schema :as schema])
  (:import [java.util UUID]))

(use-fixtures :each
  (fn [f]
    (binding [errors/*error-prefix* "TEST_ERROR:"]
      (f))))

(defmacro with-conn [conn-binding & body]
  `(let [uri# (str "datomic:mem:registration-rollback-test-" (UUID/randomUUID))
         ~conn-binding (do
                         (d/create-database uri#)
                         (d/connect uri#))]
     (try ~@body
          (finally (d/delete-database uri#)))))

(defn- seed-schema [conn]
  @(d/transact conn schema/all-schemas))

(defn- find-user [db username]
  (when-let [e (d/q '[:find ?e . :in $ ?u :where [?e :orcpub.user/username ?u]] db username)]
    (d/pull db '[*] e)))

(defn- register-request [conn]
  {:conn conn
   :db (d/db conn)
   :scheme :https
   :headers {"host" "example.test"}
   ;; verify-email is required by registration/validate-registration and must
   ;; match :email, or register returns 400 before reaching do-verification.
   :json-params {:username "newcomer"
                 :email "newcomer@test.com"
                 :verify-email "newcomer@test.com"
                 :password "hunter2hunter2"
                 :send-updates? false}})

(deftest failed-verification-email-leaves-no-account
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      (seed-schema mocked-conn)
      (testing "an SMTP failure must not commit a half-created user"
        (with-redefs [email/configured? (constantly true)
                      routes/send-verification-email
                      (fn [& _] (throw (Exception. "SMTP down")))]
          ;; register rethrows; what matters is the DB state afterwards, not
          ;; which exception surfaced.
          (try (routes/register (register-request mocked-conn))
               (catch Throwable _ nil))
          (let [user (find-user (d/db mocked-conn) "newcomer")]
            (is (nil? user)
                (str "A failed verification email left an account behind. "
                     "Retrying registration then fails validation because the "
                     "username and email are taken -- which is exactly the "
                     "advice the error message gives. (Recoverable via resend, "
                     "see the ns docstring, but a dead end from the form.) "
                     "Found: " (pr-str user)))))))))

(deftest the-address-can-be-reused-after-a-failed-send
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      (seed-schema mocked-conn)
      (testing "after a failed send, the same details still validate as available"
        (with-redefs [email/configured? (constantly true)
                      routes/send-verification-email
                      (fn [& _] (throw (Exception. "SMTP down")))]
          (try (routes/register (register-request mocked-conn))
               (catch Throwable _ nil)))
        ;; Second attempt, this time with a working mailer.
        (with-redefs [email/configured? (constantly true)
                      routes/send-verification-email (fn [& _] nil)]
          (let [resp (routes/register (register-request mocked-conn))]
            (is (= 200 (:status resp))
                (str "Retrying after a failed send must succeed -- this is the "
                     "advice the error message gives the user. Got: " (pr-str resp)))
            (let [user (find-user (d/db mocked-conn) "newcomer")]
              (is (some? user) "the retry should create the account")
              (is (false? (:orcpub.user/verified? user))
                  "and it should be awaiting verification"))))))))

(deftest a-successful-send-still-creates-the-account
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      (seed-schema mocked-conn)
      (testing "the happy path is unchanged by the rollback"
        (with-redefs [email/configured? (constantly true)
                      routes/send-verification-email (fn [& _] nil)]
          (let [resp (routes/register (register-request mocked-conn))
                user (find-user (d/db mocked-conn) "newcomer")]
            (is (= 200 (:status resp)))
            (is (some? user))
            (is (= "newcomer@test.com" (:orcpub.user/email user)))
            (is (false? (:orcpub.user/verified? user)))
            (is (some? (:orcpub.user/verification-key user))
                "a verification key must be stored for the emailed link to resolve")))))))

(deftest re-verify-rollback-must-not-delete-an-existing-user
  ;; The dangerous case. re-verify calls do-verification with an EXISTING
  ;; {:db/id id}, so rolling back with :db/retractEntity would delete a real
  ;; account -- turning a failed resend into data loss. Only the attributes this
  ;; attempt set may be retracted.
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      (seed-schema mocked-conn)
      (with-redefs [email/configured? (constantly true)
                      routes/send-verification-email (fn [& _] nil)]
        (routes/register (register-request mocked-conn)))
      (let [before (find-user (d/db mocked-conn) "newcomer")]
        (is (some? before) "precondition: the account exists")
        (testing "a failed re-send leaves the account intact"
          (with-redefs [email/configured? (constantly true)
                      routes/send-verification-email
                        (fn [& _] (throw (Exception. "SMTP down")))]
            (try (routes/re-verify {:conn mocked-conn
                                    :db (d/db mocked-conn)
                                    :scheme :https
                                    :headers {"host" "example.test"}
                                    :query-params {:email "newcomer@test.com"}})
                 (catch Throwable _ nil)))
          (let [after (find-user (d/db mocked-conn) "newcomer")]
            (is (some? after)
                "THE USER WAS DELETED by a failed verification resend")
            (is (= (:orcpub.user/email before) (:orcpub.user/email after)))
            (is (= (:orcpub.user/password before) (:orcpub.user/password after))
                "credentials must survive a failed resend")
            ;; Not nil: the resend REPLACED the key in the user's inbox before
            ;; the send failed. Retracting the new one left the account with no
            ;; working link at all, so an email that was still valid stopped
            ;; verifying anything. The rollback has to put the old key back.
            (is (= (:orcpub.user/verification-key before)
                   (:orcpub.user/verification-key after))
                "the link already in the user's inbox must still work after a failed resend")
            (is (= (:orcpub.user/verification-sent before)
                   (:orcpub.user/verification-sent after))
                "and its expiry clock must be the original one, not the failed attempt's")))))))
