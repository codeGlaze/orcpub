# Class features, the rolling layer, and the mechanization ceiling

How built-in class features are actually structured, what the app can/can't do mechanically
(including the dice roller), and the design direction for centralizing features. This is the
"tough nut" — read the flags carefully.

Flags used: **VERIFIED** = read from code, file:line cited. **USER-REPORTED** = stated by the
maintainer, not independently verified here. **SPECULATION / NOT-EXPLORED** = reasoning or an
un-investigated area; do not take as fact.

## How a class + its features are structured — VERIFIED (fighter, rogue read)
- A class is a function → `(opt5e/class-option … cfg)` with a **hand-written cfg map**. There are
  **10** such class option fns (`classes.cljc`, `defn …-option [spells spells-map …]`).
- Features live inline in `:modifiers` (class-wide) or `:levels {N {:modifiers/:selections}}`
  (level-gated). Level-gating is that plain `:levels` map keyed by level number, plus per-trait
  `:level`.
- **Features have no key.** Verified across fighter/rogue: `trait-cfg`/`action`/`bonus-action`/
  `dependent-trait` take `:name`/`:level`/`:frequency`/`:summary`, never `:key`. Some features are
  bare modifiers with no name at all — Extra Attack is `(mod5e/num-attacks 2)` at fighter level 5.
- **Class-level coupling is literal**: `(?class-level :fighter)` / `(?class-level :rogue)` are written
  directly into summaries, frequencies, and scaling tables (Second Wind, Action Surge, Sneak Attack,
  Indomitable).
- **Partial extraction already exists**: a few features are shared helper fns — `extra-attack-trait`
  (`classes.cljc:40`, used by barbarian/ranger), `uncanny-dodge-modifier` (`options.cljc:3049`, used
  by rogue + a subclass). These are shared *code* (compile-time fns), not keyed/data-addressable
  units, and parameterized only trivially (a page number), not by class-level.

## Two kinds of feature "mechanics" — VERIFIED
1. **Sheet-affecting modifiers** (compute a derived value): `num-attacks` (Extra Attack), saving
   throws, ability boosts, AC/speed fns, spellcasting, uncanny-dodge.
2. **Dependent-traits** (text + a level-computed summary + optional `:frequency`): Sneak Attack's
   "Nd6", Second Wind's heal, Action Surge, Rage's damage/resistance, Indomitable. The number is
   computed into the string; the effect itself is described, and (without the rolling/counter wiring)
   player-applied.

## The rolling layer — VERIFIED (corrects an earlier wrong claim)
An earlier note said "the app does not resolve rolls/combat." **Wrong** — that came from grepping only
the `orcpub.dnd.e5` namespaces and missing the dice layer. Verified:
- `orcpub.dice` (`src/cljc/orcpub/dice.cljc`): `die-roll`, `roll-n`, `dice-roll {:num :sides :drop-num
  :modifier}`, `dice-roll-text-2` (parses "1d20+5" and rolls).
- `roll-button`s across the sheet — attack rolls, skill checks, saves, ability checks — with
  **advantage/disadvantage** (`views.cljs` `button-roll-fn`, `roll-button`).
- Attacks compute through `?attack-modifier-fns` / `?damage-bonus-fns` (`modifiers.cljc:545-556`) —
  the same fns Dueling uses for a real conditional +2 damage — and these feed both the *displayed*
  attack and the roll. **This is the attachment point** for mechanizing a feature's dice/mods.

USER-REPORTED (not verified here): **many damage/etc. rolls are stuck as text and not exposed to the
roller**, and users have explicitly asked for this to be fixed. So the roller exists but is applied
inconsistently — wiring the stuck-as-text rolls into it is a known, requested improvement.
USER-REPORTED: the **PDF sheets don't roll** anything but auto-calculate some fields (not verified).

## Use/resource counters — VERIFIED
A general counter exists: `actions-indicators` (`views.cljs:2344`) renders a feature's uses as
checkboxes (small count) or a 0..N selector (large count), backed by `::entity/values
::char5e/features-used` (keyed by rest-period + name) and reset by `clear-period` on long/short rest
(`events.cljs:2556-2579`). Ki/sorcery points are *authored as text* today, not because the engine
can't count — wiring them as a frequency/amount feature would give them a counter.

## The mechanization ceiling (where "make it real, not text" stops)
- **In reach:** anything resolving to a derived sheet value (AC, speed, saves, resistances, ability
  mods, flat/conditional attack & damage bonuses), known spells, use/point counters, and — via the
  roller + bonus-fns — a feature's dice/mods flowing into an actual roll (Sneak Attack's Nd6, Rage's
  +2). The situational *condition* (advantage, enemy within 5 ft) stays player-chosen (you pick the
  roll/adv mode).
