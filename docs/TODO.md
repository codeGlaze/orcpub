# TODO — Tracked Issues

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

## jpackage Windows installer

**Status:** Open — future work
**Severity:** Low (enhancement)
**Reported:** 2026-02-24

### Problem

Windows distribution currently uses NSIS (Nullsoft Scriptable Install System) to
package the app as an `.exe` installer. NSIS is a third-party tool with its own
scripting language and version compatibility issues (e.g., v3.02 → v3.11 broke
the build).

### Proposed fix

Replace NSIS with `jpackage`, which ships with JDK 14+ (we're on 21). It creates
native `.exe`/`.msi` installers with a bundled JRE — no third-party dependency.

```bash
lein uberjar
jpackage --input target --main-jar orcpub.jar --type exe \
  --name OrcPub --app-version 2.x ...
```

**Datomic consideration**: The transactor is a separate process. Options:
- Bundle Datomic alongside the app, start as a Windows service (`--win-service`)
- Package as two installers (app + transactor)
- Single installer with a launcher script that starts both
