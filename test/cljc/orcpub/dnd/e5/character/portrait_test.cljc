(ns orcpub.dnd.e5.character.portrait-test
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.character.portrait :as portrait]))

;; Kept in sync with orcpub.routes/pdf-response — a generated URL that
;; fails this regex would silently drop out of the PDF export.
(def pdf-url-regex
  #"^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")

(deftest style-for-race-defaults
  (is (= "adventurer" (portrait/style-for-race nil)))
  (is (= "adventurer" (portrait/style-for-race "Kobold")))
  (is (= "adventurer" (portrait/style-for-race "")))
  (is (= "adventurer" (portrait/style-for-race "Human"))))

(deftest style-for-race-mapped
  (is (= "lorelei"  (portrait/style-for-race "Elf")))
  (is (= "lorelei"  (portrait/style-for-race "Half-Elf")))
  (is (= "big-ears" (portrait/style-for-race "Dwarf")))
  (is (= "big-ears" (portrait/style-for-race "Halfling")))
  (is (= "big-ears" (portrait/style-for-race "Gnome"))))

(deftest random-portrait-url-format
  (testing "produced URL passes the server-side PDF regex"
    (is (re-matches pdf-url-regex (portrait/random-portrait-url "abc123"))))
  (testing "same seed → same URL"
    (is (= (portrait/random-portrait-url "s1")
           (portrait/random-portrait-url "s1"))))
  (testing "race steers style"
    (is (not= (portrait/random-portrait-url "s1" "Elf")
              (portrait/random-portrait-url "s1" "Human"))))
  (testing "unknown race falls back to default style"
    (is (= (portrait/random-portrait-url "s1")
           (portrait/random-portrait-url "s1" "Kobold"))))
  (testing "PNG endpoint (PDFBox cannot render SVG)"
    (is (re-find #"/png\?seed=" (portrait/random-portrait-url "s1")))))

(deftest random-seed-shape
  (let [s (portrait/random-seed)]
    (is (string? s))
    (is (pos? (count s)))))
