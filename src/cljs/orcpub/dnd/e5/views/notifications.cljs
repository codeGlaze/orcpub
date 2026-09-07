(ns orcpub.dnd.e5.views.notifications
  "Shared notification view components: the transient message banner, the reusable callout box,
   and the contextual banners built on it. Producers dispatch :show-*-message (events.cljs); the
   app-header mount reads :message-shown?/:message/:message-type and renders `message`. Severity
   styling is in styles/core.clj (.message + .bg-red/.bg-orange/.bg-green for the banner,
   .bg-warning for the callout)."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [clojure.string :as s]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.character :as char]))

(defn message
  "Transient banner: colored by message-type (:error/:warning else success), click anywhere
   to close via close-handler."
  [message-type message-text close-handler]
  [:div.pointer.f-w-b
   {:on-click close-handler}
   [:div.message
    {:class (case message-type
              :error "bg-red"
              :warning "bg-orange"
              "bg-green")}
    [:span message-text]
    [:i.fa.fa-times]]])

(defn callout
  "Persistent contextual notice: a warning-box (.bg-warning) with an optional fa icon class,
   body content (a string or hiccup — multi-line is fine), and optional action buttons (each
   {:label :on-click})."
  [{:keys [icon text actions]}]
  [:div.bg-warning.p-10.m-b-10.flex.align-items-c {:style {:gap "8px"}}
   (when icon [:i.fa {:class icon}])
   [:div.f-s-14.flex-grow-1 text]
   (for [{:keys [label on-click]} actions]
     ^{:key label} [:button.form-button {:on-click on-click} label])])

(defn shared-content-banner
  "Shown when viewing a character whose homebrew arrived embedded in the share link. The content
   is loaded view-only (:shared-plugins, never persisted); offers to Keep it in the library, and
   flags entries that collide by name with the viewer's own content (the shared version wins on
   this sheet, their copy is untouched). Renders nothing when there's no shared content."
  [id]
  (when-let [{:keys [count item-count collisions]} @(subscribe [::e5/shared-content-info])]
    (let [char-name @(subscribe [::char/character-name id])
          n-coll (clojure.core/count collisions)
          item-count (or item-count 0)
          parts (cond-> []
                  (pos? count)      (conj (str count " homebrew piece" (when (not= 1 count) "s")))
                  (pos? item-count) (conj (str item-count " custom item" (when (not= 1 item-count) "s"))))]
      [callout
       {:icon "fa-info-circle orange"
        :text [:div
               [:div.f-w-b.f-s-16 (str "Shared with " (s/join " and " parts) ".")]
               [:div.f-s-12.m-t-5 {:style {:opacity 0.8}}
                "Loaded for viewing only — not saved to your library."]
               (when (pos? n-coll)
                 [:div.f-s-12.m-t-5 {:style {:color "#d9a520"}}
                  (str n-coll " differ from same-named content you own — the shared version shows "
                       "here, yours is untouched"
                       (when-let [names (seq (map :name (take 4 collisions)))]
                         (str " (" (s/join ", " names) (when (> n-coll 4) ", …") ")"))
                       ".")])
               (when (pos? item-count)
                 [:div.f-s-12.m-t-5 {:style {:opacity 0.8}}
                  "Custom magic items are shown for this view only (keeping items isn't supported yet)."])]
        :actions (cond-> []
                   (pos? count) (conj {:label "Keep homebrew in my library"
                                       :on-click #(dispatch [::e5/keep-shared-content char-name])})
                   :always      (conj {:label "Dismiss"
                                       :on-click #(dispatch [::e5/dismiss-shared-content])}))}])))
