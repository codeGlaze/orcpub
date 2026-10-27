(ns orcpub.dnd.e5.views.rest-controls
  "The Rest box: short and long rests, the time of day and Now, the question after a rest, and the
   result line with Undo. Design: handoff-rest-controls.md; the engine is orcpub.dnd.e5.rests."
  (:require [clojure.string :as s]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.rests :as rests]))

(def time-of-day-options
  "The time-of-day choices in the Rest box: value, label, sun?, moon?."
  [[#{} "None" false false] [#{:dawn} "Dawn" true false] [#{:dusk} "Dusk" false true]
   [#{:dawn :dusk} "Both" true true]])

(defn- time-words
  "\"Dawn\", \"Dusk\" or \"Dawn and dusk\" for a set of times."
  [times]
  (case times #{:dawn} "Dawn" #{:dusk} "Dusk" "Dawn and dusk"))

(defn- rest-labels-shown?
  "Whether this device shows the Rest box with labels; remembered in localStorage."
  []
  (try (= "1" (.getItem js/localStorage "orcpub:rest-labels")) (catch :default _ false)))

(defn- rest-result-line
  "The Rest box's line after an action: the question after a rest with no time chosen, else what
  happened, each recharged item with its amount (type over it to use your own roll), and Undo."
  [id {:keys [request recharged ask?]}]
  (let [{kind :rest times :times} request
        rested (case kind :short "Short rest finished." :long "Long rest finished." nil)
        n (count recharged)]
    (if ask?
      [:div.rest-line {:role "status"}
       [:span rested " " [:b "Did dawn or dusk pass?"]]
       [:div.rest-answers
        (doall (for [[ts label sun? moon?] (rest time-of-day-options)]
                 ^{:key label}
                 [:button.rest-link {:type "button" :on-click #(dispatch [::char/answer-time-of-day id ts])}
                  (when sun? [:i.fas.fa-sun]) (when moon? [:i.fas.fa-moon]) label]))
        [:button.rest-link {:type "button" :on-click #(dispatch [::char/answer-time-of-day id #{}])} "Neither"]
        [:button.rest-link.right {:type "button" :on-click #(dispatch [::char/undo-rest id])} "Undo rest"]]]
      [:div.rest-line {:role "status"}
       [:span (s/join " " (remove nil? [rested
                                        (when (seq times)
                                          (str (time-words times) " passed: " n (if (= 1 n) " item" " items")
                                               " recharged."))]))]
       [:button.rest-link.right {:type "button" :on-click #(dispatch [::char/undo-rest id])} "Undo"]
       [:button.rest-close {:type "button" :aria-label "Close" :on-click #(dispatch [::char/close-rest-result])} "\u00D7"]
       (when (seq recharged)
         [:div.rest-recharged
          (doall (for [{:keys [item-key name i amount dice]} recharged]
                   ^{:key (str item-key i)}
                   [:label.rest-recharged-row
                    [:span name (when (pos? i) (str " (" (inc i) ")"))]
                    (if (= :all amount)
                      [:span.rest-amount-all "all"]
                    [:input.input.rest-amount
                     {:type "number" :min 0 :default-value amount :aria-label (str name " charges regained")
                      :on-key-down #(when (= "Enter" (.-key %)) (.blur (.-target %)))
                      :on-blur #(let [v (js/parseInt (.. % -target -value))]
                                  (when (and (not (js/isNaN v)) (not= v amount))
                                    (dispatch [::char/set-rest-amount id item-key i (max 0 v)])))}])
                    [:span.rest-dice (if dice (str "rolled " dice) "typed")]]))])])))

(defn rest-box
  "The Rest box, first on the Features tab (handoff-rest-controls.md): short and long rests and,
  for a character holding dawn or dusk things, a time-of-day choice and Now. Shows a hint, the
  question after a rest, or the result with Undo until closed. Takes the character id."
  []
  (let [times (r/atom #{})
        labels? (r/atom (rest-labels-shown?))]
    (fn [id]
      (let [items-by-key @(subscribe [::mi/all-magic-items-map])
            carried-cfgs (merge @(subscribe [::char/magic-items id]) @(subscribe [::char/magic-weapons id])
                                @(subscribe [::char/magic-armor id]))
            carried (for [[k cfg] carried-cfgs :let [item (items-by-key k)] :when item]
                      {:item-key k :item item :entry (rests/as-entry cfg)})
            features-used @(subscribe [::char/features-used id])
            time? (rests/has-time-of-day? {::entity/values {::char/features-used features-used}} carried)
            result (let [r @(subscribe [::char/rest-result])] (when (= id (:id r)) r))
            chosen @times
            act (fn [kind]
                  (dispatch [::char/take-rest id {:rest kind :times chosen} (select-keys items-by-key (keys carried-cfgs))])
                  (reset! times #{}))
            hint (when (and (seq chosen) (not result))
                   (let [ch {::entity/values {::char/features-used features-used}}
                         n (reduce + (for [t chosen :let [{:keys [items uses]} (rests/would-recharge ch carried t)]]
                                       (+ (count items) uses)))]
                     (if (pos? n)
                       (str (time-words chosen) " recharges " n (if (= 1 n) " thing" " things") " with your next rest, or tap Now.")
                       (str (time-words chosen) " has nothing to recharge."))))
            seg (fn [[ts label sun? moon?]]
                  ^{:key label}
                  [:button.rest-seg
                   {:type "button" :role "radio" :aria-checked (= ts chosen) :aria-label label
                    :class (when (= ts chosen) (if (seq ts) "on" "none-on"))
                    :on-click (fn [] (reset! times ts) (dispatch [::char/close-rest-result]))}
                   (when sun? [:i.fas.fa-sun {:aria-hidden true}])
                   (when moon? [:i.fas.fa-moon {:aria-hidden true}])
                   (cond @labels? [:span label] (empty? ts) [:span "\u2014"])])
            now [:button.form-button.rest-btn.rest-now
                 {:type "button" :aria-disabled (empty? chosen)
                  :class (when (empty? chosen) "unavailable")
                  :title (when (empty? chosen) "Choose a time of day first")
                  :on-click #(when (seq chosen) (act nil))}
                 "Now"]
            rest-buttons [[:button.form-button.rest-btn {:type "button" :aria-label "Short rest" :on-click #(act :short)}
                    (if (or @labels? (not time?)) "Short rest" "Short")]
                   [:button.form-button.rest-btn {:type "button" :aria-label "Long rest" :on-click #(act :long)}
                    (if (or @labels? (not time?)) "Long rest" "Long")]]]
        [:div.rest-box {:role "group" :aria-label "Rest"}
         [:div.rest-head
          [:i.fas.fa-bed.rest-bed {:aria-hidden true}]
          [:span.rest-title "Rest"]
          (when time?
            [:button.rest-labels
             {:type "button" :aria-expanded @labels?
              :on-click (fn [] (swap! labels? not)
                          (try (.setItem js/localStorage "orcpub:rest-labels" (if @labels? "1" "0")) (catch :default _ nil)))}
             "Labels" [:i.fa {:class (if @labels? "fa-caret-up" "fa-caret-down")}]])]
         (cond
           (not time?) (into [:div.rest-row.rests-only] rest-buttons)
           @labels? [:<>
                     [:div.rest-caption "Time of day"]
                     (into [:div.rest-segs.wide {:role "radiogroup" :aria-label "Time of day"}] (map seg time-of-day-options))
                     (into [:div.rest-row.labels now] rest-buttons)]
           :else [:<>
                  [:div.rest-caption "Time of day"]
                  [:div.rest-row
                   (into [:div.rest-segs {:role "radiogroup" :aria-label "Time of day"}] (map seg time-of-day-options))
                   now
                   (into [:div.rest-rests] rest-buttons)]])
         (cond
           result [rest-result-line id result]
           hint [:div.rest-line.hint {:role "status"} hint])]))))

(defn rest-fab
  "The floating Rest button (handoff-rest-controls.md, D1 and section 9) for character `id` on sheet
  tab `tab`: opens the Rest box as a bottom sheet on phones, a popover elsewhere. Hidden on the
  Features tab, which shows the box itself. While the box asks about dawn or dusk it stays open."
  []
  (let [open? (r/atom false)]
    (fn [id tab]
      (let [asking? (let [res @(subscribe [::char/rest-result])] (and (= id (:id res)) (:ask? res)))
            mobile? @(subscribe [:mobile?])
            close #(when-not asking? (reset! open? false))]
        [:div.rest-fab-slot
         (when (and @open? (not= "features" tab))
           [:<>
            [:div.rest-fab-dim {:class (when-not mobile? "clear") :on-click close}]
            [:div {:class (if mobile? "rest-fab-sheet" "rest-fab-card")
                   :role "dialog" :aria-modal "true" :aria-label "Rest"}
             (when mobile? [:div.rest-fab-handle])
             [rest-box id]]])
         (when (not= "features" tab)
           [:button.rest-fab
            {:type "button" :aria-label "Rest" :aria-expanded (str @open?)
             :on-click #(if @open? (close) (reset! open? true))}
            [:i.fas {:class (if (and @open? (not asking?)) "fa-times" "fa-bed") :aria-hidden true}]])]))))
