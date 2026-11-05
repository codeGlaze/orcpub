(ns orcpub.dnd.e5.events.auth
  "The re-frame events for accounts: sign-up and its form fields, login and logout, the
   verification and password reset requests, and the email change. Moved out of
   orcpub.dnd.e5.events; this namespace never requires it. Required from web/cljs/orcpub/core.cljs
   so the handlers are registered before anything dispatches them."
  (:require [bidi.bidi :as bidi]
            [orcpub.dnd.e5.event-utils :as event-utils
             :refer [backend-url show-generic-error user->local-store-interceptor
                     auth-headers]
             :rename {auth-headers authorization-headers}]
            [orcpub.errors :as errors]
            [orcpub.fork.branding :as branding]
            [orcpub.route-map :as routes]
            [re-frame.core :refer [->interceptor reg-event-db reg-event-fx]]))

;; The protection every auth request shares (security/auth-guards decides which forms ask for it):
;; the current Turnstile token goes out with the request, and a failure asks the widget for a fresh
;; one, because a token works once. Attach these to the events that send and that receive.
(def challenge-request
  (->interceptor
   :id :challenge-request
   :after (fn [context]
            (let [token (:turnstile-token (get-in context [:coeffects :db]))
                  http (get-in context [:effects :http])
                  params-key (if (contains? http :json-params) :json-params :query-params)]
              (cond-> context
                (and token http) (assoc-in [:effects :http params-key]
                                           (assoc (get http params-key) :turnstile-token token)))))))

(def challenge-reset
  (->interceptor
   :id :challenge-reset
   :after (fn [context]
            (update-in context [:effects :dispatch-n] (fnil conj []) [:turnstile-reset]))))

(reg-event-fx
 :change-email
 (fn [{:keys [db]} [_ new-email current-password]]
   {:db (dissoc db :email-change-sent? :email-change-error)
    :http {:method :put
           :headers (authorization-headers db)
           :url (backend-url (routes/path-for routes/user-email-route))
           ;; The password is sent to be COMPARED, never stored and never read
           ;; back: the server hands it to lookup-user, which checks the hash.
           :transit-params {:new-email new-email
                            :current-password current-password}
           :on-success [:change-email-success]
           :on-failure [:change-email-failure]}}))

(reg-event-db
 :change-email-success
 (fn [db [_ response]]
   (-> db
       (assoc :email-change-sent? true)
       ;; Use server-canonical (lowercased/trimmed) email for display
       (assoc-in [:user-data :user-data :pending-email]
                 (-> response :body :pending-email)))))

(reg-event-db
 :change-email-failure
 (fn [db [_ response]]
   (let [body (:body response)
         error (:error body)]
     (assoc db :email-change-error
            (case error
              :bad-credentials "That password is not right. The account stays on its current address."
              :email-taken "That email address is already in use by another account."
              :invalid-email "Please enter a valid email address."
              :same-as-current "That is already your current email address."
              :too-many-requests
              (let [secs (:retry-after-secs body)]
                (if (and secs (pos? secs))
                  (if (<= secs 60)
                    ;; 0–1 min zone: email is in transit, show short countdown
                    (str "Your email is on its way. You can resend in " secs " second" (when (> secs 1) "s") ".")
                    ;; 1–5 min zone for a different email: show minutes
                    (let [mins (.ceil js/Math (/ secs 60))]
                      (str "Please wait " mins " minute" (when (> mins 1) "s") " before requesting another change.")))
                  "Please wait a few minutes before requesting another email change."))
              :email-send-failed "Verification email could not be sent. Please try again later."
              "There was an error updating your email. Please try again.")))))

(reg-event-db
 :change-email-clear
 (fn [db _]
   (dissoc db :email-change-sent? :email-change-error)))

(reg-event-db
 :clear-login
 [user->local-store-interceptor]
 ;; With a token: only while that is still the login, so a 401 that answers a login already
 ;; replaced does not sign the new one out.
 (fn [db [_ expected-token]]
   (if (or (nil? expected-token) (= expected-token (event-utils/get-auth-token db)))
     (-> db
         (update :user-data dissoc :user-data :token)
         (dissoc :user))
     db)))

