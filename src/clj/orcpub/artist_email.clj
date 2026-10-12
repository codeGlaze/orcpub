(ns orcpub.artist-email
  "The emails artist accounts send: the welcome (account made for them), the
   upgrade (their existing account linked), a notice when an account stops
   speaking for an artist, and a copy of each to the site's admin inbox.

   Written to sound like the two people who run the site, not like a system:
   a nickname greeting, an optional personal note per artist, and a sign-off
   from APP_EMAIL_SIGNOFF. The per-artist parts come from that artist's entry
   in PORTRAIT_ARTISTS -- preferred_name, welcome_note, welcome_subject -- so
   the welcome is written once, where the artist is added.

   Links are built from APP_URL, not from a request: these are sent at startup,
   when there is no request. Without APP_URL, or with email switched off, they
   are logged and skipped rather than sent with broken links.

   Built the way email has to be: tables and inline styles, fonts every mail
   client has, and a plain-text part alongside the HTML. Message builders are
   pure so they can be tested and previewed without sending."
  (:require [clojure.string :as s]
            [hiccup2.core :as hiccup]
            [postal.core :as postal]
            [orcpub.env :as env]
            [orcpub.crypto :as crypto]
            [orcpub.email :as email]
            [orcpub.fork.branding :as branding]
            [orcpub.route-map :as routes]
            [orcpub.dnd.e5.portrait-assets :as pa])
  (:import [java.time ZoneOffset]
           [java.time.format DateTimeFormatter]
           [java.util Locale]))

;; ---------------------------------------------------------------------------
;; Settings
;; ---------------------------------------------------------------------------

