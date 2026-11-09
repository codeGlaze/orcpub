(ns orcpub.dnd.e5.views.pdf-options
  "Groups of the PDF Options panel that live outside views.cljs: the file name choices."
  (:require [clojure.string :as s]
            [re-frame.core :refer [subscribe dispatch]]
            [orcpub.pdf-spec :as pdf-spec]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.views.builder-fields :refer [option-checkbox option-group labeled-dropdown]]))

(defn file-name-format
  "The player's file name choices with pdf-spec/file-name-defaults filling the rest. Must be
   called while rendering. Returns a map."
  []
  (merge pdf-spec/file-name-defaults @(subscribe [::char/file-name-format])))

(defn- set-option
  "A handler saving file name choice `k` as `v`."
  [k v]
  #(dispatch [::char/set-file-name-option k v]))

(defn file-name-group
  "The File name group for `built-char`: which parts the PDF's name shows, where the level goes
   and how it is written, and the name those choices give now. Returns hiccup."
  [built-char]
  (let [fmt (file-name-format)
        tick (fn [label k help] [option-checkbox label (get fmt k) (set-option k (not (get fmt k))) help])
        nm (char/character-name built-char)
        preview (pdf-spec/file-name-for built-char (if (s/blank? nm) "(random name)" nm) fmt)]
    [option-group "File name"
     [tick "Player name" :player? "The player name from the Description step."]
     [tick "Character name" :character? "A character with no name is given one when exported."]
     [tick "Class" :class? "Each class, in the order they were taken."]
     (when (:class? fmt)
       [:div.m-l-20 [tick "Subclass" :subclass? "Written after its class, in brackets."]])
     [tick "Level" :level? "Each class's level, or the total when classes are left out."]
     (when (:level? fmt)
       [:div.m-l-20.flex.flex-wrap
        [:div.m-r-10.m-b-10
         [labeled-dropdown "Level goes"
          {:items [{:title "After the class" :value false} {:title "Before the class" :value true}]
           :value (boolean (:level-first? fmt))
           :typed? true
           :on-change #(dispatch [::char/set-file-name-option :level-first? %])}]]
        [:div.m-b-10
         [labeled-dropdown "Written as"
          {:items [{:title "5" :value :plain} {:title "05" :value :padded} {:title "L5" :value :prefixed}]
           :value (:level-style fmt)
           :typed? true
           :on-change #(dispatch [::char/set-file-name-option :level-style %])}]]])
     [:div.option-note (str (if (s/blank? preview) "character" preview) ".pdf")]]))
