(ns orcpub.dnd.e5.views-2
  (:require [orcpub.route-map :as routes]
            [clojure.string :as s]
            [orcpub.fork.branding :as branding]
            [orcpub.fork.splash :as splash]))

(defn style [style]
  #?(:cljs style)
  #?(:clj (s/join
           "; "
           (map
            (fn [[k v]]
              (str (name k) ": " (if (keyword? v) (name v) v)))
            style))))

(defn svg-icon-2 [icon-name & [theme]]
  [:img.svg-icon
   {:src (str "/image/" icon-name ".svg")}])

(defn splash-page-button
  "Render a splash page button. If handler is provided, uses on-click;
   otherwise resolves route (keyword = path-for, string = raw href)."
  [title icon route & [handler]]
  [:a.splash-button
   (let [cfg {:style (style {:text-decoration :none
                             :color "#f0a100"})}]
     (if handler
       (assoc cfg :on-click handler)
       (assoc cfg :href (if (string? route) route (routes/path-for route)))))
   [:div.splash-button-content
    {:style (style {:box-shadow "0 2px 6px 0 rgba(0, 0, 0, 0.5)"
                    :margin "5px"
                    :text-align "center"
                    :padding "10px"
                    :cursor :pointer
                    :display :flex
                    :align-items :center
                    :justify-content :space-around
                    :font-weight :bold})}
    [:div
     (svg-icon-2 icon 64 "dark")
     [:div
      [:span.splash-button-title-prefix "D&D 5e "] [:span title]]]]])

(def orange-style
  {:color :orange})

(defn legal-footer []
  [:div.m-l-15.m-b-10.m-t-10.t-a-l
   [:span (str "\u00a9 " branding/copyright-year " " branding/copyright-holder)]
   (for [{:keys [label href]} splash/legal-footer-links]
     ^{:key href}
     [:a.m-l-5 {:href href :target :_blank} label])])

(defn splash-page []
  [:div.app.h-full
   {:style (style {:display :flex
                   :flex-direction :column})}
   [:div
    {:style (style
             {:display :flex
              :flex-grow 1
              :color :white
              :align-items :center
              :justify-content :space-around})}
    [:div.main-text-color.splash-page-content
     {:style (style {:font-family "sans-serif"})}
     [:div
      {:style (style {:display :flex
                      :justify-content :space-around})}
      [:img {:class splash/logo-width-class
             :src branding/logo-path}]
      (when splash/edition-label
        [:div.f-s-18.opacity-5.m-t-10 splash/edition-label])]
     [:div
      {:style (style
               {:display :flex
                :flex-wrap :wrap
                :justify-content :center
                :margin-top "10px"})}
      (splash-page-button
       "Character Builder / Sheet"
       "anvil-impact"
       routes/dnd-e5-char-builder-route)
      (splash-page-button
       "Character Builder for Newbs"
       "baby-face"
       routes/dnd-e5-newb-char-builder-route)
      (splash-page-button
       "Homebrew Content"
       "beer-stein"
       routes/dnd-e5-my-content-route)]
     [:div
      {:style (style
               {:display :flex
                :flex-wrap :wrap
                :justify-content :center
                :margin-top "10px"})}
      (splash-page-button
       "Spells"
       "spell-book"
       routes/dnd-e5-spell-list-page-route)
      (splash-page-button
       "Monsters"
       "spiked-dragon-head"
       routes/dnd-e5-monster-list-page-route)
      (splash-page-button
       "Items"
       "all-for-one"
       routes/dnd-e5-item-list-page-route)
      (splash-page-button
       "Combat Tracker"
       "sword-clash"
       routes/dnd-e5-combat-tracker-page-route)]
     [:div
      {:style (style
               {:display :flex
                :flex-wrap :wrap
                :justify-content :center
                :margin-top "10px"})}
      (splash-page-button
       "Encounter Builder"
       "minions"
       routes/dnd-e5-encounter-builder-page-route)
      (splash-page-button
       "Monster Builder"
       "anatomy"
       routes/dnd-e5-monster-builder-page-route)
      (splash-page-button
       "Spell Builder"
       "gift-of-knowledge"
       routes/dnd-e5-spell-builder-page-route)
      (splash-page-button
       "Feat Builder"
       "vitruvian-man"
       routes/dnd-e5-feat-builder-page-route)
      (splash-page-button
       "Class Builder"
       "mounted-knight"
       routes/dnd-e5-class-builder-page-route)
      (splash-page-button
       "Race Builder"
       "woman-elf-face"
       routes/dnd-e5-race-builder-page-route)
      (splash-page-button
       "Background Builder"
       "ages"
       routes/dnd-e5-background-builder-page-route)]
     (when (seq splash/generator-buttons)
       [:div
        {:style (style
                 {:display :flex
                  :flex-wrap :wrap
                  :justify-content :center
                  :margin-top "10px"})}
        (for [{:keys [title icon route]} splash/generator-buttons]
          ^{:key route}
          (splash-page-button title icon route))])]]])
