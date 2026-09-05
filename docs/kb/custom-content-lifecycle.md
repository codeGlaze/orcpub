# Custom content — the three mechanisms

"Custom content" in orcpub is **not one feature**. There are three separate
mechanisms that look parallel in the code but behave completely differently.
Conflating them is the single biggest source of confusion here (and the root of
the recurring "Missing Content — Background: `:custom`" false alarm), so start
with the split.

| | A. Inline "Custom" option | B. Full builder | C. Magic items |
|---|---|---|---|
| Created by | picking **"Custom"** in a dropdown | a dedicated builder page's **Save** | the item builder's **Save** |
| Stored in | inline on the character entity | `:plugins` (the homebrew library) | `::mi/custom-items` (server) |
| localStorage | `"character"` (with the character) | `"plugins"` | server-backed (`"magic-item"` holds only the WIP draft) |
| Key | the sentinel `:custom` | stable `name-to-kw` key | server `:db/id` |
| Character references it by | `:custom` + `::entity/value` (typed name) | its **real key** | equipment entry in `::entity/values` |
| In `.orcbrew` export? | **no** | **yes** | **no** |
| Seen by missing-content reconciler? | no (resolves inline — see below) | yes, if the source is loaded + enabled | n/a (no item content-type) |

## A — Inline "Custom" option (name-only, per-character)

A `t/option-cfg` literally named `"Custom"` in a selection list. Selecting it
sets the option key to `:custom` (because `common/name-to-kw "Custom"` →
`:custom`) and stores a **typed display name** in `::entity/value`, plus any
inline sub-selections in `::entity/options`, **on the character itself**. No
library entry is created; nothing is added to any content store.

Character option shape (background example):

```clojure
{:orcpub.entity/key   :custom
 :orcpub.entity/value "My Homebrew Background"
 :orcpub.entity/options { … inline skill/tool sub-selections … }}
```

Active inline options (all render via the shared `custom-option-builder`, a
single "Name" text field): race, subrace, background, subclass
(`options.cljc` ~2118–2195, 2745). **There is no active inline custom feat or
class** (the feat wiring exists but is commented out; classes are builder-only).

Persisting events — each writes only `::entity/value`, nothing to a store:
- `:set-custom-race` `events.cljs:1937`
- `:set-custom-subrace` `events.cljs:1947`
- `:set-custom-subclass` `events.cljs:1960`
- `:set-custom-background` `events.cljs:1985`

> **Gotcha:** `:set-custom-background` (inline, above) and `::bg5e/save-background`
> (builder → `:plugins`, below) look parallel but do completely different things.
> The inline event only writes a name onto the current character.

## B — Full builders (real, reusable, exportable library entries)

The generic factory `reg-save-homebrew` (`events.cljs:712`) registers a save
event per type. On save it computes `key (common/name-to-kw name)`, validates,
then `(assoc-in plugins [option-pack content-type-kw key] item)` and dispatches
`::e5/set-plugins`, which persists `:plugins` to localStorage `"plugins"`
(`db.cljs` ~50, 255–264). Registered for background/race/subrace/subclass/class/
feat (+ spell/monster/encounter/language/invocation/boon).

These are real content: keyed by a stable name-derived key, referenced by a
character via that **real key**, and serialized into `.orcbrew` exports
(`serialize-orcbrew` `events.cljs:3913`; `::e5/export-all-plugins` `events.cljs:4204`,
which reads `(:plugins db)` only).

> **Gotcha — localStorage key collision:** the slots `"background"`, `"race"`,
> `"subclass"`, … (`db.cljs` ~35–49) hold the builder's **in-progress draft**,
> NOT the saved entry. Saved backgrounds/races/etc. live *inside* `"plugins"`.
> `builder-wip-stores` (`db.cljs` ~353) is the only place this is spelled out.

## C — Magic items (server-backed, a third store)

Magic items do **not** use `reg-save-homebrew` and do **not** land in `:plugins`.
`::mi/save-item` (`events.cljs:523`) POSTs to the items route; saved items live in
`::mi/custom-items`, hydrated from the server (`equipment_subs.cljs:33`). A
character references them as **equipment** (`::entity/values`), not as
`::entity/options` content, and they are **not** part of an `.orcbrew` export.

## Missing-content reconciliation and why inline `:custom` was false-flagged

`::char5e/available-content` (`subs.cljs:1451`) builds "what's available"
**only** from the six `plugin-*` subs, which derive from `:plugins`
(`spell_subs.cljs` — note these drop any source or item flagged `:disabled?`).
SRD content is not in `:plugins`; it's covered by small hardcoded `builtin-*`
sets in `content_reconciliation.cljs` (the 5e SRD genuinely only includes
Acolyte, Grappler, and one subclass per class, so those sets are complete and
static — not stubs).

`check-content-availability` (`content_reconciliation.cljs`) then flags any
referenced key that is in neither the plugin sets nor the builtin sets.

- **Builder content (B):** in `:plugins` → resolves. Correctly flagged **only**
  when its source is disabled or not loaded — a genuine "re-enable / upload this"
  signal.
- **Inline content (A):** key `:custom`, never in any store because it resolves
  **inline** on the character. Before the fix it fell through to "missing",
  producing the persistent false "Missing Content — Background: `:custom`".

**Fix:** guard the `missing?` computation with the closed inline-sentinel set
`#{:custom :none}` (`content_reconciliation.cljs`) — `:custom` = inline custom
content (resolves against itself), `:none` = no selection. Done at the single
`check-content-availability` chokepoint, so one guard covers background/race/
subrace/subclass. Mirrors the pre-existing `#{:none :custom}` guard in
`events.cljs` (`selected-plugin-options`). No change to `available-content` was
needed: inline content is not *meant* to resolve against a store, and builder
content already resolves through the plugin subs.

> These two sentinels are the *complete* set the entity model can produce (every
> inline "Custom" option collapses to `:custom`; `:none` is the empty selection),
> so this is a closed guard, not an open whitelist that grows per content type.

## Known weakness (follow-up)

The warning still names only the key (`Background: :custom` / `:some-key`), not
the **source `.orcbrew` file** to upload — so even for a genuine miss (disabled/
unloaded builder content) it isn't fully actionable. Recovering the source
(from the key's option-pack or the character's stored selection source) would
make it earn its place.

## Code map

- Inline options: `options.cljc` ~1193 (`custom-option-builder`), 2118–2195, 2745
- Inline events: `events.cljs:1937–1993`
- Builder save factory: `events.cljs:712` (`reg-save-homebrew`)
- Export: `events.cljs:3913` (`serialize-orcbrew`), `4204` (`export-all-plugins`)
- Magic items: `events.cljs:509–532`, `2269`; `equipment_subs.cljs:33`
- Stores / localStorage keys / writers: `db.cljs` ~33–53, 179–264, 353–378
- Reconciler: `content_reconciliation.cljs` (`check-content-availability`,
  `builtin-*`, `inline-content-sentinels`); availability sub `subs.cljs:1451`
