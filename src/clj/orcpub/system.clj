(ns orcpub.system
  (:require [com.stuartsierra.component :as component]       
            [reloaded.repl :as rrepl]
            [io.pedestal.http :as http]
            [orcpub.pedestal :as pedestal]                         
            [orcpub.routes :as routes]
            [orcpub.datomic :as datomic]
            [orcpub.config :as config]
            [orcpub.pdf :as pdf]
            [environ.core :as environ])
  (:import (org.eclipse.jetty.server.handler.gzip GzipHandler)))

(def max-form-content-size
  "Ceiling on a request body, in bytes.

   Generous for a character spec -- the largest observed PDF request is well
   under this -- while leaving headroom for a character image sent as bytes
   instead of a URL."
  (* 2 1024 1024))

(def dev-service-map-overrides
  {::http/port 8890
   ;; Bind to loopback in dev. Override per-machine with ORCPUB_HTTP_HOST (e.g.
   ;; "0.0.0.0") when the host needs to reach the dev server by IP — e.g. a Windows
   ;; browser hitting a WSL VM, where localhost-forwarding can be flaky. Defaults to
   ;; loopback so nothing is exposed to the LAN unless you opt in.
   ::http/host (or (environ/env :orcpub-http-host) "localhost")
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
   ::http/port (let [port-str (or (System/getenv "PORT") "8890")]
                 (try
                   (Integer/parseInt port-str)
                   (catch NumberFormatException e
                     (throw (ex-info "Invalid PORT environment variable. Expected a number."
                                     {:error :invalid-port
                                      :port port-str}
                                     e)))))
   ::http/join false
   ::http/resource-path "/public"
   ;; CSP configured via CSP_POLICY env var (strict|permissive|none)
   ;; See orcpub.config for details
   ::http/secure-headers (config/get-secure-headers-config)
   ;; Jetty's worker pool caps how many requests of any kind are in flight.
   ;; Pedestal's own default is 50 until roughly sixteen cores, which is well
   ;; under what a large host can carry; ORCPUB_HTTP_MAX_THREADS raises it, and
   ;; unset leaves Pedestal to decide. Exports are bounded separately -- see
   ;; docs/PDF-EXPORT-CAPACITY.md.
   ::http/container-options (cond-> {:context-configurator (fn [c]
                                                     (let [gzip-handler (GzipHandler.)]
                                                       (.setGzipHandler c gzip-handler)
                                                       ;; Cap what a request body may be before it reaches
                                                       ;; a handler. /character.pdf is unauthenticated and
                                                       ;; parses its body with edn/read-string, and a 50MB
                                                       ;; POST was neither rejected nor completed -- the
                                                       ;; connection simply stayed open. 2MB is generous for
                                                       ;; a character spec and leaves room for an image
                                                       ;; supplied as bytes rather than a URL.
                                                       (.setMaxFormContentSize c max-form-content-size)
                                                       ;; A body with an absurd number of distinct keys is
                                                       ;; the other shape of the same attack.
                                                       (.setMaxFormKeys c 200)
                                                       c))}
                              (config/get-http-max-threads)
                              (assoc :max-threads (config/get-http-max-threads)))})

(defn system [env]
  ;; Which image-fetch egress path is live is invisible until an export fails,
  ;; and the two fail very differently. One line at boot says which.
  (pdf/report-image-egress!)
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
