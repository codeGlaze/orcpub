# TODO — Tracked Issues

## Datomic transactor crashes — investigate Postgres migration

**Status:** Unverified — no recurrence observed since the report; re-measure before
acting. The analysis below is from the crashing period and has not been retested
against the current stack (the peer is on Datomic Pro now, which the analysis
predates).  
**Severity:** Critical *if it still happens* — at time of report, 3–5 crashes per
day at 2–3 min downtime each  
**Reported:** 2026-02-26  
**KB doc:** [docs/kb/datomic-crash-analysis.md](kb/datomic-crash-analysis.md)

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
- `docs/kb/datomic-crash-analysis.md` — full root cause analysis with log evidence

---

## localStorage corrupt data persistence

**Status:** Open
**Severity:** Medium
**Reported:** 2026-02-21

### Problem

When `reg-local-store-cofx` reads localStorage data that fails spec validation,
it logs a warning and ignores the data — but never removes it. The corrupt data
persists across reloads, producing `INVALID ITEM FOUND, IGNORING` on every page
load. If the user never interacts with the affected feature (to trigger an
overwrite), the corrupt data stays indefinitely.

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

- `src/cljs/orcpub/dnd/e5/db.cljs` — `reg-local-store-cofx` (line ~252)
- `src/cljs/orcpub/dnd/e5/events.cljs` — `set-combat-path-prop` nil guard
- All `*->local-store` serializers use `(str data)` / `reader/read-string`

---

## Content-library management — remaining work

**Status:** Open
**Severity:** Low — enhancements; the shipped resolution already removes the data bug
**KB doc:** [docs/kb/library-management-and-conflicts.md](kb/library-management-and-conflicts.md)

The My Content library, duplicate-key resolution, disabled-reason badges, the
mutual-exclusion UX, the opinionated (summary-first) import, the four-level
disable hierarchy, and move/copy content between sources are built and on
`feature/content-library-management` (see the KB doc for how they work). These are
the not-yet-built follow-ups, roughly in dependency order.

### Example / demo content tier

**Status:** Being built on `feature/demo-content-tier` — tracked there, not here.

A read-only example/demo tier of content, with a per-account version marker and
copy-on-edit graduation (editing an example copies it into the user's own library
so upstream updates never clobber their edits).

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
  exists: [docs/kb/plan-chunked-library-storage.md](kb/plan-chunked-library-storage.md).
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

