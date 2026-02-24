(ns orcpub.dnd.e5.views.builders.monster
  "Monster homebrew builder page — stat blocks, damage types, condition
   immunities, traits, and languages.

   Extracted from views.builders — imports shared option-* helpers and
   builder infrastructure from the parent module."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [clojure.string :as s]
            [orcpub.common :as common]
            [orcpub.components :as comps]
            [orcpub.dice :as dice]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.monsters :as monsters]
            [orcpub.dnd.e5.damage-types :as damage-types]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.dnd.e5.skills :as skills]
            [orcpub.dnd.e5.views.common
             :refer [make-event-handler make-arg-event-handler
                     labeled-dropdown textarea-field]]
            [orcpub.dnd.e5.views
             :refer [labeled-checkbox]]
            [orcpub.dnd.e5.views.builders
             :refer [builder-input-field value-to-item input-builder-field
                     plugin-datalist option-source-name-label
                     option-languages option-damage-resistance
                     option-damage-immunity option-traits
                     builder-page]]))


;;; ─── Field wrapper + monster-only option helpers ───────────────────

(defn monster-input-field [title prop monster & [class-names type]]
  (builder-input-field title prop monster ::monsters/set-monster-prop class-names type))

(defn option-damage-vulnerability
  "Damage vulnerability checkboxes — monster-only (not shared)."
  [option toggle-map-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Damage Vulnerabilities"]
   (let [kw :damage-vulnerability]
     [:div.flex.flex-wrap
      (doall
       (map
        (fn [damage-type]
          ^{:key damage-type}
          [:div.m-r-20.m-b-10
           [comps/labeled-checkbox
            (str "Vulnerability to " (name damage-type) " damage")
            (get-in option [:props kw damage-type])
            false
            #(dispatch [toggle-map-prop-event kw damage-type])]])
        opt/damage-types))])])

(defn option-condition-immunity [option toggle-map-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Condition Immunities"]
   (let [kw :condition-immunity]
     [:div.flex.flex-wrap
      (doall
       (map
        (fn [{:keys [name key]}]
          ^{:key key}
          [:div.m-r-20.m-b-10
           [comps/labeled-checkbox
            (str "Immunity to being " name)
            (get-in option [:props kw key])
            false
            #(dispatch [toggle-map-prop-event kw key])]])
        opt/conditions))])])


;;; ─── Monster builder + page wrapper ────────────────────────────────

(defn monster-builder
  "Homebrew monster/NPC builder form — stats, AC, HP, speeds, saves,
   skills, senses, languages, challenge rating, and actions/features."
  []
  (let [{:keys [name
                key
                size
                type
                alignment
                armor-class
                armor-notes
                hit-points
                speed
                str
                dex
                con
                int
                wis
                cha
                saving-throws
                skills
                senses
                languages
                challenge
                traits
                actions
                legendary-actions] :as monster}
        @(subscribe [::monsters/builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [monster-input-field
       "Name"
       :name
       monster
       "m-b-20 flex-grow-1"]
      [plugin-datalist
       option-source-name-label
       monster
       ::monsters/set-monster-prop]
      ]
     [:div.flex.w-100-p.flex-wrap

      [:div.flex-grow-1.m-b-20.m-l-5
       [labeled-dropdown
        "Size"
        {:items (map
                 (fn [kw]
                   {:title (monsters/monster-sizes kw)
                    :value kw})
                 monsters/monster-size-order)
         :value (or size :tiny)
         :on-change #(dispatch [::monsters/set-monster-prop :size (keyword %)])}]]
      [:div.flex-grow-1.m-b-20.m-l-5
       [labeled-dropdown
        "Type"
        {:items (map
                 (fn [kw]
                   {:title (common/kw-to-name kw)
                    :value kw})
                 monsters/monster-types)
         :value (or type :aberration)
         :on-change #(dispatch [::monsters/set-monster-prop :type (keyword %)])}]]
      [:div.flex-grow-1.m-b-20.m-l-5
       [labeled-dropdown
        "Alignment"
        {:items (map
                 (fn [nm]
                   {:title nm
                    :value nm})
                 @(subscribe [::monsters/alignments]))
         :value (or alignment "neutral")
         :on-change #(dispatch [::monsters/set-monster-prop :alignment %])}]]]
     [:div.flex.w-100-p.flex-wrap
      [:div.flex-grow-1.m-b-20.m-l-5
       [labeled-dropdown
        "Armor Class"
        {:items (map
                  value-to-item
                  (range 5 25))
         :value (or armor-class 10)
         :on-change #(dispatch [::monsters/set-monster-prop :armor-class (js/parseInt %)])}]]
      [monster-input-field
       "Armor Notes"
       :armor-notes
       monster
       "m-b-5 m-l-5 flex-grow-1"]]
     [:div.flex.w-100-p.flex-wrap
       [:div.m-l-5.m-b-20
        [labeled-dropdown
         "HP Die Count"
         {:items (cons
                  {:title "-"}
                  (map
                   value-to-item
                   (range 1 36)))
          :value (get hit-points :die-count)
          :on-change #(let [v (js/parseInt %)] (dispatch [::monsters/set-monster-path-prop [:hit-points :die-count] (when (not (js/isNaN v)) v)]))}]]
       [:div.m-l-5.m-b-20
        [labeled-dropdown
         "HP Die"
         {:items (cons
                  {:title "-"}
                  (map
                   value-to-item
                   dice/dice-sides))
          :value (get hit-points :die)
          :on-change #(let [v (js/parseInt %)]
                        (dispatch [::monsters/set-monster-path-prop [:hit-points :die] (when (not (js/isNaN v)) v)]))}]]
       [:div.m-l-5.m-b-20
        [input-builder-field
         [:span.f-w-b.m-b-5.f-s-16 "HP Modifier"]
         (get hit-points :modifier 0)
         #(let [v (js/parseInt %)]
            (dispatch [::monsters/set-monster-path-prop [:hit-points :modifier] (when (not (js/isNaN v)) v)]))
         {:class "input h-40"}]]
      [monster-input-field
       "Speed"
       :speed
       monster
       "m-l-5 m-b-5 flex-grow-1"]
      ]
     [:div
      [:div.f-s-24.f-w-b "Abilities"]
      [:div.flex.w-100-p.flex-wrap
       (doall
        (map
         (fn [{:keys [key name]}]
           ^{:key key}
           [:div.flex-grow-1.m-b-20.m-r-5
            (let [simple-kw (-> key clojure.core/name keyword)]
              [labeled-dropdown
               name
               {:items (map
                        value-to-item
                        (range 1 31))
                :value (or (simple-kw monster) 10)
                :on-change #(dispatch [::monsters/set-monster-prop simple-kw (js/parseInt %)])}])])
         opt/abilities))]]
     [:div
      [:div.f-s-24.f-w-b "Saving Throws"]
      [:div.flex.w-100-p.flex-wrap
       (doall
        (map
         (fn [{:keys [key name]}]
           ^{:key key}
           [:div.flex-grow-1.m-b-20.m-r-5
            (let [simple-kw (-> key clojure.core/name keyword)]
              [labeled-dropdown
               name
               {:items (cons
                        {:title "-"}
                        (map
                         value-to-item
                         (range 0 18)))
                :value (get saving-throws simple-kw)
                :on-change #(dispatch [::monsters/set-monster-path-prop [:saving-throws simple-kw] (let [parsed (js/parseInt %)] (when (not (js/isNaN parsed)) parsed))])}])])
         opt/abilities))]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b. "Skills"]
      [:div.flex.w-100-p.flex-wrap
       (doall
        (map
         (fn [{:keys [key name]}]
           ^{:key key}
           [:div.m-b-20.m-r-5
            (let [simple-kw (-> key clojure.core/name keyword)]
              [labeled-dropdown
               name
               {:items (cons
                        {:title "-"}
                        (map
                         value-to-item
                         (range 1 21)))
                :value (get skills key 0)
                :on-change #(dispatch [::monsters/set-monster-path-prop [:skills key] (js/parseInt %)])}])])
         skills/skills))]
      [:div [option-damage-vulnerability monster ::monsters/toggle-monster-map-prop]]
      [:div [option-damage-resistance monster ::monsters/toggle-monster-map-prop]]
      [:div [option-damage-immunity monster ::monsters/toggle-monster-map-prop]]
      [:div [option-condition-immunity monster ::monsters/toggle-monster-map-prop]]
      [monster-input-field
       "Senses"
       :senses
       monster
       "m-l-5 m-b-5 flex-grow-1"]
      [:div.m-t-20 [option-languages monster ::monsters/toggle-monster-map-prop]]
      ]
     [:div.w-100-p.m-b-20
     [:div.f-s-24.f-w-b.w-20-p "Challenge Rating"
      [labeled-dropdown
       ""
       {:items (map
                 (fn [v]
                   {:title (if (< 0 v 1)
                             (clojure.core/str "1/" (/ 1 v))
                             v)
                    :value v})
                 @(subscribe [::monsters/challenge-ratings]))
        :value (or challenge 0)
        :on-change #(dispatch [::monsters/set-monster-prop :challenge (js/parseFloat %)])}]]]
     [:div.w-100-p
      [:div.f-s-24.f-w-b
       "Special Traits"]
      [textarea-field
       {:value (get monster :description)
        :on-change #(dispatch [::monsters/set-monster-prop :description %])}]]
     [:div.m-t-30
      [option-traits
       monster
       ::e5/add-monster-trait
       ::e5/edit-monster-trait-name
       ::e5/edit-monster-trait-type
       ::e5/edit-monster-trait-description
       ::e5/delete-monster-trait
       :title "Actions / Features"
       :button-title "Add Action / Feature"
       :types [{:title "Other"}
               {:title "Action"
                :value :action}
               {:title "Legendary Action"
                :value :legendary-action}]]]
     [:div.w-100-p.m-t-30
      [:div.f-s-20.f-w-b
       "Legendary Actions"]
      [textarea-field
       {:value (get-in monster [:legendary-actions :description])
        :on-change #(dispatch [::monsters/set-monster-path-prop [:legendary-actions :description] %])}]]
     ]))

(defn monster-builder-page []
  (builder-page "Monster" ::monsters/reset-monster ::monsters/save-monster monster-builder))
