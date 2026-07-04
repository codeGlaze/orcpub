# Homebrew Data Preservation & Remediation — Roadmap

> Goal: bad homebrew data — whether created in the builder or loaded from storage
> — is never destroyed. It is preserved, surfaced, and recoverable, and the user
> can always get it out even when it's imperfect.
> Companion to `docs/HOMEBREW_DATA_LOSS.md` (findings) and the "localStorage
> corrupt data persistence" item in `docs/TODO.md`.

---

## Problems (pinned)

Two distinct problem sets. They are **parallel** — neither blocks the other —
and the roadmap must close both.

### Origin — what started the branch (builder boundary)
User created a custom class: visible in the builder, **never appeared in My
Content**, **never exported**, errors flagged, and they were afraid to refresh in
case it was thrown away. Net effect: **paralysis** — couldn't move it forward,
couldn't safely leave it.

| ID | Problem | Status |
|----|---------|--------|
| O1 | Save-validation rejection strands the item in the builder; it never reaches `:plugins`, so it's invisible in My Content and unexportable. | **Fixed** (A3) |
| O2 | No way to get imperfect content out — normal export needs valid `:plugins` data; WIP that won't validate can't be exported at all. | **Fixed** (A1) |
| O3 | Builder WIP is lost on refresh — the builder-item is written to `localStorage["class"]` but **never restored on boot** (the magic-item builder restores; class does not). | **Fixed** (A2) |
| O4 | App forces a manual refresh for state that should update in the background — My Content doesn't reactively reflect saves (suspected; likely overlaps `claude/fix-custom-items-disappearing`). | **Fixed** (A4 — was the O1 stuck-in-builder issue) |

### Surfaced — found while investigating (storage/export boundary)
"The whole system throws the good out with the bad, and doesn't back up the bad
so it can be fixed."

| ID | Problem | Status |
|----|---------|--------|
| S1 | Export banner dispatched `(str plugin)` → string fails `map?` → the original error. | **Fixed** (the post-save "export here" links in `events.cljs` pass the plugin map, not its string) |
| S2 | Loader was all-or-nothing — one invalid source discarded the **whole** library on reload. | **Fixed** (resilient loader, `db.cljs`) |
| S3 | Bad data was discarded, not preserved for repair. | **Fixed** (B2 — quarantine, surface, repair-and-merge-back) |
| S4 | `set-item` swallows quota failures silently → memory-only data lost on refresh. | **Fixed** (B3) |
| S5 | Save-time vs load-time spec drift (valid-to-save, invalid-to-load trap door). | **Fixed** (B4) |
| S6 | cljs suite not gated in CI; 11 pre-existing failures; stale accent/`count-non-ascii` tests. | **Fixed** (B5) |

---

## Definition of Done (project rule)
**A phase is done when (1) a Playwright e2e against the real dev frontend
(`e2e/scenarios/`) fails on the bug and passes on the fix, AND (2) a tidying pass
has removed any code the change made redundant or orphaned.** Unit/JVM tests are
for fast iteration and regression — not proof. (Harness: `e2e/README.md`.)

### Tidying pass (part of every phase's DoD)
No change is "done" until we've swept up after it:
- Remove dead/superseded code the change orphaned (old branches, replaced helpers,
  now-unused vars/requires). Verify with `lein lint` (clj-kondo, unused-var
  rules) + targeted `grep` for callers before deleting.
- Collapse duplicate logic the change introduced or exposed (e.g. the existing
  `*-with-log` vs non-log pairs flagged in `docs/orcbrew-validation-followups.md`).
- Leave a one-line note in the commit for anything intentionally kept-but-unused.


## Principles
- **Never destroy data** — only park it (named, labeled) and make it recoverable.
- **Always offer an out** — the user can export imperfect content at any time.
- **Leverage existing remediations** before writing new ones: `clean-data`,
  `fill-missing-for-export`, `fix-empty-option-pack`, `dedup-options-in-import`,
  the auto-fill export modal (`orcbrew_validation.cljs`). Extend, don't duplicate.
- **Build the data model before the UI on top of it.**

