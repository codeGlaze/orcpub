(ns orcpub.dependencies.integration-test
  "Integration tests to validate Jackson 2.15.2 and Guava 32.1.2-jre upgrades.
   Tests actual runtime behavior of upgraded dependencies."
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require [clojure.test :refer [deftest testing is]]
            [cheshire.core :as json]
            [datomic.api :as d])
  (:import [com.google.common.collect ImmutableList ImmutableMap]
           [com.google.common.base Strings]
           [com.fasterxml.jackson.databind ObjectMapper]
           [java.util UUID]))

(deftest test-jackson-json-serialization
  (testing "Jackson can serialize complex Clojure data structures"
    (let [data {:name "Test Character"
                :level 5
                :abilities {:str 18 :dex 14 :con 16}
                :skills ["athletics" "perception"]
                :nested {:deep {:value 42}}}
          json-str (json/generate-string data)
          parsed (json/parse-string json-str true)]
      (is (string? json-str))
      (is (= "Test Character" (:name parsed)))
      (is (= 5 (:level parsed)))
      (is (= 18 (get-in parsed [:abilities :str])))
      (is (= ["athletics" "perception"] (:skills parsed)))
      (is (= 42 (get-in parsed [:nested :deep :value]))))))

(deftest test-jackson-handles-edge-cases
  (testing "Jackson 2.15.2 handles edge cases that had CVEs in 2.11.x"
    ;; Test deeply nested objects (related to CVE-2020-36518)
    (let [deeply-nested (reduce (fn [acc _] {:nested acc})
                                {:value "deep"}
                                (range 50))
          json-str (json/generate-string deeply-nested)
          parsed (json/parse-string json-str true)]
      (is (map? parsed))
      (is (contains? parsed :nested)))
    
    ;; Test with nil values
    (let [data {:key nil :other "value"}
          json-str (json/generate-string data)
          parsed (json/parse-string json-str true)]
      (is (nil? (:key parsed)))
      (is (= "value" (:other parsed))))))

(deftest test-jackson-object-mapper-direct
  (testing "Jackson ObjectMapper works directly (used by Pedestal)"
    (let [mapper (ObjectMapper.)
          data {"name" "Direct Test" "value" 123}
          json-str (.writeValueAsString mapper data)
          parsed (.readValue mapper json-str java.util.Map)]
      (is (string? json-str))
      (is (= "Direct Test" (.get parsed "name")))
      (is (= 123 (.get parsed "value"))))))

(deftest test-guava-immutable-collections
  (testing "Guava 32.1.2-jre ImmutableList works correctly"
    (let [list (ImmutableList/of "a" "b" "c")]
      (is (= 3 (.size list)))
      (is (= "a" (.get list 0)))
      (is (= "c" (.get list 2)))
      (is (thrown? UnsupportedOperationException
                   (.add list "d")))))
  
  (testing "Guava ImmutableMap works correctly"
    (let [map (ImmutableMap/of "key1" "value1" "key2" "value2")]
      (is (= 2 (.size map)))
      (is (= "value1" (.get map "key1")))
      (is (thrown? UnsupportedOperationException
                   (.put map "key3" "value3"))))))

(deftest test-guava-strings-utility
  (testing "Guava Strings utility class works"
    (is (true? (Strings/isNullOrEmpty nil)))
    (is (true? (Strings/isNullOrEmpty "")))
    (is (false? (Strings/isNullOrEmpty "test")))
    (is (= "test" (Strings/nullToEmpty "test")))
    (is (= "" (Strings/nullToEmpty nil)))
    (is (= "aaa" (Strings/repeat "a" 3)))))

(deftest test-json-round-trip-with-character-data
  (testing "JSON round-trip with realistic D&D character data"
    (let [character {:name "Thorin Oakenshield"
                     :race "Dwarf"
                     :class "Fighter"
                     :level 10
                     :hp 95
                     :ac 18
                     :abilities {:str 18 :dex 12 :con 16 :int 10 :wis 14 :cha 8}
                     :proficiencies ["athletics" "intimidation" "perception"]
                     :equipment [{:name "Battleaxe" :damage "1d8" :type "slashing"}
                                 {:name "Shield" :ac-bonus 2}]
                     :features ["Action Surge" "Second Wind" "Extra Attack"]}
          json-str (json/generate-string character)
          parsed (json/parse-string json-str true)]
      ;; Verify all fields round-trip correctly
      (is (= (:name character) (:name parsed)))
      (is (= (:class character) (:class parsed)))
      (is (= (:level character) (:level parsed)))
      (is (= (get-in character [:abilities :str]) 
             (get-in parsed [:abilities :str])))
      (is (= (count (:proficiencies character))
             (count (:proficiencies parsed))))
      (is (= (count (:equipment character))
             (count (:equipment parsed))))
      (is (= "Battleaxe" (get-in parsed [:equipment 0 :name]))))))

(deftest test-compatibility-with-existing-code
  (testing "Upgraded dependencies don't break existing json-params usage"
    ;; Simulate what happens in routes when processing JSON request bodies
    (let [simulated-json-params {:username "testuser"
                                 :email "test@example.com"
                                 :password "secret123"
                                 :send-updates? true}
          ;; Convert to JSON and back (simulating HTTP request/response)
          json-str (json/generate-string simulated-json-params)
          parsed (json/parse-string json-str true)]
      (is (= "testuser" (:username parsed)))
      (is (= "test@example.com" (:email parsed)))
      (is (= true (:send-updates? parsed))))))

(deftest test-datomic-pro-basic-connectivity
  (testing "Datomic Pro can create in-memory database and perform basic operations"
    (let [uri (str "datomic:mem://test-db-" (UUID/randomUUID))]
      ;; Test database creation
      (is (true? (d/create-database uri)) "Database creation succeeds")

      ;; Test connection
      (let [conn (d/connect uri)]
        (is (some? conn) "Connection created successfully")

        ;; Test schema transaction
        (let [schema [{:db/ident :person/name
                       :db/valueType :db.type/string
                       :db/cardinality :db.cardinality/one
                       :db/doc "A person's name"}]]
          (is (some? @(d/transact conn schema)) "Schema transaction succeeds"))

        ;; Test data insertion and query
        (is (some? @(d/transact conn [{:person/name "Test User"}])) "Data insertion succeeds")

        (let [db (d/db conn)
              result (d/q '[:find ?name :where [_ :person/name ?name]] db)]
          (is (= #{["Test User"]} result) "Query returns expected result"))

        ;; Test database deletion
        (is (true? (d/delete-database uri)) "Database deletion succeeds")))))
