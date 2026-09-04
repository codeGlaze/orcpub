;; Rebuilds style 4's master with ONE spell page instead of two.
;;
;;   lein run -m clojure.main dev/style4_one_spell_page.clj
;;
;; This has been run and its output committed, and its INPUT -- the retired
;; two-spell-page master -- no longer ships in resources/. Re-running it means
;; restoring that file from git history first. It is kept as the record of what
;; the master is and what removing the page took.
;;
;; Writes resources/fillable-char-sheetstyle-4-1-spells.pdf, and
;; target/style4-{before,after}.png for checking the page is unchanged.
;;
;; Style 4 shipped two spell pages because its licence footer was taken to be
;; baked into the artwork, and a baked footer can be spread by cloning but never
;; removed -- so a :last style needed a plain page to clone and a marked page to
;; finish with.
;;
;; That is not what the pages are. Both reference the SAME background XObject, and
;; the marked page is the plain page plus one appended BT/ET block. So the MARKED
;; page is kept and the plain one dropped: the surviving page carries the footer
;; already, which makes the file a normal one-spell-page master like every other
;; style, and the footer lands on every clone.
;;
;; Two things make the removal actually work:
;;
;; - The dropped page's FIELDS have to go too. COSWriter writes what is reachable
;;   from the trailer, so a page removed from the page tree while its widgets are
;;   still listed in the AcroForm stays reachable through /Fields -> widget -> /P
;;   and is written out anyway. Removing the page alone left the object count
;;   unchanged at 2622 and the file 18 bytes LARGER.
;; - The survivor is section 2 and has to become section 1, or every caller
;;   looking for spells-L-R-1 finds nothing.
;;
;; The footer block is also trimmed from six operators to four. `0 i` sets
;; flatness tolerance, which applies to path curves and not to glyph fills, and
;; `/GS2 gs` is the page default -- ca/CA 1.0, Normal, no soft mask -- differing
;; from GS0 only in stroke adjustment, which applies to strokes.

(require '[orcpub.pdf :as pdf]
         '[clojure.java.io :as io]
         '[clojure.string :as st])
(import '[org.apache.pdfbox Loader]
        '[org.apache.pdfbox.pdmodel.common PDStream]
        '[org.apache.pdfbox.cos COSName COSArray COSDictionary COSObject]
        '[org.apache.pdfbox.rendering PDFRenderer]
        '[javax.imageio ImageIO]
        '[java.io FileOutputStream File])

(def src "fillable-char-sheetstyle-4-2-spells.pdf")
(def out "resources/fillable-char-sheetstyle-4-1-spells.pdf")

(def plain-index 2)
(def marked-index 3)

(def fields-on-page #'pdf/fields-on-page)
(def renumber-page-section! #'pdf/renumber-page-section!)

(defn- content-of [page]
  (let [b (java.io.ByteArrayOutputStream.)]
    (with-open [in (.getContents page)] (.transferTo in b))
    (String. (.toByteArray b) "ISO-8859-1")))

(defn- set-content! [doc page ^String s]
  (let [stream (PDStream. doc)]
    (with-open [os (.createOutputStream stream)]
      (.write os (.getBytes s "ISO-8859-1")))
    (.setContents page stream)))

(defn- objects [doc] (count (.getXrefTable (.getDocument doc))))

(defn- drop-struct-tree!
  "Removes the document's structure tree.

   This file is TAGGED, and the tree reaches a page through each element's /Pg,
   so removing the page from the page tree and its fields from the AcroForm is not
   enough on its own: the writer keeps whatever is reachable and the dropped page
   comes along. Measured, the tree holds 1321 of the file's 2622 objects.

   Pruning only the 242 elements that name the dropped page was tried and reached
   27 of them: /K is a dictionary, an array, an integer MCID or a reference by
   turns, and a correct pruner has to handle ParentTree and ClassMap as well.
   Removing the tree is one line and reliable.

   The cost is style 4's accessibility tagging, which is a real loss and is worth
   knowing about: styles 1 and 2 keep theirs, and style 3 already ships without
   any. Restoring it means writing the pruner properly, not undoing this."
  [doc]
  (let [cos (.getCOSObject (.getDocumentCatalog doc))]
    (.removeItem cos COSName/STRUCT_TREE_ROOT)
    (.removeItem cos (COSName/getPDFName "MarkInfo"))))

(with-open [doc (Loader/loadPDF (.readAllBytes (.openStream (io/resource src))))]
  (let [pages (vec (.getPages doc))
        plain (nth pages plain-index)
        marked (nth pages marked-index)
        form (.getAcroForm (.getDocumentCatalog doc))]
    (printf "before: %d pages, %d objects%n" (.getNumberOfPages doc) (objects doc))
    (ImageIO/write (.renderImageWithDPI (PDFRenderer. doc) marked-index 150) "png"
                   (File. "target/style4-before.png"))

    ;; 1. The dropped page's fields, or the page stays reachable through /Fields.
    (let [doomed (set (map #(System/identityHashCode (.getCOSObject %))
                           (fields-on-page doc plain)))
          keep (vec (remove #(contains? doomed (System/identityHashCode (.getCOSObject %)))
                            (.getFields form)))]
      (printf "  dropping %d field(s) that live on the plain page%n"
              (- (count (.getFields form)) (count keep)))
      (.setFields form keep))

    ;; 2. The structure tree, which reaches the page through /Pg.
    (drop-struct-tree! doc)

    ;; 3. The page itself.
    (.removePage doc plain-index)

    ;; 4. The survivor is section 2; every caller looks for section 1.
    (renumber-page-section! doc marked 2 1)

    ;; 5. Trim the footer to the operators that do something.
    (let [before (content-of marked)
          after (-> before
                    (st/replace "0 i \n" "")
                    (st/replace "/GS2 gs\n" ""))]
      (printf "  footer block: %d -> %d bytes%n" (count before) (count after))
      (set-content! doc marked after))

    (with-open [os (FileOutputStream. (File. out))]
      (.save doc os))
    (printf "after : %d pages, %d objects%n" (.getNumberOfPages doc) (objects doc))))

;; Reload from disk: the object count and the render are what actually matter.
(with-open [doc (Loader/loadPDF (File. out))]
  (let [form (.getAcroForm (.getDocumentCatalog doc))
        spell-page (some-> (.getField form "spells-0-1-1") .getWidgets first .getPage)
        idx (.indexOf (.getPages doc) spell-page)]
    (printf "reload: %d pages, %d objects; spells-0-1-1 on page %d%n"
            (.getNumberOfPages doc) (objects doc) (inc idx))
    (ImageIO/write (.renderImageWithDPI (PDFRenderer. doc) idx 150) "png"
                   (File. "target/style4-after.png"))))

(printf "%n%,d -> %,d bytes%n"
        (.length (File. (str "resources/" src))) (.length (File. out)))
