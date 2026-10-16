(ns orcpub.sheet-geometry-test
  "Pins what the export DRAWS on the AcroForm character sheet, and where the fields sit.

   The card and spellbook baselines cover pages we build from nothing. These pages are
   template artwork we append to, and nothing covered them -- so changing a shared
   primitive moved the live sheet export unwatched. draw-text has 6 callers here."
  (:require [clojure.test :refer [deftest testing]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [orcpub.pdf-ops :as ops]
            [orcpub.pdf :as pdf])
  (:import (org.apache.pdfbox Loader)
           (java.security MessageDigest)))

;; The four things that draw here, from routes.clj's order: apply-relabel-instructions!
;; (numeral patches), annotate-spell-rows! (row marks), draw-column-heading! (packed
;; column labels), stamp-site-line! (every page). Each one is a draw-text caller.

(def ^:private casters 2)

(defn- spell-rows
  "`spells-<level>-<row>-<section>` values, the naming the templates use. Names are
   fixed strings, not generated, so a change to the spell data cannot move the baseline."
  []
  (into {}
        (for [section (range 1 (inc casters))
              level [0 1 3]
              row [1 2 5]]
          [(keyword (format "spells-%d-%d-%d" level row section))
           (format "Spell L%dR%dS%d" level row section)])))

(defn- slot-fields
  "`spell-slots-<level>-<section>` is what bar-compartments reads to find a bar at all,
   and it exists on every style. `slots-expended-*` only exists on styles 1 and 3, so
   feeding it covers BOTH of bar-compartments' branches -- the field on 1 and 3, the
   spell-row right edge fallback on 2 and 4. write-fields! reporting it dropped on 2 and
   4 is that fallback being taken, not a broken fixture."
  []
  (into {}
        (for [section (range 1 (inc casters))
              level [1 3]
              [prefix v] [["spell-slots" "4"] ["slots-expended" "2"]]]
          [(keyword (format "%s-%d-%d" prefix level section)) v])))

(def ^:private fields
  (merge
   {:character-name "Characterisation Probe"
    ;; class-level, NOT class-and-level: dumped from the templates, all four agree.
    :class-level "Wizard 7 / Cleric 3"
    :race "Half-Elf" :alignment "Neutral Good" :background "Sage"
    :ac "17" :initiative "+3" :speed "30 ft."
    :cha "14" :cha-mod "+2"
    :spellcasting-class-1 "Wizard" :spellcasting-ability-1 "INT"
    :spellcasting-class-2 "Cleric" :spellcasting-ability-2 "WIS"
    ;; Spills one or two continuation pages, which are drawn output of their own.
    ;; Kept small on purpose: 220 repeats here span 32 pages a style, 144 in all.
    :features-and-traits-2
    (str/join " " (repeat 30 "A sentence that exists only to overflow its box."))}
   (slot-fields)
   (spell-rows)))

(def ^:private relabels
  "One valid, one refused -- the refused path must not draw."
  [{:section 1 :box 3 :label "4"}
   {:section 99 :box 3 :label "nope"}])

(def ^:private headings
  [{:box 0 :section 1 :class "Wizard" :ability "INT" :dc "15" :attack "+7"}
   {:box 0 :section 2 :class "Cleric" :cantrips? false}])

(defn- annotate
  "Marks for the rows whose names spell-rows made, keyed off the printed value."
  [value]
  (when (str/starts-with? value "Spell L")
    (cond-> {}
      (str/includes? value "R1") (assoc :concentration? true)
      (str/includes? value "R2") (assoc :material "M")
      (str/includes? value "R5") (assoc :tag "ritual"))))

(defn- build-sheet
  "The export's own sequence for `style`, from routes.clj's generate-character-pdf."
  [style]
  (let [{:keys [file marks site-line prints-site-line?]} (get pdf/sheet-masters style)
        doc (with-open [in (.openStream (io/resource file))]
              (Loader/loadPDF (.readAllBytes in)))]
    (pdf/grow-spell-sections! doc casters marks)
    (pdf/add-missing-spell-pages! doc fields 9)
    (let [merged (pdf/merge-style-fields style fields)]
      (pdf/apply-relabel-instructions! doc relabels casters style)
      (pdf/reserve-annotation-columns! doc)
      (pdf/write-fields! doc (pdf/spill-overflow! doc merged) false {})
      (pdf/annotate-spell-rows! doc annotate)
      (doseq [{:keys [box section class] :as h} headings]
        (pdf/draw-column-heading! doc style box section class h)))
    (pdf/stamp-site-line! doc site-line (boolean prints-site-line?))
    doc))

(defn- widget-geometry
  "Every widget as \"name x y w h\" at 2dp, sorted. Page content streams do not carry
   unflattened field boxes, so a move that shifted fields off their artwork -- which is
   exactly what a page translate or scale does wrong -- would not show in the operators."
  [doc]
  (->> (for [i (range (.getNumberOfPages doc))
             a (.getAnnotations (.getPage doc i))
             :let [r (.getRectangle a)]
             :when r]
         (format "%d %s %.2f %.2f %.2f %.2f"
                 i
                 (or (some-> a .getCOSObject (.getNameAsString "T")) "-")
                 (.getLowerLeftX r) (.getLowerLeftY r)
                 (.getWidth r) (.getHeight r)))
       sort
       vec))

(defn- sha12 [coll]
  (let [bs (.digest (MessageDigest/getInstance "SHA-256")
                    (.getBytes (str/join "\n" coll) "UTF-8"))]
    (subs (apply str (map #(format "%02x" (bit-and % 0xff)) bs)) 0 12)))

(def ^:private baseline "test/sheet-geometry-baseline.edn")

(def ^:private built
  "Each style built once. Both tests read it, and building a sheet is seconds."
  (delay (into (sorted-map) (for [style [1 2 3 4]] [style (build-sheet style)]))))

(deftest sheet-drawing-is-unchanged
  (testing "every style's drawn operators, through the export's own order"
    (let [rows (vec (for [[style doc] @built
                          page (range (.getNumberOfPages doc))]
                      [(assoc (ops/fingerprint (ops/page-ops (.getPage doc page)))
                              :style style)
                       (format "style %d page %d" style (inc page))]))]
      (ops/check-baseline! baseline (mapv first rows) (mapv second rows) "sheet"))))

(deftest field-boxes-are-unchanged
  (testing "where the widgets sit, per style -- the operators cannot see this"
    (let [rows (vec (for [[style doc] @built
                          :let [g (widget-geometry doc)]]
                      {:style style :widgets (count g) :sha (sha12 g)}))]
      (ops/check-baseline! "test/sheet-widgets-baseline.edn"
                           rows
                           (mapv #(str "style " (:style %)) rows)
                           "sheet widgets"))))
