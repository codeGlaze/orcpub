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

(defn switch-attrs
  "Attributes for a `.dev-mode-switch`: its role and state, keyboard focus, and Space or Enter
   calling `handler` (the switch's click handler, given the key event). `handler` nil: disabled."
  [on? handler]
  (cond-> {:role "switch" :aria-checked (str (boolean on?)) :tabIndex (if handler 0 -1)}
    handler (assoc :on-key-down (fn [e]
                                  #?(:cljs (when (#{" " "Enter"} (.-key e))
                                             (.preventDefault e)
                                             (handler e))
                                     :clj e)))
    (nil? handler) (assoc :aria-disabled "true")))

(defn tab-key-target
  "Index of the tab an arrow key moves to among `n` tabs from index `i`: ArrowRight and ArrowLeft
   step with wrap-around, Home the first, End the last. Returns nil for any other key or `n` < 1."
  [n i k]
  (when (pos? n)
    (case k
      "ArrowRight" (mod (inc i) n)
      "ArrowLeft" (mod (dec i) n)
      "Home" 0
      "End" (dec n)
      nil)))

#?(:cljs
(defn step-tab!
  "Moves from the tab that `e` came from to the sibling tab in its closest [role=tablist] that
   `tab-key-target` names for the key, clicks it (automatic activation) and focuses it. Returns true
   when a tab was moved to, nil when the key moves nowhere."
  [e]
  (let [tab (.-currentTarget e)
        tabs (when-let [list (.closest tab "[role=tablist]")]
               (vec (array-seq (.querySelectorAll list "[role=tab]"))))
        i (when tabs (.indexOf tabs tab))]
    (when-let [target (and i (not (neg? i)) (tab-key-target (count tabs) i (.-key e)))]
      (let [el (nth tabs target)]
        (.preventDefault e)
        (.click el)
        (.focus el)
        true)))))

(defn tab-attrs
  "Attributes for a clickable tab: its role and selected state, the accessible name `label`, a roving
   tabindex (0 on the selected tab, -1 on the rest), Space or Enter calling `handler` (the tab's click
   handler, given the key event), and arrow, Home and End keys moving to and activating a sibling.
   With `{:id tab-id :panel-id id}` the tab also gets its id, and the selected tab aria-controls the
   one rendered panel."
  ([selected? label handler] (tab-attrs selected? label handler nil))
  ([selected? label handler {:keys [id panel-id]}]
   (cond-> {:role "tab" :aria-selected (str (boolean selected?)) :aria-label label
            :tabIndex (if selected? 0 -1)
            :on-key-down (fn [e]
                           #?(:cljs (if (#{" " "Enter"} (.-key e))
                                      (do (.preventDefault e)
                                          (handler e))
                                      (step-tab! e))
                              :clj e))}
     id (assoc :id id)
     (and selected? panel-id) (assoc :aria-controls panel-id))))

(defn tab-panel-attrs
  "Attributes for the one shown tab panel: role tabpanel with id `panel-id`, labelled by the selected
   tab whose id is `tab-id`."
  [panel-id tab-id]
  {:role "tabpanel" :id panel-id :aria-labelledby tab-id})

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
   handler is called with, passed through `arg-fn` when given (an input's on-change, for one).
   GOTCHA: `arg-fn` must be a top-level function; an inline fn is new each render and grows the cache."
  (memoize
   (fn [event-kw & [arg-fn]]
     #(dispatch [event-kw (if arg-fn (arg-fn %) %)])))))

(def text-color "#484848")

(defn expand-button
  "An orange underlined toggle with a caret: `collapse-text` while `expanded?` (an atom) is false,
   `expand-text` while true. A click flips the atom and stops there."
  [expand-text collapse-text expanded?]
  [:span.flex.pointer.align-items-c.justify-cont-end
   {:on-click (fn [e]
                (swap! expanded? not)
                #?(:cljs (.stopPropagation e)))}
   [:span.underline.orange.p-0.m-r-2 (if @expanded? expand-text collapse-text)]
   [:i.fa.orange
    {:class (if @expanded? "fa-caret-up" "fa-caret-down")}]])

(defn show-info-button
  "The \"show info\" / \"hide info\" toggle for an option's help, flipping `expanded?` (an atom)."
  [expanded?]
  [:div.f-w-n.m-l-5 [expand-button "hide info" "show info" expanded?]])

(defn themed-icon
  "Icon `icon-name` from /image at `size` px (32 by default): the black set, faded, in the light
   theme (`theme` \"light-theme\"), else the white set."
  [icon-name size theme]
  (let [light-theme? (= "light-theme" theme)
        size (or size 32)]
    [:img.svg-icon
     {:style {:height (str size "px")
              :width (str size "px")}
      :class (when light-theme? " opacity-7")
      ;; Decorative: every icon sits beside its text or inside a labelled control.
      :alt ""
      :src (str (if light-theme? "/image/black/" "/image/") icon-name ".svg")}]))
