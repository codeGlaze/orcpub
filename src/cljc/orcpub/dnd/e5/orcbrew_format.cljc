(ns orcpub.dnd.e5.orcbrew-format
  "Shared constants and serialization for orcbrew content files, usable on both the
   JVM (the build-time demo-content emitter) and in the browser (every export path).

   Two things gate the not-backward-compatible content format the extensibility
   work produces: the file EXTENSION keeps new-format files out of an old build's
   file picker, and in-file version/compat tags let a new build check
   compatibility explicitly and refuse with a clear message. The version lives
   inside the file, not in the extension, so the extension name stays stable
   across versions.

   NOTE: only the extension and version constants and the pure serializer live
   here so far. Writing the version/compat tags into a file needs a wrapper
   envelope plus the matching unwrap on import (the plugin map is keyed by source
   name, so keyword tags can't be assoc'd at its top level without breaking
   multi-plugin detection and the round-trip). That envelope lands with the import
   side. See docs/kb/orcbrew-format-versioning.md."
  (:require #?(:clj  [clojure.pprint :as pprint]
               :cljs [cljs.pprint :as pprint])))

(def new-extension
  "File extension for the new, not-backward-compatible content format. NAME NOT
   FINAL — being polled with the community; kept in this one constant so the final
   choice is a single-line change. See docs/kb/orcbrew-format-versioning.md."
  ".orcbrewx")

(def format-version
  "Current orcbrew content format version. Bumped when the on-disk shape changes.
   Written into every new-format file as :orcbrew/format-version so a new build can
   read it first and refuse content from a version it doesn't understand."
  1)

(defn serialize-orcbrew
  "Pure serialize of homebrew content to .orcbrew text — no side effects, so it's
   unit-testable and shared by every export path. pretty-print? is opt-in: pprint
   inflates 3-5MB files to ~10-20MB and can freeze the UI."
  [data & {:keys [pretty-print?]}]
  (if pretty-print?
    (with-out-str (pprint/pprint data))
    (str data)))
