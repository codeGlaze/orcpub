(ns orcpub.dnd.e5.views.builders.race
  "Race and subrace homebrew builder pages — proficiency choices,
   traits, spells, damage resistances, and language selections.

   Extracted from views.builders — imports shared option-* helpers and
   builder infrastructure from the parent module."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [orcpub.common :as common]
            [orcpub.components :as comps]
            [orcpub.route-map :as routes]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.races :as races]
            [orcpub.dnd.e5.languages :as langs]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.dnd.e5.views.common
             :refer [labeled-dropdown dropdown textarea-field]]
            [orcpub.dnd.e5.views.builders
             :refer [builder-input-field value-to-item
                     plugin-datalist option-source-name-label
                     option-proficiency-choice
                     option-skill-proficiency-choice option-skill-proficiency
                     option-languages
                     option-tool-proficiency option-armor-proficiency
                     option-hps option-damage-resistance option-damage-immunity
                     option-weapon-proficiency option-traits
                     option-saving-throw-advantages
                     option-spells
                     builder-page]]))

;; ---------------------------------------------------------------------------
;; Field wrappers — thin dispatch helpers for race/subrace property events
;; ---------------------------------------------------------------------------

(defn race-input-field [title prop race & [class-names]]
  (builder-input-field title prop race ::races/set-race-prop class-names))

(defn subrace-input-field [title prop subrace & [class-names]]
  (builder-input-field title prop subrace ::races/set-subrace-prop class-names))

;; ---------------------------------------------------------------------------
;; Proficiency choice wrappers — race-exclusive option-proficiency-choice uses
;; ---------------------------------------------------------------------------

(defn option-weapon-proficiency-choice [option
                                        set-path-prop-event
                                        toggle-path-prop-event]
  (option-proficiency-choice
   "Weapon Proficiency Choice"
   :weapon-proficiency-options
   @(subscribe [::mi/custom-and-standard-weapons])
   option
   set-path-prop-event
   toggle-path-prop-event))

(defn option-language-proficiency-choice
  "Wraps option-proficiency-choice; subscribes inside render context."
  [option set-path-prop-event toggle-path-prop-event]
  (option-proficiency-choice
   "Language Proficiency Choice"
   :language-options
   @(subscribe [::langs/languages])
   option
   set-path-prop-event
   toggle-path-prop-event))

;; ---------------------------------------------------------------------------
;; Language checkboxes — race-only language toggle grid + add-language link
;; ---------------------------------------------------------------------------

(defn language-checkboxes [race languages]
  [:div
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [{:keys [name key]}]
        ^{:key key}
        [:span.m-r-20.m-b-10
         [comps/labeled-checkbox
          name
          (get-in race [:languages name])
          false
          #(dispatch [::races/toggle-language name])]])
      (sort-by
       :name
       languages)))]
   [:div.pointer.m-t-10
    [:span.bg-lighter.p-5
     {:on-click #(dispatch [:route routes/dnd-e5-language-builder-page-route])}
     [:i.fa.fa-plus]
     [:span.orange.underline.m-l-5 "Add Language"]]]])

;; ---------------------------------------------------------------------------
;; subrace-builder — ~130 lines, subrace homebrew form
;; ---------------------------------------------------------------------------

