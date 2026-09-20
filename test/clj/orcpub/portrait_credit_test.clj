(ns orcpub.portrait-credit-test
  "Attribution for composed portraits, on every surface that carries the art
   off the site: the printed sheet, the share card, and the summary.

   The point of the feature is that a contributing artist's name travels with
   their work, so the failure mode that matters is silent omission -- and the
   failure mode that must never happen is attribution taking down an export."
  (:require [clojure.test :refer [deftest testing is]]
            [datomic.api :as d]
            [datomock.core :as dm]
            [orcpub.routes :as routes]
            [orcpub.db.schema :as schema]
            [orcpub.entity.strict :as se]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.portrait-render :as pr]
            [orcpub.pdf :as pdf])
  (:import [java.io ByteArrayInputStream]
           [javax.imageio ImageIO]
           [java.util UUID]
           [org.apache.pdfbox.pdmodel PDDocument PDPage]
           [org.apache.pdfbox.pdmodel.common PDRectangle]
           [org.apache.pdfbox.text PDFTextStripper]))

;; ---------- the line itself ----------

(defn- portrait-with [layer-keys]
  {:layers (into {}
                 (keep (fn [k]
                         (when-let [a (first (pa/assets-for-layer k))]
                           [k {:artist/id (pa/artist-for-asset k (:asset/id a))
                               :asset/id  (:asset/id a)}])))
                 layer-keys)})

