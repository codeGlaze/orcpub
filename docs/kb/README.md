# Agent Knowledge Base

Deep-dive reference docs for AI agents working on this project. These contain full decision history, research results, and corrections that are too detailed for the human-facing migration docs.

## Documents

| Document | Purpose |
|----------|---------|
| [character-image-routes.md](character-image-routes.md) | How a character portrait reaches the sheet: CORS vs hotlink blocking measured against real hosts, two withdrawn conclusions, what the browser cannot be made to do, and how to reach the real internet from a browser test here |
| [code-comment-style.md](code-comment-style.md) | House comment style: tech-manual not journal; docstrings for the what, inline why only for constraints; no jargon/markers/KB-links; relating scattered code |
| [pedestal-csp-history.md](pedestal-csp-history.md) | Full CSP research: Pedestal 0.5.1→0.7.0 timeline, why nonces not static hashes, corrections to UPGRADE_PLAN.md |
| [dev-tooling-decisions.md](dev-tooling-decisions.md) | user.clj consolidation plan, dev-setup.sh decision, config.clj SSOT rationale, port/CI corrections |
| [DATOMIC_JAVA21_TEST_RESULTS.md](DATOMIC_JAVA21_TEST_RESULTS.md) | Full Datomic Free + Java 21 test matrix proving incompatibility |
| [UPGRADE_PLAN.md](UPGRADE_PLAN.md) | Original upgrade roadmap (has known inaccuracies — see pedestal-csp-history.md for corrections) |
| [SESSION-SUMMARY.md](SESSION-SUMMARY.md) | Service management scripts session history |
| [DEPENDENCY_VALIDATION.md](DEPENDENCY_VALIDATION.md) | Jackson/Guava dependency validation report |
| [UPGRADE_DEPENDENCIES.md](UPGRADE_DEPENDENCIES.md) | Dependency upgrade rationale and compatibility notes |
| [re-frame-subscribe-refactor.md](re-frame-subscribe-refactor.md) | Subscribe-outside-reactive-context: all 12 fixes, subscription chain analysis, track! risks |
| [testing-infrastructure.md](testing-infrastructure.md) | Test runners, re-frame testing truths, .cljc gotchas, namespace architecture, test patterns |
| [error-handling-import-validation.md](error-handling-import-validation.md) | Feature branch history: orcbrew validation, handle-api-response HOF, views decomposition, review findings, Clojure gotchas |
| [subscribe-diagnosis-techniques.md](subscribe-diagnosis-techniques.md) | How to diagnose subscribe-outside-reactive warnings: preload patching, monkey-patching, stack trace reading |
| [subscribe-refactor-phase2.md](subscribe-refactor-phase2.md) | Phase 2 subscribe fixes: options.cljc, pdf_spec.cljc, equipment_subs, views.cljs |
| [srd-vs-plugin-content.md](srd-vs-plugin-content.md) | What's hardcoded SRD vs from plugins: classes, races, subclasses, backgrounds, feats |
| [entity-options-architecture.md](entity-options-architecture.md) | Entity structure, single/multi-select, autosave template cache, content reconciliation |
| [modifier-vs-trait-slots.md](modifier-vs-trait-slots.md) | Why a plain trait map is inert in `:modifiers` but works in `:traits`; the three Evasions; opt5e/evasion inertness (verified live) |
| [fail-soft-rendering.md](fail-soft-rendering.md) | Never-black-screen architecture: layered error boundaries (root/tab/item), fault isolation by re-execution (item + selection level), verified findings, temp-revert fixtures to restore |
| [folder-hardening.md](folder-hardening.md) | Folder CRUD hardening: error handling, empty name prevention |
| [input-field-debounce.md](input-field-debounce.md) | Input field debounce pattern for character builder |
| [http-fx-patterns.md](http-fx-patterns.md) | :http effect handler: dispatch vectors, eager JS call bug, auth headers |
| [character-naming.md](character-naming.md) | Auto-naming: descriptive labels, random name gen, display fallbacks |
| [env-and-auth.md](env-and-auth.md) | Environment variables, SIGNATURE auth, .env sourcing chain, dev defaults |
| [views-builders-split.md](views-builders-split.md) | builders.cljs decomposition: 10 domain files, dependency rules, gotchas (class→classes, spell-selector stays shared) |
| [growable-option-menus.md](growable-option-menus.md) | Multi-select menu redesign: shared option-menu component, global layout toggle (grid/pills/A–Z), the two render-path families + inventory, map-prop/value-choice factories, menu-id rule, headless verify harness |
| [monolith-decomposition-plan.md](monolith-decomposition-plan.md) | Full decomposition roadmap: tiers, precedence, branching strategy, 31 files assessed |
| [namespace-architecture.md](namespace-architecture.md) | **START HERE** — full namespace map, dependency flows, entry points, layer boundaries. Read before scanning the repo. |
| [srd-2024-integration.md](srd-2024-integration.md) | 2024 SRD integration analysis: mix-and-match constraint, overlapping keys, possible approaches, investigation needed |
| [spa-routing-architecture.md](spa-routing-architecture.md) | SPA routing: 3-place registration (route_map, index-page-paths, core.cljs pages), user-for-email nil gotcha |
| [email-preferences-implementation.md](email-preferences-implementation.md) | Email preferences: JWT unsubscribe, send-updates? flow, fork/ reorg, social-links-footer, re-read after transact |
| [remote-dev.md](remote-dev.md) | Remote development: Figwheel WebSocket URL in Codespaces/tunnels, --fw-opts discovery, port visibility, auto-detection |
| [fork-customization.md](fork-customization.md) | Fork override files: 6-file pattern, branding/integrations/user_tier, merge strategy, cherry-pick between branches |
| [dmv-production-changes.md](dmv-production-changes.md) | DMV production analysis: backport-worthy fixes, security issues, hotfix history, git workflow gotchas |
| [pdf-generation-architecture.md](pdf-generation-architecture.md) | PDF export end-to-end: template selection, spell card lifecycle, silent catch pattern, PDFBox 3.x migration, testing methodology |
| [reframe-subscription-patterns.md](reframe-subscription-patterns.md) | reg-sub-raw HTTP pattern, loading counter (int not bool), auth guard placement, subscribe context rules, debugging |
| [docker-setup-flow.md](docker-setup-flow.md) | run mode flow: block order, flag combos, helpers, generate_env(), error recovery, test infrastructure |
| [docker-swarm-compat.md](docker-swarm-compat.md) | Compose Spec → Swarm v3 incompatibilities: depends_on, null fields, ports, jq pipeline |
| [docker-security-decisions.md](docker-security-decisions.md) | Docker security decisions: non-root, sed escaping, chmod 600, .dockerignore, DATOMIC_URL validation |
| [homebrew-class-spellcasting.md](homebrew-class-spellcasting.md) | Homebrew class spellcasting: slot schedules, known-spells, prepared casters, integration risks |
| [spell-selection-source-fix.md](spell-selection-source-fix.md) | Cantrip/spell-selection source-poisoning fix (`fix-cantrips-selection-bug` branch): feature changes, the select-keys toggle gap, reconciler plugin-scope, BOM non-issue, e2e-verified remediation |
| [lein-uberjar-hang.md](lein-uberjar-hang.md) | lein compile hang: Datomic Peer non-daemon threads, timeout workaround, build profile fix |
| [docker-testing-guide.md](docker-testing-guide.md) | Docker testing: 46-test suite, manual smoke tests, every gotcha (H2 lock-in, env -u, ports, transit, piped input, jq nulls) |
| [class-builder-extraction-plan.md](class-builder-extraction-plan.md) | Plan for the first incremental view decomposition: extract the class builder from the views.cljs monolith (reuses views-builders-split rules; the anti-views-extraction cadence) |
| [starting-equipment-override-ledger.md](starting-equipment-override-ledger.md) | Design: template + ledger override for starting equipment — stable-key addressing (not names), add/replace(=remove) ops, SRD-key freeze, derived-diff, edge cases |

