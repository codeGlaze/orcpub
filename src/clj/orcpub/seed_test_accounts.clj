(ns orcpub.seed-test-accounts
  "CLI helper to seed or check test accounts in Datomic.

  Usage:
    lein with-profile +no-prep run -m orcpub.seed-test-accounts
    lein with-profile +no-prep run -m orcpub.seed-test-accounts path.edn

  Steps:
    1) Starts the app server (user/start-server)
    2) Reads an EDN vector of account maps
    3) Calls user/ensure-test-accounts! to create+verify any missing users
    4) Prints a short existence report

  To customize:
    - Copy dev/test-accounts-example.edn to dev/test-accounts.edn
    - Edit usernames/emails/passwords in the copy (dev/test-accounts.edn is gitignored)
  "
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [user :as u]))

(def default-path "dev/test-accounts.edn")

(defn- load-accounts [path]
  (let [f (io/file path)]
    (when-not (.exists f)
      (throw (ex-info (str "Accounts file not found: " path
                           "\nCopy dev/test-accounts-example.edn to " default-path " and edit it, or pass a custom path.")
                      {:path path})))
    (with-open [r (io/reader f)]
      (edn/read (java.io.PushbackReader. r)))))

(defn- print-report [accounts]
  (println "---------------------------")
  (println "Test account status:")
  (doseq [{:keys [username email exists?]} (u/list-test-accounts accounts)]
    (println (format "  %-15s %-30s %s" username email (if exists? "EXISTS" "MISSING -> will create"))))
  (println "---------------------------"))

(defn -main [& [path]]
  (let [accounts (load-accounts (or path default-path))]
    (println "Starting server...")
    (u/start-server)
    (println "Ensuring test accounts...")
    (u/ensure-test-accounts! accounts)
    (print-report accounts)
    (shutdown-agents)))
