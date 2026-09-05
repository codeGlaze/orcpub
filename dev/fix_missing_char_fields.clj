;; Gives style 3 the hit-dice field its sheet prints a box for, and style 4 the
;; name the app writes to.
;;
;;   lein run -m clojure.main dev/fix_missing_char_fields.clj
;;
;; Rewrites the masters in resources/ in place.
;;
;; Style 3 prints a TOTAL / HIT DICE box and has no `hd` field, so a level 20
;; character's "20d6" had nowhere to go and the box printed empty on every sheet.
;; The new field is placed against the printed TOTAL label, which PDFTextStripper
;; puts at x 238.2, y 454.7 from the foot, rather than against a guess: the box
;; runs from just under that label down to the banner, and the death saves beside
;; it start at x 346.5.
;;
;; Style 4 HAS the second-page name field and calls it character-name-p2. pdf_spec
;; emits character-name-2, which every other style uses, so the value was reported
;; unplaceable and the box printed empty. Renamed rather than aliased -- one name
;; for one thing, and the alias would have to be carried by every caller.

(require '[orcpub.pdf :as pdf]
         '[clojure.java.io :as io])
(import '[org.apache.pdfbox Loader]
        '[org.apache.pdfbox.cos COSName]
        '[org.apache.pdfbox.pdmodel.common PDRectangle]
        '[org.apache.pdfbox.pdmodel.interactive.annotation PDAnnotationWidget]
        '[org.apache.pdfbox.pdmodel.interactive.form PDTextField]
        '[java.io File FileOutputStream])

(declare add-field!*)

(defn- field-named [doc nm]
  (.getField (.getAcroForm (.getDocumentCatalog doc)) nm))

(defn- add-field!
  "A text field `nm` at `rect`, taking its styling from `model-name`'s widget.

   /AP is left off deliberately, as everywhere else that builds a field here:
   write-fields! bakes an appearance from the value, and a shared /AP would make
   this field render whatever the model last rendered."
  [doc model-name nm ^PDRectangle rect]
  (if (field-named doc nm)
    (printf "    %s already exists, not added again%n" nm)
    (add-field!* doc model-name nm rect)))

(defn- add-field!* [doc model-name nm ^PDRectangle rect]
  (let [form (.getAcroForm (.getDocumentCatalog doc))
        model (field-named doc model-name)
        mw (first (.getWidgets model))
        page (.getPage mw)
        field (PDTextField. form)
        widget (PDAnnotationWidget.)]
    (.setPartialName field nm)
    (doseq [k [COSName/DA COSName/MK COSName/F COSName/FT]]
      (when-let [v (.getDictionaryObject (.getCOSObject mw) k)]
        (.setItem (.getCOSObject widget) k v)))
    (.setRectangle widget rect)
    (.setPage widget page)
    (.setWidgets field (java.util.ArrayList. [widget]))
    (.setAnnotations page (java.util.ArrayList.
                           (conj (vec (.getAnnotations page)) widget)))
    (.setFields form (java.util.ArrayList. (conj (vec (.getFields form)) field)))
    (printf "    added %s at x=%.1f y=%.1f w=%.1f h=%.1f%n"
            nm (.getLowerLeftX rect) (.getLowerLeftY rect)
            (.getWidth rect) (.getHeight rect))))

(defn- fix! [style f]
  (let [{:keys [file]} (get pdf/sheet-masters style)]
    (printf "style %d  %s%n" style file)
    (with-open [doc (Loader/loadPDF (.readAllBytes (.openStream (io/resource file))))]
      (f doc)
      (with-open [o (FileOutputStream. (File. (str "resources/" file)))]
        (.save doc o)))))

(fix! 3 (fn [doc]
          (add-field! doc "hp-temp" "hd" (PDRectangle. 236.0 418.0 60.0 30.0))))

(fix! 4 (fn [doc]
          (if-let [f (field-named doc "character-name-p2")]
            (do (.setPartialName f "character-name-2")
                (println "    character-name-p2 -> character-name-2"))
            (println "    character-name-p2 ABSENT, nothing renamed"))))
