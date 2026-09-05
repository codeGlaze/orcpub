;; Fills every spell row of every style to the maximum with real spell names and
;; renders the result, so the sheets can be looked at rather than reasoned about.
;;
;;   lein with-profile init-db run -m clojure.main dev/stress_spell_sheets.clj
;;
;; Writes target/stress-style-N.pdf and target/stress-style-N.png.
;;
;; Worst case on purpose: every row of every box carries one of the longest real
;; spell names, so clipping and shrink-to-fit show up where a typical name would
;; hide them. Goes through write-fields! rather than setting values directly,
;; because shrink-single-line-to-fit! is part of what decides the answer.
;;
;; The report says, per style and per level box, the size the rows settled on and
;; how much width is left beside the longest name -- which is the room an
;; annotation column has to live in.

(require '[orcpub.pdf :as pdf]
         '[orcpub.dnd.e5.spells :as spells]
         '[clojure.java.io :as io]
         '[clojure.string :as st])
(import '[org.apache.pdfbox Loader]
        '[org.apache.pdfbox.rendering PDFRenderer]
        '[javax.imageio ImageIO]
        '[java.io FileOutputStream File])

(def longest-names
  "Real spell names, longest first. Filling with these makes the worst case the
   normal case for this run."
  (vec (reverse (sort-by count (map :name (vals spells/spell-map))))))

(defn- row-fields
  "Every spells-LEVEL-ROW-1 field in the master, with its rectangle."
  [doc]
  (let [form (.getAcroForm (.getDocumentCatalog doc))]
    (for [fld (iterator-seq (.iterator (.getFieldTree form)))
          :let [n (.getFullyQualifiedName fld)]
          :when (and n (re-matches #"spells-(\d+)-(\d+)-1" n))
          :let [[_ lvl idx] (re-matches #"spells-(\d+)-(\d+)-1" n)
                r (some-> (first (.getWidgets fld)) .getRectangle)]
          :when r]
      {:name n :level (Integer/parseInt lvl) :idx (Integer/parseInt idx)
       :w (.getWidth r) :h (.getHeight r)})))

(defn- baked-size
  "The size a filled row actually settled on, read back from its appearance."
  [doc field-name]
  (let [form (.getAcroForm (.getDocumentCatalog doc))
        fld (.getField form field-name)
        ap (some-> (first (.getWidgets fld)) .getAppearance .getNormalAppearance
                   .getAppearanceStream)]
    (when ap
      (let [b (java.io.ByteArrayOutputStream.)]
        (with-open [in (.createInputStream (.getCOSObject ap))] (.transferTo in b))
        (when-let [m (re-find #"/\S+\s+([0-9.]+)\s+Tf" (String. (.toByteArray b)))]
          (Double/parseDouble (second m)))))))

(defn stress [style]
  (let [file (:file (get pdf/sheet-masters style))
        doc (Loader/loadPDF (.readAllBytes (.openStream (io/resource file))))
        rows (vec (row-fields doc))
        ;; One long name per row, cycling so no two adjacent rows are identical.
        values (into {} (map-indexed
                         (fn [i {:keys [name]}]
                           [(keyword name) (nth longest-names (mod i (count longest-names)))])
                         rows))
        fields (merge values
                      {:character-name "Stress Test"
                       :class-level "Wizard 20"
                       :spellcasting-class-1 "Wizard"
                       :spellcasting-ability-1 "Intelligence"
                       :spell-save-dc-1 "19" :spell-attack-bonus-1 "+11"}
                      (into {} (for [l (range 1 10)]
                                 [(keyword (str "spell-slots-" l "-1")) "4"])))]
    (pdf/write-fields! doc fields false {})
    (with-open [out (FileOutputStream. (File. (str "target/stress-style-" style ".pdf")))]
      (.save doc out))
    ;; Render the page section 1's rows actually live on. Style 4's master has
    ;; TWO spell pages, so the last page belongs to section 2 and is empty here.
    ;; Rendering it looked exactly like a total failure to fill anything.
    (let [helv pdf/HELVETICA
          per-level (for [[lvl rs] (sort (group-by :level rows))]
                      (let [sizes (keep #(baked-size doc (:name %)) rs)
                            widest (apply max
                                          (for [r rs
                                                :let [sz (or (baked-size doc (:name r)) 0)
                                                      v (get values (keyword (:name r)))]]
                                            (* 72 (pdf/string-width v helv sz))))]
                        {:level lvl :rows (count rs)
                         :w (:w (first rs))
                         :size-min (when (seq sizes) (apply min sizes))
                         :size-max (when (seq sizes) (apply max sizes))
                         :widest widest
                         :left (- (:w (first rs)) widest)}))
          form (.getAcroForm (.getDocumentCatalog doc))
          page (some-> (.getField form "spells-0-1-1") .getWidgets first .getPage)
          idx (if page (.indexOf (.getPages doc) page) (dec (.getNumberOfPages doc)))
          n (.getNumberOfPages doc)]
      (ImageIO/write (.renderImageWithDPI (PDFRenderer. doc) idx 110) "png"
                     (File. (str "target/stress-style-" style ".png")))
      (printf "%n  (section 1 rows are on page %d of %d)%n" (inc idx) n)
      (printf "%nSTYLE %d  %s   %d rows over %d pages%n" style file (count rows) n)
      (printf "  lvl rows  row-w   size          widest name   left for a column%n")
      (doseq [{:keys [level rows w size-min size-max widest left]} per-level]
        (printf "   %d   %2d   %6.2f  %5.2f-%-5.2f   %8.1f      %7.1f %s%n"
                level rows w (or size-min 0) (or size-max 0) widest left
                (cond (< left 0) "*** OVERFLOWS ***"
                      (< left 20) "tight"
                      :else "")))
      (.close doc))))

(doseq [style [1 2 3 4]] (stress style))
(println "\ntarget/stress-style-{1,2,3,4}.{pdf,png}")
