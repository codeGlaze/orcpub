(ns orcpub.config-report-test
  "The boot banner exists to answer one operator question: did my change take effect?
   These pin the three answers it can give -- set, default, and present-but-rejected --
   because getting the third wrong reports a default as though it were your value."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [orcpub.config :as config]))

(defn- lines [rows] (config/report-lines rows "datomic:mem://orcpub"))
(defn- row-for [out v] (first (filter #(str/includes? % v) out)))

(def ^:private set-row
  {:var "ORCPUB_PDF_CONCURRENCY" :value 24 :raw "24" :set? true :ignored? false :note "n"})
(def ^:private default-row
  {:var "ORCPUB_PDF_MAX_CARDS" :value 200 :raw nil :set? false :ignored? false :note "n"})
(def ^:private ignored-row
  {:var "ORCPUB_PDF_MAX_RETRIES" :value 3 :raw "oops" :set? false :ignored? true :note "n"})
(def ^:private unset-row
  {:var "ORCPUB_HTTP_MAX_THREADS" :value nil :raw nil :set? false :ignored? false
   :unset-source "Pedestal's" :note "n"})

(deftest says-where-each-value-came-from
  (let [out (lines [set-row default-row ignored-row unset-row])]
    (testing "a value from the environment"
      (is (str/includes? (row-for out "CONCURRENCY") "24"))
      (is (str/includes? (row-for out "CONCURRENCY") "SET")))
    (testing "a value the code chose"
      (is (str/includes? (row-for out "MAX_CARDS") "DEFAULT")))
    (testing "present but rejected reports DEFAULT, because the number shown IS the
              default. An earlier version labelled that row IGNORED, which reads as 'the
              default was ignored' -- backwards. It was the supplied value that was ignored,
              and the (!) line below the table says so."
      (let [r (row-for out "MAX_RETRIES")]
        (is (str/includes? r "DEFAULT") r)
        (is (not (str/includes? r "SET")) r)))
    (testing "and the rejected value is named underneath, so it can be fixed"
      (is (some #(and (str/includes? % "(!)") (str/includes? % "oops")
                      (str/includes? % "ignored")) out)))
    (testing "with no number of our own the value column shows a dash, not the word
              'unset' -- next to a SOURCE of DEFAULT that read as a contradiction"
      (is (re-find #"\s-\s" (row-for out "MAX_THREADS")) (row-for out "MAX_THREADS")))))

(deftest headers-name-the-columns
  (testing "VALUE and SOURCE are labelled, so neither can be read as commenting on the other"
    (let [out (lines [set-row])]
      (is (some #(and (str/includes? % "SETTING") (str/includes? % "VALUE")
                      (str/includes? % "SOURCE")) out)))))

(deftest reports-the-running-thread-pool-when-we-did-not-set-it
  (testing "ORCPUB_HTTP_MAX_THREADS unset means Pedestal chose, so the banner shows the size
            read back off the live server rather than a formula copied from Pedestal"
    (let [out (config/report-lines [unset-row] "datomic:mem://orcpub"
                                   {"ORCPUB_HTTP_MAX_THREADS" 50})]
      (is (str/includes? (row-for out "MAX_THREADS") "50"))
      (is (str/includes? (row-for out "MAX_THREADS") "DEFAULT"))))
  (testing "and falls back to a dash when the server cannot be read"
    (let [out (config/report-lines [unset-row] "datomic:mem://orcpub" nil)]
      (is (re-find #"\s-\s" (row-for out "MAX_THREADS"))))))

(deftest banner-is-plain-ascii
  (testing "no colour codes and no characters that come back as ? through a log pipe"
    (doseq [l (lines [set-row default-row ignored-row unset-row])]
      (is (every? #(< 31 (int %) 127) l) (str "non-ascii in: " l))))
  (testing "it says it started, and shows the database"
    (let [out (lines [set-row])]
      (is (some #(str/includes? % "orcpub started") out))
      (is (some #(str/includes? % "datomic:mem://orcpub") out)))))

(deftest the-database-line-is-redacted
  (testing "the banner is exactly where a credential would leak next"
    (let [out (config/report-lines
               [set-row]
               "datomic:sql://datomic?jdbc:postgresql://h:5432/d?user=u&password=hunter2")]
      (is (not-any? #(str/includes? % "hunter2") out) (pr-str out))
      (is (some #(str/includes? % "password=****") out)))))

(deftest every-documented-tunable-is-reported
  (testing "adding a knob without adding it to the banner fails here"
    (let [reported (set (map :var (config/report)))]
      (doseq [v ["ORCPUB_HTTP_MAX_THREADS" "ORCPUB_PDF_CONCURRENCY"
                 "ORCPUB_PDF_QUEUE_TIMEOUT_MS" "ORCPUB_PDF_MAX_RETRIES"
                 "ORCPUB_PDF_MAX_CASTER_SECTIONS" "ORCPUB_PDF_MAX_CARDS"]]
        (is (contains? reported v) (str v " is not in config/tunables")))))
  (testing "report reads real config, so every row has a value or a reason"
    (doseq [r (config/report)]
      (is (or (:value r) (:unset-source r))
          (str (:var r) " reports neither a value nor who decides")))))

(deftest columns-line-up
  (testing "values are RIGHT-aligned, so their ends line up, not their starts. A one-digit
            and a five-digit value must finish in the same column."
    (let [out  (lines [{:var "A" :value 1 :set? true :note "n"}
                       {:var "BBBB" :value 30000 :set? true :note "n"}])
          rows (filter #(re-find #"(^|\s)(1|30000)\s" %) out)
          end  (fn [l] (let [v (if (str/includes? l "30000") "30000" "1")]
                         (+ (str/index-of l v) (count v))))]
      (is (= 2 (count rows)) (pr-str rows))
      (is (apply = (map end rows)) (pr-str rows)))))
