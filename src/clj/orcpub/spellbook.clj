(ns orcpub.spellbook
  "Spellbook pages after the sheet: each class's spells as a book (two columns, full text), a
   ledger (a row a spell) or a prep sheet (a box to tick per spell). Pieces are measured, then
   placed by `paginate`'s keep rules; `units` says what each heading keeps with it.
   Coordinates are POINTS measured DOWN from the page top; `py` converts to PDF space."
  (:require [clojure.string :as s]
            [orcpub.common :as common]
            [orcpub.pdf :as pdf]
            [orcpub.dnd.e5.emblems :as emblems]
            [orcpub.dnd.e5.spell-annotations :as ann])
  (:import (java.awt.geom PathIterator)
           (org.apache.pdfbox.pdmodel PDPage PDPageContentStream)))

;; ─── Page geometry, in points ────────────────────────────────────────────────

(def page-w 612.0)
(def page-h 792.0)
(def ^:private margin 36.0)
(def ^:private body-top 56.0)
(def ^:private body-bottom (- page-h 46.0))
(def ^:private gutter 16.0)
(def ^:private tab-room
  "What the inset tabs take from every line: 0.35in, about 5% of the measure."
  25.0)

(def ^:private ink 0.08)
(def ^:private ink-2 0.3)
(def ^:private hair 0.78)

(defn- py [ty] (float (- page-h ty)))

(defn body-width [tabs] (- page-w (* 2 margin) (if (= :inset tabs) tab-room 0)))

(defn capacity
  "The height spells are laid into on one page."
  []
  (- body-bottom body-top))

;; ─── Text ────────────────────────────────────────────────────────────────────

(defn- clean
  "Text the embedded fonts can show: pdf's WinAnsi coercion, which also turns curly quotes
   straight and drops what Vollkorn has no glyph for. SRD lists bullet their items, and a
   bullet is outside that set, so it becomes a middle dot rather than a question mark."
  [v]
  (or (pdf/normalize-text (some-> v str (s/replace "\u2022" "\u00B7"))) ""))

(defn- width [font size text] (* 72.0 (pdf/string-width (clean text) font size)))

