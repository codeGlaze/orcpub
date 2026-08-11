# Content tiers, duplicate-key resolution, example content, and library management

Design direction for four interlocking pieces that fell out of the "ship an example/demo homebrew
pack" idea. Sources are cited by **symbol/namespace**, not line number (line numbers churn; the
verified behavior map is `key-collision-behavior.md`).

Markers: **VERIFIED** = traced from code. **DESIGN** = proposed, not yet built. **OPEN** = undecided.
**PREREQ** = must be confirmed/true before building.

---

## 0. The one idea that ties it together

Almost every problem below dissolves under a single invariant:

> **At most one *enabled* item per key** (across the merged, cross-source view).

Not "keys must be unique" (same-key override is a real, used feature) — but "no two *enabled* copies
of the same key at once." Enforce it by **disabling one side of a collision**, never by relying on the
implicit last-wins merge. Everything else is a consequence.

---

## 1. Duplicate-key behavior today (VERIFIED — summary; full map in `key-collision-behavior.md`)

- **Within one source:** duplicate keys are structurally impossible (inner level is a map keyed by
  item-key; EDN/`assoc-in` collapses to last-literal-wins). Collisions are always **cross-source**.
- **Import merge** (`orcpub.dnd.e5/merge-all-plugins`) is shallow last-arg-wins and only merges the
  *same* source name; different sources coexist. It does **not** resolve cross-source key duplicates.
- **Read-time flatten** (`::e5/plugin-vals` over `(vals plugins)`) decides winners:
  - **Keyed-collapse types** (spells, races, classes, monster/encounter/selection *lookups*) — a
    same-key entry **overrides**, but the winner among plugins is **source-name hash order**
    (nondeterministic, not last-imported).
  - **Pool/flat-seq types** (feats, backgrounds, invocations, boons, languages, draconic ancestries;
    the monster *listing*; subraces/subclasses/selections read as a seq) — a same-key entry
    **duplicates** (both show), it does NOT override.
  - **Spell → spell-list reverse ref** (`plugin-spell-lists`) — **unions** membership across all
    same-key copies: duplicate membership, and you can't narrow a spell's class access by override.
- **Detection**: import/export **warn** on duplicates (`detect-duplicate-keys` →
  `:internal-conflicts` / `:external-conflicts`) but **block nothing**. Optional remedy:
  `apply-key-renames` (only rewrites `subclass→:class`, `subrace→:race` references).

