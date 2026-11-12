;; PDF sheet mockups: fill a real sheet through the app's own export handler, draw the proposed
;; parts onto it with the sheet's own fonts, render pages to PNG. Run from a CODE worktree (it
;; needs the app on the classpath):
;;   lein run -m clojure.main <agents>/scripts/design/pdf-mockups.clj <out-dir>
;; Writes <out-dir>/shots/*.png and <out-dir>/shots.json for pdf-review-page.py.
;; Copy the SCENES section for a new design; the helpers above it are the reusable part.
;; How and why: docs/kb/mockup-review-pages.md, "PDF sheets".
(require '[clojure.string :as cstr]
         '[orcpub.pdf :as pdf]
         '[orcpub.pdf.draw :as draw]
         '[orcpub.pdf-spec :as pdf-spec]
         '[orcpub.routes :as routes]
         '[orcpub.dnd.e5.spells :as spells])
(import '(java.io ByteArrayOutputStream File)
        '(org.apache.pdfbox Loader)
        '(org.apache.pdfbox.pdmodel PDPageContentStream PDPageContentStream$AppendMode)
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

(defn render-page
  "Page `idx` of `doc` as a BufferedImage at `dpi`."
  [doc idx]
  (.renderImageWithDPI (PDFRenderer. doc) idx dpi ImageType/RGB))

(defn ink-width
  "Points between the first and last dark pixel inside field rect [x1 y1 x2 y2] on `img` (page
   height `page-h` points), skipping the row's rule at the bottom: what a field's text measures
   as printed, whatever size the form chose for it. Nil for an empty field."
  [img page-h [x1 y1 x2 y2]]
  (let [s (/ dpi 72.0)
        px0 (int (* x1 s)) px1 (int (* x2 s))
        py0 (int (* (- page-h y2) s)) py1 (- (int (* (- page-h y1) s)) 3)
        dark (for [x (range px0 px1) y (range py0 py1)
                   :let [l (bit-and (bit-shift-right (.getRGB img x y) 8) 0xff)]
                   :when (< l 110)]
               x)]
    (when (seq dark) (/ (- (apply max dark) (apply min dark)) s))))

(defn text-pt
  "Width in points of `text` in `font` at `size`."
  [font text size]
  (* 72 (draw/string-width (str text) font size)))

(defn draw-pt!
  "Draws `text` in `font` at `size`, its baseline at `base` points, from `x` points, grey `grey`."
  [cs font size text x base grey]
  (draw/draw-text cs (str text) font size (/ x 72.0) (/ base 72.0) [grey grey grey]))

(def box-size "Side of a drawn use box, points." 6.0)

(defn draw-boxes!
  "Draws `n` use boxes from `x` points, centred on row rect [x1 y1 x2 y2]."
  [cs [_ y1 _ y2] x n]
  (let [y (+ y1 (/ (- (- y2 y1) box-size) 2))]
    (.setLineWidth cs 0.7)
    (dotimes [i n] (.addRect cs (float (+ x (* i (+ box-size 2)))) (float y) (float box-size) (float box-size)))
    (.stroke cs)))

(defn render!
  "Exports `fields`, measures the printed size of the names in `measure` (field names), calls
   `(draw cs ctx)` on the page holding `page-field`, and writes that page to shots/<file>. `ctx`:
   {:doc :fonts :rects :size}, `:size` the measured name size. Returns the size."
  [fields measure draw page-field file]
  (with-open [doc (Loader/loadPDF (pdf-bytes fields))]
    (let [rects (widget-rects doc)
          [idx _] (get rects page-field)
          fonts (draw/load-sans doc)
          plain (draw/sans fonts :plain)
          page-h (.getHeight (.getMediaBox (.getPage doc idx)))
          img (render-page doc idx)
          sizes (for [f measure
                      :let [[_ r] (get rects f) w (ink-width img page-h r)
                            unit (text-pt plain (get fields (keyword f)) 1.0)]
                      :when (and w (pos? unit))]
                  (/ w unit))
          size (if (seq sizes) (nth (sort sizes) (quot (count sizes) 2)) 8.0)]
      (with-open [cs (PDPageContentStream. doc (.getPage doc idx) PDPageContentStream$AppendMode/APPEND true true)]
        (draw cs {:doc doc :fonts fonts :rects rects :size size}))
      (.mkdirs (File. (str out-dir "/shots")))
      (ImageIO/write (render-page doc idx) "png" (File. (str out-dir "/shots/" file)))
      (println "wrote" file "page" idx "name size" (format "%.2f" (double size)))
      size)))

(defn json-str
  "`x` (maps, sequences, strings, numbers, keywords as names) as JSON text."
  [x]
  (cond (map? x) (str "{" (cstr/join "," (for [[k v] x] (str (json-str (name k)) ":" (json-str v)))) "}")
        (sequential? x) (str "[" (cstr/join "," (map json-str x)) "]")
        (string? x) (str "\"" (-> x (cstr/replace "\\" "\\\\") (cstr/replace "\"" "\\\"") (cstr/replace "\n" "\\n")) "\"")
        (nil? x) "null"
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

(defn draw-bar!
  "The app's packed-heading layout on `box`'s bar, section 1: `caption` in the narrow compartment
   as CANTRIPS is set (bold 6.5 pt, grey), `label` centred in the wide one (bold, near-black, up to
   11 pt), and `stats` above the bar at 7 pt as packed headings set theirs. Compartments are read
   the way pdf/draw-column-heading! reads them."
  [cs {:keys [doc fonts]} box caption label stats]
  (let [{:keys [narrow wide]} (@#'pdf/bar-compartments doc box "1")
        [_ hy _ hh] (pdf/spell-level-numeral-box doc box "1")
        [nx nw] narrow [wx ww] wide
        middle (+ hy (/ hh 2.0))
        bold (draw/sans fonts :bold)
        label-size (loop [sz 11.0]
                     (if (or (<= (text-pt bold label sz) (- ww 8)) (<= sz 6)) sz (recur (- sz 0.5))))]
    (draw-pt! cs bold 6.5 caption (+ nx (/ (- nw (text-pt bold caption 6.5)) 2)) (+ middle (* -0.36 6.5)) 0.45)
    (draw-pt! cs bold label-size label (+ wx (/ (- ww (text-pt bold label label-size)) 2))
              (+ middle (* -0.36 label-size)) 0.15)
    (when stats
      (draw-pt! cs bold 7.0 stats (+ wx (/ (- ww (text-pt bold stats 7.0)) 2)) (+ middle 14.5) 0.1))))

(defn traits-scene
  "A Traits & Feats box in `box` of a Wizard 5's page: the bar per draw-bar! (`bar` is [caption
   label stats]), row 1 a grey caption line, then a row a spell. Names go in the row fields so they
   print like every other row; LVL, SOURCE (`source?`), DC (`dc?`) and USES are drawn in the sheet's sans
   at the names' printed size, each column as wide as its widest cell. Writes shots/<file>."
  [box rows {:keys [dc? source?]} bar file]
  (let [fname (fn [i] (str "spells-" box "-" i "-1"))
        names (into {} (map-indexed (fn [i r] [(keyword (fname (+ 2 i))) (:spell r)]) rows))
        fields (merge {:print-character-sheet-style? 1 :character-name "Mock" :class-level "Wizard 5"
                       :player-name "Pat"}
                      (pdf-spec/spell-page-fields wizard-5 {1 4 2 3 3 2} (constantly 15) (constantly 7)
                                                  false #{} {} spells/spell-map {} 1)
                      names
                      {:spell-relabels [{:section 1 :box box :label nil}]})
        cols (cond-> [:level] source? (conj :source) dc? (conj :dc))
        captions {:spell "SPELL" :level "LVL" :source "SOURCE" :dc "DC" :uses "USES"}
        draw (fn [cs {:keys [fonts rects size] :as ctx}]
               (apply draw-bar! cs ctx box bar)
               (let [plain (draw/sans fonts :plain)
                     bold (draw/sans fonts :bold)
                     [_ [x1 _ x2 _]] (get rects (fname 2))
                     gap 7.0
                     name-w (apply max (map #(text-pt plain (:spell %) size) rows))
                     col-w (fn [c] (apply max (text-pt bold (captions c) 5.0)
                                          (map #(text-pt plain (c %) size) rows)))
                     xs (reductions + (+ x1 2 name-w gap) (map #(+ (col-w %) gap) cols))
                     x-of (zipmap cols xs)
                     uses-x (last xs)
                     base (fn [[_ y1 _ _]] (+ y1 2.4))
                     [_ r1] (get rects (fname 1))]
                 (when (> (+ uses-x (text-pt plain "at will" size)) x2)
                   (println "WARNING: columns overrun the row by" (- (+ uses-x (text-pt plain "at will" size)) x2) "pt"))
                 (doseq [[c x] (concat [[:spell (+ x1 2)]] (map vector cols xs) [[:uses uses-x]])]
                   (draw-pt! cs bold 5.0 (captions c) x (base r1) 0.45))
                 (doseq [[i row] (map-indexed vector rows)
                         :let [[_ r] (get rects (fname (+ 2 i)))]]
                   (doseq [c cols] (draw-pt! cs plain size (c row) (x-of c) (base r) 0))
                   (if (= :at-will (:uses row))
                     (draw-pt! cs plain size "at will" uses-x (base r) 0)
                     (draw-boxes! cs r uses-x (:uses row))))))]
    (render! fields (map name (keys names)) draw (fname 1) file)))

(def tiefling-rows
  [{:spell "Thaumaturgy" :level "0" :source "Tiefling" :uses :at-will :dc "CHA 13"}
   {:spell "Hellish Rebuke" :level "2nd" :source "Tiefling" :uses 1 :dc "CHA 13"}
   {:spell "Darkness" :level "2nd" :source "Tiefling" :uses 1 :dc "CHA 13"}])

(def crop-col3 [835 225 400 440])

(def shots
  [(do (traits-scene 6 tiefling-rows {} ["TRAITS" "Tiefling" "CHA  DC 13  +5"] "v1-columns-one-source.png")
       {:id "v1" :tag "Variant 1" :title "One source: the bar names it, the DC sits above"
        :note "Tiefling Wizard. The bar is used the way packed sheets already use it: TRAITS where CANTRIPS goes, the source where a class name goes, CHA DC 13 +5 above, so the rows need no source or DC column. Names print in their own row fields; the other columns are set in the sheet's font at the names' size."
        :file "v1-columns-one-source.png" :crop crop-col3})
   (do (traits-scene 6 (conj tiefling-rows
                             {:spell "Find Familiar" :level "1st" :source "Magic Initiate" :uses 1 :dc "INT 15"}
                             {:spell "Prestidigitation" :level "0" :source "Magic Initiate" :uses :at-will :dc "INT 15"})
                     {:dc? true :source? true} ["TRAITS" "Tiefling, Magic Initiate" nil] "v2-columns-two-sources.png")
       {:id "v2" :tag "Variant 2" :title "Two sources with different abilities: a DC column"
        :note "Tiefling Wizard with Magic Initiate (Wizard). The bar names both sources and carries no numbers; each row says its source and its own DC."
        :file "v2-columns-two-sources.png" :crop crop-col3})])

(spit (str out-dir "/shots.json") (json-str shots))
(println "wrote shots.json")
(shutdown-agents)
