(ns orcpub.portrait-tint-behaviour-test
  "What the compositor does to artwork that is not a silhouette.

   Both renderers tint the same way: the asset supplies the SHAPE and the
   character supplies the COLOUR. The browser does it with a CSS mask over a
   background-colour, the server with AlphaComposite/SrcIn over a fillRect. Both
   read the asset's ALPHA and discard its RGB entirely.

   That is invisible while the assets are silhouettes, because a silhouette is
   nothing but alpha. It stops being invisible the moment real line art arrives,
   and it decides how that art has to be drawn -- so it is worth knowing before
   an illustrator spends a week on files that will not survive the pipeline.

   Set ORCPUB_TINT_OUT=<dir> to write the pictures out and look at them."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.portrait-render :as portrait-render]
            [orcpub.dnd.e5.portrait-assets :as pa])
  (:import [java.awt BasicStroke Color RenderingHints]
           [java.awt.image BufferedImage]
           [java.io ByteArrayOutputStream File]
           [javax.imageio ImageIO]))

(def ^:private w 240)
(def ^:private h 300)

(defn- png-bytes ^bytes [^BufferedImage img]
  (let [out (ByteArrayOutputStream.)]
    (ImageIO/write img "png" out)
    (.toByteArray out)))

(defn- line-art-sample
  "A stand-in for the real thing: opaque BLACK outline, opaque WHITE fill,
   transparent outside. This is how line art is normally drawn."
  ^BufferedImage []
  (let [img (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics img)]
    (.setRenderingHint g RenderingHints/KEY_ANTIALIASING
                       RenderingHints/VALUE_ANTIALIAS_OFF)
    (.setColor g Color/WHITE)
    (.fillOval g 40 40 160 200)
    (.setColor g Color/BLACK)
    (.setStroke g (BasicStroke. 8))
    (.drawOval g 40 40 160 200)
    (.drawLine g 90 120 90 150)   ; an eye
    (.drawLine g 150 120 150 150) ; the other
    (.dispose g)
    img))

(defn- silhouette-sample
  "What the repo ships today: one opaque shape, no interior detail."
  ^BufferedImage []
  (let [img (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics img)]
    (.setColor g Color/BLACK)
    (.fillOval g 40 40 160 200)
    (.dispose g)
    img))

(defn- tinted
  "Run a sample through the renderer's own tint, not a copy of it."
  ^BufferedImage [^BufferedImage sample ^Color colour]
  (let [canvas (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics canvas)]
    (try
      (#'portrait-render/draw-raster-layer! g (png-bytes sample) colour w h)
      (finally (.dispose g)))
    canvas))

(defn- opaque-colours
  "Distinct RGB values among the fully opaque pixels."
  [^BufferedImage img]
  (into #{}
        (for [x (range w) y (range h)
              :let [argb (.getRGB img x y)]
              :when (= 255 (bit-and (unsigned-bit-shift-right argb 24) 0xff))]
          (bit-and argb 0xffffff))))

(defn- write! [^BufferedImage img nm]
  (when-let [dir (System/getenv "ORCPUB_TINT_OUT")]
    (.mkdirs (File. dir))
    (ImageIO/write img "png" (File. dir (str nm ".png")))))

(deftest tinting-keeps-the-shape-and-discards-the-drawing
  (let [skin (Color. 0xE8 0xB0 0x90)
        art (line-art-sample)
        sil (silhouette-sample)
        art-tinted (tinted art skin)
        sil-tinted (tinted sil skin)]
    (write! art "1-line-art-source")
    (write! art-tinted "2-line-art-tinted")
    (write! sil "3-silhouette-source")
    (write! sil-tinted "4-silhouette-tinted")

    (testing "the source really is line art: black lines over a white fill"
      (is (<= 2 (count (opaque-colours art)))
          "two or more colours before tinting -- lines and fill"))

    (testing "after tinting, the drawing is gone"
      (let [colours (opaque-colours art-tinted)]
        (is (= 1 (count colours))
            (str "line art flattens to a single colour: " colours))
        (is (= #{0xE8B090} colours)
            "and that colour is the character's, not the artist's")))

    (testing "a silhouette loses nothing, because it had nothing to lose"
      (is (= 1 (count (opaque-colours sil))))
      (is (= #{0xE8B090} (opaque-colours sil-tinted))))

    (testing "so the two are indistinguishable once tinted -- which is the
              finding: the pipeline cannot tell a detailed drawing from its
              own outline, because it only ever reads alpha"
      (is (= (opaque-colours art-tinted) (opaque-colours sil-tinted))))))

(deftest partial-alpha-is-what-survives
  (testing "shading has to be drawn in the ALPHA channel, not in grey: a
            half-transparent pixel keeps its softness through the tint, while a
            grey opaque one comes out the same as a black opaque one"
    (let [img (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)]
      ;; column of opaque grey, column of half-transparent black
      (doseq [y (range 100)]
        (.setRGB img 50 y (unchecked-int 0xFF808080))
        (.setRGB img 100 y (unchecked-int 0x80000000)))
      (let [out (tinted img (Color. 0xE8 0xB0 0x90))
            alpha-at (fn [x y] (bit-and (unsigned-bit-shift-right (.getRGB out x y) 24) 0xff))]
        (write! out "5-alpha-vs-grey")
        (is (= 255 (alpha-at 50 10)) "opaque grey came out fully opaque -- the grey is lost")
        (is (< 100 (alpha-at 100 10) 160)
            "half-transparent black stayed half-transparent -- alpha is the channel
             that carries shading through")))))

;; ---------------------------------------------------------------------------
;; What one stored portrait costs, for sizing a cache. Prints rather than
;; asserts a threshold: the number that matters is the one the REAL art
;; produces, and this measures silhouettes.
;; ---------------------------------------------------------------------------

(deftest print-the-size-of-one-composed-portrait
  (let [portrait {:layers (into {}
                                (keep (fn [k]
                                        (when-let [a (first (pa/assets-for-layer k))]
                                          [k {:artist/id (pa/artist-for-asset k (:asset/id a))
                                              :asset/id (:asset/id a)}])))
                                pa/layer-order)
                  :colors {:hair "#5c3a1e" :skin "#e8c69c" :eyes "#3b6ea5" :clothing "#7a5c3a"}
                  :tweaks {}}
        png (portrait-render/render-png portrait)]
    (is (some? png) "the full stack of layers renders")
    (println (format "\n  COMPOSED PORTRAIT: %d layers, %.1f KB as PNG\n"
                     (count (:layers portrait)) (/ (count png) 1024.0)))))
