# OrcPub Implementation — growable multi-select menus

This maps the prototype redesign onto **the real OrcPub source**. It's written for
a developer (or Claude Code) working in the repo. All paths are repo paths.

## Where the ragged menus come from
`src/cljs/orcpub/character_builder.cljs`:

- **`option-selector-base`** (~L472) renders ONE option as a bordered orange box:
  `[:div.p-10.b-1.b-rad-5.m-5.b-orange …]` with an optional checkbox
  (`comps/checkbox`) when `multiselect?`, an optional icon, and the `name`.
- **`default-selection-section-body`** (~L692) is what produces the ragged column
  look. It:
  1. sorts options `(sort-by (juxt ::t/order ::t/name) options)`,
  2. maps each to `[new-option-selector …]`,
  3. `(partition-all (round-up (/ count num-columns)) …)` — splits the flat list
     into `num-columns` **fixed vertical chunks**, laid out with `:div.flex` and
     each column `w-{100/num-columns}-p`.

Fixed-percent columns + variable-length labels = the ragged rows in the screenshots.
The data layer is unchanged by this redesign: options are template maps with
`::t/name`, `::t/key`, `::t/order`; selection/selectable state comes from
`views-aux/option-selector-data`. We only change **rendering**.

## Strategy
Replace the column-partition layout with a **CSS-Grid auto-fill** layout (aligned,
width- and count-agnostic), and add the **boilerplate collapse + divergence**
treatment driven by the new pure namespace
`src/cljs/orcpub/dnd/e5/option_grouping.cljs` (provided alongside this doc — copy it
into the repo). Optionally add the **A–Z** and **keyword-pill** modes behind a
per-section UI toggle; ship the aligned grid first.

Keep using `new-option-selector` / `option-selector-base` for each option so all the
existing selected?/selectable?/help/edit wiring keeps working — we just thread two
extra display hints through.

## Patch 1 — thread display hints through `option-selector-base`
Add `display-name` and `non-standard?` to the destructured keys, and use them for
the label. `name` stays the fallback and is still used as the React key upstream.

```clojure
(defn option-selector-base []
  (let [expanded? (r/atom false)]
    (fn [{:keys [name display-name non-standard? help selected? selectable?
                 option-path select-fn content explanation-text icon classes
                 multiselect? disable-checkbox? edit-event]}]
      [:div.p-10.b-1.b-rad-5.m-5.b-orange
       {:class-name (s/join " " (conj
                                 (remove nil? [(when selected? "b-w-5")
                                               (when selectable? "pointer hover-shadow")
                                               (when (not selectable?) "opacity-5")
                                               (when non-standard? "b-w-3 non-standard-option")])
                                 classes))
        :on-click select-fn}
       [:div.flex.align-items-c
        [:div.flex-grow-1
         [:div.flex.align-items-c
          (when multiselect?
            [:span.m-r-5 (comps/checkbox selected? disable-checkbox?)])
          (when non-standard?
            [:span.non-standard-badge.m-r-5 "≠ NON-STD"])
          (when icon [:div.m-r-5 (views5e/svg-icon icon 24)])
          [:span.f-w-b.f-s-1.flex-grow-1 (or display-name name)]
          (when edit-event
            [:span.orange.underline.pointer
             {:on-click (apply views5e/make-stop-prop-event-handler edit-event)}
             "edit"])
          (when help [show-info-button expanded?])]
         (when (and help @expanded?) [help-section help])
         (when (and content selected?) content)
         (when explanation-text [:div.i.f-s-12.f-w-n explanation-text])]]])))
```

`new-option-selector` (~L531) builds the data map passed to `option-selector-base`.
Add the two keys when assoc-ing `data`, defaulting to nil so all other callers are
unaffected:

```clojure
[option-selector-base (assoc data
                             :display-name display-name      ; nil unless provided
                             :non-standard? non-standard?
                             :help …)]
```
Give `new-option-selector` two new optional trailing params (or pass a small
`{:display-name … :non-standard? …}` opts map) and have
`default-selection-section-body` supply them from the classification below.

## Patch 2 — grid + collapse in `default-selection-section-body`
Drop the `partition-all` columns; render one CSS-Grid container. Compute the
dominant prefix from the option **names**, classify, and feed display hints in.

