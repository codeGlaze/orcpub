<!-- Branch changelog for claude/zen-wright-04xhdz. Undated by design: intended to be merged into
     the top-level CHANGELOG.md (matches its `## [branch]` / `### Category` / `- **Title** (hash)`
     format). Forked from `develop` at d42e05d1. -->

## [content-extensibility + ability-score-increase-spreads]

<!-- Why this branch exists (reviewer context — drop at fold): make homebrew content extensible and
     cross-silo — turn "just text" into real mechanics through one shared abstraction instead of
     per-silo bespoke code. Two themes: a declarative content-builder framework (field-schemas + a
     pool/grant primitive) and a concrete feature built on it (ability-score-increase spreads), plus
     homebrew-source surfacing and a foundation of characterization tests. -->

### Highlights

Homebrew content can now carry real mechanics instead of being inert text: a content type declares
its fields once, and the builder form, save, and import checks all follow from that one declaration.
A homebrew feat can grant a fighting style, and 2024-style ability-score-increase spreads (+2 to one
ability, +1 to another) work across races, backgrounds, and subclasses the way official content does.

### Features

- **Declarative builder field-schemas** (`f32790b1`, `de0cf37f`, `da2f63d8`)
  A content type declares its fields once (`field_schemas.cljc`, `content_types.cljc`); the builder
  form, the save spec (optional-by-default), and import/export verification are all generated from
  that one declaration. Fixes the breath-weapon bug at the source rather than per-form. The Draconic
  Ancestry builder is the proof — its whole form is a field schema.

- **Pool + grant primitive** (`c1f54967`, `acaa131d`, `c67006e9`)
  A generic content-injection seam (`content_pools.cljc`, generalized from the per-bucket
  `:fighting-style` hook into a generic `:grant`). Opens a homebrew pool (e.g. draconic ancestry)
  with full mechanics, and lets one silo's content feed another (feat-granted fighting style proven
  on a built character).

- **Ability Score Increase spreads** (`b99a7b94`, `b89aa006`, `f00394aa`, `287219fe`)
  A content entry grants ASIs as one terse spread — a list of `[amount pool]` pairs, e.g.
  `[[2 :cha] [1 :martial]]` (+2 CHA fixed, +1 to a player-chosen martial stat). Supports fixed,
  floating, named groups (any/martial/mental), explicit choice-sets, and the 2024 "+2/+1 to different
  abilities" rule. Works in **races, subraces, backgrounds** (2024 ASI-via-origin), and **subclasses**
  (non-standard, behind an opt-in toggle). One shared authoring widget across every silo (no
  duplication); fixed increases compile to racial-ability modifiers, floating ones to a choice slot
  the character builder renders. Each silo's ASI stays contained to its own source and stacks
  correctly when several grant one (proven via entity-path containment). Terse on the wire (ships in
  every homebrew pack); self-documenting in source.

