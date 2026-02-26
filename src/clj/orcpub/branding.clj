(ns orcpub.branding
  "Centralized branding configuration for fork-neutral deployment.
   All values have sensible defaults; forks override via env vars.

   Server-side (.clj) is the source of truth. Client-side branding
   is delivered via the config bridge: index.clj injects client-config
   as window.__BRANDING__ JSON in <head>, and branding.cljs reads it."
  (:require [environ.core :refer [env]]))

;; ─── App Identity ──────────────────────────────────────────────────

(def app-name
  "Full display name. Used in emails, OG tags, page titles."
  (or (env :app-name) "OrcPub"))

(def app-tagline
  "One-line description for OG/meta tags."
  (or (env :app-tagline)
      "D&D 5e character builder/generator and digital character sheet far beyond any other in the multiverse."))

(def default-page-title
  "Default <title> and og:title when no page-specific title is set."
  (or (env :app-page-title)
      (str app-name ": D&D 5e Character Builder/Generator")))

;; ─── Logos & Images ────────────────────────────────────────────────

(def logo-path
  "Path to the main SVG logo (splash page, header, privacy page)."
  (or (env :app-logo-path) "/image/orcpub-logo.svg"))

(def og-image-filename
  "Filename for the OG meta image (social sharing preview).
   Combined with the request host to form the full URL."
  (or (env :app-og-image) "/image/orcpub-logo.png"))

;; ─── Copyright ─────────────────────────────────────────────────────

(def copyright-holder
  "Entity name shown in legal footer."
  (or (env :app-copyright-holder) "OrcPub"))

(def copyright-year
  "Copyright year string."
  (or (env :app-copyright-year) "2025"))

;; ─── Email ─────────────────────────────────────────────────────────

(def email-sender-name
  "Display name for outbound emails (verification, password reset)."
  (or (env :app-email-sender-name) (str app-name " Team")))

(def email-from-address
  "From address for outbound emails. Falls back to env EMAIL_FROM_ADDRESS."
  (or (env :email-from-address) "no-reply@orcpub.com"))

;; ─── Support & Help ──────────────────────────────────────────────

(def support-email
  "Contact email shown on privacy page, error messages, etc. Empty = hidden."
  (or (env :app-support-email) ""))

(def help-url
  "URL for the help/FAQ page. Empty string = hidden."
  (or (env :app-help-url) ""))

;; ─── Social Links ──────────────────────────────────────────────────
;; Set any of these to "" to hide the link in the UI.

(def social-links
  "Map of social platform links. Empty string = hidden."
  {:patreon  (or (env :app-social-patreon)  "")
   :facebook (or (env :app-social-facebook) "")
   :twitter  (or (env :app-social-twitter)  "")
   :reddit   (or (env :app-social-reddit)   "")
   :discord  (or (env :app-social-discord)  "")})

;; ─── Client-Side Config Bridge ───────────────────────────────────
;; index.clj injects this as window.__BRANDING__ JSON in <head>.
;; branding.cljs reads it at runtime for CLJS components.

(defn client-config
  "Map of branding values for CLJS injection. Serialized to JSON by index.clj."
  []
  {:app-name         app-name
   :logo-path        logo-path
   :copyright-holder copyright-holder
   :copyright-year   copyright-year
   :support-email    support-email
   :help-url         help-url
   :social-links     social-links})
