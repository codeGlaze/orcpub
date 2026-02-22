# Agent Knowledge Base

Deep-dive reference docs for AI agents working on this project. These contain full decision history, research results, and corrections that are too detailed for the human-facing migration docs.

## Documents

| Document | Purpose |
|----------|---------|
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
| [folder-hardening.md](folder-hardening.md) | Folder CRUD hardening: error handling, empty name prevention |
| [input-field-debounce.md](input-field-debounce.md) | Input field debounce pattern for character builder |
| [http-fx-patterns.md](http-fx-patterns.md) | :http effect handler: dispatch vectors, eager JS call bug, auth headers |
| [character-naming.md](character-naming.md) | Auto-naming: descriptive labels, random name gen, display fallbacks |
| [env-and-auth.md](env-and-auth.md) | Environment variables, SIGNATURE auth, .env sourcing chain, dev defaults |

## Human-Facing Docs (Copies)

The `docs/migration/` and `docs/MIGRATION-INDEX.md`, `docs/JAVA-COMPATIBILITY.md`, `docs/ENVIRONMENT.md` are copies of the human-facing docs from `breaking/2026-stack-modernization`. They're included here so integration branches don't need to cherry-pick from the feature branch.

## Known Corrections

The UPGRADE_PLAN.md contains inaccuracies discovered during the breaking branch assembly:

1. **CSP**: Presented as optional — actually required by Pedestal 0.7
2. **Figwheel port**: Claimed 9500 — always been 3449
3. **CI Java**: Was Java 8 on upgrade branch — bug, not intentional (fixed to 21)
4. **Pedestal pin**: 0.7.0 constraint is Jetty 11 vs 12 (figwheel-main compatibility), not a Java version issue
