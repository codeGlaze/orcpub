(ns orcpub.dnd.e5.views.reference
  "The reference pages: the Monsters, Spells and Items lists, the single monster, spell and item
   pages, and the Rules and Conditions pages. The shared display components (content-page and the
   spell, monster and item components) stay in orcpub.dnd.e5.views."
  (:require [clojure.string :as s]
            [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [orcpub.route-map :as routes]
            [orcpub.common :as common]
            [orcpub.components :as comps :refer [make-event-handler]]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.monsters :as monsters]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.srd-conditions :as srd-conditions]
            [orcpub.dnd.e5.srd-rules :as srd-rules]
            [orcpub.dnd.e5.views :as views]
            [orcpub.dnd.e5.views.builder-fields :refer [event-value]]))

(defn monster-page
  "The page for one monster, from the route's :key."
  [{:keys [key] :as arg}]
  (let [monster @(subscribe [::monsters/monster (keyword key)])]
    [views/content-page
     "Monster Page"
     []
     [:div.p-10.main-text-color
      [views/monster-component monster]]]))

(defn spell-page
  "The page for one spell, from the route's :key."
  [{:keys [key] :as arg}]
  (let [spell-map @(subscribe [::spells/spells-map])
        spell (spell-map (common/name-to-kw key))]
    [views/content-page
     "Spell Page"
     (remove
      nil?
      [])
     [:div.p-10.main-text-color
      [views/spell-component spell true]]]))

