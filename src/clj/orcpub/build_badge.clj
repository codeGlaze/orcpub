(ns orcpub.build-badge
  "Dev-mode corner badge: the git commit, when the client build last finished, and whether any
   source is newer than its build output. Nothing is rendered in production."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as s]
            [orcpub.config :as config])
  (:import [java.time Instant ZoneId]
           [java.time.format DateTimeFormatter]))

(def client-outputs
  "Client build files, relative to the project root. `lein fig:watch` rewrites both on every
   compile (measured); orcpub.js alone is what a one-off build leaves."
  ["resources/public/js/compiled/orcpub.js" "resources/public/js/compiled/out/cljs_deps.js"])
(def sheet-output "resources/public/css/compiled/styles.css")
(def client-inputs ["web/cljs" "src/cljc" "src/cljs" "dev.cljs.edn" "project.clj"])
(def style-inputs ["src/clj/orcpub/styles"])

(defn- source-file?
  "Whether `f` is a Clojure source file (.clj, .cljc, .cljs)."
  [^java.io.File f]
  (and (.isFile f) (boolean (re-find #"\.clj[sc]?$" (.getName f)))))

(defn- newest
  "The most recently modified file among `paths` under `root` (a directory is searched for Clojure
   sources, a plain file counts itself), or nil when there is none."
  [root paths]
  (->> paths
       (mapcat #(let [f (io/file root %)]
                  (cond (.isDirectory f) (filter source-file? (file-seq f))
                        (.isFile f) [f])))
       (sort-by #(.lastModified ^java.io.File %))
       last))

(defn- rel
  "The path of `f` relative to the directory `root`."
  [root ^java.io.File f]
  (s/replace (.getPath f) (str (.getPath (io/file root)) "/") ""))

(defn- stale-reason
  "Why the build outputs `outs` under `root` are older than the newest of `inputs`, or nil. Uses the
   newest output, so a watcher that rewrites only some files still counts as current."
  [root outs inputs]
  (let [built (->> outs (map #(io/file root %)) (filter #(.isFile ^java.io.File %))
                   (sort-by #(.lastModified ^java.io.File %)) last)]
    (if-not built
      (str (first outs) " does not exist")
      (when-let [n (newest root inputs)]
        (when (> (.lastModified ^java.io.File n) (.lastModified ^java.io.File built))
          (str (rel root built) " is older than " (rel root n)))))))

(defn problems
  "Reasons the client bundle or stylesheet under directory `root` is out of date against its
   sources, as strings; empty when both are current."
  [root]
  (vec (remove nil? [(stale-reason root client-outputs client-inputs)
                     (stale-reason root [sheet-output] style-inputs)])))

(defn built-at
  "The modification time, in millis, of the newest build output under `root`, or nil when there is none."
  [root]
  (some->> (conj client-outputs sheet-output)
           (map #(io/file root %)) (filter #(.isFile ^java.io.File %))
           (map #(.lastModified ^java.io.File %)) seq (apply max)))

(defn- clock
  "`millis` as local HH:mm."
  [millis]
  (.format (DateTimeFormatter/ofPattern "HH:mm")
           (.atZone (Instant/ofEpochMilli millis) (ZoneId/systemDefault))))

(defn- commit
  "The short git commit of the working directory's HEAD, or \"unknown\"."
  []
  (try (let [{:keys [exit out]} (shell/sh "git" "rev-parse" "--short=7" "HEAD")]
         (if (zero? exit) (s/trim out) "unknown"))
       (catch Exception _ "unknown")))

(defn badge
  "Hiccup for a tiny fixed bottom-left badge reading `<commit> · built HH:MM`, grey when the build
   is current and amber, with a title naming the newer file and `./menu start`, when it is not.
   Nil in production. Checks the working directory on every call."
  []
  (when (config/dev-mode?)
    (let [ps (problems ".")
          t (built-at ".")]
      [:div#build-badge
       (cond-> {:style (str "position:fixed;left:4px;bottom:4px;z-index:900;padding:1px 6px;"
                            "border-radius:3px;font:10px/1.4 system-ui,sans-serif;"
                            "pointer-events:none;"
                            (if (seq ps)
                              "background:#f0a100;color:#080A0D"
                              "background:rgba(128,128,128,0.35);color:rgba(255,255,255,0.7)"))}
         (seq ps) (assoc :title (str (s/join "; " ps) ". Run: ./menu start")))
       (str (commit) " · built " (if t (clock t) "never"))])))
