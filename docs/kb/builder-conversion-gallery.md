# Builder conversion gallery — the bespoke form, and the one that replaced it

**What this is.** Side-by-side pictures and code for each builder form that has been converted from
hand-written hiccup to a declarative schema, plus the measurements that say whether the conversion
was actually an improvement. Every image is the **real app** — `lein e2e-server`, the real route,
the real widgets — captured by a script, not a mockup.

**Reproduce any picture here:**

```bash
lein fig:build && lein garden once && lein e2e-server      # port 8890
LABEL=after node test/e2e/builder-gallery.js               # every builder page
LABEL=after node test/e2e/form-shape-comparison.js         # one builder, authored, with metrics
```

To photograph a *previous* shape, stash the source and rebuild — the scripts run against both:

```bash
git stash push -- src/ && lein fig:build
LABEL=before node test/e2e/form-shape-comparison.js
git stash pop && lein fig:build
```

---

## Pair 1 — Language builder (tier 1): 21 lines → 1

| bespoke (`492e2c32`) | generated (`2e91037d`) |
|---|---|
| ![bespoke](assets/builder-comparison/language-bespoke.jpg) | ![generated](assets/builder-comparison/language-generated.jpg) |

**The two JPEGs are byte-identical** — `cmp` reports no difference. Same fields, same order, same
spacing, same everything. That is the claim a tier-1 conversion should be able to make, and it is
checkable rather than asserted:

```bash
cmp docs/kb/assets/builder-comparison/language-bespoke.jpg \
    docs/kb/assets/builder-comparison/language-generated.jpg
```

### The code

```clojure
;; BEFORE — 19 lines of hiccup, plus a 2-line language-input-field with exactly one caller
(defn language-builder []
  (let [language @(subscribe [::langs/builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [language-input-field "Name" :name language "m-b-20"]
      [plugin-datalist option-source-name-label language ::langs/set-language-prop]]
     [:div.w-100-p
      [:div.f-s-24.f-w-b "Description"]
      [textarea-field
       {:value (get language :description)
        :on-change #(dispatch [::langs/set-language-prop :description %])}]]]))

;; AFTER
(defn language-builder []
  (simple-content-builder ::langs/builder-item ::langs/set-language-prop))
```

Nothing was designed for this. `simple-content-builder` already existed (June 2026) and `boon` and
`invocation` already used it; the language builder had simply never been pointed at it. **The
interesting part of a tier-1 conversion is not the diff, it is the procedure** — pin the *existing*
form with a test that asserts only observable behaviour, swap, run the same test. Both runs 12/12,
same saved item (`test/e2e/language-builder.js`).

---

## Pair 2 — Fighting Style builder (tier 2): flat fields → `:rows`

This is the one worth looking at, because it is where a declarative form stopped being merely
shorter and started being *better*.

### Empty

| flat | rows |
|---|---|
| ![flat empty](assets/builder-comparison/fighting-style-flat-empty.jpg) | ![rows empty](assets/builder-comparison/fighting-style-rows-empty.jpg) |

### The same style authored — +1 AC, +2 attack, +2 damage

| flat | rows |
|---|---|
| ![flat authored](assets/builder-comparison/fighting-style-flat-authored.jpg) | ![rows authored](assets/builder-comparison/fighting-style-rows-authored.jpg) |

Look at the middle of the flat form. **Melee, Ranged, Heavy, Thrown, Finesse, Light, Handedness —
each appears twice**, once for the attack bonus and once for the damage bonus, with nothing on
screen saying which is which. In the grouped form each sits inside a bordered group headed *Attack
Bonus* or *Damage Bonus*.

### Measured, not eyeballed

`test/e2e/form-shape-comparison.js` counts controls and ambiguous labels in both builds:

| | flat | rows |
|---|---:|---:|
| visible controls, empty form | 7 | **4** |
| visible controls, three effects authored | 23 | 23 |
| labels repeated with nothing distinguishing them | **7** | **0** |
| page height, authored | 2229px | 2513px |

**Two of these do not flatter the change, and both are real.** The fully-authored form has exactly
the same 23 controls — grouping does not remove any field — and it is **284px taller**, because the
row borders, titles and hints cost vertical space. What the rows buy is (a) an author who wants one
AC bonus sees four controls instead of seven and never scrolls past fourteen they do not want, and
(b) every tag is unambiguous. If someone later argues the chrome is not worth 284px, that is a fair
argument and the numbers for it are here.

