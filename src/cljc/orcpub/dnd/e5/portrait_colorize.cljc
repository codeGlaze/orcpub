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
                 :top (- y h)})
         ;; the lower lid, placed only for a style drawn without whites
         :lower (when-let [l (:lower e)]
                  {:x0 (px* (:x0 l)) :y0 (py* (:y0 l))
                   :mx (px* (:mx l)) :my (py* (:my l))
                   :x1 (px* (:x1 l)) :y1 (py* (:y1 l))})}))))

(defn whites-outlines
  "For an asset drawn without painted whites, one closed outline per eye in
   pixels: along the upper lid, then back along the lower lid. The whites are
   filled inside it, UNDER the eye art, so the lashes and iris sit on top.
   Each outline is [[x0 y0] [mx my x1 y1] [lx1 ly1] [lmx lmy lx0 ly0]]."
  [asset rect]
  (when (:asset/whites asset)
    (keep (fn [{:keys [lid lower]}]
            (when (and lid lower)
              [[(:x0 lid) (:y0 lid)]
               [(:mx lid) (:my lid) (:x1 lid) (:y1 lid)]
               [(:x1 lower) (:y1 lower)]
               [(:mx lower) (:my lower) (:x0 lower) (:y0 lower)]]))
          (iris-shapes asset rect))))

;; ---------------------------------------------------------------------------
;; Feathering the region's edge
;;
;; Antialiasing alone stops the colour on a crisp oval, and where that oval is
;; a hair off the drawn iris line the seam shows up close or in print. The
;; coverage is blurred and then only allowed to FALL: the colour fades out
;; inside the shape, into the drawn ring, and never spills past it into the
;; white or under the lashes. Chosen by eye on the real art at 6px on a
;; 1500px-tall drawing; 8 took the colour out of the far eye, whose iris is
;; about 12px across at its narrowest.
;; ---------------------------------------------------------------------------

(def feather-fraction
  "Feather radius as a fraction of the drawn asset's height."
  (/ 6.0 1500.0))

(defn feather-radius
  "The feather radius in pixels for an asset drawn `drawn-height` tall."
  [drawn-height]
  (int (Math/round (* feather-fraction (double drawn-height)))))

(defn- new-doubles [n]
  #?(:clj (double-array n) :cljs (js/Float64Array. n)))

;; typed on the JVM, where an untyped aget over a million pixels reflects
#?(:clj  (defn- ag ^double [^doubles a ^long i] (aget a i))
   :cljs (defn- ag [a i] (aget a i)))
#?(:clj  (defn- as! [^doubles a ^long i ^double v] (aset a i v))
   :cljs (defn- as! [a i v] (aset a i v)))

(defn feather
  "Coverage (0..255 per pixel, `w` x `h`, row-major) with its edge faded
   inward over radius `r`: a gaussian blur, then never more than the
   original covered. Only the area around covered pixels is touched."
  [cov w h r]
  (if (< r 1)
    cov
    (let [n (* w h)
          ;; bounding box of anything covered, grown by the radius
          [x0 y0 x1 y1] (loop [i 0 x0 w y0 h x1 -1 y1 -1]
                          (if (= i n)
                            [x0 y0 x1 y1]
                            (if (pos? (ag cov i))
                              (let [x (mod i w) y (quot i w)]
                                (recur (inc i) (min x0 x) (min y0 y) (max x1 x) (max y1 y)))
                              (recur (inc i) x0 y0 x1 y1))))]
      (if (neg? x1)
        cov
        (let [x0 (max 0 (- x0 r)) y0 (max 0 (- y0 r))
              x1 (min (dec w) (+ x1 r)) y1 (min (dec h) (+ y1 r))
              sigma (max 0.5 (/ r 2.0))
              kernel (let [ws (mapv #(Math/exp (- (/ (* % %) (* 2 sigma sigma)))) (range (- r) (inc r)))
                           s (reduce + ws)
                           k (new-doubles (count ws))]
                       (doseq [[i v] (map-indexed vector ws)] (as! k i (/ v s)))
                       k)
              pass (fn [src horizontal?]
                     (let [out (new-doubles n)]
                       (doseq [y (range y0 (inc y1)) x (range x0 (inc x1))]
                         (as! out (+ x (* y w))
                               (loop [k 0 acc 0.0]
                                 (if (> k (* 2 r))
                                   acc
                                   (let [d (- k r)
                                         xx (if horizontal? (max 0 (min (dec w) (+ x d))) x)
                                         yy (if horizontal? y (max 0 (min (dec h) (+ y d))))]
                                     (recur (inc k) (+ acc (* (ag kernel k) (ag src (+ xx (* yy w)))))))))))
                       out))
              blurred (pass (pass cov true) false)
              out (new-doubles n)]
          (dotimes [i n]
            (as! out i (min (ag cov i) (* 2.0 (max 0.0 (- (ag blurred i) 127.5))))))
          out)))))

