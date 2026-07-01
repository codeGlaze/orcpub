(ns orcpub.pdf-test
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.pdf :as pdf])
  (:import (org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream)))

(deftest fonts-test
  (testing "Font creation and ability to print latin and cyrillic characters"
    (let [^PDDocument doc (PDDocument.)
          ^PDPage page (PDPage.)
          fonts (pdf/load-fonts doc)
          required-keys [:plain :bold :italic :bold-italic]]
      (is (every? some? (map fonts required-keys)))
      (.addPage doc page)
      ;; Single content stream tests all fonts - more efficient than 4 separate streams
      (is (with-open [cs (PDPageContentStream. doc page)]
            (doseq [font-type required-keys]
              (doto cs
                (.beginText)
                (.setFont (font-type fonts) 14)
                (.newLineAtOffset (float 72) (float 700))
                (.showText (str (name font-type) ": abcABC012_?%абвАБВ"))
                (.endText)))
            true))
      (.close doc))))
