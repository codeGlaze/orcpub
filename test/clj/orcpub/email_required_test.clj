(ns orcpub.email-required-test
  "A deployed server will not start without email unless told it may: development mode, the
   explicit EMAIL_REQUIRED=false, or ALLOW_UNVERIFIED_REGISTRATION=true."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [orcpub.config :as config]
            [orcpub.env :as env]))

(defn- refusal
  "`config/start-refusal` with the env values in `vars` (keyword to string)."
  [vars]
  (with-redefs [env/value (fn ([k] (get vars k)) ([k d] (get vars k d)))
                env/flag? (fn [k] (= "true" (some-> (get vars k) str/lower-case)))]
    (config/start-refusal)))

(deftest a-deployed-server-needs-email
  (let [msg (refusal {})]
    (is (string? msg))
    (is (str/includes? msg "EMAIL_SERVER_URL") "it names the setting to add")
    (is (str/includes? msg "EMAIL_REQUIRED=false") "and the way to run without email")))

(deftest it-starts-when-email-is-set-or-not-needed
  (testing "email set" (is (nil? (refusal {:email-server-url "smtp.site.test"}))))
  (testing "development mode" (is (nil? (refusal {:dev-mode "true"}))))
  (testing "the explicit opt-out" (is (nil? (refusal {:email-required "false"}))))
  (testing "a private site without email checks" (is (nil? (refusal {:allow-unverified-registration "true"}))))
  (testing "a typo in the opt-out does not count as opting out"
    (is (string? (refusal {:email-required "flase"})))))
