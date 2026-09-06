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
node test/e2e/mockup-parity.js                            # shipped form vs the approved mockup
```

To photograph a *previous* shape, put the old files back, rebuild, shoot, restore:

```bash
for f in src/cljs/orcpub/dnd/e5/views.cljs src/cljc/orcpub/dnd/e5/builder_fields.cljc \
         src/clj/orcpub/styles/core.clj; do
  cp "$f" "/tmp/$(basename $f).head"; git show <commit>:"$f" > "$f"
done
lein garden once && lein fig:build
LABEL=before node test/e2e/form-shape-comparison.js
for f in ...; do cp "/tmp/$(basename $f).head" "$f"; done && lein garden once && lein fig:build
```

**`git stash` is the trap here, not the tool.** Stashing only reaches back as far as your
uncommitted work; once the change is committed, stashing gives you the *new* shape and the capture
silently succeeds while photographing the wrong thing. That happened on the first attempt at this
page. Name the commit explicitly, and check the witness (below) before believing any pair.

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

**Why that is not a capture bug.** Identical output is exactly what a broken before/after capture
also produces — if the old build never went live, both shots are of the new one. So the "before"
run photographs a **witness page** alongside the subject: the fighting-style builder, which differs
sharply between the two commits (7 controls then, 4 now). It is committed here as
`witness-old-build-fighting-style.jpg`, and it differs from the same page in the after-gallery:

```bash
cmp docs/kb/assets/builder-comparison/witness-old-build-fighting-style.jpg \
    target/e2e-shots/gallery-after/fighting-style-builder.jpg      # differs -> old build was live
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
| page height, authored | 2229px | **1493px** |

The fully-authored form has exactly the same 23 controls — grouping removes no fields — but it is
**736px shorter**, because inside a row a bonus gets a number's width and its tags sit inline
instead of each taking a page-wide row of its own — all seven weapon tags now fit on one line.

**The layout is a port of the approved mockup**, `assets/builder-form-mockup.html` — dashed pill
chips on the add-bar, an orange uppercase group header, a 92px centred bonus with its hint beside
it, and tags as `width:auto` selects under a muted sub-heading. The mockup is the design record and
`styles/core.clj` is its implementation.

**That claim is now checked, because asserting it by eye was wrong twice.** `test/e2e/mockup-parity.js`
renders the mockup and the real builder side by side, reads the computed style of each corresponding
element, and prints the differences. It is a **report, not a gate** — some divergence is correct, and
saying which is the design work. Two kinds are excluded by name:

- **mockup scaffolding** — `.panel`, `body`, the two-column layout. The mockup is a standalone page
  that had to draw its own idea of "the form area"; the real form area is the app page, and importing
  the panel would make this one builder unlike every other builder in the app.
- **app chrome** — input and select background/border come from the app's widget styles. The mockup
  approximated them; where they differ the app is right.

It found the thing that made the form look unfinished: **the group borders were solid white.**
`.b-1` sets the `border` shorthand with no colour, so it resets to `currentColor`, and it is declared
later in the stylesheet than any `border-color` a class of ours can set. The row owns its border now.

It also reports one difference that is the **mockup** being wrong, not the app: the bonus input reads
`font-weight: 400` there, because the mockup's own `input{font:inherit}` outranks its `.num{font-weight:700}`.
The app renders 700 — what the mockup meant. Do not "fix" the app to match a mockup bug.

**Short labels are why the tags fit.** The mockup writes *Armor*, not *Armor requirement*, and
*Ranged only*, not *Ranged weapons only* — under a header already reading ATTACK BONUS those words
are noise, and they are exactly what made each select page-wide. So a field may carry a
`:short-label`, and an option a `:short-title`, used **only** where it renders inside a group
(`:compact?`). The long form stays the default because these fragments are advertised as droppable
into any builder's flat `extra-fields`, where no header supplies the context. Both forms are pinned
by `short-forms-are-additions-not-replacements`; the `nil` ("Both") option deliberately has no
short form, since it must stay the explicit first option in every context.

**One thing in the mockup is deliberately not built: the `+ Reaction` chip.** A reaction is a
*trigger* ("when X happens, you may Y"), and triggers are sheet entries rather than computed
conditions — §4 of `builder-form-schemas.md`. The chip is missing because that vocabulary does not
exist yet, not because it was overlooked. When it lands it is one more entry in `:kinds`.

**The `set` highlight is the answer to "seven dropdowns all saying Both".** A tag carrying an
actual restriction is drawn in orange, so it reads at a glance against its unset neighbours — in
the shot above, *Ranged weapons only* against six *Both*s. The alternative considered was hiding
unset tags behind an "add a restriction" picker; it was **rejected** because it changes behaviour
to solve a visual problem, costs a click per restriction, and hides from the author the fact that
six other restrictions exist. Weight, not removal.

**Correction (same day).** The first version of this table read *2513px, 284px taller*, and the
paragraph here argued that the vertical cost was worth paying for unambiguous tags. That was
measuring a half-finished implementation: the grouping had shipped but the layout inside each group
had not, so every control was still a full-width stacked block. The number was real; the conclusion
drawn from it was wrong, and the fix was to finish the layout rather than to defend the cost. Left
in place because "the measurement said the change was worse and the answer was to do the change
properly" is the more useful lesson.

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
               :tag-header "Applies when"
               :fields ac-bonus-fields}
              {:kind :attack-bonus :title "Attack Bonus" :at [:props :attack-bonus]
               :hint "to attack rolls with matching weapons"
               :tag-header "Only with weapons that are"
               :fields attack-bonus-fields}
              {:kind :damage-bonus :title "Damage Bonus" :at [:props :damage-bonus]
               :hint "to damage rolls with matching weapons"
               :tag-header "Only with weapons that are"
               :fields damage-bonus-fields}]}]

The row renders its lead by PATH (`:at` + `:bonus`) at a number's width with the hint beside it,
then the remaining fields as inline tags under `:tag-header`. The field fragments themselves are
untouched — the same `ac-bonus-fields` the flat forms in other builders still use.
```

**It is an arrangement, not a second storage model.** A row is "present" when the item has data
under its `:at` path, so nothing extra is stored to mark a row, an item authored by the old flat
form renders here unchanged, and the `.orcbrew` is untouched (D9). The e2e pins exactly that: a
bonus typed into a row lands at `:props {:ac-bonus {:bonus 1}}`, the same path as before.

### What the comparison caught in my own work

**Twice.** First, it reported `AC Bonus x2` as an ambiguous label in the **new** form: the row header said
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

---

## Known design gaps (measured, not yet fixed)

`mockup-parity.js` only compares elements that HAVE a mockup counterpart. These have none, or sit
outside the effects UI, and are open:

- **Name and Option Source are unequal columns.** The mockup gives them `flex: 1 1 200px` each; the
  app's are noticeably different widths. It lives in `simple-content-builder`, so changing it changes
  **every** generated builder — including the language pair whose byte-identical property is a
  documented claim above. Worth doing, but it is a deliberate change to that pair, not a tweak.
- **The `:multi-enum` checkbox row is unstyled** relative to everything around it — the checkbox and
  its label run together and the row has no rhythm with the fields above.
- **Row bodies could use more breathing room**; the mockup's `.grp > .body` padding is 12px and the
  app's inner spacing is inherited from generic utility classes rather than set for this context.

None of these are guessed — each is visible in
`assets/builder-comparison/fighting-style-rows-authored.jpg` against the mockup's proposed column.
