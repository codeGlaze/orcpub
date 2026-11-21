(ns orcpub.routes.twitch
  "GET /api/twitch/live?login=<login>: whether a portrait artist is streaming on Twitch right now,
   for the dot on the artist page's Twitch button. Only logins named by a listed artist's Twitch
   link are ever sent to Twitch, so the endpoint is not an open proxy. Off (always not live) unless
   TWITCH_CLIENT_ID and TWITCH_CLIENT_SECRET are both set (orcpub.config/twitch-enabled?)."
  (:require [charred.api :as cheshire]
            [clj-http.client :as client]
            [clojure.string :as str]
            [orcpub.artist-credit :as artist-credit]
            [orcpub.artist-links :as links]
            [orcpub.config :as config]
            [orcpub.dnd.e5.portrait-assets :as portrait-assets]))

(def ^:private token-url "https://id.twitch.tv/oauth2/token")
(def ^:private streams-url "https://api.twitch.tv/helix/streams")

(def ^:private timeout-ms 3000)

(def answer-ttl-ms
  "How long one login's answer, failures included, is kept before Twitch is asked again."
  (* 2 60 1000))

(def ^:private token-margin-ms
  "The token is dropped this long before Twitch says it expires."
  60000)

(defonce ^:private token-cache (atom nil))
(defonce ^:private answer-cache (atom {}))

(def ^:dynamic *http*
  "The function that talks to Twitch: (f method url opts) returns clj-http's response map with
   :throw-exceptions false. Tests rebind it so they never reach Twitch."
  (fn [method url opts]
    (client/request (assoc opts :method method :url url))))

(defn- now-ms
  "The current time in milliseconds."
  []
  (System/currentTimeMillis))

(defn- parse-json
  "`text` read as JSON with keyword keys, or nil when it is not JSON."
  [text]
  (try (cheshire/read-json (str text) :key-fn keyword) (catch Exception _ nil)))

(defn- fetch-token!
  "Asks Twitch for an app access token (client-credentials flow) and caches it until shortly before
   it expires. Returns the token string; throws when Twitch refuses or answers without one."
  []
  (let [{:keys [status body]}
        (*http* :post token-url
                {:form-params {:client_id (config/twitch-client-id)
                               :client_secret (config/twitch-client-secret)
                               :grant_type "client_credentials"}
                 :as :string
                 :socket-timeout timeout-ms
                 :connection-timeout timeout-ms
                 :throw-exceptions false
                 :cookie-policy :none
                 :redirect-strategy :none})
        {:keys [access_token expires_in]} (when (<= 200 status 299) (parse-json body))]
    (when-not (string? access_token)
      (throw (ex-info "Twitch gave no token" {:status status})))
    (reset! token-cache {:token access_token
                         :until (+ (now-ms) (max 0 (- (* 1000 (or expires_in 0)) token-margin-ms)))})
    access_token))

(defn- token!
  "The cached app access token while it is still good, else a fresh one."
  []
  (let [{:keys [token until]} @token-cache]
    (if (and token (< (now-ms) until)) token (fetch-token!))))

(defn- streams-response
  "One call to Twitch's streams endpoint for `login` with `token`; the response map."
  [login token]
  (*http* :get streams-url
          {:query-params {:user_login login}
           :headers {"Client-Id" (config/twitch-client-id)
                     "Authorization" (str "Bearer " token)}
           :as :string
           :socket-timeout timeout-ms
           :connection-timeout timeout-ms
           :throw-exceptions false
           :cookie-policy :none
           :redirect-strategy :none}))

(defn- ask-twitch
  "True when Twitch lists a live stream for `login`. A 401 drops the cached token and retries once
   with a new one. Throws on a network failure, a non-2xx answer or a body that is not the
   expected JSON."
  [login]
  (let [resp (streams-response login (token!))
        resp (if (= 401 (:status resp))
               (do (reset! token-cache nil)
                   (streams-response login (token!)))
               resp)
        data (:data (when (<= 200 (:status resp) 299) (parse-json (:body resp))))]
    (when-not (sequential? data)
      (throw (ex-info "Twitch answered without a stream list" {:status (:status resp)})))
    (boolean (seq data))))

(defn twitch-login
  "The lower-case Twitch login a link names, or nil for a link that is not a Twitch profile."
  [url]
  (when (and (string? url) (= "twitch" (links/icon-for-url url)))
    (some-> (links/handle-for-url url) (subs 1) str/lower-case)))

(defn known-logins
  "The set of Twitch logins named by listed artists' links: the registry with the credits in force
   (portrait-assets/artist-info), plus links the overrides carry."
  []
  (let [artists (concat (map #(portrait-assets/artist-info (:artist/id %)) portrait-assets/registry)
                        (vals (portrait-assets/current-overrides)))]
    (->> artists
         (mapcat (fn [a] (cons (:artist/link a) (map :link/url (:artist/links a)))))
         (keep twitch-login)
         set)))

(defn live?
  "Whether `login` is live on Twitch, as a boolean. False without calling Twitch when the feature is
   off or `login` is not in a listed artist's Twitch link. Answers, failures too, are remembered for
   `answer-ttl-ms`; a failure logs one warning and counts as not live."
  [login]
  (let [login (some-> login str str/trim str/lower-case)]
    (cond
      (not (config/twitch-enabled?)) false
      (not (contains? (known-logins) login)) false
      :else
      (let [{:keys [at live]} (get @answer-cache login)]
        (if (and at (< (- (now-ms) at) answer-ttl-ms))
          live
          (let [live (try (ask-twitch login)
                          (catch Exception e
                            (println "twitch: live check failed, answering not live -"
                                     (.. e getClass getSimpleName))
                            false))]
            (swap! answer-cache assoc login {:at (now-ms) :live live})
            live))))))

(defn reset-caches!
  "Forgets the cached token and every cached answer. For tests."
  []
  (reset! token-cache nil)
  (reset! answer-cache {}))

(defn live-route
  "GET /api/twitch/live?login=<login>: answers {\"live\": true|false}. Never calls Twitch for a
   login that is not in a listed artist's Twitch link."
  [{:keys [db query-params]}]
  (when db (artist-credit/refresh! db))
  {:status 200
   :headers {"Content-Type" "application/json; charset=utf-8"
             "Cache-Control" "public, max-age=60"}
   :body (cheshire/write-json-str {:live (live? (:login query-params))})})