(def login-url (backend-url "/login"))

(reg-event-fx
 :login-success
 [user->local-store-interceptor]
 (fn [{:keys [db]} [_ backtrack? response]]
   ;; A button-text choice saved on the account wins over this browser's.
   {:db (let [account-choice (-> response :body :user-data :dark-button-text?)]
          (cond-> (-> db
                      (dissoc :login-challenge?)
                      (update :user-data merge (-> response :body))
                      (assoc :user (-> response :body :user-data)))
            (some? account-choice) (assoc-in [:user-data :dark-button-text?] account-choice)))
    :dispatch [:route (or
                       (:return-route db)
                       routes/dnd-e5-char-builder-route)]}))

(defn sign-in-failed-message []
  ;; Deliberately vague about which half was wrong: naming the username as unknown
  ;; would make this form a membership-disclosure oracle. See account-flows.md.
  [:show-login-message
   [:div "That username and password do not match. Usernames and passwords are case sensitive, "
    "email addresses are not. If you have not signed up yet you can "
    [:a {:href (routes/path-for routes/register-page-route)} "register"] "."]])

(defn dispatch-login-failure [message]
  {:dispatch-n [[:clear-login]
                [:show-login-message message]]})

(reg-event-fx
 :login-failure
 [challenge-reset]
 (fn [{:keys [db]} [_ response]]
   (let [error-code (-> response :body :error)
         ;; The server sends the flag identically after every failed sign-in, so the form shows
         ;; the challenge for the retry.
         challenge? (boolean (-> response :body :challenge?))
         with-challenge (fn [fx]
                          (cond-> fx
                            challenge? (assoc :db (assoc db :login-challenge? true))))]
     (with-challenge
       (cond
         (= error-code errors/challenge-required)
         {:dispatch-n [[:clear-login]
                       [:show-login-message "Please complete the security check below, then sign in again."]]}
         (= error-code errors/username-required) (dispatch-login-failure "Username is required.")
         (= error-code errors/too-many-attempts) (dispatch-login-failure "Too many attempts just now. Wait a minute and try again.")
         (= error-code errors/password-required) (dispatch-login-failure "Password is required.")
         (= error-code errors/bad-credentials) {:dispatch-n [[:clear-login]
                                                             (sign-in-failed-message)]}
         (= error-code errors/unverified) {:db (assoc db :temp-email (-> response :body :email))
                                           :dispatch [:route routes/verify-sent-route]}
         (= error-code errors/unverified-expired) {:dispatch [:route routes/verify-failed-route]}
         :else (dispatch-login-failure
                (if (seq branding/support-email)
                  [:div "An error occurred. If the problem persists please email "
                   [:a {:href (str "mailto:" branding/support-email) :target :blank} branding/support-email]]
                  [:div "An error occurred. Please try again later."])))))))

(reg-event-fx
 :logout
 ;; Only the player's own Log Out: the builder starts fresh, the draft stays under its account.
 ;; An expired session clears the login (:clear-login) without touching the builder.
 (fn [cofx [_ response]]
   {:dispatch-n [[:clear-login] [:reset-character]]}))

(def login-routes
  #{routes/login-page-route
    routes/register-page-route
    routes/verify-sent-route
    routes/reset-password-page-route
    routes/verify-failed-route
    routes/verify-success-route
    routes/send-password-reset-page-route
    routes/password-reset-success-route
    routes/password-reset-expired-route
    routes/password-reset-used-route
    routes/unsubscribe-success-route})

(reg-event-fx
 :login
 [challenge-request]
 (fn [_ [_ params backtrack?]]
   {:http {:method :post
           :url login-url
           :json-params params
           :on-success [:login-success backtrack?]
           :on-unauthorized [:login-failure]}}))

(reg-event-db
 :register-success
 (fn [db [_ backtrack? response]]
   ;; A deployment with no EMAIL_SERVER_URL verifies on creation and says so
   ;; with :verified? -- sending such a user to "check your email" would point
   ;; them at a mail that is never coming.
   (let [verified? (get-in response [:body :verified?])]
     (-> db
         (update :user-data merge (:body response))
         (assoc :route (if verified? :verify-success :verify-sent))))))

