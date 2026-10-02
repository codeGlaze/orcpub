(ns ^{:doc "Effects and utils for handling throttled autosave"}
  orcpub.dnd.e5.autosave-fx
  (:require [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.content-reconciliation :as content-recon]
            [orcpub.dnd.e5.library :as library]
            [re-frame.core :refer [reg-fx reg-event-db reg-event-fx dispatch subscribe]]
            [reagent.core :as r]))

;; timeout in ms during which we wait for further changes; if
;; none are received, the save will be performed.
(def throttled-save-timeout 7500)

(defonce throttled-save-timer (atom nil))
(defonce throttled-save-queue (atom #{}))

(defn confirm-close-window
  "While a save is pending, this function will be registered as an event listener
   on the window to try to help users not lose data."
  [e]
  (let [confirm-message "You have unsaved changes. Are you sure you want to exit?"]
    (when e
      (set! (.-returnValue e) confirm-message))
    confirm-message))

(defn dispatch-throttled-saves
  []
  (reset! throttled-save-timer nil)

  ; TODO should/can we wait until save is successful?
  (js/window.removeEventListener
    "beforeunload"
    confirm-close-window)

  (let [queued-ids @throttled-save-queue]
    (reset! throttled-save-queue #{})
    (doall
      (for [id queued-ids]
        (dispatch [::char5e/save-character id])))))

;; The primary fx handler; simply return from a -fx event handler
;; as {::char5e/save-character-throttled <characterId>}
(reg-fx
  ::char5e/save-character-throttled
  (fn [id]
    (if-let [timer @throttled-save-timer]
      ; existing timer; clear it
      (js/clearTimeout timer)
      ; no existing, so this is the first; confirm window closing
      (js/window.addEventListener
        "beforeunload"
        confirm-close-window))

    ; enqueue
    (swap! throttled-save-queue conj id)
    (reset! throttled-save-timer
            (js/setTimeout
              dispatch-throttled-saves
              throttled-save-timeout))))

;; -- Template cache --
;; Cache the global template in app-db so the save handler can compute
;; built-character without subscribing outside a reactive context.
;; track! creates a proper reactive context — no warnings.
(defn cache-template
  "Handler for `::cache-template`: stores `template`, what it offers (`offered-keys`,
   `offered-by-type`) and its `choice-tags`. On the first list, if the loaded character has a key
   the list now lets it heal, re-dispatches `:character-updated` (the same character, so its
   portrait draft stays)."
  [{:keys [db]} [_ template]]
  (let [offered (content-recon/offered-keys template)
        by-type (library/offered-by-type template)
        character (:character db)
        ;; The heal's indexes are empty until this list exists (heal-sites).
        heal? (and (nil? (::content-recon/offered-keys db))
                   character
                   (seq (:rewrote (content-recon/reconcile-former-keys
                                   character
                                   (content-recon/former-key-indexes (:plugins db) offered by-type)))))]
    (cond-> {:db (assoc db
                        ::cached-template template
                        ::content-recon/offered-keys offered
                        ::content-recon/offered-by-type by-type
                        ::content-recon/choice-tags (library/choice-tags template))}
      heal? (assoc :dispatch [:character-updated character]))))

(reg-event-fx ::cache-template cache-template)

(defn init-template-cache!
  "Start reactive watcher that mirrors ::char5e/template into app-db.
   Called from core.cljs after all subscriptions are registered.
   Guards the subscribe call itself — if the handler isn't registered yet,
   subscribe returns nil and we skip (no @nil crash). r/track! re-fires
   reactively when the subscription value changes."
  []
  (r/track!
    (fn []
      (when-let [sub (subscribe [::char5e/template])]
        (when-let [template @sub]
          (dispatch [::cache-template template]))))))

