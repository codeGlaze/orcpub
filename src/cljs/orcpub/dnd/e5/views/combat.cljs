(ns orcpub.dnd.e5.views.combat
  "Combat tracker, encounter builder, and related selector components.

   Contains the initiative tracker with per-combatant HP/condition
   management, creature/party/encounter selectors, and the encounter
   builder.  Depends on views.cljs for page wrappers and display
   components (one-way); never required by views.cljs.
   core.cljs routes to page components here directly."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [clojure.string :as s]
            [orcpub.components :as comps]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.combat :as combat]
            [orcpub.dnd.e5.encounters :as encounters]
            [orcpub.dnd.e5.party :as party]
            [orcpub.dnd.e5.monsters :as monsters]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.dnd.e5.views.common
             :refer [labeled-dropdown dropdown svg-icon]]
            [orcpub.dnd.e5.views
             :refer [obj-to-item content-page
                     character-display character-summary-2
                     monster-component monster-summary]]
            [orcpub.dnd.e5.views.builders
             :refer [value-to-item input-builder-field
                     builder-input-field builder-page
                     plugin-datalist option-source-name-label]]))

;;;; ====================================================================
;;;; Selectors
;;;; ====================================================================

(defn monster-selector
  "Dropdown for selecting a monster and its count."
  [index {:keys [monster num]} on-key-change on-num-change]
  (let [monsters @(subscribe [::monsters/sorted-monsters])]
    [:div.flex.flex-wrap.m-l-5
     [labeled-dropdown
      "Monster Name"
      {:items (cons
               {:title "<select monster>"}
               (map
                obj-to-item
                monsters))
       :value monster
       :on-change on-key-change}]
     (when monster
       [:div.m-l-5.m-b-10
        [labeled-dropdown
         "Number"
         {:items (map
                  value-to-item
                  (range 0 21))
          :value (or num 0)
          :on-change on-num-change}]])]))

(defn character-selector
  "Dropdown for selecting a character by name/race/class summary."
  [index {:keys [character]} on-change]
  (let [characters @(subscribe [::char/characters true])]
    [:div.flex.flex-wrap.m-l-5
     [:div.m-b-10
      [labeled-dropdown
       "Character Name"
       {:items (cons
                {:title "<select character>"}
                (map
                 (fn [{:keys [::char/character-name
                              ::char/race-name
                              ::char/classes] :as character-summary}]
                   {:title (str character-name
                                " - "
                                race-name
                                " "
                                (s/join
                                 "/"
                                 (map
                                  ::char/class-name
                                  classes)))
                    :value (:db/id character-summary)})
                 characters))
        :value character
        :on-change on-change}]]]))

(defn creature-selector
  "Type selector (Monster / NPC) that renders the appropriate
   child selector based on the chosen type."
  [index {:keys [type creature] :as details}]
  [:div.flex.flex-wrap.align-items-c
   [:div.m-b-10
    [labeled-dropdown
     "Type"
     {:items [{:title "<select type>"}
              {:title "Monster"
               :value :monster}
              {:title "Non-Player Character"
               :value :character}]
      :value type
      :on-change #(dispatch [::encounters/set-encounter-path-prop [:creatures index :type] (keyword %)])}]]
   (case type
     :monster [monster-selector
               index
               creature
               #(dispatch [::encounters/set-encounter-path-prop
                          [:creatures index :creature :monster]
                           (keyword %)])
               #(dispatch [::encounters/set-encounter-path-prop
                           [:creatures index :creature :num]
                           (js/parseInt %)])]
     :character [character-selector
                 index
                 creature
                 #(dispatch [::encounters/set-encounter-path-prop
                             [:creatures index :creature :character]
                             (js/parseInt %)])]
     nil)
   [:button.form-button.m-l-5.m-b-10
    {:on-click #(dispatch [::encounters/delete-creature index])}
    "delete"]])

