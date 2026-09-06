(ns orcpub.routes
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :as test]
            [io.pedestal.http.ring-middlewares :as ring]
            [ring.middleware.resource :as ring-resource]
            [ring.util.response :as ring-resp]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.interceptor.error :as error-int]
            [io.pedestal.interceptor.chain :refer [terminate]]
            #_[com.stuartsierra.component :as component]
            [buddy.auth.protocols :as proto]
            [buddy.auth.backends :as backends]
            [buddy.sign.jwt :as jwt]
            [buddy.hashers :as hashers]
            [buddy.auth.middleware :refer [authentication-request]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [orcpub.time :as time :refer [hours ago from-now instant before?]]
            [clojure.string :as s]
            [clojure.spec.alpha :as spec]
            [clojure.pprint]
            [orcpub.dnd.e5.skills :as skill5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.spell-annotations :as spell-annotations]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.template :as t5e]
            [datomic.api :as d]
            [bidi.bidi :as bidi]
            [orcpub.common :as common]
            [orcpub.route-map :as route-map]
            [orcpub.errors :as errors]
            [orcpub.privacy :as privacy]
            [orcpub.email :as email]
            [orcpub.index :refer [index-page]]
            [orcpub.pdf :as pdf]
            [orcpub.portrait-render :as portrait-render]
            [orcpub.config :as config]
            [orcpub.registration :as registration]
            [orcpub.entity.strict :as se]
            [orcpub.entity :as entity]
            [orcpub.security :as security]
            [orcpub.fork.branding :as branding]
            [orcpub.fork.auth :as auth]
            [orcpub.fork.user-data :as user-data]
            [orcpub.routes.party :as party]
            [orcpub.routes.folder :as folder]
            [hiccup.page :as page]
            [hiccup2.core :as h]
            [environ.core :as environ]
            [clojure.set :as sets]
            [ring.middleware.head :as head]
            [ring.util.codec :as codec]
            [ring.util.request :as req])
  ;; PDFBox 3.x: Use Loader class instead of PDDocument.load() static method
  ;; OLD (2.x): (PDDocument/load input-stream)
  ;; NEW (3.x): (Loader/loadPDF byte-array)  — does NOT accept InputStream
  ;; 
  ;; Import syntax notes for Clojure newcomers:
  ;;   - (org.apache.pdfbox.pdmodel PDDocument PDPage) imports multiple classes from one package
  ;;   - org.apache.pdfbox.Loader imports a single class (no parens needed)
  (:import (org.apache.pdfbox.pdmodel PDPage PDPageContentStream)
           org.apache.pdfbox.Loader
           (java.io ByteArrayOutputStream ByteArrayInputStream))
  (:gen-class))

(deftype FixedBuffer [^long len])

(def ^:private jwt-secret
  "JWT signing secret from SIGNATURE env var.
   nil when unset — check-auth returns 500 with a diagnostic message."
  (environ/env :signature))

(when-not jwt-secret
  (println "WARNING: SIGNATURE env var is not set — all authenticated API calls will fail"))

(def backend (backends/jws {:secret jwt-secret}))

(defn first-user-by [db query value]
  (let [result (d/q query
                    db
                    value)
        user-id (ffirst result)]
    (d/pull db '[*] user-id)))

(def username-query
  '[:find ?e
    :in $ ?username
    :where [?e :orcpub.user/username ?username]])

;; Case-insensitive email lookup to guard against mixed-case legacy data.
;; Callers must pass a lowercased email.
(def email-query
  '[:find ?e
    :in $ ?email
    :where [?e :orcpub.user/email ?stored]
           [(clojure.string/lower-case ?stored) ?email]])

(defn find-user-by-username-or-email [db username-or-email]
  (d/q
   '[:find (pull ?e [*]) .
     :in $ ?user-or-email
     :where (or [?e :orcpub.user/username ?user-or-email]
                [?e :orcpub.user/email ?user-or-email])]
   db
   username-or-email))

(defn find-user-by-username [db username]
  (d/q '[:find (pull ?e [*]) .
         :in $ ?username
         :where [?e :orcpub.user/username ?username]]
       db
       username))

(defn lookup-user-by-username [db username password]
  (let [user (d/q '[:find (pull ?e [*]) .
                    :in $ [?username ?password]
                    :where
                    [?e :orcpub.user/username ?username]
                    [?e :orcpub.user/password ?enc]
                    [(buddy.hashers/check ?password ?enc)]]
                  db
                  [username password])]
    user))

(defn lookup-user-by-email [db email password]
  (let [user (first-user-by db
                         '{:find [?e]
                           :in [$ [?email ?password]]
                           :where [[?e :orcpub.user/email ?email-2]
                                   [(clojure.string/lower-case ?email-2)
                                    ?email]
                                   [?e :orcpub.user/password ?enc]
                                   [(buddy.hashers/check ?password ?enc)]]}
                         [(s/lower-case email) password])]
    user))

(defn lookup-user [db username password]
  (if (re-matches registration/email-format username)
    (lookup-user-by-email db username password)
    (lookup-user-by-username db username password)))

(defn terminate-request [context status message]
  (-> context
      terminate
      (assoc :response {:status status :body {:message message}})))

(def check-auth
  "Interceptor that verifies the JWT bearer token on authenticated routes.
   Returns 401 for missing/invalid tokens, 500 with diagnostic if the
   JWT secret itself is not configured."
  (interceptor/interceptor
   {:name :check-auth
    :enter (fn [context]
             (if-not jwt-secret
               (terminate-request context 500
                                  "Server misconfigured: SIGNATURE env var not set")
               (try
                 (let [request (:request context)
                       updated-request (authentication-request request backend)
                       username (get-in updated-request [:identity :user])]
                   (if (and (:identity updated-request)
                            username)
                     (assoc context :request (assoc updated-request :username username))
                     (terminate-request context 401 "Unauthorized")))
                 (catch Exception e
                   (terminate-request context 401
                                      (str "Authentication failed: "
                                           (.getMessage e)))))))}))

(defn party-owner [db id]
  (d/q '[:find ?owner .
         :in $ ?id
         :where [?id :orcpub.dnd.e5.party/owner ?owner]]
       db
       id))

(def id-path [:request :path-params :id])

(def parse-id
  (interceptor/interceptor
   {:name :parse-id
    :enter (fn [context]
             (let [id-str (get-in context id-path)]
               (if (and id-str (re-matches #"\d+" id-str))
                 (assoc-in context
                           id-path
                           (Long/parseLong id-str))
                 (terminate-request context 400 "Bad ID"))))}))


(def check-party-owner
  (interceptor/interceptor
   {:name :check-party-owner
    :enter (fn [context]
             (let [{:keys [identity db] {:keys [id]} :path-params} (:request context)
                   party-owner (party-owner db id)]
               (if (= (:user identity) party-owner)
                 context
                 (terminate-request context 401 "You don't own this party"))))}))

(defn folder-owner [db id]
  (d/q '[:find ?owner .
         :in $ ?id
         :where [?id :orcpub.dnd.e5.folder/owner ?owner]]
       db
       id))

(def check-folder-owner
  (interceptor/interceptor
   {:name :check-folder-owner
    :enter (fn [context]
             (let [{:keys [identity db] {:keys [id]} :path-params} (:request context)
                   owner (folder-owner db id)]
               (cond
                 (nil? owner) (terminate-request context 404 "Folder not found")
                 (= (:user identity) owner) context
                 :else (terminate-request context 401 "You don't own this folder"))))}))

(defn redirect [route-key]
  (ring-resp/redirect (route-map/path-for route-key)))


(defn verification-expired? [verification-sent]
  (before? (instant verification-sent) (-> 24 hours ago)))

(defn login-error [error-key & [data]]
  {:status 401 :body (merge
                      data
                      {:error error-key})})

(defn create-token [username exp]
  (jwt/sign {:user username
             :exp exp}
            (environ/env :signature)))

(defn following-usernames [db ids]
  (map :orcpub.user/username
       (d/pull-many db '[:orcpub.user/username] ids)))

(defn user-body
  "Build the user API response. Core fields are inline; fork-specific
   fields (e.g. tier data) are added by user-data/enrich-response."
  [db user]
  (cond-> (user-data/enrich-response
           {:username (:orcpub.user/username user)
            :email (:orcpub.user/email user)
            :send-updates? (boolean (:orcpub.user/send-updates? user))
            :following (following-usernames db (map :db/id (:orcpub.user/following user)))}
           user)
    (:orcpub.user/pending-email user)
    (assoc :pending-email (:orcpub.user/pending-email user))))

(defn bad-credentials-response [db username ip]
  (security/add-failed-login-attempt! username ip)
  (if (security/too-many-attempts-for-username? username)
    (login-error errors/too-many-attempts)
    (let [user-for-username (find-user-by-username-or-email db username)]
      (login-error (if (:db/id user-for-username)
                     errors/bad-credentials
                     errors/no-account)))))

(defn create-login-response [db conn user id & [headers]]
  (let [token (create-token (:orcpub.user/username user)
                            (-> auth/token-lifetime-hours hours from-now))
        now (java.util.Date.)]
    (when auth/track-last-login?
      (d/transact conn [{:db/id id
                         :orcpub.user/last-login now}]))
    {:status 200
     :headers headers
     :body {:user-data (user-body db user)
            :token token}}))

(defn login-response
  [{:keys [json-params db conn remote-addr] :as request}]
  (let [{raw-username :username raw-password :password} json-params]
    (cond
      (s/blank? raw-username) (login-error errors/username-required)
      (s/blank? raw-password) (login-error errors/password-required)
      :else (let [username (s/trim raw-username)
                  password (s/trim raw-password)
                  {:keys [:orcpub.user/verified?
                          :orcpub.user/verification-sent
                          :orcpub.user/email
                          :db/id] :as user} (lookup-user db username password)
                  unverified? (not verified?)
                  expired? (and verification-sent (verification-expired? verification-sent))]
              (cond
                (nil? id) (bad-credentials-response db username remote-addr)
                (and unverified? expired?) (login-error errors/unverified-expired)
                unverified? (login-error errors/unverified {:email email})
                :else
                (create-login-response db conn user id))))))

(defn login [{:keys [json-params db] :as request}]
  (try
    (let [resp (login-response request)]
      resp)
    (catch Throwable e (prn "E" e) (throw e))))


(defn user-for-email [db email]
  (let [user (first-user-by db
                            '{:find [?e]
                              :in [$ ?email]
                              :where [[?e :orcpub.user/email ?email-2]
                                      [(clojure.string/lower-case ?email-2)
                                       ?email]]}
                            (s/lower-case email))]
    user))

(defn base-url [{:keys [scheme headers]}]
  (str (or (headers "x-forwarded-proto") (name scheme)) "://" (headers "host")))

(defn send-verification-email [request params verification-key]
  (email/send-verification-email
   (base-url request)
   params
   verification-key))

(defn send-email-change-verification [request params verification-key]
  (email/send-email-change-verification
   (base-url request)
   params
   verification-key))

(defn do-verification [request params conn & [tx-data]]
  (let [verification-key (str (java.util.UUID/randomUUID))
        now (java.util.Date.)]
    (try
      @(d/transact
        conn
        [(merge
          tx-data
          {:orcpub.user/verified? false
           :orcpub.user/verification-key verification-key
           :orcpub.user/verification-sent now})])
      (send-verification-email request params verification-key)
      {:status 200}
      (catch Exception e
        (println "ERROR: Failed to create verification record:" (.getMessage e))
        (throw (ex-info "Unable to complete registration. Please try again or contact support."
                        {:error :verification-failed}
                        e))))))

(defn register [{:keys [json-params db conn] :as request}]
  (let [{:keys [username email password send-updates?]} json-params
        username (when username (s/trim username))
        email (when email (s/lower-case (s/trim email)))
        password (when password (s/trim password))
        validation (registration/validate-registration
                    json-params
                    (seq (d/q email-query db email))
                    (seq (d/q username-query db username)))
        now (java.util.Date.)]
    (try
      (if (seq validation)
        {:status 400
         :body validation}
        (do-verification
         request
         json-params
         conn
         (merge
          {:orcpub.user/email email
           :orcpub.user/username username
           :orcpub.user/password (hashers/encrypt password)
           :orcpub.user/send-updates? send-updates?
           :orcpub.user/created now}
          (when auth/record-last-login-at-registration?
            {:orcpub.user/last-login now})
          (user-data/registration-defaults))))
      (catch Throwable e (prn e) (throw e)))))

(def user-for-verification-key-query
  '[:find ?e
    :in $ ?key
    :where [?e :orcpub.user/verification-key ?key]])

(def user-for-email-query
  '[:find ?e
    :in $ ?email
    :where [?e :orcpub.user/email ?email]])

(defn user-for-verification-key [db key]
  (first-user-by db user-for-verification-key-query key))

(defn user-id-for-username [db username]
  (d/q
   '[:find ?e .
     :in $ ?username
     :where [?e :orcpub.user/username ?username]]
   db
   username))

