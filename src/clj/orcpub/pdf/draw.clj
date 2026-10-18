(ns orcpub.pdf.draw
  "The drawing toolbox the PDF outputs share: page sizes, the coordinate flip, fonts, text
   measurement and wrapping, text and line drawing, and shape paths. pdf.clj's character
   sheet and cards and spellbook.clj's booklet all draw through here.
   Neither caller changes coordinate system. The shape functions take a `pt` mapper and
   `pt-from-top` takes points-per-unit, so pdf.clj stays in inches from a card's top-left
   and spellbook.clj in points from the page top; both measure y DOWNWARD."
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [orcpub.common :as common])
  (:import (org.apache.pdfbox.pdmodel PDPageContentStream PDPageContentStream$AppendMode
                                      PDResources)
           (org.apache.pdfbox.pdmodel.common PDRectangle)
           (org.apache.pdfbox.pdmodel.font PDType1Font PDType0Font PDFont
                                           Standard14Fonts$FontName)
           (org.apache.pdfbox.pdmodel.graphics.state PDExtendedGraphicsState)
           (org.apache.pdfbox.pdmodel.interactive.annotation PDAppearanceStream)
           (org.apache.pdfbox.pdmodel.graphics.image LosslessFactory)
           (org.apache.pdfbox.util Matrix)
           (javax.imageio ImageIO)))

(def page-sizes
  "Page dimensions in POINTS, [width height]. ISO 216 for the A series; US Letter is
   612x792, which is exactly 8.5x11 inches."
  {:letter [612.0 792.0]
   :a4     [595.28 841.89]
   :a5     [419.53 595.28]})

(def ^:dynamic *page*
  "The page every drawing call measures against, a key of `page-sizes`. Bind it once per
   document; the primitives read it, so nothing threads a page through 66 call sites.

   Root is :letter, which is what a bare (PDPage.) is. A binding does NOT reach a
   load-time constant derived from it -- see spellbook's page-w -- so those are fns."
  :letter)

(defn page-size
  "[width height] in points for `page`, a key of `page-sizes`. Unknown keys get Letter."
  [page]
  (get page-sizes page (page-sizes :letter)))

(defn page-width
  "The bound page's width in the caller's unit: `pt-per-unit` is 72 for inches, 1 for
   points. For code asking how wide the page is, not converting a coordinate."
  [pt-per-unit]
  (/ (first (page-size *page*)) pt-per-unit))

(defn page-height
  "The bound page's height in the caller's unit. `pt-per-unit` as `page-width`."
  [pt-per-unit]
  (/ (second (page-size *page*)) pt-per-unit))

(defn in->pt
  "Inches to PDF points, 72 per inch. For SIZES -- widths, heights, offsets -- where no
   page origin is involved, so no flip."
  [inches]
  (float (* 72 inches)))

(defn pt-from-top
  "`y` units DOWN from the page top to a PDF y coordinate, which runs up from the bottom.
   `pt-per-unit` is 72 when the caller works in inches, 1 when it works in points.

   The one flip both drawing files use: pdf.clj had it as `(in-to-sz (- 11 inches))` with
   the page height inline, spellbook.clj as `(- page-h ty)`. Same arithmetic, two units,
   and only one of them named the height."
  ([pt-per-unit y] (pt-from-top *page* pt-per-unit y))
  ([page pt-per-unit y]
   (float (- (second (page-size page)) (* pt-per-unit y)))))

(defn in-from-top
  "`y` INCHES down from the page top as inches up from the bottom, which is what
   draw-text and the box fns take. One named conversion for the card code, which
   wrote `(- 11.0 y 0.08)` at 27 sites; spellbook's `py` is the points equivalent."
  [y]
  (/ (pt-from-top 72 y) 72.0))

(defn fit
  "Largest [height width] with the image's aspect ratio that fits the rectangle
   [r-h r-w]. One dimension matches the rectangle exactly; the other is smaller. Units
   are the caller's, so long as both pairs agree."
  [[r-h r-w] [i-h i-w]]
  (if (> (/ i-h i-w) (/ r-h r-w))
    [r-h (* r-h (/ i-w i-h))]
    [(* r-w (/ i-h i-w)) r-w]))

