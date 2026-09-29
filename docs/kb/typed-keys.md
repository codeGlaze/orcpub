# A key means nothing without its type

How heals work end to end: `character-heals.md`. This page is the type rule.

**Rule.** A content key names something only together with the type it was picked as. `:blue` is a
dragon colour under the Dragonborn's ancestry choice and a background under `:background`. Any code
that looks up, heals, matches or reports a key must know the type, or else apply the strictest
rule across every type. Treating one flat set of keys as meaning one thing reads one kind of
content as another.

Verified 2026-09-29 on `port/save-gate` (#37, `43f93777`) by the two-phase upgrade test (`test/e2e/upgrade.js`)
and the tests named below. Code: `library.cljc` (`pick-homes`, `pick-types`, `offered-by-type`),
`content_reconciliation.cljs` (`typed-former-key-index`, `reconcile-former-keys`,
`check-content-availability`).

## What went wrong (verified)

| # | finding | evidence | fixed by |
|---|---|---|---|
| 1 | A saved character's own page read it raw: the key heal ran only in the builder | upgrade test: Upgrade One's page lost its background | the heal runs inside `::char5e/character`; test `a-saved-characters-page-sees-a-renamed-pick-healed` |
| 2 | The heal refused `:blue → :noble` because `:blue` was offered **anywhere**: the built-in Dragonborn "Blue" ancestry (`options.cljc`, `draconic-ancestries`) | probe of the live app: `:blue` in `offered-keys`, absent from the index | typed index for picks at a home path; test `a-background-heals-past-another-types-key` |
| 3 | The heal's walk was type-blind (`postwalk` over every `::entity/key`). Relaxing only the check in 2 would have rewritten the Dragonborn's ancestry into the background, and a save makes that permanent | code reading; the same test's second assertion fails when the walk is made type-blind | the walk passes each pick's selection path, and only home picks use a typed index |
| 4 | `pick-types` typed a pick by its selection's **name** at any depth, so a class's own choice named `:background` counted as the background | code reading | typed by selection **path**; tests `a-choice-reusing-a-homes-name-is-not-that-home`, `a-pick-is-only-what-its-selection-can-hold` |
| 5 | The builder's missing-content check used the same flat set, so the lost background was **not reported**: a silent loss | upgrade test: "reports no missing content" passed with the background gone | typed lookup for extracted picks; test `a-pick-is-missing-only-if-its-own-type-lacks-it` |

The fixture behind all of it is `test/fixtures/test-pak.orcbrew`: its background "Blue" is stored
under `:noble`, and the old app offered it under its name's key, `:blue`.

## Where a pick's type is known

`library/pick-homes` is the one table. It maps a **selection path** (the selection keys from the
character's root down to the pick) to a content type:

| path | type |
|---|---|
| `[:race]` | races |
| `[:race :subrace]` | subraces |
| `[:class]` | classes |
| `[:background]` | backgrounds |
| `[:feats]` | feats |

A pick at one of these paths is healed and checked against **its own type only**: renames of that
type, and keys offered at that path (`offered-by-type`). Any other pick (a subclass, an ancestry,
a class feature choice, anything granted) keeps the flat rule: a former key offered or held
anywhere is left alone.

Three things keep that safe:
- **No list means unknown, not empty.** A type missing from `offered-by-type` falls back to the
  flat rule. Test: `a-type-with-no-offered-list-keeps-the-flat-rule`.
- **Home by path, never by name.** `[:class :background]` is not `[:background]`.
- **One table.** `offered-by-type`, the typed index and `pick-types` all read `pick-homes`, so a
  new home is one entry.

Each guard's test was checked by removing the guard and watching the test fail.

## Before you change anything here

- **A new content type or pick home:** add one `pick-homes` entry, at its real path. Do not type
  picks from the selection name, and do not give a type an empty offered set to mean "unknown".
- **Grant pools.** A granted choice is a selection keyed by the pool's `:name`
  (`"Language"` gives `:language`), nested under the item that grants it (`options.cljc`,
  `grant-selection`). So a granted pick is never at a home path, and it gets the flat rule. That is
  safe but heals nothing. To type granted picks, take the type from the grant's pool registration
  (`grant_pools.cljc`), reached through the template selection's `:grant`/pool tags. **Never infer
  it** from the granting parent (a race granting a feat is not a race pick) or from the selection's
  name (a pool named "Background" nested under a feat is not the background).
- **A pool entry's key is only unique within its type.** A pool that mixes types, or a filter that
  matches keys across pools, has the same flaw as finding 2.
- **Across sources, same type:** a former key claimed by two items is refused, and the relink
  question asks the person. Still open: if built-in content later gains a key a homebrew item used
  to have, the heal refuses and the character quietly gets the built-in item. For backgrounds the
  only built-in is Acolyte.

## Not checked

Whether `content_pools/pool` (built-in ++ homebrew) can hold two entries with one key within a pool,
and which one a grant then gives. Look before relying on it.
