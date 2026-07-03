<!-- Branch changelog for claude/zen-wright-04xhdz. Undated by design: intended to be merged into
     the top-level CHANGELOG.md (matches its `## [branch]` / `### Category` / `- **Title** (hash)`
     format). Forked from `develop` at d42e05d1. -->

## [content-extensibility + ability-score-increase-spreads]

Make homebrew content extensible and cross-silo: turn "just text" into real mechanics through one
shared abstraction instead of per-silo bespoke code. Two themes — a declarative content-builder
framework (field-schemas + a pool/grant primitive) and a concrete cross-silo feature built on it
(ability-score-increase spreads) — plus homebrew-source surfacing and a foundation of
characterization tests.

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

- **Verified edge cases (deselect + multiple feats).** Deselecting a floating ASI pick (dropdown →
  "— choose —") cleanly reverts the total AND clears the source-column entry — rendered-verified, no
  stale state. Two feats each with a floating pool key their slots identically (`asi-0-*`) but, selected
  together under one Feats multiselect, do NOT collide — each pick applies independently and stacks
  (entity paths disambiguate); a static-ASI feat applies alongside; all feat ASIs land in "other", never
  racial (`ability_increase_grant_test/multiple-feats-*`).

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
