(ns orcpub.dnd.e5.orcbrew-format-test
  "Round-trip tests for the shared orcbrew serializer. Written in cljc so they run
   on the JVM as well — the build-time demo emitter serializes there, so JVM
   serialization has to produce readable EDN, not just the browser's."
  (:require #?(:clj  [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])
            #?(:clj  [clojure.edn :as reader]
               :cljs [cljs.reader :as reader])
            [orcpub.dnd.e5.orcbrew-format :as orcbrew-format]))

(def sample-content
  {:orcpub.dnd.e5/classes {:artificer {:name "Artificer" :option-pack "Pack"}}})

(deftest serialize-compact-roundtrips
  (testing "compact output is readable EDN that round-trips to the same data"
    (let [s (orcbrew-format/serialize-orcbrew sample-content)]
      (is (string? s))
      (is (= sample-content (reader/read-string s))))))

(deftest serialize-pretty-differs-but-same-data
  (testing "pretty-print is multi-line and larger, but the same data round-trips"
    (let [compact (orcbrew-format/serialize-orcbrew sample-content)
          pretty  (orcbrew-format/serialize-orcbrew sample-content :pretty-print? true)]
      (is (not= compact pretty))
      (is (re-find #"\n" pretty) "pretty output spans multiple lines")
      (is (= sample-content (reader/read-string pretty))))))
