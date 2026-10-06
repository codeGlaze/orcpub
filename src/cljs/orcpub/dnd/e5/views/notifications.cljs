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
  "Normalise a producer's message into {:title :details}, or {:body hiccup}.
   A map passes through. A string is the LEGACY shape, split on newlines into a title and
   detail lines, since HTML collapses its blank lines. Hiccup passes through as :body:
   (str) over a vector would print the markup instead of rendering it."
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

(defn- mark-icon [mark]
  (case mark
    :skipped "fa-exclamation-triangle mark-skipped"
    :repaired "fa-wrench mark-repaired"
    "fa-check mark-done"))

(defn- detail-line
  "A detail line is text, hiccup, or {:mark :text} -- a line marked good or bad
   by a coloured icon, which keeps the text itself readable on a tinted box."
  [line]
  (if (map? line)
    [:div.message-detail
     [:i.fa.message-mark {:class (mark-icon (:mark line))}]
     (:text line)]
    [:div.message-detail line]))

(defn message
  "Transient banner. `message-text` is {:title :details}, or a plain string or hiccup,
   which `as-parts` normalises (see there). It says what happened and offers no actions."
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
          ;; keyed by position: a detail line may be hiccup, which is no key at all
          (map-indexed (fn [i line]
                         ^{:key i}
                         [detail-line line])
                       details)])]
      [:i.fa.fa-times.message-close
       {:title "Dismiss"
        :aria-label "Dismiss"}]]]))

(defn callout
  "Persistent contextual notice: a box with an optional :icon (fa class, prefixed with .fa),
   :text (string or hiccup) and optional :actions. :tone is :warning (default, severity) or
   :note (neutral); :accent :brand draws a leading-edge rail. Each action is {:label} plus
   :on-click (a button) or :href and optional :target (a link styled as a button), and an
   optional :icon that is a COMPLETE icon class string (\"fab fa-patreon\")."
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
