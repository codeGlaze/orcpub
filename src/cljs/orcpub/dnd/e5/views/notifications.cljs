(ns orcpub.dnd.e5.views.notifications
  "Shared notification view components: the transient message banner, the reusable callout box,
   and the contextual banners built on it. Producers dispatch :show-*-message (events.cljs); the
   app-header mount reads :message-shown?/:message/:message-type and renders `message`. Severity
   styling is in styles/core.clj (.message + .tone-error/.tone-warning/.tone-success for the banner,
   .bg-warning for the callout)."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [clojure.string :as s]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.character :as char]))

(defn- as-parts
  "Normalise whatever a producer handed us into {:title :details}.

   The structured map is the shape to use. A plain string is the LEGACY shape:
   messages used to be one string with blank lines standing in for structure, and
   HTML collapses those, which ran the sentences together with no punctuation
   between them. Splitting happens here, at the edge, rather than being papered
   over in CSS — and it goes away as producers move to the map.

   HICCUP passes through untouched. Some messages are markup, not text — the
   builders' \"please fill in X\" carries a bolded field name and a clickable
   \"Save anyway with placeholders\" — and running (str) over a vector prints the
   markup at the reader instead of rendering it."
  [message-text]
  (cond
    (map? message-text) message-text
    (vector? message-text) {:body message-text}
    :else
    (let [lines (->> (s/split (str message-text) #"\n")
                     (map s/trim)
                     (remove s/blank?))]
      {:title (first lines) :details (rest lines)})))

(defn- tone-icon [message-type]
  (case message-type
    :error "fa-times-circle"
    :warning "fa-exclamation-triangle"
    "fa-check-circle"))

(defn message
  "Transient banner. `message-text` is {:title :details} — or a plain string,
   which `as-parts` splits (see there).

   It says what happened and nothing else. An import banner briefly carried an
   \"Export a backup\" button, which on My Content sat directly above the page's
   own Export All: a message telling you to use a control that is already on
   screen is noise, not help."
  [message-type message-text close-handler]
  (let [{:keys [title details body]} (as-parts message-text)]
    [:div.pointer
     {:on-click close-handler}
     [:div.message
      {:class (case message-type
                :error "tone-error"
                :warning "tone-warning"
                "tone-success")}
      [:i.fa.message-icon {:class (tone-icon message-type)}]
      [:div.message-body
       (if body
         body
         [:<>
          [:div.message-title title]
          (for [line details]
            ^{:key line}
            [:div.message-detail line])])]
      [:i.fa.fa-times.message-close
       {:title "Dismiss"
        :aria-label "Dismiss"}]]]))

(defn callout
  "Persistent contextual notice: a box with an optional fa icon class, body content
   (a string or hiccup — multi-line is fine), and optional actions.

   `accent` draws a rail down the leading edge for a notice that wants an identity
   without being tinted like a severity. It names a shade rather than carrying one:
   :brand is the app's accent. Add further options in styles/core.clj.

   `tone` picks the box: :warning (default) is the severity-coloured one, :note is
   the neutral one for a callout that informs rather than warns, so an offer or an
   explanation is not dressed as a problem.

   Each action is {:label ...} plus either :on-click for a button, or :href (with
   optional :target) for a link. A link renders with the same button styling, so a
   call to action sits flush with the buttons beside it instead of trailing off as
   a bare anchor. An action may also carry :icon, which is a COMPLETE set of icon
   classes -- \"fab fa-patreon\" as readily as \"fa fa-download\" -- so an action can
   name where it goes. The callout's own :icon above is prefixed with .fa for it,
   which is why that one cannot reach the brand icons."
  [{:keys [icon text actions tone accent]}]
  [:div.p-10.m-b-10.flex.align-items-c
   {:class (cond-> [(case tone :note "bg-note" "bg-warning")]
             ;; A rail on the leading edge, the same device .health-rail uses, so a
             ;; callout can carry an identity without a severity colour filling the
             ;; box. Named, not a colour: the shades live in styles/core.clj.
             (= accent :brand) (conj "callout-accent-brand"))
    :style {:gap "8px"}}
   (when icon [:i.fa {:class icon}])
   [:div.f-s-14.flex-grow-1 text]
   ;; with-meta on the ELEMENT, not on the let: metadata on a let form attaches to
   ;; the form, never reaches what it returns, and React logs a missing-key warning
   ;; for every action in the seq.
   (for [{:keys [label on-click href target icon]} actions]
     (let [body (if icon
                  [:span [:i.m-r-5 {:class icon}] label]
                  label)]
       (with-meta
         (if href
           [:a.form-button {:href href :target (or target "_blank")} body]
           [:button.form-button {:on-click on-click} body])
         {:key label})))])

(defn shared-content-banner
  "Shown when viewing a character whose homebrew arrived embedded in the share link. The content
   is loaded view-only (:shared-plugins, never persisted); offers to Keep it in the library, and
   flags entries that collide by name with the viewer's own content (the shared version wins on
   this sheet, their copy is untouched). Renders nothing when there's no shared content."
  [id]
  (when-let [{:keys [count item-count collisions]} @(subscribe [::e5/shared-content-info])]
    (let [char-name @(subscribe [::char/character-name id])
          n-coll (clojure.core/count collisions)
          item-count (or item-count 0)
          parts (cond-> []
                  (pos? count)      (conj (str count " homebrew piece" (when (not= 1 count) "s")))
                  (pos? item-count) (conj (str item-count " custom item" (when (not= 1 item-count) "s"))))]
      [callout
       {:icon "fa-info-circle orange"
        :text [:div
               [:div.f-w-b.f-s-16 (str "Shared with " (s/join " and " parts) ".")]
               [:div.f-s-12.m-t-5 {:style {:opacity 0.8}}
                "Loaded for viewing only — not saved to your library."]
               (when (pos? n-coll)
                 [:div.f-s-12.m-t-5 {:style {:color "#d9a520"}}
                  (str n-coll " differ from same-named content you own — the shared version shows "
                       "here, yours is untouched"
                       (when-let [names (seq (map :name (take 4 collisions)))]
                         (str " (" (s/join ", " names) (when (> n-coll 4) ", …") ")"))
                       ".")])
               (when (pos? item-count)
                 [:div.f-s-12.m-t-5 {:style {:opacity 0.8}}
                  "Custom magic items are shown for this view only (keeping items isn't supported yet)."])]
        :actions (cond-> []
                   (pos? count) (conj {:label "Keep homebrew in my library"
                                       :on-click #(dispatch [::e5/keep-shared-content char-name])})
                   :always      (conj {:label "Dismiss"
                                       :on-click #(dispatch [::e5/dismiss-shared-content])}))}])))
