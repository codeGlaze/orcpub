(ns orcpub.build-badge-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [orcpub.build-badge :as bb]
            [orcpub.config])
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
  "A temp project dir with a client source at time `src`, the bundle at `bundle`, out/cljs_deps.js
   at `deps` and the stylesheet at `sheet` (nil leaves a file out); a style source is at time 1000."
  [src bundle deps sheet]
  (let [root (.toFile (Files/createTempDirectory "badge" (make-array FileAttribute 0)))]
    (touch! root "web/cljs/orcpub/core.cljs" src)
    (touch! root "src/clj/orcpub/styles/core.clj" 1000)
    (when bundle (touch! root (first bb/client-outputs) bundle))
    (when deps (touch! root (second bb/client-outputs) deps))
    (when sheet (touch! root bb/sheet-output sheet))
    root))

(deftest current-build-has-no-problems
  (is (= [] (bb/problems (project 1000 2000 2000 2000)))))

(deftest missing-build-files-are-reported
  (let [ps (bb/problems (project 1000 nil nil nil))]
    (is (= 2 (count ps)))
    (is (some #(re-find #"orcpub\.js does not exist" %) ps))
    (is (some #(re-find #"styles\.css does not exist" %) ps))))

(deftest a-newer-client-source-makes-the-bundle-stale
  (let [ps (bb/problems (project 3000 2000 2000 2000))]
    (is (= 1 (count ps)))
    (is (re-find #"older than web/cljs/orcpub/core\.cljs" (first ps)))))

(deftest a-watcher-that-moves-only-one-output-still-counts-as-current
  (testing "orcpub.js old, cljs_deps.js new: the build ran after the source changed"
    (is (= [] (bb/problems (project 3000 2000 4000 4000)))))
  (testing "orcpub.js new, cljs_deps.js old"
    (is (= [] (bb/problems (project 3000 4000 2000 4000))))))

(deftest a-newer-style-source-makes-only-the-stylesheet-stale
  (let [root (project 1000 2000 2000 2000)]
    (touch! root "src/clj/orcpub/styles/core.clj" 5000)
    (let [ps (bb/problems root)]
      (is (= 1 (count ps)))
      (is (re-find #"styles\.css is older than" (first ps))))))

(deftest a-newer-build-config-or-project-file-makes-the-bundle-stale
  (doseq [f ["dev.cljs.edn" "project.clj"]]
    (let [root (project 1000 2000 2000 2000)]
      (touch! root f 6000)
      (is (re-find (re-pattern (str "older than " (java.util.regex.Pattern/quote f)))
                   (first (bb/problems root)))))))

(deftest built-at-is-the-newest-output
  (is (= 4000 (bb/built-at (project 1000 2000 4000 3000))))
  (is (nil? (bb/built-at (project 1000 nil nil nil)))))

(deftest the-badge-shows-only-in-dev-mode-grey-when-current-amber-when-stale
  (testing "production renders nothing"
    (with-redefs [orcpub.config/dev-mode? (constantly false)]
      (is (nil? (bb/badge)))))
  (with-redefs [orcpub.config/dev-mode? (constantly true)]
    (testing "current: grey, no tooltip"
      (with-redefs [bb/problems (constantly [])]
        (let [b (pr-str (bb/badge))]
          (is (re-find #"built \d\d:\d\d|built never" b))
          (is (re-find #"rgba\(128" b))
          (is (not (re-find #":title" b))))))
    (testing "stale: amber, title names the newer file and the fix"
      (with-redefs [bb/problems (constantly ["orcpub.js is older than x.cljs"])]
        (let [b (pr-str (bb/badge))]
          (is (re-find #"f0a100" b))
          (is (re-find #":title \"orcpub\.js is older than x\.cljs\. Run: \./menu start\"" b)))))))
