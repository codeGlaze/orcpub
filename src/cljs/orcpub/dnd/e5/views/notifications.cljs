(ns orcpub.dnd.e5.views.notifications
  "Shared notification view components: the transient message banner and the persistent
   callout box. Producers dispatch :show-*-message (events.cljs); the app-header mount reads
   :message-shown?/:message/:message-type and renders `message`. Severity styling is in
   styles/core.clj (.message + .bg-red/.bg-orange/.bg-green for the banner, .bg-warning for
   the callout).")

(defn message
  "Transient banner: colored by message-type (:error/:warning else success), click anywhere
   to close via close-handler."
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

(defn callout
  "Persistent contextual notice: a warning-box (.bg-warning) with an optional fa icon class,
   message content (string or hiccup), and optional action buttons (each {:label :on-click})."
  [{:keys [icon text actions]}]
  [:div.bg-warning.p-10.m-b-10.flex.align-items-c {:style {:gap "8px"}}
   (when icon [:i.fa {:class icon}])
   [:span.f-s-14.flex-grow-1 text]
   (for [{:keys [label on-click]} actions]
     ^{:key label} [:button.form-button {:on-click on-click} label])])
