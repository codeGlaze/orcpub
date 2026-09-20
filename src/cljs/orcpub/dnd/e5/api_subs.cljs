(ns orcpub.dnd.e5.api-subs
  "Shared HOF for API-backed `reg-sub-raw` subscriptions that lazy-load
   data from the backend on first subscribe.

   Eliminates ~10 lines of boilerplate per sub (guard, loading counter,
   http/get with auth headers, handle-api-response wrapping, reaction
   construction) that was previously duplicated across 5 sites —
   `::mi5e/custom-items`, `::char5e/characters`, `::party5e/parties`,
   `::folder5e/folders`, `:user`.

   The guard uses `event-utils/get-auth-token` as its canonical check,
   so every API sub registered via `reg-api-sub` gets the correct
   token-path check for free and cannot re-introduce the
   `:user` / `:user-data` typo class that broke `::mi5e/remote-item`.

   This file lives in its own namespace (rather than event_utils.cljc
   or subs.cljs) because the HOF needs cljs-only imports that don't
   belong in a cross-platform `.cljc` file, and putting it in
   `subs.cljs` would require a new import edge from `equipment_subs.cljs`
   → `subs.cljs` to reuse it for `::mi5e/custom-items`. A small
   dedicated namespace is the cleanest break."
  (:require [re-frame.core :refer [reg-sub-raw dispatch]]
            [reagent.ratom :as ra]
            [orcpub.dnd.e5.event-utils :as event-utils]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

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
     :on-401     — 1-arg fn receiving the query-v; omit for the
                   `handle-api-response` default (dispatches
                   `:route-to-login`)
     :on-500     — 1-arg fn receiving the query-v; omit for the
                   `handle-api-response` default (dispatches
                   `show-generic-error`)
     :context    — error log string; default `(str sub-key)`
     :default    — default value for unset `db-key`; default `[]`

   The guard, loading-counter management, header injection, response
   dispatch shape, and reaction construction are owned by this HOF.
   Call sites express only what varies between subs."
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
