(ns orcpub.dnd.e5.homebrew-check
  "Wires homebrew-guard into the app. deep-check converts and fully realizes every
   entry to catch a throw hidden in a lazy part of an option, one that per-entry
   builder guarding would miss.
   GOTCHA: realizing costs ~20ms an entry (9s for test-pak's 464), so call it only for the
   entries a change wrote (the homebrew-shapes interceptor's ::check-entries), or after a page
   has already failed. See docs/HOMEBREW_GUARD.md."
  (:require [clojure.string :as s]
            [orcpub.route-map :as routes]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.db :refer [keep-pre-fix-copy!]]
            [orcpub.dnd.e5.orcbrew-validation :as orcbrew-val]
            [orcpub.dnd.e5.homebrew-guard :as guard]
            [orcpub.dnd.e5.homebrew-shapes :as shapes]
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

(defn unreadable-reports
  "A report for each entry `findings` could not read, carrying the entry as `library` held it."
  [library findings]
  (for [[[src ct k] fs] (group-by (juxt :source :content-type :key)
                                  (filter #(= :unreadable (:action %)) findings))
        :when k]
    {:content-type ct :key k :source src :original (get-in library [src ct k])
     :error (str (s/join ", " (map #(pr-str (:path %)) fs)) " could not be read")}))

;; Per db slot (:plugins, :shared-plugins): the last library read through `checked`, and what the
;; app was given for it. Each event and each view read compares against it, so a library already
;; checked costs one comparison, and a new one is walked only where it differs from the last.
(defonce ^:private last-read (atom {}))

(defn checked
  "`library`, from the db `slot`, as the app may read it. Every write goes through the
   homebrew-shapes interceptor, so this finds nothing unless something wrote app-db another way (a
   script, the console, a bug of ours): then the fixes are read, an entry that cannot be read is
   left out and reported, and the whole is logged as a bug. Writes nothing."
  [slot library]
  (let [{:keys [raw given]} (get @last-read slot)]
    (cond
      (identical? library raw) given
      (or (identical? library given) (not (map? library))) library
      :else
      (let [{cleaned :library findings :findings} (shapes/clean-library library given)
            {given' :library} (shapes/split-unreadable cleaned library findings)]
        (when (seq findings)
          (js/console.error "Homebrew reached the app without the shape check (a bug):"
                            (pr-str slot (map #(select-keys % [:source :content-type :key :path :action])
                                              findings)))
          (doseq [r (unreadable-reports library findings)]
            (guard/report! r)))
        (swap! last-read assoc slot {:raw library :given given'})
        given'))))

;; ============================================================================
;; Setting aside, and the shape check on every write (registered in events.cljs, which holds
;; the library gate these events go through)
;; ============================================================================

(defn- find-broken-entry
  "Where a reported entry sits in the library, as [source entry]: the source the report
   names, then the entry's own :option-pack, then any source holding that key."
  [plugins {:keys [content-type key source option-pack]}]
  (let [holds? (fn [src] (map? (get-in plugins [src content-type key])))
        src (cond
              (and source (holds? source)) source
              (and option-pack (holds? option-pack)) option-pack
              :else (some #(when (holds? %) %) (keys plugins)))]
    (when src
      [src (get-in plugins [src content-type key])])))

(defn set-aside-broken-entries
  "Move each reported entry from `plugins` into `rejected`. Returns {:plugins :rejected
   :moved [{:source :content-type :key :entry}]}. A report whose entry is not in the
   library (shared content, or already gone) moves nothing. A report's `:original` is what is
   set aside when it has one: the entry as it was written, before anything was read past."
  [plugins rejected reports]
  (reduce (fn [acc {:keys [content-type key original] :as report}]
            (if-let [[src entry] (find-broken-entry (:plugins acc) report)]
              (-> acc
                  (update-in [:plugins src content-type] dissoc key)
                  (assoc-in [:rejected src content-type key] (or original entry))
                  (update :moved conj {:source src :content-type content-type :key key :entry entry}))
              acc))
          {:plugins plugins :rejected (or rejected {}) :moved []}
          reports))

(defn broken-homebrew-notice
  "The warning notice for `moved` (set-aside-broken-entries' :moved): names up to three, links to
   My Content."
  [moved]
  (let [n (count moved)
        label (fn [{:keys [source content-type key entry]}]
                (str "“" (if (and (string? (:name entry)) (not (s/blank? (:name entry))))
                                (:name entry)
                                (str key))
                     "” (" (s/lower-case (get orcbrew-val/content-type-singular content-type "entry"))
                     ") in “" source "”"))]
    {:title (if (= 1 n) "A homebrew entry was set aside" (str n " homebrew entries were set aside"))
     :details [(str (s/join ", " (map label (take 3 moved)))
                    (when (> n 3) (str " and " (- n 3) " more"))
                    ;; true whether a field could not be read or the entry broke the builder
                    " couldn't be used, so "
                    (if (= 1 n) "it was" "they were")
                    " set aside. Everything else still works.")
               [:span.pointer.underline.orange
                {:on-click (fn [e]
                             (.stopPropagation e)
                             (dispatch [:route routes/dnd-e5-my-content-route]))}
                "Fix or discard it in My Content"]]}))

(reg-fx
 ::keep-pre-fix-copy
 (fn [plugins] (keep-pre-fix-copy! plugins)))

(defn homebrew-shapes-checked
  "An event's effects, with the homebrew it changed made to fit homebrew-shapes. The library: what
   was fixed is in the new state and stored through the gate (after a copy of the library as it
   was); an entry with a field that cannot be read is set aside, as written; the entries it
   changed are converted by the deep check. Shared content is never stored: what is fixed is used,
   and an entry that cannot be read is left out and reported.
   Nothing to do when the event left both as they were, which is nearly every event."
  [before-db effects]
  (let [db (:db effects)
        before (:plugins before-db)
        lib (:plugins db)
        shared-before (:shared-plugins before-db)
        shared (:shared-plugins db)]
    (if (or (nil? db) (and (identical? lib before) (identical? shared shared-before)))
      effects
      (let [{cleaned :library findings :findings} (if (map? lib)
                                                    (shapes/clean-library lib before)
                                                    {:library lib})
            {shared' :library shared-findings :findings}
            (if (map? shared) (shapes/clean-library shared shared-before) {:library shared})
            shared' (:library (shapes/split-unreadable shared' shared shared-findings))
            changed (when (and (map? cleaned) (map? before))
                      (set (for [[src source] cleaned
                                 :when (and (map? source) (not (identical? source (get before src))))
                                 [ct section] source
                                 :when (and (map? section)
                                            (not (identical? section (get-in before [src ct]))))
                                 [k entry] section
                                 :when (not (identical? entry (get-in before [src ct k])))]
                             [src ct k])))
            fx (concat
                (when-not (identical? cleaned lib)
                  [[::keep-pre-fix-copy lib]
                   [:dispatch [::e5/set-plugins cleaned]]])
                (for [r (concat (unreadable-reports lib findings)
                                (unreadable-reports shared shared-findings))]
                  [::report r])
                (when (seq changed)
                  [[::check-entries changed]]))]
        (cond-> (assoc effects :db
                       (cond-> db
                         (not (identical? cleaned lib)) (assoc :plugins cleaned) ;; library gate
                         (not (identical? shared' shared)) (assoc :shared-plugins shared')))
          (seq fx) (update :fx (fnil into []) fx))))))
