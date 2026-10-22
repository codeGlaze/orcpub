(ns orcpub.dnd.e5.homebrew-shape-table-test
  "The shape table against real content and against junk. Real content must come through with
   nothing unreadable (loading is never stricter than saving); junk in any field must come out
   fitting the table, without a throw."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.edn :as edn]
            [orcpub.dnd.e5.homebrew-shapes :as shapes]))

(defn- fixture [f]
  (edn/read-string {:default tagged-literal} (slurp (str "test/fixtures/" f))))

(def ^:private sound
  ["test-pak.orcbrew" "coverage-pak.orcbrew" "sourced-classes.orcbrew" "junk-bases.orcbrew"])

(defn- library [data]
  ;; a file holds one source or a {source-name source} library
  (if (every? string? (keys data)) data {"Fixture" data}))

(deftest real-content-has-nothing-unreadable
  (doseq [f sound]
    (let [lib (library (fixture f))
          {:keys [findings]} (shapes/clean-library lib)
          unreadable (filter #(= :unreadable (:action %)) findings)]
      (is (empty? unreadable) (str f ": " (pr-str (take 5 unreadable)))))))

(deftest real-content-comes-back-as-it-was
  (testing "a library with nothing to fix is the identical object, so nothing downstream recomputes"
    (let [lib (library (fixture "coverage-pak.orcbrew"))]
      (is (identical? lib (:library (shapes/clean-library lib))))))
  (testing "what the table fixes in real content, listed so a change to it is seen"
    (let [fixes (for [f sound
                      x (:findings (shapes/clean-library (library (fixture f))))
                      :when (= :fixed (:action x))]
                  [f (:content-type x) (:path x) (:found x)])]
      (is (= #{["sourced-classes.orcbrew" :orcpub.dnd.e5/races [:size] "text"]}
             (set fixes))
          (pr-str (distinct fixes))))))

(def ^:private junk [nil 42 "x" true :kw [] [42] ["x"] [{}] {} {:a 1} #{42} (list 42) 1.5])

(defn- field-paths
  "Each field of `entry`, and each field one level inside a map or a list of maps."
  [entry]
  (concat (for [k (keys entry)] [k])
          (for [[k v] entry
                p (cond (map? v) (for [k2 (keys v)] [k k2])
                        (and (sequential? v) (map? (first v))) (for [k2 (keys (first v))] [k 0 k2])
                        :else nil)]
            p)))

(deftest junk-in-any-field-comes-out-fitting
  (let [base-entries (get (fixture "junk-bases.orcbrew") "Junk Pak")
        failures (for [[ct {base :junk-base}] base-entries
                       path (field-paths base)
                       v junk
                       :let [entry (assoc-in base path v)
                             r (try (shapes/clean-entry ct entry)
                                    (catch Throwable e {:threw (.getMessage e)}))]
                       :when (or (:threw r)
                                 (not (shapes/fits? (get shapes/shapes ct) (:entry r))))]
                   [ct path v (or (:threw r) "does not fit")])]
    (is (empty? failures) (pr-str (take 10 failures)))))

(deftest a-section-that-is-not-a-map
  (let [clean #(shapes/clean-library {"P" {:orcpub.dnd.e5/races %}})]
    (testing "true or false, which the format allows, reads as empty"
      (is (= {} (get-in (clean true) [:library "P" :orcpub.dnd.e5/races])))
      (is (empty? (:findings (clean false)))))
    (testing "anything else cannot be read, and is named"
      (doseq [v [42 "x" [] [42] #{42}]]
        (let [{lib :library findings :findings} (clean v)]
          (is (not (contains? (get lib "P") :orcpub.dnd.e5/races)) (pr-str v))
          (is (= [{:source "P" :content-type :orcpub.dnd.e5/races :path [] :action :unreadable}]
                 (map #(select-keys % [:source :content-type :path :action]) findings))
              (pr-str v)))))))

(deftest damaged-content-never-throws
  (doseq [f ["cruft-shapes.orcbrew" "broken-content.orcbrew" "keyword-trap.orcbrew"]]
    (let [r (shapes/clean-library (library (fixture f)))]
      (is (map? (:library r)) f))))

(deftest only-what-changed-is-walked
  (let [junk-race {:name "Junk" :abilities 42}
        before {"P" {:orcpub.dnd.e5/races {:a junk-race}}}]
    (testing "an entry identical to the one before is not walked again"
      (is (empty? (:findings (shapes/clean-library before before)))))
    (testing "a changed one is"
      (let [after (assoc-in before ["P" :orcpub.dnd.e5/races :b] junk-race)]
        (is (= [:b] (map :key (:findings (shapes/clean-library after before)))))))))

(deftest what-cannot-be-read-is-split-out-as-written
  (let [bad {:name "Bad" :abilities 42}
        lib {"P" {:orcpub.dnd.e5/races {:bad bad :ok {:name "Ok"}}
                  :orcpub.dnd.e5/feats 42}}
        {cleaned :library findings :findings} (shapes/clean-library lib)
        {kept :library set-aside :set-aside} (shapes/split-unreadable cleaned lib findings)]
    (is (= {"P" {:orcpub.dnd.e5/races {:ok {:name "Ok"}}}} kept))
    (is (= {"P" {:orcpub.dnd.e5/races {:bad bad} :orcpub.dnd.e5/feats 42}} set-aside))))
