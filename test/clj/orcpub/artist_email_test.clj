(ns orcpub.artist-email-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as s]
            [orcpub.artist-email :as ae])
  (:import [java.util Date]
           [java.time LocalDate ZoneOffset]))

(def expires (Date/from (.toInstant (.atStartOfDay (LocalDate/of 2026 10 6) ZoneOffset/UTC))))

(def created-event
  {:artist-id :house-pack
   :user {:orcpub.user/username "fusspot" :orcpub.user/email "hello@fusspot.rip"}
   :spec {:account "hello@fusspot.rip"
          :preferred-name "Fuss"
          :welcome-subject "We did it, Fuss! Your art is officially on OrcPub"
          :welcome-note "We did it! Your art is officially on OrcPub! Our inaugural character art! We can't thank you enough for helping get this off the ground. <3"}
   :reset-key "abc-123"
   :expires expires})

(defn- parts [msg]
  (let [[_ text html] (:body msg)] [(:content text) (:content html)]))

(deftest the-welcome-reads-like-the-mock-up
  (let [msg (ae/welcome-message "https://site.test" created-event)
        [text html] (parts msg)]
    (is (= "hello@fusspot.rip" (:to msg)))
    (is (= "We did it, Fuss! Your art is officially on OrcPub" (:subject msg)) "the artist's own subject")
    (testing "both parts carry the same things"
      (doseq [part [text html]]
        (is (s/includes? part "Hey Fuss!") "greets them by their preferred name")
        (is (s/includes? part "Our inaugural character art!") "their personal note")
        (is (s/includes? part "brand new account type, just for artists"))
        (is (s/includes? part "https://site.test/pages/reset-password-page?key=abc-123") "the set-password link")
        (is (s/includes? part "fusspot") "their username")
        (is (s/includes? part "6 October") "when the link expires")
        (is (s/includes? part "Forgot password") "what to do if it has")))
    (is (s/includes? text "<3") "the heart survives in plain text")
    (is (s/includes? html "&lt;3") "and is escaped in the HTML, not treated as a tag")
    (is (not (s/includes? html "<3")))))

(deftest a-welcome-without-a-note-still-reads-well
  (let [event (-> created-event (update :spec dissoc :welcome-note :welcome-subject :preferred-name))
        msg (ae/welcome-message "https://site.test" event)
        [text _] (parts msg)]
    (is (s/includes? text "Hey Fusspot!") "falls back to the credited name")
    (is (s/includes? text "Welcome aboard!") "and a default note")
    (is (s/includes? (:subject msg) "Your artist account is ready"))))

(deftest the-upgrade-has-no-password-step
  (let [msg (ae/upgrade-message "https://site.test"
                                (assoc created-event :user {:orcpub.user/username "fusspotart"
                                                            :orcpub.user/email "hello@fusspot.rip"}))
        [text html] (parts msg)]
    (is (s/includes? text "upgraded your account (fusspotart)"))
    (is (s/includes? text "https://site.test/pages/my-account#artist-credit") "links to their credit settings")
    (is (not (s/includes? html "set your password")))))

(deftest the-admin-copy-says-what-happened
  (with-redefs [ae/admin-recipient (constantly "admin@site.test")]
    (let [msg (ae/admin-message :created created-event)
          [text _] (parts msg)]
      (is (= "admin@site.test" (:to msg)))
      (is (= "New artist on board: Fusspot" (:subject msg)))
      (is (s/includes? text "hello@fusspot.rip"))
      (is (s/includes? text "expires 6 October"))
      (is (s/includes? text "\"account\": null") "and how to undo it"))))

(deftest nothing-is-sent-without-app-url
  (testing "a startup send has no request to build links from; without APP_URL
            the welcome is skipped and logged rather than sent with a broken link"
    (let [sent (atom [])]
      (with-redefs [ae/base-url (constantly nil)
                    postal.core/send-message (fn [_ m] (swap! sent conj m) {:error :SUCCESS})]
        ((:created ae/notify) created-event))
      (is (empty? @sent)))))

(deftest with-app-url-both-the-artist-and-the-admin-hear
  (let [sent (atom [])]
    (with-redefs [ae/base-url (constantly "https://site.test")
                  ae/admin-recipient (constantly "admin@site.test")
                  environ.core/env (assoc environ.core/env :email-server-url "smtp.test")
                  postal.core/send-message (fn [_ m] (swap! sent conj m) {:error :SUCCESS})]
      ((:created ae/notify) created-event))
    (is (= ["hello@fusspot.rip" "admin@site.test"] (map :to @sent)))))

(deftest a-credit-change-tells-the-artist-what-it-now-says
  (with-redefs [ae/admin-recipient (constantly "admin@site.test")]
    (let [event {:artist-id :house-pack
                 :user {:orcpub.user/username "fusspot" :orcpub.user/email "hello@fusspot.rip"}
                 :before nil
                 :after {:artist/name "Fuss"
                         :artist/links [{:link/label "Twitch" :link/url "https://twitch.tv/fusspot"}]}}
          [text _] (parts (ae/credit-changed-message "https://site.test" event))
          [admin _] (parts (ae/credit-admin-message event))]
      (is (s/includes? text "Name: Fuss"))
      (is (s/includes? text "Twitch: https://twitch.tv/fusspot"))
      (is (s/includes? text "If this wasn't you"))
      (is (s/includes? admin "Before:\n  Name: (default)"))
      (is (s/includes? admin "PORTRAIT_ARTISTS")))))
