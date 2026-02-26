(ns orcpub.branding
  "Client-side branding config. Reads server-injected window.__BRANDING__.
   Fallback values are used in dev/REPL where no server injection exists.

   Server-side source of truth: branding.clj
   Bridge: index.clj injects branding/client-config as JSON in <head>.")

;; ─── Config Bridge ───────────────────────────────────────────────
;; Server injects branding.clj/client-config as window.__BRANDING__ JSON.
;; We read it once at namespace load time.

(def ^:private config
  (when (exists? js/window.__BRANDING__)
    (js->clj js/window.__BRANDING__ :keywordize-keys true)))

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
