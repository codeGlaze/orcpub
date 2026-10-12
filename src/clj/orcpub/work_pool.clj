(ns orcpub.work-pool
  "A bound on how much of one kind of heavy work runs at once, separate from the HTTP pool: fair
   permits, a count of who is waiting, and a running mean of how long the work takes, which is
   what a turned-away caller's Retry-After is worked out from. PDF export, the image probe and
   server portrait renders each have one; docs/PORTRAIT-SERVING.md.")

(defn pool
  "A pool sized by `(size)` permits, waiting up to `(timeout-ms)` for one. Both are functions so
   they read config when first used. `seed-ms` is a plausible duration until real ones arrive."
  [{:keys [size timeout-ms seed-ms]}]
  {:size size
   :timeout-ms timeout-ms
   :permits (delay (java.util.concurrent.Semaphore. (int (size)) true))
   :waiting (java.util.concurrent.atomic.AtomicInteger. 0)
   :mean-ms (java.util.concurrent.atomic.AtomicLong. (long seed-ms))})

(defn permits
  "The pool's semaphore."
  ^java.util.concurrent.Semaphore [p]
  @(:permits p))

(defn queue-depth
  "How many callers are waiting for a permit right now."
  [p]
  (.get ^java.util.concurrent.atomic.AtomicInteger (:waiting p)))

(defn- record! [p ms]
  (let [^java.util.concurrent.atomic.AtomicLong m (:mean-ms p)]
    (.set m (long (+ (* 0.8 (.get m)) (* 0.2 ms))))))

(defn retry-after-seconds
  "Whole seconds for a turned-away caller to wait: the queue ahead of it over how fast the permits
   drain it. Never zero (Retry-After takes no fraction, and zero sends the caller straight back)
   and at most 30."
  [p waiting]
  (-> (/ (* waiting (.get ^java.util.concurrent.atomic.AtomicLong (:mean-ms p)))
         (* 1000.0 ((:size p))))
      Math/ceil int (max 1) (min 30)))

(defn run
  "Run `(f)` holding a permit: {:result x}, or {:busy retry-seconds :waiting n} when none frees
   within the timeout. The permit is released however `f` ends."
  [p f]
  (let [^java.util.concurrent.atomic.AtomicInteger w (:waiting p)
        waiting (.incrementAndGet w)]
    (try
      (if (.tryAcquire (permits p) (long ((:timeout-ms p))) java.util.concurrent.TimeUnit/MILLISECONDS)
        (let [start (System/nanoTime)]
          (try {:result (f)}
               (finally
                 (record! p (/ (- (System/nanoTime) start) 1e6))
                 (.release (permits p)))))
        {:busy (retry-after-seconds p waiting) :waiting waiting})
      (finally (.decrementAndGet w)))))
