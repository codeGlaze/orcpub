(ns orcpub.dnd.e5.option-menu-views
  "Shared view layer for the growable multi-select menus.

   ONE component (`option-menu`) renders the full menu chrome — a quoted pattern
   banner (when boilerplate wording dominates), a search box, a selected-chips
   tray, an N-of-M count + Clear, and the option body — in whichever of the three
   layouts the GLOBAL toggle selects (`:grid` / `:pills` / `:az`). The layout is a
   single app-wide, persisted value: flipping `layout-toggle` re-renders every menu
   at once. Only the search query and active A–Z letter are per-menu and transient.

   Each caller supplies its options already normalized (see `normalize`/`checkbox-options`)
   plus an optional `cell-fn`; the default cell renders the option's pre-built `:card`
   hiccup (the rich SRD selector) or, failing that, a checkbox cell. This keeps the
   chrome/layout shared while the per-option rendering stays family-specific.

   Pure grouping logic lives in `orcpub.dnd.e5.option-grouping`; this ns is only view
   + the small re-frame state the menus need."
  (:require [re-frame.core :refer [reg-sub reg-event-db subscribe dispatch after]]
            [reagent.core :as r]
            [clojure.string :as str]
            [orcpub.components :as comps]
            [orcpub.dnd.e5.db :as db5e]
            [orcpub.dnd.e5.option-grouping :as grouping]))

;; ---------------------------------------------------------------------------
;; re-frame state
;;   Global layout  -> [:user-data :menu-layout]   (persisted, like :theme)
;;   Per-menu state -> [::menu-state menu-id {:query :letter}]  (transient)
;; ---------------------------------------------------------------------------

(def ^:private persist-user
  (after (fn [db] (db5e/user->local-store (:user-data db)))))

(reg-sub ::layout (fn [db _] (get-in db [:user-data :menu-layout] :grid)))

(reg-event-db
 ::set-layout
 [persist-user]
 (fn [db [_ layout]] (assoc-in db [:user-data :menu-layout] layout)))

(reg-sub ::menu-query  (fn [db [_ id]] (get-in db [::menu-state id :query] "")))
(reg-sub ::menu-letter (fn [db [_ id]] (get-in db [::menu-state id :letter])))

(reg-event-db ::set-menu-query  (fn [db [_ id q]] (assoc-in db [::menu-state id :query] q)))
(reg-event-db ::set-menu-letter (fn [db [_ id l]] (assoc-in db [::menu-state id :letter] l)))

;; Collapse is a persisted per-section preference (like :menu-layout) — default
;; expanded, independent per section, survives reload.
(reg-sub ::collapsed (fn [db [_ id]] (get-in db [:user-data :menu-collapsed id] false)))
(reg-event-db
 ::toggle-collapsed
 [persist-user]
 (fn [db [_ id]] (update-in db [:user-data :menu-collapsed id] not)))

;; ---------------------------------------------------------------------------
;; Option normalization
;; ---------------------------------------------------------------------------

;; These work out the shared wording and the per-option labels from the list of
;; option names. Reagent re-runs this component constantly — on every keystroke,
;; layout switch, and checkbox toggle — but this analysis only depends on the
;; names, so redoing it each time is wasted work. `memoize` remembers the result
;; for a given list of names and hands it back when that same list returns, so the
;; work only happens when a menu's options actually change.
(def ^:private memo-prefix (memoize grouping/dominant-prefix))
(def ^:private memo-classify (memoize grouping/classify))

