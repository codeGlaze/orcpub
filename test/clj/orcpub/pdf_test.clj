(ns orcpub.pdf-test
  (:require [clojure.test :refer [deftest is]]
            [orcpub.pdf :as pdf])
  (:import (org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream)))

(deftest fonts-test
  (let [^PDDocument doc (PDDocument.)
        ^PDPage page (PDPage.)
        fonts (pdf/load-fonts doc)
        required-keys [:plain :bold :italic :bold-italic]]
    (is (every? some? (map fonts required-keys)))
    (is (do (.addPage doc page)
            (doseq [font-type required-keys]
              (doto (PDPageContentStream. doc page)
                (.beginText)
                (.setFont (font-type fonts) 14)
                (.newLine)
                (.showText "abcABC012_?%абвАБВ")
                (.endText)))
            true))
    (.close doc)))
