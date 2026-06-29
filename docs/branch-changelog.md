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

### Documentation

- **Decision log + roadmap** (`docs/kb/`) — the canonical D-log (prototype-then-converge, one
  mechanism per job, terse-export-data, deprecation policy), dependency-ordered roadmap, the
  ability-increase-spreads spec, and a backfill ledger for migrating bespoke paths onto the new
  standard.
