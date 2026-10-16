(ns orcpub.card-export-test
  "Covers what a document pays for when it carries card pages."
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [orcpub.pdf.draw :as draw]
            [orcpub.routes :as routes]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.magic-items :as magic-items])
  (:import (org.apache.pdfbox.pdmodel PDDocument)
           (org.apache.pdfbox.cos COSName COSObject COSDictionary COSArray COSStream)
           (java.io ByteArrayOutputStream)
           (org.apache.pdfbox Loader)))

(def ^:private card-back-logo "public/image/dmv-mark-black.png")

(defn- walk-cos
  "Calls `f` with [parent-key value] for every reachable COS object, once each."
  [doc f]
  (let [seen (java.util.IdentityHashMap.)]
    ((fn go [b via]
       (let [b (if (instance? COSObject b) (.getObject ^COSObject b) b)]
         (when (and b (not (.containsKey seen b)))
           (.put seen b true)
           (f via b)
           (cond
             (instance? COSDictionary b)
             (doseq [k (.keySet ^COSDictionary b)]
               (go (.getItem ^COSDictionary b k) (.getName ^COSName k)))
             (instance? COSArray b) (doseq [i (seq b)] (go i via))))))
     (.getCOSObject (.getDocumentCatalog doc)) nil)))

(defn- embedded-faces
  "The base name of every embedded font program, with subset prefixes stripped.

   PDFBox tags each subset with its own random `ABCDEF+`, so two embeddings of one
   face look like two different fonts until the prefix comes off."
  [doc]
  (let [out (atom [])]
    (walk-cos doc (fn [_ b]
                    (when (instance? COSDictionary b)
                      (doseq [k ["FontFile" "FontFile2" "FontFile3"]]
                        (when (instance? COSStream
                                         (.getDictionaryObject ^COSDictionary b
                                                               (COSName/getPDFName k)))
                          (swap! out conj
                                 (-> (.getNameAsString ^COSDictionary b
                                                       (COSName/getPDFName "FontName"))
                                     (str/replace #"^[A-Z]{6}\+" ""))))))))
    @out))

(defn- image-count [doc]
  (let [n (atom 0)]
    (walk-cos doc (fn [_ b]
                    (when (and (instance? COSStream b)
                               (= "Image" (.getNameAsString ^COSDictionary b COSName/SUBTYPE)))
                      (swap! n inc))))
    @n))

(defn- render-cards
  "A document carrying `kinds` of card page, reloaded from its own bytes."
  [kinds]
  (let [out (ByteArrayOutputStream.)]
    (with-open [doc (PDDocument.)]
      (let [fonts (draw/load-fonts doc)
            img (draw/make-image-loader doc)
            spells-known {:wizard (vec (for [k (take 12 (keys spells/spell-map))]
                                         {:key k :class "Wizard"}))}]
        (when (kinds :spells)
          (routes/add-spell-cards! doc fonts img spells-known {"Wizard" 15} {"Wizard" 7}
                                   nil false card-back-logo false false))
        (when (kinds :items)
          (routes/add-magic-item-cards! doc fonts img (vec (take 12 magic-items/magic-items))
                                        card-back-logo false false)))
      (.save doc out))
    (Loader/loadPDF (.toByteArray out))))

(deftest cards-actually-render
  (testing "both kinds produce pages, so the assertions below are about real output"
    (with-open [doc (render-cards #{:spells :items})]
      (is (pos? (.getNumberOfPages doc)))
      (is (seq (embedded-faces doc)) "card text is set in the embedded faces"))))

(deftest one-set-of-fonts-per-document
  ;; load-fonts embeds its own subset of every face it is asked for, so calling it
  ;; per card KIND put two complete copies of Vollkorn in one file -- 25 KB on a
  ;; sheet that printed both. The fonts belong to the document, not to the caller.
  (testing "a face is embedded once however many kinds of card are printed"
    (with-open [doc (render-cards #{:spells :items})]
      (let [faces (embedded-faces doc)]
        (is (= (count faces) (count (distinct faces)))
            (str "a face was embedded more than once: " (sort faces))))))
  (testing "and each kind alone embeds no more than the four faces exist"
    (doseq [kinds [#{:spells} #{:items}]]
      (with-open [doc (render-cards kinds)]
        (let [faces (embedded-faces doc)]
          (is (= (count faces) (count (distinct faces))) (str kinds " " (sort faces)))
          (is (<= (count faces) 4)))))))

(deftest one-copy-of-the-card-back-logo
  ;; Same cause: a second make-image-loader has its own memo, so it re-embedded the
  ;; 998x998 mark rather than referencing the one already there.
  (testing "the card back mark is embedded once, not once per card kind"
    (with-open [both (render-cards #{:spells :items})
                one (render-cards #{:spells})]
      (is (= (image-count one) (image-count both))
          "adding a second kind of card must not add a second copy of the logo"))))

;; ─── The site stamp on card backs ────────────────────────────────────────────

(defn- glyphs
  "Every glyph on `page-index` as {:ch :x :y}, y measured from the page top."
  [doc page-index]
  (let [out (atom [])
        stripper (proxy [org.apache.pdfbox.text.PDFTextStripper] []
                   (writeString [text positions]
                     (doseq [p positions]
                       (swap! out conj {:ch (.getUnicode p)
                                        :x (.getXDirAdj p)
                                        :y (.getYDirAdj p)}))))]
    (.setStartPage stripper (inc page-index))
    (.setEndPage stripper (inc page-index))
    (.getText stripper doc)
    @out))

(defn- back-page-with-full-overflow
  "Card pages whose every back takes the overflow branch at full height."
  []
  (let [blurb (str/join " " (repeat 400 "The spell surges with overwhelming arcane force."))
        customs (vec (for [i (range 9)]
                       {:key (keyword (str "stress-" i))
                        :name (str "Stress Spell " i)
                        :level 3 :school "evocation" :description blurb
                        :casting-time "1 action" :range "60 feet" :duration "1 minute"
                        :components {:verbal true :somatic true}}))
        spells-known {:wizard (vec (for [c customs] {:key (:key c) :class "Wizard"}))}
        out (ByteArrayOutputStream.)]
    (with-open [doc (PDDocument.)]
      (let [fonts (draw/load-fonts doc)
            img (draw/make-image-loader doc)]
        (routes/add-spell-cards! doc fonts img spells-known {"Wizard" 15} {"Wizard" 7}
                                 customs false card-back-logo false false))
      (.save doc out))
    (Loader/loadPDF (.toByteArray out))))

(deftest site-stamp-on-every-card-back
  (testing "each of the nine card backs carries the site line"
    (with-open [doc (render-cards #{:spells})]
      (let [text (str/join (map :ch (glyphs doc 1)))]
        (is (= 9 (count (re-seq (re-pattern draw/site-stamp) text)))
            "one stamp per card on the back page"))))
  (testing "and the item cards' backs too, which share print-backs"
    (with-open [doc (render-cards #{:items})]
      (let [text (str/join (map :ch (glyphs doc 1)))]
        (is (= 9 (count (re-seq (re-pattern draw/site-stamp) text))))))))

(deftest overflow-text-clears-the-site-stamp
  ;; REGRESSION: draw-lines-to-box fills its box to the last line that fits, and
  ;; `take` given a fractional count rounds UP -- so a strip of 0.22 in computed
  ;; 24.2 lines, laid down 25, and the descenders sat on the stamp. The reserved
  ;; strip has to be big enough that the last line clears it at full overflow.
  (testing "a back filled to overflow does not print over its stamp"
    (with-open [doc (back-page-with-full-overflow)]
      (let [all (glyphs doc 1)
            ;; Group by card. Three columns of 2.5in and three rows of 3.5in at 72
            ;; units to the inch, offset by the margins print-backs centres the
            ;; grid with: (8.5 - 7.5)/2 across and (11 - 10.5)/2 down.
            box-of (fn [{:keys [x y]}]
                     [(int (/ (- x (* 72 0.5)) (* 72 2.5)))
                      (int (/ (- y (* 72 0.25)) (* 72 3.5)))])
            stamp-chars (set draw/site-stamp)]
        (doseq [[box gs] (group-by box-of all)
                ;; The page header sits above the grid and is not a card.
                :when (<= 0 (second box) 2)]
          (let [;; The stamp is the lowest run on the card; body text is what sits
                ;; above it. Split on the stamp's own baseline.
                bottom (apply max (map :y gs))
                stamp (filter #(> (:y %) (- bottom 2)) gs)
                body (remove #(> (:y %) (- bottom 2)) gs)]
            (is (= draw/site-stamp (str/join (map :ch (sort-by :x stamp))))
                (str "box " box " ends with the stamp"))
            (when (seq body)
              (is (> (- bottom (apply max (map :y body))) 6.0)
                  (str "box " box ": body text clears the stamp by at least 6pt")))))))))


;; ── Text the embedded font cannot draw ───────────────────────────────────────
;; Homebrew names are user text. The embedded Vollkorn covers Latin-1 and the
;; punctuation the SRD uses, but not every script, and PDFBox throws on a glyph it
;; has not got. add-magic-item-cards! wraps EVERY page in one try, so one bad name on
;; the first sheet costs the export every sheet after it.

(def ^:private unrenderable-name
  "Katakana: verified absent from Vollkorn in all three weights."
  "Blade of \u30c4\u30c4\u30c4")

(def ^:private control-char-name
  "A BEL and a SOH, the shape of a copy-paste accident."
  "Wand\u0007of\u0001Fire")

(defn- renamed
  "The nth real magic item with `nm` for its name, so only the name differs."
  [n nm]
  (assoc (nth magic-items/magic-items n) :name nm))

(defn- render-items
  "Item card pages for `items`, as [page-count extracted-text]."
  [items]
  (let [out (ByteArrayOutputStream.)]
    (with-open [doc (PDDocument.)]
      (routes/add-magic-item-cards! doc (draw/load-fonts doc) (draw/make-image-loader doc)
                                    (vec items) card-back-logo false false)
      (.save doc out))
    (with-open [doc (Loader/loadPDF (.toByteArray out))]
      [(.getNumberOfPages doc)
       (.getText (org.apache.pdfbox.text.PDFTextStripper.) doc)])))

(def ^:private last-page-item
  "Named on the SECOND sheet, so it is only drawn if the first sheet finished."
  "Wings of Flying")

(defn- two-sheets-with
  "Ten items: `bad` first, nine real ones after, the last named so it lands on sheet two."
  [bad]
  (concat [(renamed 0 bad)]
          (map #(renamed % (str "Filler Item " %)) (range 1 9))
          [(renamed 9 last-page-item)]))

(deftest ten-items-make-two-sheets-and-their-backs
  (testing "the baseline shape this test depends on, with no bad names"
    (let [[pages text] (render-items (two-sheets-with "Ordinary Name"))]
      (is (= 4 pages) "two fronts and two backs")
      (is (str/includes? text last-page-item)))))

(deftest a-name-the-font-cannot-draw-does-not-cost-the-later-sheets
  (let [[pages text] (render-items (two-sheets-with unrenderable-name))]
    (is (= 4 pages)
        "a glyph the font has not got cost the export whole sheets")
    (is (str/includes? text last-page-item)
        (str last-page-item " is on sheet two and never got drawn"))))

(deftest control-characters-in-a-name-do-not-cost-the-later-sheets
  (let [[pages text] (render-items (two-sheets-with control-char-name))]
    (is (= 4 pages) "control characters in a name cost the export whole sheets")
    (is (str/includes? text last-page-item)
        (str last-page-item " is on sheet two and never got drawn"))))

(deftest the-punctuation-the-srd-uses-is-left-alone
  (testing "Vollkorn draws these, so nothing should replace them"
    (let [nm "Rod \u2014 of \u201cLordly\u201d Might\u2019s \u2022 Grip"
          [pages text] (render-items [(renamed 0 nm)])]
      (is (pos? pages))
      (doseq [[cp label] [[0x2014 "em dash"] [0x201C "curly open"] [0x2019 "curly apos"]
                          [0x2022 "bullet"]]]
        (is (str/includes? text (str (char cp)))
            (str label " was replaced; Vollkorn has a glyph for it"))))))
