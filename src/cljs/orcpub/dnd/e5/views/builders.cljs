(ns orcpub.dnd.e5.views.builders
  "Builder implementations and builder-specific shared infrastructure.

   Contains 14 builder page components (spell, monster, class, race,
   background, feat, item, etc.), their shared option-* components,
   field wrappers, and plugin-datalist.  Depends on views.cljs for
   content-page and display utilities (one-way); never required by
   views.cljs.  core.cljs routes to builder pages directly."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [clojure.string :as s]
            [orcpub.common :as common]
            [orcpub.components :as comps]
            [orcpub.entity-spec :as es]
            [orcpub.route-map :as routes]
            [orcpub.dice :as dice]
            [orcpub.template :as template]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.backgrounds :as bg]
            [orcpub.dnd.e5.languages :as langs]
            [orcpub.dnd.e5.selections :as selections]
            [orcpub.dnd.e5.races :as races]
            [orcpub.dnd.e5.classes :as classes]
            [orcpub.dnd.e5.feats :as feats]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.monsters :as monsters]

            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.damage-types :as damage-types]
            [orcpub.dnd.e5.skills :as skills]
            [orcpub.dnd.e5.equipment :as equip]
            [orcpub.dnd.e5.weapons :as weapon]
            [orcpub.dnd.e5.armor :as armor]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.dnd.e5.views.common
             :refer [base-builder-field builder-field textarea-field
                     make-event-handler make-arg-event-handler
                     labeled-dropdown dropdown]]
            [orcpub.dnd.e5.views
             :refer [content-page obj-to-item delete-item-handler
                     two-columns-style three-columns-style
                     labeled-checkbox]]))


(defn input-builder-field [name value on-change attrs]
  [builder-field :input name value on-change attrs])

(defn number-field [{:keys [value on-change]}]
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

(defn value-to-item [v]
  {:title v
   :value v})

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

(defn builder-input-field [title prop item prop-event & [class-names type]]
  [:div.flex-grow-1
   {:class class-names
    :name prop}
   [input-builder-field
    [:span.f-w-b title]
    (prop item)
    #(dispatch [prop-event prop %])
    {:class "input h-40"
     :type type}]])

#_(defn item-input-field [title prop item & [class-names]]
  (builder-input-field title prop item ::mi/set-item-name class-names))

(defn spell-input-field [title prop spell & [class-names]]
  (builder-input-field title prop spell ::spells/set-spell-prop class-names))

(defn monster-input-field [title prop monster & [class-names type]]
  (builder-input-field title prop monster ::monsters/set-monster-prop class-names type))

(defn language-input-field [title prop language & [class-names]]
  (builder-input-field title prop language ::langs/set-language-prop class-names))

(defn invocation-input-field [title prop invocation & [class-names]]
  (builder-input-field title prop invocation ::classes/set-invocation-prop class-names))

(defn boon-input-field [title prop boon & [class-names]]
  (builder-input-field title prop boon ::classes/set-boon-prop class-names))

(defn selection-input-field [title prop selection & [class-names]]
  (builder-input-field title prop selection ::selections/set-selection-prop class-names))

(defn background-input-field [title prop bg & [class-names]]
  (builder-input-field title prop bg ::bg/set-background-prop class-names))

(defn race-input-field [title prop race & [class-names]]
  (builder-input-field title prop race ::races/set-race-prop class-names))

(defn subrace-input-field [title prop subrace & [class-names]]
  (builder-input-field title prop subrace ::races/set-subrace-prop class-names))

(defn subclass-input-field [title prop subclass & [class-names]]
  (builder-input-field title prop subclass ::classes/set-subclass-prop class-names))

(defn class-input-field [title prop class & [class-names]]
  (builder-input-field title prop class ::classes/set-class-prop class-names))

(defn feat-input-field [title prop feat & [class-names]]
  (builder-input-field title prop feat ::feats/set-feat-prop class-names))

