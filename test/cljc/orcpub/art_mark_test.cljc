(ns orcpub.art-mark-test
  "The licensed-art mark writes the same bytes in the browser and on the server, and reads back."
  (:require #?(:clj [clojure.test :refer [deftest is]]
               :cljs [cljs.test :refer-macros [deftest is]])
            [orcpub.art-mark :as mark]))

(def ^:private tiny-png
  "A 1x1 PNG: signature, IHDR, IEND."
  [137 80 78 71 13 10 26 10, 0 0 0 13 73 72 68 82, 0 0 0 1 0 0 0 1 8 6 0 0 0, 31 21 196 137,
   0 0 0 0 73 69 78 68 174 66 96 130])

(def ^:private expected
  "tiny-png marked \"licensed; pack v1; artist house-pack\", as the JVM writes it (CRCs checked
   against zlib)."
  [137 80 78 71 13 10 26 10 0 0 0 13 73 72 68 82 0 0 0 1 0 0 0 1 8 6 0 0 0 31 21 196 137 0 0 0 51 105 84 88 116 111 114 99 112 117 98 45 97 114 116 0 0 0 0 0 108 105 99 101 110 115 101 100 59 32 112 97 99 107 32 118 49 59 32 97 114 116 105 115 116 32 104 111 117 115 101 45 112 97 99 107 241 216 110 169 0 0 0 0 73 69 78 68 174 66 96 130])

(defn- bytes-of
  "Ints `v` as the platform's byte array."
  [v] #?(:clj (byte-array (map unchecked-byte v)) :cljs (js/Uint8Array. (clj->js v))))
(defn- ints-of
  "A platform byte array as a vector of ints 0..255."
  [b] #?(:clj (mapv #(bit-and % 0xff) b) :cljs (vec (array-seq (js/Array.from b)))))

(deftest the-mark-is-the-same-bytes-everywhere
  (let [marked (mark/write-mark (bytes-of tiny-png) "licensed; pack v1; artist house-pack")]
    (is (= expected (ints-of marked)) "chunk, CRC and position identical on both platforms")
    (is (= "licensed; pack v1; artist house-pack" (mark/read-mark marked)))
    (is (nil? (mark/read-mark (bytes-of tiny-png))))
    (is (= expected (ints-of (mark/write-mark marked "other"))) "marking twice adds nothing")))

(def ^:private tiny-jpeg
  "The smallest JPEG framing: start of image, end of image."
  [255 216 255 217])

(deftest a-jpeg-carries-the-mark-in-a-comment
  (let [marked (mark/write-mark (bytes-of tiny-jpeg) "licensed; pack v1; artist house-pack")]
    (is (= [255, 216, 255, 254, 0, 49, 111, 114, 99, 112, 117, 98, 45, 97, 114, 116, 0, 108, 105, 99, 101, 110, 115, 101, 100, 59, 32, 112, 97, 99, 107, 32, 118, 49, 59, 32, 97, 114, 116, 105, 115, 116, 32, 104, 111, 117, 115, 101, 45, 112, 97, 99, 107, 255, 217] (ints-of marked))
        "a comment segment straight after start-of-image, the same bytes on both platforms")
    (is (= "licensed; pack v1; artist house-pack" (mark/read-mark marked)))
    (is (nil? (mark/read-mark (bytes-of tiny-jpeg))))
    (is (= (ints-of marked) (ints-of (mark/write-mark marked "other"))) "marking twice adds nothing")))

(deftest a-jpeg-keeps-its-app-segments-first
  ;; JFIF needs its APP0 straight after start-of-image, so the comment goes after any APPn
  (let [app0 [255 224 0 4 74 70] app1 [255 225 0 2]
        marked (ints-of (mark/write-mark (bytes-of (concat [255 216] app0 app1 [255 217])) "x"))]
    (is (= (concat [255 216] app0 app1 [255 254]) (take 14 marked)) "APP0 and APP1 untouched, then the comment")
    (is (= "x" (mark/read-mark (bytes-of marked))))))

(deftest an-unmarkable-format-is-refused-not-skipped
  (is (thrown? #?(:clj Exception :cljs js/Error) (mark/write-mark (bytes-of [71 73 70 56 57 97]) "x"))
      "a GIF cannot hold the mark, so writing one fails loudly"))

(deftest carrying-marks-marks-only-what-was-drawn-from-marked-pieces
  (let [png (bytes-of tiny-png)]
    (is (identical? png (mark/carry-marks png [nil nil])) "no marked piece: the bytes as they were")
    (is (= "licensed; composed from licensed; pack v1; artist house-pack"
           (mark/read-mark (mark/carry-marks png [nil "licensed; pack v1; artist house-pack"]))))))

(deftest the-mark-of-a-composite-names-its-sources
  (is (nil? (mark/combine-marks [nil nil])) "no marked piece, no mark")
  (is (= "licensed; composed from licensed; pack v1; artist a"
         (mark/combine-marks [nil "licensed; pack v1; artist a" "licensed; pack v1; artist a"]))))

(deftest a-mark-between-the-scans-of-a-progressive-jpeg-is-found
  (let [com (fn [text] (let [t (map #(#?(:clj int :cljs .charCodeAt) %) (str "orcpub-art" (char 0) text))
                             n (+ 2 (count t))]
                         (concat [255 254 (quot n 256) (rem n 256)] t)))
        sos [255 218 0 8 1 1 0 0 63 0]
        ;; entropy-coded data with a stuffed FF00, a restart marker and a fill byte, none of them markers
        data [18 255 0 52 255 208 86 255 255]
        jpeg (concat [255 216] sos data (com "x") [255 217])]
    (is (= "x" (mark/read-mark (bytes-of jpeg))) "the comment after the first scan is read")
    (is (nil? (mark/read-mark (bytes-of (concat [255 216] sos (map #(#?(:clj int :cljs .charCodeAt) %) "orcpub-art") [255 217]))))
        "the keyword inside image data is not a mark")))
