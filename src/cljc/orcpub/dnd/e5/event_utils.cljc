(ns ^{:doc "Shared utility functions used by both event handlers and subscription
     files. Extracted from events.cljs to break the circular dependency:
     subs.cljs -> events.cljs -> (can't import subs.cljs)."}
  orcpub.dnd.e5.event-utils
  (:require [orcpub.modifiers :as mod]
            [orcpub.route-map :as routes]
            [clojure.string :as s]))

;; -- URL helpers (CLJS-only: depend on js/window.location) --

#?(:cljs
   (defn backend-url
     "Rewrites a path to the backend URL in local dev (port 8890)."
     [path]
     (if (and js/window.location
              (s/starts-with? js/window.location.href "http://localhost"))
       (str "http://localhost:8890" (if (not (s/starts-with? path "/")) "/") path)
       path)))

#?(:cljs
   (defn url-for-route
     "Builds a full backend URL for a bidi route."
     [route & args]
     (backend-url (apply routes/path-for route args))))

;; -- Auth --

(defn auth-headers
  "Returns Authorization header map from db. Handles nil token gracefully."
  [db]
  (let [token (-> db :user-data :token)]
    (if token
      {"Authorization" (str "Token " token)}
      {})))

;; -- Error helpers --

(defn show-generic-error
  "Returns a dispatch vector for the generic error message."
  []
  [:show-error-message [:div "There was an error, please refresh your browser and try again."]])

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
