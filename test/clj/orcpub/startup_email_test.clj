(ns orcpub.startup-email-test
  "The startup email: what goes in it (problems, then this release's settings, then the ones worth
   setting), when it is sent (once per release, again when its list changes, one reminder for
   problems), and that its memory file keeps names, never values."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [orcpub.config :as config]
            [orcpub.startup-email :as se]))

(defn- row
  "A `config/report` row for setting `v` with `opts` merged in."
  [v & {:as opts}]
  (merge {:var v :group "g" :since "2.5" :set? false :ignored? false :value nil :note "n"} opts))

(def ^:private rows
  [(row "SIGNATURE" :secret? true :set? true)
   (row "EMAIL_SERVER_URL")
   (row "APP_URL")
   (row "PROFILE_ENCRYPTION_KEYS" :secret? true :since config/current-release)
   (row "TRUST_CLOUDFLARE" :since config/current-release :value "false")
   (row "ORCPUB_PDF_CONCURRENCY" :value 24)
   (row "ALERT_MAX_PER_HOUR" :since config/current-release :raw "lots" :ignored? true :value 20)])

(deftest every-setting-has-a-class
  (is (= (set (map :var config/settings)) (set (keys config/setting-classes)))
      "a new setting is given a class when it is added")
  (is (every? #{:required :recommended :tuning} (vals config/setting-classes))))

(deftest the-summary-sorts-settings-by-what-they-need
  (let [s (se/summary rows {:sender-ok? true :deployment-problems []})]
    (testing "problems: a required setting missing, a value that was ignored"
      (is (= #{"missing:EMAIL_SERVER_URL" "ignored:ALERT_MAX_PER_HOUR"} (set (map :key (:problems s))))))
    (testing "this release's settings, set or not"
      (is (= ["PROFILE_ENCRYPTION_KEYS" "TRUST_CLOUDFLARE" "ALERT_MAX_PER_HOUR"] (map :var (:new s)))))
    (testing "worth setting: recommended and unset, not already listed as new"
      (is (= ["APP_URL"] (map :var (:worth s)))))
    (testing "tuning settings left at their defaults are only counted"
      (is (= 1 (:tuning-defaults s))))))

(deftest a-sender-that-cannot-deliver-is-a-problem
  (is (some #(= "missing:EMAIL_FROM_ADDRESS" (:key %))
            (:problems (se/summary [] {:sender-ok? false :deployment-problems []}))))
  (is (= 1 (count (filter #(= "missing:EMAIL_FROM_ADDRESS" (:key %))
                          (:problems (se/summary [(row "EMAIL_FROM_ADDRESS")]
                                                 {:sender-ok? false :deployment-problems []})))))
      "an unset From address is one problem, not two"))

(def ^:private day (* 24 60 60 1000))

(deftest when-it-is-sent
  (let [s     (se/summary rows {:sender-ok? true :deployment-problems []})
        sent  (se/memory s config/current-release 0)]
    (testing "the first start on a release" (is (= :first (se/decide nil s config/current-release day))))
    (testing "nothing new on the next start" (is (nil? (se/decide sent s config/current-release day))))
    (testing "a changed list"
      (let [s2 (se/summary (conj rows (row "DATOMIC_PASSWORD" :secret? true)) {:sender-ok? true :deployment-problems []})]
        (is (= :changed (se/decide sent s2 config/current-release day)))))
    (testing "one reminder after 7 days while a problem remains, then none"
      (is (= :reminder (se/decide sent s config/current-release (* 7 day))))
      (is (nil? (se/decide (assoc sent :reminded? true) s config/current-release (* 8 day)))))
    (testing "no reminder when nothing is wrong"
      (let [ok (se/summary [(row "APP_URL")] {:sender-ok? true :deployment-problems []})]
        (is (nil? (se/decide (se/memory ok config/current-release 0) ok config/current-release (* 9 day))))))
    (testing "nothing to say: no email"
      (is (nil? (se/decide nil {:problems [] :new [] :worth [] :tuning-defaults 3} config/current-release 0))))))

(deftest the-email-reads-in-order-and-shows-no-secret
  (let [secret-row (row "DATOMIC_PASSWORD" :secret? true :set? true :raw "hunter2" :value "hunter2")
        {:keys [subject body]} (se/email (se/summary (conj rows secret-row)
                                                     {:sender-ok? true
                                                      :deployment-problems [{:key "deploy:crash-inside"
                                                                             :text "Crash files are inside the container."}]})
                                         {:app-name "Test Vault" :release config/current-release
                                          :admin "Admin email: help@site.test (from APP_SUPPORT_EMAIL)"})]
    (is (str/includes? subject "Test Vault") subject)
    (is (str/includes? subject "3 problems") subject)
    (let [at #(str/index-of body %)]
      (is (< (at "NEEDS FIXING") (at "NEW IN") (at "WORTH SETTING") (at "DEPLOYMENT")) body))
    (is (str/includes? body "Crash files are inside the container."))
    (is (str/includes? body "ALERT_MAX_PER_HOUR=lots was ignored"))
    (is (not (str/includes? body "hunter2")) "never a secret's value")
    (is (str/includes? body "1 tuning setting at its default"))
    (testing "new settings whose defaults suit nearly everyone: one per line, with their note, under their area"
      (let [{body2 :body} (se/email (se/summary [(row "ORCPUB_PWNED_CHECK" :group "runtime" :since config/current-release
                                                      :value "on" :note "checks passwords")
                                                 (row "ORCPUB_PDF_MAX_CARDS" :group "capacity" :since config/current-release
                                                      :value 198 :note "cards per export")]
                                                {:sender-ok? true :deployment-problems []})
                                    {:app-name "V" :release config/current-release})
            lines (str/split-lines body2)]
        (is (some #(re-matches #"      ORCPUB_PWNED_CHECK +checks passwords" %) lines) body2)
        (is (some #(= "    Runtime" %) lines) body2)
        (is (some #(= "    Capacity" %) lines) body2)
        (is (not-any? #(re-find #"ORCPUB_PWNED_CHECK.*ORCPUB_PDF_MAX_CARDS" %) lines) "one name per line")))))

(deftest the-memory-keeps-names-not-values
  (let [s (se/summary [(row "EMAIL_SERVER_URL" :raw "smtp.secret.test" :value "smtp.secret.test" :set? true)
                       (row "APP_URL")]
                      {:sender-ok? true :deployment-problems []})
        m (pr-str (se/memory s config/current-release 0))]
    (is (str/includes? m "APP_URL"))
    (is (not (str/includes? m "smtp.secret.test")) m)))
