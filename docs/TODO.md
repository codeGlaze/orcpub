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

## Restrict PDF image-URL schemes and reject private address ranges

**Status:** Open
**Severity:** Medium — unauthenticated endpoint, pre-existing
**Reported:** 2026-04-22
**File:** `src/clj/orcpub/routes.clj:678-693`
**Surfaced during:** PDF widget-warnings review session (working branch
`claude/fix-pdf-widget-warnings-hUt9i`, code landed on
`bugfix/pdf-widget-warnings`).

### Summary

`character-pdf-2` accepts `image-url` / `faction-image-url` from the EDN
request body and fetches them via `java.net.URL.openConnection` to embed
as portrait / faction images. The current validation regex allows four
URL schemes:

```clojure
(re-matches #"^(https?|ftp|file)://..." image-url)
```

`file://` and `ftp://` are meaningful attack vectors, and `http[s]://`
is not restricted to public address space. The endpoint also has no
`check-auth` interceptor (`routes.clj:1498`), so any caller can drive
the URL fetch.

### Realistic exploit shape

- **LFI** (e.g. `file:///etc/...`): the URL is fed to `ImageIO/read`. If
  the file isn't a valid image format, the read fails and the PDF comes
  back without that image. So LFI is gated on the target file being a
  valid image — but server-readable PNG/JPEG files (other characters'
  uploaded portraits, branding assets, container layer files) are
  reachable.
- **SSRF / metadata theft** (e.g. cloud metadata endpoints): typically
  return JSON, not images, so the embed fails — but timing differences
  (success / 4xx / 5xx / timeout) are observable from the response body
  size and can be used for service discovery.
- **Internal SSRF**: same shape as cloud metadata — observable timing
  reveals reachable internal services even without successful embed.

### Proposed fix

- Restrict schemes to `https?` only.
- Resolve the hostname once and reject if the resolved address falls in
  a private / link-local / loopback / unspecified range
  (IPv4: `10/8`, `172.16/12`, `192.168/16`, `127/8`, `169.254/16`,
  `0/8`; IPv6: `::1`, `fc00::/7`, `fe80::/10`).
- Decide whether `/character.pdf` should require authentication at all —
  currently anyone reaching the host can drive PDF generation.

### Notes for future agents

- Pre-existing — predates the PDF widget-warnings fix. That fix narrowed
  the attack surface slightly (removed UA-driven flatten path) but did
  nothing for image URL handling.
- Not filed on the public-facing `bugfix/pdf-widget-warnings` branch —
  kept here on `agents/develop` to avoid surfacing a security writeup
  on a public PR. When a real fix lands, that change can carry a clean
  changelog entry describing the validation tightening without exploit
  detail.