(defn base-url
  "The public site address, without a trailing slash, or nil."
  []
  (some-> branding/app-url not-empty (s/replace #"/+$" "")))

(defn admin-recipient
  "Where artist-account notices go: EMAIL_ADMIN_TO, else the error inbox."
  []
  (or (env/value :email-admin-to) (env/value :email-errors-to)))

(defn- email-on? [] (boolean (env/value :email-server-url)))

(defn reset-url [base key]
  (str base (routes/path-for routes/reset-password-page-route) "?key=" key))

(defn credit-url [base]
  (str base (routes/path-for routes/my-account-page-route) "#artist-credit"))

(defn- day-month [^java.util.Date d]
  (.format (DateTimeFormatter/ofPattern "d MMMM" Locale/ENGLISH)
           (.atZone (.toInstant d) ZoneOffset/UTC)))

(defn greeting-name
  "What to call them: their preferred name (from the config at creation, or
   decrypted from the account), else the artist's credited name, else the
   username."
  [{:keys [artist-id user spec]}]
  (or (not-empty (:preferred-name spec))
      (crypto/decrypt crypto/preferred-name-purpose (:orcpub.user/preferred-name user))
      (:artist/name (pa/artist-info artist-id))
      (:orcpub.user/username user)))

;; ---------------------------------------------------------------------------
;; Pieces
;; ---------------------------------------------------------------------------

(def ^:private sans "-apple-system, 'Segoe UI', Helvetica, Arial, sans-serif")
(def ^:private serif "Georgia, 'Times New Roman', serif")

(defn- p [& content]
  (into [:p {:style (str "margin:0 0 14px;font:15px/1.6 " sans ";color:#2a2f36")}] content))

(defn- small [& content]
  (into [:p {:style (str "margin:0 0 12px;font:13px/1.55 " sans ";color:#6b7280")}] content))

(defn- button [href label]
  [:table {:role "presentation" :cellpadding "0" :cellspacing "0" :border "0" :style "margin:26px 0 12px"}
   [:tr [:td {:style "background:#e8980a;border-radius:6px"}
         [:a {:href href :style (str "display:inline-block;padding:13px 26px;font:700 15px/1 " sans
                                     ";color:#1f1604;text-decoration:none;border-radius:6px")}
          label]]]])

(defn- credit-panel [artist-name]
  (let [rule [:td {:valign "middle"}
              [:div {:style "height:1px;background:#6b5420;font-size:0;line-height:0"} (hiccup/raw "&nbsp;")]]]
    [:table {:role "presentation" :width "100%" :cellpadding "0" :cellspacing "0" :border "0"
             :style "margin:14px 0 24px;background:#0e131a;border-radius:8px"}
     [:tr [:td {:style "padding:18px 16px 20px;text-align:center"}
           [:div {:style (str "font:600 10px/1 " sans ";letter-spacing:.18em;text-transform:uppercase;color:#7b8494")}
            "Art by"]
           [:table {:role "presentation" :width "100%" :cellpadding "0" :cellspacing "0" :border "0" :style "margin-top:10px"}
            [:tr rule
             [:td {:valign "middle" :style (str "width:1%;white-space:nowrap;padding:0 14px;font:italic 19px/1.2 "
                                                serif ";color:#ebeef4")}
              artist-name]
             rule]]]]]))

(defn- shell [inner footer]
  [:table {:role "presentation" :width "100%" :cellpadding "0" :cellspacing "0" :border "0" :style "background:#eceef2"}
   [:tr [:td {:align "center" :style "padding:26px 12px"}
         [:table {:role "presentation" :width "100%" :cellpadding "0" :cellspacing "0" :border "0"
                  :style "max-width:560px;background:#ffffff;border-radius:10px;border:1px solid #e1e4ea"}
          [:tr [:td {:style "padding:30px 32px 10px"}
                [:div {:style (str "font:700 11px/1 " sans ";letter-spacing:.24em;text-transform:uppercase;color:#b87400;margin-bottom:22px")}
                 branding/app-name]
                inner]]
          (when footer
            [:tr [:td {:style "padding:16px 32px 26px;border-top:1px solid #eef0f3"} footer]])]]]])

(defn- html [& content] (str (hiccup/html (into [:div] content))))

(defn- message [to subject text html-body]
  {:from (str branding/email-sender-name " <" (email/emailfrom) ">")
   :to to
   :subject subject
   :body [:alternative
          {:type "text/plain; charset=utf-8" :content text}
          {:type "text/html; charset=utf-8" :content html-body}]})

(defn- can-do-html [artist-name]
  (list
   (p [:b "It's your corner of the site."] " From it you can:")
   [:ul {:style (str "margin:0 0 18px;padding-left:20px;font:15px/1.7 " sans ";color:#2a2f36")}
    [:li "change the name your art is credited under"]
    [:li "choose where your name links to: your site, your Twitch, wherever you like"]
    [:li "pick which link icons appear beside your name"]]
   (p "And every portrait people build with your pieces carries your name, right under the picture:")
   (credit-panel artist-name)))

(defn- can-do-text [artist-name]
  (str "It's your corner of the site. From it you can:\n"
       "  - change the name your art is credited under\n"
       "  - choose where your name links to: your site, your Twitch, wherever you like\n"
       "  - pick which link icons appear beside your name\n\n"
       "And every portrait people build with your pieces carries your name,\n"
       "right under the picture:\n\n"
       "    ART BY\n"
       "    ----- " artist-name " -----\n"))

(defn- default-note [] (str "Welcome aboard! Your art is now part of the "
                            branding/app-name " portrait builder."))

(defn- why-footer [artist-name]
  (str "You're getting this because someone who runs " branding/app-name
       " listed this address as the contact for the artist " artist-name "."))

;; ---------------------------------------------------------------------------
;; Messages (pure)
;; ---------------------------------------------------------------------------

(defn welcome-message
  "A new account was made for them: set a password to get started."
  [base {:keys [artist-id user spec reset-key expires] :as event}]
  (let [who (greeting-name event)
        artist-name (or (:artist/name (pa/artist-info artist-id)) who)
        note (or (not-empty (:welcome-note spec)) (default-note))
        url (reset-url base reset-key)
        username (:orcpub.user/username user)
        until (day-month expires)
        subject (or (not-empty (:welcome-subject spec))
                    (str "Welcome to " branding/app-name ", " who "! Your artist account is ready"))]
    (message
     (:orcpub.user/email user) subject
     (str "Hey " who "!\n\n" note "\n\n"
          "We've set you up with a brand new account type, just for artists!\n\n"
          "You can set your password here:\n" url "\n\n"
          "Your username is " username ". This link works once and expires on " until ".\n\n"
          (can-do-text artist-name) "\n"
          "If the link has expired, use \"Forgot password\" on the login page with this email address.\n\n"
          "We can't wait to see the characters people make with your work.\n\n"
          "Thank you for being part of this,\n" branding/email-signoff "\n\n"
          "P.S. If this landed with the wrong person, just reply and we'll sort it out.\n\n"
          "--\n" (why-footer artist-name))
     (html
      (shell
       (list
        [:h1 {:style (str "margin:0 0 16px;font:italic 26px/1.2 " serif ";color:#1c2027;font-weight:normal")}
         (str "Hey " who "!")]
        (p note)
        (p "We've set you up with a brand new account type, just for artists!")
        (button url "You can set your password here")
        (small "Your username is " [:b {:style "color:#2a2f36"} username]
               ". This link works once and expires on " until ".")
        [:div {:style "height:10px"}]
        (can-do-html artist-name)
        (p "If the link has expired, use " [:b "Forgot password"]
           " on the login page with this email address.")
        (p "We can't wait to see the characters people make with your work.")
        [:p {:style (str "margin:22px 0 18px;font:15px/1.6 " sans ";color:#2a2f36")}
         "Thank you for being part of this," [:br]
         [:span {:style (str "font-style:italic;font-family:" serif)} branding/email-signoff]]
        (small "P.S. If this landed with the wrong person, just reply and we'll sort it out."))
       (list
        (small "If the button doesn't work, paste this into your browser:" [:br]
               [:a {:href url :style "color:#9a6400;word-break:break-all"} url])
        (small (why-footer artist-name))))))))

(defn upgrade-message
  "Their existing account now speaks for them as an artist."
  [base {:keys [artist-id user spec] :as event}]
  (let [who (greeting-name event)
        artist-name (or (:artist/name (pa/artist-info artist-id)) who)
        note (or (not-empty (:welcome-note spec)) (default-note))
        url (credit-url base)
        username (:orcpub.user/username user)
        subject (or (not-empty (:welcome-subject spec))
                    (str "Welcome aboard, " who "! You're now an artist on " branding/app-name))]
    (message
     (:orcpub.user/email user) subject
     (str "Hey " who "!\n\n" note "\n\n"
          "We've upgraded your account (" username ") to a brand new account type, just for artists!\n\n"
          "Edit your credit here:\n" url "\n\n"
          (can-do-text artist-name) "\n"
          "We can't wait to see the characters people make with your work.\n\n"
          "Thank you for being part of this,\n" branding/email-signoff "\n\n"
          "P.S. If this landed with the wrong person, just reply and we'll sort it out.\n\n"
          "--\n" (why-footer artist-name))
     (html
      (shell
       (list
        [:h1 {:style (str "margin:0 0 16px;font:italic 26px/1.2 " serif ";color:#1c2027;font-weight:normal")}
         (str "Hey " who "!")]
        (p note)
        (p "We've upgraded your account (" [:b username] ") to a brand new account type, just for artists!")
        (button url "Edit your credit")
        [:div {:style "height:10px"}]
        (can-do-html artist-name)
        (p "We can't wait to see the characters people make with your work.")
        [:p {:style (str "margin:22px 0 18px;font:15px/1.6 " sans ";color:#2a2f36")}
         "Thank you for being part of this," [:br]
         [:span {:style (str "font-style:italic;font-family:" serif)} branding/email-signoff]]
        (small "P.S. If this landed with the wrong person, just reply and we'll sort it out."))
       (small (why-footer artist-name)))))))

(defn unlinked-message
  "Their account no longer speaks for this artist."
  [{:keys [artist-id user]}]
  (let [artist-name (or (:artist/name (pa/artist-info artist-id)) (name artist-id))]
    (message
     (:orcpub.user/email user)
     (str "Your " branding/app-name " account is no longer an artist account")
     (str "Hi,\n\nYour " branding/app-name " account (" (:orcpub.user/username user)
          ") is no longer linked to the artist " artist-name
          ". Everything else about your account is unchanged.\n\n"
          "If you didn't expect this, just reply and we'll look into it.\n\n"
          branding/email-signoff)
     (html
      (shell
       (list
        (p "Hi,")
        (p "Your " branding/app-name " account (" [:b (:orcpub.user/username user)]
           ") is no longer linked to the artist " artist-name
           ". Everything else about your account is unchanged.")
        (p "If you didn't expect this, just reply and we'll look into it.")
        (p [:span {:style (str "font-style:italic;font-family:" serif)} branding/email-signoff]))
       nil)))))

(defn admin-message
  "The admin's copy of any change: what happened, to whom, what was sent."
  [kind {:keys [artist-id user expires]}]
  (let [artist-name (or (:artist/name (pa/artist-info artist-id)) (name artist-id))
        [subject lead] (case kind
                         :created [(str "New artist on board: " artist-name)
                                   (str artist-name " is on board. Their artist account is set up and the welcome email is on its way.")]
                         :upgraded [(str "New artist on board: " artist-name)
                                    (str artist-name "'s existing account is now an artist account, and they've been told.")]
                         :unlinked [(str "Artist account unlinked: " artist-name)
                                    (str "An account no longer speaks for " artist-name ", and its owner has been told.")])
        rows (cond-> [["Artist" (str artist-name " (" (name artist-id) ")")]
                      ["Email" (:orcpub.user/email user)]
                      ["Username" (:orcpub.user/username user)]]
               expires (conj ["Sent" (str "Set-password link, expires " (day-month expires))])
               (= kind :upgraded) (conj ["Sent" "Upgrade email with a link to their credit settings"]))
        how (str "To remove it, set \"account\": null for this artist in PORTRAIT_ARTISTS and restart the service.")]
    (message
     (admin-recipient) subject
     (str lead "\n\n" (s/join "\n" (for [[k v] rows] (str k ": " v)))
          (when (not= kind :unlinked) (str "\n\n" how)))
     (html
      (shell
       (list
        (p [:b lead])
        [:table {:role "presentation" :cellpadding "0" :cellspacing "0" :border "0"
                 :style (str "margin:4px 0 16px;font:14px/1.7 " sans ";color:#2a2f36")}
         (for [[k v] rows]
           [:tr [:td {:style "color:#6b7280;padding-right:18px"} k] [:td v]])]
        (when (not= kind :unlinked) (small how)))
       nil)))))

;; ---------------------------------------------------------------------------
;; Credit changes
;; ---------------------------------------------------------------------------

(defn credit-rows
  "A credit as [label value] rows, for showing before and after."
  [credit]
  (let [{:artist/keys [name link links]} credit]
    (concat [["Name" (or name "(default)")]
             ["Link" (or link "(default)")]]
            (if (seq links)
              (for [{:link/keys [label url]} links] [label url])
              [["Icons" "(default)"]]))))

(defn- rows-table [rows]
  [:table {:role "presentation" :cellpadding "0" :cellspacing "0" :border "0"
           :style (str "margin:4px 0 16px;font:14px/1.7 " sans ";color:#2a2f36")}
   (for [[k v] rows]
     [:tr [:td {:style "color:#6b7280;padding-right:18px;vertical-align:top"} k]
      [:td {:style "word-break:break-all"} v]])])

(defn- rows-text [rows]
  (s/join "\n" (for [[k v] rows] (str "  " k ": " v))))

(defn credit-changed-message
  "To the artist: their credit was changed, so a change they didn't make is
   noticed by the one person who would know."
  [base {:keys [artist-id user after]}]
  (let [artist-name (or (:artist/name (pa/artist-info artist-id)) (name artist-id))
        who (greeting-name {:artist-id artist-id :user user})
        rows (credit-rows after)
        settings (when base (credit-url base))]
    (message
     (:orcpub.user/email user)
     (str "Your " branding/app-name " credit was updated")
     (str "Hi " who ",\n\n"
          "Your credit as " artist-name " was just updated. It now reads:\n\n"
          (rows-text rows) "\n\n"
          (when settings (str "You can change it any time: " settings "\n\n"))
          "If this wasn't you, reply to this email and we'll put it back and lock it.\n\n"
          branding/email-signoff)
     (html
      (shell
       (list
        (p "Hi " who ",")
        (p "Your credit as " [:b artist-name] " was just updated. It now reads:")
        (rows-table rows)
        (when settings (p "You can change it any time from " [:a {:href settings} "your account"] "."))
        (p [:b "If this wasn't you,"] " reply to this email and we'll put it back and lock it.")
        (p [:span {:style (str "font-style:italic;font-family:" serif)} branding/email-signoff]))
       nil)))))

(defn credit-admin-message
  "The admin's copy: before and after, and how to overrule it."
  [{:keys [artist-id user before after]}]
  (let [artist-name (or (:artist/name (pa/artist-info artist-id)) (name artist-id))
        lead (str (:orcpub.user/username user) " updated the credit for " artist-name ".")
        how (str "To overrule it, set the field for " (name artist-id)
                 " in PORTRAIT_ARTISTS; the config always wins over an artist's own edits.")]
    (message
     (admin-recipient)
     (str "Artist credit changed: " artist-name)
     (str lead "\n\nBefore:\n" (rows-text (credit-rows before))
          "\n\nAfter:\n" (rows-text (credit-rows after)) "\n\n" how)
     (html
      (shell
       (list
        (p [:b lead])
        (small "Before")
        (rows-table (credit-rows before))
        (small "After")
        (rows-table (credit-rows after))
        (small how))
       nil)))))

;; ---------------------------------------------------------------------------
;; Sending
;; ---------------------------------------------------------------------------

(defn- send! [msg]
  (try
    (let [result (postal/send-message (email/email-cfg) msg)]
      (when (not= :SUCCESS (:error result))
        (println "artist-email: not sent to" (:to msg) "-" (:error result))))
    (catch Exception e
      (println "artist-email: failed to send to" (:to msg) "-" (.getMessage e)))))

(defn- deliver! [kind artist-msg event]
  (let [who (get-in event [:user :orcpub.user/username])]
    (cond
      (not (email-on?))
      (println "artist-email: email is off; not sending the" (name kind) "email for" who)

      :else
      (do (when artist-msg (send! artist-msg))
          (if (admin-recipient)
            (send! (admin-message kind event))
            (println "artist-email: no EMAIL_ADMIN_TO or EMAIL_ERRORS_TO; admin copy not sent"))))))

(def notify
  "The notifier artist accounts use on the live site."
  {:created (fn [event]
              (if-let [base (base-url)]
                (deliver! :created (welcome-message base event) event)
                (println "artist-email: APP_URL is not set, so the welcome email for"
                         (get-in event [:user :orcpub.user/username])
                         "was not sent. They can use \"Forgot password\" with their email.")))
   :upgraded (fn [event]
               (if-let [base (base-url)]
                 (deliver! :upgraded (upgrade-message base event) event)
                 (println "artist-email: APP_URL is not set; upgrade email not sent")))
   :unlinked (fn [event] (deliver! :unlinked (unlinked-message event) event))})

(defn credit-changed!
  "Tell the artist and the admin that a credit changed. Unlike the startup
   emails this needs no APP_URL -- the message reads fine without the link."
  [event]
  (if-not (email-on?)
    (println "artist-email: email is off; credit change for"
             (get-in event [:user :orcpub.user/username]) "not announced")
    (do (send! (credit-changed-message (base-url) event))
        (if (admin-recipient)
          (send! (credit-admin-message event))
          (println "artist-email: no EMAIL_ADMIN_TO or EMAIL_ERRORS_TO; admin copy not sent")))))
