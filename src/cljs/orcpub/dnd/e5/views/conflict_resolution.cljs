(ns orcpub.dnd.e5.views.conflict-resolution
  "Conflict resolution modal, export warning modal, and combined overlay.
   Handles import key conflicts and missing-field warnings during orcbrew export."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [clojure.string :as s]
            [orcpub.dnd.e5.views.import-log :as import-log]))

(def ^:private modal-backdrop
  {:position "fixed"
   :top 0 :left 0 :right 0 :bottom 0
   :background "rgba(0,0,0,0.6)"
   :z-index 10001
   :display "flex"
   :align-items "center"
   :justify-content "center"})

(def ^:private modal-container
  {:background "#1a1e28"
   :border-radius "5px"
   :max-width "600px"
   :max-height "80vh"
   :overflow "hidden"
   :display "flex"
   :flex-direction "column"
   :box-shadow "0 2px 6px 0 rgba(0,0,0,0.5)"})

(def ^:private modal-header
  {:padding "16px 20px"
   :border-bottom "1px solid rgba(255,255,255,0.15)"
   :background "#2c3445"})

(def ^:private modal-footer
  {:padding "16px 20px"
   :border-top "1px solid rgba(255,255,255,0.15)"
   :display "flex"
   :justify-content "flex-end"
   :gap "12px"})

(defn- radio-option
  "Styled radio option using FA icons instead of native inputs."
  [selected? on-click label]
  [:div.pointer
   {:style {:margin-bottom "8px" :padding "8px" :background "rgba(0,0,0,0.2)" :border-radius "5px"}
    :on-click on-click}
   [:label.flex.align-items-c.pointer
    [:i {:class (str "fa " (if selected? "fa-dot-circle-o" "fa-circle-o"))
         :style {:color (if selected? "#f0a100" "rgba(255,255,255,0.35)")
                 :font-size "16px" :margin-right "10px" :width "16px"}}]
    label]])

(defn conflict-resolution-item
  "Renders a single conflict with resolution options."
  [{:keys [id type key content-type-name sources
           import-source import-name existing-source existing-name
           suggested-renames suggested-new-key] :as conflict}
   decision]
  (let [selected-action (:action decision)]
    [:div {:style {:background "rgba(255,255,255,0.05)"
                   :border-radius "5px"
                   :padding "12px"
                   :margin-bottom "8px"
                   :border "1px solid rgba(255,255,255,0.2)"}}

     ;; Conflict description
     [:div {:style {:margin-bottom "10px"}}
      [:span.f-w-b.f-s-14 {:style {:color "#f0a100"}}
       (str ":" (clojure.core/name key))]
      [:span {:style {:color "rgba(255,255,255,0.7)" :margin-left "8px"}}
       (str "(" content-type-name ")")]]

     (if (= type :internal)
       ;; Internal conflict: same key in multiple sources within import
       [:div
        [:div.f-s-12 {:style {:color "rgba(255,255,255,0.7)" :margin-bottom "8px"}}
         "This key appears in multiple sources within the import file:"]
        [:div {:style {:margin-left "12px"}}
         (for [{:keys [source name]} sources]
           ^{:key source}
           [:div.f-s-12 {:style {:margin-bottom "6px" :color "white"}}
            [:strong {:style {:color "#47eaf8"}} source]
            (when name [:span {:style {:color "rgba(255,255,255,0.5)"}} (str " - " name)])])]]

       ;; External conflict: imported key conflicts with existing
       [:div
        [:div.f-s-12 {:style {:color "rgba(255,255,255,0.7)" :margin-bottom "8px"}}
         "This key conflicts with existing content:"]
        [:div.f-s-12 {:style {:margin-left "12px"}}
         [:div {:style {:margin-bottom "6px" :color "white"}}
          [:span {:style {:color "rgba(255,255,255,0.5)"}} "Import: "]
          [:strong {:style {:color "#47eaf8"}} import-name]
          [:span {:style {:color "rgba(255,255,255,0.35)"}} (str " from " import-source)]]
         [:div {:style {:color "white"}}
          [:span {:style {:color "rgba(255,255,255,0.5)"}} "Existing: "]
          [:strong {:style {:color "#70a800"}} existing-name]
          [:span {:style {:color "rgba(255,255,255,0.35)"}} (str " from " existing-source)]]]])

     ;; Resolution options
     [:div {:style {:margin-top "12px" :border-top "1px solid rgba(255,255,255,0.2)" :padding-top "12px"}}
      [:div.f-s-12.f-w-b {:style {:color "rgba(255,255,255,0.5)" :margin-bottom "10px"}} "Choose resolution:"]

      ;; Option: Rename import
      [radio-option
       (= selected-action :rename-import)
       #(dispatch [:set-conflict-decision id
                   {:action :rename-import
                    :source (or import-source (-> sources first :source))
                    :new-key (or suggested-new-key
                                 (-> suggested-renames first :new-key))}])
       [:span
        [:span "Rename imported key to: "]
        [:code {:style {:background "rgba(0,0,0,0.3)" :padding "3px 8px" :border-radius "3px"
                        :margin-left "6px" :color "#47eaf8" :font-weight "bold"}}
         (str ":" (clojure.core/name (or suggested-new-key (-> suggested-renames first :new-key))))]]]

      ;; Option: Keep both (override)
      [radio-option
       (= selected-action :keep-both)
       #(dispatch [:set-conflict-decision id {:action :keep-both}])
       [:span "Keep both (imported will override existing)"]]

      ;; Option: Skip
      [radio-option
       (= selected-action :skip)
       #(dispatch [:set-conflict-decision id {:action :skip}])
       [:span "Skip this item (don't import)"]]]]))

