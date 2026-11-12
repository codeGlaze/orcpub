;; PDF sheet mockups: fill a real sheet through the app's own export handler, draw the proposed
;; parts onto it, render pages to PNG. Run from a CODE worktree (it needs the app on the classpath):
;;   lein run -m clojure.main <agents>/scripts/design/pdf-mockups.clj <out-dir>
;; Writes <out-dir>/shots/*.png and <out-dir>/shots.json for pdf-review-page.py.
;; Copy the SCENES section for a new design; the helpers above it are the reusable part.
;; How and why: docs/kb/mockup-review-pages.md, "PDF sheets".
(require '[clojure.string :as cstr]
         '[orcpub.pdf-spec :as pdf-spec]
         '[orcpub.routes :as routes]
         '[orcpub.dnd.e5.spells :as spells])
(import '(java.io ByteArrayOutputStream File)
        '(org.apache.pdfbox Loader)
        '(org.apache.pdfbox.pdmodel PDPageContentStream PDPageContentStream$AppendMode)
        '(org.apache.pdfbox.pdmodel.font PDType1Font Standard14Fonts$FontName)
        '(org.apache.pdfbox.rendering PDFRenderer ImageType)
        '(javax.imageio ImageIO))

;; ---------------------------------------------------------------- helpers

(def out-dir (or (first *command-line-args*) "pdf-mockups"))
(def dpi 150)

(defn pdf-bytes
  "The export handler's PDF for `fields` (the map the builder sends), fillable: drawn headings
   find their place through the form fields, which a flattened PDF no longer has."
  [fields]
  (let [resp (routes/character-pdf-2 {:form-params {:body (pr-str (assoc fields :flatten? false))}})
        out (ByteArrayOutputStream.)]
    (.transferTo (:body resp) out)
    (.toByteArray out)))

(defn widget-rects
  "Field name -> [page-index [x1 y1 x2 y2]] (PDF points) for every widget in `doc`."
  [doc]
  (let [form (.getAcroForm (.getDocumentCatalog doc))
        pages (vec (iterator-seq (.iterator (.getPages doc))))]
    (into {}
          (for [f (iterator-seq (.iterator (.getFieldTree form)))
                w (try (.getWidgets f) (catch Exception _ []))
                :let [pg (.getPage w) idx (when pg (.indexOf pages pg)) r (.getRectangle w)]
                :when (and idx (>= idx 0))]
            [(.getFullyQualifiedName f)
             [idx [(.getLowerLeftX r) (.getLowerLeftY r) (.getUpperRightX r) (.getUpperRightY r)]]]))))

(def ^:private helv (PDType1Font. Standard14Fonts$FontName/HELVETICA))

(defn draw-text!
  "Text at x (fraction of the field's width, from its left) on field `fname`'s row."
  [cs [x1 y1 x2 _] fx text size grey]
  (.beginText cs)
  (.setFont cs helv (float size))
  (.setNonStrokingColor cs (float grey) (float grey) (float grey))
  (.newLineAtOffset cs (float (+ x1 (* fx (- x2 x1)))) (float (+ y1 2.2)))
  (.showText cs text)
  (.endText cs)
  (.setNonStrokingColor cs (float 0) (float 0) (float 0)))

(defn draw-boxes!
  "`n` small squares starting at x (fraction of the field's width) on the field's row."
  [cs [x1 y1 x2 y2] fx n]
  (let [h (- y2 y1) sz (min 6.5 (* 0.6 h)) y (+ y1 (/ (- h sz) 2))]
    (.setLineWidth cs 0.7)
    (dotimes [i n] (.addRect cs (float (+ x1 (* fx (- x2 x1)) (* i (+ sz 2)))) (float y) (float sz) (float sz)))
    (.stroke cs)))

(defn render!
  "Exports `fields`, runs `draw` ((fn [cs rects]) per page index it names) and writes the page
   holding `page-field` to shots/<file>. Returns the page index."
  [fields draws page-field file]
  (with-open [doc (Loader/loadPDF (pdf-bytes fields))]
    (let [rects (widget-rects doc)]
      (doseq [[idx f] (group-by first (for [[fname g] draws :let [[idx r] (get rects fname)] :when idx]
                                         [idx #(g % r)]))]
        (with-open [cs (PDPageContentStream. doc (.getPage doc idx) PDPageContentStream$AppendMode/APPEND true true)]
          (doseq [[_ g] f] (g cs))))
      (let [[idx _] (get rects page-field)
            img (.renderImageWithDPI (PDFRenderer. doc) idx dpi ImageType/RGB)]
        (.mkdirs (File. (str out-dir "/shots")))
        (ImageIO/write img "png" (File. (str out-dir "/shots/" file)))
        (println "wrote" file "page" idx)
        idx))))

(defn json-str
  "`x` (maps, sequences, strings, numbers, keywords as names) as JSON text."
  [x]
  (cond (map? x) (str "{" (cstr/join "," (for [[k v] x] (str (json-str (name k)) ":" (json-str v)))) "}")
        (sequential? x) (str "[" (cstr/join "," (map json-str x)) "]")
        (string? x) (str "\"" (-> x (cstr/replace "\\" "\\\\") (cstr/replace "\"" "\\\"") (cstr/replace "\n" "\\n")) "\"")
        :else (str x)))

;; ---------------------------------------------------------------- scenes

(def int* :orcpub.dnd.e5.character/int)
(defn wiz
  "A ?spells-known entry for Wizard spell `k`, cast with Intelligence."
  [k]
  {:key k :class "Wizard" :ability int*})
(def wizard-5
  {0 {1 (wiz :fire-bolt) 2 (wiz :mage-hand) 3 (wiz :light)}
   1 {1 (wiz :magic-missile) 2 (wiz :shield) 3 (wiz :mage-armor)}
   2 {1 (wiz :shatter) 2 (wiz :misty-step)}
   3 {1 (wiz :fireball) 2 (wiz :counterspell)}})

(defn traits-box
  "A Traits & Feats box in `box` of section 1: row 1 carries grey column labels, then one row a
   spell. `rows` are {:spell :level :source :uses (n, :at-will) :dc}. With `dc?` a DC column."
  [box rows dc? heading-stats]
  (let [cols (if dc? {:level 0.50 :source 0.60 :dc 0.80 :uses 0.90} {:level 0.55 :source 0.66 :uses 0.88})
        fname (fn [row] (str "spells-" box "-" row "-1"))
        label-row [(fname 1) (fn [cs r]
                               (doseq [[k t] (cond-> [[:level "LVL"] [:source "SOURCE"] [:uses "USES"]]
                                               dc? (conj [:dc "DC"]))]
                                 (draw-text! cs r (cols k) t 5.5 0.45))
                               (draw-text! cs r 0.0 "SPELL" 5.5 0.45))]
        spell-rows (map-indexed
                    (fn [i {:keys [level source uses dc]}]
                      [(fname (+ 2 i))
                       (fn [cs r]
                         (draw-text! cs r (:level cols) level 7 0)
                         (draw-text! cs r (:source cols) source 7 0)
                         (when dc? (draw-text! cs r (:dc cols) dc 7 0))
                         (if (= uses :at-will)
                           (draw-text! cs r (:uses cols) "at will" 7 0)
                           (draw-boxes! cs r (:uses cols) uses)))])
                    rows)]
    {:fields (into {} (map-indexed (fn [i {:keys [spell]}] [(keyword (fname (+ 2 i))) spell]) rows))
     :draws (cons label-row spell-rows)
     :spell-relabels [{:section 1 :box box :label nil}]
     :spell-headings [(merge {:class "Traits & Feats" :box box :section 1 :cantrips? false} heading-stats)]}))

(defn wizard-with-traits
  "Renders a level 5 Wizard's spell page with a traits-box in `box` to shots/<file>; see traits-box
   for `rows`, `dc?` and `heading-stats`. Returns the page index."
  [box rows dc? heading-stats file]
  (let [{:keys [fields draws spell-relabels spell-headings]} (traits-box box rows dc? heading-stats)
        f (merge {:print-character-sheet-style? 1 :character-name "Mock" :class-level "Wizard 5"
                  :player-name "Pat"}
                 (pdf-spec/spell-page-fields wizard-5 {1 4 2 3 3 2} (constantly 15) (constantly 7)
                                             false #{} {} spells/spell-map {} 1)
                 fields
                 {:spell-relabels spell-relabels :spell-headings spell-headings})]
    (render! f draws (str "spells-" box "-1-1") file)))

(def tiefling-rows
  [{:spell "Thaumaturgy" :level "0" :source "Tiefling" :uses :at-will :dc "CHA 13"}
   {:spell "Hellish Rebuke" :level "2nd" :source "Tiefling" :uses 1 :dc "CHA 13"}
   {:spell "Darkness" :level "2nd" :source "Tiefling" :uses 1 :dc "CHA 13"}])

(def crop-col3 [835 265 400 420])

(def shots
  [(do (wizard-with-traits 6 tiefling-rows false {:ability "CHA" :dc 13 :attack "+5"} "v1-columns-one-source.png")
       {:id "v1" :tag "Variant 1" :title "Columns, one source"
        :note "Tiefling Wizard. Grey column labels in the box's first row; the heading carries CHA DC 13 +5 because every row shares it."
        :file "v1-columns-one-source.png" :crop crop-col3})
   (do (wizard-with-traits 6 (conj tiefling-rows
                                   {:spell "Find Familiar" :level "1st" :source "Magic Initiate" :uses 1 :dc "INT 15"}
                                   {:spell "Prestidigitation" :level "0" :source "Magic Initiate" :uses :at-will :dc "INT 15"})
                           true {} "v2-columns-two-sources.png")
       {:id "v2" :tag "Variant 2" :title "Columns, two sources with different abilities"
        :note "Tiefling Wizard with Magic Initiate (Wizard). The DC column appears only when the rows do not share one ability; the heading drops its numbers."
        :file "v2-columns-two-sources.png" :crop crop-col3})])

(spit (str out-dir "/shots.json") (json-str shots))
(println "wrote shots.json")
(shutdown-agents)
