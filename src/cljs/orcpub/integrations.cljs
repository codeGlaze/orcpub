(ns orcpub.integrations
  "Client-side integration hooks.
   DMV: Matomo analytics + Google AdSense.
   Public repo overrides with no-op stubs.

   Companion to integrations.clj (server-side head tags).
   Server-side loads third-party SDKs in <head>;
   this namespace provides the in-app component hooks.")

;; ─── Page View Tracking ─────────────────────────────────────────
;; Called from the :route event handler (events.cljs), NOT from render
;; function bodies (which fire on every React re-render).

(defn track-page-view!
  "Full Matomo page view tracking suite. Called once per navigation from
   the :route event handler (single choke point)."
  [_route]
  (when (exists? js/_paq)
    (.push js/_paq #js ["setReferrerUrl" js/location.href])
    (.push js/_paq #js ["setCustomUrl" js/window.location])
    (.push js/_paq #js ["setDocumentTitle" js/document.title])
    (.push js/_paq #js ["deleteCustomVariables" "page"])
    (.push js/_paq #js ["trackPageView"])
    (.push js/_paq #js ["MediaAnalytics::scanForMedia" (js/document.getElementById "app")])
    (.push js/_paq #js ["FormAnalytics::scanForForms" (js/document.getElementById "app")])
    (.push js/_paq #js ["trackContentImpressionsWithinNode" (js/document.getElementById "app")])
    (.push js/_paq #js ["enableLinkTracking"])))

;; ─── Ad Components ──────────────────────────────────────────────
;; The AdSense SDK script tag is loaded server-side via integrations.clj;
;; this component renders the actual ad placement element.

(defn ad-banner
  "Google AdSense in-page banner. Returns hiccup with dangerouslySetInnerHTML.
   Patron-gated rendering is handled by the caller in views.cljs."
  []
  [:div {:dangerouslySetInnerHTML
         #js {:__html (str "<!-- InPage -->\n"
                           "<ins class=\"adsbygoogle\" style=\"display:block\" "
                           "data-ad-client=\"ca-pub-3202063096003962\" "
                           "data-ad-slot=\"4970831358\" data-ad-format=\"auto\" "
                           "data-full-width-responsive=\"true\"></ins>\n"
                           "<script> (adsbygoogle = window.adsbygoogle || []).push({}); </script>")}}])
