(ns orcpub.cloud.events
  "re-frame events for cloud storage integration (Google Drive, Dropbox).

  Integrates with existing OrcPub import/export logic in orcpub.dnd.e5.events."
  (:require [re-frame.core :as rf]
            [orcpub.cloud.google-drive :as gdrive]
            [orcpub.dnd.e5.events :as e5]
            [clojure.pprint :as pprint]
            [clojure.string :as str]))

;; ============================================================================
;; State Management
;; ============================================================================

(rf/reg-sub
  ::cloud-state
  (fn [db _]
    (get db :cloud-storage
         {:google-drive {:connected? false
                         :user-info nil
                         :files []
                         :sync-status :idle
                         :last-sync nil
                         :error nil}
          :dropbox {:connected? false
                    :user-info nil
                    :files []
                    :sync-status :idle
                    :last-sync nil
                    :error nil}})))

(rf/reg-sub
  ::google-drive-connected?
  :<- [::cloud-state]
  (fn [state _]
    (get-in state [:google-drive :connected?])))

(rf/reg-sub
  ::google-drive-user
  :<- [::cloud-state]
  (fn [state _]
    (get-in state [:google-drive :user-info])))

(rf/reg-sub
  ::google-drive-files
  :<- [::cloud-state]
  (fn [state _]
    (get-in state [:google-drive :files])))

(rf/reg-sub
  ::google-drive-sync-status
  :<- [::cloud-state]
  (fn [state _]
    (get-in state [:google-drive :sync-status])))

(rf/reg-sub
  ::google-drive-enabled?
  (fn [_ _]
    (gdrive/enabled?)))

;; ============================================================================
;; Google Drive Authentication Events
;; ============================================================================

(rf/reg-event-fx
  ::init-google-drive
  (fn [{:keys [db]} _]
    (if-not (gdrive/enabled?)
      {:db db
       :dispatch [:show-error-message "Google Drive integration not configured. Contact administrator."]}
      {:db (assoc-in db [:cloud-storage :google-drive :sync-status] :authenticating)
       :fx [[:google-auth nil]]})))

(rf/reg-fx
  :google-auth
  (fn [_]
    (-> (gdrive/init-auth!)
        (.catch (fn [error]
                  (rf/dispatch [::google-auth-failed (.-message error)]))))))

(rf/reg-event-fx
  ::handle-google-callback
  (fn [{:keys [db]} _]
    {:db (assoc-in db [:cloud-storage :google-drive :sync-status] :authenticating)
     :fx [[:google-callback nil]]}))

(rf/reg-fx
  :google-callback
  (fn [_]
    (-> (gdrive/handle-callback!)
        (.then (fn [user-info]
                 (rf/dispatch [::google-auth-success user-info])))
        (.catch (fn [error]
                  (rf/dispatch [::google-auth-failed (.-message error)]))))))

(rf/reg-event-fx
  ::google-auth-success
  (fn [{:keys [db]} [_ user-info]]
    {:db (-> db
             (assoc-in [:cloud-storage :google-drive :connected?] true)
             (assoc-in [:cloud-storage :google-drive :user-info] user-info)
             (assoc-in [:cloud-storage :google-drive :sync-status] :idle)
             (assoc-in [:cloud-storage :google-drive :error] nil))
     :dispatch [:show-success-message (str "Connected to Google Drive as " (:email user-info))]}))

(rf/reg-event-fx
  ::google-auth-failed
  (fn [{:keys [db]} [_ error-message]]
    {:db (-> db
             (assoc-in [:cloud-storage :google-drive :connected?] false)
             (assoc-in [:cloud-storage :google-drive :sync-status] :idle)
             (assoc-in [:cloud-storage :google-drive :error] error-message))
     :dispatch [:show-error-message (str "Google Drive authentication failed: " error-message)]}))

(rf/reg-event-fx
  ::disconnect-google-drive
  (fn [{:keys [db]} _]
    (gdrive/disconnect!)
    {:db (-> db
             (assoc-in [:cloud-storage :google-drive :connected?] false)
             (assoc-in [:cloud-storage :google-drive :user-info] nil)
             (assoc-in [:cloud-storage :google-drive :files] [])
             (assoc-in [:cloud-storage :google-drive :sync-status] :idle)
             (assoc-in [:cloud-storage :google-drive :error] nil))
     :dispatch [:show-success-message "Disconnected from Google Drive"]}))

