(ns orcpub.integrations
  "Client-side integration hooks. No-op by default.
   Forks override these functions with real implementations
   (analytics tracking, ad placements, etc.).

   Companion to integrations.clj (server-side head tags).
   Server-side loads third-party SDKs in <head>;
   this namespace provides the in-app component hooks.")

;; ─── Page View Tracking ─────────────────────────────────────────
;; Call from the :route event handler (events.cljs), NOT from render
;; function bodies (which fire on every React re-render).
;;
;; Example override (Matomo):
;;   (defn track-page-view! [route]
;;     (when (exists? js/_paq)
;;       (.push js/_paq #js ["setCustomUrl" js/window.location.href])
;;       (.push js/_paq #js ["trackPageView"])))

(defn track-page-view!
  "Track a page navigation. No-op by default."
  [_route])

;; ─── Ad Components ──────────────────────────────────────────────
;; Hook for ad banner placement in the page body.
;; The SDK script tag is loaded server-side via integrations.clj;
;; this component renders the actual ad placement element.
;;
;; Example override (AdSense):
;;   (defn ad-banner []
;;     [:ins.adsbygoogle {:style {:display "block"}
;;                        :data-ad-client "ca-pub-xxx"
;;                        :data-ad-slot "yyy"}])

(defn ad-banner
  "Ad banner component. Returns nil (renders nothing) by default."
  []
  nil)
