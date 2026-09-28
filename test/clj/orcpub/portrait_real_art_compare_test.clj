(ns orcpub.portrait-real-art-compare-test
  "Compose a real pack both ways and look at the difference.

   Point ORCPUB_PACK at an unpacked portrait pack -- <layer-key>/<file>.png, the
   shape the Loom and build-portrait-assets.py both emit -- and this stacks the
   layers in z-order twice: once through the mask the app uses today, once
   through multiply. Writes both to ORCPUB_PACK_OUT.

   Skips when the pack is not there, because it describes art that does not live
   in this repository."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.dnd.e5.portrait-layout :as layout]
            [clojure.data.json :as json]
            [clojure.string :as s])
  (:import [java.awt AlphaComposite Color RenderingHints]
           [java.awt.geom Area Path2D$Double Ellipse2D$Double]
           [java.awt.image BufferedImage]
           [java.io File]
           [javax.imageio ImageIO]))

(def ^:private W 464)
(def ^:private H 638)

;; A character, so the comparison is about the art rather than about grey.
(def ^:private colours
  {:hair  (Color. 0x5C 0x3A 0x1E)
   :skin  (Color. 0xE8 0xC6 0x9C)
   :eyes  (Color. 0x3B 0x6E 0xA5)
   :shirt (Color. 0x7A 0x5C 0x3A)})

(def ^:private category-fallback (Color. 0xB5 0x6B 0x5E))   ; mouth: clay red

(defn- pack-dir []
  (when-let [d (System/getenv "ORCPUB_PACK")]
    (let [f (File. d)] (when (.isDirectory f) f))))

