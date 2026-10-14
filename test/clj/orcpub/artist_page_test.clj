(ns orcpub.artist-page-test
  "The server's part of the artist profile: share tags for the SPA page, a real 404 for a
   slug nobody has, and the example images. No database; the page reads only the registry."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as s]
            [orcpub.routes :as routes]
            [orcpub.dnd.e5.artist-profile :as ap]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.portrait-render :as render]
            [orcpub.artist-credit :as artist-credit]
            [orcpub.bounded-cache :as bounded-cache]
            [orcpub.work-pool :as work-pool])
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

(defn- cache-of
  "The routes cache named `sym` (examples kept, or failures remembered)."
  [sym]
  (some-> (ns-resolve 'orcpub.routes sym) deref))

(defn- clear-cache!
  "Empty the routes cache named `sym`; anything that is not a bounded cache is left as it is."
  [sym]
  (let [c (cache-of sym)] (when (map? c) (bounded-cache/clear! c))))

(defn- cache-size
  "How many entries the routes cache named `sym` holds."
  [sym]
  (let [c (cache-of sym)] (if (map? c) (bounded-cache/size c) (count @c))))

(defn- fresh-examples!
  "Forget every kept example and failure, so a test starts from nothing."
  []
  (clear-cache! 'example-jpegs)
  (clear-cache! 'example-failures))

(deftest a-failed-render-waits-before-it-is-tried-again
  (testing "a failure is remembered for a short while, so a broken example is not re-rendered on
            every request; once that passes it is tried again, and a success is kept"
    (fresh-examples!)
    (let [calls (atom 0)]
      (with-redefs [ap/example-portraits (fn [_] [{:layers {}}])
                    render/render-example-jpeg (fn [_ _]
                                                 (swap! calls inc)
                                                 (when (> @calls 1) (byte-array [1 2 3])))]
        (is (nil? (#'routes/example-jpeg :cache-test-artist 1 "t")) "the first render fails")
        (is (nil? (#'routes/example-jpeg :cache-test-artist 1 "t")) "and is not retried at once")
        (is (= 1 @calls))
        (clear-cache! 'example-failures)
        (is (= [1 2 3] (seq (#'routes/example-jpeg :cache-test-artist 1 "t")))
            "after the wait it renders again")
        (#'routes/example-jpeg :cache-test-artist 1 "t")
        (is (= 2 @calls) "and the success is kept")))))

(deftest concurrent-requests-share-one-render
  (fresh-examples!)
  (let [calls (atom 0)]
    (with-redefs [ap/example-portraits (fn [_] [{:layers {}}])
                  render/render-example-jpeg (fn [_ _] (swap! calls inc) (Thread/sleep 300) (byte-array [7]))]
      (let [fs (doall (repeatedly 4 #(future (#'routes/example-jpeg :flight-test 1 "t"))))]
        (is (every? #(= [7] (seq @%)) fs))
        (is (= 1 @calls) "four requests at once, one render")))))

(deftest the-kept-examples-are-bounded
  (testing "the key includes the watermark text, so every link edit makes new keys; old ones go"
    (fresh-examples!)
    (with-redefs [ap/example-portraits (fn [_] [{:layers {}}])
                  render/render-example-jpeg (fn [_ _] (byte-array [1]))]
      (doseq [i (range 200)] (#'routes/example-jpeg :bound-test 1 (str "text " i)))
      (is (<= (cache-size 'example-jpegs) 64)))))

(deftest examples-render-through-the-draw-pool
  (testing "when no draw slot frees, the example answers 503 with a Retry-After instead of queueing"
    (fresh-examples!)
    (with-redefs [work-pool/run (fn [_ _] {:busy 4 :waiting 9})]
      (let [resp (example "fusspot" "1")]
        (is (= 503 (:status resp)))
        (is (= "4" (get-in resp [:headers "Retry-After"])))))))

(deftest examples-use-the-current-credits
  (testing "a fresh container reads the artists' own credit edits before drawing the watermark"
    (fresh-examples!)
    (let [refreshed (atom nil)]
      (with-redefs [artist-credit/refresh! (fn [db] (reset! refreshed db))]
        (routes/artist-example-jpeg {:db :the-db :path-params {:slug "fusspot" :n "1"}})
        (is (= :the-db @refreshed))))))

(deftest there-are-only-three-examples
  (doseq [n ["0" "4" "x" "1.jpg" nil]]
    (is (= 404 (:status (example "fusspot" n))) (pr-str n))))

(deftest the-list-page-is-served
  (is (= 200 (:status (routes/artists-page {:headers {"host" "example.test"}
                                             :uri "/artists"})))))

(deftest a-page-turned-off-redirects-to-the-list
  (try
    (pa/set-artist-overrides! {:house-pack {:artist/profile? false}})
    (let [resp (routes/artist-page (req "fusspot"))]
      (is (= 302 (:status resp)) "temporary, so turning it back on works at once")
      (is (= "/artists" (get-in resp [:headers "Location"])))
      (is (not (s/includes? (str (:body resp)) "Fusspot")) "and nothing in it names them"))
    (is (= 404 (:status (example "fusspot" "1"))) "the example images go with it")
    (is (= 404 (:status (routes/artist-page (req "nobody")))) "a slug nobody has is still a 404")
    (finally (pa/set-artist-overrides! {}))))

(deftest examples-switched-off-are-not-served
  (try
    (pa/set-artist-overrides! {:house-pack {:artist/show-examples? false}})
    (is (= 404 (:status (example "fusspot" "1"))))
    (let [body (:body (routes/artist-page (req "fusspot")))]
      (is (s/includes? body "Fusspot") "the page itself is still there")
      (is (not (s/includes? body "/examples/")) "and its share card shows no example"))
    (finally (pa/set-artist-overrides! {}))))

(deftest the-piece-count-switched-off-is-not-shared
  (try
    (pa/set-artist-overrides! {:house-pack {:artist/show-count? false}})
    (is (not (re-find #"\d+ hand-drawn" (:body (routes/artist-page (req "fusspot"))))))
    (finally (pa/set-artist-overrides! {}))))

(deftest an-artist-name-is-never-page-source
  (testing "a name with markup in it appears escaped in the title and share tags, never as tags"
    (try
      (pa/set-artist-overrides! {:house-pack {:artist/name "<b>MARK</b>"}})
      (let [{:keys [status body]} (routes/artist-page (req "fusspot"))]
        (is (= 200 status))
        (is (not (s/includes? body "<b>MARK</b>")) "the raw markup is nowhere in the page")
        (is (s/includes? body "&lt;b&gt;MARK&lt;/b&gt;") "the name is there, escaped"))
      (finally (pa/set-artist-overrides! {})))))
