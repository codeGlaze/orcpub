(ns orcpub.pedestal
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [io.pedestal.interceptor :as interceptor]
            [pandect.algo.sha1 :refer [sha1]]
            [datomic.api :as d]
            [clojure.string :as s]
            [java-time.api :as t]
            [orcpub.csp :as csp]
            [orcpub.config :as config])
  (:import [java.io File]
           [java.time.format DateTimeFormatter]))

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
  (DateTimeFormatter/ofPattern "EEE, dd MMM yyyy HH:mm:ss Z"))

(defn parse-date [date content-length]
  (when date
    (str (.toEpochMilli
          (t/instant
           (t/zoned-date-time rfc822-formatter (s/replace date #"GMT" "+0000"))))
         "-"
         content-length)))

(defn make-nonce-interceptor
  "Creates an interceptor that generates per-request CSP nonces.

   In prod (dev-mode?=false) with CSP_POLICY=strict:
   - :enter phase generates a nonce and stores it in [:request :csp-nonce]
   - :leave phase adds enforcing Content-Security-Policy header with the nonce

   In dev mode: CSP is skipped entirely. Pedestal 0.7's default CSP is still
   active, but the nonce interceptor becomes a no-op. This avoids flooding the
   browser console with Report-Only violations (inline Figwheel scripts, etc.)
   that obscure real issues during development."
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
                         (csp/build-csp-header nonce :dev-mode? false))
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
              (catch Throwable t (prn "T" t ))))}))

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
