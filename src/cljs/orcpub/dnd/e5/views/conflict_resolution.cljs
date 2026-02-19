(ns orcpub.dnd.e5.views.conflict-resolution
  "Conflict resolution modal, export warning modal, and combined overlay.
   Handles import key conflicts and missing-field warnings during orcbrew export."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [clojure.string :as s]
            [orcpub.dnd.e5.views.import-log :as import-log]))

(defn conflict-resolution-item
  "Renders a single conflict with resolution options."
  [{:keys [id type key content-type-name sources
           import-source import-name existing-source existing-name
           suggested-renames suggested-new-key] :as conflict}
   decision]
  (let [selected-action (:action decision)]
    [:div {:style {:background "#3a3a3a"
                   :border-radius "4px"
                   :padding "12px"
                   :margin-bottom "8px"
                   :border "1px solid #555"}}

     ;; Conflict description
     [:div {:style {:margin-bottom "10px"}}
      [:span {:style {:font-weight "bold" :color "#ff9800" :font-size "14px"}}
       (str ":" (clojure.core/name key))]
      [:span {:style {:color "#ccc" :margin-left "8px"}}
       (str "(" content-type-name ")")]]

     (if (= type :internal)
       ;; Internal conflict: same key in multiple sources within import
       [:div
        [:div {:style {:font-size "13px" :color "#ddd" :margin-bottom "8px"}}
         "This key appears in multiple sources within the import file:"]
        [:div {:style {:margin-left "12px"}}
         (for [{:keys [source name]} sources]
           ^{:key source}
           [:div {:style {:font-size "13px" :margin-bottom "6px" :color "#fff"}}
            [:strong {:style {:color "#64b5f6"}} source]
            (when name [:span {:style {:color "#bbb"}} (str " - " name)])])]]

       ;; External conflict: imported key conflicts with existing
       [:div
        [:div {:style {:font-size "13px" :color "#ddd" :margin-bottom "8px"}}
         "This key conflicts with existing content:"]
        [:div {:style {:margin-left "12px" :font-size "13px"}}
         [:div {:style {:margin-bottom "6px" :color "#fff"}}
          [:span {:style {:color "#bbb"}} "Import: "]
          [:strong {:style {:color "#64b5f6"}} import-name]
          [:span {:style {:color "#999"}} (str " from " import-source)]]
         [:div {:style {:color "#fff"}}
          [:span {:style {:color "#bbb"}} "Existing: "]
          [:strong {:style {:color "#81c784"}} existing-name]
          [:span {:style {:color "#999"}} (str " from " existing-source)]]]])

     ;; Resolution options
     [:div {:style {:margin-top "12px" :border-top "1px solid #555" :padding-top "12px"}}
      [:div {:style {:font-size "12px" :color "#aaa" :margin-bottom "10px" :font-weight "bold"}} "Choose resolution:"]

      ;; Option: Rename import
      [:div {:style {:margin-bottom "8px" :padding "8px" :background "#2d2d2d" :border-radius "4px"}}
       [:label {:style {:display "flex" :align-items "center" :cursor "pointer" :color "#fff"}}
        [:input {:type "radio"
                 :name (str "conflict-" id)
                 :checked (= selected-action :rename-import)
                 :on-change #(dispatch [:set-conflict-decision id
                                        {:action :rename-import
                                         :source (or import-source (-> sources first :source))
                                         :new-key (or suggested-new-key
                                                      (-> suggested-renames first :new-key))}])
                 :style {:margin-right "10px" :width "16px" :height "16px"}}]
        [:span {:style {:color "#eee"}} "Rename imported key to: "]
        [:code {:style {:background "#1a1a1a" :padding "3px 8px" :border-radius "3px" :margin-left "6px" :color "#4fc3f7" :font-weight "bold"}}
         (str ":" (clojure.core/name (or suggested-new-key (-> suggested-renames first :new-key))))]]]

      ;; Option: Keep both (override)
      [:div {:style {:margin-bottom "8px" :padding "8px" :background "#2d2d2d" :border-radius "4px"}}
       [:label {:style {:display "flex" :align-items "center" :cursor "pointer" :color "#fff"}}
        [:input {:type "radio"
                 :name (str "conflict-" id)
                 :checked (= selected-action :keep-both)
                 :on-change #(dispatch [:set-conflict-decision id {:action :keep-both}])
                 :style {:margin-right "10px" :width "16px" :height "16px"}}]
        [:span {:style {:color "#eee"}} "Keep both (imported will override existing)"]]]

      ;; Option: Skip
      [:div {:style {:padding "8px" :background "#2d2d2d" :border-radius "4px"}}
       [:label {:style {:display "flex" :align-items "center" :cursor "pointer" :color "#fff"}}
        [:input {:type "radio"
                 :name (str "conflict-" id)
                 :checked (= selected-action :skip)
                 :on-change #(dispatch [:set-conflict-decision id {:action :skip}])
                 :style {:margin-right "10px" :width "16px" :height "16px"}}]
        [:span {:style {:color "#eee"}} "Skip this item (don't import)"]]]]]))

