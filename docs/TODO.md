# TODO — Tracked Issues

## 📌 PINNED — new content-format extension (HIGH PRIORITY, blocks the new format)

The content-extensibility refactor produces content **older builds can't read**, and old
builds fail opaquely. The fix is a **new file extension** for the new format (keeps it out
of old file pickers) plus an in-file compat tag for new builds. **The extension NAME is an
open decision — being polled with the community + other developers.**

- Candidates: **`.orcbrewx`** (leading), `.orcbrewed`, `.orcgrog`.
- Placeholder until decided: the token `<NEW_EXT>` (standing in as `.orcbrewx`), referenced
  from one constant so the final name is a one-place swap.
- Full design (extension + `:orcbrew/format-version` + `:orcbrew/requires` compat tag +
  the open conversion-tag question): **[docs/kb/orcbrew-format-versioning.md](kb/orcbrew-format-versioning.md)**.
**Status:** Unverified — no recurrence observed since the report; re-measure before
acting. The analysis below is from the crashing period and has not been retested
against the current stack (the peer is on Datomic Pro now, which the analysis
predates).  
**Severity:** Critical *if it still happens* — at time of report, 3–5 crashes per
day at 2–3 min downtime each  
**Reported:** 2026-02-26

### Summary

The Datomic transactor is self-terminating multiple times daily with
`"Critical failure, cannot continue: Heartbeat failed"`. Root cause is H2
write-lock contention during memoryIndex flushes starving the heartbeat thread.
`writeConcurrency=4` amplifies the problem — H2 cannot parallelize writes.

### Immediate mitigation (low risk, config only)

Set `datomic.writeConcurrency=1` in the transactor properties file. See KB doc
for caveats.

### Permanent fix

Migrate from Datomic Free + H2 to Datomic Pro + PostgreSQL. Datomic Pro is
free under Apache 2.0 (see `docs/migration/datomic-pro.md` — peer migration
already done). What remains is the **storage backend migration**:

1. Provision PostgreSQL (Docker service or managed)
2. Run Datomic's SQL init scripts (`bin/sql/postgres-*.sql`)
3. Export data from H2 transactor with `bin/datomic backup-db`
4. Restore into Postgres transactor with `bin/datomic restore-db`
5. Update transactor properties: `storage-class=sql`, JDBC params
6. Update Docker Compose to add Postgres service and remove H2 volume

### Related

- `docker/datomic/` — transactor container and config templates
- `docs/migration/datomic-pro.md` — peer library already migrated to Pro

---

## Homebrew fighting-style authoring — never wired (refactor follow-up)

**Status:** Open — a deliberate loose end left mid-refactor.

The generic `:grant {:from :fighting-styles :choose N}` primitive works for a feat granting a
**built-in** style, but a pack **cannot author a new fighting style** — that half was never
wired:

- No `::e5/fighting-styles` plugin key, no content-type entry, no save spec, no
  `plugin-fighting-styles` merge sub.
- `template.cljc:1545-1550` hard-codes the feat's grantable pool to built-in styles only,
  with the in-code comment calling it a **"BRIDGE PROTOTYPE"** and threading the homebrew pool
  **"the follow-up wiring step."**
- `opt5e/fighting-style-option` exists and is unit-tested (`fighting_style_feat_e2e_test.cljc`,
  `fighting_style_grant_matrix_test.cljc`) but only via hand-built pools — it reaches no plugin
  content.

To finish (the shape the draconic-ancestry pool already follows): add the `::e5/fighting-styles`
content type + field-schema/spec, a `::e5/plugin-vals`-backed pool sub (built-in ++ homebrew),
thread that pool into `template.cljc` where the feat registry is built, then add a demo fighting
style to the pack with a build test. Being done on a branch cut from
`refactor/content-extensibility`.

By contrast, **homebrew draconic ancestries ARE fully wired** (`::e5/draconic-ancestries` +
`::races5e/draconic-ancestry-pool`) — the demo pack ships one (`:demo-tidal`) with a passing
build test; that's the pattern to copy here.

---

## 📌 PINNED — localStorage corrupt data persistence (HANDS OFF until deliberately scheduled)

**Do not touch this without explicit intent.** It sits in the homebrew-consistency-sensitive
storage path; a careless change risks the exact homebrew-draft loss the surrounding work
protects against. Pinned so it isn't casually "cleaned up" — schedule it deliberately.

**Status:** Open
**Severity:** Medium
**Reported:** 2026-02-21

### Problem

**Narrowed (2026-09).** The *unreadable / parse-failure* case is now handled:
`get-local-storage-item` self-heals a bare-colon empty keyword in place and
re-saves, and `handle-unreadable` copies homebrew to a `:corrupt` companion slot
(recoverable) before clearing the active slot, hard-deleting only throwaway keys.

