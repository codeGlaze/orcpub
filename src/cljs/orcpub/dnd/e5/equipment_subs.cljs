(ns orcpub.dnd.e5.equipment-subs
  (:require [re-frame.core :refer [reg-sub reg-sub-raw dispatch #_subscribe]]
            [orcpub.common :as common]
            [orcpub.template :as t]
            [orcpub.dnd.e5.spell-subs]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.backgrounds :as bg5e]
            [orcpub.dnd.e5.languages :as langs5e]
            [orcpub.dnd.e5.feats :as feats5e]
            [orcpub.dnd.e5.races :as races5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.weapons :as weapon5e]
            [orcpub.dnd.e5.armor :as armor5e]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.equipment :as equipment5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.route-map :as routes]
            [orcpub.dnd.e5.event-utils :as event-utils :refer [url-for-route auth-headers
                                                                    handle-api-response]]
            [orcpub.dnd.e5.api-subs :as api-subs]
            [reagent.ratom :as ra]
            [clojure.string :as s]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(def sorted-items
  (delay (sort-by mi5e/name-key mi5e/magic-items))
  )

;; Browser vs test/CLJ compile-time split: the browser gets the real
;; HTTP-backed reg-sub-raw via reg-api-sub; the test/CLJ path gets a
;; plain reg-sub returning [] so the sub key is always registered but
;; doesn't try to hit the network during tests.
(if js/window.location
  (api-subs/reg-api-sub
   {:sub-key    ::mi5e/custom-items
    :route      routes/dnd-e5-items-route
    :db-key     ::mi5e/custom-items
    :set-event  ::mi5e/set-custom-items
    ;; Silent 401 is deliberate: the handle-api-response default is
    ;; :route-to-login, which would cause a login-loop here since this
    ;; sub fires on first subscribe and can race with
    ;; :verify-user-session. The console.warn is diagnostic-only — zero
    ;; behavior change, but gives future debuggers a greppable breadcrumb
    ;; when users report "items missing" (#669). Search the dev console
    ;; for "custom-items fetch rejected".
    :on-401     (fn [_]
                  (js/console.warn
                   "custom-items fetch rejected (401); session may be stale"))
    :context    "fetch custom items"})
  (reg-sub
   ::mi5e/custom-items
   (fn [_ _] [])))

(reg-sub
 ::mi5e/expanded-custom-items
 :<- [::mi5e/custom-items]
 (fn [custom-items _]
   (mi5e/expand-magic-items custom-items)))

(reg-sub
 ::mi5e/custom-item-map
 :<- [::mi5e/expanded-custom-items]
 (fn [custom-items _]
   (common/map-by-id custom-items)))

(reg-sub
 ::mi5e/custom-item
 :<- [::mi5e/custom-item-map]
 (fn [custom-item-map [_ id]]
   (get custom-item-map id)))

(reg-sub
 ::char5e/sorted-items
 :<- [::mi5e/expanded-custom-items]
 (fn [custom-items _]
   (concat
    custom-items
    @sorted-items)))

(reg-sub
 ::mi5e/custom-weapons
 :<- [::mi5e/expanded-custom-items]
 (fn [custom-items _]
   (sequence
    mi5e/magic-weapon-xform
    custom-items)))

(reg-sub
 ::mi5e/custom-and-standard-weapons
 :<- [::mi5e/custom-weapons]
 (fn [custom-weapons _]
   (concat
    (map
     (fn [{:keys [::mi5e/name] :as i}]
       (assoc i :name name))
     custom-weapons) weapon5e/weapons)))

(reg-sub
 ::mi5e/custom-and-standard-weapons-map
 :<- [::mi5e/custom-and-standard-weapons]
 (fn [custom-and-standard-weapons _]
   (common/map-by-key custom-and-standard-weapons)))

(reg-sub
 ::mi5e/magic-weapons
 :<- [::char5e/sorted-items]
 (fn [sorted-items _]
   (sequence
    mi5e/magic-weapon-xform
    sorted-items)))

(reg-sub
 ::mi5e/all-weapons
 :<- [::mi5e/magic-weapons]
 (fn [magic-weapons]
   (concat magic-weapons weapon5e/weapons)))

;; Unused — melee-only filter. UI filters inline. Restore if a melee-only view is added.
#_(reg-sub
   ::mi5e/all-melee-weapons
   :<- [::mi5e/all-weapons]
   (fn [all-weapons]
     (filter
      ::weapon5e/melee?
      all-weapons)))

(defn map-by-key-or-id [items]
  (reduce
   (fn [m {:keys [:db/id key] :as item}]
     (assoc m
            key item
            id item))
   {}
   items))

(reg-sub
 ::mi5e/magic-weapon-map
 :<- [::mi5e/magic-weapons]
 (fn [magic-weapons _]
   (map-by-key-or-id magic-weapons)))

(defn magic-item-options [modifier-fn nm]
  (fn [items _]
    (map
     (fn [{:keys [:db/id
                  ::mi5e/name
                  key
                  ::mi5e/description
                  ::mi5e/page
                  ::mi5e/modifiers
                  ::mi5e/source] :as item}]
       (let [item-key (or key (keyword (str "id-" id)))
             full-item (update item
                               ::mi5e/modifiers
                               mod5e/build-modifiers)]
         (t/option-cfg
          {:name (or (:name item) name)
           :key item-key
           :help (when (or description
                         page)
                   (t5e/inventory-help description page source))
           :modifiers [(modifier-fn
                        item-key
                        full-item)]})))
     items)))

(reg-sub
 ::mi5e/magic-weapon-options
 :<- [::mi5e/magic-weapons]
 (magic-item-options mod5e/deferred-magic-weapon "Magic Weapon"))

(reg-sub
 ::mi5e/magic-armor-options
 :<- [::mi5e/magic-armor]
 (magic-item-options mod5e/deferred-magic-armor "Magic Armor"))

(reg-sub
 ::mi5e/other-magic-item-options
 :<- [::mi5e/other-magic-items]
 (magic-item-options mod5e/deferred-magic-item "Magic Item"))

(reg-sub
 ::mi5e/magic-armor
 :<- [::char5e/sorted-items]
 (fn [sorted-items _]
   (sequence
    mi5e/magic-armor-xform
    sorted-items)))

(reg-sub
 ::mi5e/magic-armor-map
 :<- [::mi5e/magic-armor]
 (fn [magic-armor _]
   (map-by-key-or-id magic-armor)))

(reg-sub
 ::mi5e/other-magic-items
 :<- [::char5e/sorted-items] ;function relies on state of this sub
 (fn [sorted-items _]
   (sequence
    mi5e/other-magic-items-xform
    sorted-items)))

(reg-sub
 ::mi5e/all-armor-map
 :<- [::mi5e/magic-armor-map]
 (fn [magic-armor-map]
   (merge
    magic-armor-map
    armor5e/armor-map)))

(reg-sub
 ::mi5e/other-magic-items-map
 :<- [::mi5e/other-magic-items]
 (fn [magic-items _]
   (map-by-key-or-id magic-items)))

;; Standard (SRD) equipment lookup maps — used by character_builder's
;; inventory-selector for the non-magic equipment sections. These are
;; thin wrappers around static vars, kept as subscriptions because
;; inventory-selector receives the sub vector dynamically.
(reg-sub
 ::equipment5e/weapons-map
 (fn [_ _] weapon5e/weapons-map))

;; For homebrew-inclusive weapons, use ::mi5e/all-weapons-map instead.

(reg-sub
 ::mi5e/all-weapons-map
 :<- [::mi5e/magic-weapon-map]
 (fn [magic-weapons-map]
   (merge
    magic-weapons-map
    weapon5e/weapons-map)))

(reg-sub
 ::mi5e/all-magic-items-map
 :<- [::mi5e/magic-weapon-map]
 :<- [::mi5e/magic-armor-map]
 :<- [::mi5e/other-magic-items-map]
 (fn [maps _]
   (apply merge
          mi5e/all-magic-items-map
          maps)))

;; ============================================================================
;; ORPHANED: Cross-user item detail fetch chain — commented out, kept for
;; reference. Item sharing is on the roadmap but not yet prioritized.
;; ============================================================================
;;
;; PURPOSE: Groundwork for viewing magic items owned by OTHER users.
;;
;; The bulk `GET /api/dnd/e5/items` endpoint returns only items where
;; `::mi5e/owner = (:user identity)` — items the current user owns.
;; The server also exposes `GET /api/dnd/e5/items/:id` (routes.clj
;; `get-item`) which returns ANY item by db-id regardless of whose
;; owner it has. This chain was intended as the client-side consumer
;; for that by-id endpoint, so visiting `/items/<id>` for someone
;; else's shared item would fetch it on demand.
;;
;; The live `views/item-page` (views.cljs:3874) bypasses this chain —
;; it subscribes to `::mi/custom-item item-key` directly, which reads
;; from the bulk-fetched `::mi/custom-items` list. As a result,
;; visiting `/items/<id>` for an item you don't own silently falls
;; back to "not found."
;;
;; CHAIN (when restored):
;;
;;   views/item-page calls (subscribe [::mi5e/item item-key])
;;     |
;;     |--- ::mi5e/item dispatcher (below)
;;           |
;;           |-- int key: (subscribe [::mi5e/remote-item id])
;;           |       |
;;           |       |-- fires GET /api/dnd/e5/items/:id with auth headers
;;           |       |-- dispatches [::mi5e/add-remote-item (:body response)]
;;           |             (handler lives in events.cljs — also commented)
;;           |       |-- stores under db[::mi5e/remote-items][id]
;;           |       +-- reaction reads that path
;;           |
;;           +-- kw key: (get mi5e/all-equipment-map key) via make-reaction
;;
;; COMMENTED OUT because:
;;
;; - Half-alive: registered in the signal graph but zero live subscribers.
;;   Only in-tree reference is inside the #_ dispatcher below. Was
;;   confusing to auditors who kept re-discovering it.
;;
;; - Broken guard since 45ef969 (Aug 2025): the `(and (:user @app-db)
;;   (:token (:user @app-db)))` check reads db[:user] which has NEVER
;;   contained a :token. db[:user] is only written by :set-user via
;;   :follow-user / :unfollow-user with {:following [...]} shapes.
;;   The canonical token path is [:user-data :token] — see
;;   event_utils/get-auth-token. Guard has been false 100% of the time
;;   since it was introduced, so the fetch never fired even if a live
;;   caller had existed. Fixed in the commented form below so the
;;   next restorer doesn't hit the same trap.
;;
;; TO RESTORE when item sharing is implemented:
;;
;; 1. Uncomment the four forms below AND ::mi/add-remote-item in
;;    events.cljs (search for "ORPHANED: see equipment_subs").
;;
;; 2. Update views/item-page (views.cljs:3874) to subscribe to
;;    [::mi5e/item key] instead of [::mi/custom-item item-key], so
;;    numeric id keys route through the remote fetch while keyword
;;    keys continue to resolve against the local map.
;;
;; 3. Consider whether the remote fetch should be an explicit event
;;    on route-mount rather than a reg-sub-raw side effect — the
;;    modern pattern per docs/kb/reframe-subscription-patterns.md on
;;    agents/develop. The current shape works but is not the style
;;    the rest of the code has moved toward.
;;
;; 4. Product decisions needed before wiring the UI:
;;    - Can viewers edit items they don't own?
;;    - Can they favorite/clone/share them?
;;    - Decide before exposing the detail page.
;;
;; 5. Add a KB entry to docs/kb/ on agents/develop documenting the
;;    cross-user item fetch chain once it's wired and working.
;; ============================================================================

#_(reg-sub
    ::mi5e/remote-items
    (fn [db _]
      (::mi5e/remote-items db)))

#_(reg-sub-raw
    ::mi5e/remote-item
    (fn [app-db [_ id]]
      ;; Guard uses event-utils/get-auth-token (canonical path). Original
      ;; was `(and (:user @app-db) (:token (:user @app-db)))` which was
      ;; wrong — db[:user] has never contained :token. Fixed here so the
      ;; next restorer doesn't re-hit 45ef969's typo.
      (when (event-utils/get-auth-token @app-db)
        (go (dispatch [:set-loading true])
            (let [response (<! (http/get (url-for-route
                                           routes/dnd-e5-item-route
                                           :id id)
                                         {:headers (auth-headers @app-db)}))]
              (dispatch [:set-loading false])
              (handle-api-response response
                #(dispatch [::mi5e/add-remote-item (:body response)])
                :context "fetch item"))))
      (ra/make-reaction
       (fn [] (get-in @app-db [::mi5e/remote-items id] {})))))

