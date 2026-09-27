(ns orcpub.library-gate-test
  "Nothing writes the homebrew library except the gate (events/commit-library). A write anywhere
   else fails here. See docs/kb/homebrew-keys-design.md §4 and §7."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private write-patterns
  "Forms that write the library to app-db or to its storage slot."
  [#"\(plugins->local-store\s" #"\(assoc db :plugins" #"\(assoc-in db \[:plugins"
   #"\(update db :plugins" #"\(update-in db \[:plugins" #"\(dissoc db :plugins"
   #"\(assoc :plugins" #"\(path :plugins\)" #"set-item local-storage-plugins-key"])

(def ^:private markers
  "A write line carries one: the gate itself, the initial load, or a map that is not app-db."
  [";; library gate" ";; library load" ";; not app-db"])

(defn- source-lines []
  (for [root ["src" "web"]
        f (file-seq (io/file root))
        :when (re-find #"\.clj[cs]?$" (.getName f))
        [n line] (map-indexed vector (str/split-lines (slurp f)))
        :let [t (str/triml line)]
        :when (not (or (str/starts-with? t ";") (str/starts-with? t "#_")))]
    {:file (.getPath f) :line (inc n) :text line}))

(deftest only-the-gate-writes-the-library
  (let [writes (filter (fn [{:keys [text]}] (some #(re-find % text) write-patterns)) (source-lines))]
    (testing "every write is marked"
      (is (empty? (remove (fn [{:keys [text]}] (some #(str/includes? text %) markers)) writes))
          "a library write outside the gate: route it through events/commit-library"))
    (testing "and the gate is where it says"
      (is (every? #(re-find #"events\.cljs$|db\.cljs$" (:file %))
                  (filter #(str/includes? (:text %) ";; library gate") writes))))
    (testing "the scan finds the gate at all"
      (is (<= 3 (count (filter #(str/includes? (:text %) ";; library gate") writes)))))))
