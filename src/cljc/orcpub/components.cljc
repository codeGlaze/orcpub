(ns orcpub.components
  (:require [clojure.string :as s]
            #?(:cljs [reagent.core :refer [atom]])))

(defn checkbox [selected? disable?]
  [:i.fa.fa-check.f-s-14.bg-white.b-color-gray.orange-shadow.pointer.b-1
   {:class (str (if selected? "black slight-text-shadow" "white transparent")
                     " "
                     (when disable?
                       "opacity-5"))}])

(defn labeled-checkbox [label selected? disabled? on-click]
  [:div.flex.pointer
   {:on-click on-click}
   [checkbox selected? disabled?]
   [:span.m-l-5 label]])

(defn selection-item [key name selected?]
  [:option.builder-dropdown-item
   {:value key}
   name])

(defn selection-adder [values on-change]
  [:select.builder-option.builder-option-dropdown
   {:value ""
    :on-change on-change}
   [:option.builder-dropdown-item
    {:value ""
     :disabled true}
    "<select to add>"]
   (doall
    (map
     (fn [{:keys [key name]}]
       ^{:key key}
       [selection-item key name false])
     values))])

(defn selection [values on-change selected-value]
  [:select
   {:value selected-value
    :on-change on-change}
   (for [{:keys [key name]} values]
     ^{:key key} [selection-item key name])])

(defn input-field
  "Debounced input: shows typed text immediately via local :temp-val,
   dispatches on-change after 500ms. Clears temp-val only when the
   parent value prop catches up (avoids flicker from the old
   clear-in-setTimeout approach)."
  []
  (let [state (atom {:timeout nil
                     :temp-val nil
                     :prev-value nil})]
    (fn [type value on-change attrs]
      ;; When parent value changes (subscription caught up or external
      ;; change), sync prev-value and clear stale temp-val.
      (when (not= value (:prev-value @state))
        (swap! state assoc :prev-value value :temp-val nil))
      [type
       (merge
        attrs
        {:value (or (:temp-val @state) value "")
         :on-click #(.stopPropagation %)
         :on-change (fn [e] #?(:cljs
                               (swap! state
                                      (fn [{:keys [timeout] :as s}]
                                        (when timeout
                                          (js/clearTimeout timeout))
                                        (let [v (.. e -target -value)]
                                          (assoc s
                                                 :timeout (js/setTimeout
                                                           (fn [] (on-change v))
                                                           500)
                                                 :temp-val v))))))})])))

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
