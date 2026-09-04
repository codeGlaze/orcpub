# Proposed: a rules-override layer (working title — name TBD)

**Status: not built. Cross-branch concept doc — this outlives any one refactor branch.**

## What it is

A layer of a-la-carte mechanical grants and permissions that sit *above* the content silos. Not
content, not a feat, not a race trait: a ledger of things a DM has decided are true for a character
or a table.

Worked examples, all of which are awkward or impossible today:

- this character gets an extra feat, at an arbitrary point
- *everyone in the party* gets a feat at level 1
- a tortle can wear armor
- a warforged can wear armor
- this character is size Large

## Why it can't just be "make a feat for it"

A feat is a selection made *by* a character *from* a list, subject to the same rules everything else
is. These are grants that change what the rules permit, applied *to* a character from outside. They
have different lifecycles (a table-wide grant applies to every character in a campaign), different
authorship (the DM, not the player), and different scope (some are permissions — "may wear armor" —
which no feat vocabulary expresses).

Forcing them into feats loses the ledger: you cannot later ask "what has this table been granted,
and by whom", which is the point.

## Naming

**"Boon" is taken twice over** — epic boons and DM boons are real 5e rules constructs, and the app
already ships `:orcpub.dnd.e5.classes/homebrew-boon` in its save specs. The new layer needs its own
word.

Candidates, DM's pick:

- **Dispensations** — literally "permission to do what is normally not allowed". The most exact fit
  for the "a tortle can wear armor" cases. Long.
- **Decrees** — short, unmistakably DM-issued, no rules collision.
- **Writs** — short, ledger-flavoured, reads well in "the party's writs".
- **Codicils** — an amendment to a standing document. Precise but obscure.

## Design constraints, from the discussion that produced this

- It is **its own layer**, not a silo alongside races/classes/feats.
- It is a **ledger** — the record of what was granted is as important as the effect.
- Scope must include **table-wide**, not only per-character.
- Some entries are **permissions** (may do X), not numeric effects. See the note below on why that
  distinction matters mechanically.

## The mechanical hook that already exists

The mug icon (`homebrew-override.md`) waives **selection** rules per selection path, and nothing
consults it when computing a derived value. That is the seam this layer should use: a permission
expressed as a selection constraint can be granted or waived; one expressed as a computation cannot
be overridden at all.

Concretely, "a tortle can wear armor" is only expressible if the tortle restriction is built as an
equipment-selection constraint. It is currently `:armor-gives-no-ac`, a computation — see the AC
roadmap item.
