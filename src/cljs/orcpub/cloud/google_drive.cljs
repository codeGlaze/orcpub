(ns orcpub.cloud.google-drive
  "Google Drive API integration for OrcPub homebrew import/export.

  Provides OAuth authentication and file operations using Google Drive API v3.
  All operations are browser-side only - no backend server involvement.

  Scope used: drive.file (per-file access, no verification needed)
  API: https://developers.google.com/drive/api/v3/reference"
  (:require [orcpub.cloud.oauth :as oauth]
            [clojure.string :as str]))

;; ============================================================================
;; Configuration
;; ============================================================================

(def ^:private auth-config
  "Google OAuth configuration.
  Client ID injected by backend when GOOGLE_CLIENT_ID env var is set."
  {:client-id (.-googleClientId js/window)
   :auth-endpoint "https://accounts.google.com/o/oauth2/v2/auth"
   :token-endpoint "https://oauth2.googleapis.com/token"
   :redirect-uri (str js/window.location.origin "/oauth/google/callback")
   :scope "https://www.googleapis.com/auth/drive.file"})

(def ^:private api-base "https://www.googleapis.com/drive/v3")
(def ^:private upload-base "https://www.googleapis.com/upload/drive/v3")

;; ============================================================================
;; Feature Detection
;; ============================================================================

(defn enabled?
  "Check if Google Drive integration is enabled (client ID configured)."
  []
  (some? (:client-id auth-config)))

(defn connected?
  "Check if user is authenticated with Google Drive."
  []
  (oauth/connected? :google-drive))

;; ============================================================================
;; Authentication
;; ============================================================================

(defn init-auth!
  "Initialize Google Drive OAuth flow.

  Returns a promise that resolves when user completes authentication.

  Steps:
  1. Generate PKCE parameters
  2. Store verifier in sessionStorage
  3. Redirect to Google OAuth consent screen
  4. User will be redirected back to callback URL after consent"
  []
  (js/Promise.
   (fn [resolve reject]
     (if-not (enabled?)
       (reject (js/Error. "Google Drive integration not enabled. Set GOOGLE_CLIENT_ID environment variable."))
       (try
         (let [verifier (oauth/generate-code-verifier)
               state (oauth/generate-state)]
           (-> (oauth/generate-code-challenge verifier)
               (.then (fn [challenge]
                        ;; Store PKCE params for callback
                        (oauth/store-pkce-params! state verifier)
                        ;; Build auth URL and redirect
                        (let [auth-url (oauth/build-auth-url
                                        auth-config
                                        {:state state
                                         :code-challenge challenge})]
                          ;; Redirect to Google
                          (set! js/window.location.href auth-url)
                          ;; Promise never resolves here since we redirect
                          ;; Actual completion happens in handle-callback!
                          resolve)))
               (.catch reject)))
         (catch js/Error e
           (reject e)))))))

(defn handle-callback!
  "Handle OAuth redirect callback from Google.

  Should be called on the redirect URI page to complete authentication.

  Returns a promise that resolves to user info on success."
  []
  (js/Promise.
   (fn [resolve reject]
     (try
       (let [params (oauth/parse-redirect-params)
             {:keys [code state error error-description]} params]
         (cond
           ;; OAuth error
           error
           (reject (js/Error. (str "OAuth failed: " error
                                   (when error-description
                                     (str " - " error-description)))))

           ;; Missing code
           (not code)
           (reject (js/Error. "No authorization code in redirect"))

           ;; Success - exchange code for token
           :else
           (let [{stored-state :state verifier :verifier} (oauth/get-pkce-params)]
             ;; Validate state (CSRF protection)
             (if-not (oauth/validate-state state stored-state)
               (reject (js/Error. "Invalid OAuth state (possible CSRF attack)"))
               ;; Exchange code for access token
               (-> (oauth/exchange-code-for-token auth-config code verifier)
                   (.then (fn [token-data]
                            ;; Get user info
                            (-> (get-user-info (:access-token token-data))
                                (.then (fn [user-info]
                                         ;; Store token with user info
                                         (oauth/store-token! :google-drive
                                                             (assoc token-data :user-info user-info))
                                         (resolve user-info)))
                                (.catch reject))))
                   (.catch reject))))))
       (catch js/Error e
         (reject e))))))

