(ns orcpub.portrait-render
  "Server-side rendering of a composed portrait to PNG, for the share card (og:image).
   Entry points: `render-png`, `site-mark`. Vector assets are drawn with pdf/svg-path-ops;
   raster assets are read off the classpath and tinted by `multiply!`.
   Every failure returns nil (no portrait) rather than throwing.
   Why: PORTRAIT-COMPOSITOR.md, \"Rendering on the server\"."
  (:require [orcpub.env :as env]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [orcpub.fork.branding :as branding]
            [orcpub.pdf.draw :as draw]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.dnd.e5.portrait-layout :as layout]
            [orcpub.dnd.e5.portrait-colorize :as colorize]
            [orcpub.dnd.e5.portrait-effects :as fx]
            [orcpub.bounded-cache :as bounded-cache]
            [orcpub.art-mark :as art-mark]
            [orcpub.portrait-pack.strands :as strands]
            [orcpub.dnd.e5.portrait-face :as face]
            [orcpub.dnd.e5.portrait-light :as light])
  (:import [java.awt AlphaComposite BasicStroke Color Graphics2D RadialGradientPaint RenderingHints]
           [java.awt.geom AffineTransform Area Ellipse2D$Double Path2D$Double Point2D$Double]
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
              {:mime (if (s/ends-with? (.toLowerCase (str uri) java.util.Locale/ROOT) ".svg")
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
  "Build a Path2D from draw/svg-path-ops output, scaled by sx/sy."
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
  (when-let [d (and color (draw/last-svg-path svg))]
    (let [[vw vh] (svg-view-box svg)
          path (ops->path2d (draw/svg-path-ops d) (/ w vw) (/ h vh))]
      (.setColor g color)
      (.fill g path)
      ;; The registry's placeholder art strokes as well as fills, in the same
      ;; colour -- match it so the silhouette has the same weight on screen
      ;; and in a share card.
      (.setStroke g (BasicStroke. 2.0 BasicStroke/CAP_ROUND BasicStroke/JOIN_ROUND))
      (.draw g path))))

(def ^:private dev-solid?
  "A developer tool, off in production: draw each piece with its inside made
   solid, to preview art whose fill is not quite opaque. The fix belongs in
   the art (the Loom flags it and can fill it on export). On when the server
   runs with PORTRAIT_DEV_SOLID=1."
  (delay (= "1" (env/value :portrait-dev-solid))))

(defn- solid
  "A copy of the art with its inside made fully opaque (portrait-colorize/
   solid-mask); the soft edge is left as drawn. The art itself is untouched."
  ^BufferedImage [^BufferedImage src]
  (let [w (.getWidth src) h (.getHeight src)
        out (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics out)
        _ (do (.drawImage g src 0 0 nil) (.dispose g))
        ^ints d (.. out getRaster getDataBuffer getData)
        ^bytes m (colorize/solid-mask (fn [i] (bit-and (unsigned-bit-shift-right (aget d (int i)) 24) 0xff)) w h)]
    (dotimes [i (* w h)]
      (when (pos? (aget m i))
        (aset d i (unchecked-int (bit-or (bit-and (aget d i) 0xffffff) (unchecked-int 0xff000000))))))
    out))

(defn- placed
  "The asset scaled into the frame the way every renderer places it, its
   inside made solid first."
  ^BufferedImage [^BufferedImage src0 w h]
  (let [src (if @dev-solid? (solid src0) src0)
        out (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
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

(defn- ellipse ^Area [{:keys [cx cy rx ry rot]}]
  (Area. (.createTransformedShape
          (doto (AffineTransform.) (.translate (double cx) (double cy)) (.rotate (double rot)))
          (Ellipse2D$Double. (- rx) (- ry) (* 2 rx) (* 2 ry)))))

(defn- above-lid ^Area [{:keys [x0 y0 mx my x1 y1 top]}]
  (Area. (doto (Path2D$Double.)
           (.moveTo (double x0) (double y0))
           (.quadTo (double mx) (double my) (double x1) (double y1))
           (.lineTo (double x1) (double top))
           (.lineTo (double x0) (double top))
           (.closePath))))

(defn- iris-coverage
  "How much of each pixel the placed iris region covers, 0..255, filled with
   antialiasing on and then feathered inward (portrait-colorize/feather), so
   the edge is a soft blend weight rather than a stair-step or a seam."
  ^doubles [asset rect w h]
  (let [region (Area.)]
    (doseq [{:keys [iris pupil lid]} (colorize/iris-shapes asset rect)]
      (let [a (ellipse iris)]
        (.subtract a (ellipse pupil))
        (when lid (.subtract a (above-lid lid)))
        (.add region a)))
    (let [img (BufferedImage. w h BufferedImage/TYPE_BYTE_GRAY)
          g (.createGraphics img)]
      (try
        (.setRenderingHint g RenderingHints/KEY_ANTIALIASING RenderingHints/VALUE_ANTIALIAS_ON)
        (.setColor g Color/WHITE)
        (.fill g region)
        (finally (.dispose g)))
      (let [^bytes raw (.. img getRaster getDataBuffer getData)
            cov (double-array (* w h))]
        (dotimes [i (* w h)] (aset cov i (double (bit-and (aget raw i) 0xff))))
        ;; faded inward over a radius set by how tall the asset is drawn
        (colorize/feather cov w h (colorize/feather-radius (nth rect 3)))))))

(defn- colorize!
  "Map the drawing's luminance through `color` wherever `cov` covers it, in
   place (orcpub.dnd.e5.portrait-colorize). nil `cov` means every opaque
   pixel: the lips, which are nothing but the thing being coloured."
  [^BufferedImage img ^Color color gamma cov]
  (let [dst (.. img getRaster getDataBuffer getData)
        er (.getRed color) eg (.getGreen color) eb (.getBlue color)
        gamma (double gamma)]
    (dotimes [i (* (.getWidth img) (.getHeight img))]
      (let [argb (aget ^ints dst i)
            a (bit-and (unsigned-bit-shift-right argb 24) 0xff)
            ;; irises by their placed region; lips by how solid the stroke is
            k (if cov (/ (aget ^doubles cov i) 255.0) (colorize/lip-weight a))]
        (when (and (pos? a) (pos? k))
          (let [[r g b] (colorize/colorize-rgb (bit-and (unsigned-bit-shift-right argb 16) 0xff)
                                               (bit-and (unsigned-bit-shift-right argb 8) 0xff)
                                               (bit-and argb 0xff)
                                               er eg eb gamma k)]
            (aset-int dst i (unchecked-int (bit-or (bit-shift-left a 24)
                                                   (bit-shift-left r 16)
                                                   (bit-shift-left g 8)
                                                   b)))))))))

(defn- draw-whites!
  "Fill the whites of an eye style drawn without them, under the eye art."
  [^Graphics2D g ^bytes data asset ^Color color w h]
  (when-let [src (ImageIO/read (ByteArrayInputStream. data))]
    (let [rect (layout/contain-rect (.getWidth src) (.getHeight src) w h)]
      (doseq [[[x0 y0] [mx my x1 y1] [lx1 ly1] [lmx lmy lx0 ly0]] (colorize/whites-outlines asset rect)]
        (.setColor g color)
        (.fill g (doto (Path2D$Double.)
                   (.moveTo (double x0) (double y0))
                   (.quadTo (double mx) (double my) (double x1) (double y1))
                   (.lineTo (double lx1) (double ly1))
                   (.quadTo (double lmx) (double lmy) (double lx0) (double ly0))
                   (.closePath)))))))

(defn colorizes?
  "Whether this asset is drawn by colorizing. An eye style with no placed
   region is multiplied instead -- colouring the whole asset would paint the
   whites and the lashes too."
  [layer-key asset]
  (and (= :colorize (pa/render-mode layer-key asset))
       (or (seq (:asset/iris asset))
           (not= :eyes (pa/slot-for-asset layer-key asset)))))

(defn- colorize-eyes!
  "The eyes: each iris coloured through its luminance in its own colour, lit
   from below, with the pupil the portrait asks for (portrait-face)."
  [^BufferedImage img portrait asset ^Color color gamma ^doubles cov rect]
  (let [w (.getWidth img) h (.getHeight img)
        ^ints d (.. img getRaster getDataBuffer getData)
        {:keys [second-eye pupil]} (face/face-settings portrait)
        f (face/eye-pixel-fn (colorize/iris-shapes asset rect)
                             [(.getRed color) (.getGreen color) (.getBlue color)]
                             (some-> second-eye colorize/hex->rgb)
                             gamma pupil)
        all? (not= pupil :round)]
    (dotimes [i (* w h)]
      (let [argb (aget d i)
            a (bit-and (unsigned-bit-shift-right argb 24) 0xff)
            k (/ (aget cov i) 255.0)]
        ;; a new pupil paints where the drawn one is, outside the iris region
        (when (and (pos? a) (or all? (pos? k)))
          (let [[r g b] (f (rem i w) (quot i w)
                           [(bit-and (unsigned-bit-shift-right argb 16) 0xff)
                            (bit-and (unsigned-bit-shift-right argb 8) 0xff)
                            (bit-and argb 0xff)]
                           k)]
            (aset d i (unchecked-int (bit-or (bit-shift-left a 24) (bit-shift-left (int r) 16)
                                             (bit-shift-left (int g) 8) (int b))))))))))

(defn- draw-colorized-layer!
  "Place the asset in the frame and colour it through its luminance: the iris
   inside its placed region, the lips all over."
  [^Graphics2D g ^bytes data portrait layer-key asset ^Color color w h]
  (when-let [src (ImageIO/read (ByteArrayInputStream. data))]
    (let [img (placed src w h)
          rect (layout/contain-rect (.getWidth src) (.getHeight src) w h)
          cov (when (seq (:asset/iris asset)) (iris-coverage asset rect w h))
          gamma (pa/effective-gamma portrait layer-key asset)]
      (if (and cov (= :eyes (pa/slot-for-asset layer-key asset)))
        (colorize-eyes! img portrait asset color gamma cov rect)
        (colorize! img color gamma cov))
      (.drawImage g img 0 0 nil))))

;; ---------- hair ombre and cast shadows (portrait-effects) ----------

(defn- alpha-fn
  "Alpha (0..255) at a pixel index of an ARGB image."
  [^BufferedImage img]
  (let [^ints d (.. img getRaster getDataBuffer getData)]
    (fn [i] (bit-and (unsigned-bit-shift-right (aget d (int i)) 24) 0xff))))

(defn- placed-asset
  "An asset's raster placed in the frame, or nil for a vector or unreadable one."
  [asset w h]
  (let [{:keys [mime bytes]} (asset-source (:asset/url asset))]
    (when (and bytes (not (s/includes? (str mime) "svg")))
      (some-> (ImageIO/read (ByteArrayInputStream. bytes)) (placed w h)))))

(defn- hair-crown
  "Where the hair grows from, for this portrait's head, in frame pixels."
  [portrait placed-of w h]
  (when-let [head (pa/selected-asset portrait :head)]
    (when-let [img (placed-of head)]
      (fx/crown (alpha-fn img) w h))))

(def ^:private strand-fields
  "Parsed strand fields by art URL."
  (bounded-cache/cache {:max-entries 64}))

(defn- strand-field-of
  "The piece's strand field (portrait-effects/strand-field) in art pixels, from the .strands.png
   beside the art or, when none was deployed, the one worked out from the art
   (orcpub.portrait-pack.strands/worked-out-png): the same bytes either way, so a deploy without
   the build step draws the same picture."
  [url ^BufferedImage src _layer-key]
  (bounded-cache/fetch! strand-fields url
                        #(when-let [bytes (or (some-> (fx/strands-url url) asset-source :bytes)
                                              (strands/worked-out-png url))]
                           (when-let [^BufferedImage img (ImageIO/read (ByteArrayInputStream. bytes))]
                             (let [gw (.getWidth img) gh (.getHeight img) r (.getRaster img)]
                               (fx/bytes->field (fn [g] (.getSample r (int (mod g gw)) (int (quot g gw)) 0))
                                                gw gh (.getWidth src) (.getHeight src)))))))

(defn- draw-ombre-layer!
  "A hair piece: coloured root to tip (portrait-effects/ombre-rgb) instead of
   one flat colour, the linework kept."
  [^Graphics2D g ^bytes data portrait layer-key ^Color root crown w h]
  (when-let [src (ImageIO/read (ByteArrayInputStream. data))]
    (let [img (placed src w h)
          ^ints d (.. img getRaster getDataBuffer getData)
          {:keys [tip angle] :as settings} (fx/piece-settings (fx/ombre-settings portrait)
                                                              (pa/selected-asset portrait layer-key) layer-key)
          root-rgb [(.getRed root) (.getGreen root) (.getBlue root)]
          [root-rgb tip-rgb] (fx/layer-colours layer-key root-rgb (or (some-> tip colorize/hex->rgb) root-rgb) settings)
          shine? (and (pos? (:shine settings)) (fx/shine-layers layer-key))
          frame (fx/gradient-frame (alpha-fn img) w h crown (fx/run-mode settings) shine?)
          ;; streaks only show where there is a second colour to streak; the
          ;; shine breaks along the strands too
          field (when (or shine? (and tip (pos? (:clumps settings)) (not (:split settings))))
                  (assoc (strand-field-of (:asset/url (pa/selected-asset portrait layer-key)) src layer-key)
                         :rect (layout/contain-rect (.getWidth src) (.getHeight src) w h)))
          pix (fx/pixel-fn root-rgb tip-rgb settings frame layer-key field)]
      (dotimes [i (* w h)]
        (let [argb (aget d i)
              a (bit-and (unsigned-bit-shift-right argb 24) 0xff)]
          (when (pos? a)
            (let [rgb (.invokePrim ^clojure.lang.IFn$LLLL pix (rem i w) (quot i w)
                                   (bit-and argb 0xffffff))]
              (aset d i (unchecked-int (bit-or (bit-shift-left a 24) rgb)))))))
      (.drawImage g img 0 0 nil))))

(defn- combined-alpha
  "The alphas of these layers' selected assets, combined (max), as doubles."
  ^doubles [portrait placed-of layer-keys pred w h]
  (let [out (double-array (* w h))]
    (doseq [k layer-keys
            :let [asset (pa/selected-asset portrait k)]
            :when (and asset (pred asset))
            :let [img (placed-of asset)]
            :when img]
      (let [a (alpha-fn img)]
        (dotimes [i (* w h)]
          (aset out i (max (aget out i) (double (a i)))))))
    out))

(defn- asset-rect
  "Where an asset's art lands in a `w` x `h` frame."
  [asset w h]
  (when-let [{:keys [bytes]} (asset-source (:asset/url asset))]
    (when-let [^BufferedImage src (ImageIO/read (ByteArrayInputStream. bytes))]
      (layout/contain-rect (.getWidth src) (.getHeight src) w h))))

(defn- shade-skin!
  "Multiply what lands on the skin after every layer is down: the shadow the
   overhanging hair casts, then blush and freckles (portrait-face). All of it
   confined to skin that shows."
  [^BufferedImage img portrait placed-of w h]
  (let [casts? (some #(some-> (pa/selected-asset portrait %) fx/casts-shadow?) pa/layer-order)
        fs (face/face-settings portrait)
        eyes (pa/selected-asset portrait :eyes)
        marks? (and (face/marks? fs) (seq (:asset/iris eyes)))
        shade? (pos? (:shade fs))]
    (when (or casts? marks? shade?)
      (let [^doubles skin (combined-alpha portrait placed-of [:head :ears] any? w h)
            ^doubles hair (combined-alpha portrait placed-of [:hair-front :bangs] any? w h)
            ^doubles k (when casts?
                (fx/shadow-map (combined-alpha portrait placed-of pa/layer-order fx/casts-shadow? w h)
                               skin hair w h (:light (fx/ombre-settings portrait))))
            m (when marks?
                (let [^doubles eye-alpha (combined-alpha portrait placed-of [:eyes] any? w h)
                      showing (double-array (* w h))]
                  (dotimes [i (* w h)]
                    (aset showing i (* (/ (aget skin i) 255.0)
                                       (- 1.0 (/ (max (aget hair i) (aget eye-alpha i)) 255.0)))))
                  (when-let [rect (asset-rect eyes w h)]
                    (face/marks fs (colorize/iris-shapes eyes rect) showing w h
                                (face/alpha-box (combined-alpha portrait placed-of [:nose] any? w h) w h)))))
            ^doubles ks (when shade?
                          (let [showing (double-array (* w h))]
                            (dotimes [i (* w h)]
                              (aset showing i (* (/ (aget skin i) 255.0) (- 1.0 (/ (aget hair i) 255.0)))))
                            (face/shade-map fs showing hair w h)))
            ^doubles mb (:blush m) ^doubles mf (:freckles m)
            blush-rgb (colorize/hex->rgb (:blush-colour fs))
            ^ints d (.. img getRaster getDataBuffer getData)]
        (dotimes [i (* w h)]
          (let [kk (+ (if k (aget k i) 0.0) (if ks (aget ks i) 0.0))
                kb (if mb (aget mb i) 0.0)
                kf (if mf (aget mf i) 0.0)]
            (when (or (pos? kk) (pos? kb) (pos? kf))
              (let [argb (aget d i)
                    [sr sg sb] (fx/shadow-factors kk)
                    [mr mg mbb] (face/mark-factors kb kf blush-rgb)
                    ch (fn [shift f] (int (* f (bit-and (unsigned-bit-shift-right argb shift) 0xff))))]
                (aset-int d i (unchecked-int
                               (bit-or (bit-and argb (unchecked-int 0xff000000))
                                       (bit-shift-left (ch 16 (* sr mr)) 16)
                                       (bit-shift-left (ch 8 (* sg mg)) 8)
                                       (ch 0 (* sb mbb)))))))))))))

(defn- light!
  "Glowing eyes and mood light over the finished picture (portrait-light)."
  [^BufferedImage img portrait drawable placed-of w h]
  (let [ls (light/light-settings portrait)]
    (when (light/lit? ls)
      (let [n (* w h)
            ^ints d (.. img getRaster getDataBuffer getData)
            alpha (double-array n)
            ink (double-array n)]
        ;; the drawing itself, uncoloured, top piece wins
        (doseq [[_ asset] drawable
                :let [^BufferedImage pi (placed-of asset)]
                :when pi]
          (let [^ints pd (.. pi getRaster getDataBuffer getData)]
            (dotimes [i n]
              (let [v (aget pd i) a (bit-and (unsigned-bit-shift-right v 24) 0xff)]
                (when (pos? a)
                  (aset alpha i (max (aget alpha i) (double a)))
                  (when (> a 128)
                    (aset ink i (/ (+ (bit-and (bit-shift-right v 16) 0xff) (bit-and (bit-shift-right v 8) 0xff) (bit-and v 0xff)) 765.0))))))))
        (let [eyes (pa/selected-asset portrait :eyes)
              iris (when (and (:glow ls) (seq (:asset/iris eyes)))
                     (when-let [rect (asset-rect eyes w h)] (iris-coverage eyes rect w h)))
              maps (light/light-maps ls {:alpha alpha :ink ink :iris iris
                                         :hair-cover (combined-alpha portrait placed-of [:hair-front :bangs] any? w h)}
                                     w h)]
          (dotimes [i n]
            (let [argb (aget d i) a (bit-and (unsigned-bit-shift-right argb 24) 0xff)]
              (when (pos? a)
                (let [[r g b] (light/apply-pixel maps i
                                                 (double (bit-and (unsigned-bit-shift-right argb 16) 0xff))
                                                 (double (bit-and (unsigned-bit-shift-right argb 8) 0xff))
                                                 (double (bit-and argb 0xff)))]
                  (aset d i (unchecked-int (bit-or (bit-shift-left a 24)
                                                   (bit-shift-left (int (min 255.0 r)) 16)
                                                   (bit-shift-left (int (min 255.0 g)) 8)
                                                   (int (min 255.0 b))))))))))))))

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
  "Draws the credit for artist `names` centred along the bottom of a `w`x`h` frame, outlined
   then filled: the longest wording from pa/credit-variants that fits beside the site mark
   (layout/fit-credit). Why it is in the pixels: PORTRAIT-COMPOSITOR.md, \"The baked credit
   and the site mark\"."
  [^Graphics2D g names w h]
  (let [{:keys [halo baseline]
         [or* og ob oa] :outline
         [fr fg fb fa] :fill} (layout/credit-layout w h)
        {:keys [text size]} (layout/fit-credit
                             (pa/credit-variants names) w h
                             (fn [text size]
                               (.stringWidth (.getFontMetrics g (face credit-face size)) ^String text)))
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
             g (.createGraphics img)
             ;; each piece decoded and placed once, shared by the crown and
             ;; the shadows, which both need pieces other than the one drawing
             placed-of (memoize #(placed-asset % w h))
             crown (delay (try (hair-crown portrait placed-of w h) (catch Exception _ nil)))]
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
                 ;; the whites go down first, so the eye art sits on them
                 (when (and bytes (:asset/whites asset) (pa/whites-colour portrait)
                            (not (s/includes? (str mime) "svg")))
                   (draw-whites! g bytes asset (hex->color (pa/whites-colour portrait)) w h))
                 (when (and mime bytes (or as-drawn? color))
                   (cond
                     (s/includes? mime "svg")
                     (draw-vector-layer! g (String. ^bytes bytes "UTF-8") color w h)

                     (and color (colorizes? layer-key asset))
                     (draw-colorized-layer! g bytes portrait layer-key asset color w h)

                     (and color (fx/hair-layer? layer-key))
                     (draw-ombre-layer! g bytes portrait layer-key color @crown w h)

                     :else (draw-raster-layer! g bytes color w h))))
               (catch Exception e
                 (println "portrait-render: skipped layer" layer-key "-" (.getMessage e)))))
           ;; after every layer, before the marks: the marks are not skin
           (try (shade-skin! img portrait placed-of w h)
                (catch Exception e
                  (println "portrait-render: shadows skipped -" (.getMessage e))))
           (try (light! img portrait drawable placed-of w h)
                (catch Exception e
                  (println "portrait-render: light skipped -" (.getMessage e))))
           (when marks?
             (try
               (when-let [names (not-empty (pa/credit-names portrait))]
                 (draw-credit! g names w h))
               (when-let [mark (site-mark)]
                 (draw-site-mark! g mark w h))
               (catch Exception e
                 (println "portrait-render: marks skipped -" (.getMessage e)))))
           (finally (.dispose g)))
         img)))))

