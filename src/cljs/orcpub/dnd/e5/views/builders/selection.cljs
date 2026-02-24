(ns orcpub.dnd.e5.views.builders.selection
  "Selection homebrew builder page — named option lists that characters
   choose from during creation, typically tied to a class at a given level.

   Extracted from views.builders — imports shared builder infrastructure
   from the parent module."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [clojure.string :as s]
            [orcpub.common :as common]
            [orcpub.dnd.e5.selections :as selections]
            [orcpub.dnd.e5.views.common
             :refer [textarea-field]]
            [orcpub.dnd.e5.views.builders
             :refer [builder-input-field input-builder-field
                     plugin-datalist option-source-name-label
                     title-with-help selection-help
                     builder-page]]))


;;; ─── Field wrapper + validation ────────────────────────────────────

(defn selection-input-field [title prop selection & [class-names]]
  (builder-input-field title prop selection ::selections/set-selection-prop class-names))

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


;;; ─── Selection builder + page wrapper ──────────────────────────────

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

(defn selection-builder-page []
  (builder-page "Selection" ::selections/reset-selection ::selections/save-selection selection-builder [title-with-help "Selection Builder" selection-help]))
