(ns orcpub.dnd.e5.views.builders.item
  "Item (magic item) homebrew builder page — armor/weapon base selectors,
   attunement, ability/speed/save bonuses, damage/condition immunities,
   and name validation.

   Extracted from views.builders — imports shared field factories and
   builder infrastructure from the parent module."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [clojure.string :as s]
            [orcpub.common :as common]
            [orcpub.components :as comps]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.classes :as classes]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.damage-types :as damage-types]
            [orcpub.dnd.e5.armor :as armor]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.dnd.e5.weapons :as weapon]
            [orcpub.template :as template]
            [orcpub.dnd.e5.views.common
             :refer [base-builder-field make-event-handler
                     make-arg-event-handler labeled-dropdown
                     dropdown textarea-field]]
            [orcpub.dnd.e5.views
             :refer [labeled-checkbox two-columns-style
                     three-columns-style content-page
                     delete-item-handler]]
            [orcpub.dnd.e5.views.builders
             :refer [input-builder-field value-to-item
                     get-owner? deletion-modal-with]]))


;;; ─── Field helpers ─────────────────────────────────────────────────

(defn number-field
  "Integer-only input field — rejects non-digit input."
  [{:keys [value on-change]}]
  [comps/input-field
   :input
   value
   (fn [v]
     (on-change
      (when (re-matches #"\d+" v) (js/parseInt v))))
   {:class "input"
    :type :number}])

(defn attunement-value [attunement key name]
  [:div
   {:on-click (make-event-handler ::mi/toggle-attunement-value key)}
   [labeled-checkbox name ((set attunement) key)]])

(defn attunement-selector [attunement]
  (base-builder-field
   "Attunement"
   [:div
    [:div.flex.align-items-c.m-b-10
     {:on-click (make-event-handler ::mi/toggle-attunement)}
     (comps/checkbox attunement false)
     [:span.f-s-24.f-w-b.m-l-5 "Attunement"]]
    (when attunement
      [:div
       [labeled-checkbox "Any" (= #{:any} (set attunement))]
       [:div.flex.flex-wrap
        [:div.flex-grow-1
         (base-builder-field
          [:div.f-w-b.m-b-5 "Class"]
          [:div
           (doall
            (map
             (fn [{:keys [::template/key ::template/name]}]
               ^{:key key}
               [attunement-value attunement key name])
             (cons
              {::template/key :spellcaster
               ::template/name "Spellcaster"}
              @(subscribe [::classes/classes]))))])]
        [:div.flex-grow-1
         (base-builder-field
          [:div.f-w-b.m-b-5 "Alignment"]
          [:div
           [:div.m-b-5]
           (doall
            (map
             (fn [{:keys [key name]}]
               ^{:key key}
               [attunement-value attunement key name])
             (concat
              [{:name "Good"
                :key :good}
               {:name "Evil"
                :key :evil}]
              opt/alignments)))])]]])]))


;;; ─── Base type selectors ───────────────────────────────────────────

(defn base-armor-selector []
  (let [mobile? @(subscribe [:mobile?])]
    [:div.m-b-20
     [:div.main-text-color.m-b-10
      [:span.f-s-24.f-w-b "Base Armor"]]
     [:div.flex.flex-wrap
      [:div.flex-grow-1
       (base-builder-field
        [:div.f-w-b.m-b-5 "Armor Type"]
        [:div
         {:style (if mobile?
                   two-columns-style
                   three-columns-style)}
         (doall
          (map
           (fn [{:keys [:key :name]}]
             ^{:key key}
             [:div
              {:on-click (make-event-handler ::mi/toggle-subtype key)}
              [labeled-checkbox name @(subscribe [::mi/has-subtype? key])]])
           (concat
            [{:name "All"
              :key :all}]
            (map
             (fn [type]
               {:name (str "All " (clojure.core/name type))
                :key type})
             armor/armor-types)
            armor/armor)))])]]]))

(defn base-weapon-selector []
  (let [mobile? @(subscribe [:mobile?])
        other? @(subscribe [::mi/has-subtype? :other])
        versatile? @(subscribe [::mi/item-versatile?])
        melee-ranged @(subscribe [::mi/item-melee-ranged])]
    [:div.m-b-20
     [:div.main-text-color.m-b-10
      [:span.f-s-24.f-w-b "Base Weapon"]]
     [:div.flex.flex-wrap
      [:div.flex-grow-1
       (base-builder-field
        [:div.f-w-b.m-b-5 "Weapon Type"]
        [:div
         {:style (if mobile?
                   two-columns-style
                   three-columns-style)}
         (doall
          (map
           (fn [{:keys [key name]}]
             ^{:key key}
             [:div
              {:on-click (make-event-handler ::mi/toggle-subtype key)}
              [labeled-checkbox name @(subscribe [::mi/has-subtype? key])]])
           (concat
            [{:name "Custom" :key :other}
             {:name "All" :key :all}
             {:name "All Swords" :key :sword}
             {:name "All Axes" :key :axe}]
            (common/aloof-sort-by :name
              @(subscribe [::mi/custom-and-standard-weapons])
            )
            )))])]]
     (when other?
       [:div.main-text-color.m-b-10.m-t-10
        [:span.f-s-18.f-w-b "Base Weapon Details"]
        [:div.flex.flex-wrap.m-t-10
         [:div
          {:on-click (make-event-handler ::mi/toggle-item-finesse?)}
          [labeled-checkbox "Finesse?" @(subscribe [::mi/item-finesse?])]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-versatile?)}
          [labeled-checkbox "Versatile?" versatile?]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-reach?)}
          [labeled-checkbox "Reach?" @(subscribe [::mi/item-reach?])]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-two-handed?)}
          [labeled-checkbox "Two-Handed?" @(subscribe [::mi/item-two-handed?])]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-thrown?)}
          [labeled-checkbox "Thrown?" @(subscribe [::mi/item-thrown?])]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-heavy?)}
          [labeled-checkbox "Heavy?" @(subscribe [::mi/item-heavy?])]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-light?)}
          [labeled-checkbox "Light?" @(subscribe [::mi/item-light?])]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-ammunition?)}
          [labeled-checkbox "Ammunition?" @(subscribe [::mi/item-ammunition?])]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-special?)}
          [labeled-checkbox "Special?" @(subscribe [::mi/item-special?])]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-loading?)}
          [labeled-checkbox "Loading?" @(subscribe [::mi/item-loading?])]]]
        [:div.flex.flex-wrap
         [:div.m-t-10
          [labeled-dropdown
           "Damage Die Number"
           {:items (map
                    value-to-item
                    (range 1 10))
            :value @(subscribe [::mi/item-damage-die-count])
            :on-change (make-arg-event-handler ::mi/set-item-damage-die-count js/parseInt)}]]
         [:div.m-l-10.m-t-10
          [labeled-dropdown
           "Damage Die"
           {:items (map
                    (fn [v]
                      {:value v
                       :title (str "d" v)})
                    [4 6 8 10 12 20 100])
            :value @(subscribe [::mi/item-damage-die])
            :on-change (make-arg-event-handler ::mi/set-item-damage-die js/parseInt)}]]
         (when versatile?
           [:div.m-l-10.m-t-10
            [labeled-dropdown
             "Versatile Damage Die Number"
             {:items (map
                      value-to-item
                      (range 1 10))
              :value @(subscribe [::mi/item-versatile-damage-die-count])
              :on-change (make-arg-event-handler ::mi/set-item-versatile-damage-die-count js/parseInt)}]])
         (when versatile?
           [:div.m-l-10.m-t-10
            [labeled-dropdown
             "Versatile Damage Die"
             {:items (map
                      (fn [v]
                        {:value v
                         :title (str "d" v)})
                      [4 6 8 10 12 20 100])
              :value @(subscribe [::mi/item-versatile-damage-die])
              :on-change (make-arg-event-handler ::mi/set-item-versatile-damage-die js/parseInt)}]])
         [:div.m-l-10.m-t-10
          [labeled-dropdown
           "Simple / Martial?"
           {:items [{:value :simple
                     :title "Simple"}
                    {:value :martial
                     :title "Martial"}]
            :value @(subscribe [::mi/item-weapon-type])
            :on-change (make-arg-event-handler ::mi/set-item-weapon-type)}]]
         [:div.m-l-10.m-t-10
          [labeled-dropdown
           "Melee / Ranged?"
           {:items [{:value :melee
                     :title "Melee"}
                    {:value :ranged
                     :title "Ranged"}]
            :value melee-ranged
            :on-change (make-arg-event-handler ::mi/set-item-melee-ranged)}]]
         (when (= :ranged melee-ranged)
           [:div.m-l-10.m-t-10
            [:div.f-w-b.m-b-5 "Range Min"]
            [number-field
             {:value @(subscribe [::mi/item-range-min])
              :on-change (make-arg-event-handler ::mi/set-item-range-min)}]])
         (when (= :ranged melee-ranged)
           [:div.m-l-10.m-t-10
            [:div.f-w-b.m-b-5 "Range Max"]
            [number-field
             {:value @(subscribe [::mi/item-range-max])
              :on-change (make-arg-event-handler ::mi/set-item-range-max)}]])
         [:div.m-l-10.m-t-10
          [labeled-dropdown
           "Damage Type"
           {:items (map
                    (fn [type]
                      {:value type
                       :title (name type)})
                    damage-types/damage-types)
            :value @(subscribe [::mi/item-damage-type])
            :on-change (make-arg-event-handler ::mi/set-item-damage-type)
            }]]]]
            )]))


