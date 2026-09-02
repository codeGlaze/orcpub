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

;; ── Format versioning ───────────────────────────────────────────────────────

(def v1-content
  "Fully backward-compatible: a plain feat with only legacy fields."
  {"Pack" {:orcpub.dnd.e5/feats {:x {:name "X" :key :x :option-pack "Pack"}}}})

(def v2-grant
  {"Pack" {:orcpub.dnd.e5/feats {:g {:name "G" :key :g :option-pack "Pack"
                                     :grant {:from :fighting-styles :choose 1}}}}})

(def v2-spread
  {"Pack" {:orcpub.dnd.e5/feats {:s {:name "S" :key :s :option-pack "Pack"
                                     :ability-increases [[1 :con] [1 :any]]}}}})

(def v2-draconic
  {"Pack" {:orcpub.dnd.e5/draconic-ancestries
           {:d {:name "D" :key :d :option-pack "Pack"
                :breath-weapon {:damage-type :cold}}}}})

(deftest v1-content-is-plain-and-unstamped
  (testing "backward-compatible content classifies as v1 and stamp leaves it untouched"
    (is (= 1 (orcbrew-format/content-format-version v1-content)))
    (is (empty? (orcbrew-format/detect-incompatible-features v1-content)))
    (is (= v1-content (orcbrew-format/stamp v1-content)) "v1 stays plain — never gated")
    (is (not (orcbrew-format/envelope? (orcbrew-format/stamp v1-content))))))

(deftest incompatible-features-classify-as-v2
  (testing "any non-backward-compatible feature makes content v2"
    (doseq [[label c marker] [["grant" v2-grant :grant]
                              ["ability-increase spread" v2-spread :ability-increase-spread]
                              ["draconic ancestries type" v2-draconic :orcpub.dnd.e5/draconic-ancestries]]]
      (is (= 2 (orcbrew-format/content-format-version c)) (str label " is v2"))
      (is (contains? (orcbrew-format/detect-incompatible-features c) marker)
          (str label " is detected")))))

(deftest v2-stamp-round-trips-through-serialize
  (testing "a v2 stamp wraps, serializes, reads back, and unwraps to the original content"
    (let [stamped (orcbrew-format/stamp v2-spread)]
      (is (orcbrew-format/envelope? stamped))
      (is (= 2 (:orcbrew/format-version stamped)))
      (is (= [:ability-increase-spread] (:orcbrew/requires stamped)))
      (let [round (reader/read-string (orcbrew-format/serialize-orcbrew stamped))]
        (is (orcbrew-format/envelope? round))
        (is (= v2-spread (orcbrew-format/unwrap round)) "unwrap recovers the exact content"))
      (is (= stamped (orcbrew-format/stamp stamped)) "stamp is idempotent on an envelope"))))

(deftest compat-check-accepts-supported-and-refuses-newer
  (testing "a plain file and a supported v2 envelope import; a future version is refused"
    (is (:ok? (orcbrew-format/compat-check v1-content)) "plain file ok")
    (is (:ok? (orcbrew-format/compat-check (orcbrew-format/stamp v2-grant))) "v2 ok on a v2-capable build")
    (let [future {:orcbrew/format-version 99 :orcbrew/requires [:something] :orcbrew/content v1-content}
          {:keys [ok? message]} (orcbrew-format/compat-check future)]
      (is (not ok?) "a version beyond supported is refused")
      (is (re-find #"newer format" message) "with an actionable message"))))
