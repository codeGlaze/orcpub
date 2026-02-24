(ns orcpub.dnd.e5.views.builders.warlock
  "Warlock-specific homebrew builder pages: Eldritch Invocation and
   Pact Boon.  Both are small, structurally identical builders for
   Warlock class features — name, option-pack, and description.

   Extracted from views.builders — imports shared builder infrastructure
   from the parent module."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [orcpub.dnd.e5.classes :as classes]
            [orcpub.dnd.e5.views.common
             :refer [textarea-field]]
            [orcpub.dnd.e5.views.builders
             :refer [builder-input-field plugin-datalist
                     option-source-name-label builder-page]]))


;;; ─── Eldritch Invocation ─────────────────────────────────────────────

(defn invocation-input-field [title prop invocation & [class-names]]
  (builder-input-field title prop invocation ::classes/set-invocation-prop class-names))

(defn invocation-builder []
  (let [invocation @(subscribe [::classes/invocation-builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [invocation-input-field
       "Name"
       :name
       invocation
       "m-b-20"]
      [plugin-datalist
       option-source-name-label
       invocation
       ::classes/set-invocation-prop]
      ]
     [:div.w-100-p
      [:div.f-s-24.f-w-b
       "Description"]
      [textarea-field
       {:value (get invocation :description)
        :on-change #(dispatch [::classes/set-invocation-prop :description %])}]]]))

(defn invocation-builder-page []
  (builder-page "Eldritch Invocation" ::classes/reset-invocation ::classes/save-invocation invocation-builder))


;;; ─── Pact Boon ───────────────────────────────────────────────────────

(defn boon-input-field [title prop boon & [class-names]]
  (builder-input-field title prop boon ::classes/set-boon-prop class-names))

(defn boon-builder []
  (let [boon @(subscribe [::classes/boon-builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [boon-input-field
       "Name"
       :name
       boon
       "m-b-20"]
      [plugin-datalist
       option-source-name-label
       boon
       ::classes/set-boon-prop]
      ]
     [:div.w-100-p
      [:div.f-s-24.f-w-b
       "Description"]
      [textarea-field
       {:value (get boon :description)
        :on-change #(dispatch [::classes/set-boon-prop :description %])}]]]))

(defn boon-builder-page []
  (builder-page "Pact Boon" ::classes/reset-boon ::classes/save-boon boon-builder))
