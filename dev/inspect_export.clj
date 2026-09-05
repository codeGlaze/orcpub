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

(def ^:private still-unnamed
  "Template names the preparation passes are meant to have replaced. The suffix
   is what split-fields-across-pages! appends to a copy, and it has to be part of
   the pattern or a copy that no pass renamed reads as named."
  #"(?i)(check box|slotsremaining) \d+(-p\d+)?")

(defn -main [& [path min-pages]]
  (let [file (File. (or path "/tmp/e2e-pdf/character.pdf"))
        wanted (when min-pages (Integer/parseInt min-pages))]
    (when-not (.exists file)
      (println "  FAIL  no exported PDF at" (.getPath file))
      (System/exit 1))
    (with-open [doc (Loader/loadPDF (.readAllBytes (java.io.FileInputStream. file)))]
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            ;; the field tree, not .getFields: that returns only root fields, so
            ;; anything nested would go unchecked
            fields (if form (iterator-seq (.iterator (.getFieldTree form))) [])
            names (map #(.getFullyQualifiedName %) fields)
            valued (into {} (for [f fields
                                  :let [v (str (.getValueAsString f))]
                                  :when (not (str/blank? v))]
                              [(.getFullyQualifiedName f) v]))
            page-numbers (into {} (map-indexed (fn [i p] [p (inc i)]) (.getPages doc)))
            pages-of (fn [f] (into #{} (keep #(page-numbers (.getPage %)) (.getWidgets f))))
            orphans (for [f fields w (.getWidgets f) :when (nil? (.getPage w))] w)
            duplicates (->> names frequencies (filter #(> (val %) 1)) (map key))
            unnamed (filter #(re-matches still-unnamed %) names)
            ;; a field with widgets on two pages is ONE value shown twice, so
            ;; ticking it on one class's page ticks it on another's
            spanning (filter #(> (count (pages-of %)) 1) fields)

            ;; spellcasting sections, in page order
            sections (->> fields
                          (filter #(re-matches #"spellcasting-class-\d+"
                                               (.getFullyQualifiedName %)))
                          (keep (fn [f]
                                  (let [v (str (.getValueAsString f))]
                                    (when-not (str/blank? v)
                                      {:suffix (subs (.getFullyQualifiedName f)
                                                     (count "spellcasting-class-"))
                                       :page (first (sort (pages-of f)))
                                       :heading v
                                       :class (str/replace v #"\s*\(continued\)$" "")
                                       :continued? (str/ends-with? v "(continued)")}))))
                          (sort-by :page))
            slots-on (fn [suffix]
                       (for [level (range 1 10)
                             :let [f (.getField form (str "spell-slots-" level "-" suffix))]
                             :when (and f (not (str/blank? (str (.getValueAsString f)))))]
                         level))
            ;; a class's first page carries its slots; a later page of the same
            ;; class must be marked and must not repeat them
            mismarked (let [seen (volatile! #{})]
                        (doall
                         (for [s sections
                               :let [repeat? (contains? @seen (:class s))]
                               :when (do (vswap! seen conj (:class s))
                                         (not= repeat? (:continued? s)))]
                           (str "page " (:page s) " " (pr-str (:heading s))
                                (if repeat? " is a repeat but unmarked" " is marked but is the first")))))
            repeated-slots (for [s sections
                                 :when (and (:continued? s) (seq (slots-on (:suffix s))))]
                             (str "page " (:page s) " repeats levels "
                                  (str/join "," (slots-on (:suffix s)))))
            results
            (concat
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
              (report (empty? unnamed) "every field carries a meaningful name"
                      (when (seq unnamed) (str (count unnamed) " left as in the template: "
                                               (str/join ", " (take 3 unnamed)))))
              (report (empty? spanning) "no field spans two pages"
                      (if (seq spanning)
                        (str (count spanning) " would mirror, e.g. "
                             (str/join ", " (take 3 (map #(.getFullyQualifiedName %) spanning))))
                        "nothing mirrors between class pages"))]
             (when (seq sections)
               [(report (every? #(not (str/blank? (:class %))) sections)
                        "every spellcasting section names its class"
                        (str/join " / " (map :heading sections)))
                (report (empty? mismarked) "a class's later pages are marked continued"
                        (if (seq mismarked) (str/join "; " mismarked)
                            (str (count (distinct (map :class sections))) " class(es) over "
                                 (count sections) " page(s)")))
                (report (empty? repeated-slots) "slots appear only on a class's first page"
                        (when (seq repeated-slots) (str/join "; " repeated-slots)))]))]
        (println (format "\n  %d/%d checks passed on the exported PDF"
                         (count (filter true? results)) (count results)))
        (System/exit (if (every? true? results) 0 1))))))

(apply -main *command-line-args*)
