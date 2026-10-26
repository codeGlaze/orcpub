(ns orcpub.crash-report-test
  "After a crash, the next start tells the admin once per crash file, says what happened, and finds
   the files where the JVM's own flags put them."
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.test :refer [deftest is]]
            [orcpub.alerts :as alerts]
            [orcpub.crash-report :as crash])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- temp-dir
  "A new empty directory."
  []
  (str (Files/createTempDirectory "crash-report-test" (make-array FileAttribute 0))))

(def ^:private crash-log
  "The head of a JVM crash log, as HotSpot writes it."
  (s/join "\n" ["#" "# A fatal error has been detected by the Java Runtime Environment:" "#"
                "#  SIGSEGV (0xb) at pc=0x00007f, pid=7, tid=9" "#"
                "# JRE version: OpenJDK Runtime Environment Temurin-21" "#" "" "---------------  S U M M A R Y ------------"]))

(defn- raised-during
  "The alerts [kind subject body] raised while `f` runs."
  [f]
  (let [raised (atom [])]
    (with-redefs [alerts/raise-current! (fn [k sub body] (swap! raised conj [k sub body]))]
      (f))
    @raised))

(deftest each-crash-is-reported-once
  (let [dir (temp-dir)]
    (spit (io/file dir "heap.hprof") "not really a heap dump")
    (spit (io/file dir "jvm-crash-7.log") crash-log)
    (let [first-start (raised-during #(crash/report! [dir]))]
      (is (= 2 (count first-start)) "one alert per crash file")
      (is (some #(re-find #"ran out of memory" (second %)) first-start))
      (is (some #(re-find #"Java process crashed" (second %)) first-start)))
    (is (empty? (raised-during #(crash/report! [dir]))) "not again on the next start")
    (spit (io/file dir "jvm-crash-8.log") crash-log)
    (is (= 1 (count (raised-during #(crash/report! [dir])))) "a new crash is reported")))

(deftest the-alert-says-what-happened
  (let [dir (temp-dir)
        f (io/file dir "jvm-crash-7.log")]
    (spit f crash-log)
    (let [{:keys [body]} (crash/alert-for f)]
      (is (s/includes? body "A fatal error has been detected") "the JVM's own summary is quoted")
      (is (s/includes? body (.getPath f)) "and where the file is"))
    (let [heap (io/file dir "heap.hprof")]
      (spit heap "x")
      (is (re-find #"keep it private" (:body (crash/alert-for heap)))
          "a heap dump holds player data; the alert says so"))))

(deftest nothing-to-report-raises-nothing
  (is (empty? (raised-during #(crash/report! [(temp-dir)])))))

(deftest the-folder-comes-from-the-jvm-flags
  (is (= {:heap-dump "/var/lib/orcpub/state/crash/heap.hprof"
          :error-file "/var/lib/orcpub/state/crash/jvm-crash-%p.log"}
         (crash/crash-paths ["-Xmx1g" "-XX:+HeapDumpOnOutOfMemoryError"
                             "-XX:HeapDumpPath=/var/lib/orcpub/state/crash/heap.hprof"
                             "-XX:ErrorFile=/var/lib/orcpub/state/crash/jvm-crash-%p.log"])))
  (is (= {:heap-dump nil :error-file nil} (crash/crash-paths ["-Xmx1g"]))))