#_(reg-sub-raw
    ::mi5e/item
    (fn [_app-db [_ key]]
      (if (int? key)
        (subscribe [::mi5e/remote-item key])
        (ra/make-reaction (fn [] (get mi5e/all-equipment-map key))))))

(reg-sub
 ::equipment5e/armor-map
 (fn [_ _] armor5e/armor-map))

(reg-sub
 ::equipment5e/equipment-map
 (fn [_ _] equipment5e/equipment-map))

(reg-sub
 ::equipment5e/treasure-map
 (fn [_ _] equipment5e/treasure-map))

;; For homebrew-inclusive armor, use ::mi5e/all-armor-map instead.

(reg-sub
 ::char5e/template-selections
 :<- [::mi5e/magic-weapon-options]
 :<- [::mi5e/magic-armor-options]
 :<- [::mi5e/other-magic-item-options]
 :<- [::mi5e/all-weapons-map]
 :<- [::mi5e/custom-and-standard-weapons]
 :<- [::spells5e/spell-lists]
 :<- [::spells5e/spells-map]
 :<- [::bg5e/backgrounds]
 :<- [::races5e/races]
 :<- [::classes5e/classes]
 :<- [::feats5e/feats]
 :<- [::langs5e/language-map]
 (fn [[magic-weapon-options
       magic-armor-options
       other-magic-item-options
       weapons-map
       custom-and-standard-weapons
       spell-lists
       spells-map
       backgrounds
       races
       classes
       feats
       language-map] _]
   (t5e/template-selections magic-weapon-options
                            magic-armor-options
                            other-magic-item-options
                            weapons-map
                            custom-and-standard-weapons
                            spell-lists
                            spells-map
                            backgrounds
                            races
                            classes
                            feats
                            language-map)))

(reg-sub
 ::char5e/template
 :<- [::char5e/template-selections]
 (fn [template-selections _]
   (t5e/template template-selections)))
