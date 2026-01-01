# Upgrade Plan — Dependencies & Tooling 🔧

## Overview

This document describes a proposed, incremental plan to modernize the project's runtime, libraries, and build tooling. The goal is to bring server- and client-side dependencies up to supported modern versions, reduce security risk, and improve developer experience and CI reliability.

---

## Current observations (quick audit)
- `project.clj` uses **Clojure 1.10.0** and **ClojureScript 1.10.439**.
- Frontend uses **Reagent 0.7.0**, **re-frame 0.10.9** and `cljsjs`-packaged **React 16.6.0**.
- Dev tooling uses **Figwheel** + `lein-cljsbuild` and older `figwheel-sidecar`.
- Server libraries include **Pedestal 0.5.1**, **Buddy 1.x**, **Datomic Free 0.9.x**, **Jackson 2.11.1**, **Guava 21.0** and `clj-time`.
- There are several possibly outdated/unsafe transitive deps (e.g., Guava, Jackson) that should be audited.

---

## Goals (measurable)
- Upgrade to a supported JDK (target: **JDK 17** or **JDK 21** — choose after audit).
- Upgrade Clojure to the latest stable 1.10+/1.11+ line as appropriate.
- Upgrade ClojureScript, Reagent, and re-frame; replace `cljsjs` React with npm-managed React.
- Evaluate and migrate to a modern CLJS build chain (Shadow-CLJS or updated cljsbuild + Figwheel Main).
- Update Pedestal and server libs, and bump Guava / Jackson to secure versions.
- Keep CI green across the upgrade; add matrix testing for JDK and Node versions.

---

## Scope & Constraints
- Work incrementally with small PRs; each PR targets a single library or related set of small breaking changes.
- Maintain backwards compatibility for export formats where possible (see repo docs regarding exports).
- When schema changes are required (e.g., DB migration), provide a migration script & tests.

---

## High-level roadmap & milestones
1. Audit (this week)
   - Run `lein deps :tree`, `lein test`, `lein lint`, `npm outdated`, `npx npm-check-updates --packageFile package.json` and capture failing tests.
   - Produce a precise list of direct deps to upgrade with suggested target versions and risk notes.
2. Branching & PR strategy
   - Create branch: `upgrade/deps-YYYY-MM-DD` and open draft PR.
   - Make small, atomic PRs: `upgrade/clojure-1.11`, `upgrade/pedestal-0.x`, `upgrade/cljs-...` etc.
3. JDK & build tooling
   - Set and test target JDK (17 or 21). Update Dockerfiles and CI configs.
4. Server-side upgrades (incremental)
   - Jackson, Guava, Pedestal, Buddy, Datomic (client or compat check).
5. Client-side upgrades (incremental)
   - ClojureScript, Reagent, re-frame; replace `cljsjs` React with npm React; consider Shadow-CLJS migration.
6. CI and tooling updates
   - Linters, clj-kondo, cljfmt, GH Actions/CI matrix adjustments.
7. Tests and deprecation fixes
   - Make tests pass; fix deprecations; add tests where missing.
8. Release & cleanup
   - Merge mainline PRs, tag release, update README & docs.

---

## Detailed tasks (immediate)
- [x] Create this `UPGRADE_PLAN.md` (this file).
- [ ] Run dependency audit: `lein deps :tree` and `npm outdated`.
- [ ] Run test suite: `lein test` and `npm test` (if applicable).
- [ ] Create branch `upgrade/deps-<date>` and open a draft PR.
- [ ] Prioritize critical security updates (e.g., Jackson, Guava) for immediate PRs.

---

## Branching & PR rules
- One major upgrade per PR where practical.
- Include migration notes and failing test artifacts in PR description.
- Add `:breaking` label and a short compatibility note if public API or exports change.

---

## Testing & CI
- Add a CI matrix for JDK versions (17, 21) and Node versions used for CLJS builds.
- Run unit tests and an integration smoke test (start server, call a handful of endpoints).
- Add vulnerability checks (`mvn dependency:check` or `snyk` / `npm audit` as appropriate).

---

## Risk & Rollback
- Keep PRs small so rollbacks are easy.
- If DB/schema change is required, add a reversible migration script and one-liner to revert.
- Keep release notes for breaking changes.

---

## Notes / Helpful commands
- Dependency tree: `lein deps :tree`
- Run server tests: `lein test`
- Run linter: `lein lint` (alias provided)
- Check CLJS build locally: `lein figwheel` (dev) or `lein prod-build`
- To convert to Shadow-CLJS, consider: `npm init -y; npm i --save-dev shadow-cljs react react-dom` and follow incremental porting guide.

---

## Next steps (my plan)
1. Run the dependency audit and collect `lein deps :tree`, `lein test`, and `npm outdated` outputs and add the findings to this document.
2. Create the upgrade branch and begin with high-priority security upgrades.

---

If you want, I can proceed to run the audit now and append concrete version suggestions to this file. Reply with “start audit” to continue. ✅
