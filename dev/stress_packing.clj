(ns stress-packing
  "Runs a set of caster shapes through the packed spell layout on every sheet
   style, and checks that nothing is lost on the way.

   The check that matters is the last one: every spell name handed to the packer
   has to come back out of the finished document's spell row fields, as many
   times as it went in. A packed page renumbers its level boxes and moves classes
   between columns, and a spell that falls off the end of a box, or into a box
   with fewer fields than printed rows, disappears without an error -- which is
   how three templates were silently dropping spells before the row fields were
   repaired.

   Read off the fields rather than out of the page text: the form is not
   flattened, so a field's value lives in its widget's appearance stream and
   PDFTextStripper does not see it at all.

   Shapes cover a caster that fits in one page, one that spills to a second, a
   pact caster beside full casters, a class with no cantrips, a class with only
   cantrips, and a name long enough to be shortened in its column heading.

   Run: lein run -m stress-packing"
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [orcpub.common :as common]
            [orcpub.dnd.e5.spell-annotations :as ann]
            [orcpub.dnd.e5.spell-packing :as packing]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.pdf :as pdf])
  (:import [java.io File FileOutputStream]
           [javax.imageio ImageIO]
           [org.apache.pdfbox Loader]
           [org.apache.pdfbox.rendering PDFRenderer]))

(def ^:private names
  (vec (sort (map (comp :name val) spells/spell-map))))

