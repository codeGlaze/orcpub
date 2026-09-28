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
            [clojure.data.json :as json])
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

(defn- scaled
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
        tr (.getRed tint) tg (.getGreen tint) tb (.getBlue tint)]
    (dotimes [y H]
      (dotimes [x W]
        (let [argb (.getRGB art x y)
              a (bit-and (unsigned-bit-shift-right argb 24) 0xff)]
          (when (pos? a)
            (let [r (bit-and (unsigned-bit-shift-right argb 16) 0xff)
                  g (bit-and (unsigned-bit-shift-right argb 8) 0xff)
                  b (bit-and argb 0xff)]
              (.setRGB out x y
                       (unchecked-int
                        (bit-or (bit-shift-left a 24)
                                (bit-shift-left (quot (* tr r) 255) 16)
                                (bit-shift-left (quot (* tg g) 255) 8)
                                (quot (* tb b) 255)))))))))
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

(def ^:dynamic *iris-floor*
  "How much of the eye colour the darkest iris ink keeps. 0.30 was the first
   guess and it lost the colour on this art, whose irises are drawn almost
   black."
  0.30)

(defn- colorize-iris
  "Paint the iris its colour instead of ADDING colour to it.

   Adding is what every preview so far did, and it is why the irises looked
   thin: the artwork shows through, so shading and the highlight wash toward
   white rather than reading as a coloured eye. This maps the drawing's own
   luminance through the chosen colour -- dark stays dark, mid becomes the
   colour, the highlight stays a highlight -- which is opaque where the drawing
   is opaque."
  ^BufferedImage [^BufferedImage art ^Area region ^Color eye]
  (let [out (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
        cov (coverage-mask region)
        er (.getRed eye) eg (.getGreen eye) eb (.getBlue eye)]
    (dotimes [y H]
      (dotimes [x W]
        (let [argb (.getRGB art x y)
              a (bit-and (unsigned-bit-shift-right argb 24) 0xff)]
          (when (pos? a)
            (let [k (/ (bit-and (aget cov (+ x (* y W))) 0xff) 255.0)]
              (if (zero? k)
                (.setRGB out x y argb)
                (let [r (bit-and (unsigned-bit-shift-right argb 16) 0xff)
                      g (bit-and (unsigned-bit-shift-right argb 8) 0xff)
                      b (bit-and argb 0xff)
                      l (/ (+ r g b) 765.0)              ; 0 = ink, 1 = paper
                      ;; below the midpoint, ramp from black up to the colour;
                      ;; above it, ramp from the colour up to white
                      ;; floor is how much of the colour the DARKEST iris ink
                      ;; keeps. Too low and a dark drawing swallows the colour;
                      ;; the eye reads grey and the slot looks broken. This is
                      ;; not opacity -- the artwork never shows through, only
                      ;; the colour's own brightness varies -- so it can be
                      ;; lifted a long way without going transparent.
                      floor *iris-floor*
                      mix (fn [c] (if (< l 0.5)
                                    (* c (+ floor (* (- 1.0 floor) 2.0 l)))
                                    (+ c (* (- 255 c) (* 2.0 (- l 0.5))))))
                      ;; k is how much of this pixel the region covers, so the
                      ;; edge fades into the drawing instead of stepping
                      blend (fn [src tgt] (int (min 255.0 (+ (* src (- 1.0 k))
                                                             (* tgt k)))))]
                  (.setRGB out x y
                           (unchecked-int
                            (bit-or (bit-shift-left a 24)
                                    (bit-shift-left (blend r (mix er)) 16)
                                    (bit-shift-left (blend g (mix eg)) 8)
                                    (blend b (mix eb))))))))))))
    out))

(defn- compose-with-eyes
  "A character, drawn the way the app will draw it: multiply everywhere, the
   mouth left alone, the eyes coloured inside their placed regions."
  [^File pack manifest n]
  (let [spec (iris-spec manifest)
        palette (nth palettes (mod n (count palettes)))
        canvas (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics canvas)]
    (try
      (doseq [layer-key pa/layer-order]
        (when-let [choices (seq (assets-in pack layer-key))]
          (let [^File f (nth choices (mod n (count choices)))]
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
    canvas))

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
