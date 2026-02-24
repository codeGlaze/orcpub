(ns orcpub.dnd.e5.views.builders.language
  "Language homebrew builder page — name and description.

   Extracted from views.builders — imports shared builder infrastructure
   from the parent module."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [orcpub.dnd.e5.languages :as langs]
            [orcpub.dnd.e5.views.common
             :refer [textarea-field]]
            [orcpub.dnd.e5.views.builders
             :refer [builder-input-field plugin-datalist
                     option-source-name-label builder-page]]))


(defn language-input-field [title prop language & [class-names]]
  (builder-input-field title prop language ::langs/set-language-prop class-names))

(defn language-builder []
  (let [language @(subscribe [::langs/builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [language-input-field
       "Name"
       :name
       language
       "m-b-20"]
      [plugin-datalist
       option-source-name-label
       language
       ::langs/set-language-prop]
      ]
     [:div.w-100-p
      [:div.f-s-24.f-w-b
       "Description"]
      [textarea-field
       {:value (get language :description)
        :on-change #(dispatch [::langs/set-language-prop :description %])}]]]))

(defn language-builder-page []
  (builder-page "Language" ::langs/reset-language ::langs/save-language language-builder))