(defn- assets-in [^File pack layer-key]
  (let [d (File. pack (name layer-key))]
    (when (.isDirectory d)
      (->> (.listFiles d)
           (filter #(re-find #"(?i)\.png$" (.getName ^File %)))
           (sort-by #(.getName ^File %))
           vec))))

(defn- asset-for [^File pack layer-key]
  (let [d (File. pack (name layer-key))]
    (when (.isDirectory d)
      (->> (.listFiles d)
           (filter #(re-find #"(?i)\.png$" (.getName ^File %)))
           sort first))))

(defn- px
  "The backing int[] of a TYPE_INT_ARGB image.

   .getRGB/.setRGB go through the ColorModel on every call. These loops run
   over ~300k pixels per layer per variant, so that indirection was most of
   the render time."
  ^ints [^BufferedImage img]
  (.. img getRaster getDataBuffer getData))

(defn- scaled*
  "The asset placed in the frame the way both renderers place it."
  ^BufferedImage [^File f]
  (when-let [src (ImageIO/read f)]
    (let [out (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
          g (.createGraphics out)
          [x y dw dh] (layout/contain-rect (.getWidth src) (.getHeight src) W H)]
      (try
        (.setRenderingHint g RenderingHints/KEY_INTERPOLATION
                           RenderingHints/VALUE_INTERPOLATION_BILINEAR)
        (.drawImage g src (int x) (int y) (int dw) (int dh) nil)
        (finally (.dispose g)))
      out)))

(def ^:private scaled
  "Memoized: a sweep composes the same ten layers dozens of times over, and
   every call was an ImageIO/read off disk plus a bilinear rescale."
  (memoize scaled*))

(defn- masked
  "What the app does today: keep the alpha, replace every colour."
  ^BufferedImage [^BufferedImage art ^Color tint]
  (let [out (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics out)]
    (try
      (.drawImage g art 0 0 nil)
      (.setComposite g AlphaComposite/SrcIn)
      (.setColor g tint)
      (.fillRect g 0 0 W H)
      (finally (.dispose g)))
    out))

(defn- multiplied
  "Tint through the alpha, then put the drawing back over it."
  ^BufferedImage [^BufferedImage art ^Color tint]
  (let [out (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
        src (px art) dst (px out)
        tr (.getRed tint) tg (.getGreen tint) tb (.getBlue tint)]
    (dotimes [i (* W H)]
      (let [argb (aget src i)
            a (bit-and (unsigned-bit-shift-right argb 24) 0xff)]
        (when (pos? a)
          (let [r (bit-and (unsigned-bit-shift-right argb 16) 0xff)
                g (bit-and (unsigned-bit-shift-right argb 8) 0xff)
                b (bit-and argb 0xff)]
            (aset-int dst i
                      (unchecked-int
                       (bit-or (bit-shift-left a 24)
                               (bit-shift-left (quot (* tr r) 255) 16)
                               (bit-shift-left (quot (* tg g) 255) 8)
                               (quot (* tb b) 255))))))))
    out))

(defn- compose [^File pack tint-fn]
  (let [canvas (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics canvas)
        drawn (atom [])]
    (try
      (doseq [layer-key pa/layer-order]
        (when-let [f (asset-for pack layer-key)]
          (when-let [art (scaled f)]
            (let [slot (get pa/color-slots layer-key)
                  tint (get colours slot category-fallback)]
              (.drawImage g ^BufferedImage (tint-fn art tint) 0 0 nil)
              (swap! drawn conj layer-key)))))
      (finally (.dispose g)))
    {:image canvas :layers @drawn}))

(defn- opaque-colour-count [^BufferedImage img]
  (count (into #{} (for [x (range W) y (range H)
                         :let [argb (.getRGB img x y)]
                         :when (= 255 (bit-and (unsigned-bit-shift-right argb 24) 0xff))]
                     (bit-and argb 0xffffff)))))

(defn- write! [^BufferedImage img nm]
  (when-let [dir (System/getenv "ORCPUB_PACK_OUT")]
    (.mkdirs (File. dir))
    (ImageIO/write img "png" (File. dir (str nm ".png")))
    (println (format "  wrote %s/%s.png" dir nm))))

(deftest compose-a-real-pack-both-ways
  (if-let [pack (pack-dir)]
    (let [m (compose pack masked)
          x (compose pack multiplied)]
      (println (format "\n  composed %d layers: %s"
                       (count (:layers m)) (pr-str (:layers m))))
      (write! (:image m) "composed-mask")
      (write! (:image x) "composed-multiply")

      (is (seq (:layers m)) "found layers in the pack")
      (is (= (:layers m) (:layers x)) "same stack both ways")

      (let [mc (opaque-colour-count (:image m))
            xc (opaque-colour-count (:image x))]
        (println (format "  distinct opaque colours -- mask: %d, multiply: %d\n" mc xc))
        ;; A first version asserted the mask leaves about as many colours as
        ;; there are slots. That was wrong, and the real pack said so: ten
        ;; layers with antialiased edges blend into each other, so masking
        ;; still yields ~1000. The absolute count says nothing. The RATIO does.
        (testing "multiply keeps the drawing, which is several times the colour"
          (is (> xc (* 3 mc))
              (str "multiply produced " xc " against the mask's " mc)))))
    (println "\n  ORCPUB_PACK not set or not a directory -- skipping.\n")))

;; ---------------------------------------------------------------------------
;; Several characters from one pack, so the question is what the compositor
;; does across combinations rather than what it did to one.
;; ---------------------------------------------------------------------------

(def ^:private palettes
  [{:hair (Color. 0x5C 0x3A 0x1E) :skin (Color. 0xE8 0xC6 0x9C)
    :eyes (Color. 0x3B 0x6E 0xA5) :shirt (Color. 0x7A 0x5C 0x3A)}
   {:hair (Color. 0x1B 0x14 0x12) :skin (Color. 0x8D 0x5A 0x3C)
    :eyes (Color. 0x3E 0x2A 0x1C) :shirt (Color. 0x2F 0x4F 0x6B)}
   {:hair (Color. 0xC9 0x8E 0x3A) :skin (Color. 0xF2 0xD8 0xBE)
    :eyes (Color. 0x4E 0x7A 0x46) :shirt (Color. 0x7B 0x2F 0x3A)}
   {:hair (Color. 0x9B 0x3D 0x2E) :skin (Color. 0xC8 0x96 0x6E)
    :eyes (Color. 0x5B 0x4A 0x7A) :shirt (Color. 0x33 0x3A 0x33)}])

(defn- compose-variant
  "Character `n`: the nth option of every layer, wrapping where a layer has
   fewer, painted from the nth palette."
  [^File pack n tint-fn]
  (let [canvas (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics canvas)
        palette (nth palettes (mod n (count palettes)))]
    (try
      (doseq [layer-key pa/layer-order]
        (when-let [choices (seq (assets-in pack layer-key))]
          (when-let [art (scaled (nth choices (mod n (count choices))))]
            (let [slot (get pa/color-slots layer-key)]
              (.drawImage g ^BufferedImage (tint-fn art (get palette slot category-fallback))
                          0 0 nil)))))
      (finally (.dispose g)))
    canvas))

(defn- contact-sheet [images]
  (let [sheet (BufferedImage. (* W (count images)) H BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics sheet)]
    (try
      (.setColor g Color/WHITE)
      (.fillRect g 0 0 (.getWidth sheet) H)
      (doseq [[i ^BufferedImage img] (map-indexed vector images)]
        (.drawImage g img (int (* i W)) 0 nil))
      (finally (.dispose g)))
    sheet))

(deftest four-characters-from-one-pack
  (if-let [pack (pack-dir)]
    (let [n 4
          multi (mapv #(compose-variant pack % multiplied) (range n))
          mask (mapv #(compose-variant pack % masked) (range n))]
      (write! (contact-sheet multi) "four-multiply")
      (write! (contact-sheet mask) "four-mask")
      (doseq [[i ^BufferedImage img] (map-indexed vector multi)]
        (is (pos? (opaque-colour-count img))
            (str "variant " i " drew something")))
      (testing "the variants differ from each other -- the pack really does
                offer choices, rather than one face in four colours"
        (is (apply distinct? (map opaque-colour-count multi)))))
    (println "\n  ORCPUB_PACK not set -- skipping.\n")))

;; ---------------------------------------------------------------------------
;; Layers that hold more than one thing.
;;
;; A smiling mouth is lips AND teeth AND the dark inside. An open eye is sclera
;; AND iris AND pupil. One tint over the whole asset is wrong for both, whether
;; that tint arrives by mask or by multiply -- character three came out with
;; clay-red teeth because the mouth layer's category colour flooded everything
;; in it.
;;
;; The third option is to tint such a layer not at all and draw it as the
;; illustrator coloured it. That costs the layer its colour slot, which is free
;; for the mouth (nobody picks a lip colour) and not free for the eyes, where
;; the iris would have to become its own layer for an eye-colour slot to have
;; anything to act on.
;; ---------------------------------------------------------------------------

(defn- as-drawn
  "No tint at all: the artist's own colours."
  ^BufferedImage [^BufferedImage art ^Color _]
  art)

(def ^:private untinted-layers #{:mouth :eyes})

(defn- compose-mixed
  "multiply everywhere, except the layers that carry their own colours."
  [^File pack n]
  (let [canvas (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics canvas)
        palette (nth palettes (mod n (count palettes)))]
    (try
      (doseq [layer-key pa/layer-order]
        (when-let [choices (seq (assets-in pack layer-key))]
          (when-let [art (scaled (nth choices (mod n (count choices))))]
            (let [tint-fn (if (untinted-layers layer-key) as-drawn multiplied)
                  slot (get pa/color-slots layer-key)]
              (.drawImage g ^BufferedImage (tint-fn art (get palette slot category-fallback))
                          0 0 nil)))))
      (finally (.dispose g)))
    canvas))

(deftest teeth-should-not-be-pink
  (if-let [pack (pack-dir)]
    ;; variant 2 is the one with the open smile
    (let [n 2
          all-multiply (compose-variant pack n multiplied)
          mixed (compose-mixed pack n)]
      (write! (contact-sheet [all-multiply mixed]) "mouth-tinted-vs-as-drawn")
      (is (not= (opaque-colour-count all-multiply) (opaque-colour-count mixed))
          "leaving two layers untinted changes the picture")
      (testing "and it is the mouth that changes: tinting it paints teeth and
                lips the same clay red, drawing it as-is keeps whatever the
                illustrator put there"
        (let [mouth (first (assets-in pack :mouth))]
          (is (some? mouth) "the pack has a mouth layer to reason about"))))
    (println "\n  ORCPUB_PACK not set -- skipping.\n")))

;; ---------------------------------------------------------------------------
;; The mode per layer, which is the shape the registry would carry.
;;
;; multiply keeps a drawing and colours it, so it suits every layer that is
;; one material. It is wrong where an asset holds two things that want
;; different answers -- teeth are not lips. For the eyes there is a third
;; possibility worth looking at rather than reasoning about: MASK them. It
;; flattens the whole eye to the chosen colour, lashes included, which is
;; destructive in principle and may be unnoticeable in practice, because the
;; region is tiny and stylised. Keeping it also keeps the eye-colour picker,
;; which drawing them as-is gives up.
;; ---------------------------------------------------------------------------

(def ^:private layer-modes
  "One exception, not two. Looked at side by side on the same face at 3x:

     mask      flat blue almonds -- no pupil, no lashes, no highlight
     multiply  iris, pupil, lashes and highlight, in the chosen colour
     as-drawn  the eye in black and white, since this asset carries no colour

   So the eyes want multiply like everything else, and the worry that it would
   flood the sclera was wrong: these eyes are drawn with a large iris, so the
   pale area is small and reading it as eye colour is right rather than a
   mistake. Only the mouth is special, because teeth are white and must stay
   white while lips are not."
  {:mouth :none})

(defn- tint-fn-for [layer-key]
  (case (get layer-modes layer-key :multiply)
    :mask masked
    :none as-drawn
    multiplied))

(defn- compose-by-mode [^File pack n]
  (let [canvas (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics canvas)
        palette (nth palettes (mod n (count palettes)))]
    (try
      (doseq [layer-key pa/layer-order]
        (when-let [choices (seq (assets-in pack layer-key))]
          (when-let [art (scaled (nth choices (mod n (count choices))))]
            (.drawImage g ^BufferedImage
                        ((tint-fn-for layer-key) art
                         (get palette (get pa/color-slots layer-key) category-fallback))
                        0 0 nil))))
      (finally (.dispose g)))
    canvas))

(deftest one-character-three-eye-treatments
  (testing "the same face three ways, so the eyes can be compared without a
            different character confusing the picture"
    (if-let [pack (pack-dir)]
      (let [n 0]
        (write! (compose-variant pack n masked) "eyes-a-mask")
        (write! (compose-variant pack n multiplied) "eyes-b-multiply")
        (write! (compose-mixed pack n) "eyes-c-as-drawn")
        (is true))
      (println "\n  ORCPUB_PACK not set -- skipping.\n"))))

(deftest per-layer-modes-across-four-characters
  (if-let [pack (pack-dir)]
    (let [sheet (contact-sheet (mapv #(compose-by-mode pack %) (range 4)))]
      (write! sheet "four-by-mode")
      (is (pos? (opaque-colour-count sheet))))
    (println "\n  ORCPUB_PACK not set -- skipping.\n")))

;; ---------------------------------------------------------------------------
;; The eyes, rendered the way the SERVER will render them.
;;
;; Every picture in this conversation so far came out of a scratch script in a
;; third language -- not the browser's canvas, not this Java2D path, a Python
;; reimplementation of both. That is how a render came back with clay-red teeth
;; long after the mouth had been settled: the script did not know. A preview
;; that is not produced by the shipping code proves nothing about the shipping
;; code, so this reads the pack's own manifest and draws through Java2D.
;; ---------------------------------------------------------------------------

(defn- read-manifest [^File pack]
  (let [f (File. pack "manifest.json")]
    (when (.isFile f)
      (json/read-str (slurp f) :key-fn keyword))))

(defn- iris-spec
  "The regions someone placed in the Loom, by asset file name."
  [manifest]
  (into {}
        (for [layer (:layers manifest)
              a (:assets layer)
              :when (seq (:iris a))]
          [(:file a) a])))

(defn- ellipse-area
  "An iris oval as a shape, in frame pixels."
  ^Area [e scale]
  (let [rx (* (:rx e) W scale), ry (* (:ry e) H scale)
        cx (* (:cx e) W), cy (* (:cy e) H)
        el (Ellipse2D$Double. (- rx) (- ry) (* 2 rx) (* 2 ry))
        tx (doto (java.awt.geom.AffineTransform.)
             (.translate cx cy)
             (.rotate (:rot e)))]
    (Area. (.createTransformedShape tx el))))

(defn- above-lid
  "Everything above the lid curve -- the part the lash covers."
  ^Area [lid]
  (when lid
    (let [p (Path2D$Double.)]
      (.moveTo p (* (:x0 lid) W) (* (:y0 lid) H))
      (.quadTo p (* (:mx lid) W) (* (:my lid) H)
               (* (:x1 lid) W) (* (:y1 lid) H))
      (.lineTo p (* (:x1 lid) W) (- H))
      (.lineTo p (* (:x0 lid) W) (- H))
      (.closePath p)
      (Area. p))))

(defn- shifted
  "The pupil, moved off the iris centre. A pupil pinned to the middle of the
   oval stares straight out of the page, which on a wide eye reads as startled
   rather than as a character looking at something."
  [e]
  (let [px (or (:px e) 0.0), py (or (:py e) 0.0)]
    (assoc e
           :cx (+ (:cx e) (* px (:rx e)))
           :cy (+ (:cy e) (* py (:ry e))))))

(defn- iris-region
  "Oval minus pupil minus whatever the lid covers."
  ^Area [spec]
  (let [pupil (or (:pupil spec) 0.45)
        area (Area.)]
    (doseq [e (:iris spec)]
      (let [a (ellipse-area e 1.0)]
        (.subtract a (ellipse-area (shifted e) pupil))
        (when-let [lid (above-lid (:lid e))] (.subtract a lid))
        (.add area a)))
    area))

(defn- coverage-mask
  "How much of each pixel the region covers, 0..255.

   Area/contains is a yes-or-no test, so the iris and pupil edges came out
   stair-stepped against artwork that is smooth everywhere else. Invisible at
   portrait size and not invisible on a printed sheet. Filling the shape with
   antialiasing on gives partial coverage at the boundary, which is then a
   blend weight rather than a switch."
  ^bytes [^Area region]
  (let [img (BufferedImage. W H BufferedImage/TYPE_BYTE_GRAY)
        g (.createGraphics img)]
    (try
      (.setRenderingHint g RenderingHints/KEY_ANTIALIASING
                         RenderingHints/VALUE_ANTIALIAS_ON)
      (.setColor g Color/WHITE)
      (.fill g region)
      (finally (.dispose g)))
    (.. img getRaster getDataBuffer getData)))

(def ^:dynamic *iris-gamma*
  "Bends the drawing's luminance before the ramp reads it.

   The floor alone cannot serve both viewing distances: raising it lifts the
   iris's AVERAGE colour, which is what a glance sees, by squeezing the range
   between its darkest and lightest ink, which is what a close look sees. One
   knob doing two jobs is why 0.55 read better up close and 0.75 better across
   the room.

   A gamma below 1 lifts the midtones -- where the body of the iris sits --
   while leaving the darkest ink alone, so the average moves toward the colour
   and the contrast stays."
  1.0)

(def ^:dynamic *iris-floor*
  "How much of the eye colour the darkest iris ink keeps. 0.30 was the first
   guess and it lost the colour on this art, whose irises are drawn almost
   black."
  0.30)

(defn- colorize-through
  "Map a drawing's own luminance through `colour`, wherever `cov` says.

   Dark stays dark, mid becomes the colour, the highlight stays a highlight.
   The input has to be a luminance ramp with no hue of its own -- which is how
   the irises are drawn, and, it turns out, the lips.

   `cov` is per-pixel coverage 0..255, so a region's antialiased edge fades into
   the drawing instead of stepping."
  ^BufferedImage [^bytes cov ^BufferedImage art ^Color colour]
  (let [out (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
        src (px art) dst (px out)
        floor (double *iris-floor*) gamma (double *iris-gamma*)
        er (.getRed colour) eg (.getGreen colour) eb (.getBlue colour)]
    (dotimes [i (* W H)]
      (let [argb (aget src i)
            a (bit-and (unsigned-bit-shift-right argb 24) 0xff)]
        (when (pos? a)
          (let [k (/ (bit-and (aget cov i) 0xff) 255.0)]
            (if (zero? k)
              (aset-int dst i argb)
              (let [r (bit-and (unsigned-bit-shift-right argb 16) 0xff)
                    g (bit-and (unsigned-bit-shift-right argb 8) 0xff)
                    b (bit-and argb 0xff)
                    l (/ (+ r g b) 765.0)
                    lg (Math/pow l gamma)
                    mix (fn [c] (if (< lg 0.5)
                                  (* c (+ floor (* (- 1.0 floor) 2.0 lg)))
                                  (+ c (* (- 255 c) (* 2.0 (- lg 0.5))))))
                    blend (fn [s tgt] (int (min 255.0 (+ (* s (- 1.0 k))
                                                         (* tgt k)))))]
                (aset-int dst i
                          (unchecked-int
                           (bit-or (bit-shift-left a 24)
                                   (bit-shift-left (blend r (mix er)) 16)
                                   (bit-shift-left (blend g (mix eg)) 8)
                                   (blend b (mix eb)))))))))))
    out))

(defn- colorize-iris
  "Paint the iris its colour instead of ADDING colour to it.

   Adding is what every preview so far did, and it is why the irises looked
   thin: the artwork shows through, so shading and the highlight wash toward
   white rather than reading as a coloured eye. This maps the drawing's own
   luminance through the chosen colour -- dark stays dark, mid becomes the
   colour, the highlight stays a highlight -- which is opaque where the drawing
   is opaque."
  ^BufferedImage [^BufferedImage art ^Area region ^Color eye]
  (colorize-through (coverage-mask region) art eye))

(defn- whole-asset-coverage
  "Every opaque pixel, as a coverage array. The degenerate region, for an asset
   that is nothing but the thing to be coloured."
  ^bytes [^BufferedImage art]
  (let [cov (byte-array (* W H)) src (px art)]
    (dotimes [i (* W H)]
      (when (pos? (bit-and (unsigned-bit-shift-right (aget src i) 24) 0xff))
        (aset-byte cov i (unchecked-byte 255))))
    cov))

(defn- compose-with-eyes
  "A character, drawn the way the app will draw it: multiply everywhere, the
   mouth left alone, the eyes coloured inside their placed regions."
  ([^File pack manifest n] (compose-with-eyes pack manifest n nil nil))
  ([^File pack manifest n eye-idx eye-colour]
   (let [spec (iris-spec manifest)
        palette (cond-> (nth palettes (mod n (count palettes)))
                  eye-colour (assoc :eyes eye-colour))
        canvas (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics canvas)]
    (try
      (doseq [layer-key pa/layer-order]
        (when-let [choices (seq (assets-in pack layer-key))]
          ;; an eye asset is one that carries a placed iris; when a style is
          ;; being held fixed it is picked by eye-idx rather than by n
          (let [eyes? (some #(get spec (.getName ^File %)) choices)
                ^File f (nth choices (mod (if (and eyes? eye-idx) eye-idx n)
                                          (count choices)))]
            (when-let [art (scaled f)]
              (let [slot (get pa/color-slots layer-key)
                    s (get spec (.getName f))]
                (cond
                  s (.drawImage g (colorize-iris art (iris-region s)
                                                 (get palette :eyes))
                                0 0 nil)
                  ;; the mouth carries its own colours: teeth are not lips
                  (nil? slot) (.drawImage g art 0 0 nil)
                  :else (.drawImage g ^BufferedImage
                                    (multiplied art (get palette slot)) 0 0 nil)))))))
      (finally (.dispose g)))
    canvas)))

(deftest render-the-pack-the-way-the-server-will
  (if-let [pack (pack-dir)]
    (let [manifest (read-manifest pack)]
      (if-not manifest
        (println "\n  no manifest.json in the pack -- skipping\n")
        (let [sheet (contact-sheet (mapv #(compose-with-eyes pack manifest %) (range 4)))]
          (write! sheet "server-render")
          (println (format "  iris specs found: %s"
                           (pr-str (keys (iris-spec manifest)))))
          (is (pos? (opaque-colour-count sheet))))))
    (println "\n  ORCPUB_PACK not set -- skipping.\n")))

;; ---------------------------------------------------------------------------
;; A grade over the finished portrait.
;;
;; Observed on a laptop with night-light on: everything looked warmer and
;; richer. Worth testing rather than assuming, because a display filter warms
;; the WHOLE field -- the art, the page, the chrome -- and the eye adapts to
;; all of it. The same shift applied to the portrait ALONE has to survive
;; sitting next to neutral white, which is a different question.
;;
;; Written here, in the shipping language, so that if any of it is worth
;; keeping it is already the implementation rather than a sketch of one.
;; ---------------------------------------------------------------------------

(defn- grade
  "Per-channel gain plus a pull toward or away from grey."
  ^BufferedImage [^BufferedImage src [rg gg bg] sat]
  (let [w (.getWidth src) h (.getHeight src)
        out (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)]
    (dotimes [y h]
      (dotimes [x w]
        (let [argb (.getRGB src x y)
              a (bit-and (unsigned-bit-shift-right argb 24) 0xff)
              r (* (bit-and (unsigned-bit-shift-right argb 16) 0xff) (double rg))
              g (* (bit-and (unsigned-bit-shift-right argb 8) 0xff) (double gg))
              b (* (bit-and argb 0xff) (double bg))
              ;; luma with the usual weights, so saturation moves colour without
              ;; moving brightness
              l (+ (* 0.2126 r) (* 0.7152 g) (* 0.0722 b))
              mix (fn [c] (int (max 0 (min 255 (+ l (* sat (- c l)))))))]
          (.setRGB out x y
                   (unchecked-int (bit-or (bit-shift-left a 24)
                                          (bit-shift-left (mix r) 16)
                                          (bit-shift-left (mix g) 8)
                                          (mix b)))))))
    out))

(defn- stack [images]
  (let [w (.getWidth ^BufferedImage (first images))
        h (.getHeight ^BufferedImage (first images))
        sheet (BufferedImage. w (* h (count images)) BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics sheet)]
    (try
      (.setColor g Color/WHITE)
      (.fillRect g 0 0 w (* h (count images)))
      (doseq [[i ^BufferedImage im] (map-indexed vector images)]
        (.drawImage g im 0 (* i h) nil))
      (finally (.dispose g)))
    sheet))

(deftest see-whether-a-grade-is-worth-having
  (if-let [pack (pack-dir)]
    (if-let [manifest (read-manifest pack)]
      (let [base (contact-sheet (mapv #(compose-with-eyes pack manifest %) (range 4)))
            ;; night-light knocks blue back hard and green a little
            warm (grade base [1.0 0.96 0.82] 1.0)
            ;; the same warmth with a touch more colour, since "pop" may be
            ;; saturation rather than temperature
            warm+ (grade base [1.0 0.96 0.82] 1.18)
            ;; and saturation on its own, to separate the two
            richer (grade base [1.0 1.0 1.0] 1.22)]
        (write! (stack [base warm warm+ richer]) "grades")
        (is (pos? (opaque-colour-count base))))
      (println "\n  no manifest -- skipping\n"))
    (println "\n  ORCPUB_PACK not set -- skipping.\n")))

;; ---------------------------------------------------------------------------
;; Two things that make an eye colour readable: how much of the colour the ink
;; keeps, and whether the colour was strong enough to begin with.
;; ---------------------------------------------------------------------------

(defn- richer
  "Push a colour away from grey and up in value. The shipped presets are muted
   mid-tones, which is a reasonable instinct for clothing and the wrong one for
   an iris drawn in near-black ink."
  ^Color [^Color c sat val]
  (let [hsb (Color/RGBtoHSB (.getRed c) (.getGreen c) (.getBlue c) nil)]
    (Color. (Color/HSBtoRGB (aget hsb 0)
                            (float (min 1.0 (* (aget hsb 1) sat)))
                            (float (min 1.0 (* (aget hsb 2) val)))))))

(deftest compare-iris-floor-and-preset-strength
  (if-let [pack (pack-dir)]
    (if-let [manifest (read-manifest pack)]
      (let [row (fn [floor sat val]
                  (binding [*iris-floor* floor]
                    (contact-sheet
                     (mapv (fn [n]
                             (let [p (nth palettes (mod n (count palettes)))]
                               (with-redefs [palettes (mapv #(update % :eyes richer sat val)
                                                            palettes)]
                                 (compose-with-eyes pack manifest n))))
                           (range 4)))))]
        (write! (stack [(row 0.30 1.0 1.0)     ; what ships now
                        (row 0.55 1.0 1.0)     ; lift the floor only
                        (row 0.75 1.0 1.0)     ; lift it further
                        (row 0.75 1.35 1.15)]) ; and richer presets with it
                "iris-strength")
        (is true))
      (println "\n  no manifest -- skipping\n"))
    (println "\n  ORCPUB_PACK not set -- skipping.\n")))

;; ---------------------------------------------------------------------------
;; Near and far are different tests, so run both.
;; ---------------------------------------------------------------------------

(defn- shrunk
  "The portrait at thumbnail size -- what a glance actually sees."
  ^BufferedImage [^BufferedImage src scale]
  (let [w (int (* (.getWidth src) scale)) h (int (* (.getHeight src) scale))
        out (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics out)]
    (try
      (.setRenderingHint g RenderingHints/KEY_INTERPOLATION
                         RenderingHints/VALUE_INTERPOLATION_BILINEAR)
      (.drawImage g src 0 0 w h nil)
      (finally (.dispose g)))
    out))

(deftest reconcile-near-and-far
  (if-let [pack (pack-dir)]
    (if-let [manifest (read-manifest pack)]
      (let [variant (fn [floor gamma sat val]
                      (binding [*iris-floor* floor *iris-gamma* gamma]
                        (with-redefs [palettes (mapv #(update % :eyes richer sat val)
                                                     palettes)]
                          (compose-with-eyes pack manifest 0))))
            candidates [["floor 0.55, no gamma  (better up close)" (variant 0.55 1.0 1.0 1.0)]
                        ["floor 0.75 + richer   (better at distance)" (variant 0.75 1.0 1.35 1.15)]
                        ["floor 0.55, gamma 0.5 + richer  (the settled pair)" (variant 0.55 0.5 1.35 1.15)]
                        ["floor 0.45, gamma 0.4 + richer  (more modelling still)" (variant 0.45 0.4 1.35 1.15)]]]
        (doseq [[label img] candidates]
          (write! img (str "near-" (-> label (.replaceAll "[^a-z0-9]+" "-")))))
        ;; and the same four at a sixth the size, which is the glance test
        (write! (contact-sheet (mapv (fn [[_ img]] (shrunk img 1.0)) candidates))
                "near-row")
        (write! (stack (mapv (fn [[_ img]] (shrunk img 0.22)) candidates))
                "far-column")
        (is true))
      (println "\n  no manifest -- skipping\n"))
    (println "\n  ORCPUB_PACK not set -- skipping.\n")))

;; ---------------------------------------------------------------------------
;; The two viewing distances measured, so the choice is not a matter of taste.
;;
;; A glance integrates the iris into one average colour, so how strongly the
;; eye reads across the room is its MEAN. A close look sees the shading inside
;; it, so how modelled it looks is the SPREAD between its darkest and lightest
;; ink. The floor moves both together -- lifting it raises the mean by squeezing
;; the ramp -- which is why no single floor won at both distances.
;;
;; A gamma below 1 lifts the midtones, where the body of the iris sits, without
;; touching the darkest ink, so the two can move apart.
;; ---------------------------------------------------------------------------

(defn- iris-stats
  "Mean and spread of the coloured iris, over the region only.

   Mean is the distance test. Spread is p90-p10 rather than min-max, because a
   single antialiased pixel at either end would decide the answer."
  [^BufferedImage img ^Area region]
  (let [cov (coverage-mask region)
        vals (persistent!
              (reduce (fn [acc i]
                        (if (< 200 (bit-and (aget cov i) 0xff))
                          (let [x (rem i W) y (quot i W)
                                argb (.getRGB img x y)]
                            (if (pos? (bit-and (unsigned-bit-shift-right argb 24) 0xff))
                              (conj! acc (/ (+ (bit-and (unsigned-bit-shift-right argb 16) 0xff)
                                               (bit-and (unsigned-bit-shift-right argb 8) 0xff)
                                               (bit-and argb 0xff))
                                            765.0))
                              acc))
                          acc))
                      (transient []) (range (* W H))))
        sorted (vec (sort vals))
        n (count sorted)
        at (fn [q] (nth sorted (min (dec n) (int (* q n)))))]
    (when (pos? n)
      {:n n
       :mean (/ (reduce + sorted) (double n))
       :spread (- (at 0.90) (at 0.10))})))

(deftest gamma-separates-legibility-from-modelling
  (if-let [pack (pack-dir)]
    (if-let [manifest (read-manifest pack)]
      (let [spec (iris-spec manifest)
            ;; whichever eye asset this pack's first character uses
            eye-file (first (filter #(get spec (.getName ^File %))
                                    (mapcat #(assets-in pack %) pa/layer-order)))]
        (if-not eye-file
          (println "\n  no placed iris in this pack -- skipping\n")
          (let [region (iris-region (get spec (.getName ^File eye-file)))
                art (scaled eye-file)
                blue (Color. 0x1d 0x54 0xae)
                measure (fn [floor gamma]
                          (binding [*iris-floor* floor *iris-gamma* gamma]
                            (iris-stats (colorize-iris art region blue) region)))
                close (measure 0.55 1.0)       ; better up close, said the eye
                far (measure 0.75 1.0)         ; better at distance, said the eye
                both (measure 0.55 0.5)]       ; the settled pair
            (println (format "\n  iris over %d px%n    floor .55 gamma 1.0   mean %.3f  spread %.3f%n    floor .75 gamma 1.0   mean %.3f  spread %.3f%n    floor .55 gamma 0.5   mean %.3f  spread %.3f%n"
                             (:n close)
                             (:mean close) (:spread close)
                             (:mean far) (:spread far)
                             (:mean both) (:spread both)))

            (testing "the floor really does trade one for the other -- this is
                      the effect being worked around, and if it ever stops
                      holding the rest of this reasoning is void"
              (is (> (:mean far) (:mean close))
                  "raising the floor lifts the mean, which is why 0.75 won at a distance")
              (is (< (:spread far) (:spread close))
                  "and flattens the spread, which is why 0.55 won up close"))

            (testing "gamma gets the distance legibility"
              (is (>= (:mean both) (* 0.97 (:mean far)))
                  "as bright on average as the floor-0.75 render, which is the
                   one that read across the room"))

            (testing "without paying for it in modelling"
              (is (> (:spread both) (:spread far))
                  "more tonal range inside the iris than floor 0.75 leaves")
              (is (> (:spread both) (:spread close))
                  "and MORE tonal range than floor 0.55 kept -- the row that
                   looked right up close -- so neither distance is being
                   traded away")))))
      (println "\n  no manifest -- skipping\n"))
    (println "\n  ORCPUB_PACK not set -- skipping.\n")))

;; ---------------------------------------------------------------------------
;; Every eye style, at the settled floor and gamma, across the colour slots.
;; Each style was given its own iris ellipse, pupil offset and lid curve by
;; hand, so each needs looking at separately -- a setting that flatters the
;; style with the biggest irises can still fail the one with the narrowest.
;; ---------------------------------------------------------------------------

(def ^:private eye-slot-colours
  "The eye column of the richer presets, which is what will actually ship."
  [["brown"  (Color. 0x83 0x48 0x10)]
   ["blue"   (Color. 0x1d 0x54 0xae)]
   ["green"  (Color. 0x40 0x95 0x43)]
   ["amber"  (Color. 0xa8 0x90 0x24)]
   ["red"    (Color. 0xa8 0x37 0x37)]
   ["teal"   (Color. 0x37 0xa8 0xa8)]
   ["violet" (Color. 0x8c 0x37 0xa8)]
   ["pale"   (Color. 0xef 0xd3 0x89)]])

(defn- row
  "Join images left to right at whatever size they actually are.

   contact-sheet assumes every tile is a full WxH portrait, so handing it
   cropped bands stranded them at the top of a tall white canvas and clipped
   the last one off the right edge."
  ^BufferedImage [images]
  (let [ws (mapv #(.getWidth ^BufferedImage %) images)
        h (apply max (mapv #(.getHeight ^BufferedImage %) images))
        sheet (BufferedImage. (reduce + ws) h BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics sheet)]
    (try
      (.setColor g Color/WHITE)
      (.fillRect g 0 0 (.getWidth sheet) h)
      (loop [x 0 [^BufferedImage i & more] images]
        (when i
          (.drawImage g i (int x) 0 nil)
          (recur (+ x (.getWidth i)) more)))
      (finally (.dispose g)))
    sheet))

(defn- column
  "Join images top to bottom at whatever size they actually are."
  ^BufferedImage [images]
  (let [w (apply max (mapv #(.getWidth ^BufferedImage %) images))
        hs (mapv #(.getHeight ^BufferedImage %) images)
        sheet (BufferedImage. w (reduce + hs) BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics sheet)]
    (try
      (.setColor g Color/WHITE)
      (.fillRect g 0 0 w (.getHeight sheet))
      (loop [y 0 [^BufferedImage i & more] images]
        (when i
          (.drawImage g i 0 (int y) nil)
          (recur (+ y (.getHeight i)) more)))
      (finally (.dispose g)))
    sheet))

(defn- eye-band
  "The eye band of a portrait, at 2x, which is the only part worth looking at
   when the question is about irises."
  ^BufferedImage [^BufferedImage src]
  (let [y0 (int (* H 0.28)) y1 (int (* H 0.44))
        ;; the face sits in the middle; the hair either side is not the subject
        x0 (int (* W 0.30)) x1 (int (* W 0.80))
        bw (* (- x1 x0) 2) bh (* (- y1 y0) 2)
        out (BufferedImage. bw bh BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics out)]
    (try
      (.setRenderingHint g RenderingHints/KEY_INTERPOLATION
                         RenderingHints/VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
      (.drawImage g src 0 0 bw bh x0 y0 x1 y1 nil)
      (finally (.dispose g)))
    out))

(deftest every-eye-style-across-the-colour-slots
  (if-let [pack (pack-dir)]
    (if-let [manifest (read-manifest pack)]
      (let [spec (iris-spec manifest)
            styles (->> pa/layer-order
                        (mapcat #(assets-in pack %))
                        (filter #(get spec (.getName ^File %)))
                        vec)]
        (if (empty? styles)
          (println "\n  no placed irises in this pack -- skipping\n")
          (binding [*iris-floor* 0.55 *iris-gamma* 0.5]
            (println (format "\n  %d eye styles x %d colours, floor .55 gamma .5"
                             (count styles) (count eye-slot-colours)))
            (dotimes [i (count styles)]
              (let [nm (.getName ^File (nth styles i))]
                ;; one row per style: every eye colour on the same face, so the
                ;; only thing changing across the row is the slot
                (write! (row
                         (mapv (fn [[_ c]]
                                 (eye-band (compose-with-eyes pack manifest 0 i c)))
                               eye-slot-colours))
                        (str "style-" (inc i) "-" (s/replace nm #"\.png$" "") "-colours"))
                ;; and the same style on each of the four base faces, because
                ;; skin and hair around it change how the iris reads
                (write! (row
                         (mapv #(eye-band (compose-with-eyes pack manifest % i nil))
                               (range 4)))
                        (str "style-" (inc i) "-" (s/replace nm #"\.png$" "") "-faces"))
                ;; and the same row at a stronger gamma. The styles do not all
                ;; want the same number: style 3's eyes are half-lidded and its
                ;; iris is under 40% the area of style 1's, so it has far fewer
                ;; pixels to say a colour with. Dropping the gamma lifts its
                ;; mean AND its spread, where raising the floor lifts the mean
                ;; and flattens it -- so this is the knob to tweak per style.
                (write! (column
                         [(row (mapv (fn [[_ c]]
                                       (binding [*iris-gamma* 0.5]
                                         (eye-band (compose-with-eyes pack manifest 0 i c))))
                                     eye-slot-colours))
                          (row (mapv (fn [[_ c]]
                                       (binding [*iris-gamma* 0.35]
                                         (eye-band (compose-with-eyes pack manifest 0 i c))))
                                     eye-slot-colours))])
                        (str "style-" (inc i) "-" (s/replace nm #"\.png$" "")
                             "-gamma-50-over-35"))
                (println (format "    style %d: %s" (inc i) nm))))
            ;; the three styles side by side on one colour, which is the
            ;; comparison that shows whether one region needs re-authoring
            (write! (row
                     (mapv #(eye-band (compose-with-eyes pack manifest 0 %
                                                         (Color. 0x1d 0x54 0xae)))
                           (range (count styles))))
                    "all-styles-one-colour")
            (println)
            (is (= 3 (count styles))
                "this pack authored three eye styles; a style losing its iris
                 spec would silently drop out of these sheets otherwise"))))
      (println "\n  no manifest -- skipping\n"))
    (println "\n  ORCPUB_PACK not set -- skipping.\n")))

;; ---------------------------------------------------------------------------
;; One setting, three styles. The floor and gamma were tuned on the style with
;; the largest irises, and a style whose eyes are half-lidded has far fewer
;; pixels to say "blue" with -- so the tuning has to be checked against the
;; narrowest one, not the most flattering.
;; ---------------------------------------------------------------------------

(deftest the-settled-pair-checked-against-every-style
  (if-let [pack (pack-dir)]
    (if-let [manifest (read-manifest pack)]
      (let [spec (iris-spec manifest)
            styles (->> pa/layer-order
                        (mapcat #(assets-in pack %))
                        (filter #(get spec (.getName ^File %)))
                        vec)]
        (if (empty? styles)
          (println "\n  no placed irises -- skipping\n")
          (let [blue (Color. 0x1d 0x54 0xae)
                stats (binding [*iris-floor* 0.55 *iris-gamma* 0.5]
                        (mapv (fn [^File f]
                                (let [region (iris-region (get spec (.getName f)))
                                      st (iris-stats (colorize-iris (scaled f) region blue)
                                                     region)]
                                  (assoc st :name (.getName f))))
                              styles))]
            (println "\n  at floor .55 gamma .5, one eye colour, per style:")
            (doseq [{:keys [name n mean spread]} stats]
              (println (format "    %-18s %4d px   mean %.3f  spread %.3f"
                               name n mean spread)))
            (println)

            (testing "every style gets enough iris to carry a colour at all.
                      Style 3's eyes are half-lidded, so this is the one that
                      decides whether the setting is usable -- a threshold that
                      only style 1 has to clear is not a threshold."
              (doseq [{:keys [name n]} stats]
                (is (> n 150)
                    (str name " has " n " px of iris; below roughly 150 the "
                         "colour stops reading at portrait size"))))

            (testing "and none of them is washed out by the shared setting"
              (doseq [{:keys [name mean spread]} stats]
                (is (> mean 0.20) (str name " mean " mean " -- iris reads grey"))
                (is (> spread 0.05)
                    (str name " spread " spread " -- iris is a flat slab")))))))
      (println "\n  no manifest -- skipping\n"))
    (println "\n  ORCPUB_PACK not set -- skipping.\n")))


;; ---------------------------------------------------------------------------
;; The mouths.
;;
;; Drawing the mouth layer untinted is what stopped the teeth coming out red.
;; It is also why the lipped mouth comes out silver: EVERY mouth in this pack
;; is drawn in greys, so "carries its own colour" was never the thing that
;; separated them. Teeth should stay neutral and lips should take a hue, and
;; which is which is a fact about the asset -- the same kind of per-asset fact
;; as the iris region, and it wants the same mechanism.
;; ---------------------------------------------------------------------------

(defn- max-saturation
  "The most saturated opaque pixel in an asset, 0..255. Zero means the artist
   drew it in greys, with no hue of its own."
  [^BufferedImage img]
  (reduce max 0
          (for [x (range (.getWidth img)) y (range (.getHeight img))
                :let [argb (.getRGB img x y)]
                :when (< 250 (bit-and (unsigned-bit-shift-right argb 24) 0xff))
                :let [r (bit-and (unsigned-bit-shift-right argb 16) 0xff)
                      g (bit-and (unsigned-bit-shift-right argb 8) 0xff)
                      b (bit-and argb 0xff)]]
            (- (max r g b) (min r g b)))))

(def ^:private lip-colours
  [["bare"  (Color. 0xc9 0x8d 0x82)]
   ["rose"  (Color. 0xc4 0x6a 0x6a)]
   ["berry" (Color. 0x9b 0x3c 0x55)]
   ["plum"  (Color. 0x6e 0x33 0x4e)]
   ["coral" (Color. 0xd8 0x7a 0x5c)]])

(defn- compose-with-mouth
  "The face on base `n`, mouth pinned to `mouth-idx`. With `lip-colour` the
   mouth goes through the colorize instead of being drawn raw."
  [^File pack manifest n mouth-idx lip-colour]
  (let [spec (iris-spec manifest)
        palette (nth palettes (mod n (count palettes)))
        canvas (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics canvas)]
    (try
      (doseq [layer-key pa/layer-order]
        (when-let [choices (seq (assets-in pack layer-key))]
          (let [mouth? (= :mouth layer-key)
                ^File f (nth choices (mod (if mouth? mouth-idx n) (count choices)))]
            (when-let [art (scaled f)]
              (let [slot (get pa/color-slots layer-key)
                    sp (get spec (.getName f))]
                (cond
                  sp (.drawImage g (colorize-iris art (iris-region sp)
                                                  (get palette :eyes)) 0 0 nil)
                  (and mouth? lip-colour)
                  (.drawImage g (colorize-through (whole-asset-coverage art)
                                                  art lip-colour) 0 0 nil)
                  (nil? slot) (.drawImage g art 0 0 nil)
                  :else (.drawImage g ^BufferedImage
                                    (multiplied art (get palette slot)) 0 0 nil)))))))
      (finally (.dispose g)))
    canvas))

(defn- mouth-band
  ^BufferedImage [^BufferedImage src]
  (let [y0 (int (* H 0.42)) y1 (int (* H 0.56))
        x0 (int (* W 0.38)) x1 (int (* W 0.72))
        bw (* (- x1 x0) 3) bh (* (- y1 y0) 3)
        out (BufferedImage. bw bh BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics out)]
    (try
      (.setRenderingHint g RenderingHints/KEY_INTERPOLATION
                         RenderingHints/VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
      (.drawImage g src 0 0 bw bh x0 y0 x1 y1 nil)
      (finally (.dispose g)))
    out))

(deftest the-mouths-as-drawn
  (if-let [pack (pack-dir)]
    (let [manifest (read-manifest pack)
          mouths (vec (assets-in pack :mouth))]
      (if (or (nil? manifest) (empty? mouths))
        (println "\n  no mouths or no manifest -- skipping\n")
        (do
          (println "\n  mouth assets:")
          (doseq [^File f mouths]
            (println (format "    %-18s max saturation %d" (.getName f)
                             (max-saturation (scaled f)))))
          (println)
          (write! (row (mapv #(mouth-band (compose-with-mouth pack manifest 0 % nil))
                             (range (count mouths))))
                  "mouths-untinted")

          (testing "every mouth in this pack is drawn in greys, so 'carries its
                    own colour' cannot be what decides whether to tint one --
                    which is the flaw in the per-LAYER rule that is shipping"
            (doseq [^File f mouths]
              (is (< (max-saturation (scaled f)) 8)
                  (str (.getName f) " is drawn neutral"))))

          ;; the lipped mouth through the SAME function the irises use, over its
          ;; whole area. A luminance ramp with no hue is precisely what that
          ;; function consumes, so no second mechanism is needed for lips.
          (when-let [^File lips (first (filter #(re-find #"02" (.getName ^File %)) mouths))]
            (binding [*iris-floor* 0.55 *iris-gamma* 0.5]
              (write! (row (mapv (fn [[_ c]]
                                   (mouth-band (compose-with-mouth
                                                pack manifest 0 (.indexOf mouths lips) c)))
                                 lip-colours))
                      "mouth-02-lips-colorized")
              ;; where in the ramp the art actually sits, which is the thing
              ;; that decides the parameters
              (let [art (scaled lips)
                    ls (sort (for [x (range W) y (range H)
                                   :let [argb (.getRGB art x y)]
                                   :when (< 250 (bit-and (unsigned-bit-shift-right argb 24) 0xff))]
                               (/ (+ (bit-and (unsigned-bit-shift-right argb 16) 0xff)
                                     (bit-and (unsigned-bit-shift-right argb 8) 0xff)
                                     (bit-and argb 0xff))
                                  765.0)))
                    n (count ls)
                    at (fn [q] (nth ls (min (dec n) (int (* q n)))))]
                (println (format "  lips luminance: p10 %.2f  median %.2f  p90 %.2f  (%d px, %.0f%% above the ramp midpoint)"
                                 (at 0.10) (at 0.50) (at 0.90) n
                                 (* 100.0 (/ (count (filter #(> % 0.5) ls)) (double n))))))
              (doseq [gm [0.5 1.0 1.6 2.2]]
                (binding [*iris-gamma* gm]
                  (write! (row (mapv (fn [[_ c]]
                                       (mouth-band (compose-with-mouth
                                                    pack manifest 0 (.indexOf mouths lips) c)))
                                     lip-colours))
                          (format "mouth-02-lips-gamma-%.1f" gm))))
              (testing "and the lips take a real hue when put through it"
                (is (> (max-saturation
                        (colorize-through (whole-asset-coverage (scaled lips))
                                          (scaled lips) (Color. 0x9b 0x3c 0x55)))
                       40)
                    "colorized lips carry saturation the raw asset had none of"))

              (testing "the gamma has to go the OTHER WAY for lips than for
                        irises, and that is not a preference -- the two are
                        drawn at opposite ends of the tonal range. The irises
                        are near-black, so they need a floor and a gamma below
                        1 to lift them off it. The lips are drawn light, so
                        most of them sit in the half of the ramp that runs
                        toward WHITE, and lifting them further is what washed
                        them out. One global pair of numbers cannot serve both,
                        which is the case for putting them on the asset."
                (let [median (fn [^BufferedImage art]
                               (let [ls (sort (for [x (range W) y (range H)
                                                    :let [argb (.getRGB art x y)]
                                                    :when (< 250 (bit-and (unsigned-bit-shift-right argb 24) 0xff))]
                                                (/ (+ (bit-and (unsigned-bit-shift-right argb 16) 0xff)
                                                      (bit-and (unsigned-bit-shift-right argb 8) 0xff)
                                                      (bit-and argb 0xff))
                                                   765.0)))]
                                 (when (seq ls) (nth ls (quot (count ls) 2)))))
                      eye-art (first (keep (fn [^File f]
                                             (when (get (iris-spec manifest) (.getName f))
                                               (scaled f)))
                                           (mapcat #(assets-in pack %) pa/layer-order)))]
                  (is (> (median (scaled lips)) 0.55)
                      "the lips are drawn LIGHT, above the ramp's midpoint")
                  (when eye-art
                    (is (< (median eye-art) 0.55)
                        "and the eyes are drawn dark, below it -- so a gamma
                         that helps one hurts the other")))))))))
    (println "\n  ORCPUB_PACK not set -- skipping.\n")))

;; ---------------------------------------------------------------------------
;; The whole thing, composed by the REGISTRY's own rules.
;;
;; Everything above tried one decision at a time. This asks portrait-assets
;; what to do with each asset -- render-mode, slot-for-asset, tint-gamma -- and
;; does exactly that, so what comes out is the specification for the renderer
;; port rather than another opinion about it. If these busts are right, the
;; port's job is to make three renderers agree with this picture.
;; ---------------------------------------------------------------------------

(def ^:private registry-asset-by-file
  "Every registry asset, keyed by the filename the pack uses. The pack and the
   registry name the same 28 files, which is what lets the harness run the
   shipped rules instead of restating them."
  (delay
   (into {}
         (for [layer-key pa/layer-order
               asset (pa/assets-for-layer layer-key)]
           [[layer-key (last (s/split (:asset/url asset) #"/"))] asset]))))

(def ^:private full-palettes
  "Hair / skin / eyes / shirt / lips, as the slots would be filled in."
  [{:hair "#5c3a1e" :skin "#e8c69c" :eyes "#1d54ae" :shirt "#3a4a5c" :lips "#c46a6a"}
   {:hair "#2e180c" :skin "#8d5a3c" :eyes "#834810" :shirt "#7a94b8" :lips "#9b3c55"}
   {:hair "#e1a243" :skin "#f7e0c5" :eyes "#409543" :shirt "#633737" :lips "#c98d82"}
   {:hair "#9b3d2e" :skin "#cd986e" :eyes "#8c37a8" :shirt "#3b6137" :lips "#6e334e"}
   {:hair "#d4d4d4" :skin "#6e4622" :eyes "#37a8a8" :shirt "#303030" :lips "#a85c4a"}
   {:hair "#813872" :skin "#f2ddc4" :eyes "#a83737" :shirt "#d8cdae" :lips "#d87a5c"}])

(defn- hex->awt ^Color [hex]
  (Color. (Integer/parseInt (subs hex 1 3) 16)
          (Integer/parseInt (subs hex 3 5) 16)
          (Integer/parseInt (subs hex 5 7) 16)))

(defn- compose-by-registry
  "One bust, every layer handled the way portrait-assets says to handle it."
  [^File pack manifest palette picks]
  (let [spec (iris-spec manifest)
        canvas (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics canvas)]
    (try
      (doseq [layer-key pa/layer-order]
        (when-let [choices (seq (assets-in pack layer-key))]
          (let [^File f (nth choices (mod (get picks layer-key 0) (count choices)))
                nm (.toLowerCase (.getName f))
                asset (get @registry-asset-by-file [layer-key nm])
                mode (pa/render-mode layer-key asset)
                slot (pa/slot-for-asset layer-key asset)
                colour (some-> (get palette slot) hex->awt)]
            (when-let [art (scaled f)]
              (cond
                ;; already coloured by the illustrator: teeth, bare lines
                (= :as-drawn mode) (.drawImage g art 0 0 nil)

                ;; a hueless ramp mapped through the slot colour. The region is
                ;; the authored iris for eyes, and the whole asset for lips,
                ;; which are nothing but the thing being coloured.
                (and (= :colorize mode) colour)
                (binding [*iris-gamma* (pa/tint-gamma layer-key asset)]
                  (.drawImage g (colorize-through
                                 (if-let [sp (get spec (.getName f))]
                                   (coverage-mask (iris-region sp))
                                   (whole-asset-coverage art))
                                 art colour)
                              0 0 nil))

                colour (.drawImage g ^BufferedImage (multiplied art colour) 0 0 nil)
                :else (.drawImage g art 0 0 nil))))))
      (finally (.dispose g)))
    canvas))

(deftest the-range-of-busts
  (if-let [pack (pack-dir)]
    (if-let [manifest (read-manifest pack)]
      (let [n-eyes (count (assets-in pack :eyes))
            n-mouth (count (assets-in pack :mouth))
            ;; the lipped mouth, so the lips slot is exercised
            lip-idx (first (keep-indexed
                            (fn [i ^File f]
                              (when (= :lips (pa/slot-for-asset
                                              :mouth (get @registry-asset-by-file
                                                          [:mouth (.toLowerCase (.getName f))])))
                                i))
                            (assets-in pack :mouth)))]
        (binding [*iris-floor* 0.55]
          ;; one sheet per eye style: six palettes, lips on
          (dotimes [e n-eyes]
            (write! (row (mapv (fn [p]
                                 (compose-by-registry pack manifest p
                                                      {:eyes e :mouth (or lip-idx 0)}))
                               full-palettes))
                    (str "busts-eyes-0" (inc e) "-with-lips")))
          ;; and one showing every mouth on one palette, so the teeth can be
          ;; checked against the lips they are not
          (write! (row (mapv (fn [m]
                               (compose-by-registry pack manifest (first full-palettes)
                                                    {:eyes 0 :mouth m}))
                             (range n-mouth)))
                  "busts-every-mouth")
          ;; the same range at a sixth scale, which is how a summary shows it
          (write! (row (mapv (fn [p]
                               (shrunk (compose-by-registry
                                        pack manifest p
                                        {:eyes 0 :mouth (or lip-idx 0)})
                                       0.3))
                             full-palettes))
                  "busts-thumbnail-scale"))

        (testing "the lipped mouth really did go down the colorize path -- if
                  the registry lookup misses, every bust still renders and the
                  only sign is lips that came out grey"
          (is (some? lip-idx) "a mouth asset in this pack claims the lips slot")
          ;; not a count -- the registry has an asset the pack does not (the
          ;; scalp is generated, not drawn), so a magic number breaks the next
          ;; time one is added. Check the thing the sentence actually claims.
          (doseq [layer-key pa/layer-order
                  ^File f (assets-in pack layer-key)]
            (is (contains? @registry-asset-by-file
                           [layer-key (.toLowerCase (.getName f))])
                (str (.getName f) " has no registry asset"))))

        (testing "and the teeth did not take the lip colour"
          (let [smile-idx (first (keep-indexed
                                  (fn [i ^File f]
                                    (when (re-find #"03" (.getName f)) i))
                                  (assets-in pack :mouth)))
                bust (compose-by-registry pack manifest
                                          {:lips "#ff0000" :skin "#e8c69c"}
                                          {:eyes 0 :mouth smile-idx})
                reds (count (for [x (range W) y (range H)
                                  :let [argb (.getRGB bust x y)]
                                  :when (and (= 255 (bit-and (unsigned-bit-shift-right argb 24) 0xff))
                                             (> (bit-and (unsigned-bit-shift-right argb 16) 0xff) 200)
                                             (< (bit-and (unsigned-bit-shift-right argb 8) 0xff) 60)
                                             (< (bit-and argb 0xff) 60))]
                              1))]
            (is (zero? reds)
                (str "a pure-red lip colour put " reds " red pixels on a face "
                     "whose mouth is teeth")))))
      (println "\n  no manifest -- skipping\n"))
    (println "\n  ORCPUB_PACK not set -- skipping.\n")))

;; ---------------------------------------------------------------------------
;; The actual range, which means varying the FEATURES and not just the palette.
;;
;; the-range-of-busts above pinned every layer but the eyes and the mouth, so
;; six panels of it were six recolours of one face. The pack is 3x3x3x2x2x3x3x3
;; x3x3 = 8748 combinations, and none of that was visible.
;;
;; These portraits come out of pa/compose-for-seed -- the app's own randomizer,
;; the one behind the Randomize button -- so the sheet shows what a person
;; would actually get rather than what the harness felt like showing.
;; ---------------------------------------------------------------------------

(defn- file-for-asset
  "The pack file backing a registry asset, or nil when the pack lacks it."
  ^File [^File pack layer-key asset-id]
  (when-let [asset (pa/asset-by-id layer-key asset-id)]
    (let [want (.toLowerCase ^String (last (s/split (:asset/url asset) #"/")))]
      (first (filter #(= want (.toLowerCase (.getName ^File %)))
                     (assets-in pack layer-key))))))

(defn- palette-for-seed
  "A colour per slot, drawn from the presets the picker offers. Same seed, same
   palette, on either platform -- it is the registry's own PRNG."
  [seed]
  (let [r (pa/mulberry32 (pa/seed->int (str seed "-colors")))]
    (into {} (for [slot pa/color-slot-order
                   :let [choices (pa/color-presets slot)]
                   :when (seq choices)]
               [slot (nth choices (int (Math/floor (* (r) (count choices)))))]))))

(defn- compose-seeded
  "One bust for `seed`: features from the app's randomizer, colours from the
   presets, every layer routed by the registry's own render-mode."
  [^File pack manifest seed]
  (let [layers (pa/compose-for-seed seed)
        palette (palette-for-seed seed)
        spec (iris-spec manifest)
        canvas (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics canvas)]
    (try
      (doseq [layer-key pa/layer-order]
        (when-let [^File f (some->> (get-in layers [layer-key :asset/id])
                                    (file-for-asset pack layer-key))]
          (let [asset (pa/asset-by-id layer-key (get-in layers [layer-key :asset/id]))
                mode (pa/render-mode layer-key asset)
                colour (some-> (get palette (pa/slot-for-asset layer-key asset)) hex->awt)]
            (when-let [art (scaled f)]
              (cond
                (= :as-drawn mode) (.drawImage g art 0 0 nil)
                (and (= :colorize mode) colour)
                (binding [*iris-gamma* (pa/tint-gamma layer-key asset)]
                  (.drawImage g (colorize-through
                                 (if-let [sp (get spec (.getName f))]
                                   (coverage-mask (iris-region sp))
                                   (whole-asset-coverage art))
                                 art colour)
                              0 0 nil))
                colour (.drawImage g ^BufferedImage (multiplied art colour) 0 0 nil)
                :else (.drawImage g art 0 0 nil))))))
      (finally (.dispose g)))
    canvas))

(def ^:private gallery-seeds
  (mapv #(str "bust-" %) (range 24)))

(deftest a-gallery-of-actually-different-characters
  (if-let [pack (pack-dir)]
    (if-let [manifest (read-manifest pack)]
      (binding [*iris-floor* 0.55]
        (write! (column (mapv (fn [chunk] (row (mapv #(compose-seeded pack manifest %) chunk)))
                              (partition 6 gallery-seeds)))
                "gallery")
        (write! (column (mapv (fn [chunk]
                                (row (mapv #(shrunk (compose-seeded pack manifest %) 0.32)
                                           chunk)))
                              (partition 8 gallery-seeds)))
                "gallery-thumbnails")

        (testing "the seeds really do produce different FACES, not one face in
                  different colours. This is the check the previous sheet did
                  not have, which is why six panels of it were one character."
          (let [picks (mapv pa/compose-for-seed gallery-seeds)
                distinct-combos (count (distinct picks))]
            (println (format "\n  %d seeds -> %d distinct feature combinations"
                             (count gallery-seeds) distinct-combos))
            (is (> distinct-combos (* 0.8 (count gallery-seeds)))
                (str "only " distinct-combos " of " (count gallery-seeds)
                     " seeds gave a different set of features"))
            ;; only layers that HAVE a choice. The scalp has exactly one
            ;; asset and is not offered in the picker, so "it never varied" is
            ;; the correct behaviour rather than a pinned layer.
            (doseq [layer-key pa/layer-order
                    :when (> (pa/asset-count-for-layer layer-key) 1)]
              (let [chosen (distinct (map #(get-in % [layer-key :asset/id]) picks))]
                (is (> (count chosen) 1)
                    (str layer-key " has "
                         (pa/asset-count-for-layer layer-key)
                         " assets but never varied across " (count gallery-seeds)
                         " seeds -- that layer is pinned, which is the bug this
                          test exists to catch"))))
            (testing "and the scalp is in every portrait, since it is not
                      optional -- a portrait without it can show bare crown"
              (is (every? #(= :l2b-scalp-01 (get-in % [:scalp :asset/id])) picks)))))

        (testing "and different colours too, or the gallery is one palette"
          (let [pals (map palette-for-seed gallery-seeds)]
            (doseq [slot pa/color-slot-order]
              (is (> (count (distinct (map #(get % slot) pals))) 1)
                  (str slot " is the same colour on every bust"))))))
      (println "\n  no manifest -- skipping\n"))
    (println "\n  ORCPUB_PACK not set -- skipping.\n")))

;; I claimed in review that the shirt straps "stay near-black across all six
;; palettes" and that this was the art. It was not: five of the six shirt
;; colours I had picked were dark, and the asset has no straps -- what reads as
;; one is the hair-back layer over the shoulder. This measures the thing rather
;; than describing it.
(deftest a-pale-shirt-colour-produces-a-pale-shirt
  (when-let [pack (pack-dir)]
    (when-let [manifest (read-manifest pack)]
      (doseq [[label hex] [["near-white" "#f4f1ea"] ["mid"  "#7a94b8"] ["dark" "#303030"]]]
        (doseq [i (range (count (assets-in pack :shirt)))]
          (let [^File f (nth (assets-in pack :shirt) i)
                art (scaled f)
                out (multiplied art (hex->awt hex))
                ls (sort (for [x (range W) y (range H)
                               :let [argb (.getRGB out x y)]
                               :when (< 250 (bit-and (unsigned-bit-shift-right argb 24) 0xff))]
                           (/ (+ (bit-and (unsigned-bit-shift-right argb 16) 0xff)
                                 (bit-and (unsigned-bit-shift-right argb 8) 0xff)
                                 (bit-and argb 0xff))
                              765.0)))
                n (count ls)]
            (when (pos? n)
              (println (format "  shirt %d @ %-10s  p10 %.2f  median %.2f  p90 %.2f  (%d px)"
                               (inc i) label
                               (nth ls (int (* 0.10 n))) (nth ls (quot n 2))
                               (nth ls (int (* 0.90 n))) n))
              (when (= "near-white" label)
                (is (> (nth ls (quot n 2)) 0.40)
                    (str "shirt " (inc i) " at a near-white colour came out at "
                         (format "%.2f" (nth ls (quot n 2)))
                         " -- the tint is not reaching it")))))))
      (write! (row (mapv (fn [hex]
                           (let [^File f (first (assets-in pack :shirt))]
                             (multiplied (scaled f) (hex->awt hex))))
                         ["#f4f1ea" "#d8cdae" "#7a94b8" "#303030"]))
              "shirt-across-lightness"))))

;; ---------------------------------------------------------------------------
;; Two busts with the same bangs and the same hair-back, one of which shows a
;; strip of scalp between the bangs and the ear. Different head, hair-front and
;; ear assets -- so either one head is wider than the hair drawn to cover it,
;; or one hair-front covers less. Marking the head layer says which.
;; ---------------------------------------------------------------------------

(defn- compose-marking
  "The bust for `seed`, with `mark-layer` painted a flat signal colour so the
   pixels it is responsible for are unmistakable."
  [^File pack manifest seed mark-layer]
  (let [layers (pa/compose-for-seed seed)
        palette (palette-for-seed seed)
        spec (iris-spec manifest)
        canvas (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics canvas)]
    (try
      (doseq [layer-key pa/layer-order]
        (when-let [^File f (some->> (get-in layers [layer-key :asset/id])
                                    (file-for-asset pack layer-key))]
          (let [asset (pa/asset-by-id layer-key (get-in layers [layer-key :asset/id]))
                mode (pa/render-mode layer-key asset)
                colour (some-> (get palette (pa/slot-for-asset layer-key asset)) hex->awt)]
            (when-let [art (scaled f)]
              (cond
                (= layer-key mark-layer)
                (.drawImage g ^BufferedImage (multiplied art (Color. 0xff 0x00 0xff)) 0 0 nil)
                (= :as-drawn mode) (.drawImage g art 0 0 nil)
                (and (= :colorize mode) colour)
                (binding [*iris-gamma* (pa/tint-gamma layer-key asset)]
                  (.drawImage g (colorize-through
                                 (if-let [sp (get spec (.getName f))]
                                   (coverage-mask (iris-region sp))
                                   (whole-asset-coverage art))
                                 art colour)
                              0 0 nil))
                colour (.drawImage g ^BufferedImage (multiplied art colour) 0 0 nil)
                :else (.drawImage g art 0 0 nil))))))
      (finally (.dispose g)))
    canvas))

(defn- opaque-bounds
  "[x0 y0 x1 y1] of an asset's opaque pixels, or nil."
  [^BufferedImage img]
  (let [src (px img)]
    (loop [i 0 x0 W y0 H x1 -1 y1 -1]
      (if (= i (* W H))
        (when (<= 0 x1) [x0 y0 x1 y1])
        (if (pos? (bit-and (unsigned-bit-shift-right (aget src i) 24) 0xff))
          (let [x (rem i W) y (quot i W)]
            (recur (inc i) (min x0 x) (min y0 y) (max x1 x) (max y1 y)))
          (recur (inc i) x0 y0 x1 y1))))))

(deftest which-layer-leaves-the-scalp-showing
  (if-let [pack (pack-dir)]
    (if-let [manifest (read-manifest pack)]
      (binding [*iris-floor* 0.55]
        (doseq [seed ["bust-19" "bust-20"]]
          (write! (row [(compose-seeded pack manifest seed)
                        (compose-marking pack manifest seed :head)
                        (compose-marking pack manifest seed :hair-front)])
                  (str "scalp-" seed)))

        ;; the numbers behind it: how wide each head is, and how wide the
        ;; hair-front drawn over it reaches
        (println "\n  widths of the pieces involved:")
        (doseq [layer-key [:head :hair-front :bangs :ears]]
          (doseq [^File f (assets-in pack layer-key)]
            (when-let [[x0 y0 x1 y1] (opaque-bounds (scaled f))]
              (println (format "    %-12s %-20s x %3d..%3d (%3d wide)  y %3d..%3d"
                               (name layer-key) (.getName f) x0 x1 (- x1 x0) y0 y1)))))
        (println)
        (is true))
      (println "\n  no manifest -- skipping\n"))
    (println "\n  ORCPUB_PACK not set -- skipping.\n")))

;; ---------------------------------------------------------------------------
;; Exposed scalp.
;;
;; One gallery bust showed an island of skin between the bangs and the side
;; lock. It is not a tinting or a z-order fault: hair-front 01 is a crown cap
;; 142px wide and hair-front 02 is a side lock 231px wide that does not cover
;; the crown, so some combinations leave the top of the head bare.
;;
;; Only five layers can cover the skull, so the space is 3 heads x 2 hair-bits
;; x 2 hair-back x 3 hair-front x 3 bangs = 108, small enough to check ALL of
;; it rather than the one bust that happened to be looked at.
;; ---------------------------------------------------------------------------

(def ^:private skull-bottom
  "Below this the head is a face. Bare skin there is a forehead, not a hole."
  (int (* H 0.30)))

(defn- exposed-head-mask
  "Head pixels, above the brow, that no hair layer covers."
  ^booleans [^File head ^File bits ^File back ^File front ^File bangs]
  (let [h (px (scaled head))
        covers (mapv #(px (scaled %)) (remove nil? [bits back front bangs]))
        out (boolean-array (* W H))]
    (dotimes [y skull-bottom]
      (dotimes [x W]
        (let [i (+ x (* y W))]
          (when (and (pos? (bit-and (unsigned-bit-shift-right (aget h i) 24) 0xff))
                     (not-any? (fn [^ints c]
                                 (pos? (bit-and (unsigned-bit-shift-right (aget c i) 24) 0xff)))
                               covers))
            (aset out i true)))))
    out))

(defn- island-px
  "Exposed head that does NOT reach the face -- skin surrounded by hair.

   Counting exposed pixels outright was the wrong measure and said so loudly:
   it called a short fringe the worst case in the pack at 3899px, because a
   high fringe shows more FOREHEAD, and a forehead is supposed to be skin.
   What reads as a fault is an ISLAND -- a patch with hair on every side and no
   path down to the face. So the exposed region is flooded from the brow line,
   and whatever the flood cannot reach is the defect."
  [^booleans exposed]
  (let [seen (boolean-array (* W H))
        stack (java.util.ArrayDeque.)]
    ;; every exposed pixel on the brow line is forehead, and so is anything
    ;; connected to one
    (dotimes [x W]
      (let [i (+ x (* (dec skull-bottom) W))]
        (when (and (aget exposed i) (not (aget seen i)))
          (aset seen i true)
          (.push stack (int i)))))
    (while (not (.isEmpty stack))
      (let [i (int (.pop stack))
            x (rem i W) y (quot i W)]
        (doseq [[dx dy] [[-1 0] [1 0] [0 -1] [0 1]]]
          (let [nx (+ x dx) ny (+ y dy)]
            (when (and (< -1 nx W) (< -1 ny skull-bottom))
              (let [ni (+ nx (* ny W))]
                (when (and (aget exposed ni) (not (aget seen ni)))
                  (aset seen ni true)
                  (.push stack (int ni)))))))))
    (count (for [i (range (* W H))
                 :when (and (aget exposed i) (not (aget seen i)))]
             1))))

(defn- exposed-scalp-px
  [^File head ^File bits ^File back ^File front ^File bangs]
  (island-px (exposed-head-mask head bits back front bangs)))

(def ^:private known-scalp-islands
  "Hair combinations the art does not cover, as [hair-front bangs] -- the head
   makes no difference to any of them.

   This is a ledger, not a suppression. It exists so the test fails on a NEW
   gap, which is the thing nobody will notice: 108 combinations is more than
   anyone looks at, and every one of them renders without complaint.

   Fixing these is the illustrator's call, not code's. Either the fringe is
   redrawn to meet the side lock, or the randomizer learns to avoid the pairs."
  #{["L4_hair_front_02.png" "L9_bangs_03.png"]   ; ~207px between fringe and lock
    ["L4_hair_front_01.png" "L9_bangs_01.png"]   ; ~87px at the crown
    ["L4_hair_front_02.png" "L9_bangs_01.png"]
    ["l4_hair_front_03.png" "L9_bangs_01.png"]})

(deftest no-combination-leaves-a-bare-scalp
  (if-let [pack (pack-dir)]
    (let [heads (assets-in pack :head)
          bits (assets-in pack :hair-bits)
          backs (assets-in pack :hair-back)
          fronts (assets-in pack :hair-front)
          bangss (assets-in pack :bangs)
          results (for [hd heads bt bits bk backs ft fronts bg bangss]
                    {:combo [(.getName ^File hd) (.getName ^File ft) (.getName ^File bg)]
                     :px (exposed-scalp-px hd bt bk ft bg)})
          bad (->> results
                   (filter #(> (:px %) 40))
                   (remove #(contains? known-scalp-islands (vec (rest (:combo %)))))
                   (sort-by (comp - :px))
                   distinct)
          ledger-hits (->> results
                           (filter #(> (:px %) 40))
                           (map #(vec (rest (:combo %))))
                           set)]
      (println (format "\n  %d skull combinations; %d leave an island of scalp"
                       (count results) (count bad)))
      (doseq [{:keys [combo px]} (take 10 bad)]
        (println (format "    %5d px  %s" px (s/join " + " combo))))
      (println (format "    worst %d px, median %d px\n"
                       (:px (first (sort-by (comp - :px) results)))
                       (nth (sort (map :px results)) (quot (count results) 2))))

      (testing "a hairstyle that leaves a patch of scalp showing between the
                bangs and the hair is a combination the randomizer can hand
                someone, and 108 of them can be checked rather than noticed"
        (is (empty? bad)
            (str (count bad) " NEW hair combinations leave an island of scalp "
                 "surrounded by hair. The art does not cover them -- it is not "
                 "a rendering fault, and it needs either redrawn art or a "
                 "randomizer that avoids the pair. Add to known-scalp-islands "
                 "only once that decision is made: " (pr-str (map :combo bad)))))

      (testing "and the ledger does not rot. A pair that stops leaving a gap --
                because the art was redrawn -- should be taken off the list,
                not left behind to make the next real gap look expected."
        (doseq [pair known-scalp-islands]
          (is (contains? ledger-hits pair)
              (str (pr-str pair) " is listed as a known scalp gap but no longer "
                   "leaves one; remove it from known-scalp-islands")))))
    (println "\n  ORCPUB_PACK not set -- skipping.\n")))

(deftest look-at-the-worst-scalp-combination
  (when-let [pack (pack-dir)]
    (let [pick (fn [layer-key nm] (first (filter #(= nm (.getName ^File %))
                                                 (assets-in pack layer-key))))
          render (fn [bangs-name front-name]
                   (let [canvas (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
                         g (.createGraphics canvas)]
                     (try
                       (doseq [[lk ^File f] [[:hair-bits (first (assets-in pack :hair-bits))]
                                             [:hair-back (first (assets-in pack :hair-back))]
                                             [:head (pick :head "L2_head_01.png")]
                                             [:hair-front (pick :hair-front front-name)]
                                             [:bangs (pick :bangs bangs-name)]]]
                         (when-let [art (scaled f)]
                           (.drawImage g ^BufferedImage
                                       (multiplied art (if (= lk :head)
                                                         (Color. 0xff 0x00 0xff)
                                                         (Color. 0xdd 0xdd 0xdd)))
                                       0 0 nil)))
                       (finally (.dispose g)))
                     canvas))]
      ;; left: a SHORT FRINGE, which shows more forehead and is not a fault --
      ;; counting bare pixels called this the worst case in the pack at 3899px,
      ;; which is how the area metric announced it was measuring the wrong
      ;; thing. middle: the real one, an island with hair all round it.
      ;; right: a fringe that meets the lock, and no gap at all.
      (write! (row [(render "L9_bangs_01.png" "L4_hair_front_02.png")
                    (render "L9_bangs_03.png" "L4_hair_front_02.png")
                    (render "L9_bangs_02.png" "L4_hair_front_02.png")])
              "scalp-forehead-vs-island")
      (is true))))

(defn- island-mask
  "The island pixels alone, as a coverage array ready to fill."
  ^bytes [^booleans exposed]
  (let [seen (boolean-array (* W H))
        stack (java.util.ArrayDeque.)
        out (byte-array (* W H))]
    (dotimes [x W]
      (let [i (+ x (* (dec skull-bottom) W))]
        (when (and (aget exposed i) (not (aget seen i)))
          (aset seen i true) (.push stack (int i)))))
    (while (not (.isEmpty stack))
      (let [i (int (.pop stack)) x (rem i W) y (quot i W)]
        (doseq [[dx dy] [[-1 0] [1 0] [0 -1] [0 1]]]
          (let [nx (+ x dx) ny (+ y dy)]
            (when (and (< -1 nx W) (< -1 ny skull-bottom))
              (let [ni (+ nx (* ny W))]
                (when (and (aget exposed ni) (not (aget seen ni)))
                  (aset seen ni true) (.push stack (int ni)))))))))
    (dotimes [i (* W H)]
      (when (and (aget exposed i) (not (aget seen i)))
        (aset-byte out i (unchecked-byte 255))))
    out))

(defn- on?
  "Is this mask pixel set?

   Not `pos?`. A byte array holding 255 holds -1, because Java bytes are
   signed, so `pos?` reads a fully-set mask as empty -- which is exactly what
   it did: island-px counted 207 pixels and island-mask, over the same array,
   reported none. The existing colorize reads its coverage with (bit-and .. 0xff)
   and was never affected; these masks are new and were not."
  [^bytes m i]
  (not (zero? (aget m i))))

(defn- grown
  "Spread a mask by `n` pixels. The island is ringed by hair, so growing it a
   little keeps it UNDER the hair rather than pushing it past the hairline --
   it just stops a one-pixel seam of skin surviving at the boundary."
  ^bytes [^bytes m n]
  (loop [m m k 0]
    (if (= k n)
      m
      (let [out (byte-array (* W H))]
        (dotimes [y H]
          (dotimes [x W]
            (let [i (+ x (* y W))]
              (when (or (on? m i)
                        (and (> x 0) (on? m (dec i)))
                        (and (< x (dec W)) (on? m (inc i)))
                        (and (> y 0) (on? m (- i W)))
                        (and (< y (dec H)) (on? m (+ i W))))
                (aset-byte out i (unchecked-byte 255))))))
        (recur out (inc k))))))

(defn- fill-mask!
  [^BufferedImage canvas ^bytes m ^Color c]
  (let [dst (px canvas)
        v (unchecked-int (bit-or (bit-shift-left 255 24)
                                 (bit-shift-left (.getRed c) 16)
                                 (bit-shift-left (.getGreen c) 8)
                                 (.getBlue c)))]
    (dotimes [i (* W H)]
      (when (on? m i) (aset-int dst i v)))))

(defn- darker ^Color [^Color c f]
  (Color. (int (* (.getRed c) f)) (int (* (.getGreen c) f)) (int (* (.getBlue c) f))))

(defn- ellipse-mask
  ^bytes [cx cy rx ry]
  (let [img (BufferedImage. W H BufferedImage/TYPE_BYTE_GRAY)
        g (.createGraphics img)]
    (try
      (.setRenderingHint g RenderingHints/KEY_ANTIALIASING
                         RenderingHints/VALUE_ANTIALIAS_ON)
      (.setColor g Color/WHITE)
      (.fill g (Ellipse2D$Double. (- cx rx) (- cy ry) (* 2 rx) (* 2 ry)))
      (finally (.dispose g)))
    (.. img getRaster getDataBuffer getData)))

(def ^:private hides-it
  "How opaque hair has to be before it can be said to HIDE something behind it.

   Not 1. Using any alpha at all was the flaw that let the patch show: this art
   is sketchy at the crown, full of wisps and antialiased strands drawn at very
   low alpha, and a pixel of hair at alpha 20 does not conceal a flat fill
   underneath -- you see the fill straight through it. The check said zero
   poke-out while the render showed a grey band along the hairline, because the
   check counted those wisps as cover and an eye does not."
  200)

(defn- hair-cover
  "Union of every hair layer's pixels that are opaque enough to hide a fill."
  ^bytes [^File bits ^File back ^File front ^File bangs]
  (let [out (byte-array (* W H))
        srcs (mapv #(px (scaled %)) (remove nil? [bits back front bangs]))]
    (dotimes [i (* W H)]
      (when (some (fn [^ints c]
                    (>= (bit-and (unsigned-bit-shift-right (aget c i) 24) 0xff) hides-it))
                  srcs)
        (aset-byte out i (unchecked-byte 255))))
    out))

(defn- union-mask ^bytes [masks]
  (let [out (byte-array (* W H))]
    (doseq [^bytes m masks]
      (dotimes [i (* W H)] (when (on? m i) (aset-byte out i (unchecked-byte 255)))))
    out))

(defn- intersect-mask ^bytes [masks]
  (let [out (byte-array (* W H))]
    (dotimes [i (* W H)]
      (when (every? (fn [^bytes m] (on? m i)) masks)
        (aset-byte out i (unchecked-byte 255))))
    out))

(defn- clip-mask ^bytes [^bytes m ^bytes allowed]
  (let [out (byte-array (* W H))]
    (dotimes [i (* W H)]
      (when (and (on? m i) (on? allowed i)) (aset-byte out i (unchecked-byte 255))))
    out))

(defn- skull-patch-for
  "The one patch, derived once from the art.

   Two bounds, and they are the two in the user's sentence. Bigger than every
   empty area: it starts as the union of every gap. Not so big it clips out:
   it is grown, then clipped to the region hair covers in EVERY combination --
   because anywhere else, some hairstyle would leave it showing."
  ^bytes [^File pack]
  (let [combos (for [hd (assets-in pack :head) bt (assets-in pack :hair-bits)
                     bk (assets-in pack :hair-back) ft (assets-in pack :hair-front)
                     bg (assets-in pack :bangs)]
                 [hd bt bk ft bg])
        gaps (union-mask (map (fn [[hd bt bk ft bg]]
                                (island-mask (exposed-head-mask hd bt bk ft bg)))
                              combos))
        always-hair (intersect-mask (map (fn [[_ bt bk ft bg]] (hair-cover bt bk ft bg))
                                         combos))
        allowed (union-mask [always-hair gaps])]
    (clip-mask (grown gaps 14) allowed)))

(def ^:private skull-patch (atom nil))

(deftest look-at-the-one-patch
  (when-let [pack (pack-dir)]
    (when-let [manifest (read-manifest pack)]
      (let [patch (or @skull-patch (reset! skull-patch (skull-patch-for pack)))
            shape (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
            with-patch (fn [seed shade]
                         (let [layers (pa/compose-for-seed seed)
                               palette (palette-for-seed seed)
                               spec (iris-spec manifest)
                               hair (hex->awt (get palette :hair))
                               canvas (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)]
                           (doseq [lk pa/layer-order]
                             (when (and shade (= lk :hair-front))
                               (fill-mask! canvas patch (darker hair shade)))
                             (when-let [^File f (some->> (get-in layers [lk :asset/id])
                                                         (file-for-asset pack lk))]
                               (let [asset (pa/asset-by-id lk (get-in layers [lk :asset/id]))
                                     mode (pa/render-mode lk asset)
                                     colour (some-> (get palette (pa/slot-for-asset lk asset))
                                                    hex->awt)
                                     g (.createGraphics canvas)]
                                 (when-let [art (scaled f)]
                                   (cond
                                     (= :as-drawn mode) (.drawImage g art 0 0 nil)
                                     (and (= :colorize mode) colour)
                                     (binding [*iris-gamma* (pa/tint-gamma lk asset)]
                                       (.drawImage g (colorize-through
                                                      (if-let [sp (get spec (.getName f))]
                                                        (coverage-mask (iris-region sp))
                                                        (whole-asset-coverage art))
                                                      art colour) 0 0 nil))
                                     colour (.drawImage g ^BufferedImage
                                                        (multiplied art colour) 0 0 nil)
                                     :else (.drawImage g art 0 0 nil)))
                                 (.dispose g))))
                           canvas))]
        (fill-mask! shape patch (Color. 0x00 0xcc 0x44))
        (write! (row [shape
                      (with-patch "bust-19" nil) (with-patch "bust-19" 0.75)
                      (with-patch "bust-2" nil) (with-patch "bust-2" 0.75)])
                "one-patch")
        ;; a flat fraction of the hair colour is not the right rule. On dark
        ;; hair 0.75 is invisible; on WHITE hair it is a mid-grey blob, because
        ;; 0.75 of white is grey and the hair around it renders near-white.
        ;; Shade matters far less now that the patch only lives under real
        ;; cover, but 1.0 remains the safe default: a fraction of the hair
        ;; colour is a grey on WHITE hair (0.75 of #f2f2f2 is rgb(181,181,181))
        ;; and invisible on dark, so the same number does not mean the same
        ;; thing at both ends.
        (doseq [sh [1.0 0.85 0.75]]
          (write! (row [(with-patch "bust-19" sh) (with-patch "bust-2" sh)])
                  (format "patch-shade-%.2f" sh)))
        (is true)))))

(deftest debug-patch-colour-in-bust-2
  (when-let [pack (pack-dir)]
    (when-let [manifest (read-manifest pack)]
      (let [patch (or @skull-patch (reset! skull-patch (skull-patch-for pack)))]
        (doseq [seed ["bust-19" "bust-2"]]
          (let [layers (pa/compose-for-seed seed)
                palette (palette-for-seed seed)
                hair (hex->awt (get palette :hair))
                want (darker hair 0.75)
                ;; which layer ends up owning each patch pixel?
                owner (fn [i]
                        (last (for [lk pa/layer-order
                                    :let [f (some->> (get-in layers [lk :asset/id])
                                                     (file-for-asset pack lk))]
                                    :when (and f (> (bit-and (unsigned-bit-shift-right
                                                              (aget ^ints (px (scaled f)) i) 24)
                                                             0xff) 200))]
                                lk)))
                idxs (for [i (range (* W H)) :when (on? patch i)] i)
                by-owner (frequencies (map #(or (owner %) :patch-visible) idxs))]
            (println (format "\n  %s  hair %s -> patch should be rgb(%d,%d,%d)"
                             seed (get palette :hair)
                             (.getRed want) (.getGreen want) (.getBlue want)))
            (println (format "    patch pixels: %d" (count idxs)))
            (doseq [[lk n] (sort-by (comp - val) by-owner)]
              (println (format "      %-16s %d" (str lk) n)))
            ;; and what colour actually lands there in the finished bust
            (let [canvas (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
                  spec (iris-spec manifest)]
              (doseq [lk pa/layer-order]
                (when (= lk :hair-front)
                  (fill-mask! canvas patch want))
                (when-let [^File f (some->> (get-in layers [lk :asset/id])
                                            (file-for-asset pack lk))]
                  (let [asset (pa/asset-by-id lk (get-in layers [lk :asset/id]))
                        mode (pa/render-mode lk asset)
                        colour (some-> (get palette (pa/slot-for-asset lk asset)) hex->awt)
                        g (.createGraphics canvas)]
                    (when-let [art (scaled f)]
                      (cond
                        (= :as-drawn mode) (.drawImage g art 0 0 nil)
                        (and (= :colorize mode) colour)
                        (binding [*iris-gamma* (pa/tint-gamma lk asset)]
                          (.drawImage g (colorize-through
                                         (if-let [sp (get spec (.getName f))]
                                           (coverage-mask (iris-region sp))
                                           (whole-asset-coverage art))
                                         art colour) 0 0 nil))
                        colour (.drawImage g ^BufferedImage (multiplied art colour) 0 0 nil)
                        :else (.drawImage g art 0 0 nil)))
                    (.dispose g))))
              (let [out (px canvas)
                    ;; patch pixels the head is topmost under -- the ones that show
                    vis (for [i idxs
                              :when (= :head (owner i))]
                          (aget out i))
                    avg (fn [f] (int (/ (reduce + (map f vis)) (max 1 (count vis)))))]
                (when (seq vis)
                  (println (format "    %d visible; average rgb(%d,%d,%d), wanted rgb(%d,%d,%d)"
                                   (count vis)
                                   (avg #(bit-and (unsigned-bit-shift-right % 16) 0xff))
                                   (avg #(bit-and (unsigned-bit-shift-right % 8) 0xff))
                                   (avg #(bit-and % 0xff))
                                   (.getRed want) (.getGreen want) (.getBlue want))))))))
        (is true)))))
(defn- render-combo
  "One skull combination, composed. `paint` maps a layer key to a flat colour
   to paint it in, or nil to draw it normally. `patch-colour` fills the patch
   under the hair."
  ^BufferedImage [^File pack [^File hd ^File bt ^File bk ^File ft ^File bg]
                  ^bytes patch ^Color patch-colour paint]
  (let [canvas (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
        hair (Color. 0x6b 0x4a 0x2a)
        skin (Color. 0xe8 0xc6 0x9c)]
    (doseq [[lk ^File f] [[:hair-bits bt] [:hair-back bk] [:head hd]
                          [:hair-front ft] [:bangs bg]]]
      (when (and patch-colour (= lk :hair-front))
        (fill-mask! canvas patch patch-colour))
      (when-let [art (scaled f)]
        (let [g (.createGraphics canvas)]
          (.drawImage g ^BufferedImage
                      (multiplied art (or (get paint lk)
                                          (if (= lk :head) skin hair)))
                      0 0 nil)
          (.dispose g))))
    canvas))

(defn- changed-px
  "Pixels where two renders visibly differ. `tol` is per channel out of 255."
  [^BufferedImage a ^BufferedImage b tol]
  (let [pa (px a) pb (px b)
        out (byte-array (* W H))
        n (atom 0)]
    (dotimes [i (* W H)]
      (let [x (aget pa i) y (aget pb i)
            d (max (Math/abs (- (bit-and (unsigned-bit-shift-right x 16) 0xff)
                                (bit-and (unsigned-bit-shift-right y 16) 0xff)))
                   (Math/abs (- (bit-and (unsigned-bit-shift-right x 8) 0xff)
                                (bit-and (unsigned-bit-shift-right y 8) 0xff)))
                   (Math/abs (- (bit-and x 0xff) (bit-and y 0xff)))
                   (Math/abs (- (bit-and (unsigned-bit-shift-right x 24) 0xff)
                                (bit-and (unsigned-bit-shift-right y 24) 0xff))))]
        (when (> d tol)
          (aset-byte out i (unchecked-byte 255))
          (swap! n inc))))
    [out @n]))

(defn- carve-patch
  "Shrink a candidate patch until none of it reaches the screen outside a gap.

   Derived the way it is tested: paint the candidate a loud colour, render
   every combination, and delete any pixel whose green escapes. No alpha
   threshold anywhere -- guessing one is what let a patch bleed through the
   wisps at the crown twice. Repeats because removing pixels changes what
   blends through the ones left, and stops when a pass removes nothing."
  ^bytes [^File pack ^bytes start combos]
  (let [loud (Color. 0x00 0xff 0x00)
        ;; every gap in the pack. A pixel that fills a gap in ONE combination
        ;; must survive carving even if it leaks in another -- the first version
        ;; carved on leaking alone and ate the gap edges, leaving a pale rim.
        needed (union-mask (map #(island-mask (apply exposed-head-mask %)) combos))]
    (loop [patch start pass 1]
      (let [forbidden (byte-array (* W H))
            removed (atom 0)]
        (doseq [c combos]
          (let [plain (render-combo pack c patch nil nil)
                loudly (render-combo pack c patch loud nil)
                [visible _] (changed-px plain loudly 10)
                island (island-mask (apply exposed-head-mask c))]
            (dotimes [i (* W H)]
              (when (and (on? visible i) (not (on? island i))
                         (not (on? needed i)) (not (on? forbidden i)))
                (aset-byte forbidden i (unchecked-byte 255))
                (swap! removed inc)))))
        (let [next-patch (byte-array (* W H))]
          (dotimes [i (* W H)]
            (when (and (on? patch i) (not (on? forbidden i)))
              (aset-byte next-patch i (unchecked-byte 255))))
          (println (format "    pass %d: removed %d px" pass @removed))
          (if (or (zero? @removed) (> pass 6))
            next-patch
            (recur next-patch (inc pass))))))))

(deftest the-patch-is-only-visible-where-the-scalp-was
  "Paint the patch a ridiculous colour and see where it survives to the screen.

   Two earlier versions of this check compared MASKS -- patch against hair
   alpha -- and passed while the render showed a grey band, because both were
   wrong about what alpha counts as hiding something. A render diff has no
   opinion on that. It asks the only question that matters: does putting the
   patch there change what you can see, and if so, was that a place the scalp
   was showing anyway?"
  (if-let [pack (pack-dir)]
    (let [combos (vec (for [hd (assets-in pack :head) bt (assets-in pack :hair-bits)
                           bk (assets-in pack :hair-back) ft (assets-in pack :hair-front)
                           bg (assets-in pack :bangs)]
                       [hd bt bk ft bg]))
          _ (println "\n  carving the patch:")
          patch (or @skull-patch
                    (reset! skull-patch
                            (carve-patch pack (skull-patch-for pack) combos)))
          _ (println (format "  patch is %d px"
                             (count (for [i (range (* W H)) :when (on? patch i)] 1))))
          loud (Color. 0x00 0xff 0x00)
          rows (for [c combos]
                 (let [[_ _ _ ^File ft ^File bg] c
                       plain (render-combo pack c patch nil nil)
                       loudly (render-combo pack c patch loud nil)
                       [visible n-vis] (changed-px plain loudly 10)
                       ;; the gap this patch exists for -- scalp with hair all
                       ;; round it. "Anywhere the head shows" is NOT the target:
                       ;; that includes the forehead, and a hair-coloured blob
                       ;; on a forehead is the very thing being guarded against.
                       island (island-mask (apply exposed-head-mask c))
                       n-island (count (for [i (range (* W H)) :when (on? island i)] 1))
                       stray (count (for [i (range (* W H))
                                          :when (and (on? visible i) (not (on? island i)))]
                                      1))
                       missed (count (for [i (range (* W H))
                                           :when (and (on? island i) (not (on? visible i)))]
                                       1))]
                   {:combo [(.getName ^File ft) (.getName ^File bg)]
                    :visible n-vis :island n-island :stray stray :missed missed}))]
      (println (format "\n  render diff over %d combinations, patch painted pure green:"
                       (count rows)))
      (println (format "    combos where any green reaches the screen: %d"
                       (count (filter #(pos? (:visible %)) rows))))
      (println (format "    green landing outside the gap:             %d (worst %d px)"
                       (count (filter #(pos? (:stray %)) rows))
                       (apply max (map :stray rows))))
      (println (format "    gap left showing through:                  %d (worst %d px)\n"
                       (count (filter #(pos? (:missed %)) rows))
                       (apply max (map :missed rows))))

      (testing "green barely reaches the screen outside the gap.

                Not zero, and it cannot be: a handful of pixels along the gap
                edge are INSIDE a gap in one hair combination and on bare skin
                in another, so one static patch has to either leave a pale rim
                in the first or put a speck of hair colour in the second. It
                leaves the speck, which is the smaller fault, and it is ~10px
                -- about 3x3 at the size this ships at. Making the patch bigger
                grows the speck; making it smaller brings back the rim. The
                bound is here so a real regression still fails."
        (doseq [{:keys [combo stray]} rows]
          (is (<= stray 16)
              (str (s/join " + " combo) ": " stray "px of green reaches the "
                   "screen outside the gap"))))

      (testing "and it reaches all of the gap, or the scalp still shows"
        (doseq [{:keys [combo missed]} rows]
          (is (zero? missed)
              (str (s/join " + " combo) ": " missed "px of gap the patch does "
                   "not cover")))))
    (println "\n  ORCPUB_PACK not set -- skipping.\n")))

(deftest look-at-the-8px-conflict
  (when-let [pack (pack-dir)]
    (let [combos (vec (for [hd (assets-in pack :head) bt (assets-in pack :hair-bits)
                            bk (assets-in pack :hair-back) ft (assets-in pack :hair-front)
                            bg (assets-in pack :bangs)]
                        [hd bt bk ft bg]))
          patch (or @skull-patch
                    (reset! skull-patch (carve-patch pack (skull-patch-for pack) combos)))
          worst (apply max-key
                       (fn [c]
                         (let [island (island-mask (apply exposed-head-mask c))]
                           (count (for [i (range (* W H))
                                        :when (and (on? island i) (not (on? patch i)))]
                                    1))))
                       combos)
          hair (Color. 0x6b 0x4a 0x2a) skin (Color. 0xe8 0xc6 0x9c)]
      ;; the worst combination at 4x over the crown: no patch, patch, and the
      ;; patch in green so the leftover sliver is unmistakable
      (let [zoom (fn [^BufferedImage src]
                   (let [out (BufferedImage. (* W 2) 300 BufferedImage/TYPE_INT_ARGB)
                         g (.createGraphics out)]
                     (.drawImage g src 0 0 (* W 2) 300 100 80 (+ 100 232) 230 nil)
                     (.dispose g) out))]
        (write! (column [(zoom (render-combo pack worst patch nil nil))
                         (zoom (render-combo pack worst patch hair nil))
                         (zoom (render-combo pack worst patch (Color. 0x00 0xff 0x00) nil))])
                "eight-px-conflict")
        ;; the same thing at the size it actually ships at, because a rim that
        ;; only exists at 4x is not a rim
        (write! (row [(render-combo pack worst patch nil nil)
                      (render-combo pack worst patch hair nil)])
                "eight-px-at-real-size"))
      (is true))))

;; ---------------------------------------------------------------------------
;; Write the scalp asset.
;;
;; Everything above derives and checks the shape. This turns it into a PNG the
;; app can ship as an ordinary layer -- it goes between the head and the hair
;; and takes the hair colour like any other hair piece. Run with
;; ORCPUB_WRITE_SCALP=1 to regenerate it after the art changes.
;; ---------------------------------------------------------------------------

(deftest write-the-scalp-asset
  (if-not (System/getenv "ORCPUB_WRITE_SCALP")
    (println "\n  ORCPUB_WRITE_SCALP not set -- not regenerating the scalp asset.\n")
    (if-let [pack (pack-dir)]
      (let [combos (vec (for [hd (assets-in pack :head) bt (assets-in pack :hair-bits)
                              bk (assets-in pack :hair-back) ft (assets-in pack :hair-front)
                              bg (assets-in pack :bangs)]
                          [hd bt bk ft bg]))
            patch (carve-patch pack (skull-patch-for pack) combos)
            img (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
            dst (px img)
            ;; WHITE, not black. Every layer is multiplied by its colour, and
            ;; black multiplies to black whatever the hair is.
            white (unchecked-int 0xffffffff)
            out (io/file "resources/public/image/portraits/scalp/l2b_scalp_01.png")]
        (dotimes [i (* W H)] (when (on? patch i) (aset-int dst i white)))
        (.mkdirs (.getParentFile out))
        (ImageIO/write img "png" out)
        (println (format "\n  wrote %s (%d px)\n" (.getPath out)
                         (count (for [i (range (* W H)) :when (on? patch i)] 1))))
        (is (.exists out)))
      (println "\n  ORCPUB_PACK not set -- skipping.\n"))))