What remains is the **parses-fine-but-fails-spec** case: when `reg-local-store-cofx`
reads data that reads back OK but doesn't match its spec, it logs "Invalid stored
item, ignoring" and drops it for the session — but never removes it, so the warning
fires on every reload and the value can't self-correct until something overwrites
that key.

**Constraint — do not just `removeItem`.** The affected keys hold real user data:
an in-progress character, user prefs, and the class / subclass / invocation / boon /
magic-item **builder drafts**. Given the homebrew-consistency work on localStorage, a
fix must follow the parse path's model — **quarantine homebrew to a `:corrupt` slot,
and delete only genuinely throwaway keys.** A blind delete would lose a homebrew
draft, which is exactly what that work protects.

(The old combat-tracker `assoc-in`-on-nil corruption vector below is a separate,
already-partially-fixed thread — kept for reference.)

Known corruption vector: `assoc-in` on `nil` builds maps with integer keys
instead of vectors. Example from combat tracker:

```clojure
(assoc-in nil [:monsters 0 :monster] :adult-gold-dragon)
;; => {:monsters {0 {:monster :adult-gold-dragon}}}  — MAP, not vector
```

This was partially fixed by guarding `set-combat-path-prop` with
`(or combat default-combat)`, but other handlers using `assoc-in` through
`path` interceptors may have the same vulnerability.

### Proposed fix

Scope cleanup by data criticality:

| Category | Examples | Action on invalid |
|----------|----------|-------------------|
| Ephemeral | combat, builder state | `.removeItem` — safe to lose |
| Rebuildable | spells, monsters | `.removeItem` — regenerated from source |
| Critical | plugins, characters, user | Quarantine: rename key to `<key>_corrupt_<timestamp>` |

This preserves recovery options for irreplaceable user data (homebrew plugins
can be 2-5MB of daily imports) while cleaning up transient state that would
otherwise stubbornly persist.

### Related

- `src/cljs/orcpub/dnd/e5/db.cljs` — `get-local-storage-item` / `handle-unreadable` /
  `reg-local-store-cofx` (line ~303–360)
- `src/cljs/orcpub/dnd/e5/events.cljs` — `set-combat-path-prop` nil guard
- All `*->local-store` serializers use `(str data)` / `reader/read-string`

---

## Content-library management — remaining work

**Status:** Open
**Severity:** Low — enhancements; the shipped resolution already removes the data bug

The My Content library, duplicate-key resolution, disabled-reason badges, the
mutual-exclusion UX, the opinionated (summary-first) import, the four-level
disable hierarchy, and move/copy content between sources are built and on
`feature/content-library-management` (see the KB doc for how they work). These are
the not-yet-built follow-ups, roughly in dependency order.

### Example / demo content tier

**Status:** Being built on `feature/demo-content-tier` — tracked there, not here.

A read-only example/demo tier of content, with a per-account version marker and
copy-on-edit graduation (editing an example copies it into the user's own library
so upstream updates never clobber their edits). **Design notes + decided approach
(copy-on-edit + provenance, not a diff) and the separate "variant rules" idea:**
[docs/kb/demo-content-tier.md](kb/demo-content-tier.md).

### Also parked (with reasons — do not lose)

- **Account backup/restore** of libraries + prefs: blocked NOT by code (the
  `share_url` codec already does gzip + fail-closed decode) but by **legal**
  (hosting user-uploaded, often copyrighted content), **database/scale** (3–5 MB ×
  every user), and standing **admin resistance**. If ever entertained: server-side
  at-rest/transport encryption, NOT end-to-end (lost key → lost backup); and
  backup-restore (last-write-wins) *before* multi-device sync (sync needs conflict
  resolution).
