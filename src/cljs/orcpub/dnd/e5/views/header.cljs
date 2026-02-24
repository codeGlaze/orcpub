(ns orcpub.dnd.e5.views.header
  "Application header bar and navigation components.

   Contains the top header bar (logo, search, user menu), navigation
   tabs with dropdown menus, social media icons, and search helpers.
   Depends only on views.common (never on views.cljs or siblings),
   keeping the dependency graph acyclic."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [orcpub.route-map :as routes]
            [orcpub.dnd.e5.views.common :as views-common]))

;;;; ====================================================================
;;;; Header Constants & Styles
;;;; ====================================================================

(def menu-color
  "Background color for header dropdown menus."
  "#2c3445")

(def header-menu-item-style
  "Absolute-positioned dropdown menu for desktop header tabs."
  {:position :absolute
   :background-color "#2c3445"
   :z-index 10000
   :top 84
   :right 0})

;; dead — zero callers
#_(def desktop-menu-item-style
  (assoc header-menu-item-style
         :width "100%"))

(def mobile-header-menu-item-style
  "Dropdown menu positioned for mobile header (shorter top offset)."
  (assoc header-menu-item-style
         :top 46))

;; dead — zero callers (moved from views.cljs)
#_(def header-tab-style
  {:width "85px"})

(def user-menu-style
  "Hidden-by-default dropdown for logged-in user actions."
  {:background-color menu-color
   :z-index 10000
   :position :absolute
   :right 0
   :display :none})

(def social-icon-style
  {:color :white
   :font-size "20px"})

(def search-input-style
  "Style for the header search bar input."
  {:height "60px"
   :margin-top "0px"
   :border :none
   :font-size "28px"
   :background-color :transparent
   :color :white})

;; dead — zero callers
#_(def search-icon-style
  {:top 6
   :right 25})

(def search-input-parent-style
  {:background-color "rgba(0,0,0,0.15)"})

;; dead — zero callers
#_(def transparent-search-input-style
  (assoc search-input-style :color :transparent))

;;;; ====================================================================
;;;; Header Helpers
;;;; ====================================================================

(defn handle-user-menu
  "Show user dropdown menu on hover.
   Pre-existing: reads bounding-rect/width/right/window-width but
   only uses display — the extra DOM reads are harmless but unnecessary."
  [e]
  (let [user-header (js/document.getElementById "user-header")
        user-menu (js/document.getElementById "user-menu")
        bounding-rect (.getBoundingClientRect user-header)
        width (.-offsetWidth user-header)
        bottom (.-bottom bounding-rect)
        right (.-right bounding-rect)
        style (.-style user-menu)
        window-width js/document.documentElement.clientWidth]
    (set! (.-display style) "block")))

(defn hide-user-menu
  "Hide user dropdown menu on mouse-out."
  [e]
  (let [user-menu (js/document.getElementById "user-menu")
        style (.-style user-menu)]
    (set! (.-display style) "none")))

(defn search-input-keypress
  "Dispatch search on Enter keypress."
  [e]
  (when (= "Enter" (.-key e)) (dispatch [:set-search-text (.. e -target -value)])))

(defn set-search-text
  "Dispatch search text from input change event."
  [e]
  (dispatch [:set-search-text (views-common/event-value e)]))

(defn set-search-text-empty
  "Clear search text."
  [e]
  (dispatch [:set-search-text ""]))

(defn open-orcacle
  "Open the Orcacle search overlay."
  []
  (dispatch [:open-orcacle]))

;;;; ====================================================================
;;;; Header Components
;;;; ====================================================================

(defn social-icon
  "Branded social media icon link (FontAwesome)."
  [icon link]
  [:a.p-5.opacity-5.hover-opacity-full.main-text-color
   {:style social-icon-style
    :href link :target :_blank}
   [:i.fab
    {:class (str "fa-" icon)}]])

