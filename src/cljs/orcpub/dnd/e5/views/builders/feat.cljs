(ns orcpub.dnd.e5.views.builders.feat
  "Feat homebrew builder page and all feat-specific helper components.

   Extracted from views.builders — imports shared option-* helpers and
   builder infrastructure from the parent module."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [orcpub.common :as common]
            [orcpub.components :as comps]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.feats :as feats]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.dnd.e5.races :as races]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.damage-types :as damage-types]
            [orcpub.dnd.e5.skills :as skills]
            [orcpub.dnd.e5.weapons :as weapon]
            [orcpub.dnd.e5.armor :as armor]
            [orcpub.dnd.e5.views.common
             :refer [make-event-handler labeled-dropdown
                     textarea-field]]
            [orcpub.dnd.e5.views
             :refer [labeled-checkbox]]
            [orcpub.dnd.e5.views.builders
             :refer [builder-input-field plugin-datalist
                     option-source-name-label option-armor-proficiency
                     option-damage-resistance option-hps
                     option-skill-proficiency-or-expertise
                     option-tool-proficiency-or-expertise
                     builder-page]]))


;;; ─── Field wrapper ─────────────────────────────────────────────────

(defn feat-input-field [title prop feat & [class-names]]
  (builder-input-field title prop feat ::feats/set-feat-prop class-names))


;;; ─── Feat-specific option helpers ──────────────────────────────────

(defn feat-prereqs
  "Prerequisite checkboxes: spellcasting, ability scores, armor, race."
  [feat]
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


;;; ─── Feat builder + page wrapper ───────────────────────────────────

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

(defn feat-builder-page []
  (builder-page "Feat" ::feats/reset-feat ::feats/save-feat feat-builder))
