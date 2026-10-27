(ns orcpub.fork.branding
  "Centralized branding configuration for fork-neutral deployment.
   All values have sensible defaults; forks override via env vars.

   Server-side (.clj) is the source of truth. Client-side branding
   is delivered via the config bridge: index.clj injects client-config
   as window.__BRANDING__ JSON in <head>, and branding.cljs reads it."
  (:require [orcpub.env :as env]
            [cheshire.core :as cheshire]
            [clojure.string :as s]
            [orcpub.artist-links :as links]
            [orcpub.dnd.e5.portrait-assets :as portrait-assets])
  (:import [java.time Year]))

;; ─── Checking operator settings ────────────────────────────────────
;; Every value below comes from the environment. A bad one falls back to its default with a warning
;; naming the setting, so a typo costs that value, not a page or the server.

(defonce ^{:doc "Branding values that fell back to their default, as {:var :value :why}."}
  ignored
  (atom []))

(defn- warn!
  "Prints that `setting` (a keyword like :app-help-url) was ignored, and why, and adds
   {:var :value :why} to `ignored` for the boot report. Returns nil."
  [setting value why]
  (let [var-name (.toUpperCase ^String (s/replace (name setting) "-" "_") java.util.Locale/ROOT)]
    (swap! ignored conj {:var var-name :value (pr-str value) :why why})
    (println (str "branding: " var-name " ignored (" why "): " (pr-str value)))))

