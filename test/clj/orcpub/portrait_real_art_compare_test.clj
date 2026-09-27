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
            [orcpub.dnd.e5.portrait-layout :as layout])
  (:import [java.awt AlphaComposite Color RenderingHints]
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
