(ns orcpub.email
  (:require [hiccup.core :as hiccup]
            [postal.core :as postal]
            [environ.core :as environ]
            [clojure.pprint :as pprint]
            [clojure.string :as s]
            [orcpub.route-map :as routes]
            [cuerdas.core :as str]))

(defn verification-email-html [first-and-last-name username verification-url]
  [:div
   (str "Dear Dungeon Master's Vault Patron,")
   [:br]
   [:br]
   "Your Dungeon Master's Vault account is almost ready, we just need you to verify your email address going the following URL to confirm that you are authorized to use this email address:"
   [:br]
   [:br]
   [:a {:href verification-url} verification-url]
   [:br]
   [:br]
   "Sincerely,"
   [:br]
   [:br]
   "The Dungeon Master's Vault Team"])

(defn verification-email [first-and-last-name username verification-url]
  [{:type "text/html"
    :content (hiccup/html (verification-email-html first-and-last-name username verification-url))}])

(defn email-change-verification-html
  "Email body for existing users changing their email (distinct from registration)."
  [username verification-url]
  [:div
   "Dear Dungeon Master's Vault Patron,"
   [:br]
   [:br]
   "You requested to change the email address on your account (" username "). "
   "Please visit the following URL to confirm this change:"
   [:br]
   [:br]
   [:a {:href verification-url} verification-url]
   [:br]
   [:br]
   "If you did not request this change, you can safely ignore this email."
   [:br]
   [:br]
   "Sincerely,"
   [:br]
   [:br]
   "The Dungeon Master's Vault Team"])

(defn email-change-verification-email [username verification-url]
  [{:type "text/html"
    :content (hiccup/html (email-change-verification-html username verification-url))}])

(defn email-cfg []
  (let [cfg {:user (environ/env :email-access-key)
             :pass (environ/env :email-secret-key)
             :host (environ/env :email-server-url)
             :port (Integer/parseInt (or (environ/env :email-server-port) "587"))
             :ssl  (or (str/to-bool (environ/env :email-ssl)) nil)
             :tls  (or (str/to-bool (environ/env :email-tls)) nil)}]
    cfg))

(defn emailfrom []
  (if (not (s/blank? (environ/env :email-from-address))) (environ/env :email-from-address) (str "no-reply@dungeonmastersvault.com")))

(defn send-verification-email [base-url {:keys [email username first-and-last-name]} verification-key]
  (postal/send-message (email-cfg)
                       {:from (str "Dungeon Master's Vault Team <" (emailfrom) ">")
                        :to email
                        :subject "Dungeon Master's Vault Email Verification"
                        :body (verification-email
                               first-and-last-name
                               username
                               (str base-url (routes/path-for routes/verify-route) "?key=" verification-key))}))

(defn send-email-change-verification
  "Send a verification email for an email-change request (not registration)."
  [base-url {:keys [email username]} verification-key]
  (postal/send-message (email-cfg)
                       {:from (str "Dungeon Master's Vault Team <" (emailfrom) ">")
                        :to email
                        :subject "Dungeon Master's Vault Email Change Verification"
                        :body (email-change-verification-email
                               username
                               (str base-url (routes/path-for routes/verify-route) "?key=" verification-key))}))

(defn reset-password-email-html [first-and-last-name reset-url]
  [:div
   (str "Dear Dungeon Master's Vault Patron")
   [:br]
   [:br]
   "We received a request to reset your password, to do so please go to the following URL to complete the reset."
   [:br]
   [:br]
   [:a {:href reset-url} reset-url]
   [:br]
   [:br]
   "If you did NOT request a reset, please do no click on the link."
   [:br]
   [:br]
   "Sincerely,"
   [:br]
   [:br]
   "The Dungeon Master's Vault Team"])

(defn reset-password-email [first-and-last-name reset-url]
  [{:type "text/html"
    :content (hiccup/html (reset-password-email-html first-and-last-name reset-url))}])

(defn send-reset-email [base-url {:keys [email username first-and-last-name]} reset-key]
  (postal/send-message (email-cfg)
                       {:from (str "Dungeon Master's Vault Team <" (emailfrom) ">")
                        :to email
                        :subject "Dungeon Master's Vault Password Reset"
                        :body (reset-password-email
                                first-and-last-name
                                (str base-url (routes/path-for routes/reset-password-page-route) "?key=" reset-key))}))

(defn send-error-email [context exception]
  (if (not-empty (environ/env :email-errors-to))
    (postal/send-message (email-cfg)
                         {:from (str "Dungeon Master's Vault Errors <" (emailfrom) ">")
                          :to (str (environ/env :email-errors-to))
                          :subject "Exception"
                          :body [{:type "text/plain"
                                  :content (let [writer (java.io.StringWriter.)]
                                             (clojure.pprint/pprint (:request context) writer)
                                             (clojure.pprint/pprint (or (ex-data exception) exception) writer)
                                             (str writer))}]})))

