(ns orcpub.dnd.e5.views.common
  "Shared UI infrastructure for all views.* feature modules.

   Extracted from the views.cljs monolith to break circular dependencies
   and enable modular view splitting. Contains:
   - Style constants and CSS maps
   - Form utilities (inputs, validation, value extraction)
   - Icon and display section components
   - Re-frame dispatch wrappers and route navigation
   - Memoized event handler factories
   - Form input components (dropdowns, builder fields)
   - PDF export helpers
   - Messaging and confirmation UI
   - Registration page layout wrapper
   - Generic page header component

   No def in this file should depend on any views.* sibling module.
   All dependencies flow one way: common <- feature modules <- orchestrator."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [orcpub.common :as common]
            [orcpub.components :as comps]
            [orcpub.route-map :as routes]
            [orcpub.pdf-spec :as pdf-spec]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.views-2 :as views-2]
            [clojure.string :as s]))

;;;; ====================================================================
;;;; Constants & Styles
;;;; ====================================================================

;; Threshold: use dropdown (not checkboxes) when action count exceeds this
(def actions-amount-many 5)

(def text-color "#484848")

(def orange "#f0a100")

(def input-style
  {:height "38px"
   :border-style "solid"
   :border-width "1px"
   :border-radius "3px"
   :font-size "14px"
   :padding-left "10px"
   :color text-color})

(def default-input-style
  (merge
   input-style
   {:border-color "rgba(72,72,72,0.37)"}))

(def login-style
  {:color "#f0a100"})

(def active-style {:background-color "rgba(240, 161, 0, 0.7)"})

(def loading-style
  {:position :fixed
   :height "100%"
   :width "100%"
   :top 0
   :bottom 0
   :right 0
   :left 0
   :z-index 100
   :background-color "rgba(0,0,0,0.6)"})

;; Registration layout styles (used by auth pages)
(def registration-content-style
  {:background-color :white
   :border "1px solid white"
   :color text-color})

(def registration-page-style
  {:background-image "url(/image/login-side.jpg)"
   :background-clip :content-box
   :width "350px"
   :min-height "600px"})

(def registration-left-column-style
  {:flex-direction :column
   :width "435px"})

(def registration-header-style
  {:height "65px"
   :background-color "#1a2532"
   :border-right "1px solid white"})

;;;; ====================================================================
;;;; Form Utilities
;;;; ====================================================================

(defn event-value
  "Extract the value from a DOM input event."
  [e]
  (.. e -target -value))

(defn set-value
  "Update an atom's key with the value from a DOM input event."
  [atom key e]
  (swap! atom assoc key (event-value e)))

(defn validation-messages
  "Render a bulleted list of validation error messages."
  [messages]
  (when messages
    [:ul.t-a-l.p-l-20.p-r-20.m-b-10
     (doall
      (map-indexed
       (fn [i msg]
         ^{:key i}
         [:li.red (str common/dot-char " " msg)])
       messages))]))

(defn base-input
  "Minimal labeled input wrapper — placeholder text shown above the input."
  [attrs]
  [:div.m-b-10
   [:div.f-s-10.t-a-l.m-l-10 (:placeholder attrs)]
   [:div.flex.p-l-10.p-l-10.p-r-10
    [:input.flex-grow-1
     (merge
      attrs
      ;; Rem'd out to allow auto fill on user/password
      ;;{:auto-complete :off}
     )]]])

(defn form-input
  "Input with validation messages that appear on blur.
   Reagent Form-2 component (returns render fn from closure)."
  []
  (let [blurred? (r/atom false)]
    (fn [{:keys [title key value messages type on-change]}]
      [:div
       [base-input
        {:name key
         :type type
         :value value
         :placeholder title
         :style input-style
         :class (if (and @blurred? (seq messages))
                       "b-red"
                       "b-gray")
         :on-focus (fn [_] (reset! blurred? false))
         :on-change on-change
         :on-blur (fn [e] (reset! blurred? true))}]
       (when @blurred? (validation-messages messages))])))

;;;; ====================================================================
;;;; Icons & Display Components
;;;; ====================================================================

