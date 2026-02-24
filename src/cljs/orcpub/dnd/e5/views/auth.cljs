(ns orcpub.dnd.e5.views.auth
  "Authentication and registration page components.

   Contains all auth-flow pages: login, registration, password reset,
   email verification, and their helper components. Every def here
   depends only on views.common (never on views.cljs or siblings),
   keeping the dependency graph acyclic."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [orcpub.dnd.e5.views.common :as views-common]
            [orcpub.registration :as registration]))

;;;; ====================================================================
;;;; Auth Helpers
;;;; ====================================================================

(defn password-validation-messages
  "Validate password and return any error messages."
  [password]
  (-> password
      registration/validate-password
      :password))

;;;; ====================================================================
;;;; Shared Auth Components
;;;; ====================================================================

(defn login-link
  "Inline styled link that navigates to the login page."
  []
  [:span.underline.f-w-b.m-l-10.pointer.orange
   {:on-click views-common/dispatch-route-to-login}
   "LOGIN"])

(defn email-sent
  "Generic 'check your email' page with custom body text."
  [text]
  (views-common/registration-page
   [:div {:style {:text-align :center}}
    [:div {:style {:color views-common/orange
                   :font-weight :bold
                   :font-size "36px"
                   :text-transform :uppercase
                   :text-shadow "1px 2px 1px rgba(0,0,0,0.37)"
                   :margin-top "100px"}}
     "Check your email"]
    [:div.p-20
     text]]))

;;;; ====================================================================
;;;; Email Verification Pages
;;;; ====================================================================

(defn verify-failed
  "Page shown when email verification link has expired.
   Offers a form to resend the verification email."
  []
  (let [params (r/atom {})]
    (fn []
      (views-common/registration-page
       [:div.flex.justify-cont-s-b {:style {:text-align :center
                                            :flex-direction :column}}
        [:div.p-20
         [:div.f-w-b.f-s-24.p-b-10
          "Your key has expired."]
         [:div "You must verify your email within 24 hours of registering. Send another verification email by submitting your address here:"]
         [views-common/base-input
          {:name :email
           :value (:email @params)
           :type :email
           :placeholder "Email"
           :style views-common/default-input-style
           :on-change (partial views-common/set-value params :email)}]
         [:button.form-button.m-l-20.m-t-10
          {:style {:height "40px"
                   :width "174px"
                   :font-size "16px"
                   :font-weight "600"}
           :on-click (views-common/make-event-handler :re-verify @params)}
          "RESEND"]]]))))

(defn verify-success
  "Success page after email verification is complete."
  []
  (views-common/registration-page
   [:div {:style {:text-align :center}}
    [:div {:style {:color views-common/orange
                   :font-weight :bold
                   :font-size "36px"
                   :text-transform :uppercase
                   :text-shadow "1px 2px 1px rgba(0,0,0,0.37)"
                   :margin-top "100px"}}
     "Success! Registration is complete"]
    [:div.m-t-20 "You can now"]
    [login-link]]))

(defn verify-sent
  "Confirmation page after verification email is sent."
  []
  (email-sent
   [:div
    [:span "We sent a verification email to "]
    [:span.f-w-b.red.f-s-18 @(subscribe [:temp-email])]
    [:span ". You must verify to complete registration and the link we sent will only be valid for 24 hours."]]))

;;;; ====================================================================
;;;; Password Reset Pages
;;;; ====================================================================

(defn send-password-reset-page
  "Form page for requesting a password reset email.
   Accepts an optional error-message for expired/used link flows."
  []
  (let [params (r/atom {})]
    (fn [error-message]
      (let [email (:email @params)
            bad-email? (registration/bad-email? email)]
        (views-common/registration-page
         [:div.flex.justify-cont-s-b.w-100-p
          {:style {:text-align :center
                   :flex-direction :column}}
          [:div.p-t-10
           (when error-message [:div.red.m-b-20 error-message])
           [:div.f-w-b.f-s-24.p-b-10
            "Send Password Reset Email"]
           [:div.m-b-10 "Submit your email address here and we will send you a link to reset your password."]
           [views-common/form-input
            {:title "Email"
             :key :email
             :messages (if bad-email?
                         ["Not a valid email address"]
                         [])
             :type :email
             :value email
             :on-change (partial views-common/set-value params :email)}]
           (when @(subscribe [:login-message-shown?])
             [:div.m-t-5.p-r-5.p-l-5
              [views-common/message
               :error
               @(subscribe [:login-message])
               views-common/hide-login-message]])
           [:button.form-button.m-t-10
            {:style {:height "40px"
                     :width "174px"
                     :font-size "16px"
                     :font-weight "600"}
             :class (when bad-email? "disabled opacity-5 hover-no-shadow")
             :on-click (when (not bad-email?) (views-common/make-event-handler :send-password-reset @params))}
            "SUBMIT"]]])))))

