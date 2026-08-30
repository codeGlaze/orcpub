# Bulk review of unclassified custom items

Paused, not abandoned — a security branch took priority. Everything needed to
pick this up is here.

## Why it exists

`classify` has three answers: `:magical`, `:mundane`, `:unreviewed`. The third
means "the server cannot tell, so it will not guess" — the item keeps behaving
exactly as it always has (as a magic item) and its owner is asked instead.

Dropping the `:common` rarity inference (commit `0bee818`) was correct — rarity
is a magic-item property in 5e, mundane gear has none, and `:common` is both a
real magic rarity and the item builder's old default, so it carries no signal.
But it means noticeably more items land in `:unreviewed` than under the old
rule, and the only way to answer the question today is to open each item
individually.

## The gap

There is no way to see how many items are unreviewed, and no way to answer more
than one at a time. For an owner with a decade of content that is not a
realistic ask.

## What it needs

- A count, surfaced somewhere the owner will see it (My Items).
- A filtered view: show me only the items still asking a question.
- Answer several at once, without opening each — the question is binary, so a
  list with two buttons per row is enough.
- Nothing destructive. Marking an item mundane suspends its magical properties;
  it never deletes them, and that has to stay true in bulk. `without-magical-
  properties` is suppression, `clear-magical-properties` is the deliberate
  removal, and bulk review must only ever do the former.

## What already exists to build on

- `::mi5e/items-holding-magic` — keys only, deliberately, so suspended
  mechanics have no route back into anything that builds modifiers.
- `mi/classify` / `mi/ready-to-save?` / `mi/incomplete-fields` — the item's
  state is already derivable without new predicates.
- `::mi/set-item-kind` — writes an explicit boolean, which is what retires an
  item's guess. Bulk review is this event applied to a selection.
- The My Items list already renders classification and the "magic set aside"
  marker per row.

## Open question that shapes the UI

How many items are actually unreviewed in production? A list of twenty needs no
paging or filters; a list of two thousand needs both. Worth measuring before
choosing a shape — the backfill's report already counts `:left-unreviewed` at
startup, so the number may already be in the server log.

## Not to be confused with

The item-classification work itself, which is finished and on
`fix/custom-item-classification`. This is only the "answer the question at
scale" piece.
