(ns orcpub.pdf-supplied-image-test
  "Image bytes the browser read and sent with the export.

   They arrive from the same untrusted client that supplies the URL, so sending
   bytes skips the fetch and nothing else: every ceiling safe-image-bytes applies
   is applied here too, and the format is read from the bytes rather than from
   what the client called them."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.pdf :as pdf])
  (:import (java.awt.image BufferedImage)
           (java.io ByteArrayOutputStream)
           (java.util Base64)
           (javax.imageio ImageIO)))

(defn- encoded
  "A `w` x `h` image written as `fmt`, base64 as the client sends it. Solid black,
   so a large canvas still compresses small and the pixel ceiling can be tested
   without tripping the byte ceiling first."
  [fmt w h]
  (let [out (ByteArrayOutputStream.)]
    (ImageIO/write (BufferedImage. w h BufferedImage/TYPE_INT_RGB) fmt out)
    (.encodeToString (Base64/getEncoder) (.toByteArray out))))

(deftest a-jpeg-is-recognised-from-its-bytes
  (let [{:keys [data jpg?]} (pdf/decode-image-bytes (encoded "jpg" 40 40))]
    (is jpg? "JPEG bytes embed as they are, so they have to be identified")
    (is (pos? (alength ^bytes data)))))

(deftest a-png-is-not-mistaken-for-a-jpeg
  (let [{:keys [data jpg?]} (pdf/decode-image-bytes (encoded "png" 40 40))]
    (is (false? jpg?) "a PNG has to be decoded and re-encoded to embed")
    (is (pos? (alength ^bytes data)))))

(deftest the-format-is-read-from-the-bytes-not-from-the-client
  ;; decode-image-bytes is given only the payload, never the mime type the browser
  ;; reported, so a blob labelled image/jpeg that is really a PNG cannot reach
  ;; JPEGFactory and fail the embed.
  (is (false? (:jpg? (pdf/decode-image-bytes (encoded "png" 8 8))))))

(deftest nothing-usable-yields-nothing
  (testing "absent"
    (is (nil? (pdf/decode-image-bytes nil)))
    (is (nil? (pdf/decode-image-bytes "")))
    (is (nil? (pdf/decode-image-bytes "   "))))
  (testing "not base64"
    (is (nil? (pdf/decode-image-bytes "this is not base64 %%%"))))
  (testing "base64 of something that is not an image"
    (is (nil? (pdf/decode-image-bytes
               (.encodeToString (Base64/getEncoder) (.getBytes "hello" "UTF-8")))))))

(deftest a-payload-past-the-byte-ceiling-is-refused-before-it-is-decoded
  ;; The check is on the ENCODED length, so an oversized image never becomes a
  ;; byte array at all. Base64 spends four characters on every three bytes, so
  ;; anything past 128k of image is past ~171k of string.
  (let [too-long (apply str (repeat (* 200 1024) "A"))]
    (is (nil? (pdf/decode-image-bytes too-long)))))

(deftest a-payload-past-the-pixel-ceiling-is-refused
  ;; Small file, enormous canvas: the byte ceiling does not bound the decode, so
  ;; the dimensions are read from the header separately.
  (let [b64 (encoded "png" 2001 2001)]
    (is (< (count b64) (* 128 1024))
        "the guard under test is the pixel one, so this must clear the byte one")
    (is (nil? (pdf/decode-image-bytes b64)))))

(deftest an-image-inside-both-ceilings-is-taken
  (let [b64 (encoded "png" 1999 1999)]
    (is (some? (pdf/decode-image-bytes b64)))))
