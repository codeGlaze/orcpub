(ns orcpub.dnd.e5.views.builders.background
  "Background homebrew builder page — skill proficiencies, languages,
   tool proficiencies, starting equipment, and traits.

   Extracted from views.builders — imports shared option-* helpers and
   builder infrastructure from the parent module."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [orcpub.components :as comps]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.backgrounds :as bg]
            [orcpub.dnd.e5.skills :as skills]
            [orcpub.dnd.e5.equipment :as equip]
            [orcpub.dnd.e5.views.common
             :refer [textarea-field]]
            [orcpub.dnd.e5.views.builders
             :refer [builder-input-field input-builder-field
                     plugin-datalist option-source-name-label
                     option-traits builder-page]]))


;;; ─── Background-specific checkbox helpers ────────────────────────────

(defn background-input-field [title prop bg & [class-names]]
  (builder-input-field title prop bg ::bg/set-background-prop class-names))

(defn tool-prof-checkboxes [background tools]
  [:div.flex.flex-wrap
   (doall
    (map
     (fn [{:keys [name key]}]
       ^{:key key}
       [:span.m-r-20.m-b-10
        [comps/labeled-checkbox
         name
         (get-in background [:profs :tool key])
         false
         #(dispatch [::bg/toggle-tool-prof key])]])
     tools))])

(defn tool-choice-checkboxes [background key]
  [:div.flex.flex-wrap
   (doall
    (map
     (fn [num]
       ^{:key num}
       [:span.m-r-20.m-b-10
        [comps/labeled-checkbox
         (str "Any " num)
         (= num (get-in background [:profs :tool-options key]))
         false
         #(dispatch [::bg/toggle-choice-tool-prof key num])]])
     (range 1 4)))])

(defn language-choice-checkboxes [background]
  [:div.flex.flex-wrap
   (doall
    (map
     (fn [num]
       ^{:key num}
       [:span.m-r-20.m-b-10
        [comps/labeled-checkbox
         (str "Any " num)
         (= num (get-in background [:profs :language-options :choose]))
         false
         #(dispatch [::bg/toggle-choice-language-prof num])]])
     (range 1 4)))])

(defn starting-equipment-choice-checkboxes [background equipment equipment-name]
  [:div.m-r-20.m-b-10
   [comps/labeled-checkbox
    "Any 1"
    (some
     (fn [{:keys [name]}]
       (= name equipment-name))
     (:equipment-choices background))
    false
    #(dispatch [::bg/toggle-starting-equipment-choice equipment equipment-name])]])

(defn starting-equipment-checkboxes [background equipment]
  [:div.flex.flex-wrap
   (doall
    (map
     (fn [{:keys [name key]}]
       ^{:key key}
       [:span.m-r-20.m-b-10
        [comps/labeled-checkbox
         name
         (get-in background [:equipment key])
         false
         #(dispatch [::bg/toggle-starting-equipment key])]])
     equipment))])


;;; ─── Background builder sections ─────────────────────────────────────

(defn background-skill-proficiencies [background]
  [:div.m-b-20
   [:div.f-s-24.f-w-b.m-b-20 "Skill Proficiencies"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [{:keys [name key]}]
        ^{:key key}
        [:span.m-r-20.m-b-10
         [comps/labeled-checkbox
          name
          (get-in background [:profs :skill key])
          false
          #(dispatch [::bg/toggle-skill-prof key])]])
      skills/skills))]])

(defn background-languages [background]
  [:div.m-t-20.m-b-20
   [:div.f-s-24.f-w-b.m-b-20 "Languages"]
   [:div
    [language-choice-checkboxes background]]])

(defn background-tool-proficiencies [background]
  [:div.m-t-20.m-b-20
   [:div.f-s-24.f-w-b.m-b-10 "Tool Proficiencies"]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Artisans Tools"]
    [:div
     [tool-choice-checkboxes background :artisans-tool]]
    [:div
     [tool-prof-checkboxes background equip/artisans-tools]]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Musical Instruments"]
    [tool-choice-checkboxes background :musical-instrument]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Gaming Set"]
    [tool-choice-checkboxes background :gaming-set]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Vehicles"]
    [tool-prof-checkboxes background equip/vehicle-types]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Other Tools"]
    [tool-prof-checkboxes background equip/misc-tools]]])

(defn background-starting-equipment [background]
  [:div.m-t-20.m-b-20
   [:div.f-s-24.f-w-b.m-b-10 "Starting Equipment"]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Treasure"]
    [input-builder-field
     [:span.f-w-b "Gold"]
     (get-in background [:treasure :gp])
     #(dispatch [::bg/set-background-gold %])
     {:class "input h-40"
      :type :number}]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Clothing"]
    [:div [starting-equipment-checkboxes background equip/clothes]]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Artisan's Tools"]
    [:div [starting-equipment-choice-checkboxes background equip/artisans-tools "Artisan's Tools"]]
    [:div [starting-equipment-checkboxes background equip/artisans-tools]]]
   [:div.m-b-20
    [:div.f-s-18.f-w-b.m-b-10 "Musical Instruments"]
    [starting-equipment-choice-checkboxes background equip/musical-instruments "Musical Instruments"]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Other Tools"]
    [starting-equipment-checkboxes background equip/misc-tools]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Holy Symbols"]
    [starting-equipment-checkboxes background equip/holy-symbols]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Other Equipment"]
    [starting-equipment-checkboxes background equip/misc-equipment]]])


;;; ─── Background builder + page wrapper ───────────────────────────────

(defn background-builder []
  (let [background @(subscribe [::bg/builder-item])]
    [:div.p-20.main-text-color
     [:div.m-b-20.flex.flex-wrap
      [background-input-field
       "Name"
       :name
       background]
      [plugin-datalist
       option-source-name-label
       background
       ::bg/set-background-prop]
      ]
     [:div.m-b-20
       [:div.f-w-b
        "Description"]
       [textarea-field
        {:value (get background :help)
         :on-change #(dispatch [::bg/set-background-prop :help %])}]]
     [:div [background-skill-proficiencies background]]
     [:div [background-languages background]]
     [:div [background-tool-proficiencies background]]
     [:div [background-starting-equipment background]]
     [:div
      [option-traits
       background
       ::e5/add-background-trait
       ::e5/edit-background-trait-name
       ::e5/edit-background-trait-type
       ::e5/edit-background-trait-description
       ::e5/delete-background-trait]]]))

(defn background-builder-page []
  (builder-page "Background" ::bg/reset-background ::bg/save-background background-builder))
