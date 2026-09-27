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
