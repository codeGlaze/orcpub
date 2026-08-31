(ns on-demand-pages
  "PROOF OF CONCEPT -- not wired into the exporter.

   Generates spell pages at runtime instead of shipping seven pre-baked variants
   per style, which is where the orphaned widgets come from: the variants were
   made by deleting pages from a master, and deleting a page leaves its fields
   behind in the AcroForm.

     lein with-profile init-db run -m clojure.main dev/on_demand_pages.clj

   Writes /tmp/ondemand.pdf: a 2-page base plus EIGHT spellcasting class pages.
   Today's sheet tops out at six and silently drops classes seven and eight.

   Result: 10 pages, 1830 fields, ZERO duplicate names, 757 KB -- against the
   current six-class template's 9 pages, 1407 fields, 1596 orphans and ~1.2 MB.
   More classes, more pages, fewer bytes, no ghosts.

   Two things make it work:

   1. A cloned page REFERENCES the master's /Contents and /Resources rather than
      copying them, so extra pages cost field structure only, not artwork.
   2. Every generated field is renamed per page. This is not cosmetic: in PDF,
      fields sharing a fully-qualified name ARE THE SAME FIELD and share one
      value -- tick a checkbox on one page and its twin ticks on another. That
      constraint is almost certainly why the original template carries hundreds
      of uniquely-numbered \"Check Box NNNN\" fields for pages that were later
      deleted.

   The same mechanism covers the other overflow cases: more pages as a caster
   levels up, and features-and-traits-3, -4 and so on when the traits text
   outgrows the single continuation page.

   Still to do before this could ship: decide where page generation belongs
   relative to routes.clj's sheet0..sheet6 selection, and teach pdf_spec to emit
   names beyond the sixth class."
  (:require [clojure.string :as str])
  (:import (org.apache.pdfbox Loader)
           (org.apache.pdfbox.cos COSDictionary COSName)
           (org.apache.pdfbox.pdmodel PDPage)
           (org.apache.pdfbox.pdmodel.interactive.form PDTextField PDCheckBox PDTerminalField)
           (org.apache.pdfbox.pdmodel.interactive.annotation PDAnnotationWidget)
           (java.io File ByteArrayOutputStream)))

(defn form [d] (.getAcroForm (.getDocumentCatalog d)))
(defn live-set [d] (into #{} (for [p (.getPages d) a (.getAnnotations p)]
                               (System/identityHashCode (.getCOSObject a)))))
(defn prune! [d]
  (let [live (live-set d)
        ok? #(contains? live (System/identityHashCode (.getCOSObject %)))
        kept (reduce (fn [acc f]
                       (let [ws (vec (.getWidgets f)) good (filterv ok? ws)]
                         (cond (empty? good) acc
                               (= (count good) (count ws)) (conj acc f)
                               (instance? PDTerminalField f)
                               (do (.setWidgets f (java.util.ArrayList. good)) (conj acc f))
                               :else (conj acc f))))
                     [] (vec (.getFields (form d))))]
    (.setFields (form d) (java.util.ArrayList. kept)) kept))

;; page dict that REFERENCES the source artwork -- no pixels copied
(defn clone-page [^PDPage src]
  (let [sd (.getCOSObject src) d (COSDictionary.)]
    (.setItem d COSName/TYPE COSName/PAGE)
    (doseq [k [COSName/CONTENTS COSName/RESOURCES COSName/MEDIA_BOX COSName/ROTATE]]
      (when-let [v (.getDictionaryObject sd k)] (.setItem d k v)))
    (PDPage. d)))

(defn rename-suffix
  "spells-3-4-1 -> spells-3-4-<n>; spell-slots-2-1 -> spell-slots-2-<n>; etc."
  [nm n]
  (if (re-find #"-1$" nm) (str/replace nm #"-1$" (str "-" n)) (str nm "-" n)))

(defn add-class-page!
  "Clone the spell-page master into `doc` as class index `n`, with UNIQUE names."
  [doc master-page master-fields n]
  (let [pg (clone-page master-page)
        frm (form doc)]
    (.addPage doc pg)
    (let [new-fields
          (doall (for [fld master-fields
                       :let [w (first (.getWidgets fld))]
                       :when (and w (instance? PDTerminalField fld))]
                   (let [txt? (instance? PDTextField fld)
                         nf (if txt? (PDTextField. frm) (PDCheckBox. frm))
                         nw (PDAnnotationWidget.)
                         nd (.getCOSObject nw) od (.getCOSObject w)]
                     (.setPartialName nf (rename-suffix (.getFullyQualifiedName fld) n))
                     (doseq [k [COSName/RECT COSName/DA COSName/MK COSName/F
                                COSName/AP COSName/AS COSName/FT]]
                       (when-let [v (.getDictionaryObject od k)] (.setItem nd k v)))
                     (.setPage nw pg)
                     (.setWidgets nf (java.util.ArrayList. [nw]))
                     (.setAnnotations pg (java.util.ArrayList. (conj (vec (.getAnnotations pg)) nw)))
                     nf)))]
      (.setFields frm (java.util.ArrayList. (concat (vec (.getFields frm)) new-fields)))
      new-fields)))

;; --- build a spell-page master and a 2-page base from existing assets ---
(defn cut [keep-pred out]
  (with-open [d (Loader/loadPDF (File. "resources/fillable-char-sheetstyle-1-6-spells.pdf"))]
    (doseq [i (reverse (range (.getNumberOfPages d))) :when (not (keep-pred i))]
      (.removePage d i))
    (prune! d)
    (let [o (ByteArrayOutputStream.)] (.save d o)
      (java.nio.file.Files/write (.toPath (File. out)) (.toByteArray o)
                                 (into-array java.nio.file.OpenOption [])))))
(cut #(= % 2) "/tmp/m-spell.pdf")
(cut #(< % 2) "/tmp/m-base.pdf")

;; --- generate EIGHT class pages onto the base ---
(with-open [base (Loader/loadPDF (File. "/tmp/m-base.pdf"))
            mst  (Loader/loadPDF (File. "/tmp/m-spell.pdf"))]
  (let [mp (.getPage mst 0)
        mf (vec (.getFields (form mst)))]
    (dotimes [i 8] (add-class-page! base mp mf (inc i)))
    (let [frm (form base)
          names (map #(.getFullyQualifiedName %) (.getFields frm))
          dupes (->> names frequencies (filter #(> (val %) 1)) (map key))]
      (println "pages:" (.getNumberOfPages base))
      (println "fields:" (count names))
      (println "DUPLICATE names:" (count dupes) (if (empty? dupes) "<- none, every page is independent" (take 5 dupes)))
      ;; fill class 7 and 8 -- the two the current sheet drops entirely
      (doseq [n [7 8]]
        (.setValue (.getField frm (str "spellcasting-class-" n)) (str "CLASS " n " NOW HAS A PAGE"))
        (.setValue (.getField frm (str "spells-1-1-" n)) (str "First spell of class " n)))
      (let [o (ByteArrayOutputStream.)] (.save base o)
        (java.nio.file.Files/write (.toPath (File. "/tmp/ondemand.pdf")) (.toByteArray o)
                                   (into-array java.nio.file.OpenOption []))
        (println "written:" (quot (.size o) 1024) "KB for" (.getNumberOfPages base) "pages")))))
