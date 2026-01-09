(ns orcpub.config
  (:require [environ.core :refer [env]]))

(def default-datomic-uri "datomic:dev://localhost:4334/orcpub")

(defn datomic-env
  "Return the raw DATOMIC_URL environment value or nil if unset." []
  (or (env :datomic-url)
      (some-> (System/getenv "DATOMIC_URL") not-empty)))

(defn get-datomic-uri
  "Return the Datomic URI from the environment or the default.

  Prefers the raw env value (from `datomic-env`), otherwise returns a safe
  local development default (datomic:dev://localhost:4334/orcpub)."
  []
  (or (datomic-env)
      default-datomic-uri))
