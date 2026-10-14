(ns orcpub.components
  (:require [clojure.string :as s]
            #?(:cljs [reagent.core :refer [atom]])))

(defn- press-to-click
  "A keydown handler that turns Space or Enter into a click on the element, so whatever handles
   its clicks (on it or an ancestor) handles the key too."
  [e]
  #?(:cljs (when (#{" " "Enter"} (.-key e))
             (.preventDefault e)
             (.click (.-currentTarget e)))
     :clj e))

(defn checkbox-mark
  "The box drawn alone, hidden from screen readers: for a row that is itself the control (a
   labelled checkbox, a settings switch). .checkbox in core.clj."
  [selected? disable?]
  [:span.checkbox
   {:class (str (when selected? "checked") (when disable? " disabled"))
    :aria-hidden true}])

(defn checkbox
  "A tick box, checked when `selected?` and dimmed when `disable?`: a checkbox to a screen reader,
   named `label` when given, reached with Tab, and Space or Enter click it, so the caller's click
   handler (on it or an ancestor) runs."
  ([selected? disable?] (checkbox selected? disable? nil))
  ([selected? disable? label]
   [:span.checkbox
    (cond-> {:class (str (when selected? "checked") (when disable? " disabled"))
             :role "checkbox"
             :aria-checked (str (boolean selected?))
             :tab-index (if disable? -1 0)}
      label (assoc :aria-label label)
      disable? (assoc :aria-disabled "true")
      (not disable?) (assoc :on-key-down press-to-click))]))

(defn labeled-checkbox
  "`label` beside a tick box, the two one checkbox control: clicked, or Space or Enter on it, calls
   `on-click`. Checked when `selected?`, dimmed and skipped when `disabled?`."
  [label selected? disabled? on-click]
  [:div.flex.align-items-c.pointer.checkbox-row
   (cond-> {:on-click on-click
            :role "checkbox"
            :aria-checked (str (boolean selected?))
            :tab-index (if disabled? -1 0)}
     disabled? (assoc :aria-disabled "true")
     (not disabled?) (assoc :on-key-down press-to-click))
   [checkbox-mark selected? disabled?]
   [:span.m-l-5 label]])

(defn selection-item [key name selected?]
  [:option.builder-dropdown-item
   {:value key}
   name])

;; Form-2 component: resets <select> to placeholder after each on-change fires.
(defn selection-adder [values on-change]
  (let [selected-value (atom "")]
    (fn [values on-change]
      [:select.builder-option.builder-option-dropdown
       {:value @selected-value
        :on-change (fn [e]
                     (let [v (-> e .-target .-value)]
                       (on-change e)
                       (reset! selected-value "")))}
       [:option.builder-dropdown-item
        {:value ""
         :disabled true}
        "<select to add>"]
       (doall
        (map
         (fn [{:keys [key name]}]
           ^{:key key}
           [selection-item key name false])
         values))])))

(defn selection [values on-change selected-value]
  [:select
   {:value selected-value
    :on-change on-change}
   (for [{:keys [key name]} values]
     ^{:key key} [selection-item key name])])

(defn input-field
  "Dispatches on-change on every keystroke. Build debounce lives in
   the :built-character subscription, not here. Local atom buffers the
   typed value so React doesn't flicker before re-frame catches up."
  []
  (let [local-val (atom nil)
        prev      (atom nil)]
    (fn [type value on-change attrs]
      ;; Subscription caught up — clear local override
      (when (not= value @prev)
        (reset! prev value)
        (reset! local-val nil))
      [type
       (merge
        attrs
        {:value (or @local-val value "")
         :on-click #(.stopPropagation %)
         :on-change (fn [e]
                      #?(:cljs
                         (let [v (.. e -target -value)]
                           (reset! local-val v)
                           (on-change v))))})])))

(defn int-field [value on-change attrs]
  [input-field
   :input
   value
   (fn [str-v]
     #?(:cljs
        (let [v (when (not (s/blank? str-v))
                  (js/parseInt str-v))]
          (on-change v))))
   (assoc attrs :type :number)])