- **Out of reach (would be a different product):** combat *state* and *turn* resolution — targets,
  hit/miss outcome, applying damage to a creature, reactions firing, action economy ("extra action"),
  enforced "once per turn." That's a simulator, not a sheet+roller.

## The code-capture catch — VERIFIED (the thing that makes the registry non-trivial)
The optimistic registry plan assumes a feature is *data* you can store, filter, and override. It
isn't, today — a feature is **captured code**, and that changes what the registry's compile step has
to do.
- `dependent-trait`, `action`, `bonus-action`, `prop-trait` are **macros** (`modifiers.cljc:308`,
  `:583`, `:589`, `:299`). They expand the cfg map into a `mods/modifier` form. The cfg map itself is
  *embedded as a literal in that expansion* — it is not evaluated and handed to a function, it is
  spliced into code.
- The cfg fields contain **live entity-spec references and control flow**, not values. Examples from
  real fighter (`classes.cljc:1075-1101`):
  - Action Surge uses: `:frequency (units5e/rests (if (>= (?class-level :fighter) 17) 2 1))`
  - Second Wind heal: `:summary (str "regain 1d10 " (common/mod-str (?class-level :fighter)) " HPs")`
  - Indomitable uses: `:frequency (units5e/long-rests (mod5e/level-val (?class-level :fighter) {13 2 17 3 :default 1}))`
- `?class-level` is **not a variable** — it's an entity-spec attribute, defined as
  `?class-level (fn [class-kw] (get-in ?levels [class-kw :class-level]))` (`template_base.cljc:125`),
  resolved during `entity/build`. `level-val` (`modifiers.cljc:595`) is a **compile-time macro** that
  expands a threshold table into a `condp <=` form. So the scaling tables are baked into code at
  compile time, keyed off a build-time attribute.

**Consequence for the registry.** To make a feature data-addressable you cannot just lift the cfg map
out — its `:frequency`/`:summary` are code. A `compile-feature` step takes a DATA spec + the granting
class's level and produces the same cfg the macros consume. Two translations:
- Scaling → **data schedule + runtime lookup.** Replace the hand-written
  `(if (>= (?class-level …) 17) 2 1)` / `level-val` table with a `{level→n}` map resolved by a runtime
  `level-lookup` (the runtime analogue of the `level-val` macro). `level-lookup` reproduces `level-val`
  exactly: highest threshold ≤ level wins, else `:default` (verified against Indomitable's
  `{13 2 17 3 :default 1}` → 9→1, 13→2, 17→3). Arithmetic scaling (the rogue's
  `round-up(level/2)` Sneak-Attack dice) is expressed as the equivalent threshold table; a small
  formula form would compact it (not built).
- Summary → **fields + a fill template, not interpolation.** (Corrects an earlier note that called
  this a separate "templating sub-problem.") The fix isn't templating into a string; it's *storing the
  overridable numbers as fields* and making the summary a projection of them. The number that scales
  (Second Wind's heal bonus, Sneak Attack's die) is a field; `:text` is a template
  (`"regain {dice} {+bonus} HPs"`) where `{name}` prints a value and `{+name}` signs it (one rule, because
  a heal bonus reads "+5" but a dice count reads "3d6"). So "override Sneak Attack's die" is **not
  blocked** — it was only blocked while the die lived inside prose; promote it to a `:die` field and the
  summary regenerates.

**Data-shape findings forced by the real fighter/rogue cases (not aesthetics):**
- **No class named in the feature.** A feature must not carry `:class :fighter` — that re-hardcodes the
  coupling de-siloing removes. The feature takes a `level` at compile time; *whoever grants it supplies
  the level*, so a custom class can grant the same feature and feed its own level.
- **`:effect` is a map `{:kind … + params}`, not a `[kind params]` tuple.** Override is a deep-merge,
  and a tuple can't deep-merge its inner params; a map lets `{:overrides {:effect {:die "d8"}}}` change
  one field and nothing else.
- **Editable reference = deep-merge of `:overrides` onto the spec before compile.**

See the `compile-feature` proof in `test/cljc/orcpub/dnd/e5/class_feature_snapshot_test.clj`: a DATA
spec reproduces the **real built** fighter's Action Surge (use-count 1→2) and Second Wind (heal
+5→+17), and the rogue's Sneak Attack (3d6@5, 6d6@11, once/turn) — frequency **and** rendered summary;
a `:uses` override changes only the count; a `:die` override regenerates the summary (3d6→3d8) and
changes nothing else. All asserted equal to the live `entity/build` output, so it's pinned to real
behavior, not a hand-written expectation.

## Design direction for centralizing features — DESIGN (not built)
The maintainer's intuition: move core features out of class declarations, centralize, re-insert. The
question was "small per-feature pools, or one larger keyed/filterable registry?"

