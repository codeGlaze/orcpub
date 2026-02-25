(ns orcpub.system
  (:require [com.stuartsierra.component :as component]       
            [reloaded.repl :as rrepl]
            [io.pedestal.http :as http]
            [orcpub.pedestal :as pedestal]                         
            [orcpub.routes :as routes]
            [orcpub.datomic :as datomic]
            [orcpub.config :as config]
            [environ.core :as environ])
  (:import (org.eclipse.jetty.server.handler.gzip GzipHandler)))

(def dev-service-map-overrides
  {::http/port 8890
   ;; Bind to loopback only in dev (don't expose to LAN)
   ::http/host "localhost"
   ;; do not block thread that starts web server
   ::http/join? false
   ;; Routes can be a function that resolve routes,
   ;;  we can use this to set the routes to be reloadable
   ::http/routes #(deref #'routes/routes)
   ;; all origins are allowed in dev mode
   ::http/allowed-origins {:creds true :allowed-origins (constantly true)}
   ;; CSP now enabled in dev mode too (catches issues early).
   ;; Uses same config as prod - nonce-interceptor handles strict mode dynamically.
   ;; See orcpub.config/get-secure-headers-config
   })

(def prod-service-map
  {::http/routes routes/routes
   ::http/type :jetty
   ;; Bind to all interfaces so the server is reachable from other Docker
   ;; containers (nginx proxy, healthchecks) and external clients.
   ;; Pedestal defaults to "localhost" when unset, which only binds loopback.
   ::http/host "0.0.0.0"
   ;; Pedestal 0.7+ requires explicit interceptor coercion for maps/functions
   ::http/enable-session false  ; Disable default session handling if not needed
   ::http/port (let [port-str (System/getenv "PORT")]
                 (when port-str
                   (try
                     (Integer/parseInt port-str)
                     (catch NumberFormatException e
                       (throw (ex-info "Invalid PORT environment variable. Expected a number."
                                       {:error :invalid-port
                                        :port port-str}
                                       e))))))
   ::http/join false
   ::http/resource-path "/public"
   ;; CSP configured via CSP_POLICY env var (strict|permissive|none)
   ;; See orcpub.config for details
   ::http/secure-headers (config/get-secure-headers-config)
   ::http/container-options {:context-configurator (fn [c]
                                                     (let [gzip-handler (GzipHandler.)]
                                                       (.setGzipHandler c gzip-handler)
                                                       c))}})

(defn system [env]
  (component/system-map
    :conn
    (datomic/new-datomic
      (config/get-datomic-uri))

    :service-map
    (cond-> (merge
              {:env env}
              prod-service-map
              (when (= :dev env) dev-service-map-overrides))
      true http/default-interceptors
      (= :dev env) http/dev-interceptors)

    :pedestal
    (component/using
      (pedestal/new-pedestal)
      [:service-map :conn])))

(rrepl/set-init! #(system :prod))
