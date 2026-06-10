# Export unification handoff

Branch: `claude/fix-brave-export-bug-2Tt7j`
Last commit on this work: `1002cc5`

## Origin

User reported on Brave: `Export validation failed for Circle of the Moonlit
Veil`, console showed `Got: "{:orcpub.dnd.e5/subclasses ..."` — a string at
the root where `::e5/plugin` spec expects a map. Originally suspected as a
browser-specific bug; investigation found it was browser-agnostic.

## Verified findings (root cause)

Two distinct problems, surfaced in order:

1. **Stringification regression.** Toast "here" link
   (`events.cljs:560` and `:675`) dispatched
   `[::e5/export-plugin option-pack (str (new-plugins option-pack))]` —
   sending a stringified plugin to a validating event that expected a map.
   `(spec/valid? ::e5/plugin "{:foo ...}")` fails at root with `map?`.
   **Fixed in commit `25c4a37`** (already on branch when this work started).

2. **Asymmetric validation across four parallel export events.** Tracing
   the post-fix landscape revealed:

   | Event | Trigger | Validated? | Notes |
   |---|---|---|---|
   | `::e5/export-plugin` | Toast "here" | Yes | compact `(str plugin)` |
   | `::e5/export-plugin-pretty-print` | Row "export" button | **No** | introduced 2019, never wired to validation |
   | `::e5/export-all-plugins` | "Export All" button | Yes | compact |
   | `::e5/export-all-plugins-pretty-print` | Dev cloud-icon | No | dev escape hatch |

   When the Feb 2026 import-validation feature (`b8434a2`, `2539a0c`) wired
   `validate-before-export` into the *non*-pretty events, the pretty-print
   events were missed. Format and validation got tangled via the
   `-pretty-print` event-name suffix. The most-used export button (row)
   silently skipped validation for ~3 months.

## Key decisions (made with the user)

- **Format preserved per path**, not unified. `.orcbrew` files reach
  **3-5 MB compact** (verified with user); pretty-printing them inflates to
  ~10-20 MB and freezes the UI during `pprint`. Toast = compact (fast
  post-save), row = pretty (deliberate inspection action), Export All =
  compact, dev icon = pretty.
- **Dev cloud-icon stays as pressure-release escape hatch** — skips
  validation by user direction.
- **Bulk recovery modal** (single-plugin has it via
  `:show-export-warning-modal`, bulk doesn't): out of scope; "Export All
  refuses all if any fails" preserved.
- **Helpers, not monolith.** User pushed back on an inlined ~60-line event;
  restructured into one ~14-line router + extracted helpers so common code
  lives in one place and bespoke branches stay small.
- **Unified router with explicit flags** (`:all? :plugin :name :pretty?
  :validate? :recovery?`) so future export entry points can't silently skip
  validation — they'd have to explicitly type `:validate? false`.

## Commit on this work

**`1002cc5` — Unify export paths through `::e5/export-content` event**

Replaces the four legacy events with one router event. Extracted helpers
in `events.cljs:3609-3666`:

- `serialize-edn pretty? content` — pure.
- `save-orcbrew! file-name pretty? content` — public (testable via
  `with-redefs`); only side effect.
- `log-warnings! label warnings` — console-only.
- `single-export-fx opts` — returns re-frame fx map; handles valid /
  missing-required-with-recovery / invalid branches.
- `bulk-export-fx plugins opts` — all-or-nothing validation,
  `"all-content".orcbrew` filename.

Router at `events.cljs:3673-3685` merges
`{:validate? true :recovery? true :pretty? false}` defaults over raw opts,
routes on `(not validate?)` / `all?` / else.

Updated `:show-export-warning-modal` (`events.cljs:3690`) to capture full
`:opts`, and `:export-anyway` (`events.cljs:3705`) to replay via
`::e5/export-content` with `:recovery? false` (prevents modal-loop if
`fill-missing-for-export` leaves residual issues).

### Caller updates

- `events.cljs:560, 675` — toast "here" links.
- `views.cljs:1086` — dev cloud-icon.
- `views.cljs:7710` — row "export" button.
- `views.cljs:7738` — "Export All" button.

### Tests added

`test/cljs/orcpub/dnd/e5/events_test.cljs:191-300` (8 deftests): router
dispatches to right branch, `:validate? false` bypasses, `:recovery? false`
skips modal, defaults validate, bulk all-or-nothing. All use
`with-redefs events/save-orcbrew!` to capture calls.

## Verification status

**Done.** Code changes pushed. Structural diff reviewed; old event
references confirmed gone (`grep` returns nothing). `:export-warning`
subscription at `subs.cljs:1423` confirmed intact. `export-warning-modal`
view (`conflict_resolution.cljs:150`) only destructures
`:active? :name :issues :warnings` — unaffected by the new `:opts` key on
the warning map.

**Not done — needs the next agent / human.**

- `lein test` and `clj-kondo lint` — neither installed in the local
  sandbox; **CI on the PR will run them**.
- **End-to-end browser verification** of all five paths:
  1. Row export, valid plugin → pretty-printed download, no console errors.
  2. Row export, missing required field → modal opens → "Export Anyway" →
     pretty-printed file with placeholders.
  3. Toast link, valid plugin → compact download.
  4. Toast link, missing required field → modal → "Export Anyway" → compact
     file with placeholders.
  5. Export All → all-or-nothing behavior unchanged.
  6. Dev cloud-icon → pretty `all-content.orcbrew`, no validation.

## Where it's going

If CI passes and browser verification holds, ready to merge. Explicit
out-of-scope followups for a separate "enhance-exports" branch:

- Bulk modal recovery for "Export All" (would slot in as a `:recovery? true`
  bulk branch).
- `pprint` performance on multi-MB plugins (web-worker / streaming writer).
- Spec for the export options map.
- File-name sanitization for slashes / unicode in plugin names.
- Collapsing `serialize-edn` + the `(pprint/pprint)` perf risk into a
  single async serializer if web-worker work lands.

## Files to read first as next agent

- `src/cljs/orcpub/dnd/e5/events.cljs:3602-3713` — all the new export code.
- `src/cljs/orcpub/dnd/e5/import_validation.cljs:670` —
  `validate-before-export` (already-shared validation, untouched).
- `src/cljs/orcpub/dnd/e5/views/conflict_resolution.cljs:150` — modal that
  the warning dispatches feed.
- `test/cljs/orcpub/dnd/e5/events_test.cljs:191-300` — test coverage for
  the router.
