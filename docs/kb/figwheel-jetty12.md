# Figwheel, Pedestal and Jetty 12

*Checked 2026-11-18 against Maven Central, Clojars and the figwheel repos on GitHub. Re-check
before acting: this is about someone else's release schedule.*

## Why it matters

Two high Dependabot alerts (jetty-http, needs 11.0.29; jetty-security, needs 11.0.31) cannot be
fixed by a pin. Jetty 11's last public release is **11.0.26**, which we already pin in
`project.clj`; later 11.x builds come only through Jetty's paid support. The fix is Jetty 12,
which means Pedestal 0.7.1 or later (0.8.3 is current).

## What blocks it

Not Datomic. `figwheel-main` 0.2.20 (released 2025-02-18, still the latest) runs its dev server on
Ring 1.13, which uses Jetty 11 and Jetty 11's WebSocket API. With Pedestal's Jetty 12 on the same
classpath it fails with `NoClassDefFoundError: ScopedHandler` (the comment above the Pedestal deps
in `project.clj`). Production never runs figwheel, but every `fig:*` alias and the Docker build
compile with it.

## What upstream has, unreleased

- 2026-03-19, in both `figwheel-repl` and `figwheel-main`: "[sse-transport] replace
  WebSocket/polling with SSE transport". The reload channel is now plain HTTP server-sent events,
  so the Jetty WebSocket code that broke across versions is gone.
- figwheel-main's `deps.edn` and `project.clj` on master now ask for **Ring 1.15.3**, which runs on
  Jetty 12 (ring-jetty-adapter 1.15.x pulls jetty-server 12.1).
- 2026-06-20/21: EventSource fallbacks for the SSE client, a Rebel Readline bump, and a new
  feature ("managed Figwheel nREPL support"). No commits since.
- **Not published.** The `0.2.21-SNAPSHOT` on Clojars (all three artifacts) is dated 2025-02-18,
  before any of this. Do not assume the snapshot has SSE or Jetty 12.

## Why there is no release (as far as the record shows)

- One maintainer works in bursts (2026-03, 2026-06) with months of silence between.
- No issue, PR or discussion mentions releasing the SSE work, and no outside user has reported on
  it: it has had no testers beyond its author.
- The day after the SSE docs, a new feature landed on top instead of a release, so master is
  still gaining features, not stabilising.
- The release mechanism is ready (`make deploy` publishes all three repos in lockstep, per the
  repo's own CLAUDE.md), so nothing technical stops a release.

Reading: the transport rewrite is new and untested by others, and master is still moving. The
owner's call (2026-11-18): do not build on unreleased figwheel while it is in flux.

## Options

1. **Wait for a release**, then move Pedestal to 0.8.x on `upgrade/pedestal-jetty12` (approved
   name, not yet created). Pedestal 0.8's own API changes are not yet read.
2. **Vendor master at a pinned commit** through the `file:lib` repository. Unblocks now, but on
   code the author has not released.
3. **Replace figwheel with shadow-cljs** (3.5.14, active, own server, no Jetty clash). Touches
   every `fig:*` alias, the build configs, the cljs suite, the junk audit, Docker, e2e and the
   docs: the biggest change.
4. Patching figwheel ourselves: not needed; upstream already did the work.

The other alerts on the same push: carmine 3.7.0 (nippy 3.9.0, aircompressor 2.0.3) clears the
aircompressor alert; H2 2.1.214 comes from `com.datomic/peer` 1.0.7745, which is the latest, and
waits on Datomic. Triage in general: [dependency-alerts.md](dependency-alerts.md).
