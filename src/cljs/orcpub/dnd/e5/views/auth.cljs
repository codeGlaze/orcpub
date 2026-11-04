(ns orcpub.dnd.e5.views.auth
  "The account screens: sign-up, login, verification and password reset pages, the outcome pages,
   and the Cloudflare Turnstile widget the sign-up form carries.
   The Turnstile widget (moved from orcpub.turnstile) loads only when the server put a site key on
   the page (window.__TURNSTILE_SITE_KEY__), and only when the form mounts, so no other page
   fetches anything from Cloudflare. If the script cannot load, the form says so."
  (:require [re-frame.core :refer [subscribe dispatch reg-fx]]
            [reagent.core :as r]
            [orcpub.route-map :as routes]
            [orcpub.components :as comps :refer [base-input form-input make-event-handler]]
            [orcpub.registration :as registration]
            [orcpub.errors :as errors]
            [orcpub.fork.branding :as branding]
            [orcpub.dnd.e5.views-2 :as views-2]
            [orcpub.dnd.e5.views.notifications :as notifications]
            [orcpub.dnd.e5.views.builder-fields :refer [event-value set-value]]
            [clojure.string :as s]))

;; ---- Cloudflare Turnstile widget (sign-up form, managed mode) ----

(def ^:private script-id "cf-turnstile-api")
(def ^:private script-src "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit")

(defn site-key
  "The public site key, or nil when Turnstile is not configured."
  []
  (let [k (aget js/window "__TURNSTILE_SITE_KEY__")]
    (when (and (string? k) (seq k)) k)))

(defonce ^:private widget-id (atom nil))

(defn- api [] (aget js/window "turnstile"))

(defn- call-api
  "Calls window.turnstile[method] by string name, so advanced compilation cannot rename it."
  [method & args]
  (let [t (api)]
    (.apply (aget t method) t (to-array args))))

(defn- with-api
  "Calls `f` once the Turnstile API is available, loading its script once. Never throws."
  [f]
  (if (api)
    (f)
    (if-let [existing (.getElementById js/document script-id)]
      (.addEventListener existing "load" f)
      (let [el (.createElement js/document "script")]
        (set! (.-id el) script-id)
        (set! (.-src el) script-src)
        (set! (.-async el) true)
        (set! (.-defer el) true)
        (.addEventListener el "load" f)
        ;; Script failed to load (an ad blocker, usually): say so where the widget would be, and
        ;; drop the tag so remounting the form tries again.
        (.addEventListener el "error" (fn [_]
                                        (.remove el)
                                        (dispatch [:turnstile-token nil])
                                        (dispatch [:turnstile-blocked true])))
        (.appendChild (.-head js/document) el)))))

(defn reset-widget!
  "Tokens are single use: asks the widget for a fresh one. No-op without a widget."
  []
  (dispatch [:turnstile-token nil])
  (dispatch [:turnstile-blocked false])
  (when-let [id @widget-id]
    (try (call-api "reset" id) (catch :default _ nil))))

;; The :turnstile-reset event (events/auth) returns this effect, so it need not require this
;; namespace; core.cljs loads this one at startup, before anything dispatches.
(reg-fx
 ::turnstile-reset
 (fn [_] (reset-widget!)))

(defn- render! [el key]
  (when (and (api) (nil? @widget-id) (.-isConnected el))
    (reset! widget-id
            (try
              (call-api "render" el
                       #js {:sitekey key
                            :callback (fn [token] (dispatch [:turnstile-token token]))
                            :expired-callback (fn [] (dispatch [:turnstile-token nil]))
                            :error-callback (fn []
                                              (dispatch [:turnstile-token nil])
                                              (dispatch [:turnstile-blocked true]))})
              (catch :default _ nil)))))

