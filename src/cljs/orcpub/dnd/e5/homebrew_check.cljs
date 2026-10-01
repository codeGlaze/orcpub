(ns orcpub.dnd.e5.homebrew-check
  "Wires homebrew-guard into the app. deep-check converts and fully realizes every
   entry to catch a throw hidden in a lazy part of an option, one that per-entry
   builder guarding would miss.
   GOTCHA: realizing everything costs ~2s for a 12-source library vs ~40ms without
   realizing, so call it only for sources an import/restore changed, or after a
   page has already failed. See homebrew-safety-net.md."
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
   one at a time, with the conversions the subscriptions register. Returns a report for
   each entry that fails."
  [sources]
  (let [found (atom [])
        sub #(deref (subscribe %))
        in-scope? #(or (nil? sources)
                       (contains? sources (or (:plugin-source %) (:option-pack %))))]
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
                         :when (in-scope? entry)]
                   (guard/guard-option content-type entry f)))
               (catch :default e
                 (js/console.error "Could not check homebrew" (str content-type) e)))))))))
    @found))

(defn check-and-set-aside!
  "deep-check, then set aside whatever failed. Returns the reports."
  [sources]
  (let [found (deep-check sources)]
    (when (seq found)
      (dispatch [::e5/set-aside-broken-homebrew found]))
    found))

(reg-fx
 ::check-builds
 (fn [sources]
   (when (seq sources)
     ;; after the current event, so its own notice draws first
     (js/setTimeout #(check-and-set-aside! sources) 0))))
