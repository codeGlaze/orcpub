;; SRD PDF -> text with page markers, so every later claim can cite a page.
;; Page markers are the point: provenance is what the previous edition analysis lacked.
(require '[clojure.java.io :as io])
(import '[org.apache.pdfbox Loader]
        '[org.apache.pdfbox.text PDFTextStripper]
        '[java.io File])

(defn extract [in out]
  (with-open [doc (Loader/loadPDF (File. ^String in))]
    (let [n (.getNumberOfPages doc)
          stripper (PDFTextStripper.)]
      (with-open [w (io/writer out)]
        (doseq [p (range 1 (inc n))]
          (.setStartPage stripper p)
          (.setEndPage stripper p)
          (.write w (str "\n<<<PAGE " p ">>>\n"))
          (.write w (.getText stripper doc))))
      n)))

(doseq [base *command-line-args*]
  (let [in (str base ".pdf") out (str base ".txt")
        pages (extract in out)]
    (println (format "  %-18s %4d pages -> %9d chars"
                     (last (.split base "/")) pages (.length (File. out))))))
