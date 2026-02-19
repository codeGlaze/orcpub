(ns orcpub.dnd.e5.views.conflict-resolution
  "Conflict resolution modal, export warning modal, and combined overlay.
   Handles import key conflicts and missing-field warnings during orcbrew export."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [clojure.string :as s]
            [orcpub.dnd.e5.views.import-log :as import-log]))

(defn- radio-option
  "Styled radio option with color-coded left border for visual identity.
   variant is one of :rename, :keep, :skip — maps to CSS class."
  [selected? on-click label variant]
  [:div {:class (str "conflict-radio conflict-radio-" (name variant)
                     (when selected? " selected"))
         :on-click on-click}
   [:label.flex.align-items-c.pointer
    [:i {:class (str "fa radio-icon "
                     (if selected? "fa-dot-circle-o" "fa-circle-o"))}]
    label]])

(defn conflict-resolution-item
  "Renders a single conflict with resolution options."
  [{:keys [id type key content-type-name sources
           import-source import-name existing-source existing-name
           suggested-renames suggested-new-key] :as conflict}
   decision]
  (let [selected-action (:action decision)]
    [:div.conflict-item

     ;; Conflict description
     [:div.conflict-item-header
      [:span.f-w-b.f-s-14.conflict-item-key
       (str ":" (clojure.core/name key))]
      [:span.conflict-item-type
       (str "(" content-type-name ")")]]

     (if (= type :internal)
       ;; Internal conflict: same key in multiple sources within import
       [:div
        [:div.f-s-12.conflict-item-desc
         "This key appears in multiple sources within the import file:"]
        [:div.conflict-item-detail
         (for [{:keys [source name]} sources]
           ^{:key source}
           [:div.f-s-12.conflict-source-row
            [:strong.conflict-source-import source]
            (when name [:span.conflict-source-label (str " - " name)])])]]

       ;; External conflict: imported key conflicts with existing
       [:div
        [:div.f-s-12.conflict-item-desc
         "This key conflicts with existing content:"]
        [:div.f-s-12.conflict-item-detail
         [:div.conflict-source-row
          [:span.conflict-source-label "Import: "]
          [:strong.conflict-source-import import-name]
          [:span.conflict-source-origin (str " from " import-source)]]
         [:div.conflict-source-row
          [:span.conflict-source-label "Existing: "]
          [:strong.conflict-source-existing existing-name]
          [:span.conflict-source-origin (str " from " existing-source)]]]])

     ;; Resolution options
     [:div.conflict-options
      [:div.conflict-options-label "Choose resolution:"]

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
        [:code.conflict-code
         (str ":" (clojure.core/name (or suggested-new-key (-> suggested-renames first :new-key))))]]
       :rename]

      ;; Option: Keep both (override)
      [radio-option
       (= selected-action :keep-both)
       #(dispatch [:set-conflict-decision id {:action :keep-both}])
       [:span "Keep both (imported will override existing)"]
       :keep]

      ;; Option: Skip
      [radio-option
       (= selected-action :skip)
       #(dispatch [:set-conflict-decision id {:action :skip}])
       [:span "Skip this item (don't import)"]
       :skip]]]))

(defn conflict-resolution-modal []
  (let [resolution @(subscribe [:conflict-resolution])
        {:keys [active? import-name conflicts decisions]} resolution
        all-decided? (every? #(contains? decisions (:id %)) conflicts)]
    (when active?
      [:div.conflict-backdrop
       [:div.conflict-modal

        ;; Header
        [:div.conflict-modal-header
         [:div.flex.align-items-c
          [:i.fa.fa-exclamation-triangle.m-r-5.conflict-title-icon]
          [:span.f-s-18.f-w-b.conflict-title "Key Conflicts Detected"]]
         [:div.f-s-12.conflict-subtitle
          (str "Importing: " import-name)]
         [:div.f-s-12.conflict-count
          (str (count conflicts) " conflict(s) need resolution before import can continue.")]]

        ;; Conflict list
        [:div.conflict-modal-body
         (for [conflict conflicts]
           ^{:key (:id conflict)}
           [conflict-resolution-item conflict (get decisions (:id conflict))])]

        ;; Footer with buttons
        [:div.conflict-modal-footer
         [:span.link-button
          {:on-click #(dispatch [:cancel-conflict-resolution])}
          "Cancel Import"]
         [:button.form-button
          {:on-click #(dispatch [:rename-all-conflicts])}
          "Rename All"]
         [:button.form-button
          {:class (when-not all-decided? "disabled")
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
      [:div.conflict-backdrop
       [:div.conflict-modal

        ;; Header
        [:div.conflict-modal-header
         [:div.flex.align-items-c
          [:i.fa.fa-exclamation-triangle.m-r-5.conflict-title-icon]
          [:span.f-s-18.f-w-b.conflict-title "Missing Required Fields"]]
         [:div.f-s-12.conflict-subtitle
          (str "Exporting: " name)]
         [:div.f-s-12.conflict-count
          "Some items are missing required fields (names, etc.). You can cancel and fix them, or export with placeholder data."]]

        ;; Issues list
        [:div.conflict-modal-body {:style {:max-height "300px"}}
         (for [{:keys [content-type invalid-items]} issues]
           ^{:key content-type}
           [:div {:style {:margin-bottom "12px"}}
            [:div.export-issue-type
             (get content-type-display-names content-type (clojure.core/name content-type))]
            [:ul {:style {:margin 0 :padding-left "20px"}}
             (for [{:keys [key name missing-fields traits-missing-names]} invalid-items]
               ^{:key key}
               [:li.export-issue-item
                [:span.export-issue-name
                 (or name (clojure.core/name key))]
                (when (seq missing-fields)
                  [:span.export-issue-missing
                   (str "missing: " (s/join ", " (map clojure.core/name missing-fields)))])
                (when (and traits-missing-names (pos? traits-missing-names))
                  [:span.export-issue-missing
                   (str traits-missing-names " trait(s) missing names")])])]])]

        ;; Footer with buttons
        [:div.conflict-modal-footer
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
