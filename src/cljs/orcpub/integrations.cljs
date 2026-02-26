(ns orcpub.integrations
  "Client-side integration hooks. No-op by default.
   Fork overrides: replace these functions with real implementations.

   Companion to integrations.clj (server-side head tags).
   Server-side loads third-party scripts in <head>;
   this namespace provides the in-app lifecycle hooks.")

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

;; ─── Content Slot ──────────────────────────────────────────────
;; Hook for rendering supplementary content in the page body.
;; Fork overrides: return hiccup for banners, promotions, etc.

(defn content-slot
  "Supplementary content component. Returns nil (renders nothing) by default.
   Fork overrides: return hiccup to render content in designated slots."
  []
  nil)
