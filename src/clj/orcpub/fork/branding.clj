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
  (env/value :app-url ""))

(def default-page-title
  "Default <title> and og:title when no page-specific title is set."
  (env/value :app-page-title (str app-name ": D&D 5e Character Builder/Generator")))

;; ─── Logos & Images ────────────────────────────────────────────────

(def logo-path
  "Path to the main SVG logo (splash page, header, privacy page)."
  (env/value :app-logo-path "/image/orcpub-logo.svg"))

(def og-image-filename
  "Filename for the OG meta image (social sharing preview).
   Combined with the request host to form the full URL."
  (env/value :app-og-image "/image/orcpub-logo.png"))

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

(def email-from-address
  "From address for outbound emails. Falls back to env EMAIL_FROM_ADDRESS."
  (env/value :email-from-address "no-reply@orcpub.com"))

;; ─── Support & Help ──────────────────────────────────────────────

(def support-email
  "Contact email shown on privacy page, error messages, etc. Empty = hidden."
  (env/value :app-support-email ""))

(def help-url
  "URL for the help/FAQ page. Empty string = hidden."
  (env/value :app-help-url ""))

;; ─── Social Links ──────────────────────────────────────────────────
;; Each link appears in the header/footer when non-empty.
;; Set the corresponding env var to a URL to enable, or leave unset to hide.
;; e.g. in .env:  APP_SOCIAL_PATREON=https://www.patreon.com/YourProject
;;                APP_SOCIAL_DISCORD=https://discord.gg/your-invite

(def social-links
  "Map of social platform links. Empty string = hidden."
  {:patreon  (env/value :app-social-patreon "")
   :facebook (env/value :app-social-facebook "")
   :bluesky  (env/value :app-social-bluesky "")
   :twitter  (env/value :app-social-twitter "")
   :reddit   (env/value :app-social-reddit "")
   :discord  (env/value :app-social-discord "")})

;; ─── Footer ─────────────────────────────────────────────────────

(def copyright-url
  "URL for copyright holder name in footer. Empty string = plain text."
  (env/value :app-copyright-url ""))

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
  {:notes  (or (some-> (env/value :app-field-limit-notes) Integer/parseInt) 50000)
   :text   (or (some-> (env/value :app-field-limit-text) Integer/parseInt) 255)
   :number (or (some-> (env/value :app-field-limit-number) Integer/parseInt) 7)})

;; ─── Client-Side Config Bridge ───────────────────────────────────
;; index.clj injects this as window.__BRANDING__ JSON in <head>.
;; branding.cljs reads it at runtime for CLJS components.

(defn client-config
  "Map of branding values for CLJS injection. Serialized to JSON by index.clj."
  []
  {:app-name                  app-name
   :logo-path                 logo-path
   :copyright-holder          copyright-holder
   :copyright-year            copyright-year
   :copyright-url             copyright-url
   :support-email             support-email
   :help-url                  help-url
   :social-links              social-links
   :field-limits              field-limits
   :registration-logo-class   registration-logo-class
   :restrict-print-to-owner?  restrict-print-to-owner?})
