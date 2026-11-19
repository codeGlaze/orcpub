# Figwheel, Pedestal and Jetty 12

*Checked 2026-11-18 against Maven Central, Clojars and the figwheel repos on GitHub. Re-check
before acting: this is about someone else's release schedule.*

## Why it matters

Two high Dependabot alerts (jetty-http, needs 11.0.29; jetty-security, needs 11.0.31) cannot be
fixed by a pin. Jetty 11's last public release is **11.0.26**, which #89 (`feature/inventory-kits`) pins in
`project.clj` (`integration` itself has no Jetty pins); later 11.x builds come only through Jetty's paid support. The fix is Jetty 12,
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

## Tested 2026-11-18 (scratch worktree, nothing committed)

figwheel master at core `92dcbb4b`, repl `5a67d34f`, main `cd7b0698`, built locally as
`0.2.21-orcpub1`, with Pedestal 0.8.3:

- **figwheel 0.2.20 cannot stay a main dependency.** Even `fig:prod` (build-once, no server)
  crashes on `ScopedHandler`, and the app server itself fails (`NoSuchMethodError` in
  `JakartaWebSocketServletContainerInitializer`) because figwheel's Jetty 11 WebSocket jars
  shadow Jetty 12's. That would reach the uberjar.
- **figwheel master works:** `fig:build`, `fig:prod`, `fig:watch` with SSE hot reload
  (`/figwheel-connect` as `text/event-stream`, `[Figwheel] loaded (orcpub.core)` after an edit),
  and the cljs suite (1039 tests, 0 failures).
- **Jetty must be pinned to one line:** Pedestal brings Jetty core 12.0.x, Ring 1.15.3 brings ee9
  at 12.1.0, and the mix fails (`ee9.nested.ContextHandler/<clinit>`). Pinning every Jetty
  artifact to 12.1.15 fixes it; lein ignores imported BOMs.
- **Pedestal 0.8 code changes:** `routes.clj` `routes` must be one `expand-routes` call
  (each call now returns a `RoutingTable` record, so concatenating two breaks); `system.clj`
  gzip uses `.insertHandler` (Jetty 12 has no `.setGzipHandler`); four tests read
  `(:routes routes/routes)`. CSP nonces and gzip match 0.7.0 with `CSP_POLICY=strict`.
- Untested: `fig:dev` (REPL).

**Production without figwheel (tested 2026-11-19, same worktree).** Figwheel is a main dependency
today and the Docker build (`docker/Dockerfile` ~93) and `lein build` compile through it, so its
jars ship. Moving `figwheel-main` and `rebel-readline-cljs` into the `:dev-config` profile and
building production with `cljs.main` works:
- alias `"cljs:prod" ["with-profile" "-dev,+cljs-prod" "run" "-m" "cljs.main" "-co" "prod.cljs.edn"
  "-d" "target/cljs-prod-out" "-c" "orcpub.core"]` plus profile
  `:cljs-prod {:source-paths ["web/cljs" "src/cljc" "src/cljs"]}` (web/cljs is otherwise dev-only);
  cljs.main reads prod.cljs.edn as is.
- The bundle matches figwheel's to within gensym counters (3,787,078 vs 3,787,091 bytes; every
  string literal equal); neither compiler is byte-deterministic between runs.
- The uberjar (via `lein build` with `cljs:prod`) holds no figwheel, no ring-jetty-adapter, Jetty
  only at 12.1.15; it runs clean in Chromium with CSP nonces and gzip intact.
- Trap: an uberjar built without `clean` packs stale figwheel dev output from
  `resources/public/js/compiled/out`. `lein build` and Docker's fresh context avoid it.
- Dev (`fig:build`, `fig:watch` with SSE reload, `e2e-server`, `lein test`) all run under `:dev`.
- Vendoring figwheel master for dev fits the existing pattern: `lib/` is gitignored but holds
  force-added jars (the pdfbox snapshot) served through the `file:lib` repository.

## Options

1. **Wait for a release**, then move Pedestal to 0.8.x on `upgrade/pedestal-jetty12` (approved
   name, not yet created). Pedestal 0.8's code changes are listed above.
2. **Vendor master at a pinned commit** through the `file:lib` repository. Unblocks now, but on
   code the author has not released.
3. **Replace figwheel with shadow-cljs** (3.5.14, active, own server, no Jetty clash). Touches
   every `fig:*` alias, the build configs, the cljs suite, the junk audit, Docker, e2e and the
   docs: the biggest change.
4. Patching figwheel ourselves: not needed; upstream already did the work.

The other alerts on the same push: carmine 3.7.0 (nippy 3.9.0, aircompressor 2.0.3) clears the
aircompressor alert; H2 2.1.214 comes from `com.datomic/peer` 1.0.7745, which is the latest, and
waits on Datomic. Triage in general: [dependency-alerts.md](dependency-alerts.md).
