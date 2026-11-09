(ns orcpub.components
  (:require [clojure.string :as s]
            #?(:cljs [re-frame.core :refer [dispatch]])
            #?(:cljs [reagent.core :as r :refer [atom]])))

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

(def orange "#f0a100")

(defn base-input
  "One auth field: a notched-outline label, an optional reveal button, and the
   notice slot `messages`/`hint` render into.
   GOTCHA: both label positions are always rendered and the stylesheet picks
   via :placeholder-shown / .is-wrong, so a live check and a submit lay out
   the same. :messages, :hint, :reveal? are optional; other keys pass to <input>.
   See account-flows.md."
  [{:keys [title messages hint action reveal? revealed? on-reveal] :as attrs}]
  (let [id (str "f-" (name (or (:name attrs) (:key attrs) (gensym "x"))))
        message-id (str id "-message")
        wrong? (boolean (seq messages))
        input-attrs (-> attrs
                        (dissoc :title :messages :hint :action :reveal? :revealed? :on-reveal :key)
                        (assoc :id id
                               ;; the selector the notch is driven by
                               :placeholder " "
                               :aria-describedby message-id)
                        (cond-> wrong? (assoc :aria-invalid true)))]
    [:div.field {:class (when wrong? "is-wrong")}
     [:label.lift {:for id} title]
     [:div.field-notice.is-error {:id message-id}
      (when wrong? [:span.field-notice-what (first messages)])]
     [:div.field-box
      [:input input-attrs]
      [:label.notch {:for id} title]
      (when reveal?
        [:button.peek {:type "button"
                       :aria-controls id
                       :aria-pressed (boolean revealed?)
                       :on-click on-reveal}
         (if revealed? "Hide" "Show")])]
     (when (or hint action)
       [:div.field-notice.is-note
        (when hint [:span.field-notice-what hint])
        (when-let [{:keys [label on-choose]} action]
          ;; on-mouse-down, NOT on-click: blur re-renders this notice and the
          ;; button is gone before mouseup, so a click handler here never fires.
          ;; preventDefault stops focus moving at all.
          [:button.field-notice-action
           {:type "button"
            :on-mouse-down (fn [e] (.preventDefault e) (on-choose))}
           label])])]))

#?(:cljs
(defn form-input []
  (let [blurred? (r/atom false)
        revealed? (r/atom false)]
    ;; show-errors? is how a submit reveals faults in fields nobody has visited.
    ;; Without it, pressing the button on a form with an untouched empty field
    ;; had nothing to say, which is why the button used to dim instead.
    (fn [{:keys [title key value messages type on-change show-errors? hint action reveal?]
          reveal-state :revealed? on-reveal :on-reveal}]
      ;; The reveal is usually this field's own business. A password and its
      ;; confirmation have to share one, so the caller can own it instead.
      (let [shown (when (or @blurred? show-errors?) messages)
            shown-as-text? (if (some? reveal-state) reveal-state @revealed?)
            flip (or on-reveal #(swap! revealed? not))]
        [base-input
         {:name key
          ;; A revealed password is a text field. The type is the reveal.
          :type (if (and reveal? shown-as-text?) :text type)
          :value value
          :title title
          :messages shown
          :hint hint
          :action action
          :reveal? reveal?
          :revealed? shown-as-text?
          :on-reveal flip
          :on-focus (fn [_] (reset! blurred? false))
          :on-change on-change
          :on-blur (fn [_] (reset! blurred? true))}])))))

#?(:cljs
(def make-event-handler
  (memoize
   (fn [event-kw & args]
     #(dispatch (vec (cons event-kw args)))))))

#?(:cljs
(def make-arg-event-handler
  "A handler, memoized per arguments, dispatching [`event-kw` value], the value being what the
   handler is called with, passed through `arg-fn` when given (an input's on-change, for one)."
  (memoize
   (fn [event-kw & [arg-fn]]
     #(dispatch [event-kw (if arg-fn (arg-fn %) %)])))))

(def text-color "#484848")
