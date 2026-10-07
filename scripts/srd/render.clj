;; Render a PDF page to PNG with PDFBox. Independent of the TEXT layer: this is what
;; the page actually looks like, which is the only check that is not circular when the
;; text extraction itself is what is under suspicion.
(import '[org.apache.pdfbox Loader] '[org.apache.pdfbox.rendering PDFRenderer]
        '[javax.imageio ImageIO] '[java.io File])
(let [[pdf pg out] *command-line-args*
      doc (Loader/loadPDF (File. ^String pdf))
      img (.renderImageWithDPI (PDFRenderer. doc) (dec (Integer/parseInt pg)) 110.0)]
  (ImageIO/write img "png" (File. ^String out))
  (.close doc)
  (println (format "  rendered page %s -> %s (%dx%d)" pg out (.getWidth img) (.getHeight img))))
