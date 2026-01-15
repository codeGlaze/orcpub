(ns orcpub.cloud.views
  "UI components for cloud storage integration (Google Drive, Dropbox).

  Provides:
  - Connection status indicator
  - Connect/disconnect buttons
  - File browser for cloud files
  - Import/export buttons"
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [orcpub.cloud.events :as cloud-events]
            [clojure.string :as str]))

;; ============================================================================
;; Utility Components
;; ============================================================================

(defn loading-spinner
  "Simple loading spinner"
  []
  [:div.flex.items-center.justify-center.p-20
   [:div.spinner "Loading..."]])

(defn status-badge
  "Status indicator badge"
  [status]
  (let [colors {:idle "bg-gray-200 text-gray-700"
                :uploading "bg-blue-500 text-white"
                :downloading "bg-blue-500 text-white"
                :loading "bg-blue-500 text-white"
                :authenticating "bg-yellow-500 text-white"
                :deleting "bg-red-500 text-white"
                :error "bg-red-500 text-white"}
        labels {:idle "Ready"
                :uploading "Uploading..."
                :downloading "Downloading..."
                :loading "Loading..."
                :authenticating "Connecting..."
                :deleting "Deleting..."
                :error "Error"}]
    [:span.inline-block.px-3.py-1.text-xs.font-semibold.rounded-full
     {:class (get colors status "bg-gray-200 text-gray-700")}
     (get labels status "Unknown")]))

;; ============================================================================
;; Google Drive Connection Component
;; ============================================================================

