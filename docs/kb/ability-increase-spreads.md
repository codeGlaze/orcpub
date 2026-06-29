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

## How it compiles (`opt5e/compile-ability-increases`, `options.cljc`)

Input: the spread. Output: `{:modifiers [...] :selections [...]}`, merged onto the content by the
race/subrace/**background**/**subclass** assembly (`spell_subs.cljs` `plugin-races`/`plugin-subraces`/
`plugin-backgrounds`/`plugin-subclasses` — the same one-line hook each). Backgrounds are the 2024-PHB
"ASI via origin"; subclass ASI is non-standard for 5e, so it's authored behind an opt-in toggle (see
Authoring). (Classes already grant ASIs via their own `:ability-increase-levels` mechanism — left as-is.)

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
(floating). It emits the terse pairs. Explicit sets (`#{:wis :con}`) are valid data but currently
authored via orcbrew EDN, not the form. For non-standard cases (subclass ASI) the widget sits behind
`optional-builder-section` — a reusable toggle that's collapsed by default (keeps the form uncluttered)
but opens when content exists; data only persists if you fill it in. *(Pending: a "choose between spreads" option — offering the player a
choice among several spreads — and explicit-set authoring in the form.)*

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
  `{:ability}`/`{:select}` shapes only ever existed on this branch pre-convergence). `compile` just
  skips non-`[amount pool]` entries (`filter vector?`) so that one malformed homebrew race can't throw
  and break the whole race list — that fan-out crash-safety is the only defensiveness needed.
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
  fixed-only, fixed+floating, multi-floating, per-increment pools, save/load survival.
- cljs harness `test/cljs/.../ability_increase_grant_cljs_test.cljs` — the spread flows through the
  real `::races5e/races`/`::bg5e/backgrounds`/`::classes5e/plugin-subclasses` subs; orcbrew
  export→import preserves it verbatim.
- E2E `test/e2e/exact-spread-asi.js` — rendered builder: slots render, pool restriction + distinctness
  enforced, amounts applied on screen. `race-builder-asi.js` / `export-import-use.js` — authoring +
  round-trip through the real UI. `background-asi.js` / `subclass-asi-toggle.js` — the other silos +
  the opt-in toggle. `multi-container-asi.js` — two silos' ASIs stay contained and stack (above).
  `multi-container-roundtrip.js` — unbroken chain: a race AND a background are **authored through
  their real builder forms** (driving the `<select>` coercion) into one pack, then survive **export →
  cleared browser (`localStorage.clear()`) → re-import**, both spreads intact, then both render,
  attribute to their own container, and stack. No seeded localStorage — the round-tripped data is what
  the front-end forms actually produced.
