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
(reg-event-db
 ::cache-template
 (fn [db [_ template]]
   (assoc db ::cached-template template)))

;; Deferred init: subscribe inside r/track! (reactive context — no warning).
;; core.cljs requires equipment-subs before events (which transitively loads
;; this file), so ::char5e/template is registered by the time setTimeout 0
;; fires. Even if load order shifted, when-let + r/track! is self-healing:
;; nil on first tick, re-fires reactively once the sub becomes available.
(defonce _init-template-cache
  (js/setTimeout
    (fn []
      (r/track!
        (fn []
          (when-let [template @(subscribe [::char5e/template])]
            (dispatch [::cache-template template])))))
    0))