(defn svg-icon
  "Render a themed SVG icon. Uses light/dark image paths based on theme sub."
  [icon-name & [size theme-override]]
  (let [theme (or theme-override @(subscribe [:theme]))
        light-theme? (= "light-theme" theme)
        size (or size 32)]
    [:img.svg-icon
     {:style {:height (str size "px")
              :width (str size "px")}
      :class (when light-theme? " opacity-7")
      :src (str (if light-theme? "/image/black/" "/image/") icon-name ".svg")}]))

(defn display-section
  "Section with optional icon, title, value, and action buttons."
  [title icon-name value & [list? buttons]]
  [:div.m-t-20
   [:div.flex.justify-cont-s-b
    [:div.flex.align-items-c
     (when icon-name (svg-icon icon-name 32))
     [:span.m-l-5.f-s-16.f-w-600 title]]
    (when (seq buttons)
      (apply
       conj
       [:div]
       buttons))]
   [:div {:class (if list? "m-t-0" "m-t-4")}
    [:span.f-s-24.f-w-600
     value]]])

(defn list-display-section
  "Display section showing a comma-separated list of values."
  [title image-name values]
  (when (seq values)
    (display-section
     title
     image-name
     [:span.m-t-5.f-s-14.f-w-n.i
      (s/join
       ", "
       values)]
     true)))

(defn list-item-section
  "Display section for a list of maps, extracting :name (or custom fn) from each."
  [list-name icon-name items & [name-fn]]
  [list-display-section list-name icon-name
   (map
    (fn [item]
      ((or name-fn :name) item))
    items)])

(defn character-display-name
  "Return the character's name, or a descriptive fallback like 'High Elf Ranger 3'
   built from race/class/level when the name is blank. Works on summary maps."
  [{:keys [::char/character-name ::char/race-name ::char/subrace-name ::char/classes]}]
  (if (s/blank? character-name)
    (let [race-part (or subrace-name race-name)
          class-str (when (seq classes)
                      (s/join "/"
                              (map (fn [{:keys [::char/class-name ::char/level]}]
                                     (str class-name " " level))
                                   classes)))]
      (cond
        (and race-part class-str) (str race-part " " class-str)
        race-part race-part
        class-str class-str
        :else "New Character"))
    character-name))

(defn details-button
  "Toggle button showing 'details'/'hide' with caret icon."
  [expanded? on-click]
  [:span.orange.underline.pointer
   {:on-click on-click}
   [:span (if expanded? "hide" "details")]
   [:i.fa.m-l-5
    {:class (if expanded? "fa-caret-up" "fa-caret-down")}]])

(defn section-header-2
  "Small section header with optional icon and bold title."
  [title icon]
  [:div
   (when icon (svg-icon icon 24))
   [:div.f-s-18.f-w-b.m-b-5 title]])

;;;; ====================================================================
;;;; Dispatch Wrappers & Route Navigation
;;;; ====================================================================

(defn dispatch-logout []
  (dispatch [:logout]))

(defn dispatch-route-to-login [e]
  (.stopPropagation e)
  (dispatch [:route-to-login]))

(defn dispatch-route-to-my-account [e]
  (dispatch [:route :my-account]))

(defn route-fn
  "Create an event handler that dispatches a route and stops propagation."
  [route]
  (fn [e]
    (dispatch [:route route])
    (.stopPropagation e)))

(def route-handler
  "Memoized version of route-fn — avoids re-creating handler fns."
  (memoize route-fn))

(defn route-to-default-route []
  (dispatch [:route routes/default-route]))

(defn route-to-default-page []
  (dispatch [:route :default]))

(defn route-to-character-list-page []
  (dispatch [:route routes/dnd-e5-char-list-page-route]))

(defn route-to-spell-list-page []
  (dispatch [:route routes/dnd-e5-spell-list-page-route]))

(defn route-to-monster-list-page []
  (dispatch [:route routes/dnd-e5-monster-list-page-route]))

(defn route-to-item-list-page []
  (dispatch [:route routes/dnd-e5-item-list-page-route]))