## Quarantine data model (decided)
`plugins:rejected` becomes a map keyed by **source name** (`{name → bad-data}`),
merged on each bad load (latest-wins per name): one copy per distinct broken
source, bounded, no clobbering, no timestamp buildup. It lives in its own key
that the load path (`localStorage["plugins"]`) and export path (app-db `:plugins`)
never read — so it cannot leak into a normal `.orcbrew` export.

## Cross-branch note: `claude/fix-brave-export-bug-2Tt7j` is **superseded**
That branch fixed the same `str` bug we did and then unified exports behind an
`::e5/export-content` router — but its own handoff concluded **develop's version
is better** (the router dropped develop's inline editing + multi-plugin recovery).
So we do **not** adopt the router; we stay on develop's richer export/modal/
remediation base. The two things worth keeping were absorbed:
- the pure **`serialize-orcbrew`** split (compact vs pretty), done; and
- the **principle** that an export path must *explicitly* opt out of validation
  (our draft/emergency exports already do). No code reshape of the high-drift
  export area beyond that.

`claude/fix-orcbrew-errors-bLkFJ` is **already in develop** (squash-merged: the
modal, inline-edit remediation, conflict-resolution, the `import_validation →
orcbrew_validation` rename) — it's our foundation, not a conflict.

### `claude/zen-wright-04xhdz` (content-extensibility / generated builder) — **converge, don't fork**
That branch independently hit the same toggle-nil bug family (its tip commit is a
"nil-immune `:boolean` field type") and fixed it from the *opposite end* — a
leaf-level `toggle-next` = `(not (true? v))` that reads defensively (garbage → off)
and adds `boolean?` validation in its shared field-value pred. Ours (B6) fixed the
*structure* end: `common/toggle-flag`/`toggle-in` refuse to collapse a map and
self-heal a stray-`false` intermediate. **Neither fix alone is complete**, and the
two independently triangulated the same defect — a good sign. On merge, converge on
ONE hardened shared primitive rather than two parallel ones:
- **leaf:** defensive read (theirs) + collection-preserving (ours) →
  `(if (coll? v) v (not (true? v)))`;
- **structure:** self-healing intermediates (`toggle-in`, ours);
- **boundary:** `boolean?` in the shared field pred (theirs) + the `save ⊆ load`
  content-specs registry (B4, ours).
- **still exposed on their side:** the ~20 hand-rolled `(update-in path not)`
  content-prop toggles are unconverted (their widget only covers *generated*
  boolean fields). Route them through the shared helper; don't leave the "not
  can't make nil" framing to green-light them (a `not` whose path lands on a map
  still collapses it → read-nil + crash).

---

## Phase 0 — e2e harness ✅ DONE
Committed `e2e/` (server, scenarios, headless cljs runner). 4/4 green.

## Track A — Origin: builder content preservation
*Closes the problem that started the branch. Ordered by paralysis relief.*

- **A1 — Export imperfect WIP (paralysis-breaker)** [O2] ✅ DONE: builder-level
  "Export draft" action dumps the in-progress builder-item (not `:plugins`) as a
  re-importable `.orcbrew` with no validation. `reg-export-draft` (`events.cljs`)
  + optional `export-draft-event` on `builder-page` (`views.cljs`), wired for the
  Class builder. *e2e:* `e2e/scenarios/a1-export-draft.spec.ts` — types a name-only
  (unsaved, no option-pack) class → Export draft → file contains the WIP under
  `:orcpub.dnd.e5/classes`. Green. Also proven (`a1-roundtrip-and-errors.spec.ts`):
  the draft **re-imports** into `:plugins`, and a draft still **exports after the
  save is flagged invalid** (the rejected save correctly stays out of `:plugins`).
  - Follow-up ✅ DONE: wired into EVERY homebrew builder. `events/builder-drafts`
    (one table: save-event → [builder-item, content-type]) registers the draft
    event for all of them; `events/draft-event-for` derives the event key from the
    save event, and `builder-page` derives the same key — so the button appears on
    all builders with no per-builder wiring. The class's bespoke `export-class-draft`
    arg was removed. *e2e:* `a1-export-draft-all-builders.spec.ts`
    (Spell/Race/Feat/Background/Subclass).