(reg-event-db
 :registration-attempted
 ;; Set when JOIN is pressed on a form that is not ready. The button no longer
 ;; dims -- a control that looks dead teaches people the site is broken rather
 ;; than that their form is -- so pressing it has to SAY something instead.
 (fn [db _]
   (assoc db :registration-attempted? true)))

(reg-event-fx
 :register-failure
 ;; :registration-server-errors holds what only the server can judge, shown by field; any other
 ;; failure becomes a :general notice, so no server-side failure is silent. account-flows.md.
 [challenge-reset]
 (fn [{:keys [db]} [_ response]]
   (let [body (:body response)]
     {:db (assoc db :registration-server-errors
                 (cond
                   (= :email-not-configured (:error body))
                   {:general ["Registration is currently unavailable. Please contact the site administrator."]}
                   (and (map? body) (not (contains? body :error))) body
                   :else {:general ["Registration failed. Please try again."]}))
      :dispatch-n [[:clear-login]]})))

(reg-event-db
 :turnstile-token
 (fn [db [_ token]]
   (assoc db :turnstile-token token)))

(reg-event-db
 :turnstile-blocked
 ;; True when the widget's script could not load or the widget reported an error.
 (fn [db [_ blocked?]]
   (assoc db :turnstile-blocked? (boolean blocked?))))

(reg-event-fx
 :turnstile-reset
 (fn [_ _]
   ;; Registered in views/auth, which owns the widget.
   {:orcpub.dnd.e5.views.auth/turnstile-reset true}))

#_ ;; dead stub — real impl is orcpub.registration/validate-registration
  (defn validate-registration [])

(reg-event-db
 :username-taken
 (fn [db [_ response]]
   (assoc db :username-taken? (-> response :body (= "true")))))

#_ ;; never dispatched — registration form uses :register-first-and-last-name
  (reg-event-db
   :registration-first-and-last-name
   (fn [db [_ first-and-last-name]]
     (assoc-in db [:registration-form :first-and-last-name] first-and-last-name)))

(reg-event-db
 :registration-email
 (fn [db [_ email]]
   (assoc-in db [:registration-form :email] email)))

(reg-event-fx
 :registration-verify-email
 (fn [{:keys [db]} [_ email]]
   {:db (assoc-in db [:registration-form :verify-email] email)}))

(reg-event-fx
 :registration-username
 (fn [{:keys [db]} [_ username]]
   {:db (assoc-in db [:registration-form :username] username)
    :dispatch [:check-username username]}))

(reg-event-db
 :registration-password
 (fn [db [_ password]]
   (-> db
       ;; What the server said was about the password it was sent, not this one.
       (dissoc :registration-server-errors)
       (assoc-in [:registration-form :password] password))))

(reg-event-db
 :registration-password-revealed?
 ;; In the db rather than a local atom: whatever decides the form may be
 ;; submitted has to know the confirmation is not being asked for, and a reveal
 ;; hidden inside the field component is invisible to it.
 (fn [db [_ revealed?]]
   (assoc-in db [:registration-form :password-revealed?] revealed?)))

(reg-event-db
 :registration-verify-password
 (fn [db [_ password]]
   (-> db
       (dissoc :registration-server-errors)
       (assoc-in [:registration-form :verify-password] password))))

(reg-event-db
 :registration-send-updates?
 (fn [db [_ send-updates?]]
   (assoc-in db [:registration-form :send-updates?] send-updates?)))

#_ ;; never dispatched from UI
  (reg-event-db
   :register-first-and-last-name
   (fn [db [_ first-and-last-name]]
     (assoc-in db [:registration-form :first-and-last-name] first-and-last-name)))

(reg-event-fx
 :check-username
 (fn [{:keys [db]} [_ username]]
   {:http {:method :get
           :url (backend-url (bidi/path-for routes/routes routes/check-username-route))
           :query-params {:username username}
           :on-success [:username-taken]}}))