- **Save-proficiency grants (rider + standalone tool)**
  Two orthogonal ways to grant saving-throw proficiencies, both compiling to the one save primitive
  (`modifiers/saving-throws`): a per-increment `:save` rider on the ASI spread
  (`[[1 :martial :save]]` → +1 to a chosen martial stat *and* its save — the Resilient pattern,
  opt-in so the default stays bump-only), and a standalone `:save-proficiencies [[count pool]]` field
  for saves on a different stat than the bump, or with no bump at all (fixed, or "choose N distinct
  saves from a pool"). Wired into every silo through a single merged hook (`compile-ability-grants`):
  races, subraces, backgrounds, subclasses, and feats. Same-stat overlap collapses to one proficiency
  (set semantics — no double bonus); the builder runs `save-coverage-warnings` over the entry being
  authored and shows an inline warn-and-explain note for redundant/overlapping save coverage.

- **Feats consume ASI spreads (dual-format reader)**
  `feat-option-from-cfg` reads `:ability-increases` by shape: a vector is the new cross-silo spread
  (routed through `compile-ability-increases`, like the other silos), a set is the legacy feat format
  (`#{:str :con}` + the optional `:saves?` save-proficiency marker). The legacy path is left untouched —
  released feat data keeps working verbatim, including saves — and homebrew feats can now grant
  fixed/floating/grouped spreads. (The spread now models saves via the `:save` rider above, so the only
  remaining step is migrating the *released* feat `:saves?` set onto it — a deliberate data migration
  tracked in the backfill ledger, not a missing capability.)

- **Draconic Ancestry homebrew builder** (`0aca6113`)
  End-to-end builder for a homebrew draconic ancestry, built entirely from a field schema.

- **Strict-mode import** (`e4614519`)
  Optional toggle for creators/devs: report missing required fields instead of silently auto-filling
  them.

- **Homebrew source on class names** (`8f94a94c`)
  Opt-in `show-class-source-suffix` preference surfaces which homebrew pack a class came from; plugin
  source is carried as its own slot through the option config rather than folded into the name.

### Bug Fixes

- **Homebrew floating ASI choice now renders** (`8e331564`)
  The builder only rendered ability selections keyed `:asi`; a homebrew floating ASI used a different
  key, so the choice never appeared. Now keyed correctly and rendered.

- **Builder dropdown string coercion** (`050dbb95`, `64db2448`)
  `<select>` returns strings; authored ASI values were stored as raw `"cha"`/`"martial"`/`"1"` instead
  of typed keywords/ints. Fixed via `:typed?` dropdowns (index round-trip), with the footgun
  documented.

- **Floating-pool restriction honored** (`fac6ca92`)
  The assign-from-bag widget offered all six abilities regardless of the creator's pool; now restricted
  to the declared pool.

- **`make-summary` null crash on save** (`42ceaaa8`)
  Gated behind the ability check so a character without abilities can't crash the save.

- **Import dedup + non-ASCII count** (`86eb5cc4`)
  Dedup homebrew-selection options on import; fix a cljs non-ASCII counting bug.

- **Spell-selection key reconciliation** (`fe549631`)
  Derive spell-selection keys from the class key, not the display name, and reconcile orphaned keys on
  a character at load — so renamed/sourced classes don't drop spell choices.

### Internal / Testing

- **Characterization nets** — class features across all 12 classes, spell-slot progression, AC
  computation, `compile-feature`, structured effects, and a `.orcbrew` round-trip coverage map. Pin
  baseline behavior before refactoring.

- **ASI vertical, every layer** — JVM compile/apply, cljs sub-wiring (races/backgrounds/subclasses),
  and rendered-UI E2E: authoring, pool restriction, distinctness, multi-silo containment, and a full
  authored → export → cleared-browser → import → use round-trip (`test/e2e/*asi*`).

- **`builder-notes` consolidation** — rule-of-three: three builder surfaces each rendered an
  item-problem list differently (`simple-content-builder`/`validate-fields`, `selection-builder`'s
  name checks, save-coverage). Unified the *render* into one `builder-notes [problems {:severity}]`
  component (producers stay separate; per-row highlighting stays bespoke). Documented in
  `content-extensibility-direction.md` for discovery.

- **Malformed-entry surfacing (harden → surface, guardrail 6).** The compilers silently skip malformed
  `:ability-increases`/`:save-proficiencies` entries for fan-out crash-safety; the authoring form now
  *surfaces* it — `opt5e/ignored-entry-warnings` + `ability-save-notes` show "N entries are malformed
  and will be IGNORED" so a creator editing imported/hand-edited content sees the drop. The builder
  survives the junk. `ability_increase_grant_test/ignored-entry-warnings-*` + `test/e2e/ignored-entry-note.js`.

- **Verified edge cases (deselect + multiple feats), rendered.** Deselecting a floating ASI pick
  (dropdown → "— choose —") cleanly reverts the total AND clears the source-column entry — no stale
  state. Multiple feats with floating pools: unlocked via the live per-section **Homebrew toggle** (the
  beer-stein mug — `character_builder.cljs:644`), two floating-ASI feats render as separate breadcrumbed
  widgets and their picks do NOT collide even though both key their slots `asi-0-*` (each feat's own
  entity path disambiguates — STR stacks +2+1=+3); a static-ASI feat applies immediately (+2 CON); all
  feat ASIs land in "other", never racial. E2E `test/e2e/multi-feat-floating.js` + JVM
  `ability_increase_grant_test/multiple-feats-*`.

- **Bug fix: floating ASI picks were attributed to nothing (orphaned in the level-up bucket).** A
  chosen floating +N applied via `level-ability-increase` → `?level-ability-increases`, which the
  per-source ability breakdown doesn't show — so a picked floating ASI updated the total but appeared
  in no column (and a subrace with a floating ASI showed no "subrace" column at all). Root cause: the
  compiler decided attribution in two places and only the fixed branch was silo-aware. Single-sourced
  it — floating slot options now apply the same `fixed-modifier` as fixed increments, so a pick lands
  in its silo's column. Total unchanged; option keys unchanged (no save-compat impact). Proven by
  `test/e2e/multi-source-floating-attribution.js` (three concurrent floating sources — race/subrace/
  background — render separately and attribute to their own columns) + a JVM floating-attribution test.

- **Bug fix: per-silo ASI attribution (fixed increments were mis-shown as racial).** The spread
  compiler used `race-ability` for every fixed increment, so a background/subclass/feat fixed +N landed
  in the ability breakdown's "race" column (and cancelled out of "other"). `compile-ability-grants` now
  takes `:attribution` (`:race` default / `:subrace` / `:general`); non-racial silos pass `:general` (a
  neutral `mod5e/ability`, shown under "other"). Total unaffected; only the source column was wrong.
  Floating increments were never affected (they use `level-ability-increase`). Caught during the feat-
  migration review; behavioral test builds a character and checks the race bucket per silo.

- **Fan-out crash-safety for messy paks.** A malformed spread/save entry with a nil pool (`[:bad]`,
  `[]`) reached `resolve-pool` and NPE'd on `(name nil)` — in the races sub (mapped over every race)
  that crashed the whole pack. Tightened the compilers' guard from `filter vector?` to `pool-entry?`
  (numeric amount + keyword/collection pool), so one junk entry is skipped, not fatal. Surfaced by a
  new `messy-pak-survives.js` E2E (guardrail: prove against realistically-messy content in the real
  app) + a JVM messy-tolerance test.

- **Toggle nil-safety — deferred to the shared helper (no parallel mechanism).** The generated-UI
  `:boolean` field is intentionally NOT built here: toggle nil-safety is owned by `common/toggle-in` /
  `common/toggle-flag` (+ `strip-export-blanks`) on `claude/custom-class-source-error-2k5ykd`, which
  fixes the real root cause (a toggle path landing on a map collapsed the collection). A note in
  `builder_fields.cljc` / `render-builder-field` records that the `:boolean` type is added later
  routing through that helper.

### Documentation

- **Decision log + roadmap** (`docs/kb/`) — the canonical D-log (prototype-then-converge, one
  mechanism per job, terse-export-data, deprecation policy), dependency-ordered roadmap, the
  ability-increase-spreads spec, and a backfill ledger for migrating bespoke paths onto the new
  standard.

### Integration

- **Merged develop (homebrew-data-preservation) and the Summer Patch.** Two upstream integrations:
  `origin/develop` (PR #29, homebrew-data-preservation) and then `origin/contrib/summer-fixes` (the
  Summer Patch — character-load self-heal + diagnostic report/email endpoint, single-colon keyword
  crash guard, per-entry homebrew salvage + quarantine repair UI, source-less content → the built-in
  Default Option Source, dangling spell-ref surfacing, printer-friendly B&W PDF spell cards,
  `http_safe.cljs`). One import-path conflict (`events.cljs`): kept our `::e5/set-strict-import?`
  toggle and their per-entry salvage helpers, and routed the success branch through their
  `store-imported-sources` gate — a strict superset of our per-source keyword-trap quarantine
  (`valid-item-for-load?` rejects letter-trap keys per item), with our strict-mode `:strict-unfilled`
  errors preserved in the import log. Fixed 3 tests that were red on summer-fixes itself (commit
  `54f4e87d` changed the fill default `"Unnamed Content"` → `"Default Option Source"` but left the
  assertions stale). Post-merge: JVM 361/2037, cljs 237/684, 0 failures; import round-trip E2E green.
# Branch changelog — `consolidate/notifications-and-exports`

## Why this branch exists

Six branches were cut over one day for what is three pieces of work, and one of
them — the hiccup-rendering fix — existed only on a branch that never went back
to integration, so the break it fixed stayed live for nine hours. This branch is
the single place that work lives now, plus the developer-mode toggle and the
export coverage added after the merge.

## Highlights

Homebrew can now be rescued from a broken app. The boot shell carries a download
control that the app removes once it has actually rendered, so a missing bundle,
a failed init or a component that throws all leave it standing — and the error
screen puts it back. It reads storage at the moment you click it, not at page
load, so work done during the session is in the file.

## Added

- **Homebrew rescue in the boot shell** — downloads what is stored when the app
  itself cannot. Present by default and removed on a clean render, so nothing has
  to detect the failure (`46b6f807`, `7bd0d7bc`).
- **Developer mode** — a named switch in the footer reveals "Dump library" and
  "Debug info", off by default and remembered per device. Replaces two unlabelled
  icons that sat on every page (`c1dad978`).
- **Export coverage for the save banner's link** — a source holding one item from
  before the session and one saved seconds ago must export with both, and the file
  must re-import into a clean store (`c1dad978`, `cb40740e`).

## Fixed

- **Banners render markup again** — every message built from hiccup had been
  rendering as its own source text since `e13d2b2f`, which removed every control
  inside one, including the save banner's export link (`68c9873e`).
- **Per-source export runs the same checks as Export All** — the same source no
  longer produces two different files depending on which button you press
  (`f7b259fe`).
- **The size a rescue reports** — anything under a kilobyte read as "0 KB", which
  looks like there is nothing to save (`7ccc9d3a`).

## Changed

- **The footer's raw library dump is named and explained** — it stays reachable in
  production, because it is the way out when validation is what is broken
  (`17658bb5`, `c1dad978`).

# Branch changelog — `test/overlay-probe-user-menu-and-modals`

## Why this branch exists

Kept open while the import UI's wording is still moving. The helper every homebrew
probe depends on clicks the import modal's primary button by label, and that label
has changed three times: "Import", then "Import with these fixes" (`f7285198`),
now "Import with default fixes" (`2661be88`).

## Added

- The overlay probe walks **My Content's delete-all guard** — the quiet `Delete…`
  button, the `.mc-liftpop` it unfurls, and the `.mc-confirmbar` underneath. It
  cancels at the last step and then asserts the stored library is the same size it
  was, so a run can never be one stray click from wiping a library. None of the
  three steps is a modal, which is why the earlier version of this probe waited on
  a selector belonging to the item builder's confirmation and skipped.

## Fixed

- **The import helper follows the button's latest wording**, with the bare-word
  entries kept last so the next rewording of the same control still lands.

# Branch changelog — `fix/item-builder-save-label`

## Why this branch exists

Every builder's save button says "Save to Browser Storage", and for every builder
but one that is true. A magic item is saved to the database, like a character —
`::mi/save-item` posts to `/dnd/5e/items` with an auth header — so on that page the
label promised local storage while requiring an account, and a logged-out click
went to the login page having never said an account was needed.

## Fixed

- **The item builder's save button says "Save Item"** rather than claiming browser
  storage it does not use.

# Branch changelog — `refactor/banner-parts-and-design`

## Why this branch exists

The import banner's parts were joined with `\n` and rendered as HTML, which
collapses them — so three sentences arrived with no punctuation between them. The
previous fix made CSS honour the newlines, which treated the symptom: display text
should be structured, not a string with sentinels in it.

The banner also had no hierarchy — three bold lines of equal weight, an emoji
standing in for an icon, a saturated warning-orange slab for a *success*, a close
glyph with no touch target, and its most useful suggestion written as advice
rather than offered as an action.

## Changed

- **Import messages are structured** — `format-import-result` returns
  `{:title :details}`, one idea per line, and the banner renders each line itself.
  No newline sentinels, no `white-space: pre-line`, no regex collapsing blank
  lines. A plain string is still accepted and split at the edge, so the remaining
  legacy callers keep working while they move over.
- **The banner has a hierarchy** — a severity icon carrying the colour, a headline,
  supporting detail at a lighter weight, a tinted box instead of a filled slab,
  and a dismiss with a real touch target.
- **A successful import reads as success** — green, not warning-orange.
- **The banner says what happened and stops there.** The advice line — "To be
  safe, export all content now" — is gone rather than promoted to a button: on My
  Content it would have sat directly above the page's own Export All. A message
  pointing at a control already on screen is noise.

- **The save-validation error leads with the problem.** It opened with a line
  naming the builder you were already standing in ("Spell:"), then the problem,
  then the escape hatch — three stacked blocks for one short message. The problem
  is the headline now, with the rest beneath it.
- **Exporting from the storage warning no longer closes the card.** The banner
  dismisses on any click that reaches it, and the export link did not stop its
  own, so the surface vanished mid-action — which is how a working control comes
  to feel broken.
- **The browser-storage warning leads with the point.** It was one
  200-character sentence opening with "IMPORTANT!:" and ending with the export
  link — the part that matters, last. Now: "Spell saved — in this browser only",
  and under it "Clearing browser data loses it. Export this source to keep a copy."
- **The banner is sized to what it says** rather than to one fixed width. A flat
  cap fixed the five-word slab and then squeezed a two-sentence warning into a
  narrow column; `fit-content` with a ceiling does both jobs.
- **A save confirmation says what it saved** — "Saved “Flame Tongue”" rather than
  "Your item has been saved.", for characters too when they have a name.

## Fixed

- **A message that is markup renders as markup.** The builders' "please fill in
  X" carries a bolded field name and a clickable "Save anyway with placeholders",
  and the new banner ran `str` over it, printing the hiccup at the reader. Vectors
  now pass through untouched.

- **Callout action buttons carry a React key again** — the key was attached to the
  `let` form rather than the element it returns, so every callout with actions
  logged a missing-key warning.

# Branch changelog — `fix/my-content-source-toolbar`

## Why this branch exists

Inside an expanded source, the search box sat on a line of its own beneath the
Export and Delete buttons, running edge to edge against the panel wall. It reads
as an afterthought rather than part of that source's controls.

## Changed

- **An expanded source's search sits inline with its buttons** — search, show
  disabled, then Export and Delete on one row, with side padding so nothing is
  flush against the panel. On a phone the search takes the row and the toggle and
  buttons wrap beneath it.
