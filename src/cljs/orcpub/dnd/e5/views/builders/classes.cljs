(ns orcpub.dnd.e5.views.builders.classes
  "Class and subclass homebrew builder pages, plus the modifier/level
   selection system they share.

   Extracted from views.builders — imports shared option-* helpers and
   builder infrastructure from the parent module."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [orcpub.common :as common]
            [orcpub.components :as comps]
            [orcpub.template :as template]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.selections :as selections]
            [orcpub.dnd.e5.classes :as classes]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.skills :as skills]
            [orcpub.dnd.e5.equipment :as equip]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.dnd.e5.views.common
             :refer [labeled-dropdown dropdown textarea-field]]
            [orcpub.dnd.e5.views
             :refer [obj-to-item]]
            [orcpub.dnd.e5.views.builders
             :refer [builder-input-field value-to-item
                     plugin-datalist option-source-name-label
                     option-skill-proficiency-choice
                     option-skill-expertise-choice
                     option-traits
                     spell-selector modifier-level-selector
                     title-with-help selection-help
                     builder-page]]))


;;; ====================================================================
;;; Field wrappers — thin delegates to builder-input-field
;;; ====================================================================

(defn subclass-input-field [title prop subclass & [class-names]]
  (builder-input-field title prop subclass ::classes/set-subclass-prop class-names))

(defn class-input-field [title prop class & [class-names]]
  (builder-input-field title prop class ::classes/set-class-prop class-names))


;;; ====================================================================
;;; Modifier values — catalogue of modifier types + their dropdown data
;;; ====================================================================

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


;;; ====================================================================
;;; Level selections — class/subclass level-gated options
;;; ====================================================================

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


;;; ====================================================================
;;; Cantrip number selector
;;; ====================================================================

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


;;; ====================================================================
;;; Level selections — wraps option-level-selection with title + help
;;; ====================================================================

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


;;; ====================================================================
;;; Class builder — the main homebrew class form (~267 lines)
;;; ====================================================================

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


;;; ====================================================================
;;; Subclass spells table
;;; ====================================================================

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


;;; ====================================================================
;;; Subclass builder — the main homebrew subclass form
;;; ====================================================================

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


;;; ====================================================================
;;; Page wrappers — delegating to builder-page from views.builders
;;; ====================================================================

(defn subclass-builder-page []
  (builder-page "Subclass" ::classes/reset-subclass ::classes/save-subclass subclass-builder))

(defn class-builder-page []
  (builder-page "Class" ::classes/reset-class ::classes/save-class class-builder))
