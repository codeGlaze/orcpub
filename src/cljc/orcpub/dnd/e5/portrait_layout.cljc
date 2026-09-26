(ns orcpub.dnd.e5.portrait-layout
  "Where the pieces of a composed portrait land.

   THREE renderers draw the same picture and cannot share drawing code: the
   drawer stacks CSS-masked divs in the DOM, the export bakes a canvas in the
   browser, and the share card is composed in Java2D because a crawler runs no
   JavaScript. What they can share is every NUMBER, and until this namespace
   existed they did not.

   Each divergence cost a bug or a visible difference:

     * both rasterizers stretched each layer to fill the frame while the DOM
       composites with `mask-size: contain`, so a shared portrait and a printed
       sheet came out 10% wider than the face the drawer showed;
     * the credit was sized with `round` in the browser and `int` on the server,
       so at a 750px frame it was set at 20px with a 2px halo in a PDF and 19px
       with a 1px halo on a share card -- the same caption, tuned twice.

   So the geometry lives here once, and each renderer asks rather than derives.
   Nothing in here draws; it returns numbers, in frame pixels.")

(defn contain-rect
  "Where a `sw`x`sh` asset lands in a `w`x`h` frame under `mask-size: contain`
   -- scaled to fit, centred, aspect kept. Returns [x y w h].

   This is the DOM's behaviour, and the DOM is the version a person is looking
   at while they choose, so it is the one the other two have to match."
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
  "The family the baked credit is set in. The browser reaches it through an
   @font-face and the server loads the same file off the classpath, but the name
   is one fact."
  "Vollkorn")

(defn credit-layout
  "Metrics for the artist credit burned along the bottom of a `w`x`h` frame.

   `:outline` and `:fill` are [r g b a] with a out of 255, because Java2D wants
   it that way and a canvas can divide. Dark fill under a light outline: the PNG
   is transparent, so it may land on a white page or a dark chat client, and one
   of the two always reads.

   Rounded, not truncated -- the browser rounded and the server truncated, and
   the browser is what someone watched while they picked."
  [w h]
  (let [size (max 9 (Math/round (* h 0.026)))]
    {:size size
     :halo (max 1 (Math/round (/ (double size) 12)))
     :center-x (/ (double w) 2)
     :baseline (- h (max 4 (Math/round (* h 0.018))))
     :outline [255 255 255 220]
     :fill [20 20 20 235]}))

(defn site-mark-layout
  "Metrics for the site's own mark running up the RIGHT edge of a `w`x`h` frame.

   Deliberately on a different edge from the artist credit: two marks in one
   caption band would give anyone who wants the advertising gone a reason to
   crop her name off with it. On opposite edges the cheap crop takes this and
   leaves her.

   `:x` is the baseline's distance from the left. Where it starts vertically
   depends on the rendered text width, which only a renderer with font metrics
   knows, so that stays with the renderer."
  [w h]
  {:size (max 8 (Math/round (* h 0.020)))
   :x (- w (max 4 (Math/round (* w 0.022))))
   :outline [255 255 255 90]
   :fill [20 20 20 128]})

(defn alpha->unit
  "An [r g b a] alpha, as a canvas wants it."
  [[_ _ _ a]]
  (/ (double a) 255))
