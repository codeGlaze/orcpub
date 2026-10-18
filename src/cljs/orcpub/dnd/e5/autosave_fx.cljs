(ns ^{:doc "Effects and utils for handling throttled autosave"}
  orcpub.dnd.e5.autosave-fx
  (:require [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.content-reconciliation :as content-recon]
            [orcpub.dnd.e5.library :as library]
            [re-frame.core :refer [reg-fx dispatch subscribe]]
            [reagent.core :as r]))

;; timeout in ms during which we wait for further changes; if
;; none are received, the save will be performed.
(def throttled-save-timeout 7500)

(defonce throttled-save-timer (atom nil))
(defonce throttled-save-queue (atom #{}))
;; Ids whose save has been sent but not yet confirmed; closing the tab warns until it is empty.
(defonce unconfirmed-saves (atom #{}))

(defn confirm-close-window
  "While a save is pending, this function will be registered as an event listener
   on the window to try to help users not lose data."
  [e]
  (let [confirm-message "You have unsaved changes. Are you sure you want to exit?"]
    (when e
      (set! (.-returnValue e) confirm-message))
    confirm-message))

(defn dispatch-throttled-saves
  "Sends a save for every queued character id. Each stays in unconfirmed-saves, and closing the tab
   keeps warning, until ::saved reports it. Returns nil."
  []
  (reset! throttled-save-timer nil)
  (let [queued-ids @throttled-save-queue]
    (reset! throttled-save-queue #{})
    (swap! unconfirmed-saves into (map js/parseInt) queued-ids)
    (doseq [id queued-ids]
      (dispatch [::char5e/save-character id]))))

(defn resend-unconfirmed!
  "Sends again every save that has not been confirmed: one that failed while the browser was
   offline otherwise waits for another edit. Returns nil."
  []
  (doseq [id @unconfirmed-saves]
    (dispatch [::char5e/save-character id])))

;; Registered once, for its effect: the browser coming back online resends unconfirmed saves.
(defonce resend-when-online
  (.addEventListener js/window "online" #(resend-unconfirmed!)))

(reg-fx
  ::saved
  ;; A save of character `id` succeeded. The close warning goes once nothing is queued or unconfirmed;
  ;; a failed save never reports here, so it keeps warning.
  (fn [id]
    (swap! unconfirmed-saves disj (js/parseInt id))
    (when (and (empty? @unconfirmed-saves) (empty? @throttled-save-queue) (nil? @throttled-save-timer))
      (js/window.removeEventListener "beforeunload" confirm-close-window))))

;; The primary fx handler; simply return from a -fx event handler
;; as {::char5e/save-character-throttled <characterId>}
(declare ensure-template-cache!)

(reg-fx
  ::char5e/save-character-throttled
  (fn [id]
    ;; The save builds the character from the cached template; start caching it now.
    (ensure-template-cache!)
    (when-let [timer @throttled-save-timer]
      (js/clearTimeout timer))
    ;; Adding the same listener again does nothing, so this is safe on every edit.
    (js/window.addEventListener "beforeunload" confirm-close-window)

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
   the list now lets it heal, re-dispatches `:set-character`."
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
      ;; No route follows this heal, so it announces itself.
      heal? (assoc :dispatch-n [[:set-character character] [:orcpub.dnd.e5/announce-heal]]))))

;; Registered in events.cljs, inside the planned-picks hold.

(defn init-template-cache!
  "Start reactive watcher that mirrors ::char5e/template into app-db.
   Started once, by ensure-template-cache!.
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
  "Starts the template cache once: when a character is opened or a page showing one is
   reached, or when a save needs it. Never at app start.
   GOTCHA: built eagerly, bad homebrew stopped the app from starting. The save and the
   heal of renamed picks both read it. See homebrew-safety-net.md."
  []
  (when-not @template-cache
    (reset! template-cache (init-template-cache!))))

(reg-fx
  ::ensure-template-cache
  (fn [_] (ensure-template-cache!)))

