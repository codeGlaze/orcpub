(ns orcpub.dnd.e5.api-subs
  "`reg-api-sub`: one HOF for API-backed `reg-sub-raw` subscriptions that lazy-load from the
   backend on first subscribe (guard, loading counter, auth headers, response handling, reaction).
   Its guard is `event-utils/get-auth-token`, the canonical token path, so no sub can re-introduce
   the `:user` / `:user-data` typo that broke `::mi5e/remote-item`.
   GOTCHA: not in `subs.cljs`, which `equipment_subs.cljs` would then have to require."
  (:require [re-frame.core :refer [reg-sub-raw dispatch]]
            [reagent.ratom :as ra]
            [orcpub.dnd.e5.event-utils :as event-utils]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn reg-api-sub
  "Registers `:sub-key` as a `reg-sub-raw` that, when logged in, GETs `:route` (`url-for-route`)
   with auth headers, and returns a reaction on `:db-key` (keyword or `get-in` path; unset reads
   `:default`, default []). Success calls `:on-success` with the response, else dispatches
   `[:set-event body]`, else does nothing. `:on-401` / `:on-500` get the query-v; omitted, the
   `handle-api-response` defaults route to login / show a generic error. `:context` names the call
   in error logs, default `(str sub-key)`."
  [{:keys [sub-key route db-key set-event on-success on-401 on-500 context default]
    :or {default []}}]
  (reg-sub-raw sub-key
    (fn [app-db query-v]
      (when (event-utils/get-auth-token @app-db)
        (go (dispatch [:set-loading true])
            (let [response (<! (http/get (event-utils/url-for-route route)
                                         {:headers (event-utils/auth-headers @app-db)}))]
              (dispatch [:set-loading false])
              (event-utils/handle-api-response response
                (cond
                  on-success #(on-success response)
                  set-event  #(dispatch [set-event (:body response)])
                  :else      (fn []))
                :on-401 (when on-401 #(on-401 query-v))
                :on-500 (when on-500 #(on-500 query-v))
                :context (or context (str sub-key))))))
      (ra/make-reaction
       (fn [] (if (vector? db-key)
                (get-in @app-db db-key default)
                (get @app-db db-key default)))))))
