# Character heals — how a character's picks follow renamed content

A character stores each pick as a content key (`{:orcpub.entity/key :noble}`). When the content's
key changes (an import conflict renamed it, an author changed it, an older version offered it under
a different key), the character's key names nothing and the pick renders as empty. A **heal**
rewrites the pick to the key the content has now. This page is the long form; the code carries the
spec and the `FIELD NOTE`s (`content_reconciliation.cljs`, "Former keys").

Code as of `port/save-gate` (#37), 2026-09-29.

## The three things that happen when a character loads

| step | what | repairs? | code |
|---|---|---|---|
| 1 | **former-key heal**: picks of an old key move to the item's current key | yes, automatically | `reconcile-former-keys` |
| 2 | **spell-selection heal**: a class's spell selections saved under the wrong prefix move to the class's own keys | yes, automatically | `reconcile-spell-selection-keys` |
| 3 | **class binding report**: classes that do not bind; subclasses filed under the wrong class | no, reported only | `class-binding-report` |

Then, separately, the **relink question** ("Which “X” did this character mean?") asks the person
when an import renamed their item and another item now holds the old key. That is a question, never
automatic (`relink-to-ask`, `::e5/answer-relink`).

Order matters: step 1 runs first because step 2 needs each class's key already current. Step 3 runs
last so it lists only what is still wrong.

## Where heals run

Every heal goes through `reconcile-former-keys`. It is called from three places:

| where | reads | stored? |
|---|---|---|
| `events/set-character` | the builder's character, on every load | when the person saves |
| `autosave-fx/cache-template` | re-dispatches `:set-character` once, when the list of offered keys first exists | as above |
| `subs ::char5e/character` | saved characters, in every view that shows one (`views.cljs` reads it in eight places) | never; in memory only |

Before the template exists, the offered-key lists are nil and both indexes are empty, so nothing
heals. `cache-template` exists for that case: a character that loaded before the template is healed
when the template arrives.

A heal in the builder is **in memory until saved**. It sets `:character-healed`, which shows an
8-second toast ("Reconnected N references… Save the character to keep the fix.") and gives the Save
button the `save-healed` class. Unsaved, the same heal runs again on every load. After a save,
`:set-character` runs on the saved character, finds nothing, and clears the flag.

## What a heal will and will not do

The **index** maps each former key to a current key. A former key comes from an item's
`:former-keys`, and, for a background, from its name (`common/name-to-kw` of `:name`): earlier
versions offered backgrounds by name even when the item stored another key, and characters hold
those keys.

A former key is **left out** of the index, so picks of it are not moved, when:
- two items claim it (which one did the character mean is a question for the person);
- a library item holds it now (the key still answers; moving the pick would change what it means);
- the builder offers it now (the same, for built-in content).

**By type.** A key is unique only within its type. Picks at a `library/pick-homes` path (race,
subrace, class, background, feats) use their own type's index and offered keys. Every other pick
uses the flat index, where a key offered or held by anything blocks the heal. Why, and the case
that proved it: `typed-keys.md`.

**The character is rewritten, not the matching.** `t/option-cfg` builds template options from a
fixed list of fields and drops the rest, so `:former-keys` never reaches the template. Healing at
match time would mean threading it through every option builder.

## The spell-selection heal

A class's spell selections are keyed `:<class-key>-cantrips-known` and `:<class-key>-spells-known`.
Two populations of saved characters have them under another prefix:
- For a while the plugin-classes subscription changed a class's `:name` to "Cleric (Source)", and
  selection keys are derived from the name, so characters saved then hold
  `:cleric-source-cantrips-known`.
- Before keys were stored, a homebrew class's selection keys came from its name, so a class stored
  as `:artificer-kibbles-tasty` has selections under `:artificer-cantrips-known`.

A key is moved only when exactly one of the class's expected keys has the same suffix. Classes not
loaded are left alone; the missing-content report covers them.

## The class binding report

It reports and never repairs: an unbound class cannot be fixed by guessing, and a misfiled subclass
binds without error while granting the wrong features. It is kept out of the heals' return value on
purpose. `:rewrote` was once returned alongside the character and every caller destructured only
`:character`, so heals were invisible and redone forever. A separate report cannot be dropped that
way. A subclass whose owner is unknown is never called misfiled, or every missing plugin would
produce a second, wrong complaint.

## Tracing a heal

| symptom | look at |
|---|---|
| empty pick on the character page, fine in the builder | the `::char5e/character` subscription; is the page reading `::char5e/character-map` directly? |
| fine on the page, empty in the builder | `set-character`; did `cache-template` run (is `::content-recon/offered-keys` set)? |
| the heal toast on every load | the character was never saved, or the save did not persist the healed key |
| no heal at all | the index: is the former key in it, and if not, which rule left it out? |
| the wrong thing healed | a pick typed by the wrong rule; `typed-keys.md` |

In the browser console, on the page in question:

```js
const cc = cljs.core, rd = cljs.reader.read_string, rf = re_frame.core;
const db = cc.deref(re_frame.db.app_db);
// the raw saved pick, and what pages see
cc.pr_str(cc.get_in(db, rd('[:orcpub.dnd.e5.character/character-map <id> :orcpub.entity/options :background]')));
cc.pr_str(cc.get_in(cc.deref(rf.subscribe(rd('[:orcpub.dnd.e5.character/character <id>]'))),
                    rd('[:orcpub.entity/options :background]')));
// the indexes, and whether a key is offered anywhere
cc.pr_str(cc.deref(rf.subscribe(rd('[:orcpub.dnd.e5.content-reconciliation/former-key-index]'))));
cc.pr_str(cc.deref(rf.subscribe(rd('[:orcpub.dnd.e5.content-reconciliation/typed-former-key-index]'))));
cc.contains_QMARK_(cc.get(db, rd(':orcpub.dnd.e5.content-reconciliation/offered-keys')), rd(':blue'));
```

## Tests

- `content_reconciliation_test.cljs`: the index rules, the typed heal and its three guards, the
  spell-selection heal, the binding report.
- `events_test.cljs`: `a-saved-characters-page-sees-a-renamed-pick-healed`.
- `homebrew_save_lifecycle_test.cljs`: heals after key changes made through the builder.
- `test/e2e/upgrade.js`: characters made by the old app, opened by the new one.
- `field_notes_test.clj`: the heal's field notes stay in the code.

## History

- The former-key heal came with import-conflict renames: resolving a conflict renamed a key and
  every character that had picked it lost it. It first kept one `:former-key`; chains (A→B→C) kept
  only the last link, so it became the `:former-keys` list (`key-collision-behavior.md`, "The
  rename history").
- 2026-09-29: the upgrade test found saved characters losing a homebrew background on their page.
  Pages read characters unhealed, and the heal was blocked by a dragon colour sharing the key. Both
  fixed on #37 (`43f93777`); `typed-keys.md`.
