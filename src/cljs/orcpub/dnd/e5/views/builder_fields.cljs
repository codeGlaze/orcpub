(ns orcpub.dnd.e5.views.builder-fields
  "The labelled fields the homebrew builders are made of: text, number and long-text inputs and a
   dropdown, each under a field label."
  (:require [orcpub.components :as comps]
            [orcpub.fork.branding :as branding]))

(defn event-value
  "The value of the input a DOM event `e` came from."
  [e]
  (.. e -target -value))

(defn dropdown
  "A select of `items` ([{:value :title :disabled?}]) showing `value`; calls `on-change` with the
   picked value as a string."
  [{:keys [items value on-change]}]
  ;; Dedup items by :value as a safety net — duplicate option values from
  ;; homebrew plugins can slip through if imported before dedup was added.
  (let [unique-items (->> items
                          (reduce (fn [{:keys [seen result]} item]
                                    (let [v (:value item)]
                                      (if (contains? seen v)
                                        {:seen seen :result result}
                                        {:seen (conj seen v) :result (conj result item)})))
                                  {:seen #{} :result []})
                          :result)]
    [:select.builder-option.builder-option-dropdown.m-t-0
     {:value (or value "")
      :on-change #(on-change (event-value %))}
     (doall
      (map-indexed
       (fn [i {:keys [value title disabled?]}]
         ^{:key (str i "-" (or value title))}
         [:option.builder-dropdown-item
          (cond-> {:value value}
            disabled? (assoc :disabled true))
          title])
       unique-items))]))

(defn labeled-dropdown
  "A `dropdown` (cfg as there) under a bold `label`."
  [label cfg]
  [:div
   [:div.f-w-b.m-b-5 label]
   [dropdown cfg]])

(defn base-builder-field
  "A builder field: the label `name` over the component `comp`."
  [name comp]
  [:div.field.main-text-color.m-t-0
   [:div.personality-label.f-s-16 name]
   comp])

(defn builder-field
  "A labelled input of `el-type` (:input, :textarea) showing `value`; `on-change` gets the text."
  [el-type name value on-change attrs & [children]]
  (base-builder-field
   name
   [comps/input-field
    el-type
    value
    on-change
    attrs]))

(defn input-builder-field
  "A labelled text input; args as builder-field."
  [name value on-change attrs]
  [builder-field :input name value on-change attrs])

(defn textarea-field
  "A long-text input showing `value`, capped at the notes length; `on-change` gets the text."
  [{:keys [value on-change]}]
  [comps/input-field
   :textarea
   value
   on-change
   {:class-name "input"
    :maxLength (:notes branding/field-limits)}])

(defn number-field
  "A whole-number input showing `value`; `on-change` gets the number, or nil for anything else."
  [{:keys [value on-change]}]
  [comps/input-field
   :input
   value
   (fn [v]
     (on-change
      (when (re-matches #"\d+" v) (js/parseInt v))))
   {:class "input"
    :type :number
    :maxLength (:number branding/field-limits)}])
