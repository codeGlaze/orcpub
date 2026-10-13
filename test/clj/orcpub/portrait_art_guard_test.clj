(ns orcpub.portrait-art-guard-test
  "The public repository carries the anonymised silhouettes, never the licensed art. Each portrait
   PNG must match the digest recorded for its silhouette, unless the private-art marker says this
   is a private deployment branch (scripts/private-art.sh writes it).
   A new or redrawn silhouette: PORTRAIT_SILHOUETTES=record lein test :only orcpub.portrait-art-guard-test"
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [orcpub.art-mark :as art-mark])
  (:import [java.security MessageDigest]
           [java.nio.file Files]
           [java.awt.image BufferedImage]
           [javax.imageio ImageIO]))

(def art-dir "resources/public/image/portraits")
(def marker (io/file art-dir "PRIVATE-ART"))
(def digests-file "test/portrait-silhouettes.edn")
(def reviewed-file "test/reviewed-images.edn")

(defn- sha256 [^java.io.File f]
  (apply str (map #(format "%02x" %) (.digest (MessageDigest/getInstance "SHA-256")
                                              (Files/readAllBytes (.toPath f))))))

(defn portrait-pngs
  "{relative path -> digest} for every portrait PNG; strand files are derived and never committed."
  []
  (into (sorted-map)
        (for [^java.io.File f (file-seq (io/file art-dir))
              :let [n (.getName f)]
              :when (and (.isFile f) (re-find #"(?i)\.png$" n) (not (re-find #"(?i)\.strands\.png$" n)))]
          [(str (.relativize (.toPath (io/file art-dir)) (.toPath f))) (sha256 f)])))

(declare marked-files)

(deftest no-file-carries-the-licensed-art-mark
  (testing "the Loom marks every piece of the real art, and everything drawn from it carries the
            mark: a marked file is the licensed art, whatever else it passes"
    (doseq [path (marked-files)]
      (is false (str path " carries the licensed-art mark (orcpub-art). It must not be in this"
                     " repository: it belongs only on the private deployment branch.")))))

(deftest the-public-repo-carries-only-silhouettes
  (cond
    (.exists marker)
    (println "\n  private art branch (" (.getPath marker) "): silhouette guard off\n")

    (= "record" (System/getenv "PORTRAIT_SILHOUETTES"))
    (do (spit digests-file (with-out-str (pprint/pprint (portrait-pngs))))
        (println "recorded" (count (portrait-pngs)) "silhouettes to" digests-file))

    :else
    (let [recorded (edn/read-string (slurp digests-file))
          found (portrait-pngs)]
      (testing "every portrait file is the recorded silhouette, byte for byte"
        (doseq [[path digest] found]
          (is (= (get recorded path) digest)
              (str path (if (contains? recorded path) " is not the recorded silhouette" " is not a recorded silhouette")
                   ". If this is the licensed art it must never be committed here: it belongs only on the"
                   " private deployment branch (scripts/private-art.sh). If it is a new silhouette,"
                   " record it: PORTRAIT_SILHOUETTES=record lein test :only orcpub.portrait-art-guard-test"))))
      (testing "and every recorded silhouette is still there"
        (is (empty? (remove (set (keys found)) (keys recorded))) "a recorded silhouette is missing")))))

;; ---------------------------------------------------------------------------
;; Every other image in the repository
;; ---------------------------------------------------------------------------

(defn- git-files
  "Paths git tracks or would commit (untracked and not ignored), from the repository root."
  []
  (let [run #(let [p (.start (ProcessBuilder. ^java.util.List (into ["git"] %)))]
               (str/split-lines (slurp (.getInputStream p))))]
    (into (run ["ls-files"]) (run ["ls-files" "--others" "--exclude-standard"]))))

(defn repo-images
  "{path -> digest} for every image outside the portrait art directory that git tracks or would
   commit, docs/ included."
  []
  (into (sorted-map)
        (for [path (git-files)
              :when (and (re-find #"(?i)\.(png|jpe?g|webp|gif)$" path)
                         (not (str/starts-with? path (str art-dir "/")))
                         (.isFile (io/file path)))]
          [path (sha256 (io/file path))])))

(defn pdf-marked?
  "Whether PDF bytes `b` carry the mark in their document information (the exported sheet puts it
   there; the file's own compression hides it from a plain byte search)."
  [^bytes b]
  (try (with-open [doc (org.apache.pdfbox.Loader/loadPDF b)]
         (some? (.getCustomMetadataValue (.getDocumentInformation doc) art-mark/mark-keyword)))
       (catch Exception _ (str/includes? (String. b "ISO-8859-1") art-mark/mark-keyword))))

(defn marked-files
  "Paths git tracks or would commit that carry the licensed-art mark: a PNG's orcpub-art chunk, or
   the key anywhere in another image or a PDF (the sheet records it in its metadata). The art
   directory is skipped on the private deployment branch, where the art belongs."
  []
  (let [private? (.exists marker)]
    (for [path (git-files)
          :when (and (re-find #"(?i)\.(png|jpe?g|webp|gif|pdf)$" path)
                     (.isFile (io/file path))
                     (not (and private? (str/starts-with? path (str art-dir "/")))))
          :let [b (Files/readAllBytes (.toPath (io/file path)))]
          :when (cond (re-find #"(?i)\.(png|jpe?g)$" path) (art-mark/marked? b)
                      (re-find #"(?i)\.pdf$" path) (pdf-marked? b)
                      :else (str/includes? (String. ^bytes b "ISO-8859-1") art-mark/mark-keyword))]
      path)))

(defn linework
  "Percent of an image's drawn pixels that are inked line: dark, with lighter drawn pixels within
   2 px. Shown to whoever reviews a new image; the licensed art's pieces run 0..23 (median 6.7),
   and logos and photos overlap that range, so it informs the review rather than replacing it."
  [path]
  (when-let [^BufferedImage img (try (ImageIO/read (io/file path)) (catch Exception _ nil))]
    (let [w (.getWidth img) h (.getHeight img)
          step (max 1 (quot (max w h) 800))
          gw (quot w step) gh (quot h step)
          px (fn [x y] (.getRGB img (int (* x step)) (int (* y step))))
          info (fn [x y] (let [c (px x y) a (bit-and (unsigned-bit-shift-right c 24) 0xff)
                               l (+ (* 0.299 (bit-and (bit-shift-right c 16) 0xff))
                                    (* 0.587 (bit-and (bit-shift-right c 8) 0xff))
                                    (* 0.114 (bit-and c 0xff)))]
                           [(> a 128) l]))
          cells (for [y (range gh) x (range gw)] [x y])
          drawn (filter #(first (apply info %)) cells)
          light? (fn [x y] (let [[d l] (info x y)] (and d (> l 140))))
          stroke (filter (fn [[x y]]
                           (and (< (second (info x y)) 60)
                                (some (fn [[dx dy]] (let [nx (+ x dx) ny (+ y dy)]
                                                      (and (< -1 nx gw) (< -1 ny gh) (light? nx ny))))
                                      (for [dy (range -2 3) dx (range -2 3)] [dx dy]))))
                         drawn)]
      (if (empty? drawn) 0.0 (/ (Math/round (* 1000.0 (/ (count stroke) (count drawn)))) 10.0)))))

(deftest every-image-is-a-silhouette-or-reviewed
  (let [reviewed (edn/read-string (slurp reviewed-file))
        found (repo-images)]
    (if (= "record" (System/getenv "REVIEWED_IMAGES"))
      (do (spit reviewed-file
                (with-out-str
                  (pprint/pprint
                   (into (sorted-map)
                         (for [[path digest] found]
                           [path (if (= digest (get-in reviewed [path :sha256]))
                                   (get reviewed path)
                                   {:sha256 digest :what "DESCRIBE: what is this image?"
                                    :linework (linework path)})])))))
          (println "recorded" (count found) "images to" reviewed-file "-- describe each new one"))
      (testing "every image is one a person has looked at: the licensed art must never be in this
                repository, in docs/ or anywhere else, and no measure tells it from a logo reliably"
        ;; The message is built only on a miss: `is` evaluates it on a pass too, and scoring
        ;; every image for linework takes seconds each.
        (doseq [[path digest] found
                :let [ok? (and (= digest (get-in reviewed [path :sha256]))
                               (not (str/starts-with? (str (get-in reviewed [path :what])) "DESCRIBE")))]]
          (is ok?
              (when-not ok?
               (str path " (linework " (linework path) "%) is not a reviewed image. Look at it: if it"
                   " contains the licensed portrait art, it must not be committed here (it belongs only"
                   " on the private deployment branch). A silhouette composite, screenshot, logo or"
                   " photo: REVIEWED_IMAGES=record lein test :only orcpub.portrait-art-guard-test,"
                   " then say what it is in " reviewed-file))))))))
