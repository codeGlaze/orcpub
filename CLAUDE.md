# OrcPub — Agent Instructions

## Orientation

Before scanning the codebase, read the architecture map and KB index:

1. **Start here**: `/workspaces/orcpub-agents/docs/kb/namespace-architecture.md` — full namespace map, dependency flows, entry points, layer boundaries
2. **Topic index**: `/workspaces/orcpub-agents/docs/kb/README.md` — links to 20+ deep-dive docs on specific subsystems
3. **Active work**: Check the git branch and recent commits before starting — work may be in progress

## Project Conventions

- **Clojure/ClojureScript** monolith with re-frame (events/subs/views pattern)
- **Homebrew content is core functionality** — builders are creative tools, not just rules replicators
- Brief comments on any function touched; section headers in files > 100 lines
- `lein fig:build` for CLJS compilation, `lein test` for JVM tests (206 tests, 945 assertions)

## Worktree Layout

| Path | Branch | Purpose |
|------|--------|---------|
| `/workspaces/orcpub` | varies | Primary working tree |
| `/workspaces/orcpub-agents` | `agents/develop` | Agent KB docs (read-only reference from here) |

## Known Gotchas

- **JS reserved keywords in namespaces** — `class`, `import`, `default` cause Closure munging. Use plural (`classes`) or synonym
- **`def` vs `defn`** — several key helpers are `def`s (partials/data), not `defn`s. Won't appear in `defn` greps
- **Shared helper deps dictate placement** — if a shared helper calls X, X must stay shared too, even if X looks domain-specific
- **`.clj-kondo` is in `.gitignore`** — use `git add -f` to stage kondo config
