(ns orcpub.export-paths-test
  "Guards the shape of the export system rather than any one export.

   Every per-path test passes on a codebase where one path runs the correction
   gate and another does not — that is exactly how the per-source EXPORT button
   drifted out of the unification and stayed there. These are net tests over the
   ClojureScript source: they fail when a NEW way to write a file appears, or
   when an existing writer stops going through the shared entry points, so the
   next divergence surfaces on the file instead of in someone's .orcbrew."
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as s]))

(def ^:private events-src (slurp (io/file "src/cljs/orcpub/dnd/e5/events.cljs")))

(defn- enclosing-form-names
  "Names of the top-level forms in `src` whose body contains `needle`. A
   top-level form starts at column 0; the name is the symbol after the opening
   symbol, which covers `(defn f`, `(defn- f` and `(reg-event-fx\\n ::e5/f`."
  [src needle]
  (->> (s/split src #"(?m)^(?=\()")
       (filter #(s/includes? % needle))
       (keep #(second (re-find #"^\(\S+\s+(\S+)" (s/replace % #"\n" " "))))
       set))

(deftest only-one-function-writes-a-file
  ;; saveAs is the browser's "put bytes on disk". Keeping every call inside
  ;; save-orcbrew-blob! is what makes "grep the export paths" a complete answer;
  ;; the two paths that skipped it were invisible to a grep for the helper.
  (testing "js/saveAs is reached only through save-orcbrew-blob!"
    (is (= #{"save-orcbrew-blob!"}
           (enclosing-form-names events-src "js/saveAs"))
        (str "A new file writer bypasses the shared serializer. Route it "
             "through save-orcbrew-blob! rather than building a Blob."))))

(deftest every-download-is-gated-or-a-named-hatch
  ;; The allowlist IS the policy: an export either runs the correction gate or
  ;; is one of the hatches below, deliberately unvalidated and documented as
  ;; such. A new entry here is a decision someone has to make on purpose.
  (testing "callers of save-orcbrew-blob! are the gate or a declared hatch"
    (is (= #{;; the gate — corrects, validates, then writes
             "validate-and-show-modal-or-export"    ; one source
             "::e5/export-all-plugins"              ; the whole library
             ":export-with-auto-fix"                ; the gate's fill-in dialog, resumed
             ;; hatches: unvalidated on purpose, so content can always get out
             ":export-as-is"                        ; "export anyway", after the gate objected
             "reg-export-draft"                     ; builder WIP rescue
             "::e5/emergency-export-raw"            ; offered when export refuses
             "::e5/export-quarantined-raw"          ; a source validation already rejected
             "::e5/export-all-plugins-pretty-print"} ; footer safety valve
           (disj (enclosing-form-names events-src "(save-orcbrew-blob!")
                 "save-orcbrew-blob!"))
        (str "An export path changed. If it is a new hatch, add it here with a "
             "note saying why it skips validation; otherwise route it through "
             "validate-and-show-modal-or-export or ::e5/export-all-plugins."))))
