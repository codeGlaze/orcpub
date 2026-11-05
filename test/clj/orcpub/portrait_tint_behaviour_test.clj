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

(deftest tinting-keeps-the-drawing
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

    (testing "after tinting, the drawing survives.

              This USED to assert the opposite, and the opposite was true: the
              renderer masked, which reads the asset's alpha and discards its
              RGB, so line art came out as one flat colour. Multiply replaced
              it, and these are the same three checks with the answers the
              renderer now gives."
      (let [colours (opaque-colours art-tinted)]
        (is (< 1 (count colours))
            (str "more than one colour survives, so the lines are still there: "
                 colours))
        (is (contains? colours 0xE8B090)
            "the white fill came out exactly the character's colour")
        (is (contains? colours 0x000000)
            "and the black lines stayed black")))

    (testing "a BLACK silhouette multiplies to black, not to the tint.

              This is the catch that comes with multiply, and it is live now
              rather than hypothetical: any silhouette that has to survive this
              pipeline must be drawn WHITE. The ones shipping in
              resources/public/image/portraits are near-white already (mean
              opaque brightness ~228 of 255), which is why the switch did not
              black out the placeholder art."
      (is (= #{0x000000} (opaque-colours sil-tinted))))

    (testing "so a drawing and a silhouette are no longer the same picture --
              which was the whole point of the change"
      (is (not= (opaque-colours art-tinted) (opaque-colours sil-tinted))))))

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

;; ---------------------------------------------------------------------------
;; The fix, proven before it is adopted.
;;
;; Masking is not the only way to colour line art, and it is the wrong one for
;; art that HAS lines. The usual technique is multiply: lay the character's
;; colour down through the artwork's alpha, then multiply the artwork back over
;; it. White fill leaves the tint alone; black lines stay black; a grey darkens
;; the tint by however grey it is.
;;
;; Java2D has no multiply -- AlphaComposite is Porter-Duff only -- so on the
;; server it is a pixel pass. This shows that pass keeping what SrcIn destroys.
;; ---------------------------------------------------------------------------

(defn- multiply-tint
  "out.rgb = tint.rgb * art.rgb, out.a = art.a. The candidate replacement for
   the AlphaComposite/SrcIn in draw-raster-layer!."
  ^BufferedImage [^BufferedImage art ^Color tint]
  (let [aw (.getWidth art) ah (.getHeight art)
        out (BufferedImage. aw ah BufferedImage/TYPE_INT_ARGB)
        tr (.getRed tint) tg (.getGreen tint) tb (.getBlue tint)]
    (dotimes [y ah]
      (dotimes [x aw]
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

(deftest multiply-keeps-the-drawing-that-masking-destroys
  (let [skin (Color. 0xE8 0xB0 0x90)
        art (line-art-sample)
        masked (tinted art skin)
        multiplied (multiply-tint art skin)]
    (write! multiplied "6-line-art-multiplied")

    (testing "the renderer and this reference implementation agree.

              `masked` is a historical name: it runs the shipping
              draw-raster-layer!, which multiplies now, so it produces what
              multiply-tint produces."
      (is (= (opaque-colours masked) (opaque-colours multiplied))))

    (testing "multiply keeps the lines AND takes the character's colour"
      (let [colours (opaque-colours multiplied)]
        (is (< 1 (count colours))
            "more than one colour survives, so the drawing is still there")
        (is (contains? colours 0xE8B090)
            "the white fill came out exactly the character's colour")
        (is (contains? colours 0x000000)
            "and the black lines stayed black")))

    (testing "a silhouette is unharmed by the change, so both kinds of asset
              can share one pipeline"
      (let [sil (silhouette-sample)]
        (is (= #{0x000000} (opaque-colours (multiply-tint sil skin)))
            "a pure black silhouette multiplies to black, NOT to the tint --
             which is the catch: silhouettes would have to be white, not black,
             for multiply to colour them")))))
