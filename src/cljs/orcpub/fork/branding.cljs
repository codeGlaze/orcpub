(ns orcpub.fork.branding
  "Client-side branding config. Reads server-injected window.__BRANDING__.
   Fallback values are used in dev/REPL where no server injection exists.

   Server-side source of truth: branding.clj
   Bridge: index.clj injects branding/client-config as JSON in <head>."
  (:require [orcpub.dnd.e5.portrait-assets :as portrait-assets]))

;; ─── Config Bridge ───────────────────────────────────────────────
;; Server injects branding.clj/client-config as window.__BRANDING__ JSON.
;; We read it once at namespace load time.

(def ^:private config
  (when (exists? js/window.__BRANDING__)
    (js->clj js/window.__BRANDING__ :keywordize-keys true)))

;; Portrait artist credits, applied as soon as the config is read. The drawer
;; and the PDF export render here while the share card renders on the server,
;; so both runtimes have to be told the same thing -- a credit that disagreed
;; between the sheet and the link someone shared would be worse than none.
(when-let [artists (:portrait-artists config)]
  (portrait-assets/set-artist-overrides!
   (into {} (for [[id fields] artists] [(keyword (name id)) fields]))))

;; ─── Branding Values ─────────────────────────────────────────────
;; Each def reads from the bridge config with a hardcoded fallback
;; for dev/REPL environments where the server isn't injecting.

(def app-name
  "Full display name."
  (:app-name config "OrcPub"))

(def logo-path
  "Path to the main SVG logo."
  (:logo-path config "/image/orcpub-logo.svg"))

(def copyright-holder
  "Entity name for legal footer."
  (:copyright-holder config "OrcPub"))

(def copyright-year
  "Copyright year string."
  (:copyright-year config "2025"))

(def support-email
  "Contact email for error messages and privacy page."
  (:support-email config ""))

(def help-url
  "URL for the help/FAQ page. Empty = hidden."
  (:help-url config ""))

(def social-links
  "Map of social platform links. Empty string = hidden."
  (:social-links config {}))

(def field-limits
  "Max-length constraints for form input fields."
  (:field-limits config {:notes 50000 :text 255 :number 7}))

;; ─── Footer ────────────────────────────────────────────────────────

(def copyright-url
  "URL for copyright holder name in footer. Empty = plain text."
  (:copyright-url config ""))

;; ─── UI Behavior ───────────────────────────────────────────────────

(def registration-logo-class
  "CSS class for logo on registration/login page."
  (:registration-logo-class config "h-55"))

(def restrict-print-to-owner?
  "Whether print button is restricted to character owner."
  (:restrict-print-to-owner? config false))
