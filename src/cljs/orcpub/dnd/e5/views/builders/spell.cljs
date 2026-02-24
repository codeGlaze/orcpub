(ns orcpub.dnd.e5.views.builders.spell
  "Spell homebrew builder page — level, school, components, ritual,
   attack roll, class spell-list assignment.

   Extracted from views.builders — imports shared builder infrastructure
   from the parent module."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [orcpub.common :as common]
            [orcpub.components :as comps]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.views.common
             :refer [textarea-field labeled-dropdown]]
            [orcpub.dnd.e5.views.builders
             :refer [builder-input-field plugin-datalist
                     option-source-name-label builder-page]]))


;;; ─── Field wrapper + helpers ───────────────────────────────────────

(defn spell-input-field [title prop spell & [class-names]]
  (builder-input-field title prop spell ::spells/set-spell-prop class-names))

(defn component-checkbox
  "Single V/S/M component toggle for the spell builder."
  [component spell]
  [:span.m-r-20.m-b-10
   [comps/labeled-checkbox
    (common/kw-to-name component)
    (get-in spell [:components component])
    false
    #(dispatch [::spells/toggle-spell-component component])]])


;;; ─── Spell builder + page wrapper ──────────────────────────────────

(defn spell-builder []
  (let [{:keys [:level :school] :as spell} @(subscribe [::spells/builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [spell-input-field
       "Name"
       :name
       spell
       "m-b-20"]
      [plugin-datalist
       option-source-name-label
       spell
       ::spells/set-spell-prop]
      ]

     [:div.flex.w-100-p.flex-wrap
      [:div.flex-grow-1.m-b-20
       [labeled-dropdown
        "Level"
        {:items (map
                 (fn [level] {:title (if (zero? level)
                                       "Cantrip"
                                       (str (common/ordinal level) "-level"))
                              :value level})
                 (range 10))
         :value level
         :on-change #(dispatch [::spells/set-spell-level %])}]]
      [:div.flex-grow-1.m-l-5
       [labeled-dropdown
        "School"
        {:items (map
                 (fn [school] {:title school
                               :value school})
                 (sort spells/schools))
         :value school
         :on-change #(dispatch [::spells/set-spell-prop :school %])}]]
      [
       :div.flex-grow-1.m-l-5
       [:div.m-t-20.m-r-20.m-b-10
        [comps/labeled-checkbox
         "Ritual?"
         (get spell :ritual)
         false
         #(dispatch [::spells/toggle-spell-prop :ritual])]]
       [:div.m-r-20.m-b-10
        [comps/labeled-checkbox
         "Requires Attack Roll?"
         (get spell :attack-roll?)
         false
         #(dispatch [::spells/toggle-spell-prop :attack-roll?])]]]]
     [:div.flex.w-100-p.flex-wrap
      [spell-input-field "Casting Time" :casting-time spell "m-b-20"]
      [spell-input-field "Range" :range spell "m-l-5 m-b-20"]]
     [:div [:h2.f-s-24.f-w-b.m-b-10 "Components"]]
     [:div.flex.w-100-p.flex-wrap
      [component-checkbox :verbal spell]
      [component-checkbox :somatic spell]
      [component-checkbox :material spell]]
     [:div.m-b-20
      [textarea-field
       {:value (get-in spell [:components :material-component])
        :on-change #(dispatch [::spells/set-material-component %])}]]
     [:div.m-b-20
      [spell-input-field "Duration" :duration spell "m-b-20"]]
     [:div.w-100-p
      [:div.f-s-24.f-w-b
       "Description"]
      [:div.m-b-20
       [textarea-field
        {:value (get spell :description)
         :on-change #(dispatch [::spells/set-spell-prop :description %])}]]]
     [:div.m-b-20
      [:div.f-w-b.m-b-10 "Add This Spell to Which Class Spell Lists?"]
      [:div.flex.flex-wrap
       (map
        (fn [{:keys [key name]}]
          ^{:key key}
          [:div.m-r-10.pointer.m-b-10
           {:on-click #(dispatch [::spells/toggle-spell-list key])}
           [comps/checkbox (get-in spell [:spell-lists key])]
           [:span.m-l-5 name]])
        @(subscribe [::spells/spellcasting-classes]))]]]))

(defn spell-builder-page []
  (builder-page "Spell" ::spells/reset-spell ::spells/save-spell spell-builder))
