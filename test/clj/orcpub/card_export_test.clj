(ns orcpub.card-export-test
  "Covers what a document pays for when it carries card pages."
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [orcpub.pdf :as pdf]
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
      (let [fonts (pdf/load-fonts doc)
            img (pdf/make-image-loader doc)
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
