(ns orcpub.portrait-render
  "Server-side rasterization of a composed paper-doll portrait.

   The PDF path gets its picture from the browser, which bakes the layers with
   canvas before posting them. A share crawler has no browser: it reads
   og:image out of the page HTML and fetches that URL, so the image has to be
   rendered here.

   No new dependency is needed. Vector assets reuse pdf/svg-path-ops -- the
   same `d`-attribute parser the card icons are drawn with -- and its
   [:move]/[:line]/[:curve]/[:close] output maps directly onto a Java2D
   Path2D. Raster assets (what the real illustrator's art will be) are tinted
   with AlphaComposite/SrcIn, which is the exact server-side equivalent of the
   canvas 'source-in' trick the client uses.

   Every failure degrades to 'no portrait' rather than throwing: a share card
   without a picture is a state the page already handles, and it must not cost
   the character their page."
  (:require [clojure.string :as s]
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

(defn- draw-raster-layer! [^Graphics2D g ^bytes data ^Color color w h]
  (when-let [src (ImageIO/read (ByteArrayInputStream. data))]
    ;; Tint through the source's alpha: draw it, then flood the colour with
    ;; SrcIn so it lands only where the asset is opaque. Server-side twin of
    ;; the canvas 'source-in' composite the client uses.
    (let [tinted (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
          tg (.createGraphics tinted)]
      (try
        (.setRenderingHint tg RenderingHints/KEY_INTERPOLATION
                           RenderingHints/VALUE_INTERPOLATION_BILINEAR)
        (.drawImage tg src 0 0 w h nil)
        (.setComposite tg AlphaComposite/SrcIn)
        (.setColor tg color)
        (.fillRect tg 0 0 w h)
        (finally (.dispose tg)))
      (.drawImage g tinted 0 0 nil))))

;; ---------- the portrait ----------

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
               (let [{:keys [mime bytes]} (parse-data-uri (:asset/url asset))
                     color (hex->color (pa/tint-for portrait layer-key))]
                 (when (and mime bytes color)
                   (if (s/includes? mime "svg")
                     (draw-vector-layer! g (String. ^bytes bytes "UTF-8") color w h)
                     (draw-raster-layer! g bytes color w h))))
               (catch Exception e
                 (println "portrait-render: skipped layer" layer-key "-" (.getMessage e)))))
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
