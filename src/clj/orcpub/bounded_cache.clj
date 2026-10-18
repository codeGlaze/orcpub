(ns orcpub.bounded-cache
  "An in-memory cache bounded by entry count and, optionally, age. The least recently used entry
   goes first. `fetch!` is single-flight: concurrent misses for one key share one computation, so a
   burst of identical requests does the work once. Used for the server's image work (rendered
   portraits, probed pictures, strand fields); docs/PORTRAIT-SERVING.md.")

(defn cache
  "A cache of at most `max-entries`, each kept for at most `max-age-ms` (nil: no age limit)."
  [{:keys [max-entries max-age-ms]}]
  {:max-age-ms max-age-ms
   :m (proxy [java.util.LinkedHashMap] [16 0.75 true]
        (removeEldestEntry [_] (> (.size ^java.util.Map this) max-entries)))})

(defn- now [] (System/currentTimeMillis))

(defn- live-entry
  "The entry under `k` if it has not aged out; an aged one is dropped. Call holding the lock."
  [{:keys [^java.util.Map m max-age-ms]} k]
  (when-let [e (.get m k)]
    (if (and max-age-ms (> (- (now) (:at e)) max-age-ms))
      (do (.remove m k) nil)
      e)))

(defn lookup
  "[value] for `k` when present and finished, else nil. A cached nil is [nil]."
  [{:keys [m] :as c} k]
  (when-let [e (locking m (live-entry c k))]
    (when (realized? (:v e)) [(deref (:v e))])))

(defn put!
  "Store `v` under `k`."
  [{:keys [^java.util.Map m]} k v]
  (locking m (.put m k {:at (now) :v (doto (promise) (deliver v))}))
  v)

(defn fetch!
  "The value under `k`, computing it with `(f)` on a miss. Concurrent callers wait for the one
   computation. If `f` throws, nothing is kept and every waiting caller sees the exception."
  [{:keys [^java.util.Map m] :as c} k f]
  (let [[d mine?] (locking m
                    (if-let [e (live-entry c k)]
                      [(:v e) false]
                      (let [d (delay (f))]
                        (.put m k {:at (now) :v d})
                        [d true])))]
    (try @d
         (catch Throwable t
           (when mine? (locking m (when (identical? d (:v (.get m k))) (.remove m k))))
           (throw t)))))

(defn size [{:keys [^java.util.Map m]}] (locking m (.size m)))

(defn clear! [{:keys [^java.util.Map m]}] (locking m (.clear m)))
