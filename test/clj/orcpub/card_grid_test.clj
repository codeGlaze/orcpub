(ns orcpub.card-grid-test
  "The cut grid must match the cards it cuts, at any card size.

   print-backs and print-spells passed draw-grid literal 2.5 3.5 while threading their
   own box-width/box-height to everything else, so at any other size the lines and the
   cards came from different numbers. Latent: every routes.clj caller passes 2.5x3.5."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.pdf-ops :as ops]
            [orcpub.pdf.draw :as draw]
            [orcpub.pdf.cards :as cards])
  (:import (org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream)))

(def ^:private spell
  {:orcpub.dnd.e5.spells/name "Probe"
   :orcpub.dnd.e5.spells/level 1
   :orcpub.dnd.e5.spells/school "evocation"
   :orcpub.dnd.e5.spells/casting-time "1 action"
   :orcpub.dnd.e5.spells/range "60 feet"
   :orcpub.dnd.e5.spells/components {:verbal true}
   :orcpub.dnd.e5.spells/duration "Instantaneous"
   :orcpub.dnd.e5.spells/description "Short."})

(defn- grid-lines
  "The cut grid's own moveTo/lineTo, isolated by its light-grey stroke. draw-grid sets
   0.88 grey, strokes the lines, then resets to black -- so the ops between those two
   RG operators are the grid and nothing else."
  [page-ops]
  (let [from (first (keep-indexed #(when (= "0.88 0.88 0.88 RG" %2) %1) page-ops))
        rest' (drop (inc from) page-ops)
        window (take-while #(not (re-find #" RG$" %)) rest')]
    (vec (filter #(re-find #" [ml]$" %) window))))

(defn- rendered
  "`draw!` on one page, as drawing operators."
  [draw!]
  (with-open [doc (PDDocument.)]
    (let [pg (PDPage.)]
      (.addPage doc pg)
      (with-open [cs (PDPageContentStream. doc pg)]
        (draw! cs doc (draw/load-fonts doc) (draw/make-image-loader doc)))
      (ops/page-ops pg))))

(defn- spells-grid [bw bh]
  (grid-lines (rendered (fn [cs doc fonts img]
                          (cards/print-spells cs doc fonts img bw bh [spell] 0 false false false)))))

(defn- backs-grid [bw bh]
  ;; print-backs calls (remaining-lines-vec i) -- a vector AS a function -- for every
  ;; card slot, so it needs one entry per slot or it throws. 20 covers 4x3 and 3x3.
  (grid-lines (rendered (fn [cs _doc fonts img]
                          (cards/print-backs cs fonts img bw bh (vec (repeat 20 nil)) 0 nil)))))

(defn- own-grid [bw bh]
  (grid-lines (rendered (fn [cs _doc _fonts _img] (draw/draw-grid cs bw bh)))))

(deftest the-grid-follows-the-card-size
  (testing "2.5x3.5, the size production uses: 3 columns, 0.5in margin"
    ;; 4 vertical lines at x = 36, 216, 396, 576 pt (0.5in + 2.5in steps).
    (is (= (own-grid 2.5 3.5) (spells-grid 2.5 3.5)))
    (is (= (own-grid 2.5 3.5) (backs-grid 2.5 3.5))))
  (testing "2.0x3.0: 4 columns at 144pt from an 18pt margin, NOT 3 at 180 from 36"
    ;; Measured before the fix: print-spells and print-backs both emitted the 2.5x3.5
    ;; grid here -- x = 36, 216, 396, 576 - while placing cards at 18, 162, 306, 450,
    ;; 594. Wrong column count and wrong margin, from the same literal.
    (is (= (own-grid 2.0 3.0) (spells-grid 2.0 3.0)))
    (is (= (own-grid 2.0 3.0) (backs-grid 2.0 3.0))))
  (testing "and the two sizes really do differ, so the above is not vacuous"
    (is (not= (own-grid 2.5 3.5) (own-grid 2.0 3.0)))))
