# Ability-increase spreads (`:ability-increases`)

How a race/subrace/feat grants ability score increases — fixed, floating, or any mix — as compact
data, including the standard Tasha's/MotM "+2 to one, +1 to another" pattern and arbitrary custom
spreads. Roadmap item A4.

## The format

A content entry's `:ability-increases` is **one spread**: a terse list of `[amount pool]` pairs.

```clojure
:ability-increases [[2 :cha] [1 :martial]]   ; +2 CHA (fixed), +1 to any martial stat (player's choice)
```

A **pool** is one of:

| Pool | Meaning |
|------|---------|
| `:any` | any of the six abilities |
| `:martial` | str / dex / con (named group) |
| `:mental` | int / wis / cha (named group) |
| `#{:wis :con}` | an explicit choice-set |
| `:con` | a single ability → a **fixed** increase (a one-element pool) |

Short ability keywords (`:str`, `:con`, …) are namespaced at compile time, so authors never write
`:orcpub.dnd.e5.character/con` by hand and the export stays small. There is **no** separate "fixed"
vs "floating" concept in the data — a fixed increase is just an increment whose pool is one stat.

The whole list is the unit of the **"different abilities" rule**: every increment lands on a distinct
ability. So `[[2 :dex] [1 :any]]` will not let the player put the +1 back on DEX.

### Examples

| Intent | Data |
|---|---|
| +3 CON, +1 CHA (both fixed) | `[[3 :con] [1 :cha]]` |
| +3, +2, +1 to any | `[[3 :any] [2 :any] [1 :any]]` |
| +2 DEX, +1 to any (other) | `[[2 :dex] [1 :any]]` |
| +2 WIS, +2 martial | `[[2 :wis] [2 :martial]]` |
| +1 mental, +2 martial | `[[1 :mental] [2 :martial]]` |
| +2 (wis\|con), +1 (str\|cha) | `[[2 #{:wis :con}] [1 #{:str :cha}]]` |

## Save proficiencies

Two orthogonal ways to grant a saving-throw proficiency, both compiling to the one save primitive
(`modifiers/saving-throws`) — never a parallel engine:

**1. The `:save` rider (coupled to a bump).** An ASI increment may carry a trailing `:save`, meaning
"also grant proficiency in the save for *this increment's* ability". For a fixed increment that's an
unconditional save on that stat; for a floating one the save rides whichever ability the player picks.
This is the Resilient pattern.

```clojure
:ability-increases [[1 :con :save]]            ; +1 CON and a CON save (fixed)
:ability-increases [[2 :cha] [1 :martial :save]]  ; +2 CHA; +1 to a chosen martial stat AND its save
```

The rider is **opt-in** — an increment with no `:save` is bump-only (the default). It can't express a
save on a *different* stat than the bump, or a save with no bump — that's what the standalone tool is for.

**2. `:save-proficiencies` (the standalone tool).** A terse `[[count pool]]` list — same shape as the
spread, but the number is **how many saves**, not a bonus. A single-stat pool is a fixed save; a
multi-stat pool is "choose `count` distinct saves from the pool". Completely independent of any ASI.

| Intent | Data |
|---|---|
| a CON save (fixed) | `[[1 :con]]` |
| choose 1 mental save | `[[1 :mental]]` |
| choose 2 saves, any | `[[2 :any]]` |
| a CON save + choose 1 mental save | `[[1 :con] [1 :mental]]` |

Both are compiled by `opt5e/compile-save-proficiencies` / the rider branch of
`compile-ability-increases`, and merged onto content by `compile-ability-grants` (the single silo hook
— see below). Cross-entry duplicate saves collapse harmlessly (`?saving-throws` is a set).

