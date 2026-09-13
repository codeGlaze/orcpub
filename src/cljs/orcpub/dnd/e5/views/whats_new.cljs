(ns orcpub.dnd.e5.views.whats-new
  "The What's New panel: the current release's highlights, opened once per release
   and reopenable from the footer."
  (:require [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [orcpub.dnd.e5 :as e5]
            [orcpub.ver :as v]
            [orcpub.whats-new :as whats-new]))

(defn- close []
  (dispatch [::e5/close-whats-new]))

(defn- open []
  (dispatch [::e5/open-whats-new]))

(defn- stop-propagation [e]
  (.stopPropagation e))

(def ^:private cookie-notice-selector
  "The cookie notice's banner (resources/public/js/cookies.js).

   GOTCHA: the `.window` is the fixed bar; its wrapper div is a zero-height node in the normal
   flow, so measuring the wrapper returns 0. The wrapper is removed on dismissal."
  "#cookie-policy-popup .window")

(defn- cookie-notice-height []
  (if-let [el (js/document.querySelector cookie-notice-selector)]
    (.-offsetHeight el)
    0))

(defn- release-body [{:keys [title subtitle] :as release}]
  [:div.whats-new-panel
   {:on-click stop-propagation}
   [:div.whats-new-header
    [:div.flex.justify-cont-s-b.align-items-c
     [:div
      [:div.whats-new-eyebrow "What's New"]
      [:div.whats-new-title title]]
     [:i.fa.fa-times.whats-new-close
      {:on-click close
       :title "Close"}]]
    (when subtitle
      [:div.whats-new-subtitle subtitle])]

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
           [:div.whats-new-item-detail detail]]])])]

   [:div.whats-new-footer
    [:div.whats-new-version (str "Version " (v/version))]
    [:button.form-button
     {:on-click close}
     "Got it"]]])

(defn panel
  "Mount once in the app shell. Renders nothing until the panel is open.

   The backdrop stops above the cookie notice rather than covering it, so both are usable at once
   and the panel reclaims the space when the notice goes. The notice's height is measured, not
   assumed — it is third-party markup that wraps differently at different widths."
  []
  (let [on-key   (fn [e] (when (= "Escape" (.-key e)) (close)))
        notice-h (r/atom 0)
        measure! (fn [] (reset! notice-h (cookie-notice-height)))
        observer (atom nil)]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (js/document.addEventListener "keydown" on-key)
        (js/window.addEventListener "resize" measure!)
        (measure!)
        ;; The notice adds and removes ITSELF from body, so watch body rather than polling.
        (reset! observer (doto (js/MutationObserver. measure!)
                           (.observe js/document.body #js {:childList true}))))
      :component-will-unmount
      (fn [_]
        (js/document.removeEventListener "keydown" on-key)
        (js/window.removeEventListener "resize" measure!)
        (some-> @observer (.disconnect)))
      :reagent-render
      (fn []
        (when @(subscribe [::e5/whats-new-open?])
          [:div.whats-new-backdrop
           {:on-click close
            :style (when (pos? @notice-h) {:bottom (str @notice-h "px")})}
           [release-body whats-new/current-release]]))})))

(defn footer-link []
  [:a.orange.m-l-5.pointer
   {:on-click open}
   "What's New"])