;; ============================================================================
;; Google Drive File Listing
;; ============================================================================

(rf/reg-event-fx
  ::list-google-drive-files
  (fn [{:keys [db]} _]
    (if-not (gdrive/connected?)
      {:dispatch [:show-error-message "Not connected to Google Drive"]}
      {:db (assoc-in db [:cloud-storage :google-drive :sync-status] :loading)
       :fx [[:list-drive-files nil]]})))

(rf/reg-fx
  :list-drive-files
  (fn [_]
    (-> (gdrive/list-orcbrew-files)
        (.then (fn [files]
                 (rf/dispatch [::list-files-success :google-drive files])))
        (.catch (fn [error]
                  (rf/dispatch [::list-files-failed :google-drive (.-message error)]))))))

(rf/reg-event-fx
  ::list-files-success
  (fn [{:keys [db]} [_ provider files]]
    {:db (-> db
             (assoc-in [:cloud-storage provider :files] files)
             (assoc-in [:cloud-storage provider :sync-status] :idle)
             (assoc-in [:cloud-storage provider :last-sync] (js/Date.)))
     :dispatch [:show-success-message (str "Found " (count files) " .orcbrew files in Google Drive")]}))

(rf/reg-event-fx
  ::list-files-failed
  (fn [{:keys [db]} [_ provider error-message]]
    {:db (-> db
             (assoc-in [:cloud-storage provider :sync-status] :idle)
             (assoc-in [:cloud-storage provider :error] error-message))
     :dispatch [:show-error-message (str "Failed to list files: " error-message)]}))

;; ============================================================================
;; Google Drive Export (Upload)
;; ============================================================================

(rf/reg-event-fx
  ::export-to-google-drive
  (fn [{:keys [db]} [_ plugin-name plugin pretty-print?]]
    (if-not (gdrive/connected?)
      {:dispatch [:show-error-message "Not connected to Google Drive. Connect first."]}
      (let [content (if pretty-print?
                      (with-out-str (pprint/pprint plugin))
                      (str plugin))
            filename (str plugin-name ".orcbrew")]
        {:db (assoc-in db [:cloud-storage :google-drive :sync-status] :uploading)
         :fx [[:upload-to-drive {:filename filename :content content}]]}))))

(rf/reg-event-fx
  ::export-all-to-google-drive
  (fn [{:keys [db]} [_ pretty-print?]]
    (if-not (gdrive/connected?)
      {:dispatch [:show-error-message "Not connected to Google Drive. Connect first."]}
      (let [plugins (:plugins db)
            content (if pretty-print?
                      (with-out-str (pprint/pprint plugins))
                      (str plugins))
            filename "all-content.orcbrew"]
        {:db (assoc-in db [:cloud-storage :google-drive :sync-status] :uploading)
         :fx [[:upload-to-drive {:filename filename :content content}]]}))))

(rf/reg-fx
  :upload-to-drive
  (fn [{:keys [filename content]}]
    (-> (gdrive/upload-file filename content)
        (.then (fn [file-info]
                 (rf/dispatch [::upload-success file-info])))
        (.catch (fn [error]
                  (rf/dispatch [::upload-failed (.-message error)]))))))

(rf/reg-event-fx
  ::upload-success
  (fn [{:keys [db]} [_ file-info]]
    {:db (assoc-in db [:cloud-storage :google-drive :sync-status] :idle)
     :dispatch-n [[:show-success-message (str "Uploaded '" (:name file-info) "' to Google Drive")]
                  [::list-google-drive-files]]}))

(rf/reg-event-fx
  ::upload-failed
  (fn [{:keys [db]} [_ error-message]]
    {:db (-> db
             (assoc-in [:cloud-storage :google-drive :sync-status] :idle)
             (assoc-in [:cloud-storage :google-drive :error] error-message))
     :dispatch [:show-error-message (str "Upload failed: " error-message)]}))