Recommendation: **one keyed, filterable feature registry**, not many small pools.
- A pool (existing `content_pools/pool`) is "choose one from a set of interchangeable options" — it
  fits *choices* (alternate features, a feature that grants a pick), not a single named feature.
  "Each feature its own pool" is a category mismatch.
- Features are named units you reference, place at a level, replace, and query (all rogue features;
  all features of kind X; features valid at this slot). That's a **registry: key → parameterized
  definition + filterable metadata** (class(es)/generic, level/scaling, kind, source).
- **Pools become filtered views over the registry** — the same built-in ++ homebrew pool sub, but
  sourced by a filter on the registry. So it's not registry *vs* pools; it's one registry, with pools
  derived from it. This matches how the app already builds pools.
- Registry entries must be **parameterized by class-key** so a feature scales by whatever class grants
  it (today `?class-level :fighter` is hardcoded).

**Distinct features vs scaling (VERIFIED on fighter/rogue) — this shrinks the scope.** A class's
`:levels` table is mostly *scaling/padding*, not distinct features: ASI levels
(`:ability-increase-levels`), Extra Attack increments (`num-attacks` 2/3/4), and feature dice growing
by level (`level-val`, e.g. Sneak Attack). Each class has only ~3–6 genuinely-distinct features
(fighter: Second Wind, Action Surge, Indomitable, Fighting Style). So:
- The registry holds the **handful of distinct features per class** (class-tagged), not whole level
  tables. "A class's standout features" = a **filter** on the registry (`:class :fighter`), not a
  per-class silo — per-class pools would over-fragment ~3–6 entries.
- Scaling/padding stays where it is — `:ability-increase-levels` + `level-val` + bare modifiers are
  fine declarative primitives and don't enter the feature registry.
- So the migration is ≈ extracting ~3–6 features × ~12 classes (+ subclasses), not rewriting every
  level table. Smaller and lower-risk than "re-architect every class."

**Two builder entry points over the one registry (not either/or):**
- **Template-from-a-base-class** (the default UX — most homebrew is "an official class, tweaked"):
  initialize the custom class with the base class's feature **references + scaling/profs**, then edit.
  Hard rule: copy **references (feature keys), not definitions** — deep-copying definitions
  reintroduces the two-versions/drift smell; copying references keeps everyone pointed at the one
  shared feature.
- **Filterable picker** (the editing tool, needed regardless): browse the registry by filter
  (class/kind/level) to add, replace, or build from scratch.
The template is a thin layer over the picker — build the registry + picker, and template-from-base
falls out cheaply. "Change/replace a feature" = the alternate-features capability (needs feature
keys + an addressable feature list; touches saved data only when a swap is chosen).

**Editable references (design the SHAPE now — retrofitting is "working backward"):** a reference
should be a **map with optional overrides** (`{:feature :second-wind :overrides {:uses 2}}`), and a
feature a **structured, parameterized record with defaults** (`{:key :second-wind :uses 1
:heal-die :d10 :scaling …}`); overrides merge onto defaults at compile. This is what lets a custom
class say "more Second Wind uses" / "Sneak Attack uses d8" / "extra Sneak Attack dice at these
levels." Crucial coupling: **you can only override a parameter the feature exposes** — Sneak Attack's
die currently lives in a summary *string*, so overriding it requires structuring the feature first.
So editable references and mechanizing features (structured fields, not prose) are the same work.
Design-now = the reference format (`:overrides` map) + structured feature records + a merge step;
add-later = which overrides the UI exposes and how deep (a param vs editing a scaling table). Keep
the data shape open to arbitrary overrides; throttle the UI. (Avoid the inverse trap: exposing every
knob immediately piles up UI + merge/validation edge cases.)

Sequence (to contain regression risk): build a characterization net (snapshot every class's built
features) → define the feature record + registry → extract incrementally, proving byte-identical
output per step → then make entries data-addressable and expose to the custom builder + alternates.
Backward-compat note: features are auto-granted (not stored choices), so extraction doesn't touch
saved characters as long as output is identical; only *choosable* alternates put feature keys into
saved data.

## NOT-EXPLORED / to verify before sizing
- ~~The full per-class feature catalogue~~ — DONE: all 10 base classes inventoried in
  `class-feature-catalogue.md`. It confirmed the ~3–6/class sizing (monk/paladin ~10 outliers) and
  surfaced the odd cases the `compile-feature` slice doesn't yet cover: multi-source use-counts
  (ability-derived, formula, level), class-wide resource pools (ki/sorcery/Lay-on-Hands),
  build-context summary interpolation (derived stats + user selections), multi-part features
  (compile → seq of modifiers), and `?attr` interdependence.
- Exactly how a *new conditional dice rider* (e.g. "+Nd6 when you have advantage") would attach to a
  roll button in the UI.
- Whether the combat tracker tracks uses/resources (separate from the sheet).
- The PDF auto-calc fields (USER-REPORTED; not checked).