- **A2 — Refresh safety** [O3] ✅ DONE: restore the class builder-item on boot
  (cofx `:local-store-class`, validated only as `map?` so invalid WIP isn't
  dropped; wired into `:initialize-db`). Parity with the magic-item builder.
  *e2e:* `e2e/scenarios/a2-refresh-safety.spec.ts` — type a class name → reload →
  it's still there. Green (red before the fix: field was empty after reload).
  - Follow-up ✅ DONE: generalized to every builder. The persist side was already
    wired per builder (the `->local-store` interceptors); added `db/builder-wip-stores`
    (localStorage key → builder-item app-db key) + one `:local-store-builder-items`
    cofx that restores them all, replacing the class-only `:local-store-class`
    special-case. *e2e:* `a2-refresh-safety-all-builders.spec.ts`
    (Spell/Feat/Race/Subclass WIP survives a reload).
- **A3 — Let imperfect content move forward** [O1] ✅ DONE: the save-failure
  banner now offers **"Save anyway with placeholders"** — `reg-save-homebrew`
  registers a companion `<save-event>-anyway` event that reuses
  `fill-all-missing-fields` and force-fills a placeholder option source
  ("Unsorted Homebrew") + key, landing the item in My Content (flagged) instead
  of stranding it in the builder. *e2e:* `e2e/scenarios/a3-save-anyway.spec.ts`
  — name-only class → Save fails → Save anyway → it's in `:plugins`. Green.
  - Follow-up ✅ DONE: `reg-save-homebrew` already generates the anyway event for
    all 12 builders it registers; the only gap was the standalone Selection handler,
    which now has an explicit `::selections5e/save-selection-anyway` (mirrors the
    reg-save-homebrew remediation) and passes it to the failure banner. *e2e:*
    `a3-save-anyway-selection.spec.ts` (name-only selection → Save anyway → lands
    under "Unsorted Homebrew").
- **A4 — Reactive propagation** [O4] ✅ DONE (investigated): **not a separate
  bug.** My Content renders `@(subscribe [::e5/plugins])`, and `::e5/plugins`
  (`spell_subs.cljs:39`) is a plain reactive sub over `(:plugins db)` with no
  init-only cache between — so a save reflects in My Content via in-app
  navigation, with **no full reload**. The original "not in My Content / had to
  refresh" was the stuck-in-builder problem (O1), fixed by A1/A3. *e2e:*
  `e2e/scenarios/a4-reactive-my-content.spec.ts` — Save anyway → SPA route to My
  Content (no reload) → the source shows. Green.
  - Distinct, still-open: custom magic-items/equipment missing from the
    **character-builder dropdowns** (template-cache-derived, not `:plugins`) is a
    real reactivity issue — but that's the `claude/fix-custom-items-disappearing`
    surface, not My Content. Left to that branch.
- **A5 — Export auto-fix applies backward** ✅ DONE: when the export warning modal
  flags a missing required field, the user fills what they can and "Export &
  Auto-Fix". That fix used to go ONLY into the exported file — the live library
  silently kept the un-fixed version, so the next export re-prompted for the same
  field. Now the same auto-fixed data is persisted to `:plugins` (+
  `plugins->local-store`), so My Content matches the file. That includes the
  dummy-fill for blank fields (a blank `:name` → `[Missing Name]`) — it's a
  self-labeling placeholder, flagged in the toast/log for the user to replace,
  consistent with A3 "Save anyway with placeholders" (persisting the blanks too is
  the point: otherwise the library and file diverge on exactly the fields the
  auto-fix touched). *e2e:* `export-autofix-applies-backward.spec.ts` — a filled
  `:level` AND a dummy-filled blank `:name` both land in `:plugins`, matching the
  exported file.

## Track B — Surfaced: don't throw good out with bad; back up the bad
*Runs in parallel with Track A.*

