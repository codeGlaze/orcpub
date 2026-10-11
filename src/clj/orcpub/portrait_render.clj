(ns orcpub.portrait-render
  "Server-side rendering of a composed portrait to PNG, for the share card (og:image).
   Entry points: `render-png`, `site-mark`. Vector assets are drawn with pdf/svg-path-ops;
   raster assets are read off the classpath and tinted by `multiply!`.
   Every failure returns nil (no portrait) rather than throwing.
   Why: PORTRAIT-COMPOSITOR.md, \"Rendering on the server\"."
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [orcpub.fork.branding :as branding]
            [orcpub.pdf :as pdf]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.dnd.e5.portrait-layout :as layout])
  (:import [java.awt AlphaComposite BasicStroke Color Graphics2D RadialGradientPaint RenderingHints]
           [java.awt.geom Path2D$Double Point2D$Double]
           [java.awt.image BufferedImage]
           [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.util Base64]
           [javax.imageio IIOImage ImageIO ImageWriteParam]))

(def ^:private default-width 600)
(def ^:private default-height 750)   ;; 4:5, matching the on-screen frame

;; ---------- data URIs ----------

(defn parse-data-uri
  "Split a `data:<mime>;base64,<payload>` URI into {:mime :bytes}, or nil.

   Only base64 data URIs are handled: that is what the asset registry emits,
   and the raw-utf8 form would have to be re-parsed for percent-encoding."
  [uri]
  (when (string? uri)
    (when-let [[_ mime b64] (re-matches #"(?s)data:([^;,]+);base64,(.+)" uri)]
      (try
        {:mime mime :bytes (.decode (Base64/getDecoder) ^String b64)}
        (catch Exception _ nil)))))

(defn asset-source
  "{:mime :bytes} for an asset URL: a base64 data URI decoded in place, or a site-absolute
   path read off the classpath under public/. nil for anything else.
   An off-site URL is never fetched."
  [uri]
  (or (parse-data-uri uri)
      (when (and (string? uri) (s/starts-with? uri "/") (not (s/includes? uri "..")))
        (when-let [res (io/resource (str "public" uri))]
          (try
            (with-open [in (io/input-stream res)
                        out (ByteArrayOutputStream.)]
              (io/copy in out)
              {:mime (if (s/ends-with? (s/lower-case uri) ".svg")
                       "image/svg+xml"
                       "image/png")
               :bytes (.toByteArray out)})
            (catch Exception _ nil))))))

(defn- svg-view-box
  "The [w h] a path's coordinates are expressed in, defaulting to the
   registry's 400x500 when the document does not say."
  [svg]
  (or (when-let [[_ _ _ w h] (re-find #"viewBox\s*=\s*[\"']([\d.+-]+)\s+([\d.+-]+)\s+([\d.+-]+)\s+([\d.+-]+)[\"']" svg)]
        [(Double/parseDouble w) (Double/parseDouble h)])
      [400.0 500.0]))

;; ---------- colors ----------

(defn hex->color
  "#rrggbb to a Color, or nil. Unparseable input yields nil so a bad stored
   tint skips the layer instead of failing the render."
  [hex]
  (when (and (string? hex) (re-matches #"#[0-9a-fA-F]{6}" hex))
    (Color. (Integer/parseInt (subs hex 1 3) 16)
            (Integer/parseInt (subs hex 3 5) 16)
            (Integer/parseInt (subs hex 5 7) 16))))

;; ---------- one layer ----------

(defn ops->path2d
  "Build a Path2D from pdf/svg-path-ops output, scaled by sx/sy."
  [ops sx sy]
  (let [p (Path2D$Double.)]
    (doseq [op ops]
      (case (first op)
        :move  (let [[_ x y] op] (.moveTo p (* x sx) (* y sy)))
        :line  (let [[_ x y] op] (.lineTo p (* x sx) (* y sy)))
        :curve (let [[_ x1 y1 x2 y2 x y] op]
                 (.curveTo p (* x1 sx) (* y1 sy) (* x2 sx) (* y2 sy) (* x sx) (* y sy)))
        :close (.closePath p)
        nil))
    p))

(defn- draw-vector-layer!
  "A vector asset is a bare path with no colour of its own, so unlike a raster
   one it cannot be drawn `:as-drawn` -- there is nothing there to draw. A nil
   colour means the caller had no tint for it, and the layer is skipped rather
   than filled with whatever Graphics2D happens to be set to."
  [^Graphics2D g svg ^Color color w h]
  (when-let [d (and color (pdf/last-svg-path svg))]
    (let [[vw vh] (svg-view-box svg)
          path (ops->path2d (pdf/svg-path-ops d) (/ w vw) (/ h vh))]
      (.setColor g color)
      (.fill g path)
      ;; The registry's placeholder art strokes as well as fills, in the same
      ;; colour -- match it so the silhouette has the same weight on screen
      ;; and in a share card.
      (.setStroke g (BasicStroke. 2.0 BasicStroke/CAP_ROUND BasicStroke/JOIN_ROUND))
      (.draw g path))))

(defn- placed
  "The asset scaled into the frame the way every renderer places it."
  ^BufferedImage [^BufferedImage src w h]
  (let [out (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics out)
        [x y dw dh] (layout/contain-rect (.getWidth src) (.getHeight src) w h)]
    (try
      (.setRenderingHint g RenderingHints/KEY_INTERPOLATION
                         RenderingHints/VALUE_INTERPOLATION_BILINEAR)
      (.drawImage g src (int x) (int y) (int dw) (int dh) nil)
      (finally (.dispose g)))
    out))

(defn- multiply!
  "Tints `img` in place: out.rgb = colour.rgb * art.rgb, out.a = art.a. A pixel pass over
   the backing int[], because Java2D's AlphaComposite has no multiply.
   Why multiply and not a mask: PORTRAIT-TINTING.md."
  [^BufferedImage img ^Color colour]
  (let [dst (.. img getRaster getDataBuffer getData)
        tr (.getRed colour) tg (.getGreen colour) tb (.getBlue colour)
        n (* (.getWidth img) (.getHeight img))]
    (dotimes [i n]
      (let [argb (aget ^ints dst i)
            a (bit-and (unsigned-bit-shift-right argb 24) 0xff)]
        (when (pos? a)
          (let [r (bit-and (unsigned-bit-shift-right argb 16) 0xff)
                gg (bit-and (unsigned-bit-shift-right argb 8) 0xff)
                b (bit-and argb 0xff)]
            (aset-int dst i
                      (unchecked-int
                       (bit-or (bit-shift-left a 24)
                               (bit-shift-left (quot (* tr r) 255) 16)
                               (bit-shift-left (quot (* tg gg) 255) 8)
                               (quot (* tb b) 255))))))))))

(defn- draw-raster-layer!
  "Place the asset in the frame and tint it. A nil `color` draws it exactly as
   the illustrator made it."
  [^Graphics2D g ^bytes data ^Color color w h]
  (when-let [src (ImageIO/read (ByteArrayInputStream. data))]
    (let [img (placed src w h)]
      (when color (multiply! img color))
      (.drawImage g img 0 0 nil))))

(def credit-face "public/fonts/Vollkorn-Italic.ttf")
(def mark-face "public/fonts/Vollkorn-Regular.ttf")

(def ^:private load-face
  "The java.awt.Font at classpath `path`, parsed once per path; nil if it will not load.
   Faces ship with the app, so rendering does not depend on the host's fonts."
  (memoize
    (fn [path]
      (try
        (with-open [in (io/input-stream (io/resource path))]
          (java.awt.Font/createFont java.awt.Font/TRUETYPE_FONT in))
        (catch Exception e
          (println "portrait-render: could not load" path "-" (.getMessage e))
          nil)))))

(defn- face
  "`path` at `size`, falling back to the logical sans rather than losing the
   mark entirely if the file is missing from a build."
  [path size]
  (if-let [base (load-face path)]
    (.deriveFont ^java.awt.Font base (float size))
    (java.awt.Font. java.awt.Font/SANS_SERIF java.awt.Font/PLAIN (int size))))

(defn- draw-credit!
  "Draws `text` centred along the bottom of a `w`x`h` frame, outlined then filled, at
   layout/credit-layout's metrics. Why it is in the pixels: PORTRAIT-COMPOSITOR.md,
   \"The baked credit and the site mark\"."
  [^Graphics2D g text w h]
  (let [{:keys [size halo baseline]
         [or* og ob oa] :outline
         [fr fg fb fa] :fill} (layout/credit-layout w h)
        font (face credit-face size)
        fm (.getFontMetrics g font)
        tw (.stringWidth fm text)
        ;; centred on the measured string, which needs metrics the shared
        ;; layout has no way to get -- it gives the band, not the width.
        x (int (/ (- w tw) 2))
        y (int baseline)]
    (.setFont g font)
    (.setColor g (Color. (int or*) (int og) (int ob) (int oa)))
    (doseq [dx [(- halo) 0 halo]
            dy [(- halo) 0 halo]
            :when (not (and (zero? dx) (zero? dy)))]
      (.drawString g ^String text (int (+ x dx)) (int (+ y dy))))
    (.setColor g (Color. (int fr) (int fg) (int fb) (int fa)))
    (.drawString g ^String text x y)))

;; ---------- the portrait ----------

(defn site-mark
  "The domain to stamp down the edge of a shared portrait, or nil.

   Read from branding rather than written literally: app-url is empty by
   default and every fork overrides it, so a hardcoded domain would brand
   other people's deployments with ours. Empty means no mark."
  []
  (some-> branding/app-url
          s/trim
          not-empty
          (s/replace #"^https?://" "")
          (s/replace #"/+$" "")
          s/trim
          not-empty))

(defn- draw-site-mark!
  "The site's own mark, running up the right edge of a shared portrait.

   Deliberately on a different edge from the artist credit, and deliberately
   faint. Two marks in one caption band would give anyone who wants the
   advertising gone a reason to crop the artist's name off with it; on
   opposite edges the cheap crop takes this and leaves her."
  [^Graphics2D g text w h]
  (let [{:keys [size x]
         [or* og ob oa] :outline
         [fr fg fb fa] :fill} (layout/site-mark-layout w h)
        font (face mark-face size)
        fm (.getFontMetrics g font)
        tw (.stringWidth fm text)
        ;; centred on the measured string: the layout gives the edge, the
        ;; renderer knows how long the text turned out.
        y (int (/ (+ h tw) 2))
        saved (.getTransform g)]
    (try
      (.setFont g font)
      (.translate g (double x) (double y))
      ;; counter-clockwise, so it reads bottom-to-top like a book spine and
      ;; the glyphs hang to the left of the baseline, inside the picture
      (.rotate g (- (/ Math/PI 2)))
      (.setColor g (Color. (int or*) (int og) (int ob) (int oa)))
      (.drawString g ^String text 1 1)
      (.setColor g (Color. (int fr) (int fg) (int fb) (int fa)))
      (.drawString g ^String text 0 0)
      (finally (.setTransform g saved)))))

(defn render
  "Composite `portrait` ({:layers :colors :tweaks}) into a `w`x`h` ARGB BufferedImage, or
   nil when it selects nothing drawable. The credit and site mark are drawn in unless
   `marks?` is false."
  ([portrait] (render portrait default-width default-height))
  ([portrait w h] (render portrait w h {:marks? true}))
  ([portrait w h {:keys [marks?]}]
   (let [drawable (keep (fn [k]
                          (when-let [asset (some->> (get-in portrait [:layers k])
                                                    :asset/id
                                                    (pa/asset-by-id k))]
                            [k asset]))
                        pa/layer-order)]
     (when (seq drawable)
       (let [img (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
             g (.createGraphics img)]
         (try
           (.setRenderingHint g RenderingHints/KEY_ANTIALIASING
                              RenderingHints/VALUE_ANTIALIAS_ON)
           (.setRenderingHint g RenderingHints/KEY_STROKE_CONTROL
                              RenderingHints/VALUE_STROKE_PURE)
           (.setRenderingHint g RenderingHints/KEY_TEXT_ANTIALIASING
                              RenderingHints/VALUE_TEXT_ANTIALIAS_ON)
           (.setRenderingHint g RenderingHints/KEY_FRACTIONALMETRICS
                              RenderingHints/VALUE_FRACTIONALMETRICS_ON)
           (doseq [[layer-key asset] drawable]
             ;; One unreadable layer is skipped, not fatal -- the rest of the
             ;; portrait is still worth showing.
             (try
               (let [{:keys [mime bytes]} (asset-source (:asset/url asset))
                     ;; :as-drawn means the artist already coloured it -- teeth
                     ;; are white because they were drawn white, and tinting
                     ;; them through the mouth's category colour is what made
                     ;; them red.
                     as-drawn? (= :as-drawn (pa/render-mode layer-key asset))
                     color (when-not as-drawn?
                             (hex->color (pa/tint-for portrait layer-key)))]
                 (when (and mime bytes (or as-drawn? color))
                   (if (s/includes? mime "svg")
                     (draw-vector-layer! g (String. ^bytes bytes "UTF-8") color w h)
                     (draw-raster-layer! g bytes color w h))))
               (catch Exception e
                 (println "portrait-render: skipped layer" layer-key "-" (.getMessage e)))))
           (when marks?
             (try
               (when-let [credit (pa/credit-line portrait)]
                 (draw-credit! g credit w h))
               (when-let [mark (site-mark)]
                 (draw-site-mark! g mark w h))
               (catch Exception e
                 (println "portrait-render: marks skipped -" (.getMessage e)))))
           (finally (.dispose g)))
         img)))))

(defn render-png
  "PNG bytes for `portrait`, or nil when there is nothing to draw."
  ([portrait] (render-png portrait default-width default-height))
  ([portrait w h]
   (try
     (when-let [img (render portrait w h)]
       (let [out (ByteArrayOutputStream.)]
         (when (ImageIO/write img "png" out)
           (.toByteArray out))))
     (catch Exception e
       (println "portrait-render: render failed -" (.getMessage e))
       nil))))

;; ---------- example images for the artist pages ----------

(defn- halve-toward
  "`img` scaled by bilinear steps of at most half until it is `w`x`h`, so a large
   reduction does not skip pixels the way one bilinear step would."
  ^BufferedImage [^BufferedImage img w h]
  (loop [^BufferedImage src img]
    (if (and (= w (.getWidth src)) (= h (.getHeight src)))
      src
      (let [nw (max w (quot (.getWidth src) 2))
            nh (max h (quot (.getHeight src) 2))
            dst (BufferedImage. nw nh BufferedImage/TYPE_INT_ARGB)
            g (.createGraphics dst)]
        (try
          (.setRenderingHint g RenderingHints/KEY_INTERPOLATION
                             RenderingHints/VALUE_INTERPOLATION_BILINEAR)
          (.drawImage g src 0 0 (int nw) (int nh) nil)
          (finally (.dispose g)))
        (recur dst)))))

(defn- rgb
  "An [r g b] vector as an opaque java.awt.Color."
  [[r g b]]
  (Color. (int r) (int g) (int b)))

(defn- rgba
  "An [r g b a] vector, a out of 255, as a java.awt.Color."
  [[r g b a]]
  (Color. (int r) (int g) (int b) (int a)))

(defn- paint-ground!
  "Fills a `w`x`h` frame with layout/example-ground, the portrait frame's gradient."
  [^Graphics2D g w h]
  (let [{:keys [centre mid edge]} layout/example-ground
        cx (/ w 2.0) cy (* h 0.35)
        radius (Math/hypot (max cx (- w cx)) (max cy (- h cy)))]
    (.setPaint g (RadialGradientPaint. (Point2D$Double. cx cy) (float radius)
                                       (float-array [0.0 0.6 1.0])
                                       (into-array Color [(rgb centre) (rgb mid) (rgb edge)])))
    (.fillRect g 0 0 (int w) (int h))))

(defn- draw-watermark!
  "Tiles `text` across the whole `w`x`h` frame in diagonal rows, at
   layout/example-watermark-layout's metrics, alternate rows offset by half a repeat."
  [^Graphics2D g ^String text w h]
  (let [{:keys [size angle step-x step-y fill outline]} (layout/example-watermark-layout w h)
        font (face mark-face size)
        repeat-w (+ (.stringWidth (.getFontMetrics g font) text) step-x)
        reach (int (Math/hypot w h))
        saved (.getTransform g)]
    (try
      (.setFont g font)
      (.rotate g (double angle) (/ w 2.0) (/ h 2.0))
      (doseq [[row y] (map-indexed vector (range (- reach) (* 2 reach) step-y))
              x (range (- (- reach) (if (odd? row) (quot repeat-w 2) 0)) (* 2 reach) repeat-w)]
        (.setColor g (rgba outline))
        (.drawString g text (int (inc x)) (int (inc y)))
        (.setColor g (rgba fill))
        (.drawString g text (int x) (int y)))
      (finally (.setTransform g saved)))))

(defn- jpeg-bytes
  "`img` as JPEG bytes at `quality` (0 to 1)."
  ^bytes [^BufferedImage img quality]
  (let [writer (.next (ImageIO/getImageWritersByFormatName "jpeg"))
        param (doto (.getDefaultWriteParam writer)
                (.setCompressionMode ImageWriteParam/MODE_EXPLICIT)
                (.setCompressionQuality (float quality)))
        out (ByteArrayOutputStream.)]
    (with-open [ios (ImageIO/createImageOutputStream out)]
      (.setOutput writer ios)
      (.write writer nil (IIOImage. img nil nil) param)
      (.dispose writer))
    (.toByteArray out)))

(def example-quality
  "JPEG quality of an artist page's example: lossy on purpose."
  0.72)

(defn render-example-jpeg
  "JPEG bytes of `portrait` as an artist page shows it: flattened onto the frame's ground
   at layout/example-size, with `watermark` tiled across it and no credit line. nil when
   there is nothing to draw or the render fails."
  [portrait watermark]
  (try
    (when-let [full (render portrait default-width default-height {:marks? false})]
      (let [[w h] layout/example-size
            out (BufferedImage. w h BufferedImage/TYPE_INT_RGB)
            g (.createGraphics out)]
        (try
          (doto g
            (.setRenderingHint RenderingHints/KEY_ANTIALIASING RenderingHints/VALUE_ANTIALIAS_ON)
            (.setRenderingHint RenderingHints/KEY_TEXT_ANTIALIASING
                               RenderingHints/VALUE_TEXT_ANTIALIAS_ON))
          (paint-ground! g w h)
          (.drawImage g (halve-toward full w h) 0 0 nil)
          (when-not (s/blank? watermark) (draw-watermark! g watermark w h))
          (finally (.dispose g)))
        (jpeg-bytes out example-quality)))
    (catch Exception e
      (println "portrait-render: example failed -" (.getMessage e))
      nil)))

