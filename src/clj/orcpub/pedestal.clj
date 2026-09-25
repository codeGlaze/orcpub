(ns orcpub.pedestal
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.log :as log]
            [pandect.algo.sha1 :refer [sha1]]
            [datomic.api :as d]
            [clojure.string :as s]
            [java-time.api :as t]
            [orcpub.csp :as csp]
            [orcpub.config :as config]
            [orcpub.fork.integrations :as integrations])
  (:import [java.io File]
           [java.time.format DateTimeFormatter]
           [java.util Locale]))

(defn test?
  [service-map]
  (= :test (:env service-map)))

(defn db-interceptor [conn]
  (interceptor/interceptor
   {:name :db-interceptor
    :enter (fn [context]
             (let [conn (:conn conn)
                   db (d/db conn)]
               (update context :request assoc :db db :conn conn)))}))

(defmulti calculate-etag class)

(defmethod calculate-etag String [s]
  (sha1 s))

(defmethod calculate-etag File [f]
  (str (.lastModified f) "-" (.length f)))

(defmethod calculate-etag :default [x]
  nil)

(def rfc822-formatter
  ;; Locale/ENGLISH is load-bearing. HTTP dates are always English (RFC 7231),
  ;; but ofPattern without a locale parses using the JVM default, which follows
  ;; the OS regional settings. On a non-English machine "Mon" is not a day name
  ;; and parse-date throws, which used to blank the response entirely.
  (DateTimeFormatter/ofPattern "EEE, dd MMM yyyy HH:mm:ss Z" Locale/ENGLISH))

(defn parse-date [date content-length]
  (when date
    (str (.toEpochMilli
          (t/instant
           (t/zoned-date-time rfc822-formatter (s/replace date #"GMT" "+0000"))))
         "-"
         content-length)))

(defn make-nonce-interceptor
  "Creates an interceptor that generates per-request CSP nonces.

   When dev-mode? is false -- which is the DEFAULT, not just production --
   and CSP_POLICY=strict:
   - :enter phase generates a nonce and stores it in [:request :csp-nonce]
   - :leave phase adds enforcing Content-Security-Policy header with the nonce

   In dev mode: no nonce is generated, so the :leave branch never fires and
   this interceptor sets no CSP header. Pedestal's own CSP is disabled in the
   strict branch of get-secure-headers-config, so dev runs with no CSP from
   this application -- which is what lets Figwheel's scripts and its websocket
   work.

   There is no Report-Only mode anywhere in this codebase, despite what earlier
   comments here claimed."
  [dev-mode?]
  (interceptor/interceptor
   {:name :nonce-interceptor
    :enter (fn [ctx]
             (if (and (config/strict-csp?) (not dev-mode?))
               (assoc-in ctx [:request :csp-nonce] (csp/generate-nonce))
               ctx))
    :leave (fn [ctx]
             (if-let [nonce (get-in ctx [:request :csp-nonce])]
               (assoc-in ctx [:response :headers "Content-Security-Policy"]
                         (csp/build-csp-header nonce
                           ;; Always false here, and not a mistake: :enter only
                           ;; makes a nonce when dev-mode? is false, so this
                           ;; branch is unreachable in dev mode.
                           :dev-mode? false
                           :extra-connect-src (:connect-src integrations/csp-domains)
                           :extra-frame-src (:frame-src integrations/csp-domains)))
               ctx))}))

;; Create the nonce interceptor with current dev-mode? setting
(def nonce-interceptor (make-nonce-interceptor (config/dev-mode?)))

(def etag-interceptor
  (interceptor/interceptor
   {:name :etag-interceptor
    :leave (fn [{:keys [request response] :as context}]
             (try
               (let [{{etag "etag"
                       if-none-match "if-none-match"
                       last-modified "Last-Modified"
                      :as headers} :headers} request
                    {body :body
                     {last-modified "Last-Modified"
                      content-length "Content-Length"} :headers} response
                    old-etag (when if-none-match
                               (-> if-none-match (s/split #"--gzip") first))
                    new-etag (or (parse-date last-modified content-length) (calculate-etag body))
                    not-modified? (and old-etag (= new-etag old-etag))]
                (if not-modified?
                  (-> context
                      (assoc-in [:response :status] 304)
                      (update :response dissoc :body))
                  (if new-etag
                    (assoc-in context [:response :headers "etag"] new-etag)
                    context)))
              ;; Return the context. Without it the catch yields log/error's
              ;; value, discarding the response: the client gets 200 with an
              ;; empty body and no headers, and nothing reports a problem.
              (catch Throwable t
                (log/error :msg "ETag interceptor error" :exception t)
                context)))}))
(defrecord Pedestal [service-map conn service]
  component/Lifecycle

  (start [this]
    (if service
      this
      (cond-> service-map
        ;; nonce-interceptor first: runs last in :leave phase (sets CSP header after response built)
        true (update ::http/interceptors conj nonce-interceptor (db-interceptor conn) etag-interceptor)
        true http/create-server
        (not (test? service-map)) http/start
        true ((partial assoc this :service)))))

  (stop [this]
    (when (and service (not (test? service-map)))
      (http/stop service))
    (assoc this :service nil)))

(defn new-pedestal
  []
  (map->Pedestal {}))