;; ============================================================================
;; Google Drive Import (Download)
;; ============================================================================

(rf/reg-event-fx
  ::import-from-google-drive
  (fn [{:keys [db]} [_ file-id filename]]
    (if-not (gdrive/connected?)
      {:dispatch [:show-error-message "Not connected to Google Drive"]}
      {:db (assoc-in db [:cloud-storage :google-drive :sync-status] :downloading)
       :fx [[:download-from-drive {:file-id file-id :filename filename}]]})))

(rf/reg-fx
  :download-from-drive
  (fn [{:keys [file-id filename]}]
    (-> (gdrive/download-file file-id)
        (.then (fn [content]
                 (rf/dispatch [::download-success filename content])))
        (.catch (fn [error]
                  (rf/dispatch [::download-failed (.-message error)]))))))

(rf/reg-event-fx
  ::download-success
  (fn [{:keys [db]} [_ filename content]]
    (let [plugin-name (str/replace filename #"\.orcbrew$" "")]
      {:db (assoc-in db [:cloud-storage :google-drive :sync-status] :idle)
       :dispatch-n [[::e5/import-plugin plugin-name content]
                    [:show-success-message (str "Imported '" filename "' from Google Drive")]]})))

(rf/reg-event-fx
  ::download-failed
  (fn [{:keys [db]} [_ error-message]]
    {:db (-> db
             (assoc-in [:cloud-storage :google-drive :sync-status] :idle)
             (assoc-in [:cloud-storage :google-drive :error] error-message))
     :dispatch [:show-error-message (str "Download failed: " error-message)]}))

;; ============================================================================
;; Google Drive Delete
;; ============================================================================

(rf/reg-event-fx
  ::delete-from-google-drive
  (fn [{:keys [db]} [_ file-id filename]]
    (if-not (gdrive/connected?)
      {:dispatch [:show-error-message "Not connected to Google Drive"]}
      {:db (assoc-in db [:cloud-storage :google-drive :sync-status] :deleting)
       :fx [[:delete-from-drive {:file-id file-id :filename filename}]]})))

(rf/reg-fx
  :delete-from-drive
  (fn [{:keys [file-id filename]}]
    (-> (gdrive/delete-file file-id)
        (.then (fn [_]
                 (rf/dispatch [::delete-success filename])))
        (.catch (fn [error]
                  (rf/dispatch [::delete-failed (.-message error)]))))))

(rf/reg-event-fx
  ::delete-success
  (fn [{:keys [db]} [_ filename]]
    {:db (assoc-in db [:cloud-storage :google-drive :sync-status] :idle)
     :dispatch-n [[:show-success-message (str "Deleted '" filename "' from Google Drive")]
                  [::list-google-drive-files]]}))

(rf/reg-event-fx
  ::delete-failed
  (fn [{:keys [db]} [_ error-message]]
    {:db (-> db
             (assoc-in [:cloud-storage :google-drive :sync-status] :idle)
             (assoc-in [:cloud-storage :google-drive :error] error-message))
     :dispatch [:show-error-message (str "Delete failed: " error-message)]}))

;; ============================================================================
;; Public API Summary
;; ============================================================================

(comment
  "Events:

  Authentication:
  - [::init-google-drive] - Start OAuth flow (redirects to Google)
  - [::handle-google-callback] - Handle OAuth redirect callback
  - [::disconnect-google-drive] - Sign out

  File Operations:
  - [::list-google-drive-files] - Refresh file list
  - [::export-to-google-drive plugin-name plugin pretty-print?] - Upload plugin
  - [::export-all-to-google-drive pretty-print?] - Upload all plugins
  - [::import-from-google-drive file-id filename] - Download and import
  - [::delete-from-google-drive file-id filename] - Delete file

  Subscriptions:
  - @(rf/subscribe [::google-drive-enabled?]) - Is integration configured?
  - @(rf/subscribe [::google-drive-connected?]) - Is user authenticated?
  - @(rf/subscribe [::google-drive-user]) - User info {:email :name :picture}
  - @(rf/subscribe [::google-drive-files]) - List of files
  - @(rf/subscribe [::google-drive-sync-status]) - Current status (:idle :uploading :downloading :loading)")
