;; Finds where the site line can sit on each sheet style without printing over
;; the artwork.
;;
;;   lein run -m clojure.main dev/scan_site_line.clj
;;
;; Reports, per style and per page, the x range in which a stamp-sized box is
;; free of ink at each candidate height. The intersection down a style's pages is
;; that style's :site-line in pdf/sheet-masters.
;;
;; This exists because the positions cannot be read out of the page's text.
;; PDFTextStripper reports glyphs and nothing else, so a first pass that placed
;; every style at one spot -- chosen from where each page's lowest TEXT sat --
;; put the line straight through the corner flourish on the last page of styles
;; 1 and 2 and through the frame on style 4. Ink is what matters, so the page is
;; rendered and the pixels are counted.
;;
;; Pages a style prints its own footer on are skipped, the same as at export.

(require '[orcpub.pdf :as pdf]
         '[clojure.java.io :as io])
(import '[org.apache.pdfbox Loader]
        '[org.apache.pdfbox.rendering PDFRenderer]
        '[java.awt.image BufferedImage]
        '[java.io ByteArrayOutputStream])

(def dpi 150.0)
(def stamp-width (pdf/string-width pdf/site-stamp pdf/HELVETICA 6))
(def stamp-height
  "6pt of cap height plus room for a descender, in inches."
  (/ 8.0 72))

(defn clear?
  "Whether every pixel is near-white in the stamp-sized box whose bottom-left is
   `xin`,`yin` inches from the page's bottom-left."
  [^BufferedImage img xin yin]
  (let [h (.getHeight img)
        x0 (int (* xin dpi))
        x1 (int (* (+ xin stamp-width) dpi))
        ;; Inches up from the page foot, into rows down from the image top.
        y-top (int (- h (* (+ yin stamp-height) dpi)))
        y-bottom (int (- h (* yin dpi)))]
    (and (>= x0 0) (< x1 (.getWidth img)) (>= y-top 0) (<= y-bottom h)
         (every? (fn [y]
                   (every? (fn [x]
                             (let [p (.getRGB img x y)]
                               (and (> (bit-and (bit-shift-right p 16) 0xff) 244)
                                    (> (bit-and (bit-shift-right p 8) 0xff) 244)
                                    (> (bit-and p 0xff) 244))))
                           (range x0 x1)))
                 (range y-top y-bottom)))))

(defn- grown
  "A style's master grown to `casters` sections, reloaded from its own bytes."
  [style casters]
  (let [{:keys [file marks]} (get pdf/sheet-masters style)
        out (ByteArrayOutputStream.)]
    (with-open [doc (Loader/loadPDF (.readAllBytes (.openStream (io/resource file))))]
      (pdf/grow-spell-sections! doc casters marks)
      (.save doc out))
    (Loader/loadPDF (.toByteArray out))))

(doseq [style [1 2 3 4]]
  (let [{:keys [site-line prints-site-line?]} (get pdf/sheet-masters style)]
    (printf "%nSTYLE %d   shipping :site-line %s%n" style (pr-str site-line))
    (with-open [doc (grown style 2)]
      (doseq [i (range (.getNumberOfPages doc))]
        (if (and prints-site-line? (#'pdf/page-prints-site-line? doc i))
          (printf "  p%d  prints its own footer, skipped at export%n" (inc i))
          (let [img (.renderImageWithDPI (PDFRenderer. doc) i dpi)]
            (doseq [y [0.06 0.08 0.10 0.12]]
              (let [xs (filter #(clear? img % y)
                               (range 0.15 (- 8.5 stamp-width 0.15) 0.05))]
                (printf "  p%d y=%.2f  %s%n" (inc i) y
                        (if (seq xs)
                          (format "clear x %.2f .. %.2f" (apply min xs) (apply max xs))
                          "no clear run"))))))))))