- **B1 — Resilient loader** [S2] ✅ DONE (quarantine, don't nuke).
- **B2 — Quarantine-for-repair** [S3]: name-keyed `plugins:rejected` map →
  surface (notice + panel with humanized reason) → fix-and-merge-back into
  `:plugins`. **Broken into the sub-roadmap below** — the reuse map and the full
  foundation audit live in `docs/HOMEBREW_B2_QUARANTINE_PLAN.md`.
- **B3 — Storage robustness** [S4]: surface `set-item` quota failures (warn +
  offer raw export) ✅ DONE; ~~compress the plugins blob~~ (deferred — an
  optimization, not a data-loss fix). `set-item` now returns a success boolean
  and `plugins->local-store` dispatches `::e5/plugins-save-failed` on a failed
  write, which surfaces a loud error + a "Download a full backup now" link
  (`::e5/emergency-export-raw`). Landed alongside B2.0/B2.1 as the "storage-layer
  firm-up" slice.
  - *cljs:* `db_test.cljs` — `set-item` returns true/false (false when the write
    throws). *e2e:* `quota-failure.spec.ts` — writes to `plugins` throw a
    `QuotaExceededError` → error banner + backup offer shown.
    Verified **red before / green after**. Full e2e 22/22.
- **B4 — Spec alignment + drift guard** [S5] ✅ DONE: single `content-type → spec`
  registry used at save and load (loose fallback) + generative `save ⊆ load` test.
  - **Registry:** `src/cljc/orcpub/dnd/e5/content_specs.cljc` — one map,
    `content-type → strict save spec` (all 13 builder types), plus `load-item-spec`
    (the loose floor `::e5/homebrew-item`), `save-spec-for`, and `valid-for-load?`.
    This replaced FOUR scattered copies of the type→spec mapping (the per-call
    `reg-save-homebrew` args, the load predicate inline in `db.cljs`, and the
    keyword-audit test table).
  - **Used at save:** `reg-save-homebrew` (events.cljs) now derives the spec from
    the content type via `content-specs/save-spec-for` — the per-call spec arg is
    gone; the bespoke Selection handler routes through it too. **Used at load:**
    `db.cljs` injects `content-specs/valid-for-load?` into `salvage-plugins` (same
    loose behavior, now sourced from the registry). Load stays deliberately loose
    = the backward-compat guarantee (never quarantine real, already-saved content).
  - *cljs/jvm:* `content_specs_test.clj` — registry integrity (1:1 with the bases,
    every value a registered spec), each canonical base is both save-valid AND
    load-valid, and a **generative** `save ⊆ load` property (test.check, 300 cases
    × 13 types) that goes RED if load ever grows stricter than save (verified red
    when `load-item-spec` was pointed at a strict spec: 24 failures; green on
    revert). `keyword_audit_test` gained a guard asserting its table matches the
    registry. *DoD met:* drift test red-on-divergence ✓; real ~700KB pak survives
    reload with 0 quarantine (`plugin_load_test`, now through the registry
    predicate) ✓; existing `boot-resilience`/`resilient-loader` e2e still green.
- **B5 — Hygiene** [S6] ✅ DONE: triaged + greened the 11 pre-existing cljs
  failures (cljs suite now **0 failures, 0 errors**). Kept the cljs unit suite +
  Playwright e2e as the **local/pre-merge** gate; CI stays JVM `lein test` + lint
  + cljs **compile** (`fig:build`) — browser-based suites are NOT run in CI
  (decision recorded). **Expectation-vs-guard discipline** (see
  `docs/HOMEBREW_TESTING_NOTES.md`): a failing guard means fix the CODE, not the
  test. 3 of 6 were guards catching **real bugs**:
  - [x] `test-count-non-ascii` — **REAL BUG** (code): `(int char)` is 0 in cljs;
    fixed `count-non-ascii` to `.charCodeAt`. Guard kept.
  - [x] `test-normalize-text-in-data-recursive` — **STALE** (test): corrected to
    assert accents are preserved (typographic punctuation still normalized).
  - [x] `test-apply-key-renames-batch` — **STALE scaffolding** (test): keys
    `:old-key`/`:new-key` → `:from`/`:to` (matches the real caller); guard now
    actually exercises the rename + cross-ref rewrite, and passes.
  - [x] `test-dedup-options-in-import-full-pipeline` — **REAL BUG** (code):
    `dedup-options-in-item` only walked nested `:selections`, so a top-level
    homebrew Selection's own `:options` were never deduped. Fixed to handle both.
  - [x] `user-stale-user-no-token-still-guarded` — **mis-written guard** (test):
    rewrote to assert the real intent (no `http/get` when tokenless), since the
    `:user` sub is a passthrough.
  - [x] `save-character-rejects-missing-abilities` — **REAL crash** (code): an
    empty `{}` template reached `entity/build` (null-fn `.call`); autosave guard
    extended from nil → nil-or-empty. (This one was hidden behind the runner's
    filter, which dropped `ERROR in` lines — runner fixed too.)

- **B6 — Nil/blank hygiene at the root** ✅ DONE. Repetitive toggling ("click a
  lot") produced the "true/false/nil" corruption. Root cause: a boolean
  `(update-in path not)` toggle whose path lands on a MAP does `(not map)` =
  `false`, nuking the map (data loss); reads under it then return `nil`
  (`(get false :k)`), and the next child toggle does `(assoc false …)` → crash.
  Toggling a flag on→off also leaves `{:skill false}` false-cruft that exports
  forever.
  - **Fix:** content-prop toggles use `common/toggle-in`/`toggle-flag` instead of
    bare `not` — they never collapse a map, and **self-heal** a stray `false`
    intermediate (legacy collapsed data) into a fresh map on the next click.
    `orcbrew-val/strip-export-blanks` drops meaningless blanks (false/nil/empty map
    values) on **normal** exports only (raw/draft/emergency untouched).
  - **Blank audit** (`docs/HOMEBREW_BLANK_AUDIT.md`): the one meaningful `false`
    (the "first-class-only" multiclass rule) is stored as `[prof-kw bool]` **pairs**,
    not `{key false}` map values, so stripping false/nil/empty map values is safe.
    Meaningful-nil keys kept: `:spell-list-kw :ability :class-key`. The import
    cleaner (`clean-nil-in-map-with-log`) is left as-is; the toggle layer was
    verified to emit clean `true/false/[]` (guarded by `event_handlers_test.cljc`).
  - *Tests:* `toggle_stress_test.cljs` (reproduces + pins the fix + self-heal),
    `orcbrew_validation_test.cljs` (strip round-trip-safe), e2e
    `export-strip-blanks.spec.ts`.
  - **Real-content validation** (survey of 147 community `.orcbrew` files): every
    file loads with 0 quarantine; the only malformed data is cruft in non-required
    fields — false-cruft ×221 (spells storing every off-class as `false`, ~1000 in
    one file), nil-val ×70, nil-key ×53 (`{nil nil}`). It passes the save spec
    (`s/keys` ignores extra keys), so only `strip-export-blanks` removes it (import
    auto-clean already drops the nils). **Zero** keyword-traps or missing
    option-packs in published content — so B2 quarantine guards builder mistakes,
    not published paks. Distilled into fixtures (`cruft-shapes.orcbrew`,
    `broken-content.orcbrew`) and proven end-to-end:
    `roundtrip-import-export-use.spec.ts` (import → export ideal → re-import → build
    a character with it) and `broken-content-survives-use.spec.ts` (thoroughly
    broken content loads, degrades gracefully, never crashes).
  - **B7 — keyword-trap on IMPORT is surfaced + repaired (not silently hidden) ✅ DONE:**
    an item with a key that doesn't start with a letter (`:9-lives`, from a name
    like "9 Lives") used to **bypass save-time validation when it arrived via
    IMPORT** (progressive import only requires `:option-pack`), land in `:plugins`,
    and then its homebrew classes would **silently never appear** in the character
    builder — no error, no explanation (the app was otherwise fine). A dumb user
    just complains their class is missing. **Fix** (`events.cljs` `::e5/import-plugin`
    success branch): before storing, a source with a keyword-trap item (detected by
    `e5/invalid-keyed-items`) is routed to the **same** quarantine the boot loader
    uses (`plugins:rejected` + reactive `:quarantined-plugins`) with a plain-English
    message, so the **existing** rename→rekey→restore repair UI surfaces it — not a
    parallel mechanism. Deliberately narrow: only the keyword-trap is quarantined;
    other imperfections (missing option-pack, incomplete WIP) are still auto-cleaned
    and land, so the export-draft / re-import escape hatch (A1) keeps working.
    *Fixture:* `test/fixtures/keyword-trap.orcbrew` (grab-able). *e2e:*
    `import-keyword-trap-repair.spec.ts` — import → surfaced+quarantined (NOT in
    `:plugins`) → rename "9 Lives"→"Nine Lives" in My Content → source restored with
    a valid key, quarantine cleared, and the class now selectable + builds. Full
    suites green after; the A1 draft round-trip regression (from an over-broad first
    cut) is what caught the need to narrow the trigger.

### B2 sub-roadmap — quarantine-for-repair
*Firm the foundation before stacking the feature on it. Phases are ordered so
each is an independently-shippable, DoD-complete slice; the first two are pure
data-layer hardening that pay off even if the UI slips. Findings (F1–F7) and the
reuse map are detailed in `docs/HOMEBREW_B2_QUARANTINE_PLAN.md`.*

**Foundation (firm-up first):**
- **B2.0 — Don't destroy unreadable plugin slots** [F5, HIGH] ✅ DONE —
  `get-local-storage-item` (`db.cljs`) now preserves the homebrew slots
  (`plugins`/`plugins:rejected`, via `preserve-on-unreadable-keys`)
  on a parse failure: it copies the raw bytes to a `<key>:corrupt` slot and
  clears the active slot (so a poison value can't brick boot) instead of deleting
  the data. Non-homebrew slots keep the old remove-on-unreadable behavior.
  - *cljs:* `test/cljs/orcpub/dnd/e5/db_test.cljs` — truncated blob preserved to
    `:corrupt` + active slot cleared for each plugin slot; non-homebrew slot still
    removed; clean read untouched. *e2e:* `boot-resilience.spec.ts` "unreadable
    (truncated) storage" — app boots, `plugins:corrupt` holds the raw, `plugins`
    cleared. Verified **red before / green after**. Full e2e 20/20.
- **B2.1 — Name-keyed, reconciled, self-clearing `plugins:rejected`** [F4, MED]
  ✅ DONE — the loader now maintains a name-keyed quarantine map via the pure
  `e5/reconcile-rejected` (`e5.cljc`): it MERGES this load's rejected sources with
  the already-quarantined map (latest-wins per name, so records survive a save
  that drops the bad source from `:plugins`), DROPS any entry whose source is now
  present-and-valid (in `kept`), and the loader REMOVES the key when the map
  empties. A parsed-but-not-a-map blob now routes to the `:corrupt` slot (B2.0)
  instead of clobbering `:rejected`.
  - *JVM:* `e5_test.clj` — accumulate / latest-wins / drops-repaired / empty +
    non-map-old (5 tests). *e2e:* `resilient-loader.spec.ts` "quarantine
    accumulates … and clears a repaired one" (poll-based, no fixed timeouts) +
    `boot-resilience.spec.ts` updated for the `:corrupt` routing. Full e2e 21/21.

**Build (on the firmed foundation):**
- **B2.2 — Re-key primitive** [F2, HIGH] ✅ DONE — pure `e5/rekey-plugin` /
  `rekey-content-group` (`e5.cljc`): re-keys ONLY items whose current key is
  invalid (the keyword trap) to the keyword derived from their corrected `:name`,
  syncs the `:key` field, resolves collisions with a numeric suffix (no drops),
  and leaves valid keys + name-less items untouched (no disturbed references).
  - *JVM:* `e5_test.clj` — fixes-trap / leaves-valid / collision-suffixes /
    no-name-keeps-key / plugin-passthrough (5 tests).
- **B2.3 — Persist-to-library repair event** [F1, HIGH] ✅ DONE — at the time, the
  export auto-fix only rewrote the exported *file* (since fixed by A5); this
  PERSISTS. `::e5/repair-quarantined-source`
  applies the user's edits (`orcbrew-val/apply-user-edits-to-plugin`) + B2.2
  re-key, validates `::e5/plugin`, and on success lands the source in `:plugins`
  (+ `plugins->local-store`) AND removes it from `plugins:rejected`, atomically.
  A still-invalid fix persists nothing and reports what's wrong.
  - *cljs:* `events_test.cljs` — lands-and-clears / rejects-still-invalid /
    missing-is-noop (3 integration tests, real localStorage + app-db). e2e for
    the full UI loop comes with B2.4/B2.5.
- **B2.4 — Surface + live-validated repair UI** [F3, MED] ✅ DONE — a
  `quarantine-panel` at the top of My Content (`views.cljs`) surfaces quarantined
  sources (loaded into app-db `:quarantined-plugins` via the `::e5/rejected-plugins`
  cofx + `::e5/quarantined-plugins` sub) with a humanized reason. For keyword-trap
  items it shows **live-validated** name inputs (`common/starts-with-letter?`),
  a "Repair & Restore" action (disabled until valid) wired to
  `::e5/repair-quarantined-source`, and a per-source "Export raw" hatch
  (`::e5/export-quarantined-raw`, which reaches `:quarantined-plugins`). Pure
  `e5/invalid-keyed-items` identifies the repairable items.
- **B2.5 — Full-loop e2e** [F7, MED] ✅ DONE — `e2e/scenarios/quarantine-repair.spec.ts`:
  two sources quarantined (A = keyword trap, B = missing option-pack) → both
  surfaced → A's bad name flagged live, fixed, "Repair & Restore" → A re-keyed
  into `:plugins` and removed from quarantine, B remains → reload, state holds.
  Full e2e 23/23.

## Meaningful errors — keyword-field audit ✅ DONE
The original bug was *non-obvious* because a name that LOOKS fine
(`"5th Edition"`, `"9 Lives"`) silently derives an invalid keyword
(`common/name-to-kw` does **not** sanitise a leading non-letter), which then
fails validation pointing vaguely at "Name".
- **Coverage proven watertight.** `test/clj/orcpub/dnd/e5/keyword_audit_test.clj`
  enumerates **every** homebrew content type (class, subclass, invocation, boon,
  race, subrace, feat, background, language, monster, encounter, selection,
  spell) × a battery of tricky names (leading digit/dash/dot/space/symbol,
  accented + non-latin) and asserts each spec rejects them while a clean name
  still validates (245 assertions). It also pins `name-to-kw`'s exact derivation
  per input so the failure mode is documented in one place.
- **Decision (name-to-kw stays pure).** We do **not** silently strip the leading
  non-letter — strip causes silent key collisions = data loss. The spec boundary
  rejects and we surface a clear message instead.
- **Nested errors made specific.** `spec-field-problems` now derives a 1-based
  `:location` ("Option 2") from the spec `:in` path, and `builder-error-hiccup`
  renders it, so a bad name on a nested selection option reads
  *"Option 2 Name must start with a letter."* instead of a generic top-level
  "Name". *e2e:* `e2e/scenarios/nested-option-error.spec.ts` (real selection
  builder + real save handler). *cljs:* location classification + render tests in
  `events_test.cljs`.

## Escape hatches — proven in-app ✅ DONE
The two "always offer an out" paths now have frontend e2e, not just unit tests:
- **str-export fix** [S1]: the post-save *"click here to export"* link dispatched
  `::e5/export-plugin` with `(str plugin)` — a string — which failed
  `validate-before-export`'s `map?` check (the original `cljs.core/map?` error)
  and produced no export. *e2e:* `e2e/scenarios/str-export-fix.spec.ts` —
  save a valid class → click "here" → a valid `.orcbrew` map downloads. Verified
  **red-on-bug** (reintroducing the `str` wrap makes the download never fire) and
  green-on-fix.
- **emergency raw export** [S6.1 / "always offer an out"]: when a saved source
  fails the hard `::e5/plugin` spec, the export-failure banner offers *"Download
  raw backup instead"*, dumping the RAW source unvalidated. *e2e:*
  `e2e/scenarios/emergency-export.spec.ts` — a **thoroughly**-bugged source (4
  content types, each broken a different way that defeats normal export but passes
  the missing-fields check: digit/dash-leading item keys, missing/numeric
  option-pack, rich nested traits/components) → My Content "export" is refused →
  "Download raw backup instead" downloads a file that preserves **every** content
  type, invalid key, and nested field verbatim. (Picker logic also unit-tested:
  `select-emergency-export` in `events_test.cljs`.)

---

## Sequencing
- **Foundation:** Phase 0 ✅.
- **Track A** delivers the original-branch outcome; **Track B** delivers the
  surfaced data-loss fixes. They are independent — work in parallel.
- Already landed: S1, S2 (and B1). Next up: **A1** (paralysis-breaker) and **B2**
  (quarantine-for-repair).
