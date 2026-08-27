(ns orcpub.override-demo
  "Renders the Weapons picker's option list, before and after, so the
   override behaviour can be looked at rather than only asserted.

   Everything on screen comes from the real chain: app-db is seeded with a
   custom item, ::mi5e/mundane-weapon-options is subscribed exactly as the
   builder does, the options go through the real t5e/inventory-selection, and
   the labels are produced by the real character-builder/name-and-key.

   To look at it:

     lein run -m figwheel.main -- --build-once demo
     cp dev/demo-index.html target/demo/index.html
     (cd target/demo && python3 -m http.server 8899)

   then open http://localhost:8899/index.html."
  (:require [clojure.string :as str]
            [reagent.dom.client :as rdomc]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.template :as t]
            [orcpub.character-builder :as cb]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.weapons :as weapon5e]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.equipment-subs]))

(def homebrew-longsword
  {::mi5e/name "Longsword"
   ::mi5e/type :weapon
   ::mi5e/rarity :common
   ::mi5e/magical? false
   ::mi5e/owner "kaylee"})

(defn- weapons-selection [extra]
  (t5e/inventory-selection "Weapons" "plain-dagger" weapon5e/weapons
                           mod5e/deferred-weapon nil extra))

(defn- labels
  "The rows inventory-adder would offer, as the user sees them."
  [selection]
  (->> (::t/options selection)
       (remove ::t/legacy-only?)
       (map cb/name-and-key)
       (sort-by :name)))

(defn- longsword-rows [rows]
  (filter #(str/starts-with? (:name %) "Longsword") rows))

(defn- panel [title rows tone]
  [:div {:style {:flex "1" :min-width "320px"}}
   [:h2 {:style {:font "600 15px/1.4 system-ui, sans-serif"
                 :color tone :margin "0 0 10px"}} title]
   [:div {:style {:border (str "1px solid " tone) :border-radius "6px"
                  :overflow "hidden" :background "#fff"}}
    (doall
     (map-indexed
      (fn [i {:keys [name key]}]
        ^{:key (str key i)}
        [:div {:style {:padding "9px 12px"
                       :font "13px/1.3 ui-monospace, Menlo, monospace"
                       :border-top (when (pos? i) "1px solid #eee")
                       :background (if (str/includes? name "your version")
                                     "#fff8e1" "#fff")
                       :color "#222"}}
         name
         [:span {:style {:float "right" :color "#999" :font-size "11px"}}
          (str key)]])
      rows))]])

(defn root []
  (let [custom @(rf/subscribe [::mi5e/mundane-weapon-options])
        after (labels (weapons-selection custom))
        ;; What the list looked like before: options simply concatenated.
        before (->> (concat (::t/options (weapons-selection nil)) custom)
                    (remove ::t/legacy-only?)
                    (map cb/name-and-key)
                    (sort-by :name))]
    [:div {:style {:font "14px/1.5 system-ui, sans-serif" :padding "28px"
                   :background "#f6f7f9" :min-height "100vh"}}
     [:h1 {:style {:font "600 19px/1.3 system-ui, sans-serif" :margin "0 0 4px"}}
      "Weapons picker — a custom item named \"Longsword\""]
     [:p {:style {:color "#666" :margin "0 0 22px" :font-size "13px"}}
      "Rows the character builder offers. Only the Longsword entries differ."]
     [:div {:style {:display "flex" :gap "26px" :align-items "flex-start"}}
      [panel (str "Before — " (count (longsword-rows before)) " rows, winner undefined")
       (longsword-rows before) "#c0392b"]
      [panel (str "After — " (count (longsword-rows after)) " row, yours")
       (longsword-rows after) "#1e8449"]]
     [:p {:style {:color "#666" :margin "24px 0 0" :font-size "12px"}}
      (str "SRD list on its own: " (count (labels (weapons-selection nil)))
           " rows. With the old concat: " (count before)
           " — the extra row is the duplicate. Now: " (count after)
           " — the custom Longsword stands in the SRD one's place.")]]))

(defonce rt (delay (rdomc/create-root (.getElementById js/document "app"))))

(defn ^:export main []
  (reset! app-db {::mi5e/custom-items [homebrew-longsword]})
  (rf/clear-subscription-cache!)
  (rdomc/render @rt [root]))

(main)