(defn checkbox-options
  "Adapt a plain list of items into the option maps `option-menu` expects.

   `items` is a seq of maps that each have a `:name` (the text shown) and a `:key`
   (its identity). For each item you pass two small functions, both of which
   receive that item map:
     selected-fn  ->  truthy when the item is currently chosen
     toggle-fn    ->  selects/deselects it (usually a re-frame dispatch)
   This is the glue nearly every homebrew-builder menu uses; the callers in
   views.cljs all follow the same shape."
  [items selected-fn toggle-fn]
  (mapv (fn [{:keys [name key] :as item}]
          {:key key
           :label name
           :selected? (boolean (selected-fn item))
           :selectable? true
           :on-toggle #(toggle-fn item)})
        items))

(def ^:private layouts
  [[:grid "Grid"] [:pills "Pills"] [:az "A–Z"]])

(defn layout-toggle
  "Segmented control bound to the GLOBAL layout. Reads/writes shared state, so any
   number of instances stay in sync — place one in each builder header."
  []
  (let [cur @(subscribe [::layout])]
    [:div.opt-menu-layout-toggle
     (doall
      (for [[mode label] layouts]
        ^{:key mode}
        [:span.opt-menu-layout-seg
         {:class (when (= cur mode) "active")
          :on-click #(dispatch [::set-layout mode])}
         label]))]))

;; ---------------------------------------------------------------------------
;; Chrome pieces
;; ---------------------------------------------------------------------------

(defn- pattern-banner [prefix slot-label]
  [:div.opt-menu-banner
   [:div.opt-menu-banner-caption "Every option reads"]
   [:div.opt-menu-banner-quote
    (str/trim prefix) " "
    [:span.opt-menu-banner-slot (or slot-label "keyword")]]])

(defn- search-box [menu-id]
  [:div.opt-menu-search-wrap
   [:span.opt-menu-search-icon "⌕"]
   [:input.opt-menu-search
    {:type "text"
     :value @(subscribe [::menu-query menu-id])
     :placeholder "Search…"
     :on-click #(.stopPropagation %)
     :on-change #(dispatch [::set-menu-query menu-id (.. % -target -value)])}]])

(defn- chips-tray [selected chip-fn on-clear]
  ;; The selected items, each removable by clicking its ×. Clear lives here too
  ;; (right-aligned) since it acts on exactly these chips.
  (when (seq selected)
    [:div.opt-menu-chips
     [:span.opt-menu-chips-label "Chosen"]
     (doall
      (for [o selected]
        ^{:key (:key o)}
        [:span.opt-menu-chip
         {:on-click (fn [e] (.stopPropagation e) ((:on-toggle o)))}
         (chip-fn o)
         [:span.opt-menu-chip-x "×"]]))
     (when on-clear
       [:span.opt-menu-clear {:on-click on-clear} "Clear"])]))

(defn- count-line [selected total]
  [:div.opt-menu-count (str (count selected) " of " total " selected")])

(defn summarize-selected
  "One-line 'Chosen' summary for a collapsed section: the first three labels, then
   '+N more', or 'Nothing selected yet' when empty."
  [labels]
  (cond
    (empty? labels) "Nothing selected yet"
    (<= (count labels) 3) (str "Chosen: " (str/join ", " labels))
    :else (str "Chosen: " (str/join ", " (take 3 labels))
               "  +" (- (count labels) 3) " more")))

(defn- section-header
  "Panel header row: optional amber accent tab, the title, a count pill, Clear, and
   (when collapsible) a chevron. The whole row toggles collapse; Clear stops the
   click from also toggling."
  [{:keys [title top-level? count-label show-count? show-clear? clear-fn
           collapsible? collapsed? on-toggle]}]
  [:div.opt-section-head
   (when collapsible? {:class "collapsible" :on-click on-toggle})
   (when top-level? [:span.opt-section-accent])
   [:span {:class (if top-level? "opt-section-title" "opt-subsection-title")} title]
   (when show-count? [:span.opt-section-count count-label])
   (when (and show-clear? clear-fn)
     [:span.opt-menu-clear {:on-click (fn [e] (.stopPropagation e) (clear-fn))} "Clear"])
   (when collapsible?
     [:i.fa.fa-chevron-down.opt-section-chevron {:class (when collapsed? "collapsed")}])])

(defn- wildcard-group
  "The 'Choose any' dashed group (the Any-N options) shown above a panel's list.
   Each wildcard is {:key :name :selected? :on-toggle}."
  [wildcards]
  [:div.opt-wildcards
   [:span.opt-wildcards-label "Choose any"]
   [:div.opt-wildcards-list
    (doall
     (for [{:keys [key name selected? on-toggle]} wildcards]
       ^{:key key}
       [:div.opt-wildcard {:class (when selected? "selected")
                           :on-click #(on-toggle)}
        [:span.checkbox-box.dashed {:class (when selected? "checked")}
         (when selected? [:i.fa.fa-check])]
        [:span name]]))]])

;; ---------------------------------------------------------------------------
;; Cells
;; ---------------------------------------------------------------------------

(defn default-cell
  "Render one option. SRD callers attach a pre-built `:card`; everyone else gets a
   checkbox cell. `layout` is :grid/:pills/:az (cells differ for pills)."
  [{:keys [card display label selected? selectable? non-standard? on-toggle]} layout]
  (cond
    ;; Rich SRD card: use as-is for grid/az. Pills can't shrink a card, so it still
    ;; renders the card (documented fallback) rather than mangling it.
    card card

    (= layout :pills)
    [:span.opt-menu-pill
     {:class (str/join " " (remove nil? [(when selected? "selected")
                                         (when non-standard? "non-standard")]))
      :on-click #(when selectable? (on-toggle))}
     (when selected? [:i.fa.fa-check.m-r-5])
     (or display label)]

    :else
    [:div.opt-menu-cell
     {:class (str/join " " (remove nil? [(when selected? "selected")
                                         (when non-standard? "non-standard")
                                         (when-not selectable? "opacity-5")]))
      :on-click #(when selectable? (on-toggle))}
     (when non-standard? [:span.non-standard-badge "≠ NON-STD"])
     (comps/checkbox selected? (not selectable?))
     [:span (or display label)]]))

(defn- display-letter [{:keys [display label]}]
  (let [ch (str/upper-case (str (first (or display label))))]
    (if (re-matches #"[A-Z]" ch) ch "#")))

;; ---------------------------------------------------------------------------
;; Layout bodies
;; ---------------------------------------------------------------------------

(defn- grid-body [items cell-fn]
  [:div.opt-menu-grid
   (doall
    (for [o items]
      ^{:key (:key o)}
      [:div {:class (when (:non-standard? o) "opt-menu-grid-span")}
       (cell-fn o :grid)]))])

(defn- pills-body [items cell-fn]
  ;; cell-fn returns a vector; attach the React key to that vector directly
  ;; (metadata on the call form wouldn't survive evaluation).
  [:div.opt-menu-pills
   (doall
    (for [o items]
      (with-meta (cell-fn o :pills) {:key (:key o)})))])

(defn- az-body [menu-id annotated filtered cell-fn]
  (let [active  @(subscribe [::menu-letter menu-id])
        letters (grouping/present-letters annotated)
        shown   (if active (filterv #(= active (display-letter %)) filtered) filtered)
        groups  (grouping/group-by-letter shown)]
    [:div.opt-menu-az
     [:div.opt-menu-az-bar
      [:span.opt-menu-az-letter {:class (when (nil? active) "active")
                                 :on-click #(dispatch [::set-menu-letter menu-id nil])}
       "All"]
      (doall
       (for [l letters]
         ^{:key l}
         [:span.opt-menu-az-letter
          {:class (when (= l active) "active")
           :on-click #(dispatch [::set-menu-letter menu-id (when (not= l active) l)])}
          l]))]
     (doall
      (for [{:keys [letter items]} groups]
        ^{:key letter}
        [:div.opt-menu-az-group
         [:div.opt-menu-az-heading letter]
         [:div.opt-menu-grid
          (doall
           (for [o items]
             ^{:key (:key o)}
             [:div {:class (when (:non-standard? o) "opt-menu-grid-span")}
              (cell-fn o :az)]))]]))]))

;; ---------------------------------------------------------------------------
;; The component
;; ---------------------------------------------------------------------------

;; A list only gets a search box once it's long enough to need one (matches the
;; reference's SEARCH_MIN); short lists stay compact.
(def ^:private search-min 10)

(defn option-menu
  "Growable multi-select menu / section panel.

   Required: `:menu-id` (stable id; keys per-menu search + letter) and `:options`
   (normalized vector). Optional:
     :title        when set, renders the panel header (accent + title + count + Clear)
                   instead of the legacy count line; `:top-level?` sizes the accent.
     :wildcards    the 'Choose any' Any-N group shown above the list
     :slot-label   pattern-banner slot word
     :multiselect? default true; gates count/Clear (and the legacy chips tray)
     :collapsible? when true (and :title set), the header toggles a persisted collapse
                   that hides the body behind a 'Chosen' summary line
     :on-clear / :cell-fn / :chip-fn / :trailer"
  [{:keys [menu-id title top-level? wildcards slot-label options
           multiselect? collapsible? on-clear cell-fn chip-fn trailer]
    :or   {multiselect? true}}]
  (let [collapsible? (boolean (and title collapsible?))
        collapsed?   (and collapsible? @(subscribe [::collapsed menu-id]))
        layout    @(subscribe [::layout])
        query     @(subscribe [::menu-query menu-id])
        cell-fn   (or cell-fn default-cell)
        chip-fn   (or chip-fn (fn [o] (or (:display o) (:label o))))
        ;; classify FIRST so :display/:non-standard? line up with options, THEN filter
        labels    (mapv :label options)
        prefix    (memo-prefix labels)
        annotated (mapv merge options (memo-classify labels prefix))
        q         (str/lower-case (str/trim (or query "")))
        filtered  (if (str/blank? q)
                    annotated
                    (filterv (fn [{:keys [display label]}]
                               (or (str/includes? (str/lower-case (str display)) q)
                                   (str/includes? (str/lower-case (str label)) q)))
                             annotated))
        selected   (filterv :selected? annotated)
        has-opts?  (seq options)
        clear-fn   (when multiselect?
                     (or on-clear #(doseq [o selected] ((:on-toggle o)))))
        searchable? (>= (count options) search-min)]
    [:div.opt-menu
     (if title
       ;; new panel header: count pill + Clear live in the title row
       [section-header {:title title
                        :top-level? top-level?
                        :show-count? (and multiselect? has-opts?)
                        :count-label (str (count selected) " of " (count options) " selected")
                        :show-clear? (and multiselect? (seq selected))
                        :clear-fn clear-fn
                        :collapsible? collapsible?
                        :collapsed? collapsed?
                        :on-toggle #(dispatch [::toggle-collapsed menu-id])}]
       ;; legacy header (used by builders not yet migrated to the panel look)
       (when multiselect? [count-line selected (count options)]))
     (if collapsed?
       [:div.opt-section-summary (summarize-selected (mapv :label selected))]
       [:div
        (when (seq wildcards) [wildcard-group wildcards])
        (when prefix [pattern-banner prefix slot-label])
        (when (and has-opts? searchable?) [search-box menu-id])
        ;; legacy chips tray only in the no-title mode; the panel header carries Clear
        (when (and (not title) multiselect?) [chips-tray selected chip-fn clear-fn])
        (when has-opts?
          (cond
            (empty? filtered) [:div.opt-menu-empty (str "No options match “" query "”.")]
            (= layout :pills) [pills-body filtered cell-fn]
            (= layout :az)    [az-body menu-id annotated filtered cell-fn]
            :else             [grid-body filtered cell-fn]))
        (when trailer trailer)])]))

;; ---------------------------------------------------------------------------
;; Section containment — cards (top-level) and recessed wells (nested children)
;; ---------------------------------------------------------------------------

(defn section-card
  "A standalone top-level section: an elevated card around one option-menu panel.
   Collapsible only once it's long enough to be worth collapsing (>= search-min)."
  [opts]
  [:div.opt-section
   [option-menu (assoc opts
                       :top-level? true
                       :collapsible? (>= (count (:options opts)) search-min))]])

(defn subsection
  "A recessed child well around one option-menu panel (nested inside a parent card).
   Children are never independently collapsible — only the parent card collapses."
  [opts]
  [:div.opt-subsection [option-menu (assoc opts :top-level? false)]])

(defn parent-section
  "A parent card with a heading and a stack of recessed child wells nested inside.
   `children` are already-rendered hiccup (use `subsection` for option-menu panels,
   or any `.opt-subsection` element). With `:collapse-id` the whole card collapses
   (incl. children) behind a `summarize-selected` line built from `:summary-labels`;
   `meta` is an optional summary pill (e.g. count)."
  [{:keys [title meta collapse-id summary-labels]} & children]
  (let [collapsed? (and collapse-id @(subscribe [::collapsed collapse-id]))]
    [:div.opt-section
     [:div.opt-section-head
      (when collapse-id
        {:class "collapsible" :on-click #(dispatch [::toggle-collapsed collapse-id])})
      [:span.opt-section-accent.tall]
      [:span.opt-section-title title]
      (when meta [:span.opt-section-count meta])
      (when collapse-id
        [:i.fa.fa-chevron-down.opt-section-chevron {:class (when collapsed? "collapsed")}])]
     (if collapsed?
       [:div.opt-section-summary (summarize-selected summary-labels)]
       (into [:div.opt-subsections] children))]))