(def bezier-k
  "Control-point offset that turns four cubic curves into a circle, to within a fifth of
   a percent. There is no arc operator in a PDF content stream."
  0.5522847)

(defn path!
  "`moveTo` the first of `pts`, `lineTo` the rest, each mapped through `pt`.

   Emits no close and no stroke on purpose: the callers' terminators differ, and the
   difference is visible in the content stream -- closeAndStroke is `s`, while closePath
   then stroke is `h S`. Sharing only the path keeps both byte-identical."
  [^PDPageContentStream cs pt pts]
  (let [[[px py] & more] (map (fn [[a b]] (pt a b)) pts)]
    (.moveTo cs (float px) (float py))
    (doseq [[qx qy] more] (.lineTo cs (float qx) (float qy)))))

(defn circle-path!
  "A circle of radius `r` about (`cx`, `cy`) in the caller's drawing space, as `moveTo`
   plus four cubic curves, counter-clockwise from the rightmost point. Closes nothing and
   strokes nothing, as `path!`."
  [^PDPageContentStream cs pt cx cy r]
  (let [k (* r bezier-k)
        [x0 y0] (pt (+ cx r) cy)
        curve (fn [[ax ay] [bx by] [ex ey]]
                (.curveTo cs (float ax) (float ay) (float bx) (float by)
                          (float ex) (float ey)))]
    (.moveTo cs (float x0) (float y0))
    ;; y runs DOWN in both callers' spaces, so (- cy r) is the top of the circle.
    (curve (pt (+ cx r) (- cy k)) (pt (+ cx k) (- cy r)) (pt cx (- cy r)))
    (curve (pt (- cx k) (- cy r)) (pt (- cx r) (- cy k)) (pt (- cx r) cy))
    (curve (pt (- cx r) (+ cy k)) (pt (- cx k) (+ cy r)) (pt cx (+ cy r)))
    (curve (pt (+ cx k) (+ cy r)) (pt (+ cx r) (+ cy k)) (pt (+ cx r) cy))))

(def HELVETICA
  "Standard Helvetica font (regular weight, upright)"
  (PDType1Font. Standard14Fonts$FontName/HELVETICA))

(def HELVETICA_BOLD
  "Standard Helvetica font (bold weight, upright)"
  (PDType1Font. Standard14Fonts$FontName/HELVETICA_BOLD))

(def HELVETICA_BOLD_OBLIQUE
  "Standard Helvetica font (bold weight, italic/oblique)"
  (PDType1Font. Standard14Fonts$FontName/HELVETICA_BOLD_OBLIQUE))

(def site-stamp
  "The site line printed on card backs, the spellbook foot and every character-sheet
   page. Here because all three draw it; the placement is each one's own."
  "dungeonmastersvault.com")

(def site-stamp-size
  "Point size of `site-stamp`. The spellbook sets its own, 6.6 with letter-spacing."
  6)

(defn load-fonts
  "Loads the fonts for the document. Will contain
  :plain, :italic, :bold and :bold-italic fonts."
  [doc]
  (reduce-kv
    (fn [m type file]
      (assoc m type
               (with-open [stream (.openStream (io/resource file))]
                 (PDType0Font/load doc stream))))
    {}
    {:plain       "Vollkorn-Regular.ttf"
     :italic      "Vollkorn-Italic.ttf"
     :bold        "Vollkorn-Bold.ttf"
     :bold-italic "Vollkorn-BoldItalic.ttf"}))

