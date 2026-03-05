(ns orcpub.email
  "Email sending functionality with error handling.

  Provides functions for sending verification emails, password reset emails,
  and error notification emails. All operations include comprehensive error
  handling to prevent silent failures when the SMTP server is unavailable."
  (:require [hiccup2.core :as hiccup]
            [postal.core :as postal]
            [environ.core :as environ]
            [clojure.pprint :as pprint]
            [clojure.string :as s]
            [orcpub.route-map :as routes]
            [cuerdas.core :as str]))

(defn verification-email-html [first-and-last-name username verification-url]
  [:div
   "Dear Dungeon Master's Vault Patron,"
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
    :content (str (hiccup/html (verification-email-html first-and-last-name username verification-url)))}])

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
    :content (str (hiccup/html (email-change-verification-html username verification-url)))}])

(defn email-cfg []
  (try
    {:user (environ/env :email-access-key)
     :pass (environ/env :email-secret-key)
     :host (environ/env :email-server-url)
     :port (Integer/parseInt (or (environ/env :email-server-port) "587"))
     :ssl (or (str/to-bool (environ/env :email-ssl)) nil)
     :tls (or (str/to-bool (environ/env :email-tls)) nil)}
    (catch NumberFormatException e
      (throw (ex-info "Invalid email server port configuration. Expected a number."
                      {:error :invalid-port
                       :port (environ/env :email-server-port)}
                      e)))))

(defn emailfrom []
  (if (not (s/blank? (environ/env :email-from-address))) (environ/env :email-from-address) "no-reply@dungeonmastersvault.com"))

(defn send-verification-email
  "Sends account verification email to a new user.

  Args:
    base-url - Base URL for the application (for verification link)
    user-map - Map containing :email, :username, and :first-and-last-name
    verification-key - Unique key for email verification

  Returns:
    Postal send-message result

  Throws:
    ExceptionInfo with :verification-email-failed error code if email cannot be sent"
  [base-url {:keys [email username first-and-last-name]} verification-key]
  (try
    (let [result (postal/send-message (email-cfg)
                                      {:from (str "Dungeon Master's Vault Team <" (emailfrom) ">")
                                       :to email
                                       :subject "Dungeon Master's Vault Email Verification"
                                       :body (verification-email
                                              first-and-last-name
                                              username
                                              (str base-url (routes/path-for routes/verify-route) "?key=" verification-key))})]
      (when (not= :SUCCESS (:error result))
        (throw (ex-info "Failed to send verification email"
                        {:error :email-send-failed
                         :email email
                         :postal-response result})))
      result)
    (catch Exception e
      (println "ERROR: Failed to send verification email to" email ":" (.getMessage e))
      (throw (ex-info "Unable to send verification email. Please check your email configuration or try again later."
                      {:error :verification-email-failed
                       :email email
                       :username username}
                      e)))))

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
   "Dear Dungeon Master's Vault Patron"
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
    :content (str (hiccup/html (reset-password-email-html first-and-last-name reset-url)))}])

(defn send-reset-email
  "Sends password reset email to a user.

  Args:
    base-url - Base URL for the application (for reset link)
    user-map - Map containing :email, :username, and :first-and-last-name
    reset-key - Unique key for password reset

  Returns:
    Postal send-message result

  Throws:
    ExceptionInfo with :reset-email-failed error code if email cannot be sent"
  [base-url {:keys [email username first-and-last-name]} reset-key]
  (try
    (let [result (postal/send-message (email-cfg)
                                      {:from (str "Dungeon Master's Vault Team <" (emailfrom) ">")
                                       :to email
                                       :subject "Dungeon Master's Vault Password Reset"
                                       :body (reset-password-email
                                              first-and-last-name
                                              (str base-url (routes/path-for routes/reset-password-page-route) "?key=" reset-key))})]
      (when (not= :SUCCESS (:error result))
        (throw (ex-info "Failed to send password reset email"
                        {:error :email-send-failed
                         :email email
                         :postal-response result})))
      result)
    (catch Exception e
      (println "ERROR: Failed to send password reset email to" email ":" (.getMessage e))
      (throw (ex-info "Unable to send password reset email. Please check your email configuration or try again later."
                      {:error :reset-email-failed
                       :email email
                       :username username}
                      e)))))

(defn send-error-email
  "Sends error notification email to configured admin email.

  This function is called when unhandled exceptions occur in the application.
  It includes request context and exception details for debugging.

  Args:
    context - Request context map
    exception - The exception that occurred

  Returns:
    Postal send-message result, or nil if no error email is configured
    or if sending fails (failures are logged but not thrown)"
  [context exception]
  (when (not-empty (environ/env :email-errors-to))
    (try
      (let [result (postal/send-message (email-cfg)
                                        {:from (str "Dungeon Master's Vault Errors <" (emailfrom) ">")
                                         :to (str (environ/env :email-errors-to))
                                         :subject "Exception"
                                         :body [{:type "text/plain"
                                                 :content (let [writer (java.io.StringWriter.)]
                                                            (clojure.pprint/pprint (:request context) writer)
                                                            (clojure.pprint/pprint (or (ex-data exception) exception) writer)
                                                            (str writer))}]})]
        (when (not= :SUCCESS (:error result))
          (println "WARNING: Failed to send error notification email:" (:error result)))
        result)
      (catch Exception e
        (println "ERROR: Failed to send error notification email:" (.getMessage e))
        nil))))