(defn disconnect!
  "Sign out from Google Drive (clear tokens)."
  []
  (oauth/disconnect! :google-drive))

(defn refresh-token-if-needed!
  "Refresh access token if expired or expiring soon.

  Returns a promise that resolves to true if refreshed, false if no refresh needed."
  []
  (js/Promise.
   (fn [resolve reject]
     (if-not (oauth/token-needs-refresh? :google-drive)
       (resolve false)
       (let [{:keys [refresh-token]} (oauth/get-token :google-drive)]
         (-> (oauth/refresh-access-token auth-config refresh-token)
             (.then (fn [token-data]
                      ;; Store refreshed token (preserve user-info)
                      (let [current-user-info (:user-info (oauth/get-token :google-drive))]
                        (oauth/store-token! :google-drive
                                            (assoc token-data :user-info current-user-info)))
                      (resolve true)))
             (.catch reject)))))))

;; ============================================================================
;; API Utilities
;; ============================================================================

(defn- get-access-token
  "Get current access token, throw if not authenticated."
  []
  (let [{:keys [access-token]} (oauth/get-token :google-drive)]
    (if-not access-token
      (throw (js/Error. "Not authenticated with Google Drive"))
      access-token)))

(defn- api-request
  "Make authenticated API request to Google Drive.

  method: HTTP method (:get, :post, :patch, :delete)
  endpoint: API endpoint path
  options: {:query-params, :body, :headers}

  Returns a promise that resolves to parsed JSON response."
  [method endpoint {:keys [query-params body headers]}]
  (js/Promise.
   (fn [resolve reject]
     (-> (refresh-token-if-needed!)
         (.then (fn [_]
                  (let [access-token (get-access-token)
                        url (str api-base endpoint
                                 (when query-params
                                   (str "?" (->> query-params
                                                 (map (fn [[k v]] (str (name k) "=" (js/encodeURIComponent v))))
                                                 (str/join "&")))))
                        request-headers (merge {"Authorization" (str "Bearer " access-token)}
                                               headers)
                        fetch-options (cond-> {:method (name method)
                                               :headers (clj->js request-headers)}
                                        body (assoc :body body))]
                    (-> (js/fetch url (clj->js fetch-options))
                        (.then (fn [response]
                                 (if (.-ok response)
                                   (.json response)
                                   (.then (.text response)
                                          (fn [error-text]
                                            (reject (js/Error. (str "API request failed: " (.-status response)
                                                                    " - " error-text))))))))
                        (.then (fn [data] (resolve (js->clj data :keywordize-keys true))))
                        (.catch reject)))))
         (.catch reject)))))

;; ============================================================================
;; User Info
;; ============================================================================

(defn get-user-info
  "Get user info from Google (email, name, picture).

  access-token: OAuth access token

  Returns a promise that resolves to user info map."
  [access-token]
  (js/Promise.
   (fn [resolve reject]
     (-> (js/fetch "https://www.googleapis.com/oauth2/v2/userinfo"
                   (clj->js {:headers {"Authorization" (str "Bearer " access-token)}}))
         (.then (fn [response]
                  (if (.-ok response)
                    (.json response)
                    (reject (js/Error. (str "Failed to get user info: " (.-status response)))))))
         (.then (fn [data]
                  (resolve {:email (.-email data)
                            :name (.-name data)
                            :picture (.-picture data)})))
         (.catch reject)))))

;; ============================================================================
;; File Operations
;; ============================================================================

(defn list-orcbrew-files
  "List all .orcbrew files in user's Google Drive.

  Returns a promise that resolves to list of file metadata:
  [{:id, :name, :created-time, :modified-time, :size}]

  Files are sorted by modified time (newest first)."
  []
  (api-request :get "/files"
               {:query-params {:q "name contains '.orcbrew' and trashed=false"
                               :fields "files(id,name,createdTime,modifiedTime,size)"
                               :orderBy "modifiedTime desc"
                               :pageSize 100}}))

