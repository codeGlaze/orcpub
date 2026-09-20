(ns orcpub.config-redaction-test
  "The Datomic SQL URI carries the database password in plain sight, and it was being
   printed at boot and embedded in three ex-info maps. These pin the redaction so it cannot
   regress quietly -- a leak of this kind is invisible until someone reads a log."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [orcpub.config :as config]))

(def ^:private real-shape
  "The shape from the production boot log, which is what prompted this."
  "datomic:sql://datomic?jdbc:postgresql://dev_dmv_postgres:5432/datomic?user=datomic&password=hunter2")

(deftest password-never-survives
  (testing "the query-parameter form"
    (let [out (config/redact-secrets real-shape)]
      (is (not (str/includes? out "hunter2")) out)
      (is (str/includes? out "password=****") out)))
  (testing "the parts that are not secret are kept, so the log stays useful"
    (let [out (config/redact-secrets real-shape)]
      (is (str/includes? out "dev_dmv_postgres:5432"))
      (is (str/includes? out "user=datomic"))
      (is (str/starts-with? out "datomic:sql://"))))
  (testing "userinfo form"
    (is (= "postgresql://datomic:****@host:5432/db"
           (config/redact-secrets "postgresql://datomic:hunter2@host:5432/db"))))
  (testing "other credential parameter names"
    (doseq [k ["password" "passwd" "pwd" "secret" "token" "api-key" "api_key" "apikey"]]
      (let [out (config/redact-secrets (str "datomic:sql://x?jdbc:p://h/db?" k "=hunter2"))]
        (is (not (str/includes? out "hunter2")) (str k " leaked: " out)))))
  (testing "case is not a way around it"
    (is (not (str/includes? (config/redact-secrets "x?PASSWORD=hunter2") "hunter2")))))

(deftest harmless-uris-are-left-alone
  (testing "the local shapes still read normally in a log"
    (doseq [u ["datomic:mem://orcpub"
               "datomic:dev://localhost:4334/orcpub"
               "datomic:sql://datomic?jdbc:postgresql://host:5432/datomic?user=datomic"]]
      (is (= u (config/redact-secrets u)) u))))

(deftest does-not-blow-up
  (testing "nil in, nil out -- callers log whatever they have"
    (is (nil? (config/redact-secrets nil))))
  (testing "empty and non-string"
    (is (= "" (config/redact-secrets "")))
    (is (= "1234" (config/redact-secrets 1234)))))

(deftest only-the-secret-is-cut
  (testing "a password containing & or = stops at the parameter boundary, and what
            follows it survives"
    (let [out (config/redact-secrets "jdbc:p://h/db?password=a=b&user=datomic&ssl=true")]
      (is (not (str/includes? out "a=b")) out)
      (is (str/includes? out "user=datomic") out)
      (is (str/includes? out "ssl=true") out))))
