# Copilot instructions for Orcpub (short, actionable)

Purpose: help an AI coding agent get immediately productive in this repo — what to read first, how to run and test locally, and project-specific patterns to preserve.

1) Big picture (where to start)
- Repo is a full-stack Clojure/ClojureScript app (backend: Pedestal + Datomic; frontend: Reagent + re-frame + Figwheel). See `project.clj`, `src/clj/orcpub/system.clj`, and `web/cljs/orcpub/core.cljs`.
- Runtime wiring is Component-based (`com.stuartsierra/component`) — the live system lives in `orcpub.system` and `orcpub.pedestal`.
- DB is Datomic Free. Schema lives in `src/clj/orcpub/db/schema.clj` and is transacted via the REPL helper `(init-database)` in `dev/user.clj`.

2) Quick dev workflow (exact commands/examples)
- Start Datomic transactor (local manual): run the Datomic transactor as the Datomic docs show (e.g. `bin/transactor config/samples/free-transactor-template.properties` from your Datomic install).
- Docker alternative: use `docker-compose.yaml` (or rename `docker-compose-build.yaml` -> `docker-compose.yaml` to build from local source). Build: `docker-compose build` then `docker-compose up`.
- Start backend + REPL: `lein with-profile +start-server repl` OR from repl call `(start-server)` (see `dev/user.clj`).
- Init DB (once on a fresh DB): `(init-database)` from a REPL (calls schema transact).
- Frontend live reload: `lein figwheel` or call `(fig-start)` then `(cljs-repl)`; front-end build id is `:dev` (see :cljsbuild in `project.clj`).
- Prod build: `lein prod-build` (alias) or `lein with-profile prod cljsbuild once main`, then `lein uberjar` to make `target/orcpub.jar`.
- Run tests: `lein test` (server and cljc tests are under `test/`). **Note: This only tests server-side Clojure code!**
- **For frontend changes (Reagent, re-frame, CLJS)**: Must run `lein cljsbuild once dev` to validate ClojureScript compilation.
- Lint & format: `lein lint` (runs clj-kondo via alias), and use `lein cljfmt` for formatting.

3) Important environment & launch details
- Environment variables are authoritative via `environ`/`environ.core` (see `system.clj` and `routes.clj`). Important vars:
  - `DATOMIC_URL` (or use docker-compose DATOMIC_URL), `DATOMIC_PASSWORD`, `ADMIN_PASSWORD`
  - `SIGNATURE` — JWT signing secret (used by authentication middleware in `orcpub.routes`)
  - `PORT` — used by the web server in production
  - `EMAIL_*` variables for SMTP (used by `orcpub.email`)
- If authentication feels broken locally, verify `SIGNATURE` is set in the environment the server uses.

4) Code patterns & conventions to preserve
- Shared logic that runs on both JVM and CLJS goes into `src/cljc` (e.g. `orcpub.entity`), backend-only code in `src/clj`, and client-only code in `src/cljs` or `web/cljs`.
- Server lifecycle: prefer the component/system lifecycle patterns (see `orcpub.system`) and use `reloaded.repl` helpers in `dev/user.clj` to restart cleanly rather than re-defining globals.
- Authentication uses Buddy JWS tokens (see `orcpub.routes` — `backend` uses `environ/env :signature`). Keep token payloads & expiration handling consistent.
- DB schema is canonical in `src/clj/orcpub/db/schema.clj`; make schema changes there and run `(init-database)` (or a migration flow) in dev.
- **Front-facing keys used in exportable "homebrew" (templates, option keys, exported JSON identifiers) are user-facing and must not be renamed without providing compatibility.** If a rename is absolutely necessary, add a new key *and* accept or map the old key (e.g., parsing should accept both `:old-key` and `:new-key`, or add a migration that rewrites old exports). Add a test that verifies older exported homebrew still imports correctly.
- Frontend uses re-frame & reagent; follow existing event/subscription naming conventions in `web/cljs/orcpub/*`.

5) Files and locations to read first (quick map)
- Entry points: `src/clj/orcpub/server.clj`, `src/clj/orcpub/system.clj`, `web/cljs/orcpub/core.cljs`
- Routes & auth: `src/clj/orcpub/routes.clj`
- DB schema: `src/clj/orcpub/db/schema.clj`; DB helpers in `orcpub.datomic`
- Shared domain & entity builder: `src/cljc/orcpub/entity.cljc` and `src/cljc/orcpub/template.cljc`
- REPL/dev helpers: `dev/user.clj`
- PDF generation code: `src/clj/orcpub/pdf.clj` and local wkhtmltox under `bin/wkhtmltox` if needed

6) Safety/PR notes for agents
- Run `lein lint` and `lein test` before proposing code changes.
- Large schema changes must include a clear migration plan and a dev-test that exercises the schema change (use `dev/user.clj` helpers to set up a test DB).
- Avoid committing secrets (SIGNATURE, DB credentials). Use `.env` or CI secret store instead.
- **When swapping libraries, keep the same require alias** (e.g., if code uses `[clj-time.core :as t]`, swap to `[java-time.api :as t]` — not `:as jt`). Changing aliases introduces unnecessary churn and potential bugs. Only rename if there's an actual conflict with another library's alias.

If anything here is unclear or you want additional examples (e.g., a short checklist for creating a new endpoint or a sample PR checklist), tell me which section to expand. I can iterate. ✅