(defn source-marks
  "The licensed-art marks of the pieces `portrait` is drawn from, one per selected layer (nil for
   an unmarked piece): what anything drawn from it must carry, via art-mark/carry-marks."
  [portrait]
  (for [k pa/layer-order
        :let [url (some-> (pa/selected-asset portrait k) :asset/url)]
        :when url]
    (some-> (asset-source url) :bytes art-mark/read-mark)))

(defn render-png
  "PNG bytes for `portrait`, or nil when there is nothing to draw. Carries the licensed-art mark
   (orcpub.art-mark) when any piece drawn from carries one."
  ([portrait] (render-png portrait default-width default-height))
  ([portrait w h]
   (try
     (when-let [img (render portrait w h)]
       (let [out (ByteArrayOutputStream.)]
         (when (ImageIO/write img "png" out)
           (art-mark/carry-marks (.toByteArray out) (source-marks portrait)))))
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
    (try
      (with-open [ios (ImageIO/createImageOutputStream out)]
        (.setOutput writer ios)
        (.write writer nil (IIOImage. img nil nil) param))
      ;; the writer holds native resources, released even when the write throws
      (finally (.dispose writer)))
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
        (art-mark/carry-marks (jpeg-bytes out example-quality) (source-marks portrait))))
    (catch Exception e
      (println "portrait-render: example failed -" (.getMessage e))
      nil)))

