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
      ;; Saved and reloaded before rendering, as the export is: fonts loaded into the open
      ;; document are subset and embedded only on save.
      (let [out (ByteArrayOutputStream.)]
        (.save doc out)
        (with-open [saved (Loader/loadPDF (.toByteArray out))]
          (ImageIO/write (render-page saved idx) "png" (File. (str out-dir "/shots/" file)))))
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


(def int* :orcpub.dnd.e5.character/int)
(def wis* :orcpub.dnd.e5.character/wis)

(defn known
  "?spells-known ({level {key cfg}}) for `cls` cast with `ability`, from {level [spell-keys]}."
  [cls ability by-level]
  (into {} (for [[lvl ks] by-level]
             [lvl (into {} (for [k ks] [k {:key k :class cls :ability ability}]))])))

(def wizard-5 (known "Wizard" int* {0 [:fire-bolt :mage-hand :light] 1 [:magic-missile :shield :mage-armor]
                                    2 [:shatter :misty-step] 3 [:fireball :counterspell]}))
(def wizard-3 (known "Wizard" int* {0 [:fire-bolt :mage-hand :light] 1 [:magic-missile :shield :mage-armor]
                                    2 [:shatter :misty-step]}))
(def cleric-3 (known "Cleric" wis* {0 [:guidance :sacred-flame :light] 1 [:bless :cure-wounds :healing-word]
                                    2 [:spiritual-weapon :lesser-restoration]}))

(def sheet
  "The page-one fields every scene shares."
  {:print-character-sheet-style? 1 :character-name "Mock" :player-name "Pat"})

(def row-counts "Rows each box holds on style 1, boxes 0-9." [8 12 13 13 13 9 9 9 7 7])

(defn row-name
  "The field holding row `i` (from 1) of `box` in section 1."
  [box i]
  (str "spells-" box "-" i "-1"))

