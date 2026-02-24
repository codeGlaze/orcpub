(ns orcpub.dnd.e5.views.lists
  "List browser pages for characters, parties, monsters, spells, and items.

   Each list page renders a filterable, sortable, expandable list of
   entities with inline detail views. Depends on views.cljs for shared
   page scaffolding (content-page, character-display, summary components)
   and views.common for UI atoms (svg-icon, make-event-handler, etc.).

   Dependency flow: common → header → views.cljs → THIS → core.cljs"
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [clojure.string :as s]
            [orcpub.common :as common]
            [orcpub.components :as comps]
            [orcpub.route-map :as routes]
            [orcpub.entity.strict :as se]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.languages :as langs]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.monsters :as monsters]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.folder :as folder]
            [orcpub.dnd.e5.party :as party]
            [orcpub.dnd.e5.views.common
             :refer [make-event-handler make-arg-event-handler
                     export-pdf svg-icon event-value
                     character-display-name]]
            [orcpub.dnd.e5.views
             :refer [content-page character-display character-display-style
                     character-summary-2 other-user-component share-link
                     delete-item-handler close-icon-style
                     monster-summary monster-component
                     spell-summary spell-component
                     item-summary item-component
                     orcacle]]))

;;;; ====================================================================
;;;; Character List
;;;; ====================================================================

(defn expanded-character-list-item
  "Expanded detail view for a character in the list: edit/save/view/print
   buttons, folder assignment, delete confirmation, and full character
   display."
  [id owner username char-page-route]
  (let [built-char @(subscribe [::char/built-character id])
        character @(subscribe [::char/character id])
        plugin-data {:spells-map @(subscribe [::spells/spells-map])
                     :plugin-spells-map @(subscribe [::spells/plugin-spells-map])
                     :language-map @(subscribe [::langs/language-map])
                     :all-weapons-map @(subscribe [::mi/all-weapons-map])
                     :all-magic-items-map @(subscribe [::mi/all-magic-items-map])
                     :current-armor-class @(subscribe [::char/current-armor-class id])}
        folders @(subscribe [::folder/folders])
        char-folder-map @(subscribe [::folder/character-folder-map])
        current-folder-id (get char-folder-map id)]
    [:div
     {:style character-display-style}
     [:div.flex.justify-cont-end.uppercase.align-items-c
      [share-link id]
      (when (= username owner)
        [:button.form-button
         {:on-click (make-event-handler :edit-character character)}
         "edit"])
      (when (= username owner)
        [:button.form-button.m-l-5
         {:on-click (make-event-handler ::char/save-character id)}
         "save"])
      [:button.form-button.m-l-5
       {:on-click (make-event-handler :route char-page-route)}
       "view"]
      [:button.form-button.m-l-5
       {:on-click (export-pdf
                   built-char
                   id
                   plugin-data
                   {:print-character-sheet? true
                    :print-spell-cards? true
                    :print-prepared-spells? false
                    :print-character-sheet-style? 1
                    :print-spell-card-dc-mod? true})}
       "print"]
      (when (and (= username owner) (seq folders))
        [:select.form-button.m-l-5.builder-option-dropdown
         {:value (or current-folder-id "")
          :on-change (fn [e]
                       (let [val (.-value (.-target e))]
                         (if (= val "")
                           (when current-folder-id
                             (dispatch [::folder/remove-character current-folder-id id]))
                           (dispatch [::folder/add-character (js/parseInt val) id]))))}
         [:option.builder-dropdown-item {:value ""} "No folder"]
         (doall
          (map (fn [f]
                 ^{:key (:db/id f)}
                 [:option.builder-dropdown-item {:value (:db/id f)} (::folder/name f)])
               (sort-by ::folder/name folders)))])
      (when (= username owner)
        [:button.form-button.m-l-5
         {:on-click (make-event-handler ::char/show-delete-confirmation id)}
         "delete"])]
     (when @(subscribe [::char/delete-confirmation-shown? id])
       [:div.p-20.flex.justify-cont-end
        [:div
         [:div.m-b-10 "Are you sure you want to delete this character?"]
         [:div.flex
          [:button.form-button
           {:on-click (make-event-handler ::char/hide-delete-confirmation id)}
           "cancel"]
          [:span.link-button
           {:on-click (make-event-handler :delete-character id)}
           "delete"]]]])
     [character-display id false (if (= :mobile @(subscribe [:device-type])) 1 2)]]))

