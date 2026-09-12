# Extras: what each kind needs to function

*The layer beneath [plan-companions-and-wild-shape.md](plan-companions-and-wild-shape.md).
That doc classifies the seven kinds; this one says what each needs to work. Nothing built.*

Markers, borrowed from `declarative-grant-vocabulary.md` on `refactor/content-extensibility`:
**VERIFIED** = read from code, with file:line. **DESIGN** = proposed. **OPEN** = undecided.

## One record, capabilities toggled

**DESIGN.** Not four disjoint types. One creature record, with capabilities switched on per
kind:

```
statblock      referenced source + local copy      every kind, including pets
identity       name, image, notes, ribbons         every kind
enhancements   table-granted mechanical additions  every kind
parameterised  computed from the owner             companions only
progression    gains its own class levels          sidekicks only
```

The reason is **promotion**. A pet becomes an NPC becomes a sidekick — not at every table,
but often enough to design for. Disjoint types would force delete-and-recreate, losing the
name, the notes and the history, which is precisely what a promoted creature has earned.
Promotion is switching on `progression`. Nothing is destroyed.

This also means a pet is not "inert". It gets a statblock like everything else — tables pick
a close-enough beast when the SRD lacks the exact one — and room for ribbons and notes.

## Copy-on-adopt, not reference

**DESIGN**, and it reverses an earlier lean toward references.

A table picking Wolf for their pet fox does not want a live pointer to Wolf. They want to
start from Wolf and diverge: rename it, add a ribbon, bump a stat when it toughens up. A
reference either forbids that or silently drifts when the source changes.

So: **copy the statblock on adoption, record where it came from.** Consequences worth
stating —

- A copy cannot dangle. Removing an option-pack cannot orphan a companion, which removes the
  reference-integrity risk the plan doc previously flagged as the main one.
- A copy cannot be corrected either. If the source statblock is fixed, adopted copies keep
  the bug. The recorded source makes a "source has changed" notice possible; whether to build
  one is **OPEN**.
- The same operation serves the [NPC statblock customizer](plan-npc-statblock-customizer.md):
  reskinning a Bandit Captain is copy-on-adopt with edits.

## What each kind needs

| Kind | Statblock | Parameterised | State during play | Builder must ask |
| --- | --- | --- | --- | --- |
| **Pet / follower** | copy, any source | no | HP, conditions | which creature, name |
| **Familiar** | copy, from a fixed list | no | HP, conditions | which form |
| **Steed** | copy | no | HP, conditions | which creature |
| **Companion** | copy + owner formulas | **yes** | HP, conditions | which creature, within bounds |
| **Summon** | copy per instance | sometimes | HP, conditions, duration | count-vs-CR trade, who chooses |
| **Raised** | copy per instance | no | HP, count, re-assert window | how many, which type |
| **Sidekick** | copy + **its own levels** | no | everything a character has | class, then everything |
| **Wild Shape form** | copy, filtered by bounds | merge with owner | HP only — it ends the form | which forms, up to N |

Two rows carry nearly all the cost. **Companion** needs the owner-formula layer; **sidekick**
needs a second progression engine and is deliberately deferred.

## Where state lives

**VERIFIED.** Per-creature mutable state already exists — the encounter tracker stores
`[:monster-data <monster-key> <individual-index> :hit-points]` and `:conditions`
(`events.cljs:3346`, `:3354`). Keyed by monster and instance index, so multiple copies of one
creature are already handled.

**But it persists to local store, not the character entity** — `combat-interceptors` is
`[(path ::combat/tracker-item) combat->local-store-interceptor]` (`events.cljs:227`). Browser-
local, per-device, not part of the saved character.

That settles the shape of the earlier open question. Three options, ordered by cost:

1. **Print only.** No state. The card has a ruled HP box; the table writes on it. Cheapest,
   matches how a Wild Shape card is used.
2. **Local state**, reusing the tracker's existing shape. Survives a reload, not a device
   change. Moderate.
3. **On the character entity.** Survives everything, syncs, appears on a shared sheet. The
   largest change in either leaf — the entity has no per-creature store today.

**OPEN**, but the gap between 1 and 3 is wide enough that it should be decided before slice 4,
not during it.

## Decided here

- One record with capabilities, not a type per kind.
- Promotion is a capability toggle, never a re-create.
- Copy-on-adopt with the source recorded; not a live reference.
- Every kind gets a statblock, pets and followers included.

## Still open

- **HP state**: print-only, local, or on the entity. See above.
- **Ribbon vs enhancement**: two fields, or one field with an optional mechanical part? One is
  simpler and a ribbon is arguably an enhancement with no numbers.
- **Is promotion reversible?** Demotion is rare; "I promoted the wrong one" is not. Undo is
  far cheaper to design now than to retrofit.
- **The collective noun.** Extras is the working term, with companions / sidekicks / summons /
  raised / bound / shapes / mounts+vehicles / properties beneath it. Not settled.
