(ns orcpub.fork.branding
  "Centralized branding configuration for fork-neutral deployment.
   All values have sensible defaults; forks override via env vars.

   Server-side (.clj) is the source of truth. Client-side branding
   is delivered via the config bridge: index.clj injects client-config
   as window.__BRANDING__ JSON in <head>, and branding.cljs reads it."
  (:require [orcpub.env :as env])
  (:import [java.time Year]))

;; ─── App Identity ──────────────────────────────────────────────────

(def app-name
  "Full display name. Used in emails, OG tags, page titles."
  (env/value :app-name "OrcPub"))

(def app-tagline
  "One-line description for OG/meta tags."
  (env/value :app-tagline "D&D 5e character builder/generator and digital character sheet far beyond any other in the multiverse."))

(def app-url
  "Primary application URL for legal pages and external references. Empty = hidden."
  (env/checked-url :app-url (env/value :app-url "") ""))

(def default-page-title
  "Default <title> and og:title when no page-specific title is set."
  (env/value :app-page-title (str app-name ": D&D 5e Character Builder/Generator")))

;; ─── Logos & Images ────────────────────────────────────────────────

(def logo-path
  "Path to the main SVG logo (splash page, header, privacy page)."
  (env/checked-url :app-logo-path (env/value :app-logo-path "/image/orcpub-logo.svg") "/image/orcpub-logo.svg"))

(def og-image-filename
  "Filename for the OG meta image (social sharing preview).
   Combined with the request host to form the full URL."
  (env/checked-url :app-og-image (env/value :app-og-image "/image/orcpub-logo.png") "/image/orcpub-logo.png"))

;; ─── Copyright ─────────────────────────────────────────────────────

(def copyright-holder
  "Entity name shown in legal footer."
  (env/value :app-copyright-holder "OrcPub"))

(def copyright-year
  "Copyright year string. Defaults to the current year."
  (env/value :app-copyright-year (str (.getValue (Year/now)))))

;; ─── Email ─────────────────────────────────────────────────────────

(def email-sender-name
  "Display name for outbound emails (verification, password reset)."
  (env/value :app-email-sender-name (str app-name " Team")))

(def email-signoff
  "Who the warmer emails (the artist welcome) are signed from. Defaults to
   'The <sender name>'; a small team may prefer something personal, e.g.
   'both of us at OrcPub'."
  (env/value :app-email-signoff (str "The " email-sender-name)))

(def email-from-address
  "From address for outbound emails (verification, password reset, reports).
   Self-hosters MUST set EMAIL_FROM_ADDRESS to an address on a domain they
   control and have verified with their mail provider (SES/SMTP), or outbound
   mail will be rejected. The fallback is an RFC-2606 reserved placeholder — it
   is intentionally non-deliverable so a misconfigured instance fails loudly
   instead of silently sending from a real domain it does not own."
  (env/checked-email :email-from-address (env/value :email-from-address "no-reply@example.com") "no-reply@example.com"))

;; ─── Support & Help ──────────────────────────────────────────────

(def support-email
  "Contact email shown on privacy page, error messages, etc. Empty = hidden."
  (env/checked-email :app-support-email (env/value :app-support-email "") ""))

(def report-recipient
  "Where user-submitted 'character won't load' reports are emailed. Prefers the
   public support address (APP_SUPPORT_EMAIL); falls back to the admin error inbox
   (EMAIL_ERRORS_TO) that the app's error notifications already use — so an operator
   who has configured error reporting needs no new setting. Empty (neither set) =
   reports can't send and the UI offers the copyable report instead."
  (if (seq support-email) support-email (env/checked-email :email-errors-to (env/value :email-errors-to "") "")))

(def help-url
  "URL for the help/FAQ page. Empty string = hidden."
  (env/checked-url :app-help-url (env/value :app-help-url "") ""))

;; ─── Social Links ──────────────────────────────────────────────────
;; Each link shows in the header/footer when its env var is set to a URL; unset falls back to the default.
;; e.g. in .env:  APP_SOCIAL_PATREON=https://www.patreon.com/YourProject
;;                APP_SOCIAL_DEV_TIP=https://buymeacoffee.com/you  (a one-off tip to the developer)

(def social-link-defaults
  "The links shown when their setting is unset: the maintainer's Patreon and tip link."
  {:patreon "https://www.patreon.com/DungeonMastersVault"
   :dev-tip "https://buymeacoffee.com/tinkan"})

(def social-links
  "Map of social platform links: each APP_SOCIAL_* setting, else `social-link-defaults`, else empty
   (hidden). A blank setting counts as unset, so it cannot hide a default; set another URL."
  (into {} (for [k [:patreon :dev-tip :facebook :bluesky :twitter :reddit :discord]
                 :let [setting (keyword (str "app-social-" (name k)))
                       default (get social-link-defaults k "")]]
             [k (env/checked-url setting (env/value setting default) default)])))

;; ─── Footer ─────────────────────────────────────────────────────

(def copyright-url
  "URL for copyright holder name in footer. Empty string = plain text."
  (env/checked-url :app-copyright-url (env/value :app-copyright-url "") ""))

;; ─── UI Behavior ────────────────────────────────────────────────

(def registration-logo-class
  "CSS class for logo on registration/login page."
  "h-55")

(def restrict-print-to-owner?
  "Whether the print button on character list is restricted to the character owner."
  false)

;; ─── Field Limits ────────────────────────────────────────────────
;; Input field max-length constraints for form validation.

(def field-limits
  "Max-length constraints for form input fields."
  {:notes  (env/checked-int :app-field-limit-notes (env/value :app-field-limit-notes) 50000)
   :text   (env/checked-int :app-field-limit-text (env/value :app-field-limit-text) 255)
   :number (env/checked-int :app-field-limit-number (env/value :app-field-limit-number) 7)})
