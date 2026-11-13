(ns orcpub.dnd.e5.views.whats-new
  "The What's New panel: the current release's highlights, opened once per release
   and reopenable from the footer. A deployment's patron credits (whats-new/credits-path) close
   the list, and its support links sit in the panel's footer; with neither, the panel is unchanged."
  (:require [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [cljs.core.async :refer [<!]]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.http-safe :as http]
            [orcpub.fork.branding :as branding]
            [orcpub.ver :as v]
            [orcpub.whats-new :as whats-new])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def patreon-mark
  "Patreon's own logomark, the one the header's supporter button uses on a phone."
  "https://c5.patreon.com/external/logo/downloads_logomark_color_on_navy.png")

(def tip-mark
  "The developer-tip icon, the Buy Me a Coffee cup."
  "/image/bmac-cup.png")

(defonce ^:private credits
  ;; nil until asked for, then :loading, then the parsed groups ([] when the deployment has none).
  (r/atom nil))

(defn- load-credits!
  "Fetches the deployment's patron credits once per page load, the first time the panel opens."
  []
  (when (nil? @credits)
    (reset! credits :loading)
    (go (let [{:keys [status body]} (<! (http/get whats-new/credits-path))]
          (reset! credits (if (and (= 200 status) (string? body)) (whats-new/parse-credits body) []))))))

(defn- close []
  (dispatch [::e5/close-whats-new]))

(defn- open []
  (dispatch [::e5/open-whats-new]))

(defn- stop-propagation [e]
  (.stopPropagation e))

(defn- scroll-to-credits!
  "Scrolls the panel's body to the credits section."
  []
  (some-> (js/document.getElementById "whats-new-credits")
          (.scrollIntoView #js {:behavior "smooth" :block "start"})))

(defn- credits-section
  "The patron credits closing the release notes: a heading, one line, then the names, under a
   tier heading where the credits file names one. `groups` as whats-new/parse-credits returns."
  [groups]
  [:div.whats-new-group {:id "whats-new-credits"}
   [:div.whats-new-group-title "Made possible by our patrons"]
   [:div.whats-new-credits-intro "The server is paid for by patrons. Thank you for keeping it online."]
   (for [[i {:keys [tier names]}] (map-indexed vector groups)]
     ^{:key i}
     [:div
      (when tier [:div.whats-new-credits-tier tier])
      [:div.whats-new-patrons
       (for [[j nm] (map-indexed vector names)]
         ^{:key j} [:div nm])]])])

(defn- support-links
  "The footer's \"Help support our:\" icons: Server (Patreon) and Dev (the tip link), each shown
   only when its link is configured; nil when neither is."
  []
  (let [{:keys [patreon dev-tip]} branding/social-links
        link (fn [url src label]
               [:a.whats-new-support-link {:href url :target :_blank :rel "noopener"}
                [:img {:src src :alt ""}] label])]
    (when (or (seq patreon) (seq dev-tip))
      [:div.whats-new-support
       [:div.whats-new-support-label "Help support our:"]
       [:div.flex
        (when (seq patreon) (link patreon patreon-mark "Server"))
        (when (seq dev-tip) (link dev-tip tip-mark "Dev"))]])))

(defn- release-body
  "The open panel for `release`: header, highlights, the credits when the deployment has any,
   and the footer with the support links, version and Got it."
  [{:keys [title subtitle] :as release}]
  (let [groups @credits
        credits? (and (sequential? groups) (seq groups))]
    [:div.whats-new-panel
     {:on-click stop-propagation}
     [:div.whats-new-header
      [:div.flex.justify-cont-s-b.align-items-c
       [:div
        [:div.whats-new-eyebrow "What's New"]
        [:div.whats-new-title title]]
       [:button.fa.fa-times.whats-new-close
        {:on-click close
         :type "button"
         :title "Close"
         :aria-label "Close"}]]
      (when subtitle
        [:div.whats-new-subtitle subtitle])
      (when credits?
        [:button.whats-new-credits-link {:type "button" :on-click scroll-to-credits!}
         "Credits " [:i.fa.fa-arrow-down]])]

     [:div.whats-new-body
      (for [[group group-items] (whats-new/grouped-items release)]
        ^{:key (or group "ungrouped")}
        [:div.whats-new-group
         (when group
           [:div.whats-new-group-title group])
         (for [{:keys [icon headline detail]} group-items]
           ^{:key headline}
           [:div.whats-new-item
            [:i.fa.whats-new-item-icon {:class icon}]
            [:div
             [:div.whats-new-item-headline headline]
             [:div.whats-new-item-detail detail]]])])
      (when credits? [credits-section groups])]

     [:div.whats-new-footer
      [:div [support-links]]
      [:div.flex.align-items-c
       [:div.whats-new-version (str "Version " (v/version))]
       [:button.form-button.m-l-10
        {:on-click close}
        "Got it"]]]]))

(defn panel
  "Mount once in the app shell. Renders nothing until the panel is open; opening it the first
   time fetches the patron credits."
  []
  (let [on-key (fn [e] (when (= "Escape" (.-key e)) (close)))]
    (r/create-class
     {:component-did-mount    (fn [_] (js/document.addEventListener "keydown" on-key))
      :component-will-unmount (fn [_] (js/document.removeEventListener "keydown" on-key))
      :reagent-render
      (fn []
        (when @(subscribe [::e5/whats-new-open?])
          (load-credits!)
          [:div.whats-new-backdrop
           {:on-click close}
           [release-body whats-new/current-release]]))})))

(defn footer-link []
  [:a.orange.m-l-5.pointer
   {:on-click open}
   "What's New"])
