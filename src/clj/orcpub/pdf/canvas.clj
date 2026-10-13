(ns orcpub.pdf.canvas
  "Path emission shared by the two files that draw shapes into a PDF: pdf.clj's card frame
   and spellbook.clj's line art, which each had their own copy of the same arithmetic.
   Everything takes a `pt` mapper, [x y] in the caller's drawing space to PDF page units,
   so neither has to change coordinate system -- pdf.clj works in inches from the card's
   top-left, spellbook.clj in points from the page top. Both measure y DOWNWARD, which is
   why one formula serves them."
  (:import (org.apache.pdfbox.pdmodel PDPageContentStream)))

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