(defn normalize-text
  "Coerces a value into the WinAnsiEncoding 0x20-0xFF subset PDFBox can render
   (PDType1Font throws on anything else, blanking the field). nil -> nil; non-strings
   stringified. \\t -> space; other 0x00-0x1F dropped except \\n and \\r; curly
   quotes -> straight; U+2013 / U+2014 -> - / --; U+2026 -> ...; anything else
   above U+00FF -> '?'."
  [s]
  (when (some? s)
    (-> (str s)
        (s/replace "\t" " ")
        (s/replace #"[\u0000-\u0008\u000B\u000C\u000E-\u001F]" "")
        (s/replace "\u2018" "'")  (s/replace "\u2019" "'")
        (s/replace "\u201C" "\"") (s/replace "\u201D" "\"")
        (s/replace "\u2013" "-")  (s/replace "\u2014" "--")
        (s/replace "\u2026" "...")
        (s/replace #"[^\u0000-\u00FF]" "?"))))

(defn font-safe
  "`text` with whatever `font` has no glyph for made drawable: characters below 0x20
   dropped, anything else replaced by `?`. Returns `text` itself when the whole string
   draws, which is every string in the SRD -- the embedded Vollkorn covers Latin-1 and
   the punctuation the books use, so this only fires on homebrew."
  [^PDFont font text]
  (if-not (string? text)
    text
    (try
      (.getStringWidth font text)
      text
      (catch Exception _
        (letfn [(drawable? [s] (try (.getStringWidth font s) true
                                    (catch Exception _ false)))]
          (->> (seq (.toArray (.codePoints text)))
               (keep #(let [c (String. (Character/toChars (int %)))]
                        (when (>= (int %) 0x20) (if (drawable? c) c "?"))))
               (apply str)))))))

;; FIELD NOTE (glyph-throw): PDFBox throws IllegalArgumentException on a glyph the
;; embedded font has not got, and add-magic-item-cards! wraps every sheet in one try.
;; Unprotected, one homebrew name in a script Vollkorn lacks cost the export the sheet
;; it was on AND every sheet after it. Measured: 1 page of an expected 4.

(defn string-width
  "Width of `text` in INCHES at `font-size`, not points: the raw glyph width is
   divided by 72. Callers working in points must convert. Falls back to `font-safe`
   for text the font cannot draw, rather than throwing."
  [text ^PDFont font font-size]
  (if text
    (let [t (if (keyword? text) (common/safe-name text) text)]
      (/ (* (/ (try (.getStringWidth font t)
                    (catch Exception _ (.getStringWidth font (font-safe font t))))
               1000.0)
            font-size)
         72))
    0))

;; ─── Orphaned widgets ────────────────────────────────────────────────────────

(defn content-stream
  "Opens a compressed PDPageContentStream that APPENDs to `page`'s existing
   content, so template artwork survives (OVERWRITE would replace it)."
  [doc page]
  (PDPageContentStream. doc page PDPageContentStream$AppendMode/APPEND true))

(defn split-lines
  "Greedy word wrap of `text` to `width` INCHES at `font-size` (see
   `string-width`, which also works in inches). Splits on whitespace, so
   existing newlines are not honoured. A word wider than the box gets its own
   over-long line rather than being hyphenated or dropped.

   Shared with spellbook.clj."
  [text ^PDFont font font-size width]
  (let [words (s/split text #"\s")]
    (loop [lines []
           current-line nil
           [next-word & remaining-words :as current-words] words]
      (if next-word
        (let [line-with-word (str current-line (when current-line " ") next-word)
              new-width (string-width line-with-word font font-size)]
          (if (> new-width width)
            (if current-line
              (recur (conj lines current-line)
                     nil
                     current-words)
              ;; A single word wider than the box takes its own line. Retrying it
              ;; against an empty current-line would never consume it, so the word
              ;; has to be taken here or the loop never terminates.
              (recur (conj lines next-word)
                     nil
                     remaining-words))
            (recur lines
                   line-with-word
                   remaining-words)))
        (if current-line
          (conj lines current-line)
          lines)))))

;; ─── Fitting text to a box ────────────────────────────────────────────────────

(defn draw-lines-to-box
  "Draws pre-wrapped `lines` into a box `height` inches tall topped at [`x` `y`],
   inches with `y` from the page BOTTOM (see `in-to-coord-y`). Leading is 1.1x the
   font size. RETURNS the lines that did not fit, which is how card overflow reaches
   the backs; empty means all fit. Nothing is truncated horizontally -- that is
   `split-lines`' job."
  [cs lines font font-size x y height]
  (let [leading (* font-size 1.1)
        max-lines (dec (/ (* 72 height) leading))
        units-x (* 72 x)
        units-y (* 72 y)
        fitting-lines (vec (take max-lines lines))]
    (.beginText cs)
    (.setFont cs font font-size)
    (.newLineAtOffset cs units-x units-y)
    (doseq [i (range (count fitting-lines))]
      (let [line (get fitting-lines i)]
        (.newLineAtOffset cs 0 (- leading))
        (.showText cs (font-safe font line))))
    (.endText cs)
    (vec (drop max-lines lines))))

(defn draw-text-to-box
  "`split-lines` then `draw-lines-to-box`: wraps `text` to `width` and fills the
   box, returning the lines that did not fit. Inches throughout, `y` from the
   page BOTTOM."
  [cs text font font-size x y width height]
  (let [lines (split-lines text font font-size width)]
    (draw-lines-to-box cs lines font font-size x y height)))

(defn set-text-color
  "Set text (non-stroking) color. Values must be 0.0-1.0 floats (PDFBox 3.x)."
  [cs r g b]
  (.setNonStrokingColor cs (float r) (float g) (float b)))

(defn draw-text
  "One line of text with its baseline at [`x` `y`], in inches with `y` from the
   page BOTTOM. No wrapping and no box -- it runs off the page if too long.
   `color` is an [r g b] of 0.0-1.0 floats and, when given, is reset to black
   afterwards. Keywords are rendered via `common/safe-name`; nil `text` is a no-op."
  [cs text font font-size x y & [color]]
  (when text
    (let [units-x (* 72 x)
          units-y (* 72 y)]
      (.beginText cs)
      (.setFont cs font font-size)
      (when color
        (apply set-text-color cs color))
      (.newLineAtOffset cs units-x units-y)
      (.showText cs (font-safe font (if (keyword? text)
                                          (common/safe-name text) text)))
      (when color
        (set-text-color cs 0 0 0))
      (.endText cs))))

(defn draw-halo-text
  "Draw text with a white outline (8 offset copies) then black fill, so a black
   label stays legible on top of a solid-black icon. Used only in printer-friendly
   B&W mode, where icon and text are both pure black; offsets are in inches."
  [cs text font font-size x y]
  (let [d 0.009]
    (doseq [dx [(- d) 0 d]
            dy [(- d) 0 d]
            :when (not (and (zero? dx) (zero? dy)))]
      (draw-text cs text font font-size (+ x dx) (+ y dy) [1 1 1]))
    (draw-text cs text font font-size x y [0 0 0])))

(defn draw-line
  "Draw a line. PDFBox 3.x removed drawLine — use moveTo/lineTo/stroke."
  [cs start-x start-y end-x end-y]
  (.moveTo cs (float start-x) (float start-y))
  (.lineTo cs (float end-x) (float end-y))
  (.stroke cs))

(defn draw-line-in
  "`draw-line` with every coordinate given in inches instead of points. Both
   axes measure from the page BOTTOM-left; there is no flip here."
  [cs & coords]
  (apply draw-line cs (map in->pt coords)))

(defn grid-layout
  "How many `box-width` x `box-height` inch cells fit the bound page, and the margins
   that centre them: {:cols :rows :margin-x :margin-y :width :height}, in inches.

   One source for the whole 9-up layout. draw-grid draws its lines from this and the
   card printers place cards by it, so the cut lines and the cards cannot be computed
   from different numbers -- which is exactly the bug they had."
  [box-width box-height]
  (let [pw (page-width 72)
        ph (page-height 72)
        cols (int (/ pw box-width))
        rows (int (/ ph box-height))
        width (* cols box-width)
        height (* rows box-height)]
    {:cols cols :rows rows :width width :height height
     :margin-x (/ (- pw width) 2)
     :margin-y (/ (- ph height) 2)}))

(defn page-rect
  "A PDRectangle for the bound page, for `(PDPage. (draw/page-rect))`. A bare (PDPage.)
   is PDFBox's Letter, which silently ignores `*page*`."
  []
  (let [[w h] (page-size *page*)]
    (PDRectangle. (float w) (float h))))

(defn cards-per-page
  "How many `box-width` x `box-height` inch cards fit the bound page. What a caller must
   partition by -- 9 on Letter and A4, 4 on A5, and a literal 9 drops the rest."
  [box-width box-height]
  (let [{:keys [cols rows]} (grid-layout box-width box-height)]
    (* cols rows)))

(defn draw-grid
  "The cut grid for cards `box-width` x `box-height` inches, as many as fit the bound
   page, centred. Light grey lines. Takes its page from `*page*`, so a 2-up A5 grid on
   A4 is the same call with a different binding."
  [cs box-width box-height]
  (let [{:keys [cols rows margin-x margin-y width height]} (grid-layout box-width box-height)
        ;; PDFBox 3.x: setStrokingColor(float,float,float) requires 0.0-1.0 range
        ;; (PDFBox 2.x accepted 0-255 integers via a separate overload)
        light-gray (float (/ 225.0 255.0))]
    (.setStrokingColor cs light-gray light-gray light-gray)
    (doseq [i (range (inc cols))]
      (let [x (+ margin-x (* box-width i))]
        (draw-line-in cs x margin-y x (+ margin-y height))))
    (doseq [i (range (inc rows))]
      (let [y (+ margin-y (* box-height i))]
        (draw-line-in cs margin-x y (+ margin-x width) y)))
    (.setStrokingColor cs (float 0) (float 0) (float 0))))

(def ^:private svg-token
  #"([MmLlHhVvCcSsQqTtAaZz])|(-?(?:\d*\.\d+|\d+)(?:[eE][-+]?\d+)?)")

(defn- svg-tokens
  "The `d` attribute as an alternating stream of command keywords and numbers."
  [d]
  (for [[_ cmd num] (re-seq svg-token d)]
    (if cmd (keyword cmd) (Double/parseDouble num))))

(defn- arc-points
  "An elliptical arc sampled as points. SVG's A takes radii, a rotation and two
   flags; PDF has no arc operator, and the exact bezier decomposition is a page of
   trigonometry for a curve that spans a few points on an icon, so it is sampled."
  [x0 y0 rx ry rot large? sweep? x1 y1]
  (let [phi (Math/toRadians rot)
        cos-p (Math/cos phi) sin-p (Math/sin phi)
        dx2 (/ (- x0 x1) 2.0) dy2 (/ (- y0 y1) 2.0)
        tx (+ (* cos-p dx2) (* sin-p dy2))
        ty (- (* cos-p dy2) (* sin-p dx2))
        rx (Math/abs rx) ry (Math/abs ry)
        ;; A radius too small to span the endpoints is scaled up, per the spec.
        lam (+ (/ (* tx tx) (* rx rx)) (/ (* ty ty) (* ry ry)))
        [rx ry] (if (> lam 1) [(* rx (Math/sqrt lam)) (* ry (Math/sqrt lam))] [rx ry])
        num (- (* rx rx ry ry) (* rx rx ty ty) (* ry ry tx tx))
        den (+ (* rx rx ty ty) (* ry ry tx tx))
        co (* (if (= large? sweep?) -1 1) (Math/sqrt (max 0.0 (/ num den))))
        cx' (* co (/ (* rx ty) ry))
        cy' (* co (- (/ (* ry tx) rx)))
        cx (+ (* cos-p cx') (- (* sin-p cy')) (/ (+ x0 x1) 2.0))
        cy (+ (* sin-p cx') (* cos-p cy') (/ (+ y0 y1) 2.0))
        ang (fn [ux uy vx vy]
              (let [d (/ (+ (* ux vx) (* uy vy))
                         (* (Math/hypot ux uy) (Math/hypot vx vy)))
                    a (Math/acos (max -1.0 (min 1.0 d)))]
                (if (neg? (- (* ux vy) (* uy vx))) (- a) a)))
        th1 (ang 1 0 (/ (- tx cx') rx) (/ (- ty cy') ry))
        dth (let [d (ang (/ (- tx cx') rx) (/ (- ty cy') ry)
                         (/ (- (- tx) cx') rx) (/ (- (- ty) cy') ry))]
              (cond (and (not sweep?) (pos? d)) (- d (* 2 Math/PI))
                    (and sweep? (neg? d)) (+ d (* 2 Math/PI))
                    :else d))
        steps (max 6 (int (* 24 (/ (Math/abs dth) Math/PI))))]
    (for [i (range 1 (inc steps))
          :let [t (+ th1 (* dth (/ i (double steps))))
                ex (* rx (Math/cos t)) ey (* ry (Math/sin t))]]
      [(+ cx (* cos-p ex) (- (* sin-p ey)))
       (+ cy (* sin-p ex) (* cos-p ey))])))

(defn svg-path-ops
  "Parses an SVG `d` attribute into [:move x y], [:line x y],
   [:curve x1 y1 x2 y2 x y] and [:close], in the SVG's own coordinates.
   Covers M L H V C S Q T A Z and their relative forms, with pairs after an M
   continuing as implicit L. Quadratics are raised to cubics; arcs, which PDF
   lacks, are sampled as lines."
  [d]
  (loop [[t & more :as ts] (svg-tokens d)
         cmd nil, cx 0.0, cy 0.0, sx 0.0, sy 0.0
         prev-c nil, prev-q nil, out []]
    (if (nil? t)
      out
      (let [[cmd ts] (if (keyword? t) [t more] [cmd ts])
            rel? (and cmd (Character/isLowerCase (first (name cmd))))
            n (fn [i] (nth ts i))
            ax (fn [v] (if rel? (+ cx v) v))
            ay (fn [v] (if rel? (+ cy v) v))]
        (case (if cmd (keyword (common/ascii-upper-case (name cmd))) :none)
          :M (let [x (ax (n 0)) y (ay (n 1))]
               (recur (drop 2 ts) (if rel? :l :L) x y x y nil nil
                      (conj out [:move x y])))
          :L (let [x (ax (n 0)) y (ay (n 1))]
               (recur (drop 2 ts) cmd x y sx sy nil nil (conj out [:line x y])))
          :H (let [x (ax (n 0))]
               (recur (drop 1 ts) cmd x cy sx sy nil nil (conj out [:line x cy])))
          :V (let [y (ay (n 0))]
               (recur (drop 1 ts) cmd cx y sx sy nil nil (conj out [:line cx y])))
          :C (let [x1 (ax (n 0)) y1 (ay (n 1)) x2 (ax (n 2)) y2 (ay (n 3))
                   x (ax (n 4)) y (ay (n 5))]
               (recur (drop 6 ts) cmd x y sx sy [x2 y2] nil
                      (conj out [:curve x1 y1 x2 y2 x y])))
          ;; S and T take their first control point by reflecting the previous
          ;; one through the current point; with no previous curve it is the
          ;; current point itself.
          :S (let [[px py] (or prev-c [cx cy])
                   x1 (- (* 2 cx) px) y1 (- (* 2 cy) py)
                   x2 (ax (n 0)) y2 (ay (n 1)) x (ax (n 2)) y (ay (n 3))]
               (recur (drop 4 ts) cmd x y sx sy [x2 y2] nil
                      (conj out [:curve x1 y1 x2 y2 x y])))
          :Q (let [qx (ax (n 0)) qy (ay (n 1)) x (ax (n 2)) y (ay (n 3))]
               (recur (drop 4 ts) cmd x y sx sy nil [qx qy]
                      (conj out [:curve (+ cx (* 2/3 (- qx cx))) (+ cy (* 2/3 (- qy cy)))
                                 (+ x (* 2/3 (- qx x))) (+ y (* 2/3 (- qy y))) x y])))
          :T (let [[px py] (or prev-q [cx cy])
                   qx (- (* 2 cx) px) qy (- (* 2 cy) py)
                   x (ax (n 0)) y (ay (n 1))]
               (recur (drop 2 ts) cmd x y sx sy nil [qx qy]
                      (conj out [:curve (+ cx (* 2/3 (- qx cx))) (+ cy (* 2/3 (- qy cy)))
                                 (+ x (* 2/3 (- qx x))) (+ y (* 2/3 (- qy y))) x y])))
          :A (let [x (ax (n 5)) y (ay (n 6))
                   pts (arc-points cx cy (n 0) (n 1) (n 2)
                                   (not (zero? (n 3))) (not (zero? (n 4))) x y)]
               (recur (drop 7 ts) cmd x y sx sy nil nil
                      (into out (map (fn [[px py]] [:line px py]) pts))))
          :Z (recur ts cmd sx sy sx sy nil nil (conj out [:close]))
          (recur more cmd cx cy sx sy prev-c prev-q out))))))

(defn- emit-svg-path!
  "Writes `ops` from svg-path-ops as PDF path operators, mapping SVG coordinates
   through `px` and `py`.

   game-icons.net glyphs are one filled path with holes, which is exactly what the
   nonzero winding rule of `fill` produces."
  [cs ops px py]
  (doseq [op ops]
    (case (first op)
      :move  (.moveTo cs (px (nth op 1)) (py (nth op 2)))
      :line  (.lineTo cs (px (nth op 1)) (py (nth op 2)))
      :curve (.curveTo cs (px (nth op 1)) (py (nth op 2))
                       (px (nth op 3)) (py (nth op 4))
                       (px (nth op 5)) (py (nth op 6)))
      :close (.closePath cs)
      nil))
  (.fill cs))

(def ^:private svg-view
  "game-icons.net glyphs are all drawn in a 512x512 viewBox."
  512.0)

(defn last-svg-path
  "The `d` of the LAST <path> in an SVG document, in either quote style (the
   colour icons use double quotes, the `black/` set single).
   GOTCHA: game-icons.net glyphs open with a transparent square background path;
   filling it would black out the icon, so the glyph is the last one."
  [svg]
  (when-let [m (last (re-seq #"<path\b[^>]*\sd\s*=\s*(?:\"([^\"]*)\"|'([^']*)')" svg))]
    (or (nth m 1) (nth m 2))))

(defn load-svg-icon
  "The drawable path operations of an icon in resources/public/image, or nil if
   that icon was never vendored as an SVG."
  [icon-name]
  (some-> (io/resource (str "public/image/" icon-name ".svg"))
          slurp last-svg-path svg-path-ops))

(defn- round-unit
  "A form coordinate, rounded to one decimal place to keep the file small. A
   tenth of a 512-unit box stays under a 600 DPI dot at card icon sizes."
  [v]
  (float (/ (Math/round (* 10.0 (double v))) 10.0)))

(defn svg-form
  "An icon's paths as a Form XObject in a `svg-view`-unit box, y already flipped
   so the form's own space is upright. Written into the file once and referenced
   wherever it is drawn. The form sets no colour of its own, so the fill colour
   and alpha in force at each draw site apply to it."
  [doc ops]
  (let [form (PDAppearanceStream. doc)]
    (.setBBox form (PDRectangle. 0 0 (float svg-view) (float svg-view)))
    (.setResources form (PDResources.))
    (with-open [cs (PDPageContentStream. doc form)]
      (emit-svg-path! cs ops round-unit (fn [v] (round-unit (- svg-view v)))))
    form))

(defn draw-imagex
  "Draws an already-embedded PDImageXObject into the box at [`x` `y`] sized
   `width` x `height`, all in INCHES, with `y` measured from the page TOP. The
   image keeps its aspect ratio (see `fit`) and is centred in whichever
   dimension it falls short of."
  [c-stream img x y width height]
  (let [[scaled-height scaled-width] (fit [height width] [(.getHeight img) (.getWidth img)])]
    (.drawImage
     c-stream
     img
     (in->pt (+ x (if (< scaled-width width)
                           (/ (- width scaled-width) 2)
                           0)))
     (pt-from-top 72 (+ height y (if (< scaled-height height)
                                  (/ (- scaled-height height) 2)
                                  0)))
     (in->pt scaled-width)
     (in->pt scaled-height))))

(defn draw-imagex-alpha
  "draw-imagex at a reduced constant opacity (0.0-1.0). Used for the faded
   grayscale card icons: a black `-bw` icon drawn at ~40% reads as a light
   backdrop the black label sits over. Isolated in a save/restore so the alpha
   doesn't leak into later drawing."
  [cs img x y width height alpha]
  (let [gs (doto (PDExtendedGraphicsState.)
             (.setNonStrokingAlphaConstant (float alpha)))]
    (.saveGraphicsState cs)
    (.setGraphicsStateParameters cs gs)
    (draw-imagex cs img x y width height)
    (.restoreGraphicsState cs)))

(def icon-red
  "The red the card icons were baked at, as [r g b alpha]. The PNGs carry #910000
   at half opacity; filling the vector with the same two values composites
   identically over whatever is beneath it."
  [0.5686 0.0 0.0 0.5])

(defn draw-svg-icon!
  "Draws a named icon into a `size`-inch box, filled in `color` at `alpha`. `y` is
   inches from the page TOP to the box's top edge, as in draw-imagex (whose PDF
   arithmetic makes it look otherwise). The icon is the document's shared form, placed
   by a transform; colour and alpha sit in a save/restore so they reach the form but
   not the label drawn over it. Without a vendored SVG it falls back to the PNG of the
   same name, which ignores `color`."
  [cs img icon-name x y size [r g b] alpha]
  (if (io/resource (str "public/image/" icon-name ".svg"))
    ;; The form is a svg-view-unit square; this maps that square onto `size` inches.
    (let [unit (float (/ (* 72 size) svg-view))]
      (.saveGraphicsState cs)
      (when (< alpha 1)
        (.setGraphicsStateParameters
         cs (doto (PDExtendedGraphicsState.)
              (.setNonStrokingAlphaConstant (float alpha)))))
      (.setNonStrokingColor cs (float r) (float g) (float b))
      (.transform cs (Matrix. unit 0 0 unit
                              (in->pt x) (pt-from-top 72 (+ y size))))
      (.drawForm cs (img (str "public/image/" icon-name ".svg")))
      (.restoreGraphicsState cs))
    (draw-imagex-alpha cs (img (str "public/image/" icon-name ".png"))
                       x y size size alpha)))

(defn draw-svg-path!
  "Fills `ops` straight into a `size`-inch box whose top-left is (x, y) on the
   page, scaling from a `view` square viewBox and flipping SVG's downward y.
   GOTCHA: writes the whole path into the page's content stream every call;
   anything drawn more than once should go through the image loader instead."
  [cs ops x y size view]
  (emit-svg-path! cs ops
                  (fn [v] (in->pt (+ x (* size (/ v view)))))
                  (fn [v] (pt-from-top 72 (+ y (* size (/ v view)))))))

(defn make-image-loader
  "Returns a memoized (resource-path -> XObject) embedder scoped to ONE document:
   a PDImageXObject for a raster path, a form (see svg-form) for a `.svg` one.
   Each distinct resource is embedded once and referenced thereafter, so repeated
   icons and logos are neither re-decoded nor duplicated in the file."
  [doc]
  (memoize
   (fn [resource-path]
     (if (s/ends-with? resource-path ".svg")
       (svg-form doc (svg-path-ops (last-svg-path (slurp (io/resource resource-path)))))
       (with-open [in (io/input-stream (io/resource resource-path))]
         (LosslessFactory/createFromImage doc (ImageIO/read in)))))))
