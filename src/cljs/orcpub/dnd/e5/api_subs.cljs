(ns orcpub.dnd.e5.api-subs
  "`reg-api-sub`: one HOF for API-backed `reg-sub-raw` subscriptions that lazy-load from the
   backend on first subscribe (guard, loading counter, auth headers, response handling, reaction).
   Its guard is `event-utils/get-auth-token`, the canonical token path, so no sub can re-introduce
   the `:user` / `:user-data` typo that broke `::mi5e/remote-item`.
   GOTCHA: not in `subs.cljs`, which `equipment_subs.cljs` would then have to require."
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
  "Registers `:sub-key` as a `reg-sub-raw` that, when logged in, GETs `:route` (`url-for-route`)
   with auth headers, and returns a reaction on `:db-key` (keyword or `get-in` path; unset reads
   `:default`, default []). Success calls `:on-success` with the response, else dispatches
   `[:set-event body]`, else does nothing. A 401 first logs out (`:clear-login`). `:on-401` /
   `:on-500` get the query-v; omitted, the `handle-api-response` defaults route to login / show a
   generic error. `:context` names the call in error logs, default `(str sub-key)`."
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
