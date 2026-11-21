(ns orcpub.fork.integrations
  "Client-side integration hooks with minimal defaults; forks replace them with full
   implementations. Lifecycle hooks (track-page-view!, on-app-mount!, etc.) are no-ops; UI
   hooks have basic defaults (supporter-link shows a Patreon button when configured,
   share-url returns a shared character link unchanged). Companion to integrations.clj, which loads
   third-party scripts in <head>; this namespace provides the in-app component hooks."
  (:require [orcpub.fork.branding :as branding]))

;; ─── Page View Tracking ─────────────────────────────────────────
;; Called from the :route event handler (events.cljs), NOT from render
;; function bodies (which fire on every React re-render).

(defn track-page-view!
  "Track a page navigation. No-op by default.
   Fork overrides: call your analytics provider here."
  [_route])

;; ─── App Mount Hook ───────────────────────────────────────────────
;; Called from the app root component-did-mount. Handles mount-time
;; integration setup (e.g. user identification, external service init).

(defn on-app-mount!
  "Mount-time integrations. Called once from app root component-did-mount.
   Context map: {:user-tier :free|... :username str :email str}
   Fork overrides: wire analytics user identification, etc."
  [_context])

;; ─── Analytics Custom Variables ─────────────────────────────────
;; Called from render functions that need to tag analytics events
;; with page-specific data.

(defn track-character-list!
  "Tag the character list view with analytics data. No-op by default."
  [_character-count _user-tier])

;; ─── Content Slot ──────────────────────────────────────────────
;; Hook for rendering supplementary content in the page body.
;; Self-gated: accepts user-tier, returns nil by default.
;; Fork overrides: return hiccup for banners, promotions, etc.

(defn content-slot
  "Supplementary content component. Returns nil (renders nothing) by default.
   Fork overrides: return hiccup to render content in designated slots."
  [_user-tier]
  nil)

;; ─── Supporter Link ──────────────────────────────────────────
;; Header supporter area. Shows a supporter button when a URL is configured.
;; Fork overrides: add tier badges, enhanced button styles, etc.

(defn supporter-link
  "Header supporter link: Patreon's own button (its logomark when `mobile?`), served from this
   site's /image/, when a Patreon URL is configured; else nil.
   icon-fn: (fn [icon-name size css] hiccup) — render function, unused in default."
  [_user-tier mobile? _icon-fn]
  (when-let [url (not-empty (:patreon branding/social-links))]
    [:a {:href url :target :_blank}
     [:img.h-32.m-l-10.m-b-5.pointer.opacity-7.hover-opacity-full
      {:src (if mobile?
              "/image/patreon-logomark.png"
              "/image/patreon-button.png")}]]))

;; ─── Support Banner ──────────────────────────────────────────
;; Dismissable banner for site announcements or support messages.
;; Fork overrides: return hiccup for donation CTAs, announcements, etc.

(defn support-banner
  "Site announcement/support banner. Returns nil by default.
   Opts: {:srd-message-closed? bool :hide-header-message? bool
          :frame? bool :user-tier keyword :on-dismiss fn}"
  [_opts]
  nil)

;; ─── PDF Sheet Styles ───────────────────────────────────────
;; Returns the list of available character sheet styles for the dropdown.
;; Fork overrides: return tier-gated styles for premium users.

(defn sheet-styles
  "Available character sheet styles. Returns the default sheet only.
   Fork overrides: give extra styles only to a tier you positively recognise (\"is this a
   paying tier\", not \"is this not :free\" -- nil or a string tier must fail closed), and
   gate `pdf-options-slot` on the same predicate."
  [_user-tier]
  [{:title "Original 5e Character sheet" :value 1}])

;; ─── PDF Options Slot ────────────────────────────────────────
;; Hook for additional content below PDF sheet options.
;; Fork overrides: return hiccup for premium feature promos, etc.

(defn pdf-options-slot
  "Additional content below PDF options. Returns nil by default.

   Fork overrides: key this on the same predicate that gates `sheet-styles`, so
   the pitch appears exactly when the extra styles are locked."
  [_user-tier]
  nil)

;; ─── Share Link ─────────────────────────────────────────────────
;; Every link the share controls show or copy passes through this hook.

(defn share-url
  "The link to share for a character, given its address `url`. The public default returns it
   unchanged; a fork may add query parameters (DMV adds frame=true). Returns a string; a fragment
   (#...) is dropped, since the share token or payload goes there."
  [url]
  url)
