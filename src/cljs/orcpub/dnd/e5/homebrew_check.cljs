(ns orcpub.dnd.e5.homebrew-check
  "Wires homebrew-guard into the app, and checks homebrew deeply when it is worth the cost.

   While the builder works, each entry is guarded where it is converted, so an entry that
   throws there is skipped and reported, and ::e5/homebrew-entry-broke sets it aside and
   says so. A throw can also hide in a lazy part of an option and only surface when a page
   draws it. deep-check finds those by converting and fully realizing every entry, one at
   a time. Measured with a 12-source library that is about two seconds, against about
   40 ms for converting without realizing, so it runs only for the sources an import or a
   restore changed, and when a page has already failed."
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
