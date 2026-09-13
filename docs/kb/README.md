# Agent Knowledge Base

Verified, research-backed findings from in-depth investigations. Each document is sourced from
direct inspection of code, logs, or authoritative references. Speculation is marked
**⚠️ UNVALIDATED SPECULATION** and must not be treated as fact without further verification.

Two knowledge bases were merged here on 2026-09-13 when `feature/grant-rows` was pulled into
`agents/develop`: the Fall Update content-extensibility track (51 docs, indexed first) and the
agent/operations KB (73 docs, indexed second). Neither was a superset of the other, and both
sides had been written blind to the other's findings.

## Start here

| If you are | Read |
| --- | --- |
| Taking over the Fall Update content work | [roadmap.md](roadmap.md), then [handoff-grant-rows.md](handoff-grant-rows.md) |
| Looking for which doc owns a topic | [topic-index.md](topic-index.md) |
| New to the codebase | [namespace-architecture.md](namespace-architecture.md) |
| About to touch a builder, a control, or CSS | [before-you-start.md](before-you-start.md) |
| Writing a doc | [documentation-discipline.md](documentation-discipline.md) · [verification-discipline.md](verification-discipline.md) |

## Index
### Plan & status
| Document | Topic |
|----------|-------|
| [plan-next.md](plan-next.md) | **The working list.** What to build next, ordered, with what each item unblocks and roughly how big. One hard dependency; the rest can move. | Working list |
| [roadmap.md](roadmap.md) | **START HERE — what this branch is.** The single branch plan: both phases (content/pool+grant, largely built; mechanization/class-feature/spell-slot expansion), a BUILT/DECIDED/OPEN ledger anchored to commits, flagged conflicts, the full doc map, and the critical path. |

