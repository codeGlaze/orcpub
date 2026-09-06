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

(defn- release-body [{:keys [title subtitle items]}]
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
    (for [{:keys [icon headline detail]} items]
      ^{:key headline}
      [:div.whats-new-item
       [:i.fa.whats-new-item-icon {:class icon}]
       [:div
        [:div.whats-new-item-headline headline]
        [:div.whats-new-item-detail detail]]])]

   [:div.whats-new-footer
    [:div.whats-new-version (str "Version " (v/version))]
    [:button.form-button
     {:on-click close}
     "Got it"]]])

(defn panel
  "Mount once in the app shell. Renders nothing until the panel is open."
  []
  (let [on-key (fn [e] (when (= "Escape" (.-key e)) (close)))]
    (r/create-class
     {:component-did-mount    (fn [_] (js/document.addEventListener "keydown" on-key))
      :component-will-unmount (fn [_] (js/document.removeEventListener "keydown" on-key))
      :reagent-render
      (fn []
        (when @(subscribe [::e5/whats-new-open?])
          [:div.whats-new-backdrop
           {:on-click close}
           [release-body whats-new/current-release]]))})))

(defn footer-link []
  [:a.orange.m-l-5.pointer
   {:on-click open}
   "What's New"])
