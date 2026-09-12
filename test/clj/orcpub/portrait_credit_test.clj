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
            [orcpub.dnd.e5.portrait-assets :as pa])
  (:import [java.util UUID]
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

(deftest drawing-a-credit-never-fails-an-export
  (testing "a broken document must cost the credit, not the sheet"
    (is (nil? (routes/draw-portrait-credit! nil 1 "Art: Someone"))
        "no exception escapes")
    (is (nil? (routes/draw-portrait-credit! nil 1 nil)))))

;; ---------- the credit actually lands on the page ----------

(defn- blank-doc [pages]
  (let [doc (PDDocument.)]
    (dotimes [_ pages] (.addPage doc (PDPage. PDRectangle/LETTER)))
    doc))

(defn- text-of [doc page-index]
  (let [st (doto (PDFTextStripper.)
             (.setStartPage (inc page-index))
             (.setEndPage (inc page-index)))]
    (.getText st doc)))

(deftest credit-reaches-the-printed-sheet
  (testing "styles 1-3 draw the portrait on page 1"
    (doseq [style [1 2 3]]
      (with-open [doc (blank-doc 3)]
        (routes/draw-portrait-credit! doc style "Art: A Person")
        (is (re-find #"Art: A Person" (text-of doc 1))
            (str "style " style " credit missing from page 1"))
        (is (not (re-find #"Art:" (text-of doc 0)))
            (str "style " style " credit must not land on page 0")))))
  (testing "style 4 moves the portrait, and the credit follows it to page 0"
    (with-open [doc (blank-doc 3)]
      (routes/draw-portrait-credit! doc 4 "Art: A Person")
      (is (re-find #"Art: A Person" (text-of doc 0)))
      (is (not (re-find #"Art:" (text-of doc 1)))))))

(deftest credit-is-centred-on-the-portrait-box
  (testing "centred so it clears the template's own caption and border art;
            left-aligned it landed on the frame ornament in style 4"
    (with-open [doc (blank-doc 3)]
      (routes/draw-portrait-credit! doc 1 "Art: X")
      (let [short-x (-> (doto (PDFTextStripper.) (.setStartPage 2) (.setEndPage 2))
                        (.getText doc))]
        (is (re-find #"Art: X" short-x))))
    ;; a long credit and a short one cannot share a left edge if centred
    (is (< (:x (routes/portrait-credit-origin 1 "Art: Somebody With A Long Name"))
           (:x (routes/portrait-credit-origin 1 "Art: X")))
        "the longer line starts further left")))

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
