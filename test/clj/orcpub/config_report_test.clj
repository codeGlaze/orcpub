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
  {:var "ORCPUB_PDF_MAX_CARDS" :group "capacity" :value 198 :raw nil :set? false
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

(deftest a-rejected-share-prune-window-is-called-out
  (testing "the prune window takes 0, so it has its own check, and the line says what it wanted"
    (let [row (fn [raw]
                (with-redefs-fn {#'config/env-raw (fn [n] (when (= n "ORCPUB_SHARE_PRUNE_DAYS") raw))}
                  #(first (filter (comp #{"ORCPUB_SHARE_PRUNE_DAYS"} :var) (config/report)))))
          bad (row "oops")]
      (is (:ignored? bad))
      (is (not (:set? bad)) "a typo does not read as SET")
      (is (some #(str/includes? % "ORCPUB_SHARE_PRUNE_DAYS=oops was ignored: not a whole number of days, 0 or more")
                (lines [bad])))
      (is (:ignored? (row "-5")))
      (is (:set? (row "0")) "0 keeps links, a choice rather than a typo")
      (is (:set? (row "45"))))))

(deftest a-portrait-store-path-reads-as-set
  (let [row (with-redefs-fn {#'config/env-raw (fn [n] (when (= n "PORTRAIT_STORE_DIR") "/data/portraits"))}
              #(first (filter (comp #{"PORTRAIT_STORE_DIR"} :var) (config/report))))]
    (is (:set? row) "a directory path is what the setting takes")
    (is (not (:ignored? row)))
    (is (not-any? #(str/includes? % "was ignored") (lines [row])))))

(deftest every-documented-tunable-is-reported
  (testing "adding a knob without adding it to the banner fails here"
    (let [reported (set (map :var (config/report)))]
      (doseq [v ["ORCPUB_HTTP_MAX_THREADS" "ORCPUB_PDF_CONCURRENCY"
                 "ORCPUB_PDF_QUEUE_TIMEOUT_MS" "ORCPUB_PDF_MAX_RETRIES"
                 "ORCPUB_PDF_MAX_CASTER_SECTIONS" "ORCPUB_PDF_MAX_CARDS"
                 "PORT" "DEV_MODE" "DATOMIC_URL" "DATOMIC_PASSWORD" "SIGNATURE"
                 "CSP_POLICY" "EMAIL_FROM_ADDRESS" "EMAIL_SECRET_KEY" "LOAD_HOMEBREW_URL"
                 "ORCPUB_SHARE_MAX_UPLOAD_KB" "ORCPUB_SHARE_MAX_TEXT_KB" "ORCPUB_SHARE_MAX_ACCOUNT_KB"
                 "ORCPUB_SHARE_PRUNE_DAYS"]]
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

(deftest critical-settings-are-the-ones-that-actually-break-the-site
  (testing "SIGNATURE: no login, signup or authenticated call works"
    (is (some #(and (= "SIGNATURE" (:var %)) (:critical? %)) config/settings)))
  (testing "EMAIL_SERVER_URL: send-verification-email runs INSIDE the signup transaction,
            so unconfigured mail does not merely skip the email -- registration throws and
            tells the user to try again, which never works. Password reset fails too."
    (is (some #(and (= "EMAIL_SERVER_URL" (:var %)) (:critical? %)) config/settings)))
  (testing "and the ones that degrade rather than break are NOT marked, so the (!!) lines
            keep meaning something"
    (doseq [v ["PORT" "CSP_POLICY" "DEV_MODE" "LOAD_HOMEBREW_URL" "EMAIL_ERRORS_TO"
               "ORCPUB_PDF_CONCURRENCY"]]
      (is (not (some #(and (= v (:var %)) (:critical? %)) config/settings))
          (str v " is marked critical but has a working default"))))
  (testing "every critical setting explains how to fix it, not just what broke"
    (doseq [{env-var :var :keys [fix]} (filter :critical? config/settings)]
      (is (some? fix) (str env-var " is critical with no remedy"))
      (is (str/includes? fix "Fix:") (str env-var "'s message has no Fix: line")))))

(deftest the-card-cap-is-whole-sheets
  (testing "nine cards to a sheet, so a cap that is not a multiple of nine leaves a ragged
            last page for no reason. 200 was 22 sheets plus two cards on a twenty-third."
    (is (zero? (mod (config/get-pdf-max-cards) config/cards-per-page))
        (str (config/get-pdf-max-cards) " cards is "
             (/ (config/get-pdf-max-cards) (double config/cards-per-page)) " sheets")))
  (testing "and still far past any real character -- a level 20 wizard's spellbook is ~44"
    (is (> (config/get-pdf-max-cards) 150)))
  (testing "cards-per-page matches what pdf.clj derives: (int (/ 8.5 2.5)) by
            (int (/ 11.0 3.5)), the box size the card call sites pass"
    (is (= config/cards-per-page (* (int (/ 8.5 2.5)) (int (/ 11.0 3.5)))))))

(deftest every-setting-says-which-release-added-it
  (testing "the end-of-boot block lists what this release added, so every row names its release"
    (is (empty? (remove :since config/settings))
        (str "rows with no :since: " (pr-str (map :var (remove :since config/settings)))))))

(def ^:private new-unset
  {:var "ORCPUB_NEW_UNSET" :group "email" :value nil :raw nil :set? false :note "the new one"
   :since config/current-release})
(def ^:private new-set
  (assoc new-unset :var "ORCPUB_NEW_SET" :raw "x" :value "x" :set? true))
(def ^:private old-unset
  (assoc new-unset :var "ORCPUB_OLD_UNSET" :since "2.5"))

(deftest new-settings-left-unset-end-the-report
  (let [out (vec (lines [secret-missing new-unset new-set old-unset]))
        tail (drop-while #(not (str/includes? % "NEW IN")) out)]
    (testing "a block headed with this release closes the report, after the critical ones"
      (is (seq tail) (pr-str out))
      (is (str/includes? (first tail) (str/upper-case config/current-release)) (first tail))
      (is (not-any? #(str/includes? % "(!!)") tail) "nothing critical prints after it"))
    (testing "it names the new settings still unset, with what they do"
      (is (some #(and (str/includes? % "ORCPUB_NEW_UNSET") (str/includes? % "the new one")) tail)))
    (testing "and not the ones already set, or older ones"
      (is (not-any? #(str/includes? % "ORCPUB_NEW_SET") tail))
      (is (not-any? #(str/includes? % "ORCPUB_OLD_UNSET") tail)))))

(deftest no-block-when-every-new-setting-is-set
  (is (not-any? #(str/includes? % "NEW IN") (lines [new-set old-unset]))))

(deftest every-branding-setting-is-counted
  (testing "each APP_* name branding.clj reads is in branding-vars, so the count covers it"
    (let [src (slurp "src/clj/orcpub/fork/branding.clj")
          read-names (->> (re-seq #":app-[a-z-]+" src)
                          (map #(-> % (subs 1) (str/replace "-" "_") str/upper-case))
                          set)
          socials (->> (re-find #"\[:patreon[^\]]*\]" src) (re-seq #":([a-z]+)")
                       (map #(str "APP_SOCIAL_" (str/upper-case (second %)))))]
      (is (empty? (remove (set config/branding-vars) (concat read-names socials)))
          (pr-str (remove (set config/branding-vars) (concat read-names socials)))))))

(deftest a-rejected-branding-value-is-named
  (testing "a branding value that fell back to its default says so in the report, not only in the log"
    (let [out (config/report-lines [set-row] nil [{:var "APP_HELP_URL" :value "help me" :why "not a web address or site path"}])]
      (is (some #(and (str/includes? % "(!)") (str/includes? % "APP_HELP_URL")
                      (str/includes? % "help me") (str/includes? % "not a web address")) out)
          (pr-str out)))))

(deftest the-report-says-where-crash-files-land
  (testing "a folder linked to the host's /var/log is named as the host path"
    (let [out (config/deployment-lines {:crash-dir "/host-log/dmv/crash" :alert-dir "/host-log/dmv/alerts"})]
      (is (some #(str/includes? % "/var/log/dmv/crash on the host") out) (pr-str out))
      (is (not-any? #(str/includes? % "inside the container") out))))
  (testing "a folder inside the container says so, with the mount that moves it"
    (let [out (config/deployment-lines {:crash-dir "/var/lib/orcpub/state/crash" :alert-dir "/var/lib/orcpub/state/alerts"})]
      (is (some #(str/includes? % "inside the container") out) (pr-str out))
      (is (some #(str/includes? % "/var/log:/host-log") out))))
  (testing "bare metal: the folders as they are, and no mount advice"
    (let [out (config/deployment-lines {:crash-dir "/srv/orcpub/logs/app/crash" :alert-dir "/tmp/orcpub-alerts"})]
      (is (some #(str/includes? % "Crash files: /srv/orcpub/logs/app/crash") out) (pr-str out))
      (is (not-any? #(str/includes? % "/host-log") out))))
  (testing "the memory Java may use is shown, with how to change it"
    (let [out (config/deployment-lines {:max-heap-bytes (* 1536 1024 1024)})]
      (is (some #(and (str/includes? % "Java memory limit: 1536 MB") (str/includes? % "JAVA_OPTS")) out)
          (pr-str out))))
  (testing "the release's deployment notes come with them, and the block is in the report"
    (let [out (config/report-lines [set-row] nil nil (config/deployment-lines {}))]
      (is (some #(str/includes? % "TRANSACTOR_HOST") out) (pr-str out))
      (is (some #(str/includes? % "DEPLOYMENT") out)))))