(defn- wrap
  "`text` broken into lines of at most `w` points, hard breaks kept."
  [font size text w]
  (into []
        (mapcat #(if (s/blank? %) [] (pdf/split-lines (s/trim %) font size (/ w 72.0))))
        (s/split-lines (clean text))))

(defn- clip
  "`text` cut at a word boundary to fit `w` points, with an ellipsis when cut."
  [font size text w]
  (let [t (clean text)]
    (if (<= (width font size t) w)
      t
      (loop [words (s/split t #"\s+")]
        (let [c (str (s/join " " words) "...")]
          (if (or (<= (width font size c) w) (<= (count words) 1))
            c
            (recur (butlast words))))))))

;; Vollkorn's default figures are old-style, so "15" reads "I5" and "1st" reads "Ist" in a
;; heading. Its lining figures are alternates (`one.lf`) that a PDF content stream cannot
;; select, so headings and numbers are drawn from the glyph outlines instead. Running text
;; keeps the old-style figures, which suit it, and stays selectable.

(def ^:private ttf
  (memoize (fn [font] (.getTrueTypeFont (.getDescendantFont font)))))

(def ^:private digit-names
  {\0 "zero" \1 "one" \2 "two" \3 "three" \4 "four" \5 "five" \6 "six" \7 "seven" \8 "eight" \9 "nine"})

(defn- gid [tt ch]
  (if-let [n (digit-names ch)]
    (let [g (.nameToGID tt (str n ".lf"))] (if (pos? g) g (.getGlyphId (.getUnicodeCmapLookup tt) (int ch))))
    (.getGlyphId (.getUnicodeCmapLookup tt) (int ch))))

(defn- lining-width [font size t spacing]
  (let [tt (ttf font) sc (/ size (.getUnitsPerEm tt)) t (clean t)]
    (+ (reduce + (map #(* sc (.getAdvanceWidth tt (gid tt %))) t))
       (* spacing (max 0 (dec (count t)))))))

(defn- outline-text!
  "`t` drawn as filled glyph outlines with lining figures, baseline `ty` from the page top."
  [cs font size x ty t spacing]
  (let [tt (ttf font) sc (/ size (.getUnitsPerEm tt)) glyphs (.getGlyph tt) base (py ty)
        coords (double-array 6)]
    (reduce
     (fn [gx ch]
       (let [g (gid tt ch)
             X #(float (+ gx (* sc %))) Y #(float (+ base (* sc %)))]
         (when-let [glyph (.getGlyph glyphs g)]
           (let [it (.getPathIterator (.getPath glyph) nil)]
             (loop [cx 0.0 cy 0.0]
               (when-not (.isDone it)
                 (let [seg (.currentSegment it coords)
                       [a b c d e f] (vec coords)]
                   (.next it)
                   (condp = seg
                     PathIterator/SEG_MOVETO (do (.moveTo cs (X a) (Y b)) (recur a b))
                     PathIterator/SEG_LINETO (do (.lineTo cs (X a) (Y b)) (recur a b))
                     PathIterator/SEG_QUADTO (let [c1x (+ cx (* 2/3 (- a cx))) c1y (+ cy (* 2/3 (- b cy)))
                                                   c2x (+ c (* 2/3 (- a c))) c2y (+ d (* 2/3 (- b d)))]
                                               (.curveTo cs (X c1x) (Y c1y) (X c2x) (Y c2y) (X c) (Y d))
                                               (recur c d))
                     PathIterator/SEG_CUBICTO (do (.curveTo cs (X a) (Y b) (X c) (Y d) (X e) (Y f)) (recur e f))
                     PathIterator/SEG_CLOSE (do (.closePath cs) (recur cx cy))))))
             (.fill cs)))
         (+ gx (* sc (.getAdvanceWidth tt g)) (or spacing 0))))
     (double x)
     (clean t))))

(defn- gray! [cs g]
  (.setNonStrokingColor cs (float g) (float g) (float g))
  (.setStrokingColor cs (float g) (float g) (float g)))

(defn- text!
  "Draws `t` with its baseline `ty` points from the page top. `spacing` is extra
   points between letters, for the spaced capitals of the headings."
  [cs font size x ty t & [{:keys [color spacing lining]}]]
  (let [t (clean t)]
    (when (and (seq t) lining)
      (gray! cs (or color ink))
      (outline-text! cs font size x ty t spacing))
    (when (and (seq t) (not lining))
      (.beginText cs)
      (.setFont cs font (float size))
      (when spacing (.setCharacterSpacing cs (float spacing)))
      (gray! cs (or color ink))
      (.newLineAtOffset cs (float x) (py ty))
      (.showText cs t)
      (when spacing (.setCharacterSpacing cs (float 0)))
      (.endText cs))))

(defn- spaced-width [font size t spacing]
  (+ (width font size t) (* spacing (max 0 (dec (count (clean t)))))))

(defn- centred!
  "Centred on `cx`, in lining figures: everything centred here is a number or a heading."
  [cs font size cx ty t & [opts]]
  (text! cs font size (- cx (/ (lining-width font size t (or (:spacing opts) 0)) 2)) ty t (assoc opts :lining true)))

;; ─── Line art ────────────────────────────────────────────────────────────────

(def ^:private bezier-k 0.5522847)

(defn- circle-path! [cs cx cy r]
  (let [k (* r bezier-k) x (float cx) y (py cy)]
    (.moveTo cs (float (+ cx r)) y)
    (.curveTo cs (float (+ cx r)) (py (- cy k)) (float (+ cx k)) (py (- cy r)) x (py (- cy r)))
    (.curveTo cs (float (- cx k)) (py (- cy r)) (float (- cx r)) (py (- cy k)) (float (- cx r)) y)
    (.curveTo cs (float (- cx r)) (py (+ cy k)) (float (- cx k)) (py (+ cy r)) x (py (+ cy r)))
    (.curveTo cs (float (+ cx k)) (py (+ cy r)) (float (+ cx r)) (py (+ cy k)) (float (+ cx r)) y)
    (.closePath cs)))

(defn- circle! [cs cx cy r lw & [fill?]]
  (.setLineWidth cs (float lw))
  (circle-path! cs cx cy r)
  (if fill? (.fill cs) (.stroke cs)))

(defn- polyline! [cs pts lw & [{:keys [close? fill?]}]]
  (let [[[x y] & more] pts]
    (.setLineWidth cs (float lw))
    (.moveTo cs (float x) (py y))
    (doseq [[a b] more] (.lineTo cs (float a) (py b)))
    (cond fill? (do (.closePath cs) (.fill cs))
          close? (.closeAndStroke cs)
          :else (.stroke cs))))

(defn- line! [cs x1 y1 x2 y2 lw]
  (polyline! cs [[x1 y1] [x2 y2]] lw))

(defn- svg-d!
  "Strokes (or fills) an SVG path `d` drawn in a box whose origin lands at (x, ty) and
   whose units are `unit` points each."
  [cs d x ty unit lw & [fill?]]
  (.setLineWidth cs (float lw))
  (doseq [op (pdf/svg-path-ops d)
          :let [px #(float (+ x (* unit %)))
                pyy #(py (+ ty (* unit %)))]]
    (case (first op)
      :move (.moveTo cs (px (nth op 1)) (pyy (nth op 2)))
      :line (.lineTo cs (px (nth op 1)) (pyy (nth op 2)))
      :curve (.curveTo cs (px (nth op 1)) (pyy (nth op 2)) (px (nth op 3)) (pyy (nth op 4))
                       (px (nth op 5)) (pyy (nth op 6)))
      :close (.closePath cs)
      nil))
  (if fill? (.fill cs) (.stroke cs)))

(defn- polar [cx cy r a] [(+ cx (* r (Math/cos a))) (+ cy (* r (Math/sin a)))])

(def ^:private up (- (/ Math/PI 2)))

(defn- ring-pts [cx cy f n]
  (for [i (range (inc n)) :let [a (+ up (* 2 Math/PI (/ i (double n))))]]
    (polar cx cy (f a) a)))

(defn- leaf!
  "A small pointed leaf of length `len` from (x, y), turned `deg` degrees."
  [cs x y len deg]
  (let [t (Math/toRadians deg)
        rot (fn [[a b]] [(+ x (- (* a (Math/cos t)) (* b (Math/sin t))))
                         (+ y (* a (Math/sin t)) (* b (Math/cos t)))])
        w (* len 0.35)
        [p0 c1 tip c2] (map rot [[0 0] [(/ len 2) (- w)] [len 0] [(/ len 2) w]])]
    (.moveTo cs (float (first p0)) (py (second p0)))
    (.curveTo cs (float (first c1)) (py (second c1)) (float (first c1)) (py (second c1))
              (float (first tip)) (py (second tip)))
    (.curveTo cs (float (first c2)) (py (second c2)) (float (first c2)) (py (second c2))
              (float (first p0)) (py (second p0)))
    (.fill cs)))

(defn- ring-theme!
  "The class's mark on the emblem's ring, drawn about (cx, cy) at `s` points per mock unit
   (the emblem is 30 units across). Returns the radius the level ticks reach to."
  [cs class-kw cx cy s level]
  (let [outer (* 13.6 s) inner (* 11.2 s)
        plain-outer #(circle! cs cx cy outer (* 0.55 s))
        plain-inner #(circle! cs cx cy inner (* 0.3 s))]
    (case class-kw
      :bard (let [n (* (max 1 level) (max 1 (Math/round (/ 16.0 (max 1 level)))))]
              (polyline! cs (ring-pts cx cy #(+ outer (* 0.45 s (Math/cos (* n (- % up))))) 360)
                         (* 0.55 s))
              (plain-inner)
              (+ outer (* 0.45 s)))
      :cleric (do (plain-outer)
                  (doseq [i (range 28) :let [[x y] (polar cx cy inner (* 2 Math/PI (/ i 28.0)))]]
                    (circle! cs x y (* 0.42 s) 0 true))
                  outer)
      :druid (do (plain-outer) (plain-inner)
                 (doseq [i (range 10)
                         :let [a (+ (* 2 Math/PI (/ i 10.0)) (/ Math/PI 10))
                               [x y] (polar cx cy outer a)
                               d (+ (Math/toDegrees a) 90)]
                         t [-28 28]]
                   (leaf! cs x y (* 2.3 s) (+ d t)))
                 outer)
      :paladin (do (circle! cs cx cy outer (* 1.1 s))
                   (circle! cs cx cy (* 14.55 s) (* 0.3 s))
                   (plain-inner)
                   outer)
      :ranger (do (plain-outer) (plain-inner)
                  (doseq [i (range 4) :let [a (+ up (* i (/ Math/PI 2)))]]
                    (polyline! cs [(polar cx cy (* 14.95 s) a) (polar cx cy (* 13.4 s) (- a 0.09))
                                   (polar cx cy (* 13.4 s) (+ a 0.09))] 0 {:fill? true}))
                  outer)
      :sorcerer (do (polyline! cs (ring-pts cx cy #(+ (* 13.3 s) (* 1.35 s (Math/pow (Math/abs (Math/sin (* 9 %))) 4))) 540)
                               (* 0.55 s))
                    (plain-inner)
                    outer)
      :warlock (do (plain-outer) (plain-inner)
                   (doseq [i (range 12) :let [a (+ (* 2 Math/PI (/ i 12.0)) (/ Math/PI 12))]]
                     (polyline! cs [(polar cx cy (* 9.8 s) a) (polar cx cy (* 11.25 s) (- a 0.09))
                                    (polar cx cy (* 11.25 s) (+ a 0.09))] 0 {:fill? true}))
                   outer)
      :wizard (do (plain-outer)
                  (.setLineDashPattern cs (float-array (map #(float (* s %)) [2.2 0.7 0.6 0.7 1.2 0.7])) (float 0))
                  (circle! cs cx cy inner (* 0.55 s))
                  (.setLineDashPattern cs (float-array []) (float 0))
                  outer)
      (do (plain-outer) (plain-inner) outer))))

(defn- emblem!
  "A class emblem `size` points across, its top-left at (x, ty): the class's ring, one tick
   per class level, and its icon in the middle."
  [cs img class-kw icon level x ty size]
  (let [s (/ size 30.0) cx (+ x (/ size 2)) cy (+ ty (/ size 2))]
    (gray! cs ink)
    (let [reach (ring-theme! cs class-kw cx cy s (or level 0))]
      (when (and level (pos? level))
        (doseq [t (range level) :let [a (+ up (* 2 Math/PI (/ t (double level))))]]
          (line! cs (first (polar cx cy (* 11.2 s) a)) (second (polar cx cy (* 11.2 s) a))
                 (first (polar cx cy reach a)) (second (polar cx cy reach a)) (* 0.9 s)))))
    (let [icon-size (* size 0.5)]
      (pdf/draw-svg-icon! cs img (str "emblems/" icon)
                          (/ (- cx (/ icon-size 2)) 72.0) (/ (- cy (/ icon-size 2)) 72.0)
                          (/ icon-size 72.0) [ink ink ink] 1.0))))

(def ^:private sigils
  "A small mark per school, as SVG paths in a 10-unit box."
  {"abjuration" ["M5 1 L8.5 3 L8.5 7 L5 9 L1.5 7 L1.5 3 Z"]
   "conjuration" ["M1 5 A2.8 2.8 0 1 0 6.6 5 A2.8 2.8 0 1 0 1 5 Z"
                  "M3.4 5 A2.8 2.8 0 1 0 9 5 A2.8 2.8 0 1 0 3.4 5 Z"]
   "divination" ["M1 5 Q5 1 9 5 Q5 9 1 5 Z" [:dot 5 5 1.2]]
   "enchantment" ["M5 5 m0 -.8 a.8 .8 0 1 1 -.8 .8 a1.8 1.8 0 1 1 1.8 1.8 a2.9 2.9 0 1 1 2.9 -2.9"]
   "evocation" (vec (for [t (range 8)
                          :let [a (* t (/ Math/PI 4))
                                r1 (if (odd? t) 1.6 1.1) r2 (if (odd? t) 3.4 4.3)]]
                      (format "M%.2f %.2f L%.2f %.2f" (+ 5 (* r1 (Math/cos a))) (+ 5 (* r1 (Math/sin a)))
                              (+ 5 (* r2 (Math/cos a))) (+ 5 (* r2 (Math/sin a))))))
   "illusion" ["M3.6 1.5 L6 5 L3.6 8.5 L1.2 5 Z" "M6.4 1.5 L8.8 5 L6.4 8.5 L4 5 Z"]
   "necromancy" ["M6.5 1.4 A3.8 3.8 0 1 0 6.5 8.6 A2.9 2.9 0 1 1 6.5 1.4 Z"]
   "transmutation" ["M1 5 A4 4 0 1 0 9 5 A4 4 0 1 0 1 5 Z" "M5 1.6 L8 6.8 L2 6.8 Z"]})

(defn- sigil!
  "The school's mark, `size` points square, its top-left at (x, ty)."
  [cs school x ty size]
  (let [u (/ size 10.0)]
    (gray! cs ink)
    (doseq [p (get sigils (common/ascii-lower-case (str school)))]
      (if (vector? p)
        (let [[_ cx cy r] p] (circle! cs (+ x (* u cx)) (+ ty (* u cy)) (* u r) 0 true))
        (svg-d! cs p x ty u (* 0.7 u))))))

(defn- level-ring!
  "A level heading's circle, `size` points across: one arc per spell slot, the level in the
   middle; cantrips get an open ring."
  [cs fonts level slots x ty size]
  (let [s (/ size 15.0) cx (+ x (/ size 2)) cy (+ ty (/ size 2)) r (* 6.2 s)
        arc (fn [a0 a1] (for [i (range 25) :let [a (+ a0 (* (- a1 a0) (/ i 24.0)))]] (polar cx cy r a)))]
    (gray! cs ink)
    (if (or (zero? level) (not (pos? (or slots 0))))
      (polyline! cs (arc (+ up 0.5) (+ up (* 2 Math/PI) -0.5)) (* 0.55 s))
      (let [gap 0.32 seg (/ (* 2 Math/PI) slots)]
        (doseq [t (range slots) :let [a0 (+ up (* t seg) (/ gap 2))]]
          (polyline! cs (arc a0 (+ a0 (- seg gap))) (* 1.0 s)))))
    (centred! cs (:bold fonts) (* 6 s) cx (+ cy (* 2.1 s)) (if (zero? level) "c" (str level)))))

(def ^:private crest-paths
  {:plaque "M33 8.5 H60 L69 18 L60 27.5 H33 L37.5 18 Z"
   :plaque-inner "M35.4 10.3 H59.2 L66.6 18 L59.2 25.7 H35.4"
   :shield "M2 2.5 H31 V15 Q31 28.5 16.5 34.5 Q2 28.5 2 15 Z"
   :shield-inner "M4.2 4.7 H28.8 V15 Q28.8 26.8 16.5 32.1 Q4.2 26.8 4.2 15 Z"})

(defn- crest!
  "Save DC on a shield, spell attack on an arrowhead nested into it; `w` points wide (the
   crest is twice as wide as tall), top-left at (x, ty)."
  [cs fonts dc attack x ty w]
  (let [u (/ w 72.0) at (fn [cx] (+ x (* u cx)))]
    (gray! cs ink)
    (svg-d! cs (:plaque crest-paths) x ty u (* 0.7 u))
    (svg-d! cs (:plaque-inner crest-paths) x ty u (* 0.3 u))
    (svg-d! cs (:shield crest-paths) x ty u (* 0.9 u))
    (svg-d! cs (:shield-inner crest-paths) x ty u (* 0.3 u))
    (centred! cs (:bold fonts) (* 12 u) (at 16.5) (+ ty (* u 18.6)) (str dc))
    (centred! cs (:plain fonts) (* 3.6 u) (at 16.5) (+ ty (* u 24.8)) "SAVE DC" {:spacing (* 0.4 u)})
    (centred! cs (:bold fonts) (* 10.5 u) (at 51) (+ ty (* u 19.8)) (str attack))
    (centred! cs (:plain fonts) (* 3.2 u) (at 51) (+ ty (* u 24.4)) "ATTACK" {:spacing (* 0.4 u)})))

(defn- folio! [cs fonts cx cy n]
  (gray! cs ink)
  (circle! cs cx cy 8.6 0.5)
  (.setLineDashPattern cs (float-array [0.8 0.9]) (float 0))
  (circle! cs cx cy 7.0 0.3)
  (.setLineDashPattern cs (float-array []) (float 0))
  (centred! cs (:bold fonts) 7.5 cx (+ cy 2.6) (str n)))

;; ─── Spell text ──────────────────────────────────────────────────────────────

(defn- level-word [level] (if (zero? level) "Cantrip" (str (common/ordinal level))))

(defn- school-line [{:keys [level school]}]
  (let [school (s/capitalize (str (or school "")))]
    (if (zero? (or level 0))
      (str school " cantrip")
      (str (common/ordinal level) "-level " (common/ascii-lower-case school)))))

(defn- components-str [{:keys [verbal somatic material]}]
  (s/join " " (cond-> [] verbal (conj "V") somatic (conj "S") material (conj "M"))))

(defn- short-time
  "The cards' abbreviation, first clause only: \"1 reaction, which you take when...\"
   is a sentence, not a casting time."
  [t]
  (pdf/abbreviate-casting-time (first (s/split (str t) #","))))

(defn- short-duration [d] (pdf/abbreviate-duration (str d)))

(defn- short-range [r] (pdf/abbreviate-range (str r)))

(defn marks
  "The small italic notes after a spell's name: concentration, ritual, a costly material."
  [spell]
  (let [a (ann/annotation spell)]
    (cond-> []
      (:concentration? a) (conj "conc.")
      (:ritual spell) (conj "ritual")
      (:material a) (conj (:material a)))))

(def ^:private telling
  "Words that mark the sentence saying what a spell DOES rather than how it looks."
  #"(?i)\d+d\d+|saving throw|hit points?|damage|advantage|disadvantage|speed|resistance|restrain|frighten|charm|paraly|invisible|teleport|heal|regain")

(defn summary
  "One sentence on what `spell` does, for the ledger and the prep sheet, which have no room
   for the full text. SRD descriptions often open with how the spell looks, so the first
   sentence with numbers or a game term wins; failing that, the first sentence."
  [spell]
  (let [sentences (->> (s/split (clean (:description spell)) #"(?<=[.!?])\s+")
                       ;; List items open with a bullet, which reads as a stray dot alone.
                       (map #(s/trim (s/replace % #"^[\s\u00B7]+" "")))
                       (remove s/blank?)
                       (remove #(re-find #"(?i)^at higher levels" %)))]
    (or (first (filter #(re-find telling %) sentences))
        (first sentences)
        "")))

;; ─── Units: what the paginator places ────────────────────────────────────────
;; Each is {:kind :h :draw (fn [cs x ty w]) ...}; :full? units span the page, the rest sit
;; in a column. :keep is how many following spells must land with the unit.

(def ^:private sizes
  {:name 10.0 :school 7.2 :stat 7.2 :desc 8.4 :desc-lead 10.2 :stat-lead 8.8
   :ledger 7.4 :ledger-lead 8.8})

(defn- name-line! [cs fonts img spell order x ty w size]
  (let [x (if (= :alpha order)
            (do (gray! cs ink)
                (circle! cs (+ x 3.8) (- ty 3.3) 3.6 0.4)
                (centred! cs (:bold fonts) 4.6 (+ x 3.8) (- ty 1.7) (if (zero? (:level spell 0)) "c" (str (:level spell))))
                (+ x 10))
            x)
        nm (clean (:name spell))
        nm-w (width (:bold fonts) size nm)
        notes (s/join " " (marks spell))]
    (text! cs (:bold fonts) size x ty nm)
    (when (seq notes)
      (text! cs (:italic fonts) (* size 0.62) (+ x nm-w 3) ty notes {:color ink-2}))))

(def ^:private higher-levels #"^At Higher Levels[.:]")

(defn- book-unit [fonts img spell order w]
  (let [;; The lead is set in bold italic, which runs wider than the roman it was
        ;; measured in, so its paragraph wraps a little short.
        lead-w (- (width (:bold-italic fonts) (:desc sizes) "At Higher Levels.")
                  (width (:plain fonts) (:desc sizes) "At Higher Levels."))
        desc-lines (into [] (mapcat #(wrap (:plain fonts) (:desc sizes) %
                                           (if (re-find higher-levels %) (- w lead-w 1) w)))
                         (s/split-lines (str (:description spell))))
        stat (s/join "  ·  " (remove s/blank? [(:casting-time spell) (:range spell)
                                                     (components-str (:components spell))
                                                     (:duration spell)]))
        stat-lines (wrap (:plain fonts) (:stat sizes) stat w)
        head-h (+ 3 12 (* (:stat-lead sizes) (+ 1 (count stat-lines))) 1.5)
        draw-head (fn [cs x ty]
                    (name-line! cs fonts img spell order x (+ ty 11) w (:name sizes))
                    (sigil! cs (:school spell) x (+ ty 14.5) 7)
                    (text! cs (:italic fonts) (:school sizes) (+ x 9.5) (+ ty 21) (school-line spell) {:color ink-2 :lining true})
                    (doseq [[i l] (map-indexed vector stat-lines)]
                      (text! cs (:plain fonts) (:stat sizes) x (+ ty 21 (* (:stat-lead sizes) (inc i))) l)))]
    {:kind :spell :spell spell :head-h head-h :lines desc-lines
     :line-h (:desc-lead sizes) :draw-head draw-head}))

(defn- finish-book-unit
  "A book entry (or a piece of one, when a spell is split across columns) as a unit."
  [{:keys [spell head-h lines line-h draw-head] :as u} part]
  (let [cont? (pos? part)
        hh (if cont? 13 head-h)]
    (assoc u
           :h (+ hh (* line-h (count lines)) 5)
           :draw (fn [cs fonts x ty w]
                   (if cont?
                     (text! cs (:bold-italic fonts) 8.4 x (+ ty 10) (str (:name spell) ", continued") {:color ink-2})
                     (draw-head cs x ty))
                   (doseq [[i l] (map-indexed vector lines)
                           :let [lty (+ ty hh (* line-h (inc i)) -2)]]
                     (if-let [lead (re-find higher-levels l)]
                       (do
                         (text! cs (:bold-italic fonts) (:desc sizes) x lty lead)
                         (text! cs (:plain fonts) (:desc sizes) (+ x (width (:bold-italic fonts) (:desc sizes) lead)) lty
                                (subs l (count lead))))
                       (text! cs (:plain fonts) (:desc sizes) x lty l)))
                   (gray! cs hair)
                   (line! cs x (+ ty hh (* line-h (count lines)) 4) (+ x w) (+ ty hh (* line-h (count lines)) 4) 0.3)))))

(defn- split-book-unit
  "A book entry taller than a column, cut at line boundaries into pieces that fit."
  [{:keys [head-h line-h lines] :as u} cap]
  (if (<= (+ head-h (* line-h (count lines)) 5) cap)
    [(finish-book-unit u 0)]
    (let [first-n (max 1 (int (/ (- cap head-h 5) line-h)))
          rest-n (max 1 (int (/ (- cap 13 5) line-h)))
          chunks (cons (take first-n lines) (partition-all rest-n (drop first-n lines)))]
      (vec (map-indexed (fn [i c] (finish-book-unit (assoc u :lines (vec c)) i)) chunks)))))

(def ^:private ledger-cols
  "Column x offsets as fractions of the measure: Level, Spell, Time, Range, Comp., Lasts,
   What it does."
  [0 0.085 0.31 0.405 0.5 0.565 0.655])

(defn- ledger-unit [fonts spell w]
  (let [xs (mapv #(* w %) ledger-cols)
        what-w (- w (peek xs))
        f (:plain fonts) sz (:ledger sizes) lead (:ledger-lead sizes)
        what (take 2 (wrap f sz (summary spell) what-w))
        what (if (< (count (wrap f sz (summary spell) what-w)) 3)
               what
               [(first what) (clip f sz (str (second what) " " (s/join " " (drop 2 (wrap f sz (summary spell) what-w)))) what-w)])
        name-w (- (xs 2) (xs 1) 4)
        nm (clip (:bold fonts) sz (:name spell) (- name-w 9))
        h (+ 3.5 (* lead (max 1 (count what))))]
    {:kind :spell :spell spell :h h
     :draw (fn [cs fonts x ty _w]
             (let [base (+ ty lead)]
               (text! cs (:italic fonts) sz (+ x (xs 0)) base (level-word (:level spell 0)) {:color ink-2 :lining true})
               (sigil! cs (:school spell) (+ x (xs 1)) (- base 6) 6)
               (text! cs (:bold fonts) sz (+ x (xs 1) 8) base nm)
               (let [notes (s/join " " (marks spell))]
                 (when (seq notes)
                   (text! cs (:italic fonts) (* sz 0.7) (+ x (xs 1) 8 (width (:bold fonts) sz nm) 2) base notes {:color ink-2})))
               (text! cs f sz (+ x (xs 2)) base (clip f sz (short-time (:casting-time spell)) (- (xs 3) (xs 2) 3)))
               (text! cs f sz (+ x (xs 3)) base (clip f sz (short-range (:range spell)) (- (xs 4) (xs 3) 3)))
               (text! cs f sz (+ x (xs 4)) base (components-str (:components spell)))
               (text! cs f sz (+ x (xs 5)) base (clip f sz (short-duration (:duration spell)) (- (xs 6) (xs 5) 3)))
               (doseq [[i l] (map-indexed vector what)]
                 (text! cs f sz (+ x (xs 6)) (+ base (* i lead)) l {:color ink-2}))
               (gray! cs hair)
               (line! cs x (+ ty h) (+ x _w) (+ ty h) 0.3)))}))

(defn- ledger-head-unit [fonts w]
  (let [xs (mapv #(* w %) ledger-cols)]
    {:kind :colhead :full? true :h 13
     :draw (fn [cs fonts x ty w]
             (doseq [[cx t] (map vector xs ["LEVEL" "SPELL" "TIME" "RANGE" "COMP." "LASTS" "WHAT IT DOES"])]
               (text! cs (:plain fonts) 5.6 (+ x cx) (+ ty 9) t {:color ink-2 :spacing 0.7}))
             (gray! cs ink)
             (line! cs x (+ ty 11.5) (+ x w) (+ ty 11.5) 0.5))}))

(defn- prep-unit [fonts spell order w]
  (let [f (:plain fonts) sz 7.6
        stat (s/join " · " (remove s/blank? [(short-time (:casting-time spell)) (short-range (:range spell))
                                                   (components-str (:components spell)) (short-duration (:duration spell))]))
        stat-w (min (* 0.34 w) (+ 2 (width f 6.8 stat)))
        text-w (- w 14 stat-w 8)
        what (clip f 6.8 (summary spell) text-w)
        h 22]
    {:kind :spell :spell spell :h h
     :draw (fn [cs fonts x ty w]
             (gray! cs ink)
             (when (pos? (:level spell 0))
               (.setLineWidth cs (float 0.5))
               (.addRect cs (float x) (py (+ ty 11.5)) (float 7) (float 7))
               (.stroke cs))
             (sigil! cs (:school spell) (+ x 13) (+ ty 4.6) 6.5)
             (name-line! cs fonts nil spell order (+ x 22) (+ ty 10.5) text-w 9.0)
             (text! cs f 6.8 (+ x 22) (+ ty 18.5) what {:color ink-2})
             (text! cs f 6.8 (- (+ x w) (width f 6.8 stat)) (+ ty 10.5) stat {:color ink-2})
             (gray! cs hair)
             (line! cs x (+ ty h) (+ x w) (+ ty h) 0.3))}))

(defn- lvh-unit
  "A level heading: the slot circle, the level in spaced capitals and how many slots.
   `pact-level` is set for a pact caster, whose few slots are all cast at that level and
   serve every spell below it."
  [fonts level slots pact-level]
  {:kind :lvh :keep 2 :h 22 :level level :slots slots :pact-level pact-level
   :draw (fn [cs fonts x ty w]
           (let [label (if (zero? level) "CANTRIPS" (s/upper-case (str (common/ordinal level) " level")))
                 n-slots (str slots (when pact-level " pact") (if (= 1 slots) " slot" " slots"))
                 sub (cond (zero? level) "at will"
                           (not (pos? (or slots 0))) "no slots yet"
                           (and pact-level (not= pact-level level)) (str n-slots ", cast at " (common/ordinal pact-level))
                           :else n-slots)
                 lw (max (lining-width (:bold fonts) 6.8 label 1.2) (lining-width (:italic fonts) 5.8 sub 0))
                 block (+ 17 3 lw)
                 x0 (+ x (/ (- w block) 2))
                 mid (+ ty 13)]
             (gray! cs ink)
             (line! cs x mid (- x0 5) mid 0.5)
             (line! cs (+ x0 block 5) mid (+ x w) mid 0.5)
             (level-ring! cs fonts level slots x0 (+ ty 4.5) 17)
             (text! cs (:bold fonts) 6.8 (+ x0 20) (+ ty 12.4) label {:spacing 1.2 :lining true})
             (text! cs (:italic fonts) 5.8 (+ x0 20) (+ ty 19) sub {:color ink-2 :lining true})))})

(defn- chap-unit
  "A class's chapter head: emblem, name and level, casting ability, and the DC crest. The
   continued form at the top of a later page is smaller and carries no crest."
  [fonts img {:keys [class level ability dc attack class-kw icon]} cont?]
  {:kind :chap :full? true :h (if cont? 30 50) :cont? cont?
   :draw (fn [cs fonts x ty w]
           (let [e (if cont? 20 36)
                 nm-size (if cont? 13 20)
                 nm-x (+ x e 7)
                 nm-base (+ ty (if cont? 15 22))
                 nm-w (width (:bold-italic fonts) nm-size class)
                 rule-y (+ ty (if cont? 26 45))]
             (emblem! cs img class-kw icon level x (+ ty 1) e)
             (text! cs (:bold-italic fonts) nm-size nm-x nm-base class)
             (text! cs (:plain fonts) (if cont? 6.4 8) (+ nm-x nm-w 5) nm-base
                    (str (when level (str "LEVEL " level)) (when cont? (if level ", CONTINUED" "CONTINUED")))
                    {:spacing 0.9 :lining true})
             (when (and ability (not cont?))
               (text! cs (:plain fonts) 7.2 nm-x (+ nm-base 12) (str "Spellcasting ability " ability) {:color ink-2}))
             (when (and dc (not cont?))
               (crest! cs fonts dc attack (- (+ x w) 76) (+ ty 3) 76))
             (gray! cs ink)
             (line! cs x rule-y (+ x w) rule-y 1.2)))})

(defn- pool-unit
  "The prep sheet's slot pool, printed once at the top: one pip per slot to mark off,
   wrapping onto further lines inside the box when one is not wide enough."
  [fonts slots pact-slots shared? w]
  (let [label (if shared? "SPELL SLOTS, SHARED" "SPELL SLOTS")
        x1 (+ 6 (lining-width (:bold fonts) 6.2 label 1) 10)
        groups (for [[lvl n prefix] (concat (for [[l n] (sort slots) :when (pos? n)] [l n ""])
                                            (for [[l n] (sort pact-slots) :when (pos? n)] [l n "pact "]))
                     :let [t (str prefix (common/ordinal lvl))
                           tw (lining-width (:plain fonts) 7 t 0)]]
                 {:t t :tw tw :n n :gw (+ tw 5 (* n 7))})
        ;; [line dx group] for each group, measured from the box's left edge.
        placed (first (reduce (fn [[out line dx] g]
                                (if (and (> dx x1) (> (+ dx (:gw g)) (- w 6)))
                                  [(conj out [(inc line) x1 g]) (inc line) (+ x1 (:gw g) 9)]
                                  [(conj out [line dx g]) line (+ dx (:gw g) 9)]))
                              [[] 0 x1] groups))
        lines (inc (reduce max 0 (map first placed)))
        box-h (+ 16 (* 11 (dec lines)))]
    {:kind :pool :full? true :h (+ box-h 8)
     :draw (fn [cs fonts x ty w]
             (gray! cs ink)
             (.setLineWidth cs (float 0.5))
             (.addRect cs (float x) (py (+ ty 2 box-h)) (float w) (float box-h))
             (.stroke cs)
             (text! cs (:bold fonts) 6.2 (+ x 6) (+ ty 12.4) label {:spacing 1 :lining true})
             (doseq [[line dx {:keys [t tw n]}] placed
                     :let [lty (+ ty (* 11 line))]]
               (text! cs (:plain fonts) 7 (+ x dx) (+ lty 12.6) t {:lining true})
               (doseq [i (range n)]
                 (circle! cs (+ x dx tw 5 (* i 7)) (+ lty 10) 2.4 0.5))))}))

;; ─── Which spells, in what order ─────────────────────────────────────────────

(defn spell-sort-key
  "Level then name, or name alone for :alpha. #520: cards came out in the order the spells
   were picked."
  [order spell]
  (let [nm (common/ascii-lower-case (str (:name spell)))]
    (if (= :alpha order) [nm] [(or (:level spell) 0) nm])))

(defn class-spells
  "The spells printed under `class-name`, in `order`. `spells-known` is keyed by level, each
   value a sequence of {:key :class ...} configs; `spells-map` resolves a key to its spell."
  [spells-known spells-map class-name order]
  (->> (vals spells-known)
       (apply concat)
       (filter #(= class-name (:class %)))
       (keep #(when-let [sp (get spells-map (:key %))]
                (cond-> sp
                  (:qualifier %) (update :name str " (" (:qualifier %) ")"))))
       (reduce (fn [[seen out] sp] (if (seen (:name sp)) [seen out] [(conj seen (:name sp)) (conj out sp)]))
               [#{} []])
       second
       (sort-by (partial spell-sort-key order))))

(defn units
  "Everything the pages hold, in order, each measured for the column it will sit in."
  [fonts img {:keys [classes slots pact-slots layout order tabs]} spells-known spells-map]
  (let [w (body-width tabs)
        col-w (if (= :book layout) (/ (- w gutter) 2) w)
        ;; A heading never ends a column alone. In two columns one spell under it is
        ;; enough, since the next sits beside it rather than over the page; and a chapter
        ;; head keeps the same, so what it promises the heading below can keep.
        keep-n (if (= :book layout) 1 2)]
    (vec
     (concat
      (when (= :prep layout)
        [(pool-unit fonts slots pact-slots (> (count (remove :pact? classes)) 1) w)])
      (mapcat
       (fn [ci {:keys [class pact?] :as k}]
         (let [spells (class-spells spells-known spells-map class order)
               pact-level (when (and pact? (seq pact-slots)) (apply max (keys pact-slots)))
               ;; Pact slots exist only at the pact level, so a pact caster's lower levels
               ;; draw on those rather than reading "no slots yet".
               slot-of (fn [lvl] (if pact-level (get pact-slots pact-level 0) (get slots lvl 0)))]
           (when (seq spells)
             (concat
              [(assoc (chap-unit fonts img k false) :cls ci :keep keep-n)]
              (when (= :ledger layout) [(assoc (ledger-head-unit fonts w) :cls ci)])
              (loop [[sp & more] spells last-level nil out []]
                (if-not sp
                  out
                  (let [lvl (or (:level sp) 0)
                        out (if (and (= :level order) (not= lvl last-level))
                              (conj out (assoc (lvh-unit fonts lvl (slot-of lvl) pact-level) :cls ci :keep keep-n))
                              out)
                        made (case layout
                               :ledger [(ledger-unit fonts sp w)]
                               :prep [(prep-unit fonts sp order w)]
                               ;; Cut short enough that a continued page's 30pt head and a
                               ;; 22pt level heading still fit above the first piece.
                               (split-book-unit (book-unit fonts img sp order col-w) (- (capacity) 30 22 5)))]
                    (recur more lvl (into out (map #(assoc % :cls ci) made))))))))))
       (range) classes)))))

;; ─── Pagination ──────────────────────────────────────────────────────────────

(defn- need
  "Height unit `i` needs free before it goes down: itself and what it keeps with it. In two
   columns a full-width head's followers sit in a column, so the first row is what counts."
  [us i]
  (let [u (us i)]
    (if-not (:keep u)
      (:h u)
      (loop [j (inc i) h (:h u) spells 0]
        (let [v (get us j)]
          (cond
            (or (nil? v) (>= spells (:keep u)) (= :chap (:kind v)) (= :pool (:kind v))) h
            (= :spell (:kind v)) (recur (inc j) (+ h (:h v)) (inc spells))
            :else (recur (inc j) (+ h (:h v)) spells)))))))

(defn- balance
  "Splits a block's items between two columns as evenly as their order allows, never
   leaving a level heading at the foot of the first nor making a column taller than
   `limit`. With no such split, the columns as they were filled, `as-filled`, stand."
  [items limit as-filled]
  (let [hs (mapv :h items) total (reduce + hs)
        options (for [k (range 1 (inc (count items)))
                      :when (not= :lvh (:kind (nth items (dec k))))
                      :let [a (reduce + (subvec hs 0 k))]
                      :when (<= (max a (- total a)) limit)]
                  [(max a (- total a)) k])]
    (if-let [k (second (first (sort options)))]
      [(vec (take k items)) (vec (drop k items))]
      as-filled)))

(defn paginate
  "Places `us` on pages. Returns [{:blocks [...] :classes #{} :levels #{} :names [...]}],
   each block either {:full unit} or {:cols [[unit ...] [unit ...]]}. `two-col?` lays
   column units side by side; `class-break` :page starts each later class on a fresh page.
   `cont-units` gives the units that head a page continuing class `ci`."
  [us {:keys [two-col? class-break cap cont-units]}]
  (let [us (vec us)
        col-total (fn [c] (reduce + (map :h c)))]
    (loop [i 0
           pages []
           page {:blocks [] :used 0 :classes #{} :levels #{} :names []}
           cols nil          ;; open column block: {:cols [[] []] :col 0 :top used}
           current-cls nil]
      (let [close-cols (fn [page cols]
                         (if-not cols
                           page
                           ;; Re-split the whole block, not just the first column: a block
                           ;; that spilled into its second column is still uneven, and the
                           ;; next class can only run on below the taller of the two.
                           (let [[a b] (if two-col?
                                         (balance (concat (first (:cols cols)) (second (:cols cols)))
                                                  (- cap (:top cols)) (:cols cols))
                                         [(first (:cols cols)) []])]
                             (-> page
                                 (update :blocks conj {:cols [a b]})
                                 (assoc :used (+ (:top cols) (max (col-total a) (col-total b))))))))
            new-page (fn [pages page cls]
                       (let [pages (conj pages page)
                             fresh {:blocks [] :used 0 :classes #{} :levels #{} :names []}]
                         [pages (reduce (fn [p u] (-> p (update :blocks conj {:full u}) (update :used + (:h u))))
                                        fresh
                                        (when cls (cont-units cls)))]))]
        (if (>= i (count us))
          (let [page (close-cols page cols)]
            (vec (remove #(empty? (:blocks %)) (conj pages page))))
          (let [u (us i)
                note (fn [p] (cond-> p
                               (= :spell (:kind u)) (-> (update :classes conj (:cls u))
                                                        (update :levels conj (get-in u [:spell :level] 0))
                                                        (update :names conj (get-in u [:spell :name])))))
                n (need us i)]
            (if (:full? u)
              (let [page (close-cols page cols)
                    later-class? (and (= :chap (:kind u)) (some? current-cls) (seq (:blocks page)))
                    fits? (and (<= (+ (:used page) n) cap)
                               (not (and later-class? (= :page class-break))))
                    [pages page] (if (or fits? (empty? (:blocks page)))
                                   [pages page]
                                   (new-page pages page (when-not (= :chap (:kind u)) current-cls)))]
                (recur (inc i) pages
                       (-> page (update :blocks conj {:full u}) (update :used + (:h u)) note)
                       nil
                       (if (= :chap (:kind u)) (:cls u) current-cls)))
              (let [cols (or cols {:cols [[] []] :col 0 :top (:used page)})
                    room (- cap (:top cols) (col-total (get-in cols [:cols (:col cols)])))
                    in-col (fn [cols] (update-in cols [:cols (:col cols)] conj u))]
                (cond
                  ;; Nothing placed can do better than a fresh page, so an oversize piece
                  ;; goes down there rather than breaking page after page.
                  (or (<= n room) (and (empty? (get-in cols [:cols (:col cols)])) (empty? (:names page))))
                  (recur (inc i) pages (note page) (in-col cols) current-cls)

                  (and two-col? (zero? (:col cols)))
                  (recur i pages page (assoc cols :col 1) current-cls)

                  :else
                  (let [page (cond-> (assoc page :used cap)
                               (some seq (:cols cols)) (update :blocks conj {:cols (:cols cols)}))
                        [pages page] (new-page pages page current-cls)]
                    (recur i pages page nil current-cls)))))))))))

;; ─── Drawing ─────────────────────────────────────────────────────────────────

(defn- running-head! [cs fonts page {:keys [classes tabs]} max-level w]
  (let [x margin ty 30
        stats (for [ci (sort (:classes page)) :let [k (get classes ci)] :when (:dc k)]
                (str (s/upper-case (:class k)) " DC " (:dc k) "  ATTACK " (:attack k)))
        names (:names page)
        guide (cond (empty? names) ""
                    (= (first names) (peek names)) (first names)
                    :else (str (first names) " to " (peek names)))]
    (let [stats-end (reduce (fn [px t] (text! cs (:plain fonts) 6.4 px ty t {:spacing 0.6 :lining true})
                              (+ px (lining-width (:plain fonts) 6.4 t 0.6) 14))
                            x stats)
          n (inc max-level) bw 10 gap 2 total (- (* n (+ bw gap)) gap)
          ;; Centred unless the classes' numbers reach that far; then just past them.
          x0 (max (- (+ x (/ w 2)) (/ total 2)) stats-end)
          free-from (if (= :head tabs) (+ x0 total 10) stats-end)
          room (- (+ x w) free-from)
          guide (if (< room 40) "" (clip (:italic fonts) 7.2 guide room))]
      (when (= :head tabs)
         (doseq [l (range n) :let [bx (+ x0 (* l (+ bw gap))) on? (contains? (:levels page) l)]]
           (gray! cs ink)
           (.setLineWidth cs (float 0.5))
           (.addRect cs (float bx) (py (+ ty 2)) (float bw) (float bw))
           (if on? (.fill cs) (.stroke cs))
           (centred! cs (:bold fonts) 6.2 (+ bx (/ bw 2)) (- ty 0.6) (if (zero? l) "c" (str l))
                     {:color (if on? 1.0 ink)})))
      (text! cs (:italic fonts) 7.2 (- (+ x w) (width (:italic fonts) 7.2 guide)) ty guide))
    (gray! cs ink)
    (line! cs x (+ ty 4) (+ x w) (+ ty 4) 0.5)))

(defn- thumb-tabs! [cs fonts page max-level]
  (let [n (inc max-level) x (- page-w 18 15) top (+ body-top 4) bottom (- body-bottom 4)
        th (min 36 (/ (- bottom top (* 3 (dec n))) n))]
    (doseq [l (range n) :let [ty (+ top (* l (+ th 3))) on? (contains? (:levels page) l)]]
      (gray! cs ink)
      (.setLineWidth cs (float 0.5))
      (.addRect cs (float x) (py (+ ty th)) (float 15) (float th))
      (if on? (.fill cs) (.stroke cs))
      (centred! cs (:bold fonts) 7 (+ x 7.5) (+ ty (/ th 2) 2.5) (if (zero? l) "c" (str l))
                {:color (if on? 1.0 ink)}))))

(defn- credit!
  "CC BY asks for credit where the icons appear: the authors of the emblems on these
   pages, once, under the last page's footer."
  [cs fonts classes w]
  (let [names (->> classes (keep #(emblems/authors (:icon %))) distinct)]
    (when (seq names)
      (let [t (str "Class emblems by " (s/join ", " names) ", from game-icons.net under CC BY 3.0")]
        (text! cs (:italic fonts) 5.4 (- (+ margin (/ w 2)) (/ (width (:italic fonts) 5.4 t) 2)) (- page-h 8) t
               {:color ink-2})))))

(defn- footer! [cs fonts character-name n w]
  (let [ty (- page-h 22)]
    (text! cs (:plain fonts) 6.6 margin ty (str "Spellbook of " (if (s/blank? character-name) "an adventurer" character-name)) {:color ink-2})
    (folio! cs fonts (+ margin (/ w 2)) (- ty 2.4) n)
    (let [sw (spaced-width (:plain fonts) 6.6 pdf/site-stamp 0.4)]
      (text! cs (:plain fonts) 6.6 (- (+ margin w) sw) ty pdf/site-stamp {:color ink-2 :spacing 0.4}))))

(defn- draw-page! [cs fonts page opts max-level n last?]
  (let [w (body-width (:tabs opts))
        col-w (/ (- w gutter) 2)]
    (running-head! cs fonts page opts max-level w)
    (when (= :inset (:tabs opts)) (thumb-tabs! cs fonts page max-level))
    (reduce (fn [ty block]
              (if-let [u (:full block)]
                (do ((:draw u) cs fonts margin ty w) (+ ty (:h u)))
                (let [[a b] (:cols block)
                      two? (= :book (:layout opts))
                      draw-col (fn [items x cw] (reduce (fn [t u] ((:draw u) cs fonts x t cw) (+ t (:h u))) ty items))
                      ha (draw-col a margin (if two? col-w w))
                      hb (when (seq b) (draw-col b (+ margin col-w gutter) col-w))]
                  (when (and two? (seq a) (seq b))
                    (gray! cs hair)
                    (line! cs (+ margin col-w (/ gutter 2)) (+ ty 3) (+ margin col-w (/ gutter 2)) (- (max ha hb) 3) 0.4))
                  (max ha (or hb ty)))))
            body-top (:blocks page))
    (footer! cs fonts (:character-name opts) n w)
    (when last? (credit! cs fonts (:classes opts) w))))

(defn layout-pages
  "The measured, paginated pages for `opts` -- everything but the drawing, so a test can
   check where the breaks fall."
  [fonts img opts spells-known spells-map]
  (let [us (units fonts img opts spells-known spells-map)
        w (body-width (:tabs opts))]
    (paginate us {:two-col? (= :book (:layout opts))
                  :class-break (:class-break opts)
                  :cap (capacity)
                  :cont-units (fn [ci]
                                (let [k (get (:classes opts) ci)]
                                  (cond-> [(assoc (chap-unit fonts img k true) :cls ci)]
                                    (= :ledger (:layout opts)) (conj (assoc (ledger-head-unit fonts w) :cls ci)))))})))

(defn add-spellbook!
  "Appends the spellbook pages to `doc`. `opts` is the sanitised :spellbook request map
   (see routes); `spells-map` resolves every spell key the character knows. Returns the
   number of pages added."
  [doc fonts img opts spells-known spells-map]
  (let [pages (layout-pages fonts img opts spells-known spells-map)
        max-level (reduce max 0 (for [p pages l (:levels p)] l))]
    (doseq [[i page] (map-indexed vector pages)]
      (let [pdpage (PDPage.)]
        (.addPage doc pdpage)
        (with-open [cs (PDPageContentStream. doc pdpage)]
          (draw-page! cs fonts page opts max-level (inc i) (= i (dec (count pages)))))))
    (count pages)))
