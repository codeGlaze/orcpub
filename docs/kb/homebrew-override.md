# The homebrew override (the mug icon)

A per-selection switch in the **character builder** that waives the rules the app would otherwise
enforce on that selection. Easy to forget it exists, which is why it is written down here.

Not to be confused with **"My Content"** — the homebrew *authoring* area, which uses the same
`beer-stein` icon (`views.cljs:549`, `views_2.cljc:91`). Different feature, same glyph.

## Where it is

`character_builder.cljs:644-652`, rendered next to a selection's title, beside the lock. Tooltip:

> Homebrew is off for X — enabling this option allows you select options you would not normally
> have (turns on homebrew rules)

## It is already per-thing

`(toggle-homebrew path)` — the flag is keyed by **selection path**, not global. Turning it on for
Skills does not turn it on for Feats. Call sites opt out with `:hide-homebrew? true` where the
switch makes no sense.

## The mechanism

State lives on the character as `::entity/homebrew-paths`, a `{path → bool}` map
(`events.cljs:1961`, `subs.cljs:98`). It survives save/load — `to-strict` / `from-strict`
round-trip it (`entity.cljc:129-218`), so it is part of the character, not UI state.

Three enforcement points read it:

| what it waives | where |
|---|---|
| the selection's min/max count — `count-remaining` returns 0 | `entity.cljc:726` |
| option availability — `allow-select?` ignores `selectable?`, `remaining`, `has-prof?` | `character_builder.cljs:1277` |
| makes `<none>` a legal pick, via a prereq that reads the flag | `options.cljc:2358` |

## What it does NOT do — the part that matters for design

It waives **selection** constraints: what you are allowed to pick. It does not touch **computed**
values. Nothing in the AC engine, or any other derived number, consults `::homebrew-paths`.

So a rule expressed as *"you may not select this"* is overridable by the player for free. A rule
expressed as *"this computes to N"* is not overridable at all.

**Design consequence.** Restrictions should be built as selection constraints wherever possible, so
this switch is their override. Worked example: "a tortle can't wear armor" (see the roadmap). Built
as an equipment-selection restriction, a DM and player can flip the mug and build an armor-wearing
tortle. Built as an AC computation — which is what `:armor-gives-no-ac` is today — there is no way
to override it short of authoring different content.
