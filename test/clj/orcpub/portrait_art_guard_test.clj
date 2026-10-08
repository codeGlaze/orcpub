(ns orcpub.portrait-art-guard-test
  "The public repository carries the anonymised silhouettes, never the licensed art. Each portrait
   PNG must match the digest recorded for its silhouette, unless the private-art marker says this
   is a private deployment branch (scripts/private-art.sh writes it).
   A new or redrawn silhouette: PORTRAIT_SILHOUETTES=record lein test :only orcpub.portrait-art-guard-test"
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint])
  (:import [java.security MessageDigest]
           [java.nio.file Files]))

(def art-dir "resources/public/image/portraits")
(def marker (io/file art-dir "PRIVATE-ART"))
(def digests-file "test/portrait-silhouettes.edn")

(defn- sha256 [^java.io.File f]
  (apply str (map #(format "%02x" %) (.digest (MessageDigest/getInstance "SHA-256")
                                              (Files/readAllBytes (.toPath f))))))

(defn portrait-pngs
  "{relative path -> digest} for every portrait PNG; strand files are derived and never committed."
  []
  (into (sorted-map)
        (for [^java.io.File f (file-seq (io/file art-dir))
              :let [n (.getName f)]
              :when (and (.isFile f) (re-find #"(?i)\.png$" n) (not (re-find #"(?i)\.strands\.png$" n)))]
          [(str (.relativize (.toPath (io/file art-dir)) (.toPath f))) (sha256 f)])))

(deftest the-public-repo-carries-only-silhouettes
  (cond
    (.exists marker)
    (println "\n  private art branch (" (.getPath marker) "): silhouette guard off\n")

    (= "record" (System/getenv "PORTRAIT_SILHOUETTES"))
    (do (spit digests-file (with-out-str (pprint/pprint (portrait-pngs))))
        (println "recorded" (count (portrait-pngs)) "silhouettes to" digests-file))

    :else
    (let [recorded (edn/read-string (slurp digests-file))
          found (portrait-pngs)]
      (testing "every portrait file is the recorded silhouette, byte for byte"
        (doseq [[path digest] found]
          (is (= (get recorded path) digest)
              (str path (if (contains? recorded path) " is not the recorded silhouette" " is not a recorded silhouette")
                   ". If this is the licensed art it must never be committed here: it belongs only on the"
                   " private deployment branch (scripts/private-art.sh). If it is a new silhouette,"
                   " record it: PORTRAIT_SILHOUETTES=record lein test :only orcpub.portrait-art-guard-test"))))
      (testing "and every recorded silhouette is still there"
        (is (empty? (remove (set (keys found)) (keys recorded))) "a recorded silhouette is missing")))))