(defn user-header-view
  "User avatar + login/logout dropdown in the header bar."
  []
  (let [username @(subscribe [:username])
        mobile? @(subscribe [:mobile?])]
    [:div#user-header.pointer.posn-rel
     (when username
       {:on-mouse-over handle-user-menu
        :on-mouse-out hide-user-menu})
     [:div.flex.align-items-c
      [:div.user-icon [views-common/svg-icon "orc-head" 40 ""]]
      (if username
        [:span.f-w-b.t-a-r
         (when (not @(subscribe [:mobile?])) [:span.m-r-5 username])]
        [:span.pointer.flex.flex-column.align-items-end
         [:span.orange.underline.f-w-b.m-l-5
          {:style views-common/login-style
           :on-click views-common/dispatch-route-to-login}
          [:span "LOGIN"]]])
      (when username
        [:i.fa.m-l-5.fa-caret-down])]
     [:div#user-menu.shadow.f-w-b
      {:style user-menu-style
       :on-click hide-user-menu}
      [:div.p-10.opacity-5.hover-opacity-full
       {:on-click views-common/dispatch-logout}
       "LOG OUT"]
      [:div.p-10.opacity-5.hover-opacity-full
       {:on-click views-common/dispatch-route-to-my-account}
       "ACCOUNT"]]
     ;; dead — original if/else login/logout version
     #_(if username
         [:span.f-w-b.t-a-r
          (if (not @(subscribe [:mobile?])) [:span.m-r-5 username])
          [:span.underline.pointer
           {:style views-common/login-style
            :on-click views-common/dispatch-logout}
           "LOG OUT"]]
         [:span.pointer.flex.flex-column.align-items-end
          [:span.orange.underline.f-w-b.m-l-5
           {:style views-common/login-style
            :on-click views-common/dispatch-route-to-login}
           [:span "LOGIN"]]])]))

(defn header-tab
  "Navigation tab in the header bar with optional dropdown buttons.
   Pre-existing: when buttons are present, on-click returns #(swap!)
   instead of calling (swap!) — hover handles the dropdown, so the
   click handler is effectively a no-op for tabbed navigation."
  []
  (let [hovered? (r/atom false)]
    (fn [title icon on-click disabled active device-type & buttons]
      (let [mobile? (= :mobile device-type)]
        [:div.f-w-b.f-s-14.t-a-c.header-tab.m-l-2.m-r-2.posn-rel
         {:on-click (fn [e] (if (seq buttons)
                              #(swap! hovered? not)
                              (on-click e)))
          :on-mouse-enter #(reset! hovered? true)
          :on-mouse-leave #(reset! hovered? false)
          :style (when active views-common/active-style)
          :class (str (if disabled "disabled" "pointer")
                           " "
                           (when (not mobile?) " w-110"))}
         [:div.p-10
          {:class (when (not active) (if disabled "opacity-2" "opacity-6 hover-opacity-full"))}
          (let [size (if mobile? 24 48)] (views-common/svg-icon icon size ""))
          (when (not mobile?)
            [:div.title.uppercase title])]
         (when (and (seq buttons)
                  @hovered?)
           [:div.uppercase.shadow
            {:style (if mobile? mobile-header-menu-item-style header-menu-item-style)}
            (doall
             (map
              (fn [{:keys [name route]}]
                ^{:key name}
                [:div.p-10.opacity-5.hover-opacity-full
                 (let [current-route @(subscribe [:route])]
                   {:style (when (or (= route current-route)
                                   (= route (get current-route :handler))) views-common/active-style)
                    :on-click (views-common/route-handler route)})
                 name])
              buttons))])]))))

