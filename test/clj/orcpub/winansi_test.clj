(ns orcpub.winansi-test
  "normalize-text must keep every character WinAnsi -- and so Helvetica -- can draw.

   It treated WinAnsi as Unicode U+0000-U+00FF. It is not: WinAnsi is a byte encoding,
   and its 0x80-0x9F slots hold curly quotes, dashes, the ellipsis, the bullet, oe and
   the euro sign. Helvetica draws all of them; normalize-text replaced or dropped them."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.pdf.draw :as draw]
            [orcpub.spellbook :as sb])
  (:import (org.apache.pdfbox.pdmodel.font PDType1Font Standard14Fonts$FontName)))

(def ^:private winansi-extras
  "‘’“”–—…•œŒ€™ŠšŽžŸ")

(deftest helvetica-draws-everything-in-winansi
  (testing "the premise, measured rather than assumed"
    (doseq [c winansi-extras]
      (is (try (.getStringWidth (PDType1Font. Standard14Fonts$FontName/HELVETICA) (str c)) true (catch Exception _ false))
          (format "U+%04X" (int c))))))

(deftest normalize-text-keeps-what-helvetica-draws
  (is (= winansi-extras (draw/normalize-text winansi-extras)))
  (testing "and still refuses what it cannot"
    (is (= "?ukasz" (draw/normalize-text "Łukasz")))
    (is (= "a?b" (draw/normalize-text "a\u0085b")) "C1 controls are not WinAnsi")))

(deftest the-spellbook-keeps-the-srd-punctuation
  ;; 135 such characters across the SRD's spells were printed as straight quotes and
  ;; hyphens, in a font that draws the real ones.
  (is (= "“No,” she said — twice." (#'sb/clean "“No,” she said — twice."))))

(deftest a-font-that-lacks-a-dash-still-gets-a-readable-one
  ;; The old substitutions are now a fallback, used only where the font has no glyph.
  (is (= "a -- b" (draw/normalize-text "a — b" (constantly false)))))
