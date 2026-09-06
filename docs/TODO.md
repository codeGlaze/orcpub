# TODO — Tracked Issues

## Datomic transactor crashes — investigate Postgres migration

**Status:** Open  
**Severity:** Critical — transactor crashing 3–5× per day, 2–3 min downtime each  
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

## Builder render weight — measured, not yet acted on

**Status:** Open
**Severity:** Low — noticeable, not broken. The 1 s freeze this came from is FIXED and merged.
**KB doc:** [docs/kb/memoize-antipattern-scan.md](kb/memoize-antipattern-scan.md), [docs/kb/perf-homebrew-builder-loop.md](kb/perf-homebrew-builder-loop.md)

DOM census per builder tab, mega-64 homebrew, 1280x720 (`dev-scratch` probe; see the KB):

```
tab              DOMnodes  cards  offscreen  selects  selOptions
Race                 2049     68         68        0          0
Class / Level        1515      0          0        2        162
Background           2067     72         72        0          0
Proficiencies        1384      6          4        0          0
Equipment            2558      6          6        7       1037
Spells               1276      0          0        0          0
```

(Spells and Class/Level understate: the census ran without a caster configured.)

Longest task at 4x CPU throttle: Equipment 354 ms, Race 160-197 ms. Profiled, it is one
reagent render with no app function above ~10 ms of self time — **render volume, nothing to
cache**. Three separate problems, in the order worth doing:

1. **Per-card DOM cost.** ~2500 nodes should not cost 354 ms. Each option card is a nested
   tree (checkbox, icon, name, edit link, info button). Trimming it helps every tab at once
   and is far smaller than a virtual scroller. Do this before reaching for virtualisation.
2. **1037 `<option>` elements across 7 native selects on Equipment.** Native selects cannot
   be virtualised — the browser owns that list. The fix is a searchable picker; see the
   entry below.
3. **~70 off-screen option cards on Race and Background** (100% below the fold at 720px).
   This is also what first paint pays for, Race being the landing tab. Virtualisation is the
   lever, and it is the invasive one: needs a scroll container, measured row heights, and it
   breaks in-page find. Only if 1 leaves it slow.

### The searchable picker already exists on a branch — grabbing it is cheaper than it looks

`port/redesign-on-refactor` adds `option_menu_views.cljs` (610 lines: search box, A-Z
grouping, selected-chips tray, three layouts), `option_grouping.cljs` and `themes.cljs`
(91 lines). All three are **pure additions** — no conflict risk in taking them. Its
`views.cljs` change is a net *reduction* (-1483/+908) because it replaces inline menu code
with calls to the shared component.

So the earlier "wait for convergence, it is coupled to theme infrastructure" note
overstated the coupling. What taking it early actually costs: our own wiring for the seven
Equipment selects would conflict with that branch's `views.cljs` rewrite when it lands.
Bounded and understandable, not a blocker.

**VERIFIED 2026-09-06:** the three files compile standalone against integration — zero
errors, zero warnings naming them, and zero warnings in the whole build. Checked by copying
them in, adding a temporary require in `web/cljs/orcpub/core.cljs` to force compilation
(unreferenced namespaces are not compiled otherwise), running `lein fig:build`, then
reverting. Their entire orcpub dependency surface is `orcpub.components` and
`orcpub.dnd.e5.db`, both already present; `option_grouping` and `themes` require nothing
from orcpub.

So taking the picker early is a real option. What it costs: our own wiring for the seven
Equipment selects would conflict with that branch's `views.cljs` rewrite when it lands —
one file, with the shared component identical on both sides. What it buys: search and A–Z
grouping over 1037 option elements, which is the Equipment tab's dominant cost and a UX
improvement independent of performance.


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

**Status:** Open — nothing built  
**Severity:** Medium — a broken feature for users, and the last unauthenticated
outbound fetch the server makes  
**Reported:** 2026-09-04  
**KB doc:** [docs/kb/pdf-form-techniques.md](kb/pdf-form-techniques.md) (image guard sections)  
**Runbook:** [docs/CHARACTER-IMAGE-FETCH.md](CHARACTER-IMAGE-FETCH.md)

### Summary

A character's portrait is stored as a URL and fetched **by the server** at export
time. Hosts that block hotlinking block it on Referer and datacenter IP, so the
browser's own request succeeds and the server's does not: the thumbnail shows in
the builder and the PDF comes out blank. The server fetch is also the only place
the app makes an outbound request to a caller-supplied address, which is why
`safe-image-url?` and its tests exist at all.

### What exists

Nothing sends image bytes to the server, on any branch. Two adjacent pieces:

- `claude/image-poisoning-spa-1dkpva` — a standalone SPA under `image-shield/`,
  four files, wired to nothing. It contains working browser-side resize and
  recompress: `createObjectURL` → `drawImage` → `getImageData` → `toBlob`.
- `claude/character-portrait-generator-hOutO` — paper-doll compositor, v1 MVP.
  Stores the layer CHOICES on the character, produces no bytes. **It cannot reach
  the PDF at all**: the export understands only `image-url`, so a character with a
  composited portrait exports with no picture. This work unblocks that branch.

### The constraint, and why it does not block the feature

A cross-origin image with no `Access-Control-Allow-Origin` cannot be read by the
browser — `fetch` in no-cors mode gives an opaque response and `<img>` + canvas
taints. That is a real browser boundary.

It does not settle the question, because the two populations barely overlap.
Hotlink bans are Referer- and IP-based; CORS headers are a separate decision, and
image CDNs built for embedding generally send them. Worth measuring against real
URLs rather than assuming — but the design degrades either way.

### Plan

1. Browser tries `fetch(url)` → bytes.
2. Failing that, `<img crossOrigin="anonymous">` → canvas → blob. Different
   failure modes, cheap to try.
3. Failing both, say the host will not allow it and offer **upload**, which always
   works. Lift the resize/recompress out of `image-shield/app.js` into cljs.
4. Send the bytes with the export POST. The 128 KB image cap fits well inside the
   2 MB body cap. Server skips the fetch entirely when bytes are present.
5. Then decide whether the server fetch still earns its keep as a fallback.

### Consequences

- The server fetch stops being the default path. The SSRF surface — and the
  SSRF surface shrinks to a rarely-hit fallback, or disappears.
- Do this on its own branch cut from `integration` **after**
  `feature/one-template-per-style` lands. Both touch
  `routes/generate-character-pdf`, and that branch is already 118 commits.

### Merge hazard

`claude/character-portrait-generator-hOutO` is cut from an older base and still
carries `#"^(https?|ftp|file)://…"` in `routes.clj` — the regex that allowed
`file:///etc/passwd`. Merged after the hardening without a rebase, it reintroduces
that hole.

## PDF export follow-ups

**Status:** Open  
**Severity:** Low — none is a live defect  
**Reported:** 2026-09-04

- **Total slot hold on images.** Worst case is now about 40s per image (10s
  connect + 20s transfer + one read timeout), so 80s for a character with a
  portrait and a faction image. Bounded, but possibly still generous.
- **`safe-image-url?` runs twice per image** — once in the route and once inside
  `safe-image-bytes`. Three DNS lookups per image, and it widens the gap above.
- **`create-monsters-pdf` is dead.** Private, zero callers, writes to a temp file.
  Scrub it or finish the feature.
- **Not audited:** whether a fetched portrait is re-encoded or downscaled to its
  drawn size. Bounded at 128 KB so the exposure is small.

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