(reg-event-fx
 :register
 [challenge-request]
 (fn [{:keys [db]} [_ params backtrack?]]
   (let [registration-form (:registration-form db)]
     {:db (assoc db :temp-email (:email registration-form))
      :http {:method :post
             :url (backend-url (bidi/path-for routes/routes routes/register-route))
             :json-params registration-form
             :on-success [:register-success backtrack?]
             :on-failure [:register-failure]}})))

(reg-event-db
 :re-verify-success
 (fn [db [_ response]]
   ;; With no SMTP and ALLOW_UNVERIFIED_REGISTRATION set, a resend verifies the
   ;; account on the spot and says so -- "check your email" would leave the user
   ;; waiting for a message that is never sent.
   (assoc db :route (if (get-in response [:body :verified?])
                      routes/verify-success-route
                      routes/verify-sent-route))))

(defn- refused-request
  "Effects for a request the server refused: its :general message when it gave one (the failed or
   missing challenge), else the generic error."
  [response]
  (let [message (first (get-in response [:body :general]))]
    {:dispatch-n [(if message [:show-login-message message] (show-generic-error))]}))

(reg-event-fx
 :re-verify-failure
 [challenge-reset]
 (fn [_ [_ response]]
   (refused-request response)))

(reg-event-fx
 :re-verify
 [challenge-request]
 (fn [{:keys [db]} [_ params]]
   {:db (assoc db :temp-email (:email params))
    :http {:method :get
           :url (backend-url (bidi/path-for routes/routes routes/re-verify-route))
           :query-params params
           :on-success [:re-verify-success]
           :on-failure [:re-verify-failure]}}))

(reg-event-db
 :send-password-reset-success
 (fn [db []]
   (assoc db :route routes/password-reset-sent-route)))

(reg-event-fx
 :send-password-reset-failure
 ;; The endpoint answers 200 for every address, so :no-account cannot arrive here. A refused
 ;; challenge carries a :general message; anything else is a network or server fault.
 [challenge-reset]
 (fn [_ [_ response]]
   (refused-request response)))

(reg-event-fx
 :send-password-reset
 [challenge-request]
 (fn [{:keys [db]} [_ params]]
   {:db (assoc db :temp-email (:email params))
    :http {:method :get
           :url (backend-url (bidi/path-for routes/routes routes/send-password-reset-route))
           :query-params params
           :on-success [:send-password-reset-success]
           :on-failure [:send-password-reset-failure]}}))

(reg-event-db
 :password-reset-success
 (fn [db []]
   (assoc db :route routes/password-reset-success-route)))

(reg-event-fx
 :password-reset-failure
 ;; The same bug :register-failure had: the body was thrown away and replaced
 ;; with one generic sentence, so a password refused for being a common one said
 ;; only that something had gone wrong, and the obvious next move was to try the
 ;; same password again. The server's reasons are field-keyed; keep them.
 (fn [{:keys [db]} [_ response]]
   (let [errors (when (map? (:body response)) (:body response))]
     (cond
       ;; An expired or used link: nothing in the form can fix it.
       (:general errors) (dispatch-login-failure (first (:general errors)))
       (seq errors)
       {:db (assoc db :password-reset-server-errors errors)}
       :else
       ;; A failure with nothing to say -- a network drop, a 500 -- still needs
       ;; to say something.
       (dispatch-login-failure "There was an error resetting your password.")))))

(reg-event-db
 :password-reset-clear-errors
 ;; What the server said was about the password it was sent, not this one.
 (fn [db _]
   (dissoc db :password-reset-server-errors)))

(reg-event-fx
 :password-reset
 ;; The link's key is the only authority the server takes for a reset; there is no session.
 (fn [{:keys [db]} [_ params]]
   (let [reset-key (.get (js/URLSearchParams. (.. js/window -location -search)) "key")]
     {:db (assoc db :temp-email (:email params))
      :http {:method :post
             :url (backend-url (bidi/path-for routes/routes routes/reset-password-route))
             ;; A revealed password has no confirm box; the server still compares the two fields.
             :json-params {:password (:password params)
                           :verify-password (if (:password-revealed? params)
                                              (:password params)
                                              (:verify-password params))
                           :key reset-key}
             :on-success [:password-reset-success]
             :on-unauthorized [:password-reset-failure]
             :on-failure [:password-reset-failure]}})))
