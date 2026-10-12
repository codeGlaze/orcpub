(ns orcpub.dnd.e5.homebrew-guard
  "Per-entry isolation for turning homebrew into character options. An entry that
   throws while it is converted is skipped and reported, so one bad entry cannot stop
   the character options from loading. The app registers the reporter, which sets the
   entry aside and says so (orcpub.dnd.e5.homebrew-check); without one, a failure is
   only logged.")

(defonce ^:private reporter (atom nil))

(defn set-reporter! [f]
  (reset! reporter f))

(defn call-with-reporter
  "Run (body) with `f` as the reporter, then put the previous one back."
  [f body]
  (let [previous @reporter]
    (reset! reporter f)
    (try
      (body)
      (finally
        (reset! reporter previous)))))

;; Content type -> {:entries (fn [sub] entries) :convert (fn [sub] (fn [entry] option))}.
;; Registered by the subscriptions that own those conversions, so the deep check can run
;; them without requiring the subscription namespaces.
(defonce ^:private registered (atom {}))

(defn register-conversions! [m]
  (swap! registered merge m))

(defn conversions []
  @registered)

(defn report!
  "Report a homebrew entry that failed to convert:
   {:content-type :key :name :option-pack :source :error}."
  [info]
  #?(:cljs (js/console.warn "Skipped a homebrew entry that failed to load:"
                            (pr-str (dissoc info :error)) (:error info)))
  (when-let [f @reporter]
    (f info)))

(defn realize
  "Walk `x` so every lazy sequence inside it runs now. Option builders return lazy
   sequences, and a throw inside one would otherwise surface later, outside the guard."
  [x]
  (dorun (tree-seq coll? seq x))
  x)

(defn guard-entry
  "(f entry), or nil when it throws; the failure is reported with the entry's identity."
  [content-type entry f]
  (try
    (f entry)
    (catch #?(:clj Exception :cljs :default) e
      (report! {:content-type content-type
                :key (:key entry)
                :name (:name entry)
                :option-pack (:option-pack entry)
                :source (:plugin-source entry)
                :error e})
      nil)))

(defn guard-option
  "guard-entry, with the result realized inside the guard."
  [content-type entry f]
  (guard-entry content-type entry (comp realize f)))