(deftest formats-one-credit-for-the-artists-named
  (is (= "Art: A Person" (pa/format-credit ["A Person"])))
  (is (= "Art: A Person, B Person" (pa/format-credit ["A Person" "B Person"])))
  (testing "one artist across many layers is one credit, not many"
    (is (= 1 (count (re-seq #"Art:" (pa/format-credit ["A Person"])))))))

(deftest an-unnamed-artist-gets-no-invented-byline
  (testing "the illustrator has not yet said how they want to be credited, so
            :artist/name is nil and every surface shows nothing rather than a
            name nobody chose"
    (is (nil? (pa/credit-line (portrait-with [:head :shirt :eyes])))))
  (is (nil? (pa/format-credit nil)))
  (is (nil? (pa/format-credit []))))

(deftest artists-on-canvas-are-found-and-deduped
  (testing "the lookup still works; only the name is missing"
    (let [artists (pa/artists-for-layers (:layers (portrait-with [:head :shirt :eyes])))]
      (is (= 1 (count artists)) "one artist, once, across three layers")
      (is (= :house-pack (:artist/id (first artists)))))))

(deftest nil-when-there-is-nothing-to-credit
  (is (nil? (pa/credit-line nil)))
  (is (nil? (pa/credit-line {})))
  (is (nil? (pa/credit-line {:layers {}})))
  (is (nil? (pa/credit-line {:layers {:head {:asset/id :no-such-asset}}}))
      "a selection naming an unknown asset credits nobody"))

;; ---------- keeping it printable ----------

(deftest strips-what-the-pdf-font-cannot-encode
  (testing "PDFBox standard-14 is WinAnsi; an unencodable glyph throws on
            showText and would take the whole sheet down"
    (is (= "Art: A Person" (routes/pdf-safe-text "Art: A Person")))
    (is (= "Art: A Person" (routes/pdf-safe-text "Art: A ☃ Person"))
        "snowman dropped")
    (is (= "Art: Renee Dore" (routes/pdf-safe-text "Art: Renee Dore")))
    (is (= "Art: Renée Doré" (routes/pdf-safe-text "Art: Renée Doré"))
        "latin-1 accents are inside WinAnsi and must survive")))

(deftest keeps-the-credit-line-short
  (let [long-line (str "Art: " (apply str (repeat 200 "x")))
        out (routes/pdf-safe-text long-line)]
    (is (<= (count out) 78) (str "was " (count out)))
    (is (re-find #"\.\.\.$" out) "truncation is visible")))

(deftest nil-rather-than-an-empty-line
  (is (nil? (routes/pdf-safe-text nil)))
  (is (nil? (routes/pdf-safe-text "")))
  (is (nil? (routes/pdf-safe-text "   ")))
  (is (nil? (routes/pdf-safe-text "☃☄")) "nothing encodable left"))

;; ---------- the picture carries its own credit ----------
;;
;; The sheet and the page each show a credit beside the portrait, but the
;; composed image is what gets passed around, and it arrives detached from
;; both. These cover the caption that is burned into the pixels instead.

(deftest contain-rect-fits-without-stretching
  (testing "8:11 art in a 4:5 frame is height-bound, centred, aspect kept"
    (let [[x y w h] (pr/contain-rect 192 264 600 750)]
      (is (= 750 h) "fills the short axis")
      (is (= 545 w) (str "expected 545 wide, got " w))
      (is (= 28 x) "centred horizontally -- (600-545)/2 rounds up, in both runtimes")
      (is (= 0 y))
      (is (< (Math/abs (- (/ (double w) h) (/ 192.0 264))) 0.005)
          "source aspect preserved"))))

(deftest contain-rect-handles-the-other-orientation
  (let [[x y w h] (pr/contain-rect 800 400 600 750)]
    (is (= 600 w) "width-bound this time")
    (is (= 300 h))
    (is (= 0 x))
    (is (= 225 y) "centred vertically")))

(deftest contain-rect-survives-a-degenerate-asset
  (is (= [0 0 600 750] (pr/contain-rect 0 0 600 750)))
  (is (= [0 0 600 750] (pr/contain-rect 10 0 600 750))))

(deftest rendered-portrait-is-not-stretched
  (testing "both rasterizers used to fill the frame, so a shared picture came
            out a different shape from the one the drawer showed"
    (let [png (pr/render-png (portrait-with [:head]) 600 750)
          img (ImageIO/read (ByteArrayInputStream. png))
          w (.getWidth img) h (.getHeight img)
          xs (for [x (range w) y (range h)
                   :when (pos? (bit-and (unsigned-bit-shift-right (.getRGB img x y) 24) 0xff))]
               x)]
      (is (seq xs) "something was drawn")
      (is (<= (- (apply max xs) (apply min xs)) 560)
          "the art keeps side gutters instead of being widened to the frame"))))

(defn- blank-doc [pages]
  (let [doc (PDDocument.)]
    (dotimes [_ pages] (.addPage doc (PDPage. PDRectangle/LETTER)))
    doc))

;; ---------- fitting the composed portrait for the PDF ----------
;;
;; The sheet embeds the picture the browser baked. Generated artwork is fitted
;; rather than refused (see docs/kb on agents/develop), and HOW it is fitted
;; matters: the general path re-encodes as JPEG, which cannot carry alpha.

(deftest fitted-artwork-keeps-its-transparency
  (testing "pdf/fit-for-sheet scales into TYPE_INT_RGB and writes JPEG, which
            is right for a photograph and turns a composed portrait's
            transparent ground black -- a black box around the character on
            every printed sheet"
    (let [png (pr/render-png (portrait-with [:head :shirt]) 600 750)
          {:keys [data jpg?]} (pdf/decode-artwork-bytes
                               (.encodeToString (java.util.Base64/getEncoder) png))
          img (ImageIO/read (ByteArrayInputStream. data))]
      (is (false? jpg?) "artwork is never handed over as JPEG")
      (is (.hasAlpha (.getColorModel img)) "the alpha channel survives")
      (is (zero? (bit-and (unsigned-bit-shift-right (.getRGB img 2 2) 24) 0xff))
          "the corner is still transparent, not filled"))))

(deftest fitted-artwork-keeps-print-resolution
  (testing "the 128k ceiling is what a user may UPLOAD; holding generated art
            to it dropped a 600x750 portrait to 367x459, 156 dpi in the 2.35in
            box, under the 200 dpi the assets are built for"
    (let [png (pr/render-png (portrait-with [:head :shirt]) 600 750)
          {:keys [data]} (pdf/decode-artwork-bytes
                          (.encodeToString (java.util.Base64/getEncoder) png))
          img (ImageIO/read (ByteArrayInputStream. data))]
      (is (>= (.getHeight img) 630)
          (str "only " (int (/ (.getHeight img) 3.15)) " dpi in the printed box")))))

(deftest heavy-artwork-is-fitted-not-refused
  (testing "the weight ceiling is a backstop, not a wall: something over it
            comes back smaller rather than nil"
    (let [png (pr/render-png (portrait-with [:head :shirt :eyes :bangs]) 1400 1750)
          {:keys [data jpg?]} (pdf/decode-artwork-bytes
                               (.encodeToString (java.util.Base64/getEncoder) png))]
      (is (some? data) "fitted, not refused")
      (is (false? jpg?) "and still not a JPEG")
      (is (<= (alength data) (alength png)) "no heavier than it arrived"))))

(deftest a-decompression-bomb-is-refused-outright
  (testing "the pixel budget is checked from the header before any pixels are
            decoded, and unlike the weight ceiling it is a wall -- 2400x3000
            is 7.2M pixels against a 4M cap"
    (let [png (pr/render-png (portrait-with [:head]) 2400 3000)]
      (is (nil? (pdf/decode-artwork-bytes
                 (.encodeToString (java.util.Base64/getEncoder) png)))
          "refused, not scaled down"))))

;; ---------- document metadata ----------

(deftest stamps-honest-document-info
  (testing "the templates are third-party InDesign files, so an untouched
            export claims Adobe made it and names no one"
    (with-open [doc (blank-doc 1)]
      (let [info (.getDocumentInformation doc)]
        (.setCreator info "Adobe InDesign CS6 (Macintosh)")
        (.setProducer info "Master PDF Editor"))
      (routes/stamp-document-info! doc {:character-name "Sharey"
                                        :credit "Art: A Person"})
      (let [info (.getDocumentInformation doc)]
        (is (= "Sharey" (.getTitle info)))
        (is (not= "Adobe InDesign CS6 (Macintosh)" (.getCreator info))
            "the template's claim to authorship is overwritten")
        (is (not= "Master PDF Editor" (.getProducer info)))
        (is (= "Art: A Person" (.getSubject info))
            "the credit lives in the metadata as well as on the page")
        (is (= "Art: A Person" (.getKeywords info)))))))

(deftest metadata-carries-no-credit-when-there-is-none
  (with-open [doc (blank-doc 1)]
    (routes/stamp-document-info! doc {:character-name "Sharey" :credit nil})
    (let [info (.getDocumentInformation doc)]
      (is (= "Sharey" (.getTitle info)))
      (is (nil? (.getSubject info)) "no empty credit in the metadata"))))

(deftest stamping-never-fails-an-export
  (is (nil? (routes/stamp-document-info! nil {:character-name "x"}))))

(deftest untitled-sheets-still-get-a-title
  (with-open [doc (blank-doc 1)]
    (routes/stamp-document-info! doc {:character-name "   " :credit nil})
    (is (seq (.getTitle (.getDocumentInformation doc)))
        "a blank name falls back rather than titling the file empty")))

;; ---------- the share card ----------

(defmacro with-conn [conn-binding & body]
  `(let [uri# (str "datomic:mem:orcpub-credit-test-" (UUID/randomUUID))
         ~conn-binding (do (d/create-database uri#) (d/connect uri#))]
     (try ~@body (finally (d/delete-database uri#)))))

(defn- setup [conn]
  (let [c (dm/fork-conn conn)]
    @(d/transact c schema/all-schemas)
    @(d/transact c [{:orcpub.user/username "testy" :orcpub.user/email "t@t.com"}])
    c))

(defn- save! [conn character]
  (:body (routes/do-save-character (d/db conn) conn character {:user "testy"})))

(defn- character-with [values]
  {::se/selections []
   ::se/summary {::char5e/character-name "Sharey" ::char5e/race-name "Halfling"}
   ::se/values values})

(defn- description-of [c id]
  (let [html (:body (routes/character-page
                     {:db (d/db c) :conn c :headers {"host" "example.test"}
                      :uri "/" :path-params {:id id}}))
        tag (re-find #"<meta[^>]*og:description[^>]*>" html)]
    (second (some->> tag (re-find #"content=\"([^\"]*)\"")))))

(deftest share-card-carries-whatever-credit-there-is
  (testing "the card appends the credit-line verbatim, so it says exactly what
            the registry says -- nothing today, the artist's name once they
            have chosen one, and never a half-built 'Art:' with no names"
    (with-conn conn
      (let [c (setup conn)
            p (portrait-with [:head :shirt])
            id (:db/id (save! c (character-with {::char5e/portrait (pr-str p)})))
            desc (str (description-of c id))]
        (is (seq desc) "the card still has a description")
        (is (= (boolean (pa/credit-line p))
               (boolean (re-find #"Art:" desc)))
            (str "card and registry disagree about the credit; got " desc))
        (is (not (re-find #"Art:\s*(·|$)" desc))
            "never an empty credit")))))

(deftest share-card-stays-clean-without-a-portrait
  (with-conn conn
    (let [c (setup conn)
          id (:db/id (save! c (character-with
                               {::char5e/image-url "https://example.com/a.png"})))]
      (is (not (re-find #"Art:" (or (description-of c id) "")))
          "a pasted URL has no artist we can name"))))
