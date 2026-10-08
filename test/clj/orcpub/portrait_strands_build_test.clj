(ns orcpub.portrait-strands-build-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [orcpub.portrait-pack.strands :as strands])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- art-copy
  "The repo's hair art (silhouettes) copied to a scratch directory, laid out as a pack."
  []
  (let [dir (.toFile (Files/createTempDirectory "strands-build" (make-array FileAttribute 0)))]
    (doseq [layer ["hair-back" "hair-bits" "hair-front" "bangs"]
            :let [src (io/file "resources/public/image/portraits" layer)]
            ^java.io.File f (.listFiles src)
            :when (re-find #"\.png$" (.getName f))]
      (io/make-parents (io/file dir layer "x"))
      (io/copy f (io/file dir layer (.getName f))))
    dir))

(deftest the-build-writes-only-what-is-missing-or-stale
  (let [dir (art-copy)
        statuses (fn [all?] (frequencies (vals (strands/write-missing! dir all?))))
        hair (count (filter #(.endsWith (.getName ^java.io.File %) ".png") (file-seq dir)))]
    (testing "a pack with no strand files gets one for every hair piece"
      (is (= {:written hair} (statuses false))))
    (testing "running again changes nothing"
      (is (= {:kept hair} (statuses false))))
    (testing "art newer than its strand file has it rewritten, and only that one"
      (let [art (io/file dir "bangs" "l9_bangs_02.png")]
        (.setLastModified art (+ (System/currentTimeMillis) 60000))
        (is (= {:written 1 :kept (dec hair)} (statuses false)))))
    (testing "--all rewrites every one"
      (is (= {:written hair} (statuses true))))))