(defn download-file
  "Download a file from Google Drive.

  file-id: Google Drive file ID

  Returns a promise that resolves to file contents (text)."
  [file-id]
  (js/Promise.
   (fn [resolve reject]
     (-> (refresh-token-if-needed!)
         (.then (fn [_]
                  (let [access-token (get-access-token)
                        url (str api-base "/files/" file-id "?alt=media")]
                    (-> (js/fetch url (clj->js {:headers {"Authorization" (str "Bearer " access-token)}}))
                        (.then (fn [response]
                                 (if (.-ok response)
                                   (.text response)
                                   (reject (js/Error. (str "Download failed: " (.-status response)))))))
                        (.then resolve)
                        (.catch reject)))))
         (.catch reject)))))

(defn upload-file
  "Upload a file to Google Drive.

  filename: Name of file (should end with .orcbrew)
  content: File content (string)
  mime-type: MIME type (default: text/plain)

  Returns a promise that resolves to uploaded file metadata."
  ([filename content]
   (upload-file filename content "text/plain"))
  ([filename content mime-type]
   (js/Promise.
    (fn [resolve reject]
      (-> (refresh-token-if-needed!)
          (.then (fn [_]
                   (let [access-token (get-access-token)
                         metadata (js/JSON.stringify (clj->js {:name filename}))
                         boundary "orcpub_upload_boundary"
                         body (str "--" boundary "\r\n"
                                   "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                                   metadata "\r\n"
                                   "--" boundary "\r\n"
                                   "Content-Type: " mime-type "\r\n\r\n"
                                   content "\r\n"
                                   "--" boundary "--")
                         url (str upload-base "/files?uploadType=multipart")]
                     (-> (js/fetch url
                                   (clj->js {:method "POST"
                                             :headers {"Authorization" (str "Bearer " access-token)
                                                       "Content-Type" (str "multipart/related; boundary=" boundary)}
                                             :body body}))
                         (.then (fn [response]
                                  (if (.-ok response)
                                    (.json response)
                                    (.then (.text response)
                                           (fn [error-text]
                                             (reject (js/Error. (str "Upload failed: " (.-status response)
                                                                     " - " error-text))))))))
                         (.then (fn [data]
                                  (resolve {:id (.-id data)
                                            :name (.-name data)})))
                         (.catch reject)))))
          (.catch reject))))))

(defn delete-file
  "Delete a file from Google Drive.

  file-id: Google Drive file ID

  Returns a promise that resolves when deleted."
  [file-id]
  (api-request :delete (str "/files/" file-id) {}))

;; ============================================================================
;; Public API Summary
;; ============================================================================

(comment
  "Public API:

  Feature Detection:
  - (enabled?) -> boolean
  - (connected?) -> boolean

  Authentication:
  - (init-auth!) -> Promise<void> (redirects to Google)
  - (handle-callback!) -> Promise<user-info>
  - (disconnect!) -> nil

  File Operations:
  - (list-orcbrew-files) -> Promise<[{:id :name :created-time :modified-time :size}]>
  - (download-file file-id) -> Promise<string>
  - (upload-file filename content) -> Promise<{:id :name}>
  - (delete-file file-id) -> Promise<void>

  Usage Example:

  ;; Check if enabled
  (when (enabled?)
    ;; Start auth
    (-> (init-auth!)
        (.catch #(js/console.error \"Auth failed:\" %))))

  ;; On callback page
  (-> (handle-callback!)
      (.then #(js/console.log \"Logged in as:\" (:email %)))
      (.catch #(js/console.error \"Callback failed:\" %)))

  ;; List files
  (-> (list-orcbrew-files)
      (.then #(js/console.log \"Files:\" %)))

  ;; Upload
  (-> (upload-file \"my-homebrew.orcbrew\" \"{:name \\\"My Homebrew\\\"}\")
      (.then #(js/console.log \"Uploaded:\" %)))

  ;; Download
  (-> (download-file \"file-id-here\")
      (.then #(js/console.log \"Content:\" %)))")