- **Compress localStorage plugins** with the existing gzip codec to fit more under
  the browser ceiling — no cloud, no legal exposure. Caveats: makes stored
  content opaque to inspection, and a hard cap is still needed (compression moves
  the ceiling, doesn't remove it). The ceiling is now measured rather than assumed:
  **5,177,344 characters** in Chromium, identical for ASCII and CJK fills, so it
  counts UTF-16 units and compares directly against a library's character count
  (`test/browser/localstorage_ceiling_e2e.js`).
- **Chunked per-source library storage** — *nice to have, not scheduled.* The
  library is 13 sources in memory but ONE localStorage value (measured: 2,166,081
  chars for MegaPak), so every save rewrites the whole thing and every load parses
  it back through a single blocking `read-string` (~750 ms). A full phased design
  exists.
  **Deliberately parked**: that ~750 ms is a one-time load cost, while the reported
  problem is the click loop (race/class selection), where no storage read happens
  at all. It also does not raise the ceiling. Revisit if cold load becomes the
  complaint — or fold it into an IndexedDB move, which dissolves most of the plan's
  complexity (natively per-key and async) and is the only real answer to capacity
  (~916 MB of measured origin quota vs ~5.18 M chars for localStorage).
- **Native `<select>` → custom popover**: the add-content menu uses a native
  select; adopt `port/redesign-on-refactor`'s Phase 7 custom-select popover when
  branches converge (NOT a cheap early crib — it's coupled to that branch's
  theme-token infrastructure).

## Route character images through the browser instead of fetching them server-side

**Status:** Shipped on `feature/browser-side-character-images`
**Severity:** was Medium — a broken feature for users
**Reported:** 2026-09-04 · **Built:** 2026-09-05
**Runbook:** [docs/CHARACTER-IMAGE-FETCH.md](CHARACTER-IMAGE-FETCH.md)

### What was built

The browser reads the picture and the export carries the bytes; the server's fetch
is now the fallback for a picture the browser was refused. `orcpub.image-capture`
reads it off a canvas, scales it to what the sheet prints, and hands base64 to the
export spec; `pdf/decode-image-bytes` applies the same 128 KB and 2000×2000
ceilings on arrival and reads the format from the bytes rather than from the mime
type. When no read is allowed, the builder says so and offers an upload.

Only the canvas route exists, not the `fetch` one the original plan had: the app's
CSP is `connect-src 'self'`, so `fetch` to an image host is blocked and an attempt
would log a CSP violation on every export. `img-src` allows `https:`, which is what
makes the canvas route work. Widening `connect-src` to arbitrary hosts was the
larger cost.

### The measurement, taken

Sixteen common portrait hosts, 2026-09-05. Nine let the browser read: Imgur,
Discord, Fandom, Wikimedia, ArtStation, DeviantArt, `lh3.googleusercontent.com`,
Tumblr, `raw.githubusercontent.com`. Seven do not: Pinterest, D&D Beyond, postimg,
imgbb, Flickr, Dropbox, `i.redd.it`. See the runbook for the table.

The two groups are largely complementary rather than overlapping — most of the
second group allows hotlinking, so the server fetches those. Pinterest and D&D
Beyond refuse both and are upload-or-nothing.

### What it does not settle

- **A refused host logs a CORS error in the console.** Unavoidable: any attempt to
  read a cross-origin image without the header logs one, and not trying is what
  the feature exists to stop doing.
- **The server fetch still earns its keep** — step 5 of the original plan. It now
  runs as the second tier rather than the default, but it is still there. Decide
  separately whether to keep it.
- **Measured, and the blocker was ours.** With a browser able to reach real hosts
  and real URLs in hand: Pinterest serves this server a 200 and 393 KB of JPEG,
  Wikimedia 224 KB. Neither ever blocked us. Both were refused by our own 128 KB
  ceiling, which was applied to the DOWNLOAD as well as to the document. Split in
  two — 2 MB down, 128 KB into the PDF, with fitting in between — a Pinterest
  portrait now reaches the sheet with nothing asked of the user.

  Two earlier conclusions here were drawn from invented URLs that returned S3
  `AccessDenied`, and both were wrong: "Pinterest and D&D Beyond refuse the
  server", and "header tuning does not help". Withdrawn. Nothing has been shown to
  block this server at all.

  `/image-probe` logs the host whenever it answers false, so the genuinely
  unreachable set is measured from real traffic rather than guessed. Watch it: if
  it stays empty, the paste and upload routes are dead weight.

## PDF export follow-ups

**Status:** Open
**Severity:** Low — none is a live defect
**Reported:** 2026-09-04 · **Last checked:** 2026-09-05

- **Total slot hold on images.** Bounded at roughly 40s for a character with both
  a portrait and a faction image — the two are fetched concurrently, so it is one
  image's worst case rather than two. Still generous. *(The 80s figure this
  originally quoted predated `254da03b`.)*
- ~~`safe-image-url?` runs twice per image~~ — **done** in `254da03b`. The route no
  longer pre-validates; `fetch-image` validates through `safe-image-bytes`, whose
  resolved addresses are the ones the connection is pinned to. Note the leftover:
  `safe-image-url?` now has **no production callers**, and its forty-odd SSRF tests
  exercise a wrapper nothing calls. The checks themselves still run, inside
  `validated-addresses`. Either point those tests at `validated-addresses` or drop
  the wrapper.
- ~~`create-monsters-pdf` is dead~~ — **scrubbed**, along with the
  `draw-text-from-top` helper, the `HELVETICA_OBLIQUE` font and the
  `orcpub.dnd.e5.monsters` require it was the only user of.