(defn conflict-resolution-modal []
  (let [resolution @(subscribe [:conflict-resolution])
        {:keys [active? import-name conflicts decisions]} resolution
        all-decided? (every? #(contains? decisions (:id %)) conflicts)]
    (when active?
      [:div {:style modal-backdrop}
       [:div {:style modal-container}

        ;; Header
        [:div {:style modal-header}
         [:div.flex.align-items-c
          [:i.fa.fa-exclamation-triangle.m-r-5 {:style {:color "#f0a100" :font-size "18px"}}]
          [:span.f-s-18.f-w-b {:style {:color "#f0a100"}} "Key Conflicts Detected"]]
         [:div.f-s-12 {:style {:color "rgba(255,255,255,0.35)" :margin-top "4px"}}
          (str "Importing: " import-name)]
         [:div.f-s-12 {:style {:color "rgba(255,255,255,0.5)" :margin-top "8px"}}
          (str (count conflicts) " conflict(s) need resolution before import can continue.")]]

        ;; Conflict list
        [:div {:style {:padding "16px 20px"
                       :overflow-y "auto"
                       :flex 1}}
         (for [conflict conflicts]
           ^{:key (:id conflict)}
           [conflict-resolution-item conflict (get decisions (:id conflict))])]

        ;; Footer with buttons
        [:div {:style modal-footer}
         [:span.link-button
          {:on-click #(dispatch [:cancel-conflict-resolution])}
          "Cancel Import"]
         [:button.form-button
          {:on-click #(dispatch [:rename-all-conflicts])}
          "Rename All"]
         [:button.form-button
          {:class (when-not all-decided? "disabled")
           :style (when-not all-decided? {:opacity 0.5 :cursor "not-allowed"})
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
      [:div {:style modal-backdrop}
       [:div {:style modal-container}

        ;; Header
        [:div {:style modal-header}
         [:div.flex.align-items-c
          [:i.fa.fa-exclamation-triangle.m-r-5 {:style {:color "#f0a100" :font-size "18px"}}]
          [:span.f-s-18.f-w-b {:style {:color "#f0a100"}} "Missing Required Fields"]]
         [:div.f-s-12 {:style {:color "rgba(255,255,255,0.35)" :margin-top "4px"}}
          (str "Exporting: " name)]
         [:div.f-s-12 {:style {:color "rgba(255,255,255,0.5)" :margin-top "8px"}}
          "Some items are missing required fields (names, etc.). You can cancel and fix them, or export with placeholder data."]]

        ;; Issues list
        [:div {:style {:padding "16px 20px"
                       :overflow-y "auto"
                       :flex 1
                       :max-height "300px"}}
         (for [{:keys [content-type invalid-items]} issues]
           ^{:key content-type}
           [:div {:style {:margin-bottom "12px"}}
            [:div.f-w-b {:style {:color "rgba(255,255,255,0.7)" :margin-bottom "6px"}}
             (get content-type-display-names content-type (clojure.core/name content-type))]
            [:ul {:style {:margin 0 :padding-left "20px"}}
             (for [{:keys [key name missing-fields traits-missing-names]} invalid-items]
               ^{:key key}
               [:li {:style {:color "rgba(255,255,255,0.5)" :font-size "12px" :margin-bottom "4px"}}
                [:span {:style {:color "rgba(255,255,255,0.8)"}}
                 (or name (clojure.core/name key))]
                (when (seq missing-fields)
                  [:span {:style {:color "#f0a100" :margin-left "8px"}}
                   (str "missing: " (s/join ", " (map clojure.core/name missing-fields)))])
                (when (and traits-missing-names (pos? traits-missing-names))
                  [:span {:style {:color "#f0a100" :margin-left "8px"}}
                   (str traits-missing-names " trait(s) missing names")])])]])]

        ;; Footer with buttons
        [:div {:style modal-footer}
         [:span.link-button
          {:on-click #(dispatch [:cancel-export])}
          "Cancel"]
         [:button.form-button
          {:on-click #(dispatch [:export-anyway])}
          "Export Anyway"]]]])))

(defn import-log-overlay
  "Composite component rendering all import/export overlay UI.
   Mount this once in the app root."
  []
  [:div
   [import-log/import-log-button]
   [import-log/import-log-panel]
   [conflict-resolution-modal]
   [export-warning-modal]])
