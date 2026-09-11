(ns orcpub.server
  (:require [orcpub.system :as s]
            [com.stuartsierra.component :as component])
  (:gen-class))

(defn -main []
  ;; The banner prints from the Pedestal component, between create-server and start:
  ;; http/start blocks in prod, so nothing after this line would ever run.
  (component/start (s/system :prod)))
