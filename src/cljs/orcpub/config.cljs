(ns orcpub.config
  "Client-side configuration access.
  Reads configuration injected by server into window.orcpubConfig")

(defn get-config
  "Returns the configuration map injected by the server"
  []
  (js->clj (.-orcpubConfig js/window) :keywordize-keys true))

(defn google-drive-enabled?
  "Returns true if Google Drive integration is enabled"
  []
  (:google-drive-enabled (get-config)))

(defn google-client-id
  "Returns Google OAuth Client ID if configured"
  []
  (:google-client-id (get-config)))
