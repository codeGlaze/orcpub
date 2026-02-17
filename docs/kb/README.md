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

## Human-Facing Docs (Copies)

The `docs/migration/` and `docs/MIGRATION-INDEX.md`, `docs/JAVA-COMPATIBILITY.md`, `docs/ENVIRONMENT.md` are copies of the human-facing docs from `breaking/2026-stack-modernization`. They're included here so integration branches don't need to cherry-pick from the feature branch.

## Known Corrections

The UPGRADE_PLAN.md contains inaccuracies discovered during the breaking branch assembly:

1. **CSP**: Presented as optional — actually required by Pedestal 0.7
2. **Figwheel port**: Claimed 9500 — always been 3449
3. **CI Java**: Was Java 8 on upgrade branch — bug, not intentional (fixed to 21)
4. **Pedestal pin**: 0.7.0 constraint is Jetty 11 vs 12 (figwheel-main compatibility), not a Java version issue
