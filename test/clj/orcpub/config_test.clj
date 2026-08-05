(ns orcpub.config-test
  "Tests for host-provided (site) homebrew reading in orcpub.config.

   read-site-homebrew scans a directory for *.orcbrew files and returns their
   raw text plus a stable version hash, which the app injects into the page for
   the client to validate and merge. These pin the read behavior: it picks up
   only .orcbrew files, sorts them for a stable hash, tolerates a missing dir,
   and produces a version that changes iff the content changes."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [orcpub.config :as config]))

(defn- temp-dir! []
  (let [d (io/file (System/getProperty "java.io.tmpdir")
                   (str "orcpub-homebrew-test-" (System/nanoTime)))]
    (.mkdirs d)
    d))

(defn- spit-file! [dir name content]
  (spit (io/file dir name) content))

(deftest missing-dir-returns-empty
  (testing "A non-existent directory yields no sources and no version"
    (let [result (config/read-site-homebrew
                  (str (io/file (System/getProperty "java.io.tmpdir")
                                (str "orcpub-does-not-exist-" (System/nanoTime)))))]
      (is (= {:version nil :sources []} result)))))

(deftest empty-dir-returns-empty
  (testing "An existing but empty directory yields no sources and no version"
    (let [dir (temp-dir!)]
      (is (= {:version nil :sources []}
             (config/read-site-homebrew (str dir)))))))

(deftest reads-only-orcbrew-files
  (testing "Only *.orcbrew files are read; other files are ignored"
    (let [dir (temp-dir!)]
      (spit-file! dir "pack.orcbrew" "{\"Pack\" {}}")
      (spit-file! dir "notes.txt" "ignore me")
      (spit-file! dir "README.md" "ignore me too")
      (let [{:keys [sources version]} (config/read-site-homebrew (str dir))]
        (is (= ["{\"Pack\" {}}"] sources))
        (is (string? version))))))

(deftest sorted-for-stable-version
  (testing "Sources are ordered by filename so the version hash is deterministic"
    (let [dir (temp-dir!)]
      (spit-file! dir "b.orcbrew" "BBB")
      (spit-file! dir "a.orcbrew" "AAA")
      (let [{:keys [sources]} (config/read-site-homebrew (str dir))]
        (is (= ["AAA" "BBB"] sources)
            "a.orcbrew must come before b.orcbrew regardless of write order")))))

(deftest version-changes-with-content
  (testing "Version hash is stable for identical content and differs when content changes"
    (let [dir1 (temp-dir!)
          dir2 (temp-dir!)
          dir3 (temp-dir!)]
      (spit-file! dir1 "a.orcbrew" "{\"Pack\" {:x 1}}")
      (spit-file! dir2 "a.orcbrew" "{\"Pack\" {:x 1}}")
      (spit-file! dir3 "a.orcbrew" "{\"Pack\" {:x 2}}")
      (let [v1 (:version (config/read-site-homebrew (str dir1)))
            v2 (:version (config/read-site-homebrew (str dir2)))
            v3 (:version (config/read-site-homebrew (str dir3)))]
        (is (= v1 v2) "same content → same version")
        (is (not= v1 v3) "different content → different version")))))

(deftest skips-empty-files
  (testing "Empty .orcbrew files are dropped (not-empty), not returned as blank sources"
    (let [dir (temp-dir!)]
      (spit-file! dir "empty.orcbrew" "")
      (spit-file! dir "real.orcbrew" "{\"Pack\" {}}")
      (let [{:keys [sources]} (config/read-site-homebrew (str dir))]
        (is (= ["{\"Pack\" {}}"] sources))))))
