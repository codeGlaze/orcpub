(ns orcpub.admin-address-test
  "Mail meant for the site's admin reaches an address whenever any of the admin settings is set:
   alerts and artist notices fall back to each other, then to the public support address."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.artist-email :as ae]
            [orcpub.config :as config]
            [orcpub.email :as email]
            [orcpub.env :as env]
            [orcpub.fork.branding :as branding]))

(defn- with-settings
  "Calls `f` with env values from `vars` (keyword to value) and APP_SUPPORT_EMAIL `support`."
  [vars support f]
  (with-redefs [env/value (fn ([k] (get vars k)) ([k d] (get vars k d)))
                branding/support-email (or support "")]
    (f)))

(deftest alerts-go-to-the-first-admin-address-set
  (testing "EMAIL_ERRORS_TO first"
    (is (= {:address "errors@site.test" :from "EMAIL_ERRORS_TO"}
           (with-settings {:email-errors-to "errors@site.test" :email-admin-to "admin@site.test"} "help@site.test"
             config/alert-address))))
  (testing "then EMAIL_ADMIN_TO"
    (is (= {:address "admin@site.test" :from "EMAIL_ADMIN_TO"}
           (with-settings {:email-admin-to "admin@site.test"} "help@site.test" config/alert-address))))
  (testing "then the public support address"
    (is (= {:address "help@site.test" :from "APP_SUPPORT_EMAIL"}
           (with-settings {} "help@site.test" config/alert-address))))
  (testing "nil when none is set"
    (is (nil? (with-settings {} nil config/alert-address)))))

(deftest artist-notices-prefer-the-admin-address
  (is (= "admin@site.test"
         (with-settings {:email-errors-to "errors@site.test" :email-admin-to "admin@site.test"} nil ae/admin-recipient)))
  (is (= "help@site.test" (with-settings {} "help@site.test" ae/admin-recipient))))

(deftest alerts-are-emailed-with-only-the-support-address-set
  (is (some? (with-settings {:email-server-url "smtp.site.test"} "help@site.test" email/alert-channel))
      "a mail server and APP_SUPPORT_EMAIL are enough")
  (is (nil? (with-settings {:email-server-url "smtp.site.test"} nil email/alert-channel))
      "no address at all: nothing to send to"))

(deftest the-report-names-where-admin-mail-goes
  (is (some #(= "Admin email: help@site.test (from APP_SUPPORT_EMAIL)" %)
            (config/deployment-lines {:admin {:address "help@site.test" :from "APP_SUPPORT_EMAIL"}})))
  (is (some #(re-find #"Admin email: nobody" %) (config/deployment-lines {:admin nil}))))
