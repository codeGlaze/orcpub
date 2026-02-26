(ns orcpub.integrations
  "Client-side integration hooks. No-op by default.
   Fork overrides: replace these functions with real implementations.

   Companion to integrations.clj (server-side head tags).
   Server-side loads third-party scripts in <head>;
   this namespace provides the in-app lifecycle hooks."
  (:require [orcpub.branding :as branding]
            [orcpub.route-map :as routes]))

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
  "Header supporter link. Shows Patreon/supporter button when URL is configured.
   icon-fn: (fn [icon-name size css] hiccup) — render function, unused in default."
  [_user-tier mobile? _icon-fn]
  (when-let [url (not-empty (:patreon branding/social-links))]
    [:a {:href url :target :_blank}
     [:img.h-32.m-l-10.m-b-5.pointer.opacity-7.hover-opacity-full
      {:src (if mobile?
              "https://c5.patreon.com/external/logo/downloads_logomark_color_on_navy.png"
              "https://c5.patreon.com/external/logo/become_a_patron_button.png")}]]))

;; ─── Support Banner ──────────────────────────────────────────
;; Dismissable banner for site announcements or support messages.
;; Fork overrides: return hiccup for donation CTAs, announcements, etc.

(defn support-banner
  "Site announcement/support banner. Returns nil by default.
   Opts: {:srd-message-closed? bool :hide-header-message? bool
          :frame? bool :user-tier keyword :on-dismiss fn}"
  [_opts]
  nil)

;; ─── PDF Options Slot ────────────────────────────────────────
;; Hook for additional content below PDF sheet options.
;; Fork overrides: return hiccup for premium feature promos, etc.

(defn pdf-options-slot
  "Additional content below PDF options. Returns nil by default."
  [_user-tier]
  nil)

;; ─── Share Links ─────────────────────────────────────────────
;; Character sharing links. Default provides a single email share.
;; Fork overrides: add direct links, frame support, etc.

(defn share-links
  "Returns a seq of share-link hiccup elements for a character.
   Default: single email share with dynamic protocol."
  [id character-name]
  [[:a.m-r-5.f-s-14
    {:href (str "mailto:?subject=My%20" (js/encodeURIComponent branding/app-name) "%20Character%20"
                character-name
                "&body=" js/window.location.protocol "//"
                js/window.location.hostname
                (when-let [p js/window.location.port] (when (seq p) (str ":" p)))
                (routes/path-for routes/dnd-e5-char-page-route :id id))}
    [:i.fa.fa-envelope.m-r-5]
    "share"]])

(defn share-link-www
  "Direct www share link. Default: same as email share.
   Fork overrides: add frame support, direct URL, etc."
  [id]
  [:a.m-r-5.f-s-14
   {:href (str js/window.location.protocol "//"
               js/window.location.hostname
               (when-let [p js/window.location.port] (when (seq p) (str ":" p)))
               (routes/path-for routes/dnd-e5-char-page-route :id id))
    :target "_blank"}
   [:i.fa.fa-link.m-r-5]
   "www"])
