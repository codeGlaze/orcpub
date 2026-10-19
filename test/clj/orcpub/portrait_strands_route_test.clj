(ns orcpub.portrait-strands-route-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [orcpub.routes :as routes]
            [orcpub.portrait-pack.strands :as strands]
            [orcpub.system :as system]
            [io.pedestal.http :as http]
            [io.pedestal.test :refer [response-for]])
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

(def ^:private service
  ;; the server's own chain: static files, the guard, then the routes
  (delay (::http/service-fn (http/create-servlet (system/with-portrait-guard
                                                   (http/default-interceptors system/prod-service-map))))))

(defn- served [uri] (response-for @service :get uri))

(deftest the-art-directory-serves-images-only
  (testing "through the whole chain, where static files answer before any route"
    (testing "a manifest unpacked beside the art is not public"
      (is (some? (io/resource "public/image/portraits/silhouette-index.json")) "the file is there")
      (is (= 404 (:status (served "/image/portraits/silhouette-index.json"))) "but not served")
      (is (= 404 (:status (served "/image/portraits/manifest.json"))))
      (is (= 404 (:status (served "/image/portraits/"))) "and the directory is not listed"))
    (testing "each piece, and each worked-out strand file, asks not to be used for AI"
      (let [r (served "/image/portraits/bangs/l9_bangs_01.png")]
        (is (= 200 (:status r)))
        (is (= "noai, noimageai" (get-in r [:headers "X-Robots-Tag"]))))
      (let [r (served "/image/portraits/bangs/l9_bangs_03.strands.png")]
        (is (= 200 (:status r)))
        (is (= "noai, noimageai" (get-in r [:headers "X-Robots-Tag"])))))
    (testing "images outside the art directory are served as before"
      (let [r (served "/image/card-logo-bw.png")]
        (is (= 200 (:status r)))
        (is (nil? (get-in r [:headers "X-Robots-Tag"])))))))

(deftest only-registered-pieces-are-served
  (let [stray (io/file "resources/public/image/portraits/bangs/l9_bangs_99.png")]
    (try
      (io/copy (io/file "resources/public/image/portraits/bangs/l9_bangs_01.png") stray)
      (is (some? (io/resource "public/image/portraits/bangs/l9_bangs_99.png")) "the file is there")
      (is (= 404 (:status (served "/image/portraits/bangs/l9_bangs_99.png")))
          "a piece no artist registered is not served, whatever its name")
      (is (= 404 (:status (served "/image/portraits/bangs/l9_bangs_99.strands.png"))) "nor its strands")
      (is (nil? (get-image "/image/portraits/bangs/l9_bangs_99.strands.png")))
      (is (= 404 (:status (served "/image/portraits/head/l2_head_01.strands.png"))) "a head has no strands")
      (is (= 404 (:status (served "/image/portraits/bangs/../head/l2_head_01.png"))) "no path tricks")
      (finally (io/delete-file stray true)))))

(deftest an-encoded-path-cannot-get-past-the-guard
  (testing "the static file server decodes the path, so the guard must judge the decoded one"
    (doseq [uri ["/image/%70ortraits/silhouette-index.json"
                 "/image/portraits%2Fsilhouette-index.json"
                 "/image/%2570ortraits/silhouette-index.json"
                 "/image/./portraits/silhouette-index.json"
                 "/image/x/../portraits/silhouette-index.json"
                 "/%69mage/portraits/silhouette-index.json"]]
      (is (not= 200 (:status (served uri))) uri))
    (let [r (served "/image/%70ortraits/bangs/l9_bangs_01.png")]
      (is (contains? #{200 404} (:status r)))
      (when (= 200 (:status r))
        (is (= "noai, noimageai" (get-in r [:headers "X-Robots-Tag"]))
            "a piece reached by an encoded path still asks not to be used for AI")))))
