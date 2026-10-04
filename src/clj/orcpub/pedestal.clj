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
  ;; Locale/ENGLISH: HTTP dates are English (RFC 7231), and without it a non-English JVM
  ;; cannot parse "Mon" and parse-date throws. docs/kb/locale-safety.md.
  (DateTimeFormatter/ofPattern "EEE, dd MMM yyyy HH:mm:ss Z" Locale/ENGLISH))

(defn parse-date [date content-length]
  (when date
    (str (.toEpochMilli
          (t/instant
           (t/zoned-date-time rfc822-formatter (s/replace date #"GMT" "+0000"))))
         "-"
         content-length)))

(defn make-nonce-interceptor
  "Creates an interceptor that sets per-request CSP nonces when CSP_POLICY=strict and
   `dev-mode?` is false (the default): :enter stores a nonce at [:request :csp-nonce], :leave
   adds an enforcing Content-Security-Policy header. In dev mode it does nothing, and strict
   disables Pedestal's own CSP, so dev sends no CSP at all (what Figwheel needs). There is no
   Report-Only mode."
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

(defn- arm-boot-report!
  "Print the boot banner when Jetty reports itself started.

   Best effort: if the listener cannot be attached -- a different container, a Jetty API
   change -- print immediately rather than lose the banner, since a missing report is worse
   than one a few milliseconds early."
  [created]
  (let [server (get created ::http/server)]
    (try
      (.addEventListener
       ^org.eclipse.jetty.util.component.LifeCycle server
       (reify org.eclipse.jetty.util.component.LifeCycle$Listener
         (lifeCycleStarted [_ _] (config/print-report! created))))
      (catch Throwable _ (config/print-report! created)))))

(defrecord Pedestal [service-map conn service]
  component/Lifecycle

  (start [this]
    (if service
      this
      (cond-> service-map
        ;; nonce-interceptor first: runs last in :leave phase (sets CSP header after response built)
        true (update ::http/interceptors conj nonce-interceptor (db-interceptor conn) etag-interceptor)
        true http/create-server
        ;; Arm the banner here, fire it when Jetty says it is up. It cannot go after
        ;; http/start -- that BLOCKS in prod, where join? defaults true, so the first
        ;; version printed nothing in the one configuration it exists for -- and printing
        ;; before start would call it "started" while it is not yet listening.
        (not (test? service-map)) (doto arm-boot-report!)
        (not (test? service-map)) http/start
        true ((partial assoc this :service)))))

  (stop [this]
    (when (and service (not (test? service-map)))
      (http/stop service))
    (assoc this :service nil)))

(defn new-pedestal
  []
  (map->Pedestal {}))
