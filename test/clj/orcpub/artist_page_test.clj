(ns orcpub.artist-page-test
  "The server's part of the artist profile: share tags for the SPA page, a real
   404 for a slug nobody has, and the og image. No database -- the page reads
   only the registry."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as s]
            [orcpub.routes :as routes]))

(defn- req [slug]
  {:headers {"host" "example.test"}
   :uri (str "/artists/" slug)
   :path-params {:slug slug}})

(deftest the-profile-carries-its-own-share-tags
  (let [{:keys [status body]} (routes/artist-page (req "fusspot"))]
    (is (= 200 status))
    (is (s/includes? body "Fusspot — portrait artist") "the title names the artist")
    (is (s/includes? body "hand-drawn pieces") "the description says what is there")
    (is (s/includes? body "https://example.test/artists/fusspot/portrait.png")
        "og:image is the page's lead portrait, rendered on the server")))

(deftest an-unknown-slug-is-a-404
  (testing "so a mistyped link is not indexed as a page; the SPA still loads
            and says there is nobody at that address"
    (is (= 404 (:status (routes/artist-page (req "nobody")))))
    (is (= 404 (:status (routes/artist-portrait-png (req "nobody")))))))

(deftest the-og-image-is-a-png
  (let [resp (routes/artist-portrait-png (req "fusspot"))
        buf (byte-array 4)]
    (is (= 200 (:status resp)))
    (is (= "image/png" (get-in resp [:headers "Content-Type"])))
    (is (= "noai, noimageai" (get-in resp [:headers "X-Robots-Tag"])))
    (.read ^java.io.InputStream (:body resp) buf)
    (is (= [-119 80 78 71] (vec buf)))))

(deftest the-list-page-is-served
  (is (= 200 (:status (routes/artists-page {:headers {"host" "example.test"}
                                             :uri "/artists"})))))
