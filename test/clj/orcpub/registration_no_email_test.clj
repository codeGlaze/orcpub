(ns orcpub.registration-no-email-test
  "Registration on a deployment with no SMTP configured.

   .env.example offers an empty EMAIL_SERVER_URL as the way to run without email.
   Nothing implemented that: the variable was read in exactly one place, as
   postal's :host, so leaving it blank did not disable email -- it made every
   send FAIL. Registration sends a verification mail, and login refuses an
   unverified account (routes.clj:301), so the documented way to turn email off
   was also the way to make the site unusable: nobody could register, and any
   account that did exist could not log in.

   do-verification now verifies on creation when email is unconfigured. The
   operator of a mail-less instance is handing out accounts themselves, so
   nobody was proving address ownership either way.

   The decisive test here is the login one. Everything else is bookkeeping;
   being able to sign in is the thing that was broken."
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

(defn- fresh-conn []
  (let [uri (str "datomic:mem:rne-" (UUID/randomUUID))]
    (d/create-database uri)
    (let [c (dm/fork-conn (d/connect uri))]
      @(d/transact c schema/all-schemas)
      c)))

(defn- register-request [conn]
  {:conn conn
   :db (d/db conn)
   :scheme :https
   :headers {"host" "example.test"}
   :json-params {:username "newcomer"
                 :email "newcomer@test.com"
                 :verify-email "newcomer@test.com"
                 :password "hunter2hunter2"
                 :send-updates? false}})

(defn- find-user [db]
  (when-let [e (d/q '[:find ?e . :where [?e :orcpub.user/username "newcomer"]] db)]
    (d/pull db '[*] e)))

(deftest no-smtp-verifies-on-creation
  (let [conn (fresh-conn)]
    (testing "registration succeeds and the account is already verified"
      (with-redefs [email/configured? (constantly false)
                    email/unverified-registration-allowed? (constantly true)
                    ;; If this is ever called the test should fail loudly: the
                    ;; whole point is that no send is attempted.
                    routes/send-verification-email
                    (fn [& _] (throw (AssertionError. "tried to send mail with no SMTP configured")))]
        (let [resp (routes/register (register-request conn))
              user (find-user (d/db conn))]
          (is (= 200 (:status resp)))
          (is (true? (get-in resp [:body :verified?]))
              "the client branches on this to show \"you can log in\" instead of \"check your email\"")
          (is (true? (:orcpub.user/verified? user)))
          (is (nil? (:orcpub.user/verification-key user))
              "no key should be stored for a link that is never sent"))))))

(deftest an-auto-verified-account-can-actually-log-in
  ;; The one that matters. login-response refuses an unverified account, so
  ;; before this change a mail-less instance produced accounts nobody could use.
  (let [conn (fresh-conn)]
    (with-redefs [email/configured? (constantly false)
                    email/unverified-registration-allowed? (constantly true)
                  routes/send-verification-email (fn [& _] nil)]
      (routes/register (register-request conn)))
    (testing "the account registered without SMTP can sign in"
      (let [resp (routes/login-response
                  {:conn conn
                   :db (d/db conn)
                   :remote-addr "127.0.0.1"
                   :json-params {:username "newcomer" :password "hunter2hunter2"}})]
        (is (= 200 (:status resp))
            (str "Login was refused for an account created on a mail-less "
                 "instance. Got: " (pr-str (select-keys resp [:status :body]))))))))

(deftest with-smtp-configured-nothing-changes
  (let [conn (fresh-conn)
        sent (atom 0)]
    (testing "the normal flow still creates an unverified account and mails a link"
      (with-redefs [email/configured? (constantly true)
                    email/unverified-registration-allowed? (constantly false)
                    routes/send-verification-email (fn [& _] (swap! sent inc) nil)]
        (let [resp (routes/register (register-request conn))
              user (find-user (d/db conn))]
          (is (= 200 (:status resp)))
          (is (nil? (get-in resp [:body :verified?]))
              "no :verified? flag, so the client shows \"check your email\"")
          (is (false? (:orcpub.user/verified? user)))
          (is (some? (:orcpub.user/verification-key user)))
          (is (= 1 @sent) "exactly one verification email"))))))

(deftest losing-smtp-config-fails-closed
  ;; The security case. Auto-verify keyed on "no SMTP" ALONE fails open: a
  ;; typo'd variable name, a value dropped by a deploy, a failed secrets mount
  ;; or a stray space all read as "no email", and a production site silently
  ;; stops requiring verification. Measured before this guard: " ", "" and
  ;; absent all produced auto-verify.
  ;;
  ;; Not attacker-triggerable -- environ.core/env is a static map built once at
  ;; namespace load, so no request can flip it. The risk is one operator slip
  ;; downgrading the site to open registration with no alarm.
  (let [conn (fresh-conn)]
    (testing "no SMTP and no explicit opt-in refuses to register anyone"
      (with-redefs [email/configured? (constantly false)
                    email/unverified-registration-allowed? (constantly false)
                    routes/send-verification-email
                    (fn [& _] (throw (AssertionError. "must not attempt a send")))]
        (let [thrown (try (routes/register (register-request conn)) nil
                          (catch Throwable e e))]
          (is (some? thrown)
              "registration must FAIL when email config is missing and nothing opted out")
          (is (= :email-not-configured (:error (ex-data thrown)))
              (str "expected a specific, actionable error. Got: " (pr-str (ex-data thrown))))
          (is (nil? (find-user (d/db conn)))
              "and no account may be created, verified or otherwise"))))))
