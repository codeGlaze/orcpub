(ns orcpub.fork.branding
  "Centralized branding configuration for fork-neutral deployment.
   All values have sensible defaults; forks override via env vars.

   Server-side (.clj) is the source of truth. Client-side branding
   is delivered via the config bridge: index.clj injects client-config
   as window.__BRANDING__ JSON in <head>, and branding.cljs reads it."
  (:require [environ.core :refer [env]])
  (:import [java.time Year]))

;; ─── App Identity ──────────────────────────────────────────────────

(def app-name
  "Full display name. Used in emails, OG tags, page titles."
  (or (env :app-name) "Dungeon Master's Vault"))

(def app-tagline
  "One-line description for OG/meta tags."
  (or (env :app-tagline)
      "Dungeons & Dragons 5th Edition (D&D 5e) character builder/generator and digital character sheet far beyond any other in the multiverse."))

(def app-url
  "Primary application URL for legal pages and external references."
  (or (env :app-url) "https://www.dungeonmastersvault.com"))

(def default-page-title
  "Default <title> and og:title when no page-specific title is set."
  (or (env :app-page-title)
      (str app-name ": D&D 5e Character Builder/Generator")))

;; ─── Logos & Images ────────────────────────────────────────────────

(def logo-path
  "Path to the main SVG logo (splash page, header, privacy page)."
  (or (env :app-logo-path) "/image/dmv-logo.svg"))

(def og-image-filename
  "Filename for the OG meta image (social sharing preview).
   Combined with the request host to form the full URL."
  (or (env :app-og-image) "/image/dmv-box-logo.png"))

;; ─── Copyright ─────────────────────────────────────────────────────

(def copyright-holder
  "Entity name shown in legal footer."
  (or (env :app-copyright-holder) "Dungeon Master's Vault"))

(def copyright-year
  "Copyright year string. Defaults to the current year."
  (or (env :app-copyright-year) (str (.getValue (Year/now)))))

;; ─── Email ─────────────────────────────────────────────────────────

(def email-sender-name
  "Display name for outbound emails (verification, password reset)."
  (or (env :app-email-sender-name) "Dungeon Master's Vault Team"))

(def email-from-address
  "From address for outbound emails. Falls back to env EMAIL_FROM_ADDRESS."
  (or (env :email-from-address) "no-reply@dungeonmastersvault.com"))

;; ─── Support & Help ──────────────────────────────────────────────

(def support-email
  "Contact email shown on privacy page, error messages, etc."
  (or (env :app-support-email) "thDM@dungeonmastersvault.com"))

(def help-url
  "URL for the help/FAQ page. Empty string = hidden."
  (or (env :app-help-url) "https://www.dungeonmastersvault.com/help/"))

;; ─── Social Links ──────────────────────────────────────────────────
;; Set any of these to "" to hide the link in the UI.

(def social-links
  "Map of social platform links. Empty string = hidden."
  {:patreon  (or (env :app-social-patreon)  "https://www.patreon.com/DungeonMastersVault")
   :facebook (or (env :app-social-facebook) "https://www.facebook.com/groups/252484128656613/")
   :bluesky  (or (env :app-social-bluesky)  "")
   :twitter  (or (env :app-social-twitter)  "https://twitter.com/thdmv")
   :reddit   (or (env :app-social-reddit)   "")
   :discord  (or (env :app-social-discord)  "")})

;; ─── Field Limits ────────────────────────────────────────────────
;; Input field max-length constraints for form validation.

(def field-limits
  "Max-length constraints for form input fields."
  {:notes  (or (some-> (env :app-field-limit-notes) Integer/parseInt) 50000)
   :text   (or (some-> (env :app-field-limit-text) Integer/parseInt) 255)
   :number (or (some-> (env :app-field-limit-number) Integer/parseInt) 7)})

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
   :social-links     social-links
   :field-limits     field-limits})