(defn app-header
  "Top-level application header: logo, search bar, user menu, and
   navigation tabs for characters, spells, monsters, items, encounters,
   and 'My Content'."
  []
  (let [device-type @(subscribe [:device-type])
        mobile? (= :mobile device-type)
        active-route @(subscribe [:route])]
      [:div#app-header.app-header.flex.flex-column.justify-cont-s-b.white
       [:div.app-header-bar.container
        [:div.content
         [:div.flex.align-items-c.h-100-p
          [:div.flex.justify-cont-s-b.align-items-c.w-100-p.p-l-20.p-r-20.h-100-p
           views-common/logo
           (let [search-text @(subscribe [:search-text])
                 search-text? @(subscribe [:search-text?])]
             [:div
              {:class (if mobile? "p-l-10 p-r-10" "p-l-20 p-r-20 flex-grow-1")}
              [:div.b-rad-5.flex.align-items-c
               {:style search-input-parent-style}
               (when (not mobile?)
                 [:div.p-l-20.flex-grow-1
                  [:input.w-100-p.main-text-color
                   {:style search-input-style
                    :value search-text
                    :on-key-press search-input-keypress
                    :on-change set-search-text
                    :placeholder "search"}]])
               [:div.p-r-10.pointer
                {:on-click open-orcacle}
                [views-common/svg-icon "magnifying-glass" (if mobile? 32 48) ""]]]])
           [user-header-view]]]]]
       [:div.container
        [:div.content
         [:div.flex.w-100-p.align-items-end
          {:class (if mobile? "justify-cont-s-b" "justify-cont-s-b")}
          [:div
           {:style {:min-width "53px"}}
           [:a {:href "https://www.patreon.com/DungeonMastersVault" :target :_blank}
            [:img.h-32.m-l-10.m-b-5.pointer.opacity-7.hover-opacity-full
             {:src (if mobile?
                     "https://c5.patreon.com/external/logo/downloads_logomark_color_on_navy.png"
                     "https://c5.patreon.com/external/logo/become_a_patron_button.png")}]]
           (when (not mobile?)
             [:div.main-text-color.p-10
              (social-icon "facebook-f" "https://www.facebook.com/groups/252484128656613/")
              (social-icon "twitter" "https://twitter.com/thDMV")
              (social-icon "reddit-alien" "https://www.reddit.com/r/dungeonmastersvault/")])]
          [:div.flex.m-b-5.m-t-5.justify-cont-s-b.app-header-menu
           [header-tab
            "characters"
            "battle-gear"
            views-common/route-to-character-list-page
            false
            (routes/dnd-e5-char-page-routes (or (:handler active-route) active-route))
            device-type
            {:name "Character List"
             :route routes/dnd-e5-char-list-page-route}
            {:name "Character Builder"
             :route routes/dnd-e5-char-builder-route}
            {:name "Parties"
             :route routes/dnd-e5-char-parties-page-route}]
           [header-tab
            "spells"
            "spell-book"
            views-common/route-to-spell-list-page
            false
            (routes/dnd-e5-spell-page-routes (or (:handler active-route) active-route))
            device-type
            {:name "Spell List"
             :route routes/dnd-e5-spell-list-page-route}
            {:name "Spell Builder"
             :route routes/dnd-e5-spell-builder-page-route}]
           [header-tab
            "monsters"
            "spiked-dragon-head"
            views-common/route-to-monster-list-page
            false
            (routes/dnd-e5-monster-page-routes (or (:handler active-route) active-route))
            device-type
            {:name "Monster List"
             :route routes/dnd-e5-monster-list-page-route}
            {:name "Monster Builder"
             :route routes/dnd-e5-monster-builder-page-route}]
           [header-tab
            "items"
            "all-for-one"
            views-common/route-to-item-list-page
            false
            (routes/dnd-e5-item-page-routes
             (or (:handler active-route)
                 active-route))
            device-type
            {:name "Item List"
             :route routes/dnd-e5-item-list-page-route}
            {:name "Item Builder"
             :route routes/dnd-e5-item-builder-page-route}]
           [header-tab
            "encounters"
            "dungeon-gate"
            views-common/route-to-my-encounters-page
            false
            (routes/dnd-e5-my-encounters-routes
             (or (:handler active-route)
                 active-route))
            device-type
            {:name "Combat Tracker"
             :route routes/dnd-e5-combat-tracker-page-route}
            {:name "Encounter Builder"
             :route routes/dnd-e5-encounter-builder-page-route}]
           [header-tab
            "My Content"
            "beer-stein"
            views-common/route-to-my-content-page
            false
            (routes/dnd-e5-my-content-routes
             (or (:handler active-route)
                 active-route))
            device-type
            {:name "Content List"
             :route routes/dnd-e5-my-content-route}
            {:name "Feat Builder"
             :route routes/dnd-e5-feat-builder-page-route}
            {:name "Background Builder"
             :route routes/dnd-e5-background-builder-page-route}
            {:name "Language Builder"
             :route routes/dnd-e5-language-builder-page-route}
            {:name "Race Builder"
             :route routes/dnd-e5-race-builder-page-route}
            {:name "Subrace Builder"
             :route routes/dnd-e5-subrace-builder-page-route}
            {:name "Class Builder"
             :route routes/dnd-e5-class-builder-page-route}
            {:name "Subclass Builder"
             :route routes/dnd-e5-subclass-builder-page-route}
            {:name "Eldritch Invocation Builder"
             :route routes/dnd-e5-invocation-builder-page-route}
            {:name "Pact Boon Builder"
             :route routes/dnd-e5-boon-builder-page-route}
            {:name "Selection Builder"
             :route routes/dnd-e5-selection-builder-page-route}]]]]]]))
