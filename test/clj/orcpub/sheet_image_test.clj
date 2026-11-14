(ns orcpub.sheet-image-test
  "Portrait and faction images must land on the template artwork on any paper.

   fit-sheet-to-page! re-papers the sheet by moving the MEDIABOX, so the template's
   content keeps its Letter coordinate space. Anything drawn onto a template page after
   that must measure against Letter; measuring against the requested page instead puts
   the image 49.89pt off on A4."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [orcpub.pdf-ops :as ops]
            [orcpub.pdf :as pdf]
            [orcpub.pdf.draw :as draw])
  (:import (java.awt.image BufferedImage)
           (java.io ByteArrayOutputStream)
           (javax.imageio ImageIO)
           (org.apache.pdfbox.pdmodel PDDocument PDPage)))

(def ^:private png
  "A 4x4 PNG. Only its placement matters here, not its content."
  (delay
    (let [img (BufferedImage. 4 4 BufferedImage/TYPE_INT_RGB)
          out (ByteArrayOutputStream.)]
      (ImageIO/write img "png" out)
      (.toByteArray out))))

(defn- portrait-cm
  "The image placement matrices on a Letter template page, with `*page*` bound to `page`.
   `cm` carries the scale and translate PDFBox writes for a drawn image."
  [page]
  (binding [draw/*page* page]
    (with-open [doc (PDDocument.)]
      (let [pg (PDPage.)]                      ; a bare PDPage is Letter, like the templates
        (.addPage doc pg)
        ;; The style 1 portrait box, as routes.clj passes it.
        (pdf/draw-image-bytes! doc pg @png false 0.45 1.75 2.35 3.15)
        (vec (filter #(str/ends-with? % " cm") (ops/page-ops pg)))))))

(deftest template-images-ignore-the-requested-page-size
  (let [letter (portrait-cm :letter)]
    (testing "the placement is the template's, so it is the same on every paper"
      (is (seq letter) "an image was drawn")
      (is (= letter (portrait-cm :a4)) "A4"))))

(deftest the-shim-moves-the-window-not-the-content
  (testing "fit-sheet-to-page! leaves content coordinates alone"
    (binding [draw/*page* :a4]
      (with-open [doc (PDDocument.)]
        (let [pg (PDPage.)]
          (.addPage doc pg)
          (pdf/draw-image-bytes! doc pg @png false 0.45 1.75 2.35 3.15)
          (let [before (vec (filter #(str/ends-with? % " cm") (ops/page-ops pg)))]
            (pdf/fit-sheet-to-page! doc)
            (is (= before (vec (filter #(str/ends-with? % " cm") (ops/page-ops pg))))
                "re-papering must not rewrite the content stream")
            ;; The window moved instead: A4 is taller than Letter, so the box starts below 0.
            (let [mb (.getMediaBox pg)]
              (is (> 0.01 (Math/abs (- 595.28 (.getWidth mb)))) "A4 width")
              (is (neg? (.getLowerLeftY mb)) "the A4 window extends below the Letter origin"))))))))
