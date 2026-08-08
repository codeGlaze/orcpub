;; Datomic Database Verification Tool
;;
;; Connects to a running Datomic database and reports statistics: basis-t,
;; user count, and entity (character) count. Used after migration to verify
;; data integrity by comparing pre- and post-migration numbers.
;;
;; Requires the uberjar on the classpath (includes Clojure + Datomic peer):
;;   java -cp target/orcpub.jar clojure.main docker/scripts/migrate-db.clj verify [db-uri]
;;
;; Note: Backup and restore use the bin/datomic CLI (backup-db, restore-db),
;; NOT the Peer API — those functions do not exist in datomic.api.
;; See scripts/migrate-db.sh (bare-metal) or docker-migrate.sh (Docker).

(ns migrate-db
  (:require [datomic.api :as d]))

;; ---------------------------------------------------------------------------
;; Configuration
;; ---------------------------------------------------------------------------

(def ^{:doc "Database URI. Set via DATOMIC_URL env var."}
  datomic-url
  (or (System/getenv "DATOMIC_URL")
      "datomic:dev://datomic:4334/orcpub"))

;; ---------------------------------------------------------------------------
;; Database statistics — used for pre/post migration verification
;; ---------------------------------------------------------------------------

(defn db-stats
  "Returns a map of database statistics: basis-t, user count, entity count."
  [db]
  (let [users    (or (d/q '[:find (count ?e) .
                             :where [?e :orcpub.user/username]]
                           db) 0)
        entities (or (d/q '[:find (count ?e) .
                             :where [?e :orcpub.entity/owner]]
                           db) 0)]
    {:basis-t  (d/basis-t db)
     :users    users
     :entities entities}))

(defn print-stats
  "Prints database statistics in a readable format."
  [stats]
  (println (format "  Basis-t:    %d" (:basis-t stats)))
  (println (format "  Users:      %d" (:users stats)))
  (println (format "  Characters: %d" (:entities stats))))

;; ---------------------------------------------------------------------------
;; Verify — connects to a database and reports stats
;; ---------------------------------------------------------------------------

(defn verify!
  "Connects to a database and reports statistics."
  [db-uri]
  (println "Connecting to" db-uri "...")
  (let [conn  (d/connect db-uri)
        db    (d/db conn)
        stats (db-stats db)]
    (println "Database statistics:")
    (print-stats stats)
    stats))

;; ---------------------------------------------------------------------------
;; CLI dispatch
;; ---------------------------------------------------------------------------

(let [args *command-line-args*
      cmd  (first args)]
  (try
    (case cmd
      "verify" (let [[_ db-uri] args]
                 (verify! (or db-uri datomic-url)))

      ;; Default: show usage
      (do
        (println "Datomic Database Verification Tool")
        (println)
        (println "Usage:")
        (println "  migrate-db.clj verify [uri]    Report database stats (defaults to DATOMIC_URL)")
        (println)
        (println "Environment:")
        (println "  DATOMIC_URL    Database URI (default: datomic:dev://datomic:4334/orcpub)")
        (println)
        (println "For backup and restore, use the bin/datomic CLI:")
        (println "  bin/datomic backup-db  <from-db-uri> <to-backup-uri>")
        (println "  bin/datomic restore-db <from-backup-uri> <to-db-uri>")
        (System/exit 1)))
    (catch Exception e
      (binding [*out* *err*]
        (println "FATAL:" (.getMessage e)))
      (System/exit 1)))
  ;; Datomic peer threads are non-daemon; force clean exit
  (System/exit 0))
