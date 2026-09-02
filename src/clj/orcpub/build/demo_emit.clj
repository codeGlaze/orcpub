(ns orcpub.build.demo-emit
  "Build-time emitter for the bundled demo-content pack. Reads the cljc recipe
   (orcpub.dnd.e5.demo-content), verifies it would survive a real import, then
   writes it to resources/public/demo/ via the shared serializer.

   Run via `lein gen-demo`. The generated file is committed (the same pattern the
   compiled CSS uses); the golden test proves the committed file matches this
   output and fails the build if it's stale. See docs/kb/demo-content-tier.md."
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.spec.alpha :as spec]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.content-specs :as content-specs]
            [orcpub.dnd.e5.orcbrew-format :as orcbrew-format]
            [orcpub.dnd.e5.demo-content :as demo]))

;; The demo pack is old-format compatible for now (demo/requires is empty), so it
;; ships as a plain .orcbrew. When the pack starts using features older builds
;; can't read, bump this to orcbrew-format/new-extension and rename the boot fetch.
(def output-path "resources/public/demo/demo-content.orcbrew")

(defn- save-spec-failures
  "Seq of [source content-type key] for every recipe item that doesn't satisfy its
   content type's strict save spec — the same spec the builder enforces on save, so
   a demo item is held to what a real authored item must be."
  [plugins]
  (for [[source plugin] plugins
        [content-type items] plugin
        :let [spec-k (content-specs/save-spec-for content-type)]
        [item-key item] items
        :when (and spec-k (not (spec/valid? spec-k item)))]
    [source content-type item-key]))

(defn verify!
  "Throw unless the pack would survive a real import: no item fails the per-item
   load floor, no item fails its strict save spec, and serialize->read round-trips
   to the same data. Returns the pack unchanged when it passes."
  [plugins]
  (let [{:keys [rejected]} (e5/salvage-library-items content-specs/valid-item-for-load? plugins)]
    (when (seq rejected)
      (throw (ex-info "Demo pack has items that fail the load floor"
                      {:rejected rejected})))
    (when-let [bad (seq (save-spec-failures plugins))]
      (throw (ex-info "Demo pack has items that fail their save spec"
                      {:failures (vec bad)})))
    (let [text  (orcbrew-format/serialize-orcbrew plugins)
          round (edn/read-string text)]
      (when-not (= plugins round)
        (throw (ex-info "Demo pack does not round-trip through the serializer" {}))))
    plugins))

(defn rendered
  "The exact text the committed demo file should contain: the verified pack,
   format-stamped (the pack uses non-backward-compatible features, so this wraps it
   in the v2 envelope), serialized. Pure — the golden test compares this to the
   file on disk, and the app unwraps it on import."
  []
  (orcbrew-format/serialize-orcbrew
   (orcbrew-format/stamp (verify! demo/plugins))))

(defn emit!
  "Verify and write the demo pack to `path` (default output-path). Returns the path."
  ([] (emit! output-path))
  ([path]
   (let [text (rendered)]
     (io/make-parents path)
     (spit path text)
     path)))

(defn -main [& _]
  (println "Emitting demo content pack ->" output-path)
  (emit!)
  (println "OK"))
