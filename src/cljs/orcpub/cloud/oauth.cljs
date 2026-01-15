(ns orcpub.cloud.oauth
  "OAuth 2.0 utilities for browser-based cloud storage authentication.

  Handles OAuth 2.0 with PKCE (Proof Key for Code Exchange) flow for
  secure browser-side authentication without backend server involvement.

  Security best practices:
  - Uses PKCE to prevent authorization code interception
  - Tokens stored in memory only (no localStorage for access tokens)
  - Automatic token refresh before expiry
  - State parameter for CSRF protection"
  (:require [clojure.string :as str]))

;; ============================================================================
;; Token Storage
;; ============================================================================

(defonce ^:private token-store
  "In-memory token storage. Access tokens never persisted to avoid XSS risks."
  (atom {:google-drive {:access-token nil
                        :token-expiry nil
                        :refresh-token nil
                        :user-info nil}}))

(defn store-token!
  "Store access token and expiry for a provider.

  provider: keyword (:google-drive, :dropbox)
  token-data: map with :access-token, :expires-in, :refresh-token, :user-info"
  [provider token-data]
  (let [expires-in (:expires-in token-data)
        expiry-ms (when expires-in
                    (+ (.getTime (js/Date.)) (* expires-in 1000)))]
    (swap! token-store assoc provider
           {:access-token (:access-token token-data)
            :token-expiry expiry-ms
            :refresh-token (:refresh-token token-data)
            :user-info (:user-info token-data)})))

(defn get-token
  "Retrieve stored token for provider."
  [provider]
  (get @token-store provider))

(defn clear-token!
  "Clear stored token for provider (logout)."
  [provider]
  (swap! token-store assoc provider
         {:access-token nil
          :token-expiry nil
          :refresh-token nil
          :user-info nil}))

(defn token-valid?
  "Check if stored token is still valid (not expired).
  Returns false if token doesn't exist or is expired."
  [provider]
  (let [{:keys [access-token token-expiry]} (get @token-store provider)]
    (and access-token
         token-expiry
         (> token-expiry (.getTime (js/Date.))))))

(defn token-needs-refresh?
  "Check if token should be refreshed (expires in < 5 minutes).
  Returns true if token exists and expires soon."
  [provider]
  (let [{:keys [access-token token-expiry]} (get @token-store provider)
        five-minutes (* 5 60 1000)]
    (and access-token
         token-expiry
         (< (- token-expiry (.getTime (js/Date.))) five-minutes))))

;; ============================================================================
;; PKCE Utilities (for OAuth 2.0 security)
;; ============================================================================

