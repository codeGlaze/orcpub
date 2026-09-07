(ns orcpub.server
  (:require [orcpub.system :as s]
            [orcpub.config :as config]
            [com.stuartsierra.component :as component])
  (:gen-class))

(defn -main []
  (let [system (component/start (s/system :prod))]
    ;; After start, not before: an operator reading "started" should be able to trust it.
    (config/print-report!)
    system))
