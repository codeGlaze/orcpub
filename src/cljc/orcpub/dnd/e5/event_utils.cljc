(ns ^{:doc "Shared utility functions used by both event handlers and subscription
     files. Extracted from events.cljs to break the circular dependency:
     subs.cljs -> events.cljs -> (can't import subs.cljs)."}
  orcpub.dnd.e5.event-utils
  (:require [orcpub.modifiers :as mod]
            [orcpub.route-map :as routes]
            [clojure.string :as s]
            #?(:cljs [re-frame.core :refer [dispatch]])))

;; -- URL helpers (CLJS-only: depend on js/window.location) --

#?(:cljs
   (defn backend-url
     "A path on the server that served the page.

      It used to send every http://localhost page to port 8890, from when figwheel served the page
      on another port. dev.cljs.edn now opens the app on the server itself, so that rewrite only
      broke a server on any other localhost port: logins and API calls went to 8890."
     [path]
     (if (s/starts-with? path "/") path (str "/" path))))

#?(:cljs
   (defn url-for-route
     "Builds a full backend URL for a bidi route."
     [route & args]
     (backend-url (apply routes/path-for route args))))

;; -- Auth --

(defn get-auth-token
  "The auth token from app-db, or nil when nobody is logged in.

   The one place the token path [:user-data :token] is read. Use it for the token itself (an
   Authorization header, an :http :auth-token) and as the logged-in check,
   (when (get-auth-token db) ...). A guard that reads another key fails silently: the old
   (:token (:user db)) was always nil, so the fetch it guarded never fired."
  [db]
  (-> db :user-data :token))

(defn auth-headers
  "Returns Authorization header map from db. Handles nil token gracefully."
  [db]
  (if-let [token (get-auth-token db)]
    {"Authorization" (str "Token " token)}
    {}))

;; -- Error helpers --

(defn show-generic-error
  "Returns a dispatch vector for the generic error message."
  []
  [:show-error-message [:div "There was an error, please refresh your browser and try again."]])

#?(:cljs
   (defn handle-api-response
     "Dispatch on HTTP response status with sensible defaults.
      on-success is called for 200. Options:
        :on-401  — called on 401 (default: dispatch :route-to-login)
        :on-500  — called on 500 (default: dispatch show-generic-error)
        :context — string describing the request, used in console warning for unhandled statuses"
     [response on-success & {:keys [on-401 on-500 context]}]
     (case (:status response)
       200 (on-success)
       401 (if on-401
             (on-401)
             (dispatch [:route-to-login]))
       500 (if on-500
             (on-500)
             (dispatch (show-generic-error)))
       (js/console.warn "Unhandled HTTP status:" (:status response)
                        (str "(" (or context "unknown request") ")")))))

;; -- Modifier config --

(defn mod-cfg
  "Builds a modifier config map."
  [key & args]
  {::mod/key key
   ::mod/args args})

(defmulti mod-key
  "Extracts a comparison key from a modifier config."
  (fn [{:keys [::mod/key ::mod/args] :as item}]
    key))

(defmethod mod-key :ability [{:keys [::mod/key ::mod/args]}]
  [key (first args)])

(defmethod mod-key :ability-override [{:keys [::mod/key ::mod/args]}]
  [key (first args)])

(defmethod mod-key :default [{:keys [::mod/key ::mod/args]}]
  [key args])

(defn compare-mod-keys
  "Comparator for modifier configs, used by sorted-set."
  [item-1 item-2]
  (compare (mod-key item-1)
           (mod-key item-2)))

(defn default-mod-set
  "Ensures a modifier set is a sorted-set ordered by compare-mod-keys."
  [mod-set]
  (if (and (set? mod-set)
           (sorted? mod-set))
    mod-set
    (into (sorted-set-by compare-mod-keys) mod-set)))
