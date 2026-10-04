(ns scan-cantrips-word
  "Measures the band the word CANTRIPS is printed in, in the bar of each style's
   cantrips box, as offsets from the bar's middle in points.

   draw-column-heading! covers that word before writing a class name over it, and
   the styles do not agree on where it sits: on style 3 it rides about four points
   higher than on style 4, so a single patch band either left style 3's word
   showing or painted over style 4's rules. Run this after changing a template's
   cantrips box and copy the numbers into pdf/cantrips-word-patch."
  (:require [clojure.java.io :as io]
            [orcpub.pdf :as pdf])
  (:import [org.apache.pdfbox Loader]
           [org.apache.pdfbox.rendering PDFRenderer]))

(def dpi 300.0)

(defn- word-rows
  "Rows of `img` inside the given PDF-point rect that hold lettering, as page y.

   A row is lettering when between 5% and 60% of its columns carry ink. The bar's
   rules run the width of the strip and the open paper between them carries none,
   so both fall outside that band and only the word is left."
  [img page-height x0 x1 y0 y1]
  (let [s (/ dpi 72.0)
        px #(int (Math/round (* s (double %))))
        top (fn [y] (px (- page-height y)))
        cols (vec (range (px x0) (px x1)))]
    (for [row (range (top y1) (top y0))
          :let [n (count (filter (fn [col]
                                   (let [rgb (.getRGB img col row)]
                                     (< (bit-and (bit-shift-right rgb 8) 0xff) 170)))
                                 cols))
                frac (/ (double n) (count cols))]
          :when (< 0.05 frac 0.6)]
      (- page-height (/ row s)))))

(defn -main [& _]
  (doseq [style [1 2 3 4]]
    (let [file (:file (get pdf/sheet-masters style))]
      (with-open [doc (Loader/loadPDF (.readAllBytes (.openStream (io/resource file))))]
        (let [[hx hy hw hh] (@#'pdf/cantrips-hexagon-box doc 1)
              {[_ _] :narrow [wx ww] :wide} (@#'pdf/bar-compartments doc 0 1)
              middle (+ hy (/ hh 2.0))
              widget (-> (.getAcroForm (.getDocumentCatalog doc))
                         (.getField "spells-0-1-1") .getWidgets first)
              page (.getPage widget)
              idx (.indexOf (vec (.getPages doc)) page)
              img (.renderImageWithDPI (PDFRenderer. doc) idx dpi)
              ph (.getHeight (.getMediaBox page))
              rows (word-rows img ph (+ wx (* 0.35 ww)) (+ wx (* 0.65 ww))
                              (- middle 16.0) (+ middle 16.0))]
          (printf "style %d  middle %.1f  bar %.1f..%.1f  ink %.1f..%.1f  -> dy %.1f h %.1f%n"
                  style middle (- middle 16.0) (+ middle 16.0)
                  (double (apply min rows)) (double (apply max rows))
                  (- (apply min rows) middle) (- (apply max rows) (apply min rows))))))))
