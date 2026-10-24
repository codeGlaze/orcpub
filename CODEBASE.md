# OrcPub codebase overview

Full-stack Clojure/ClojureScript: Pedestal + Datomic + Buddy auth on the server, Reagent +
re-frame in the browser, built with Leiningen and figwheel-main.

## Layout

```
src/clj/    server-only Clojure (JVM)
src/cljc/   shared code (JVM and browser)
src/cljs/   browser-only ClojureScript
web/cljs/   frontend entry
dev/        REPL tools: on the classpath under :dev (the default), :tools and :init-db; never in the uberjar
```

## Where things are

| Purpose | Location |
|---------|----------|
| Server entry | `src/clj/orcpub/server.clj`, `src/clj/orcpub/system.clj` |
| Frontend entry | `web/cljs/orcpub/core.cljs` |
| re-frame events / subs | `src/cljs/orcpub/dnd/e5/events.cljs`, `subs.cljs` |
| Character builder | `src/cljs/orcpub/character_builder.cljs` |
| Routes and auth | `src/clj/orcpub/routes.clj`; route names in `src/cljc/orcpub/route_map.cljc` |
| DB schema | `src/clj/orcpub/db/schema.clj` |
| Shared domain logic | `src/cljc/orcpub/entity.cljc`, `src/cljc/orcpub/template.cljc` |
| D&D 5e rules | `src/cljc/orcpub/dnd/e5/` |
| PDF generation | `src/clj/orcpub/pdf.clj` |
| Styles (Garden) | `src/clj/orcpub/styles/core.clj` |
| Splash page (server-rendered) | `src/cljc/orcpub/dnd/e5/views_2.cljc` |
| REPL helpers | `dev/user.clj` |
| JVM tests | `test/clj/`, `test/cljc/` (`lein test`) |
| cljs tests | `test/cljs/` (`lein fig:test`, run headless by `test/e2e/cljs-harness.js`) |
| Browser tests: real SPA, no backend | `test/e2e/*.js` |
| Browser tests: real app, DB and login | `test/browser/*_e2e.js`, `scripts/e2e/run.sh` |
| Project config | `project.clj` |

## Builds and profiles

```bash
lein fig:build                         # dev browser bundle, once
lein fig:dev                           # dev bundle with hot reload and a REPL
lein fig:test                          # cljs test bundle (run with node test/e2e/cljs-harness.js)
lein garden once                       # compile styles once
lein garden auto                       # recompile on change
lein with-profile +start-server repl   # REPL that starts the web server
lein e2e-server                        # in-memory server for the browser probes; no seeded user
./scripts/e2e/run.sh <suite>           # builds, boots a seeded server (kaylee / serenity99), runs the suite
lein lint                              # clj-kondo over src, test and web
```

Without `./menu`, start the transactor, then the server:

```bash
lib/com/datomic/datomic-pro/1.0.7482/bin/transactor \
  lib/com/datomic/datomic-pro/1.0.7482/config/working-transactor.properties &
sleep 5          # let the transactor accept connections first
PORT=8890 lein run
```

Calva's Jack-in offers the same profiles (`start-server`, `dev`). Running the whole app:
`README.md` (`./menu`, `scripts/start.sh`) and `SETUP.md`. `scripts/start.sh --install` installs Datomic
Pro on first setup; `--tmux` runs the services in a tmux session named `orcpub` (its header lists the rest).

## Style gotchas

- **Vendor prefixes in Reagent styles are camelCase:** `:WebkitMaskImage`, not
  `:-webkit-mask-image`, which React drops silently. Set `mask-image` and `-webkit-mask-image`
  both. The server-rendered `style` in `views_2.cljc` turns camelCase back into CSS.
- **Never set `background-image: none`** next to a `background` shorthand gradient: it cancels the
  gradient. Set `background-image` to the gradient itself.
- **Garden:** each rule is one vector; several top-level vectors in one `def` fail with "Too many
  arguments to def".
