(ns orcpub.static-cache-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [pandect.algo.sha1 :refer [sha1]]
            [orcpub.pedestal :as p]
            [orcpub.index :as index]
            [orcpub.config :as config]))

(deftest each-kind-of-static-file-gets-its-cache-rule
  (is (= "public, max-age=31536000, immutable" (p/static-cache-control "/js/compiled/orcpub.js" true)))
  (is (= "public, max-age=31536000, immutable" (p/static-cache-control "/css/cookiestyles.css" true)))
  (is (= "no-cache" (p/static-cache-control "/js/compiled/orcpub.js" false))
      "an unstamped bundle is rechecked, so a deploy reaches an old link too")
  (is (= "public, max-age=604800" (p/static-cache-control "/image/orc-head.svg" false)))
  (is (= "public, max-age=604800" (p/static-cache-control "/fonts/open-sans/open-sans-latin.woff2" false)))
  (is (= "public, max-age=86400" (p/static-cache-control "/js/cookies.js" false)))
  (is (nil? (p/static-cache-control "/pages/dnd/5e/character-builder" false)) "pages are not static files")
  (is (nil? (p/static-cache-control "/dnd/5e/items" true)) "data routes are not static files"))

(defn- leave
  "Runs `interceptor`'s :leave over a GET-like request for `uri` and a 200 response, returning the
   response's Cache-Control. `req` and `resp` override parts of either."
  [interceptor uri & {:keys [req resp]}]
  (get-in ((:leave interceptor)
           {:request  (merge {:request-method :get :uri uri} req)
            :response (merge {:status 200 :headers {}} resp)})
          [:response :headers "Cache-Control"]))

(deftest the-interceptor-sets-the-rule-only-where-it-belongs
  (let [prod (p/make-static-cache-interceptor false)]
    (is (= "public, max-age=31536000, immutable"
           (leave prod "/js/compiled/orcpub.js" :req {:query-string "v=0123456789ab"})))
    (is (= "public, max-age=604800" (leave prod "/image/orc-head.svg")))
    (is (= "no-store" (leave prod "/image/x.svg" :resp {:headers {"Cache-Control" "no-store"}}))
        "a response that sets its own Cache-Control keeps it")
    (is (nil? (leave prod "/image/x.svg" :req {:request-method :post})))
    (is (nil? (leave prod "/image/missing.svg" :resp {:status 404})))
    (is (= "no-cache" (leave prod "/js/compiled/orcpub.js" :req {:query-string "nav=1"}))
        "only a v= parameter is a stamp; this is an unstamped bundle"))
  (is (nil? (leave (p/make-static-cache-interceptor true) "/image/orc-head.svg"))
      "dev mode sets nothing, so Figwheel's rewrites are never served stale"))

(deftest page-links-carry-the-files-content-stamp
  (let [path "/css/cookiestyles.css"
        expected (with-open [in (io/input-stream (io/resource (str "public" path)))] (subs (sha1 in) 0 12))]
    (with-redefs [config/dev-mode? (constantly false)]
      (is (= (str path "?v=" expected) (index/asset-url path)))
      (is (= "/css/no-such-file.css" (index/asset-url "/css/no-such-file.css")) "a missing file is left bare"))
    (with-redefs [config/dev-mode? (constantly true)]
      (is (= path (index/asset-url path)) "dev mode links the bare file"))))