## Findings moved from `integration`

These were written on feature branches and folded into `integration`'s `docs/kb/`
before that tree was retired. Each records what was measured, what turned out to be
wrong, and why.

### Performance

- **[perf-homebrew-builder-loop.md](perf-homebrew-builder-loop.md)** -- the builder freeze
  with large homebrew libraries. Root cause: `cljs.core/memoize` cache lookups that
  deep-compare every class in the library (1125 ms -> 100 ms). Also covers the storage
  layer, heap behaviour under class browsing, and several withdrawn conclusions.
- **[perf-entity-build.md](perf-entity-build.md)** -- `entity/build` cost, and the
  `kahn-sort` rewrite (23.0 -> 3.0 ms JVM, 25.2 -> 4.9 ms browser) with the CLJS
  set-ordering trap that made a JVM-green rewrite diverge in the browser.
- **[plan-chunked-library-storage.md](plan-chunked-library-storage.md)** -- *parked.*
  Per-source localStorage keys. Measured ceiling 5,177,344 chars; why copy-then-delete
  migration is dead; why this does not fix the reported freeze.

### Practice

- **[memoize-antipattern-scan.md](memoize-antipattern-scan.md)** -- every `memoize` site
  scanned and traced. Four are dead code; `memoized-spell-option` is measurably 10x slower
  than no cache. Planned, not executed.
