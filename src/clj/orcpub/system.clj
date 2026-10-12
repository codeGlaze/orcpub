(ns orcpub.system
  (:require [orcpub.env :as env]
            [com.stuartsierra.component :as component]       
            [reloaded.repl :as rrepl]
            [io.pedestal.http :as http]
            [orcpub.pedestal :as pedestal]                         
            [datomic.api :as d]
            [orcpub.routes :as routes]
            [orcpub.datomic :as datomic]
            [orcpub.artist-accounts :as artist-accounts]
            [orcpub.portrait-pack.strands :as strands]
            [orcpub.heartbeat :as heartbeat]
            [orcpub.routes.share :as share]
            [orcpub.pwned :as pwned]
            [orcpub.security :as security]
            [orcpub.config :as config]
            [orcpub.pdf :as pdf])
  (:import (org.eclipse.jetty.server.handler.gzip GzipHandler)))

(def max-form-content-size
  "Ceiling on a request body, in bytes.

   Generous for a character spec -- the largest observed PDF request is well
   under this -- while leaving headroom for a character image sent as bytes
   instead of a URL."
  (* 2 1024 1024))

(defn- configured-port
  "The port to bind, from PORT, defaulting to 8890. Shared by the dev and prod service maps so
   PORT means the same thing in both, and scripts/common.sh probes the port the server is on."
  []
  ;; Blank counts as unset: a bare `PORT=` would otherwise throw while the namespace loads.
  (let [port-str (env/value :port "8890")]
    (try
      (Integer/parseInt port-str)
      (catch NumberFormatException e
        (throw (ex-info "Invalid PORT environment variable. Expected a number."
                        {:error :invalid-port
                         :port port-str}
                        e))))))

(def dev-service-map-overrides
  {::http/port (configured-port)
   ;; Bind to loopback in dev. Override per-machine with ORCPUB_HTTP_HOST (e.g.
   ;; "0.0.0.0") when the host needs to reach the dev server by IP — e.g. a Windows
   ;; browser hitting a WSL VM, where localhost-forwarding can be flaky. Defaults to
   ;; loopback so nothing is exposed to the LAN unless you opt in.
   ::http/host (env/value :orcpub-http-host "localhost")
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

(defn with-portrait-guard
  "The service map with the portrait art guard first in its chain (see routes/portrait-art-guard)."
  [service-map]
  (update service-map ::http/interceptors #(into [routes/portrait-art-guard] %)))

(def prod-service-map
  {::http/routes routes/routes
   ::http/type :jetty
   ;; Bind to all interfaces so the server is reachable from other Docker
   ;; containers (nginx proxy, healthchecks) and external clients.
   ;; Pedestal defaults to "localhost" when unset, which only binds loopback.
   ::http/host "0.0.0.0"
   ;; Pedestal 0.7+ requires explicit interceptor coercion for maps/functions
   ::http/enable-session false  ; Disable default session handling if not needed
   ::http/port (configured-port)
   ::http/join false
   ::http/resource-path "/public"
   ;; CSP configured via CSP_POLICY env var (strict|permissive|none)
   ;; See orcpub.config for details
   ::http/secure-headers (config/get-secure-headers-config)
   ;; Jetty's worker pool caps how many requests of any kind are in flight. Unset
   ;; ORCPUB_HTTP_MAX_THREADS leaves Pedestal's default (50 until roughly sixteen cores).
   ;; Exports are bounded separately -- see docs/PDF-EXPORT-CAPACITY.md.
   ::http/container-options (cond-> {:context-configurator (fn [c]
                                                     (let [gzip-handler (GzipHandler.)]
                                                       (.setGzipHandler c gzip-handler)
                                                       ;; Cap request bodies before a handler:
                                                       ;; /character.pdf is unauthenticated and
                                                       ;; parses its body with edn/read-string.
                                                       ;; The cap leaves room for image bytes.
                                                       (.setMaxFormContentSize c max-form-content-size)
                                                       ;; A body with an absurd number of distinct keys is
                                                       ;; the other shape of the same attack.
                                                       (.setMaxFormKeys c 200)
                                                       c))}
                              (config/get-http-max-threads)
                              (assoc :max-threads (config/get-http-max-threads)))})

(defrecord TokenWithdrawals [conn]
  component/Lifecycle
  (start [this]
    (try
      (let [n (routes/refresh-token-withdrawals! (d/db (:conn conn)))]
        (when (pos? n)
          (println (str "token withdrawals: " n " account(s) whose password moved recently"))))
      (catch Exception e
        ;; Never blocks the boot. An empty register refuses nothing, which is
        ;; where this started -- so failing to load it is no worse than not
        ;; having it, and a server that will not start is worse than both.
        (println "WARNING: could not read recent password changes; tokens from before a"
                 "recent reset stay usable until the heartbeat refreshes:" (.getMessage e))))
    this)
  (stop [this] this))

(defn new-token-withdrawals [] (map->TokenWithdrawals {}))

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
      true with-portrait-guard
      (= :dev env) http/dev-interceptors)

    ;; :token-withdrawals is listed only so Component starts it first; Pedestal
    ;; never reads it. Without it the start order between the two is arbitrary.
    :pedestal
    (component/using
      (pedestal/new-pedestal)
      [:service-map :conn :token-withdrawals])

    ;; Reads the recent password changes back into memory BEFORE the server takes
    ;; traffic (enforced by :pedestal depending on it). The heartbeat also refreshes it, but not until a minute in, and a
    ;; restart inside that minute would let a token a reset had already withdrawn
    ;; work again -- exactly the case the withdrawal exists for.
    :token-withdrawals
    (component/using
      (new-token-withdrawals)
      [:conn])

    :heartbeat
    (component/using
      (heartbeat/new-heartbeat {"share link pruning" share/prune-job
                                "token withdrawal refresh" routes/withdrawal-refresh-job
                                ;; Says nothing on a quiet hour. A line every
                                ;; hour reading all zeroes is how a log stops
                                ;; being read.
                                "breach check summary" pwned/summary-job
                                "rate limit summary" security/summary-job})
      [:conn])

    ;; links or creates artist accounts named in PORTRAIT_ARTISTS; runs in the
    ;; background after the database is up and never blocks the site starting
    :artist-accounts
    (component/using
      (artist-accounts/new-artist-accounts)
      [:conn])

    ;; says in the log if the portrait art shipped without its strand fields
    :strand-check
    (strands/new-strand-check)))

(rrepl/set-init! #(system :prod))
