(ns orcpub.turnstile
  "Cloudflare Turnstile check for the sign-up form (managed mode).
   GOTCHA: fails OPEN. If Cloudflare cannot be reached, the signup goes through; only an
   answer of success=false (or no token at all) refuses. The secret key is never logged."
  (:require [clj-http.client :as client]
            [clojure.string :as s]
            [orcpub.config :as config]))

(def ^:private endpoint "https://challenges.cloudflare.com/turnstile/v0/siteverify")

;; Short enough that a stalled third party cannot hold a signup open (same idea as orcpub.pwned).
(def ^:private timeout-ms 4000)

(defn enabled?
  "True only when both TURNSTILE_SITE_KEY and TURNSTILE_SECRET_KEY are set."
  []
  (config/turnstile-enabled?))

(defn- post-siteverify
  "POSTs to Cloudflare; returns the parsed JSON body as a map. Throws on transport failure,
   non-2xx or an unparseable body. A separate fn so tests can with-redefs it."
  [token remote-ip]
  (let [{:keys [status body]}
        (client/post endpoint
                     {:form-params (cond-> {:secret (config/turnstile-secret-key)
                                            :response token}
                                     (not (s/blank? remote-ip)) (assoc :remoteip remote-ip))
                      :as :json
                      :socket-timeout timeout-ms
                      :connection-timeout timeout-ms
                      :throw-exceptions false
                      :cookie-policy :none
                      :redirect-strategy :none})]
    (if (<= 200 status 299)
      body
      (throw (ex-info "siteverify non-2xx" {:status status})))))

;; A sustained outage would otherwise log a line per signup.
(def ^:private log-window-ms 60000)
(def ^:private last-logged (atom 0))

(defn- warn-unreachable! [reason]
  (let [now (System/currentTimeMillis)
        [before _] (swap-vals! last-logged #(if (> (- now %) log-window-ms) now %))]
    (when (> (- now before) log-window-ms)
      ;; Reason is an exception class name or HTTP status: never the secret or the token.
      (println "turnstile: siteverify unreachable, letting signup through -" reason))))

(defn verify
  "Checks a Turnstile `token` for `remote-ip`. Returns :pass, :fail, or :unreachable (the caller
   treats :unreachable as a pass). A blank token is :fail without calling Cloudflare."
  [token remote-ip]
  (if (s/blank? token)
    :fail
    (try
      (let [result (post-siteverify token remote-ip)]
        (if (true? (:success result)) :pass :fail))
      (catch Exception e
        (warn-unreachable! (if-let [st (:status (ex-data e))]
                             (str "HTTP " st)
                             (.. e getClass getSimpleName)))
        :unreachable))))