(defn subrace-builder []
  (let [subrace @(subscribe [::races/subrace-builder-item])
        race-key (get subrace :race)
        race @(subscribe [::races/race race-key])
        races @(subscribe [::races/races])
        mobile? @(subscribe [:mobile?])]
    [:div.p-20.main-text-color
     [:div.flex.flex-wrap
      [:div.m-b-20
       [subrace-input-field
        "Name"
        :name
        subrace]]
      [:div.m-l-5.m-b-20
       [labeled-dropdown
        "Race"
        {:items (map
                 (fn [{:keys [name key]}]
                   {:title name
                    :value (clojure.core/name key)})
                 races)
         :value (get subrace :race)
         :on-change #(dispatch [::races/set-subrace-prop :race (keyword %)])}]]
       [plugin-datalist
        option-source-name-label
        subrace
        ::races/set-subrace-prop]
      ]
     [:div.m-b-20.flex.flex-wrap
      [:div.m-r-5
       [labeled-dropdown
        "Size"
        {:items (map
                 (fn [kw]
                   {:title (name kw)
                    :value (name kw)})
                 ["small" "medium" "large"])
         :value (name (or (get subrace :size)
                          (get race :size)))
         :on-change #(dispatch [::races/set-subrace-prop :size (keyword %)])}]]
      [:div.m-r-5
       [labeled-dropdown
        "Speed"
        {:items (map
                 value-to-item
                 (range 5 55 5))
         :value (or (get subrace :speed)
                    (get race :speed))
         :on-change #(dispatch [::races/set-subrace-speed %])}]]
      [:div.m-r-5
       [labeled-dropdown
        "Darkvision"
        {:items (map
                 value-to-item
                 [0 60 120])
         :value (or (get subrace :darkvision)
                    (get race :darkvision))
         :on-change #(dispatch [::races/set-subrace-prop :darkvision (js/parseInt %)])}]]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Ability Score Increases"]
      [:table.t-a-c
       [:tbody
        [:tr.f-w-b
         [:th.p-2.t-a-l "Ability"]
         [:th.p-2 "Race Bonus"]
         [:th.p-2]
         [:th.p-2 "Subrace Bonus"]
         [:th.p-2]
         [:th.p-2 "Total"]]
        (doall
         (map
          (fn [{:keys [name key abbr]}]
            (let [race-bonus (get-in race [:abilities key] 0)
                  subrace-bonus (get-in subrace [:abilities key] 0)]
              ^{:key key}
              [:tr
               [:td.p-2.f-w-b.t-a-l (if mobile? abbr name)]
               [:td.p-2 race-bonus]
               [:td.p-2 "+"]
               [:td.p-2 [dropdown
                         {:items (map
                                  (fn [bonus]
                                    {:title (common/bonus-str bonus)
                                     :value bonus})
                                  (range -2 3 1))
                          :value subrace-bonus
                          :on-change #(dispatch [::races/set-subrace-ability-increase key %])}]]
               [:td.p-2 "="]
               [:td.p-2 (+ race-bonus subrace-bonus)]]))
          opt/abilities))]]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Modifiers"]
      [:div [option-hps subrace ::races/toggle-subrace-value-prop]]
      [:div [option-damage-resistance subrace ::races/toggle-subrace-map-prop]]
      [:div [option-damage-immunity subrace ::races/toggle-subrace-map-prop]]
      [:div [option-saving-throw-advantages subrace ::races/toggle-subrace-map-prop]]
      [:div [option-weapon-proficiency subrace ::races/toggle-subrace-map-prop]]
      [:div [option-armor-proficiency subrace ::races/toggle-subrace-map-prop]]
      [:div [option-tool-proficiency subrace ::races/toggle-subrace-path-prop]]
      [:div [option-skill-proficiency subrace ::races/toggle-subrace-map-prop]]
      [:div
       [option-skill-proficiency-choice
        subrace
        ::races/set-subrace-path-prop
        ::races/toggle-subrace-path-prop]]
      [:div [option-languages subrace ::races/toggle-subrace-map-prop]]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Spells"]
      [option-spells
       subrace
       ::races/set-subrace-spell-level
       ::races/set-subrace-spell-value
       ::races/delete-subrace-spell]]
     [option-traits
      subrace
      ::e5/add-subrace-trait
      ::e5/edit-subrace-trait-name
      ::e5/edit-subrace-trait-type
      ::e5/edit-subrace-trait-description
      ::e5/delete-subrace-trait
      :types [{:title "Other"
               :value :other}
              {:title "Action"
               :value :action}
              {:title "Bonus Action"
               :value :b-action}
              {:title "Reaction"
               :value :reaction}]]]))

;; ---------------------------------------------------------------------------
;; race-builder — ~149 lines, race homebrew form
;; ---------------------------------------------------------------------------

(defn race-builder []
  (let [race @(subscribe [::races/builder-item])]
    [:div.p-20.main-text-color
     [:div.m-b-20.flex.flex-wrap
      [race-input-field
       "Name"
       :name
       race]
      [plugin-datalist
        option-source-name-label
        race
        ::races/set-race-prop]
      ]
     [:div.m-b-20
       [:div.f-w-b
        "Description"]
       [textarea-field
        {:value (get race :help)
         :on-change #(dispatch [::races/set-race-prop :help %])}]]
     [:div.m-b-20.flex.flex-wrap
      [:div.m-r-5
       [labeled-dropdown
        "Size"
        {:items (map
                 (fn [kw]
                   {:title (name kw)
                    :value (name kw)})
                 ["small" "medium" "large"])
         :value (common/safe-name (get race :size :medium))
         :on-change #(dispatch [::races/set-race-prop :size (keyword %)])}]]
      [:div.m-r-5
       [labeled-dropdown
        "Speed"
        {:items (map
                 value-to-item
                 (range 0 55 5))
         :value (get race :speed)
         :on-change #(dispatch [::races/set-race-speed %])}]]
      [:div.m-r-5
       [labeled-dropdown
        "Flying Speed"
        {:items (map
                 value-to-item
                 (range 0 55 5))
         :value (or (get-in race [:props :flying-speed]) 0)
         :on-change #(dispatch [::races/set-race-value-prop :flying-speed (js/parseInt %)])}]]
      [:div.m-r-5
       [labeled-dropdown
        "Swimming Speed"
        {:items (map
                 value-to-item
                 (range 0 55 5))
         :value (or (get-in race [:props :swimming-speed]) 0)
         :on-change #(dispatch [::races/set-race-value-prop :swimming-speed (js/parseInt %)])}]]
      [:div.m-r-5
       [labeled-dropdown
        "Darkvision"
        {:items (map
                 value-to-item
                 [0 60 120])
         :value (get race :darkvision)
         :on-change #(dispatch [::races/set-race-prop :darkvision (js/parseInt %)])}]]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Armor Class"]
      [:div.flex.flex-wrap
       [comps/labeled-checkbox
        "Without armor your AC becomes 13 + your DEX modifier."
        (get-in race [:props :lizardfolk-ac])
        false
        #(dispatch [::races/toggle-race-prop :lizardfolk-ac])]
       [:div.m-l-20
        [comps/labeled-checkbox
         "Your AC is 17, regardless of your DEX modifier or armor."
         (get-in race [:props :tortle-ac])
         false
         #(dispatch [::races/toggle-race-prop :tortle-ac])]]]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Ability Score Increases"]
      [:div.flex.flex-wrap
       (doall
        (map
         (fn [{:keys [name key]}]
           ^{:key key}
           [:div.m-l-5
            [labeled-dropdown
             name
             {:items (map
                      (fn [bonus]
                        {:title (common/bonus-str bonus)
                         :value bonus})
                      (range -2 3 1))
              :value (get-in race [:abilities key] 0)
              :on-change #(dispatch [::races/set-race-ability-increase key %])}]])
         opt/abilities))]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Modifiers"]
      [:div.m-b-20
       [:div.f-s-18.f-w-b.m-b-10 "Languages"]
       [:div [language-checkboxes race @(subscribe [::langs/languages])]]]
      [:div.m-b-20
       [:div [option-weapon-proficiency race ::races/toggle-race-map-prop]]]
      [:div.m-b-20
       [:div [option-armor-proficiency race ::races/toggle-race-map-prop]]]
      [:div.m-b-20
       [option-tool-proficiency race ::races/toggle-race-path-prop]]
      [:div.m-b-20
       [:div [option-damage-resistance race ::races/toggle-race-map-prop]]]
      [:div.m-b-20
       [:div [option-damage-immunity race ::races/toggle-race-map-prop]]]
      [:div.m-b-20
       [:div [option-skill-proficiency race ::races/toggle-race-map-prop]]]
      [:div
       [option-skill-proficiency-choice
        race
        ::races/set-race-path-prop
        ::races/toggle-race-path-prop]]
      [:div
       [option-language-proficiency-choice
        race
        ::races/set-race-path-prop
        ::races/toggle-race-path-prop]]
      [:div
       [option-weapon-proficiency-choice
        race
        ::races/set-race-path-prop
        ::races/toggle-race-path-prop]]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Spells"]
      [option-spells
       race
       ::races/set-race-spell-level
       ::races/set-race-spell-value
       ::races/delete-race-spell]]
     [option-traits
      race
      ::e5/add-race-trait
      ::e5/edit-race-trait-name
      ::e5/edit-race-trait-type
      ::e5/edit-race-trait-description
      ::e5/delete-race-trait
      :types [{:title "Other"
               :value :other}
              {:title "Action"
               :value :action}
              {:title "Bonus Action"
               :value :b-action}
              {:title "Reaction"
               :value :reaction}]]]))

;; ---------------------------------------------------------------------------
;; Page wrappers — builder-page scaffolding for routing
;; ---------------------------------------------------------------------------

(defn subrace-builder-page []
  (builder-page "Subrace" ::races/reset-subrace ::races/save-subrace subrace-builder))

(defn race-builder-page []
  (builder-page "Race" ::races/reset-race ::races/save-race race-builder))
