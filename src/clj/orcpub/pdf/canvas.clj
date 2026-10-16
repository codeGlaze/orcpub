(ns orcpub.pdf.canvas
  "Path emission shared by the two files that draw shapes into a PDF: pdf.clj's card frame
   and spellbook.clj's line art, which each had their own copy of the same arithmetic.
   Everything takes a `pt` mapper, [x y] in the caller's drawing space to PDF page units,
   so neither has to change coordinate system -- pdf.clj works in inches from the card's
   top-left, spellbook.clj in points from the page top. Both measure y DOWNWARD, which is
   why one formula serves them."
  (:import (org.apache.pdfbox.pdmodel PDPageContentStream)))

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
