;; PDF sheet mockups: fill a real sheet through the app's own export handler, draw the proposed
;; parts onto it with the sheet's own fonts, render pages to PNG. Run from a CODE worktree (it
;; needs the app on the classpath):
;;   lein run -m clojure.main <agents>/scripts/design/pdf-mockups.clj <out-dir>
;; Writes <out-dir>/shots/*.png and <out-dir>/shots.json for pdf-review-page.py.
;; Copy the SCENES section for a new design; the helpers above it are the reusable part.
;; How and why: docs/kb/mockup-review-pages.md, "PDF sheets".
(require '[clojure.string :as cstr]
         '[orcpub.pdf :as pdf]
         '[orcpub.common :as common]
         '[orcpub.dnd.e5.spell-print :as sp]
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

;; ---------------------------------------------------------------- compact spell cards

(defn page-with
  "Index of the first page of `doc` whose text holds every string in `needles` and none in
   `without`, or nil."
  [doc needles without]
  (let [stripper (org.apache.pdfbox.text.PDFTextStripper.)]
    (first (for [i (range (.getNumberOfPages doc))
                 :let [_ (.setStartPage stripper (inc i)) _ (.setEndPage stripper (inc i))
                       t (.getText stripper doc)]
                 :when (and (every? #(cstr/includes? t %) needles) (not-any? #(cstr/includes? t %) without))]
             i))))

(defn fit-pt
  "The size, from `start` down to `floor` in quarter points, at which `text` in `font` is no wider
   than `width` points; the floor when nothing larger fits."
  [font text width start floor]
  (or (first (filter #(<= (text-pt font text %) width) (range start (- floor 0.01) -0.25))) floor))

(defn ellipsize
  "`text` cut with an ellipsis to fit `width` points in `font` at `size`."
  [font text size width]
  (if (<= (text-pt font text size) width)
    text
    (loop [t text]
      (let [c (str (cstr/trimr t) "…")]
        (if (or (<= (text-pt font c size) width) (< (count t) 2)) c (recur (subs t 0 (dec (count t)))))))))

(defn rounded-rect!
  "Strokes a rectangle with rounded corners: left `x`, PDF `y` of its bottom, `w` x `h`, radius `r`."
  [cs x y w h r lw]
  (let [k (* 0.5523 r) x2 (+ x w) y2 (+ y h)]
    (.setLineWidth cs (float lw))
    (.moveTo cs (float (+ x r)) (float y))
    (.lineTo cs (float (- x2 r)) (float y))
    (.curveTo cs (float (+ (- x2 r) k)) (float y) (float x2) (float (- (+ y r) k)) (float x2) (float (+ y r)))
    (.lineTo cs (float x2) (float (- y2 r)))
    (.curveTo cs (float x2) (float (+ (- y2 r) k)) (float (+ (- x2 r) k)) (float y2) (float (- x2 r)) (float y2))
    (.lineTo cs (float (+ x r)) (float y2))
    (.curveTo cs (float (- (+ x r) k)) (float y2) (float x) (float (+ (- y2 r) k)) (float x) (float (- y2 r)))
    (.lineTo cs (float x) (float (+ y r)))
    (.curveTo cs (float x) (float (- (+ y r) k)) (float (- (+ x r) k)) (float y) (float (+ x r)) (float y))
    (.closePath cs)
    (.stroke cs)))

(defn circle-stroke!
  "Strokes a circle of radius `r` about PDF point (cx, cy)."
  [cs cx cy r]
  (let [k (* 0.5523 r)]
    (.moveTo cs (float (+ cx r)) (float cy))
    (.curveTo cs (float (+ cx r)) (float (+ cy k)) (float (+ cx k)) (float (+ cy r)) (float cx) (float (+ cy r)))
    (.curveTo cs (float (- cx k)) (float (+ cy r)) (float (- cx r)) (float (+ cy k)) (float (- cx r)) (float cy))
    (.curveTo cs (float (- cx r)) (float (- cy k)) (float (- cx k)) (float (- cy r)) (float cx) (float (- cy r)))
    (.curveTo cs (float (+ cx k)) (float (- cy r)) (float (+ cx r)) (float (- cy k)) (float (+ cx r)) (float cy))
    (.stroke cs)))

(def compact-layouts
  "Baselines in points from the card top, and the text size, per header height. At 40 the source
   shares the school row."
  {50 {:size 7.5 :title 19 :school 30 :source 38 :rule1 42 :facts 50 :rule2 53 :material 61 :text 55}
   40 {:size 7.0 :title 16 :school 26 :source 26 :rule1 30 :facts 37 :rule2 40 :material 47 :text 42}})

(defn draw-compact-card!
  "A compact spell card in grid cell (`col`, `row`) of a card page `page-h` points tall, at header
   `header` (50 or 40, compact-layouts). Title row: the spellbook's level ring, the title fitted
   between it and the crest, the spellbook's DC/attack crest. School row: sigil, italic school
   line, then (at 40) the source; use circles at the right for a limited spell (`uses` {:n
   :label}). At 50 the source has its own row. Facts row: the old cards' abbreviations with tiny
   labels, shrunk to fit. Material in italics when present, then the text; 'continued on the
   back' only when it overflows. Every line is fitted to its room, so nothing leaves the card."
  [cs fonts page-h col row header {:keys [key spell source dc attack uses]}]
  (let [{:keys [margin-x margin-y]} (draw/grid-layout 2.5 3.5)
        L (compact-layouts header) size (:size L)
        X (* 72 (+ margin-x (* 2.5 col))) T (* 72 (+ margin-y (* 3.5 row))) W 180.0 H 252.0
        py (fn [from-top] (- page-h (+ T from-top)))
        serif (:plain fonts) serif-b (:bold fonts) serif-i (:italic fonts)
        sans (draw/sans fonts :plain) sans-b (draw/sans fonts :bold)
        {spell-name :name spell-range :range :keys [level school casting-time components duration description] :as spell}
        (or spell (spells/spell-map key))
        left (+ X 11) right (- (+ X W) 11)
        crest-w 40.0
        ring-r 6.5
        ring-cx (+ left ring-r)
        title-x (+ left (* 2 ring-r) 5)
        title-size (fit-pt serif-b spell-name (- right crest-w 4 title-x) 10.5 7.0)]
    (rounded-rect! cs (+ X 5) (py (- H 5)) (- W 10) (- H 10) 8.0 0.9)
    (let [cy (+ (py (:title L)) (* 0.35 10.5))
          lv (if (zero? (or level 0)) "c" (str level))]
      (.setLineWidth cs (float 0.7))
      (circle-stroke! cs ring-cx cy ring-r)
      (draw-pt! cs serif-b 7.0 lv (- ring-cx (/ (text-pt serif-b lv 7.0) 2)) (- cy (* 0.35 7.0)) 0))
    (draw-pt! cs serif-b title-size spell-name title-x (py (:title L)) 0)
    (@#'orcpub.spellbook/crest! cs fonts dc attack (- right crest-w) (+ T (- (:title L) 11)) crest-w)
    (let [line (@#'orcpub.spellbook/school-line spell)
          line-x title-x
          line-w (text-pt serif-i line 7.0)
          uses-w (if uses (+ (text-pt sans-b (:label uses) 4.8) 4 (* (:n uses) 9)) 0)
          src (cstr/upper-case source)
          [src-text src-x src-room] (if (= header 40)
                                      [(str "·  " src) (+ line-x line-w 4) (- right crest-w 4 (+ line-x line-w 4))]
                                      [src left (- right left (if uses (+ uses-w 6) 0))])]
      (@#'orcpub.spellbook/sigil! cs school (- ring-cx 3.25) (+ T (- (:school L) 5.9)) 6.5)
      (draw-pt! cs serif-i 7.0 line line-x (py (:school L)) 0.15)
      (let [sz (fit-pt sans src-text src-room 4.8 3.6)]
        (draw-pt! cs sans sz (ellipsize sans src-text sz src-room) src-x (py (:source L)) 0.4))
      (when (and uses (= header 50))
        (let [{:keys [n label]} uses
              x0 (- right (* n 9))]
          (draw-pt! cs sans-b 4.8 label (- x0 (text-pt sans-b label 4.8) 3) (py (:source L)) 0.4)
          (.setLineWidth cs (float 0.8))
          (doseq [i (range n)]
            (circle-stroke! cs (+ x0 (* i 9) 3.5) (+ (py (:source L)) 1.7) 3.0)))))
    (doseq [k [:rule1 :rule2]]
      (.setLineWidth cs (float 0.4))
      (.moveTo cs (float left) (float (py (k L)))) (.lineTo cs (float right) (float (py (k L)))) (.stroke cs))
    (let [comps (cstr/join "," (keep (fn [[c l]] (when (get components c) l)) [[:verbal "V"] [:somatic "S"] [:material "M"]]))
          facts [["CAST" (sp/abbreviate-casting-time (first (cstr/split (str casting-time) #",")))]
                 ["RANGE" (sp/abbreviate-range (str spell-range))]
                 ["COMP" comps]
                 ["DUR" (sp/abbreviate-duration duration)]]
          width-at (fn [vs] (reduce + (map (fn [[l v]] (+ (text-pt sans-b l 4.2) 2.5 (text-pt serif v vs) 6)) facts)))
          uses-w (if (and uses (= header 40)) (+ (text-pt sans-b (:label uses) 4.2) 4 (* (:n uses) 9)) 0)
          room (- right left uses-w)
          vsize (or (first (filter #(<= (width-at %) room) (range 7.0 5.49 -0.25))) 5.5)]
      (when (and uses (= header 40))
        (let [{:keys [n label]} uses x0 (- right (* n 9))]
          (draw-pt! cs sans-b 4.2 label (- x0 (text-pt sans-b label 4.2) 3) (py (:facts L)) 0.4)
          (.setLineWidth cs (float 0.8))
          (doseq [i (range n)] (circle-stroke! cs (+ x0 (* i 9) 3.5) (+ (py (:facts L)) 2.2) 3.0))))
      (loop [x left [[label v] & more] facts]
        (when label
          (draw-pt! cs sans-b 4.2 label x (py (:facts L)) 0.45)
          (let [vx (+ x (text-pt sans-b label 4.2) 2.5)]
            (draw-pt! cs serif vsize v vx (py (:facts L)) 0)
            (recur (+ vx (text-pt serif v vsize) 6) more)))))
    ;; Measured layout check: boxes [x0 y0 x1 y1] from the card's top-left.
    (let [ts title-size
          ring-top (- (:title L) (* 0.35 10.5) ring-r)
          boxes {:ring [(- ring-cx X ring-r) ring-top (+ (- ring-cx X) ring-r) (+ ring-top (* 2 ring-r))]
                 :title [(- title-x X) (- (:title L) (* 0.72 ts)) (+ (- title-x X) (text-pt serif-b spell-name ts)) (:title L)]
                 :crest [(- right crest-w X) (- (:title L) 11) (- right X) (+ (- (:title L) 11) (/ crest-w 2))]
                 :sigil [(- ring-cx 3.25 X) (- (:school L) 5.9) (+ (- ring-cx X) 3.25) (+ (- (:school L) 5.9) 6.5)]
                 :school [(- title-x X) (- (:school L) 5.5) (+ (- title-x X) (text-pt serif-i (@#'orcpub.spellbook/school-line spell) 7.0)) (+ (:school L) 1.8)]
                 :source [(- left X) (- (:source L) 3.6) (+ (- left X) (min (- right left) (text-pt sans (cstr/upper-case source) 4.8))) (:source L)]}
          boxes (if uses
                  (let [uw (+ (text-pt sans-b (:label uses) 4.8) 4 (* (:n uses) 9))]
                    (-> boxes
                        (assoc :uses [(- right uw X) (- (:source L) 4.5) (- right X) (+ (:source L) 1.5)])
                        (assoc-in [:source 2] (min (get-in boxes [:source 2]) (- right uw 6 X)))))
                  boxes)
          frame-in 8.0
          inside? (fn [[x0 y0 x1 y1]] (and (>= x0 frame-in) (>= y0 frame-in) (<= x1 (- W frame-in))))
          overlap? (fn [[a0 b0 a1 b1] [c0 d0 c1 d1]] (and (< a0 c1) (< c0 a1) (< b0 d1) (< d0 b1)))
          names (keys boxes)]
      (doseq [[k b] boxes :when (not (inside? b))] (println "LAYOUT" spell-name k "closer than 3 pt to the frame" b))
      (doseq [a names b names :when (and (pos? (compare (name a) (name b))) (overlap? (boxes a) (boxes b)))]
        (println "LAYOUT" spell-name a "overlaps" b (boxes a) (boxes b)))
      (let [rc (/ (+ (first (:ring boxes)) (nth (:ring boxes) 2)) 2) sc (/ (+ (first (:sigil boxes)) (nth (:sigil boxes) 2)) 2)]
        (when (> (Math/abs (double (- rc sc))) 0.2) (println "LAYOUT" spell-name "ring and sigil off centre by" (- rc sc)))))
    (let [mat (get-in spell [:components :material-component])
          text-top (if mat (+ (:material L) 3) (:text L))]
      (when mat
        (let [t (common/ascii-capitalize mat)]
          (draw-pt! cs serif-i (fit-pt serif-i t (- right left) 6.5 5.0) t left (py (:material L)) 0.2)))
      (let [overflow (draw/draw-text-to-box cs (str description) serif size (/ left 72.0) (/ (py text-top) 72.0)
                                            (/ (- right left) 72.0) (/ (- H text-top 13) 72.0))]
        (when (seq overflow)
          (let [t "continued on the back"]
            (draw-pt! cs serif-i 5.5 t (- (+ X (/ W 2)) (/ (text-pt serif-i t 5.5) 2)) (py (- H 9)) 0.45)))))))

(defn compact-cards-scene
  "Today's Tiefling cards (top row), the compact card at a 50 pt header with 7.5 pt text (middle)
   and at 40 pt with 7 pt text (bottom): Thaumaturgy, Hellish Rebuke (use circles, a long source)
   and Fireball (material). Renders the card fronts page to shots/<file>."
  [file]
  (let [cha :orcpub.dnd.e5.character/cha
        cards (pdf-spec/make-spell-card-info (known "Tiefling" cha {0 [:thaumaturgy] 1 [:hellish-rebuke] 2 [:darkness]})
                                             (constantly 13) (constantly 5) false #{} {} spells/spell-map {})
        fields (merge sheet {:class-level "Fighter 5" :print-spell-cards? true :print-spell-card-dc-mod? true} cards)
        specs [{:key :thaumaturgy :source "Tiefling · Infernal Legacy" :dc 13 :attack "+5"}
               {:key :hellish-rebuke :source "Tiefling · Infernal Legacy · cast at 2nd level" :dc 13 :attack "+5"
                :uses {:n 1 :label "1 / LONG REST"}}
               {:key :fireball :source "Wizard" :dc 15 :attack "+7"}]]
    (with-open [doc (Loader/loadPDF (pdf-bytes fields))]
      (let [sp-page (page-with doc ["Rebuke"] ["reverse"])
            fonts (draw/load-fonts doc)
            ph (.getHeight (.getMediaBox (.getPage doc sp-page)))]
        (with-open [cs (PDPageContentStream. doc (.getPage doc sp-page) PDPageContentStream$AppendMode/APPEND true true)]
          (doseq [[i spec] (map-indexed vector specs)]
            (draw-compact-card! cs fonts ph i 1 50 spec))
          (let [all (vals spells/spell-map)
                widest (fn [f] (apply max-key #(count (str (f %))) all))
                stress {:name (:name (apply max-key #(text-pt (:bold fonts) (:name %) 10.5) all))
                        :level 9
                        :school (:school (apply max-key #(count (@#'orcpub.spellbook/school-line (assoc % :level 9))) all))
                        :casting-time (:casting-time (apply max-key #(count (sp/abbreviate-casting-time (first (cstr/split (str (:casting-time %)) #",")))) all))
                        :range (:range (apply max-key #(count (sp/abbreviate-range (str (:range %)))) all))
                        :duration (:duration (apply max-key #(count (str (sp/abbreviate-duration (:duration %)))) all))
                        :components {:verbal true :somatic true :material true
                                     :material-component (get-in (widest #(get-in % [:components :material-component])) [:components :material-component])}
                        :description (:description (widest :description))}]
            (println "STRESS" (pr-str (dissoc stress :description)))
            (draw-compact-card! cs fonts ph 0 2 50 {:spell stress :source "Half-Elf (Variant) · Magic Initiate (Wizard) · cast at 2nd level"
                                                    :dc 19 :attack "+11" :uses {:n 6 :label "6 / LONG REST"}})))
        (let [out (ByteArrayOutputStream.)]
          (.save doc out)
          (with-open [saved (Loader/loadPDF (.toByteArray out))]
            (.mkdirs (File. (str out-dir "/shots")))
            (ImageIO/write (render-page saved sp-page) "png" (File. (str out-dir "/shots/" file)))
            ;; The three new headers at 600 dpi, to look at alignment rather than the whole page.
            (let [{:keys [margin-x margin-y]} (draw/grid-layout 2.5 3.5)
                  big (.renderImageWithDPI (PDFRenderer. saved) sp-page 600 ImageType/RGB)
                  s (/ 600 72.0)
                  x (int (* s 72 margin-x)) y (int (* s 72 (+ margin-y 3.5))) w (int (* s 72 7.5)) h (int (* s 66))]
              (ImageIO/write (.getSubimage big x y w h) "png" (File. (str out-dir "/shots/headers-zoom.png")))
              (let [mid (.renderImageWithDPI (PDFRenderer. saved) sp-page 300 ImageType/RGB)
                    s3 (/ 300 72.0)]
                (ImageIO/write (.getSubimage mid (int (* s3 72 margin-x)) (int (* s3 72 (+ margin-y 7.0)))
                                             (int (* s3 72 2.5)) (int (* s3 72 3.5)))
                               "png" (File. (str out-dir "/shots/stress-card.png")))))
            (println "wrote" file "page" sp-page)))))))

;; ---------------------------------------------------------------- card design v2: assets, layout, check

(defn circle-d
  "SVG path of a circle of radius `r` about (cx, cy), as four cubic arcs."
  [cx cy r]
  (let [k (* 0.5523 r)]
    (format "M%s %s C%s %s %s %s %s %s C%s %s %s %s %s %s C%s %s %s %s %s %s C%s %s %s %s %s %s Z"
            (+ cx r) cy (+ cx r) (+ cy k) (+ cx k) (+ cy r) cx (+ cy r)
            (- cx k) (+ cy r) (- cx r) (+ cy k) (- cx r) cy
            (- cx r) (- cy k) (- cx k) (- cy r) cx (- cy r)
            (+ cx k) (- cy r) (+ cx r) (- cy k) (+ cx r) cy)))

(def card-icons
  "The card's icons, designed once: each an SVG path set in its own box (:w x :h points at scale
   1), a stroke width, and the point its number is centred on (:slot), all measured from the box's
   top-left. Placed only through draw-icon!, by these numbers."
  {:attack {:w 28 :h 15 :lw 0.8 :paths ["M1 1 H21 L27 7.5 L21 14 H1 L4.5 7.5 Z"] :slot [13.5 7.5]}
   :level {:w 19 :h 19 :lw 0.9 :paths [(circle-d 9.5 9.5 9.0)] :inner [(circle-d 9.5 9.5 7.6)] :slot [9.5 9.5]}
   :save {:w 20 :h 23 :lw 0.8 :paths ["M1 1 H19 V10 Q19 19.5 10 22.5 Q1 19.5 1 10 Z"] :slot [10 10.5]}
   :use {:w 7 :h 7 :lw 0.8 :paths [(circle-d 3.5 3.5 3.0)] :slot [3.5 3.5]}})

(defn draw-icon!
  "Draws icon `k` of card-icons with its box's top-left at (x, y) points from the card's top-left
   (card at X, T on a page `page-h` tall), at `scale`."
  [cs page-h X T k x y scale]
  (let [{:keys [lw paths inner]} (card-icons k)
        px #(float (+ X x (* scale %))) py #(float (- page-h (+ T y (* scale %))))]
    (doseq [[ds w] [[paths lw] [inner (* 0.4 lw)]] d ds]
      (.setLineWidth cs (float (* scale w)))
      (doseq [op (draw/svg-path-ops d)]
        (case (first op)
          :move (.moveTo cs (px (nth op 1)) (py (nth op 2)))
          :line (.lineTo cs (px (nth op 1)) (py (nth op 2)))
          :curve (.curveTo cs (px (nth op 1)) (py (nth op 2)) (px (nth op 3)) (py (nth op 4)) (px (nth op 5)) (py (nth op 6)))
          :close (.closePath cs)
          nil))
      (.stroke cs))))

(def card-w 180.0)
(def card-h 252.0)
(def content-l 11.0)
(def content-r 169.0)
(def footer-h 12.0)

(defn wrap-lines
  "`text` wrapped to `width` points in `font` at `size`, as a vector of lines."
  [font size width text]
  (wrap font size width text))

(defn card-layout
  "Every element of a spell card as measured boxes, before anything is drawn: a vector of
   {:kind :text|:icon|:rule|:legend|:body ...} with :box [x0 y0 x1 y1] in points from the card's
   top-left. `config` :band (trio centred under the title) or :beside (trio right of the title).
   Rules: the title wraps to two lines when it does not fit; use circles share the source row or
   take their own band; facts wrap to two rows; the material wraps to two lines then points to the
   back; the body leaves the footer free."
  [fonts config {:keys [spell source dc attack uses]}]
  (let [serif (:plain fonts) serif-b (:bold fonts) serif-i (:italic fonts)
        sans (draw/sans fonts :plain) sans-b (draw/sans fonts :bold)
        {spell-name :name spell-range :range :keys [level school casting-time components duration description]} spell
        W (- content-r content-l)
        out (atom [])
        add! (fn [e] (swap! out conj e) e)
        text! (fn [font size t x baseline & [grey]]
                (add! {:kind :text :font font :size size :text t :x x :y baseline :grey (or grey 0)
                       :box [x (- baseline (* 0.72 size)) (+ x (text-pt font t size)) (+ baseline (* 0.22 size))]}))
        legend! (fn [t cx rule-y]
                  (let [w (text-pt sans-b t 4.2)]
                    (add! {:kind :legend :text t :x (- cx (/ w 2)) :y (+ rule-y 1.5) :w w
                           :box [(- cx (/ w 2) 1.5) (- rule-y 2.2) (+ cx (/ w 2) 1.5) (+ rule-y 2.2)]})))
        rule! (fn [y] (add! {:kind :rule :y y :box [content-l y content-r y]}))
        trio-scale 0.85
        tw (* trio-scale (+ 28 3 19 3 20)) th (* trio-scale 23)
        trio! (fn [x y]
                (let [s trio-scale
                      parts [[:attack x (+ y (* s 4)) attack]
                             [:level (+ x (* s 31)) (+ y (* s 2)) (if (zero? (or level 0)) "c" (str level))]
                             [:save (+ x (* s 53)) y (str dc)]]]
                  (doseq [[k ix iy n] parts
                          :let [{:keys [w h slot]} (card-icons k)
                                [sx sy] slot
                                size (if (= k :attack) 7.0 8.0)
                                nw (text-pt serif-b n size)]]
                    (add! {:kind :icon :k k :x ix :y iy :scale s :box [ix iy (+ ix (* s w)) (+ iy (* s h))]})
                    (let [tx (- (+ ix (* s sx)) (/ nw 2)) ty (+ iy (* s sy) (* 0.35 size))]
                      (add! {:kind :text :font serif-b :size size :text n :x tx :y ty :grey 0 :inside k
                             :box [tx (- ty (* 0.72 size)) (+ tx nw) (+ ty (* 0.22 size))]})))
                  {:attack-cx (+ x (* s 13.5)) :level-cx (+ x (* s 40.5)) :save-cx (+ x (* s 63))}))
        title-size 10.5
        title-fits? (fn [width] (<= (text-pt serif-b spell-name title-size) width))
        y (atom 0)]
    ;; title and trio
    (case config
      :band (let [lines (if (title-fits? W) [spell-name] (wrap-lines serif-b 9.5 W spell-name))
                  size (if (= 1 (count lines)) title-size 9.5)]
              (doseq [[i l] (map-indexed vector lines)]
                (text! serif-b size l (+ content-l (/ (- W (text-pt serif-b l size)) 2)) (+ 18 (* i 11))))
              (reset! y (+ 18 (* 11 (dec (count lines))) 5))
              (let [{:keys [attack-cx level-cx save-cx]} (trio! (+ content-l (/ (- W tw) 2)) @y)
                    ry (+ @y th 4)]
                (rule! ry) (legend! "ATTACK" attack-cx ry) (legend! "LEVEL" level-cx ry) (legend! "SAVE DC" save-cx ry)
                (reset! y (+ ry 10))))
      :beside (let [room (- W tw 6)
                    lines (if (title-fits? room) [spell-name] (wrap-lines serif-b 9.5 room spell-name))
                    size (if (= 1 (count lines)) title-size 9.5)]
                (doseq [[i l] (map-indexed vector lines)]
                  (text! serif-b size l content-l (+ 16 (* i 11))))
                (let [ty 7 {:keys [attack-cx level-cx save-cx]} (trio! (- content-r tw) ty)
                      after-title (+ 16 (* 11 (dec (count lines))) 10)]
                  (reset! y after-title)
                  (let [ry (max (+ ty th 4) (+ after-title 4))]
                    (reset! y (- ry 4))
                    (reset! y {:school-y after-title :rule-y ry :cx [attack-cx level-cx save-cx]})))))
    ;; school line (sigil + italic), then the rule for :beside, then source and uses
    (let [line (@#'orcpub.spellbook/school-line spell)
          [school-y next-y] (if (map? @y)
                              (let [{:keys [school-y rule-y cx]} @y]
                                [school-y (do (rule! rule-y)
                                              (doseq [[t c] (map vector ["ATTACK" "LEVEL" "SAVE DC"] cx)] (legend! t c rule-y))
                                              (+ rule-y 10))])
                              [(+ @y 0) (+ @y 9)])
          lw (text-pt serif-i line 7.0)
          sx (if (= config :band) (+ content-l (/ (- W lw 9) 2)) content-l)]
      (add! {:kind :sigil :school school :x sx :y (- school-y 5.9) :box [sx (- school-y 5.9) (+ sx 6.5) (+ school-y 0.6)]})
      (text! serif-i 7.0 line (+ sx 9) school-y 0.15)
      (reset! y next-y))
    (let [src (cstr/upper-case source)
          src-w (text-pt sans src 4.8)
          uw (if uses (+ (text-pt sans-b (:label uses) 4.8) 4 (* (:n uses) 9)) 0)
          shared? (and uses (<= (+ src-w 6 uw) W))
          center? (= config :band)]
      (text! sans 4.8 (if (<= src-w W) src (ellipsize sans src 4.8 W))
             (if (and center? (not uses)) (+ content-l (/ (- W src-w) 2)) content-l) @y 0.4)
      (when uses
        (let [row (if shared? @y (+ @y 9))
              x0 (- content-r (* (:n uses) 9))]
          (text! sans-b 4.8 (:label uses) (- x0 (text-pt sans-b (:label uses) 4.8) 3) row 0.4)
          (doseq [i (range (:n uses))]
            (add! {:kind :icon :k :use :x (+ x0 (* i 9)) :y (- row 5.2) :scale 1.0
                   :box [(+ x0 (* i 9)) (- row 5.2) (+ x0 (* i 9) 7) (+ row 1.8)]}))
          (when-not shared? (swap! y + 9))))
      (swap! y + 11))
    ;; facts: values above a rule, labels as legends centred under them; two rows when one is too wide
    (let [comps (cstr/join ", " (keep (fn [[c l]] (when (get components c) l)) [[:verbal "V"] [:somatic "S"] [:material "M"]]))
          facts [["CAST" (sp/abbreviate-casting-time (first (cstr/split (str casting-time) #",")))]
                 ["RANGE" (sp/abbreviate-range (str spell-range))]
                 ["COMP" comps]
                 ["DUR" (str (sp/abbreviate-duration duration))]]
          vs 7.0
          cell-w (fn [[l v]] (max (text-pt serif v vs) (+ (text-pt sans-b l 4.2) 4)))
          row! (fn [fs]
                 (let [ws (map cell-w fs) gap (/ (- W (reduce + ws)) (max 1 (dec (count fs))))
                       xs (reductions + content-l (map #(+ % gap) ws))
                       ry (+ @y 4)]
                   (doseq [[[l v] x w] (map vector fs xs ws)
                           :let [cx (+ x (/ w 2))]]
                     (text! serif vs v (- cx (/ (text-pt serif v vs) 2)) @y)
                     (legend! l cx ry))
                   (rule! ry)
                   (reset! y (+ ry 10))))]
      (if (<= (reduce + (map cell-w facts)) (- W 12))
        (row! facts)
        (do (row! (take 2 facts)) (row! (drop 2 facts)))))
    ;; material, two lines at most
    (when-let [mat (get-in spell [:components :material-component])]
      (let [ls (wrap-lines serif-i 6.5 W (common/ascii-capitalize (cstr/replace mat #"\s+" " ")))
            ls (if (> (count ls) 2) [(first ls) (ellipsize serif-i (str (second ls) " … (full text on the back)") 6.5 W)] ls)]
        (doseq [l ls] (text! serif-i 6.5 l content-l @y 0.2) (swap! y + 7.5))
        (swap! y + 1.5)))
    (add! {:kind :body :y @y :box [content-l (- @y 7) content-r (- card-h footer-h 5)]})
    {:elements @out :text-top @y}))

(defn layout-problems
  "Overlaps between content boxes (rules excepted, and a number inside its own icon), and boxes
   closer than 3 pt to the frame (inset 5). Returns a vector of strings."
  [{:keys [elements]}]
  (let [content (remove #(#{:rule :body} (:kind %)) elements)
        ov? (fn [[a0 b0 a1 b1] [c0 d0 c1 d1]] (and (< a0 c1) (< c0 a1) (< b0 d1) (< d0 b1)))
        label (fn [e] (str (name (:kind e)) " " (or (:text e) (some-> (:k e) name) (:school e))))]
    (vec (concat
          (for [e content :let [[x0 y0 x1 _] (:box e)] :when (or (< x0 8) (< y0 8) (> x1 (- card-w 8)))]
            (str (label e) " is within 3 pt of the frame"))
          (for [[i a] (map-indexed vector content) b (drop (inc i) content)
                :when (and (ov? (:box a) (:box b))
                           (not (and (:inside a) (= (:inside a) (:k b))))
                           (not (and (:inside b) (= (:inside b) (:k a))))
                           (not (and (= :legend (:kind a)) (= :legend (:kind b)) false)))]
            (str (label a) " overlaps " (label b)))))))

(defn draw-card-v2!
  "Draws `layout` (card-layout) in grid cell (col, row) of a page `page-h` points tall: frame,
   elements, the body text and the footer when the text overflows. Returns the overflow lines."
  [cs fonts page-h col row {:keys [elements text-top]} description]
  (let [{:keys [margin-x margin-y]} (draw/grid-layout 2.5 3.5)
        X (* 72 (+ margin-x (* 2.5 col))) T (* 72 (+ margin-y (* 3.5 row)))
        py (fn [y] (- page-h (+ T y)))
        sans-b (draw/sans fonts :bold) serif (:plain fonts) serif-i (:italic fonts)]
    (.setNonStrokingColor cs (float 1) (float 1) (float 1))
    (.addRect cs (float (+ X 1)) (float (py (- card-h 1))) (float (- card-w 2)) (float (- card-h 2)))
    (.fill cs)
    (.setNonStrokingColor cs (float 0) (float 0) (float 0))
    (.setStrokingColor cs (float 0) (float 0) (float 0))
    (rounded-rect! cs (+ X 5) (py (- card-h 5)) (- card-w 10) (- card-h 10) 8.0 0.9)
    (doseq [e (filter #(= :rule (:kind %)) elements)]
      (.setLineWidth cs (float 0.5))
      (.moveTo cs (float (+ X content-l)) (float (py (:y e)))) (.lineTo cs (float (+ X content-r)) (float (py (:y e)))) (.stroke cs))
    (doseq [e elements]
      (case (:kind e)
        :icon (draw-icon! cs page-h X T (:k e) (:x e) (:y e) (:scale e))
        :sigil (@#'orcpub.spellbook/sigil! cs (:school e) (+ X (:x e)) (+ T (:y e)) 6.5)
        :text (draw-pt! cs (:font e) (:size e) (:text e) (+ X (:x e)) (py (:y e)) (:grey e))
        :legend (let [[x0 _ x1 _] (:box e)]
                  (.setNonStrokingColor cs (float 1) (float 1) (float 1))
                  (.addRect cs (float (+ X x0)) (float (py (+ (:y e) 1.2))) (float (- x1 x0)) (float 5.5))
                  (.fill cs)
                  (draw-pt! cs sans-b 4.2 (:text e) (+ X (:x e)) (py (:y e)) 0.45))
        nil))
    (let [over (draw/draw-text-to-box cs (str description) serif 7.5 (/ (+ X content-l) 72.0) (/ (py text-top) 72.0)
                                      (/ (- content-r content-l) 72.0) (/ (- card-h footer-h 5 text-top) 72.0))]
      (when (seq over)
        (let [t "continued on the back"]
          (draw-pt! cs serif-i 5.5 t (+ X (/ (- card-w (text-pt serif-i t 5.5)) 2)) (py (- card-h 8)) 0.45)))
      over)))

(defn icon-sheet!
  "Draws every card icon at 3x in grid cell (col, row) with its measured box dashed and its size."
  [cs fonts page-h col row]
  (let [{:keys [margin-x margin-y]} (draw/grid-layout 2.5 3.5)
        X (* 72 (+ margin-x (* 2.5 col))) T (* 72 (+ margin-y (* 3.5 row)))
        sans (draw/sans fonts :plain) serif-b (:bold fonts)]
    (draw-pt! cs serif-b 10 "Card icons" (+ X 12) (- page-h (+ T 20)) 0)
    (doseq [[i k] (map-indexed vector [:attack :level :save :use])
            :let [{:keys [w h]} (card-icons k) s 3.0 x (+ 14 (* (mod i 2) 80)) y (+ 34 (* (quot i 2) 90))]]
      (draw-icon! cs page-h X T k x y s)
      (.setLineDashPattern cs (float-array [1.5 1.5]) (float 0))
      (.setLineWidth cs (float 0.3))
      (.addRect cs (float (+ X x)) (float (- page-h (+ T y (* s h)))) (float (* s w)) (float (* s h)))
      (.stroke cs)
      (.setLineDashPattern cs (float-array []) (float 0))
      (draw-pt! cs sans 6 (str (name k) "  " w " x " h " pt") (+ X x) (- page-h (+ T y (* s h) 10)) 0.3))))

(defn fit-share
  "Share of SRD spells whose text fits a card front when the body starts `text-top` points down,
   at 7.5 pt."
  [fonts text-top]
  (let [serif (:plain fonts) leading (* 1.1 7.5)
        room (int (dec (/ (- card-h footer-h 5 text-top) leading)))
        all (vals spells/spell-map)]
    (/ (count (filter #(<= (count (draw/split-lines (str (:description %)) serif 7.5 (/ (- content-r content-l) 72.0))) room) all))
       (double (count all)))))

(defn card-v2-scene
  "One card page: row 0 config :band, row 1 config :beside (Thaumaturgy, Hellish Rebuke, Fireball),
   row 2 the stress card in each config and the icon sheet. Prints LAYOUT problems and each
   config's fit share; writes shots/<file> and shots/<file>-zoom.png (600 dpi)."
  [file]
  (let [cha :orcpub.dnd.e5.character/cha
        cards (pdf-spec/make-spell-card-info (known "Tiefling" cha {0 [:thaumaturgy]}) (constantly 13) (constantly 5)
                                             false #{} {} spells/spell-map {})
        fields (merge sheet {:class-level "Fighter 5" :print-spell-cards? true} cards)]
    (with-open [doc (Loader/loadPDF (pdf-bytes fields))]
      (let [pg (page-with doc ["Thaumaturgy"] ["reverse" "FEATURES"])
            fonts (draw/load-fonts doc)
            ph (.getHeight (.getMediaBox (.getPage doc pg)))
            all (vals spells/spell-map)
            widest (fn [f] (apply max-key #(count (str (f %))) all))
            stress {:name (:name (apply max-key #(text-pt (:bold fonts) (:name %) 10.5) all)) :level 9
                    :school (:school (apply max-key #(count (@#'orcpub.spellbook/school-line (assoc % :level 9))) all))
                    :casting-time (:casting-time (apply max-key #(count (sp/abbreviate-casting-time (first (cstr/split (str (:casting-time %)) #",")))) all))
                    :range (:range (apply max-key #(count (sp/abbreviate-range (str (:range %)))) all))
                    :duration (:duration (apply max-key #(count (str (sp/abbreviate-duration (:duration %)))) all))
                    :components {:verbal true :somatic true :material true
                                 :material-component (get-in (widest #(get-in % [:components :material-component])) [:components :material-component])}
                    :description (:description (widest :description))}
            specs [{:spell (spells/spell-map :thaumaturgy) :source "Tiefling · Infernal Legacy" :dc 13 :attack "+5"}
                   {:spell (spells/spell-map :hellish-rebuke) :source "Tiefling · Infernal Legacy · cast at 2nd level" :dc 13 :attack "+5"
                    :uses {:n 1 :label "1 / LONG REST"}}
                   {:spell (spells/spell-map :fireball) :source "Wizard" :dc 15 :attack "+7"}]
            stress-spec {:spell stress :source "Half-Elf (Variant) · Magic Initiate (Wizard) · cast at 2nd level"
                         :dc 19 :attack "+11" :uses {:n 6 :label "6 / LONG REST"}}]
        (with-open [cs (PDPageContentStream. doc (.getPage doc pg) PDPageContentStream$AppendMode/APPEND true true)]
          (doseq [[config row] [[:band 0] [:beside 1]]
                  [col spec] (map-indexed vector specs)
                  :let [l (card-layout fonts config spec)]]
            (doseq [p (layout-problems l)] (println "LAYOUT" (name config) (:name (:spell spec)) "-" p))
            (when (= col 0) (println "FIT" (name config) "body starts at" (:text-top l) "pt:" (format "%.0f%%" (* 100 (fit-share fonts (:text-top l))))))
            (draw-card-v2! cs fonts ph col row l (:description (:spell spec))))
          (doseq [[config col] [[:band 0] [:beside 1]]
                  :let [l (card-layout fonts config stress-spec)]]
            (doseq [p (layout-problems l)] (println "LAYOUT stress" (name config) "-" p))
            (draw-card-v2! cs fonts ph col 2 l (:description stress)))
          (icon-sheet! cs fonts ph 2 2))
        (let [out (ByteArrayOutputStream.)]
          (.save doc out)
          (with-open [saved (Loader/loadPDF (.toByteArray out))]
            (.mkdirs (File. (str out-dir "/shots")))
            (ImageIO/write (render-page saved pg) "png" (File. (str out-dir "/shots/" file)))
            (let [big (.renderImageWithDPI (PDFRenderer. saved) pg 300 ImageType/RGB)]
              (ImageIO/write big "png" (File. (str out-dir "/shots/" file "-300.png"))))
            (println "wrote" file "page" pg)))))))

(def shots
  [(do (card-v2-scene "cards-v2.png")
       {:id "v2" :tag "Proposal" :title "Two header configurations, the stress card in each, and the icon sheet"
        :note "Row 1: trio band. Row 2: trio beside the title. Row 3: the stress card (every field at its SRD maximum) in each, and the designed icons with their measured boxes."
        :file "cards-v2.png" :crop nil})])

(spit (str out-dir "/shots.json") (json-str shots))
(println "wrote shots.json")
(shutdown-agents)