### Content-extensibility track (the pool/grant initiative)
| Document | Topic | Source quality |
|----------|-------|---------------|
| [handoff-grant-rows.md](handoff-grant-rows.md) | **Taking over `feature/grant-rows`? Start here.** Where the branch is, what is proven, and the eight steps in order with an acceptance test each — shim registry, the `:ref` decision, the other silos, effect kinds, E3, the one-liner, spells. Plus the rules that bit and the don'ts. | High — written at handoff |
| [pool-grant-map.md](pool-grant-map.md) | **Start here for anything pool/grant.** The whole web on one page: the three layers, a REAL-vs-AIR ledger with dates and provenance, the dependency graph between the open pieces, why spells are closer to a pool than they look, and the three claims this area got wrong by designing before grepping. | High — code-verified ledger |
| [content-extensibility-direction.md](content-extensibility-direction.md) | **Canonical detail + direction for the content track** (v2 spine): the pool+grant model, the one principle (an abstraction must be thicker than what it hides), the variant forward-compat seam, and the next levers/pins. | Mixed — verified + DESIGN flagged |
| [content-extensibility-decisions.md](content-extensibility-decisions.md) | The numbered decision log (**D1–D34**) — how each decision was reached, incl. the prototype-then-converge governance (D23), the grant conflict (D29/D30), and the vocabulary/AC duplication findings (D31). | Decision record |
| [content-extensibility-framework.md](content-extensibility-framework.md) | How-to reference for the registry-driven content system (mental model + schema + add-a-type + invariants). | High — code + design |
| [content-extensibility-compatibility.md](content-extensibility-compatibility.md) | Inventory of the persisted data formats (saved characters, `.orcbrew`) the refactor must not break — the backward-compat invariants. | High — format inventory |
| [content-extensibility-e2e.md](content-extensibility-e2e.md) | Live end-to-end verification checklist (what the JVM gate can't cover; for a browser/figwheel run). | Checklist |
| [registry-before-after.md](registry-before-after.md) | Representative before/after of adding a content type (Pact Boon) — scattered wiring vs the registry-driven path. | High — code |
| [content-extensibility.md](content-extensibility.md) · [content-extensibility-plan.md](content-extensibility-plan.md) | ⚠️ **HISTORY (superseded)** by the direction doc — read as "what was tried," not the live plan. | Historical |

### Verified topic / reference
| Document | Topic | Source quality |
|----------|-------|---------------|
| [decision-vocabulary.md](decision-vocabulary.md) | **Map of the homebrew wiring**: which decision keys each silo emits and which assembly fn compiles them. Includes the verified A/B grant-vocabulary comparison (shared primitive, B is level-gated, cljc/cljs layer split). | Medium-High — call-graph verified; key claims now test-backed |
| [homebrew-content-merge.md](homebrew-content-merge.md) | The `feat-options` trap: why "X isn't homebrew-extensible" conclusions are usually wrong (merge happens at the concat point, not the static `*-options` def). | High — code |
| [key-collision-behavior.md](key-collision-behavior.md) | What happens when content keys collide, **per layer**: classes/races/spells → homebrew OVERRIDES built-in (predictable, plugin-wins); subraces/pools/lists → coexist; import → conflict modal. The "duplicate keys" map. Includes the two later-traced corrections (plugin-vs-plugin winner is **hash-order/nondeterministic**; spell-list membership **unions** across duplicates). | High — traced + test-backed (`key_collision_test`) |
| [content-tiers-and-key-resolution.md](content-tiers-and-key-resolution.md) | **Design direction**: the "≤1 *enabled* item per key" invariant + disable-based duplicate resolution; three content provenance tiers (owned/example/variant) in one store; versioned self-updating example content with copy-on-edit graduation; library-management UX (show/search/count disabled, hide empty categories); move-between-sources; and the suggested branch decomposition. | Mixed — verified refs + DESIGN flagged |
| [spell-granting-across-silos.md](spell-granting-across-silos.md) | Why feats/races/classes grant spells differently when they bottom out at the same primitives (`spells-known`, `spell-selection`); the per-silo wrappers, the gaps, the route-one-key fix. | High — chain traced; some paths flagged NOT-TESTED |
| [spell-slot-progression.md](spell-slot-progression.md) | How spell *slots* are computed: the overloaded `:level-factor` (table + multiclass + prepared count; why Artificer can't be expressed), warlock pact vs normal multiclassing, and the bucket-of-tables design. Test-backed (`spell_slot_characterization_test`). | High — traced + tested |
| [declarative-grant-vocabulary.md](declarative-grant-vocabulary.md) | DESIGN: a builder-UI vocabulary (`<grant spell>` / `<select spell>` + filters) compiling to existing primitives; the Magic-Initiate special case; open scope flags. | Mixed — verified + DESIGN |
| [class-features-and-mechanization.md](class-features-and-mechanization.md) | How class features are structured (inline, class-coupled, captured code), the rolling layer + mechanization ceiling, the registry/`compile-feature` direction (data spec → real fighter/rogue output, overridable fields). | Mixed — VERIFIED/USER-REPORTED/SPECULATION flagged |
| [class-feature-catalogue.md](class-feature-catalogue.md) | Per-class inventory of all 12 base classes (C1): distinct auto-features, sizing, and the odd cases the registry must handle (multi-source counts, resource pools, build-context interpolation, multi-part features, attribute interdependence). | High — all 12 read |
| [building-a-class-from-builders.md](building-a-class-from-builders.md) | What a homebrew class can be assembled from today: the `homebrew-class` spec, what `subclass-option`/`spellcasting-template` accept, the invocation/boon pool pattern, the `ua_artificer` witness, and the real gaps. | High — code |
| [armor-class-computation.md](armor-class-computation.md) | How AC is computed (max-of-alternatives + sum-of-bonuses), the channels, custom-AC friction. Test-backed (`ac_characterization_test`: armored dex-cap, unarmored tie-break, natural-AC duplication; the `:max-dex-mod`-ignored + cljs-nil-add findings). | High — traced + tested |
| [runtime-toggles-and-conditional-modifiers.md](runtime-toggles-and-conditional-modifiers.md) | How a player toggle (equipped armor/magic items) changes computed sheet stats — the `equipped?`-flag + deferred-modifier mechanism; basis for "while active" features. | High — code |
| [ability-increase-spreads.md](ability-increase-spreads.md) | `:ability-increases` as `[amount pool]` pairs, the `:save` rider, multi-silo containment, the feat dual-format reader. Decision D33. | High — JVM + cljs + E2E |
| [dropdown-value-coercion.md](dropdown-value-coercion.md) | The `<select>`-yields-a-string footgun and the `:typed?` template. Decision D32. | High |
| [starting-equipment.md](starting-equipment.md) | Starting-equipment data shape, consumption, the class-builder UI, and the base+delta export encoding (`collapse-class`/`expand-class`). | High |
| [custom-content-lifecycle.md](custom-content-lifecycle.md) | The three custom-content mechanisms (inline custom, full builders, server-backed magic items) and the false-flagged reconciliation. | High |
| [content-tiers-and-key-resolution.md](content-tiers-and-key-resolution.md) · [key-collision-behavior.md](key-collision-behavior.md) · [library-management-and-conflicts.md](library-management-and-conflicts.md) · [keyword-trap-name-repair.md](keyword-trap-name-repair.md) · [orcbrew-format-versioning.md](orcbrew-format-versioning.md) · [demo-content-tier.md](demo-content-tier.md) | Content library, key resolution, import/export conflicts, format versioning, and the demo tier. | High |
| [built-character-representation.md](built-character-representation.md) | Two halves of the entity-spec engine: READING a built character (deferred `:entity-fn?` values, `entity-val`, why it has no flat spec) and WRITING against it (`?attr` refs are rewritten at compile time, so only a MACRO can splice one into generated code — the rule that decides what is expressible, and why the Dual Wielder feat was hand-written for years). | High — code |

### Search surfaces
| Document | Topic |
|----------|-------|
| [topic-index.md](topic-index.md) | **Generated search surface** — every document with its distinctive vocabulary and section headings. Use it to find which document owns a topic. For *whether* something has been looked at, grep the corpus: `grep -ril "<term>" docs/kb/` — measured, that answers more than any index here does. Regenerate with `lein with-profile +tools run -m orcpub.topic-index`. |
| [before-you-start.md](before-you-start.md) | **Review lessons indexed by TASK, not by topic.** What to check before designing a control, adding a CSS class, changing how something is rendered, converting a builder, borrowing a value from a mock, or believing a CSS change worked. Every entry is a rule a review had to supply, with one line of evidence. |

### Builders + authored mechanics (this branch, 2026-09)
| Document | Topic | Source quality |
|----------|-------|---------------|
| [builder-form-schemas.md](builder-form-schemas.md) | **Read before touching a builder form.** The three content tiers, what is shipped vs proposed, the `:rows` node design and its D9 storage-shape question, the Track E plan (E0–E5) with acceptance tests, why triggers are sheet entries not conditions, and the corrected 16-builder census. | Mixed — measured + DESIGN flagged |
| [builder-disposition-audit.md](builder-disposition-audit.md) | **Read before converting any of the six unconverted builders.** Every widget in race, subrace, class, subclass, background and monster: what it writes, what reads it, and what it becomes (GRANT row / EFFECT row / typed field / shared / bespoke). 35 widget instances become one node. Monster is a stat block and grants nothing; the four pools still to register; two open design points. | High — measured from the assembly paths |
| [feat-builder-audit.md](feat-builder-audit.md) | **Read before converting or extending the feat builder.** Widget → `:props` key → compiler arm for all 14 widgets. The grant/select verb split (both halves built, wired to opposite silos), the six AC/weapon props the compiler accepts and the builder cannot author, one dead control, the unlisted third grant vocabulary in the Custom Feat path, and why feat is last rather than next. Records the five storage shapes one language question is persisted in, and `grant-selection` — the generic pool-agnostic, owner-agnostic hook that collapses them, now wired to a second silo (`race-option`) with `:languages` registered. | High — builder diffed against compiler |
| [builder-conversion-gallery.md](builder-conversion-gallery.md) | **Pictures.** Each converted builder form side by side with the bespoke one it replaced — real app, captured by script — plus the measurements (controls, ambiguous labels, page height) and the code for each pair. Includes the numbers that do *not* flatter the change. | High — measured |
| [fighting-style-authoring.md](fighting-style-authoring.md) | The fighting-style **decisions**: feat-grant wired; the class-path divvying rule (`:classes`, absent = all); the `:ref` footgun; Phase B (builder) — now built. | High — decision record |
| [fighting-style-vocabulary-gap.md](fighting-style-vocabulary-gap.md) | What a style builder must express, measured against 14 published styles: 3 authorable, engine hooks for 8, the wielding-predicate need, what only engine work can reach. Includes the round-trip and usability E2E results. | High — measured |
| [armor-class-refactor.md](armor-class-refactor.md) | **The current AC model** + the refactor ledger: universal authored shape, channel trim 18→10, engine namespace, parity sweep at 0, the bucketing rejection (measured), the Bracers bug, corrections. `armor-class-computation.md` is its historical predecessor. | High — traced + tested |
| [requirements-registry.md](requirements-registry.md) | **Reference for `requirements.cljc`** — the facts an effect can gate on (`:dual-wielding?`, `:armored?`), the entry shape, the three gates, the three-state semantics, how to add one, and why entries hold predicates over a context rather than condition forms (a macro captures forms at compile time, so a runtime registry of them is unbuildable). Design record at the tail. | DESIGN — evidence verified |
| [authoring-vocabulary.md](authoring-vocabulary.md) | **Read before adding a tag, condition, or one of the little lookup tables.** What `::melee?` means, why `tag->flag` cannot be derived (three of thirteen are spelled differently), the shared three-state / unknown-tags-ignored semantics and the forward-compatibility they buy, and the real test for whether a table earns its place — "how many places must agree", not "how many entries". | High — code-verified |
| [edition-drift.md](edition-drift.md) | 2014 vs 2024 two-weapon fighting, and which LAYER each difference hits — content values, which vocabulary applies, where a fact is stored, how content is categorised. All four absorbed by the current shapes. Includes two data traps: Light Crossbow is not Light, and the Dual Wielder implementation dropped "melee". | Measured from 5etools data |
| [weapon-data-model.md](weapon-data-model.md) | Every field on a weapon, derived from the loaded data: absent-vs-false flags, three fields missing their `?`, maps that look like flags, and the invariants the attack vocabulary leans on. | High — data-derived |
| [homebrew-override.md](homebrew-override.md) | The mug icon: a per-selection switch that waives *selection* rules and never touches computed values — the design consequence for restrictions. | High — code |
| [rules-override-layer.md](rules-override-layer.md) | PROPOSAL, cross-branch: DM-issued grants/permissions as a ledger above the silos; naming candidates; should ride `:props`. | DESIGN |
| [frontend-redesign-parallel-work.md](frontend-redesign-parallel-work.md) | **Read before styling anything.** `port/redesign-on-refactor` is a theme-token design system (themes as data, per-theme `:accent` wired to `--accent`, a 610-line searchable option picker). The builder CSS now consumes `var(--accent)`; the light/dark surface tokens are an open follow-up. | High — read from the branch |
| [fonts.md](fonts.md) | Open Sans self-hosted; CSP tightened; regeneration recipe. | High |

### Process & infrastructure
| Document | Topic |
|----------|-------|
| [verification-discipline.md](verification-discipline.md) | Lessons on assumptions/thoroughness + the **standing rule**: don't call it verified without walking it up and down and backing it with a falsifiable test (or the full chain); and how a characterization test doubles as the old-vs-new comparison instrument. |
| [data-safety-layers.md](data-safety-layers.md) | **Standing design rule** for robustness against bad data: the four layers (prevent / harden / heal / surface), the rule that picks between them by lifecycle, the `save ⊆ load` + diagnosable-rejection invariants, worked examples, and anti-patterns (silent-drop, speculative-heal). From the cross-branch toggle-nil review. |
| [cljs-headless-harness.md](cljs-headless-harness.md) | How to run the cljs test suite headless in a container (compile `fig:test` → serve `target/test` → Playwright Chromium) — the gate for cljs-only code; plus the full-app click-through E2E (`test/e2e/`). |
| [dropdown-value-coercion.md](dropdown-value-coercion.md) | The `<select>`-always-yields-a-string footgun — bit this branch twice (breath weapon, then floating ASI) because the first fix lived only in a code comment — and the `:typed?` template that round-trips the value's type. Decision **D32**. | High — git-verified provenance + E2E-backed |
| [ability-increase-spreads.md](ability-increase-spreads.md) | `:ability-increases` as terse `[amount pool]` pairs — fixed/floating/Tasha's "+2/+1"/arbitrary custom spreads, the "different abilities" rule, the opt-in `:save` rider, the standalone `:save-proficiencies` tool, multi-silo containment, and the feat dual-format reader. Compile + assign-from-bag widget + authoring. Decisions **D33** (terse export data). | High — JVM + cljs + E2E-backed |
| [documentation-discipline.md](documentation-discipline.md) | When a change earns a doc; update in place vs record reversals; current-truth-first structure; the audit history; the git-push reminder hook. |
| [backfill-ledger.md](backfill-ledger.md) | Living list for converging bespoke paths onto the pool/grant standard (D29) + deprecating code (D34): migration recipe, the ledger table, and the watch-list. | Process doc |
| [test-suite-state.md](test-suite-state.md) | Verified record of what the test suites run and gate, the pre-existing cljs failures (classified), and open decisions. |
| [character-validation.md](character-validation.md) | Preserves the intent of *validating a character* + a falsifiable replacement charter (own-branch). |

## Agent and operations KB

### Documents


| Document | Purpose |
|----------|---------|
| [character-image-routes.md](character-image-routes.md) | How a character portrait reaches the sheet: CORS vs hotlink blocking measured against real hosts, two withdrawn conclusions, what the browser cannot be made to do, and how to reach the real internet from a browser test here |
| [documentation-discipline.md](documentation-discipline.md) | What earns a KB doc; verify don't remember; update in place; record reversals; index it or it is invisible; arm the hooks |
| [claude-branch-triage.md](claude-branch-triage.md) | Which of the 37 `claude/*` auto-branches hold work that exists nowhere else: content-based verdicts (4 already squash-merged despite the ancestor check, 5 duplicated elsewhere, 16 unique, 12 disposable), the method that beats reachability, and the live Robe of the Archmagi AC bug it turned up |
| [unsaved-knowledge-on-prunable-branches.md](unsaved-knowledge-on-prunable-branches.md) | What dies with the branches slated for deletion: nine docs to lift first, a Datomic crash analysis deleted by the commit meant to relocate it, and why `check-docs.sh`'s superset check cannot see KB drift — it compares against `develop`, which carries no `docs/kb/` at all |
| [rescued/README.md](rescued/README.md) | Docs copied verbatim off dead or prunable branches because they existed nowhere else — archival snapshots, NOT accepted KB; the manifest says which to promote, which to lift in part, and which are awaiting a read |
| [empty-keyword-corruption.md](empty-keyword-corruption.md) | "A single colon is not a valid keyword": how an empty name took a whole saved character down, why the throw was uncatchable at the call site, and the three places the shipped defence lives |
| [plan-669-merge-verification.md](plan-669-merge-verification.md) | How to take the #669 branch safely: the five units it actually contains ranked by blast radius, the pre-flight results, two behaviour changes that are not the fix, a stage-gated test matrix, and what to do when each stage fails |
| [filtered-list-staleness.md](filtered-list-staleness.md) | LIVE: My Items / My Spells show a filter result cached by the event layer, so the list goes stale when content changes (Orcpub#669). Why the subscription looks correct in review |
| [multi-tab-character-contamination.md](multi-tab-character-contamination.md) | LIVE: the character draft is cached under one localStorage key with no id, so two builder tabs overwrite each other and edits land on the wrong character. Notes carry :db/noHistory, so the loss is unrecoverable |
| [datomic-crash-analysis.md](datomic-crash-analysis.md) | Transactor crash forensics: the crash mechanism, GC's role, why writeConcurrency=4 hurts with H2 storage, recovery time and fix options |
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
| [orcbrew-level-modifiers.md](orcbrew-level-modifiers.md) | The value vocabulary a homebrew item can use: the twelve `:level-modifiers` types and their `:value` domains, `:props` consumed on classes with no editor, the namespaced-ability-key rule, why none of the three validation layers checks shape, and `:saving-throw-advantage` computing into a dead end |
| [homebrew-class-spellcasting.md](homebrew-class-spellcasting.md) | Homebrew class spellcasting: slot schedules, known-spells, prepared casters, integration risks |
| [spell-selection-source-fix.md](spell-selection-source-fix.md) | Cantrip/spell-selection source-poisoning fix (`fix-cantrips-selection-bug` branch): feature changes, the select-keys toggle gap, reconciler plugin-scope, BOM non-issue, e2e-verified remediation |
| [lein-uberjar-hang.md](lein-uberjar-hang.md) | lein compile hang: Datomic Peer non-daemon threads, timeout workaround, build profile fix |
| [docker-testing-guide.md](docker-testing-guide.md) | Docker testing: 46-test suite, manual smoke tests, every gotcha (H2 lock-in, env -u, ports, transit, piped input, jq nulls) |
| [class-builder-extraction-plan.md](class-builder-extraction-plan.md) | Plan for the first incremental view decomposition: extract the class builder from the views.cljs monolith (reuses views-builders-split rules; the anti-views-extraction cadence) |
| [starting-equipment-override-ledger.md](starting-equipment-override-ledger.md) | Design: template + ledger override for starting equipment — stable-key addressing (not names), add/replace(=remove) ops, SRD-key freeze, derived-diff, edge cases |

### From `feat/option-picker`

- **[equipment-option-picker.md](equipment-option-picker.md)** -- the Equipment tab's 1037
  `<option>` elements, five controls measured against each other, and why the winner is a
  filtering combobox on the native Popover API (1541 DOM nodes vs 2558 native). Records three
  withdrawn claims: that datalists cannot be styled, a 12-row cap that made the list
  searchable but not browsable, and prefetch-then-expand as a fix for the mount cost.
- **[fast-browser-probes.md](fast-browser-probes.md)** -- why a measurement loop takes 40
  minutes and how to make it 4. The `.lein-env` race that boots the e2e server against the
  wrong database, and why `character_image_capture` runs 393s.

### Findings moved from `integration`

These were written on feature branches and folded into `integration`'s `docs/kb/`
before that tree was retired. Each records what was measured, what turned out to be
wrong, and why.

#### Performance

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

#### Plans

- **[extras-definitions.md](extras-definitions.md)** -- *not built.* What each kind of Extra
  needs to function. One creature record with capabilities toggled rather than a type per kind,
  because pets get promoted to sidekicks and a type system would force delete-and-recreate.
  Copy-on-adopt rather than live reference. Settles where per-creature state can live.
- **[plan-npc-statblock-customizer.md](plan-npc-statblock-customizer.md)** -- *not built.*
  A DM tool: change an NPC's ability scores and have AC, HP, attacks, save DCs and skills
  recompute. The monster builder exists but derives nothing -- `monsters.cljc` has one
  function. The hard part is that CR drives proficiency bonus and the edits move CR.
- **[duplicate-key-durability-roadmap.md](duplicate-key-durability-roadmap.md)** -- *not built.*
  Why duplicate keys keep coming back and the plan to stop them. Extends
  `RECONCILIATION-LOG.md` phase 3 and `name-to-kw-audit.md` section 6.
- **[share-custom-items-plan.md](share-custom-items-plan.md)** -- *not built.* Making a
  character's custom magic items, weapons and armor apply on a shared sheet opened by a
  different session. Extends the homebrew-embed share feature.
- **[plan-companions-and-wild-shape.md](plan-companions-and-wild-shape.md)** -- *not built.*
  Fall-update research into pets, summons and Wild Shape. Opens with the domain map --
  seven kinds of peripheral creature (bonded, conjured, raised, bound, people, property,
  transformation) and what the app has for each. The druid flow -- prepare a short
  list of forms, pick one, view merged stats, print them as cards -- lands almost entirely on
  machinery the app already has (prepared spells, `filter-monsters`, two existing PDF card
  families). Blocked on one type fix: monsters store CR as a number, the character stores it
  as a string. Covers what gets printed per kind (card vs sheet, decided by whether the
  creature persists), what gets a digital view, and how homebrew declares a companion or a
  summon (the specs are open `spec/keys`, so the key is additive). Wild Shape is SRD and ships in base content; Beast Master and Circle of the Moon
  are `#_` discarded as non-SRD, so companions must be plugin-driven. Two slice sequences,
  Wild Shape and companions, the second depending on the first. Carries a revision log.

### Operations

- **[secrets-in-boot-output.md](secrets-in-boot-output.md)** -- the database password was in
  every boot log, in four places rather than one, because `ex-info` data is log output too.
  Covers redacting at the boundary, the near-miss that would have broken every connection,
  and two boot-banner designs that reported a rejected setting as though it had taken effect.

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

#### Domain

- [pdf-form-techniques.md](pdf-form-techniques.md) -- what works against the real PDF
  templates, and what does not.

#### Browser probes

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

### Human-Facing Docs (Copies)

The `docs/migration/` and `docs/MIGRATION-INDEX.md`, `docs/JAVA-COMPATIBILITY.md`, `docs/ENVIRONMENT.md` are copies of the human-facing docs from `breaking/2026-stack-modernization`. They're included here so integration branches don't need to cherry-pick from the feature branch.

### Known Corrections

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
