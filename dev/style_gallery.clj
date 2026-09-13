;; Renders one fully-populated character on every sheet style, every page.
;;
;;   lein with-profile init-db run -m clojure.main dev/style_gallery.clj
;;
;; Writes target/gallery-style-N.pdf and target/gallery-style-N-pM.png.
;;
;; The character is dev/sample_character.clj's level 20 evoker, which fills every
;; spell row the page offers, so the spell page is seen at its worst case rather
;; than half empty. Row counts come from spell-packing/sheet-geometry so each
;; style is filled to ITS capacity -- style 4's cantrip box holds 7 where the
;; others hold 8, and filling all four from one number leaves a value with no
;; field on style 4 every run.
;;
;; Goes through the same calls as routes.clj in the same order, so what comes out
;; is what a download gives: grow, add pages, merge, spill, write, stamp.

(load-file "dev/sample_character.clj")

(require '[orcpub.pdf :as pdf]
         '[orcpub.dnd.e5.spells :as spells]
         '[orcpub.dnd.e5.spell-lists :as sl]
         '[orcpub.dnd.e5.spell-packing :as pk]
         '[orcpub.dnd.e5.spell-annotations :as spell-annotations]
         '[orcpub.common :as common]
         '[clojure.java.io :as io])
(import '[org.apache.pdfbox Loader]
        '[org.apache.pdfbox.rendering PDFRenderer]
        '[javax.imageio ImageIO]
        '[java.io File FileOutputStream])

(def fixture @#'sample-character/wizard-20)

(defn- spell-rows
  "The spell-name fields for one class, filled to `style`'s own row counts."
  [{:keys [list max-spell-level]} suffix style]
  (let [available (get sl/spell-lists list)
        geometry (get pk/sheet-geometry style)]
    (into {}
          (for [level (range 0 (inc max-spell-level))
                :let [names (sort (map #(or (:name (get spells/spell-map %)) (name %))
                                       (get available level)))]
                [idx nm] (map-indexed vector (take (nth geometry level) names))]
            [(keyword (format "spells-%d-%d-%d" level (inc idx) suffix)) nm]))))

(defn- fields-for [style]
  (let [caster (first (:casters fixture))]
    (merge (:fields fixture)
           {:spellcasting-class-1 (:name-str caster)
            :spellcasting-ability-1 (:ability caster)
            :spell-save-dc-1 (str (:dc caster))
            :spell-attack-bonus-1 (:attack caster)}
           (into {} (for [[lvl n] (:slots caster)]
                      [(keyword (str "spell-slots-" lvl "-1")) (str n)]))
           (spell-rows caster 1 style))))

(doseq [style [1 2 3 4]]
  (let [{:keys [file site-line prints-site-line?]} (get pdf/sheet-masters style)
        out (File. (format "target/gallery-style-%d.pdf" style))
        all (fields-for style)]
    (with-open [doc (Loader/loadPDF (.readAllBytes (.openStream (io/resource file))))]
      (pdf/grow-spell-sections! doc 1 :all)
      (pdf/add-missing-spell-pages! doc all)
      (let [all (pdf/merge-style-fields style all)
            _ (pdf/reserve-annotation-columns! doc)
            dropped (pdf/write-fields! doc (pdf/spill-overflow! doc all) false {})
            _ (pdf/annotate-spell-rows!
               doc #(some-> (get spells/spell-map (common/name-to-kw %))
                            spell-annotations/annotation))]
        (pdf/stamp-site-line! doc site-line (boolean prints-site-line?))
        (with-open [o (FileOutputStream. out)] (.save doc o))
        (printf "style %d: %d pages, %d fields, %d KB%s%n"
                style (.getNumberOfPages doc) (count all) (quot (.length out) 1024)
                (if (seq dropped) (format "  [%d unplaceable]" (count dropped)) ""))))
    (with-open [doc (Loader/loadPDF out)]
      (doseq [i (range (.getNumberOfPages doc))]
        (ImageIO/write (.renderImageWithDPI (PDFRenderer. doc) i 110) "png"
                       (File. (format "target/gallery-style-%d-p%d.png" style (inc i))))))))