(defn traits-draw
  "A draw fn for a Traits box in `box`: draw-bar! with `bar` ([caption label stats]), row 1 a grey
   caption line, then a row a spell (names already in the row fields). LVL, SOURCE (`source?`),
   DC (`dc?`) and USES in the sheet's sans at the measured name size, each column as wide as its
   widest cell. `rows`: {:spell :level :source :uses (n or :at-will) :dc}."
  [box rows {:keys [dc? source?]} bar]
  (let [cols (cond-> [:level] source? (conj :source) dc? (conj :dc))
        captions {:spell "SPELL" :level "LVL" :source "SOURCE" :dc "DC" :uses "USES"}]
    (fn [cs {:keys [fonts rects size] :as ctx}]
      (apply draw-bar! cs ctx box bar)
      (let [plain (draw/sans fonts :plain)
            bold (draw/sans fonts :bold)
            [_ [x1 _ x2 _]] (get rects (row-name box 2))
            gap 7.0
            name-w (apply max (map #(text-pt plain (:spell %) size) rows))
            col-w (fn [c] (apply max (text-pt bold (captions c) 5.0) (map #(text-pt plain (c %) size) rows)))
            xs (reductions + (+ x1 2 name-w gap) (map #(+ (col-w %) gap) cols))
            x-of (zipmap cols xs)
            uses-x (last xs)
            base (fn [[_ y1 _ _]] (+ y1 2.4))
            [_ r1] (get rects (row-name box 1))]
        (when (> (+ uses-x (text-pt plain "at will" size)) x2)
          (println "WARNING: columns overrun the row by" (- (+ uses-x (text-pt plain "at will" size)) x2) "pt"))
        (doseq [[c x] (concat [[:spell (+ x1 2)]] (map vector cols xs) [[:uses uses-x]])]
          (draw-pt! cs bold 5.0 (captions c) x (base r1) 0.45))
        (doseq [[i row] (map-indexed vector rows) :let [[_ r] (get rects (row-name box (+ 2 i)))]]
          (doseq [c cols] (draw-pt! cs plain size (c row) (x-of c) (base r) 0))
          (if (= :at-will (:uses row))
            (draw-pt! cs plain size "at will" uses-x (base r) 0)
            (draw-boxes! cs r uses-x (:uses row))))))))

(defn traits-fields
  "Row fields for a Traits box in `box`: the spell names from row 2."
  [box rows]
  (into {} (map-indexed (fn [i r] [(keyword (row-name box (+ 2 i))) (:spell r)]) rows)))

(def tiefling-rows
  [{:spell "Thaumaturgy" :level "0" :source "Tiefling" :uses :at-will :dc "CHA 13"}
   {:spell "Hellish Rebuke" :level "2nd" :source "Tiefling" :uses 1 :dc "CHA 13"}
   {:spell "Darkness" :level "2nd" :source "Tiefling" :uses 1 :dc "CHA 13"}])

(def initiate-rows
  [{:spell "Find Familiar" :level "1st" :source "Magic Initiate" :uses 1 :dc "INT 15"}
   {:spell "Prestidigitation" :level "0" :source "Magic Initiate" :uses :at-will :dc "INT 15"}])

(def v1-bar ["TRAITS" "Tiefling" "CHA  DC 13  +5"])
(def v2-bar ["TRAITS" "Tiefling, Magic Initiate" nil])

(defn per-class-scene
  "Wizard 5, per-class layout, a Traits box in box 6. Writes shots/<file>."
  [rows opts bar file]
  (render! (merge sheet {:class-level "Wizard 5"}
                  (pdf-spec/spell-page-fields wizard-5 {1 4 2 3 3 2} (constantly 15) (constantly 7)
                                              false #{} {} spells/spell-map {} 1)
                  (traits-fields 6 rows)
                  {:spell-relabels [{:section 1 :box 6 :label nil}]})
           (map name (keys (traits-fields 6 rows))) (traits-draw 6 rows opts bar) (row-name 6 1) file))

(defn packed-scene
  "Wizard 3 / Cleric 3 packed by the app's own packing, a Traits box in the first free level box
   that holds it. Writes shots/<file>; returns the box used."
  [rows opts bar file]
  (let [dc (fn [a] (if (= a wis*) 13 13))
        classes (pdf-spec/packing-classes (merge-with merge wizard-3 cleric-3) spells/spell-map
                                          {1 4 2 3} {} dc (constantly 5))
        packed (pdf-spec/packed-spellcasting-fields classes 1)
        used (set (keep #(some->> (name %) (re-find #"^spells-(\d)-\d+-1$") second Integer/parseInt)
                        (keys packed)))
        box (first (for [b [6 7 8 9 3 4 5 1 2] :when (and (not (used b)) (> (row-counts b) (count rows)))] b))]
    (render! (merge sheet {:class-level "Wizard 3 / Cleric 3"} packed (traits-fields box rows)
                    {:spell-relabels (conj (vec (:spell-relabels packed)) {:section 1 :box box :label nil})})
             (map name (keys (traits-fields box rows))) (traits-draw box rows opts bar) (row-name box 1) file)
    box))

(defn wrap
  "`text` broken into lines no wider than `width` points in `font` at `size`."
  [font size width text]
  (reduce (fn [lines word]
            (let [cur (peek lines) cand (if (cstr/blank? cur) word (str cur " " word))]
              (if (or (cstr/blank? cur) (<= (text-pt font cand size) width))
                (conj (pop lines) cand)
                (conj lines word))))
          [""] (cstr/split (cstr/replace (str text) #"\s+" " ") #" ")))

(defn write-up-lines
  "Spell `k` written out for a box: `lead` (or nil) and a line of casting facts, then its text
   wrapped to `width` points in `font` at `size`, cut to `rows` lines with a pointer to the card."
  [k lead font size width rows]
  (let [{:keys [casting-time range components duration description]} (spells/spell-map k)
        facts (cstr/join " · " (remove cstr/blank? [lead (first (cstr/split (str casting-time) #","))
                                                    (str range)
                                                    (cstr/join ", " (keep (fn [[c l]] (when (get components c) l))
                                                                          [[:verbal "V"] [:somatic "S"] [:material "M"]]))
                                                    (if (map? duration) "Concentration" (str duration))]))
        lines (into (wrap font size width facts) (wrap font size width description))]
    (if (> (count lines) rows) (conj (vec (take (dec rows) lines)) "… full text on its spell card") lines)))

(defn draw-write-up!
  "Draws spell `k` written out down `box`'s rows, every line drawn at `size` so all share one
   size (a row field would size each line on its own). `lead` starts the facts line."
  [cs {:keys [fonts rects size]} box k lead]
  (let [plain (draw/sans fonts :plain)
        [_ [x1 _ x2 _]] (get rects (row-name box 1))
        lines (write-up-lines k lead plain size (- x2 x1 6) (row-counts box))]
    (doseq [[i line] (map-indexed vector lines)
            :let [[_ [rx ry _ _]] (get rects (row-name box (inc i)))]]
      (draw-pt! cs plain size line (+ rx 2) (+ ry 2.4) 0))))

(defn non-caster-scene
  "Tiefling Champion Fighter 5 (no Spellcasting feature): Thaumaturgy written out in the cantrips
   box under the app's own heading, Hellish Rebuke and Darkness in level boxes, the Traits box at
   the top of the middle column, every other numeral blanked. Writes shots/<file>."
  [file]
  (let [traits-box 3
        fields (merge sheet {:class-level "Fighter 5"
                             ;; What the builder sends today for a page of race spells; the
                             ;; export adds a spell page per spellcasting-class-N.
                             :spellcasting-class-1 "Tiefling"}
                      (traits-fields traits-box tiefling-rows)
                      {:spell-relabels (vec (for [b (range 1 10)] {:section 1 :box b :label nil}))
                       :spell-headings [{:class "Thaumaturgy" :box 0 :section 1 :cantrips? true}]})
        draw (fn [cs ctx]
               ((traits-draw traits-box tiefling-rows {} v1-bar) cs ctx)
               (draw-write-up! cs ctx 0 :thaumaturgy nil)
               (apply draw-bar! cs ctx 1 ["1ST" "Hellish Rebuke" nil])
               (draw-write-up! cs ctx 1 :hellish-rebuke "2nd level, 1/long rest, CHA DC 13")
               (apply draw-bar! cs ctx 2 ["2ND" "Darkness" "Tiefling  CHA  DC 13"])
               (draw-write-up! cs ctx 2 :darkness "1/long rest"))]
    (render! fields (map name (keys (traits-fields traits-box tiefling-rows))) draw (row-name traits-box 1) file)))

(defn features-scene
  "Tiefling Wizard 5's Features & Traits page with plain spell lines under Bonus Actions and
   Reactions. Writes shots/<file>."
  [file]
  (let [text (str "Tiefling, Wizard 5\n\n"
                  "----------Bonus Actions----------\n"
                  "Spells: Misty Step\n\n"
                  "-------------Reactions-------------\n"
                  "Spells: Counterspell, Hellish Rebuke, Shield\n\n"
                  "-----------Other Traits------------\n"
                  "Darkvision: 60 ft.\n"
                  "Hellish Resistance: resistance to fire damage.\n"
                  "Infernal Legacy: Thaumaturgy, Hellish Rebuke and Darkness (spell page, Traits).\n"
                  "Arcane Recovery: regain spell slots on a short rest, once a day.\n"
                  "Arcane Tradition (Evocation): Sculpt Spells.")]
    (render! (merge sheet {:class-level "Wizard 5" :features-and-traits-2 text}) [] (fn [_ _]) "features-and-traits-2" file)))

(def crop-col3 [835 225 400 440])

(def shots
  [(do (per-class-scene tiefling-rows {} v1-bar "v1-one-source.png")
       {:id "v1" :tag "One source: Variant 1" :title "Tiefling Wizard: the bar carries source and DC"
        :note "Rows show spell, level and uses. The plan: Variant 1 whenever every row shares one source, with a PDF option to always use Variant 2."
        :file "v1-one-source.png" :crop crop-col3})
   (do (per-class-scene (into tiefling-rows initiate-rows) {:dc? true :source? true} v2-bar "v2-two-sources.png")
       {:id "v2" :tag "Several sources: Variant 2" :title "Tiefling Wizard with Magic Initiate"
        :note "Each row says its source and its own DC; the bar names the sources."
        :file "v2-two-sources.png" :crop crop-col3})
   (let [box (packed-scene tiefling-rows {} v1-bar "packed-two-classes.png")]
     {:id "packed" :tag "Combined page" :title "Wizard 3 / Cleric 3 packed, plus the Traits box"
      :note (str "The app's own packing lays out both classes; the Traits box takes the first free level box (box " box "), not the cantrips box, its numeral blanked. Tap \"Show the whole page\" for the full sheet.")
      :file "packed-two-classes.png" :crop nil})
   (do (non-caster-scene "non-caster-spell-page.png")
       {:id "nc" :tag "No Spellcasting feature" :title "Tiefling Champion Fighter: the Traits box, then each spell written out"
        :note "The Traits box sits at the top of the middle column. Each spell is written out in a box of its own, its bar naming it (the cantrips box uses the app's own heading). Text past a box's rows ends with a pointer to the spell card."
        :file "non-caster-spell-page.png" :crop nil})
   (do (features-scene "features-spell-lines.png")
       {:id "feat" :tag "Every character" :title "Features page: spells you can cast as a bonus action or reaction"
        :note "Plain names, from each spell's casting time; the spell page has the details and the use boxes."
        :file "features-spell-lines.png" :crop [0 0 1275 620]})])

(spit (str out-dir "/shots.json") (json-str shots))
(println "wrote shots.json")
(shutdown-agents)