;;; ─── Bonus / modifier sections ─────────────────────────────────────

(defn item-ability-bonuses []
  (base-builder-field
   [:div.f-w-b.m-b-5 "Ability Bonus"]
   [:div
    (doall
     (map
      (fn [ability-kw]
        ^{:key ability-kw}
        [:div.flex.align-items-c
         [:div.w-40 (s/upper-case (name ability-kw))]
         [:div
          [dropdown
           {:value @(subscribe [::mi/ability-mod-type ability-kw])
            :on-change #(dispatch [::mi/set-ability-mod-type ability-kw %])
            :items [{:value :becomes-at-least
                     :title "Becomes At Least"}
                    {:value :increases-by
                     :title "Increases By"}]}]]
         [:div.w-60.m-l-5
          [number-field
           {:value @(subscribe [::mi/ability-mod-value ability-kw])
            :on-change #(dispatch [::mi/set-ability-mod-value ability-kw %])}]]])
      char/ability-keys))]))

(defn item-saving-throw-bonuses []
  (base-builder-field
   [:div.f-w-b.m-b-5 "Saving Throw Bonus"]
   [:div
    (doall
     (map
      (fn [ability-kw]
        ^{:key ability-kw}
        [:div.flex.align-items-c
         [:div.w-40 (str (s/upper-case (name ability-kw)) " Save")]
         [:div
          [dropdown
           {:value :increases-by
            :items [{:value :increases-by
                     :title "Increases By"}]}]]
         [:div.w-60.m-l-5
          [number-field
           {:value @(subscribe [::mi/save-mod-value ability-kw])
            :on-change #(dispatch [::mi/set-save-mod-value ability-kw %])}]]])
      char/ability-keys))]))

(defn item-speed-bonuses []
  (base-builder-field
   [:div.f-w-b.m-b-5 "Speed Bonus"]
   [:div
    (doall
     (map
      (fn [type-kw]
        (let [speed-mod-type @(subscribe [::mi/speed-mod-type type-kw])]
          ^{:key type-kw}
          [:div.flex.align-items-c
           [:div.w-100 (common/safe-capitalize-kw type-kw)]
           [:div
            [dropdown
             {:value speed-mod-type
              :on-change #(dispatch [::mi/set-speed-mod-type type-kw %])
              :items (let [items [{:value :becomes-at-least
                                   :title "Becomes At Least"}
                                  {:value :increases-by
                                   :title "Increases By"}]]
                       (if (= :speed type-kw)
                         items
                         (conj items
                               {:value :equals-walking-speed
                                :title "Equals Walking Speed"})))}]]
           (when (not= :equals-walking-speed speed-mod-type)
             [:div.w-60.m-l-5
              [number-field
               {:value @(subscribe [::mi/speed-mod-value type-kw])
                :on-change #(dispatch [::mi/set-speed-mod-value type-kw %])}]])]))
      [:speed :flying-speed :swimming-speed :climbing-speed]))]))

(defn item-modifier-toggles [title item-kws toggle-event has-sub]
  (base-builder-field
   [:div.f-w-b.m-b-5 title]
   [:div
    (doall
     (map
      (fn [type-kw]
        ^{:key type-kw}
        [:div
         {:on-click #(dispatch [toggle-event type-kw])}
         [labeled-checkbox (common/safe-capitalize-kw type-kw) @(subscribe [has-sub type-kw])]])
      item-kws))]))

(defn item-damage-resistances []
  [item-modifier-toggles
   "Damage Resistances"
   opt/damage-types
   ::mi/toggle-damage-resistance
   ::mi/has-damage-resistance?])

(defn item-damage-vulnerabilities []
  [item-modifier-toggles
   "Damage Vulnerabilities"
   opt/damage-types
   ::mi/toggle-damage-vulnerability
   ::mi/has-damage-vulnerability?])

(defn item-damage-immunities []
  [item-modifier-toggles
   "Damage Immunities"
   opt/damage-types
   ::mi/toggle-damage-immunity
   ::mi/has-damage-immunity?])

(defn item-condition-immunities []
  [item-modifier-toggles
   "Condition Immunities"
   (keys opt/conditions-map)
   ::mi/toggle-condition-immunity
   ::mi/has-condition-immunity?])

(defn item-bonuses [{:keys [::mi/magical-damage-bonus
                            ::mi/magical-attack-bonus
                            ::mi/magical-ac-bonus
                            ::mi/type] :as item}]
  [:div.m-b-20
   [:div.m-b-10
    [:span.f-s-24.f-w-b "Item Properties"]]
   [:div.flex.m-b-20
    (when (= type :weapon)
      [:div
       [:div.f-w-b.m-b-5 "Magical Damage Bonus"]
       [number-field
        {:value magical-damage-bonus
         :on-change #(dispatch [::mi/set-item-damage-bonus %])}]])
    (when (= type :weapon)
      [:div.m-l-20.m-r-20
       [:div.f-w-b.m-b-5 "Magical Attack Bonus"]
       [number-field
        {:value magical-attack-bonus
         :on-change #(dispatch [::mi/set-item-attack-bonus %])}]])
    [:div
     [:div.f-w-b.m-b-5 "Magical AC Bonus"]
     [number-field
      {:value magical-ac-bonus
       :on-change #(dispatch [::mi/set-item-ac-bonus %])}]]]
   [:div.flex.flex-wrap.m-b-20
    [:div.flex-grow-1
     [item-ability-bonuses]]
    [:div.flex-grow-1
     [item-saving-throw-bonuses]]
    [:div.flex-grow-1
     [item-speed-bonuses]]]
   [:div.flex.flex-wrap
    [:div.flex-grow-1
     [item-damage-resistances]]
    [:div.flex-grow-1
     [item-damage-vulnerabilities]]
    [:div.flex-grow-1
     [item-damage-immunities]]
    [:div.flex-grow-1
     [item-condition-immunities]]]])


;;; ─── Item builder + page wrapper ───────────────────────────────────

(defn validate-name
  "Returns an error message string if name is invalid, nil otherwise."
  [name]
  (when (and (some? name) (not (common/starts-with-letter? (str name))))
    "Name must start with a letter"))

(defn valid-wel [name]
  (when-let [messages (validate-name name)]
  [:span {:required true
          :id "verify-name"
          :class "warntiptext"
          :data-tip messages
          :aria-live "polite"
          }
  messages]
  )
  )

(defn item-builder
  "Homebrew magic item builder form — name, type, rarity, description,
   armor/weapon config, attunement, and stat bonuses."
  []
  (let [{:keys [::mi/name ::mi/type ::mi/rarity ::mi/description ::mi/attunement] :as item}
        @(subscribe [::mi/builder-item])
        item-types @(subscribe [::mi/item-types])
        item-rarities @(subscribe [::mi/rarities])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [:div.flex-grow-1.m-b-20.warntip
       [input-builder-field
          "Item Name" ;:name
          name
          #(dispatch [::mi/set-item-name %])
          {:class "input h-40"}]
       (when (seq name)
         (valid-wel name))
       #_(when-let [messages (validate-name name)]
           (validation-messages messages))
       ]
      [:div.flex-grow-1.m-l-5
       (base-builder-field
        "Type"
        [:div.m-t-5
         [dropdown
          {:items (map
                   (fn [type-kw]
                     {:value type-kw
                      :title (common/kw-to-name type-kw)})
                   item-types)
           :value type
           :on-change #(dispatch [::mi/set-item-type %])}]])]
      [:div.flex-grow-1.m-l-5
       (base-builder-field
        "Rarity"
        [:div.m-t-5
         [dropdown
          {:items (map
                   (fn [rarity]
                     {:value rarity
                      :title (clojure.core/name rarity)})
                   item-rarities)
           :value rarity
           :on-change #(dispatch [::mi/set-item-rarity %])}]])]]
     [:div.m-b-40 (base-builder-field "Description" [textarea-field
                                                     {:value description
                                                      :on-change #(dispatch [::mi/set-item-description %])}])]
     (when (= :armor type)
       [:div.m-b-40 [base-armor-selector]])
     (when (= :weapon type)
       [:div.m-b-40 [base-weapon-selector]])
     [:div.m-b-40
      [attunement-selector attunement]]
     [item-bonuses item]]))


(defn item-builder-buttons [item-key item]
  (let [owner? (get-owner? item-key)
        base-buttons [{:title "New Item"
                       :icon "plus"
                       :on-click #(dispatch [::mi/reset-item])}
                      {:title "Save to Browser Storage"
                       :icon "save"
                       :on-click #(dispatch [::mi/save-item])}]
        ]
    (if (and item-key owner?) ;Show if we have an item key and the user owns it
      (conj base-buttons {:title "Delete"
                          :icon "trash"
                          :on-click (make-event-handler ::mi/show-delete-confirmation item-key)})
      base-buttons)
    ))

(defn item-builder-page []
  (let [item @(subscribe [::mi/builder-item])
        item-key (:db/id item)
        buttons (item-builder-buttons item-key item)]
    [content-page
     "Item Builder"
     buttons
     [deletion-modal-with
      item-builder
      item-key]
     ]))
