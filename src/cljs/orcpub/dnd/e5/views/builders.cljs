(ns orcpub.dnd.e5.views.builders
  "Shared builder infrastructure — field factories, option-* helpers,
   plugin-datalist, spell/modifier-level selectors, builder-page, and
   the newb character builder.  All domain-specific builders live in
   child namespaces under builders/."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [clojure.string :as s]
            [orcpub.common :as common]
            [orcpub.components :as comps]
            [orcpub.route-map :as routes]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.languages :as langs]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.skills :as skills]
            [orcpub.dnd.e5.equipment :as equip]
            [orcpub.dnd.e5.armor :as armor]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.dnd.e5.views.common
             :refer [builder-field textarea-field
                     make-event-handler
                     labeled-dropdown]]
            [orcpub.dnd.e5.views
             :refer [content-page obj-to-item delete-item-handler]]))


;;; ─── Field factories ────────────────────────────────────────────────

(defn input-builder-field [name value on-change attrs]
  [builder-field :input name value on-change attrs])

(defn value-to-item [v]
  {:title v
   :value v})

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


;;; ─── Plugin datalist ────────────────────────────────────────────────

(def option-source-name-label
  [:span
   [:span "Option Source Name"]
   [:span.f-w-n.f-s-12.m-l-5 "(e.g. "
    [:span.i "Player's Manual"]
    [:span ", "]
    [:span.i "Hodor's Guide to Hodors"]
    [:span ")"]]])

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


;;; ─── Proficiency choice ─────────────────────────────────────────────

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

(def option-skill-expertise-choice
  (partial option-proficiency-choice
           "Skill Expertise (Double Proficiency) Choice"
           :skill-expertise-options
           skills/skills))

(def option-skill-proficiency-choice
  (partial option-proficiency-choice
           "Skill Proficiency Choice"
           :skill-options
           skills/skills))


;;; ─── Option helpers ─────────────────────────────────────────────────

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


;;; ─── Spell / level infrastructure ───────────────────────────────────
;;; spell-selector and modifier-level-selector live here (not in
;;; builders/classes.cljs) because option-spell depends on both, and
;;; option-spell is shared infrastructure used by race AND class builders.
;;; Moving them to a child would create a child→child dependency.

(defn spell-selector
  "Spell picker row: spell level, spellcasting ability, and spell name dropdowns.
   Used by option-spell (shared) and by modifier-values :spell (classes.cljs)."
  [index spell-cfg value-change-event]
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

(defn modifier-level-selector
  "Dropdown for selecting the level at which a modifier/selection unlocks."
  [index level edit-modifier-level-event]
  [labeled-dropdown
   "Unlock at Level"
   {:items (map
            (fn [lvl]
              {:title lvl
               :value lvl})
            (range 1 21))
    :value level
    :on-change #(dispatch [edit-modifier-level-event index (js/parseInt %)])}])


;;; ─── Selection help + title-with-help ───────────────────────────────

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


;;; ─── Spell option helpers ───────────────────────────────────────────

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


;;; ─── Builder page infrastructure ────────────────────────────────────

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
