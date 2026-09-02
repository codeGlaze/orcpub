(ns orcpub.dnd.e5.orcbrew-format
  "Shared constants, serialization, and format-versioning for orcbrew content
   files, usable on both the JVM (the build-time demo-content emitter) and in the
   browser (every export/import path).

   Format versioning classifies a file by whether its CONTENT is backward
   compatible, not by when it was made:

   - v1 = fully backward compatible. Ships as a PLAIN file (no tag, no wrapper), so
     old builds read it unchanged.
   - v2 = contains at least one non-backward-compatible feature. Wrapped in an
     envelope `{:orcbrew/format-version 2 :orcbrew/requires [...] :orcbrew/content
     <plugin map>}`. The wrapper's keyword keys break an old build's \"every
     top-level key is a source name\" parse, so old builds bounce off it — which is
     exactly what we want for incompatible content. New builds read the version
     first and unwrap, or refuse a version they don't support with a clear message.

   The version lives inside the file. A separate file EXTENSION (name still being
   polled) will later keep v2 files out of an old build's file PICKER too; until
   then the envelope alone protects (old builds fail to load rather than corrupt).
   See docs/kb/orcbrew-format-versioning.md."
  (:require [clojure.string :as str]
            #?(:clj  [clojure.pprint :as pprint]
               :cljs [cljs.pprint :as pprint])))

(def new-extension
  "File extension for v2 (not-backward-compatible) files. NAME NOT FINAL — being
   polled with the community; kept in this one constant so the final choice is a
   single-line change. See docs/kb/orcbrew-format-versioning.md."
  ".orcbrewx")

(def current-format-version
  "The version stamped into content that uses non-backward-compatible features.
   Bumped when a new, incompatible generation of features lands."
  2)

(def supported-format-version
  "The highest format version this build can read. A file whose version exceeds
   this is refused on import with a clear message (forward-compat insurance: a
   build from this generation cleanly declines a future v-next file)."
  2)

;; ── Classification ──────────────────────────────────────────────────────────
;; What makes content v2 (incompatible with old builds). Bias is CONSERVATIVE:
;; over-tagging only gates a file needlessly (mildly annoying); under-tagging lets
;; an old build silently mangle incompatible content (the real headache). When a
;; new incompatible feature lands, add its marker here.

(def incompatible-content-types
  "Whole content types old builds don't know — any content under these is v2."
  #{:orcpub.dnd.e5/draconic-ancestries})

(defn- item-features
  "The non-backward-compatible feature markers a single content ITEM carries."
  [item]
  (when (map? item)
    (cond-> #{}
      (contains? item :grant)                (conj :grant)
      (seq (:save-proficiencies item))       (conj :save-proficiencies)
      ;; the ability-increase SPREAD is the vector form [[amount pool] ...]; the
      ;; legacy feat format is a set (#{:str}) and stays backward compatible.
      (vector? (:ability-increases item))     (conj :ability-increase-spread))))

(defn detect-incompatible-features
  "Scan content for the features that make it v2. Returns a sorted set of feature
   markers (empty = fully backward compatible). Crash-safe: malformed shapes yield
   no markers rather than throwing, so it can run on any export (incl. emergency
   dumps). Accepts either a multi-plugin `{source plugin}` map or a single
   `{content-type items}` plugin."
  [content]
  (if-not (map? content)
    (sorted-set)
    (let [plugins (if (every? string? (keys content)) (vals content) [content])]
      (into (sorted-set)
            (mapcat
             (fn [plugin]
               (when (map? plugin)
                 (mapcat
                  (fn [[content-type items]]
                    (concat
                     (when (and (contains? incompatible-content-types content-type)
                                (seq items))
                       [content-type])
                     (when (map? items)
                       (mapcat item-features (vals items)))))
                  plugin)))
             plugins)))))

(defn content-format-version
  "1 if `content` is fully backward compatible, else `current-format-version`."
  [content]
  (if (seq (detect-incompatible-features content)) current-format-version 1))

;; ── Envelope (v2 files only) ────────────────────────────────────────────────

(defn envelope?
  "True if `data` is a version-stamped v2 envelope (vs. a plain plugin map)."
  [data]
  (and (map? data) (contains? data :orcbrew/format-version)))

(defn stamp
  "Wrap `content` in the version envelope IFF it contains an incompatible feature;
   otherwise return it unchanged (plain v1). Idempotent — an already-stamped
   envelope passes through. Every export path can call this safely: plain content
   stays plain, so backward-compatible files are never needlessly gated.

   `envelope-meta` (2-arity) merges extra fields into the envelope, e.g.
   `{:orcbrew/content-version 1}` — the CONTENT's own revision number (see
   `content-version`), distinct from the format version. Only carried when the
   content is v2 (there's an envelope to hang it on)."
  ([content] (stamp content nil))
  ([content envelope-meta]
   (if (envelope? content)
     content
     (let [features (detect-incompatible-features content)]
       (if (seq features)
         (merge {:orcbrew/format-version current-format-version
                 :orcbrew/requires (vec features)
                 :orcbrew/content content}
                envelope-meta)
         content)))))

(defn unwrap
  "The plugin map inside a v2 envelope, or `data` unchanged when it's a plain file."
  [data]
  (if (envelope? data) (:orcbrew/content data) data))

(defn file-version
  "The format version a parsed file declares: the envelope's tag, or 1 for a plain
   (un-enveloped) file."
  [data]
  (if (envelope? data) (:orcbrew/format-version data) 1))

(defn content-version
  "The content's OWN revision number, read from a stamped envelope's
   `:orcbrew/content-version` (nil when absent or on a plain file). Distinct from
   the FORMAT version: this tracks a content pack's revisions — e.g. the demo pack's
   release number, for copy-on-edit provenance / graduation — not the file-format
   compatibility class. Read it from the raw parsed file BEFORE `unwrap` discards
   the envelope."
  [data]
  (when (envelope? data) (:orcbrew/content-version data)))

(defn compat-check
  "Decide whether this build can import `data`. Returns {:ok? true} for a plain
   file or a v2 envelope this build supports; {:ok? false :message ...} when the
   file's version exceeds `supported-format-version`, so the caller can refuse with
   a clear, actionable message instead of mangling content it doesn't understand."
  [data]
  (let [v (file-version data)]
    (if (and (number? v) (> v supported-format-version))
      {:ok? false
       :version v
       :requires (:orcbrew/requires data)
       :message (str "This content is in a newer format (v" v ") than this app "
                     "version can read (v" supported-format-version "). Update the "
                     "app to import it"
                     (when-let [r (seq (:orcbrew/requires data))]
                       (str " — it uses: " (str/join ", " (map name r))))
                     ".")}
      {:ok? true :version v})))

(defn serialize-orcbrew
  "Pure serialize of homebrew content to .orcbrew text — no side effects, so it's
   unit-testable and shared by every export path. pretty-print? is opt-in: pprint
   inflates 3-5MB files to ~10-20MB and can freeze the UI."
  [data & {:keys [pretty-print?]}]
  (if pretty-print?
    (with-out-str (pprint/pprint data))
    (str data)))