- **[verification-discipline.md](verification-discipline.md)** -- how this repo has been
  wrong, and the probe defects that produced confident wrong answers. Read before writing a
  performance probe.
- **[reagent-architecture-tenets.md](reagent-architecture-tenets.md)** -- subscriptions,
  lifetime, and what to use instead of another cache.
- **[documentation-tenets.md](documentation-tenets.md)** -- record reversals; never silently
  overwrite superseded reasoning.

### Domain

- [pdf-form-techniques.md](pdf-form-techniques.md) -- what works against the real PDF
  templates, and what does not.
- [custom-content-lifecycle.md](custom-content-lifecycle.md)
- [library-management-and-conflicts.md](library-management-and-conflicts.md)
- [keyword-trap-name-repair.md](keyword-trap-name-repair.md)
- [starting-equipment.md](starting-equipment.md)

### Browser probes

All under `test/browser/`, run against `lein e2e-server` (see `test/browser/README.md`).

| Probe | Answers |
| --- | --- |
| `tab_switch_freeze_e2e.js` | the freeze: longest task per Race<->Class switch, heap, counters, stacks |
| `class_body_cost_e2e.js` | class-body cost at open and per switch, retained heap |
| `builds_per_interaction_e2e.js` | `entity/build` calls per click |
| `freeze_cpu_profile_e2e.js` | CPU profile ranked by inclusive time |
| `storage_shape_e2e.js` | what is actually in localStorage after a real import |
| `localstorage_ceiling_e2e.js` | the real quota, and whether it counts chars or bytes |
| `library_chunk_granularity_e2e.js` | how finely a library can be split |
| `character_image_capture_e2e.js` | both routes a portrait can take into a PDF, and the shape of the field's notices |
| `scripts/test/run-cljs-tests.js` | runs the ClojureScript suite headlessly (repo's canonical runner, not under test/browser) |

## Human-Facing Docs (Copies)

The `docs/migration/` and `docs/MIGRATION-INDEX.md`, `docs/JAVA-COMPATIBILITY.md`, `docs/ENVIRONMENT.md` are copies of the human-facing docs from `breaking/2026-stack-modernization`. They're included here so integration branches don't need to cherry-pick from the feature branch.

## Known Corrections

The UPGRADE_PLAN.md contains inaccuracies discovered during the breaking branch assembly:

1. **CSP**: Presented as optional — actually required by Pedestal 0.7
2. **Figwheel port**: Claimed 9500 — always been 3449
3. **CI Java**: Was Java 8 on upgrade branch — bug, not intentional (fixed to 21)
4. **Pedestal pin**: 0.7.0 constraint is Jetty 11 vs 12 (figwheel-main compatibility), not a Java version issue

## Contribution rules

Each document should be sourced from direct inspection of code, logs, or authoritative
references — not speculation.

- Only add findings you can cite directly (log lines, code lines, benchmark results, official docs).
- If you are reasoning from circumstantial evidence, mark the paragraph **⚠️ UNVALIDATED SPECULATION — [brief rationale]**.
- Include the date the analysis was done and the artifact(s) it was based on.
- Do not remove speculation flags — when something is later verified, replace the flag with **✅ VERIFIED — [how]** and update the text.