### Should the pool types be "fixed" to dedup by key? (answer: no)
**DESIGN.** Per-type dedup just relocates the problem — it wouldn't fix the nondeterministic winner or
the spell-list union, and it adds parallel dedup logic per type. The pool shape ("show every enabled
item") is *correct* once the §0 invariant holds. Fix the invariant, not the pools.

---

## 2. Duplicate-key resolution mechanism (DESIGN)

One mechanism, used by three triggers, all reusing the existing `:disabled?` flag + `common/toggle-in`:

- **On create** (user makes a new `:elf` over an existing one): warn; if they don't rename, **disable
  the original** (newcomer wins — deterministic, no hash-order roulette).
- **On import** (incoming duplicate): default to **disabling the *incoming* copy** (don't silently
  override the user's existing content), with one-click **"override instead"** that flips which side is
  disabled. Do NOT assume the import should win.
- **On fork/variant** (see §4): disable the *base* example item; the user's copy is the live one.

Rules: **warn + let the user choose, never silent-disable**. Always disable exactly one side so the §0
invariant holds. This converts today's implicit / nondeterministic / type-varying override into one
explicit, deterministic, uniform rule — and it's the same primitive the fork feature needs.

**PREREQ (load-bearing):** item-level `:disabled?` must be honored in **every** read sub, not just
source-level. Source-level drop is confirmed in `::e5/plugin-vals`; item-level filtering exists on a
load path (`events.cljs`, the `(not (:disabled? ...))` guards) but has NOT been confirmed across all
content subs. Confirm/close this first — the whole disable-based approach rests on it.

**Prior-decision note:** `key-collision-behavior.md` records that the import conflict *edge cases*
(rename/skip/replace interactions) were deliberately parked ("leave them"). The disable-based approach
is a *different, cleaner tack* than re-chasing that modal's edge cases — but building it is a
deliberate re-opening of that area and belongs on its own branch (§5), not smuggled into unrelated work.

---

## 3. Content provenance tiers (DESIGN)

Three tiers, all living in the normal `:plugins` store (so the builder edit/save/delete/toggle events —
which are all `:plugins`-pathed — work unchanged; a separate store would force every edit event to be
store-aware, which is the opposite of "behaves like homebrew"):

| Tier | Flag | Editable? | In Export-All? | Notes |
|---|---|---|---|---|
| **Owned** | (none) | yes | yes | normal homebrew |
| **Example** | `:example?` | yes | **no** | ours, versioned (§4); usable + editable but not yet theirs |
| **Variant** | fork of an example | yes | yes (it's owned) | user's edited copy; base example item disabled |

- **Export-All** (`::e5/export-all-plugins`, reads `(:plugins db)`) filters out `:example?` sources with
  one predicate. Per-source export ignores the flag (explicitly picking it *is* the opt-in). This is the
  whole cost of "loads by default, opt-in to export, no padding."
- `:example?` is **orthogonal** to `:disabled?` (disabled = "don't use"; example = "usable but not
  yours").

---

## 4. Example content: versioned, self-updating (DESIGN)

The example set is **the one homebrew we author**, so we can **version** it. That turns "seed once" into
"seed per version" and makes the lifecycle clean:

- Ship the set as versioned app data `{:version N :plugins {...}}`. Track a small **per-account** record
  `{:example-version-resolved K, :mode :active|:kept|:dismissed}` (per-account so it follows the user
  across devices; localStorage is the MVP fallback).
- **Seeder is version-gated**: surface the example source only when `app-version > resolved-version`.
- **Keep in library** (reuse the share feature's verb) = clear `:example?` on the existing source
  in-place **and** stamp `resolved-version = N`. No duplication: the same instance becomes owned, and the
  version-gate stops re-seeding it.
- **Dismiss / Reset examples** = disable/remove + stamp `resolved-version = N`.
- **New version** (`N+1 > resolved`) auto-surfaces the new content ("automatically enables").
- **Edit an example item → it graduates to Variant** (per-item copy-on-edit): the edited item becomes
  owned and version-**pinned**; untouched example items keep updating and physically can't clobber the
  user's edits. Whole-source **Keep** graduates everything at once.

### Version reconciliation of a forked variant (DESIGN — build LAST)
When the base of something a user forked changes in a new version: **notify** (reuse the share feature's
collision-notice) — *"the example you based this on was updated"* — with **Keep mine / Take update /
Keep both**. **Hold the line: notify + pick, never auto-merge** their fork against the new base (that's
3-way merge of arbitrary homebrew — not worth it). Most speculative piece; design now, build last.

Content must be **clearly fictional/synthetic** (no non-SRD content shipped as if official), each item
exercising one mechanic (race floating spread, background saves, feat `:save` rider, draconic ancestry,
standalone save-prof). Doubles as the shared E2E fixture.

---

## 5. Library-management UX (DESIGN — the "next important part")

Independent of examples and independently valuable; these are the **management surface** the whole
disable-based approach needs (else disabled content is a black hole):

- **Show disabled content** in My Content — with a **counter** and a filter toggle. Required, not
  optional, once disable is a real override/fork mechanism.
- **Search** within a library (grows necessary as libraries + disabled items pile up).
- **Hide empty content-type categories** — the UI currently renders every possible type per library
  even when it holds none. Near-free win; do it first.

---

## 6. Move / copy content between sources (DESIGN)

The reorg gap the user keeps naming. Mechanically small: relocate the item between top-level source maps
+ rewrite its `:option-pack`. The hard part (key collisions) is covered by the Summer Patch machinery
(`detect-duplicate-keys`, `apply-key-renames`) — reuse it. Independent, foundational, broadly useful.

---

## 7. Suggested branch decomposition (DESIGN)

Each slice is independently shippable → smaller PRs, faster integration, less merge risk. Matches the
prototype-then-converge governance (D23).

1. **Library management + duplicate-key resolution** (foundation, ship first): hide empty categories,
   disabled visibility/search/counter, and the §2 disable-based duplicate resolution. *Also fixes the
   nondeterministic-override bug — value independent of examples.* PREREQ §2 first.
2. **Move/copy content between sources** (§6) — independent, foundational.
3. **Example-content tier + seed-per-version + fork/variant** (§3–§4) — builds on 1 (+ share-feature
   `keep-in-library` verb). Version-reconciliation (§4) built last.

---

## Open decisions
- **OPEN:** where do graduated (Kept/edited) items land — the source's original name, a "My Homebrew"
  default, or user-chosen at keep time?
- **OPEN:** should `:disabled?` (override) content be excluded from export too, or exported as-is?
- **OPEN:** per-account state store vs localStorage for `example-version-resolved` (accounts exist;
  per-account is better but heavier).
- **PREREQ:** item-level `:disabled?` honored across all read subs (§2).
- **TRACK:** file the nondeterministic-override behavior as its own issue (affects users today,
  independent of this work).