(defn widget
  "The challenge container. Renders nothing when no site key is configured."
  []
  (when-let [key (site-key)]
    (let [el (atom nil)]
      (r/create-class
       {:display-name "turnstile-widget"
        :component-did-mount
        (fn [_]
          (dispatch [:turnstile-blocked false])
          (with-api #(when @el (render! @el key))))
        :component-will-unmount
        (fn [_]
          (when-let [id @widget-id]
            (try (call-api "remove" id) (catch :default _ nil)))
          (reset! widget-id nil)
          (dispatch [:turnstile-token nil]))
        :reagent-render
        (fn []
          [:div.m-t-10
           [:div {:ref #(reset! el %)}]
           (when @(subscribe [:turnstile-blocked?])
             [notifications/message :warning errors/turnstile-blocked-message identity])])}))))

;; ---- Account screens ----

(def ^:private password-rule-chips
  "One chip per rule: which rule it is, and whether `password` satisfies it.
   GOTCHA: the \"not your name\" chip only renders where `ctx` has identifying
   info -- the reset page passes none, so a context chip there would always
   pass. See account-flows.md."
  [{:label "12 or more"
    :ok? (fn [p _] (>= (count (or p "")) registration/min-password-length))}
   {:label "no triples"
    :ok? (fn [p _] (not (registration/repeated-run? p)))}
   {:label "no key runs"
    :ok? (fn [p _] (not (registration/sequential-run? p)))}
   {:label "some variety"
    :ok? (fn [p _] (not (registration/too-few-distinct? p)))}
   {:label "not your name"
    :needs-context? true
    :ok? (fn [p ctx] (not (registration/contains-identifier? p ctx)))}])

(def ^:private password-tips
  "Per-rung lines to show once the password passes that rung, several per rung.
   GOTCHA: `password-tip` picks among a rung's lines by text length, not
   randomly, so the line holds still while typing instead of flickering on
   every keystroke. See account-flows.md."
  [["Two words that have no business together beat one clever one."
    "Another word buys more than another symbol."
    "Swapping o for 0 fools nobody. That is the first thing tried."]
   ["Spaces are allowed. A short sentence types faster than it looks."
    "One more word is the cheapest upgrade here."
    "Nonsense is fine. It does not have to mean anything."]
   ["Try something your character would say."
    "One more word and this is as good as it gets."
    "If you can picture it, you will remember it."]
   ["Nothing left to prove. Write it down somewhere safe."
    "About as good as it gets. Go make a character."
    "Just do not use this one anywhere else."]])

(defn- password-tip
  "The tip for this rung, held still by picking it from the length."
  [rung length]
  (when (and rung (not (neg? rung)))
    (let [set (nth password-tips rung)]
      (nth set (mod length (count set))))))

(defn password-meter
  "The password-strength meter, shared by the register and reset forms.
   `context` is identifying info the password must not contain, empty on the
   reset page, which is also what the server judges it against. `refused`
   shows \"too common\" once the server has rejected the password.
   GOTCHA: only this meter checks live; the server enforces the same rules
   silently otherwise. See account-flows.md."
  ([password] (password-meter password nil nil))
  ([password context] (password-meter password context nil))
  ([password context refused]
   (let [{:keys [rung fills]} (registration/password-strength password context)
         tier-names ["Uncommon" "Rare" "Very Rare" "Legendary"]
         ;; The corpus outranks the ladder. Only the server can reach it, so it
         ;; arrives late and separately -- but a password it refuses is not a
         ;; Rare one, and showing both is how the page ended up holding two
         ;; verdicts that disagreed.
         refused? (boolean (seq refused))
         reached? (and (not refused?) rung (not (neg? rung)))
         suffix (cond refused? "fail" (nil? rung) nil (neg? rung) "fail" :else rung)
         remaining (when (and reached? (< rung 3))
                     (- (nth registration/strength-rungs (inc rung))
                        (count (or password ""))))]
     [:div.p-r-10.p-l-10.p-t-5
      [:div.pw-slots
       (doall
        (for [[i percent] (map-indexed vector fills)]
          ^{:key i}
          [:div.pw-slot
           ;; The only inline style on this widget. The width IS the
           ;; measurement, so it cannot be a named class.
           [:div.pw-fill {:class (when suffix (str "pw-fill-" suffix))
                          :style {:width (str percent "%")}}]]))]
      [:div.pw-verdict
       [:span.pw-tier-name {:class (when suffix (str "pw-name-" suffix))}
        (cond refused? "Too common"
              reached? (nth tier-names rung))]
       [:span.pw-next
        (cond
          refused? nil
          remaining (str (nth tier-names (inc rung)) " in " remaining)
          (seq password) (str (count password) " characters"))]]
      ;; Every rule, each as its own chip. Before this the five rules spoke only
      ;; through whichever one happened to fail first, so nobody could see what
      ;; was being asked of them until they had broken it.
      (when (seq password)
        [:div.pw-chips
         (doall
          (for [{:keys [label ok? needs-context?]} password-rule-chips
                :when (or (not needs-context?) (seq (vals context)))]
            ^{:key label}
            [:span.pw-chip {:class (if (ok? password context) "is-ok" "is-bad")}
             label]))
         ;; Only the server can reach the corpus, so this appears once it has
         ;; answered and never guesses in the meantime.
         (when refused? [:span.pw-chip.is-bad "too common"])])
      ;; The line under the bar: what is wrong when something is, and what to do
      ;; next when nothing is. It sits with the bar it is about rather than in
      ;; the field's error slot, because both readings are advice on writing a
      ;; better password, which is what the whole widget is.
      (cond
        refused? [:div.pw-note refused]
        :else (when-let [tip (password-tip rung (count (or password "")))]
                [:div.pw-note.is-tip tip]))])))

(defn password-pair
  "A password field and its confirmation; revealing the password retires the
   confirmation field entirely.
   GOTCHA: the confirm field is REMOVED, not faded, on reveal -- faded would
   stay in the accessibility tree and a submit could fail pointing at a field
   nobody can see. `revealed?`/`on-toggle` are the caller's, since submit-gating
   has to know confirmation isn't being asked for. See account-flows.md."
  [{:keys [password confirm messages confirm-messages show-errors? revealed?
           on-toggle on-password on-confirm]}]
  [:div
   [form-input {:title "Password"
                :key :password
                :value password
                :type :password
                :messages messages
                :show-errors? show-errors?
                :reveal? true
                :revealed? revealed?
                :on-reveal on-toggle
                :on-change on-password}]
   (when-not revealed?
     [form-input {:title "Confirm password"
                  :key :verify-password
                  :value confirm
                  :type :password
                  ;; The caller may have more to say about this field than a
                  ;; mismatch -- the server answers per field too -- so its
                  ;; messages win when it passes any.
                  :messages (or (seq confirm-messages)
                                (when (and (seq password) (seq confirm)
                                           (not= password confirm))
                                  ["Passwords do not match"]))
                  :show-errors? show-errors?
                  :on-change on-confirm}])])

(defn dispatch-route-to-login [e]
  (.stopPropagation e)
  (dispatch [:route-to-login]))

(defn route-to-default-page []
  (dispatch [:route :default]))

(defn registration-page
  "The shell every auth page renders through. `legal-links?` false suppresses the
   footer's two links for a page that already carries them in its own consent copy."
  ([content] (registration-page content true))
  ([content legal-links?]
  [:div.sans.h-full.flex.flex-column
   [:div.flex.justify-cont-s-a.align-items-c.flex-grow-1.h-100-p
    [:div.registration-content
     [:div.flex.h-100-p
      [:div.flex.registration-left-col
       [:div.flex.justify-cont-s-a.align-items-c.registration-header
        [:img.pointer
         {:class branding/registration-logo-class
          :src branding/logo-path
          :on-click route-to-default-page}]]
       [:div.flex-grow-1 content]
       [views-2/legal-footer legal-links?]]
      [:div.registration-image]]]]]))

(defn auth-page
  "The shared auth shell: heading, rule, optional `lede` line, then `content`.
   `lede` is the line under the rule and is optional.
   See account-flows.md."
  ([heading content] (auth-page heading nil content nil))
  ([heading lede content] (auth-page heading lede content nil))
  ([heading lede content {:keys [legal-links?] :or {legal-links? true}}]
   (registration-page
    [:div
     [:h1.auth-heading.m-t-20 heading]
     [:div.auth-rule]
     (when lede [:div.auth-lede lede])
     content]
    legal-links?)))

(defn cookies []
  (let [cookie js/document.cookie]
    (into {}
          (map #(s/split % "="))
          (s/split cookie "; "))))

(defn- reset-username
  "The username of the account a reset link is for, from the plain cookie the reset page sets.
   Grants nothing; used only to flag \"password equals username\" while typing. The server
   re-derives it from the link's key on submit. See account-flows.md."
  []
  (some-> (get (cookies) "reset-username") js/decodeURIComponent))

(defn password-fields
  "The password pair plus the strength meter under it, as one unit.
   GOTCHA: state stays with the caller -- register keeps it in app-db, reset in
   a local atom, since a reset form exists for ninety seconds behind a one-use
   link. See account-flows.md."
  [{:keys [password confirm revealed? context messages confirm-messages
           show-errors? refused on-password on-confirm on-toggle]}]
  [:div
   [password-pair
    {:password password
     :confirm confirm
     :messages messages
     :confirm-messages confirm-messages
     :show-errors? show-errors?
     :revealed? revealed?
     :on-toggle on-toggle
     :on-password on-password
     :on-confirm on-confirm}]
   [password-meter password context refused]])

(defn auth-form-page
  "An auth-page shell for a form: `fields` in a column, then `tail` (submit and
   any links or fineprint) below them.
   See account-flows.md."
  [{:keys [heading lede fields tail legal-links?]
    :or {legal-links? true}}]
  (auth-page heading lede
             [:div
              [:div.m-t-10.auth-form fields]
              (when tail [:div.m-t-10.auth-tail tail])]
             {:legal-links? legal-links?}))

(defn hide-login-message []
  (dispatch [:hide-login-message]))

(defn- login-message-block
  "The form's refusal message, when there is one."
  []
  (when @(subscribe [:login-message-shown?])
    [:div.m-t-5.p-r-5.p-l-5
     [notifications/message :error @(subscribe [:login-message]) hide-login-message]]))

(defn verify-failed []
  (let [params (r/atom {})]
    (fn []
      (auth-form-page
       {:heading "Your key has expired"
        :lede (str "You must verify your email within 24 hours of registering. "
                   "Send another verification email by submitting your address here.")
        :fields [:div
                 [base-input
                  {:name :email
                   :value (:email @params)
                   :type :email
                   :title "Email address"
                   :on-change (partial set-value params :email)}]
                 ;; A refused challenge is answered here, as on the reset page.
                 [login-message-block]
                 [widget]]
        :tail [:button.form-button.form-submit-btn
               {:on-click (make-event-handler :re-verify @params)}
               "RESEND"]}))))

(defn send-password-reset-page []
  (let [params (r/atom {})]
    (fn [error-message]
      (let [email (:email @params)
            bad-email? (registration/bad-email? email)]
        (auth-form-page
         {:heading "Reset your password"
          :lede "Submit your email address and we will send you a link to reset your password."
          :fields
          [:div
           (when error-message [:div.red.m-b-20 error-message])
           [form-input
            {:title "Email"
             :key :email
             :messages (if bad-email?
                           ["Not a valid email address"]
                           [])
             :type :email
             :value email
             :on-change (partial set-value params :email)}]
           [login-message-block]
           [widget]]
          :tail
          [:div
           [:button.form-button.form-submit-btn
            {:class (when bad-email? "disabled opacity-5 hover-no-shadow")
             :on-click (when (not bad-email?) (make-event-handler :send-password-reset @params))}
            "SUBMIT"]
           ;; The link text is the whole phrase, not the one word "whitelist" in
           ;; the middle of it: a single word is a small target, and it is what a
           ;; screen reader announces on its own, where it names no destination.
           [:div.auth-help
            [:span "Didn't receive the reset email? Check your spam folder, or "]
            [:a.orange {:href "/help/im-not-getting-my-signup-password-reset-email/" :target "_blank"}
             "read how to let our email through"]
            [:span ", then try again."]]]})))))

(defn password-reset-expired-page []
  ;; Reached by an expired link AND by one that is mangled or unrecognised, so
  ;; the wording cannot claim to know which. It also no longer says 24 hours,
  ;; which stopped being true when the window became two.
  [send-password-reset-page
   (str "That reset link no longer works. Links last "
        "two hours, and each one can only be used once. "
        "Send yourself a new one below.")])

(defn password-reset-used-page []
  [send-password-reset-page "Your reset link has already been used. Please use the form below to send another reset email."])

(defn password-validation-messages [password]
  (-> password
      registration/validate-password
      :password))

(defn password-reset-page []
  (let [params (r/atom {})]
    (fn []
      (let [password (:password @params)
            verify-password (:verify-password @params)
            ;; Merged per field, so a server objection -- a common password, the
            ;; one rule only the server can apply -- sits alongside whatever the
            ;; form already found rather than replacing it.
            server-errors @(subscribe [:password-reset-server-errors])
            ;; GOTCHA: context carries only the username, never email, to avoid a disclosure
            ;; via cookie; the server checks email separately on submit. See account-flows.md.
            context {:username (reset-username)}
            ;; Reading the password back is what the confirm box stands in for,
            ;; so revealing it retires the box -- and the check that decides
            ;; whether this can be submitted has to know that, which is why the
            ;; flag is out here rather than inside password-pair.
            revealed? (:password-revealed? @params)
            faults (registration/password-pair-faults password verify-password
                                                      revealed? context)
            password-messages (vec (distinct (concat (:password faults)
                                                     (:password server-errors))))
            confirm-messages (vec (distinct (concat (:verify-password faults)
                                                    (:verify-password server-errors))))
            invalid? (or (seq password-messages) (seq confirm-messages))
            ;; Local to this page's own atom on purpose. The register form keeps
            ;; the same flag in app-db, and reaching for that from here is what
            ;; split the reveal state between a db read and a component atom
            ;; last time.
            attempted? (:attempted? @params)]
        (auth-form-page
         {:heading "Choose a new password"
          :lede "This replaces the password on your account."
          :fields
          [:div
           ;; The same pair and meter the register form uses.
           ;; GOTCHA: this is account RECOVERY, the last door somebody has -- a
           ;; silently dimmed button with no message is worse here than anywhere
           ;; else. See account-flows.md.
           [password-fields
            {:password password
             :confirm verify-password
             :context context
             :messages password-messages
             :confirm-messages confirm-messages
             :show-errors? attempted?
             :revealed? revealed?
             :refused (first (:password-common server-errors))
             :on-toggle #(swap! params update :password-revealed? not)
             :on-password (fn [e]
                            (dispatch [:password-reset-clear-errors])
                            (swap! params assoc :password (event-value e)))
             :on-confirm (fn [e]
                           (dispatch [:password-reset-clear-errors])
                           (swap! params assoc :verify-password (event-value e)))}]
           (when @(subscribe [:login-message-shown?])
             [:div.m-t-5.p-r-5.p-l-5 [notifications/message
                                      :error
                                      @(subscribe [:login-message])
                                      hide-login-message]])
           ]
          ;; Never dimmed, the same call the register form makes: a button that
          ;; looks dead reads as a broken site and cannot say why it will not
          ;; go. Pressing it always does something, and when the form is not
          ;; ready that something is showing the faults it has been sitting on.
          :tail [:button.form-button.form-submit-btn
                 {:on-click #(if invalid?
                               (swap! params assoc :attempted? true)
                               ((make-event-handler :password-reset @params)))}
                 "SUBMIT"]})))))

(defn login-link []
  [:span.underline.f-w-b.m-l-10.pointer.orange
   {:on-click dispatch-route-to-login}
   "LOGIN"])

(defn auth-outcome
  "A page with nothing to ask for: a statement, and at most one `onward` action.
   GOTCHA: `onward` is a button, not the inline link the register form uses --
   the one thing to do should look like it. See account-flows.md."
  ([heading message] (auth-outcome heading message nil))
  ([heading message onward]
   (auth-page heading
              [:div.auth-outcome
               [:div.auth-outcome-line message]
               (when onward [:div.auth-outcome-onward onward])])))

(defn- login-button []
  [:button.form-button.form-submit-btn
   {:on-click dispatch-route-to-login}
   "LOGIN"])

(defn verify-success []
  (auth-outcome
   "Success! Registration is complete"
   "Your account is ready."
   [login-button]))

(defn password-reset-success []
  (auth-outcome
   "Password changed"
   "Use the new one from now on."
   [login-button]))

(defn unsubscribe-success []
  (auth-outcome
   "Unsubscribed"
   [:div
    [:div "You will not get email updates from us any more."]
    [:div.m-t-10 "You can turn them back on whenever you like, from your account settings."]]))

(defn email-sent [text]
  (auth-outcome "Check your email" text))

(defn verify-sent []
  (email-sent
   [:div
    [:span "We sent a verification email to "]
    ;; Ink, not .red. Red is what a fault looks like on every one of these
    ;; pages, and this is the address somebody just typed correctly.
    [:span.auth-emphasis @(subscribe [:temp-email])]
    [:span ". You must verify to complete registration and the link we sent will only be valid for 24 hours."]
    [:span " "]
    [:span "Remember to check your spam folder."]]))

(defn password-reset-sent []
  ;; Conditional on purpose. The endpoint now answers identically for an address
  ;; with no account, so this page is no longer in a position to claim that an
  ;; email went out -- and claiming it would hand back the membership test the
  ;; server stopped answering.
  (email-sent
   (str "If "
        @(subscribe [:temp-email])
        " has an account, a link to reset your password is on its way.")))

(def ^:private field-order
  "The order the summary lists faults in, which is the order they appear on the
   form. Sorting by map key would list them however the keywords happen to hash."
  [[:username "Username"] [:email "Email address"] [:verify-email "Confirm email address"]
   [:password "Password"] [:verify-password "Confirm password"]
   [:first-and-last-name "Name"]])

(defn error-summary
  "A list of faults above the form, each a link to its field, after a submit
   that could not go through. Renders nothing until a submit has been attempted.
   GOTCHA: counts FIELDS, not messages (two faults on one field count as one),
   and each link's text is the fault message, not the field name -- prefixing
   the field name would say it twice, and the link text is what a screen
   reader reads on its own. See account-flows.md."
  [validation]
  (let [faults (for [[k label] field-order
                     :let [messages (seq (get validation k))]
                     :when messages]
                 [k label (first messages)])]
    (when (seq faults)
      [:div.auth-summary {:role "alert"}
       [:div.auth-summary-title
        (str (count faults) (if (= 1 (count faults))
                              " thing needs fixing"
                              " things need fixing"))]
       [:ul.auth-summary-list
        (for [[k label message] faults]
          ^{:key k}
          [:li
           [:a {:href (str "#f-" (name k))
                :on-click (fn [_]
                            (when-let [el (.getElementById js/document (str "f-" (name k)))]
                              (.focus el)))}
            message]])]])))

(defn register-form []
  (let [base-validation @(subscribe [:registration-validation])
        registration-form @(subscribe [:registration-form])
        show-errors? @(subscribe [:registration-attempted?])
        ;; The confirmation is checked HERE, not in the shared validator, which
        ;; runs on the server too and has no idea whether the field is currently
        ;; revealed -- and a revealed password has no confirmation to differ from.
        confirm-needed? (not (:password-revealed? registration-form))
        mismatch? (and confirm-needed?
                       (seq (:password registration-form))
                       (not= (:password registration-form)
                             (:verify-password registration-form)))
        registration-validation (cond-> base-validation
                                  mismatch?
                                  (update :verify-password conj "Passwords do not match"))
        send-updates? (not= false (:send-updates? registration-form))
        ]
    (auth-form-page
     {:heading "Join for free"
      :lede "Save your characters, share them, and pick up where you left off."
      :legal-links? false
      :fields
      [:div
       (when show-errors? [error-summary registration-validation])
       [form-input {:title "Username"
                    :key :username
                    :value (:username registration-form)
                    :messages (:username registration-validation)
                    :show-errors? show-errors?
                    :type :username
                    :on-change (fn [e] (dispatch [:registration-username (event-value e)]))}]
       (let [suggestion (registration/suggest-email-domain (:email registration-form))]
         [form-input {:title "Email"
                      :key :email
                      :value (:email registration-form)
                      :messages (:email registration-validation)
                      :show-errors? show-errors?
                      :type :email
                      :hint (when suggestion "That domain looks like a typo.")
                      :action (when suggestion
                                {:label (str "Use " suggestion)
                                 :on-choose #(dispatch [:registration-email suggestion])})
                      :on-change (fn [e] (dispatch [:registration-email (event-value e)]))}])
       [form-input {:title "Verify Email"
                    :key :verify-email
                    :value (:verify-email registration-form)
                    :messages (:verify-email registration-validation)
                    :show-errors? show-errors?
                    :type :email
                    :on-change (fn [e] (dispatch [:registration-verify-email (event-value e)]))}]
       [password-fields
        {:password (:password registration-form)
         :confirm (:verify-password registration-form)
         :context {:username (:username registration-form)
                   :email (:email registration-form)}
         :messages (:password registration-validation)
         :confirm-messages (:verify-password registration-validation)
         :show-errors? show-errors?
         :revealed? (boolean (:password-revealed? registration-form))
         :refused @(subscribe [:registration-password-common])
         :on-toggle #(dispatch [:registration-password-revealed?
                                (not (:password-revealed? registration-form))])
         :on-password (fn [e] (dispatch [:registration-password (event-value e)]))
         :on-confirm (fn [e] (dispatch [:registration-verify-password (event-value e)]))}]
       [:div.m-t-20.t-a-l.m-l-15
        [comps/labeled-checkbox (str "Yes! Send me updates about " branding/app-name) send-updates? false
         #(dispatch [:registration-send-updates? (not send-updates?)])]]
       [widget]
       ]
      :tail
      [:div
       (when-let [notice @(subscribe [:registration-notice])]
          [:div.m-t-10.registration-notice
           (for [line notice] ^{:key line} [:div line])])
        ;; Not .m-b-10: that margin utility is bundled into a rule that sets
        ;; font-weight bold across the whole app (styles/core.clj, .modal-container
        ;; group). Using it here quietly emboldened this sentence.
        [:div.m-t-10.m-b-5
         [:span "We will send a confirmation link to the address above."]]
        ;; Never dimmed. A submit button that looks dead reads as a broken site
        ;; rather than an unfinished form, and it cannot tell anyone WHY it will
        ;; not go -- so pressing it always does something, and when the form is
        ;; not ready that something is showing the faults it has been sitting on.
        [:button.form-button.join-button
         {:on-click #(if (empty? registration-validation)
                       (dispatch [:register])
                       (dispatch [:registration-attempted]))}
         "JOIN"]
       [:div.m-t-20.auth-alt
        [:span "Already have an account?"]
        (login-link)]
       ;; This page links both documents in its consent line, so the shell's
       ;; footer keeps the copyright and drops its copies of the same two links.
       [:div.auth-fineprint
        "By joining you agree to our "
        [:a {:href "/terms-of-use" :target :_blank} "Terms of Use"]
        " and confirm you have read our "
        [:a {:href "/privacy-policy" :target :_blank} "Privacy Policy"]
        "."]]})))

(defn route-to-register-page []
  (dispatch [:route routes/register-page-route {:secure true :no-return? true}]))

(defn route-to-reset-password-page []
  (dispatch [:route routes/send-password-reset-page-route {:secure? true :no-return? true}]))

(defn login-page []
  (let [params (r/atom {})]
    (fn []
      (let [login-message-shown? @(subscribe [:login-message-shown?])
            login-message @(subscribe [:login-message])]
        ;; .login-form-inputs was this page's own container, so its fields were
        ;; 350px where every other page's are the column's width, and the tail
        ;; nested inside it came out narrower than the fields above it. It is on
        ;; the same shape as the rest now.
        (auth-form-page
         {:heading "Welcome back"
          :fields
          [:div
           [form-input {:title "Username or Email"
                        :key :username
                        :value (:username @params)
                        :type :username
                        :on-change #(swap! params assoc :username (event-value %))}]
           [form-input {:title "Password"
                        :key :password
                        :value (:password @params)
                        :type :password
                        :on-change #(swap! params assoc :password (event-value %))}]
           ;; Only after a failed attempt: the server flags it, :login-failure sets this.
           (when @(subscribe [:login-challenge?]) [widget])
           (when login-message-shown?
             [:div.m-t-5 [notifications/message
                          :error
                          login-message
                          hide-login-message]])]
          :tail
          [:div
            [:button.form-button.join-button
             {:on-click #(dispatch [:login @params true])}
             "LOGIN"]
            ;; One group, one rhythm: each question and its answer share a line,
            ;; the way the register page already asks its one.
            [:div.m-t-20.auth-alts
             [:div.auth-alt
              [:span "Don't have a login?"]
              [:span.orange.underline.pointer
               {:on-click route-to-register-page}
               "REGISTER NOW"]]
             [:div.auth-alt
              [:span "Forgot your password?"]
              [:span.orange.underline.pointer
               {:on-click route-to-reset-password-page}
               "RESET PASSWORD"]]]
            ;; Kept apart from the pair above: it is not another way in, it is
            ;; what to do when the way in never arrived.
            [:div.auth-help
             [:span "Didn't receive the validation email? Check your spam folder, or "]
             [:a.orange {:href "/help/im-not-getting-my-signup-password-reset-email/" :target "_blank"}
              "read how to let our email through"]
             [:span "."]]]})))))
