(ns orcpub.dnd.e5.views-shared
  "Views the server renders as well as the browser, and the page-frame pieces they share with the
   app: the home page, the site footer, the copyright strip and the support links. Code here must
   compile for both; browser-only parts sit behind #?(:cljs ...). Anything else belongs in
   views.cljs or views/*."
  (:require [orcpub.route-map :as routes]
            [clojure.string :as s]
            [orcpub.fork.branding :as branding]
            [orcpub.fork.splash :as splash]
            #?(:cljs [re-frame.core :refer [dispatch]])
            #?(:cljs [orcpub.dnd.e5.views.whats-new :as whats-new-view])
            #?(:cljs [orcpub.ver :as v])))

(defn style
  "Returns `style`, a map of CSS properties, in the form the target needs: the map itself in the
   browser, a CSS declaration string (\"color: red; margin: 0\") on the server."
  [style]
  #?(:cljs style)
  #?(:clj (s/join
           "; "
           (map
            (fn [[k v]]
              (str (name k) ": " (if (keyword? v) (name v) v)))
            style))))

(defn svg-icon-2
  "An <img> of /image/<icon-name>.svg with the svg-icon class. `theme` is accepted and ignored.
   Returns hiccup."
  [icon-name & [theme]]
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
      [:span.splash-button-title-prefix {:translate "no" :lang "en"} "D&D 5e "] [:span title]]]]])

(defn legal-footer
  "The site-wide copyright line and its two legal links.

   `links?` false leaves the copyright and drops the links, for a page that has
   already linked both documents in its own consent copy. Printing them twice
   sixteen pixels apart does not make the agreement any more agreed to."
  ([] (legal-footer true))
  ([links?]
   [:div.m-l-15.m-b-10.m-t-10.t-a-l
    [:span (str "\u00a9 " branding/copyright-year " " branding/copyright-holder)]
    (when links?
      (for [{:keys [label href]} splash/legal-footer-links]
        ^{:key href}
        [:a.m-l-5 {:href href :target :_blank} label]))]))

(defn- whats-new-pill
  "The \"What's New\" pill, drawn above the support links (an amber cap and glow): opens the release
   panel (:orcpub.dnd.e5/open-whats-new) by click, or by Enter or Space when focused. Inert in the
   server-rendered page, which the app replaces. Returns hiccup."
  []
  [:a.support-pill.support-pill-whats-new
   #?(:cljs {:role "button"
             :tab-index 0
             :on-click #(dispatch [:orcpub.dnd.e5/open-whats-new])
             :on-key-down (fn [e]
                            (when (#{"Enter" " "} (.-key e))
                              (.preventDefault e)
                              (dispatch [:orcpub.dnd.e5/open-whats-new])))}
      :clj {})
   [:i.fa.fa-gift] "What's New"])

(defn support-pills
  "A row of quiet pills: What's New when `whats-new?`, then Support the Server (the Patreon link)
   and Fuel the Dev (the developer tip link), each only when its link is set. `compact?` draws
   them as icon-and-word links at text size (the site footer, which must not grow past its legal
   text). Returns hiccup, or nil when there is nothing to show."
  [& {:keys [whats-new? compact?]}]
  (let [{:keys [patreon dev-tip]} branding/social-links
        pills (cond-> []
                whats-new? (conj ^{:key "new"} (whats-new-pill))
                (seq patreon) (conj ^{:key "server"}
                                    [:a.support-pill {:href patreon :target "_blank" :rel "noopener"}
                                     [:i.fab.fa-patreon] "Support the Server"])
                (seq dev-tip) (conj ^{:key "dev"}
                                    [:a.support-pill {:href dev-tip :target "_blank" :rel "noopener"}
                                     [:img {:src "/image/bmac-cup.png" :alt ""}] "Fuel the Dev"]))]
    (when (seq pills)
      (into [:div.support-pills {:class (when compact? "support-pills-compact")}] pills))))

(defn splash-page
  "The home page: the logo, the grid of tool buttons and the support pills. Takes no args;
   returns hiccup. Rendered by the server as the first paint and by the app after mount."
  []
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
          (with-meta (splash-page-button title icon route) {:key route}))])
     (support-pills :whats-new? true)]]])

;; ─── Site footer (every app page): labelled link rows, then the legal band ───

(def srd-link
  "Link to the bundled SRD 5.1 PDF, as hiccup."
  [:a.orange {:href "/dnld/SRD-OGL_V5.1.pdf" :target "_blank"} "the 5e SRD-OGL 5.1"])

#?(:cljs
   (defn current-year
     "The current calendar year in the viewer's time zone. Returns a number."
     []
     (.getFullYear (js/Date.))))

#?(:cljs
   (defn- footer-row
     "One labelled row: `label` in small caps, then `links` (hiccup) inline. Returns hiccup, or nil
      when `links` is empty, so a group with nothing to show takes no space."
     [label links]
     (when (seq (remove nil? links))
       (into [:div.site-footer-group [:div.site-footer-label label]] (remove nil? links)))))

#?(:cljs
   (defn site-footer
     "The footer's link rows and legal band. Takes no args; reads branding and splash settings.
      Returns hiccup."
     []
     [:div.site-footer
      [:div.site-footer-groups
       (footer-row "Help"
                   [(when (seq branding/help-url)
                      [:a.orange {:href branding/help-url :target :_blank} "Help"])
                    [:a.orange {:href "https://github.com/Orcpub/orcpub/issues" :target :_blank} "Feedback/Bug Reports"]
                    [whats-new-view/footer-link]])
       (footer-row "Support" [(support-pills :compact? true)])
       (footer-row "Legal"
                   (for [{:keys [label href]} splash/legal-footer-links]
                     ^{:key href}
                     [:a.orange {:href href :target :_blank} label]))]
      [:div.legal-footer
       [:p "© " (current-year) " "
        (if (seq branding/copyright-url)
          [:a.orange {:href branding/copyright-url :target :_blank} branding/copyright-holder]
          branding/copyright-holder)]
       [:p "This site is based on " srd-link " - Wizards of the Coast, Dungeons & Dragons, D&D, and their logos are trademarks of Wizards of the Coast LLC in the United States and other countries. © 2025 Wizards. All Rights Reserved."]
       [:p branding/app-name " is not affiliated with, endorsed, sponsored, or specifically approved by Wizards of the Coast LLC."]
       [:p "Icons by Lorc, Delapouite, Caduceus, Caro Asercion, Faithtoken, Sbed, Skoll and Willdabeast ("
        [:a.orange {:href "http://game-icons.net"} "game-icons.net"]
        "). Artwork by Sandra ("
        [:a.orange {:href "https://www.deviantart.com/sandara" :target :_blank} "Deviantart"] ")."]
       [:p.pointer.underline
        {:on-click #(dispatch [:orcpub.dnd.e5/open-whats-new])}
        "Version " (v/version) " (" (v/date) ") " (v/description) " edition"]]]))
