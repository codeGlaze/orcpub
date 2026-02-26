(ns orcpub.branding
  "Centralized branding configuration for fork-neutral deployment.
   All values have sensible defaults; forks override via env vars.

   Server-side only (.clj). Client-side branding in views.cljs reads
   these values indirectly via server-rendered HTML (splash page, OG tags)
   or can be centralized separately for CLJS in a future pass."
  (:require [environ.core :refer [env]]))

;; ─── App Identity ──────────────────────────────────────────────────

(def app-name
  "Full display name. Used in emails, OG tags, page titles."
  (or (env :app-name) "Dungeon Master's Vault"))

(def app-tagline
  "One-line description for OG/meta tags."
  (or (env :app-tagline)
      "Dungeons & Dragons 5th Edition (D&D 5e) character builder/generator and digital character sheet far beyond any other in the multiverse."))

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
  "Copyright year string."
  (or (env :app-copyright-year) "2025"))

;; ─── Email ─────────────────────────────────────────────────────────

(def email-sender-name
  "Display name for outbound emails (verification, password reset)."
  (or (env :app-email-sender-name) "Dungeon Master's Vault Team"))

;; ─── Social Links ──────────────────────────────────────────────────
;; Set any of these to "" to hide the link in the UI.

(def social-links
  "Map of social platform links. Empty string = hidden."
  {:patreon  (or (env :app-social-patreon)  "https://www.patreon.com/DungeonMastersVault")
   :facebook (or (env :app-social-facebook) "https://www.facebook.com/groups/252484128656613/")
   :twitter  (or (env :app-social-twitter)  "https://twitter.com/thdmv")
   :reddit   (or (env :app-social-reddit)   "")
   :discord  (or (env :app-social-discord)  "")})
