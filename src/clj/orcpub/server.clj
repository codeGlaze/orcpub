(ns orcpub.server
  (:require [orcpub.config :as config]
            [orcpub.system :as s]
            [com.stuartsierra.component :as component])
  (:gen-class))

(defn -main
  "Starts the production server, or prints why it may not (`config/start-refusal`) and exits 1."
  []
  (when-let [why (config/start-refusal)]
    (binding [*out* *err*] (println why))
    (System/exit 1))
  ;; The banner prints from the Pedestal component, between create-server and start:
  ;; http/start blocks in prod, so nothing after this line would ever run.
  (component/start (s/system :prod)))