(defn password-reset-expired-page
  "Password reset page pre-filled with an expiry error message."
  []
  [send-password-reset-page "Your reset link has expired, you must complete the reset within 24 hours. Please use the form below to send another reset email."])

(defn password-reset-used-page
  "Password reset page pre-filled with an already-used error message."
  []
  [send-password-reset-page "Your reset link has already been used. Please use the form below to send another reset email."])

(defn password-reset-page
  "Form for setting a new password after clicking a reset link."
  []
  (let [params (r/atom {})]
    (fn []
      (let [password (:password @params)
            verify-password (:verify-password @params)
            password-messages (password-validation-messages password)
            different? (not= password verify-password)
            invalid? (or (seq password-messages)
                         different?)]
        (views-common/registration-page
         [:div.flex.justify-cont-s-b {:style {:text-align :center
                                              :flex-direction :column}}
          [:div.p-20
           [:div.f-w-b.f-s-24.p-b-10
            "Reset Password"]
           [:div "Create a new password."]
           [views-common/form-input {:title "Password"
                                     :key :password
                                     :value password
                                     :type :password
                                     ;;:messages password-messages
                                     :on-change (fn [e] (swap! params assoc :password (views-common/event-value e)))}]
           [views-common/form-input {:title "Verify Password"
                                     :key :verify-password
                                     :value verify-password
                                     :type :password
                                     :messages (when different? ["Passwords do not match"])
                                     :on-change (fn [e] (swap! params assoc :verify-password (views-common/event-value e)))}]
           (when @(subscribe [:login-message-shown?])
             [:div.m-t-5.p-r-5.p-l-5 [views-common/message
                                      :error
                                      @(subscribe [:login-message])
                                      views-common/hide-login-message]])
           [:button.form-button.m-l-20.m-t-10
            {:style {:height "40px"
                     :width "174px"
                     :font-size "16px"
                     :font-weight "600"}
             :class (when invalid? "opacity-5 hover-no-shadow cursor-disabled")
             :on-click (when (not invalid?) (views-common/make-event-handler :password-reset @params))}
            "SUBMIT"]]])))))

(defn password-reset-success
  "Success page shown after a password has been reset."
  []
  (views-common/registration-page
   [:div {:style {:text-align :center}}
    [:div {:style {:color views-common/orange
                   :font-weight :bold
                   :font-size "36px"
                   :text-transform :uppercase
                   :text-shadow "1px 2px 1px rgba(0,0,0,0.37)"
                   :margin-top "100px"}}
     "Your password has been successfully reset"]
    [:div.m-t-20 "You can now log in"]
    [login-link]]))

(defn password-reset-sent
  "Confirmation page after a password reset email is sent."
  []
  (email-sent
   (str "We sent an email to "
        @(subscribe [:temp-email])
        " with a link to reset your password.")))

;;;; ====================================================================
;;;; Registration & Login
;;;; ====================================================================

