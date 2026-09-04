;; Measures whether style 4's licence footer is artwork or drawing, and whether
;; its spell page can be reduced from two to one.
;;
;;   lein run -m clojure.main dev/rebuild_style4_master.clj
;;
;; Writes target/ only -- it does NOT replace the shipped master. See the finding
;; at the bottom of this comment for why.
;;
;; Style 4 ships two spell pages because its licence footer was taken to be baked
;; into the artwork, and a baked footer can be spread by cloning but never
;; removed -- so a :last style needed a plain page to clone and a marked page to
;; finish with.
;;
;; That is not what the pages are. Both reference the SAME 1.27 MB background
;; XObject, and the marked page is the plain page plus one appended BT/ET block of
;; four load-bearing operators:
;;
;;     0.133 0.118 0.122 rg      colour
;;     /C0_0 4 Tf                font, 4pt
;;     22.745 12.437 Td          origin
;;     <001D002E...>Tj           the CID-encoded string
;;
;; The other two the file carries are inert for filled text: `0 i` is flatness
;; tolerance, which applies to path curves, and `/GS2 gs` is the page default
;; (ca/CA 1.0, Normal, no SMask), differing from GS0 only in stroke adjustment.
;;
;; So the footer is drawing, not artwork, and can go on any page. The one thing
;; that does not travel by itself is its font: C0_0 (BCANRW+CartaMarina) is named
;; only in the MARKED page's resources, so dropping that page drops the last
;; reference. This copies it across first, and the reload below proves it survives.
;;
;; THE FINDING, and why no new master is shipped: removing the page reclaims
;; nothing. PDFBox does not garbage-collect on save and prepare_templates has no
;; compaction step, so the orphaned page dictionary and content stream stay in the
;; file -- 2622 objects before, 2622 after, and the file 18 bytes LARGER. The
;; reclaim would have been 1.8 KB of 4.5 MB in any case. Churning a 4.5 MB binary
;; for that is not worth it, so the structural win belongs in code instead: draw
;; the footer per policy, always clone the plain page, and let the second page go
;; unreferenced.

(require '[clojure.java.io :as io])
(import '[org.apache.pdfbox Loader]
        '[org.apache.pdfbox.rendering PDFRenderer]
        '[org.apache.pdfbox.cos COSName]
        '[javax.imageio ImageIO]
        '[java.io FileOutputStream File])

(def src "fillable-char-sheetstyle-4-2-spells.pdf")
(def out "target/style4-one-spell-page.pdf")

(def plain-page-index 2)
(def marked-page-index 3)

(defn- render! [doc idx path]
  (ImageIO/write (.renderImageWithDPI (PDFRenderer. doc) idx 150) "png" (File. path)))

(with-open [doc (Loader/loadPDF (.readAllBytes (.openStream (io/resource src))))]
  (let [pages (vec (.getPages doc))
        plain (nth pages plain-page-index)
        marked (nth pages marked-page-index)
        marked-res (.getResources marked)
        plain-res (.getResources plain)
        footer-font (COSName/getPDFName "C0_0")]
    ;; Keep the footer's font reachable from the page that survives.
    (.put plain-res footer-font (.getFont marked-res footer-font))
    (println "before:" (.getNumberOfPages doc) "pages; plain page fonts now"
             (vec (map str (.getFontNames plain-res))))
    (render! doc marked-page-index "target/style4-attrib-before.png")
    (.removePage doc marked-page-index)
    (with-open [os (FileOutputStream. (File. out))]
      (.save doc os))
    (println "after :" (.getNumberOfPages doc) "pages ->" out)))

;; Reload from disk and confirm the font survived the save, since that is the
;; whole risk of dropping the page that referenced it.
(with-open [doc (Loader/loadPDF (File. out))]
  (let [plain (nth (vec (.getPages doc)) plain-page-index)
        res (.getResources plain)
        f (.getFont res (COSName/getPDFName "C0_0"))]
    (println "reloaded:" (.getNumberOfPages doc) "pages; C0_0 ="
             (if f (.getName f) "MISSING")
             "embedded:" (some? (some-> f .getFontDescriptor)))
    (render! doc plain-page-index "target/style4-attrib-after.png")))

(printf "%n%,d bytes -> %,d bytes%n"
        (.length (File. (str "resources/" src)))
        (.length (File. out)))
