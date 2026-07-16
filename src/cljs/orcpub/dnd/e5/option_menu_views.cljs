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
            [orcpub.dnd.e5.themes :as themes]
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

;; Active builder theme (Classic / Dwarven / Arcane) — a persisted user preference like
;; the menu layout. Drives the header shape, page environment, and glyphs (see themes.cljs).
(reg-sub ::builder-theme (fn [db _] (get-in db [:user-data :builder-theme] :classic)))

(reg-event-db
 ::set-builder-theme
 [persist-user]
 (fn [db [_ t]] (assoc-in db [:user-data :builder-theme] t)))

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
(def ^:private memo-suffix (memoize grouping/dominant-suffix))
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

(defn segmented-control
  "Reusable sliding-thumb segmented control. `options` is [[value label] …]; the amber
   thumb slides to the active segment. Equal-width columns sized from the option count,
   so translateX(idx*100%) lands on segment idx — works for any number of segments."
  [{:keys [value options on-change class]}]
  (let [n (count options)
        idx (or (first (keep-indexed (fn [i [v _]] (when (= v value) i)) options)) 0)]
    [:div.segmented-control
     {:class class
      :style {:grid-template-columns (str "repeat(" n ", 1fr)")}}
     [:div.segmented-control-thumb
      {:style {:width (str "calc((100% - 8px) / " n ")")
               :transform (str "translateX(" (* idx 100) "%)")}}]
     (doall
      (for [[v label] options]
        ^{:key v}
        [:span.segmented-control-seg
         {:class (when (= v value) "active")
          :on-click #(on-change v)}
         label]))]))

(defn layout-toggle
  "Segmented control bound to the GLOBAL layout. Reads/writes shared state, so any
   number of instances stay in sync — place one in each builder header."
  []
  [segmented-control
   {:value @(subscribe [::layout])
    :options layouts
    :on-change #(dispatch [::set-layout %])}])

(def ^:private theme-chevron
  [:svg {:width "12" :height "12" :viewBox "0 0 24 24" :fill "none" :stroke "currentColor"
         :stroke-width "2.5" :stroke-linecap "round" :stroke-linejoin "round"}
   [:polyline {:points "6 9 12 15 18 9"}]])

(defn theme-switcher
  "End-user theme control: a swatch+label button opening a palette dropdown of themes
   (swatch, label, per-theme note). Sets the persisted ::builder-theme; dismisses on an
   outside click. A custom button+popover (not a native select) so the swatches and notes
   render and the menu aligns under the button."
  []
  (let [open?    (r/atom false)
        wrap-ref (atom nil)
        on-doc   (fn [e] (let [n @wrap-ref]
                           (when (and n (not (.contains n (.-target e)))) (reset! open? false))))]
    (r/create-class
     {:component-did-mount    (fn [_] (js/document.addEventListener "mousedown" on-doc))
      :component-will-unmount (fn [_] (js/document.removeEventListener "mousedown" on-doc))
      :reagent-render
      (fn []
        (let [current @(subscribe [::builder-theme])
              cur     (themes/theme current)]
          [:div.theme-switch {:ref #(reset! wrap-ref %)}
           [:button.theme-switch-btn
            {:on-click (fn [e] (.stopPropagation e) (swap! open? not))}
            [:span.theme-swatch {:style {:background (:swatch cur)}}]
            [:span.theme-switch-label (:label cur)]
            [:span.theme-switch-chev {:class (when @open? "open")} theme-chevron]]
           (when @open?
             [:div.theme-switch-menu
              (doall
               (for [k themes/theme-order]
                 (let [t (themes/theme k)]
                   ^{:key k}
                   [:button.theme-opt {:class (when (= k current) "active")
                                       :on-click (fn [] (reset! open? false)
                                                   (dispatch [::set-builder-theme k]))}
                    [:span.theme-opt-head
                     [:span.theme-swatch {:style {:background (:swatch t)}}]
                     [:span.theme-opt-label (:label t)]]
                    [:span.theme-opt-note (:note t)]])))])]))})))

(def ^:private crest-svg
  ;; the app crest — a constant background rune (lower-left watermark). Literal inline SVG
  ;; (JS-built SVG didn't render through the template holes).
  [:svg {:viewBox "0 0 100 100" :width "620" :height "620" :fill "none"
         :stroke "currentColor" :stroke-width "0.5"}
   [:circle {:cx 50 :cy 50 :r 47}]
   [:circle {:cx 50 :cy 50 :r 38}]
   [:circle {:cx 50 :cy 50 :r 26}]
   [:polygon {:points "50,3 91,26 91,74 50,97 9,74 9,26"}]
   [:polygon {:points "50,12 83,31 83,69 50,88 17,69 17,31"}]
   [:line {:x1 50 :y1 3 :x2 50 :y2 97}]
   [:line {:x1 9 :y1 26 :x2 91 :y2 74}]
   [:line {:x1 91 :y1 26 :x2 9 :y2 74}]])

(defn page-environment
  "Stackable page-environment behind the content — fixed, negative-z layers toggled by
   the active theme's :page tokens: an ambient radial backdrop (+amber glow), the crest
   watermark (lower-left), a lifted column-spine lane, and soft-light grain. Mechanics are
   central; a theme just flips which layers are on."
  []
  (let [page (themes/page-fx @(subscribe [::builder-theme]))]
    [:div.page-fx
     (when (:ambient page)   [:div.page-fx-ambient])
     (when (:ambient page)   [:div.page-fx-ambient-glow])
     (when (:spine page)     [:div.page-fx-spine])
     (when (:watermark page) [:div.page-fx-watermark crest-svg])
     (when (:grain page)     [:div.page-fx-grain])]))

(defn select-menu
  "Custom button+popover select — alignment-controllable, unlike a native <select> whose
   popup is OS-positioned and can't be styled/aligned. `options` is [[value label] …];
   `on-change` receives the chosen value. Dismisses on an outside click."
  [_opts]
  (let [open?    (r/atom false)
        wrap-ref (atom nil)
        on-doc   (fn [e] (let [n @wrap-ref]
                           (when (and n (not (.contains n (.-target e)))) (reset! open? false))))]
    (r/create-class
     {:component-did-mount    (fn [_] (js/document.addEventListener "mousedown" on-doc))
      :component-will-unmount (fn [_] (js/document.removeEventListener "mousedown" on-doc))
      :reagent-render
      (fn [{:keys [value options on-change placeholder]}]
        (let [cur (some (fn [[v l]] (when (= v value) l)) options)]
          [:div.select-menu {:ref #(reset! wrap-ref %)}
           [:button.select-menu-btn
            {:type "button" :on-click (fn [e] (.stopPropagation e) (swap! open? not))}
            [:span (or cur placeholder "Select…")]
            [:span.select-menu-chev {:class (when @open? "open")} theme-chevron]]
           (when @open?
             [:div.select-menu-pop
              (doall
               (for [[v l] options]
                 ^{:key (str v)}
                 [:button.select-menu-opt
                  {:type "button" :class (when (= v value) "active")
                   :on-click (fn [] (reset! open? false) (on-change v))}
                  l]))])]))})))

(defn header-mark
  "The per-theme header badge — a large faint warm-tinted glyph bleeding off the band's
   upper-right (clipped by the band's overflow). Reads the active theme's :glyph token;
   renders nothing when the theme has no glyph (Classic). Pairs with the cool-slate crest
   watermark (lower-left) so the two read as one glyph system."
  []
  (let [g (:glyph (themes/tokens @(subscribe [::builder-theme])))]
    (when g
      [:div.header-mark {:style {:color (get themes/glyph-colors g "#f0a100")}}
       (get themes/glyphs g)])))

(defn layout-control-row
  "An anchored control row: the theme switcher on the left, the global layout selector on
   the right, placed with the menus/header they control."
  []
  [:div.opt-layout-control
   [theme-switcher]
   [:div.opt-layout-control-right
    [:span.opt-layout-control-label "Option Layout"]
    [layout-toggle]]])

(defn info-popover
  "A ⓘ button that toggles a small popover on click — works with mouse AND touch, no
   hover dependency. The ⓘ toggles; a click/tap anywhere outside dismisses it (a
   document mousedown listener that ignores clicks within this widget). `text` is the
   popover body."
  [_text]
  (let [open?    (r/atom false)
        wrap-ref (atom nil)
        outside? (fn [e] (let [n @wrap-ref] (and n (not (.contains n (.-target e))))))
        on-doc   (fn [e] (when (outside? e) (reset! open? false)))]
    (r/create-class
     {:component-did-mount    (fn [_] (js/document.addEventListener "mousedown" on-doc))
      :component-will-unmount (fn [_] (js/document.removeEventListener "mousedown" on-doc))
      :reagent-render
      (fn [text]
        [:span.opt-info-wrap
         {:ref #(reset! wrap-ref %)}
         [:i.fa.fa-info-circle.opt-info-btn
          {:role "button"
           :aria-label "More info"
           :on-click (fn [e] (.preventDefault e) (.stopPropagation e) (swap! open? not))}]
         (when @open?
           ;; clicking the popover (which overlaps the ⓘ) also dismisses it; combined
           ;; with the outside-click handler, a tap anywhere closes it.
           [:div.opt-info-popover
            {:on-click (fn [e] (.stopPropagation e) (reset! open? false))}
            text])])})))

;; ---------------------------------------------------------------------------
;; Chrome pieces
;; ---------------------------------------------------------------------------

(defn- pattern-banner [prefix slot-label suffix]
  [:div.opt-menu-banner
   [:div.opt-menu-banner-caption "Every option reads"]
   [:div.opt-menu-banner-quote
    (str/trim prefix) " "
    [:span.opt-menu-banner-slot (or slot-label "keyword")]
    ;; value-in-the-middle boilerplate: show the shared tail after the slot too
    (when suffix (str " " (str/trim suffix)))]])

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
     :header-extra hiccup rendered at the top of the body (e.g. a 'Choose N' dropdown),
                   hidden along with the body when collapsed
     :on-clear / :cell-fn / :chip-fn / :trailer"
  [{:keys [menu-id title top-level? wildcards slot-label options header-extra
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
        suffix    (memo-suffix labels prefix)
        annotated (mapv merge options (memo-classify labels prefix suffix))
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
     (when collapsed?
       [:div.opt-section-summary (summarize-selected (mapv :label selected))])
     ;; body stays mounted and animates open/closed via grid-template-rows; very long
     ;; lists skip the animation (instant) to avoid reflow jank.
     [:div.opt-section-body
      {:class (str/join " " (remove nil? [(when collapsed? "collapsed")
                                          (when (>= (count options) 40) "instant")]))}
      [:div.opt-section-body-inner
       (when header-extra header-extra)
       (when (seq wildcards) [wildcard-group wildcards])
       (when prefix [pattern-banner prefix slot-label suffix])
       (when (and has-opts? searchable?) [search-box menu-id])
       ;; legacy chips tray only in the no-title mode; the panel header carries Clear
       (when (and (not title) multiselect?) [chips-tray selected chip-fn clear-fn])
       (when has-opts?
         (cond
           (empty? filtered) [:div.opt-menu-empty (str "No options match “" query "”.")]
           (= layout :pills) [pills-body filtered cell-fn]
           (= layout :az)    [az-body menu-id annotated filtered cell-fn]
           :else             [grid-body filtered cell-fn]))
       (when trailer trailer)]]]))

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

(defn card
  "A flat section card with an amber accent-tab heading wrapping arbitrary content —
   for form-field sections (dropdowns, inputs) that aren't option menus."
  [title & body]
  (into [:div.opt-section
         [:div.opt-section-head
          [:span.opt-section-accent]
          [:span.opt-section-title title]]]
        body))

(defn group-label
  "A lightweight, NON-card section label: a small uppercase eyebrow + a hairline rule.
   For grouping headings that sit above already-carded option-menus (Modifiers, Spells,
   Spellcasting, …) — keeps the page flat instead of nesting card-in-card. The menus
   follow it as their own top-level cards."
  [title]
  [:div.opt-group-label title])

(defn subsection
  "A recessed child well around one option-menu panel (nested inside a parent card).
   A child is independently collapsible once it's large enough to be worth it
   (>= search-min options, e.g. Other Equipment), so a big list can be tucked away
   without collapsing its whole parent. Small children stay open."
  [opts]
  [:div.opt-subsection
   [option-menu (assoc opts
                       :top-level? false
                       :collapsible? (>= (count (:options opts)) search-min))]])

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
     (when collapsed?
       [:div.opt-section-summary (summarize-selected summary-labels)])
     ;; parents aggregate large child lists, so collapse them instantly (no height
     ;; animation) to avoid reflow jank.
     [:div.opt-section-body.instant {:class (when collapsed? "collapsed")}
      [:div.opt-section-body-inner
       (into [:div.opt-subsections] children)]]]))
