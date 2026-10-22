(ns orcpub.art-mark-drawing-test
  "The licensed-art mark: what the Loom writes into the real art is carried into everything drawn
   from it and refused by the guard. Set ORCPUB_MARKED_PACK to a pack exported by the Loom (real art,
   outside the repository) to run the real-art checks; without it they use marked silhouettes."
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [orcpub.art-mark :as mark]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.portrait-render :as pr]
            [orcpub.portrait-pack.strands :as strands]
            [orcpub.portrait-art-guard-test :as guard]
            [orcpub.routes :as routes])
  (:import [javax.imageio ImageIO]
           [java.io ByteArrayInputStream]
           [java.nio.file Files]
           [org.apache.pdfbox.pdmodel PDDocument PDPage]))

(defn- resource-bytes
  "The bytes of classpath resource `path`."
  ^bytes [path] (with-open [in (io/input-stream (io/resource path))] (.readAllBytes in)))

(defn- pixels
  "The decoded ARGB pixels of PNG bytes `b`."
  [^bytes b]
  (let [img (ImageIO/read (ByteArrayInputStream. b))]
    (vec (.getRGB img 0 0 (.getWidth img) (.getHeight img) nil 0 (.getWidth img)))))

(def marked-pack (some-> (System/getenv "ORCPUB_MARKED_PACK") io/file))

(defn- pack-bytes
  "The marked pack's bytes for the art at registry `url`, matched by layer and file name."
  [url]
  (let [[_ layer file] (re-find #"/image/portraits/([^/]+)/([^/]+)$" url)]
    (some->> (.listFiles (io/file marked-pack layer))
             (filter #(= (str/lower-case (.getName ^java.io.File %)) (str/lower-case file)))
             first .toPath Files/readAllBytes)))

(defn- portrait
  "A portrait choosing every layer's first piece, with an ombre so the strand fields are used."
  []
  {:layers (into {} (keep (fn [k] (when-let [a (first (pa/assets-for-layer k))]
                                    [k {:artist/id (pa/artist-for-asset k (:asset/id a)) :asset/id (:asset/id a)}])))
                 pa/layer-order)
   :colors {:hair "#7a3b1f" :skin "#c68642"}
   :ombre {:tip "#ffd27a"}})

(defn- with-marked-art
  "Run `f` with every piece read as marked art: the real pack when ORCPUB_MARKED_PACK is set, else
   silhouettes marked here."
  [f]
  (let [real pr/asset-source]
    (with-redefs [pr/asset-source (fn [url]
                                    (let [src (real url)]
                                      (cond-> src
                                        (and src (str/includes? (:mime src) "png"))
                                        (assoc :bytes (or (when marked-pack (pack-bytes url))
                                                          (mark/with-mark (:bytes src) "licensed; pack test; artist test"))))))]
      (f))))

(deftest marking-leaves-every-pixel-as-it-was
  (doseq [url (take 4 (map :asset/url (mapcat val (:artist/layers pa/house-pack))))
          :let [plain (resource-bytes (str "public" url))
                marked (mark/with-mark plain "licensed; pack test; artist test")]]
    (is (nil? (mark/mark-of plain)) "a silhouette carries no mark")
    (is (= "licensed; pack test; artist test" (mark/mark-of marked)))
    (is (= (pixels plain) (pixels marked)) url)
    (is (= (vec (drop 33 plain)) (vec (drop (- (alength marked) (- (alength plain) 33)) marked)))
        "the image data is byte for byte the same")
    (is (identical? marked (mark/with-mark marked "again")) "marking twice adds nothing")))

(deftest a-picture-drawn-from-marked-pieces-is-marked
  (is (nil? (mark/mark-of (pr/render-png (portrait)))) "drawn from silhouettes: no mark")
  (with-marked-art
    #(let [png (pr/render-png (portrait))]
       (is (str/starts-with? (str (mark/mark-of png)) "licensed; composed from ")
           (str "drawn from marked pieces: marked" (when marked-pack " (the real pack)"))))))

(deftest strand-files-from-marked-art-are-marked
  (let [dir (.toFile (Files/createTempDirectory "strands" (make-array java.nio.file.attribute.FileAttribute 0)))
        f (io/file dir "bangs" "l9_bangs_01.png")]
    (try
      (io/make-parents f)
      (io/copy (mark/with-mark (resource-bytes "public/image/portraits/bangs/l9_bangs_01.png") "licensed; pack test; artist test") f)
      (strands/write-missing! (.getPath dir) true)
      (is (mark/marked? (Files/readAllBytes (.toPath (io/file dir "bangs" "l9_bangs_01.strands.png"))))
          "a strand file worked out from marked art carries the mark")
      (finally (doseq [x (reverse (file-seq dir))] (.delete ^java.io.File x))))))

(deftest a-pdf-made-with-a-marked-portrait-is-marked
  (with-open [doc (doto (PDDocument.) (.addPage (PDPage.)))]
    (routes/stamp-document-info! doc {:character-name "Marky" :credit "Art: Fusspot"
                                      :art-mark "licensed; composed from licensed; pack test"})
    (is (= "licensed; composed from licensed; pack test"
           (.getCustomMetadataValue (.getDocumentInformation doc) "orcpub-art")))
    (let [out (java.io.ByteArrayOutputStream.)]
      (.save doc out)
      (is (guard/pdf-marked? (.toByteArray out)) "and the saved file says so where the guard looks"))))

(deftest the-guard-refuses-anything-marked
  (let [docs (io/file "docs/art-mark-check")
        piece (io/file docs "piece.png")
        composite (io/file docs "composite.png")]
    (try
      (.mkdirs docs)
      (io/copy (or (when marked-pack (pack-bytes "/image/portraits/hair-front/l4_hair_front_01.png"))
                   (mark/with-mark (resource-bytes "public/image/portraits/hair-front/l4_hair_front_01.png") "licensed; pack test; artist test"))
               piece)
      (with-marked-art #(io/copy (pr/render-png (portrait)) composite))
      (let [found (set (guard/marked-files))]
        (is (contains? found "docs/art-mark-check/piece.png") "a marked piece")
        (is (contains? found "docs/art-mark-check/composite.png") "a composite of marked pieces"))
      (finally (doseq [x (reverse (file-seq docs))] (.delete ^java.io.File x))))))