(defn component-checkbox [component spell]
  [:span.m-r-20.m-b-10
   [comps/labeled-checkbox
    (common/kw-to-name component)
    (get-in spell [:components component])
    false
    #(dispatch [::spells/toggle-component component])]])

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

(def option-source-name-label
  [:span
   [:span "Option Source Name"]
   [:span.f-w-n.f-s-12.m-l-5 "(e.g. "
    [:span.i "Player's Manual"]
    [:span ", "]
    [:span.i "Hodor's Guide to Hodors"]
    [:span ")"]]])

(defn option-proficiency-choice [title
                                 proficiency-choice-key
                                 proficiency-options
                                 option
                                 set-path-prop-event
                                 toggle-path-prop-event]
  [:div.m-b-20
   [:div.f-s-24.f-w-b.m-b-20 title]
   [:div.m-b-10
    [labeled-dropdown
     "Choose"
     {:items (map
              value-to-item
              (range 1 6))
      :value (get-in option [:profs proficiency-choice-key :choose] 1)
      :on-change #(dispatch [set-path-prop-event [:profs proficiency-choice-key :choose] (js/parseInt %)])}]]
   [:div.f-s-18.f-w-b.m-b-20 "Options"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [{:keys [name key]}]
        ^{:key key}
        [:span.m-r-20.m-b-10
         [comps/labeled-checkbox
          name
          (get-in option [:profs proficiency-choice-key :options key])
          false
          #(dispatch [toggle-path-prop-event [:profs proficiency-choice-key :options key]])]])
      proficiency-options))]])

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

(def option-skill-expertise-choice
  (partial option-proficiency-choice
           "Skill Expertise (Double Proficiency) Choice"
           :skill-expertise-options
           skills/skills))

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

(def option-skill-proficiency-choice
  (partial option-proficiency-choice
           "Skill Proficiency Choice"
           :skill-options
           skills/skills))

(defn option-skill-proficiency [option toggle-event]
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
          (get-in option [:props :skill-prof key])
          false
          #(dispatch [toggle-event :skill-prof key])]])
      skills/skills))]])

(defn option-languages [option toggle-map-prop-event]
  (let [languages @(subscribe [::langs/languages])]
    [:div.m-b-20
     [:div.f-s-24.f-w-b.m-b-20 "Languages"]
     [:div.flex.flex-wrap
      (doall
       (map
        (fn [{:keys [name key]}]
          ^{:key key}
          [:span.m-r-20.m-b-10
           [comps/labeled-checkbox
            name
            (get-in option [:props :language key])
            false
            #(dispatch [toggle-map-prop-event :language key])]])
        (sort-by
         :name
         languages)))]
     [:div.pointer.m-t-10
      [:span.bg-lighter.p-5
       {:on-click #(dispatch [:route routes/dnd-e5-language-builder-page-route])}
       [:i.fa.fa-plus]
       [:span.orange.underline.m-l-5 "Add Language"]]]]))

(defn option-skill-proficiency-or-expertise [option toggle-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-20 "Skill Proficiency or Expertise"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [{:keys [name key]}]
        ^{:key key}
        [:span.m-r-20.m-b-10
         [comps/labeled-checkbox
          name
          (get-in option [:props :skill-prof-or-expertise key])
          false
          #(dispatch [toggle-event :skill-prof-or-expertise key])]])
      skills/skills))]])

(defn option-tool-proficiency [option toggle-path-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-20 "Tool Proficiency"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [{:keys [name key]}]
        ^{:key key}
        [:span.m-r-20.m-b-10
         [comps/labeled-checkbox
          name
          (get-in option [:profs :tool key])
          false
          #(dispatch [toggle-path-prop-event [:profs :tool key]])]])
      equip/tools))]])

(defn option-tool-proficiency-or-expertise [option toggle-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-20 "Tool Proficiency or Expertise"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [{:keys [name key]}]
        ^{:key key}
        [:span.m-r-20.m-b-10
         [comps/labeled-checkbox
          name
          (get-in option [:props :tool-prof-or-expertise key])
          false
          #(dispatch [toggle-event :tool-prof-or-expertise key])]])
      equip/tools))]])

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

(defn feat-prereqs [feat]
  [:div.m-b-20
   [:div.f-s-24.f-w-b.m-b-10 "Prerequisites"]
   [:div.flex.flex-wrap
    [:div.m-r-20.m-b-10
     [comps/labeled-checkbox
      "The ability to cast at least one spell"
      (get-in feat [:prereqs :spellcasting])
      false
      #(dispatch [::feats/toggle-spellcasting-prereq])]]
    [:div
     (doall
      (map
       (fn [{:keys [name key]}]
         ^{:key key}
         [:div.m-r-20.m-b-10
          [comps/labeled-checkbox
           (str name " 13 or higher")
           (get-in feat [:prereqs key])
           false
           #(dispatch [::feats/toggle-ability-prereq key])]])
       opt/abilities))]
    [:div
     (doall
      (map
       (fn [key]
         ^{:key key}
         [:div.m-r-20.m-b-10
          (let [prop-key key]
            [comps/labeled-checkbox
             (str "Proficiency with " (name key) " armor")
             (get-in feat [:prereqs prop-key])
             false
             #(dispatch [::feats/toggle-ability-prereq prop-key])])])
       armor/armor-types))]
    [:div
     (doall
      (map
       (fn [{:keys [key name] :as race}]
         ^{:key key}
         [:div.m-r-20.m-b-10
          [comps/labeled-checkbox
           (str name " race")
           (get-in feat [:path-prereqs :race key])
           false
           #(dispatch [::feats/toggle-path-prereq [:race key]])]])
       @(subscribe [::races/races])))]]])

(defn feat-ability-increase-options [feat]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Ability Increase Options"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [{:keys [name key]}]
        ^{:key key}
        [:div.m-r-20.m-b-10
         [comps/labeled-checkbox
          name
          (get-in feat [:ability-increases key])
          false
          #(dispatch [::feats/toggle-feat-ability-increase key])]])
      opt/abilities))]
   [:div.m-r-20.m-b-10
    [comps/labeled-checkbox
     "You also gain proficiency in saving throws with the above chosen abilities"
     (get-in feat [:ability-increases :saves?])
     false
     #(dispatch [::feats/toggle-feat-ability-increase :saves?])]]
   [:div (let [increases (:ability-increases feat)
               non-save (disj increases :saves?)]
           (when (seq non-save)
             (str "= \"Increase your "
                  (common/list-print
                   (map
                    (comp :name opt/abilities-map)
                    non-save)
                   "or")
                  " score by 1, to a maximum of 20."
                  (when (increases :saves?)
                    " You gain proficiency in the saves using the chosen ability.\""))))]])

(defn feat-skill-proficiency [feat]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Skill or Tool Proficiency"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [num]
        ^{:key num}
        [:div.m-r-20.m-b-10
         (let [kw :skill-tool-choice]
           [comps/labeled-checkbox
            (str "You gain proficiency in " num " skills or tools of your choice")
            (= num (get-in feat [:props kw]))
            false
            #(dispatch [::feats/toggle-feat-value-prop kw num])])])
      (range 1 4)))]])

(defn feat-weapon-proficiency [feat]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Weapon Proficiency"]
   [:div.flex.flex-wrap
    [:div.m-r-20.m-b-10
     (let [kw :improvised-weapons-prof]
       [comps/labeled-checkbox
        "You gain proficiency with improvised weapons"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]
    (doall
     (map
      (fn [num]
        ^{:key num}
        [:div.m-r-20.m-b-10
         (let [kw :weapon-prof-choice]
           [comps/labeled-checkbox
            (str "You gain proficiency with " num " weapons of your choice")
            (= num (get-in feat [:props kw]))
            false
            #(dispatch [::feats/toggle-feat-value-prop kw num])])])
      (range 3 5)))]])

(defn option-armor-proficiency [option toggle-map-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Armor Proficiency"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [armor-type]
        ^{:key armor-type}
        [:div.m-r-20.m-b-10
         (let [kw :armor-prof]
           [comps/labeled-checkbox
            (str "You gain proficiency with " (name armor-type) (when (not= armor-type :shields) " armor"))
            (get-in option [:props kw armor-type])
            false
            #(dispatch [toggle-map-prop-event kw armor-type])])])
      (conj armor/armor-types :shields)))]])

(defn feat-armor-proficiency [feat]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Armor Proficiency"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [armor-type]
        ^{:key armor-type}
        [:div.m-r-20.m-b-10
         (let [kw :armor-prof]
           [comps/labeled-checkbox
            (str "You gain proficiency with " (name armor-type) (when (not= armor-type :shields) " armor"))
            (get-in feat [:props kw armor-type])
            false
            #(dispatch [::feats/toggle-feat-map-prop kw armor-type])])])
      (conj armor/armor-types :shields)))
    [:div.m-r-20.m-b-10
     (let [kw :medium-armor-stealth]
       [comps/labeled-checkbox
        "Wearing medium armor doesn't give disadvantage on Stealth checks"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]
    [:div.m-r-20.m-b-10
     (let [kw :medium-armor-max-dex-3]
       [comps/labeled-checkbox
        "When wearing medium armor, you can add 3 to your AC if your Dexterity is 16+"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]]])

(defn option-hps [option toggle-value-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Hit Points"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [num]
        ^{:key num}
        [:div.m-r-20.m-b-10
         (let [kw :max-hp-bonus]
           [comps/labeled-checkbox
            (str "Your hit point maximum increases by " num " for each of your levels")
            (= (get-in option [:props kw]) num)
            false
            #(dispatch [toggle-value-prop-event kw num])])])
      (range 1 3)))]])

(defn feat-hps [feat]
  (option-hps feat ::feats/toggle-feat-value-prop))

(defn feat-speed-bonuses [feat]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Speed Bonuses"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [v]
        ^{:key v}
        [:div.m-r-20.m-b-10
         (let [kw :speed]
           [comps/labeled-checkbox
            (str "Your speed is increased by " v " ft.")
            (= v (get-in feat [:props kw]))
            false
            #(dispatch [::feats/toggle-feat-value-prop kw v])])])
      (range 5 20 5)))]])

(defn feat-initiative-bonuses [feat]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Initiative Bonuses"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [v]
        ^{:key v}
        [:div.m-r-20.m-b-10
         (let [kw :initiative]
           [comps/labeled-checkbox
            (str "You gain a +" v " bonus to initiative")
            (= v (get-in feat [:props kw]))
            false
            #(dispatch [::feats/toggle-feat-value-prop kw v])])])
      (range 1 6)))]])

(defn feat-languages [feat]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Languages"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [v]
        ^{:key v}
        [:div.m-r-20.m-b-10
         (let [kw :language-choice]
           [comps/labeled-checkbox
            (str "You learn " v " languages of your choice.")
            (= v (get-in feat [:props kw]))
            false
            #(dispatch [::feats/toggle-feat-value-prop kw v])])])
      (range 1 4)))]])

(defn option-damage-resistance [option toggle-map-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Damage Resistances"]
   (let [kw :damage-resistance]
     [:div.flex.flex-wrap
      [:div.m-r-20.m-b-10
       [comps/labeled-checkbox
        "Resistance to damage from traps"
        (get-in option [:props kw :traps])
        false
        #(dispatch [toggle-map-prop-event kw :traps])]]
      (doall
       (map
        (fn [damage-type]
          ^{:key damage-type}
          [:div.m-r-20.m-b-10
           [comps/labeled-checkbox
            (str "Resistance to " (name damage-type) " damage")
            (get-in option [:props kw damage-type])
            false
            #(dispatch [toggle-map-prop-event kw damage-type])]])
        opt/damage-types))])])

(defn option-damage-immunity [option toggle-map-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Damage Immunities"]
   (let [kw :damage-immunity]
     [:div.flex.flex-wrap
      (doall
       (map
        (fn [damage-type]
          ^{:key damage-type}
          [:div.m-r-20.m-b-10
           [comps/labeled-checkbox
            (str "Immunity to " (name damage-type) " damage")
            (get-in option [:props kw damage-type])
            false
            #(dispatch [toggle-map-prop-event kw damage-type])]])
        opt/damage-types))])])

(defn option-damage-vulnerability [option toggle-map-prop-event]
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

(defn option-weapon-proficiency [option toggle-map-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Weapon Proficiencies"]
   (let [kw :weapon-prof]
     [:div.flex.flex-wrap
      (doall
       (concat
        (map
         (fn [weapon-type]
           ^{:key weapon-type}
           [:div.m-r-20.m-b-10
            [comps/labeled-checkbox
             (str "All " (common/safe-capitalize-kw weapon-type) " Weapons")
             (get-in option [:props kw weapon-type])
             false
             #(dispatch [toggle-map-prop-event kw weapon-type])]])
         [:simple :martial])
        (map
         (fn [{:keys [key name]}]
           ^{:key key}
           [:div.m-r-20.m-b-10
            [comps/labeled-checkbox
             name
             (get-in option [:props kw key])
             false
             #(dispatch [toggle-map-prop-event kw key])]])
         @(subscribe [::mi/custom-and-standard-weapons]))))])])

(defn option-traits [option
                     add-trait-event
                     edit-trait-name-event
                     edit-trait-type-event
                     edit-trait-description-event
                     delete-trait-event
                     & {:keys [edit-trait-level-event types title button-title]}]
  [:div.m-b-20
   [:div.p-t-10.p-b-10.f-w-b.flex.justify-cont-s-b.align-items-c
    [:div.f-s-24.f-w-b.m-b-10 (or title "Features/Traits")]
    [:div
     [:button.form-button.m-l-5
      {:on-click (make-event-handler add-trait-event)}
      (or button-title "add feature / trait")]]]
   [:div
    (if (seq (:traits option))
      (doall
       (map-indexed
        (fn [i {:keys [name type description level]}]
          ^{:key i}
          [:div.m-b-30
           [:div.flex.align-items-end.m-b-10
            [:div.flex-grow-1
             [input-builder-field
              [:span.f-w-b "Name"]
              name
              #(dispatch [edit-trait-name-event i %])
              {:class "input h-40"}]]
            (when types
              [:div.flex-grow-1.m-l-5
               [labeled-dropdown
                "Type"
                {:items types
                 :value type
                 :on-change #(dispatch [edit-trait-type-event i (keyword %)])}]])
            (when edit-trait-level-event
              [:div.m-l-5
               [labeled-dropdown
                "Unlocked at Level"
                {:items (map
                         (fn [lvl]
                           {:title lvl
                            :value lvl})
                         (range 1 21))
                 :value level
                 :on-change #(dispatch [edit-trait-level-event i (js/parseInt %)])}]])
            [:div
             [:button.form-button.m-l-5
              {:on-click #(dispatch [delete-trait-event i])}
              "delete"]]]
           [:div.w-100-p
            [:div.f-w-b
             "Description"]
            [textarea-field
             {:value description
              :on-change #(dispatch [edit-trait-description-event i %])}]]])
        (:traits option)))
      [:div.p-10.bg-lighter.pointer
       {:on-click #(dispatch [add-trait-event])}
       [:span "There are currently no features/traits, click "]
       [:span.orange.underline "here"]
       [:span " or on the button above to add one."]])]
   ;; Mirror add button at bottom for long trait lists
   [:div.p-t-10.flex.justify-cont-end
    [:button.form-button.m-l-5
     {:on-click (make-event-handler add-trait-event)}
     (or button-title "add feature / trait")]]])

(defn option-saving-throw-advantages [option toggle-map-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Saving Throw Advantage"]
   (let [kw :saving-throw-advantage]
     [:div.flex.flex-wrap
      (doall
       (map
        (fn [{:keys [name key]}]
          ^{:key key}
          [:div.m-r-20.m-b-10
           [comps/labeled-checkbox
            (str "You have advantage on saving throws against being " name)
            (get-in option [:props kw key])
            false
            #(dispatch [toggle-map-prop-event kw key])]])
        opt/conditions))])])

(defn feat-damage-resistance [feat]
  (option-damage-resistance feat ::feats/toggle-feat-map-prop))


(defn feat-misc-modifiers [feat]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Misc. Modifiers"]
   [:div.flex.flex-wrap
    [:div.m-r-20.m-b-10
     (let [kw :two-weapon-ac-1]
       [comps/labeled-checkbox
        "+1 AC Bonus while wielding two melee weapons"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]
    [:div.m-r-20.m-b-10
     (let [kw :two-weapon-any-one-handed]
       [comps/labeled-checkbox
        "You can use two-weapon fighting with any one-handed melee weapon"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]
    [:div.m-r-20.m-b-10
     (let [kw :saving-throw-advantage-traps]
       [comps/labeled-checkbox
        "Advantage on saving throws against traps"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]
    [:div.m-r-20.m-b-10
     (let [kw :passive-perception-5]
       [comps/labeled-checkbox
        "You gain a +5 to your passive Perception"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]
    [:div.m-r-20.m-b-10
     (let [kw :passive-investigation-5]
       [comps/labeled-checkbox
        "You gain a +5 to your passive Investigation"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]]])

(defn feat-spellcasting [feat]
  [:div.m-b-30
   [:div.f-s-18.f-w-b.m-b-10 "Spellcasting"]
   [:div.flex.flex-wrap
    [:div.m-r-20.m-b-10
     (let [kw :magic-novice]
       [comps/labeled-checkbox
        "Choose a class, gain (2) cantrips and (1) 1st-level spell from that class's spell list"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]
    [:div.m-r-20.m-b-10
     (let [kw :ritual-casting]
       [comps/labeled-checkbox
        "Choose a class, gain (2) 1st-level ritual spells from that class's spell list"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]
    [:div.m-r-20.m-b-10
     (let [kw :attack-spell]
       [comps/labeled-checkbox
        "Choose a class, gain a cantrip requiring an attack roll from that class's spell list"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]]])


(defn get-plugin-names []
  (let [plugins @(subscribe [::e5/plugins])]
    (map (fn [plugin-name]
           {:value plugin-name
            :title plugin-name})
         (sort-by s/lower-case (keys plugins)))))

;;; Create a datalist element and load the plugin names
(defn plugin-datalist [label plugin-val dispatch-event] 
  (let [selected-value (atom (or (:option-pack plugin-val) "")) ;TODO: reframe functions may or may not help handle this more efficiently
        ]
    (fn []
      [:div.flex-grow-1
       {:class "m-l-5 m-b-20"
        :name "option-pack"}
       [:div.f-w-b.m-b-5 label]
       [:input {:type "text"
                :list "plugins-list"
                :name "plugins-choice"
                :id "plugins-choice"
                :class "input h-40"
                :placeholder "Default Option Source"
                :value @selected-value
                :onChange #(do
                             ; When user types in input field:
                             ; 1. Update the local state of the component with the new value
                             ; 2. Dispatch event to update the state of the entire app
                             ;    w/ new value to save to app db (can be used elsewhere in app)
                             (reset! selected-value (-> % .-target .-value))
                             (dispatch [dispatch-event :option-pack @selected-value])
                             )
                }]
       [:datalist {:id "plugins-list" :class "width-100-p"}
        (for [{:keys [title value]} (get-plugin-names)]
          [:option {:key title :value value}])]
      ]
       )))

(defn feat-builder []
  (let [feat @(subscribe [::feats/builder-item])
        plugins @(subscribe [::e5/plugins])]
    [:div.p-20.main-text-color
     [:div.m-b-20.flex.flex-wrap
      [feat-input-field
       "Name"
       :name
       feat]
      [plugin-datalist 
         option-source-name-label 
         feat
         ::feats/set-feat-prop
       ]
       ;"m-l-5 m-b-20"]
      [:div.w-100-p
       [:div.f-w-b
        "Description"]
       [textarea-field
        {:value (get feat :description)
         :on-change #(dispatch [::feats/set-feat-prop :description %])}]]]
     [:div [feat-prereqs feat]]
     [:div.f-s-24.f-w-b.m-b-10 "Modifiers"]
     [:div [feat-ability-increase-options feat]]
     [:div [feat-skill-proficiency feat]]
     [:div [feat-languages feat]]
     [:div [feat-weapon-proficiency feat]]
     [:div [feat-armor-proficiency feat]]
     [:div [feat-hps feat]]
     [:div [feat-damage-resistance feat]]
     [:div [feat-speed-bonuses feat]]
     [:div [feat-initiative-bonuses feat]]
     [:div [feat-misc-modifiers feat]]
     [:div [feat-spellcasting feat]]
     [:div [option-skill-proficiency-or-expertise feat ::feats/toggle-feat-map-prop]]
     [:div [option-tool-proficiency-or-expertise feat ::feats/toggle-feat-map-prop]]]))


(defn spell-selector [index spell-cfg value-change-event]
  (let [spells @(subscribe [::spells/spells-for-level (or (:level spell-cfg) 0)])
        spells-map @(subscribe [::spells/spells-map])
        spell-kw (get spell-cfg :key)
        spell (get spells-map spell-kw)]
    [:div.flex
     [:div
      [labeled-dropdown
       "Spell Level"
       {:items (map
                (fn [lvl]
                  {:title lvl
                   :value lvl})
                (range 0 10))
        :value (:level spell-cfg)
        :on-change #(dispatch [value-change-event index (assoc spell-cfg :level (js/parseInt %))])}]]
     [:div.m-l-5
      [labeled-dropdown
       "Spellcasting Ability"
       {:items (cons
                {:title "<select ability>"
                 :value :select
                 :disabled? true}
                (map
                 obj-to-item
                 opt/abilities))
        :value (or (:ability spell-cfg) :select)
        :on-change #(dispatch [value-change-event
                               index
                               (assoc spell-cfg
                                 :ability
                                 (keyword 'orcpub.dnd.e5.character %))])}]]
     [:div.m-l-5
      [labeled-dropdown
       "Spell"
       {:items (cons
                {:title "<select spell>"
                 :value :select
                 :disabled? true}
                (map
                 obj-to-item
                 spells))
        :value (or (:key spell-cfg) :select)
        :on-change #(dispatch [value-change-event index (assoc spell-cfg :key (keyword %))])}]]]))

(def damage-dropdown-values
  (map (fn [kw]
         {:title (name kw)
          :value kw})
       opt/damage-types))

(defn modifier-values []
  (sorted-map-by
   <
   :weapon-prof {:name "Weapon Proficiency"
                 :value-fn keyword
                 :values (common/aloof-sort-by :title (concat
                          (map
                           (fn [type]
                             {:title (str "All " (name type))
                              :value type})
                           [:simple :martial])
                          (map
                           obj-to-item
                           @(subscribe [::mi/custom-and-standard-weapons]))
                          ))}
   :num-attacks {:name "Number of Attacks"
                 :value-fn js/parseInt
                 :values (map
                          value-to-item
                          (range 2 5))}
   :damage-resistance {:name "Damage Resistance"
                       :value-fn keyword
                       :values damage-dropdown-values}
   :damage-immunity {:name "Damage Immunity"
                     :value-fn keyword
                     :values damage-dropdown-values}
   :saving-throw-advantage {:name "Saving Throw Advantage"
                            :value-fn keyword
                            :values (map
                                     obj-to-item
                                     opt/conditions)}
   :skill-prof {:name "Skill Proficiency"
                :value-fn keyword
                :values (map
                         obj-to-item
                         skills/skills)}
   :armor-prof {:name "Armor Proficiency"
                :value-fn keyword
                :values (concat
                         (map
                          (fn [armor-type]
                            {:title armor-type
                             :value (name armor-type)})
                          [:light :medium :heavy :shields]))}
   :tool-prof {:name "Tool Proficiency"
               :value-fn keyword
               :values (map
                        obj-to-item
                        equip/tools)}
   :flying-speed {:name "Flying Speed"
                  :value-fn js/parseInt
                  :values (map
                           (fn [speed]
                             {:title (str speed " ft.")
                              :value speed})
                           (range 10 130 10))}
   :flying-speed-equals-walking-speed {:name "Flying Speed Equals Walking Speed"}
   :swimming-speed {:name "Swimming Speed"
                    :value-fn js/parseInt
                    :values (map
                             (fn [speed]
                               {:title (str speed " ft.")
                                :value speed})
                             (range 10 130 10))}
   :spell {:name "Spell"
           :component spell-selector}))

(defn modifier-level-selector [index level edit-modifier-level-event]
  [labeled-dropdown
   "Unlock at Level"
   {:items (map
            (fn [lvl]
              {:title lvl
               :value lvl})
            (range 1 21))
    :value level
    :on-change #(dispatch [edit-modifier-level-event index (js/parseInt %)])}])

(defn option-level-selection [{:keys [type level num]}
                             index
                             edit-selection-type-event
                             edit-selection-num-event
                             edit-selection-level-event
                             delete-selection-event]
  (let [selections @(subscribe [::selections/plugin-selections])]
    [:div
     [:div.flex.flex-wrap.align-items-end.m-b-20
      [:div.m-t-10
       [labeled-dropdown
        "Selection Type"
        {:items (sort-by :title (concat
                 [{:title "<select type to add>"
                   :disabled? true
                   :value :select}]
                 (map
                  obj-to-item
                  selections)
                 [{:title "<create new selection>"
                   :value :new-selection}]))
         :value (or type :select)
         :on-change #(if (= "new-selection" %)
                       (dispatch [::selections/new-selection])
                       (dispatch [edit-selection-type-event index (keyword %)]))}]]
      (when type
        [:div.m-t-10.m-l-5
         [modifier-level-selector index level edit-selection-level-event]])
      (when type
        [:div.m-t-10.m-l-5
         [labeled-dropdown
          "Amount to Select at this Level"
          {:items (map
                   value-to-item
                   (range 1 11))
           :value (or num 1)
           :on-change #(dispatch [edit-selection-num-event index (js/parseInt %)])}]])
      (when (or type level num)
        [:div.m-t-10
         [:button.form-button.m-l-5
          {:on-click #(dispatch [delete-selection-event index])}
          "delete"]])]]))

(defn option-level-modifier [{:keys [type value level]}
                             index
                             edit-modifier-type-event
                             edit-modifier-value-event
                             edit-modifier-level-event
                             delete-modifier-event]
  (let [mod-values (modifier-values)
        {:keys [name values component value-fn]} (when type (mod-values type))]
    [:div
     [:div.flex.flex-wrap.align-items-end.m-b-20
      [:div.m-t-10
       [labeled-dropdown
        "Modifier Type"
        {:items (sort-by :title (cons
                 {:title "<select type to add>"
                  :disabled? true
                  :value :select}
                 (map
                  (fn [[kw {:keys [name]}]]
                    {:title name
                     :value kw})
                  mod-values)))
         :value (if type (clojure.core/name type) :select)
         :on-change #(dispatch [edit-modifier-type-event index (keyword %)])}]]
      (when type
        [:div.m-t-10.m-l-5
         [modifier-level-selector index level edit-modifier-level-event]])
      (if (and type values)
        [:div.m-l-5.m-t-10
         [labeled-dropdown
          name
          {:items (cons
                   {:title "<select value>"
                    :disabled? true
                    :value :select}
                   values)
           :value (or value :select)
           :on-change #(dispatch [edit-modifier-value-event index (value-fn %)])}]]
        (when component
          [:div.m-l-5 [component index value edit-modifier-value-event]]))
      (when (or type level value)
        [:div.m-t-10
         [:button.form-button.m-l-5
          {:on-click #(dispatch [delete-modifier-event index])}
          "delete"]])]]))

(defn option-level-modifiers [{:keys [level-modifiers]}
                              add-modifier-event
                              edit-modifier-type-event
                              edit-modifier-value-event
                              edit-modifier-level-event
                              delete-modifier-event]
  [:div
   [:div
    (doall
     (map-indexed
      (fn [index modifier]
        ^{:key index}
        [option-level-modifier
         modifier
         index
         edit-modifier-type-event
         edit-modifier-value-event
         edit-modifier-level-event
         delete-modifier-event])
      level-modifiers))]
   [:div
    [option-level-modifier
     nil
     (count level-modifiers)
     edit-modifier-type-event
     edit-modifier-value-event
     edit-modifier-level-event
     delete-modifier-event]]])

(def selection-help
  "Help tooltip explaining what a Selection is in the builder context."
  [:div.p-20
   "A Selection is a choice your character makes — like picking a "
   "Fighting Style, a Totem Spirit, or Martial Maneuvers. "
   "You define the list of options here, then add the Selection to a "
   "class or subclass so it shows up at the right level during "
   "character creation."])

(defn title-with-help []
  (let [expanded? (r/atom false)]
    (fn [title help]
      [:div
       [:div
        title
        [:span.orange.pointer.f-s-18.m-l-5
         {:on-click #(swap! expanded? not)}
         [:i.fa.fa-question-circle.m-r-2]
         [:i.fa {:class (if @expanded? "fa-caret-up" "fa-caret-down")}]]]
       (when @expanded?
         [:div.bg-light.f-s-18
          help])])))

(defn cantrip-num-selector [level cantrips-known]
  [:div.flex.m-b-5
   [:div.w-150
    [dropdown
     {:items (cons
              {:title "<select level>"}
              (map
               (fn [v]
                 {:title v
                  :value v})
               (range 2 21)))
      :value level
      :on-change #(dispatch [::classes/set-class-path-prop
                             [:spellcasting :cantrips-known]
                             (-> cantrips-known
                                 (dissoc level)
                                 (assoc (js/parseInt %) 1))])}]]
   [:button.form-button.m-l-5
    {:on-click #(dispatch [::classes/set-class-path-prop
                           [:spellcasting :cantrips-known]
                           (dissoc cantrips-known level)])}
    "remove"]])

(defn option-level-selections [{:keys [level-selections]}
                              add-selection-event
                              edit-selection-type-event
                              edit-selection-num-event
                              edit-selection-level-event
                              delete-selection-event]
  [:div
   [title-with-help
    [:span.f-w-b.f-s-24 "Selections"]
    selection-help]
   [:div
    (doall
     (map-indexed
      (fn [index selection]
        ^{:key index}
        [option-level-selection
         selection
         index
         edit-selection-type-event
         edit-selection-num-event
         edit-selection-level-event
         delete-selection-event])
      level-selections))]
   [:div
    [option-level-selection
     nil
     (count level-selections)
     edit-selection-type-event
     edit-selection-num-event
     edit-selection-level-event
     delete-selection-event]]])

(defn class-builder
  "Homebrew class builder form — hit die, saves, spellcasting, cantrips,
   spell lists, modifiers, level selections, and traits."
  []
  (let [class @(subscribe [::classes/builder-item])
        spell-lists @(subscribe [::spells/spell-lists])
        class-key (get class :class)
        classes @(subscribe [::classes/classes])
        class-map @(subscribe [::classes/class-map])
        mobile? @(subscribe [:mobile?])]
    [:div.p-20.main-text-color
     [:div.flex.flex-wrap
      [:div.m-b-20.flex-grow-1
       [class-input-field
        "Name"
        :name
        class]]
      [plugin-datalist
       option-source-name-label
       class
       ::classes/set-class-prop]
      ]
     [:div.m-b-20
      [:div.f-w-b
       "Description"]
      [textarea-field
       {:value (get class :help)
        :on-change #(dispatch [::classes/set-class-prop :help %])}]]
     [:div.m-b-20.flex.flex-wrap
      [:div.m-l-5.flex-grow-1
       [labeled-dropdown
        "Hit Die"
        {:items (map
                 (fn [sides]
                   {:title sides
                    :value sides})
                 [6 8 10 12])
         :value (:hit-die class)
         :on-change #(dispatch [::classes/set-class-prop :hit-die (js/parseInt %)])}]]
      [:div.m-l-5.flex-grow-1
       [labeled-dropdown
        "Pick Subclass at Level"
        {:items (map
                 (fn [level]
                   {:title level
                    :value level})
                 (range 1 4))
         :value (:subclass-level class)
         :on-change #(dispatch [::classes/set-class-prop :subclass-level (js/parseInt %)])}]]
      [:div.flex-grow-1
       [class-input-field
        "Subclass Title"
        :subclass-title
        class
        "m-l-5"]]]
     #_[:div.m-b-20
        [:div.f-w-b
         "Subclass Description"]
        [textarea-field
         {:value (get class :subclass-help)
          :on-change #(dispatch [::classes/set-class-prop :subclass-help %])}]]
     [:div.m-b-20
      [class-input-field
       "Subclass Flavor"
       :subclass-help
       class]]
     [:div.m-b-30
      [:div.f-s-24.f-w-b.m-b-10 "Saving Throws"]
      [:div.flex.flex-wrap
       (doall
        (map
         (fn [{:keys [name key]}]
           ^{:key key}
           [:div.m-r-20.m-b-10
            [comps/labeled-checkbox
             name
             (get-in class [:profs :save key])
             false
             #(dispatch [::classes/toggle-save-prof key])]])
         opt/abilities))]]
     [:div.m-b-30
      [:div.f-s-24.f-w-b.m-b-10 "Ability Increase Levels"]
      [:div.flex.flex-wrap
       (let [asi-levels-set (set (:ability-increase-levels class))]
         (doall
          (map
           (fn [level]
             ^{:key level}
             [:div.m-r-20.m-b-10
              [comps/labeled-checkbox
               level
               (asi-levels-set level)
               false
               #(dispatch [::classes/toggle-ability-increase-level level])]])
           (range 4 21))))]]
     (let [spellcaster? (boolean (get class :spellcasting))]
       [:div.m-b-30
        [:div.f-s-24.f-w-b.m-b-10 "Spellcasting"]
        [:div.flex.flex-wrap.m-b-20
         [labeled-dropdown
          "Does this class have spell slots?"
          {:items [{:title "No"
                    :value false}
                   {:title "Yes"
                    :value true}]
           :value spellcaster?
           :on-change #(dispatch [::classes/set-class-prop
                                  :spellcasting
                                  (when (= "true" %)
                                    {:level-factor 3
                                     :known-mode :schedule
                                     :ability ::char/cha
                                     :spells-known classes/third-caster-spells-known-schedule})])}]
         (when spellcaster?
           [:div.m-l-5
            [labeled-dropdown
             "What spell list does this class use?"
             {:items (cons
                      {:title "Custom"
                       :value "custom"}
                      (map
                       (fn [[class-kw]]
                         ^{:key class-kw}
                         {:title (get-in class-map [class-kw ::template/name])
                          :value class-kw})
                       spell-lists))
              :value (get-in class [:spellcasting :spell-list-kw])
              :on-change #(dispatch [::classes/set-class-path-prop
                                     [:spellcasting :spell-list-kw] (when (not= "custom" %)
                                                                      (keyword %))])}]])
         (when spellcaster?
           [:div.m-l-5
            [labeled-dropdown
             "Spellcasting ability"
             {:items (cons
                      {:title "<select ability>"
                       :value nil}
                      (map
                       obj-to-item
                       opt/abilities))
              :value (get-in class [:spellcasting :ability])
              :on-change #(dispatch [::classes/set-class-path-prop [:spellcasting :ability] (keyword "orcpub.dnd.e5.character" %)])}]])
         (when spellcaster?
           [:div.m-l-5
            [labeled-dropdown
             "At what level does this class first gain spell slots?"
             {:items (map
                      value-to-item
                      (range 1 4))
              :value (get-in class [:spellcasting :level-factor] 1)
              :on-change #(let [level-factor (js/parseInt %)]
                            (dispatch [::classes/set-class-path-prop
                                       [:spellcasting :level-factor] level-factor
                                       [:spellcasting :spells-known] (case level-factor
                                                                       1 classes/full-caster-spells-known-schedule
                                                                       2 classes/half-caster-spells-known-schedule
                                                                       3 classes/third-caster-spells-known-schedule)]))}]])]
        (when (and spellcaster?
                 (not (get-in class [:spellcasting :spell-list-kw])))
          (let [cantrips? (get-in class [:spellcasting :cantrips?])]
            [:div
             [:div.f-s-18.f-w-b "Cantrips"]
             [:div.flex.flex-wrap.m-b-20
              [:div
               [labeled-dropdown
                "Does this class gain cantrips?"
                {:items [{:title "No"
                          :value false}
                         {:title "Yes"
                          :value true}]
                 :value cantrips?
                 :on-change #(dispatch [::classes/set-class-path-prop
                                        [:spellcasting :cantrips?]
                                        (= % "true")])}]]
              (when cantrips?
                [:div.m-l-5
                 [labeled-dropdown
                  "How many cantrips does this class know at first level?"
                  {:items (map
                           (fn [v]
                             {:title v
                              :value v})
                           (range 0 6))
                   :value (get-in class [:spellcasting :cantrips-known 1])
                   :on-change #(dispatch [::classes/set-class-path-prop
                                          [:spellcasting :cantrips-known 1]
                                          (js/parseInt %)])}]])]
             (when cantrips?
               [:div.m-b-20
                [:div.f-s-18.f-w-b.m-b-5 "At what other levels does this class gain cantrips?"]
                (let [cantrips-known (get-in class [:spellcasting :cantrips-known])]
                  [:div
                   (map
                    (fn [[level]]
                      ^{:key level}
                      [cantrip-num-selector level cantrips-known])
                    (sort-by first (dissoc cantrips-known 1)))
                   [cantrip-num-selector nil cantrips-known]])])
             [:div.f-s-18.f-w-b "Select spells from which this class can choose"]
             [:div
              (doall
               (map
                (fn [level]
                  ^{:key level}
                  [:div.m-t-10
                   [:div.f-s-16.f-w-b.m-b-10 (if (zero? level)
                                               "Cantrips"
                                               (str "Level " level))]
                   [:div.flex.flex-wrap
                    (doall
                     (map
                      (fn [{:keys [name key]}]
                        ^{:key key}
                        [:div.m-r-20.m-b-10
                         [comps/labeled-checkbox
                          name
                          (get-in class [:spellcasting :spell-list level key])
                          false
                          #(dispatch [::classes/toggle-class-spell-list level key])]])
                      @(subscribe [::spells/spells-for-level level])))]])
                (range (if cantrips? 0 1)
                       (inc (case (get-in class [:spellcasting :level-factor])
                              2 5
                              3 4
                              9)))))]]))])
     [:div.m-b-10
      [option-skill-proficiency-choice
       class
       ::classes/set-class-path-prop
       ::classes/toggle-class-path-prop]]
     [:div.m-b-10
      [option-skill-expertise-choice
       class
       ::classes/set-class-path-prop
       ::classes/toggle-class-path-prop]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Modifiers"]
      [option-level-modifiers
       class
       ::e5/add-class-modifier
       ::e5/edit-class-modifier-type
       ::e5/edit-class-modifier-value
       ::e5/edit-class-modifier-level
       ::e5/delete-class-modifier]]
     [:div.m-b-20.m-t-30
      [option-level-selections
       class
       ::e5/add-class-selection
       ::e5/edit-class-selection-type
       ::e5/edit-class-selection-num
       ::e5/edit-class-selection-level
       ::e5/delete-class-selection]]
     [:div
      [option-traits
       class
       ::e5/add-class-trait
       ::e5/edit-class-trait-name
       ::e5/edit-class-trait-type
       ::e5/edit-class-trait-description
       ::e5/delete-class-trait
       :edit-trait-level-event ::e5/edit-class-trait-level
       :types [{:title "Other"
                :value :other}
               {:title "Action"
                :value :action}
               {:title "Bonus Action"
                :value :b-action}
               {:title "Reaction"
                :value :reaction}]]]]))

(defn subclass-spells [subclass spells-title spells-kw]
  [:div
   [:div.f-s-18.f-w-b.m-b-10 (str (:name subclass) " " spells-title)]
   [:table
    [:tbody
     [:tr.f-w-b
      [:th.p-5 "Spell Level"]
      [:th.p-5.t-a-l "Spells"]]
     (doall
      (map
       (fn [level]
         ^{:key level}
         [:tr
          [:th.p-5 (common/ordinal level)]
          (let [spells-for-level @(subscribe [::spells/spells-for-level level])]
            [:th.p-5
             [:div.flex.flex-wrap
              (doall
               (map
                (fn [i]
                  ^{:key i}
                  [:div.m-l-5.m-b-10
                   [dropdown
                    {:items (cons
                             {:title "<select spell>"
                              :value :select
                              :disabled? true}
                             (map
                              obj-to-item
                              spells-for-level))
                     :value (or (get-in subclass [spells-kw level i])
                                :select)
                     :on-change #(dispatch [::classes/set-class-spell spells-kw level i (keyword %)])}]])
                (range 2)))]])])
       (range 1 6)))]]])

(defn subclass-builder []
  (let [subclass @(subscribe [::classes/subclass-builder-item])
        spell-lists @(subscribe [::spells/spell-lists])
        class-key (get subclass :class)
        classes @(subscribe [::classes/classes])
        mobile? @(subscribe [:mobile?])]
    [:div.p-20.main-text-color
     [:div.flex.flex-wrap
      [:div.m-b-20
       [subclass-input-field
        "Name"
        :name
        subclass]]
      [:div.m-l-5.m-b-20
       [labeled-dropdown
        "Class"
        {:items (map
                 (fn [{:keys [:orcpub.template/name :orcpub.template/key]}]
                   {:title name
                    :value (clojure.core/name key)})
                 classes)
         :value (get subclass :class)
         :on-change #(dispatch [::classes/set-subclass-prop :class (keyword %)])}]]
      [plugin-datalist 
         option-source-name-label 
         subclass
         ::classes/set-subclass-prop
       ]
      ]
     (when (#{:fighter :rogue :warlock :cleric :paladin} class-key)
       (let [spellcasting (get subclass :spellcasting)
             spellcasting? (some? spellcasting)]
         [:div.m-b-20
          [:div.f-s-24.f-w-b.m-b-10 "Spellcasting"]
          (cond
            (#{:fighter :rogue} class-key)
            [:div.flex.flex-wrap
             [labeled-dropdown
              "Does this subclass cast wizard spells?"
              {:items (map
                       (fn [v]
                         {:title (if v "Yes" "No")
                          :value v})
                       [false true])
               :value spellcasting?
               :on-change #(dispatch [::classes/toggle-subclass-spellcasting])}]]

            (= :paladin class-key)
            [:div [subclass-spells subclass "Spells" :paladin-spells]]

            (= :cleric class-key)
            [:div [subclass-spells subclass "Domain Spells" :cleric-spells]]

            (= :warlock class-key)
            [:div [subclass-spells subclass "Expanded Spells" :warlock-spells]])]))
     [:div
      [option-skill-proficiency-choice
       subclass
       ::classes/set-subclass-path-prop
       ::classes/toggle-subclass-path-prop]]
     [:div
      [option-skill-expertise-choice
       subclass
       ::classes/set-subclass-path-prop
       ::classes/toggle-subclass-path-prop]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Modifiers"]
      [option-level-modifiers
       subclass
       ::e5/add-subclass-modifier
       ::e5/edit-subclass-modifier-type
       ::e5/edit-subclass-modifier-value
       ::e5/edit-subclass-modifier-level
       ::e5/delete-subclass-modifier]]
     [:div.m-b-20.m-t-30
      [option-level-selections
       subclass
       ::e5/add-subclass-selection
       ::e5/edit-subclass-selection-type
       ::e5/edit-subclass-selection-num
       ::e5/edit-subclass-selection-level
       ::e5/delete-subclass-selection]]
     [option-traits
      subclass
      ::e5/add-subclass-trait
      ::e5/edit-subclass-trait-name
      ::e5/edit-subclass-trait-type
      ::e5/edit-subclass-trait-description
      ::e5/delete-subclass-trait
      :edit-trait-level-event ::e5/edit-subclass-trait-level
       :types [{:title "Other"
                :value :other}
               {:title "Action"
                :value :action}
               {:title "Bonus Action"
                :value :b-action}
               {:title "Reaction"
                :value :reaction}]]]))

(defn option-spell [index
                     {:keys [level value] :as spell-cfg}
                     set-spell-level-event
                     set-spell-value-event
                     delete-spell-event]
  [:div.flex.flex-wrap.m-b-10.align-items-end
   [modifier-level-selector
    index
    level
    set-spell-level-event]
   [:div.m-l-5
    [spell-selector
     index
     value
     set-spell-value-event]]
   (when (or level value)
     [:div.m-t-10
      [:button.form-button.m-l-5
       {:on-click #(dispatch [delete-spell-event index])}
       "delete"]])])

(defn option-spells [option
                     set-spell-level-event
                     set-spell-value-event
                     delete-spell-event]
  [:div
   [:div
    (doall
     (map-indexed
      (fn [i spell-cfg]
        ^{:key i}
        [option-spell
         i
         spell-cfg
         set-spell-level-event
         set-spell-value-event
         delete-spell-event])
      (:spells option)))]
   [:div [option-spell
          (count (:spells option))
          {}
          set-spell-level-event
          set-spell-value-event
          delete-spell-event]]])

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

(defn- find-duplicate-option-names
  "Returns a set of option names (lowercased via name-to-kw) that appear more than once.
   Used by selection-builder to highlight duplicate names inline."
  [options]
  (let [keys (map #(when-let [n (:name %)]
                     (when-not (s/blank? n)
                       (common/name-to-kw n)))
                  options)
        freqs (frequencies (remove nil? keys))]
    (set (map first (filter #(> (val %) 1) freqs)))))

(defn selection-builder []
  (let [selection @(subscribe [::selections/builder-item])
        options (:options selection)
        dupe-keys (find-duplicate-option-names options)
        has-dupes? (seq dupe-keys)
        ;; Collect the display names of duplicate options for the summary
        dupe-names (when has-dupes?
                     (->> options
                          (filter #(and (not (s/blank? (:name %)))
                                        (contains? dupe-keys (common/name-to-kw (:name %)))))
                          (map :name)
                          distinct
                          sort))
        ;; Check for empty/blank option names
        has-empty? (some #(s/blank? (:name %)) options)]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [selection-input-field
       "Name"
       :name
       selection
       "m-b-20"]
      [plugin-datalist
       option-source-name-label
       selection
       ::selections/set-selection-prop]
      ]
     [:div
      [:div.flex.justify-cont-s-b
       [:div.f-s-24.f-w-b "Options"]
       [:button.form-button
        {:on-click #(dispatch [::selections/add-option])}
        "Add Option"]]
      ;; Summary warning for duplicate names
      (when has-dupes?
        [:div.p-10.m-b-10.red
         {:style {:background-color "rgba(255,0,0,0.1)"
                  :border "1px solid red"
                  :border-radius "4px"}}
         [:span.f-w-b "Duplicate names found: "]
         [:span (s/join ", " dupe-names)]
         [:div.f-s-12 "Each option must have a unique name. Rename duplicates before saving."]])
      ;; Warning for empty option names
      (when has-empty?
        [:div.p-10.m-b-10.red
         {:style {:background-color "rgba(255,0,0,0.1)"
                  :border "1px solid red"
                  :border-radius "4px"}}
         "One or more options have no name. All options must be named."])
      [:div
       (doall
        (map-indexed
         (fn [i {:keys [name description]}]
           (let [is-dupe? (and (not (s/blank? name))
                               (contains? dupe-keys (common/name-to-kw name)))
                 is-empty? (s/blank? name)]
             ^{:key i}
             [:div.m-b-30
              [:div.flex.align-items-end.m-b-10
               [:div.f-w-b.f-s-24.m-r-10 (str (inc i) ".")]
               [:div.flex-grow-1
                [input-builder-field
                 [:span.f-w-b "Name"]
                 name
                 #(dispatch [::selections/set-selection-path-prop [:options i :name] %])
                 {:class "input h-40"
                  :style (when (or is-dupe? is-empty?)
                           {:border "2px solid red"})}]
                (when is-dupe?
                  [:div.red.f-s-12.m-t-2
                   "Duplicate name \u2014 rename to a unique name before saving"])
                (when is-empty?
                  [:div.red.f-s-12.m-t-2
                   "Option name is required"])]
               [:div
                [:button.form-button.m-l-5
                 {:on-click #(dispatch [::selections/delete-option i])}
                 "delete"]]]
              [:div.w-100-p
               [:div.f-w-b
                "Description"]
               [textarea-field
                {:value description
                 :on-change #(dispatch [::selections/set-selection-path-prop [:options i :description] %])}]]]))
         options))]
      ;; Mirror add button at bottom for long option lists
      [:div.p-t-10.flex.justify-cont-end
       [:button.form-button
        {:on-click #(dispatch [::selections/add-option])}
        "Add Option"]]]]))

(defn language-builder []
  (let [language @(subscribe [::langs/builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [language-input-field
       "Name"
       :name
       language
       "m-b-20"]
      [plugin-datalist
       option-source-name-label
       language
       ::langs/set-language-prop]
      ]
     [:div.w-100-p
      [:div.f-s-24.f-w-b
       "Description"]
      [textarea-field
       {:value (get language :description)
        :on-change #(dispatch [::langs/set-language-prop :description %])}]]]))

(defn boon-builder []
  (let [boon @(subscribe [::classes/boon-builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [boon-input-field
       "Name"
       :name
       boon
       "m-b-20"]
      [plugin-datalist
       option-source-name-label
       boon
       ::classes/set-boon-prop]
      ]
     [:div.w-100-p
      [:div.f-s-24.f-w-b
       "Description"]
      [textarea-field
       {:value (get boon :description)
        :on-change #(dispatch [::classes/set-boon-prop :description %])}]]]))

(defn invocation-builder []
  (let [invocation @(subscribe [::classes/invocation-builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [invocation-input-field
       "Name"
       :name
       invocation
       "m-b-20"]
      [plugin-datalist
       option-source-name-label
       invocation
       ::classes/set-invocation-prop]
      ]
     [:div.w-100-p
      [:div.f-s-24.f-w-b
       "Description"]
      [textarea-field
       {:value (get invocation :description)
        :on-change #(dispatch [::classes/set-invocation-prop :description %])}]]]))

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


(defn newb-character-builder-page []
  [content-page
   "Character Builder for Newbs"
   []
   (let [{:keys [key question answers] :as q} @(subscribe [::char/current-question])
         newb-char-data @(subscribe [::char/newb-char-data])
         current-answer (get-in newb-char-data [:answers key])
         has-history? @(subscribe [::char/has-question-history?])]
     [:div.p-20.main-text-color
      (if (some? q)
        [:div
         [:div
          [:div.f-w-b.f-s-24
           question]]
         [:div.m-t-5
          (doall
           (map
            (fn [{:keys [answer tag] :as a}]
              ^{:key tag}
              [:div.p-5.f-s-16.f-w-b
               [comps/labeled-checkbox
                answer
                (= tag (get-in newb-char-data [:answers key]))
                false
                #(dispatch [::char/add-answer q a])]])
            answers))]]
        [:div.p-20.main-text-color
         [:div.f-s-18.f-w-b "Your character is complete, click the button below to view it"]
         [:button.form-button
          {:on-click #(dispatch [::char/open-character (:char newb-char-data)])}
          "View Character"]])
      [:div.m-t-20
       [:button.link-button
        {:class (when (not has-history?) "disabled")
         :on-click #(when has-history? (dispatch [::char/previous-question]))}
        "Back"]
       [:button.form-button
        {:on-click #(when current-answer (dispatch [::char/next-question]))
         :class (when (nil? current-answer) "disabled")}
        "Next"]]])
   :hide-header-message? true])

;; events are set and passed by the individual pages defined below this
(defn builder-page [item-title reset-event save-event builder & [title]]
  [content-page
   (or title (str item-title " Builder"))
   [{:title (str "New " item-title)
     :icon "plus"
     :on-click #(dispatch [reset-event])}
    {:title "Save to Browser Storage"
     :icon "save"
     :on-click #(dispatch [save-event])}]
   [builder]])



(defn get-owner?
  "True when the logged-in user owns the given custom item (by db id)."
  [item-key]
  (let [username @(subscribe [:username])
        item @(subscribe [::mi/custom-item item-key])
        builder-item @(subscribe [::mi/builder-item item-key])]
    (= username (or (::mi/owner item) (::mi/owner builder-item)))))

(defn deletion-modal-with [builder-page item-key]
  (let [show? @(subscribe [::mi/delete-confirmation-shown? item-key])
        ]
    [:span
     [:div {:class (if show? "modal-container" "modal-container hidden")}
      [:div.modal
       [:div.modal_content
        [:div.m-b-10 "Are you sure you want to delete this item?"]
        [:div
         [:button.form-button
          {:on-click (make-event-handler ::mi/hide-delete-confirmation item-key)}
          "cancel"]
         [:span.link-button
          {:on-click (delete-item-handler item-key)}
          "delete"]
         ]]]]
     [builder-page]]
   )
  )

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

(defn spell-builder-page []
  (builder-page "Spell" ::spells/reset-spell ::spells/save-spell spell-builder))

(defn monster-builder-page []
  (builder-page "Monster" ::monsters/reset-monster ::monsters/save-monster monster-builder))


(defn language-builder-page []
  (builder-page "Language" ::langs/reset-language ::langs/save-language language-builder))

(defn invocation-builder-page []
  (builder-page "Eldritch Invocation" ::classes/reset-invocation ::classes/save-invocation invocation-builder))

(defn boon-builder-page []
  (builder-page "Pact Boon" ::classes/reset-boon ::classes/save-boon boon-builder))

(defn selection-builder-page []
  (builder-page "Selection" ::selections/reset-selection ::selections/save-selection selection-builder [title-with-help "Selection Builder" selection-help]))

(defn background-builder-page []
  (builder-page "Background" ::bg/reset-background ::bg/save-background background-builder))

(defn race-builder-page []
  (builder-page "Race" ::races/reset-race ::races/save-race race-builder))

(defn subrace-builder-page []
  (builder-page "Subrace" ::races/reset-subrace ::races/save-subrace subrace-builder))

(defn subclass-builder-page []
  (builder-page "Subclass" ::classes/reset-subclass ::classes/save-subclass subclass-builder))

(defn class-builder-page []
  (builder-page "Class" ::classes/reset-class ::classes/save-class class-builder))

(defn feat-builder-page []
  (builder-page "Feat" ::feats/reset-feat ::feats/save-feat feat-builder))

