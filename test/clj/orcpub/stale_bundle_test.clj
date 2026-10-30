(ns orcpub.stale-bundle-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [orcpub.config]
            [orcpub.stale-bundle :as sb])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- touch!
  "Create the file `path` under `root` (parents too) with modified time `ms`."
  [root path ms]
  (let [f (io/file root path)]
    (io/make-parents f)
    (spit f "x")
    (.setLastModified f ms)
    f))

(defn- project
  "A temp project dir with a client source at time `src`, the bundle at `bundle` and the
   stylesheet at `sheet` (nil leaves a build file out); a style source is at time 1000."
  [src bundle sheet]
  (let [root (.toFile (Files/createTempDirectory "stale-bundle" (make-array FileAttribute 0)))]
    (touch! root "web/cljs/orcpub/core.cljs" src)
    (touch! root "src/clj/orcpub/styles/core.clj" 1000)
    (when bundle (touch! root sb/bundle-path bundle))
    (when sheet (touch! root sb/sheet-path sheet))
    root))

(deftest current-build-has-no-problems
  (is (= [] (sb/problems (project 1000 2000 2000)))))

(deftest missing-build-files-are-reported
  (let [ps (sb/problems (project 1000 nil nil))]
    (is (= 2 (count ps)))
    (is (some #(re-find #"orcpub\.js does not exist" %) ps))
    (is (some #(re-find #"styles\.css does not exist" %) ps))))

(deftest a-newer-client-source-makes-the-bundle-stale
  (let [ps (sb/problems (project 3000 2000 2000))]
    (testing "names the bundle and the source that outdates it, relative to the project"
      (is (= 1 (count ps)))
      (is (re-find #"orcpub\.js is older than web/cljs/orcpub/core\.cljs" (first ps))))))

(deftest a-newer-style-source-makes-only-the-stylesheet-stale
  (let [root (project 1000 2000 2000)]
    (touch! root "src/clj/orcpub/styles/core.clj" 5000)
    (let [ps (sb/problems root)]
      (is (= 1 (count ps)))
      (is (re-find #"styles\.css is older than" (first ps))))))

(deftest a-newer-build-config-makes-the-bundle-stale
  (let [root (project 1000 2000 2000)]
    (touch! root "dev.cljs.edn" 6000)
    (is (re-find #"orcpub\.js is older than dev\.cljs\.edn" (first (sb/problems root))))))

(deftest banner-shows-only-in-dev-mode-and-only-when-stale
  (with-redefs [sb/problems (constantly ["orcpub.js is older than x.cljs"])]
    (testing "dev mode with a stale build names the problem"
      (with-redefs [orcpub.config/dev-mode? (constantly true)]
        (is (re-find #"orcpub\.js is older than x\.cljs" (pr-str (sb/banner))))))
    (testing "production shows nothing"
      (with-redefs [orcpub.config/dev-mode? (constantly false)]
        (is (nil? (sb/banner))))))
  (with-redefs [sb/problems (constantly [])
                orcpub.config/dev-mode? (constantly true)]
    (testing "dev mode with a current build shows nothing"
      (is (nil? (sb/banner))))))
