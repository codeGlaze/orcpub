(ns orcpub.dnd.e5.portrait-colorize
  "Colouring art that is drawn as a hueless luminance ramp -- the irises and
   the lips -- shared by every renderer: the share card (Java2D), the PDF bake
   and the builder's drawer (both canvas).

   Multiply, which every other layer uses, darkens: on an iris drawn almost
   black it leaves almost black, and on lips drawn in light greys it gives a
   pastel. What these want instead is to map the drawing's own luminance
   THROUGH the colour: dark stays dark, the middle becomes the colour, the
   highlight stays a highlight. Opaque wherever the drawing is.

   Worked out against the real art in portrait_real_art_compare_test and
   written down in docs/PORTRAIT-TINTING.md; this is that code, moved to where
   the app runs it. Only the pixel loop and the drawing of the region are per
   renderer -- the numbers and the geometry are here, once.")

(def floor
  "How much of the colour the darkest ink keeps. The irises are drawn close
   to black, and a lower floor loses the colour altogether."
  0.55)

(defn colorize-channel
  "One channel of the colour `c` (0..255), at gamma-bent luminance `lg`."
  [c lg]
  (if (< lg 0.5)
    (* c (+ floor (* (- 1.0 floor) 2.0 lg)))
    (+ c (* (- 255 c) (* 2.0 (- lg 0.5))))))

(defn colorize-rgb
  "[r g b] for a pixel of the drawing (`r g b`), mapped through the colour
   [er eg eb] and blended in by coverage `k` (0..1) so a region's
   antialiased edge fades into the drawing instead of stepping."
  [r g b er eg eb gamma k]
  (let [l (/ (+ r g b) 765.0)
        lg (Math/pow l gamma)
        blend (fn [s t] (int (min 255.0 (+ (* s (- 1.0 k)) (* t k)))))]
    [(blend r (colorize-channel er lg))
     (blend g (colorize-channel eg lg))
     (blend b (colorize-channel eb lg))]))

(defn hex->rgb
  "[r g b] for #rrggbb, or nil."
  [hex]
  (when-let [[_ r g b] (re-matches #"#([0-9a-fA-F]{2})([0-9a-fA-F]{2})([0-9a-fA-F]{2})" (str hex))]
    (mapv #?(:clj #(Integer/parseInt % 16) :cljs #(js/parseInt % 16)) [r g b])))

;; ---------------------------------------------------------------------------
;; The iris region
;;
;; Placed by hand in the Loom, per eye style, as fractions of the asset's own
;; width and height: an oval, a pupil (a fraction of the oval, optionally
;; nudged off centre), and the curve of the upper lid -- everything above it is
;; under the lashes and must not be coloured.
;; ---------------------------------------------------------------------------

(defn iris-shapes
  "The region in pixels for an asset drawn into `rect` [x y w h]: one map per
   eye, {:iris ellipse :pupil ellipse :lid quad-or-nil}, where an ellipse is
   {:cx :cy :rx :ry :rot} and a lid is {:x0 :y0 :mx :my :x1 :y1 :top}. The
   region is iris minus pupil minus everything above the lid. Radii scale by
   width and height separately, then rotate -- the same order the Loom used."
  [{:asset/keys [iris pupil]} [x y w h]]
  (let [pupil (or pupil 0.45)
        px* #(+ x (* % w))
        py* #(+ y (* % h))]
    (for [e iris]
      (let [shift-x (* (or (:px e) 0.0) (:rx e))
            shift-y (* (or (:py e) 0.0) (:ry e))
            lid (:lid e)]
        {:iris {:cx (px* (:cx e)) :cy (py* (:cy e))
                :rx (* (:rx e) w) :ry (* (:ry e) h) :rot (:rot e)}
         :pupil {:cx (px* (+ (:cx e) shift-x)) :cy (py* (+ (:cy e) shift-y))
                 :rx (* (:rx e) w pupil) :ry (* (:ry e) h pupil) :rot (:rot e)}
         :lid (when lid
                {:x0 (px* (:x0 lid)) :y0 (py* (:y0 lid))
                 :mx (px* (:mx lid)) :my (py* (:my lid))
                 :x1 (px* (:x1 lid)) :y1 (py* (:y1 lid))
                 ;; the region above the curve runs off the top of the frame
                 :top (- y h)})}))))