- ~~Not audited: whether a fetched portrait is re-encoded or downscaled~~ —
  **answered.** `draw-image-bytes!` embeds JPEG bytes as they are and decodes and
  losslessly re-encodes everything else; nothing is downscaled server-side, and
  128 KB is the only bound. Pictures read by the BROWSER are scaled to 1000px on
  the long edge before they are sent, so that path is bounded twice.

---

## Layout is chosen by user agent, not by viewport width

**Status:** Open
**Severity:** Medium — a desktop browser at phone width gets a broken hybrid
**Reported:** 2026-09-05

### Summary

The app decides desktop-vs-mobile from the USER AGENT: `user-agent/device-type`
is a Closure sniff, `:device-type` is set once in `db.cljs`, and `:mobile?`
follows it. The stylesheet, meanwhile, switches on WIDTH (`xs-query`, ≤767px).
The two disagree whenever a desktop browser is narrowed to phone width, or a
phone requests the desktop site: the desktop component tree renders into a
phone-width viewport with the mobile CSS applied to it — both builder columns
side by side, the character summary running off the right edge, the tab bar at
full size, header buttons icon-only.

A real phone is fine, because its UA says so. `test/browser/sticky_header_e2e.js`
documents the trap: a bare 390px viewport with Chrome's desktop UA renders the
hybrid, which is why that test uses a device descriptor.

### Where

- `src/cljs/orcpub/user_agent.cljs` `device-type` — the sniff
- `src/cljs/orcpub/dnd/e5/db.cljs` `:device-type` — read once at init
- `src/cljs/orcpub/dnd/e5/subs.cljs` `:mobile?` — what every view keys off
- `src/clj/orcpub/styles/core.clj` `xs-query` and the `at-media` blocks — the
  width side
- `src/cljs/orcpub/character_builder.cljs` — the largest `:mobile?` consumer
  (column layout, tooltips, tabs)

### Proposed fix

Derive `:mobile?` from `window.matchMedia("(max-width: 767px)")` — the same
breakpoint the stylesheet uses — and update it from that query's `change` event,
so the component tree and the CSS always agree and a resize re-lays the page.
Keep the UA sniff only as the value before the first `matchMedia` read, or drop
it. Touch (`hasTouch`) is a separate question from width and should not decide
layout.

### Consequences

Every `:mobile?` consumer becomes live rather than fixed at load. Anything that
caches a layout decision (the character builder's column state, tab selection)
needs to survive the flip. Test in both directions: narrow a desktop window
past the breakpoint and widen a phone-emulated one.


## Window the combobox row list, and reach the 969-option monster picker

Two related follow-ups from the Equipment combobox. Neither is urgent; both are
recorded so the measurements behind them are not re-derived.

### Windowing

`inventory-combobox` mounts every match when it opens. That is ~0.19 ms per row at
4x CPU throttle, so the 306-item Magic Weapons section costs 62 ms — about one frame
unthrottled, fine today. It scales linearly, so a 3000-item section extrapolates to
~570 ms, which is a freeze.

Mounting only the rows in view plus a buffer, updated on scroll, is the only
approach that reduces the work rather than rescheduling it. **Prefetch-then-expand
was tried and does not work** — measured 55/64/53 ms against a 62 ms baseline, pure
noise, because the deferred chunk is still one large mount. That version hit two traps: the row budget must
be able to reach every match, and it has to expand synchronously on an arrow key.

Costs to weigh: browser find-in-page stops working over unmounted rows, and the
keyboard highlight must force its row to mount before scrolling to it.

Do this when a library with a section in the low thousands actually turns up, not
before.

### The monster picker

A census of every `<select>` in the app (`test/browser/select_option_census_e2e.js`,
run against the WotC megapack) found only one picker outside Equipment that is
large:

| Page | selects | total options | biggest |
| --- | --- | --- | --- |
| combat-tracker | 4 | 972 | **969** |
| monster-builder | 37 | 807 | 36 |
| class-builder | 8 | 67 | 22 |
| magic-item-builder | 18 | 44 | 9 |
| spell-builder | 2 | 18 | 10 |

`monster-selector` (`src/cljs/orcpub/dnd/e5/views.cljs:7754`) renders all of
`::monsters/sorted-monsters` into one native `<select>` — 969 options, three times
the largest Equipment section, and used by both the combat tracker and the encounter
builder. It is the one other place that clearly wants a filtering combobox.

Everything else is under ~40 options, where a native `<select>` is the right control
and should be left alone.

The blocker is not size but semantics: `inventory-combobox` is an *add to a list*
control (it dispatches, clears the query and closes), while `monster-selector` picks
a *single current value* that must stay displayed. Generalising means parameterising
the selected-value display and the on-pick behaviour.
