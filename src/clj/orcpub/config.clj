(ns orcpub.config
  "Configuration management for OrcPub features"
  (:require [environ.core :as environ]
            [clojure.string :as str]))

(defn google-client-id
  "Returns Google OAuth Client ID if configured, nil otherwise"
  []
  (let [client-id (environ/env :google-client-id)]
    (when-not (str/blank? client-id)
      client-id)))

(defn google-drive-enabled?
  "Returns true if Google Drive integration is enabled via environment config"
  []
  (boolean (google-client-id)))

(defn client-config
  "Returns configuration map to be passed to frontend JavaScript.
  Only includes non-sensitive configuration values."
  []
  {:google-drive-enabled (google-drive-enabled?)
   :google-client-id (google-client-id)})
