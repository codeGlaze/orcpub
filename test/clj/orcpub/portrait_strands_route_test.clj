(ns orcpub.portrait-strands-route-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [orcpub.routes :as routes]
            [orcpub.portrait-pack.strands :as strands])
  (:import [javax.imageio ImageIO]
           [java.io ByteArrayInputStream]))

(defn- get-image [uri] (routes/get-image {:request-method :get :uri uri}))

(deftest a-missing-strand-file-is-worked-out-from-the-art
  (testing "the repo ships the art (silhouettes) without strand files, as a deploy that
            skipped the strand step would: the request is answered, not a 404"
    (let [uri "/image/portraits/bangs/l9_bangs_02.strands.png"
          art (with-open [in (io/input-stream (io/resource "public/image/portraits/bangs/l9_bangs_02.png"))]
                (ImageIO/read in))
          want (strands/field->image (strands/field-of art :bangs))
          {:keys [status headers body]} (get-image uri)
          got (ImageIO/read (ByteArrayInputStream. body))]
      (is (nil? (io/resource "public/image/portraits/bangs/l9_bangs_02.strands.png")) "no file was deployed")
      (is (= 200 status))
      (is (= "image/png" (get headers "Content-Type")))
      (is (= [(.getWidth want) (.getHeight want)] [(.getWidth got) (.getHeight got)]))
      (is (= (vec (.getDataElements (.getRaster want) 0 0 (.getWidth want) (.getHeight want) nil))
             (vec (.getDataElements (.getRaster got) 0 0 (.getWidth got) (.getHeight got) nil)))
          "the same field the deploy step would have written")
      (is (identical? body (:body (get-image uri))) "worked out once, then kept"))))

(deftest only-hair-art-that-exists-gets-one
  (is (nil? (get-image "/image/portraits/head/l2_head_01.strands.png")) "a head has no strands")
  (is (nil? (get-image "/image/portraits/bangs/no_such_piece.strands.png")) "no art, no field")
  (is (nil? (get-image "/image/portraits/../../project.clj.strands.png")) "no way out of the art directory")
  (is (some? (get-image "/image/portraits/bangs/l9_bangs_02.png")) "the art itself is served as before"))
