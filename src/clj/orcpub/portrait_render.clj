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
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.dnd.e5.portrait-layout :as layout]
            [orcpub.dnd.e5.portrait-colorize :as colorize]
            [orcpub.dnd.e5.portrait-effects :as fx]
            [orcpub.dnd.e5.portrait-face :as face])
  (:import [java.awt AlphaComposite BasicStroke Color Graphics2D RenderingHints]
           [java.awt.geom AffineTransform Area Ellipse2D$Double Path2D$Double]
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
  "out.rgb = colour.rgb * art.rgb, out.a = art.a, in place.

   Masking -- which is what this did -- reads the asset's ALPHA and throws its
   RGB away, so a drawing with black lines over a white fill came out as one
   flat colour, indistinguishable from its own silhouette. Multiply keeps the
   lines: white fill takes the colour exactly, black lines stay black, a grey
   darkens the colour by however grey it is.

   Java2D has no multiply -- AlphaComposite is Porter-Duff only -- so this is a
   pixel pass over the frame's backing int[]."
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
            k (if cov (/ (aget ^doubles cov i) 255.0) 1.0)]
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

(defonce ^:private strand-cache (atom {}))

(defn- read-strands
  "The piece's precomputed field, from the .strands.png beside its art."
  [url w h]
  (when-let [{:keys [bytes]} (some-> (fx/strands-url url) asset-source)]
    (when-let [^BufferedImage img (ImageIO/read (ByteArrayInputStream. bytes))]
      (let [gw (.getWidth img) gh (.getHeight img)
            r (.getRaster img)]
        (fx/bytes->field (fn [g] (.getSample r (int (mod g gw)) (int (quot g gw)) 0)) gw gh w h)))))

(defn- strand-field-of
  "The piece's strand field (portrait-effects/strand-field) in art pixels:
   read from beside the art, or -- when nobody has generated it yet --
   worked out from the art once and kept."
  [url ^BufferedImage src layer-key]
  (or (get @strand-cache url)
      (let [w (.getWidth src) h (.getHeight src)
            f (or (try (read-strands url w h) (catch Exception _ nil))
                  (let [argb (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
                        g (.createGraphics argb)
                        _ (do (.drawImage g src 0 0 nil) (.dispose g))
                        ^ints d (.. argb getRaster getDataBuffer getData)]
                    (fx/strand-field (fn [i] (bit-and (aget d (int i)) 0xffffff)) (alpha-fn argb) w h
                                     (fx/piece-seed layer-key))))]
        (swap! strand-cache #(assoc (if (> (count %) 48) {} %) url f))
        f)))

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
        marks? (and (face/marks? fs) (seq (:asset/iris eyes)))]
    (when (or casts? marks?)
      (let [^doubles skin (combined-alpha portrait placed-of [:head :ears] any? w h)
            ^doubles hair (combined-alpha portrait placed-of [:scalp :hair-front :bangs] any? w h)
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
            ^doubles mb (:blush m) ^doubles mf (:freckles m)
            blush-rgb (colorize/hex->rgb (:blush-colour fs))
            ^ints d (.. img getRaster getDataBuffer getData)]
        (dotimes [i (* w h)]
          (let [kk (if k (aget k i) 0.0)
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

(def credit-face "public/fonts/Vollkorn-Italic.ttf")
(def mark-face "public/fonts/Vollkorn-Regular.ttf")

(def ^:private load-face
  "Vollkorn off the classpath, parsed once.

   Not java.awt.Font/SANS_SERIF: that is a *logical* family the JVM resolves
   through the host's fontconfig, so a slim container renders the marks in
   whatever it happens to have, or in a fallback full of boxes. This is the
   same face the PDF is set in, shipped with the app, so the picture looks the
   same wherever it is rendered."
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
  "Burn the artist credit into the picture itself.

   The page and the sheet can both carry a credit beside the portrait, but the
   composed image is what people actually pass around -- saved off a share
   card, pulled out of a PDF -- and it arrives detached from either. A caption
   in the pixels travels with it.

   Dark fill under a white outline, because the background is unknowable: the
   PNG is transparent, so it may land on a white page or a dark chat client,
   and one of the two always reads. Not a watermark -- a crop removes it --
   just a credit that survives being right-click-saved."
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
                 (when (and bytes (:asset/whites asset) (not (s/includes? (str mime) "svg")))
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
           (try
             (when-let [names (not-empty (pa/credit-names portrait))]
               (draw-credit! g names w h))
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