(defn register-form
  "Full registration form with username, email, password, strength meter,
   and opt-in checkbox."
  []
  (let [registration-validation @(subscribe [:registration-validation])
        registration-form @(subscribe [:registration-form])
        send-updates? (not= false (:send-updates? registration-form))
        password-strength (registration/password-strength (:password registration-form))]
    (views-common/registration-page
     [:div {:style {:text-align :center}}
      [:div {:style {:color views-common/orange
                     :font-weight :bold
                     :font-size "36px"
                     :text-transform :uppercase
                     :text-shadow "1px 2px 1px rgba(0,0,0,0.37)"
                     :margin-top "20px"}}
       "join for free"]
      [:div.f-s-16.m-t-20 "Join now to save your characters and more!"]
      [:div.m-t-10
       [views-common/form-input {:title "Username"
                                 :key :username
                                 :value (:username registration-form)
                                 :messages (:username registration-validation)
                                 :type :username
                                 :on-change (fn [e] (dispatch [:registration-username (views-common/event-value e)]))}]
       [views-common/form-input {:title "Email"
                                 :key :email
                                 :value (:email registration-form)
                                 :messages (:email registration-validation)
                                 :type :email
                                 :on-change (fn [e] (dispatch [:registration-email (views-common/event-value e)]))}]
       [views-common/form-input {:title "Verify Email"
                                 :key :verify-email
                                 :value (:verify-email registration-form)
                                 :messages (:verify-email registration-validation)
                                 :type :email
                                 :on-change (fn [e] (dispatch [:registration-verify-email (views-common/event-value e)]))}]
       [views-common/form-input {:title "Password"
                                 :key :password
                                 :value (:password registration-form)
                                 :messages (:password registration-validation)
                                 :type :password
                                 :on-change (fn [e] (dispatch [:registration-password (views-common/event-value e)]))}]
       (let [[color text]
              (cond
                (= 5 password-strength) ["bg-green" "Strong"]
                (< 1 password-strength 5) ["bg-orange" "Moderate"]
                :else ["bg-red" "Weak"])]
         [:div.p-r-10.p-l-10.p-t-5
          [:div
           {:style {:position :relative
                    :height "30px"}}
           [:div.b-rad-5
            {:style {:top 0
                     :left 0
                     :height "30px"
                     :opacity "0.7"
                     :width "100%"
                     :position :absolute}
             :class color}]
           [:div.b-rad-5.password-strength-meter
            {:style {:top 0
                     :left 0
                     :position :absolute
                     :height "30px"
                     :transition "width 1s"
                     :width (str (* 100 (float (/ password-strength 5))) "%")}
             :class color}]
           [:div.main-text-color.p-l-10.b-rad-5
            {:style {:position :absolute
                     :padding-top "6px"}}
             [:span "Password Strength:"]
             [:span.f-w-b.m-l-5 text]]]])
       [:div.m-t-20
        {:style {:text-align :left
                 :margin-left "15px"}}
        [:i.fa.fa-check.f-s-14.pointer
         {:class (if send-updates? "orange" "white")
          :style {:margin-top "-3px"
                  :border-color "#f0a100"
                  :border-style :solid
                  :border-width "1px"
                  :border-bottom-width "3px"}
          :on-click #(dispatch [:registration-send-updates? (not send-updates?)])}]
        [:span.m-l-5 "Yes! Send me updates about OrcPub."]]
       [:div.m-t-30
        [:div.p-10
         [:span "Already have an account?"]
         (login-link)]
        [:button.form-button
         {:style {:height "40px"
                  :width "174px"
                  :font-size "16px"
                  :font-weight "600"}
          :class (when (seq registration-validation) "opacity-5 hover-no-shadow cursor-disabled")
          :on-click #(when (empty? registration-validation)
                       (dispatch [:register]))}
         "JOIN"]]]
      [:div.m-t-5.p-r-10.p-l-10
       [:span.f-s-14
        "By clicking JOIN you agree to our"
        [:a.m-l-5 {:href "/terms-of-use" :target :_blank
                   :style {:color views-common/text-color}} "Terms of Use"]
        [:span.m-l-5 "and that you've read our"]
        [:a.m-l-5 {:href "/privacy-policy" :target :_blank
                   :style {:color views-common/text-color}} "Privacy Policy"]]]])))

(defn login-page
  "Login page with username/password fields, register and reset links."
  []
  (let [params (r/atom {})]
    (fn []
      (let [login-message-shown? @(subscribe [:login-message-shown?])
            login-message @(subscribe [:login-message])]
        (views-common/registration-page
         [:div {:style {:text-align :center}}
          [:div {:style {:color views-common/orange
                         :font-weight :bold
                         :font-size "36px"
                         :text-transform :uppercase
                         :text-shadow "1px 2px 1px rgba(0,0,0,0.37)"
                         :margin-top "20px"}}
           "LOGIN"]
          ;[:div.m-t-10
          ; [facebook-login-button]]
          [:div.login-form-inputs
           [views-common/form-input {:title "Username or Email"
                                     :key :username
                                     :value (:username @params)
                                     :type :username
                                     :on-change #(swap! params assoc :username (views-common/event-value %))}]
           [views-common/form-input {:title "Password"
                                     :key :password
                                     :value (:password @params)
                                     :type :password
                                     :on-change #(swap! params assoc :password (views-common/event-value %))}]
           (when login-message-shown?
             [:div.m-t-5.p-r-5.p-l-5 [views-common/message
                                      :error
                                      login-message
                                      views-common/hide-login-message]])
           [:div.m-t-10
            [:button.form-button
             {:style {:height "40px"
                      :width "174px"
                      :font-size "16px"
                      :font-weight "600"}
              :on-click #(dispatch [:login @params true])}
             "LOGIN"]
            [:div.m-t-20
             [:span "Don't have a login? "]
             [:span.orange.underline.pointer
              {:on-click views-common/route-to-register-page}
              "REGISTER NOW"]]
            [:div.m-t-20
             [:span "Forgot your password? "]
             [:span.orange.underline.pointer
              {:on-click views-common/route-to-reset-password-page}
              "RESET PASSWORD"]]]]])))))