(defn- pick
  "n distinct spell names, walking the list from `seed` so two classes in one
   shape do not get the same spells."
  [n seed]
  (mapv #(nth names (mod (+ seed (* 7 %)) (count names))) (range n)))

(defn- caster [class levels slots & {:keys [pact? dc attack]}]
  (cond-> {:class class :levels levels :slots slots
           :ability "CHA" :dc (or dc 15) :attack (or attack "+7")}
    pact? (assoc :pact? true)))

(def shapes
  "Name, and the classes to pack. Ordered from the ordinary to the punishing."
  [["one full caster"
    [(caster "Wizard" {0 (pick 5 3) 1 (pick 8 41) 2 (pick 7 91) 3 (pick 6 141)}
             {1 4 2 3 3 3})]]

   ["pact caster beside a full caster"
    [(caster "Warlock" {0 (pick 3 7) 5 (pick 10 61)} {5 2} :pact? true)
     (caster "Sorcerer" {0 (pick 5 21) 1 (pick 6 31) 2 (pick 5 51) 3 (pick 4 71)}
             {1 4 2 3 3 2})]]

   ["four classes, one with no cantrips"
    [(caster "Warlock" {0 (pick 2 3) 5 (pick 12 11)} {5 2} :pact? true)
     (caster "Sorcerer" {0 (pick 4 21) 1 (pick 3 31) 2 (pick 2 41)} {1 4 2 3})
     (caster "Paladin" {1 (pick 3 51) 2 (pick 2 61)} {1 4 2 3})
     (caster "Bard" {0 (pick 3 71) 1 (pick 4 81)} {1 4})]]

   ["a class with nothing but cantrips"
    [(caster "Warlock" {0 (pick 4 5)} {} :pact? true)
     (caster "Cleric" {0 (pick 4 25) 1 (pick 5 45)} {1 4})]]

   ["a name long enough to be shortened"
    [(caster "Eldritch Knight" {0 (pick 3 9) 1 (pick 6 29)} {1 4})
     (caster "Arcane Trickster" {0 (pick 3 49) 1 (pick 5 69)} {1 4})]]

   ["every level filled, spilling to a second page"
    [(caster "Wizard" {0 (pick 6 2) 1 (pick 12 12) 2 (pick 13 32) 3 (pick 13 52)
                       4 (pick 13 72) 5 (pick 9 92) 6 (pick 9 112) 7 (pick 9 132)
                       8 (pick 7 152) 9 (pick 7 172)}
             {1 4 2 3 3 3 4 3 5 3 6 2 7 2 8 1 9 1})
     (caster "Cleric" {0 (pick 5 202) 1 (pick 10 222) 2 (pick 10 242) 3 (pick 8 262)}
             {1 4 2 3 3 3})
     (caster "Druid" {0 (pick 4 302) 1 (pick 8 322) 2 (pick 8 342)} {1 4 2 3})]]])

(defn- spell-row-values
  "Every non-blank spells-LEVEL-ROW-SECTION value in the document."
  [doc]
  (when-let [form (.getAcroForm (.getDocumentCatalog doc))]
    (into []
          (comp (map #(vector (str (.getFullyQualifiedName %)) (str (.getValueAsString %))))
                (filter (fn [[n v]] (and (re-matches #"spells-\d+-\d+-\d+" n)
                                         (not (s/blank? v)))))
                (map second))
          (iterator-seq (.iterator (.getFieldTree form))))))

(defn- render!
  "Builds the packed PDF for one shape on one style. Returns what came out."
  [style classes out]
  (let [{:keys [file site-line prints-site-line?]} (get pdf/sheet-masters style)
        {:keys [fields relabels headings pages unplaced]} (packing/packed-fields style classes)]
    (with-open [doc (Loader/loadPDF (.readAllBytes (.openStream (io/resource file))))]
      (pdf/grow-spell-sections! doc pages :all)
      (let [[ok refused] (pdf/apply-relabel-instructions! doc relabels pages style)]
        (pdf/reserve-annotation-columns! doc)
        (let [dropped (pdf/write-fields! doc fields false {})]
          (pdf/annotate-spell-rows! doc #(some-> (get spells/spell-map (common/name-to-kw %))
                                                 ann/annotation))
          (doseq [{:keys [class box section] :as h} headings]
            (pdf/draw-column-heading! doc style box (or section 1) class h))
          (pdf/stamp-site-line! doc site-line (boolean prints-site-line?))
          (when out
            (with-open [o (FileOutputStream. (File. (str out ".pdf")))] (.save doc o))
            (let [idx (.indexOf (vec (.getPages doc))
                                (some-> (.getAcroForm (.getDocumentCatalog doc))
                                        (.getField "spells-0-1-1")
                                        .getWidgets first .getPage))]
              (when (nat-int? idx)
                (ImageIO/write (.renderImageWithDPI (PDFRenderer. doc) idx 110) "png"
                               (File. (str out ".png"))))))
          {:pages pages
           :relabels-applied ok
           :relabels-refused refused
           :headings (count headings)
           :dropped dropped
           :unplaced unplaced
           :written (spell-row-values doc)})))))

(defn -main [& args]
  (let [out-dir (or (first args) "target/stress")]
    (.mkdirs (File. out-dir))
    (println (format "%-46s %-6s %-6s %-9s %-7s %s"
                     "shape / style" "pages" "heads" "relabels" "packs?" "lost without saying so"))
    (let [failures
          (doall
           (for [[shape classes] shapes
                 style [1 2 3 4]]
             (let [out (str out-dir "/" (s/replace shape #"[^a-z]+" "-") "-style-" style)
                   {:keys [pages relabels-applied relabels-refused headings dropped
                           unplaced written]}
                   (render! style classes out)
                   wanted (mapcat (comp #(mapcat val %) :levels) classes)
                   ;; By count, not by set: two classes can know the same spell,
                   ;; and both copies have to be on the sheet.
                   have (frequencies written)
                   missing (for [[nm n] (frequencies wanted)
                                 :let [got (get have nm 0)]
                                 :when (< got n)]
                             (str nm " (" got " of " n ")"))
                   ;; A packing that cannot hold the character is allowed to
                   ;; place less, as long as it SAYS so -- pdf_spec reads the
                   ;; same report and prints a page per class instead. What is
                   ;; never allowed is a spell going missing in silence.
                   reported? (seq unplaced)
                   fits? (packing/fits? style (packing/packing-shape classes))
                   silent (when-not reported? (vec missing))]
               (println (format "%-46s %-6d %-6d %d/%-7d %-7s %s"
                                (str shape " / " style) pages headings
                                relabels-applied relabels-refused
                                (if fits? "yes" "no")
                                (cond
                                  (seq silent) (pr-str silent)
                                  (seq missing) (str "- (" (count missing)
                                                     " unplaced, reported)")
                                  :else "-")))
               (when (or (seq silent) (seq dropped) (pos? relabels-refused)
                         ;; fits? and the field count have to agree, or the
                         ;; fallback fires on the wrong characters.
                         (not= fits? (empty? missing)))
                 [shape style {:silent silent :dropped dropped
                               :refused relabels-refused
                               :fits? fits? :missing (count missing)}]))))
          failures (remove nil? failures)]
      (println)
      (if (seq failures)
        (do (println (count failures) "FAILURE(S):")
            (doseq [f failures] (println " " (pr-str f)))
            (System/exit 1))
        (println "Every shape either packed with nothing lost, or said what it could not hold.")))))