(defn conflict-resolution-modal []
  (let [resolution @(subscribe [:conflict-resolution])
        {:keys [active? import-name conflicts decisions]} resolution
        all-decided? (every? #(contains? decisions (:id %)) conflicts)]
    (when active?
      [:div {:style {:position "fixed"
                     :top 0
                     :left 0
                     :right 0
                     :bottom 0
                     :background "rgba(0,0,0,0.7)"
                     :z-index 2000
                     :display "flex"
                     :align-items "center"
                     :justify-content "center"}}
       [:div {:style {:background "#2a2a2a"
                      :border-radius "8px"
                      :max-width "600px"
                      :max-height "80vh"
                      :overflow "hidden"
                      :display "flex"
                      :flex-direction "column"
                      :box-shadow "0 4px 20px rgba(0,0,0,0.5)"}}

        ;; Header
        [:div {:style {:padding "16px 20px"
                       :border-bottom "1px solid #444"
                       :background "#333"}}
         [:div {:style {:font-size "18px" :font-weight "bold" :color "#f0ad4e"}}
          "\u26a0\ufe0f Key Conflicts Detected"]
         [:div {:style {:font-size "12px" :color "#888" :margin-top "4px"}}
          (str "Importing: " import-name)]
         [:div {:style {:font-size "12px" :color "#aaa" :margin-top "8px"}}
          (str (count conflicts) " conflict(s) need resolution before import can continue.")]]

        ;; Conflict list
        [:div {:style {:padding "16px 20px"
                       :overflow-y "auto"
                       :flex 1}}
         (for [conflict conflicts]
           ^{:key (:id conflict)}
           [conflict-resolution-item conflict (get decisions (:id conflict))])]

        ;; Footer with buttons
        [:div {:style {:padding "16px 20px"
                       :border-top "1px solid #444"
                       :display "flex"
                       :justify-content "flex-end"
                       :gap "12px"}}
         [:button {:style {:padding "8px 16px"
                           :background "#555"
                           :color "white"
                           :border "none"
                           :border-radius "4px"
                           :cursor "pointer"}
                   :on-click #(dispatch [:cancel-conflict-resolution])}
          "Cancel Import"]
         [:button {:style {:padding "8px 16px"
                           :background "#ff9800"
                           :color "white"
                           :border "none"
                           :border-radius "4px"
                           :cursor "pointer"}
                   :on-click #(dispatch [:rename-all-conflicts])}
          "Rename All"]
         [:button {:style {:padding "8px 16px"
                           :background (if all-decided? "#5cb85c" "#3a5f3a")
                           :color "white"
                           :border "none"
                           :border-radius "4px"
                           :cursor (if all-decided? "pointer" "not-allowed")
                           :opacity (if all-decided? 1 0.6)}
                   :disabled (not all-decided?)
                   :on-click #(when all-decided?
                               (dispatch [:apply-conflict-resolutions]))}
          (if all-decided?
            "Apply & Import"
            (str "Resolve All (" (count decisions) "/" (count conflicts) ")"))]]]])))

(def content-type-display-names
  "Human-readable names for content types"
  {:orcpub.dnd.e5/classes "Classes"
   :orcpub.dnd.e5/subclasses "Subclasses"
   :orcpub.dnd.e5/races "Races"
   :orcpub.dnd.e5/subraces "Subraces"
   :orcpub.dnd.e5/backgrounds "Backgrounds"
   :orcpub.dnd.e5/feats "Feats"
   :orcpub.dnd.e5/spells "Spells"
   :orcpub.dnd.e5/monsters "Monsters"
   :orcpub.dnd.e5/invocations "Invocations"
   :orcpub.dnd.e5/languages "Languages"
   :orcpub.dnd.e5/selections "Selections"
   :orcpub.dnd.e5/encounters "Encounters"})

(defn export-warning-modal []
  (let [warning @(subscribe [:export-warning])
        {:keys [active? name issues warnings]} warning]
    (when active?
      [:div {:style {:position "fixed"
                     :top 0
                     :left 0
                     :right 0
                     :bottom 0
                     :background "rgba(0,0,0,0.7)"
                     :z-index 2000
                     :display "flex"
                     :align-items "center"
                     :justify-content "center"}}
       [:div {:style {:background "#2a2a2a"
                      :border-radius "8px"
                      :max-width "600px"
                      :max-height "80vh"
                      :overflow "hidden"
                      :display "flex"
                      :flex-direction "column"
                      :box-shadow "0 4px 20px rgba(0,0,0,0.5)"}}

        ;; Header
        [:div {:style {:padding "16px 20px"
                       :border-bottom "1px solid #444"
                       :background "#333"}}
         [:div {:style {:font-size "18px" :font-weight "bold" :color "#f0ad4e"}}
          "\u26a0\ufe0f Missing Required Fields"]
         [:div {:style {:font-size "12px" :color "#888" :margin-top "4px"}}
          (str "Exporting: " name)]
         [:div {:style {:font-size "12px" :color "#aaa" :margin-top "8px"}}
          "Some items are missing required fields (names, etc.). You can cancel and fix them, or export with placeholder data."]]

        ;; Issues list
        [:div {:style {:padding "16px 20px"
                       :overflow-y "auto"
                       :flex 1
                       :max-height "300px"}}
         (for [{:keys [content-type invalid-items]} issues]
           ^{:key content-type}
           [:div {:style {:margin-bottom "12px"}}
            [:div {:style {:font-weight "bold" :color "#ddd" :margin-bottom "6px"}}
             (get content-type-display-names content-type (clojure.core/name content-type))]
            [:ul {:style {:margin 0 :padding-left "20px"}}
             (for [{:keys [key name missing-fields traits-missing-names]} invalid-items]
               ^{:key key}
               [:li {:style {:color "#aaa" :font-size "13px" :margin-bottom "4px"}}
                [:span {:style {:color "#e0e0e0"}}
                 (or name (clojure.core/name key))]
                (when (seq missing-fields)
                  [:span {:style {:color "#f0ad4e" :margin-left "8px"}}
                   (str "missing: " (s/join ", " (map clojure.core/name missing-fields)))])
                (when (and traits-missing-names (pos? traits-missing-names))
                  [:span {:style {:color "#f0ad4e" :margin-left "8px"}}
                   (str traits-missing-names " trait(s) missing names")])])]])]

        ;; Footer with buttons
        [:div {:style {:padding "16px 20px"
                       :border-top "1px solid #444"
                       :display "flex"
                       :justify-content "flex-end"
                       :gap "12px"}}
         [:button {:style {:padding "8px 16px"
                           :background "#555"
                           :color "white"
                           :border "none"
                           :border-radius "4px"
                           :cursor "pointer"}
                   :on-click #(dispatch [:cancel-export])}
          "Cancel"]
         [:button {:style {:padding "8px 16px"
                           :background "#f0ad4e"
                           :color "#222"
                           :border "none"
                           :border-radius "4px"
                           :cursor "pointer"
                           :font-weight "bold"}
                   :on-click #(dispatch [:export-anyway])}
          "Export Anyway (with placeholders)"]]]])))

(defn import-log-overlay
  "Composite component rendering all import/export overlay UI.
   Mount this once in the app root."
  []
  [:div
   [import-log/import-log-button]
   [import-log/import-log-panel]
   [conflict-resolution-modal]
   [export-warning-modal]])
