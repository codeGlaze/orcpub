(ns orcpub.artist-page-test
  "The server's part of the artist profile: share tags for the SPA page, a real 404 for a
   slug nobody has, and the example images. No database; the page reads only the registry."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as s]
            [orcpub.routes :as routes]
            [orcpub.dnd.e5.artist-profile :as ap]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.portrait-render :as render])
  (:import (javax.imageio ImageIO)))

(defn- req [slug]
  {:headers {"host" "example.test"}
   :uri (str "/artists/" slug)
   :path-params {:slug slug}})

(defn- example [slug n]
  (routes/artist-example-jpeg {:path-params {:slug slug :n n}}))

(defn- body-bytes [resp]
  (.readAllBytes ^java.io.InputStream (:body resp)))

(deftest the-profile-carries-its-own-share-tags
  (let [{:keys [status body]} (routes/artist-page (req "fusspot"))]
    (is (= 200 status))
    (is (s/includes? body "Fusspot — portrait artist") "the title names the artist")
    (is (s/includes? body "hand-drawn pieces") "the description says what is there")
    (is (s/includes? body "https://example.test/artists/fusspot/examples/1")
        "og:image is the page's first example")))

(deftest an-unknown-slug-is-a-404
  (testing "so a mistyped link is not indexed as a page; the SPA still loads
            and says there is nobody at that address"
    (is (= 404 (:status (routes/artist-page (req "nobody")))))
    (is (= 404 (:status (example "nobody" "1"))))))

(deftest the-examples-are-small-watermarked-jpegs
  (doseq [n ["1" "2" "3"]]
    (let [resp (example "fusspot" n)
          bytes (body-bytes resp)
          img (ImageIO/read (java.io.ByteArrayInputStream. bytes))]
      (is (= 200 (:status resp)) n)
      (is (= "image/jpeg" (get-in resp [:headers "Content-Type"])) n)
      (is (= "noai, noimageai" (get-in resp [:headers "X-Robots-Tag"])) n)
      (is (= [-1 -40] (vec (take 2 bytes))) (str n " is a JPEG"))
      (is (= [240 300] [(.getWidth img) (.getHeight img)])
          (str n " is about the size the page shows it, not larger"))
      (is (not= (seq bytes)
                (seq (render/render-example-jpeg
                      (get (ap/example-portraits :house-pack) (dec (parse-long n))) "")))
          (str n " carries a watermark: it differs from the same picture without one")))))

(deftest each-example-is-rendered-once
  (testing "the same bytes, not a fresh render, on every request"
    (is (identical? (#'routes/example-jpeg :house-pack 1 "x")
                    (#'routes/example-jpeg :house-pack 1 "x")))
    (is (= (seq (body-bytes (example "fusspot" "2")))
           (seq (body-bytes (example "fusspot" "2")))))))

(deftest there-are-only-three-examples
  (doseq [n ["0" "4" "x" "1.jpg" nil]]
    (is (= 404 (:status (example "fusspot" n))) (pr-str n))))

(deftest the-list-page-is-served
  (is (= 200 (:status (routes/artists-page {:headers {"host" "example.test"}
                                             :uri "/artists"})))))

(deftest a-page-the-artist-turned-off-is-a-404
  (try
    (pa/set-artist-overrides! {:house-pack {:artist/profile? false}})
    (is (= 404 (:status (routes/artist-page (req "fusspot")))))
    (is (= 404 (:status (example "fusspot" "1")))
        "the example images go with it")
    (is (not (s/includes? (:body (routes/artist-page (req "fusspot"))) "Fusspot"))
        "and the 404's share tags do not name them")
    (finally (pa/set-artist-overrides! {}))))
