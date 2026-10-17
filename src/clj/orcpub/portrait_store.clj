(ns orcpub.portrait-store
  "The server's drawings of character portraits for shared links, from the saved selection and
   registered art only: kept as files by character id and fingerprint in PORTRAIT_STORE_DIR, behind
   a bounded memory cache, every draw through the portrait work pool. docs/PORTRAIT-SERVING.md.
   GOTCHA: files, not Datomic, which keeps every past value forever and is not for large values."
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [orcpub.bounded-cache :as bounded-cache]
            [orcpub.config :as config]
            [orcpub.work-pool :as work-pool]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.portrait-render :as portrait-render])
  (:import [java.security MessageDigest]
           [java.nio.file Files StandardCopyOption]
           [java.util.concurrent Executors ScheduledExecutorService TimeUnit]))

;; ---------------------------------------------------------------------------
;; Fingerprint
;; ---------------------------------------------------------------------------

(defn- hex
  "`b` as lower-case hex."
  [^bytes b] (apply str (map #(format "%02x" %) b)))

(defn- digest-of
  "SHA-256 over `chunks` (strings or byte arrays), as hex."
  [chunks]
  (let [md (MessageDigest/getInstance "SHA-256")]
    (doseq [c chunks]
      (.update md (if (string? c) (.getBytes ^String c "UTF-8") ^bytes c))
      (.update md (byte-array [0])))
    (hex (.digest md))))

(def drawing-sources
  "What a portrait's pixels depend on besides the art files: the drawing code and the pack's data.
   A change to any of it draws every portrait again."
  ["orcpub/portrait_render.clj" "orcpub/dnd/e5/portrait_effects.cljc"
   "orcpub/dnd/e5/portrait_face.cljc" "orcpub/dnd/e5/portrait_colorize.cljc"
   "orcpub/dnd/e5/portrait_layout.cljc" "orcpub/dnd/e5/portrait_light.cljc"
   "orcpub/dnd/e5/portrait_random.cljc" "orcpub/dnd/e5/portrait_assets.cljc"
   "portrait-pack/loom.edn" "portrait-pack/pieces.edn"])

(defn- code-source?
  "Whether `path` is a source file, which a production jar leaves out (:omit-source)."
  [path]
  (boolean (re-find #"\.cljc?$" path)))

(defn- resource-bytes
  "The bytes of classpath resource `path`, or nil."
  [path]
  (when-let [r (io/resource path)]
    (with-open [in (io/input-stream r)] (.readAllBytes in))))

(defmacro ^:private compiled-code-digest
  "A digest of the drawing code, taken when this namespace is compiled, while the sources are still
   on the classpath; a production jar has the compiled value."
  []
  (digest-of (mapcat (fn [p] [p (or (resource-bytes p) "missing")]) (filter code-source? drawing-sources))))

(defn- art-digest*
  "A digest of everything a drawing depends on outside the database: the registered art files and
   the drawing code. Stable across restarts, so a stored file stays good until either changes."
  []
  (digest-of (concat [(compiled-code-digest)]
                     (mapcat (fn [p] [p (or (resource-bytes p) "missing")]) (remove code-source? drawing-sources))
                     (for [{:artist/keys [layers]} pa/registry
                           [_ assets] (sort-by key layers)
                           {:asset/keys [url]} assets
                           x [url (or (resource-bytes (str "public" url)) "missing")]]
                       x))))

(def ^:private art-digest (delay (art-digest*)))

(defn tag
  "The validator for a stored portrait EDN, quoted as an ETag: its pixels depend on the selection,
   the credit line, the site mark and the art digest. nil when there is no stored portrait."
  [stored]
  (when stored
    (let [portrait (char5e/parse-portrait stored)]
      (str "\"" (digest-of [@art-digest (str stored)
                            (str (some-> portrait pa/credit-line))
                            (str (portrait-render/site-mark))])
           "\""))))

(defn version
  "The short form of a validator, for the image address (?v=) and the file name."
  [fp]
  (subs fp 1 17))

;; ---------------------------------------------------------------------------
;; Files
;; ---------------------------------------------------------------------------

(defn- store-dir
  "The directory drawings are kept in (PORTRAIT_STORE_DIR)."
  ^java.io.File [] (io/file (config/get-portrait-store-dir)))

(defn file-for
  "Where character `id`'s drawing under validator `tag` is kept."
  ^java.io.File [id fp]
  (io/file (store-dir) (str id "-" (version fp) ".png")))

(defn- read-stored
  "The kept drawing for character `id` under validator `fp`, or nil."
  [id fp]
  (let [f (file-for id fp)]
    (when (.isFile f) (Files/readAllBytes (.toPath f)))))

(defn- remove-others!
  "Delete character `id`'s files other than `kept` (nil: all of them)."
  [id ^java.io.File kept]
  (doseq [^java.io.File f (.listFiles (store-dir))
          :when (and (s/starts-with? (.getName f) (str id "-")) (not= f kept))]
    (.delete f)))

(defn- write-stored!
  "Keep `png` as character `id`'s drawing under validator `fp`, replacing its older ones. Written
   to a temporary file and moved into place, so a reader never sees half a file."
  [id fp ^bytes png]
  (let [f (file-for id fp)
        _ (io/make-parents f)
        ;; Unique per write: containers sharing the store may draw the same portrait at once.
        tmp (.toFile (Files/createTempFile (.toPath (store-dir)) (str "." (.getName f) ".") ".tmp"
                                           (make-array java.nio.file.attribute.FileAttribute 0)))]
    (try
      (io/copy png tmp)
      (Files/move (.toPath tmp) (.toPath f) (into-array [StandardCopyOption/REPLACE_EXISTING
                                                         StandardCopyOption/ATOMIC_MOVE]))
      (finally (Files/deleteIfExists (.toPath tmp)))) ; a failed write leaves nothing behind
    (remove-others! id f)))

;; ---------------------------------------------------------------------------
;; Drawing
;; ---------------------------------------------------------------------------

(def ^:private pool
  "Portraits drawn at once, sized by ORCPUB_PORTRAIT_CONCURRENCY. Each draw is about half a second
   of one core; seeded at that."
  (work-pool/pool {:size #(config/get-portrait-concurrency)
                   :timeout-ms #(config/get-portrait-queue-timeout-ms)
                   :seed-ms 500}))

(def ^:private drawn
  "The last 64 drawings by character and validator, nil for one with nothing drawable.
   Single-flight, so the crawlers a newly shared link sends all at once share one draw."
  (bounded-cache/cache {:max-entries 64}))

(defn png
  "[:ok png-or-nil] for character `id`'s portrait (stored EDN `stored`, validator `fp`): from
   memory, else its file, else drawn and kept. [:busy retry-seconds] when no draw slot frees up."
  [id fp stored]
  (try
    [:ok (bounded-cache/fetch!
          drawn [id fp]
          #(or (read-stored id fp)
               (let [{:keys [result busy]}
                     (work-pool/run pool (fn [] (portrait-render/render-png (char5e/parse-portrait stored))))]
                 (cond busy (throw (ex-info "portrait draw busy" {::busy busy}))
                       result (do (try (write-stored! id fp result)
                                       (catch Exception e
                                         ;; served all the same; it is drawn again next time
                                         (println "portrait-store: could not keep" id "in"
                                                  (str (store-dir)) "-" (.getMessage e))))
                                     result)
                       :else nil))))]
    (catch clojure.lang.ExceptionInfo e
      (if-let [retry (::busy (ex-data e))] [:busy retry] (throw e)))))

;; ---------------------------------------------------------------------------
;; On save
;; ---------------------------------------------------------------------------

(def coalesce-ms
  "How long after a save the drawing waits, so a burst of edits is drawn once, as it ended."
  3000)

(defonce ^:private ^ScheduledExecutorService scheduler
  (Executors/newSingleThreadScheduledExecutor
   (reify java.util.concurrent.ThreadFactory
     (newThread [_ r] (doto (Thread. ^Runnable r "portrait-store") (.setDaemon true))))))

(defonce ^:private pending (atom {}))

(defn- draw-pending!
  "Draw what was saved last for character `id`, if a draw is still pending for it."
  [id]
  (let [[old _] (swap-vals! pending dissoc id)]
    (when-let [stored (get old id)]
      (try (when-let [fp (tag stored)] (png id fp stored))
           (catch Throwable e (println "portrait-store: drawing" id "failed -" (.getMessage e)))))))

(defn saved!
  "A character was saved with portrait EDN `stored` (nil: none). Nothing happens when its drawing
   is already kept, which is every autosave of an unrelated edit; otherwise one draw is scheduled
   `coalesce-ms` later with whatever was saved last. A removed portrait's files are deleted.
   Returns :kept, :scheduled or :removed."
  [id stored]
  (let [fp (tag stored)]
    (cond
      (nil? fp) (do (swap! pending dissoc id) (remove-others! id nil) :removed)
      (.isFile (file-for id fp)) (do (swap! pending dissoc id) :kept)
      :else (let [[old _] (swap-vals! pending assoc id stored)]
              (when-not (contains? old id)
                (.schedule scheduler ^Runnable #(draw-pending! id) (long coalesce-ms) TimeUnit/MILLISECONDS))
              :scheduled))))

(defn await-idle!
  "Block until no draw is waiting or running. For tests."
  []
  (loop [n 0] (when (and (seq @pending) (< n 300)) (Thread/sleep 50) (recur (inc n))))
  @(.submit scheduler ^Callable (fn [] :idle)))

(defn clear-memory!
  "Forget the in-memory drawings, as a restart would. For tests."
  []
  (bounded-cache/clear! drawn))
