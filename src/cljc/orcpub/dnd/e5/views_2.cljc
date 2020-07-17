(ns orcpub.dnd.e5.views-2
  (:require [orcpub.route-map :as routes]
            [clojure.string :as s]
            #?(:cljs [re-frame.core :refer [subscribe dispatch dispatch-sync]])))

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

(defn splash-page-button [title icon route isroute & [handler]]
  [:a.splash-button
   (let [cfg {:style (style {:text-decoration :none
                             :color "#f0a100"})}]
     (if (true? isroute)
       (do (assoc cfg :on-click handler)
           (assoc cfg :href (routes/path-for route)))
       (do (assoc cfg :on-click handler)
           (assoc cfg :href route))))
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

(defn legal-footer-sm []
  [:div.m-l-15.m-b-10.m-t-10.t-a-l
   ;[:span "© 2020 Dungeon Masters Vault"]
   [:a.m-l-5 {:href "/terms-of-use" :target :_blank} "Terms of Use"]
   [:a.m-l-5 {:href "/privacy-policy" :target :_blank} "Privacy Policy"]
   [:a.m-l-5 {:href "/cookies-policy" :target :_blank} "Cookie Polciy"]])

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
      [:img.w-50-p
       {:src "/image/dmv-logo.svg"}]]
     [:div
      {:style (style
               {:display :flex
                :flex-wrap :wrap
                :justify-content :center
                :margin-top "10px"})}
      (splash-page-button
       "Character Builder / Sheet"
       "anvil-impact"
       routes/dnd-e5-char-builder-route
       true)
      (splash-page-button
       "Character Builder for Newbs"
       "baby-face"
       routes/dnd-e5-newb-char-builder-route
       true)
      (splash-page-button
       "Homebrew Content"
       "beer-stein"
       routes/dnd-e5-my-content-route
       true)]
     [:div
      {:style (style
               {:display :flex
                :flex-wrap :wrap
                :justify-content :center
                :margin-top "10px"})}
      (splash-page-button
       "Spells"
       "spell-book"
       routes/dnd-e5-spell-list-page-route
       true)
      (splash-page-button
       "Monsters"
       "spiked-dragon-head"
       routes/dnd-e5-monster-list-page-route
       true)
      (splash-page-button
       "Items"
       "all-for-one"
       routes/dnd-e5-item-list-page-route
       true)
      (splash-page-button
       "Combat Tracker"
       "sword-clash"
       routes/dnd-e5-combat-tracker-page-route
       true)]
     [:div
      {:style (style
               {:display :flex
                :flex-wrap :wrap
                :justify-content :center
                :margin-top "10px"})}
      (splash-page-button
       "Encounter Builder"
       "minions"
       routes/dnd-e5-encounter-builder-page-route
       true)
      (splash-page-button
       "Monster Builder"
       "anatomy"
       routes/dnd-e5-monster-builder-page-route
       true)
      (splash-page-button
       "Spell Builder"
       "gift-of-knowledge"
       routes/dnd-e5-spell-builder-page-route
       true)
      (splash-page-button
       "Feat Builder"
       "vitruvian-man"
       routes/dnd-e5-feat-builder-page-route
       true)
      (splash-page-button
       "Class Builder"
       "mounted-knight"
       routes/dnd-e5-class-builder-page-route
       true)
      (splash-page-button
       "Race Builder"
       "woman-elf-face"
       routes/dnd-e5-race-builder-page-route
       true)
      (splash-page-button
       "Background Builder"
       "ages"
       routes/dnd-e5-background-builder-page-route
       true)]
     [:div
      {:style (style
               {:display :flex
                :flex-wrap :wrap
                :justify-content :center
                :margin-top "10px"})}

      (splash-page-button
       "NPC Generator"
       "monk-face"
       "/generator/npcgenerator"
       false)
      (splash-page-button
       "City Generator"
       "elven-castle"
       "/generator/citygenerator"
       false)
      (splash-page-button
       "Name Generator"
       "stone-tablet"
       "/generator/namegenerator"
       false)
      (splash-page-button
       "Legend Generator"
       "giant-squid"
       "/generator/legendgenerator"
       false)
      (splash-page-button
       "Rumor Generator"
       "discussion"
       "/generator/rumorgenerator"
       false)
      (splash-page-button
       "Wanted Poster Generator"
       "wanted-reward"
       "/generator/wantedpostergenerator"
       false)
      (splash-page-button
       "So you're looking for..."
       "cash"
       "/generator/resourcegenerator"
       false)]]]])