(ns orcpub.portrait-render-test
  "Server-side portrait rasterization, for og:image. A share crawler has no
   browser to bake the CSS-mask layers the way the PDF export does, so these
   pixels are produced here -- and, like every other picture path, must
   degrade to 'no portrait' rather than throw."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.portrait-render :as pr]
            [orcpub.dnd.e5.portrait-assets :as pa])
  (:import [java.io ByteArrayInputStream]
           [javax.imageio ImageIO]))

(defn- portrait-with [layer-keys & {:keys [colors tweaks]}]
  {:layers (into {}
                 (keep (fn [k]
                         (when-let [a (first (pa/assets-for-layer k))]
                           [k {:artist/id (pa/artist-for-asset k (:asset/id a))
                               :asset/id  (:asset/id a)}])))
                 layer-keys)
   :colors (or colors {})
   :tweaks (or tweaks {})})

(defn- decode [png]
  (ImageIO/read (ByteArrayInputStream. png)))

(defn- opaque-pixels [img]
  (let [w (.getWidth img) h (.getHeight img)]
    (count (for [x (range 0 w 4) y (range 0 h 4)
                 :when (pos? (bit-and (unsigned-bit-shift-right (.getRGB img x y) 24) 0xff))]
             true))))

;; ---------- data URIs ----------

(deftest parses-base64-data-uris
  (let [{:keys [mime bytes]} (pr/parse-data-uri "data:image/svg+xml;base64,PHN2Zy8+")]
    (is (= "image/svg+xml" mime))
    (is (= "<svg/>" (String. ^bytes bytes "UTF-8")))))

(deftest rejects-unusable-data-uris
  (is (nil? (pr/parse-data-uri nil)))
  (is (nil? (pr/parse-data-uri "")))
  (is (nil? (pr/parse-data-uri "https://example.com/a.png")) "plain URL")
  (is (nil? (pr/parse-data-uri "data:image/svg+xml;utf8,<svg/>")) "non-base64 form")
  (is (nil? (pr/parse-data-uri "data:image/png;base64,!!!!")) "malformed payload"))

;; ---------- colors ----------

(deftest parses-hex-colors
  (is (= (java.awt.Color. 240 161 0) (pr/hex->color "#f0a100")))
  (is (= (java.awt.Color. 255 255 255) (pr/hex->color "#FFFFFF")) "case insensitive"))

(deftest rejects-bad-hex
  (is (nil? (pr/hex->color nil)))
  (is (nil? (pr/hex->color "#fff")) "shorthand not supported")
  (is (nil? (pr/hex->color "red")))
  (is (nil? (pr/hex->color "#gggggg"))))

;; ---------- path building ----------

(deftest builds-a-path-from-svg-ops
  (let [p (pr/ops->path2d [[:move 0.0 0.0] [:line 10.0 0.0] [:line 10.0 10.0] [:close]] 2.0 3.0)
        b (.getBounds2D p)]
    (is (= 0.0 (.getX b)))
    (is (= 20.0 (.getWidth b)) "x scaled by sx")
    (is (= 30.0 (.getHeight b)) "y scaled by sy")))

(deftest ignores-unknown-ops
  (testing "a parser that grows a new op must not crash the renderer"
    (is (some? (pr/ops->path2d [[:move 0.0 0.0] [:something-new 1.0] [:line 5.0 5.0]] 1.0 1.0)))))

;; ---------- rendering ----------

(deftest renders-a-composed-portrait
  (let [png (pr/render-png (portrait-with [:head :shirt :eyes]))]
    (is (bytes? png))
    (is (pos? (alength png)))
    (testing "output is a real PNG"
      (is (= [-119 80 78 71] (take 4 (vec png)))))
    (let [img (decode png)]
      (is (= 600 (.getWidth img)))
      (is (= 750 (.getHeight img)))
      (is (pos? (opaque-pixels img)) "something was actually drawn"))))

(deftest honors-size
  (let [img (decode (pr/render-png (portrait-with [:head]) 120 150))]
    (is (= 120 (.getWidth img)))
    (is (= 150 (.getHeight img)))))

(deftest more-layers-cover-more-canvas
  (testing "layers composite rather than replace one another"
    (let [one (opaque-pixels (decode (pr/render-png (portrait-with [:head]))))
          two (opaque-pixels (decode (pr/render-png (portrait-with [:head :shirt]))))]
      (is (> two one) (str "head=" one " head+shirt=" two)))))

(deftest character-colors-reach-the-pixels
  (testing "a slot color changes the rendered output"
    (let [plain (pr/render-png (portrait-with [:head]))
          tinted (pr/render-png (portrait-with [:head] :colors {:skin "#ff0000"}))]
      (is (not= (vec plain) (vec tinted))))))

(deftest per-piece-override-reaches-the-pixels
  (let [base (pr/render-png (portrait-with [:head] :colors {:skin "#808080"}))
        over (pr/render-png (portrait-with [:head]
                                           :colors {:skin "#808080"}
                                           :tweaks {:head {:override "#00ff00"}}))]
    (is (not= (vec base) (vec over)))))

;; ---------- degradation ----------

(deftest nil-when-nothing-to-draw
  (is (nil? (pr/render-png {:layers {} :colors {} :tweaks {}})))
  (is (nil? (pr/render-png nil)))
  (is (nil? (pr/render-png {:layers {:head {:asset/id :no-such-asset}}}))
      "a selection naming an unknown asset draws nothing"))

(deftest survives-a-corrupt-layer
  (testing "one bad tint does not cost the whole portrait"
    (let [p (assoc-in (portrait-with [:head :shirt])
                      [:tweaks :head :override] "not-a-color")
          png (pr/render-png p)]
      (is (bytes? png) "still rendered the layers it could")
      (is (pos? (opaque-pixels (decode png)))))))
