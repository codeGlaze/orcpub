(ns orcpub.sheet-shim-test
  "Re-papering the AcroForm templates onto another sheet.

   The templates are 612x792 with fixed artwork, so A4 is a framing change, not a
   redraw. This is the first thing in the page-size work that touches the character
   sheet -- the live export path for every user -- so what it must NOT do matters as
   much as what it does."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [orcpub.pdf :as pdf]
            [orcpub.pdf.draw :as draw])
  (:import (org.apache.pdfbox Loader)))

(defn- template []
  (with-open [in (.openStream (io/resource "fillable-char-sheetstyle-1-1-spells.pdf"))]
    (Loader/loadPDF (.readAllBytes in))))

(defn- boxes [doc]
  (vec (for [p (.getPages doc)
             :let [m (.getMediaBox p)]]
         [(Math/round (.getLowerLeftX m)) (Math/round (.getLowerLeftY m))
          (Math/round (.getWidth m)) (Math/round (.getHeight m))])))

(defn- widget-xs [doc]
  (vec (for [p (.getPages doc), a (.getAnnotations p) :let [r (.getRectangle a)] :when r]
         (Math/round (.getLowerLeftX r)))))

(deftest letter-is-a-no-op
  (testing "the templates ARE Letter, so nothing may change -- not the boxes, not one
            widget. This pins the opt-in guard rather than trusting that it holds."
    (with-open [doc (template)]
      (let [before (boxes doc) ws (widget-xs doc)]
        (binding [draw/*page* :letter] (pdf/fit-sheet-to-page! doc))
        (is (= before (boxes doc)) "media boxes untouched")
        (is (= ws (widget-xs doc)) "widgets untouched")
        (is (every? #(= [0 0 612 792] %) (boxes doc)))))))

(deftest a4-re-papers-every-page
  (testing "the window moves over the content: 8pt off each side, 25pt gained each end"
    (with-open [doc (template)]
      (binding [draw/*page* :a4] (pdf/fit-sheet-to-page! doc))
      (is (every? #(= [8 -25 595 842] %) (boxes doc))
          (str "got " (pr-str (distinct (boxes doc)))))))
  (testing "and the WIDGETS are not rewritten -- the box moved, so they move with it.

            Translating 549 rects by hand would reach the same place and get it wrong
            once; this is why no appearance stream is regenerated."
    (with-open [doc (template)]
      (let [ws (widget-xs doc)]
        (binding [draw/*page* :a4] (pdf/fit-sheet-to-page! doc))
        (is (= ws (widget-xs doc)))))))

(defn- widgets-outside-crop
  "Widget rects that fall outside their page's crop window -- fields a reader will not
   show and a printer will not print."
  [doc]
  (for [p (.getPages doc)
        :let [cb (.getCropBox p)]
        a (.getAnnotations p)
        :let [r (.getRectangle a)]
        :when (and r (or (< (.getLowerLeftX r) (.getLowerLeftX cb))
                         (< (.getLowerLeftY r) (.getLowerLeftY cb))
                         (> (.getUpperRightX r) (.getUpperRightX cb))
                         (> (.getUpperRightY r) (.getUpperRightY cb))))]
    r))

(deftest no-supported-sheet-size-cuts-a-field-off
  ;; The real check. The earlier A5 test asserted the crop box held what the shim was
  ;; told to put there, which is not the same as the sheet still being readable: it
  ;; passed while 159 of 335 widgets sat outside the window.
  (doseq [size pdf/sheet-page-sizes]
    (testing (str "every field is inside the printed window on " (name size))
      (with-open [doc (template)]
        (binding [draw/*page* size] (pdf/fit-sheet-to-page! doc))
        (let [out (widgets-outside-crop doc)]
          (is (empty? out) (str (count out) " widget(s) clipped on " (name size))))))))

(deftest a5-leaves-the-sheet-on-letter
  (testing "A5 is 192pt narrower and 197pt shorter than the templates, so re-papering
            would cut off half the fields. The sheet stays Letter; the cards and the
            spellbook after it are drawn to A5."
    (with-open [doc (template)]
      (binding [draw/*page* :a5] (pdf/fit-sheet-to-page! doc))
      (is (every? #(= [0 0 612 792] %) (boxes doc))
          (str "got " (pr-str (distinct (boxes doc)))))
      (is (empty? (widgets-outside-crop doc)) "and nothing is clipped"))))
