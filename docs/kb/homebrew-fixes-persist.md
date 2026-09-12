# Homebrew fixes must persist

*Verified 2026-09-12 against `integration-local` at `1f8fef2b`. References are by
function name; the probe is `test/browser/fixes_persist_e2e.js`.*

## The rule

A repair the app makes to homebrew or a character is written to localStorage when it
is made. Held only in app-db, it looks right on screen, is gone on refresh, and gets
made again on the next load. That is how "fixed" problems kept coming back. The
loader's own repairs are written back once, so a second load has nothing left to do.

## Where the writes happen

- `::e5/set-plugins`: the `plugins-interceptors` after step writes `plugins`. Used by
  builder saves and Save anyway, the cleanup that runs when the export dialog opens,
  and conflict resolution in export and library mode.
- `::e5/store-plugins` writes directly, and its success message fires only if the write
  worked. Used by imports and by import-mode conflict resolution.
- `persist-set-aside!` (db.cljs) runs on load. It writes `plugins:rejected` first. It
  then rewrites `plugins` only if something was set aside or repaired **and** that
  first write worked. If storage refuses the first write, both copies stay as they
  were: the set-aside repeats on the next load, and no entry goes missing from both.
- `::persist-healed-character` (from `:initialize-db`) writes the `character` draft.
  The server copy still needs Save, and the save button's sparkle says so.
- `plugins:corrupt`: two kinds of stored value are copied here, text that won't read
  and a value that reads but isn't a library. The active slot is cleared, but only
  once the copy is saved.

## Damaged shapes that get mended

The helpers are `mend-section`, `mend-plugin`, `mend-library` and `mend-import-data`
(e5.cljc). Import runs them right after reading the file; load runs them before
salvage.

- A section stored as text is read back.
- A section stored as a list is keyed by each entry's `:key`, or by the key its
  `:name` derives. It is left for a person if any element isn't an entry, or two
  entries claim one key: guessing there would invent or lose content.
- An empty section is dropped.
- A source stored as text is read back.
- A whole library stored as text is read back, but only if it reads as a map keyed
  by source names.
- A whole import file stored as text is read back.

Whatever none of these can read is set aside. A damaged section goes into
`plugins:rejected` whole. The needs-attention panel names it and offers Export raw
and Discard, because there is no name to type in.

## Proving a path persists

`fixes_persist_e2e.js` is registered in `scripts/test/run-browser-probes.js`. For
each path it does the following:

- Opens a fresh context and seeds storage once, behind a `probe:seeded` sentinel.
  An init script runs on every document, so an unconditional seed would undo on
  reload exactly what the probe checks was saved.
- Applies the fix through re-frame.
- Reads localStorage before trusting app-db.
- Reloads and confirms the reload has nothing left to do: no repair warnings in the
  console, and storage unchanged.
- Exports where there is a file.

To add a path, copy a block and seed whatever makes the fix fire. After
`dispatch_sync`, wait about a second: follow-up events go out on `:dispatch-n` a tick
later.

Against the build before `0b5c05be` and `0b71b8b0` it passed 25 of 33. It failed
exactly those paths, plus one probe bug that read app-db without the `:plugins`
prefix.

For a unit-level check, `events_test.cljs` has `dispatched-by`. It swaps the
`:dispatch-n` fx for a capture during one `dispatch-sync`, so a test can assert what
a handler hands to the store.

## Fixed in this pass

- A character repaired on refresh was never saved (`76dd9497`, `ae6be67b`).
- Entries set aside on load stayed in `plugins` and were set aside again on every
  load (`d3880ecb`).
- Damaged sections loaded without a word and failed later at export (`9ef8bfe5`,
  `a4c69f62`).
- A library stored as text loaded nothing (`0b5c05be`).
- A stored value that wasn't a library was handled again on every load
  (`0b5c05be`).
- Save anyway in the selection builder skipped `sanitize-item-names`, so "9 Lives"
  was saved as `:9-lives` and set aside on the next load (`0b71b8b0`).
- Renames and disables chosen for existing items were dropped when the store kept no
  incoming entry (`01217a11`).
- An entry with no `:option-pack` field was skipped on import and set aside on load;
  only a blank or `nil` one was filled. It now takes the name of the source it sits
  in (`e5/source-for`): the source it is filed under, else the one source its
  sibling entries name, else Default Option Source. The loader writes it back once
  (`1f8fef2b`).
- An import entry that isn't a map crashed the missing-field fill. It is now skipped
  and listed in the import log (`09c22e11`).

## Still open

These came from reading the code, not from a failing test, and each needs a
decision:

- **`strip-export-blanks` cleans the file only.** The library keeps its blank
  fields. This is probably intended.
- **Save anyway doesn't check for an existing entry with the same key**, and replaces
  it.
- **Restore in the needs-attention panel doesn't check its storage writes.** app-db
  updates even if a write fails.

## Checked and not a bug

- **Export & Auto-Fix and the copy it holds.** When the dialog opens, it saves the
  cleaned copy to the library and holds that same copy. The page is blocked while it
  is open, so nothing in that tab can change the source in between. Re-reading the
  source on click would give the same result. Another tab writing meanwhile is
  last-save-wins, which applies to every save in the app, not just this dialog.

## Related

- [fast-browser-probes.md](fast-browser-probes.md): running probes, and which Chromium
  they launch
- [error-handling-import-validation.md](error-handling-import-validation.md): the
  import validation this builds on
