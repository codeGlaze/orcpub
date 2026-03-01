(ns orcpub.fork.integrations
  "Client-side integration hooks.
   DMV: Matomo analytics + Google AdSense + tier-gated UI.
   Public repo overrides with no-op stubs.

   Companion to integrations.clj (server-side head tags).
   Server-side loads third-party SDKs in <head>;
   this namespace provides the in-app component hooks."
  (:require [orcpub.fork.branding :as branding]
            [orcpub.route-map :as routes]))

;; ─── Config Bridge ──────────────────────────────────────────────
;; Server injects integrations.clj/client-config as window.__INTEGRATIONS__ JSON.
;; We read it once at namespace load time.

(def ^:private config
  "Server-injected integration config (AdSense client/slot IDs, etc.)."
  (when (exists? js/window.__INTEGRATIONS__)
    (js->clj js/window.__INTEGRATIONS__ :keywordize-keys true)))

;; ─── Page View Tracking ─────────────────────────────────────────
;; Called from the :route event handler (events.cljs), NOT from render
;; function bodies (which fire on every React re-render).

(defn track-page-view!
  "Full Matomo page view tracking suite. Called once per navigation from
   the :route event handler (single choke point)."
  [_route]
  (when (exists? js/_paq)
    (.push js/_paq #js ["setReferrerUrl" js/location.href])
    (.push js/_paq #js ["setCustomUrl" js/window.location.href])
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
      (.push js/_paq (clj->js ["setCustomVariable" 3 "Tier" (name user-tier) "visit"]))))
  ;; Ad slot reload for default-tier users
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
;; Self-gated: only renders for default-tier users.

(defn content-slot
  "DMV: Google AdSense in-page banner. Self-gated to default-tier users.
   Reads ad client/slot from server-injected config bridge.
   Returns hiccup with dangerouslySetInnerHTML, or nil for tiered users."
  [user-tier]
  (let [ad-client (:adsense-client config)
        ad-slot   (:adsense-slot config)]
    (when (and (= :free user-tier) (seq ad-client) (seq ad-slot))
      [:div.m-l-20.m-r-20.f-w-b.f-s-18.container.m-b-10.main-text-color
       [:div.content.p-10.flex
        [:div.flex-grow-1.t-a-c
         [:div {:dangerouslySetInnerHTML
                #js {:__html (str "<!-- InPage -->\n"
                                  "<ins class=\"adsbygoogle\" style=\"display:block\" "
                                  "data-ad-client=\"" ad-client "\" "
                                  "data-ad-slot=\"" ad-slot "\" data-ad-format=\"auto\" "
                                  "data-full-width-responsive=\"true\"></ins>\n"
                                  "<script> (adsbygoogle = window.adsbygoogle || []).push({}); </script>")}}]]]])))

;; ─── Supporter Link ──────────────────────────────────────────
;; Header supporter area. Shows tier badge for patrons, default
;; supporter button for free users. Nil when no URL is configured.
;; icon-fn is passed from caller to avoid circular dep with views.cljs.

(defn supporter-link
  "DMV: Header supporter area with tier badge or Patreon button.
   icon-fn: (fn [icon-name size css] hiccup) — render function for tier badges."
  [user-tier mobile? icon-fn]
  (when-let [url (not-empty (:patreon branding/social-links))]
    [:a {:href url :target :_blank}
     (if (not= :free user-tier)
       [icon-fn (name user-tier) (if mobile? 40 60) ""]
       [:img.h-32.m-l-10.m-b-5.pointer.opacity-7.hover-opacity-full
        {:src (if mobile?
                "https://c5.patreon.com/external/logo/downloads_logomark_color_on_navy.png"
                "https://c5.patreon.com/external/logo/become_a_patron_button.png")}])]))

;; ─── Support Banner ──────────────────────────────────────────
;; Dismissable support/donation banner shown to default-tier users.
;; Absorbs the inline donation CTA that was in views.cljs.

(defn support-banner
  "DMV: Dismissable donation banner for default-tier users.
   Opts: {:srd-message-closed? bool :hide-header-message? bool
          :frame? bool :user-tier keyword :on-dismiss fn}"
  [{:keys [srd-message-closed? hide-header-message? frame? user-tier on-dismiss]}]
  (when (and (not srd-message-closed?)
             (not hide-header-message?)
             (= :free user-tier)
             (not frame?))
    [:div.content.bg-lighter.p-10.flex
     [:div.flex-grow-1.t-a-c
      [:div.p-t-10 "Please consider a gift of $1 to support this site."]
      [:div.p-t-10 "Your support of $1 will provide the server with one lunch because no server should go hungry."]
      [:div.p-t-10.p-b-10
       (when-let [url (not-empty (:patreon branding/social-links))]
         [:a.orange {:href url :target "_blank"} "Become a Patron today"])]]
     [:i.fa.fa-times.p-10.pointer
      {:on-click on-dismiss}]]))

;; ─── Share Links ─────────────────────────────────────────────
;; Character sharing links. DMV provides email + direct www link
;; with dynamic protocol/port and ?frame=true for embedded views.

(defn share-links
  "DMV: Returns a seq of share-link hiccup elements for a character.
   Includes email share and direct www link with frame support."
  [id character-name]
  [[:a.m-r-5.f-s-14
    {:href (str "mailto:?subject=My%20D%26D%20Character%20-%20"
                character-name
                "&body=" js/window.location.protocol "//" js/window.location.hostname
                (when-let [p js/window.location.port] (when (seq p) (str ":" p)))
                (routes/path-for routes/dnd-e5-char-page-route :id id) "?frame=true")}
    [:i.fa.fa-envelope.m-r-5]
    "share"]
   [:a.m-r-5.f-s-14
    {:href (str js/window.location.protocol "//" js/window.location.hostname
                (when-let [p js/window.location.port] (when (seq p) (str ":" p)))
                (routes/path-for routes/dnd-e5-char-page-route :id id) "?frame=true")
     :target "_blank"}
    [:i.fa.fa-link.m-r-5]
    "www"]])

(defn share-link-www
  "DMV: Direct www share link for character list items. Includes frame support."
  [id]
  [:a.m-r-5.f-s-14
   {:href (str js/window.location.protocol "//" js/window.location.hostname
               (when-let [p js/window.location.port] (when (seq p) (str ":" p)))
               (routes/path-for routes/dnd-e5-char-page-route :id id) "?frame=true")
    :target "_blank"}
   [:i.fa.fa-link.m-r-5]
   "www"])

;; ─── PDF Options Slot ────────────────────────────────────────
;; Hook below PDF sheet options. DMV shows tier-gated content here.

(defn pdf-options-slot
  "DMV: Additional content below PDF sheet options for default-tier users."
  [user-tier]
  (when (= :free user-tier)
    [:div
     [:div.flex.m-b-10 "Supporters get access to 3 additional character sheets:"]
     [:div.flex.m-b-10 "Original 5e Character sheet - optional variant"]
     [:div.flex.m-b-10 "Icewind Dale 5e Character sheet"]
     [:div.flex.m-b-10 "Cthulhu Mythos Sagas sheet"]
     (when-let [url (not-empty (:patreon branding/social-links))]
       [:div.flex.m-b-10
        [:a.orange {:href url :target "_blank"}
         "Become a Patron to unlock these today"]])]))
