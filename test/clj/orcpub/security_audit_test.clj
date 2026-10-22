(ns orcpub.security-audit-test
  "Security events leave one log line each, naming the account and the addresses it came from, and
   never a password, an email address, or a name typed for an account that does not exist."
  (:require [clojure.string :as s]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [datomic.api :as d]
            [io.pedestal.interceptor :as interceptor]
            [orcpub.db.schema :as schema]
            [orcpub.email :as email]
            [orcpub.routes :as routes]
            [orcpub.security :as security])
  (:import [java.util UUID]))

(use-fixtures :each
  (fn [f]
    (with-redefs [security/registration-allowed? (constantly true)
                  security/multiple-account-access? (constantly false)
                  security/too-many-attempts-for-username? (constantly false)
                  security/multiple-ip-attempts-to-same-account? (constantly false)
                  email/configured? (constantly true)]
      (f))))

(defn- fresh-conn
  "A connection to a new in-memory database with the full schema."
  []
  (let [uri (str "datomic:mem:security-audit-" (UUID/randomUUID))]
    (d/create-database uri)
    (doto (d/connect uri) (-> (d/transact schema/all-schemas) deref))))

(defn- request
  "A request against `conn` from 10.0.0.9, forwarded for 203.0.113.7, with `extra` merged in."
  [conn extra]
  (merge {:conn conn :db (d/db conn) :scheme :https :remote-addr "10.0.0.9"
          :headers {"host" "example.test" "x-forwarded-for" "203.0.113.7, 10.0.0.9"}}
         extra))

(defn- security-lines
  "The `security:` lines `f` prints."
  [f]
  (filter #(s/starts-with? % "security: ") (s/split-lines (with-out-str (f)))))

(defn- sign-up!
  "Registers newcomer / newcomer@test.com in `conn` and marks the account verified."
  [conn]
  (routes/register (request conn {:json-params {:username "newcomer" :email "newcomer@test.com"
                                                :verify-email "newcomer@test.com"
                                                :password "hunter2hunter2" :send-updates? false}}))
  (let [id (d/q '[:find ?e . :where [?e :orcpub.user/username "newcomer"]] (d/db conn))]
    @(d/transact conn [{:db/id id :orcpub.user/verified? true}])))

(deftest a-sign-up-is-logged-without-the-email-or-password
  (let [conn (fresh-conn)
        [line & more] (security-lines #(sign-up! conn))]
    (is (and line (empty? more)) "one line")
    (is (= "security: registered user=newcomer ip=10.0.0.9 fwd=203.0.113.7, 10.0.0.9" line))))

(deftest sign-ins-are-logged-and-a-stray-name-is-not
  (let [conn (fresh-conn)]
    (sign-up! conn)
    (testing "a good sign-in"
      (let [lines (security-lines #(routes/login-response
                                    (request conn {:json-params {:username "newcomer" :password "hunter2hunter2"}})))]
        (is (= ["security: sign-in user=newcomer ip=10.0.0.9 fwd=203.0.113.7, 10.0.0.9"] lines))))
    (testing "a wrong password on a real account names the account"
      (let [lines (security-lines #(routes/login-response
                                    (request conn {:json-params {:username "newcomer" :password "wrong-one"}})))]
        (is (= ["security: sign-in-failed user=newcomer ip=10.0.0.9 fwd=203.0.113.7, 10.0.0.9"] lines))
        (is (not-any? #(s/includes? % "wrong-one") lines) "never the password")))
    (testing "a name with no account is not written down: it may be a password in the wrong box"
      (let [lines (security-lines #(routes/login-response
                                    (request conn {:json-params {:username "MySecretPass1" :password "x"}})))]
        (is (= ["security: sign-in-failed ip=10.0.0.9 fwd=203.0.113.7, 10.0.0.9"] lines))))))

(deftest a-deleted-account-is-logged
  (let [conn (fresh-conn)]
    (sign-up! conn)
    (is (= ["security: account-deleted user=newcomer ip=10.0.0.9 fwd=203.0.113.7, 10.0.0.9"]
           (security-lines #(routes/delete-user (request conn {:identity {:user "newcomer"}})))))))

(deftest a-bad-token-is-logged
  (let [enter (:enter (interceptor/interceptor routes/check-auth))
        lines (security-lines #(enter {:request {:remote-addr "10.0.0.9"
                                                 :headers {"authorization" "Token not-a-token"}}}))]
    (is (= ["security: token-invalid ip=10.0.0.9"] lines))))

(deftest a-name-cannot-forge-a-log-line
  (let [lines (security-lines #(security/audit! :sign-in {:remote-addr "10.0.0.9"}
                                                "kaylee\nsecurity: account-deleted user=admin"))]
    (is (= 1 (count lines)) "the line break is removed, so no second line appears")))
