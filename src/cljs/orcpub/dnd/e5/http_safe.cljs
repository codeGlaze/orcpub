(ns orcpub.dnd.e5.http-safe
  "Drop-in replacement for the cljs-http.client request/get/post/... fns whose only difference is
   the application/edn decoder: `safe-edn-decode` heals a bare-colon empty keyword
   (`common/sanitize-edn-colons`) and degrades anything still unreadable to a marker instead of
   throwing. See empty-keyword-corruption.md.
   GOTCHA: cljs-http decodes inside `async/map`'s go-loop, so a throw there escapes any `try`
   around the caller's `(<! ...)`; the fix has to live in the decoder."
  (:refer-clojure :exclude [get])
  (:require [cljs-http.client :as client]
            [cljs-http.core :as core]
            [cljs.core.async :as async]
            [cljs.reader :as reader]
            [orcpub.common :as common]))

(def decode-error-key ::decode-error)
(def raw-key   ::raw)     ; the undecodable response body, for the report/diagnostics
(def error-key ::error)   ; the reader error message

(defn decode-failed?
  "True when a response :body is the marker safe-edn-decode returns for a body it
   could not decode even after sanitize. Callers should route these to recovery
   (a clear message / their character list) instead of feeding the marker into
   from-strict, which would silently build a blank default."
  [body]
  (boolean (and (map? body) (cljs.core/get body decode-error-key))))

(defn safe-edn-decode
  "Decode an application/edn response body without ever throwing out of the
   go-loop. Heals a bare-colon empty keyword first, then parses; if it is still
   unreadable, logs and returns {::decode-error true} so the caller can route to
   recovery rather than crash the entire load."
  [raw]
  (try
    (reader/read-string (:text (common/sanitize-edn-colons raw)))
    (catch :default e
      (js/console.error
       "orcpub.http-safe: EDN response unreadable even after sanitize —" e)
      {decode-error-key true
       raw-key   raw
       error-key (some-> e .-message str)})))

(defn wrap-safe-edn-response
  "Like cljs-http.client/wrap-edn-response, but with the resilient decoder."
  [client-fn]
  (fn [request]
    (-> #(client/decode-body % safe-edn-decode "application/edn" (:request-method request))
        (async/map [(client-fn request)]))))

(defn- wrap-request
  "cljs-http.client/wrap-request's stack (pinned to 0.1.49), with the one edn
   response middleware replaced. Kept in this exact order so behavior is
   identical to cljs-http apart from the safe decode."
  [request]
  (-> request
      client/wrap-accept
      client/wrap-form-params
      client/wrap-multipart-params
      client/wrap-edn-params
      wrap-safe-edn-response                 ; <-- replaces client/wrap-edn-response
      client/wrap-transit-params
      client/wrap-transit-response
      client/wrap-json-params
      client/wrap-json-response
      client/wrap-content-type
      client/wrap-query-params
      client/wrap-basic-auth
      client/wrap-oauth
      client/wrap-method
      client/wrap-url
      client/wrap-channel-from-request-map
      client/wrap-default-headers))

(def request (wrap-request core/request))

(defn get    [url & [req]] (request (merge req {:method :get :url url})))
(defn post   [url & [req]] (request (merge req {:method :post :url url})))
(defn put    [url & [req]] (request (merge req {:method :put :url url})))
(defn delete [url & [req]] (request (merge req {:method :delete :url url})))
(defn head   [url & [req]] (request (merge req {:method :head :url url})))
