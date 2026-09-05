;; Measures each style's printed level numeral: where it sits, how big it is, and
;; what colour surrounds it.
;;
;;   lein run -m clojure.main dev/scan_numerals.clj
;;
;; relabel-numeral! covers that numeral before drawing a new one, with the boxes
;; measured here (pdf/numeral-boxes). The first relabeller cut a patch to a
;; hexagon traced off style 1, and the styles do not merely offset that shape --
;; style 3 rings its numerals, style 4 uses a small hexagon -- so a packed page
;; on 2, 3 or 4 printed both numbers, the old beside the new.
;;
;; Rather than trace four shapes, this measures what actually has to be covered:
;; the digit's own box, and the flat colour immediately around it. A rectangle of
;; that colour over that box hides the digit whatever badge it sits in.

(require '[orcpub.pdf :as pdf]
         '[clojure.java.io :as io])
(import '[org.apache.pdfbox Loader]
        '[org.apache.pdfbox.rendering PDFRenderer]
        '[org.apache.pdfbox.text PDFTextStripper]
        '[java.awt.image BufferedImage])

(def dpi 300.0)

(defn- glyphs
  "Every glyph on `index`, with the box PDFBox reports for it."
  [doc index]
  (let [out (atom [])
        stripper (proxy [PDFTextStripper] []
                   (writeString [text positions]
                     (doseq [p positions]
                       (swap! out conj {:ch (.getUnicode p)
                                        :x (.getXDirAdj p)
                                        :y (.getYDirAdj p)
                                        :w (.getWidthDirAdj p)
                                        :h (.getHeightDir p)}))))]
    (.setStartPage stripper (inc index))
    (.setEndPage stripper (inc index))
    (.getText stripper doc)
    @out))

(defn- pixel
  "The colour at page point (x, y-from-bottom) as [r g b], 0-255."
  [^BufferedImage img page-height x y]
  (let [px (int (* x (/ dpi 72.0)))
        py (int (* (- page-height y) (/ dpi 72.0)))]
    (when (and (< -1 px (.getWidth img)) (< -1 py (.getHeight img)))
      (let [p (.getRGB img px py)]
        [(bit-and (bit-shift-right p 16) 0xff)
         (bit-and (bit-shift-right p 8) 0xff)
         (bit-and p 0xff)]))))

(defn- surround
  "Colours sampled just outside the digit's box, where the badge shows through."
  [img page-height {:keys [x y w h]} page-h]
  (let [bottom (- page-h y)]
    (remove nil?
            (for [[dx dy] [[-3.0 2.0] [(+ w 3.0) 2.0]
                           [(/ w 2.0) (+ h 2.5)] [(/ w 2.0) -2.5]]]
              (pixel img page-height (+ x dx) (+ bottom dy))))))

(doseq [style [1 2 3 4]]
  (let [{:keys [file]} (get pdf/sheet-masters style)]
    (with-open [doc (Loader/loadPDF (.readAllBytes (.openStream (io/resource file))))]
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            pages (vec (.getPages doc))
            spell-page (some-> (.getField form "spells-0-1-1") .getWidgets first .getPage)
            index (.indexOf pages spell-page)
            page-h (.getHeight (.getMediaBox spell-page))
            img (.renderImageWithDPI (PDFRenderer. doc) index dpi)
            gs (glyphs doc index)]
        (printf "%nSTYLE %d%n" style)
        (doseq [level (range 1 10)
                :let [r (some-> (.getField form (str "spell-slots-" level "-1"))
                                .getWidgets first .getRectangle)]
                :when r]
          (let [want (str level)
                near (filter #(and (= want (:ch %))
                                   (< (Math/abs (- (- page-h (:y %)) (+ (.getLowerLeftY r) 8))) 22)
                                   (< -40 (- (:x %) (.getLowerLeftX r)) 2))
                             gs)]
            (when-let [g (first near)]
              (printf "  L%d  digit box %.1f x %.1f at dx=%.1f dy=%.1f   around: %s%n"
                      level (:w g) (:h g)
                      (- (:x g) (.getLowerLeftX r))
                      (- (- page-h (:y g)) (.getLowerLeftY r))
                      (pr-str (surround img page-h g page-h))))))))))
