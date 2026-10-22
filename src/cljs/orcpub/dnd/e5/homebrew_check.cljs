(ns orcpub.dnd.e5.homebrew-check
  "Wires homebrew-guard into the app. deep-check converts and fully realizes every
   entry to catch a throw hidden in a lazy part of an option, one that per-entry
   builder guarding would miss.
   GOTCHA: realizing costs ~20ms an entry (9s for test-pak's 464), so call it only for the
   entries a change wrote (the homebrew-shapes interceptor's ::check-entries), or after a page
   has already failed. See docs/HOMEBREW_GUARD.md."
  (:require [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.homebrew-guard :as guard]
            [re-frame.core :refer [reg-fx dispatch subscribe]]
            [reagent.core :as r]))

(defn- message [e]
  (when e (or (ex-message e) (str e))))

(guard/set-reporter!
 (fn [info]
   (dispatch [::e5/homebrew-entry-broke (update info :error message)])))

(defn deep-check
  "Convert and fully realize every homebrew entry from `sources` (every source when nil),
   one at a time, with the conversions the subscriptions register; with `entries`, a set of
   [source content-type key], only those (~20ms each, so a save checks what it saved). Returns
   a report for each entry that fails."
  [sources & [entries]]
  (let [found (atom [])
        sub #(deref (subscribe %))
        in-scope? (fn [content-type entry]
                    (let [src (or (:plugin-source entry) (:option-pack entry))]
                      (and (or (nil? sources) (contains? sources src))
                           (or (nil? entries) (contains? entries [src content-type (:key entry)])))))]
    (guard/call-with-reporter
     #(swap! found conj (update % :error message))
     (fn []
       ;; the subscriptions need a reactive context; this one is disposed straight away
       (r/dispose!
        (r/track!
         (fn []
           (doseq [[content-type {:keys [entries convert]}] (guard/conversions)]
             (try
               (let [f (convert sub)]
                 (doseq [entry (entries sub)
                         :when (in-scope? content-type entry)]
                   (guard/guard-option content-type entry f)))
               (catch :default e
                 (js/console.error "Could not check homebrew" (str content-type) e)))))))))
    @found))

(defn check-and-set-aside!
  "deep-check, then set aside whatever failed. Returns the reports."
  [sources & [entries]]
  (let [found (deep-check sources entries)]
    (when (seq found)
      (dispatch [::e5/set-aside-broken-homebrew found]))
    found))


(reg-fx
 ::report
 ;; an entry found broken outside a conversion, by the same path as one that failed in it
 (fn [info] (guard/report! info)))

(reg-fx
 ::check-entries
 ;; entries a change wrote, as [source content-type key]
 (fn [entries]
   (when (seq entries)
     ;; after the current event, so its own notice draws first
     (js/setTimeout #(check-and-set-aside! (set (map first entries)) entries) 0))))