(defn route-to-my-content-page []
  (dispatch [:route routes/dnd-e5-my-content-route]))

(defn route-to-my-encounters-page []
  (dispatch [:route routes/dnd-e5-my-encounters-route]))

(defn route-to-register-page []
  (dispatch [:route routes/register-page-route {:secure true :no-return? true}]))

(defn route-to-reset-password-page []
  (dispatch [:route routes/send-password-reset-page-route {:secure? true :no-return? true}]))

;;;; ====================================================================
;;;; Event Handler Factories
;;;; ====================================================================

(def make-event-handler
  "Memoized factory: (make-event-handler :event-kw arg1 arg2) returns
   an fn that dispatches [:event-kw arg1 arg2] when called."
  (memoize
   (fn [event-kw & args]
     #(dispatch (vec (cons event-kw args))))))

(def make-stop-prop-event-handler
  "Like make-event-handler but also calls .stopPropagation on the DOM event."
  (memoize
   (fn [event-kw & args]
     (fn [e]
       (dispatch (vec (cons event-kw args)))
       (.stopPropagation e)))))

(def make-arg-event-handler
  "Memoized factory: creates a dispatch handler that optionally transforms
   the DOM event with arg-fn before dispatching as the event's payload.
   (make-arg-event-handler :event js/parseInt) dispatches [:event (parseInt e)]"
  (memoize
   (fn [event-kw & [arg-fn]]
     #(dispatch [event-kw (if arg-fn (arg-fn %) %)]))))

;;;; ====================================================================
;;;; Form Input Components
;;;; ====================================================================

(defn dropdown
  "HTML select dropdown from a seq of {:keys [value title disabled?]} items.
   Deduplicates items by :value as a safety net — duplicate option values from
   homebrew plugins can slip through if imported before dedup was added."
  [{:keys [items value on-change]}]
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
  "Dropdown with a bold label above it."
  [label cfg]
  [:div
   [:div.f-w-b.m-b-5 label]
   [dropdown cfg]])

;; Builder field components — consistent wrappers around comps/input-field
;; for the homebrew builder forms

(defn base-builder-field
  "Wrapper: labeled field with personality-label styling."
  [name comp]
  [:div.field.main-text-color.m-t-0
   [:div.personality-label.f-s-16 name]
   comp])

(defn builder-field
  "Builder form field wrapping comps/input-field with a label."
  [el-type name value on-change attrs & [children]]
  (base-builder-field
   name
   [comps/input-field
    el-type
    value
    on-change
    attrs]))

(defn textarea-field
  "Textarea input using comps/input-field."
  [{:keys [value on-change]}]
  [comps/input-field
   :textarea
   value
   on-change
   {:class "input"}])

;;;; ====================================================================
;;;; PDF Export
;;;; ====================================================================

(defn export-pdf
  "Returns an onClick handler that generates and submits the character PDF.
   plugin-data map is pre-subscribed by the calling component."
  [built-char id plugin-data & [options]]
  (fn [_]
    (let [field (.getElementById js/document "fields-input")]
      (aset field "value" (str (pdf-spec/make-spec built-char id options plugin-data)))
      (.submit (.getElementById js/document "download-form")))))

(defn download-form
  "Hidden form element used by export-pdf to POST character data for PDF generation."
  [built-char]
  [:form.download-form
   {:id "download-form"
    :action (if (and js/window.location
                     (s/starts-with? js/window.location.href "http://localhost"))
              "http://localhost:8890/character.pdf"
              "/character.pdf")
    :method "POST"
    :target "_blank"}
   [:input {:type "hidden" :name "body" :id "fields-input"}]])

;;;; ====================================================================
;;;; Messaging & Confirmation UI
;;;; ====================================================================

(defn message
  "Dismissable message banner with color-coded background."
  [message-type message-text close-handler]
  [:div.pointer.f-w-b
   {:on-click close-handler}
   [:div.message
    {:class (case message-type
              :error "bg-red"
              :warning "bg-orange"
              "bg-green")}
    [:span message-text]
    [:i.fa.fa-times]]])

(defn hide-login-message []
  (dispatch [:hide-login-message]))

(defn hide-confirmation []
  (dispatch [:hide-confirmation]))

(defn hide-message []
  (dispatch [:hide-message]))

(defn confirm-fn
  "Create a handler that executes optional :pre callback then dispatches :event."
  [cfg]
  #(do
     (when (:pre cfg)
       ((:pre cfg)))
     (dispatch [:confirm (:event cfg)])))

(def confirm-handler
  "Memoized confirm-fn."
  (memoize confirm-fn))

;;;; ====================================================================
;;;; Registration Page Layout
;;;; ====================================================================

(defn registration-page
  "Full-page wrapper for auth flows (login, register, password reset).
   Shows the DMV logo, content area, legal footer, and side image."
  [content]
  [:div.sans.h-full.flex
   {:style {:flex-direction :column}}
   [:div.flex.justify-cont-s-a.align-items-c.flex-grow-1.h-100-p
    [:div.registration-content
     {:style registration-content-style}
     [:div.flex.h-100-p
      [:div.flex {:style registration-left-column-style}
       [:div.flex.justify-cont-s-a.align-items-c
        {:style registration-header-style}
        [:img.h-55.pointer
         {:src "/image/dmv-logo.svg"
          :on-click route-to-default-page}]]
       [:div.flex-grow-1 content]
       [views-2/legal-footer]]
      [:div.registration-image
       {:style registration-page-style}]]]]])

;;;; ====================================================================
;;;; Logo & Generic Layout Header
;;;; ====================================================================

(def logo
  "Site logo with click-to-home navigation."
  [:img.h-60.pointer
   {:src "/image/dmv-logo.svg"
    :on-click route-to-default-route}])

(defn header
  "Generic page header with title, action buttons, confirmation dialog,
   options panel, and message display. Used by content-page."
  [title button-cfgs & {:keys [frame?]}]
  (let [device-type @(subscribe [:device-type])]
    [:div.w-100-p
     [:div.flex.align-items-c.justify-cont-s-b.flex-wrap
      [:div.flex
       [:h1.f-s-36.f-w-b.m-t-5.m-l-10
        {:class (when (not= :mobile device-type) "m-t-21 m-b-20")}
        title]
       (when frame?
         logo)]
      [:div.flex.align-items-c.justify-cont-end.flex-wrap.m-r-10.m-l-10
       (map-indexed
        (fn [i {:keys [title icon on-click style class-name] :as cfg}]
          (if (vector? cfg)
            (with-meta
              cfg
              {:key i})
            ^{:key i}
            [:button.form-button.h-40.m-l-5.m-t-5.m-b-5
             {:on-click on-click
              :class class-name
              :style style}
             [:span
              [:i.fa.f-s-18
               (when icon {:class (str "fa-" icon)})]]
             [:span.m-l-5.header-button-text title]]
              ))
        button-cfgs)]]
     (when @(subscribe [:confirmation-shown?])
       [:div.flex.justify-cont-end.m-r-10.m-b-20.m-l-10
        (let [cfg @(subscribe [:confirmation-cfg])]
          [:div
           [:div.f-w-b (:question cfg)]
           [:div.flex.justify-cont-end.m-t-5
            [:button.form-button
             {:on-click hide-confirmation}
             "CANCEL"]
            [:button.link-button.underline.f-w-b
             {:on-click (confirm-handler cfg)}
             (:confirm-button-text cfg)]]])])
     (when @(subscribe [::char/options-shown?])
       [:div.bg-light.m-b-10 @(subscribe [::char/options-component])])
     (when @(subscribe [:message-shown?])
       [:div.p-b-10.p-r-10.p-l-10.white
        [message
         @(subscribe [:message-type])
         @(subscribe [:message])
         hide-message]])]))

;;;; ====================================================================
;;;; String Utilities
;;;; ====================================================================

(defn capitalize-words
  "Capitalize the first letter of each word in a string."
  [s]
  (->> (s/split (str s) #"\b")
       (map s/capitalize)
       s/join))
