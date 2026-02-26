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

;; ─── App Mount Hook ───────────────────────────────────────────────
;; Called from the app root component-did-mount. Handles mount-time
;; integration setup (analytics user identification, ad slot init).

(defn on-app-mount!
  "Mount-time integrations. Called once from app root component-did-mount.
   Context map: {:user-tier :free|:patron|... :username str :email str}"
  [{:keys [user-tier username email]}]
  (when (exists? js/_paq)
    ;; Matomo user identification for all logged-in users
    (when (seq username)
      (.push js/_paq (clj->js ["setUserId" (str email)])))
    ;; Matomo custom variables for tiered users
    (when (not= :free user-tier)
      (.push js/_paq (clj->js ["setCustomVariable" 1 "User" (str username) "visit"]))
      (.push js/_paq (clj->js ["setCustomVariable" 2 "Email" (str email) "visit"]))
      (.push js/_paq (clj->js ["setCustomVariable" 3 "Tier" (str user-tier) "visit"]))))
  ;; Ad slot reload for free-tier users
  (when (= :free user-tier)
    (when (js-in "reloadAdSlots" js/window)
      (js/reloadAdSlots))))

;; ─── Analytics Custom Variables ─────────────────────────────────
;; Called from render functions that need to tag analytics events
;; with page-specific data (e.g. character count).

(defn track-character-list!
  "Tag the character list view with character count for analytics."
  [character-count _user-tier]
  (when (exists? js/_paq)
    (.push js/_paq (clj->js ["setCustomVariable" 4 "Characters" (str character-count) "visit"]))))

;; ─── Content Slot ──────────────────────────────────────────────
;; The AdSense SDK script tag is loaded server-side via integrations.clj;
;; this component renders the actual ad placement element.

(defn content-slot
  "DMV: Google AdSense in-page banner. Returns hiccup with dangerouslySetInnerHTML.
   Tier-gated rendering is handled by the caller in views.cljs."
  []
  [:div {:dangerouslySetInnerHTML
         #js {:__html (str "<!-- InPage -->\n"
                           "<ins class=\"adsbygoogle\" style=\"display:block\" "
                           "data-ad-client=\"ca-pub-3202063096003962\" "
                           "data-ad-slot=\"4970831358\" data-ad-format=\"auto\" "
                           "data-full-width-responsive=\"true\"></ins>\n"
                           "<script> (adsbygoogle = window.adsbygoogle || []).push({}); </script>")}}])