(defn verify [{:keys [query-params db conn] :as request}]
  (if-let [key (:key query-params)]
    (let [{:keys [:orcpub.user/verification-sent
                  :orcpub.user/verified?
                  :orcpub.user/username
                  :orcpub.user/pending-email
                  :db/id] :as user} (user-for-verification-key (d/db conn) key)]
      (if username
        (cond
          (and verified? (nil? pending-email))
          (redirect route-map/verify-success-route)

          (or (nil? verification-sent)
              (verification-expired? verification-sent))
          ;; Clean up stale pending state so user can request a fresh change
          (do (let [retractions (cond-> [[:db/retract id :orcpub.user/verification-key key]
                                         [:db/retract id :orcpub.user/verification-sent verification-sent]]
                                  pending-email
                                  (conj [:db/retract id :orcpub.user/pending-email pending-email]))]
                @(d/transact conn retractions))
              (redirect route-map/verify-failed-route))

          pending-email
          ;; Guard: re-check that the target email hasn't been claimed since request.
          ;; All paths retract verification-key and verification-sent to prevent
          ;; link reuse and avoid stale rate-limit data.
          (if (seq (d/q email-query (d/db conn) pending-email))
            (do @(d/transact conn [[:db/retract id :orcpub.user/pending-email pending-email]
                                   [:db/retract id :orcpub.user/verification-key key]
                                   [:db/retract id :orcpub.user/verification-sent verification-sent]])
                (redirect route-map/verify-failed-route))
            (do @(d/transact conn [{:db/id id
                                    :orcpub.user/email pending-email}
                                   [:db/retract id :orcpub.user/pending-email pending-email]
                                   [:db/retract id :orcpub.user/verification-key key]
                                   [:db/retract id :orcpub.user/verification-sent verification-sent]])
                (redirect route-map/verify-success-route)))

          :else
          (do @(d/transact conn [{:db/id id
                                  :orcpub.user/verified? true}])
              (redirect route-map/verify-success-route)))
        {:status 400}))
    {:status 400}))

(defn re-verify [{:keys [query-params db conn] :as request}]
  (let [email (:email query-params)
        {:keys [:orcpub.user/verification-sent
                :orcpub.user/verified?
                :db/id] :as user} (user-for-email db email)]
    (if verified?
      (redirect route-map/verify-success-route)
      (do-verification request
                       (merge query-params
                              {:first-and-last-name auth/verification-display-name})
                       conn
                       {:db/id id}))))

;; ─── Email Preferences ─────────────────────────────────────────────

(defn unsubscribe-token
  "Create a JWT-signed unsubscribe token for embedding in email links.
   Stateless — no DB storage needed. Verified by checking JWT signature."
  [email]
  (jwt/sign {:email (s/lower-case email) :action "unsubscribe"}
            (environ/env :signature)))

(defn unsubscribe
  "GET handler for /unsubscribe?token=<jwt>.
   Verifies JWT signature, sets send-updates? to false, redirects to success page.
   Idempotent — unsubscribing twice is harmless."
  [{:keys [query-params db conn]}]
  (let [token (:token query-params)]
    (if (s/blank? token)
      {:status 400 :body "Missing token"}
      (try
        (let [{:keys [email action]} (jwt/unsign token (environ/env :signature))]
          (if (not= "unsubscribe" action)
            {:status 400 :body "Invalid token"}
            (let [{:keys [:db/id]} (user-for-email (d/db conn) email)]
              (if id
                (do @(d/transact conn [{:db/id id :orcpub.user/send-updates? false}])
                    (redirect route-map/unsubscribe-success-route))
                {:status 400 :body "Unknown email"}))))
        (catch Exception _
          {:status 400 :body "Invalid or tampered token"})))))

(defn update-user-preferences
  "PUT handler for /user — update user preferences (currently send-updates?).
   Requires authentication. Only updates fields present in transit-params.
   Re-reads from DB after transact to return authoritative state."
  [{:keys [transit-params db conn identity]}]
  (let [username (:user identity)
        {:keys [:db/id]} (find-user-by-username db username)]
    (if id
      (do (when (contains? transit-params :send-updates?)
            @(d/transact conn [{:db/id id
                                :orcpub.user/send-updates? (boolean (:send-updates? transit-params))}]))
          ;; Re-read from DB after transact for authoritative response
          (let [updated-user (d/entity (d/db conn) id)]
            {:status 200
             :body {:send-updates? (boolean (:orcpub.user/send-updates? updated-user))}}))
      {:status 400 :body {:error "User not found"}})))

(defn do-send-password-reset [user-id email conn request]
  (let [key (str (java.util.UUID/randomUUID))]
    (try
      @(d/transact
        conn
        [{:db/id user-id
          :orcpub.user/password-reset-key key
          :orcpub.user/password-reset-sent (java.util.Date.)}])
      (email/send-reset-email
       (base-url request)
       {:first-and-last-name auth/verification-display-name
        :email email}
       key)
      {:status 200}
      (catch Exception e
        (println "ERROR: Failed to initiate password reset for user" user-id ":" (.getMessage e))
        (throw (ex-info "Unable to initiate password reset. Please try again or contact support."
                        {:error :password-reset-failed
                         :user-id user-id}
                        e))))))

(defn password-reset-expired? [password-reset-sent]
  (and password-reset-sent (before? (instant password-reset-sent) (-> 24 hours ago))))

(defn password-already-reset? [password-reset password-reset-sent]
  (and password-reset (before? (instant password-reset-sent) (instant password-reset))))

(defn send-password-reset [{:keys [query-params db conn scheme headers] :as request}]
  (try
    (let [email (:email query-params)
          {:keys [:orcpub.user/password-reset-sent
                  :orcpub.user/password-reset
                  :db/id] :as user} (user-for-email db email)
          expired? (password-reset-expired? password-reset-sent)
          already-reset? (password-already-reset? password-reset password-reset-sent)]
      (if id
        (do-send-password-reset id email conn request)
        {:status 400 :body {:error :no-account}}))
    (catch Throwable e (prn e) (throw e))))

(defn do-password-reset [conn user-id password]
  (try
    @(d/transact
      conn
      [{:db/id user-id
        :orcpub.user/password (hashers/encrypt (s/trim password))
        :orcpub.user/password-reset (java.util.Date.)
        :orcpub.user/verified? true}])
    {:status 200}
    (catch Exception e
      (println "ERROR: Failed to reset password for user" user-id ":" (.getMessage e))
      (throw (ex-info "Unable to reset password. Please try again or contact support."
                      {:error :password-update-failed
                       :user-id user-id}
                      e)))))

(defn reset-password [{:keys [json-params db conn cookies identity] :as request}]
  (try
    (let [{:keys [password verify-password]} json-params
          username (:user identity)
          {:keys [:db/id] :as user} (first-user-by db username-query username)]
      (cond
        (not= password verify-password) {:status 400 :message "Passwords do not match"}
        (seq (registration/validate-password password)) {:status 400 :message "New password is invalid"}
        :else (do-password-reset conn id password)))
    (catch Throwable t (prn t) (throw t))))

