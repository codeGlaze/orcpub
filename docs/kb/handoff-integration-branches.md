# Handoff — branches for `integration` (2026-09-14, outcome recorded 2026-09-18)

## Outcome

Two of the three merged into `integration` at `1bb3eb28` after an independent review pass:
`fix/get-auth-token-undeclared` and `feat/source-tagged-keys`.

**`fix/duplicate-key-traps-its-owner` is NOT fit to merge.** Its change made `self?` mean "the item
has a key" rather than "the item is in its own slot" — and since the save derives
`key (or (:key item) …)`, that is always true for a saved item, so the `:overwrite` guard went dead
for all of them. Retarget an item's Option Source Name to a library that already answers to its key
and the item sitting there is silently destroyed, with no prompt and no undo. The old code blocked
that by accident, via the `:cross` branch.

The trap it was written for is real: with the same key in two sources, neither copy can be edited,
and minting-once means renaming is no longer a way out. Fixing it properly needs the builder to
record which `[source key]` the item was LOADED from — name is not a discriminator (a rename
changes it) and key is not either (both items have the same one). That is a design change, not a
patch. The branch is left pushed for its repro test; do not merge it as it stands.

Review also found and fixed, in `feat/source-tagged-keys` before merging:

- **a stored item with no `:key`** — older libraries do not carry one and the read path derives it
  from the name. Minting a tagged key for such an item wrote a SECOND entry and left the original
  holding the pre-edit data, with no `:former-keys` to heal it. Now an address that is already
  answering is kept.
- **a typed key was accepted blank or junk** — `name-to-kw` never returns nil for a string, so `""`
  became `:unnamed-<hash>` and `"@@@"` became `:-`, which is a keyword trap the import pipeline
  quarantines. The one place in the app that sets a key by hand now checks the same invariant.

**Fixes made on a cut branch have to come back.** The key-less-stored-item fix below was made on
`feat/source-tagged-keys` during review and never landed on `feature/grant-rows`, which carried the
bug for five days — and it then blocked the port of the save-gate work, because a naive
"take mine" would have deleted it from integration. Check both directions before porting.

Still open, recorded but not fixed: other key-minting paths ignore a source's `:abbreviation`
(`relocate-content`, import conflict resolution), `coerce-invalid-names` re-derives untagged keys
for every item in a repaired source, and an import conflict "rename" can now be a no-op because the
suggested key equals the one already minted.

For the agent merging and testing them. All three are cut from `integration` at `18c28652`, all
three were built and gated in a worktree of that commit, not on `feature/grant-rows`.

The KB lives on `feature/grant-rows` only — `integration` carries no `docs/kb/`. Read a page with
`git show feature/grant-rows:docs/kb/<name>.md`.

## The branches, and the order

| branch | what | independent? |
|---|---|---|
| `fix/get-auth-token-undeclared` | one namespace qualifier + its test | yes |
| `fix/duplicate-key-traps-its-owner` | `save-collision` stops refusing an item's own slot | yes |
| `feat/source-tagged-keys` | minted keys carry the source's tag; the key control; the source's own tag | **cut from the duplicate-key fix** |

The feature branch is stacked on the duplicate-key fix because both touch
`homebrew_save_lifecycle_test.cljs`. Merge the fix first and the feature is clean; merge the
feature alone and the fix comes with it, which is harmless. Merging either fix alone is fine.

## Gates, as measured on each branch

| | JVM (`lein test`) | cljs | browser |
|---|---|---|---|
| `fix/get-auth-token-undeclared` | 493 / 3816 | 374 / 1743 | — |
| `fix/duplicate-key-traps-its-owner` | 493 / 3816 | 374 / 1744 | — |
| `feat/source-tagged-keys` | 493 / 3816 | 382 / 1775 | `change-item-key` 11/11, `source-key-tag` 11/11 |

**Running the cljs suite:** `lein fig:test`, then `node test/e2e/cljs-harness.js`.
**GOTCHA that costs an afternoon:** the harness must serve JS as `charset=utf-8`. A classic script
with no charset is decoded as windows-1252, `orcpub/common.js`'s accent-range regex fails to parse,
`orcpub.common` is then undefined, and ~270 tests error with `Cannot read properties of undefined` —
nothing wrong with the code. The harness on these branches already sends it; if a mass of
undefined-namespace errors ever appears, check the Content-Type before believing any of it.

**Running the browser checks:** `lein fig:build && lein garden once && lein e2e-server`, then
`node test/e2e/source-key-tag.js`. `test/e2e/lib.js` ships with the feature branch.

## What a reviewer has to consciously accept

`feat/source-tagged-keys` **changes what every new key looks like** — `:stone-elf-trcs`, not
`:stone-elf`. That is a product decision, not a bugfix, and it is the reason the branch is separate.

- Nothing already stored moves. An item that has a key is untouched, so this is not a migration
  (D9). Old and new content in one library will have different key shapes, and duplicates between
  them stay possible.
- **Deleting the tag is load-bearing**: it is how somebody overrides an SRD item on purpose. That is
  why the builder's key control ships in the same branch — without it, tagging makes the override
  impossible.
- The full argument, the abbreviation rule and the rejected alternatives:
  `git show feature/grant-rows:docs/kb/source-tagged-keys.md`.

## Not covered by any test — check by hand

1. **Export/import round-trip of `:abbreviation`.** The spec accepts it (`::content-keyword`,
   `::plugin` in `e5.cljc`), but nothing pushes a source carrying one through a real `.orcbrew` and
   back.
2. **A source with an existing library.** Every fixture starts empty or near-empty. Set a tag on a
   source that already holds content and confirm nothing in it shifts.

## Traps in this repo that will cost you time

- **`plugin-datalist` (the Option Source Name field) keeps the source name in a component-local atom
  that NEW does not reset.** After New the field still shows the previous source while the item has
  none, and typing the same value back fires no change event — so the save is refused for a field
  that looks filled. It cost two e2e runs to spot. Worked around with a reload in
  `source-key-tag.js`; unfixed.
- **`lein garden once` can fail while `lein fig:build` and the whole e2e suite then pass against
  stale CSS.** Check its exit code before believing a CSS change.
- **The What's New panel swallows clicks.** On `integration` it opens ~450ms after the first click on
  the document, so removing the node on arrival fixes nothing. `lib.js/dismissWhatsNew` stamps the
  seen-key that is read at boot and reloads once. Call it after every navigation in a test that also
  clears localStorage.
- **e2e scripts that predate `lib.js` call `chromium.launch()` with no `executablePath`** and cannot
  find a browser in this container. Only the scripts ported with the feature branch use
  `lib.js/findChrome`.

## When `integration` later merges back into `feature/grant-rows`

Expect conflicts in `events.cljs` and `views.cljs` — the same changes arrive from both directions.
Keep `feature/grant-rows`' side of:

- the What's New panel: it opens at LAUNCH there and stops above the cookie notice, rather than
  waiting for a click;
- `::persist-builder-wip`: it reads `content_types.cljc` there and `db/builder-wip-stores` on
  integration, because integration has no content-type registry;
- `homebrew_save_lifecycle_test.cljs`: the branch's copy is the longer one.

`docs/branch-changelog.md` is deleted on integration and kept on `feature/grant-rows`. Keep it.
