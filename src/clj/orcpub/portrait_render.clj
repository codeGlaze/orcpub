(ns orcpub.portrait-render
  "Server-side rasterization of a composed paper-doll portrait.

   The PDF path gets its picture from the browser, which bakes the layers with
   canvas before posting them. A share crawler has no browser: it reads
   og:image out of the page HTML and fetches that URL, so the image has to be
   rendered here.

   No new dependency is needed. Vector assets reuse pdf/svg-path-ops -- the
   same `d`-attribute parser the card icons are drawn with -- and its
   [:move]/[:line]/[:curve]/[:close] output maps directly onto a Java2D
   Path2D. Raster assets -- the illustrator's own layer art, read off the
   classpath under resources/public -- are tinted with AlphaComposite/SrcIn,
   which is the exact server-side equivalent of the canvas 'source-in' trick
   the client uses.

   Every failure degrades to 'no portrait' rather than throwing: a share card
   without a picture is a state the page already handles, and it must not cost
   the character their page."
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [orcpub.fork.branding :as branding]
            [orcpub.pdf :as pdf]
            [orcpub.dnd.e5.portrait-assets :as pa])
  (:import [java.awt AlphaComposite BasicStroke Color Graphics2D RenderingHints]
           [java.awt.geom Path2D$Double]
           [java.awt.image BufferedImage]
           [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.util Base64]
           [javax.imageio ImageIO]))

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
  "Bytes for an asset URL, plus the mime that decides how to draw it.

   The registry moved from inline data URIs to real files under
   resources/public once the illustrator's inventory landed, so this resolves
   both: a data URI is decoded in place, and a site-absolute path is read off
   the classpath. Anything else -- an off-site URL especially -- returns nil,
   because rendering a share card must never become a way to make the server
   fetch arbitrary URLs."
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

(defn- draw-vector-layer! [^Graphics2D g svg ^Color color w h]
  (when-let [d (pdf/last-svg-path svg)]
    (let [[vw vh] (svg-view-box svg)
          path (ops->path2d (pdf/svg-path-ops d) (/ w vw) (/ h vh))]
      (.setColor g color)
      (.fill g path)
      ;; The registry's placeholder art strokes as well as fills, in the same
      ;; colour -- match it so the silhouette has the same weight on screen
      ;; and in a share card.
      (.setStroke g (BasicStroke. 2.0 BasicStroke/CAP_ROUND BasicStroke/JOIN_ROUND))
      (.draw g path))))

(defn contain-rect
  "Where a `sw`x`sh` asset lands inside a `w`x`h` frame under CSS
   `mask-size: contain` -- scaled to fit, centred, aspect kept.

   The browser composites with `contain`; both rasterizers used to stretch to
   the frame instead, so a shared portrait came out a different shape from the
   one the drawer showed. With 8:11 art in a 4:5 frame that is a 10% widening
   of every face."
  [sw sh w h]
  (if (or (zero? sw) (zero? sh))
    [0 0 w h]
    (let [scale (min (/ (double w) sw) (/ (double h) sh))
          dw (Math/round (* sw scale))
          dh (Math/round (* sh scale))]
      [(Math/round (/ (- w dw) 2.0)) (Math/round (/ (- h dh) 2.0)) dw dh])))

(defn- draw-raster-layer! [^Graphics2D g ^bytes data ^Color color w h]
  (when-let [src (ImageIO/read (ByteArrayInputStream. data))]
    ;; Tint through the source's alpha: draw it, then flood the colour with
    ;; SrcIn so it lands only where the asset is opaque. Server-side twin of
    ;; the canvas 'source-in' composite the client uses.
    (let [tinted (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
          tg (.createGraphics tinted)
          [x y dw dh] (contain-rect (.getWidth src) (.getHeight src) w h)]
      (try
        (.setRenderingHint tg RenderingHints/KEY_INTERPOLATION
                           RenderingHints/VALUE_INTERPOLATION_BILINEAR)
        (.drawImage tg src (int x) (int y) (int dw) (int dh) nil)
        (.setComposite tg AlphaComposite/SrcIn)
        (.setColor tg color)
        (.fillRect tg 0 0 w h)
        (finally (.dispose tg)))
      (.drawImage g tinted 0 0 nil))))

(defn- draw-credit!
  "Burn the artist credit into the picture itself.

   The page and the sheet can both carry a credit beside the portrait, but the
   composed image is what people actually pass around -- saved off a share
   card, pulled out of a PDF -- and it arrives detached from either. A caption
   in the pixels travels with it.

   Dark fill under a white outline, because the background is unknowable: the
   PNG is transparent, so it may land on a white page or a dark chat client,
   and one of the two always reads. Not a watermark -- a crop removes it --
   just a credit that survives being right-click-saved."
  [^Graphics2D g text w h]
  (let [size (max 9 (int (* h 0.026)))
        font (java.awt.Font. java.awt.Font/SANS_SERIF java.awt.Font/PLAIN size)
        fm (.getFontMetrics g font)
        tw (.stringWidth fm text)
        x (int (/ (- w tw) 2))
        y (int (- h (max 4 (* h 0.018))))
        halo (max 1 (int (/ size 12)))]
    (.setFont g font)
    (.setColor g (Color. 255 255 255 220))
    (doseq [dx [(- halo) 0 halo]
            dy [(- halo) 0 halo]
            :when (not (and (zero? dx) (zero? dy)))]
      (.drawString g ^String text (int (+ x dx)) (int (+ y dy))))
    (.setColor g (Color. 20 20 20 235))
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
  (let [size (max 8 (int (* h 0.020)))
        font (java.awt.Font. java.awt.Font/SANS_SERIF java.awt.Font/PLAIN size)
        fm (.getFontMetrics g font)
        tw (.stringWidth fm text)
        x (- w (max 4 (int (* w 0.022))))
        y (int (/ (+ h tw) 2))
        saved (.getTransform g)]
    (try
      (.setFont g font)
      (.translate g (double x) (double y))
      ;; counter-clockwise, so it reads bottom-to-top like a book spine and
      ;; the glyphs hang to the left of the baseline, inside the picture
      (.rotate g (- (/ Math/PI 2)))
      (.setColor g (Color. 255 255 255 90))
      (.drawString g ^String text 1 1)
      (.setColor g (Color. 20 20 20 128))
      (.drawString g ^String text 0 0)
      (finally (.setTransform g saved)))))

(defn render
  "Composite `portrait` ({:layers :colors :tweaks}) into a BufferedImage, or
   nil when it selects nothing drawable."
  ([portrait] (render portrait default-width default-height))
  ([portrait w h]
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
           (doseq [[layer-key asset] drawable]
             ;; One unreadable layer is skipped, not fatal -- the rest of the
             ;; portrait is still worth showing.
             (try
               (let [{:keys [mime bytes]} (asset-source (:asset/url asset))
                     color (hex->color (pa/tint-for portrait layer-key))]
                 (when (and mime bytes color)
                   (if (s/includes? mime "svg")
                     (draw-vector-layer! g (String. ^bytes bytes "UTF-8") color w h)
                     (draw-raster-layer! g bytes color w h))))
               (catch Exception e
                 (println "portrait-render: skipped layer" layer-key "-" (.getMessage e)))))
           (try
             (when-let [credit (pa/credit-line portrait)]
               (draw-credit! g credit w h))
             (when-let [mark (site-mark)]
               (draw-site-mark! g mark w h))
             (catch Exception e
               (println "portrait-render: marks skipped -" (.getMessage e))))
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