(defn party-selector
  "Dropdown for selecting a party by name."
  [index party]
  (let [parties @(subscribe [::party/parties true])]
    [:div
     [labeled-dropdown
      (str "Party " (inc index))
      {:items (cons
               {:title "<select party>"}
               (map
                (fn [{:keys [:db/id ::party/name]}]
                  {:title name
                   :value id})
                parties))
       :value party
       :on-change #(dispatch [::combat/set-combat-path-prop [:parties index] (js/parseInt %)])}]]))

(defn encounter-selector
  "Dropdown for selecting a saved encounter."
  [index encounter]
  (let [encounters @(subscribe [::encounters/encounters])]
    [:div
     [labeled-dropdown
      (str "Encounter " (inc index))
      {:items (cons
               {:title "<select encounter>"}
               (map
                obj-to-item
                encounters))
       :value encounter
       :on-change #(dispatch [::combat/set-combat-path-prop [:encounters index] (keyword %)])}]]))

;;;; ====================================================================
;;;; Combat Helpers
;;;; ====================================================================

(def char-name
  "Extract character name from a combatant map."
  #(-> % :character ::char/character-name))

(defn on-character-change
  "Dispatch handler for character selection at a given index."
  [index]
  #(dispatch [::combat/set-combat-path-prop
              [:characters index]
              (js/parseInt %)]))

(defn on-monster-change
  "Dispatch handler for monster selection at a given index."
  [index]
  #(dispatch [::combat/set-combat-path-prop
              [:monsters index :monster]
              (keyword %)]))

(defn on-monster-num-change
  "Dispatch handler for monster count at a given index."
  [index]
  #(dispatch [::combat/set-combat-path-prop
              [:monsters index :num]
              (js/parseInt %)]))

;; dead — duplicates of common.cljc defs, never referenced from views
#_(def rounds-per-minute 10)
#_(def minutes-per-hour 60)
#_(def hours-per-day 24)

(def w-155
  "Style map: fixed width 155px."
  {:style {:width "155px"}})

(def w-160
  "Style map: fixed width 160px."
  {:style {:width "160px"}})

;;;; ====================================================================
;;;; Condition & Duration
;;;; ====================================================================

(defn duration-selector
  "Dropdown for a single duration component (hours, minutes, or rounds)."
  [title
   key
   max
   monster
   individual-index
   duration
   index]
  (let [value (get duration key 0)]
    [:div.m-r-5
     [:div.f-w-b.f-s-12 title]
     [dropdown
      {:items (map
               value-to-item
               (range 0 max))
       :value value
       :on-change #(dispatch [::combat/set-monster-condition-duration monster individual-index index key (js/parseInt %)])}]]))

(defn condition-selector
  "Dropdown for selecting a condition type with duration controls.
   Filters out already-used conditions from the remaining list."
  [current-round
   monster
   individual-index
   {:keys [type duration] :as condition}
   index
   deletable?
   used-conditions]
  (let [current-round (or current-round 0)
        remaining-conditions (remove
                              (comp (disj used-conditions type) :key)
                              opt/conditions)]
    (when (seq remaining-conditions)
      [:div.flex.align-items-end
       [:div.m-r-5
        w-155
        [:div.f-w-b.f-s-12 "Condition"]
        [dropdown
         {:items (cons
                  {:title "<select condition>"}
                  (map
                   obj-to-item
                   remaining-conditions))
          :value type
          :on-change #(dispatch [::combat/set-monster-condition-type monster individual-index index (keyword %)])}]]
       (when type
         [:div.flex.flex-wrap
          [duration-selector "Hours" :hours 24 monster individual-index duration index]
          [duration-selector "Minutes" :minutes 60 monster individual-index duration index]
          [duration-selector "Rounds" :rounds 10 monster individual-index duration index]])
       (when deletable?
         [:button.form-button.f-s-14
          {:on-click #(dispatch [::combat/delete-monster-condition monster individual-index index])}
          [:i.fa.fa-trash]])])))

;;;; ====================================================================
;;;; Combat Tracker
;;;; ====================================================================

(defn combat-tracker
  "Full combat initiative tracker.  Manages parties, encounters,
   characters, and monsters with per-combatant initiative, HP, and
   condition tracking.  Form-2 component — outer fn creates the
   expanded-rows atom for toggling detail panels."
  []
  (let [expanded-rows (r/atom {})]
    (fn []
      (let [mobile? @(subscribe [:mobile?])
            {:keys [parties encounters characters monsters monster-data] :as tracker-item} @(subscribe [::combat/tracker-item])
            encounter-map @(subscribe [::encounters/encounter-map])
            encounter-creatures (mapcat (comp :creatures encounter-map) encounters)
            by-type (group-by :type encounter-creatures)
            encounter-monsters (by-type :monster)
            character-summary-map @(subscribe [::char/summary-map true])
            encounter-characters (remove
                                  #(-> % :character nil?)
                                  (map
                                   (fn [{:keys [creature]}]
                                     {:type :npc
                                      :character (-> creature :character character-summary-map)})
                                   (by-type :character)))
            monster-map @(subscribe [::monsters/monster-map])
            encounter-monsters (map
                                (fn [{:keys [creature]}]
                                  {:type :monster
                                   :num (:num creature)
                                   :monster (:monster creature)})
                                (by-type :monster))
            party-map @(subscribe [::party/party-map true])
            party-characters (into
                              (sorted-set-by #(compare (char-name %1) (char-name %2)))
                              (mapcat
                               (fn [party]
                                 (->> party
                                      party-map
                                      ::party/character-ids
                                      (map
                                       (fn [character]
                                         {:type :pc
                                          :character character}))))
                               parties))
            other-characters (into
                              (sorted-set-by #(compare (char-name %1) (char-name %2)))
                              (map
                               (fn [char-id]
                                 {:type :pc
                                  :character (character-summary-map char-id)})
                               characters))
            other-monsters (map
                            (fn [{:keys [monster num monster-data]}]
                              {:type :monster
                               :num num
                               :monster monster})
                            monsters)
            all-monsters (vals
                          (reduce
                           (fn [m {:keys [num monster] :as v}]
                             (update m
                                     monster
                                     (fn [x]
                                       (if x
                                         (assoc x :num (+ (get x :num 1) (or num 1)))
                                         (assoc v :monster (monster-map monster))))))
                           {}
                           (concat encounter-monsters
                                   other-monsters)))
            combatants (concat party-characters
                               other-characters
                               encounter-characters
                               all-monsters)]
        [:div.p-20.main-text-color
         [:div.flex.flex-wrap
          [:div.m-b-20.m-r-20
           [:div.f-s-24.f-w-b "Parties"]
           [:div
            (doall
             (map-indexed
              (fn [index party]
                ^{:key index}
                [:div.m-t-10.flex.align-items-end
                 [party-selector index party]
                 [:button.form-button.m-l-5.m-b-10
                  {:on-click #(dispatch [::combat/delete-party index])}
                  "delete"]])
              parties))]
           [:div.m-t-10.flex
            [party-selector (count parties) {}]]]
          [:div.m-b-20.m-r-20
           [:div.f-s-24.f-w-b "Encounters"]
           [:div
            (doall
             (map-indexed
              (fn [index encounter]
                ^{:key index}
                [:div.m-t-10.flex.align-items-end
                 [encounter-selector index encounter]
                 [:button.form-button.m-l-5.m-b-10
                  {:on-click #(dispatch [::combat/delete-encounter index])}
                  "delete"]])
              encounters))]
           [:div.m-t-10.flex
            [encounter-selector (count encounters) {}]]]
          [:div.m-b-20.m-r-20
           [:div.f-s-24.f-w-b "Characters"]
           [:div
            (doall
             (map-indexed
              (fn [index character]
                ^{:key index}
                [:div.m-t-10.flex.align-items-end
                 [character-selector
                  index
                  {:character character}
                  (on-character-change index)]
                 [:button.form-button.m-l-5.m-b-10
                  {:on-click #(dispatch [::combat/delete-character index])}
                  "delete"]])
              characters))]
           [:div.m-t-10.flex
            [character-selector
             (count characters)
             {}
             (on-character-change (count characters))]]]
          [:div.m-b-20.m-r-20
           [:div.f-s-24.f-w-b "Monsters"]
           [:div
            (doall
             (map-indexed
              (fn [index {:keys [monster num] :as cfg}]
                ^{:key index}
                [:div.m-t-10.flex.align-items-end
                 [monster-selector
                  index
                  cfg
                  (on-monster-change index)
                  (on-monster-num-change index)]
                 [:button.form-button.m-l-5.m-b-10
                  {:on-click #(dispatch [::combat/delete-monster index])}
                  "delete"]])
              monsters))]
           [:div.m-t-10.flex
            (let [monster-count (count monsters)]
              [monster-selector
               monster-count
               {}
               (on-monster-change monster-count)
               (on-monster-num-change monster-count)])]]]
         [:div.m-b-20
          [:div.flex.justify-cont-s-b
           [:div.f-s-24.f-w-b.m-b-10 "Initiative"]
           [:div.flex
            [:button.form-button.m-l-5.m-b-10
             {:on-click #(dispatch [::combat/next-initiative monster-map])}
             [:i.fa.fa-play]
             (when (not mobile?)
               [:span.m-l-5
                "next initiative"])]
            [:button.form-button.m-l-5.m-b-10
             {:on-click #(dispatch [::combat/set-combat-prop :ordered? true])}
             [:i.fa.fa-arrow-down]
             (when (not mobile?)
               [:span.m-l-5 "order"])]]]
          [:div.flex.flex-wrap
           [:div.m-b-20.m-r-20.t-a-c
            [:div.f-s-18.f-w-b "Current Initiative"]
            [:div.f-s-36.f-w-b
             (get tracker-item
                  :current-initiative
                  (->> tracker-item
                       :initiative
                       vals
                       (mapcat vals)
                       (apply max)))]]
           [:div.m-b-20.t-a-c
            [:div.f-s-18.f-w-b "Round"]
            [:div.f-s-36.f-w-b
             (get tracker-item :round 1)]]]
          [:div.f-s-18.f-w-b.m-b-10 "Combatants"]
          [:div.item-list
           (let [current-initiative (:current-initiative tracker-item)]
             (doall
              (map-indexed
               (fn [index {:keys [type character monster num] :as combatant}]
                 (let [path [:initiative type (or (:db/id character) (:key monster))]
                       initiative (get-in tracker-item path)]
                   ^{:key index}
                   [:div.item-list-item
                    [:div.flex.justify-cont-s-b.align-items-c
                     [:div.f-s-18.f-w-b.flex.flex-wrap.align-items-c.pointer.w-100-p
                      {:on-click #(swap! expanded-rows update path not)}
                      (when (and current-initiative
                               (= current-initiative initiative))
                        [:i.fa.fa-play.f-s-24.m-r-10])
                      [input-builder-field
                       [:span.f-w-b.f-s-12 "Initiative"]
                       initiative
                       #(dispatch [::combat/set-combat-path-prop path (js/parseInt %)])
                       {:class "input h-40 w-80 f-s-24 f-w-b m-r-10 m-t-10 m-b-10"
                        :type :number}]
                      [:div.m-r-10
                       [svg-icon
                        (case type
                          :pc "orc-head"
                          :npc "overlord-helm"
                          :monster "hydra")
                        48]]
                      (if character
                        ;; Pre-existing: "bob" is a hardcoded placeholder for
                        ;; the owner-name param — should be the actual owner.
                        [:div [character-summary-2 character true "bob" false]]
                        [:div.flex.align-items-c
                         [:div.p-t-20.p-b-20
                          [monster-summary
                           (:name monster)
                           (:size monster)
                           (:type monster)
                           (:subtypes monster)
                           (:alignment monster)]]
                         [:div.f-w-b.f-s-24 (str "(" (or num 0) ")")]
                         [:div.flex.flex-wrap
                          (doall
                           (map
                            (fn [i]
                              (let [{:keys [hit-points conditions]} (get-in monster-data [(:key monster) i])]
                                ^{:key i}
                                [:div.flex.flex-wrap.align-items-c.m-l-20.m-t-10.m-b-10
                                 [:div
                                  [:div.f-s-12 "hps"]
                                  [:div.m-r-5.f-s-24.f-w-b
                                   (or hit-points (get-in monster [:hit-points :mean]))]]
                                 [:div.flex.flex-wrap.align-items-c
                                  (doall
                                   (map
                                    (fn [{:keys [type]}]
                                      ^{:key type}
                                      [:div.m-l--5
                                       [svg-icon (get-in opt/conditions-map [type :icon]) 36]])
                                    conditions))]]))
                            (range num)))]])]
                     [:i.fa {:class (if (get @expanded-rows path)
                                           "fa-caret-up"
                                           "fa-caret-down")}]]
                    (when (get @expanded-rows path)
                      (if character
                        [character-display (:db/id character) false (if mobile? 1 2)]
                        [:div.p-t-10.p-b-10
                         [:div.w-100-p.m-b-20
                          [:div.flex.justify-cont-end
                           [:button.form-button.m-t-5
                            {:on-click #(dispatch [::combat/randomize-monster-hit-points combatant monster-map])}
                            "Randomize Hit Points"]]
                          [:div.flex.flex-wrap
                           (doall
                            (map
                             (fn [x]
                               ^{:key x}
                               [:div.m-r-5.p-20
                                [:div.f-w-b.f-s-18 (str "Monster " (inc x))]
                                [:div
                                 [:div.f-s-12.f-w-b "Hit Points"]
                                 [comps/input-field
                                  :input
                                  (get-in monster-data
                                          [(:key monster) x :hit-points]
                                          (get-in monster [:hit-points :mean]))
                                  #(dispatch [::combat/set-monster-hit-points combatant x (js/parseInt %)])
                                  {:type :number
                                   :class "input w-80"}]]
                                (let [current-round (dec (get tracker-item :round 1))
                                      conditions (get-in monster-data [(:key monster) x :conditions])
                                      used-conditions (into #{} (map :type) conditions)]
                                  [:div.m-t-10
                                   [:div.flex.w-100-p
                                    [:div.f-s-16.f-w-b
                                     w-160
                                     "Conditions"]
                                    (when (seq conditions)
                                      [:div.f-s-16.f-w-b.m-l-60 "Duration"])]
                                   (doall
                                    (map-indexed
                                     (fn [i condition]
                                       ^{:key i}
                                       [:div.m-b-10
                                        [condition-selector
                                         current-round
                                         (:key monster)
                                         x
                                         condition
                                         i
                                         true
                                         used-conditions]])
                                     conditions))
                                   [condition-selector
                                    current-round
                                    (:key monster)
                                    x
                                    {}
                                    (count conditions)
                                    false
                                    used-conditions]])])
                             (range (or num 1))))]]
                         [monster-component (monster-map (:key monster))]]))]))
               (if (:ordered? tracker-item)
                 (sort-by
                  (fn [{:keys [type character monster]}]
                    (let [key (or (:db/id character) (:key monster))]
                      (get-in tracker-item [:initiative type key])))
                  >
                  combatants)
                 combatants))))]]]))))

;;;; ====================================================================
;;;; Encounter Builder
;;;; ====================================================================

(defn encounter-input-field
  "Builder input field wrapper for encounter properties."
  [title prop encounter & [class-names type]]
  (builder-input-field title prop encounter ::encounters/set-encounter-prop class-names type))

(defn encounter-builder
  "Encounter creature composition builder — name, plugin source,
   and a dynamic list of creature selectors."
  []
  (let [{:keys [creatures] :as encounter} @(subscribe [::encounters/builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [encounter-input-field
       "Name"
       :name
       encounter
       "m-b-20"]
      [plugin-datalist
       option-source-name-label
       encounter
       ::encounters/set-encounter-prop]]
     [:div.m-t-20
      [:div.f-s-24.f-w-b "Creatures"]
      [:div
       (doall
        (map-indexed
         (fn [index details]
           ^{:key index}
           [:div.m-t-10 [creature-selector index details]])
         creatures))]
      [:div.m-t-10
       [creature-selector (count creatures) {}]]]]))

;;;; ====================================================================
;;;; Page Components (routed from core.cljs)
;;;; ====================================================================

(defn combat-tracker-page
  "Combat Tracker page — initiative tracker with reset button."
  []
  [content-page
   "Combat Tracker"
   [{:title "Reset"
     :icon "undo"
     :on-click #(dispatch [::combat/reset-combat])}]
   [combat-tracker]])

(defn encounter-builder-page
  "Encounter Builder page — standard builder wrapper for encounters."
  []
  (builder-page "Encounter" ::encounters/reset-encounter ::encounters/save-encounter encounter-builder))
