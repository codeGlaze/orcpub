(ns orcpub.dnd.e5.views
  (:require [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [reagent.core :as r]
            [orcpub.route-map :as routes]
            [orcpub.common :as common]
            [orcpub.entity :as entity]
            [orcpub.components :as comps]
            [orcpub.entity-spec :as es]
            [orcpub.pdf-spec :as pdf-spec]
            [orcpub.dice :as dice]
            [orcpub.entity.strict :as se]
            [orcpub.dnd.e5.subs :as subs]
            [orcpub.dnd.e5.equipment-subs]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.backgrounds :as bg]
            [orcpub.dnd.e5.languages :as langs]
            [orcpub.dnd.e5.selections :as selections]
            [orcpub.dnd.e5.races :as races]
            [orcpub.dnd.e5.classes :as classes]
            [orcpub.dnd.e5.feats :as feats]
            [orcpub.dnd.e5.units :as units]
            [orcpub.dnd.e5.party :as party]
            [orcpub.dnd.e5.folder :as folder]
            [orcpub.dnd.e5.character.random :as char-random]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [orcpub.registration :as registration]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.damage-types :as damage-types]
            [orcpub.dnd.e5.monsters :as monsters]
            [orcpub.dnd.e5.encounters :as encounters]
            [orcpub.dnd.e5.combat :as combat]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.skills :as skills]
            [orcpub.dnd.e5.equipment :as equip]
            [orcpub.dnd.e5.weapons :as weapon]
            [orcpub.dnd.e5.armor :as armor]
            [orcpub.dnd.e5.display :as disp]
            [orcpub.dnd.e5.template :as t]
            [orcpub.dnd.e5.views-2 :as views-2]
            [orcpub.template :as template]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.dnd.e5.events :as events]
            [orcpub.ver :as v]
            [clojure.string :as s]
            [cljs.reader :as reader]
            [orcpub.user-agent :as user-agent]
            [bidi.bidi :as bidi]
            [orcpub.dnd.e5.views.common
             :refer [svg-icon make-event-handler make-arg-event-handler
                     export-pdf download-form event-value orange
                     dropdown labeled-dropdown
                     display-section list-display-section list-item-section
                     details-button section-header-2
                     character-display-name actions-amount-many
                     loading-style logo header message]]
            [orcpub.dnd.e5.views.header
             :refer [app-header search-input-style
                     search-input-keypress set-search-text
                     set-search-text-empty]]))

(def debug-data-style {:width "400px" :height "450px"})

(defn clj->json
  [ds]
  (.stringify js/JSON (clj->js ds) nil 2))

(defn debug-data []
  (let [expanded? (r/atom false)]
    (fn []
      [:div.t-a-r
       [:div.orange.pointer.underline
        {:on-click (make-event-handler ::e5/export-all-plugins-pretty-print)
         :title "Development - Download all Orcbrews as Pretty Print, if you click this button it will take a long time to generate the orcbrew.  Click and wait."}
        [:i.fa.fa-cloud-download-alt]]
       [:div.orange.pointer.underline
        {:on-click #(swap! expanded? not)
         :title "Development - Debug Info" }
        [:i.fa.fa-bug {:class (when @expanded? "white")}]]
       (when @expanded?
         [:textarea.m-t-5
          {:read-only true
           :style debug-data-style
           :value (str {:browser (user-agent/browser)
                        :browser-version (user-agent/browser-version)
                        :device-type (user-agent/device-type)
                        :platform (user-agent/platform)
                        :platform-version (user-agent/platform-version)
                        :character (char/to-strict @(subscribe [:character]))})}])
       (when @expanded?
         [:textarea.m-t-5
          {:read-only true
           :style debug-data-style
           :value (clj->json {:browser (user-agent/browser)
                              :browser-version (user-agent/browser-version)
                              :device-type (user-agent/device-type)
                              :platform (user-agent/platform)
                              :platform-version (user-agent/platform-version)
                              :character (char/to-strict @(subscribe [:character]))})}])
       ])))

(defn dice-roll-result [{:keys [total rolls mod raw-mod plus-minus]}]
  [:div.white.f-s-32.flex.align-items-c
   (svg-icon "rolling-dices" 36 "")
   [:div.m-l-10
    [:span.f-w-b total]
    [:span.m-l-10.m-r-10 "="]
    [:span (s/join " + " rolls)]
    (when (not= 0 raw-mod) [:span (if (pos? plus-minus) " + " " - ")])
    (when (not= 0 raw-mod) [:span raw-mod])]])

(defn spell-field [name value]
  [:div
   [:span.f-w-b name ":"]
   [:span.m-l-10 value]])

(defn columns-style [num]
  {:line-height "19px"
   :column-count num
   :-webkit-column-count num
   :-moz-column-count num})

(def two-columns-style
  (columns-style 2))

(def three-columns-style
  (columns-style 3))

(def two-columns-second-empty-style
  {:width "50%"})

