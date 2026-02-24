(ns orcpub.dnd.e5.views.content
  "My Content management pages.

   Contains the plugin browser (import/export/enable/disable plugins),
   per-content-type CRUD wrappers (my-spells, my-monsters, etc.),
   and the My Account page.  Depends on views.cljs for `content-page`
   (one-way); never required by views.cljs (avoids circular deps).
   core.cljs routes to page components here directly."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [clojure.string :as s]
            [orcpub.components :as comps]
            [orcpub.registration :as registration]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.selections :as selections]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.monsters :as monsters]
            [orcpub.dnd.e5.encounters :as encounters]
            [orcpub.dnd.e5.backgrounds :as bg]
            [orcpub.dnd.e5.races :as races]
            [orcpub.dnd.e5.classes :as classes]
            [orcpub.dnd.e5.feats :as feats]
            [orcpub.dnd.e5.languages :as langs]
            [orcpub.dnd.e5.views.common
             :refer [event-value
                     make-event-handler make-stop-prop-event-handler
                     svg-icon capitalize-words]]
            [orcpub.dnd.e5.views
             :refer [content-page]]))

;;;; ====================================================================
;;;; File Import
;;;; ====================================================================

(defn import-file
  "Handle .orcbrew file input: read the file and dispatch an import event."
  [e]
  (let [reader (js/FileReader.)
        file (.. e -target -files (item 0))
        filename (.-name file)
        nm (first (s/split filename #".orcbrew"))]
    (.addEventListener
     reader
     "load"
     (fn [e]
       (let [text (.. e -target -result)]
         (dispatch [::e5/import-plugin nm text]))))
    (.readAsText reader file)))

;;;; ====================================================================
;;;; Content-Type Wrappers
;;;; ====================================================================

(defn my-content-type
  "Expandable list of items for a single content type within a plugin.
   Shows item count, expand/collapse toggle, and per-item edit/delete/enable
   controls.  Form-2 component — outer fn creates the expanded? atom."
  []
  (let [expanded? (r/atom false)]
    (fn [source-name plugin type-name type-key icon add-event edit-event delete-event plural]
      (let [items (sort (type-key plugin))]
        [:div.pointer.item-list-item
         [:div.flex.justify-cont-s-b.align-items-c.p-10
          {:on-click #(swap! expanded? not)}
          [:div.flex.align-items-c
           [:div.h-48.flex.align-items-c
            (if (vector? icon)
              (doall
               (map-indexed
                (fn [index ico]
                  ^{:key index}
                  ;; Pre-existing: subscribes to :theme per icon inside loop;
                  ;; harmless but redundant — should subscribe once outside.
                  [svg-icon ico (/ 48 (count icon)) @(subscribe [:theme])])
                icon))
              [svg-icon icon 48 @(subscribe [:theme])])]
           [:span.m-l-10.f-s-24 (let [num (count items)
                                      final-type-name (if plural
                                                        (if (not= 1 num) plural type-name)
                                                        (str type-name (when (not= 1 num) "s")))]
                                  (str num " " (capitalize-words final-type-name)))]]
          [:div.orange.pointer
           [:i.fa.m-r-5
            {:class (if @expanded? "fa-caret-up" "fa-caret-down")}]
           [:span.underline (if @expanded? "collapse" "expand")]]]
         (when @expanded?
           [:div.bg-lighter.p-10
            [:div.flex.justify-cont-end
             [:button.form-button.m-l-5
              {:on-click (make-event-handler add-event source-name)}
              (str "add " type-name)]]
            [:div
             (doall
              (map-indexed
               (fn [i [key {:keys [name disabled?] :as item}]]
                 ^{:key key}
                 [:div.p-t-10.p-b-10.f-w-b.flex.justify-cont-s-b.align-items-c
                  [:div.m-r-10.flex.align-items-c.flex-column
                   {:on-click (make-stop-prop-event-handler ::e5/toggle-plugin-item source-name type-key key)}
                   [:div.f-s-10 "enabled?"]
                   [comps/checkbox
                    (not (get-in plugin [type-key key :disabled?]))
                    false]]
                  [:span.flex-grow-1 name]
                  [:div
                   [:button.form-button.m-l-5
                    {:on-click (make-event-handler edit-event item)}
                    "edit"]
                   [:button.form-button.m-l-5
                    {:on-click (make-stop-prop-event-handler delete-event item)}
                    "delete"]]])
               items))]])]))))

(defn my-selections
  "Selection content-type wrapper."
  [name plugin]
  [my-content-type
   name
   plugin
   "selection"
   ::e5/selections
   "checklist"
   ::selections/new-selection
   ::selections/edit-selection
   ::selections/delete-selection])

(defn my-spells
  "Spell content-type wrapper."
  [name plugin]
  [my-content-type
   name
   plugin
   "spell"
   ::e5/spells
   "spell-book"
   ::spells/new-spell
   ::spells/edit-spell
   ::spells/delete-spell])

(defn my-monsters
  "Monster content-type wrapper."
  [name plugin]
  [my-content-type
   name
   plugin
   "monster"
   ::e5/monsters
   "hydra"
   ::monsters/new-monster
   ::monsters/edit-monster
   ::monsters/delete-monster])

(defn my-encounters
  "Encounter content-type wrapper."
  [name plugin]
  [my-content-type
   name
   plugin
   "encounter"
   ::e5/encounters
   "hydra"
   ::encounters/new-encounter
   ::encounters/edit-encounter
   ::encounters/delete-encounter])

(defn my-backgrounds
  "Background content-type wrapper."
  [name plugin]
  [my-content-type
   name
   plugin
   "background"
   ::e5/backgrounds
   "ages"
   ::bg/new-background
   ::bg/edit-background
   ::bg/delete-background])

(defn my-races
  "Race content-type wrapper."
  [name plugin]
  [my-content-type
   name
   plugin
   "race"
   ::e5/races
   "woman-elf-face"
   ::races/new-race
   ::races/edit-race
   ::races/delete-race])

(defn my-subraces
  "Subrace content-type wrapper."
  [name plugin]
  [my-content-type
   name
   plugin
   "subrace"
   ::e5/subraces
   ["woman-elf-face"
    "woman-elf-face"]
   ::races/new-subrace
   ::races/edit-subrace
   ::races/delete-subrace])

(defn my-classes
  "Class content-type wrapper."
  [name plugin]
  [my-content-type
   name
   plugin
   "class"
   ::e5/classes
   "mounted-knight"
   ::classes/new-class
   ::classes/edit-class
   ::classes/delete-class
   "classes"])

(defn my-subclasses
  "Subclass content-type wrapper."
  [name plugin]
  [my-content-type
   name
   plugin
   "subclass"
   ::e5/subclasses
   ["mounted-knight"
    "mounted-knight"]
   ::classes/new-subclass
   ::classes/edit-subclass
   ::classes/delete-subclass
   "subclasses"])

(defn my-invocations
  "Eldritch Invocation content-type wrapper."
  [name plugin]
  [my-content-type
   name
   plugin
   "eldritch invocation"
   ::e5/invocations
   "warlock-eye"
   ::classes/new-invocation
   ::classes/edit-invocation
   ::classes/delete-invocation])

(defn my-boons
  "Pact Boon content-type wrapper."
  [name plugin]
  [my-content-type
   name
   plugin
   "pact boon"
   ::e5/boons
   "cursed-star"
   ::classes/new-boon
   ::classes/edit-boon
   ::classes/delete-boon])

(defn my-feats
  "Feat content-type wrapper."
  [name plugin]
  [my-content-type
   name
   plugin
   "feat"
   ::e5/feats
   "vitruvian-man"
   ::feats/new-feat
   ::feats/edit-feat
   ::feats/delete-feat])

(defn my-languages
  "Language content-type wrapper."
  [name plugin]
  [my-content-type
   name
   plugin
   "language"
   ::e5/languages
   "vitruvian-man"
   ::langs/new-language
   ::langs/edit-language
   ::langs/delete-language])

;;;; ====================================================================
;;;; Plugin Browser
;;;; ====================================================================

(defn my-content-item
  "Expandable card for a single plugin (option source).
   Shows enable/disable toggle, export/delete buttons, and all
   content-type sub-lists when expanded.  Form-2 component."
  []
  (let [expanded? (r/atom false)]
    (fn [name plugin]
      [:div.item-list-item
       [:div.p-20.pointer.flex.justify-cont-s-b.align-items-c.main-text-color
        {:on-click #(swap! expanded? not)}
        [:div.m-r-10.flex.align-items-c.flex-column
         {:on-click (make-stop-prop-event-handler ::e5/toggle-plugin name)}
         [:div.f-s-10 "enabled?"]
         [comps/checkbox
          (not (get plugin :disabled?))
          false]]
        [:span.f-s-24.flex-grow-1 name]
        [:div.orange
         [:i.fa.m-r-5
          {:class (if @expanded? "fa-caret-up" "fa-caret-down")}]
         [:span.pointer.underline (if @expanded? "collapse" "expand")]]]
       (when @expanded?
         [:div.bg-lighter.p-10
          [:div.flex.justify-cont-end.uppercase.align-items-c.m-b-10
           [:button.form-button.m-l-5
            {:on-click (make-event-handler ::e5/export-plugin-pretty-print name plugin)}
            "export"]
           [:button.form-button.m-l-5
            {:on-click (make-event-handler ::e5/delete-plugin name)}
            "delete"]]
          [:div.item-list
           [my-spells name plugin]
           [my-monsters name plugin]
           [my-encounters name plugin]
           [my-backgrounds name plugin]
           [my-races name plugin]
           [my-subraces name plugin]
           [my-classes name plugin]
           [my-subclasses name plugin]
           [my-invocations name plugin]
           [my-boons name plugin]
           [my-feats name plugin]
           [my-languages name plugin]
           [my-selections name plugin]]])])))

(defn my-content
  "Main plugin list view with Delete All / Export All buttons and
   per-plugin expandable cards."
  []
  [:div.main-text-color
   [:div.flex.justify-cont-end
    [:button.form-button.m-r-10.m-b-10
     {:on-click (make-event-handler ::char/show-delete-plugin-confirmation)}
     "Delete All"]
    [:button.form-button.m-r-10.m-b-10
     {:on-click (make-event-handler ::e5/export-all-plugins)}
     "Export All"]]
   [:div.flex.justify-cont-end
    (when @(subscribe [::char/delete-plugin-confirmation-shown?])
      [:div.p-20.flex.justify-cont-end
       [:div
        [:div.m-b-10 "Are you sure you want to delete ALL Option sources?"]
        [:div.flex
         [:button.form-button
          {:on-click (make-event-handler ::char/hide-delete-plugin-confirmation)}
          "cancel"]
         [:span.link-button
          {:on-click (make-event-handler ::char/delete-all-plugins)}
          "delete"]]]])]
   [:div.item-list
    (let [plugins (sort @(subscribe [::e5/plugins]))]
      (doall
       (map
        (fn [[name plugin]]
          ^{:key name}
          [my-content-item name plugin])
        plugins)))]])

;;;; ====================================================================
;;;; Page Components (routed from core.cljs)
;;;; ====================================================================

(defn my-content-page
  "My Content page — file import form + plugin browser."
  []
  [content-page
   "My Content"
   []
   [:div
    [:div.p-20.bg-lighter.main-text-color.m-b-10.m-l-10.m-r-10.b-rad-5
     [:div.f-w-b.f-s-24.m-b-5 "Import Option Source"]
     [:input {:type "file"
              :accept ".orcbrew"
              :on-change import-file}]]
    [my-content]]])

(defn my-account-page
  "My Account page — displays username/email with inline email change flow,
   confirmation field to prevent typos, rate-limit feedback, and account deletion."
  []
  (r/with-let [editing? (r/atom false)
               new-email (r/atom "")
               confirm-email (r/atom "")]
    (let [current-email @(subscribe [:email])
          pending-email @(subscribe [:pending-email])
          sent? @(subscribe [:email-change-sent?])
          error @(subscribe [:email-change-error])
          ;; Client-side validation: format check + confirm match
          bad-format? (and (seq @new-email)
                          (registration/bad-email? @new-email))
          emails-dont-match? (and (seq @confirm-email)
                                  (not= @new-email @confirm-email))
          can-submit? (and (seq @new-email)
                           (not bad-format?)
                           (= @new-email @confirm-email))]
      [content-page
       "My Account"
       [{:title "Delete Account"
         :icon "trash"
         :on-click #(dispatch
                    [:show-confirmation
                     {:confirm-button-text "DELETE ACCOUNT"
                      :question "Are you sure you want to delete your account, characters, and associated data?"
                      :event [:delete-account]}])}]
       [:div.f-s-24.p-10.white
        [:div.p-5
         [:span.f-w-b "Username: "]
         [:span @(subscribe [:username])]]
        [:div.p-5
         [:span.f-w-b "Email: "]
         (cond
           sent?
           [:div
            [:span current-email]
            [:div.m-t-5.f-s-14 "A verification email has been sent to " [:strong pending-email] ". Click the link in that email to confirm the change."]
            [:button.link-button.m-t-5.f-s-14
             {:on-click #(do (reset! editing? true)
                             (reset! new-email "")
                             (reset! confirm-email "")
                             (dispatch [:change-email-clear]))}
             "Change again"]]

           @editing?
           [:div.m-t-5
            [:input.input
             {:type :email
              :value @new-email
              :placeholder "New email address"
              :on-change #(reset! new-email (event-value %))}]
            (when bad-format?
              [:div.m-t-5.red "Not a valid email format"])
            ;; Confirm field to prevent typo-induced lockout
            [:input.input.m-t-5
             {:type :email
              :value @confirm-email
              :placeholder "Confirm new email address"
              :on-change #(reset! confirm-email (event-value %))}]
            (when emails-dont-match?
              [:div.m-t-5.red "Email addresses don't match"])
            [:div.m-t-5
             [:button.form-button
              {:disabled (not can-submit?)
               :on-click #(when can-submit?
                            (dispatch [:change-email @new-email]))}
              "Save"]
             [:button.link-button.m-l-10
              {:on-click #(do (reset! editing? false)
                              (reset! new-email "")
                              (reset! confirm-email "")
                              (dispatch [:change-email-clear]))}
              "Cancel"]]
            (when error
              [:div.m-t-5.red error])]

           :else
           [:div
            [:span current-email]
            (when pending-email
              [:div.m-t-5.f-s-14
               "Pending: " pending-email " — check your email to verify the change. "
               ;; Resend uses the same change-email flow; server enforces 3-zone rate limit
               ;; (0-1 min blocked, 1-5 min free resend, 5+ min open)
               [:button.link-button.f-s-14
                {:on-click #(dispatch [:change-email pending-email])}
                "Resend"]
               (when error
                 [:span.m-l-5.red.f-s-14 error])])
            [:button.link-button.m-l-10
             {:on-click #(do (reset! editing? true)
                             (reset! new-email "")
                             (reset! confirm-email "")
                             (dispatch [:change-email-clear]))}
             "Change"]])]]])))
