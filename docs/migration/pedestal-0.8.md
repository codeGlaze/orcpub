# Pedestal 0.7.0 → 0.8.3

## Why

- Jetty 11's last public release is 11.0.26, so the jetty-http and jetty-security security alerts could not be cleared on Pedestal 0.7.0. Pedestal 0.8.3 runs on Jetty 12.
- The old reason to stay on 0.7.0 (figwheel-main 0.2.20's Jetty 11 Ring adapter, `ScopedHandler` `NoClassDefFoundError`) is resolved by a newer figwheel in dev.

## Code changes

- `routes.clj` builds the main and index routes in one `route/expand-routes` call, because each call now returns a routing table record. Tests read `(:routes routes/routes)`.
- `system.clj` adds the `GzipHandler` with `.insertHandler`; Jetty 12's `ServletContextHandler` has no `setGzipHandler`.
- figwheel is a dev-only dependency (`:dev-config`), so it is not in the uberjar. The production bundle is built by `lein cljs:prod` (`fig:prod` is the same alias), and `docker/Dockerfile` step 1 runs it. `test/clj/orcpub/dependencies/build_shape_test.clj` fails if figwheel returns to the main dependencies or the Docker or build path uses it.

## The connector API

The server starts through `io.pedestal.connector` and `io.pedestal.http.jetty/create-connector`, not the deprecated `io.pedestal.http` service map, so it logs no deprecation warnings.

- `system.clj` builds a plain connector map (`:host`, `:port`, `:join?`, `:routes`, `:resource-path`, `:secure-headers`, `:container-options`, `:interceptors`). Its `default-interceptors` lists the interceptors `io.pedestal.http/default-interceptors` used to add, in the same order; the `with-...` functions then put ours in front, and the `:dev` system puts `io.pedestal.connector.dev/dev-interceptors` first.
- `pedestal.clj` appends the nonce, db and ETag interceptors and calls `create-connector`, then `start!`. The connector does not expose its Jetty `Server`, so a `:configurator` container option hands it over for the boot banner's thread count.
- Route handlers are Vars (`#'index`), not quoted symbols; route names are unchanged.
- Tests build a connector the same way and call `io.pedestal.connector.test/response-for`, which runs the chain without the servlet layer (a HEAD response body is `nil`, not `""`).
- `dev/e2e_boot.clj` overrides `[:service-map :port]`.

## The Jetty pin

Pedestal 0.8.3 brings Jetty 12.0.x and figwheel's Ring 1.15 brings 12.1.x; a mix fails at start-up. Every Jetty artifact is pinned to 12.1.15 in `:managed-dependencies` in `project.clj`. Leiningen ignores imported BOMs, so each artifact is listed.

## Vendored figwheel

Dev uses figwheel's unreleased main branch (SSE instead of WebSocket for reload, Ring 1.15.3, Jetty 12), built as version `0.2.21-orcpub1` with only version strings changed. It lives in `lib/com/bhauman/` and is served through the `file:lib` repository; Docker copies `lib/` into its Maven repo.

Commits: figwheel-core `92dcbb4b`, figwheel-repl `5a67d34f`, figwheel-main `cd7b0698`.

To rebuild:

1. Clone the three repos at those commits.
2. In each `project.clj`, set the version to `0.2.21-orcpub1` and the sibling figwheel dependencies likewise.
3. Run `lein install` in core, repl, then main, in that order.
4. Copy each jar and pom from `~/.m2/repository/com/bhauman/<name>/0.2.21-orcpub1/` to `lib/com/bhauman/<name>/0.2.21-orcpub1/`.

Drop the vendoring when figwheel releases a version with the SSE transport and Ring 1.15.
