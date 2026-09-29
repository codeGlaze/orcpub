# The homebrew save path — the rework, and what it cost

**Status: BUILT 2026-09-18 → 2026-09-20**, on `feature/grant-rows`. The mechanism is described in
`key-collision-behavior.md`; this page is the account of getting there, because the same mistake was
made four times in four disguises and the shape of it is worth keeping.

## The rule, if you read nothing else

> **Identity is established where an item is READ, never re-derived when it is saved.**
>
> **A guess is fine for a save that cannot lose anything, and never fine for one that deletes.**

Every bug below is one of those two sentences being violated.

## Why re-deriving identity cannot work

The save used to work out which entry it was writing from fields on the item. All three are guesses:

| field | why it lies |
|---|---|
| the **name** | matches any entry that happens to share it |
| `:option-pack` | what the item DECLARES; an import that renames a source leaves it stale |
| `:key` | absent entirely on libraries authored before keys were stored |

So: `process-plugin-vals` (and its replica `compute-plugin-vals`) stamps `:key` and `:option-pack`
onto every item on the way out of `:plugins`. That map is the one place both are known for certain.
My Content's edit and delete buttons additionally pass the row's address, and the content type is
bound at **registration** — a builder only ever edits its own kind — so every `:builder-origin`
record carries one without a call site supplying it.

`:builder-origin` is persisted beside the draft. It cannot be derived from the draft: the item's
`:option-pack` is whatever the author currently has typed in the field.

## What it started as

Retyping Option Source Name **copied** the item instead of moving it. One key then answered in two
libraries, and that state refused every later save of either copy as a collision with its twin.
Three symptoms, one missing fact: the save did not know where the item had been.

## The four review rounds

Each round reviewed a port branch cut from `integration`. Roughly two thirds of the findings were
regressions introduced by the previous round's fix — each fix addressed a symptom, and the design
only settled when identity moved to the read path.

| round | the one that mattered |
|---|---|
| 1 | A NEW item whose name matched an untagged entry silently overwrote it. The hole existed in one save path; the fix under review had extended it to four. |
| 2 | An EMPTY Option Source Name was a move. `::option-pack` is `string?`, so `""` satisfies the spec and never reached the missing-field banner — clearing the box to retype it deleted the item and re-homed it under a source named `""`. |
| 3 | "The builder stamps the address" held at **one of eleven** edit doors. A key-less item edited from any of the other ten forked into two entries. |
| 4 | The stamp fix reached **nine of ten**. The subclass pencil reads `::e5/plugins-with-sources`, which filters but does not stamp — so editing a visible copy wrote over a disabled, invisible one. |

Also found and fixed across those rounds: the delete button read identity off the item (deleting
nothing for a pre-keys library, or the wrong library's entry); `::selections5e/save-selection` had
no collision check of any kind; `replacing` could consent to discarding one entry and leave the
duplicate standing; a record left by one builder validated for another; a key change did not
re-stamp the origin; `::persist-builder-origin` ignored a quota failure, which leaves the PREVIOUS
value in the store to come back beside a draft it does not describe.

## Two process failures worth naming

- **A grep reported as conclusive was wrong.** It required `[::`, which misses
  `(make-event-handler ::ns/edit-thing item)`. Ten call sites, none found — and the round-3 fix was
  designed around the belief that they did not exist. When a fix's correctness rests on "there are
  no other callers", the search for those callers is part of the fix and deserves the same scrutiny.
- **"Too expensive to close" was not costed.** Binding the content type at registration was deferred
  twice as needing thirteen call sites threaded through two branches. It is thirteen registrations
  gaining one argument each, in one file, with no view, subscription or dispatch change.

## What the tests look like

66 tests in `homebrew_save_lifecycle_test.cljs`, plus `test/e2e/replace-or-refuse.js` and
`move-between-sources.js` (which reloads the page mid-edit, to pin the persisted origin).

**Every fix was verified by removing it** and confirming exactly the expected tests failed — seven
for the address stamp, two assertions for the blank-source guard, four browser checks for the origin
persistence. A test that has never been watched to fail is not yet evidence: two of the tests in
this suite passed for the wrong reason until a reviewer traced them (one filed a fixture under the
TAGGED key, which an untagged address never matches; another asserted a property of the reader
rather than of any door).

## Open

- `plugin-datalist` (the Option Source Name field) keeps the source in a component-local atom that
  New does not reset — `plan-next.md` has the settled fix.
- `::e5/relocate-selected`, `::e5/repair-quarantined-source` and import conflict resolution write
  `:plugins` outside the gate. Each was checked and none is made more dangerous by this work: a
  stale builder now hits the `:unrecorded` refusal where it previously hit `assoc-in`.

## Provenance

`b6ff0804` the move · `7854cc91` the replace offer and the four save paths · `d4b3eb1f` the copy
rewrite · `430db211` key-less items · `856c7f03` identity at fetch · `d2c596a0` a move needs a
record · `226bc0fb` the read path stamps · `1d6977a8` the origin is persisted · `a43da38f` the
content type at registration.
