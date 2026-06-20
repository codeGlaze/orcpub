(ns orcpub.components
  (:require [clojure.string :as s]
            #?(:cljs [reagent.core :refer [atom]])))

(defn checkbox
  "The app-wide checkbox: a rounded box, amber when checked. The original white-box
   look is preserved as a CSS fallback under the root class `classic-checkboxes`
   (see styles/core.clj) so a future user preference can switch back by toggling
   that one class — no markup change needed."
  [selected? disable?]
  [:span.checkbox-box
   {:class (str (when selected? "checked")
                (when disable? " disabled"))}
   (when selected?
     [:i.fa.fa-check])])

(defn labeled-checkbox [label selected? disabled? on-click]
  [:div.flex.pointer
   {:on-click on-click}
   [checkbox selected? disabled?]
   [:span.m-l-5 label]])

(def select-caret
  "Overlay caret for builder selects. It's a sibling element, NOT the select's
   background-image: a `background`/`background-color` shorthand on a <select>
   resets background-image:none, which silently wiped the old caret. An overlay
   can't be reset by anything set on the select, so the indicator can't regress."
  [:span.builder-select-caret
   [:svg {:width "14" :height "14" :viewBox "0 0 24 24" :fill "none"
          :stroke "currentColor" :stroke-width "2"
          :stroke-linecap "round" :stroke-linejoin "round"}
    [:polyline {:points "6 9 12 15 18 9"}]]])

(defn builder-select
  "Wrap a complete [:select …] hiccup with the overlay caret. wrap-attrs (a Hiccup
   attrs map) lands on the .builder-select-wrap span — move any layout classes that
   used to sit on the select (flex-grow-1, m-l-5, inline-block width) there."
  ([select] (builder-select {} select))
  ([wrap-attrs select]
   [:span.builder-select-wrap wrap-attrs
    select
    select-caret]))

(defn selection-item [key name selected?]
  [:option.builder-dropdown-item
   {:value key}
   name])

;; Form-2 component: resets <select> to placeholder after each on-change fires.
(defn selection-adder [values on-change]
  (let [selected-value (atom "")]
    (fn [values on-change]
      (builder-select
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
          values))]))))

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
