(ns e2e-boot
  "Boots the real server against an in-memory Datomic db and seeds a verified
   user, in ONE JVM -- a mem:// database only exists inside the process that
   created it, so seeding from a separate `lein run` would talk to a different,
   empty database.

   Used by scripts/e2e.sh. Not part of the production uberjar."
  (:require [com.stuartsierra.component :as component]
            [orcpub.system :as s]
            [user :as dev]))

(defn -main [& _]
  (let [sys (component/start (s/system :dev))
        conn (get-in sys [:conn :conn])]
    (dev/create-user! conn {:username "kaylee"
                            :email "kaylee@example.com"
                            :password "serenity99"
                            :verify? true})
    (println "E2E-READY")
    (flush)
    @(promise)))