(def font-sizes
  (merge
   (zipmap (map :key skill5e/skills) (repeat 8))
   (zipmap (map (fn [k] (keyword (str (name k) "-save"))) char5e/ability-keys) (repeat 8))
   {:personality-traits 8
    :ideals 8
    :bonds 8
    :flaws 8
    :features-and-traits 8
    :features-and-traits-2 8
    :attacks-and-spellcasting 8
    :backstory 8
    :other-profs 8
    :equipment 8
    :weapon-name-1 8
    :weapon-name-2 8
    :weapon-name-3 8}))

(defn- bound-collection
  "At most `n` entries, whether `v` is a list or a map of lists.

   A map is capped across ALL its values, not per key: spells-known is keyed by
   class, so a per-key cap would let thirteen classes carry the limit each."
  [n v]
  (cond
    (map? v) (first (reduce (fn [[m left] [k xs]]
                              (if (sequential? xs)
                                [(assoc m k (take left xs)) (max 0 (- left (count xs)))]
                                [(assoc m k xs) left]))
                            [{} n] v))
    (sequential? v) (take n v)
    :else v))

(defn bound-request
  "Caps everything caller-supplied before any part of the export sees it.

   This is the ceiling that does not have to be remembered. Both rules act on the
   REQUEST rather than on a generator, so a feature added later that reads a
   collection out of the body, or counts spellcasting-class-N field names, is
   bounded without anyone wiring it up:

   - A `spellcasting-class-N` name past the section ceiling is dropped outright.
     The count was derived in two places -- the handler and add-missing-spell-pages!
     -- and clamping only one left the endpoint just as open. Nothing downstream
     can see a number too large if the field never arrives.
   - Every collection is truncated to the card ceiling. Cards are nine to a page
     and the caller says how many, so an uncapped list is an uncapped page count.

   Generators keep their own clamps as well. This is the one that catches what
   nobody thought to clamp."
  [fields]
  (let [max-sections (config/get-pdf-max-caster-sections)
        max-cards (config/get-pdf-max-cards)
        past-ceiling? (fn [k]
                        (when-let [[_ n] (re-matches #"spellcasting-class-(\d+)" (name k))]
                          (> (or (parse-long n) 0) max-sections)))
        dropped (count (filter past-ceiling? (keys fields)))]
    (when (pos? dropped)
      (println (format "pdf: dropped %d spellcasting-class field(s) past section %d"
                       dropped max-sections)))
    (reduce-kv (fn [m k v]
                 (if (past-ceiling? k)
                   m
                   (assoc m k (bound-collection max-cards v))))
               {} fields)))

(defn- bound-cards
  "At most ORCPUB_PDF_MAX_CARDS of `cards`, reporting when it truncates.

   Nine cards to a page and the caller decides how many, so an export is otherwise
   only as bounded as the request body: 2 MB of spell keys is some 13,000 pages
   and a quarter of an hour with an export slot held the whole time. The queue
   timeout bounds how long a request WAITS for a slot, not how long it keeps one."
  [kind cards]
  (let [limit (config/get-pdf-max-cards)
        n (count cards)]
    (when (> n limit)
      (println (format "pdf: %d %s cards requested, printing the first %d"
                       n kind limit)))
    (take limit cards)))

(def ^:private max-portrait-png-bytes
  "Ceiling on a posted composed portrait. The client bakes a 600x750 PNG of
   flat-tinted shapes, which lands well under 200 KB; 2 MB is generous
   headroom that still refuses a request body pretending to be a picture."
  (* 2 1024 1024))

(defn decode-portrait-png
  "Decode a base64 PNG posted with the export into {:data bytes :jpg? false},
   or nil.

   A composed portrait has no URL to fetch -- the client rasterizes its layers
   and sends the bytes -- so this is the counterpart to pdf/fetch-image for
   that path. Returns nil rather than throwing for the same reason fetch-image
   does: a picture that will not decode must not cost the character their
   sheet."
  [b64]
  (try
    (when (and (string? b64) (not (s/blank? b64)))
      ;; Base64 is 4 chars per 3 bytes, so the encoded length bounds the decode
      ;; before any of it is allocated.
      (when (<= (long (* 0.75 (count b64))) max-portrait-png-bytes)
        (let [data (.decode (java.util.Base64/getDecoder) ^String b64)]
          (when (pos? (alength data))
            {:data data :jpg? false}))))
    (catch Exception e
      (println "pdf: composed portrait failed to decode -" (.getMessage e))
      nil)))

(defn add-spell-cards!
  "Appends spell card pages, nine to a sheet, each with its back.

   `fonts` and `img` belong to the caller because they are per-DOCUMENT: each
   load-fonts embeds its own subset of every face used, so building a set here and
   another in add-magic-item-cards! puts two complete copies of Vollkorn in a
   character sheet that prints both kinds of card."
  [doc fonts img spells-known spell-save-dcs spell-attack-mods custom-spells print-spell-card-dc-mod? logo-img bw? bw-faded?]
  (try
    (let [custom-spells-map (common/map-by-key custom-spells)
          spells-map (merge spells/spell-map custom-spells-map)
          ;; Bound the CARDS, not the classes: spells-known is keyed by class, so
          ;; capping it would keep the first few classes whole and drop the rest.
          flat-spells (bound-cards "spell" (-> spells-known vals flatten))
          sorted-spells (sort-by
                         (fn [{:keys [class key]}]
                           [(if (keyword? class)
                              (common/kw-to-name class)
                              class)
                            key])
                         flat-spells)
          parts (vec (partition-all 9 flat-spells))]
      (doseq [i (range (count parts))
              :let [part (parts i)]]
        (let [page (PDPage.)]
          (.addPage doc page)
          (with-open [cs (PDPageContentStream. doc page)]
            (let [spells (sequence
                          (comp
                           (filter (fn [spell] (spells-map (:key spell))))
                           (map
                            ;; The DC and attack maps come from the request and may
                            ;; be absent. Calling nil threw an NPE that the catch
                            ;; below swallowed, so every card vanished and the
                            ;; export looked like it had simply ignored the option.
                            (fn [{:keys [key class]}]
                              {:spell (spells-map key)
                               :class-nm class
                               :dc (get spell-save-dcs class)
                               :attack-bonus (get spell-attack-mods class)})))
                          part)
                  remaining-desc-lines (vec
                                        (pdf/print-spells
                                         cs
                                         doc
                                         fonts
                                         img
                                         2.5
                                         3.5
                                         spells
                                         i
                                         print-spell-card-dc-mod?
                                         bw?
                                         bw-faded?))
                  back-page (PDPage.)]
              (with-open [back-page-cs (PDPageContentStream. doc back-page)]
                (.addPage doc back-page)
                (pdf/print-backs back-page-cs fonts img 2.5 3.5 remaining-desc-lines i
                                 logo-img)))))))
    (catch Exception e
      (println "pdf: failed adding spell cards -" (.getMessage e)))))

(defn add-magic-item-cards!
  "Appends card pages for `magic-items`, nine to a sheet, each with its back.

   The same layout as the spell cards, and the same failure posture: a card page
   that throws must not cost the character their sheet, so this logs and returns
   rather than propagating.

   `fonts` and `img` are the caller's, for the reason given on add-spell-cards!."
  [doc fonts img magic-items logo-img bw? bw-faded?]
  (try
    (let [parts (vec (partition-all 9 (bound-cards "magic item" magic-items)))]
      (doseq [i (range (count parts))
              :let [part (parts i)]]
        (let [page (PDPage.)]
          (.addPage doc page)
          (with-open [cs (PDPageContentStream. doc page)]
            (let [remaining-desc-lines (vec (pdf/print-items cs doc fonts img 2.5 3.5
                                                             part i bw? bw-faded?))
                  back-page (PDPage.)]
              (with-open [back-page-cs (PDPageContentStream. doc back-page)]
                (.addPage doc back-page)
                (pdf/print-backs back-page-cs fonts img 2.5 3.5 remaining-desc-lines i
                                 logo-img)))))))
    (catch Exception e (println "pdf: failed adding magic item cards" e))))

(def valid-sheet-styles
  "Style ids with a template on disk: resources/fillable-char-sheetstyle-N-*.pdf"
  #{1 2 3 4})

(def default-sheet-style 1)

(def ^:private pdf-option-keys
  "Keys the client sends alongside the field values to steer the export. They name
   no field, so they are removed before write-fields!, which reports whatever it
   cannot place and would otherwise flag every one of these on every request."
  #{:image-url :image-url-failed :faction-image-url :faction-image-url-failed
    :spells-known :custom-spells :spell-save-dcs :spell-attack-mods
    :print-character-sheet? :print-spell-cards? :print-character-sheet-style?
    :print-spell-card-dc-mod? :print-card-back-logo? :card-back-logo-faded?
    :print-bw? :bw-faded? :print-prepared-spells? :print-large-abilities?
    :print-spell-annotations? :spell-relabels :spell-headings :spell-layout
    :magic-items-known :print-magic-item-cards?
    :flatten?})

(def ^:private export-slots
  "Permits for sheet generation, one per concurrent export.

   Held for the PDF work only, not for the whole request, so parsing and the
   response write stay outside it. Fair ordering: without it a thread can be
   starved indefinitely under sustained load, which is exactly the traffic this
   exists for. Sized by ORCPUB_PDF_CONCURRENCY; see orcpub.config."
  (delay (java.util.concurrent.Semaphore. (config/get-pdf-concurrency) true)))

(def ^:private exports-waiting
  "Requests currently queued for a slot, for the Retry-After estimate and for
   anything that wants to report load."
  (java.util.concurrent.atomic.AtomicInteger. 0))

(def ^:private export-millis
  "Exponentially weighted mean duration of a completed export, for the
   Retry-After estimate. Seeded at a plausible 250 ms and converges on the real
   figure within a few exports, so the estimate tracks the host and the shape of
   sheet people are actually asking for rather than a number guessed here."
  (java.util.concurrent.atomic.AtomicLong. 250))

(defn export-queue-depth
  "How many export requests are waiting for a slot right now."
  []
  (.get exports-waiting))

(defn- record-export-millis!
  [ms]
  (.set export-millis (long (+ (* 0.8 (.get export-millis)) (* 0.2 ms)))))

(defn- retry-after-seconds
  "Whole seconds to tell a turned-away client to wait: the queue ahead of it
   divided by how fast the slots are draining it. Never zero -- Retry-After takes
   no fraction and a zero sends the client straight back into the same queue --
   and capped so a spike cannot tell someone to come back in an hour."
  [waiting]
  (-> (/ (* waiting (.get export-millis))
         (* 1000.0 (config/get-pdf-concurrency)))
      Math/ceil int (max 1) (min 30)))

(def ^:private busy-page-js
  "(function(){
     var el = document.getElementById('countdown');
     var form = document.getElementById('retry-form');
     if (!el || !form) { return; }
     var base = parseInt(el.getAttribute('data-seconds'), 10);
     if (!base || base < 1) { base = 3; }
     /* Jitter so a crowd turned away together does not come back together. */
     var left = Math.max(1, Math.round(base * (0.75 + Math.random() * 0.5)));
     (function tick(){
       if (left <= 0) { el.textContent = 'Starting your sheet now.'; form.submit(); return; }
       el.textContent = left === 1 ? 'Trying again in 1 second.'
                                   : 'Trying again in ' + left + ' seconds.';
       left -= 1;
       setTimeout(tick, 1000);
     })();
   })();")

(defn- busy-page
  "The page a turned-away export lands on.

   The export is a form POST into a new tab, so this response IS what the person
   is looking at -- the retry lives here rather than in the app, and carries the
   original request body forward in a hidden field so the resubmission is the
   same export. `attempt` counts retries already spent; past `max-retries` the
   page stops retrying itself and waits to be clicked.

   Rendered through hiccup2, which escapes content and attributes, because the
   request body is caller-supplied and is reflected into a hidden field. It wears
   the site header and stylesheets the way the privacy and terms pages do; its
   own rules live in orcpub.styles.core with the rest of the stylesheet. The
   builder's markup and scripts are absent in this tab, so the page uses plain
   card classes rather than app layout classes."
  [{:keys [body attempt max-retries retry-seconds nonce action]}]
  (let [auto? (< attempt max-retries)]
    (str
     "<!DOCTYPE html>"
     (h/html
      [:html {:lang "en"}
       [:head
        [:meta {:charset "utf-8"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
        [:title (str "Busy right now - " branding/app-name)]
        [:link {:rel "stylesheet" :type "text/css" :href "/css/style.css"}]
        [:link {:rel "stylesheet" :type "text/css" :href "/css/compiled/styles.css"}]]
       [:body.sans.busy-body
        [:div.app-header-bar.container {:style "background-color:#2c3445"}
         [:div.content
          [:div.flex.justify-cont-s-b.align-items-c.w-100-p.p-l-20.p-r-20
           [:a {:href "/"} [:img.h-72.pointer {:src branding/logo-path :alt branding/app-name}]]]]]
        [:div.busy-wrap
         [:div.busy-card
          [:h1 "Lots of sheets are being made right now"]
          (if auto?
            [:p "Your character sheet is queued. This page will keep trying on its own."]
            [:p "We tried " (str max-retries) " times and could not get through. "
             "The rush should pass shortly."])
          (when auto?
            [:p#countdown.busy-countdown {:data-seconds (str retry-seconds)}
             "Trying again in " (str retry-seconds) " seconds."])
          [:form#retry-form {:method "POST" :action action}
           [:input {:type "hidden" :name "body" :value body}]
           [:input {:type "hidden" :name "retry" :value (str (inc attempt))}]
           [:button.form-button {:type "submit"} (if auto? "Try now" "Try again")]]]]
        (when auto? [:script {:nonce nonce} (h/raw busy-page-js)])]]))))

(defn- attempt-count
  "Retries already spent, from the hidden field the busy page posts back. Anything
   that is not a non-negative number counts as a first try, so a hand-edited value
   cannot buy extra attempts."
  [req]
  (let [raw (get-in req [:form-params :retry])
        n (try (Integer/parseInt (str raw)) (catch Exception _ 0))]
    (if (and (nat-int? n) (<= n 1000)) n 0)))

(defn- with-export-slot
  "Runs `f` holding one export slot, or answers 503 if none frees up within the
   configured wait.

   Bounding the work rather than the request keeps memory predictable: an export
   in flight holds roughly 11 MB, so the ceiling is a number an operator can set
   against the heap. Saying so with a Retry-After beats holding the connection
   until the browser times out with nothing to show for it."
  [req f]
  (let [waiting (.incrementAndGet exports-waiting)]
    (try
      (if (.tryAcquire ^java.util.concurrent.Semaphore @export-slots
                       (config/get-pdf-queue-timeout-ms)
                       java.util.concurrent.TimeUnit/MILLISECONDS)
        (let [start (System/nanoTime)]
          (try
            (f)
            (finally
              (record-export-millis! (/ (- (System/nanoTime) start) 1e6))
              (.release ^java.util.concurrent.Semaphore @export-slots))))
        (let [retry (retry-after-seconds waiting)
              attempt (attempt-count req)]
          (println (format "pdf: no export slot within %d ms, %d waiting, attempt %d"
                           (config/get-pdf-queue-timeout-ms) waiting attempt))
          {:status 503
           :headers {"Retry-After" (str retry)
                     "Content-Type" "text/html; charset=utf-8"}
           :body (busy-page {:body (get-in req [:form-params :body])
                             :attempt attempt
                             :max-retries (config/get-pdf-max-retries)
                             :retry-seconds retry
                             :nonce (:csp-nonce req)
                             :action (or (:uri req) "/character.pdf")})}))
      (finally (.decrementAndGet exports-waiting)))))

(defn- spell-annotation
  "The marks for the spell printed under `nm`, or nil.

   By NAME, because that is all the row carries: the server is handed a flat map
   of field names to values. A homebrew spell, or one whose name does not match
   the data, gets no marks rather than a wrong one."
  [nm]
  (some-> (get spells/spell-map (common/name-to-kw nm))
          spell-annotations/annotation))

(defn- generate-character-pdf [req]
  (let [fields (try
                 (-> req :form-params :body edn/read-string bound-request)
                 (catch Exception e
                   (throw (ex-info "Invalid character data format. Unable to parse PDF request."
                                   {:error :invalid-pdf-data}
                                   e))))
        
        {:keys [image-url image-url-failed faction-image-url faction-image-url-failed spells-known custom-spells spell-save-dcs spell-attack-mods print-spell-cards? magic-items-known print-magic-item-cards? print-character-sheet-style? print-spell-card-dc-mod? print-card-back-logo? card-back-logo-faded? print-bw? bw-faded? print-spell-annotations? spell-relabels spell-headings character-name class-level player-name flatten? portrait-png]} fields

        ;; Printer-friendly mode: monochrome spell-card icons + a forced solid-black
        ;; card-back logo (no color anywhere on the cards). bw-faded? picks the
        ;; icon style: default solid black (white-halo labels) vs faded grayscale.
        bw? (true? print-bw?)
        bw-faded? (true? bw-faded?)

        ;; Resolve the card-back logo to a concrete resource once. nil = off.
        ;; Default is the solid-black mark; the faded brand-orange watermark is an
        ;; opt-in for color printing, and B&W mode overrides it back to solid black.
        card-back-logo-img (when print-card-back-logo?
                             (if (and card-back-logo-faded? (not bw?))
                               "public/image/dmv-mark-faded-orange.png"
                               "public/image/dmv-mark-black.png"))

        ;; The id is interpolated into a resource name, so an unrecognised value
        ;; resolves to a missing resource and throws. Restrict it to ids with a
        ;; template on disk; see valid-sheet-styles.
        print-character-sheet-style? (if (contains? valid-sheet-styles
                                                    print-character-sheet-style?)
                                       print-character-sheet-style?
                                       default-sheet-style)
        ;; (2026-09) One master per style, grown to the character's shape, rather
        ;; than one of seven pre-cut files. pdf/sheet-masters carries the reasoning
        ;; and the measurements.
        ;; Clamped: the count comes from the field NAMES the caller sent, so
        ;; "spellcasting-class-9999" would otherwise ask for 9,998 cloned pages at
        ;; roughly 14 MB each, from a body of a few dozen bytes.
        requested-casters (->> (keys fields)
                               (keep #(second (re-matches #"spellcasting-class-(\d+)" (name %))))
                               (keep #(try (Integer/parseInt %) (catch Exception _ nil)))
                               (reduce max 0))
        casters (min requested-casters (config/get-pdf-max-caster-sections))
        {:keys [file marks without-casters site-line prints-site-line?]}
        (get pdf/sheet-masters print-character-sheet-style?)
        ;; A character who casts nothing opens the variant that has no spell page
        ;; rather than one with its spell page taken out: removing a page leaves
        ;; the resources it referenced behind, and for style 4 it would take the
        ;; licence line with it.
        no-casters? (and (zero? casters) (some? without-casters))
        input (.openStream (io/resource (if no-casters? without-casters file)))
        output (ByteArrayOutputStream.)
        filename (cond
                   (and (s/blank? player-name) (s/blank? character-name)) "character.pdf"
                   (s/blank? player-name) (str character-name " - " class-level ".pdf")
                   :else (str player-name " - " character-name " - " class-level ".pdf"))]
        
    ;; PDFBox 3.x: Loader/loadPDF accepts byte[], File, or RandomAccessRead —
    ;; NOT InputStream. Read the resource stream into a byte array first.
    (with-open [doc (Loader/loadPDF (.readAllBytes input))]
      ;; Fillable in every browser by default. The old non-Chrome flattening was a
      ;; workaround for Firefox ignoring NeedAppearances; write-fields! now bakes
      ;; real appearance streams, so values render everywhere AND the form stays
      ;; editable. Clients that want a locked/static PDF pass `:flatten? true`.
      ;; Both run before write-fields! so the fields they create or trim exist by
      ;; the time values are written.
      (let [fields (apply dissoc fields pdf-option-keys)]
        ;; No prune here. The masters are pruned by dev/prepare_templates.clj and
        ;; growing only adds pages, so there is nothing to find -- it was a full
        ;; scan of the form on every export for no result, and doubled the churn
        ;; of a non-caster sheet. add-missing-spell-pages! still prunes on the
        ;; branch where it generates pages, in case it meets an unbaked template.
        (pdf/grow-spell-sections! doc casters (if no-casters? :all marks))
        (pdf/add-missing-spell-pages! doc fields (config/get-pdf-max-caster-sections))
        ;; Merge before spilling, so a style's shared box is measured as the one
        ;; value it prints rather than as its parts.
        (let [fields (pdf/merge-style-fields print-character-sheet-style? fields)]
          ;; Narrowing the rows must happen before the values are written: the
          ;; rows auto-size, so this is what makes a long name shrink to clear the
          ;; annotation columns rather than run under them.
          ;; The packing decision is made in the builder, which knows what a
          ;; spell level is; the server is handed a flat field map and this small
          ;; instruction list, and applies it. Bounds-checked against the sections
          ;; this document actually grew, since it arrives from the client.
          (when (seq spell-relabels)
            (let [[applied refused]
                  (pdf/apply-relabel-instructions! doc spell-relabels casters
                                                   print-character-sheet-style?)]
              (when (pos? refused)
                (println (format "pdf: refused %d of %d relabel instruction(s)"
                                 refused (+ applied refused))))))
          (when print-spell-annotations?
            (pdf/reserve-annotation-columns! doc))
          (pdf/write-fields! doc (pdf/spill-overflow! doc fields) (true? flatten?) font-sizes)
          (when print-spell-annotations?
            (pdf/annotate-spell-rows! doc spell-annotation))
          ;; After the values, so a heading is drawn over a finished bar. Names
          ;; each packed column with the class holding it; caller-supplied, so the
          ;; box and section are checked the way the relabels are.
          (doseq [{:keys [box section class] :as heading} spell-headings
                  :when (and (integer? box) (<= 0 box 9)
                             (integer? section) (<= 1 section casters)
                             (string? class) (<= 1 (count class) 60))]
            (pdf/draw-column-heading! doc print-character-sheet-style?
                                      box section class heading))))
      ;; After the pages exist, so clones are stamped too, and before the card
      ;; pages are appended -- those carry the line on their backs already.
      (pdf/stamp-site-line! doc site-line (boolean prints-site-line?))
      ;; One set of fonts and one image embedder for the whole document. Both are
      ;; per-document: a second load-fonts embeds a second subset of every face,
      ;; and a second loader re-embeds the 998x998 card-back mark. Built only when
      ;; a card page is actually coming, since a plain sheet needs neither.
      (let [spell-cards? (and print-spell-cards? (seq spells-known))
            item-cards? (and print-magic-item-cards? (seq magic-items-known))
            fonts (when (or spell-cards? item-cards?) (pdf/load-fonts doc))
            img (when (or spell-cards? item-cards?) (pdf/make-image-loader doc))]
        (when spell-cards?
          (add-spell-cards! doc fonts img spells-known spell-save-dcs spell-attack-mods
                            custom-spells print-spell-card-dc-mod? card-back-logo-img
                            bw? bw-faded?))
        (when item-cards?
          (add-magic-item-cards! doc fonts img magic-items-known card-back-logo-img
                                 bw? bw-faded?)))

      ;; Both images are fetched BEFORE either is drawn, and concurrently.
      ;;
      ;; Fetching is where an export's seconds go -- 10s to connect, 10s on the
      ;; socket and a 20s transfer deadline apiece -- and it happens holding an
      ;; export slot. Drawn one after the other, two slow images held a slot for
      ;; up to 80s while nothing else could use it; started together they cost
      ;; one image's worst case rather than two.
      ;;
      ;; No pdf/safe-image-url? here. pdf/fetch-image validates through
      ;; safe-image-bytes, whose resolved addresses are the ones the connection
      ;; is pinned to; calling it first only resolved the host a second time.
      ;; The regex stays -- it costs nothing and refuses file:// and ftp://
      ;; without a lookup at all.
      (let [wanted (fn [url failed?]
                     (and url (not failed?)
                          (re-matches #"^https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
                                      url)
                          url))
            ;; A composed (paper-doll) portrait arrives already rendered, as
            ;; base64 PNG the client baked from its layers -- there is no URL to
            ;; fetch, so it costs no network time inside the export slot. It
            ;; takes precedence over image-url, matching how the character sheet
            ;; and summary resolve the two. Malformed base64 degrades to "no
            ;; portrait" rather than failing the export.
            composed (when portrait-png (delay (decode-portrait-png portrait-png)))
            portrait (or composed
                         (some-> (wanted image-url image-url-failed)
                                 (as-> u (future (pdf/fetch-image u)))))
            faction (some-> (wanted faction-image-url faction-image-url-failed)
                            (as-> u (future (pdf/fetch-image u))))]
        (when-let [{:keys [data jpg?]} (some-> portrait deref)]
          (case print-character-sheet-style?
            1 (pdf/draw-image-bytes! doc (pdf/get-page doc 1) data jpg? 0.45 1.75 2.35 3.15)
            2 (pdf/draw-image-bytes! doc (pdf/get-page doc 1) data jpg? 0.45 1.75 2.35 3.15)
            3 (pdf/draw-image-bytes! doc (pdf/get-page doc 1) data jpg? 0.45 1.75 2.35 3.15)
            4 (pdf/draw-image-bytes! doc (pdf/get-page doc 0) data jpg? 0.50 0.85 2.35 3.15)))
        (when-let [{:keys [data jpg?]} (some-> faction deref)]
          (case print-character-sheet-style?
            1 (pdf/draw-image-bytes! doc (pdf/get-page doc 1) data jpg? 5.88 2.4 1.905 1.52)
            2 (pdf/draw-image-bytes! doc (pdf/get-page doc 1) data jpg? 5.88 2.4 1.905 1.52)
            3 (pdf/draw-image-bytes! doc (pdf/get-page doc 1) data jpg? 5.88 2.0 1.905 1.52)
            4 nil)))
      (.save doc output))
    (let [a (.toByteArray output)]
      {:status 200
       :headers {"Content-Disposition" (str "inline; filename=\"" filename "\"")}
       :body (ByteArrayInputStream. a)})))

(defn character-pdf-2 [req]
  (with-export-slot req #(generate-character-pdf req)))

(defn html-response
  [html & [response]]
  (let [merged (merge
                response
                {:status 200
                 :body html
                 :headers {"Content-Type" "text/html"}})]
    merged))

(def user-by-password-reset-key-query
  '[:find ?e
    :in $ ?key
    :where [?e :orcpub.user/password-reset-key ?key]])

(def default-title branding/default-page-title)

(def default-description branding/app-tagline)

(defn default-image-url
  "OG meta image URL. Uses https:// for social sharing compatibility."
  [host]
  (str "https://" host branding/og-image-filename))

(defn index-page-response [{:keys [headers uri csp-nonce] :as request}
                           {:keys [title description image-url]}
                           & [response]]
  (let [host (headers "host")]
    (merge
     response
     {:status 200
      :headers {"Content-Type" "text/html" }
      :body
      (index-page
       {:url (str "http://" host uri)
        :title (or title default-title)
        :description (or description default-description)
        :image (or image-url (default-image-url host))
        :nonce csp-nonce}
       (= "/" uri))})))

(defn default-index-page [request & [response]]
  (index-page-response request {} response))

(defn index [{:keys [headers scheme uri server-name] :as request} & [response]]
  (default-index-page request response))

(defn reset-password-page [{:keys [query-params db conn] :as req}]
  (if-let [key (:key query-params)]
    (let [{:keys [:db/id
                  :orcpub.user/username
                  :orcpub.user/password-reset-key
                  :orcpub.user/password-reset-sent
                  :orcpub.user/password-reset] :as user}
          (first-user-by db user-by-password-reset-key-query key)
          expired? (password-reset-expired? password-reset-sent)
          already-reset? (password-already-reset? password-reset password-reset-sent)]
      (cond
        expired? (redirect route-map/password-reset-expired-route)
        already-reset? (redirect route-map/password-reset-used-route)
        :else (let [token (create-token username (-> 1 hours from-now))]
                (index req {:cookies {"token" token}}))))
    {:status 400
     :body "Key is required"}))

(defn check-field [query value db]
  {:status 200
   :body (-> (d/q query db value)
             seq
             boolean
             str)})

(defn check-username [{:keys [db query-params]}]
  (check-field username-query (:username query-params) db))

(defn check-email [{:keys [db query-params]}]
  (check-field email-query (some-> (:email query-params) s/lower-case) db))

(defn character-for-id [db id]
  (d/pull db '[*] id))

(defn diff-branch [ids]
  (fn [n]
    (or
     (and (map? n)
          (ids (:db/id n)))
     (sequential? n))))

(defn get-new-id [temp-id result]
  (-> result :tempids (get temp-id)))

(defn create-entity [conn username entity owner-prop]
  (try
    (as-> entity $
      (entity/remove-ids $)
      (assoc $
             :db/id "tempid"
             owner-prop username)
      @(d/transact conn [$])
      (get-new-id "tempid" $)
      (d/pull (d/db conn) '[*] $))
    (catch Exception e
      (println "ERROR: Failed to create entity for user" username ":" (.getMessage e))
      (throw (ex-info "Unable to create entity. Please try again or contact support."
                      {:error :entity-creation-failed
                       :username username}
                      e)))))

(defn email-for-username [db username]
  (d/q '[:find ?email .
         :in $ ?username
         :where
         [?e :orcpub.user/username ?username]
         [?e :orcpub.user/email ?email]]
       db
       username))

(defn update-entity [conn username entity owner-prop]
  (try
    (let [id (:db/id entity)
          current (d/pull (d/db conn) '[*] id)
          owner (get current owner-prop)
          email (email-for-username (d/db conn) username)]
      (if ((set [username email]) owner)
        (let [current-ids (entity/db-ids current)
              new-ids (entity/db-ids entity)
              retract-ids (sets/difference current-ids new-ids)
              retractions (map
                           (fn [retract-id]
                             [:db/retractEntity retract-id])
                           retract-ids)
              remove-ids (sets/difference new-ids current-ids)
              with-ids-removed (entity/remove-specific-ids entity remove-ids)
              new-entity (assoc with-ids-removed owner-prop username)
              result @(d/transact conn (concat retractions [new-entity]))]
          (d/pull (d/db conn) '[*] id))
        (throw (ex-info "Not user entity"
                        {:error :not-user-entity}))))
    (catch clojure.lang.ExceptionInfo e
      (throw e))
    (catch Exception e
      (println "ERROR: Failed to update entity for user" username ":" (.getMessage e))
      (throw (ex-info "Unable to update entity. Please try again or contact support."
                      {:error :entity-update-failed
                       :username username
                       :entity-id (:db/id entity)}
                      e)))))

(defn save-entity [conn username e owner-prop]
  (let [without-empty-fields (entity/remove-empty-fields e)]
    (if (:db/id without-empty-fields)
      (update-entity conn username without-empty-fields owner-prop)
      (create-entity conn username without-empty-fields owner-prop))))

(defn owns-entity? [db username entity-id]
  (let [user (find-user-by-username db username)
        username (:orcpub.user/username user)
        email (:orcpub.user/email user)
        entity (d/pull db '[:orcpub.entity.strict/owner] entity-id)
        owner (:orcpub.entity.strict/owner entity)]
    (or (= email owner)
        (= username owner))))

(defn entity-problem [desc actual expected]
  (str desc ", expected: " expected ", actual: " actual))

(defn entity-type-problems [expected-game expected-version expected-type {:keys [::se/type ::se/game ::se/game-version]}]
  (cond-> nil
    (not= expected-game game) (conj (entity-problem "Entity is from the wrong game" game expected-game))
    (not= expected-version game-version) (conj (entity-problem "Entity is from the wrong game version" game-version expected-version))
    (not= expected-type type) (conj (entity-problem "Entity is wrong type" type expected-type))))

(def dnd-e5-char-type-problems (partial entity-type-problems :dnd :e5 :character))

(defn add-dnd-5e-character-tags [character]
  (assoc character
         ::se/game :dnd
         ::se/game-version :e5
         ::se/type :character))

(defn update-character [db conn character username]
  (let [id (:db/id character)]
    (if (owns-entity? db username id)
      (let [current-character (d/pull db '[*] id)
            problems [] #_(dnd-e5-char-type-problems current-character)
            current-valid? (spec/valid? ::se/entity current-character)]
        (when-not current-valid?
          (prn "INVALID CHARACTER FOUND, REPLACING" #_current-character)
          (prn "INVALID CHARACTER EXPLANATION" #_(spec/explain-data ::se/entity current-character)))
        (if (seq problems)
          (throw (ex-info "Character has problems"
                          {:error :character-problems :problems problems}))
          (if-not current-valid?
            (let [new-character (entity/remove-ids character)
                  tx [[:db/retractEntity (:db/id current-character)]
                      (-> new-character
                          (assoc :db/id "tempid"
                                 :orcpub.entity.strict/owner username)
                          add-dnd-5e-character-tags)]
                  result @(d/transact conn tx)]
              (d/pull (d/db conn) '[*] (-> result :tempids (get "tempid"))))
            (let [new-character (entity/remove-orphan-ids character)
                  current-ids (entity/db-ids current-character)
                  new-ids (entity/db-ids new-character)
                  retract-ids (sets/difference current-ids new-ids)
                  retractions (map
                               (fn [retract-id]
                                 [:db/retractEntity retract-id])
                               retract-ids)
                  tx (conj retractions
                           (-> new-character
                               (assoc :orcpub.entity.strict/owner username)
                               add-dnd-5e-character-tags))]
              @(d/transact conn tx)
              (d/pull (d/db conn) '[*] id)))))
      (throw (ex-info "Not user character"
                      {:error :not-user-character})))))

(defn create-new-character
  "Creates a new D&D 5e character.

  Args:
    conn - Database connection
    character - Character data map
    username - Owner username

  Returns:
    Created character entity

  Throws:
    ExceptionInfo on database failure"
  [conn character username]
  (errors/with-db-error-handling :character-creation-failed
    {:username username}
    "Unable to create character. Please try again or contact support."
    (let [result @(d/transact conn
                              [(-> character
                                   (assoc :db/id "tempid"
                                          ::se/owner username)
                                   add-dnd-5e-character-tags)])
          new-id (get-new-id "tempid" result)]
      (d/pull (d/db conn) '[*] new-id))))

(defn clean-up-character [character]
  (if (-> character ::se/values ::char5e/xps string?)
    (update-in character
               [::se/values ::char5e/xps]
               #(try
                  (if-not (s/blank? %)
                    (Long/parseLong %)
                    0)
                  (catch NumberFormatException e 0)))
    character))

(defn do-save-character [db conn transit-params identity]
  (let [character (entity/remove-empty-fields transit-params)
        username (:user identity)
        current-id (:db/id character)]
    (try
      (if-let [data (spec/explain-data ::se/entity character)]
        {:status 400 :body data}
        (let [clean-character (clean-up-character character)
              updated-character (if (:db/id clean-character)
                                  (update-character db conn clean-character username)
                                  (create-new-character conn clean-character username))]
          {:status 200 :body updated-character}))
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (case (:error data)
            :character-problems {:status 400 :body (:problems data)}
            :not-user-character {:status 401 :body "You do not own this character"}
            (throw e))))   ; re-throw unrecognised ExceptionInfo (e.g. :db/error from Datomic)
      (catch Exception e (prn "ERROR" e) (throw e)))))

(defn save-character [{:keys [db transit-params body conn identity] :as request}]
  (do-save-character db conn transit-params identity))

(defn owns-item [db username item-id]
  (let [item (d/pull db '[::mi5e/owner] item-id)]
    (= username (::mi5e/owner item))))

(defn save-item [{:keys [db transit-params body conn identity] :as request}]
  (if-let [data (spec/explain-data ::mi5e/magic-item transit-params)]
    {:status 400 :body data}
    (let [username (:user identity)
          result (save-entity conn username transit-params ::mi5e/owner)]
      {:status 200
       :body result})))

(defn get-item [{:keys [db] {:keys [:id]} :path-params}]
  (let [item (d/pull db '[*] id)]
    (if (::mi5e/owner item)
      {:status 200
       :body item}
      {:status 404})))

(defn delete-item
  "Deletes a magic item owned by the user.

  Args:
    request - HTTP request with item ID

  Returns:
    HTTP 200 on success, 401 if not owned

  Throws:
    ExceptionInfo on database failure"
  [{:keys [db conn username] {:keys [:id]} :path-params}]
  (let [{:keys [::mi5e/owner]} (d/pull db '[::mi5e/owner] id)]
    (if (= username owner)
      (errors/with-db-error-handling :item-deletion-failed
        {:item-id id}
        "Unable to delete item. Please try again or contact support."
        @(d/transact conn [[:db/retractEntity id]])
        {:status 200})
      {:status 401})))

(defn item-list [{:keys [db identity]}]
  (let [username (:user identity)
        items (d/q '[:find (pull ?e [*])
                     :in $ ?username
                     :where
                     [?e ::mi5e/owner ?username]]
                   db
                   username)]
    {:status 200 :body (map first items)}))

(defn character-list [{:keys [db identity] :as request}]
  (let [username (:user identity)
        user (find-user-by-username-or-email db username)
        ids (d/q '[:find ?e
                   :in $ [?idents ...]
                   :where
                   [?e ::se/owner ?idents]]
                 db
                 [(:orcpub.user/username user)
                  (:orcpub.user/email user)])
        characters (d/pull-many db '[*] (map first ids))]
    {:status 200 :body characters}))

(defn character-summary-list [{:keys [db body conn identity] :as request}]
  (let [username (:user identity)
        user (find-user-by-username-or-email db username)
        following-ids (map :db/id (:orcpub.user/following user))
        following-usernames (following-usernames db following-ids)
        results (d/q '[:find (pull ?e [:db/id
                                       ::se/summary
                                       ::se/owner])
                       :in $ [?idents ...]
                       :where
                       [?e ::se/owner ?idents]]
                     db
                     (concat
                      [(:orcpub.user/username user)
                       (:orcpub.user/email user)]
                      following-usernames))
        characters (mapv
                    (fn [[{:keys [:db/id ::se/owner ::se/summary]}]]
                      (assoc
                       summary
                       :db/id id
                       ::se/owner (if (= owner (:orcpub.user/email user))
                                    (:orcpub.user/username user)
                                    owner)))
                    results)]
    {:status 200 :body characters}))

(defn follow-user
  "Adds a user to the authenticated user's following list.

  Args:
    request - HTTP request with username to follow

  Returns:
    HTTP 200 on success

  Throws:
    ExceptionInfo on database failure"
  [{:keys [db conn identity] {:keys [user]} :path-params}]
  (let [other-user-id (user-id-for-username db user)
        username (:user identity)
        user-id (user-id-for-username db username)]
    (errors/with-db-error-handling :follow-user-failed
      {:follower username :followed user}
      "Unable to follow user. Please try again or contact support."
      @(d/transact conn [{:db/id user-id
                          :orcpub.user/following other-user-id}])
      {:status 200})))

(defn unfollow-user
  "Removes a user from the authenticated user's following list.

  Args:
    request - HTTP request with username to unfollow

  Returns:
    HTTP 200 on success

  Throws:
    ExceptionInfo on database failure"
  [{:keys [db conn identity] {:keys [user]} :path-params}]
  (let [other-user-id (user-id-for-username db user)
        username (:user identity)
        user-id (user-id-for-username db username)]
    (errors/with-db-error-handling :unfollow-user-failed
      {:follower username :unfollowed user}
      "Unable to unfollow user. Please try again or contact support."
      @(d/transact conn [[:db/retract user-id :orcpub.user/following other-user-id]])
      {:status 200})))

(defn delete-character
  "Deletes a character owned by the authenticated user.

  Args:
    request - HTTP request with character ID in path params

  Returns:
    HTTP 200 on success, 400 for problems, 401 if not owned

  Throws:
    ExceptionInfo on invalid ID or database failure"
  [{:keys [db conn identity] {:keys [id]} :path-params}]
  (let [parsed-id (errors/with-validation :invalid-character-id
                    {:id id}
                    "Invalid character ID format"
                    (Long/parseLong id))
        username (:user identity)
        character (d/pull db '[*] parsed-id)
        problems [] #_(dnd-e5-char-type-problems character)]
    (if (owns-entity? db username parsed-id)
      (if (empty? problems)
        (errors/with-db-error-handling :character-deletion-failed
          {:character-id parsed-id}
          "Unable to delete character. Please try again or contact support."
          @(d/transact conn [[:db/retractEntity parsed-id]])
          {:status 200})
        {:status 400 :body problems})
      {:status 401 :body "You do not own this character"})))

(defn get-character-for-id [db id]
  (let [{:keys [::se/owner] :as character} (d/pull db '[*] id)
        problems [] #_(dnd-e5-char-type-problems character)]
    (if (or (not owner) (seq problems))
      {:status 400 :body problems}
      {:status 200 :body character})))

(defn character-summary-for-id
  "The share-card data for a character: {::se/summary … ::se/values …}.

   Returns the WHOLE pull, not just ::se/summary. It used to return the summary
   submap while its caller went on to destructure ::se/summary and ::se/values
   back out of it -- so every og:title and og:image came out nil and shared
   links fell back to the site defaults."
  [db id]
  (d/pull db
          '[::se/summary
            {::se/values [::char5e/description ::char5e/image-url ::char5e/portrait]}]
          id))

(defn get-character
  "Retrieves a character by ID.

  Args:
    request - HTTP request with character ID in path params

  Returns:
    HTTP response with character data

  Throws:
    ExceptionInfo on invalid ID format"
  [{:keys [db] {:keys [:id]} :path-params}]
  (let [parsed-id (errors/with-validation :invalid-character-id
                    {:id id}
                    "Invalid character ID format"
                    (Long/parseLong id))]
    (get-character-for-id db parsed-id)))

(defn report-character-problem
  "User-initiated report that a character failed to load. Auth required. Emails
   the client-supplied diagnostic (char-id, reader error, raw undecodable data)
   to the configured support address, cc'ing the reporting user. Rate-limited
   and gated on email config inside email/send-character-report; returns its
   {:sent? .. :reason ..} so the client can fall back to the copyable report."
  [{:keys [db transit-params identity]}]
  (let [username   (:user identity)
        user       (find-user-by-username-or-email db username)
        user-email (:orcpub.user/email user)
        {:keys [char-id error raw]} transit-params]
    {:status 200
     :body   (email/send-character-report
              {:char-id char-id :user-email user-email :error error :raw raw})}))

(defn get-user [{:keys [db identity]}]
  (let [username (:user identity)
        user (find-user-by-username-or-email db username)]
    {:status 200 :body (user-body db user)}))

(defn delete-user
  "Deletes the authenticated user's account.

  Args:
    request - HTTP request with authenticated user identity

  Returns:
    HTTP 200 on success

  Throws:
    ExceptionInfo on database failure"
  [{:keys [db conn identity]}]
  (let [username (:user identity)
        user (d/q '[:find ?u .
                    :in $ ?username
                    :where [?u :orcpub.user/username ?username]]
                  db
                  username)]
    (errors/with-db-error-handling :user-deletion-failed
      {:username username}
      "Unable to delete user account. Please try again or contact support."
      @(d/transact conn [[:db/retractEntity user]])
      {:status 200})))

(defn rate-limit-remaining-secs
  "Seconds until the user can act again. In the 0–1 min zone (email in transit)
   returns time until the 1-min resend window opens. In the 1–5 min zone (for a
   different email) returns time until the 5-min cooldown expires."
  [verification-sent new-email pending-email]
  (when verification-sent
    (let [elapsed-ms (- (System/currentTimeMillis) (.getTime ^java.util.Date verification-sent))
          ;; If same email, they're waiting for the 1-min resend window to open.
          ;; If different email, they're waiting for the full 5-min cooldown.
          target-ms (if (= new-email pending-email)
                      (* 1 60 1000)
                      (* 5 60 1000))
          remaining-ms (- target-ms elapsed-ms)]
      (when (pos? remaining-ms)
        (int (Math/ceil (/ remaining-ms 1000.0)))))))

(defn email-change-rate-limited? [verification-sent pending-email new-email]
  ;; Only rate-limit if the last key was generated for a pending email change
  ;; (not for initial registration verification).
  ;; Three zones from verification-sent:
  ;;   0–1 min  → too soon, email is in transit (always blocked)
  ;;   1–5 min  → free resend allowed for same email, otherwise blocked
  ;;   5+ min   → open for any request
  (and pending-email
       verification-sent
       (let [elapsed-ms (- (System/currentTimeMillis) (.getTime ^java.util.Date verification-sent))
             same-email? (= new-email pending-email)]
         (cond
           (>= elapsed-ms (* 5 60 1000)) false          ;; past cooldown
           (< elapsed-ms (* 1 60 1000))  true           ;; too soon
           :else                          (not same-email?)))) ;; 1-5 min: resend ok, new email blocked
  )

(defn request-email-change [{:keys [transit-params db conn identity] :as request}]
  (try
    ;; Client sends {:new-email "..."} (confirm-email is validated client-side only)
    (let [new-email (s/lower-case (s/trim (str (:new-email transit-params))))
          username (:user identity)]
      (if (nil? username)
        {:status 400 :body {:error :user-not-found}}
        (let [{:keys [:db/id
                      :orcpub.user/email
                      :orcpub.user/pending-email
                      :orcpub.user/verification-sent] :as user} (find-user-by-username db username)]
          (cond
            (nil? id)
            {:status 400 :body {:error :user-not-found}}

            (registration/bad-email? new-email)
            {:status 400 :body {:error :invalid-email}}

            (= new-email (some-> email s/lower-case))
            {:status 400 :body {:error :same-as-current}}

            (email-change-rate-limited? verification-sent pending-email new-email)
            {:status 429 :body {:error :too-many-requests
                                :retry-after-secs (rate-limit-remaining-secs verification-sent new-email pending-email)}}

            ;; Check no other account already owns this email
            (seq (d/q email-query db new-email))
            {:status 400 :body {:error :email-taken}}

            ;; Free resend: same email, 1–5 min after original send. Re-send with
            ;; existing key and don't update verification-sent (no rolling window).
            (and (= new-email pending-email)
                 verification-sent
                 (let [elapsed (- (System/currentTimeMillis) (.getTime ^java.util.Date verification-sent))]
                   (and (>= elapsed (* 1 60 1000))
                        (< elapsed (* 5 60 1000)))))
            (try
              (send-email-change-verification request
                                              {:email new-email :username username}
                                              (:orcpub.user/verification-key user))
              {:status 200 :body {:pending-email new-email}}
              (catch Throwable e
                (prn "Email resend failed:" (.getMessage e))
                {:status 500 :body {:error :email-send-failed}}))

            :else
            (let [verification-key (str (java.util.UUID/randomUUID))
                  now (java.util.Date.)]
              @(d/transact conn [{:db/id id
                                  :orcpub.user/pending-email new-email
                                  :orcpub.user/verification-key verification-key
                                  :orcpub.user/verification-sent now}])
              ;; Roll back pending-email if verification email fails to send
              (try
                (send-email-change-verification request
                                                {:email new-email :username username}
                                                verification-key)
                {:status 200 :body {:pending-email new-email}}
                (catch Throwable e
                  (errors/log-error "ERROR:" (str "Email send failed, rolling back pending state: " (.getMessage e)))
                  ;; Full rollback: retract all attributes set by the failed attempt
                  @(d/transact conn [[:db/retract id :orcpub.user/pending-email new-email]
                                     [:db/retract id :orcpub.user/verification-key verification-key]
                                     [:db/retract id :orcpub.user/verification-sent now]])
                  {:status 500 :body {:error :email-send-failed}})))))))
    (catch Throwable e (prn e) (throw e))))

(defn character-summary-description [{:keys [::char5e/race-name ::char5e/subrace-name ::char5e/classes]}]
  (str race-name
       " "
       (when subrace-name (str "(" subrace-name ") "))
       " "
       (when (seq classes)
         (s/join
          " / "
          (map
           (fn [{:keys [::char5e/class-name
                        ::char5e/subclass-name
                        ::char5e/level]}]
             (str class-name " (" level ")"))
           classes)))))

(def index-page-paths
  [[route-map/dnd-e5-char-list-page-route]
   [route-map/dnd-e5-char-parties-page-route]
   [route-map/dnd-e5-monster-list-page-route]
   [route-map/dnd-e5-monster-page-route :key ":key"]
   [route-map/dnd-e5-spell-list-page-route]
   [route-map/dnd-e5-spell-page-route :key ":key"]
   [route-map/dnd-e5-spell-builder-page-route]
   [route-map/dnd-e5-monster-builder-page-route]
   [route-map/dnd-e5-selection-builder-page-route]
   [route-map/dnd-e5-background-builder-page-route]
   [route-map/dnd-e5-encounter-builder-page-route]
   [route-map/dnd-e5-combat-tracker-page-route]
   [route-map/dnd-e5-race-builder-page-route]
   [route-map/dnd-e5-subrace-builder-page-route]
   [route-map/dnd-e5-subclass-builder-page-route]
   [route-map/dnd-e5-class-builder-page-route]
   [route-map/dnd-e5-language-builder-page-route]
   [route-map/dnd-e5-invocation-builder-page-route]
   [route-map/dnd-e5-boon-builder-page-route]
   [route-map/dnd-e5-feat-builder-page-route]
   [route-map/dnd-e5-item-list-page-route]
   [route-map/dnd-e5-item-page-route :key ":key"]
   [route-map/dnd-e5-item-builder-page-route]
   [route-map/dnd-e5-char-builder-route]
   [route-map/dnd-e5-newb-char-builder-route]
   [route-map/dnd-e5-my-content-route]
   [route-map/send-password-reset-page-route]
   [route-map/my-account-page-route]
   [route-map/register-page-route]
   [route-map/login-page-route]
   [route-map/verify-sent-route]
   [route-map/password-reset-sent-route]
   [route-map/password-reset-expired-route]
   [route-map/password-reset-used-route]
   [route-map/verify-failed-route]
   [route-map/verify-success-route]
   [route-map/unsubscribe-success-route]
   [route-map/dnd-e5-orcacle-page-route]])

(defn character-portrait-png
  "PNG of a character's composed portrait, for og:image.

   A crawler has no browser, so unlike the PDF path (where the client bakes
   the layers with canvas) this is rendered here. Access matches the character
   page itself -- unauthenticated by id -- because that page already exposes
   the same character's name, race and description in its meta tags.

   404 when the character has no composed portrait, so a crawler falls back to
   whatever og:image the page did declare."
  [{:keys [db] {:keys [id]} :path-params}]
  (let [portrait (some-> (d/pull db '[{::se/values [::char5e/portrait]}] id)
                         ::se/values
                         ::char5e/portrait
                         char5e/parse-portrait)]
    (if-let [png (some-> portrait portrait-render/render-png)]
      {:status 200
       :headers {"Content-Type" "image/png"
                 ;; Portraits change rarely and a crawler may refetch often.
                 "Cache-Control" "public, max-age=300"}
       :body (ByteArrayInputStream. png)}
      {:status 404 :body "no composed portrait"})))

(defn character-page [{:keys [db conn identity headers scheme uri] {:keys [id]} :path-params :as request}]
  (let [host (headers "host")
        {:keys [::se/summary ::se/values]} (character-summary-for-id db id)
        {:keys [::char5e/character-name]} summary
        {:keys [::char5e/description
                ::char5e/image-url
                ::char5e/portrait]} values
        ;; A composed portrait wins over a pasted URL, the same precedence the
        ;; sheet, the summary and the PDF use. It is served as a real PNG
        ;; because crawlers will not render CSS masks -- or, mostly, SVG.
        composed? (seq (:layers (char5e/parse-portrait portrait)))
        share-image (if composed?
                      (str "https://" host
                           (route-map/path-for route-map/dnd-e5-char-portrait-route :id id))
                      image-url)]
    (index-page-response request
                         {:title character-name
                          :description (str (character-summary-description summary)
                                            ". "
                                            description)
                          :image-url share-image}
                         {"X-Frame-Options" "ALLOW-FROM https://www.worldanvil.com/"})))

(def header-style
  {:style "color:#2c3445"})

(defn terms-page [body-fn]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (body-fn)})

(defn privacy-policy-page [req]
  (terms-page privacy/privacy-policy))

(defn terms-of-use-page [req]
  (terms-page privacy/terms-of-use))

(defn community-guidelines-page [_]
  (terms-page privacy/community-guidelines))

(defn cookie-policy-page [_]
  (terms-page privacy/cookie-policy))

(defn health-check [_]
  {:status 200 :body "OK"})

(def index-page-routes
  (mapv
   (fn [[route & args]]
     [(apply route-map/path-for route args) :get `default-index-page :route-name route])
   index-page-paths))

(def expanded-index-routes
  (route/expand-routes
   (set index-page-routes)))

(def service-error-handler
  (error-int/error-dispatch [ctx ex]
                            :else (do
                                    (email/send-error-email ctx ex)
                                    (assoc ctx :io.pedestal.interceptor.chain/error ex))))

(def file-hashes (atom {}))

(defn get-file [{:keys [uri] :as request}]
  (ring-resource/resource-request request "public"))

(def get-css get-file)

(def get-js get-file)

(def get-image get-file)

(def get-favicon get-file)

(def webjars-root "META-INF/resources/webjars/")

(defn get-webjar
  "Get a resource containd within a webjar.
   Expects route to be /assets/*"
  [request]
  (let [path (subs (codec/url-decode (req/path-info request)) 1)
        new-path (s/replace-first path #"^assets/" webjars-root)]
    (-> (ring-resp/resource-response new-path)
        (head/head-response request))))

(def routes
  (concat
   (route/expand-routes
    [[["/" {:get `index}
       ^:interceptors [(body-params/body-params) service-error-handler]
       ["/js/*" {:get `get-js}]
       ["/css/*" {:get `get-css}]
       ["/assets/*" {:get `get-webjar}]
       ["/image/*" {:get `get-image}]
       ["/favicon/*" {:get `get-favicon}]
       [(route-map/path-for route-map/register-route)
        {:post `register}]
       [(route-map/path-for route-map/user-route) ^:interceptors [check-auth]
        {:get `get-user
         :put `update-user-preferences
         :delete `delete-user}]
       [(route-map/path-for route-map/user-email-route) ^:interceptors [check-auth]
        {:put `request-email-change}]
       [(route-map/path-for route-map/follow-user-route :user ":user") ^:interceptors [check-auth]
        {:post `follow-user
         :delete `unfollow-user}]

       ;; Items
       [(route-map/path-for route-map/dnd-e5-items-route) ^:interceptors [check-auth]
        {:post `save-item
         :get `item-list}]
       [(route-map/path-for route-map/dnd-e5-item-route :id ":id") ^:interceptors [check-auth parse-id]
        {:delete `delete-item}]
       [(route-map/path-for route-map/dnd-e5-item-route :id ":id") ^:interceptors [parse-id]
        {:get `get-item}]

       ;; Characters
       [(route-map/path-for route-map/dnd-e5-char-list-route) ^:interceptors [check-auth]
        {:post `save-character
         :get `character-list}]
       [(route-map/path-for route-map/dnd-e5-char-summary-list-route) ^:interceptors [check-auth]
        {:get `character-summary-list}]
       [(route-map/path-for route-map/dnd-e5-char-report-route) ^:interceptors [check-auth]
        {:post `report-character-problem}]
       [(route-map/path-for route-map/dnd-e5-char-route :id ":id") ^:interceptors [check-auth]
        {:delete `delete-character}]
       [(route-map/path-for route-map/dnd-e5-char-route :id ":id")
        {:get `get-character}]

       [(route-map/path-for route-map/dnd-e5-char-page-route :id ":id") ^:interceptors [parse-id]
        {:get `character-page}]
       [(route-map/path-for route-map/dnd-e5-char-portrait-route :id ":id") ^:interceptors [parse-id]
        {:get `character-portrait-png}]
       [(route-map/path-for route-map/dnd-e5-char-parties-route) ^:interceptors [check-auth]
        {:post `party/create-party
         :get `party/parties}]
       [(route-map/path-for route-map/dnd-e5-char-party-route :id ":id") ^:interceptors [check-auth parse-id check-party-owner]
        {:delete `party/delete-party}]
       [(route-map/path-for route-map/dnd-e5-char-party-name-route :id ":id") ^:interceptors [check-auth parse-id check-party-owner]
        {:put `party/update-party-name}]
       [(route-map/path-for route-map/dnd-e5-char-party-characters-route :id ":id") ^:interceptors [check-auth parse-id check-party-owner]
        {:post `party/add-character}]
       [(route-map/path-for route-map/dnd-e5-char-party-character-route :id ":id" :character-id ":character-id") ^:interceptors [check-auth parse-id check-party-owner]
        {:delete `party/remove-character}]
       [(route-map/path-for route-map/dnd-e5-char-folders-route) ^:interceptors [check-auth]
        {:post `folder/create-folder
         :get `folder/folders}]
       [(route-map/path-for route-map/dnd-e5-char-folder-route :id ":id") ^:interceptors [check-auth parse-id check-folder-owner]
        {:delete `folder/delete-folder}]
       [(route-map/path-for route-map/dnd-e5-char-folder-name-route :id ":id") ^:interceptors [check-auth parse-id check-folder-owner]
        {:put `folder/update-folder-name}]
       [(route-map/path-for route-map/dnd-e5-char-folder-characters-route :id ":id") ^:interceptors [check-auth parse-id check-folder-owner]
        {:post `folder/add-character}]
       [(route-map/path-for route-map/dnd-e5-char-folder-character-route :id ":id" :character-id ":character-id") ^:interceptors [check-auth parse-id check-folder-owner]
        {:delete `folder/remove-character}]
       [(route-map/path-for route-map/login-route)
        {:post `login}]
       [(route-map/path-for route-map/character-pdf-route)
        {:post `character-pdf-2}]
       [(route-map/path-for route-map/verify-route)
        {:get `verify}]
       [(route-map/path-for route-map/re-verify-route)
        {:get `re-verify}]
       [(route-map/path-for route-map/unsubscribe-route)
        {:get `unsubscribe}]
       [(route-map/path-for route-map/reset-password-route) ^:interceptors [ring/cookies check-auth]
        {:post `reset-password}]
       [(route-map/path-for route-map/reset-password-page-route) ^:interceptors [ring/cookies]
        {:get `reset-password-page}]
       [(route-map/path-for route-map/send-password-reset-route)
        {:get `send-password-reset}]
       [(route-map/path-for route-map/privacy-policy-route)
        {:get `privacy-policy-page}]
       [(route-map/path-for route-map/terms-of-use-route)
        {:get `terms-of-use-page}]
       [(route-map/path-for route-map/community-guidelines-route)
        {:get `community-guidelines-page}]
       [(route-map/path-for route-map/cookies-policy-route)
        {:get `cookie-policy-page}]
       [(route-map/path-for route-map/check-email-route)
        {:get `check-email}]
       [(route-map/path-for route-map/check-username-route)
        {:get `check-username}]
       ["/health"
        {:get `health-check}]]]])
   expanded-index-routes))


