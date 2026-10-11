(ns orcpub.dnd.e5.portrait-layout
  "The geometry shared by the three portrait renderers (DOM drawer, browser canvas export,
   Java2D share card): where a layer lands and where the credit and site mark sit, in frame
   pixels. Nothing here draws; each renderer takes its numbers from here.
   See PORTRAIT-COMPOSITOR.md, \"Three renderers, one geometry\".")

(defn contain-rect
  "[x y w h] where a `sw`x`sh` asset lands in a `w`x`h` frame: scaled to fit, centred,
   aspect kept, as the DOM's `mask-size: contain` places it. The whole frame for a
   zero-sized asset."
  [sw sh w h]
  (if (or (zero? sw) (zero? sh))
    [0 0 w h]
    (let [scale (min (/ (double w) sw) (/ (double h) sh))
          dw (Math/round (* sw scale))
          dh (Math/round (* sh scale))]
      [(Math/round (/ (- w dw) 2.0))
       (Math/round (/ (- h dh) 2.0))
       dw
       dh])))

(def credit-font-family
  "The family the baked credit is set in: a webfont in the browser, the same file off the
   classpath on the server."
  "Vollkorn")

(defn credit-layout
  "Metrics for the credit along the bottom of a `w`x`h` frame: {:size :halo :center-x
   :baseline :outline :fill}. Colours are [r g b a] with a out of 255 (alpha->unit for a
   canvas): dark fill under a light outline. Sizes are rounded, never truncated."
  [w h]
  (let [size (max 9 (Math/round (* h 0.026)))]
    {:size size
     :halo (max 1 (Math/round (/ (double size) 12)))
     :center-x (/ (double w) 2)
     :baseline (- h (max 4 (Math/round (* h 0.018))))
     :outline [255 255 255 220]
     :fill [20 20 20 235]}))

(defn site-mark-layout
  "Metrics for the site mark running up the RIGHT edge of a `w`x`h` frame: {:size :x
   :outline :fill}, `:x` the baseline's distance from the left. The vertical start needs
   font metrics, so the renderer computes it. Kept off the credit's edge: see
   PORTRAIT-COMPOSITOR.md, \"The baked credit and the site mark\"."
  [w h]
  {:size (max 8 (Math/round (* h 0.020)))
   :x (- w (max 4 (Math/round (* w 0.022))))
   :outline [255 255 255 90]
   :fill [20 20 20 128]})

(defn alpha->unit
  "An [r g b a] alpha, as a canvas wants it."
  [[_ _ _ a]]
  (/ (double a) 255))

;; ---------- example images on the artist pages ----------

(def example-size
  "[w h] of an artist page's example image: about the size the page shows it, so the file
   is no better to scrape than what is on screen."
  [240 300])

(def example-ground
  "The opaque ground an example is flattened onto, as [r g b] stops of a radial gradient
   centred at 50% 35%: the portrait frame's own colours, so the image meets its frame."
  {:centre [32 41 57] :mid [19 25 36] :edge [15 20 28]})

(defn example-watermark-layout
  "Metrics for the watermark tiled across a `w`x`h` example: {:size :angle :step-x
   :step-y :fill :outline}. :angle is in radians, rows rise to the right; colours are
   [r g b a] with a out of 255. Faint, but repeated over the whole image."
  [w h]
  (let [size (max 9 (Math/round (* h 0.04)))]
    {:size size
     :angle (- (/ Math/PI 6))
     :step-x (Math/round (* size 1.5))
     :step-y (Math/round (* size 3.2))
     :fill [255 255 255 46]
     :outline [0 0 0 40]}))
