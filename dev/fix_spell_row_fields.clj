;; Makes every printed spell row reachable by the field the app writes to.
;;
;;   lein run -m clojure.main dev/fix_spell_row_fields.clj
;;
;; Rewrites the masters in resources/ in place.
;;
;; Two different faults, both of which silently lost a spell. pdf_spec emits
;; spells-LEVEL-ROW-1 counting from 1 with no gaps, so a template whose fields
;; skip a number drops the value at the gap and leaves its highest row blank.
;;
;; Styles 1 and 3, level 3: THIRTEEN fields for thirteen printed rows, numbered
;; 1-10, 12, 13, 14. Nothing is missing from the page -- the names are just
;; wrong, so spells-3-11 went nowhere and spells-3-14 was never written. Renamed
;; in ascending order, which keeps each target free as it is reached.
;;
;; Style 4, level 2: ELEVEN fields for thirteen printed rows, numbered 1-6 and
;; 9-13, with a 42.3pt hole between rows 6 and 9 -- three row-pitches where two
;; rows are drawn and have nothing on top of them. Two fields are added there.
;;
;; Style 1's PREPARED checkboxes carry the level 3 misnumbering too, in the
;; column beside those same rows -- the only style with the checkboxes at all.
;; Under print-prepared-spells? the tick for row 11 went nowhere and row 14's was
;; never set, so a prepared spell printed unticked.
;;
;; The clones made at export copy the master's fields, so fixing the master
;; fixes every generated page with it.

(require '[orcpub.pdf :as pdf]
         '[clojure.java.io :as io])
(import '[org.apache.pdfbox Loader]
        '[org.apache.pdfbox.cos COSName]
        '[org.apache.pdfbox.pdmodel.common PDRectangle]
        '[org.apache.pdfbox.pdmodel.interactive.annotation PDAnnotationWidget]
        '[org.apache.pdfbox.pdmodel.interactive.form PDTextField]
        '[java.io File FileOutputStream])

(declare add-row!*)

(defn- field-named [doc nm]
  (.getField (.getAcroForm (.getDocumentCatalog doc)) nm))

(defn- rename!
  "Renames `from` to `to`, ascending so each target is free when reached.

   Refuses when `to` already exists. These renames SHIFT a run down by one, so a
   second pass over an already-fixed file finds the next field sitting under the
   old name and renames it too -- which produced two fields called spells-3-11-1
   and lost a row. Two fields of one name are one field with one value."
  [doc from to]
  (cond
    (field-named doc to)
    (do (printf "    %s already exists, nothing renamed%n" to) false)

    (field-named doc from)
    (do (.setPartialName (field-named doc from) to)
        (printf "    %s -> %s%n" from to)
        true)

    :else
    (do (printf "    %s ABSENT, nothing renamed%n" from) false)))

(defn- add-row!
  "A text field named `nm` on `model`'s row pitch, `steps` rows below it.

   Copies the model widget's geometry and styling entries and moves the copy down
   -- the same set add-spell-pages! copies for a cloned page, and for the same
   reason: /AP is left off so write-fields! bakes an appearance from the value
   rather than sharing the model's."
  [doc model-name nm steps pitch]
  (if (field-named doc nm)
    (printf "    %s already exists, not added again%n" nm)
    (add-row!* doc model-name nm steps pitch)))

(defn- add-row!* [doc model-name nm steps pitch]
  (let [form (.getAcroForm (.getDocumentCatalog doc))
        model (field-named doc model-name)
        mw (first (.getWidgets model))
        page (.getPage mw)
        r (.getRectangle mw)
        field (PDTextField. form)
        widget (PDAnnotationWidget.)]
    (.setPartialName field nm)
    (doseq [k [COSName/DA COSName/MK COSName/F COSName/FT]]
      (when-let [v (.getDictionaryObject (.getCOSObject mw) k)]
        (.setItem (.getCOSObject widget) k v)))
    (.setRectangle widget (PDRectangle. (.getLowerLeftX r)
                                        (- (.getLowerLeftY r) (* steps pitch))
                                        (.getWidth r)
                                        (.getHeight r)))
    (.setPage widget page)
    (.setWidgets field (java.util.ArrayList. [widget]))
    (.setAnnotations page (java.util.ArrayList.
                           (conj (vec (.getAnnotations page)) widget)))
    (.setFields form (java.util.ArrayList. (conj (vec (.getFields form)) field)))
    (printf "    added %s at y=%.1f%n" nm (.getLowerLeftY (.getRectangle widget)))))

(defn- fix! [style f]
  (let [{:keys [file]} (get pdf/sheet-masters style)
        out (File. (str "resources/" file))]
    (printf "style %d  %s%n" style file)
    (with-open [doc (Loader/loadPDF (.readAllBytes (.openStream (io/resource file))))]
      (f doc)
      (with-open [o (FileOutputStream. out)] (.save doc o)))))

(doseq [style [1 3]]
  (fix! style (fn [doc]
                (doseq [[from to] [["spells-3-12-1" "spells-3-11-1"]
                                   ["spells-3-13-1" "spells-3-12-1"]
                                   ["spells-3-14-1" "spells-3-13-1"]]]
                  (rename! doc from to)))))

;; 14.1pt: (145.7 - 103.4) / 3, the pitch across the hole rather than a guess.
(fix! 4 (fn [doc]
          (add-row! doc "spells-2-6-1" "spells-2-7-1" 1 14.1)
          (add-row! doc "spells-2-6-1" "spells-2-8-1" 2 14.1)))

;; The prepared column beside style 1's level 3 rows, misnumbered the same way.
(fix! 1 (fn [doc]
          (doseq [[from to] [["prepared-3-12-1" "prepared-3-11-1"]
                             ["prepared-3-13-1" "prepared-3-12-1"]
                             ["prepared-3-14-1" "prepared-3-13-1"]]]
            (rename! doc from to))))
