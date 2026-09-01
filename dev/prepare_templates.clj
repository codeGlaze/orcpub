(ns prepare-templates
  "Bakes the static cleanups into the character sheet templates in resources/.

     lein with-profile init-db run -m clojure.main dev/prepare_templates.clj

   Four transforms are pure functions of the template, so doing them per request
   repeated identical work on every export. They are applied here once instead:

     pdf/prune-orphan-widgets!        drop widgets belonging to no page
     pdf/split-fields-across-pages!   one field per page, so nothing mirrors
     pdf/name-prepared-checkboxes!    Check Box 25 -> prepared-1-1-1
     pdf/name-death-save-checkboxes!  the six ticks on the character page
     pdf/disambiguate-duplicate-fields!  anything still sharing a name

   The per-character passes stay at runtime, since they depend on the character:
   pdf/add-missing-spell-pages! and pdf/spill-overflow!.

   Idempotent: a second run reports nothing to do. Rerun after replacing or
   re-cutting any template, and commit the result."
  (:require [orcpub.pdf :as pdf]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (org.apache.pdfbox Loader)
           (java.io File FileOutputStream)))

(defn- field-names [doc]
  (if-let [form (.getAcroForm (.getDocumentCatalog doc))]
    (map #(.getFullyQualifiedName %) (.getFields form))
    []))

(defn- stats [doc]
  (let [names (field-names doc)]
    {:fields (count names)
     :duplicates (->> names frequencies (filter #(> (val %) 1)) count)
     ;; the split suffixes its copies, so an unnamed one can read "Check Box 25-p2"
     :anonymous (count (filter #(re-matches #"(?i)check box \d+(-p\d+)?" %) names))}))

(defn- prepare! [^File file]
  (let [before-bytes (.length file)]
    (with-open [doc (Loader/loadPDF (.readAllBytes (io/input-stream file)))]
      (let [before (stats doc)
            removed (pdf/prune-orphan-widgets! doc)
            ;; before naming: a field whose widgets sit on two pages would keep
            ;; the first page's name and mirror onto the second
            split (pdf/split-fields-across-pages! doc)
            named (+ (pdf/name-prepared-checkboxes! doc)
                     (pdf/name-death-save-checkboxes! doc))
            disambiguated (pdf/disambiguate-duplicate-fields! doc)
            after (stats doc)
            ;; Compared rather than inferred from the return values: a field
            ;; carrying no widgets at all is dropped without any widget being
            ;; counted, so prune's tally can be 0 on a document it did change.
            changed? (not= before after)]
        (when changed?
          (with-open [out (FileOutputStream. file)] (.save doc out)))
        (println (format "%-46s %5d -> %-5d fields  %4d KB -> %-5d KB  %s"
                         (.getName file)
                         (:fields before) (:fields after)
                         (quot before-bytes 1024) (quot (.length file) 1024)
                         (if changed?
                           (format "pruned %d, split %d, named %d, renamed %d"
                                   removed split named disambiguated)
                           "already clean")))
        ;; Duplicates are a correctness problem -- same name, one shared value --
        ;; so they must all be gone. Anonymous names are only unhelpful, and the
        ;; geometric pairing is tuned to the style 1 layout, so other styles keep
        ;; some; they are reported rather than treated as a failure.
        (assert (zero? (:duplicates after))
                (str (.getName file) " still has duplicate field names"))
        {:changed? changed? :anonymous-left (:anonymous after)}))))

(defn -main [& _]
  (let [files (sort-by #(.getName %)
                       (filter #(re-find #"^fillable-char-sheetstyle-.*\.pdf$" (.getName %))
                               (file-seq (io/file "resources"))))
        before (reduce + (map #(.length %) files))
        results (doall (map prepare! files))
        anonymous (reduce + (map :anonymous-left results))]
    (when (pos? anonymous)
      (println (format "\n%d checkboxes keep their 'Check Box N' name: the row pairing is\nshaped for the style 1 layout." anonymous)))
    (println (format "\n%d of %d templates changed; %.1f MB -> %.1f MB"
                     (count (filter :changed? results)) (count files)
                     (/ before 1048576.0)
                     (/ (reduce + (map #(.length %) files)) 1048576.0)))))

(-main)
