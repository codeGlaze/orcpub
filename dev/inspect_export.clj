(ns inspect-export
  "Asserts the invariants of an exported character sheet.

     lein with-profile init-db run -m clojure.main dev/inspect_export.clj <file.pdf>

   scripts/e2e/run.sh calls this on the PDF the browser produced. The browser
   script can only check status, content type and size: PDF field names live in
   compressed object streams, so they cannot be read without a parser, and node
   has none here. This runs where PDFBox already is.

   Exits non-zero on any failure so the e2e run fails with it."
  (:require [clojure.string :as str])
  (:import (org.apache.pdfbox Loader)
           (java.io File)))

(defn- report [ok? label detail]
  (println (format "  %s  %s%s" (if ok? "ok  " "FAIL") label
                   (if detail (str " - " detail) "")))
  ok?)

(defn -main [& [path min-pages]]
  (let [file (File. (or path "/tmp/e2e-pdf/character.pdf"))
        wanted (when min-pages (Integer/parseInt min-pages))]
    (when-not (.exists file)
      (println "  FAIL  no exported PDF at" (.getPath file))
      (System/exit 1))
    (with-open [doc (Loader/loadPDF (.readAllBytes (java.io.FileInputStream. file)))]
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            fields (if form (vec (.getFields form)) [])
            names (map #(.getFullyQualifiedName %) fields)
            valued (into {} (for [f fields
                                  :let [v (str (.getValueAsString f))]
                                  :when (not (str/blank? v))]
                              [(.getFullyQualifiedName f) v]))
            orphans (for [f fields w (.getWidgets f) :when (nil? (.getPage w))] w)
            duplicates (->> names frequencies (filter #(> (val %) 1)) (map key))
            anonymous (filter #(re-matches #"(?i)check box \d+" %) names)
            results
            [(report (>= (.getNumberOfPages doc) (or wanted 1))
                     (if wanted
                       (str "the sheet has at least " wanted " pages")
                       "the sheet has pages")
                     (str (.getNumberOfPages doc)))
             (report (some? form) "the form survived" nil)
             (report (> (count valued) 40) "fields carry the character's values"
                     (str (count valued) " of " (count fields)))
             (report (not (str/blank? (get valued "class-level" ""))) "class and level are filled"
                     (get valued "class-level"))
             (report (empty? orphans) "no widget belongs to no page"
                     (when (seq orphans) (str (count orphans) " orphaned")))
             (report (empty? duplicates) "no field name is shared"
                     (when (seq duplicates) (str/join ", " (take 4 duplicates))))
             (report (empty? anonymous) "every checkbox is named"
                     (when (seq anonymous) (str (count anonymous) " still 'Check Box N'")))]]
        (println (format "\n  %d/%d checks passed on the exported PDF"
                         (count (filter true? results)) (count results)))
        (System/exit (if (every? true? results) 0 1))))))

(apply -main *command-line-args*)
