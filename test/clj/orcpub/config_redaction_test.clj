(ns orcpub.config-redaction-test
  "The Datomic SQL URI carries the database password in plain sight, and it was being
   printed at boot and embedded in three ex-info maps. These pin the redaction so it cannot
   regress quietly -- a leak of this kind is invisible until someone reads a log."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [orcpub.config :as config]))

(def ^:private real-shape
  "The shape from the production boot log, which is what prompted this."
  "datomic:sql://datomic?jdbc:postgresql://db:5432/datomic?user=datomic&password=hunter2")

(deftest password-never-survives
  (testing "the query-parameter form"
    (let [out (config/redact-secrets real-shape)]
      (is (not (str/includes? out "hunter2")) out)
      (is (str/includes? out "password=****") out)))
  (testing "the parts that are not secret are kept, so the log stays useful"
    (let [out (config/redact-secrets real-shape)]
      (is (str/includes? out "db:5432"))
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

(deftest prefixed-password-names-are-cut
  (testing "pgJDBC's sslpassword (the client-key password) and other names ending in password"
    (doseq [k ["sslpassword" "sslPassword" "keystore-password"]]
      (let [out (config/redact-secrets (str "datomic:sql://x?jdbc:p://h/db?user=d&" k "=hunter2"))]
        (is (not (str/includes? out "hunter2")) (str k " leaked: " out))
        (is (str/includes? out "user=d") out)))))

(defn- uri-with
  "Returns `config/get-datomic-uri` with DATOMIC_URL read as `url` and DATOMIC_PASSWORD as `pw`."
  [url pw]
  (with-redefs [config/datomic-env (constantly url)
                config/datomic-password (constantly pw)]
    (config/get-datomic-uri)))

(deftest datomic-password-joins-the-innermost-query
  (testing "a URL with no query of its own gets ?password="
    (is (= "datomic:dev://localhost:4334/orcpub?password=pw"
           (uri-with "datomic:dev://localhost:4334/orcpub" "pw")))
    (is (= "datomic:sql://orcpub?jdbc:postgresql://db:5432/datomic?password=pw"
           (uri-with "datomic:sql://orcpub?jdbc:postgresql://db:5432/datomic" "pw"))))
  (testing "a JDBC URL that already has a query gets &password=, never a second ?"
    (is (= "datomic:sql://orcpub?jdbc:postgresql://db:5432/datomic?user=datomic&password=pw"
           (uri-with "datomic:sql://orcpub?jdbc:postgresql://db:5432/datomic?user=datomic" "pw"))))
  (testing "sslpassword= is not the database password, so DATOMIC_PASSWORD is still added"
    (is (= "datomic:sql://o?jdbc:postgresql://db:5432/d?sslpassword=k&password=pw"
           (uri-with "datomic:sql://o?jdbc:postgresql://db:5432/d?sslpassword=k" "pw"))))
  (testing "a password already in the URL, or none set, leaves the URL alone"
    (is (= "datomic:dev://h:4334/o?password=x" (uri-with "datomic:dev://h:4334/o?password=x" "pw")))
    (is (= "datomic:dev://h:4334/o" (uri-with "datomic:dev://h:4334/o" nil)))))