(defn checked-url
  "`value` of `setting` when it is blank, an http(s) address, or a path on this site (/image/x.svg);
   else `default`, with a warning."
  [setting value default]
  (cond
    (s/blank? value) value
    (or (re-matches #"https?://[A-Za-z0-9.-]+(:\d+)?([/?#]\S*)?" value)
        (re-matches #"/(?![/\\])\S*" value)) value
    :else (do (warn! setting value "not a web address or site path") default)))

(defn checked-email
  "`value` of `setting` when it is blank or looks like an email address; else `default`, warned."
  [setting value default]
  (if (or (s/blank? value) (re-matches #"[^@\s]+@[^@\s]+\.[^@\s]+" value))
    value
    (do (warn! setting value "not an email address") default)))

(defn checked-int
  "`value` of `setting` as a positive whole number; `default` when unset, or with a warning when it
   is anything else."
  [setting value default]
  (if (s/blank? value)
    default
    (let [n (try (Integer/parseInt (s/trim value)) (catch NumberFormatException _ nil))]
      (if (and n (pos? n)) n (do (warn! setting value "not a positive whole number") default)))))

;; ─── App Identity ──────────────────────────────────────────────────

(def app-name
  "Full display name. Used in emails, OG tags, page titles."
  (env/value :app-name "OrcPub"))

(def app-tagline
  "One-line description for OG/meta tags."
  (env/value :app-tagline "D&D 5e character builder/generator and digital character sheet far beyond any other in the multiverse."))

(def app-url
  "Primary application URL for legal pages and external references. Empty = hidden."
  (checked-url :app-url (env/value :app-url "") ""))

(def default-page-title
  "Default <title> and og:title when no page-specific title is set."
  (env/value :app-page-title (str app-name ": D&D 5e Character Builder/Generator")))

;; ─── Logos & Images ────────────────────────────────────────────────

(def logo-path
  "Path to the main SVG logo (splash page, header, privacy page)."
  (checked-url :app-logo-path (env/value :app-logo-path "/image/orcpub-logo.svg") "/image/orcpub-logo.svg"))

(def og-image-filename
  "Filename for the OG meta image (social sharing preview).
   Combined with the request host to form the full URL."
  (checked-url :app-og-image (env/value :app-og-image "/image/orcpub-logo.png") "/image/orcpub-logo.png"))

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
  (checked-email :email-from-address (env/value :email-from-address "no-reply@example.com") "no-reply@example.com"))

(defn sender-configured?
  "Whether `address`, the checked from-address, can send: set, and not the placeholder an unset or
   invalid EMAIL_FROM_ADDRESS falls back to."
  [address]
  (and (not (s/blank? address)) (not= address "no-reply@example.com")))

(def email-configured?
  "Whether outbound email can actually be sent — true only when the operator has
   set a real from-address (not the placeholder). Email-dependent features
   should check this and degrade gracefully (e.g. hide a 'send' button, keep the
   copyable fallback) rather than attempt a doomed send."
  (sender-configured? email-from-address))

;; ─── Support & Help ──────────────────────────────────────────────

(def support-email
  "Contact email shown on privacy page, error messages, etc. Empty = hidden."
  (checked-email :app-support-email (env/value :app-support-email "") ""))

(def report-recipient
  "Where user-submitted 'character won't load' reports are emailed. Prefers the
   public support address (APP_SUPPORT_EMAIL); falls back to the admin error inbox
   (EMAIL_ERRORS_TO) that the app's error notifications already use — so an operator
   who has configured error reporting needs no new setting. Empty (neither set) =
   reports can't send and the UI offers the copyable report instead."
  (if (seq support-email) support-email (checked-email :email-errors-to (env/value :email-errors-to "") "")))

(def help-url
  "URL for the help/FAQ page. Empty string = hidden."
  (checked-url :app-help-url (env/value :app-help-url "") ""))

;; ─── Social Links ──────────────────────────────────────────────────
;; Each link shows in the header/footer when its env var is set to a URL; unset hides it.
;; e.g. in .env:  APP_SOCIAL_PATREON=https://www.patreon.com/YourProject
;;                APP_SOCIAL_DISCORD=https://discord.gg/your-invite

(def social-links
  "Map of social platform links. Empty string = hidden."
  (into {} (for [k [:patreon :facebook :bluesky :twitter :reddit :discord]
                 :let [setting (keyword (str "app-social-" (name k)))]]
             [k (checked-url setting (env/value setting "") "")])))

;; ─── Footer ─────────────────────────────────────────────────────

(def copyright-url
  "URL for copyright holder name in footer. Empty string = plain text."
  (checked-url :app-copyright-url (env/value :app-copyright-url "") ""))

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
  {:notes  (checked-int :app-field-limit-notes (env/value :app-field-limit-notes) 50000)
   :text   (checked-int :app-field-limit-text (env/value :app-field-limit-text) 255)
   :number (checked-int :app-field-limit-number (env/value :app-field-limit-number) 7)})

;; ─── Client-Side Config Bridge ───────────────────────────────────
;; index.clj injects this as window.__BRANDING__ JSON in <head>.
;; branding.cljs reads it at runtime for CLJS components.


;; ─── Portrait Artist Credits ───────────────────────────────────────

(defn artist-override
  "One artist's credit `fields` from PORTRAIT_ARTISTS (:name :link :links :license) as overrides.
   Links pass artist-links/url-problem, the check an artist's own edits pass; a link's icon and
   colour come from its address (artist-links/link-entry), never from the setting. Returns the map."
  [fields]
  (let [ok-url (fn [u] (if (links/url-problem u)
                         (do (warn! :portrait-artists u (links/url-problem u)) nil)
                         (s/trim (str u))))
        link (some-> (:link fields) ok-url)
        extra (vec (for [l (:links fields)
                         :let [u (ok-url (:url l))] :when u]
                     (cond-> (links/link-entry u)
                       (string? (:label l)) (assoc :link/label (:label l)))))]
    (cond-> {}
      (string? (:name fields))    (assoc :artist/name (:name fields))
      link                        (assoc :artist/link link)
      (seq extra)                 (assoc :artist/links extra)
      (string? (:license fields)) (assoc :artist/license (:license fields)))))

(def portrait-artists
  "Deployment overrides for how portrait artists are credited, as
   {artist-id {:artist/name .. :artist/link .. :artist/license ..}}.

   PORTRAIT_ARTISTS is JSON keyed by artist id:

     {\"house-pack\": {\"name\": \"Someone\", \"link\": \"https://...\"}}

   The registry in portrait-assets.cljc holds the public default and the
   structure -- which artist drew which asset -- because the app cannot
   resolve an asset without it. Only the presentation of the credit is
   overridable, so a fork can credit its own contributors without editing
   shared code, and cannot silently re-attribute someone's art to a
   different pack.

   A malformed value is ignored with a warning rather than taking down the
   page: a broken credit should cost the credit, not the site."
  (or (when-let [raw (not-empty (env/value :portrait-artists))]
        (try
          (into {}
                (for [[id fields] (cheshire/parse-string raw true)]
                  [(keyword (name id)) (artist-override fields)]))
          (catch Exception e
            (println "branding: PORTRAIT_ARTISTS ignored --" (.getMessage e))
            nil)))
      {}))

;; Apply them server-side at load. The client gets the same map through
;; client-config and applies it itself, so the share card (rendered here) and
;; the sheet (rendered in the browser) always name the same person.
(portrait-assets/set-artist-overrides! portrait-artists)

(defn client-config
  "Map of branding values for CLJS injection. Serialized to JSON by index.clj. Operator settings
   only: artist credits, which artists can edit, reach the browser from /dnd/5e/portrait-artists."
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
