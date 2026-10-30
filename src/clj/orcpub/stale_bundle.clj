(ns orcpub.stale-bundle
  "Dev-mode check that the compiled client and stylesheet are not older than their sources, so a
   server started without Figwheel or Garden does not quietly serve an old build
   (docs/kb/stale-bundle-banner.md)."
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [orcpub.config :as config]))

(def bundle-path "resources/public/js/compiled/orcpub.js")
(def sheet-path "resources/public/css/compiled/styles.css")
(def client-dirs ["web/cljs" "src/cljc" "src/cljs"])
(def style-dirs ["src/clj/orcpub/styles"])
(def build-inputs ["dev.cljs.edn"])

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

(defn- stale-reason
  "Why the build at `built` is out of date against the newest of `paths` under `root`, or nil."
  [root built paths]
  (let [b (io/file root built)]
    (if-not (.isFile b)
      (str built " does not exist")
      (when-let [n (newest root paths)]
        (when (> (.lastModified ^java.io.File n) (.lastModified b))
          (str built " is older than " (s/replace (.getPath ^java.io.File n)
                                                  (str (.getPath (io/file root)) "/") "")))))))

(defn problems
  "Reasons the client bundle or stylesheet under directory `root` is out of date against its
   sources, as strings; empty when both are current."
  [root]
  (vec (remove nil? [(stale-reason root bundle-path (into client-dirs build-inputs))
                     (stale-reason root sheet-path style-dirs)])))

(defn banner
  "Hiccup for a fixed bar naming what is out of date and the commands that rebuild it, or nil in
   production or when the build is current. Checks the working directory on every call."
  []
  (when (config/dev-mode?)
    (when-let [ps (seq (problems "."))]
      [:div#stale-bundle
       {:style (str "position:fixed;left:0;right:0;top:0;z-index:2147483001;padding:8px 14px;"
                    "background:#f0a100;color:#080A0D;font:600 13px/1.4 system-ui,sans-serif;"
                    "text-align:center")}
       (str "This page is running an old build: " (s/join "; " ps)
            ". Start Figwheel (./menu start figwheel), or run lein fig:build and lein garden once.")])))
