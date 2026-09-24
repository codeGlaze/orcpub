(ns orcpub.dnd.e5.api-subs
  "reg-api-sub registers a subscription that loads from the server the first time it is
   subscribed while a user is logged in: ::mi5e/custom-items, ::char5e/characters,
   ::party5e/parties, ::folder5e/folders and :user.

   A namespace of its own because it needs cljs-only requires, which rules out
   event_utils.cljc, and because equipment_subs uses it as well as subs, which would
   otherwise have to require subs."
  (:require [re-frame.core :refer [reg-sub-raw dispatch]]
            [reagent.ratom :as ra]
            [orcpub.dnd.e5.event-utils :as event-utils]
            [orcpub.dnd.e5.http-safe :as http]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn rejected-token!
  "What a loader does on a 401: the server no longer accepts the token it sent, so log out, then
   run the sub's :on-401 with its query, or route to login when it has none.

   Log out with :clear-login, not :set-user-data: that one merges into :user-data, and a merge
   cannot remove the token. Does nothing when the token changed while the request was out, since
   that 401 is about a login that has already been replaced."
  [app-db sent-token on-401 query-v]
  (when (= sent-token (event-utils/get-auth-token @app-db))
    (dispatch [:clear-login])
    (if on-401
      (on-401 query-v)
      (dispatch [:route-to-login]))))

(defn reg-api-sub
  "Register a `reg-sub-raw` that lazy-loads from a backend endpoint
   when the user is logged in.

   Required opts:
     :sub-key   — subscription registration keyword
     :route     — bidi route (passed to `url-for-route`)
     :db-key    — where cached results live; keyword or vec path for
                  `get-in`

   Optional opts:
     :set-event  — shorthand: success dispatches
                   `[set-event (:body response)]`
     :on-success — 1-arg fn called with the full response; if both
                   :set-event and :on-success are given, :on-success
                   wins; if neither, success is a no-op (fire-and-forget,
                   as with the `:user` sub)
     :on-401     — 1-arg fn receiving the query-v, called after the
                   401 has logged the user out; omit to route to login
     :on-500     — 1-arg fn receiving the query-v; omit for the
                   `handle-api-response` default (dispatches
                   `show-generic-error`)
     :context    — error log string; default `(str sub-key)`
          :default    — default value for unset `db-key`; default `[]`"
  [{:keys [sub-key route db-key set-event on-success on-401 on-500 context default]
    :or {default []}}]
  (reg-sub-raw sub-key
    (fn [app-db query-v]
      (when-let [token (event-utils/get-auth-token @app-db)]
        (go (dispatch [:set-loading true])
            (let [response (<! (http/get (event-utils/url-for-route route)
                                         {:headers {"Authorization" (str "Token " token)}}))]
              (dispatch [:set-loading false])
              ;; A logout or account switch while this request was in flight must not land:
              ;; the response is for whoever held `token`, not whoever is logged in now, so a
              ;; stale response would either send the new token nowhere useful or cache the old
              ;; account's data under the new one.
              (when (= token (event-utils/get-auth-token @app-db))
                (event-utils/handle-api-response response
                  (cond
                    on-success #(on-success response)
                    set-event  #(dispatch [set-event (:body response)])
                    :else      (fn []))
                  :on-401 #(rejected-token! app-db token on-401 query-v)
                  :on-500 (when on-500 #(on-500 query-v))
                  :context (or context (str sub-key)))))))
      (ra/make-reaction
       (fn [] (if (vector? db-key)
                (get-in @app-db db-key default)
                (get @app-db db-key default)))))))
