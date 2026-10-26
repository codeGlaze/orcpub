(ns orcpub.server
  (:require [orcpub.config :as config]
            [orcpub.system :as s]
            [com.stuartsierra.component :as component])
  (:import (java.util Locale))
  (:gen-class))

(defn pin-locale!
  "Fixes the JVM default locale to Locale/ROOT. Case folding and number formatting
   otherwise follow the operator's machine: a Turkish server filed IVAN@x.com under
   ıvan@x.com and wrote 5,00 into PDF paths. Text shown to a user takes its locale
   explicitly; nothing should inherit the machine's. docs/kb/locale-safety.md."
  []
  (Locale/setDefault Locale/ROOT))

(defn -main
  "Pins the locale, then starts the production server, or prints why it may not
   (`config/start-refusal`) and exits 1."
  []
  (pin-locale!)
  (when-let [why (config/start-refusal)]
    (binding [*out* *err*] (println why))
    (System/exit 1))
  ;; The banner prints from the Pedestal component, between create-server and start:
  ;; http/start blocks in prod, so nothing after this line would ever run.
  (component/start (s/system :prod)))
