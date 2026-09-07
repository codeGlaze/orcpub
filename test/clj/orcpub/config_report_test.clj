(ns orcpub.config-report-test
  "The boot banner exists to answer one operator question: did my change take effect?
   These pin the three answers it can give -- set, default, and present-but-rejected --
   because getting the third wrong reports a default as though it were your value."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [orcpub.config :as config]))

(defn- lines [rows] (config/report-lines rows))
(defn- row-for [out v] (first (filter #(str/includes? % v) out)))

(def ^:private set-row
  {:var "ORCPUB_PDF_CONCURRENCY" :group "capacity" :value 24 :raw "24" :set? true
   :ignored? false :note "n"})
(def ^:private default-row
  {:var "ORCPUB_PDF_MAX_CARDS" :group "capacity" :value 200 :raw nil :set? false
   :ignored? false :note "n"})
(def ^:private ignored-row
  {:var "ORCPUB_PDF_MAX_RETRIES" :group "capacity" :value 3 :raw "oops" :set? false
   :ignored? true :note "n"})
(def ^:private unset-row
  {:var "ORCPUB_HTTP_MAX_THREADS" :group "capacity" :value nil :raw nil :set? false
   :ignored? false :note "n"})

(def ^:private secret-set
  {:var "SIGNATURE" :group "security" :value nil :secret? true :set? true :note "n"})
(def ^:private secret-missing
  {:var "SIGNATURE" :group "security" :value nil :secret? true :set? false
   :critical? true :note "every login fails without it"})

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
    (let [out (config/report-lines [unset-row] {"ORCPUB_HTTP_MAX_THREADS" 50})]
      (is (str/includes? (row-for out "MAX_THREADS") "50"))
      (is (str/includes? (row-for out "MAX_THREADS") "DEFAULT"))))
  (testing "and falls back to a dash when the server cannot be read"
    (let [out (config/report-lines [unset-row] nil)]
      (is (re-find #"\s-\s" (row-for out "MAX_THREADS"))))))

(deftest banner-is-plain-ascii
  (testing "no colour codes and no characters that come back as ? through a log pipe"
    (doseq [l (lines [set-row default-row ignored-row unset-row])]
      (is (every? #(< 31 (int %) 127) l) (str "non-ascii in: " l))))
  (testing "it says it started"
    (is (some #(str/includes? % "orcpub started") (lines [set-row]))))
  (testing "a row with no group does not kill the boot banner"
    (is (seq (lines [(dissoc set-row :group)])))))

(deftest secrets-never-print-their-value
  (testing "a secret reports presence only. This banner exists because a password reached a
            log; it must not become the next way one does."
    (let [out (lines [(assoc secret-set :value "hunter2") default-row])]
      (is (not-any? #(str/includes? % "hunter2") out) (pr-str out))
      (is (str/includes? (row-for out "SIGNATURE") "set"))))
  (testing "and an absent one says so plainly, with no DEFAULT beside it implying there is
            a default password"
    (let [out (lines [secret-missing default-row])
          r   (row-for out "SIGNATURE")]
      (is (str/includes? r "NOT SET") r)
      (is (not (str/includes? r "DEFAULT")) r))))

(deftest a-missing-critical-setting-is-called-out
  (testing "SIGNATURE absent breaks every login, so it gets a line of its own rather than
            being one row among thirty"
    (let [out (lines [secret-missing default-row])]
      (is (some #(and (str/includes? % "(!!)") (str/includes? % "SIGNATURE")) out)
          (pr-str out))))
  (testing "and no such line when it is present"
    (is (not-any? #(str/includes? % "(!!)") (lines [secret-set default-row])))))

(deftest rows-are-grouped
  (testing "thirty ungrouped rows is a wall, not a report"
    (let [out (lines [secret-set set-row])]
      (is (some #(str/includes? % "[SECURITY]") out))
      (is (some #(str/includes? % "[CAPACITY]") out)))))

(deftest every-documented-tunable-is-reported
  (testing "adding a knob without adding it to the banner fails here"
    (let [reported (set (map :var (config/report)))]
      (doseq [v ["ORCPUB_HTTP_MAX_THREADS" "ORCPUB_PDF_CONCURRENCY"
                 "ORCPUB_PDF_QUEUE_TIMEOUT_MS" "ORCPUB_PDF_MAX_RETRIES"
                 "ORCPUB_PDF_MAX_CASTER_SECTIONS" "ORCPUB_PDF_MAX_CARDS"
                 "PORT" "DEV_MODE" "DATOMIC_URL" "DATOMIC_PASSWORD" "SIGNATURE"
                 "CSP_POLICY" "EMAIL_FROM_ADDRESS" "EMAIL_SECRET_KEY" "LOAD_HOMEBREW_URL"]]
        (is (contains? reported v) (str v " is not in config/settings")))))
  (testing "no secret's value survives into the report data, not merely into the printing"
    (doseq [r (config/report)]
      (when (:secret? r)
        (is (nil? (:value r)) (str (:var r) " carries a value"))
        (is (nil? (:raw r)) (str (:var r) " carries the raw env value"))))))

(deftest columns-line-up
  (testing "values are RIGHT-aligned, so their ends line up, not their starts. A one-digit
            and a five-digit value must finish in the same column."
    (let [out  (lines [{:var "A" :group "g" :value 1 :set? true :note "n"}
                       {:var "BBBB" :group "g" :value 30000 :set? true :note "n"}])
          rows (filter #(re-find #"(^|\s)(1|30000)\s" %) out)
          start (fn [l] (str/index-of l (if (str/includes? l "30000") "30000" "1")))]
      (is (= 2 (count rows)) (pr-str rows))
      ;; VALUE is left-aligned now that it holds words like "NOT SET" as well as numbers,
      ;; so the starts line up rather than the ends.
      (is (apply = (map start rows)) (pr-str rows)))))

(deftest a-broken-signature-says-how-to-fix-it
  (testing "naming the symptom and leaving the remedy to be guessed just moves the work"
    (let [m config/signature-missing-message]
      (testing "why"
        (is (str/includes? m "every login"))
        (is (str/includes? m "JWT")))
      (testing "how"
        (is (str/includes? m "SIGNATURE environment variable"))
        (is (str/includes? m "/run/secrets/signature"))
        (is (str/includes? m "openssl rand"))
        (is (str/includes? m "restart")))
      (testing "and the consequence of changing it, which is the next thing they will do"
        (is (str/includes? m "signs out everyone")))))
  (testing "the banner carries the whole remedy, aligned as one block"
    (let [out (lines [(assoc secret-missing :fix config/signature-missing-message)])
          blk (drop-while #(not (str/includes? % "(!!)")) out)]
      (is (str/includes? (first blk) "every login"))
      (is (some #(str/includes? % "openssl rand") blk) (pr-str blk))
      (is (every? #(or (str/includes? % "(!!)") (str/starts-with? % "        ")
                       (str/starts-with? % "-"))
                  blk)
          (pr-str blk)))))