(defn google-drive-connection-status
  "Display Google Drive connection status and connect/disconnect button"
  []
  (let [enabled? @(rf/subscribe [::cloud-events/google-drive-enabled?])
        connected? @(rf/subscribe [::cloud-events/google-drive-connected?])
        user @(rf/subscribe [::cloud-events/google-drive-user])
        sync-status @(rf/subscribe [::cloud-events/google-drive-sync-status])]
    [:div.border.rounded.p-4.mb-4.bg-white.shadow-sm
     [:div.flex.items-center.justify-between
      [:div.flex.items-center
       [:img.w-8.h-8.mr-3
        {:src "https://www.gstatic.com/images/branding/product/1x/drive_2020q4_48dp.png"
         :alt "Google Drive"}]
       [:div
        [:h3.text-lg.font-semibold "Google Drive"]
        (if-not enabled?
          [:p.text-sm.text-gray-500 "Not configured"]
          (if connected?
            [:div
             [:p.text-sm.text-green-600 (str "Connected as " (:email user))]
             [:p.text-xs.text-gray-500 (:name user)]]
            [:p.text-sm.text-gray-500 "Not connected"]))]]
      [:div.flex.items-center.gap-2
       [status-badge sync-status]
       (if-not enabled?
         [:button.px-4.py-2.bg-gray-300.text-gray-500.rounded.cursor-not-allowed
          {:disabled true}
          "Not Configured"]
         (if connected?
           [:button.px-4.py-2.bg-red-500.text-white.rounded.hover:bg-red-600
            {:on-click #(rf/dispatch [::cloud-events/disconnect-google-drive])}
            "Disconnect"]
           [:button.px-4.py-2.bg-blue-500.text-white.rounded.hover:bg-blue-600
            {:on-click #(rf/dispatch [::cloud-events/init-google-drive])}
            "Connect"]))]]]))

;; ============================================================================
;; Google Drive File Browser
;; ============================================================================

(defn format-file-size
  "Format bytes to human-readable size"
  [bytes]
  (let [kb 1024
        mb (* kb 1024)
        gb (* mb 1024)]
    (cond
      (nil? bytes) "Unknown"
      (> bytes gb) (str (.toFixed (/ bytes gb) 2) " GB")
      (> bytes mb) (str (.toFixed (/ bytes mb) 2) " MB")
      (> bytes kb) (str (.toFixed (/ bytes kb) 2) " KB")
      :else (str bytes " B"))))

(defn format-date
  "Format ISO date string to readable format"
  [date-str]
  (when date-str
    (try
      (.toLocaleDateString (js/Date. date-str))
      (catch js/Error _ date-str))))

(defn google-drive-file-row
  "Single file row in the file browser"
  [{:keys [id name createdTime modifiedTime size]}]
  (let [sync-status @(rf/subscribe [::cloud-events/google-drive-sync-status])]
    [:tr.border-b.hover:bg-gray-50
     [:td.px-4.py-3.text-sm.font-medium.text-gray-900 name]
     [:td.px-4.py-3.text-sm.text-gray-500 (format-file-size size)]
     [:td.px-4.py-3.text-sm.text-gray-500 (format-date modifiedTime)]
     [:td.px-4.py-3.text-sm.text-right
      [:div.flex.gap-2.justify-end
       [:button.px-3.py-1.text-xs.bg-blue-500.text-white.rounded.hover:bg-blue-600
        {:on-click #(rf/dispatch [::cloud-events/import-from-google-drive id name])
         :disabled (not= sync-status :idle)}
        "Import"]
       [:button.px-3.py-1.text-xs.bg-red-500.text-white.rounded.hover:bg-red-600
        {:on-click #(when (js/confirm (str "Delete '" name "' from Google Drive?"))
                      (rf/dispatch [::cloud-events/delete-from-google-drive id name]))
         :disabled (not= sync-status :idle)}
        "Delete"]]]]))

(defn google-drive-file-browser
  "File browser for Google Drive .orcbrew files"
  []
  (let [connected? @(rf/subscribe [::cloud-events/google-drive-connected?])
        files @(rf/subscribe [::cloud-events/google-drive-files])
        sync-status @(rf/subscribe [::cloud-events/google-drive-sync-status])]
    (if-not connected?
      [:div.border.rounded.p-4.mb-4.bg-gray-50.text-center.text-gray-500
       [:p "Connect to Google Drive to browse your .orcbrew files"]]
      [:div.border.rounded.p-4.mb-4.bg-white.shadow-sm
       [:div.flex.items-center.justify-between.mb-4
        [:h4.text-lg.font-semibold "Your .orcbrew Files"]
        [:button.px-3.py-1.text-sm.bg-gray-500.text-white.rounded.hover:bg-gray-600
         {:on-click #(rf/dispatch [::cloud-events/list-google-drive-files])
          :disabled (not= sync-status :idle)}
         "↻ Refresh"]]
       (if (= sync-status :loading)
         [loading-spinner]
         (if (empty? files)
           [:div.text-center.py-8.text-gray-500
            [:p "No .orcbrew files found in your Google Drive"]
            [:p.text-sm.mt-2 "Upload a file to get started"]]
           [:div.overflow-x-auto
            [:table.min-w-full.divide-y.divide-gray-200
             [:thead.bg-gray-50
              [:tr
               [:th.px-4.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase "Name"]
               [:th.px-4.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase "Size"]
               [:th.px-4.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase "Modified"]
               [:th.px-4.py-3.text-right.text-xs.font-medium.text-gray-500.uppercase "Actions"]]]
             [:tbody.bg-white.divide-y.divide-gray-200
              (for [file files]
                ^{:key (:id file)}
                [google-drive-file-row file])]]]))]))

;; ============================================================================
;; Export to Google Drive Component
;; ============================================================================

(defn export-to-google-drive-button
  "Button to export current plugin to Google Drive"
  [plugin-name plugin]
  (let [connected? @(rf/subscribe [::cloud-events/google-drive-connected?])
        sync-status @(rf/subscribe [::cloud-events/google-drive-sync-status])]
    [:button.px-4.py-2.bg-green-500.text-white.rounded.hover:bg-green-600.disabled:bg-gray-300.disabled:cursor-not-allowed
     {:on-click #(rf/dispatch [::cloud-events/export-to-google-drive plugin-name plugin true])
      :disabled (or (not connected?) (not= sync-status :idle))}
     "↑ Export to Google Drive"]))

(defn export-all-to-google-drive-button
  "Button to export all plugins to Google Drive"
  []
  (let [connected? @(rf/subscribe [::cloud-events/google-drive-connected?])
        sync-status @(rf/subscribe [::cloud-events/google-drive-sync-status])]
    [:button.px-4.py-2.bg-green-500.text-white.rounded.hover:bg-green-600.disabled:bg-gray-300.disabled:cursor-not-allowed
     {:on-click #(rf/dispatch [::cloud-events/export-all-to-google-drive true])
      :disabled (or (not connected?) (not= sync-status :idle))}
     "↑ Export All to Google Drive"]))

;; ============================================================================
;; Main Cloud Storage Panel
;; ============================================================================

(defn cloud-storage-panel
  "Main cloud storage panel with all features"
  []
  [:div.max-w-4xl.mx-auto.p-6
   [:h2.text-2xl.font-bold.mb-6 "Cloud Storage"]
   [:p.text-gray-600.mb-6
    "Save and load your homebrew content from cloud storage. "
    "Access your .orcbrew files from any device."]

   ;; Google Drive Section
   [:div.mb-8
    [:h3.text-xl.font-semibold.mb-4 "Google Drive"]
    [google-drive-connection-status]
    [google-drive-file-browser]]

   ;; Export Actions
   (let [connected? @(rf/subscribe [::cloud-events/google-drive-connected?])]
     (when connected?
       [:div.border.rounded.p-4.bg-white.shadow-sm
        [:h4.text-lg.font-semibold.mb-4 "Export to Cloud"]
        [:div.flex.gap-3
         [export-all-to-google-drive-button]]]))])

;; ============================================================================
;; Compact Widget (for embedding in existing UI)
;; ============================================================================

(defn google-drive-widget
  "Compact Google Drive widget for embedding in existing pages"
  []
  (let [enabled? @(rf/subscribe [::cloud-events/google-drive-enabled?])
        connected? @(rf/subscribe [::cloud-events/google-drive-connected?])
        user @(rf/subscribe [::cloud-events/google-drive-user])]
    (when enabled?
      [:div.inline-flex.items-center.gap-2.px-3.py-2.bg-white.border.rounded.shadow-sm
       [:img.w-5.h-5
        {:src "https://www.gstatic.com/images/branding/product/1x/drive_2020q4_48dp.png"
         :alt "Google Drive"}]
       (if connected?
         [:div.flex.items-center.gap-2
          [:span.text-sm.text-green-600 "✓"]
          [:span.text-sm.text-gray-700 (str "Drive: " (:email user))]]
         [:button.text-sm.text-blue-600.hover:text-blue-700
          {:on-click #(rf/dispatch [::cloud-events/init-google-drive])}
          "Connect Drive"])])))

;; ============================================================================
;; OAuth Callback Handler Page
;; ============================================================================

(defn google-oauth-callback-page
  "Page to handle Google OAuth redirect callback.

  This should be rendered at /oauth/google/callback route."
  []
  (let [handled? (r/atom false)]
    (fn []
      (when-not @handled?
        (reset! handled? true)
        (rf/dispatch [::cloud-events/handle-google-callback]))
      [:div.flex.items-center.justify-center.min-h-screen.bg-gray-50
       [:div.text-center
        [:div.mb-4
         [:img.w-16.h-16.mx-auto.animate-spin
          {:src "https://www.gstatic.com/images/branding/product/1x/drive_2020q4_48dp.png"
           :alt "Google Drive"}]]
        [:h2.text-xl.font-semibold.mb-2 "Connecting to Google Drive..."]
        [:p.text-gray-600 "Please wait while we complete the authentication."]]])))

;; ============================================================================
;; Public API Summary
;; ============================================================================

(comment
  "UI Components:

  Main Panel:
  - [cloud-storage-panel] - Full-featured cloud storage management UI

  Individual Components:
  - [google-drive-connection-status] - Connection status and connect/disconnect
  - [google-drive-file-browser] - Browse and manage cloud files
  - [export-to-google-drive-button plugin-name plugin] - Export single plugin
  - [export-all-to-google-drive-button] - Export all plugins

  Widgets:
  - [google-drive-widget] - Compact widget for embedding in existing UI

  Pages:
  - [google-oauth-callback-page] - OAuth redirect handler (route: /oauth/google/callback)

  Usage Example:

  ;; In your main view
  (when (some-condition?)
    [cloud-storage-panel])

  ;; Or embed the widget
  [google-drive-widget]")