**Authoring guidance (warn-and-explain).** Because duplicates collapse silently, the builder calls
`opt5e/save-coverage-warnings` (pure, over the entry's own data) and shows a note when a creator
authors redundant/overlapping save coverage: the same stat granted a fixed save twice ("the duplicate
has no effect"), a fixed save also reachable from a choice pool, or two choice pools that overlap ("a
player could pick the same save in both and waste one"). Guidance only — it changes no mechanics.

## How it compiles (`opt5e/compile-ability-increases`, `options.cljc`)

Input: the spread. Output: `{:modifiers [...] :selections [...]}`. The race/subrace/**background**/
**subclass** assemblies (`spell_subs.cljs` `plugin-races`/`plugin-subraces`/`plugin-backgrounds`/
`plugin-subclasses`) call **`compile-ability-grants`** — the single hook that merges this spread *and*
the standalone `:save-proficiencies` — one line each. Backgrounds are the 2024-PHB "ASI via origin";
subclass ASI is non-standard for 5e, so it's authored behind an opt-in toggle (see Authoring).
(Classes already grant ASIs via their own `:ability-increase-levels` mechanism — left as-is.)

**Feats** also consume the spread, but via a *dual-format reader* in `feat-option-from-cfg`
(`options.cljc`) rather than the one-line hook — because feats have a released, richer ASI format the
spread can't fully replace (see Backward compatibility). The feat reader dispatches on shape: a
`vector` is a spread (→ `compile-ability-increases`, same as the other silos); a `set` is the legacy
feat format. So a homebrew feat can now grant a fixed/floating/grouped spread, while existing feats are
untouched.

- **Single-stat (fixed)** increments → `mod5e/race-ability` modifiers (applied unconditionally; they
  show in the ability grid's "race" column like any racial ASI).
- **Multi-stat (floating)** increments → slots in **one** selection keyed `:asi` (so the character
  builder renders it). Each slot's options are the pool's abilities, keyed `asi-<idx>-<ability>`, each
  carrying its own `level-ability-increase`. So storage/compute reuse the existing per-option
  mechanism — nothing new in the engine.
- The full spread (fixed + floating) rides on the selection's `::t/spread` so the widget can render
  the fixed labels, give each slot its own pool, and enforce one-ability-per-spread.
- Additive: `nil`/empty → `{}` (a content entry without `:ability-increases` is unchanged).

## How it renders (`ability-bag-assigner`, `character_builder.cljs`)

The character builder's ability editor branches on `::t/spread`: such a selection is rendered by
`ability-bag-assigner` (others keep the existing +/- increment widget, so built-in races and class
ASIs are untouched). Per increment:

- **fixed** → a label ("Strength +2"); its stat is excluded from the other pickers.
- **floating** → a `:typed?` dropdown over that increment's pool, minus abilities used by any other
  increment (fixed or floating). Distinctness and exact amounts fall out of this for free.

Picks persist via `:increase`/`:decrease-ability-value` with the slot's `asi-<idx>-<ability>` key
(`events.cljs`), so they round-trip through save/load like any selection.

## Authoring

`ability-increase-choices` (`views.cljs`) is the silo-generic authoring widget: it takes the content
map and a setter, so the race/background/subclass builders share ONE form (no duplication). A spread
is rows of Amount + To, where To offers the six stats (fixed) and the groups Any/Martial/Mental
(floating). Each row also has a **"+ save prof"** checkbox — the opt-in `:save` rider. It emits the
terse pairs (with a trailing `:save` when ticked). Explicit sets (`#{:wis :con}`) are valid data but
currently authored via orcbrew EDN, not the form. For non-standard cases (subclass ASI) the widget
sits behind `optional-builder-section` — a reusable toggle that's collapsed by default (keeps the form
uncluttered) but opens when content exists; data only persists if you fill it in.

`save-proficiency-choices` (`views.cljs`) is the companion silo-generic widget for the standalone
`:save-proficiencies` field — rows of "How many" + "From" emitting the terse `[count pool]` entries. It
sits alongside `ability-increase-choices` in the race/background builders, and inside the same
non-standard toggle for subclasses. Below both, `save-coverage-notes` renders the
`save-coverage-warnings` guidance (above) so redundant/overlapping save authoring is flagged-and-
explained as you build. *(Pending: a "choose between spreads" option and explicit-set authoring.)*

## Backward compatibility (D9)

Verified against the merge-base (released data):

- **Races/subraces never had `:ability-increases`** — it's branch-new, so no released orcbrew pack
  uses it. `compile-ability-increases` is additive (nil/empty → `{}`), so existing races (built-in
  `:abilities` map, or homebrew without the field) are unchanged.
- **`:ability-increases` IS a released FEAT field** — a *set* like `#{:str :con}` (+1 to one of these,
  with an optional `:saves?` member granting a save proficiency). The feat assembly
  (`feat-option-from-cfg`) now reads it by SHAPE: a **set** stays on the legacy path (unchanged — incl.
  `:saves?`, which the spread can't model); a **vector** is a spread routed through
  `compile-ability-increases`. So released feat data is never reinterpreted as a spread (the set
  branch is byte-for-byte the old behavior), and new feats gain the spread's richness. No silent
  migration — the reader supports both formats side by side.
- **No migration shim** — there's nothing released to migrate (races never had the field, and the
  `{:ability}`/`{:select}` shapes only ever existed on this branch pre-convergence). `compile` skips
  junk entries (`pool-entry?`: a vector with a numeric amount and a keyword/collection pool) so that
  one malformed homebrew entry can't throw and break the whole race/background list at the sub's
  fan-out. (This tightened from a bare `filter vector?` after a messy-pak E2E surfaced that a nil pool
  — e.g. `[:bad]` / `[]` — reached `resolve-pool` and NPE'd on `(name nil)`.) That fan-out crash-safety
  is the only defensiveness needed; a creator sees bad input in the authoring form, not here.
- **Saved characters are unaffected** — built-in racial ASI (Half-Elf etc.), class ASI, and feat
  ASI use the unchanged increment widget / mechanisms; the new `ability-bag-assigner` only renders
  selections carrying `::t/spread`, which only this compile produces.
- **In-branch churn** (the `{:ability}`/`{:select}` → `[amount pool]` format change, and option-key
  changes) only affects data created on this unreleased branch — fine per the prototype-then-converge
  rule (D23).
- **Feat-path reconciliation — DONE** (was a future hazard): feats now reach this compile via the
  dual-format reader above (set = legacy, vector = spread), not by passing the set in raw. The one
  thing still legacy-only is `:saves?` — the spread has no save model, so feat ASI authoring keeps its
  existing set-based widget (with the saves toggle); spread support reaches feats via data/import.
  Proven in `ability_increase_grant_test` (feat-legacy-* + feat-new-spread-* deftests).

## Containment across silos (multi-source)

If a race **and** a background (and, with the toggle, a subclass — or a feat) each grant an ASI, each
stays bound to its own source. Containment is by the **entity path**, not the form: the template nests
each silo's `:asi` selection under its container's option, `entity/build` stamps `::entity/path` on it,
and the shared `ability-bag-assigner` reads/writes at *that* path. So programmatic form-generation is
orthogonal to where a pick lands — the widget always exports to the path of the selection it was handed.
The source breadcrumb (`views-aux/ancestor-names-string`) is what the UI shows as "Race - Tide" vs
"Background - Sea-Marked". Distinctness is enforced *within* a spread, not across sources: two different
containers may each put their +1 on STR, and the two stack (STR 15 → 17). Proven in
`test/e2e/multi-container-asi.js`.

## Tests

- JVM `test/cljc/.../ability_increase_grant_test.clj` — compile + apply on a built character:
  fixed-only, fixed+floating, multi-floating, per-increment pools, save/load survival; the feat
  dual-format reader; and the save tools (rider fixed/floating/opt-in, standalone fixed/floating/count,
  rider+standalone composed) read off the `?saving-throws` set.
- cljs harness `test/cljs/.../ability_increase_grant_cljs_test.cljs` — the spread flows through the
  real `::races5e/races`/`::bg5e/backgrounds`/`::classes5e/plugin-subclasses` subs; the standalone
  `:save-proficiencies` and the `:save` rider wire through the merged `compile-ability-grants` hook;
  orcbrew export→import preserves it verbatim.
- E2E `test/e2e/exact-spread-asi.js` — rendered builder: slots render, pool restriction + distinctness
  enforced, amounts applied on screen. `race-builder-asi.js` / `export-import-use.js` — authoring +
  round-trip through the real UI. `background-asi.js` / `subclass-asi-toggle.js` — the other silos +
  the opt-in toggle. `multi-container-asi.js` — two silos' ASIs stay contained and stack (above).
  `save-grants-authoring.js` — the "+ save prof" toggle and the standalone save widget emit the terse
  data through the real selects/checkbox, and authoring an overlap surfaces the warn-and-explain note.
  `save-grants-use.js` — in the rendered builder, the rider's save rides the chosen bump (DEX save flips
  on the pick) and the standalone choice (on the Proficiencies tab) flips the WIS save. The
  `save-coverage-warnings` helper itself is unit-tested in `ability_increase_grant_test`.
  `messy-pak-survives.js` — a homebrew pack with a deliberately-malformed race (junk ASI/save entries)
  loads and the good race still works, proving one bad entry doesn't crash the pack (guardrail: prove
  in the real app against realistically-messy content, not happy-path).
  `multi-container-roundtrip.js` — unbroken chain: a race AND a background are **authored through
  their real builder forms** (driving the `<select>` coercion) into one pack, then survive **export →
  cleared browser (`localStorage.clear()`) → re-import**, both spreads intact, then both render,
  attribute to their own container, and stack. No seeded localStorage — the round-tripped data is what
  the front-end forms actually produced.