An "ambiguous label" is counted as a label repeated **within the same group** (the flat form's
labels are all in one implicit group). Two `Melee`s under two different titled rows are not
ambiguous and are not counted — the metric measures the defect, not the repetition.

### The code

```clojure
;; BEFORE — every field of every effect, concatenated flat
(simple-content-builder ::classes/fighting-style-builder-item
                        ::classes/set-fighting-style-prop
                        (concat bf/fighting-style-classes-field
                                bf/ac-bonus-fields
                                bf/attack-bonus-fields
                                bf/damage-bonus-fields))

;; AFTER
(simple-content-builder ::classes/fighting-style-builder-item
                        ::classes/set-fighting-style-prop
                        (concat bf/fighting-style-classes-field
                                (bf/effect-rows)))
```

and `effect-rows` is data — the same shared field fragments, unedited, arranged:

```clojure
[{:rows      :effects
  :title     "Effects"
  :add-label "Add an effect"
  :kinds     [{:kind :ac-bonus     :title "AC Bonus"     :at [:props :ac-bonus]
               :hint "added to whichever AC calculation wins"
               :fields ac-bonus-fields}
              {:kind :attack-bonus :title "Attack Bonus" :at [:props :attack-bonus]
               :hint "to attack rolls with matching weapons"
               :fields attack-bonus-fields}
              {:kind :damage-bonus :title "Damage Bonus" :at [:props :damage-bonus]
               :hint "to damage rolls with matching weapons"
               :fields damage-bonus-fields}]}]
```

**It is an arrangement, not a second storage model.** A row is "present" when the item has data
under its `:at` path, so nothing extra is stored to mark a row, an item authored by the old flat
form renders here unchanged, and the `.orcbrew` is untouched (D9). The e2e pins exactly that: a
bonus typed into a row lands at `:props {:ac-bonus {:bonus 1}}`, the same path as before.

### What the comparison caught in my own work

The first run reported `AC Bonus x2` as an ambiguous label in the **new** form: the row header said
*AC Bonus* and the number inside it said *AC Bonus* again. Fixed by relabelling the lead field to
*Bonus* — selected **by its path**, not its position, because the field fragments are shared with
other builders' flat forms and must not be edited for this one's benefit. The measurement earned its
place on its first run.

---

## Pair 3 — what a bespoke rows widget costs (not yet converted)

The `:rows` node exists because three widgets in `views.cljs` are already hand-written versions of
it. This is the one to convert next, and it is here as a *before* with no *after* yet:

```clojure
;; option-traits — the background/race/subclass traits list. Six event keywords, positionally.
(defn option-traits [option
                     add-trait-event
                     edit-trait-name-event
                     edit-trait-type-event
                     edit-trait-description-event
                     delete-trait-event
                     & {:keys [edit-trait-level-event types title button-title]}])
```

`creature-selector` (encounters) and `equipment-grant-row` are the other two. All three are the same
control: a repeatable list of typed rows. The difference from `effect-rows` is storage shape — these
are **vectors** (ordered, duplicates allowed) where effects are a **map keyed by kind**. That is the
open design question in `builder-form-schemas.md` §6, and converting `creature-selector` is what
settles it.

---

## Every builder, current state

From `target/e2e-shots/gallery-after/index.json` (visible controls on an empty form):

| builder | controls | form |
|---|---:|---|
| selection | 3 | bespoke |
| language | 4 | **generated** |
| boon | 4 | **generated** |
| invocation | 4 | **generated** |
| fighting-style | 4 | **generated, `:rows`** |
| feat | 4 | bespoke |
| encounter | 4 | bespoke (rows widget) |
| background | 5 | bespoke (rows widget) |
| draconic-ancestry | 7 | **generated** |
| subclass | 8 | bespoke |
| spell | 10 | bespoke |
| class | 14 | bespoke |
| subrace | 18 | bespoke |
| race | 22 | bespoke |
| monster | 46 | bespoke |

Five of fifteen are generated. The control count is a rough proxy for conversion cost, not for
difficulty — `monster` is 46 controls of plain fields and may well be easier than `background`,
which is five controls plus eight domain widgets.

## See also

- `builder-form-schemas.md` — the field-type table, the `:rows` design and the phase plan.
- `fighting-style-authoring.md` — why this builder's content also has to be *usable*, not just
  authorable.
- `content-extensibility-framework.md` — the three-layer model these forms sit in.
