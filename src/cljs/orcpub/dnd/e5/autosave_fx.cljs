(ns ^{:doc "Effects and utils for handling throttled autosave"}
  orcpub.dnd.e5.autosave-fx
  (:require [orcpub.dnd.e5.character :as char5e]
            [re-frame.core :refer [reg-fx reg-event-db dispatch subscribe]]
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
(declare ensure-template-cache!)

(reg-fx
  ::char5e/save-character-throttled
  (fn [id]
    ;; The save builds the character from the cached template; start caching it now.
    (ensure-template-cache!)
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
(reg-event-db
 ::cache-template
 (fn [db [_ template]]
   (assoc db ::cached-template template)))

(defn init-template-cache!
  "Start reactive watcher that mirrors ::char5e/template into app-db.
   Started once, by ensure-template-cache!, when a character save first needs it.
   Guards the subscribe call itself — if the handler isn't registered yet,
   subscribe returns nil and we skip (no @nil crash). r/track! re-fires
   reactively when the subscription value changes."
  []
  (r/track!
    (fn []
      ;; Building the template can throw on bad homebrew. Caught, the cache stays empty,
      ;; which the save handler already allows for; uncaught, it stopped startup.
      (try
        (when-let [sub (subscribe [::char5e/template])]
          (when-let [template @sub]
            (dispatch [::cache-template template])))
        (catch :default e
          (js/console.error "Could not build the character template for the save cache:" e))))))

(defonce ^:private template-cache (atom nil))

(defn ensure-template-cache!
  "Start the template cache the first time a character save needs it. It used to start
   with the app, building the whole character template on every page before anything
   was drawn and outside every error boundary, so bad homebrew there stopped the app
   from starting. Only saving a character reads the cache."
  []
  (when-not @template-cache
    (reset! template-cache (init-template-cache!))))

(reg-fx
  ::ensure-template-cache
  (fn [_] (ensure-template-cache!)))

