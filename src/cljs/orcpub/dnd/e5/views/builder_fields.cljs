(ns orcpub.dnd.e5.views.builder-fields
  "The labelled fields the homebrew builders and the PDF options are made of: text, number and
   long-text inputs and a dropdown, each under a field label, and checkboxes with a ? help line."
  (:require [reagent.core :as r]
            [orcpub.components :as comps]
            [orcpub.fork.branding :as branding]))

(defn event-value
  "The value of the input a DOM event `e` came from."
  [e]
  (.. e -target -value))

(defn set-value [atom key e]
  (swap! atom assoc key (event-value e)))

(defn dropdown
  "A select of `items` ([{:value :title :disabled?}]) showing `value`. `on-change` gets the picked
  value as a string, or with :typed? the item's own :value of any type. :class-name styles it."
  [{:keys [items value on-change typed? class-name]}]
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
    ;; A <select>'s value is always a string, so the default path's :on-change yields a string the
    ;; caller must coerce back to the item's type (keyword/int/...). :typed? avoids that: option
    ;; values are indices and :on-change gets the selected item's original :value, any type incl.
    ;; nil and qualified keywords. See docs/kb/dropdown-value-coercion.md (D32).
    (if typed?
      (let [idx (first (keep-indexed (fn [i it] (when (= (:value it) value) i)) unique-items))]
        [:select.builder-option.builder-option-dropdown.m-t-0
         {:class class-name
          :value (if idx (str idx) "")
          :on-change #(on-change (:value (nth unique-items (js/parseInt (event-value %)))))}
         (doall
          (map-indexed
           (fn [i {:keys [title disabled?]}]
             ^{:key i}
             [:option.builder-dropdown-item
              (cond-> {:value (str i)}
                disabled? (assoc :disabled true))
              title])
           unique-items))])
      [:select.builder-option.builder-option-dropdown.m-t-0
       {:class class-name
        :value (or value "")
        :on-change #(on-change (event-value %))}
       (doall
        (map-indexed
         (fn [i {:keys [value title disabled?]}]
           ^{:key (str i "-" (or value title))}
           [:option.builder-dropdown-item
            (cond-> {:value value}
              disabled? (assoc :disabled true))
            title])
         unique-items))])))

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

(defn labeled-checkbox
  "A checkbox (ticked when `selected?`) with `label` beside it. Returns hiccup."
  [label selected?]
  [:div.flex.align-items-c.pointer.m-b-10
   (comps/checkbox selected? false label)
   [:span.m-l-5.f-s-14 label]])

(defn with-help
  "`row` with a ? appended to it, and the line that ? opens beneath the whole row.

   Appended rather than wrapped, because the row is a flex line: a help span
   holding its own text would put the explanation beside the label instead of
   under it. Click rather than hover -- the builder is used on phones, where a
   hover tooltip never opens -- with `title` for a mouse that rests on it anyway."
  [row help]
  (let [open? (r/atom false)]
    (fn [row help]
      [:div
       (conj row
             [:span.option-help.pointer.m-l-5
              {:title help
               :on-click (fn [e] (.stopPropagation e) (swap! open? not))}
              "?"])
       (when @open?
         [:div.option-help-text help])])))

(defn option-checkbox
  "One PDF option: its checkbox, and a ? holding `help`.

   The ? sits outside the clickable label so reading the explanation does not
   toggle the option."
  [label selected? on-click help]
  [:div.m-b-5
   [with-help
    [:div.flex.align-items-c
     [:div.pointer {:on-click on-click}
      [labeled-checkbox label selected?]]]
    help]])

(defn option-group
  "A titled group of options. The panel carries three -- what goes on the sheet,
   what cards follow it, and how either is inked -- because a flat list put the
   known/prepared choice, a sheet setting, in the middle of the card ones."
  [title & children]
  (into [:div.m-b-16
         [:div.option-group-title title]]
        children))
