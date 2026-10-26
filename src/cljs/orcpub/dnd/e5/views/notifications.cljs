(ns orcpub.dnd.e5.views.notifications
  "Shared notification view components: the transient message banner, the reusable callout box,
   and the contextual banners built on it. Producers dispatch :show-*-message (events.cljs); the
   app-header mount reads :message-shown?/:message/:message-type and renders `message`. Severity
   styling is in styles/core.clj (.message + .tone-error/.tone-warning/.tone-success for the banner,
   .bg-warning for the callout)."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [clojure.string :as s]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.library :as library]))

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
  "Persistent notice: optional :icon (fa class), :text (string or hiccup), :actions, :details.
   :tone :warning (default) or :note; :accent :brand draws the amber ring. An action is {:label}
   with :on-click (:link? true for a text link; :expanded marks a toggle, with an arrow) or :href
   and :target, and an optional :icon (a COMPLETE icon class). :details: {:key :label :aside}
   rows in a band under the line, as two columns. :on-close adds the close mark; :compact? fits
   a one-line notice."
  [{:keys [icon text actions tone accent on-close compact? details]}]
  [:div.p-10.m-b-10.flex.align-items-c
   {:class (cond-> [(case tone :note "bg-note" "bg-warning")]
             ;; A ring, so a callout can carry an identity without a severity colour filling
             ;; the box. Named, not a colour: the shades live in styles/core.clj.
             (= accent :brand) (conj "callout-accent-brand")
             compact? (conj "callout-compact")
             (seq details) (conj "flex-wrap"))
    :style {:gap "8px"}}
   (when icon [:i.fa {:class icon}])
   [:div.f-s-14.flex-grow-1 text]
   ;; with-meta on the ELEMENT, not on the let: metadata on a let form attaches to
   ;; the form, never reaches what it returns, and React logs a missing-key warning
   ;; for every action in the seq.
   (for [{:keys [label on-click href target icon link? expanded]} actions]
     (let [body (if icon
                  [:span [:i.m-r-5 {:class icon}] label]
                  label)]
       (with-meta
         (cond
           href [:a.form-button {:href href :target (or target "_blank")} body]
           link? [:button.link-button.underline.f-w-b.f-s-14
                  (cond-> {:on-click on-click}
                    (some? expanded) (assoc :aria-expanded expanded))
                  body
                  (when (some? expanded)
                    [:i.fa.m-l-5 {:class (if expanded "fa-angle-up" "fa-angle-down")
                                  :aria-hidden true}])]
           :else [:button.form-button {:on-click on-click} body])
         {:key label})))
   (when on-close
     [:button.message-close.main-text-color
      {:type "button" :title "Dismiss" :aria-label "Dismiss" :on-click on-close}
      [:i.fa.fa-times {:aria-hidden true}]])
   (when (seq details)
     [:div.callout-details
      (for [{:keys [key label aside]} details]
        ^{:key key} [:div.callout-detail [:span label] [:span.callout-aside aside]])])])

(defn shared-content-banner
  "Shown when viewing a character whose homebrew arrived embedded in the share link. The content
   is loaded view-only (:shared-plugins, never persisted); offers to Keep it in the library, and
   flags entries that collide by name with the viewer's own content (the shared version wins on
   this sheet, their copy is untouched). Renders nothing when there's no shared content."
  [id]
  (when-let [{:keys [count item-count collisions left-out]} @(subscribe [::e5/shared-content-info])]
    (let [char-name @(subscribe [::char/character-name id])
          shared @(subscribe [::e5/shared-plugins])
          ;; never silent: what the link carried that cannot be used here is named
          dangling (for [[src ct k] (library/orphans @(subscribe [::e5/shared-dangling]))]
                     (or (get-in shared [src ct k :name]) (name k)))
          names (fn [xs] (str " (" (s/join ", " (take 4 xs)) (when (> (clojure.core/count xs) 4) ", …") ")"))
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
               (when (seq left-out)
                 [:div.f-s-12.m-t-5 {:style {:color "#d9a520"}}
                  (str (clojure.core/count left-out) " could not be read, and are left out"
                       (names (map #(or (:name %) (name (:key %))) left-out)) ".")])
               (when (seq dangling)
                 [:div.f-s-12.m-t-5 {:style {:color "#d9a520"}}
                  (str (clojure.core/count dangling) " belong to a race or class the link did not carry, "
                       "so they are not offered" (names dangling) ".")])
               (when (pos? item-count)
                 [:div.f-s-12.m-t-5 {:style {:opacity 0.8}}
                  "Custom magic items are shown for this view only (keeping items isn't supported yet)."])]
        :actions (cond-> []
                   (pos? count) (conj {:label "Keep homebrew in my library"
                                       :on-click #(dispatch [::e5/keep-shared-content char-name])})
                   :always      (conj {:label "Dismiss"
                                       :on-click #(dispatch [::e5/dismiss-shared-content])}))}])))