(defn- random-string
  "Generate cryptographically random string for OAuth state/verifier."
  [length]
  (let [array (js/Uint8Array. length)]
    (.getRandomValues js/crypto array)
    (->> array
         array-seq
         (map #(-> % (.toString 16) (.padStart 2 "0")))
         (str/join ""))))

(defn generate-code-verifier
  "Generate PKCE code verifier (43-128 characters, base64url-encoded random)."
  []
  (random-string 64))

(defn generate-code-challenge
  "Generate PKCE code challenge from verifier using SHA-256.

  Returns a promise that resolves to the base64url-encoded challenge."
  [verifier]
  (js/Promise.
   (fn [resolve reject]
     (try
       (let [encoder (js/TextEncoder.)
             data (.encode encoder verifier)]
         (-> js/crypto.subtle
             (.digest "SHA-256" data)
             (.then (fn [hash]
                      (let [bytes (js/Uint8Array. hash)
                            binary-string (->> bytes
                                               array-seq
                                               (map #(js/String.fromCharCode %))
                                               (str/join ""))
                            base64 (js/btoa binary-string)
                            base64url (-> base64
                                          (str/replace #"\+" "-")
                                          (str/replace #"\/" "_")
                                          (str/replace #"=" ""))]
                        (resolve base64url))))
             (.catch reject)))
       (catch js/Error e
         (reject e))))))

(defn generate-state
  "Generate random state parameter for CSRF protection."
  []
  (random-string 32))

;; ============================================================================
;; Session Storage for PKCE (safe for code verifier/state)
;; ============================================================================

(defn store-pkce-params!
  "Store PKCE parameters in sessionStorage during OAuth flow.

  These are temporary and cleared after successful auth."
  [state verifier]
  (.setItem js/sessionStorage "oauth_state" state)
  (.setItem js/sessionStorage "oauth_code_verifier" verifier))

(defn get-pkce-params
  "Retrieve and remove PKCE parameters from sessionStorage."
  []
  (let [state (.getItem js/sessionStorage "oauth_state")
        verifier (.getItem js/sessionStorage "oauth_code_verifier")]
    (.removeItem js/sessionStorage "oauth_state")
    (.removeItem js/sessionStorage "oauth_code_verifier")
    {:state state
     :verifier verifier}))

;; ============================================================================
;; OAuth Helper Functions
;; ============================================================================

(defn build-auth-url
  "Build OAuth authorization URL with PKCE parameters.

  config: {:client-id, :redirect-uri, :scope, :auth-endpoint}
  pkce: {:state, :code-challenge, :code-challenge-method}"
  [{:keys [client-id redirect-uri scope auth-endpoint]} {:keys [state code-challenge]}]
  (let [params {"client_id" client-id
                "redirect_uri" redirect-uri
                "response_type" "code"
                "scope" scope
                "state" state
                "code_challenge" code-challenge
                "code_challenge_method" "S256"
                "access_type" "offline"  ; Request refresh token
                "prompt" "consent"}
        query-string (->> params
                          (map (fn [[k v]] (str k "=" (js/encodeURIComponent v))))
                          (str/join "&"))]
    (str auth-endpoint "?" query-string)))

(defn parse-redirect-params
  "Parse OAuth redirect URL parameters from window.location.

  Returns map with :code, :state, :error if present."
  []
  (let [url-params (js/URLSearchParams. js/window.location.search)]
    {:code (.get url-params "code")
     :state (.get url-params "state")
     :error (.get url-params "error")
     :error-description (.get url-params "error_description")}))

(defn validate-state
  "Validate OAuth state parameter matches stored value (CSRF protection).

  Returns true if valid, false otherwise."
  [received-state stored-state]
  (and received-state
       stored-state
       (= received-state stored-state)))

(defn exchange-code-for-token
  "Exchange authorization code for access token.

  Returns a promise that resolves to token data.

  config: {:client-id, :redirect-uri, :token-endpoint}
  code: authorization code from redirect
  verifier: PKCE code verifier"
  [{:keys [client-id redirect-uri token-endpoint]} code verifier]
  (js/Promise.
   (fn [resolve reject]
     (let [body (js/URLSearchParams.
                 (clj->js {"client_id" client-id
                           "code" code
                           "code_verifier" verifier
                           "grant_type" "authorization_code"
                           "redirect_uri" redirect-uri}))]
       (-> (js/fetch token-endpoint
                     (clj->js {:method "POST"
                               :headers {"Content-Type" "application/x-www-form-urlencoded"}
                               :body body}))
           (.then (fn [response]
                    (if (.-ok response)
                      (.json response)
                      (reject (js/Error. (str "Token exchange failed: " (.-status response)))))))
           (.then (fn [data]
                    (resolve {:access-token (.-access_token data)
                              :expires-in (.-expires_in data)
                              :refresh-token (.-refresh_token data)
                              :token-type (.-token_type data)})))
           (.catch reject))))))

(defn refresh-access-token
  "Refresh an expired access token using refresh token.

  Returns a promise that resolves to new token data.

  config: {:client-id, :token-endpoint}
  refresh-token: stored refresh token"
  [{:keys [client-id token-endpoint]} refresh-token]
  (js/Promise.
   (fn [resolve reject]
     (if-not refresh-token
       (reject (js/Error. "No refresh token available"))
       (let [body (js/URLSearchParams.
                   (clj->js {"client_id" client-id
                             "grant_type" "refresh_token"
                             "refresh_token" refresh-token}))]
         (-> (js/fetch token-endpoint
                       (clj->js {:method "POST"
                                 :headers {"Content-Type" "application/x-www-form-urlencoded"}
                                 :body body}))
             (.then (fn [response]
                      (if (.-ok response)
                        (.json response)
                        (reject (js/Error. (str "Token refresh failed: " (.-status response)))))))
             (.then (fn [data]
                      (resolve {:access-token (.-access_token data)
                                :expires-in (.-expires_in data)
                                :token-type (.-token_type data)})))
             (.catch reject)))))))

;; ============================================================================
;; Public API
;; ============================================================================

(defn connected?
  "Check if user is connected to a cloud provider."
  [provider]
  (token-valid? provider))

(defn get-user-info
  "Get stored user info for provider (email, name, etc)."
  [provider]
  (:user-info (get-token provider)))

(defn disconnect!
  "Disconnect from cloud provider (clear tokens)."
  [provider]
  (clear-token! provider))
