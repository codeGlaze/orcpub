;; Renders an icon as vector art from its SVG beside the 32px raster of the same
;; icon, at the sizes a card draws them, and rasterises the sheet for inspection.
;;
;;   lein run -m clojure.main dev/svg_icon_proof.clj
;;   ICONS=magic-swirl,crystal-ball lein run -m clojure.main dev/svg_icon_proof.clj
;;
;; This is how to check a path conversion: put the two side by side and look. A
;; quarter inch on a 600 DPI printer is about 150 device pixels, which a 32 pixel
;; source cannot fill and a path does not have to.
;;
;; The rasters are no longer what the cards draw. The comparison is kept because
;; it is the check to repeat whenever an icon or the parser changes.

(require '[orcpub.pdf :as pdf] '[clojure.string :as str] '[clojure.java.io :as io])
(import '[org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream]
        '[org.apache.pdfbox.pdmodel.font PDType1Font Standard14Fonts$FontName]
        '[org.apache.pdfbox.rendering PDFRenderer]
        '[javax.imageio ImageIO] '[java.io File])

(def icons (str/split (or (System/getenv "ICONS") "magic-swirl") #","))
(def sizes "Inches. 0.25 is what a spell card actually draws." [0.15 0.25 0.4 0.7 1.1])

(let [doc (PDDocument.) page (PDPage.) img (pdf/make-image-loader doc)
      helv (PDType1Font. Standard14Fonts$FontName/HELVETICA)
      label (fn [cs t x y] (pdf/draw-text cs t helv 7 x y))]
  (.addPage doc page)
  (with-open [cs (PDPageContentStream. doc page)]
    (doseq [[row icon] (map-indexed vector icons)]
      (let [ops (pdf/load-svg-icon icon)
            png (when (io/resource (str "public/image/" icon ".png"))
                  (img (str "public/image/" icon ".png")))
            top (+ 0.9 (* row 2.6))]
        (label cs (str icon "  (" (if ops (str (count ops) " path ops") "NO SVG") ")")
               0.7 (- 11 (- top 0.25)))
        (label cs "vector from SVG" 0.7 (- 11 (+ top 0.05)))
        (label cs (if png "32px PNG" "(never rasterised)") 0.7 (- 11 (+ top 1.35)))
        (loop [[s & more] sizes x 2.1]
          (when s
            ;; Both rows share a baseline so the eye compares like with like.
            (when ops (pdf/draw-svg-path! cs ops x (+ top 0.15) s 512))
            ;; Only some of these were ever rasterised; the row is skipped rather
            ;; than faked for the rest.
            (when png (pdf/draw-imagex cs png x (+ top 1.45) s s))
            (label cs (format "%.2fin" (double s)) x (- 11 (+ top 2.55)))
            (recur more (+ x s 0.28)))))))
  (.save doc (File. "target/svg-icon-proof.pdf"))
  (ImageIO/write (.renderImageWithDPI (PDFRenderer. doc) 0 200) "png"
                 (File. "target/svg-icon-proof.png"))
  (.close doc))

(doseq [i icons]
  (let [ops (pdf/load-svg-icon i)]
    (printf "%-20s %s%n" i
            (if ops
              (format "%d ops  %s" (count ops)
                      (str/join " " (sort (distinct (map (comp name first) ops)))))
              "no SVG in resources"))))
(println "\ntarget/svg-icon-proof.png")
