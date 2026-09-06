;; Generate scaled .orcbrew packs for perf work, by cloning a real pack's sources.
;;
;;   lein with-profile +test run -m clojure.main dev/scale_orcbrew_pack.clj
;;
;; Writes dev-scratch/paks/pak-cN.orcbrew for N in 1 2 4 8 16. Each clone gets a distinct
;; source name and distinct entry keys, so N clones are N times the content rather than N
;; overwrites of the same keys, and every pack is read back with clojure.edn and counted
;; before it is reported.
;;
;; TWO TRAPS, both hit for real:
;;   *print-length* is 50 under this project's lein profiles. Writing EDN with pr-str
;;   without binding it to nil emits "..." and produces a silently truncated, unreadable
;;   file — which then hangs a browser import for the full timeout. Hence the binding below.
;;   And a source map can hold non-content keys (:disabled?) alongside the
;;   :orcpub.dnd.e5/* content maps, so never assume every value is a map to count.

(require '[clojure.edn :as edn])

(def orig (edn/read-string (slurp "test/fixtures/test-pak.orcbrew")))

(defn content-key? [k] (and (keyword? k) (= "orcpub.dnd.e5" (namespace k))))
(defn content-source? [[_ v]] (and (map? v) (some content-key? (keys v))))
(defn sfx [i k] (if (and (keyword? k) (nil? (namespace k))) (keyword (str (name k) "-c" i)) k))

(defn clone [[src-name content] i]
  (let [new-name (str src-name " C" i)]
    [new-name
     (reduce-kv
      (fn [m ctype entries]
        (assoc m ctype
               (if-not (and (content-key? ctype) (map? entries))
                 entries
                 (reduce-kv (fn [e k v]
                              (assoc e (sfx i k)
                                     (cond-> v
                                       (:key v) (assoc :key (sfx i (:key v)))
                                       (:option-pack v) (assoc :option-pack new-name)
                                       (:source v) (assoc :source new-name))))
                            {} entries))))
      {} content)]))

(defn totals [pack]
  (reduce (fn [acc [_ content]]
            (if-not (map? content) acc
              (merge-with + acc (reduce-kv (fn [m t e]
                                             (if (and (content-key? t) (map? e))
                                               (assoc m (keyword (name t)) (count e)) m))
                                           {} content))))
          {} pack))

(doseq [mult [1 2 4 8 16]]
  (binding [*print-length* nil *print-level* nil *print-namespace-maps* false]
  (let [pack (into (into {} (remove content-source? orig))
                   (for [i (range mult) s (filter content-source? orig)] (clone s i)))
        p (str "dev-scratch/paks/pak-c" mult ".orcbrew")
        s (pr-str pack)]
    (spit p s)
    (let [back (edn/read-string (slurp p))]
      (println (format "%-32s %6.0f KB  sources=%2d  %s"
                       p (/ (count s) 1024.0) (count back) (pr-str (into (sorted-map) (totals back)))))))))
