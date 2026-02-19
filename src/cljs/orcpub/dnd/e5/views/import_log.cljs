(ns orcpub.dnd.e5.views.import-log
  "Import log slide-out panel and floating button.
   Displays import results: errors, skipped items, and auto-fixes applied."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]))

(defn format-change-item [{:keys [type path field from to description] :as change}]
  [:div.import-log-item
   {:style {:padding "8px 12px"
            :border-bottom "1px solid #444"
            :font-size "13px"}}
   (case type
     :renamed-plugin-key
     [:span [:i.fa.fa-tag.m-r-5 {:style {:color "#3498db"}}]
      "Renamed empty plugin key " [:code {:style {:background "#333" :padding "2px 6px" :border-radius "3px"}} (pr-str from)]
      " \u2192 " [:code {:style {:background "#333" :padding "2px 6px" :border-radius "3px"}} to]]

     :fixed-option-pack
     [:span [:i.fa.fa-wrench.m-r-5 {:style {:color "#3498db"}}]
      "Fixed empty option-pack at " [:code {:style {:background "#333" :padding "2px 6px" :border-radius "3px" :font-size "11px"}} (str path)]]

     :removed-nil
     [:span [:i.fa.fa-minus-circle.m-r-5 {:style {:color "#7f8c8d"}}]
      "Removed nil " [:code {:style {:background "#333" :padding "2px 6px" :border-radius "3px"}} (name field)]
      " at " [:code {:style {:background "#333" :padding "2px 6px" :border-radius "3px" :font-size "11px"}} (str path)]]

     :replaced-nil
     [:span [:i.fa.fa-exchange.m-r-5 {:style {:color "#5dade2"}}]
      "Replaced " [:code {:style {:background "#333" :padding "2px 6px" :border-radius "3px"}} (str (name field) " nil")]
      " \u2192 " [:code {:style {:background "#333" :padding "2px 6px" :border-radius "3px"}} (str to)]]

     :preserved-nil
     [:span {:style {:color "#888"}}
      [:i.fa.fa-check.m-r-5 {:style {:color "#27ae60"}}]
      "Preserved " [:code {:style {:background "#333" :padding "2px 6px" :border-radius "3px"}} (name field)]
      " nil at " [:code {:style {:background "#333" :padding "2px 6px" :border-radius "3px" :font-size "11px"}} (str path)]]

     :string-fix
     [:span [:i.fa.fa-code.m-r-5 {:style {:color "#9b59b6"}}] description]

     ;; Default
     [:span (pr-str change)])])

(defn collapsible-section
  "A collapsible section with header and content. Open by default."
  [{:keys [title icon icon-color bg-color border-color]} content]
  (let [expanded? (r/atom true)]
    (fn [{:keys [title icon icon-color bg-color border-color]} content]
      [:div {:style {:margin "10px"}}
       [:div {:style {:font-weight "bold"
                      :padding "10px"
                      :background bg-color
                      :border-left (str "3px solid " border-color)
                      :border-radius "3px"
                      :cursor "pointer"
                      :display "flex"
                      :align-items "center"}
              :on-click #(swap! expanded? not)}
        [:i {:class (str "fa fa-chevron-" (if @expanded? "down" "right"))
             :style {:font-size "12px" :color "#888" :margin-right "8px" :width "12px"}}]
        [:i {:class (str "fa " icon " m-r-5")
             :style {:color icon-color}}]
        title]
       (when @expanded?
         [:div {:style {:margin-top "4px"}}
          content])])))

(defn import-log-panel []
  (let [log @(subscribe [:import-log])
        shown? (:panel-shown? log)]
    [:div.import-log-panel
     {:style {:position "fixed"
              :top 0
              :right (if shown? 0 "-420px")
              :width "400px"
              :height "100vh"
              :background "#2a2a2a"
              :color "#eee"
              :transition "right 0.3s ease"
              :z-index 1001
              :overflow-y "auto"
              :box-shadow (if shown? "-4px 0 20px rgba(0,0,0,0.5)" "none")}}

     ;; Header
     [:div {:style {:padding "15px 20px"
                    :background "#333"
                    :border-bottom "1px solid #444"
                    :display "flex"
                    :align-items "center"}}
      [:i.fa.fa-chevron-right
       {:style {:cursor "pointer" :font-size "16px" :color "#888" :margin-right "12px"}
        :on-click #(dispatch [:close-import-log-panel])}]
      [:div
       [:span {:style {:font-weight "bold" :font-size "16px"}} "Import Log"]
       (when (:import-name log)
         [:div {:style {:font-size "12px" :color "#888" :margin-top "4px"}}
          (:import-name log)])]]

     ;; Content
     [:div {:style {:padding "10px 0"}}

      ;; Errors section
      (when (seq (:errors log))
        [collapsible-section
         {:title (str "Errors (" (count (:errors log)) ")")
          :icon "fa-exclamation-circle"
          :icon-color "#e74c3c"
          :bg-color "#3d1f1f"
          :border-color "#e74c3c"}
         [:div
          (for [[idx error] (map-indexed vector (:errors log))]
            ^{:key idx}
            [:div {:style {:padding "5px 12px" :font-size "13px" :color "#ff6b6b"}} error])]])

      ;; Skipped items section
      (when (seq (:skipped-items log))
        [collapsible-section
         {:title (str "Skipped Items (" (count (:skipped-items log)) ")")
          :icon "fa-exclamation-triangle"
          :icon-color "#f39c12"
          :bg-color "#3d3520"
          :border-color "#f39c12"}
         [:div
          (for [item (:skipped-items log)]
            ^{:key (:key item)}
            [:div {:style {:padding "8px 12px" :border-bottom "1px solid #444"}}
             [:div {:style {:font-weight "bold" :color "#f39c12"}} (name (:key item))]
             [:div {:style {:font-size "12px" :color "#888" :margin-top "4px"}} (:errors item)]])]])

      ;; Changes section
      (when (seq (:changes log))
        [collapsible-section
         {:title (str "Auto-Fixes Applied (" (count (:changes log)) ")")
          :icon "fa-wrench"
          :icon-color "#3498db"
          :bg-color "#1d2d3d"
          :border-color "#3498db"}
         [:div
          (for [[idx change] (map-indexed vector (:changes log))]
            ^{:key idx}
            [format-change-item change])]])

      ;; Empty state
      (when (and (empty? (:errors log))
                 (empty? (:skipped-items log))
                 (empty? (:changes log)))
        [:div {:style {:padding "40px" :text-align "center" :color "#888"}}
         [:i.fa.fa-check-circle {:style {:font-size "48px" :color "#27ae60" :margin-bottom "15px"}}]
         [:div {:style {:font-size "16px"}} "No issues found"]
         [:div {:style {:font-size "13px" :margin-top "5px"}} "Import completed cleanly"]])]]))

(defn import-log-button []
  (let [has-content? @(subscribe [:import-log-has-content?])
        shown? @(subscribe [:import-log-shown?])
        log @(subscribe [:import-log])
        total-count (+ (count (:changes log))
                       (count (:errors log))
                       (count (:skipped-items log)))]
    [:div
     {:style {:position "fixed"
              :bottom "20px"
              :right "20px"
              :width "36px"
              :height "36px"
              :border-radius "50%"
              :background (cond
                            shown? "#3498db"
                            has-content? "#444"
                            :else "#333")
              :color (if has-content? "white" "#666")
              :display "flex"
              :align-items "center"
              :justify-content "center"
              :cursor "pointer"
              :box-shadow "0 2px 6px rgba(0,0,0,0.25)"
              :z-index 1000
              :opacity (if has-content? 1 0.6)
              :transition "all 0.2s ease"}
      :on-click #(dispatch [:toggle-import-log-panel])}
     [:i.fa.fa-clipboard-list {:style {:font-size "14px"}}]
     (when (pos? total-count)
       [:div {:style {:position "absolute"
                      :top "-4px"
                      :right "-4px"
                      :background "#e74c3c"
                      :color "white"
                      :font-size "10px"
                      :font-weight "bold"
                      :min-width "16px"
                      :height "16px"
                      :border-radius "8px"
                      :display "flex"
                      :align-items "center"
                      :justify-content "center"
                      :padding "0 4px"}}
        total-count])]))