(defn paragraphs [str & [single-column?]]
  (let [mobile? @(subscribe [:mobile?])
        ps (s/split str #"\n")
        p-els (doall
               (map-indexed
                (fn [i p]
                  ^{:key i} [:p p])
                ps))]
    (if (or mobile?
            single-column?)
      [:div
       p-els]
      [:div
       {:style (if (= 1 (count ps))
                 two-columns-second-empty-style
                 two-columns-style)}
       p-els])))

(defn requires-attunement [attunement]
  (str
   " (requires attunement"
   (when (-> attunement set :any not)
     (str " by a "
          (common/list-print
           (map
            (fn [kw]
              (case kw
                :good " creature of good alignment"
                :evil " creature of evil alignment"
                (common/kw-to-name kw)))
            attunement) "or")))
   ")"))

(defn item-summary [{:keys [::mi/owner ::mi/name ::mi/type ::mi/item-subtype ::mi/rarity ::mi/attunement] :as item}]
  (when item
    [:div.p-b-20.flex.align-items-c
     (when owner
       [:div.m-r-5 [svg-icon "beer-stein" 24]])
     [:div
      [:span.f-s-24.f-w-b (or (:name item) name)]
      [:div.f-s-16.i.f-w-b.opacity-5
       (str (when type (common/safe-capitalize-kw type))
            (when (keyword? item-subtype)
              (str " (" (common/safe-capitalize-kw item-subtype) ")"))
            ", "
            (if (string? rarity)
              rarity
              (common/kw-to-name rarity))
            (when attunement
              (requires-attunement attunement)))]]]))

(defn item-details [{:keys [::mi/summary ::mi/description ::mi/attunment]} single-column?]
  (when (or summary description)
    (paragraphs (or summary description) single-column?)))

(defn item-component [item & [hide-summary? single-column?]]
  [:div.m-l-10.l-h-19
   (when (not hide-summary?)
     [:div [item-summary item]])
   [:div [item-details item single-column?]]])

(defn magic-item-result [item]
  [:div.white
   [:div.flex
    (svg-icon "orb-wand" 36 "")
    [item-component item]]])

(defn name-result [{:keys [sex race subrace] :as result}]
  [:div.white
   [:span.f-s-24.f-w-b (:name result)]
   [:div
    [:span.f-s-14.opacity-5.i (s/join " " (map (fn [k] (when k (name k))) [sex race subrace]))]]])

(defn tavern-name-result [name]
  [:span.f-s-24.f-w-b.white name])

(defn spell-summary [name level school ritual include-name? & [subheader-size]]
  [:div.p-b-20
   (when include-name? [:span.f-s-24.f-w-b name])
   [:div.i.f-w-b.opacity-5
    {:class (str "f-s-" (or subheader-size 18))}
    (str (when (pos? level)
           (str (common/ordinal level) "-level"))
         " "
         (common/safe-capitalize school) (if ritual " (can be cast as ritual)" "")
         (when (zero? level)
           " cantrip"))]])

(defn spell-component [{:keys [name level school casting-time ritual range duration components description summary page source] :as spell} include-name? & [subheader-size]]
  [:div.m-l-10.l-h-19
   [spell-summary name level school ritual include-name? subheader-size]
   (spell-field "Casting Time" casting-time)
   (spell-field "Range" range)
   (spell-field "Duration" duration)
   (let [{:keys [verbal somatic material material-component]} components]
     (spell-field "Components" (str (s/join ", " (remove
                                                  nil?
                                                  [(when verbal "V")
                                                   (when somatic "S")
                                                   (when material "M")]))
                                    (when material-component
                                      (str " (" material-component ")")))))
   [:div.m-t-10
    (if description
      (paragraphs description)
      [:div
       (when summary (paragraphs summary))
       #_[:span (str "(" (disp/source-description source page) " for more details)")]])]])

(defn spell-result [spell]
  [:div.white
   [:div.flex
    (svg-icon "spell-book" 36 "")
    [spell-component spell true]]])

(defn spell-results [results]
  [:div.white
   [:div.flex
    (svg-icon "spell-book" 36 36)
    [:div.m-l-10
     (doall
      (map
       (fn [{:keys [key name level school ritual casting-time range duration components description summary page source]}]
         ^{:key name}
         [:div.pointer
          {:on-click (let [spell-page-path (routes/path-for routes/dnd-e5-spell-page-route :key key)
                           spell-page-route (routes/match-route spell-page-path)]
                       (make-event-handler :route spell-page-route))}
          [spell-summary name level school ritual true 14]])
       results))]]])

(defn monster-summary [name size type subtypes alignment]
  [:div.m-r-10
   [:div name]
   [:div.f-s-14.i.opacity-5 (monsters/monster-subheader size type subtypes alignment)]])

(defn monster-results [results]
  [:div.white
   [:div.flex
    (svg-icon "hydra" 36 "")
    [:div.m-l-10
     (doall
      (map
       (fn [{:keys [key name size type subtypes alignment]}]
         ^{:key name}
         [:div.pointer.f-s-24.f-w-600.m-b-20
          {:on-click (fn [_]
                       (let [monster-page-path (routes/path-for routes/dnd-e5-monster-page-route :key key)
                             monster-page-route (routes/match-route monster-page-path)]
                         (dispatch [:route monster-page-route])))}
          [monster-summary name size type subtypes alignment]])
       results))]]])


(defn print-bonus-map [m]
  (s/join ", "
          (map
           (fn [[k v]] (str (common/safe-capitalize-kw k) " " (common/bonus-str v)))
           m)))

(def max-width-300
  {:max-width "300px"})


(defn monster-component [{:keys [name description size type subtypes hit-points alignment armor-class armor-notes speed saving-throws skills damage-vulnerabilities damage-resistances damage-immunities condition-immunities senses languages challenge traits actions legendary-actions source page] :as monster}]
  (let [traits-by-type (group-by :type traits)
        traits (traits-by-type nil)
        actions (concat actions (traits-by-type :action))
        legendary (traits-by-type :legendary-action)
        legendary-actions (if (seq legendary)
                            (update legendary-actions :actions concat legendary)
                            legendary-actions)]
    [:div.m-l-10.l-h-19
     (when (not @(subscribe [:mobile?])) {:style two-columns-style})
     [:span.f-s-24.f-w-b name]
     [:div.f-s-18.i.f-w-b (monsters/monster-subheader size type subtypes alignment)]
     (spell-field "Armor Class" (str armor-class (when armor-notes (str " (" armor-notes ")"))))
     (let [{:keys [mean die-count die modifier]} hit-points]
       (spell-field "Hit Points" (str die-count
                                      "d"
                                      die
                                      (when modifier (common/mod-str modifier))
                                      (let [mean
                                            (or mean
                                                (when (and die die-count)
                                                  (dice/dice-mean-round-down
                                                   die-count
                                                   die
                                                   (or modifier 0))))]
                                        (when mean (str " (" mean ")"))))))
     (spell-field "Speed" speed)
     [:div.m-t-10.flex.justify-cont-s-a.m-b-10
      {:style max-width-300}
      (doall
       (map
        (fn [ability-key]
          ^{:key ability-key}
          [:div.t-a-c.p-5
           [:div.f-w-b.f-s-14 (s/upper-case (common/safe-name ability-key))]
           (let [ability-value (get monster ability-key)]
             [:div ability-value " (" (common/bonus-str (opt/ability-bonus ability-value)) ")"])])
        [:str :dex :con :int :wis :cha]))]
     (when (seq saving-throws)
       (spell-field "Saving Throws" (print-bonus-map saving-throws)))
     (when skills (spell-field "Skills" (print-bonus-map skills)))
     (when damage-vulnerabilities (spell-field "Damage Vulnerabilities" damage-vulnerabilities))
     (when damage-resistances (spell-field "Damage Resistances" damage-resistances))
     (when damage-immunities (spell-field "Damage Immunities" damage-immunities))
     (when condition-immunities (spell-field "Condition Immunities" condition-immunities))
     (when senses (spell-field "Senses" senses))
     (when languages (spell-field "Languages" languages))
     (when challenge (spell-field "Challenge" (str
                                             (case challenge
                                               0.125 "1/8"
                                               0.25 "1/4"
                                               0.5 "1/2"
                                               challenge)
                                             " ("
                                             (monsters/challenge-ratings challenge)
                                             " XP)")))
     (when traits
       [:div.m-t-20
        (doall
         (map-indexed
          (fn [i {:keys [name description]}]
            ^{:key i}
            [:div.m-t-10.wsp-prw (spell-field name description)])
          traits))])
     (when actions
       [:div.m-t-20
        [:div.i.f-w-b.f-s-18 "Actions"]
        [:div
         (doall
          (map-indexed
           (fn [i {:keys [name notes description]}]
             ^{:key i}
             [:div.m-t-10.wsp-prw (spell-field (str name " " notes) description)])
           actions))]])
     (when legendary-actions
       [:div.m-t-20
        [:div.i.f-w-b.f-s-18 "Legendary Actions"]
        (when (:description legendary-actions)
          [:div (:description legendary-actions)])
        (when (:actions legendary-actions)
          [:div
           (doall
            (map-indexed
             (fn [i {:keys [name notes description]}]
               ^{:key i}
               [:div.m-t-10 (spell-field (str name " " notes) description)])
             (:actions legendary-actions)))])])
     (when description
       [:div.m-t-10 (str description)])]))

(defn monster-result [monster]
  [:div.white
   [:div.flex
    (svg-icon "hydra" 36 "")
    [monster-component monster]]])

(defn search-results []
  (when-let [{{:keys [result] :as top-result} :top-result
            results :results
            :as search-results}
           @(subscribe [:search-results])]
    [:div
     (when top-result
       [:div.p-20.m-b-20
        (let [type (:type top-result)]
          (case type
            :dice-roll (dice-roll-result result)
            :spell (spell-result result)
            :monster (monster-result result)
            :magic-item (magic-item-result result)
            :name (name-result result)
            :tavern-name (tavern-name-result result)
            nil))])
     (when (seq results)
       (doall
        (map
         (fn [{:keys [type results]}]
           ^{:key type}
           [:div.p-20
            (case type
              :spell (spell-results results)
              :monster (monster-results results))])
         results)))]))

(def oracle-frame-style
  {:overflow-y :scroll
   :position :fixed
   :z-index 1
   :background-color "rgba(0,0,0,0.95)"
   :top 0
   :left 0
   :right 0
   :bottom 0})

(def close-icon-style
  {:top 0
   :right 0
   :padding "17px"})

(def close-button-style
  {:position :fixed
   :top 20
   :right 40})

(def orcacle-input-style
  (merge search-input-style
         {:background-color "rgba(255,255,255,0.1)"}))

(defn close-orcacle []
  (dispatch [:close-orcacle]))

;; Used in legal footer below. template.cljc has a separate srd-link for character_builder.
(def srd-link
  [:a.orange {:href "/SRD-OGL_V5.1.pdf" :target "_blank"} "the 5e SRD"])

(defn orcacle []
  (let [search-text @(subscribe [:search-text])]
    [:div.flex.flex-column.h-100-p.white
     {:style oracle-frame-style}
     [:i.fa.fa-times-circle.f-s-24.orange.pointer
      {:on-click close-orcacle
       :style close-button-style}]
     [:div
      [:div.flex.justify-cont-s-a.m-t-10
       [:div.flex.align-items-c.pointer
        {:on-click close-orcacle}
        [:span.f-s-32 "Orcacle"]
        [:div.m-l-10 (svg-icon "hood" 48 "")]]]]
     [:div
      [:div.p-10
       [:div.posn-rel
        [:input.input.orcacle-input
         {:value search-text
          :on-change set-search-text
          :on-key-press search-input-keypress
          :style orcacle-input-style}]
        [:i.fa.fa-times.posn-abs.f-s-24.pointer
         {:style close-icon-style
          :on-click set-search-text-empty}]]
       [:span.f-s-14.i.opacity-5 "\"8d10 + 2\", \"magic missile\", \"kobold\", \"female calishite name\", \"tavern name\", etc."]]
      [:div.flex-grow-1
       [search-results]]]]))

(defn content-page [title button-cfgs content & {:keys [hide-header-message? frame?]}]
  ;; Plain atom (not r/atom) mirrors the :orcacle-open? subscription value
  ;; for the scroll handler, which runs as a DOM event listener outside
  ;; Reagent's reactive context. Synced from the render fn below.
  (let [orcacle-open?* (atom false)
        on-scroll (fn [e]
                    (when-not @orcacle-open?*
                      (let [app-header (js/document.getElementById "app-header")
                            header-height (.-offsetHeight app-header)
                            scroll-top (.-scrollTop (.-documentElement (.-target e)))
                            sticky-header (js/document.getElementById "sticky-header")]
                        (if (>= scroll-top header-height)
                          (set! (.-display (.-style sticky-header)) "block")
                          (set! (.-display (.-style sticky-header)) "none")))))]
    (r/create-class
     {:component-did-mount (fn [comp]
                             (when-not frame?
                               (js/window.addEventListener "scroll" on-scroll))
                             (js/window.scrollTo 0,0))
      :component-will-unmount (fn [comp]
                                (when-not frame?
                                  (js/window.removeEventListener "scroll" on-scroll)))
      :reagent-render
      (fn [title button-cfgs content & {:keys [hide-header-message? frame?]}]
        (let [srd-message-closed? @(subscribe [:srd-message-closed?])
              orcacle-open? @(subscribe [:orcacle-open?])
              theme @(subscribe [:theme])
              mobile? @(subscribe [:mobile?])]
          (reset! orcacle-open?* orcacle-open?)
          [:div.app.min-h-full
           {:class theme
            :on-scroll (when-not frame?
                         (fn [e]))}
           (when-not frame?
             [download-form])
           (when @(subscribe [:loading])
             [:div {:style loading-style}
              [:div.flex.justify-cont-s-a.align-items-c.h-100-p
               [:img.h-200.w-200.m-t-200 {:src "/image/spiral.gif"}]]])
           (when-not frame?
             [app-header])
           (when orcacle-open?
             [orcacle])
           (let [hdr [header title button-cfgs :frame? frame?]]
             [:div
              [:div#sticky-header.sticky-header.w-100-p.posn-fixed
               [:div.flex.justify-cont-c
                [:div#header-container.f-s-14.main-text-color.content
                 hdr]]]              
              [:div.flex.justify-cont-c.main-text-color
               [:div.content hdr]]
        ;  Banner for announcements
              #_[:div.m-l-20.m-r-20.f-w-b.f-s-18.container.m-b-10.main-text-color
                 (if (and (not srd-message-closed?)
                          (not hide-header-message?))
                   [:div
                    (if (not frame?)
                      [:div.content.bg-lighter.p-10.flex
                       [:div.flex-grow-1
                        [:div "Site is based on SRD rules. " srd-link "."]]
                       [:i.fa.fa-times.p-10.pointer
                        {:on-click #(dispatch [:close-srd-message])}]])])]
              [:div#app-main.container
               [:div.content.w-100-p content]]
              [:div.main-text-color.flex.justify-cont-c
               [:div.content.f-w-n.f-s-12
                [:div.flex.justify-cont-s-b.align-items-c.flex-wrap.p-10
                 [:div
                  [:div.m-b-5 "Icons made by Lorc, Caduceus, and Delapouite. Available on " [:a.orange {:href "http://game-icons.net"} "http://game-icons.net"]]
                  [:div.m-b-5 "Artwork provided by the talented Sandra. Available on " [:a.orange {:href "https://www.deviantart.com/sandara" :target :_blank} "Deviantart"]]]
                 [:div.m-l-10
                  [:a.orange {:href "https://github.com/Orcpub/orcpub/issues" :target :_blank} "Feedback/Bug Reports"]]
                 [:div.m-l-10.m-r-10.p-10
                  [:a.orange {:href "/privacy-policy" :target :_blank} "Privacy Policy"]
                  [:a.orange.m-l-5 {:href "/terms-of-use" :target :_blank} "Terms of Use"]]
                 [:div.legal-footer
                  [:p "© " (.getFullYear (js/Date.)) " " [:a.orange {:href "https://github.com/Orcpub/orcpub/" :target :_blank} "Orcpub"]]
                  [:p "This site is based on " srd-link " - Wizards of the Coast, Dungeons & Dragons, D&D, and their logos are trademarks of Wizards of the Coast LLC in the United States and other countries. © " (.getFullYear (js/Date.)) " Wizards. All Rights Reserved."]
                  [:p "This site is not affiliated with, endorsed, sponsored, or specifically approved by Wizards of the Coast LLC."]
                  [:p "Version " (v/version) " (" (v/date) ") " (v/description) " edition"]]]
                [debug-data]]]])]))})))

;; dead — zero callers (4 style defs)
#_(def row-style
  {:border-bottom "1px solid rgba(255,255,255,0.5)"})

#_(def light-row-style
  {:border-bottom "1px solid rgba(0,0,0,0.5)"})

#_(def list-style
  {:border-top "2px solid rgba(255,255,255,0.5)"})
#_(def thumbnail-style
  {:height "100px"
   :max-width "200px"})

(defn other-user-component [owner & [text-classes show-follow?]]
  (let [following-users @(subscribe [:following-users])
        following? (get following-users owner)
        username @(subscribe [:username])]
    [:div.flex.m-l-10.align-items-c
     (svg-icon "orc-head" 32)
     [:div.f-s-18.m-l-5
      {:class text-classes}
      owner]
     (when (and show-follow? username (not= username owner))
       [:button.form-button.m-l-10.p-6
        {:on-click #(dispatch [(if following?
                                 :unfollow-user
                                 :follow-user)
                               owner])}
        (if following?
          "unfollow"
          "follow")])]))


(defn character-summary-2 [{:keys [::char/character-name
                                   ::char/image-url
                                   ::char/race-name
                                   ::char/subrace-name
                                   ::char/age
                                   ::char/sex
                                   ::char/height
                                   ::char/weight
                                   ::char/hair
                                   ::char/eyes
                                   ::char/skin
                                   ::char/classes
                                   ::char/alignment
                                   ::char/background]
                            :as summary}
                           include-name?
                           owner
                           show-owner?
                           show-follow?]
  (let [username @(subscribe [:username])
        display-name (when include-name? (character-display-name summary))]
    [:div.flex.justify-cont-s-b.w-100-p.align-items-c
     [:div.flex.align-items-c.align-items-t
      (when image-url
        [:img.m-r-20.m-t-10.m-b-10.image-character-thumbnail {:src image-url }])
      [:div.flex.character-summary.m-t-20.m-b-20
       (when display-name [:span.m-r-20.m-b-5
                                               [:span.character-name display-name]
                                               [:div.f-s-12.m-t-5.opacity-6.character-background background]
                                               [:div.f-s-12.m-t-5.opacity-6.character-alignment alignment]
                                               (when (not (s/blank? age)) [:div.f-s-12.m-t-5.opacity-6.character-age "Age: " age])
                                               (when (not (s/blank? sex)) [:div.f-s-12.m-t-5.opacity-6.character-sex "Sex: " sex])
                                               (when (not (s/blank? height)) [:div.f-s-12.m-t-5.opacity-6.character-height "Height: " height])
                                               (when (not (s/blank? weight)) [:div.f-s-12.m-t-5.opacity-6.character-weight "Weight: " weight])])
       [:span.m-r-10.m-b-5
        [:span.character-race-name race-name]
        [:div.f-s-12.m-t-5.opacity-6.character-subrace-name subrace-name]
        (when (not (s/blank? hair)) [:div.f-s-12.m-t-5.opacity-6.character-hair "Hair: " hair])
        (when (not (s/blank? eyes)) [:div.f-s-12.m-t-5.opacity-6.character-eyes "Eyes: " eyes])
        (when (not (s/blank? skin)) [:div.f-s-12.m-t-5.opacity-6.character-skin "Skin: " skin])]
       (when (seq classes)
         [:span.flex
          (map-indexed
           (fn [i v]
             (with-meta v {:key i}))
           (interpose
            [:span.m-l-5.m-r-5 "/"]
            (map
             (fn [{:keys [::char/class-name ::char/level ::char/subclass-name]}]
               [:span
                [:div.class-name (str class-name)] [:div.level (str "(" level ")")]
                [:div.f-s-12.m-t-5.opacity-6.sub-class-name (when subclass-name subclass-name)]])
             classes)))])]]
     (when (and show-owner?
              (some? owner)
              (some? username)
              (not= username owner))
       [:div.m-l-10 [other-user-component owner nil show-follow?]])]))

(defn character-summary [id & [include-name?]]
  (let [character-name @(subscribe [::char/character-name id])
        age @(subscribe [::char/age id])
        sex @(subscribe [::char/sex id])
        height @(subscribe [::char/height id])
        weight @(subscribe [::char/weight id])
        hair @(subscribe [::char/hair id])
        eyes @(subscribe [::char/eyes id])
        skin @(subscribe [::char/skin id])
        image-url @(subscribe [::char/image-url id])
        race @(subscribe [::char/race id])
        subrace @(subscribe [::char/subrace id])
        levels @(subscribe [::char/levels id])
        classes @(subscribe [::char/classes id])
        alignment  @(subscribe [::char/alignment id])
        background  @(subscribe [::char/background id])
        {:keys [::se/owner] :as strict-character} @(subscribe [::char/character id])]
    (character-summary-2
     {::char/character-name character-name
      ::char/age age
      ::char/sex sex
      ::char/height height
      ::char/weight weight
      ::char/hair hair
      ::char/eyes eyes
      ::char/skin skin
      ::char/image-url image-url
      ::char/race-name race
      ::char/subrace-name subrace
      ::char/alignment alignment
      ::char/background background
      ::char/classes (map
                      (fn [class-kw]
                        (let [{:keys [class-name class-level subclass-name] :as cfg}
                              (get levels class-kw)]
                          {::char/class-name class-name
                           ::char/level class-level
                           ::char/subclass-name subclass-name}))
                      classes)}
     include-name?
     owner
     true
     true)))

;; dead — character_builder.cljs has its own realize-char
#_(defn realize-char [built-char]
  (reduce-kv
   (fn [m k v]
     (let [realized-value (es/entity-val built-char k)]
       (if (fn? realized-value)
         m
         (assoc m k realized-value))))
   (sorted-map)
   built-char))

;; dead — zero callers
#_(def summary-style
  {:padding "33px 0"})


;; dead — zero callers
#_(defn svg-icon-section [title icon-name content]
  [:div.m-t-20
   [:span.f-s-16.f-w-600 title]
   [:div.flex.align-items-c
    (svg-icon icon-name 32)
    [:div.f-s-24.m-l-10.f-w-b content]]])


;; dead — zero callers
#_(defn compare-spell [spell-1 spell-2]
  (let [key-fn (juxt :key :ability)]
    (compare (key-fn spell-1) (key-fn spell-2))))


(defn spellcaster-levels-table []
  (let [expanded? (r/atom false)]
    (fn [spell-slot-factors total-spellcaster-levels levels mobile?]
      [:div.f-s-14.f-w-n
       [:div.flex.justify-cont-s-b
        [:div
         [:span.f-w-b.f-s-16 "Total Spellcaster Levels: "]
         [:span.f-s-16.f-w-n total-spellcaster-levels]]
        (details-button @expanded? #(swap! expanded? not))]
       (when @expanded?
         [:div:div.f-s-14
          [:table.w-100-p.t-a-l.striped
           [:tbody
            [:tr.f-w-b
             [:th.p-10 "Class"]
             [:th.p-10 (if mobile? "Sub." "Subclass")]
             [:th.p-10 (if mobile? "Lvl." "Level")]
             [:th.p-10 (if mobile? "Mult." "Multiplier")]
             [:th.p-10 (if mobile? "Tot." "Total")]]
            (doall
             (map
              (fn [[class-key factor]]
                (let [{:keys [class-name class-level subclass-name]} (levels class-key)]
                  ^{:key class-key}
                  [:tr
                   [:td.p-10 class-name]
                   [:td.p-10 subclass-name]
                   [:td.p-10 class-level]
                   [:td.p-10 (if (= 1 factor) 1 (str "1/" factor))]
                   [:td.p-10 (int (/ class-level factor))]]))
              spell-slot-factors))
            [:tr
             [:td.p-10 "Total"]
             [:td]
             [:td]
             [:td]
             [:td.p-10 total-spellcaster-levels]]]]])])))

(def highlight-spell-slot-row-style
  {:background-color "rgba(255,255,255,0.3)"})

(defn spell-slots-table []
  (let [expanded? (r/atom false)
        checkboxes-expanded? (r/atom false)]
    (fn [id spell-slots spell-slot-factors total-spellcaster-levels levels mobile? pact-magic?]
      (let [multiclass? (> (count spell-slot-factors) 1)
            first-factor-key (when spell-slot-factors (-> spell-slot-factors first key))
            first-class-level (when first-factor-key (-> levels first-factor-key :class-level))]
        [:div.f-s-14.f-w-n
         [:div.flex.justify-cont-s-b
          [:div
           [:span.f-w-b.f-s-16 (str "Slots" (when multiclass? " (Multiclass)"))]]
          (when (not pact-magic?)
            (details-button @expanded? #(swap! expanded? not)))]
         [:div.f-w-n.f-s-14
          [:table.w-100-p.t-a-l.striped
           [:tbody
            [:tr.f-w-b.f-s-12
             [:th.p-5 (if mobile? "Lvl." "Caster Levels")]
             (doall
              (map
               (fn [i]
                 ^{:key i}
                 [:th.p-5 (if (and mobile?
                                   (or @expanded?
                                       (> (count spell-slots) 8)))
                            (inc i)
                            (common/ordinal (inc i)))])
               (range (if @expanded?
                        9
                        (apply max (keys spell-slots))))))
             [:th]]
            (if (and (not pact-magic?) @expanded?)
              (doall
               (map
                (fn [lvl]
                  (let [highlight? (or (and multiclass?
                                            (= total-spellcaster-levels lvl))
                                       (and (not multiclass?)
                                            (= first-class-level lvl)))]
                    ^{:key lvl}
                    [:tr
                     {:class (when highlight?
                                    "f-w-b")
                      :style (when highlight?
                               highlight-spell-slot-row-style)}
                     [:td.p-10 lvl]
                     (let [total-slots (opt/total-slots lvl (if multiclass? 1 (-> spell-slot-factors first val)))]
                       (doall
                        (map
                         (fn [spell-lvl]
                           ^{:key spell-lvl}
                           [:td.p-10 (get total-slots spell-lvl)])
                         (range 1 10))))]))
                (range 1 21)))
              [:tr.pointer
               {:on-click #(swap! checkboxes-expanded? not)}
               [:td.p-10 (if multiclass?
                           total-spellcaster-levels
                           first-class-level)]
               (doall
                (map
                 (fn [level]
                   ^{:key level}
                   [:td.p-10 (spell-slots (inc level))])
                 (range (apply max (keys spell-slots)))))
               [:td.p-r-5
                [:i.fa.orange
                 {:class (if @checkboxes-expanded? "fa-caret-up" "fa-caret-down")}]]])]]]
         (when @checkboxes-expanded?
           [:div.bg-light.p-5
            (doall
             (map
              (fn [[level slots]]
                ^{:key level}
                [:div.p-10.flex.justify-cont-s-b
                 [:span.f-w-b (str (common/ordinal level) " level")]
                 [:div
                  (doall
                   (for [i (range slots)]
                     ^{:key i}
                     [:span.m-l-5
                      {:on-click #(dispatch [::char/toggle-spell-slot-used id level i])}
                      (comps/checkbox @(subscribe [::char/spell-slot-used? id level i]) false)]))]])
              spell-slots))])]))))


(defn button-roll-fn [message roll]
  (fn [e]
    (if (.-shiftKey e)
      (dispatch [:show-message-2 (str message " w/ Disadvantage: " (dice/dice-roll-text-2 roll) "  |  " (dice/dice-roll-text-2 roll))]) 
      (if (or (.-ctrlKey e) (.-metaKey e))
        (dispatch [:show-message-2 (str message " w/ Advantage: " (dice/dice-roll-text-2 roll) "  |  " (dice/dice-roll-text-2 roll))])
        (dispatch [:show-message-2 (str message " " (dice/dice-roll-text-2 roll))])))))

(def button-roll-handler (memoize button-roll-fn))

(defn roll-button [message roll & {:keys [text disable-tooltip style]}]
  (let [mobile? @(subscribe [:mobile?])
        button [:button.roll-button
                {:on-click (fn [e]
                             (.stopPropagation e)
                             ((button-roll-handler message roll) e))
                 :style style}
                (or text "Roll")]]
    (if (or mobile? disable-tooltip)
      button
      [:div.tooltip
       button
       [:span.tooltiptext "ctrl+click for advantage shift+click for disadvantage"]])))

(defn cast-spell-component []
  (let [selected-level (r/atom nil)]
    (fn [id lvl]
      (let [slot-levels-available @(subscribe [::char/slot-levels-available id])
            usable-slot-levels (drop-while
                                (partial > lvl)
                                slot-levels-available)]
        [:div.flex.justify-cont-end.align-items-c
         [:div.w-80
          [:span "Cast at level"]
          [dropdown
           {:items (map
                    (fn [i]
                      {:value i
                       :title i})
                    usable-slot-levels)
            :value (or @selected-level lvl)
            :on-change #(reset! selected-level (js/parseInt %))}]]
         [:div.m-l-5
          [:button.form-button.p-10
           {:class (when (empty? usable-slot-levels) "disabled")
            :on-click #(when (seq usable-slot-levels)
                         (dispatch [::char/use-spell-slot id (or @selected-level (first usable-slot-levels))]))}
           "cast spell"]]]))))

(def expanded-spell-background-style
  {:background-color "rgba(0,0,0,0.1)"})

(defn spell-row [id lvl spell-modifiers prepares-spells prepared-spells-by-class {:keys [key ability qualifier class always-prepared?]} expanded? on-click prepare-spell-count prepared-spell-count]
  (let [spell-map @(subscribe [::spells/spells-map])
        spell (spell-map key)
        cls-mods (get spell-modifiers class)
        spell-dc (get cls-mods :spell-save-dc)
        remaining-preps (- prepare-spell-count
                           prepared-spell-count)]
    [[:tr.spell.pointer
      {:on-click on-click}
      [:td.p-l-10.p-b-5.p-t-5.f-w-b
       (when (and (pos? lvl)
                (get prepares-spells class))
         [:span.m-r-5
          {:class (when always-prepared?
                         "cursor-disabled")
           :on-click (fn [e]
                       (when (not always-prepared?)
                         (dispatch [::char/toggle-spell-prepared id class key]))
                       (.stopPropagation e))}
          (let [selected? (or always-prepared?
                              (get-in prepared-spells-by-class [class key]))]
            (comps/checkbox
             selected?
             (and (not selected?)
                  (or always-prepared?
                      (not (pos? remaining-preps))))))])
       (:name spell)]
      [:td.p-l-10.p-b-5.p-t-5 class]
      [:td.p-l-10.p-b-5.p-t-5 (when ability (s/upper-case (common/safe-name ability)))]
      [:td.p-l-10.p-b-5.p-t-5 (get cls-mods :spell-save-dc)]
      [:td.p-l-10.p-b-5.p-t-5 (common/bonus-str (get cls-mods :spell-attack-modifier))]
      [:td.p-l-10.p-b-5.p-t-5
       (roll-button
        (str (:name spell) " attack: ")
        (str "1d20" (common/mod-str (get cls-mods :spell-attack-modifier)))
        :text (str "1d20" (common/mod-str (get cls-mods :spell-attack-modifier))))]
      [:td.p-l-10.p-b-5.p-t-5.pointer.orange
       [:i.fa
        {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]
     (when expanded?
       [:tr {:style expanded-spell-background-style}
        [:td {:col-span 7}
         [:div.p-10
          (when (pos? lvl)
            [cast-spell-component id lvl])
          [spell-component spell false 14]]]])]))

(defn toggle-spell-expanded-fn [expanded-spells k]
  #(swap! expanded-spells update k not))

(def toggle-spell-expanded! (memoize toggle-spell-expanded-fn))

(defn spells-table []
  (let [expanded-spells (r/atom {})
        mobile? @(subscribe [:mobile?])]
    (fn [id lvl spells spell-modifiers hide-unprepared? prepare-spell-count-fn]
      (let [prepares-spells @(subscribe [::char/prepares-spells id])
            prepared-spells-by-class @(subscribe [::char/prepared-spells-by-class id])]
        [:div.m-t-10.m-b-30
         [:div.flex.justify-cont-s-b
          [:div
           [:span.f-w-b.i (if (pos? lvl)
                            (str (common/ordinal lvl) " Level")
                            "Cantrip")]
           (when hide-unprepared?
             [:span.i.opacity-5.m-l-5 "(unprepared hidden)"])]
          (when (pos? lvl)
            [:span.f-w-b (str @(subscribe [::char/spell-slots-remaining id lvl]) " remaining")])]
         [:table.w-100-p.t-a-l.striped
          [:tbody.spells
           [:tr.f-w-b.f-s-12
            [:th.p-l-10.p-b-5.p-t-5 (if (and (not (zero? lvl))
                                               (seq prepares-spells))
                                        "Prepared? / Name"
                                        "Name")]
            [:th.p-l-10.p-b-5.p-t-5 (if mobile? "Src" "Source")]
            [:th.p-l-10.p-b-5.p-t-5 (if mobile? "Aby" "Ability")]
            [:th.p-l-10.p-b-5.p-t-5 "DC"]
            [:th
             {:class (when (not mobile?) "p-b-10 p-t-10")}
             "Mod."]
            [:th.p-l-10.p-b-5.p-t-5 "Attack"]
            [:th.p-l-10.p-b-5.p-t-5]]
           (doall
            (map-indexed
             (fn [i r]
               (with-meta r {:key i}))
             (mapcat
              (fn [{:keys [key class always-prepared?] :as spell}]
                (let [k (str key class)
                      prepared-spell-count (or (some->> class
                                                        (get prepared-spells-by-class)
                                                        count)
                                               0)
                      prepare-spell-count (prepare-spell-count-fn class)]
                  (when (char/spell-prepared? {:hide-unprepared? hide-unprepared?
                                             :always-prepared? always-prepared?
                                             :lvl lvl
                                             :key key
                                             :class class
                                             :prepares-spells prepares-spells
                                             :prepared-spells-by-class prepared-spells-by-class})
                    (spell-row id
                               lvl
                               spell-modifiers
                               prepares-spells
                               prepared-spells-by-class
                               spell
                               (@expanded-spells k)
                               (toggle-spell-expanded! expanded-spells k)
                               prepare-spell-count
                               prepared-spell-count))))
              (sort-by :key spells))))]]]))))

(defn toggle-hide-unprepared-fn [hide-unprepared?]
  #(swap! hide-unprepared? not))

(def toggle-hide-unprepared! (memoize toggle-hide-unprepared-fn))

(defn spells-tables []
  (let [hide-unprepared? (r/atom false)]
    (fn [id spells-known spell-slots spell-modifiers]
      [:div.f-s-14.f-w-n
       [:div.flex.justify-cont-s-b
        [:span.f-w-b.f-s-16 "Spells By Level"]
        [:button.form-button.p-5
         {:on-click (toggle-hide-unprepared! hide-unprepared?)}
         (if @hide-unprepared?
           "Show All"
           "Hide Unprepared")]]
       (let [prepare-spell-count-fn (memoize @(subscribe [::char/prepare-spell-count-fn id]))]
         (doall
          (map
           (fn [[lvl spells]]
             ^{:key lvl}
             [spells-table id lvl (vals spells) spell-modifiers @hide-unprepared? prepare-spell-count-fn])
           spells-known)))])))

(defn finish-long-rest-fn [id]
  #(dispatch [::char/finish-long-rest id]))

(defn finish-short-rest-fn [id]
  #(dispatch [::char/finish-short-rest id]))

(defn finish-short-rest-warlock-fn [id]
  #(dispatch [::char/finish-short-rest-warlock id]))

(def finish-long-rest-handler (memoize finish-long-rest-fn))

(def finish-short-rest-handler (memoize finish-short-rest-fn))

(def finish-short-rest-handler-warlock (memoize finish-short-rest-warlock-fn))

(defn finish-long-rest-button [id]
  [:button.form-button.p-5
   {:on-click (finish-long-rest-handler id)}
   "finish long rest"])

(defn finish-short-rest-button [id]
  [:button.form-button.p-5.m-l-5
   {:on-click (finish-short-rest-handler id)}
   "finish short rest"])

(defn finish-short-rest-button-warlock [id]
  [:button.form-button.p-5.m-l-5
   {:on-click (finish-short-rest-handler-warlock id)}
   "finish short rest"])

(defn spells-known-section [id spells-known spell-slots spell-modifiers spell-slot-factors total-spellcaster-levels levels]
  (let [mobile? @(subscribe [:mobile?])
        multiclass? (> (count spell-slot-factors) 1)
        prepares-spells @(subscribe [::char/prepares-spells id])
        pact-magic? @(subscribe [::char/pact-magic? id])
        prepare-spell-count-fn @(subscribe [::char/prepare-spell-count-fn id])
        classes (set @(subscribe [::char/classes id]))]
    [display-section
     "Spells"
     "spell-book"
     [:div.m-t-20
      (when multiclass?
        [:div.m-b-20
         [spellcaster-levels-table spell-slot-factors total-spellcaster-levels levels mobile?]])
      (when (or pact-magic? spell-slot-factors)
        [:div.m-b-20
         [spell-slots-table id spell-slots spell-slot-factors total-spellcaster-levels levels mobile? pact-magic?]])
      [:div.m-b-20
       [:span.f-w-b.f-s-16 "Spell Preparation"]
       (if (seq prepares-spells)
         [:table.w-100-p.t-a-l.striped.f-s-12
          [:tbody
           [:tr.f-w-b
            [:th.p-10 "Class"]
            [:th.p-10 "Can Prepare"]]
           (doall
            (map
             (fn [[class-nm]]
               ^{:key class-nm}
               [:tr.f-w-n
                [:td.p-10 class-nm]
                [:td.p-10 (str (prepare-spell-count-fn class-nm) "/day")]])
             prepares-spells))]]
         [:div.f-s-14.f-w-n.i.m-t-5 "You don't need to prepare spells"])]
      [:div.m-b-20
       [spells-tables id spells-known spell-slots spell-modifiers]]]
     nil
     [[finish-long-rest-button id]
      (when (contains? classes :warlock) [finish-short-rest-button-warlock id])]]))

;; dead — zero callers
#_(defn equipment-section [title icon-name equipment equipment-map]
  [list-display-section title icon-name
   (map
    (fn [[equipment-kw {item-qty ::char-equip/quantity
                        equipped? ::char-equip/equipped?
                        :as num}]]
      (str (disp/equipment-name equipment-map equipment-kw)
           " (" (or item-qty num) ")"))
    equipment)])

#_(defn add-links [desc]
  desc
  (let [{:keys [abbr url]} (some (fn [[_ source]]
                                   (if (and (:abbr source)
                                            (re-matches (re-pattern (str ".*" (:abbr source) ".*")) desc))
                             source))
                 disp/sources)
        [before after] (if abbr (s/split desc (re-pattern abbr)))]
    (if abbr
      [:span
       [:span before]
       [:a {:href url :target :_blank} abbr]
       [:span after]]
      desc)))

(defn attack-comp [name description]
  [:p.m-t-10
   [:span.f-w-600.i name "."]
   [:span.f-w-n.m-l-10 description]])

(defn weapon-name [weapon]
  (or (:name weapon)
      (::mi/name weapon)))

(defn weapon-attack-description-short [{:keys [::weapon/ranged?] :as weapon}]
  (disp/attack-description-short (-> weapon
                                     (assoc :attack-type (if ranged? :ranged :melee))
                                     (dissoc :description))))

(defn weapon-attack-description [{:keys [::weapon/ranged?] :as weapon} damage-modifier attack-modifier]
  (disp/attack-description (-> weapon
                               (assoc :attack-type (if ranged? :ranged :melee))
                               (assoc :damage-modifier damage-modifier)
                               (assoc :attack-modifier attack-modifier)
                               (dissoc :description))))

(defn weapon-attack-comp [weapon off-hand? weapon-attack-modifier weapon-damage-modifier]
  [attack-comp
   (str (weapon-name weapon) (when off-hand? " (off hand)"))
   (weapon-attack-description weapon
                              (weapon-damage-modifier weapon off-hand?)
                              (weapon-attack-modifier weapon))])

(defn attacks-section [id]
  (let [attacks @(subscribe [::char/attacks id])
        all-weapons-map @(subscribe [::mi/all-weapons-map])
        main-hand-weapon-kw @(subscribe [::char/main-hand-weapon id])
        main-hand-weapon (when main-hand-weapon-kw (all-weapons-map main-hand-weapon-kw))
        off-hand-weapon-kw @(subscribe [::char/off-hand-weapon id])
        weapon-attack-modifier @(subscribe [::char/best-weapon-attack-modifier-fn id])
        weapon-damage-modifier @(subscribe [::char/best-weapon-damage-modifier-fn id])
        off-hand-weapon (when off-hand-weapon-kw (all-weapons-map off-hand-weapon-kw))]
    (when (or (seq attacks)
            main-hand-weapon)
      (display-section
       "Attacks"
       "pointy-sword"
       [:div.f-s-14
        (when main-hand-weapon
          [:div [weapon-attack-comp main-hand-weapon false weapon-attack-modifier weapon-damage-modifier]])
        (when off-hand-weapon
          [:div [weapon-attack-comp off-hand-weapon true weapon-attack-modifier weapon-damage-modifier]])
        [:div
         (doall
          (map
           (fn [{:keys [name area-type description damage-die damage-die-count damage-type save save-dc] :as attack}]
             ^{:key name}
             [attack-comp name (common/sentensize (disp/attack-description attack))])
           attacks))]]))))

(defn toggle-feature-used-fn [id units k]
  #(dispatch [::char/toggle-feature-used id units k]))

(def toggle-feature-used-handler (memoize toggle-feature-used-fn))

(defn actions-indicators [id nm units amount]
  (if (< amount actions-amount-many)
    ;; small, manageable number of uses
    [:span.m-l-10
     (doall
       (for [i (range amount)]
         (let [k (str nm "-" i)]
           ^{:key i}
           [:span
            {:on-click (toggle-feature-used-handler id units k)}
            [:span.m-r-5 (comps/checkbox @(subscribe [::char/feature-used? id units k]) false)]])))]

    ;; larger number of uses
    (let [initial-value @(subscribe [::char/feature-used-count id units nm amount])]
      [:span.m-l-10
       [comps/selection
        (for [i (range (inc amount))]
          (let [k (str nm "-" i)]
            {:key k
             :name (str i)}))

        (fn on-change [e]
          (let [v (-> e .-target .-value)]
            (when initial-value
              ; we have to dispatch-sync because in the case where id is nil,
              ; this event handler dispatches, so the call below gets
              ; a stale DB value and overwrites this one. It ought be
              ; possible to make it affect the :db directly, but I don't
              ; know what sort of side effects that could have....
              ; See: update-character-fx, and its use of :dispatch; might
              ; be able to replace that with:
              ;  {:db (set-character db (update-fn (:character db)))}
              (dispatch-sync [::char/toggle-feature-used id units initial-value]))
            ((toggle-feature-used-handler id units v))))

        ; if no initial value, assume "all uses available"
        (or initial-value
            (str nm "-" amount))]])))

(defn actions-section [id title icon-name actions]
  (when (seq actions)
    (display-section
      title
      icon-name
      [:div.f-s-14.l-h-19
       (doall
         (map
           (fn [{{:keys [units amount]} :frequency nm :name :as action}]
             ^{:key action}
             [:p.m-t-10
              [:span.f-w-600.i nm]
              [:span.f-w-n.m-l-10.wsp-prw (common/sentensize (disp/action-description action))]
              (when (and amount units)
                (actions-indicators id nm units amount))])
           (common/aloof-sort-by :name actions)))])))

(defn prof-name [prof-map prof-kw]
  (or (-> prof-kw prof-map :name) (common/kw-to-name prof-kw)))

(defn resistance-str [{:keys [value qualifier]}]
  (str (name value)
       (when qualifier (str " (" qualifier ")"))))

;; dead — zero callers
#_(def no-https-images "Sorry, we don't currently support images that start with https")

;; dead — zero callers
#_(defn default-image [race classes]
  (when (and (or (= "Human" race)
               (nil? race))
           (= :barbarian (first classes)))
    "/image/barbarian.png"))


(defn armor-class-section-2 [id]
  [:div
   [:div.p-10.flex.flex-column.align-items-c
    (section-header-2 "Armor Class" "checked-shield")
    [:div.f-s-24.f-w-b.armor-class @(subscribe [::char/current-armor-class id])]]])

(defn basic-section [title icon v show-button]
  [:div
   [:div.p-10.flex.flex-column.align-items-c
    (section-header-2 title icon)
    [:div.f-s-24.f-w-b
     {:class (orcpub.common/->kebab-case title)}
     (if (boolean show-button)
       (roll-button
        (str title " check: ")
        (str "1d20" v)
        :text v
        :style {:font-size "24px" :padding "2px 8px"})
       v)]]])

(def current-hit-points-editor-style
  {:width "60px"
   :margin-top 0})

(defn hit-dice-section-2 [id]
  (let [levels @(subscribe [::char/levels id])]
    (basic-section "Hit Dice"
                   nil
                   (s/join
                    " / "
                    (map
                     (fn [{:keys [class-level hit-die]}] (str class-level "d" hit-die))
                     (vals levels))) false)))

(defn set-current-hit-points-fn [id]
  #(dispatch [::char/set-current-hit-points
              id
              (or (-> %
                      event-value
                      js/parseInt)
                  0)]))

(def set-current-hit-points-handler (memoize set-current-hit-points-fn))

(defn hit-points-section-2 [id]
  (basic-section "Max Hit Points"
                 "health-normal"
                 [:div.flex.align-items-c
                  [:input.input
                   {:style current-hit-points-editor-style
                    :type :number
                    :value (or @(subscribe [::char/current-hit-points id])
                               @(subscribe [::char/max-hit-points id]))
                    :on-change (set-current-hit-points-handler id)}]
                  [:span.m-l-5 "/"]
                  [:span.m-l-5 @(subscribe [::char/max-hit-points id])]] false))

(defn initiative-section-2 [id]
  (basic-section "Initiative" "sprint" (common/mod-str @(subscribe [::char/initiative id])) true))

(defn darkvision-section-2 [id]
  (basic-section "Darkvision" "night-vision" (str @(subscribe [::char/darkvision id]) " ft.") false))

(defn critical-hits-section-2 [id]
  (let [crit-values-str @(subscribe [::char/crit-values-str id])]
    (basic-section "Critical Hits" nil crit-values-str false)))

(defn number-of-attacks-section-2 [id]
  (basic-section "Number of Attacks" nil @(subscribe [::char/number-of-attacks id]) false))

(defn passive-perception-section-2 [id]
  (basic-section "Passive Perception" "awareness" @(subscribe [::char/passive-perception id]) false))

(defn proficiency-bonus-section-2 [id]
  (basic-section "Proficiency Bonus" nil (common/bonus-str @(subscribe [::char/proficiency-bonus id])) false))

(defn skills-section-2 [id]
  (let [skill-profs (or @(subscribe [::char/skill-profs id]) #{})
        skill-bonuses @(subscribe [::char/skill-bonuses id])]
    [:div
     [proficiency-bonus-section-2 id]
     [passive-perception-section-2 id]
     [:div.p-10.flex.flex-column.align-items-c.skills
      (section-header-2 "Skills" "juggler")
      [:table
       [:tbody
        (doall
         (map
          (fn [{skill-name :name skill-key :key icon :icon :as skill}]
            ^{:key skill-key}
            [:tr.t-a-l
             {:class (if (skill-profs skill-key) "f-w-b" "opacity-7")}
             [:td [:div.skill-name
                   (svg-icon icon 18)
                   [:span.m-l-5 skill-name]]]
             [:td.p-1 (roll-button
                   (str skill-name " check: ")
                   (str "1d20" (common/mod-str (skill-bonuses skill-key)))
                   :text (common/bonus-str (skill-bonuses skill-key)))]])
          skills/skills))]]]]))

(defn ability-scores-section-2 [id]
  (let [abilities @(subscribe [::char/abilities id])
        ability-bonuses @(subscribe [::char/ability-bonuses id])
        theme @(subscribe [:theme])]
    [:div
     [:div.f-s-18.f-w-b "Ability Scores"]
     [:div.flex.justify-cont-s-a.m-t-10.ability-scores
      (doall
       (map
        (fn [k]
          ^{:key k}
          [:div
           (t/ability-icon k 24 theme)
           [:div.ability-score-name
            [:span.f-s-20.uppercase (name k)]]
           [:div.f-s-24.f-w-b.ability-score (abilities k)]
           [:div.f-s-12.opacity-5.m-b-2.m-t-2 " mod"]
           [:div.f-s-18.ability-score-modifier (roll-button
                                                (str (clojure.string/upper-case (name k)) " check: ")
                                                (str "1d20 " (common/mod-str (ability-bonuses k)))
                                                :text (common/bonus-str (ability-bonuses k)))]])
        char/ability-keys))]]))

(defn saving-throws-section-2 [id]
  (let [save-bonuses @(subscribe [::char/save-bonuses id])
        saving-throws @(subscribe [::char/saving-throws id])
        theme @(subscribe [:theme])]
    [:div.p-10.flex.flex-column.align-items-c
     (section-header-2 "Saving Throws" "dodging")
     [:table
      [:tbody
       (doall
        (map
         (fn [k]
           ^{:key k}
            [:tr.t-a-l
             {:class (if (saving-throws k) "f-w-b" "opacity-7")}
             [:td [:div
                   (t/ability-icon k 18 theme)
                   [:span.m-l-5.saving-throw-name (s/upper-case (name k))]]]
             [:td.p-1 (roll-button
                   (str (s/upper-case (name k)) " check: ")
                   (str "1d20" (common/mod-str (save-bonuses k)))
                   :text (common/bonus-str (save-bonuses k)))]])
         char/ability-keys))]]]))

(defn feet-str [num]
  (str num " ft."))

(defn speed-section-2 [id]
  (let [speed @(subscribe [::char/base-land-speed id])
        swim-speed @(subscribe [::char/base-swimming-speed id])
        flying-speed @(subscribe [::char/base-flying-speed id])
        speed-with-armor @(subscribe [::char/speed-with-armor id])
        unarmored-speed-bonus @(subscribe [::char/unarmored-speed-bonus id])
        equipped-armor @(subscribe [::char/armor id])
        all-armor @(subscribe [::char/all-armor-inventory id])]
    [:div.p-10
     (section-header-2 "Speed" "walking-boot")
     [:span.f-s-24.f-w-b
      [:span
       [:span [:div.speed (feet-str (+ (or unarmored-speed-bonus 0)
                      (if speed-with-armor
                        (speed-with-armor nil)
                        speed)))]
       (when (or unarmored-speed-bonus
               speed-with-armor)
         [:span.display-section-qualifier-text "(unarmored)"])]]
      (if speed-with-armor
        [:div.f-s-18
         (doall
          (map
           (fn [[armor-kw _]]
             (let [armor (mi/all-armor-map armor-kw)
                   speed (speed-with-armor armor)]
               ^{:key armor-kw}
               [:div
                [:div.speed
                 [:span (feet-str speed)]]
                 [:span.display-section-qualifier-text (str "(" (:name armor) " armor)")]]))
           (dissoc all-armor :shield)))]
        (when unarmored-speed-bonus
          [:div.f-s-18
           [:span
            [:div.speed
            [:span (feet-str speed)]]
            [:span.display-section-qualifier-text "(armored)"]]]))
      (when (and swim-speed (pos? swim-speed))
        [:div.f-s-18
         [:div.speed
         [:span (feet-str swim-speed)]] [:span.display-section-qualifier-text "(swim)"]])
      (when (and flying-speed (pos? flying-speed))
        [:div.f-s-18
         [:div.speed
         [:span (feet-str flying-speed)]] [:span.display-section-qualifier-text "(fly)"]])]]))

(defn personality-section [title & descriptions]
  (when (and (seq descriptions)
           (some (complement s/blank?) descriptions))
    [:div.m-t-20.t-a-l
     [:div.f-w-b.f-s-18 title]
     [:div
      (doall
       (map-indexed
        (fn [i description]
          ^{:key i}
          [:div
           (doall
            (map-indexed
             (fn [j p]
               ^{:key j}
               [:p p])
             (s/split
              description
              #"\n")))])
        descriptions))]]))

(defn description-section [id]
  (let [personality-trait-1 @(subscribe [::char/personality-trait-1 id])
        personality-trait-2 @(subscribe [::char/personality-trait-2 id])
        ideals @(subscribe [::char/ideals id])
        bonds @(subscribe [::char/bonds id])
        flaws @(subscribe [::char/flaws id])
        description @(subscribe [::char/description id])]
    [:div.p-5
     (personality-section "Personality Traits" personality-trait-1 personality-trait-2)
     (personality-section "Ideals" ideals)
     (personality-section "Bonds" bonds)
     (personality-section "Flaws" flaws)
     (personality-section "Description" description)]))

(def notes-style
  {:height "400px"
   :width "100%"})

(def stroke-style
  {:stroke-width "1"})

(def bar-stroke-style
  {:stroke-width "5"
   :stroke orange
   :opacity "0.8"})

(defn set-notes-fn [id]
  #(dispatch [::char/set-notes id %]))

(def set-notes-handler (memoize set-notes-fn))

(defn summary-details [num-columns id]
  (let [built-char @(subscribe [:built-character id])
        {:keys [::entity/owner] :as character} @(subscribe [::char/character id])
        username @(subscribe [:username])
        race @(subscribe [::char/race id])
        classes @(subscribe [::char/classes id])
        background @(subscribe [::char/background id])
        alignment @(subscribe [::char/alignment id])
        all-armor @(subscribe [::char/all-armor id])
        image-url-failed @(subscribe [::char/image-url-failed id])
        image-url @(subscribe [::char/image-url id])
        faction-image-url @(subscribe [::char/faction-image-url id])
        faction-image-url-failed @(subscribe [::char/faction-image-url-failed id])
        armor-class @(subscribe [::char/armor-class id])
        armor-class-with-armor @(subscribe [::char/armor-class-with-armor id])
        total-levels @(subscribe [::char/total-levels id])
        current-level-xps (opt/level-xps total-levels)
        next-level-xps (opt/level-xps (inc total-levels))
        xps (or @(subscribe [::char/xps id])
                current-level-xps)
        fraction (/ (- xps current-level-xps)
                    (- next-level-xps current-level-xps))
        line-length 160
        buffer 10
        progress-length (double (* line-length fraction))
        current-route @(subscribe [:route])
        max-levels? (>= total-levels 20)]
    [:div
     [:div
      [:div.w-100-p.t-a-c
       [:div
        [:div.m-b-20
         [:div.f-w-b.f-s-18 "Experience Points"]
         [:div.flex.justify-cont-s-a
          [:div.flex.flex-wrap.align-items-c
           [:div
            [comps/input-field
             :input
             xps
             #(dispatch [::char/set-current-xps id (js/parseInt %)])
             {:class "input"
              :type :number}]]
           [:div.p-5
            [:div
             [:svg {:width "250px"
                    :view-box "0 0 200 40"}
              [:line.stroke-color {:x1 "10"
                      :y1 "20"
                      :x2 "190"
                      :y2 "20"
                      :style stroke-style}]
              [:line.stroke-color {:x1 "20"
                      :y1 "10"
                      :x2 "20"
                      :y2 "25"
                      :style stroke-style}]
              (when (not max-levels?)
                [:line.stroke-color {:x1 "180"
                                     :y1 "10"
                                     :x2 "180"
                                     :y2 "25"
                                     :style stroke-style}])
              (let [x2 (if max-levels?
                         (if (>= xps (opt/level-xps 20))
                           20
                           0)
                         (+ progress-length buffer 10))]
                (when (and (not (js/isNaN x2))
                         (> x2 buffer))
                  [:line {:x1 (if (pos? current-level-xps)
                                "10"
                                "20")
                          :y1 "17"
                          :x2 (str x2)
                          :y2 "17"
                          :style bar-stroke-style}]))
              [:text.main-text-color {:x "8"
                      :y "30"
                      :fill "white"
                      :font-size "8"}
               (str "Level " total-levels)]
              [:text.main-text-color {:x "9"
                      :y "36"
                      :fill "white"
                      :font-size "6"}
               current-level-xps]
              (when (not max-levels?)
                [:text.main-text-color {:x "165"
                                        :y "30"
                                        :fill "white"
                                        :font-size "8"}
                 (str "Level " (inc total-levels))])
              (when (not max-levels?)
                [:text.main-text-color {:x "165"
                                        :y "36"
                                        :fill "white"
                                        :font-size "6"}
                 next-level-xps])]]]
           (when (and (>= xps next-level-xps)
                    (= (or (:handler current-route)
                           current-route) routes/dnd-e5-char-builder-route))
             [:button.form-button
              {:on-click #(dispatch [::char/level-up id])}
              [:div.flex.align-items-c
               [svg-icon "muscle-up" 24 "white"]
               [:span.m-l-5 "Level Up"]]])]]]
        [ability-scores-section-2 id]
        [:div.flex.p-10.justify-cont-s-a
         [skills-section-2 id]
         [:div
          [armor-class-section-2 id]
          [hit-points-section-2 id]
          [speed-section-2 id]
          [saving-throws-section-2 id]
          [darkvision-section-2 id]]]
        [description-section id]
        [:span.f-s-18.f-w-b.m-b-5 "Notes"]
        [:div.p-l-20.p-r-20
         [:div.w-100-p
          [comps/input-field
           :textarea
           @(subscribe [::char/notes id])
           (set-notes-handler id)
           {:style notes-style
            :class "input"}]]]]]]]))

(defn weapon-details-field [nm value]
  [:div.p-2
   [:span.f-w-b nm ":"]
   [:span.m-l-5 value]])

(defn yes-no [v]
  (if v "yes" "no"))

(defn weapon-details [{:keys [::weapon/description
                              ::weapon/type
                              ::weapon/damage-type
                              ::mi/magical-damage-bonus
                              ::mi/magical-attack-bonus
                              ::mi/magical-damage-type
                              ::weapon/ranged?
                              ::weapon/melee?
                              ::weapon/range
                              ::weapon/two-handed?
                              ::weapon/finesse?
                              ::mi/magical-finesse?
                              ::weapon/link
                              ::weapon/versatile
                              ::weapon/thrown]
                       :as weapon}
                      damage-modifier-fn]
  [:div.m-t-10.i
   (weapon-details-field "Type" (common/safe-name type))
   (if magical-damage-type
     (weapon-details-field "Damage Type" (common/safe-name magical-damage-type))
     (weapon-details-field "Damage Type" (common/safe-name damage-type)))
   (when magical-damage-bonus
     (weapon-details-field "Magical Damage Bonus" magical-damage-bonus))
   (when magical-attack-bonus
     (weapon-details-field "Magical Attack Bonus" magical-attack-bonus))
   (weapon-details-field "Melee/Ranged" (if melee? "melee" "ranged"))
   (when range
     (weapon-details-field "Range" (str (::weapon/min range) "/" (::weapon/max range) " ft.")))
   (if magical-finesse?
     (weapon-details-field "Finesse?" (yes-no magical-finesse?))
     (weapon-details-field "Finesse?" (yes-no finesse?)))
   (weapon-details-field "Two-handed?" (yes-no two-handed?))
   (weapon-details-field "Versatile" (if versatile
                                       (str (::weapon/damage-die-count versatile)
                                            "d"
                                            (::weapon/damage-die versatile)
                                            (common/mod-str (damage-modifier-fn weapon false))
                                            " damage")
                                       "no"))
   (when description
     [:div.m-t-10 description])])

(defn armor-details-section [{:keys [type
                                     base-ac
                                     weight
                                     description
                                     max-dex-mod
                                     min-str
                                     ::mi/magical-ac-bonus
                                     stealth-disadvantage?]
                              :or {magical-ac-bonus 0
                                   base-ac 10}}
                             {shield-magic-bonus ::magical-ac-bonus :or {shield-magic-bonus 0} :as shield}
                             expanded?]
  [:div
   [:div (str (when type (str (common/safe-name type) ", ")) "base AC " (+ magical-ac-bonus shield-magic-bonus base-ac (if shield 2 0)) (when stealth-disadvantage? ", stealth disadvantage"))]
   (when expanded?
     [:div
      [:div.m-t-10.i
       (when type
         (weapon-details-field "Type" (common/safe-name type)))
       (weapon-details-field "Base AC" base-ac)
       (when (not= magical-ac-bonus 0)
         (weapon-details-field "Magical AC Bonus" magical-ac-bonus))
       (when shield
         (weapon-details-field "Shield Base AC Bonus" 2))
       (when (and shield
                (not= shield-magic-bonus 0))
         (weapon-details-field "Shield Magical AC Bonus" shield-magic-bonus))
       (when max-dex-mod
         (weapon-details-field "Max DEX AC Bonus" max-dex-mod))
       (when min-str
         (weapon-details-field "Min Strength" min-str))
       (weapon-details-field "Stealth Disadvantage?" (yes-no stealth-disadvantage?))
       (when weight
         (weapon-details-field "Weight" (str weight " lbs.")))
       (when description
         [:div.m-t-10 (str "Armor: " description)])
       (when (:description shield)
         [:div.m-t-10 (str "Shield: " (:description shield))])]])])

(defn boolean-icon [v]
  [:i.fa {:class (if v "fa-check green" "fa-times red")}])

(defn toggle-details-expanded-fn [expanded-details k]
  #(swap! expanded-details (fn [d] (update d k not))))

(def toggle-details-expanded-handler (memoize toggle-details-expanded-fn))

(defn armor-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [all-armor @(subscribe [::char/all-armor id])
            ac-with-armor @(subscribe [::char/armor-class-with-armor id])
            armor-profs (set @(subscribe [::char/armor-profs id]))
            device-type @(subscribe [:device-type])
            mobile? (= :mobile device-type)
            proficiency-bonus @(subscribe [::char/proficiency-bonus id])
            all-armor-details (map @(subscribe [::mi/all-armor-map]) (keys all-armor))
            armor-details (armor/non-shields all-armor-details)
            shield-details (armor/shields all-armor-details)]
        [:div
         [:div.flex.align-items-c
          (svg-icon "breastplate" 32)
          [:span.m-l-5.f-w-b.f-s-18 "Armor"]]
         [:div
          [:table.w-100-p.t-a-l.striped
           [:tbody.armor
            [:tr.f-w-b
             {:class (when mobile? "f-s-12")}
             [:th.p-10 "Name"]
             (when (not mobile?) [:th.p-10 "Proficient?"])
             [:th.p-10 "Details"]
             [:th.p-10 "AC"]
             [:th.p-10]]
            (doall
             (for [{:keys [name description type key] :as armor} (conj armor-details nil)
                   shield (conj shield-details nil)]
               (let [k (str key (:key shield))
                     ac (ac-with-armor armor shield)
                     proficient? (and
                                  (or (nil? shield)
                                      (armor-profs :shields))
                                  (or
                                   (nil? armor)
                                   (armor-profs key)
                                   (armor-profs type)))
                     expanded? (@expanded-details k)]
                 ^{:key (str key (:key shield))}
                 [:tr.item.pointer
                  {:on-click (toggle-details-expanded-handler expanded-details k)}
                  [:td.p-10.f-w-b (str (or (::mi/name armor) (:name armor) "unarmored")
                                       (when shield (str " + " (:name shield))))]
                  (when (not mobile?)
                    [:td.p-10 (boolean-icon proficient?)])
                  [:td.p-10.w-100-p
                   [:div
                    (armor-details-section armor shield expanded?)]]
                  [:td.p-10.f-w-b.f-s-18 ac]
                  [:td.pointer
                   [:div.orange
                    #_(if (not mobile?)
                        [:span.underline (if expanded? "less" "more")])
                    [:i.fa.m-l-5
                     {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]])))]]]]))))

(defn section-header [icon title]
  [:div.flex.align-items-c
   (svg-icon icon 32)
   [:span.m-l-5.f-w-b.f-s-18 title]])

(defn weapons-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [all-weapons @(subscribe [::char/all-weapons id])
            weapon-profs (set @(subscribe [::char/weapon-profs id]))
            weapon-attack-modifier @(subscribe [::char/best-weapon-attack-modifier-fn id])
            weapon-damage-modifier @(subscribe [::char/best-weapon-damage-modifier-fn id])
            has-weapon-prof @(subscribe [::char/has-weapon-prof id])
            device-type @(subscribe [:device-type])
            mobile? (= :mobile device-type)
            proficiency-bonus @(subscribe [::char/proficiency-bonus id])
            all-weapons-map @(subscribe [::mi/all-weapons-map])]
        [:div
         [section-header "crossed-swords" "Weapons"]
         [:div
          [:table.w-100-p.t-a-l.striped
           [:tbody.weapons
            [:tr.f-w-b
             {:class (when mobile? "f-s-12")}
             [:th.p-10 "Name"]
             (when (not mobile?) [:th.p-10 "Proficient?"])
             [:th.p-10 "Details"]
             [:th.t-a-c (if mobile? "Atk" [:div.w-60 "Attack"])]
             [:th.t-a-c (if mobile? "Dmg" [:div.w-60 "Damage"])]
             [:th.p-10]]
            (doall
             (map
              (fn [[weapon-key {:keys [equipped?]}]]
                (let [{:keys [name description ranged? ::weapon/type ::weapon/damage-die-count ::weapon/damage-die ::weapon/versatile] :as weapon} (all-weapons-map weapon-key)
                      proficient? (when has-weapon-prof (has-weapon-prof weapon))
                      expanded? (@expanded-details weapon-key)
                      damage-modifier (weapon-damage-modifier weapon)
                      versatile-damage-die-count (:orcpub.dnd.e5.weapons/damage-die-count versatile)
                      versatile-damage-die (:orcpub.dnd.e5.weapons/damage-die versatile)
                      droll (str damage-die-count "d" damage-die)]
                  (when (not= type :ammunition)
                    ^{:key weapon-key}
                    [:tr.weapon.pointer
                     {:on-click (toggle-details-expanded-handler expanded-details weapon-key)}
                     [:td.p-10.f-w-b (or (:name weapon)
                                         (::mi/name weapon))]
                     (when (not mobile?)
                       [:td.p-10 (boolean-icon proficient?)])
                     [:td.p-10.w-100-p
                      [:div
                       (weapon-attack-description-short weapon)]
                      (when expanded?
                        (weapon-details weapon weapon-damage-modifier))]
                     [:td (roll-button
                           (str name " attack: ")
                           (str "1d20" (common/mod-str (weapon-attack-modifier weapon)))
                           :text (str "1d20" (common/mod-str (weapon-attack-modifier weapon))))]
                     [:td (roll-button
                           (str name " damage: ")
                           (str damage-die-count "d" damage-die (common/mod-str (weapon-damage-modifier weapon)))
                           :text (str damage-die-count "d" damage-die (common/mod-str (weapon-damage-modifier weapon)))
                           :style {:width "100%"})
                      (when versatile
                        (roll-button
                         (str name " versatile damage: ")
                         (str versatile-damage-die-count "d" versatile-damage-die (common/mod-str (weapon-damage-modifier weapon)))
                         :text (str "v " versatile-damage-die-count "d" versatile-damage-die (common/mod-str (weapon-damage-modifier weapon)))
                         :style {:width "100%"}))]
                     [:td.pointer
                      [:div.orange
                       #_(if (not mobile?)
                           [:span.underline (if expanded? "less" "more")])
                       [:i.fa.m-l-5
                        {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]])))
              all-weapons))]]]]))))

(defn magic-item-rows [expanded-details magic-item-cfgs magic-weapon-cfgs magic-armor-cfgs]
  (let [magic-item-map @(subscribe [::mi/all-magic-items-map])
        mobile? @(subscribe [:mobile?])]
    (mapcat
     (fn [[item-kw item-cfg]]
       (let [{:keys [::mi/name ::mi/type ::mi/item-subtype ::mi/rarity ::mi/attunement ::mi/description ::mi/summary] :as item} (magic-item-map item-kw)
             expanded? (@expanded-details item-kw)]
         [[:tr.pointer
           {:on-click (toggle-details-expanded-handler expanded-details item-kw)}
           [:td.p-10.f-w-b (or (:name item) name)]
           [:td.p-10 (str (common/kw-to-name type)
                          ", "
                          (common/kw-to-name rarity))]
           [:td.p-r-5.pointer
            [:div.orange
             #_(if (not mobile?)
                 [:span.underline (if expanded? "less" "more")])
             [:i.fa.m-l-5
              {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]]
          (when expanded?
            [:tr
             [:td.p-10
              {:col-span 3}
              [item-component item true true]]])]))
     (merge
      magic-item-cfgs
      magic-weapon-cfgs
      magic-armor-cfgs))))

(defn magic-items-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [mobile? @(subscribe [:mobile?])
            magic-item-cfgs @(subscribe [::char/magic-items id])
            magic-weapon-cfgs @(subscribe [::char/magic-weapons id])
            magic-armor-cfgs @(subscribe [::char/magic-items id])]
        [:div
         [:div.flex.align-items-c
          (svg-icon "orb-wand" 32)
          [:span.m-l-5.f-w-b.f-s-18 "Other Magic Items"]]
         [:div.f-s-14
          [:table.w-100-p.t-a-l.striped
           [:tbody.other-magic-items
            [:tr.f-w-b
             {:class (when mobile? "f-s-12")}
             [:th.p-10 "Name"]
             [:th.p-10 "Details"]
             [:th]]
            (doall
             (map-indexed
              (fn [i row]
                (with-meta
                  row
                  {:key i}))
              (magic-item-rows expanded-details
                               magic-item-cfgs
                               magic-weapon-cfgs
                               magic-armor-cfgs)))]]]]))))

(defn other-equipment-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [mobile? @(subscribe [:mobile?])
            equipment-cfgs (merge
                            @(subscribe [::char/equipment id])
                            (zipmap (range) @(subscribe [::char/custom-equipment id])))]
        [:div
         [:div.flex.align-items-c
          (svg-icon "backpack" 32)
          [:span.m-l-5.f-w-b.f-s-18 "Other Equipment"]]
         [:div
          [:table.w-100-p.t-a-l.striped
           [:tbody.equipment
            [:tr.f-w-b
             {:class (when mobile? "f-s-12")}
             [:th.p-10 "Name"]
             [:th.p-10 "Qty."]
             [:th.p-10 "Details"]
             [:th]]
            (doall
             (map
              (fn [[item-kw item-cfg]]
                (let [item-name (::char-equip/name item-cfg)
                      {:keys [name cost weight] :as item} (equip/equipment-map item-kw)
                      ;;expanded? (@expanded-details item-kw)
                      ]
                  ^{:key item-kw}
                  [:tr.item
                   [:td.p-10.f-w-b (or (:name item) item-name)]
                   [:td.p-10 (::char-equip/quantity item-cfg)]
                   [:td.p-10
                    [:div
                     [:div
                      (str (when cost
                             (str (:num cost)
                                  " "
                                  (common/safe-name (:type cost))
                                  ", "))
                           weight)]]]]))
              equipment-cfgs))]]]]))))

(defn treasure-section []
  (r/with-let [expanded-details (r/atom {})]
    (fn [id]
      (let [mobile? @(subscribe [:mobile?])
            treasure-cfgs (merge
                             @(subscribe [::char/treasure id])
                             (zipmap (range) @(subscribe [::char/custom-treasure id])))]
        [:div
         [:div.flex.align-items-c
          (svg-icon "cash" 32)
          [:span.m-l-5.f-w-b.f-s-18 "Treasure"]]
         [:div
          [:table.w-100-p.t-a-l.striped
           [:tbody
            [:tr.f-w-b
             {:class (when mobile? "f-s-12")}
             [:th.p-10 "Name"]
             [:th.p-10 "Qty."]
             [:th]]
            (doall
             (map
              (fn [[treasure-kw treasure-cfg]]
                (let [treasure-name (::char-equip/name treasure-cfg)
                      {:keys [::equip/name] :as treasure} (equip/treasure-map treasure-kw)]
                  ^{:key treasure-kw}
                  [:tr
                   [:td.p-10.f-w-b (or (:name treasure) treasure-name)]
                   [:td.p-10 (::char-equip/quantity treasure-cfg)]]))
              treasure-cfgs))]]]]))))


(defn skill-details-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [skill-profs (or @(subscribe [::char/skill-profs id]) #{})
            skill-bonuses @(subscribe [::char/skill-bonuses id])
            skill-expertise @(subscribe [::char/skill-expertise id])
            device-type @(subscribe [:device-type])
            mobile? (= :mobile device-type)]
        [:div
         [:div.flex.align-items-c
          (svg-icon "juggler" 32)
          [:span.m-l-5.f-w-b.f-s-18 "Skills"]]
         [:div
          [:table.w-100-p.t-a-l.striped
           [:tbody
            [:tr.f-w-b
             {:class (when mobile? "f-s-12")}
             [:th.p-5 "Name"]
             [:th.p-5 (if mobile? "Prof?" "Proficient?")]
             (when skill-expertise
               [:th.p-5 "Expertise?"])
             [:th.p-5 (when (not mobile?) [:div.w-40 "Bonus"])]]
            (doall
             (map
              (fn [{:keys [key name]}]
                (let [proficient? (key skill-profs)
                      expertise? (key skill-expertise)]
                  ^{:key key}
                  [:tr
                   [:td.p-5.f-w-b name]
                   [:td.p-5 (boolean-icon proficient?)]
                   (when skill-expertise
                     [:td.p-5 (boolean-icon expertise?)])
                   [:td.p-5.f-s-18.f-w-b (roll-button
                         (str name " check: ")
                         (str "1d20" (common/mod-str (key skill-bonuses)))
                         :text (common/bonus-str (key skill-bonuses)))]]))
              skills/skills))]]]]))))

(defn tool-prof-details-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [tool-profs (or @(subscribe [::char/tool-profs id]) #{})
            tool-expertise @(subscribe [::char/tool-expertise id])
            tool-bonus-fn @(subscribe [::char/tool-bonus-fn id])
            device-type @(subscribe [:device-type])
            mobile? (= :mobile device-type)]
        (when (seq tool-profs)
          [:div
           [:div.flex.align-items-c
            (svg-icon "stone-crafting" 32)
            [:span.m-l-5.f-w-b.f-s-18 "Tools"]]
           [:div
            [:table.w-100-p.t-a-l.striped
             [:tbody
              [:tr.f-w-b
               {:class (when mobile? "f-s-12")}
               [:th.p-10 "Name"]
               [:th.p-10 (if mobile? "Prof?" "Proficient?")]
               (when tool-expertise
                 [:th.p-10 "Expertise?"])
               [:th.p-10 (when (not mobile?) [:div.w-40 "Bonus"])]]
              (doall
               (map
                (fn [[kw]]
                  (let [name (-> equip/tools-map kw :name)
                        proficient? (kw tool-profs)
                        expertise? (kw tool-expertise)]
                    ^{:key kw}
                    [:tr
                     [:td.p-10.f-w-b name]
                     [:td.p-10 (boolean-icon proficient?)]
                     (when tool-expertise
                       [:td.p-10 (boolean-icon expertise?)])
                     [:td.p-10.f-s-18.f-w-b (common/bonus-str (tool-bonus-fn kw))]
                     [:td (roll-button (str name " check: ") (str "1d20" (common/mod-str (tool-bonus-fn kw))))]]))
                tool-profs))]]]])))))


(defn proficiency-details [num-columns id]
  (let [ability-bonuses @(subscribe [::char/ability-bonuses id])
        language-map @(subscribe [::langs/language-map])]
    [:div.details-columns
     {:class (when (= 2 num-columns) "flex")}
     [:div.flex-grow-1.details-column-2
      {:class (when (= 2 num-columns) "w-50-p m-l-20")}
      [skill-details-section-2 id]
      [:div.m-t-20
       [tool-prof-details-section-2 id]]
      [list-item-section "Languages" "lips" @(subscribe [::char/languages id]) (partial prof-name language-map)]
      [list-item-section "Tool Proficiencies" "stone-crafting" @(subscribe [::char/tool-profs id]) (fn [[kw]] (prof-name equip/tools-map kw))]
      [list-item-section "Weapon Proficiencies" "bowman" @(subscribe [::char/weapon-profs id]) (partial prof-name @(subscribe [::mi/custom-and-standard-weapons-map]))]
      [list-item-section "Armor Proficiencies" "mailed-fist" @(subscribe [::char/armor-profs id]) (partial prof-name armor/armor-map)]]]))

(defn equipped-section-dropdown [label cfg]
  [:div.m-t-10.m-r-5
   [labeled-dropdown
    label
    cfg]])

(def none-item
  {:value :none
   :title "<none>"})

(defn wield-fn [event-kw id]
  #(dispatch [event-kw id (keyword %)]))

(def wield-handler (memoize wield-fn))

(defn equipped? [v]
  (and (some? v)
       (not= :none v)))

(defn obj-to-item [{:keys [name key]}]
  {:title name
   :value key})

(defn equipped-section [id]
  [:div
   [section-header "battle-gear" "Equipped Items"]
   [:div
    (let [all-armor-map @(subscribe [::mi/all-armor-map])
          worn-armor @(subscribe [::char/worn-armor id])
          wielded-shield @(subscribe [::char/wielded-shield id])
          best-armor-combo @(subscribe [::char/best-armor-combo])]
      [:div.flex.flex-wrap
       (let [carried-armor @(subscribe [::char/carried-armor id])]
         [equipped-section-dropdown
          "Worn Armor"
          {:items (cons
                   none-item
                   (map
                    (fn [[key]]
                      (let [{:keys [name] :as item} (all-armor-map key)]
                        {:title (weapon-name item)
                         :value key}))
                    carried-armor))
           :value (or worn-armor (-> best-armor-combo :armor :key))
           :on-change (wield-handler ::char/don-armor id)}])
       (let [carried-shields @(subscribe [::char/carried-shields id])]
         [equipped-section-dropdown
          "Wielded Shield"
          {:items (cons
                   none-item
                   (map
                    (fn [[key]]
                      (let [{:keys [name] :as item} (all-armor-map key)]
                        {:title (weapon-name item)
                         :value key}))
                    carried-shields))
           :value (or wielded-shield (-> best-armor-combo :shield :key))
           :on-change (wield-handler ::char/wield-shield id)}])])
    (let [all-weapons-map @(subscribe [::mi/all-weapons-map])
          carried-weapons @(subscribe [::char/carried-weapons id])
          main-hand-weapon-kw @(subscribe [::char/main-hand-weapon id])
          main-hand-weapon (all-weapons-map main-hand-weapon-kw)
          off-hand-weapon-kw @(subscribe [::char/off-hand-weapon id])
          dual-wield-weapon? @(subscribe [::char/dual-wield-weapon-fn id])]
      [:div.flex.flex-wrap
       [equipped-section-dropdown
        "Main Hand Weapon"
        {:items (cons
                 none-item
                 (map
                  (fn [[key]]
                    (let [{:keys [name] :as item} (all-weapons-map key)]
                      {:title (weapon-name item)
                       :value key}))
                  carried-weapons))
         :value main-hand-weapon-kw
         :on-change (wield-handler ::char/wield-main-hand-weapon id)}]
       (when (or (equipped? off-hand-weapon-kw)
               (and (equipped? main-hand-weapon-kw)
                    (dual-wield-weapon? main-hand-weapon)))
         [equipped-section-dropdown
          "Off Hand Weapon"
          {:items (cons
                   none-item
                   (sequence
                    (comp
                     (filter
                      (fn [[key]]
                        (-> all-weapons-map
                            key
                            dual-wield-weapon?)))
                     (map
                      (fn [[key]]
                        (let [{:keys [name] :as item} (all-weapons-map key)]
                          {:title (weapon-name item)
                           :value key}))))
                    carried-weapons))
           :value off-hand-weapon-kw
           :on-change (wield-handler ::char/wield-off-hand-weapon id)}])
       #_[:div.flex.flex-wrap
          [equipped-section-dropdown
           "Attuned Magic Item 1"
           {:items [none-item]
            :value nil
            :on-change (fn [])}]
          [equipped-section-dropdown
           "Attuned Magic Item 2"
           {:items [none-item]
            :value nil
            :on-change (fn [])}]
          [equipped-section-dropdown
           "Attuned Magic Item 3"
           {:items [none-item]
            :value nil
            :on-change (fn [])}]]])]])

(defn combat-details [num-columns id]
  (let [weapon-profs @(subscribe [::char/weapon-profs id])
        armor-profs @(subscribe [::char/armor-profs id])
        resistances @(subscribe [::char/resistances id])
        damage-immunities @(subscribe [::char/damage-immunities id])
        damage-vulnerabilities @(subscribe [::char/damage-vulnerabilities id])
        condition-immunities @(subscribe [::char/condition-immunities id])
        immunities @(subscribe [::char/immunities id])
        weapons @(subscribe [::char/weapons id])
        armor @(subscribe [::char/armor id])
        magic-weapons @(subscribe [::char/magic-weapons id])
        magic-armor @(subscribe [::char/magic-armor id])
        critical-hit-values @(subscribe [::char/critical-hit-values id])
        non-standard-crits? (> (count critical-hit-values) 1)
        number-of-attacks @(subscribe [::char/number-of-attacks id])
        non-standard-attack-number? (> number-of-attacks 1)]
    [:div
     [:div.flex.flex-wrap.justify-cont-s-a.t-a-c
      [armor-class-section-2 id]
      [hit-points-section-2 id]
      [speed-section-2 id]
      [initiative-section-2 id]]
     (when (or non-standard-crits?
             non-standard-attack-number?)
       [:div.flex.justify-cont-s-a.t-a-c
        [critical-hits-section-2 id]
        [hit-dice-section-2 id]
        [number-of-attacks-section-2 id]])
     [:div.m-t-30
      [attacks-section id]]
     [:div.m-t-30
      [equipped-section id]]
     [:div.m-t-30
      [list-item-section "Damage Resistances" "surrounded-shield" resistances resistance-str]]
     [:div.m-t-30
      [list-item-section "Damage Vulnerabilities" nil damage-vulnerabilities resistance-str]]
     [:div.m-t-30
      [list-item-section "Damage Immunities" nil damage-immunities resistance-str]]
     [:div.m-t-30
      [list-item-section "Condition Immunities" nil condition-immunities resistance-str]]
     [:div.m-t-30
      [list-item-section "Immunities" nil immunities resistance-str]]
     [:div.m-t-30
      [weapons-section-2 id]]
     [:div.m-t-30
      [armor-section-2 id]]
     [:div
      {:class (when (= 2 num-columns) "w-50-p m-l-20")}
      [list-item-section "Weapon Proficiencies" "bowman" weapon-profs (partial prof-name @(subscribe [::mi/custom-and-standard-weapons-map]))]
      [list-item-section "Armor Proficiencies" "mailed-fist" armor-profs (partial prof-name armor/armor-map)]]]))

(defn has-frequency-units? [trait]
  (some-> trait :frequency :units))

(defn features-details [num-columns id]
  (let [resistances @(subscribe [::char/resistances id])
        damage-immunities @(subscribe [::char/damage-immunities id])
        damage-vulnerabilities @(subscribe [::char/damage-vulnerabilities id])
        condition-immunities @(subscribe [::char/condition-immunities id])
        immunities @(subscribe [::char/immunities id])
        traits-by-type (group-by :type @(subscribe [::char/traits id]))
        actions (concat @(subscribe [::char/actions id])
                        (traits-by-type :action))
        bonus-actions (concat @(subscribe [::char/bonus-actions id])
                              (traits-by-type :b-action))
        reactions (concat @(subscribe [::char/reactions id])
                          (traits-by-type :reaction))
        traits (concat (traits-by-type nil)
                       (traits-by-type :other))
        attacks @(subscribe [::char/attacks id])
        all-traits (concat actions bonus-actions reactions traits attacks)
        freqs (set (map has-frequency-units? all-traits))]
    [:div.details-columns
     {:class (when (= 2 num-columns) "flex")}

     [:div.flex-grow-1.details-column-2
      {:class (when (= 2 num-columns) "w-50-p m-l-20")}
      [list-item-section "Damage Resistances" "surrounded-shield" resistances resistance-str]
      [list-item-section "Damage Vulnerabilities" nil damage-vulnerabilities resistance-str]
      [list-item-section "Damage Immunities" nil damage-immunities resistance-str]
      [list-item-section "Condition Immunities" nil condition-immunities resistance-str]
      [list-item-section "Immunities" nil immunities resistance-str]
      [:div.flex.justify-cont-end.align-items-c
       (when (or (freqs ::units/long-rest)
               (freqs ::units/rest))
         [finish-long-rest-button id])
       (when (or (freqs ::units/short-rest)
               (freqs ::units/rest))
          [finish-short-rest-button id])
       (when (freqs ::units/round)
         [:button.form-button.p-5.m-l-5
          {:on-click (make-event-handler ::char/new-round id)}
          "new round"])
       (when (freqs ::units/turn)
         [:button.form-button.p-5.m-l-5
          {:on-click (make-event-handler ::char/new-turn id)}
          "new turn"])]
      [attacks-section id]
      [actions-section id "Actions" "beams-aura" actions]
      [actions-section id "Bonus Actions" "run" bonus-actions]
      [actions-section id "Reactions" "van-damme-split" reactions]
      [actions-section id "Features, Traits, and Feats" "vitruvian-man" traits]]]))

(defn spell-details [num-columns id]
  (let [spells-known @(subscribe [::char/spells-known id])
        spell-slots @(subscribe [::char/spell-slots id])
        spell-modifiers @(subscribe [::char/spell-modifiers id])
        spell-slot-factors @(subscribe [::char/spell-slot-factors id])
        total-spellcaster-levels @(subscribe [::char/total-spellcaster-levels id])
        levels @(subscribe [::char/levels id])]
    [:div.details-columns
     {:class (when (= 2 num-columns) "flex")}
     [:div.flex-grow-1.details-column-2
      {:class (when (= 2 num-columns) "w-50-p m-l-20")}
      (when (seq spells-known) [spells-known-section
                              id
                              spells-known
                              spell-slots
                              spell-modifiers
                              spell-slot-factors
                              total-spellcaster-levels
                              levels])]]))

(defn equipment-details [num-columns id]
  [:div
   [:div.m-t-30
    [equipped-section id]]
   [:div.m-t-30
    [weapons-section-2 id]]
   [:div.m-t-30
    [armor-section-2 id]]
   [:div.m-t-30
    [magic-items-section-2 id]]
   [:div.m-t-30
    [other-equipment-section-2 id]]
   [:div.m-t-30
    [treasure-section id]]])

(defn details-tab [title icon device-type selected? on-select]
  [:div.b-b-2.f-w-b.pointer.p-10.hover-opacity-full
   {:class (if selected? "b-orange" "b-gray")
    :on-click on-select}
   [:div.hover-opacity-full
    {:class (when (not selected?) "opacity-5")}
    [:div (svg-icon icon 24)]
    (when (= device-type :desktop)
      [:div.uppercase.f-s-10
       title])]])


(def details-tabs
  {"summary" {:icon "stabbed-note"
              :view summary-details}
   "combat" {:icon "sword-clash"
             :view combat-details}
   "proficiencies" {:icon "juggler"
                    :view proficiency-details}
   "spells" {:icon "spell-book"
             :view spell-details}
   "features" {:icon "vitruvian-man"
               :view features-details}
   "equipment" {:icon "backpack"
                :view equipment-details}})

(defn option-title [kw]
  (str common/dot-char " " (common/kw-to-name kw)))

(declare options-display)

(defn option-display [{:keys [::entity/key ::entity/options]}]
  [:div
   [:span (option-title key)]
   (when (seq options)
     [:div.p-l-20
      [options-display options]])])

(defn options-display [options]
  [:div
   (doall
    (map
     (fn [[k v]]
       ^{:key k}
       [:div
        [:span (option-title k)]
        [:div.p-l-20
         (if (vector? v)
           (doall
            (map
             (fn [option]
               ^{:key (::entity/key option)}
               [option-display option])
             v))
           (option-display v))]])
     options))])

(defn character-selections [id]
  (let [character @(subscribe [::char/character id])]
    [:div.p-20
     [:span.f-w-b.f-s-24 "Selections"]
     [:div
      (options-display (::entity/options character))]]))

(defn character-display []
  (let [show-selections? (r/atom false)]
    (fn [id show-summary? num-columns]
      (let [device-type @(subscribe [:device-type])
            selected-tab @(subscribe [::char/selected-display-tab])
            two-columns? (= 2 num-columns)
            tab (or selected-tab
                    (if two-columns?
                      "combat"
                      "summary"))]
        [:div.w-100-p
         [:div
          (when show-summary?
            [:div.f-s-24.f-w-600.m-b-16.m-l-20.text-shadow.flex
             [character-summary id true true]])
          [:div.flex.w-100-p
           (when two-columns?
             [:div.w-50-p
              [summary-details num-columns id]])
           [:div
            {:class (if two-columns? "w-50-p" "w-100-p")}
            [:div.flex.p-l-10.m-b-10.m-r-10
             (doall
              (map
               (fn [[title {:keys [view icon]}]]
                 ^{:key title}
                 [:div.flex-grow-1.t-a-c
                  [details-tab
                   title
                   icon
                   device-type
                   (= title tab)
                   (make-event-handler ::char/set-selected-display-tab title)]])
               (if two-columns?
                 (rest details-tabs)
                 details-tabs)))]
            [(-> tab details-tabs :view) num-columns id]]]
          [:div.p-10
           [:span.orange.underline.pointer
            {:on-click #(swap! show-selections? not)}
            [:span (if @show-selections?
                     "hide selections"
                     "show selections")]
            [:i.fa.m-l-5
             {:class (if @show-selections? "fa-caret-up" "fa-caret-down")}]]
           (when @show-selections?
             [character-selections id])]]]))))

(defn share-link [id]
  [:a.m-r-5.f-s-14
   {:href (str "mailto:?subject=My%20OrcPub%20Character%20"
               @(subscribe [::char/character-name id])
               "&body=https://"
               js/window.location.hostname
               (routes/path-for routes/dnd-e5-char-page-route :id id))}
   [:i.fa.fa-envelope.m-r-5]
   "share"])

(def character-display-style
  {:padding "20px 5px"
   :background-color "rgba(0,0,0,0.15)"})

(defn add-to-party-component []
  (let [party-id (r/atom nil)]
    (fn [character-id]
      [:div.m-l-10.f-w-b
       [:span "Add to Party:"]
       [:div.flex
        [:select.builder-option.builder-option-dropdown
         {:on-change (fn [e] (let [value (event-value e)
                                   id (when (not (s/blank? value))
                                        (js/parseInt value))]
                               (when id
                                 (reset! party-id id))))}
         [:option.builder-dropdown-item
          "<new party>"]
         (doall
          (map
           (fn [{:keys [:db/id ::party/name]}]
             ^{:key id}
             [:option.builder-dropdown-item
              {:value id}
              name])
           @(subscribe [::party/parties])))]
        [:button.form-button.m-t-5.m-l-5
         {:on-click #(if @party-id
                       (dispatch [::party/add-character-remote @party-id character-id true])
                       (dispatch [::party/make-party #{character-id}]))}
         "ADD"]]])))

(defn labeled-checkbox [label selected?]
  [:div.flex.align-items-c.pointer.m-b-10
   (comps/checkbox selected? false)
   [:span.m-l-5.f-s-14 label]])

(defn export-pdf-fn [built-char
                     id
                     plugin-data
                     print-character-sheet?
                     print-spell-cards?
                     print-prepared-spells?
                     print-large-abilities?
                     print-character-sheet-style?
                     print-spell-card-dc-mod?]
  #(let [export-fn (export-pdf built-char
                               id
                               plugin-data
                               {:print-character-sheet? print-character-sheet?
                                :print-spell-cards? print-spell-cards?
                                :print-prepared-spells? print-prepared-spells?
                                :print-large-abilities? print-large-abilities?
                                :print-character-sheet-style? print-character-sheet-style?
                                :print-spell-card-dc-mod? print-spell-card-dc-mod?})]
     (export-fn)
     (dispatch [::char/hide-options])))

(def export-pdf-handler (memoize export-pdf-fn))


(defn delete-item-handler [item-key]
  (fn []
    (dispatch [::mi/delete-custom-item item-key])
    (dispatch [::mi/hide-delete-confirmation item-key])
    (dispatch [::mi/reset-item])))

(defn print-button-style [print-button-enabled]
  (if print-button-enabled
    {}
    {:opacity 0.5
     :cursor :not-allowed
     :pointer-events "none"}))


(defn print-options [id built-char]
  (let [print-character-sheet? @(subscribe [::char/print-character-sheet?])
        print-spell-cards? @(subscribe [::char/print-spell-cards?])
        print-prepared-spells? @(subscribe [::char/print-prepared-spells?])
        print-large-abilities? @(subscribe [::char/print-large-abilities?])
        print-character-sheet-style? @(subscribe [::char/print-character-sheet-style?])
        print-spell-card-dc-mod? @(subscribe [::char/print-spell-card-dc-mod?])
        plugin-data {:spells-map @(subscribe [::spells/spells-map])
                     :plugin-spells-map @(subscribe [::spells/plugin-spells-map])
                     :language-map @(subscribe [::langs/language-map])
                     :all-weapons-map @(subscribe [::mi/all-weapons-map])
                     :all-magic-items-map @(subscribe [::mi/all-magic-items-map])
                     :current-armor-class @(subscribe [::char/current-armor-class id])}
        has-spells? (seq (char/spells-known built-char))
        print-button-enabled (if (or (= print-character-sheet-style? nil)
                                     (= (str print-character-sheet-style?) "NaN"))
                               false true)]
    [:div.flex.justify-cont-end
     [:div.p-20
      [:div.f-s-24.f-w-b.m-b-10 "Print Options"]
      [:div.m-b-2
       [:div.flex.m-b-10
        [:div.m-t-10
         [labeled-dropdown
          "Select Character sheet"
          {:items [{:title "Select" :value " "}
                   {:title "Original 5e Character sheet" :value 1}
                   {:title "Original 5e Character sheet - optional variant" :value 2}
                   {:title "Icewind Dale 5e Character sheet" :value 3}
                   {:title "Petersen Games - Cthulhu Mythos Sagas sheet" :value 4}]
           :value print-character-sheet-style?
           :on-change (make-arg-event-handler ::char/set-print-character-sheet-style? js/parseInt)}]]]
       [:div.flex
        [:div
         {:on-click (make-event-handler ::char/toggle-large-abilities-print)}
         [labeled-checkbox
          "Print Abilities Large (and Bonuses Small)"
          print-large-abilities?]]]]
      (when has-spells?
        [:div.m-b-2
         [:div.flex
          [:div
           {:on-click (make-event-handler ::char/toggle-spell-cards-print)}
           [labeled-checkbox
            "Print Spell Cards"
            print-spell-cards?]]]])
      (when print-spell-cards?
        [:div.m-b-2
         [:div.flex
          [:div
           {:on-click (make-event-handler ::char/toggle-spell-cards-by-dc-mod)}
           [labeled-checkbox
            "Print Spell DC and MOD"
            print-spell-card-dc-mod?]]]])
      (when has-spells?
        [:div.m-b-10
         [:div.m-b-10
          [:span.f-w-b "Spells Printed"]]
         [:div.flex
          [:div
           {:on-click (make-event-handler ::char/toggle-known-spells-print)}
           [labeled-checkbox
            "Known"
            (not print-prepared-spells?)]]
          [:div.m-l-20
           {:on-click (make-event-handler ::char/toggle-known-spells-print)}
           [labeled-checkbox
            "Prepared"
            print-prepared-spells?]]]])
      [:span.orange.underline.pointer.uppercase.f-s-12
       {:on-click (make-event-handler ::char/hide-options)}
       "Cancel"]
      [:button.form-button.p-10.m-l-5
       {:style (print-button-style print-button-enabled)
        :on-click (export-pdf-handler built-char
                                      id
                                      plugin-data
                                      print-character-sheet?
                                      print-spell-cards?
                                      print-prepared-spells?
                                      print-large-abilities?
                                      print-character-sheet-style?
                                      print-spell-card-dc-mod?)}
       "Print"]]]))

(defn make-print-handler [id built-char]
  #(dispatch
    [::char/show-options
     [print-options id built-char]]))

(defn character-page []
  (let [expanded? (r/atom false)]
    (fn [{:keys [id] :as arg}]
      (let [id (js/parseInt id)
            frame? (= "true" (get-in arg [:query "frame"]))
            _ (prn "FRAME?" frame?)
            {:keys [::entity/owner] :as character} @(subscribe [::char/character id])
            built-template (subs/built-template
                            @(subscribe [::char/template])
                            (subs/selected-plugin-options
                             character))
            built-character (subs/built-character character built-template)
            device-type @(subscribe [:device-type])
            username @(subscribe [:username])]
        [content-page
         (when (not frame?)
           "Character Page")
         (remove
          nil?
          [[share-link id]
           [:div.m-l-5.hover-shadow.pointer
            {:on-click #(swap! expanded? not)}
            [:img.h-32 {:src "/image/world-anvil.jpeg"}]]
           (when (and username
                    owner
                    (= owner username))
             {:title "Edit"
              :icon "pencil"
              :on-click (make-event-handler :edit-character character)})
           {:title "Print"
            :icon "print"
            :on-click (make-print-handler id built-character)}
           (when (and username owner (not= owner username))
             [add-to-party-component id])])
         [:div.p-10.main-text-color
          (when @expanded?
            (let [url js/window.location.href]
              [:div.p-10.flex.justify-cont-end
               [:input.input.w-500.bg-white.black
                {:value (str url
                             (when (not (s/ends-with? url "?frame=true"))
                               "?frame=true"))}]]))
          [character-display id true (if (= :mobile device-type) 1 2)]]
         :frame? frame?]))))

(defn monster-page [{:keys [key] :as arg}]
  (let [monster @(subscribe [::monsters/monster (keyword key)])]
    [content-page
     "Monster Page"
     []
     [:div.p-10.main-text-color
      [monster-component monster]]]))

(defn spell-page [{:keys [key] :as arg}]
  (let [spell-map @(subscribe [::spells/spells-map])
        spell (spell-map (common/name-to-kw key))]
    [content-page
     "Spell Page"
     (remove
      nil?
      [])
     [:div.p-10.main-text-color
      [spell-component spell true]]]))

(defn item-page [{:keys [key] :as arg}]
  (let [item-key (if (re-matches #"\d+" key)
                   (js/parseInt key)
                   (keyword key))
        item @(subscribe [::mi/custom-item item-key])
        username @(subscribe [:username])
        owner? (= username (::mi/owner item))]
    [content-page
     "Item Page"
     (remove
      nil?
      [(when owner?
         {:title "Delete"
          :icon "trash"
          :on-click (delete-item-handler item-key)})
       (when owner?
         {:title "Edit"
          :icon "pencil"
          :on-click (make-event-handler ::mi/edit-custom-item item)})])
     [:div.p-10.main-text-color
      [item-component item]
      ]]))