```clojure
(ns … (:require … [orcpub.dnd.e5.option-grouping :as grouping]))

(defn pattern-banner [prefix slot-label]
  ;; "EVERY OPTION READS  <prefix> [slot]" — the quoted example
  [:div.m-5
   [:div.f-s-10.f-w-b.uppercase.opacity-7.m-b-5 "Every option reads"]
   [:div.b-rad-5.p-10
    {:style {:border-left "3px solid #f0a100"
             :background "rgba(240,161,0,0.06)"
             :font-style "italic" :font-size "15px" :line-height 1.6}}
    (str/trim prefix) " "
    [:span.b-rad-5
     {:style {:border "1px dashed rgba(240,161,0,0.7)" :color "#f0a100"
              :font-style "normal" :font-weight 600 :padding "1px 11px"
              :border-radius "999px"}}
     (or slot-label "keyword")]]])

(defn default-selection-section-body [actual-path
                                      {:keys [::t/options] :as selection}
                                      disable-select-new? homebrew? num-columns]
  (let [sorted   (sort-by (juxt ::t/order ::t/name) options)
        labels   (mapv ::t/name sorted)
        prefix   (grouping/dominant-prefix labels)
        ann      (grouping/classify labels prefix)            ; parallel to `sorted`
        slot     (::t/slot-label selection)                  ; optional, per-selection
        selectors
        (doall
         (map
          (fn [option {:keys [display non-standard?]}]
            ^{:key (::t/key option)}
            [:div {:style (when non-standard? {:grid-column "1 / -1"})}
             [new-option-selector actual-path selection disable-select-new? homebrew?
              option {:display-name (when prefix display)     ; collapsed keyword
                      :non-standard? non-standard?}]])
          sorted ann))
        item-adder (make-item-adder selection)]
    [:div
     (when prefix [pattern-banner prefix slot])
     [:div {:style {:display "grid"
                    :grid-template-columns "repeat(auto-fill, minmax(180px, 1fr))"
                    :align-items "start"
                    :gap "2px"}}
      selectors]
     (when item-adder item-adder)]))
```

Notes:
- `num-columns` is now advisory — the grid self-fits. You can keep the arg for
  signature compatibility (callers pass it) and ignore it, or derive `minmax` min
  width from it. Auto-fill is what makes the layout survive new options + resize.
- Non-standard options get `grid-column: 1 / -1` so they break to their own full
  row, where the badge + 3px border make them obvious. Their label is shown in
  full (because `display-name` is only set when `conform?`, so non-standard falls
  back to `name`). To additionally mute the shared head and highlight the tail,
  render the label from `:diverge-at` in `option-selector-base` instead of a plain
  string (optional polish — see prototype).
- The `comps/checkbox` + selected (`b-w-5`) treatment is unchanged, so multi-select
  behavior, "remaining" counts, and the stepper all keep working.

## Patch 3 — Garden styles (`src/clj/orcpub/styles/core.clj`)
Add to the `props` vector (atomic classes) — reusing the existing `orange`
constant. See `STYLING_GUIDE.md` for why this is the right file/bucket.

```clojure
[:.non-standard-badge
 {:background-color orange
  :color "#191919"
  :font-size "10px"
  :font-weight 700
  :border-radius "4px"
  :padding "1px 5px"
  :letter-spacing "0.03em"
  :white-space "nowrap"}]

[:.non-standard-option
 {:background-color "rgba(240,161,0,0.06)"}]
```
(There is no CSS-Grid utility in the system, which is why the grid container uses an
inline `:style` map — the idiomatic escape hatch per the styling guide. If you'd
rather not inline it, add a `.option-grid` component class under `app` with the
grid properties.)

Recompile CSS with `lein garden once` (Figwheel hot-reloads it). The `.cljs`
changes hot-reload via Figwheel on save.

## Optional — layout toggle (grid / pills / A–Z)
Ship the aligned grid first; it's the highest-value, lowest-risk change. If you
want the user-facing toggle from the prototype:
- Hold `layout` in app state (re-frame) — e.g. `[:builder-menu-layout]`, default
  `:grid`. A small segmented control in the section header dispatches to set it.
- **Pills:** same `selectors`, but render each as a rounded toggle (`flex-wrap`
  container, `gap`) instead of grid cells — reuse `comps/checkbox`'s selected state
  via the pill's filled style.
- **A–Z:** `grouping/group-by-letter` over the classified items → a letter heading
  per group; `grouping/present-letters` (unfiltered) → the jump bar; a `letter`
  atom/sub filters to one initial.
All three read the same classified data; only presentation differs.

## Don't ship the sample
The prototype includes one illustrative non-standard option
("You can't be Charmed by fey creatures") **purely to demo the divergence path**.
It is not real data — do not add it to the option templates. Real divergence will
appear naturally if/when homebrew or new content introduces differently-worded
options.

## Files in this bundle
- `option_grouping.cljs` — drop into `src/cljs/orcpub/dnd/e5/`. The reusable logic.
- `STYLING_GUIDE.md` — how the Garden/utility styling system works (read first).
- `../prototype/Character Menus.dc.html` — the visual/interaction reference.
- `../README.md` — framework-agnostic spec & design tokens.
- `../menu-logic.js` — the same algorithms in JS (for reference / non-CLJS reuse).