(defn character-list-item
  "Collapsible character list item with summary and expand/collapse toggle."
  [expanded-characters
   selected-ids
   id
   owner
   username
   summary]
  (let [expanded? (get expanded-characters id)
        char-page-path (routes/path-for routes/dnd-e5-char-page-route :id id)
        char-page-route (routes/match-route char-page-path)]
    [:div.main-text-color.item-list-item
     [:div
      [:div.flex.justify-cont-s-b.align-items-c.pointer
       {:on-click (make-event-handler :toggle-character-expanded id)}
       [:div.m-l-10.flex.align-items-c
        [:div.p-5
         {:on-click (fn [e]
                      (dispatch [::char/toggle-selected id])
                      (.stopPropagation e))}
         (comps/checkbox (get selected-ids id) false)]
        [:div.f-s-24.f-w-600
         [:div.list-character-summary
          [character-summary-2 summary true owner false]]]]
       [:div.orange.pointer.m-r-10
        (when (not= @(subscribe [:device-type]) :mobile) [:span.underline (if expanded?
                                                            "collapse"
                                                            "open")])
        [:i.fa.m-l-5
         {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]
      (when expanded?
        [expanded-character-list-item id owner username char-page-route])]]))

(defn folder-item
  "Folder UI with expand/collapse, rename, delete, and nested character
   list. Form-2 component with local state for edit-name and confirm-delete."
  [f expanded-characters selected-ids username filtered-char-ids]
  (let [edit-name      (r/atom (::folder/name f))
        confirm-delete? (r/atom false)]
    (fn [f expanded-characters selected-ids username filtered-char-ids]
      (let [folder-id (:db/id f)
            folder-name (::folder/name f)
            expanded? @(subscribe [::folder/expanded])
            folder-expanded? (get expanded? folder-id)
            renaming? @(subscribe [::folder/renaming])
            folder-renaming? (get renaming? folder-id)
            chars (::folder/character-ids f)
            visible-chars (filter #(filtered-char-ids (:db/id %)) chars)
            save-fn (fn []
                      (dispatch [::folder/rename-folder folder-id @edit-name])
                      (dispatch [::folder/toggle-renaming folder-id]))]
    [:div.main-text-color.item-list-item.m-b-5
     ^{:key folder-id}
     [:div
      ;; Entire row is clickable to expand/collapse folder
      [:div.flex.justify-cont-s-b.align-items-c.pointer
       {:on-click (when (not folder-renaming?)
                    #(dispatch [::folder/toggle-expanded folder-id (mapv :db/id chars)]))}
       [:div.flex.align-items-c.m-l-10
        (when (not folder-renaming?)
          [:i.fa.m-r-10.orange
           {:class (if folder-expanded? "fa-folder-open" "fa-folder")}])
        (if folder-renaming?
          [:div.flex.align-items-c
           [:input.input
            {:auto-focus true
             :value @edit-name
             :style {:width "160px"}
             :on-change #(reset! edit-name (.-value (.-target %)))
             :on-key-down (fn [e]
                            (when (= "Enter" (.-key e))
                              (save-fn)))}]
           [:button.form-button.m-l-5
            {:on-click (fn [e]
                         (.stopPropagation e)
                         (save-fn))}
            "save"]]
          [:span.f-s-18.f-w-b
           folder-name])
        (when (not folder-renaming?)
          [:span.m-l-10.f-s-12.opacity-5
           (str "(" (count visible-chars) ")")])]
       (when (not folder-renaming?)
         [:div.flex.align-items-c.m-r-10
          [:span.link-button.m-r-10.f-s-12
           {:on-click (fn [e]
                        (.stopPropagation e)
                        (reset! edit-name folder-name)
                        (dispatch [::folder/toggle-renaming folder-id]))}
           "rename"]
          [:span.link-button.f-s-12
           {:on-click (fn [e]
                        (.stopPropagation e)
                        (reset! confirm-delete? true))}
           "delete"]
          [:i.fa.m-l-10.orange
           {:class (if folder-expanded? "fa-caret-up" "fa-caret-down")}]])]
      (when @confirm-delete?
        [:div.p-20.flex.justify-cont-end
         [:div
          [:div.m-b-10 "Are you sure you want to delete this folder? Characters will not be deleted."]
          [:div.flex
           [:button.form-button
            {:on-click (fn [e]
                         (.stopPropagation e)
                         (reset! confirm-delete? false))}
            "cancel"]
           [:span.link-button.m-l-10
            {:on-click (fn [e]
                         (.stopPropagation e)
                         (dispatch [::folder/delete-folder folder-id]))}
            "delete"]]]])
      (when folder-expanded?
        [:div.item-list
         (doall
          (map
           (fn [{:keys [:db/id ::se/owner] :as summary}]
             ^{:key (:db/id summary)}
             [character-list-item
              expanded-characters
              selected-ids
              (:db/id summary)
              owner
              username
              summary])
           (sort-by ::char/character-name visible-chars)))])]]))))

;;;; ====================================================================
;;;; Orcacle Page
;;;; ====================================================================

(defn orcacle-page
  "Wrapper page for the Orcacle search overlay."
  []
  [content-page
   "Orcacle"
   []
   [orcacle]])

;;;; ====================================================================
;;;; Character Filter Bar
;;;; ====================================================================

(defn character-filter-bar
  "Filter controls for the character list: name search, class/level
   dropdowns, portrait/faction toggles, and clear button."
  []
  (let [classes-open? (r/atom false)
        levels-open? (r/atom false)]
    (fn []
      (let [name-filter      @(subscribe [::char/char-name-filter])
            level-filters    @(subscribe [::char/char-level-filters])
            class-filters    @(subscribe [::char/char-class-filters])
            has-portrait?    @(subscribe [::char/char-has-portrait?])
            has-faction-pic? @(subscribe [::char/char-has-faction-pic?])
            avail-classes    @(subscribe [::char/char-classes-available])
            avail-levels     @(subscribe [::char/char-levels-available])
            any-filter?      (or (not (s/blank? name-filter))
                                 (seq level-filters)
                                 (seq class-filters)
                                 has-portrait?
                                 has-faction-pic?)]
        [:div.main-text-color.m-b-10.char-filter-bar
         (when @classes-open?
           [:div.posn-fixed
            {:style    {:top 0 :left 0 :right 0 :bottom 0 :z-index 100}
             :on-click (fn [e] (.stopPropagation e) (reset! classes-open? false))}])
         (when @levels-open?
           [:div.posn-fixed
            {:style    {:top 0 :left 0 :right 0 :bottom 0 :z-index 100}
             :on-click (fn [e] (.stopPropagation e) (reset! levels-open? false))}])
         [:div.flex.align-items-c.flex-wrap.p-5
          ;; Name search
          [:div.posn-rel.m-r-5.m-b-5
           [:input.input
            {:placeholder "Search by name..."
             :style       {:width "200px"}
             :value       name-filter
             :on-change   #(dispatch [::char/set-char-name-filter (.. % -target -value)])}]
           (when (not (s/blank? name-filter))
             [:i.fa.fa-times.posn-abs.pointer.orange.f-s-14
              {:style    {:right "8px" :top "8px"}
               :on-click #(dispatch [::char/set-char-name-filter ""])}])]

          ;; Classes multi-select dropdown
          [:div.posn-rel.m-r-5.m-b-5
           {:style {:z-index (if @classes-open? 200 1)}}
           [:button.form-button
            {:on-click (fn [e] (.stopPropagation e) (swap! classes-open? not))}
            (str "Classes" (when (seq class-filters) (str " (" (count class-filters) ")")))
            [:i.fa.m-l-5 {:class (if @classes-open? "fa-caret-up" "fa-caret-down")}]]
           (when @classes-open?
             [:div.filter-dropdown.main-text-color
              {:style {:min-width "160px"}}
              (if (seq avail-classes)
                (doall
                 (map (fn [cls]
                        ^{:key cls}
                        [:div.filter-dropdown-item
                         [comps/labeled-checkbox cls (contains? class-filters cls) false
                          (fn [] (dispatch [::char/toggle-char-class-filter cls]))]])
                      avail-classes))
                [:span.opacity-5.f-s-12.p-5 "No classes yet"])])]

          ;; Levels multi-select dropdown
          [:div.posn-rel.m-r-5.m-b-5
           {:style {:z-index (if @levels-open? 200 1)}}
           [:button.form-button
            {:on-click (fn [e] (.stopPropagation e) (swap! levels-open? not))}
            (str "Levels" (when (seq level-filters) (str " (" (count level-filters) ")")))
            [:i.fa.m-l-5 {:class (if @levels-open? "fa-caret-up" "fa-caret-down")}]]
           (when @levels-open?
             [:div.filter-dropdown.main-text-color
              {:style {:min-width "120px"}}
              (if (seq avail-levels)
                (doall
                 (map (fn [lvl]
                        ^{:key lvl}
                        [:div.filter-dropdown-item
                         [comps/labeled-checkbox (str "Level " lvl) (contains? level-filters lvl) false
                          (fn [] (dispatch [::char/toggle-char-level-filter lvl]))]])
                      avail-levels))
                [:span.opacity-5.f-s-12.p-5 "No levels yet"])])]

          ;; Portrait toggle (nil=all, true=with portrait, false=without portrait)
          [:button.form-button.m-r-5.m-b-5
           {:on-click #(dispatch [::char/toggle-char-has-portrait])}
           [:i.fa.fa-user.m-r-5]
           (cond (true? has-portrait?)  "Portrait: Has"
                 (false? has-portrait?) "Portrait: None"
                 :else                  "Portrait: All")]

          ;; Faction Pic toggle (nil=all, true=with faction pic, false=without faction pic)
          [:button.form-button.m-r-5.m-b-5
           {:on-click #(dispatch [::char/toggle-char-has-faction-pic])}
           [:i.fa.fa-flag.m-r-5]
           (cond (true? has-faction-pic?)  "Faction Pic: Has"
                 (false? has-faction-pic?) "Faction Pic: None"
                 :else                     "Faction Pic: All")]

          ;; Clear button — only shown when any filter is active
          (when any-filter?
            [:button.form-button.m-b-5
             {:on-click #(dispatch [::char/clear-char-filters])}
             [:i.fa.fa-times.m-r-5]
             "Clear"])]]))))

;;;; ====================================================================
;;;; Character List Page
;;;; ====================================================================

(defn character-list
  "Main characters page with folder grouping, character filtering, and
   expandable character details."
  []
  (let [characters @(subscribe [::char/filtered-characters])
        folders @(subscribe [::folder/folders])
        char-folder-map @(subscribe [::folder/character-folder-map])
        expanded-characters @(subscribe [:expanded-characters])
        username @(subscribe [:username])
        selected-ids @(subscribe [::char/selected])
        has-selected? @(subscribe [::char/has-selected?])]
    [content-page
     "Characters"
     [{:title "New"
       :icon "plus"
       :on-click #(dispatch [:new-character])}
      {:title "Make Party"
       :icon "users"
       :class (when (not has-selected?) "opacity-5 cursor-disabled")
       :on-click (when has-selected? (make-event-handler ::party/make-party selected-ids))}
      {:title "New Folder"
       :icon "folder"
       :on-click #(dispatch [::folder/create-folder])}]
     [:div.p-5
      [character-filter-bar]
      [:div
       (let [filtered-char-ids (into #{} (map :db/id) characters)
             grouped-characters (group-by ::se/owner characters)
             user-characters (find grouped-characters username)
             other-characters (sort-by key (dissoc grouped-characters username))
             user-chars-list (second user-characters)
             unfiled-user-chars (remove #(get char-folder-map (:db/id %)) user-chars-list)
             user-folders (sort-by ::folder/name folders)]
         [:div
          (when (and username (seq user-folders))
            [:div.m-b-20
             (doall
              (map
               (fn [f]
                 ^{:key (:db/id f)}
                 [folder-item f expanded-characters selected-ids username filtered-char-ids])
               user-folders))])
          (when (and username (seq unfiled-user-chars))
            [:div.m-b-40
             (when (seq user-folders)
               [:div.m-b-10.main-text-color.f-s-14.opacity-5 "Unfiled"])
             [:div.item-list
              (doall
               (map
                (fn [{:keys [:db/id ::se/owner] :as summary}]
                  ^{:key (:db/id summary)}
                  [character-list-item
                   expanded-characters
                   selected-ids
                   (:db/id summary)
                   owner
                   username
                   summary])
                (sort-by ::char/character-name unfiled-user-chars)))]])
          (doall
           (map
            (fn [[owner owner-characters]]
              ^{:key owner}
              [:div.m-b-40
               [:div.m-b-10.main-text-color.f-w-b.f-s-16
                [other-user-component owner "f-s-24 m-l-10 m-r-20 i" true]]
               [:div.item-list
                (doall
                 (map
                  (fn [{:keys [:db/id ::se/owner] :as summary}]
                    ^{:key (:db/id summary)}
                    [character-list-item
                     expanded-characters
                     selected-ids
                     (:db/id summary)
                     owner
                     username
                     summary])
                  (sort-by ::char/character-name owner-characters)))]])
            (sort-by key other-characters)))])]]]))

;;;; ====================================================================
;;;; Parties
;;;; ====================================================================

(def party-name-editor-style
  {:width "200px"
   :height "42px"})

(defn set-editing-party-fn
  "Create an on-change handler that updates the editing-parties atom."
  [editing-parties id]
  #(swap! editing-parties assoc id (event-value %)))

(def set-editing-party-handler
  "Memoized version of set-editing-party-fn."
  (memoize set-editing-party-fn))

(defn parties
  "Main parties page with inline character management: create, rename,
   add/remove characters, and expandable character details per party."
  []
  (let [editing-parties (r/atom {})
        expanded-characters (r/atom {})]
    (fn []
      (let [parties (sort-by ::party/name @(subscribe [::party/parties]))
            device-type @(subscribe [:device-type])
            username @(subscribe [:username])]
        [content-page
         "Parties"
         [{:title "Create Party"
           :icon "users"
           :on-click (make-event-handler ::party/make-empty-party)}]
         [:div.p-5
           [:div
           (doall
            (map
             (fn [{:keys [:db/id ::party/name] characters ::party/character-ids}]
               (let [editing? (get @editing-parties id)
                     character-ids (into #{} (map :db/id) characters)]
                 ^{:key id}
                 [:div.m-b-40
                  [:div.m-b-10.main-text-color.f-w-b.f-s-16
                   [:div.flex.align-items-c
                    [:i.fa.fa-users.m-l-10]
                    (if editing?
                      [:div.flex.align-items-c.flex-wrap
                       [:input.input.m-l-10
                        {:value (or (@editing-parties id) name)
                         :style party-name-editor-style
                         :on-change (set-editing-party-handler editing-parties id)}]
                       [:div.m-l-10.w-200
                        [comps/selection-adder
                         (sequence
                          (comp
                           (remove
                            (fn [{:keys [:db/id]}]
                              (character-ids id)))
                           (map
                            (fn [{:keys [:db/id] :as char-summary}]
                              {:name (character-display-name char-summary)
                               :key id})))
                          @(subscribe [::char/characters]))
                         (fn [e]
                           (let [selected-id (js/parseInt (.. e -target -value))]
                             (dispatch [::party/add-character id selected-id])))]]
                       [:div.m-t-5
                        [:button.form-button.m-l-10
                         {:on-click #(do (dispatch [::party/rename-party id (@editing-parties id)])
                                         (swap! editing-parties assoc id nil))}
                         "save"]
                        [:button.form-button.m-l-10
                         {:on-click #(dispatch [::party/delete-party id])}
                         "delete"]
                        [:button.form-button.m-l-10
                         {:on-click #(swap! editing-parties assoc id nil)}
                         "cancel"]]]
                      [:div.flex.align-items-c
                       [:span.m-l-5 name]
                       [:i.fa.fa-pencil-alt.m-l-10.opacity-5.hover-opacity-full.pointer
                        {:on-click #(swap! editing-parties assoc id name)}]])]]
                  [:div.item-list
                   (doall
                    (map
                     (fn [{:keys [::se/owner] :as summary}]
                       (let [character-id (:db/id summary)
                             character @(subscribe [::char/character character-id])
                             expanded? (get-in @expanded-characters [id character-id])
                             char-page-path (routes/path-for routes/dnd-e5-char-page-route :id character-id)
                             char-page-route (routes/match-route char-page-path)]
                         ^{:key character-id}
                         [:div.main-text-color.item-list-item
                          [:div.pointer
                           [:div.flex.justify-cont-s-b.align-items-c
                            {:on-click #(swap! expanded-characters update-in [id character-id] not)}
                            [:div.m-l-10.flex.align-items-c
                             [:div.f-s-24.f-w-600
                              [:div.list-character-summary
                               [character-summary-2 summary true owner true false]]]]
                            [:div.orange.pointer.m-r-10
                             (when (not= device-type :mobile) [:span.underline (if expanded?
                                                                               "collapse"
                                                                               "open")])
                             [:i.fa.m-l-5
                              {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]
                           (when expanded?
                             [:div
                              {:style character-display-style}
                              [:div.flex.justify-cont-end.uppercase.align-items-c
                               (when (= username owner)
                                 [:button.form-button
                                  {:on-click #(dispatch [:edit-character character])}
                                  "edit"])
                               [:button.form-button.m-l-5
                                {:on-click #(dispatch [:route char-page-route])}
                                "view"]
                               [:button.form-button.m-l-5
                                {:on-click #(dispatch [::party/remove-character id character-id])}
                                "remove from party"]]
                              [character-display character-id false (if (= :mobile device-type) 1 2)]])]]))
                     (sort-by :orcpub.dnd.e5.character/character-name characters)))]]))
             parties))]]]))))

;;;; ====================================================================
;;;; Monster List
;;;; ====================================================================

(defn monster-list-item
  "Collapsible monster list item with summary and expanded detail view."
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
             [:div.m-r-10 (svg-icon "beer-stein" 24 @(subscribe [:theme]))])
           [monster-summary name size type subtypes alignment]]]
         [:div.orange.pointer.m-r-10
          (when (not= @device-type? :mobile)
            [:span.underline (if expanded?
                               "collapse"
                               "open")])
          [:i.fa.m-l-5
           {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]
        (when expanded?
          [:div.p-10
           {:style character-display-style}
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
           [monster-component monster]])]])))

(defn monster-list-items
  "Render the filtered list of monsters."
  []
  (let [filtered-monsters @(subscribe [::monsters/filtered-monsters])]
    [:div.item-list
     (doall
       (map
         (fn [{:keys [name] :as monster}]
           ^{:key name}
           [monster-list-item monster])
         filtered-monsters))]))

(defn clear-monsters-filter
  "Dispatch handler to clear the monster text filter."
  []
  (dispatch [::char/filter-monsters ""]))

(def toggle-handler
  "Memoized toggle for boolean atoms."
  (memoize
   (fn [a]
     #(swap! a not))))

(defn- sort-toggle
  "Clickable sort column header with directional arrow indicator."
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
  "Filter panel for monster size, type, and subtype checkboxes."
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
            (comps/checkbox (not @(subscribe [::char/monster-filter-hidden? :size size])) false)
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
            (comps/checkbox (not @(subscribe [::char/monster-filter-hidden? :type type])) false)
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
              (comps/checkbox (not @(subscribe [::char/monster-filter-hidden? :subtype subtype])) false)
              (str " " (s/capitalize (common/kw-to-name subtype)))])
           subtypes))]])])

(defn monster-filter-toggle
  "Button to show/hide the monster trait filters panel."
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
  "Main monsters page with text search, sort controls, trait filters,
   and expandable monster details."
  []
  (r/with-let [filters-expanded? (r/atom false)
               sort-criteria (subscribe [::char/monster-sort-criteria])
               sort-direction (subscribe [::char/monster-sort-direction])]
    [content-page
     "Monsters"
     []
     [:div.p-l-5.p-r-5.p-b-10
      [:div.p-b-10.p-l-10.p-r-10
       [:div.posn-rel
        [:input.input.f-s-24.p-l-20.w-100-p.h-60
         {:value     @(subscribe [::char/monster-text-filter])
          :on-change (make-arg-event-handler ::char/filter-monsters event-value)}]
        [:i.fa.fa-times.posn-abs.f-s-24.pointer.main-text-color
         {:style    close-icon-style
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

;;;; ====================================================================
;;;; Spell List
;;;; ====================================================================

(defn spell-list-item
  "Collapsible spell list item with summary and expanded detail view."
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
           [:div.m-r-10 (svg-icon "beer-stein" 24 @(subscribe [:theme]))])
         [spell-summary name level school ritual true 12]]]
       [:div.orange.pointer.m-r-10
        (when (not= device-type :mobile) [:span.underline (if expanded?
                                                          "collapse"
                                                          "open")])
        [:i.fa.m-l-5
         {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]
      (when expanded?
        [:div.p-10
         {:style character-display-style}
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
         [spell-component spell true]])]]))

(defn spell-list-items
  "Render the filtered list of spells."
  [device-type]
  [:div.item-list
   (doall
    (map
     (fn [{:keys [name level school key] :as spell}]
       ^{:key name}
       [spell-list-item spell])
     @(subscribe [::char/filtered-spells])))])

(defn spell-list
  "Main spells page with text search and expandable spell details."
  []
  (let [device-type @(subscribe [:device-type])]
    [content-page
     "Spells"
     []
     [:div.p-l-5.p-r-5.p-b-10
      [:div.p-b-10.p-l-10.p-r-10
       [:div.posn-rel
        [:input.input.f-s-24.p-l-20.w-100-p.h-60
         {:value @(subscribe [::char/spell-text-filter])
          :on-change (make-arg-event-handler ::char/filter-spells event-value)}]
        [:i.fa.fa-times.posn-abs.f-s-24.pointer.main-text-color
         {:style close-icon-style
          :on-click (make-event-handler ::char/filter-spells "")}]]]
      [spell-list-items device-type]]]))

;;;; ====================================================================
;;;; Item List
;;;; ====================================================================

(defn item-list-item
  "Collapsible magic item list item with summary, expanded details, and
   delete confirmation for homebrew items."
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
         [item-summary item]]]
       [:div.orange.pointer.m-r-10
        (when (not= device-type :mobile) [:span.underline (if expanded?
                                                          "collapse"
                                                          "open")])
        [:i.fa.m-l-5
         {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]
      (when expanded?
        [:div.p-10
         {:style character-display-style}
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
                  {:on-click  (delete-item-handler id)}
                  "delete"]]]])
            [item-component item]
        ])
      ]]))

(defn item-list-items
  "Render the filtered list of magic items."
  []
  [:div.item-list
   (doall
    (map
     (fn [{:keys [:db/id key] :as item}]
       ^{:key (or key id)}
       [item-list-item item])
     @(subscribe [::char/filtered-items])))])

(defn item-list
  "Main items page with text search, 'New Item' button, and expandable
   item details."
  []
  (let [device-type @(subscribe [:device-type])]
    [content-page
     "Items"
     [[:button.form-button
       {:on-click (make-event-handler ::mi/new-item)}
       [:div.flex.align-items-c.white
        [svg-icon "beer-stein" 18 ""]
        [:span.m-l-5 "New Item"]]]]
     [:div.p-l-5.p-r-5.p-b-10
      [:div.p-b-10.p-l-10.p-r-10
       [:div.posn-rel
        [:input.input.f-s-24.p-l-20.w-100-p.h-60
         {:value @(subscribe [::char/item-text-filter])
          :on-change (make-arg-event-handler ::char/filter-items event-value)}]
        [:i.fa.fa-times.posn-abs.f-s-24.pointer.main-text-color
         {:style close-icon-style
          :on-click (make-event-handler ::char/filter-items "")}]]]
      [item-list-items]]]))
