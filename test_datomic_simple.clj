(println "Testing Datomic Pro...")

(try
  (require 'datomic.api)
  (println "SUCCESS: Datomic API loaded")
  (println "Available functions:" (keys (ns-publics 'datomic.api)))
  (catch Exception e
    (println "ERROR loading datomic.api:" (.getMessage e))
    (println "Stack trace:" (.printStackTrace e))))

(println "Test complete.")