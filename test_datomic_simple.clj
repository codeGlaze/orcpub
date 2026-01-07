(println "Testing Datomic Pro...")

(try
  (require 'datomic.api)
  (println "SUCCESS: Datomic API loaded")

  ;; Create a database URI that connects to the running transactor
  ;; Format: datomic:<protocol>://<host>:<port>/<db-name>
  (def uri "datomic:free://localhost:4334/test-db")
  (println "Connecting to database:" uri)

  ;; Create the database
  (d/create-database uri)
  (println "Database created successfully")

  ;; Connect to the database
  (def conn (d/connect uri))
  (println "Connected to database successfully")

  ;; Add some test schema and data
  (def schema [{:db/ident :person/name
                :db/valueType :db.type/string
                :db/cardinality :db.cardinality/one
                :db/doc "A person's name"}])

  (println "Transacting schema...")
  @(d/transact conn schema)
  (println "Schema transaction completed")

  (println "Adding test data...")
  @(d/transact conn [{:person/name "Alice"} {:person/name "Bob"}])
  (println "Test data inserted")

  ;; Query the data
  (def db (d/db conn))
  (def result (d/q '[:find ?name :where [_ :person/name ?name]] db))
  (println "Query result:" result)
  (println "Found" (count result) "people")

  ;; Clean up
  (d/delete-database uri)
  (println "Database deleted")

  (println "✅ Datomic Pro test PASSED!")

  (catch Exception e
    (println "ERROR:" (.getMessage e))
    (println "Stack trace:")
    (.printStackTrace e)))

(println "Test complete.")