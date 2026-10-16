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
  (:import (org.apache.pdfbox.pdmodel PDPageContentStream PDPageContentStream$AppendMode)
           (org.apache.pdfbox.pdmodel.font PDType1Font PDType0Font PDFont
                                           Standard14Fonts$FontName)))

(def page-sizes
  "Page dimensions in POINTS, [width height]. ISO 216 for the A series; US Letter is
   612x792, which is exactly 8.5x11 inches."
  {:letter [612.0 792.0]
   :a4     [595.0 842.0]
   :a5     [420.0 595.0]})

(def default-page
  "What everything draws on today. The character-sheet templates in resources/ are all
   612x792 and their artwork is fixed, so an A4 character sheet needs A4 artwork rather
   than a change here; a bare (PDPage.) is PDFBox's Letter. Changing this alone does not
   produce an A4 export -- it is the one place the page height stops being a literal."
  :letter)

(defn page-size
  "[width height] in points for `page`, a key of `page-sizes`. Unknown keys get Letter."
  [page]
  (get page-sizes page (page-sizes :letter)))

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
  ([pt-per-unit y] (pt-from-top default-page pt-per-unit y))
  ([page pt-per-unit y]
   (float (- (second (page-size page)) (* pt-per-unit y)))))

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

(defn draw-grid
  "Draw the spell card grid. Light gray lines for card boundaries."
  [cs box-width box-height]
  (let [num-boxes-x (int (/ 8.5 box-width))
        num-boxes-y (int (/ 11.0 box-height))
        total-width (* num-boxes-x box-width)
        total-height (* num-boxes-y box-height)
        remaining-width (- 8.5 total-width)
        margin-x (/ remaining-width 2)
        remaining-height (- 11.0 total-height)
        margin-y (/ remaining-height 2)
        ;; PDFBox 3.x: setStrokingColor(float,float,float) requires 0.0-1.0 range
        ;; (PDFBox 2.x accepted 0-255 integers via a separate overload)
        light-gray (float (/ 225.0 255.0))]
    (.setStrokingColor cs light-gray light-gray light-gray)
    (doseq [i (range (inc num-boxes-x))]
      (let [x (+ margin-x (* box-width i))]
        (draw-line-in cs
                      x
                      margin-y
                      x
                      (+ margin-y total-height))))
    (doseq [i (range (inc num-boxes-y))]
      (let [y (+ margin-y (* box-height i))]
        (draw-line-in cs
                      margin-x
                      y
                      (+ margin-x total-width)
                      y)))
    (.setStrokingColor cs (float 0) (float 0) (float 0))))