(defn item-page
  "The page for one item, from the route's :key (a keyword or a custom item id); its owner gets Edit and Delete."
  [{:keys [key] :as arg}]
  (let [item-key (if (re-matches #"\d+" key)
                   (js/parseInt key)
                   (keyword key))
        item @(subscribe [::mi/custom-item item-key])
        username @(subscribe [:username])
        owner? (= username (::mi/owner item))]
    [views/content-page
     "Item Page"
     (remove
      nil?
      [(when owner?
         {:title "Delete"
          :icon "trash"
          :on-click (views/delete-item-handler item-key)})
       (when owner?
         {:title "Edit"
          :icon "pencil-alt"
          :on-click (make-event-handler ::mi/edit-custom-item item)})])
     [:div.p-10.main-text-color
      [views/item-component item]
      ]]))

(defn- srd-load-failed
  "The message shown when the SRD file `kind` (:rules or :conditions) could not be loaded."
  [kind]
  [:div (str "The " (name kind) " could not be loaded. Try reloading the page.")])

(defn rules-page
  "The rules reference: every SRD 5.1 rules section, by group, and the conditions."
  []
  (r/with-let [_ (dispatch [:load-srd :rules])]
    (let [data @(subscribe [:srd :rules])
          by-key (into {} (map (juxt :key identity)) (:sections data))]
      [views/content-page
       "Rules"
       []
       [:div.p-10.main-text-color
        (cond
          data [:div
                (doall
                 (for [{:keys [key name sections]} (:groups data)]
                   ^{:key key}
                   [:div.m-b-20
                    [:div.f-s-20.f-w-b name]
                    [:div.srd-section-list
                     (doall
                      (for [k sections]
                        ^{:key k}
                        [:span.pointer.underline {:on-click (views/srd-route-handler {:kind :rule :section k})}
                         (:name (by-key k))]))]]))
                [:div.m-b-20
                 [:div.f-s-20.f-w-b "Conditions"]
                 [:div.srd-section-list
                  [:span.pointer.underline {:on-click (make-event-handler :route routes/dnd-e5-condition-list-page-route)}
                   "Blinded, charmed, frightened, grappled and the rest"]]]
                [views/srd-attribution data]]
          @(subscribe [:srd-failed? :rules]) [srd-load-failed :rules]
          :else [:div "Loading..."])]])))

(defn rule-section-page
  "One SRD 5.1 rules section, from the route's `:key`, scrolled to a rule when a link asked."
  [{:keys [key]}]
  (r/with-let [_ (dispatch [:load-srd :rules])
               _ (dispatch [:load-srd :conditions])
               _ (dispatch [:load-srd :clarifications])]
    (let [data @(subscribe [:srd :rules])
          notes (filter #(= (keyword key) (:section %)) (:notes @(subscribe [:srd :clarifications])))
          {:keys [name pages body] :as sec} (some-> (srd-rules/section data (keyword key))
                                                    (update :body srd-rules/with-notes notes))
          target @(subscribe [:srd-scroll-target])]
      (when (and sec target)
        (r/after-render (fn []
                          (some-> (.getElementById js/document (str "rule-" (cljs.core/name target)))
                                  (.scrollIntoView))
                          (dispatch [:set-srd-scroll-target nil]))))
      [views/content-page
       (or name "Rules")
       []
       [:div.p-10.main-text-color
        (cond
          sec [:div
               (when-let [[a b] pages]
                 [:div.f-s-12.opacity-5 (str (:srd/document data) (if (= a b) (str ", p. " a) (str ", pp. " a "-" b)))])
               [views/srd-body body]
               [:div.pointer.underline.m-t-20 {:on-click (make-event-handler :route routes/dnd-e5-rules-page-route)}
                "All rules"]
               [views/srd-attribution data]]
          data [:div "No rules section by that name."]
          @(subscribe [:srd-failed? :rules]) [srd-load-failed :rules]
          :else [:div "Loading..."])]])))

(defn condition-list-page
  "Every SRD 5.1 condition, with the licence attribution."
  []
  (r/with-let [_ (dispatch [:load-srd :conditions])]
    (let [data @(subscribe [:srd :conditions])]
      [views/content-page
       "Conditions"
       []
       [:div.p-10.main-text-color
        (cond
          data [:div
                (doall
                 (map (fn [c] ^{:key (:key c)} [views/condition-component c (:srd/document data)])
                      (:conditions data)))
                [views/srd-attribution data]]
          @(subscribe [:srd-failed? :conditions]) [srd-load-failed :conditions]
          :else [:div "Loading..."])]])))

(defn condition-page
  "One SRD 5.1 condition, from the route's `:key`."
  [{:keys [key]}]
  (r/with-let [_ (dispatch [:load-srd :conditions])]
    (let [data @(subscribe [:srd :conditions])
          condition (get (srd-conditions/by-key data) (keyword key))]
      [views/content-page
       "Condition"
       []
       [:div.p-10.main-text-color
        (cond
          condition [:div
                     [views/condition-component condition (:srd/document data)]
                     [:div.pointer.underline.m-t-10
                      {:on-click (make-event-handler :route routes/dnd-e5-condition-list-page-route)}
                      "All conditions"]
                     [views/srd-attribution data]]
          data [:div "No condition by that name."]
          @(subscribe [:srd-failed? :conditions]) [srd-load-failed :conditions]
          :else [:div "Loading..."])]])))

(defn monster-list-item
  "One row of the monster list: summary, expandable to the full monster. `monster` is the monster map."
  [{:keys [name size type subtypes alignment key] :as monster}]
  (r/with-let [device-type? (subscribe [:device-type])]
    (let [homebrew? (:option-pack monster)
          expanded? @(subscribe [:monster-expanded? name])]
      [:div.main-text-color.item-list-item
       [:div.pointer
        [:div.flex.justify-cont-s-b.align-items-c
         {:on-click #(dispatch [:toggle-monster-expanded name])}
         [:div.m-l-10
          [:div.f-s-24.f-w-600.p-b-20.p-t-20.flex
           (when homebrew?
             [:div.m-r-10 (views/svg-icon "beer-stein" 24 @(subscribe [:theme]))])
           [views/monster-summary name size type subtypes alignment]]]
         [:div.orange.pointer.m-r-10
          (when (not= @device-type? :mobile)
            [:span.underline (if expanded?
                               "collapse"
                               "open")])
          [:i.fa.m-l-5
           {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]
        (when expanded?
          [:div.p-10
           {:style views/character-display-style}
           [:div.flex.justify-cont-end.uppercase.align-items-c
            [:button.form-button.m-l-5
             {:on-click #(dispatch [:route (routes/match-route (routes/path-for routes/dnd-e5-monster-page-route :key key))])}
             "view"]
            (when homebrew?
              [:button.form-button.m-l-5
               {:on-click (make-event-handler ::monsters/edit-monster monster)}
               "edit"])
            (when homebrew?
              [:button.form-button.m-l-5
               {:on-click (make-event-handler ::monsters/delete-monster monster)}
               "delete"])]
           [views/monster-component monster]])]])))

(defn monster-list-items
  "The monster list rows for the current filter, each inside a render guard."
  []
  (let [filtered-monsters @(subscribe [::monsters/filtered-monsters])]
    [:div.item-list
     (doall
       (map
         (fn [{:keys [name] :as monster}]
           ^{:key name}
           ;; a monster that cannot draw costs its own row
           [views/render-guard monster [monster-list-item monster]])
         filtered-monsters))]))

(defn clear-monsters-filter
  "Clears the monster list's text filter."
  []
  (dispatch [::char/filter-monsters ""]))

(def toggle-handler
  "Memoized click handler that flips the boolean atom it is given."
  (memoize
   (fn [a]
     #(swap! a not))))

(defn- sort-toggle
  "Resembles an underlined link. Uses the FA arrows to indicate sorting direction.

   Should be generalized or moved."
  [label value sort-event sort-criteria sort-direction]
  [:div.orange.pointer.m-r-10
   {:on-click #(dispatch [sort-event value (if (= sort-direction "asc") "desc" "asc")])}
   [:span.underline label]
   [:i.fa.m-l-5
    {:class (s/join " "
                    [(when (not= sort-criteria value)
                       "invisible")
                     (if (= sort-direction "asc")
                       "fa-caret-up"
                       "fa-caret-down")])}]])

(defn monster-trait-filters
  "The size, type and subtype checkboxes that hide monsters from the list."
  []
  [:div.flex.flex-wrap
   [:div.main-text-color.p-20
    [:div.f-s-16.f-w-b "Size"]
    [:div
     (doall
       (map
         (fn [size]
           ^{:key size}
           [:div.p-5.pointer
            {:on-click (make-event-handler ::char/toggle-monster-filter-hidden :size size)}
            (comps/checkbox (not @(subscribe [::char/monster-filter-hidden? :size size])) false
                            (s/capitalize (common/kw-to-name size)))
            (str " " (s/capitalize (common/kw-to-name size)))])
         @(subscribe [::char/monster-sizes])))]]
   [:div.main-text-color.p-20
    [:div.f-s-16.f-w-b "Type"]
    [:div
     (doall
       (map
         (fn [type]
           ^{:key type}
           [:div.p-5.pointer
            {:on-click (make-event-handler ::char/toggle-monster-filter-hidden :type type)}
            (comps/checkbox (not @(subscribe [::char/monster-filter-hidden? :type type])) false
                            (s/capitalize (common/kw-to-name type)))
            (str " " (s/capitalize (common/kw-to-name type)))])
         @(subscribe [::char/monster-types])))]]
   (let [subtypes @(subscribe [::char/monster-subtypes])]
     [:div.main-text-color.p-20
      [:div.f-s-16.f-w-b "Subtype"]
      [:div
       (doall
         (map
           (fn [subtype]
             ^{:key subtype}
             [:div.p-5.pointer
              {:on-click (make-event-handler ::char/toggle-monster-filter-hidden :subtype subtype)}
              (comps/checkbox (not @(subscribe [::char/monster-filter-hidden? :subtype subtype])) false
                              (s/capitalize (common/kw-to-name subtype)))
              (str " " (s/capitalize (common/kw-to-name subtype)))])
           subtypes))]])])

(defn monster-filter-toggle
  "The link that shows or hides the monster trait filters; `filters-expanded?` is a ratom."
  [filters-expanded?]
  [:div.orange.pointer.m-r-10
   {:on-click (toggle-handler filters-expanded?)}
   (when (not= @(subscribe [:device-type]) :mobile)
     [:span.underline (if @filters-expanded?
                        "hide"
                        "filters")])
   [:i.fa.m-l-5
    {:class (if @filters-expanded? "fa-caret-up" "fa-caret-down")}]])

(defn monster-list
  "The Monsters page: text filter, trait filters and the filtered list."
  []
  (r/with-let [filters-expanded? (r/atom false)
               sort-criteria (subscribe [::char/monster-sort-criteria])
               sort-direction (subscribe [::char/monster-sort-direction])]
    [views/content-page
     "Monsters"
     []
     [:div.p-l-5.p-r-5.p-b-10
      [:div.p-b-10.p-l-10.p-r-10
       [:div.posn-rel
        [:input.input.f-s-24.p-l-20.w-100-p.h-60
         {:value     @(subscribe [::char/monster-text-filter])
          :on-change (views/make-arg-event-handler ::char/filter-monsters event-value)}]
        [:i.fa.fa-times.posn-abs.f-s-24.pointer.main-text-color
         {:style    views/close-icon-style
          :on-click clear-monsters-filter}]]]
      [:div
       [:div.flex.justify-cont-s-b.m-b-10
        [:div.orange.m-l-10.m-r-10 "Sort by:"]
        [sort-toggle "Name" "name" ::char/sort-monsters @sort-criteria @sort-direction]
        [sort-toggle "Challenge Rating" "cr" ::char/sort-monsters @sort-criteria @sort-direction]
        [:div.flex-grow-1]
        [monster-filter-toggle filters-expanded?]]
       (when @filters-expanded?
         [monster-trait-filters])]
      [monster-list-items]]]))

(defn spell-list-item
  "One row of the spell list: summary, expandable to the full spell. `spell` is the spell map."
  [{:keys [name level school ritual key] :as spell}]
  (let [expanded? @(subscribe [:spell-expanded? name])
        device-type @(subscribe [:device-type])
        spell-page-path (routes/path-for routes/dnd-e5-spell-page-route :key key)
        spell-page-route (routes/match-route spell-page-path)
        homebrew? (:option-pack spell)]
    [:div.main-text-color.item-list-item
     [:div.pointer
      [:div.flex.justify-cont-s-b.align-items-c
       {:on-click (make-event-handler :toggle-spell-expanded name)}
       [:div.m-l-10
        [:div.f-s-24.f-w-600.p-t-20.flex
         (when homebrew?
           [:div.m-r-10 (views/svg-icon "beer-stein" 24 @(subscribe [:theme]))])
         [views/spell-summary name level school ritual true 12]]]
       [:div.orange.pointer.m-r-10
        (when (not= device-type :mobile) [:span.underline (if expanded?
                                                          "collapse"
                                                          "open")])
        [:i.fa.m-l-5
         {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]
      (when expanded?
        [:div.p-10
         {:style views/character-display-style}
         [:div.flex.justify-cont-end.uppercase.align-items-c
          [:button.form-button.m-l-5
           {:on-click (make-event-handler :route spell-page-route)}
           "view"]
          (when homebrew?
            [:button.form-button.m-l-5
             {:on-click (make-event-handler ::spells/edit-spell spell)}
             "edit"])
          (when homebrew?
            [:button.form-button.m-l-5
             {:on-click (make-event-handler ::spells/delete-spell spell)}
             "delete"])
          ]
         [views/spell-component spell true]])]]))

(defn spell-list-items
  "The filtered spell list rows. `device-type` is :mobile, :tablet or :desktop."
  [device-type]
  [:div.item-list
   (doall
    (map
     (fn [{:keys [name level school key] :as spell}]
       ^{:key name}
       [spell-list-item spell])
     @(subscribe [::char/filtered-spells])))])

(defn spell-list
  "The Spells page: text filter and the filtered list."
  []
  (let [device-type @(subscribe [:device-type])]
    [views/content-page
     "Spells"
     []
     [:div.p-l-5.p-r-5.p-b-10
      [:div.p-b-10.p-l-10.p-r-10
       [:div.posn-rel
        [:input.input.f-s-24.p-l-20.w-100-p.h-60
         {:value @(subscribe [::char/spell-text-filter])
          :on-change (views/make-arg-event-handler ::char/filter-spells event-value)}]
        [:i.fa.fa-times.posn-abs.f-s-24.pointer.main-text-color
         {:style views/close-icon-style
          :on-click (make-event-handler ::char/filter-spells "")}]]]
      [spell-list-items device-type]]]))

(defn item-list-item
  "One row of the item list, expandable to the full item. `item` is the item map, `expanded?` whether it is open."
  [{:keys [key name ::mi/owner :db/id] :as item} expanded?]
  (let [expanded-key (or name (::mi/name item))
        device-type @(subscribe [:device-type])
        expanded? @(subscribe [:item-expanded? expanded-key])
        username @(subscribe [:username])
        item-page-path (routes/path-for routes/dnd-e5-item-page-route :key (or id key))
        item-page-route (routes/match-route item-page-path)]
    [:div.main-text-color.item-list-item
     [:div.pointer
      [:div.flex.justify-cont-s-b.align-items-c
       {:on-click (make-event-handler :toggle-item-expanded expanded-key)}
       [:div.m-l-10
        [:div.f-s-24.f-w-600.p-t-20
         [views/item-summary item]]]
       [:div.orange.pointer.m-r-10
        (when (not= device-type :mobile) [:span.underline (if expanded?
                                                          "collapse"
                                                          "open")])
        [:i.fa.m-l-5
         {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]
      (when expanded?
        [:div.p-10
         {:style views/character-display-style}
         [:div.flex.justify-cont-end.uppercase.align-items-c
          [:button.form-button.m-l-5
           {:on-click (make-event-handler :route item-page-route)}
           "view"]
          (when (= username owner)
            [:button.form-button.m-l-5
             {:on-click (make-event-handler ::mi/edit-custom-item @(subscribe [::mi/custom-item id]))}
             "edit"])
          (when (= username owner)
            [:button.form-button.m-l-5
             {:on-click (make-event-handler ::mi/show-delete-confirmation id)}
             "delete"])]
            (when @(subscribe [::mi/delete-confirmation-shown? id])
              [:div.p-20.flex.justify-cont-end
               [:div
                [:div.m-b-10 "Are you sure you want to delete this item?"]
                [:div.flex
                 [:button.form-button
                  {:on-click (make-event-handler ::mi/hide-delete-confirmation id)}
                  "cancel"]
                 [:span.link-button
                  {:on-click  (views/delete-item-handler id)}
                  "delete"]]]])
            [views/item-component item]
        ])
      ]]))

(defn item-list-items
  "The filtered item list rows."
  []
  [:div.item-list
   (doall
    (map
     (fn [{:keys [:db/id key] :as item}]
       ^{:key (or key id)}
       [item-list-item item])
     @(subscribe [::char/filtered-items])))])

(defn item-list
  "The Items page: New Item button, text filter and the filtered list."
  []
  (let [device-type @(subscribe [:device-type])]
    [views/content-page
     "Items"
     [[:button.form-button
       {:on-click (make-event-handler ::mi/new-item)}
       [:div.flex.align-items-c.white
        [views/svg-icon "beer-stein" 18 ""]
        [:span.m-l-5 "New Item"]]]]
     [:div.p-l-5.p-r-5.p-b-10
      [:div.p-b-10.p-l-10.p-r-10
       [:div.posn-rel
        [:input.input.f-s-24.p-l-20.w-100-p.h-60
         {:value @(subscribe [::char/item-text-filter])
          :on-change (views/make-arg-event-handler ::char/filter-items event-value)}]
        [:i.fa.fa-times.posn-abs.f-s-24.pointer.main-text-color
         {:style views/close-icon-style
          :on-click (make-event-handler ::char/filter-items "")}]]]
      [item-list-items]]]))
