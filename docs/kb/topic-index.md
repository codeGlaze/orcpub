# Topic index — what has already been looked at

**GENERATED — do not edit.** `docs/kb/tools/topic-index.sh`

## Grep the corpus first

```
grep -ril "<term>" docs/kb/
```

**That is the search.** This file is for orientation — what each document is about, and
which one owns a topic — not for recall. Measured against fourteen realistic queries the
corpus answered **all fourteen**; this index answered **nine**. It cannot match multi-word
phrases (`import conflict`, `spell list`) because it is built from single words, and a
topic mentioned once loses its place to one discussed throughout. Use it to find the right
document, then read that document; use grep to find out whether anyone has been there.

Each document is listed with its filename (hyphenated **and** spaced, because queries are
typed with spaces), the words that most distinguish it from the rest of the corpus, and
every section heading it contains.

---

## DATOMIC_JAVA21_TEST_RESULTS.md

_DATOMIC_JAVA21_TEST_RESULTS · DATOMIC_JAVA21_TEST_RESULTS_

**topics:** 5703, activemq, apache, artemis, connection, connections, datomic, free, https, java, peer, peer-to-transactor, pro, releases-pro, ssl, tls, transactor, unit

- Executive Summary
- Test Results Matrix
- Detailed Test Procedures
- Test 1: Transactor Startup
- Test 2: Peer Library Loading
- Test 3: Unit Tests (Mocked)
- Test 4: Peer-to-Transactor Connection
- Root Cause Analysis
- SSL/TLS Compatibility Issue
- Impact Assessment
- What Works
- What Doesn't Work
- Production Impact
- Recommendations
- Option 1: Migrate to Datomic Pro (Recommended)
- Option 2: Stay on Java 11
- Option 3: Test Java 17 (Not Recommended)
- Test Environment Details
- System Information
- Datomic Configuration
- Network Verification
- References
- Test Logs

## DEPENDENCY_VALIDATION.md

_DEPENDENCY_VALIDATION · DEPENDENCY_VALIDATION_

**topics:** 2-jre, automated, backward, com, compatibility, cves, dependencies, dependency, fasterxml, github, guava, jackson, libraries, maintains, outdated, pedestal, pre-upgrade, vulnerabilities

- Summary
- Changes Made
- Jackson Libraries
- Guava
- Security Validation
- GitHub Advisory Database Check ✅
- Known Issues Addressed
- Dependency Analysis
- Location in project.clj
- Transitive Dependency Impact
- Version Selection Rationale
- Jackson 2.15.2
- Guava 32.1.2-jre
- Compatibility Assessment
- Java Version Compatibility
- API Compatibility
- Pedestal Compatibility
- Testing & Validation Strategy
- Automated Testing
- Manual Validation Checklist
- Risk Assessment
- Risk Level: LOW-MEDIUM
- Mitigation Strategy
- Recommendations
- For Reviewers
- For Follow-up
- References
- Conclusion

## SESSION-SUMMARY.md

_SESSION-SUMMARY · SESSION SUMMARY_

**topics:** -----, 2026, added, claude, csp, documentation, figwheel, garden, issue, issues, january, loader, menu, port, profile, scripts, submenu, tmux

- Final State
- Key Decisions Made
- Menu Structure
- Quick Reference
- Verification Commands
- Codespace Rebuild Checklist
- Design Patterns Adopted
- Code Review Improvements (January 2026)
- High Priority (Fixed)
- Medium Priority (Fixed)
- New Functions Added
- Service Behavior (Final)
- Claude Code Data Persistence (January 2026)
- Setup
- How It Works
- Manual Setup (if needed after rebuild)
- Key Code Changes Made
- scripts/common.sh
- scripts/start.sh
- Files Modified in This Session
- Troubleshooting Notes
- "Datomic pro not found" error after script move
- Services don't return control after starting
- Port wait hangs forever when process dies
- Menu Error Handling Improvements (January 2026)
- Root Cause
- Fixes Applied to menu
- Fixes Applied to stop.sh
- Pattern for Menu Error Handling
- Port Forwarding Analysis (January 2026)
- Explanation
- Not a Production Concern
- To Disable Auto-Forwarding (Optional)
- Additional Files Modified
- External Code Review (January 2026)
- High Priority Issues (All Fixed)
- Medium Priority Issues (All Fixed)
- Recommendations for Future Work
- Session Continuity Notes
- Key Files for Context Recovery
- Session Compactions
- CSP Deep Dive & Custom Loader (January 2026 - 8th Session)
- Key Findings
- The Problem
- What We Discovered
- Solutions Implemented
- Files Created/Modified
- How the Custom Loader Works
- Why This Matters
- Research Sources
- Documentation Completion (January 2026 - 9th Session)
- Tasks Completed
- Documentation Added
- Key Insight Documented
- Leiningen Profile Cleanup (January 2026)
- Performance Fixes
- Dependency Cleanup
- New Profiles
- Menu Updates
- User Creation
- History
- Figwheel & Server Startup Fixes (January 2026 - 5th Session)
- Figwheel Startup Fixes
- Server Background Mode Fix
- Devcontainer Port Labels
- Files Modified
- Service Launch Order
- Nonce-Based CSP Implementation (January 2026 - 6th Session)
- Problem
- Solution
- Key Discovery: ClojureScript Dev Builds Incompatible
- Files Created
- Implementation Challenges Encountered
- Verification

## UPGRADE_DEPENDENCIES.md

_UPGRADE_DEPENDENCIES · UPGRADE_DEPENDENCIES_

**topics:** allows, clojurescript, closure, csp, dev, ecosystem, figwheel-main, java, loader, nonce, nonce-based, nonces, pedestal, protection, scripts, shadow-cljs, support, xss

- Java 9+/21 & Servlet API
- Datomic Pro
- Pedestal 0.7.x - Content Security Policy (Nonce-Based Implementation)
- Technical Details & Lessons Learned
- Deep Dive: CLJS Ecosystem and CSP Support
- Other Notable Upgrades
- How to Use This Document

## UPGRADE_PLAN.md

_UPGRADE_PLAN · UPGRADE_PLAN_

**topics:** -------------, 5697, cljsjs, clojure, datomic, figwheel-main, guava, jackson, jdk, jetty, lein-figwheel, npm, pedestal, pro, react, shadow-cljs, src, upgrade

- Overview
- 🚨 Major Changes (January 2026)
- Summary of Breaking Changes
- 1. Datomic Free → Datomic Pro
- 2. Pedestal 0.5.x → 0.7.0 (Interceptor Changes)
- 3. lein-figwheel → figwheel-main 0.2.20
- 4. ClojureScript Browser Detection Rewrite
- 5. Test Library: datomock Upgrade
- 6. Dependency Version Summary (Current State)
- 7. Known Constraints
- 8. Development Commands Quick Reference
- ✅ Completed Upgrades
- Phase 1: Security-Critical Updates (Complete)
- Phase 2: Core Clojure Upgrade (Complete)
- Phase 3: Pedestal Stack Upgrade (Complete)
- Phase 4: Frontend Upgrades (Complete)
- Phase 5: Additional Library Upgrades (Complete)
- Phase 6: Database Migration (Complete)
- Current observations (January 2026)
- Goals (measurable)
- Scope & Constraints
- High-level roadmap & milestones
- Detailed tasks (immediate)
- Next phase (in progress)
- ⚠️ Known Build Warnings (Third-Party, Unfixable)
- 1. garden.color/abs shadows clojure.core/abs
- 2. datomic.common/requiring-resolve shadows clojure.core/requiring-resolve
- 3. Datomic Free + Java 21 Compatibility Test Results
- 3. PDFBox font fallback warnings
- 🔮 Future Work: Unified Date/Time Library
- 🧪 Validation Commands
- What each command validates:
- Branching & PR rules
- Testing & CI
- Risk & Rollback
- Notes / Helpful commands
- Audit results (static)
- Audit results (live — deps tree)
- Security pins — STATUS as of 2026-09-24 (verified against origin/integration)
- Next steps (my plan)

## ability-increase-spreads.md

_ability-increase-spreads · ability increase spreads_

**topics:** asi, asis, authoring, breakdown, con, feat, fixed, floating, increment, mental, pool, released, rider, save, spread, standalone, terse, widget

- The format
- Examples
- Save proficiencies
- How it compiles (opt5e/compile-ability-increases, options.cljc)
- How it renders (ability-bag-assigner, characterbuilder.cljs)
- Authoring
- Backward compatibility (D9)
- Containment across silos (multi-source)
- Tests

## account-flows.md

_account-flows · account flows_

**topics:** account, composition, corpus, email, login, mail, meter, oracle, password, refused, reset, server, signing, somebody, suite, times, username, veto

- 1. The reason none of it had coverage
- Traps inside the harness itself
- 2. The stale-artifact asymmetry
- 3. The password rules, and what was decided
- Why no composition rules
- Why 1000 and not "any hit" — a decision with a rejected alternative
- Separator stripping
- 4. Four defects a green build did not catch
- 5. Components shared, composition not — the recurring shape
- 6. Security decisions, with their rejected alternatives
- The reset page's identifier check
- Login says nothing about which half was wrong
- The account limit sits on the FAILURE path
- Notifications
- 7. Open, and deliberately so
- 8. Corrections — claims made here that were wrong

## armor-class-computation.md

_armor-class-computation · armor class computation_

**topics:** ability, armor, armored, barbarian, bonus, channel, channels, con, defense, dex, mail, max, monk, scalar, shield, tie-break, unarmored, worn

- Verified behavior — TEST-BACKED (accharacterizationtest.clj, JVM)
- The model — VERIFIED (templatebase.cljc:35-88)
- The channels a feature can use — VERIFIED
- Where each kind of custom AC goes — VERIFIED mechanism
- Known friction — VERIFIED facts (the "issues" are analysis)
- Design proposal — DESIGN, not built

## armor-class-refactor.md

_armor-class-refactor · armor class refactor_

**topics:** armor, authored, barbarian, bracers, calculation, channel, defense, leather, monk, natural, plate, prop, scalar, shield, sum, tie-break, unarmored, worn

- Current state — read this first
- The channel trim — DONE. 18 attributes → 10
- How the built-ins moved
- The one shim: ?natural-ac-bonus
- LANDED: the AC engine moved to orcpub.dnd.e5.armor-class
- DECIDED: bucketing rejected, best-ac deleted
- Two things the rejection does NOT mean
- The Bracers fix is portable to integration on its own
- Hazard to characterize BEFORE trimming ?unarmored-ac-bonus
- The approach, and why it changed
- Landed
- The authored shape
- Parameters are not calculations
- Custom armor
- LANDED: :lizardfolk-ac compiles to the universal shape — parity sweep at 0
- :tortle-ac split into a calculation and an AC suppression
- LANDED: shield and character magic moved into ?ac-bonus-fns
- Two kinds of magic — name them differently
- Three mechanisms for one job (the D29 problem, concretely)
- Natural armor: which :armor? tag, measured
- Traced: what :lizardfolk-ac actually computes
- What the migration must not drop
- A gap in the tag set
- Remaining
- Channel count is going the wrong way
- Attribution, since this refactor keeps circling the same model
- REVISED: extract the AC namespace instead of deleting it
- Ledger
- Corrections

## auth-state-in-app-db.md

_auth-state-in-app-db · auth state in app db_

**topics:** 401, 669, account, deletes, env-and-auth, filtered-list-staleness, kaylee, loader, log, logged, login, merges, reframe-subscription-patterns, rejected, response, server, todo, token

- The shape
- Read the token with get-auth-token
- Why :user-data is nested twice
- Why db :user holds only the follow list
- A loader's 401 logs out
- Related

## authoring-vocabulary.md

_authoring-vocabulary · authoring vocabulary_

**topics:** author, authoring, crossbow, dual, feat, form, ignored, iterating, melee, predicate, spec, table, tag, tags, vocabulary, weapon, weapons, wielder

- The syntax first
- Why the map can't be derived
- The shared semantics: three-state, unknown-ignored
- What the values are, and why they differ
- When to build one of these (and when not)
- Growing one
- A predicate is not the same as a spec — prefer the spec
- Where these live

## backfill-ledger.md

_backfill-ledger · backfill ledger_

**topics:** 2026-09-04, 2026-12-08, bespoke, converged, d29, d34, deleted, deprecated, feat, grant, job, ledger, parity, pool, re-pointed, retained, struck, sweep

- When something lands here
- Migration recipe (per item)
- Ledger
- Watch-list (candidates — pool/grant doesn't fully subsume them yet)

## before-you-start.md

_before-you-start · before you start_

**topics:** authored, borrowing, breach-threshold, check, css, expression, form, green, item, lesson, machine, no-composition-rules, pin, predicts, review, screenshot, signing, vanished

- Already enforced — you do not have to remember these
- Judgement calls — no test can catch these
- Changing what a content item STORES (a new key, a renamed key, a widget that writes differently)
- Before designing anything (a control, a palette, a layout)
- Before borrowing a value from a mock or another branch
- Before converting a builder
- Before passing a data map as a Reagent component's first argument
- Before believing a CSS change worked
- Before reporting a UI change as done
- How this page stays small

## blank-env-values.md

_blank-env-values · blank env values_

**topics:** account, auto-verify, blank, bypass, configured, docker, email, empty, environ, environment, guard, instance, operator, opt-in, registration, send, smtp, unconfigured

- Why it kept happening
- What it cost
- The auth bypass, in detail
- The fix: a rule with teeth
- The exception, and why it is not a wart
- Verifying a change like this against Docker
- Three layers of guard, each added only after the next gap bit
- The transferable question
- Open: registration lockout when SMTP is unset (worse than the bypass)
- How the registration piece was resolved (2026-09-23/24)
- Why the opt-in, and what is not reachable
- OPEN — fold into the boot report when integration merges down
- Deploying this to a live instance
- A second finding from the same sweep
- Corrections to other docs
- Related

## browser-probe-registration.md

_browser-probe-registration · browser probe registration_

**topics:** 2026-09-20, asserting, assertion, belt, caring, conventions, env, factored, guard, image, package, probe, probes, registering, runner, sweep, untimed, var

- 1. The browser: findChrome(), not an env var
- 2. The output format is the assertion count
- 3. No baseline entry means the shortfall guard is off
- Two more the runner tells you about, if you read its output
- What good looks like

## builder-conversion-gallery.md

_builder-conversion-gallery · builder conversion gallery_

**topics:** ---, assets, bespoke, builder-comparison, chip, chips, control, controls, conversion, duration, form, heading, height, jpg, layout, mockup, row, rows

- Pair 1 — Language builder (tier 1): 21 lines → 1
- The code
- Pair 2 — Fighting Style builder (tier 2): flat fields → :rows
- Empty
- The same style authored — +1 AC, +2 attack, +2 damage
- Measured, not eyeballed
- What the comparison caught in my own work
- Pair 3 — Spell builder: 86 lines → 12, and the first :boolean
- What it needed that did not exist
- What the pin caught, which the form hid
- The regression this conversion shipped first, and the design pass
- Vertical rhythm, and the class-name collision behind it
- The layout is a GRID, because flexbox cannot align columns
- Two bugs this round, and one was in the measurement
- Re-measured like for like, on the corrected metric
- Three additions, each deliberate
- Balance pass
- On a phone
- The combo was invisible
- Every toggle is a chip
- Page stays beside the flags
- Still open on this pair
- Pair 4 — what a bespoke rows widget costs (not yet converted)
- Every builder — converted, and what the rest actually need
- The four things that would unlock all nine
- Encounter is not the tier-3 proof, and that is worth recording
- See also
- The design pass — three gaps, and the grouping question
- Does grouping help? Yes — one heading, not more boxes
- The three gaps
- What it looks like as effects are removed
- The grand tour — every content type, authored to used
- How much of this is reusable?
- Was the "easy render register" actually easy? — an accounting
- For a simple builder: yes, unambiguously
- For spell: no, and the line count says so
- What it cost the framework
- Was it confusing? In one specific place, yes
- What that implies for the remaining nine

## builder-disposition-audit.md

_builder-disposition-audit · builder disposition audit_

**topics:** ---, arm, bespoke, disposition, effect, extensible, feat, grant, legacy, monster, pool, race, row, rows, shim, widget, widgets, writes

- Legend
- Race — 152 lines, 19 widgets
- Subrace — 129 lines, 15 widgets
- Class — 268 lines, 15 widgets
- Subclass — 105 lines, 13 widgets
- Background — 46 lines, 11 widgets
- Monster — 233 lines, 12 widgets — a stat block, not a character
- ⚠️ REFRAMING (2026-09-08) — most of these are TEMPLATES with frozen parameters
- A THIRD shim class: frozen boolean → parameterized shape
- Templates carry the SENTENCE, and that is the PDF path
- OPEN — the scenario vocabulary: port tag->flag, do not invent
- What the six tables add up to
- Can the pools be registered? Yes — all four, today. Are they extensible? No, and that is fine.
- The 35 deletions — replacement and shim, one row each
- OMV (port/redesign-on-refactor)

## builder-form-schemas.md

_builder-form-schemas · builder form schemas_

**topics:** ---, bespoke, builders, creatures, feat, fields, form, fragment, framework, group, june, node, schema, tier, titled, traits, type, widgets

- 0. Three tiers of content type
- 1–2a. The model and the field node
- 2b. Group node — PROPOSED, not built
- 3. HOW TO — only what is not in content-extensibility-framework.md §2e
- 4. Triggers are not conditions
- Conditions — the app evaluates these
- Triggers — the app CANNOT evaluate these
- 5. Reconstructing the existing builders
- Two gaps the survey found before any code was written
- Converting the rest
- 5b. The OVERLAP map — measured 2026-09-06
- The widgets that are already shared
- CORRECTION (2026-09-07): "implemented twice" conflated NAME with SHAPE
- The original finding: 27% understates it
- Per builder: how much is already shared
- What this changes about the plan
- 6. Track E — the plan (pulled forward 2026-09-05; status lives in roadmap.md)
- The unifying observation
- The :rows node — BUILT 2026-09-05 (this section kept as the design record)
- The :rows node — original design
- Phases (E0–E5) — the roadmap carries status; the acceptance tests are here
- The feat builder, proposed — code and picture
- What "good UX" means here, concretely
- 7. What this does NOT solve
- 8. Open questions

## building-a-class-from-builders.md

_building-a-class-from-builders · building a class from builders_

**topics:** artificer, builders, capability, cfg, companion, content-extensibility, d28, express, first-class, gap, infusions, int-mod, magic-item, multiselect, pool, profs, scaling, tool

- The capability witness — VERIFIED
- What a homebrew class/subclass can already express — VERIFIED
- Infusions ≈ the invocation/boon pool pattern — VERIFIED
- Feature-to-mechanism map (what's data entry vs a real gap)
- Validation rule (D28)

## built-character-representation.md

_built-character-representation · built character representation_

**topics:** -ref, accessor, body, character-validation, computed, deferred, entity, flat, literally, macro, ordinary, plain, predicates, realized, spec, symbol, test-suite-state, writing

- One-liner
- How it actually works (verified)
- What this means for you
- The other half — WRITING against it, and why it needs a MACRO
- ?attr is not a variable — it is rewritten at compile time
- The consequence: a plain fn cannot read ?attrs
- The rules that follow
- Where it bit us (this session)
- Anchored in code

## character-image-routes.md

_character-image-routes · character image routes_

**topics:** 393, acao, advice, bearing, browser, clipboard, curl, host, hosts, image, picture, pinterest, proxy, serve, server, thumbnail, url, urls

- The rule that decides everything
- Measured, with real URLs
- Trap 1: an invented URL proves nothing
- Trap 2: the ceiling was ours
- Reaching the real internet from a browser test
- What the browser cannot be made to do
- Ordering constraints that bite
- Interface rules these fields follow

## character-naming.md

_character-naming · character naming_

**topics:** aasimar, blank, descriptive, dice, dwarf, elf, fallback, generator, halfling, helper, human, label, manual, name, party, races, random, sex

- Problem
- Solution (implemented 2026-02-22)
- 1. Auto-save: Descriptive label
- 2. Manual save: Random name
- 3. Display fallback: Views helper
- Tracking "was the name set by the user?"
- Name Generator System
- Supported races
- Dispatch mechanism
- Fallback for unsupported races
- Helper function
- Key files

## character-rescue-console.md

_character-rescue-console · character rescue console_

**topics:** account, backup, bundle, colons, defences, depth-aware, empty-keyword-corruption, healer, literals, minified, parties, repair, scan, tool, transit, unpatched, urls, xhr

- 1. When a console tool is the right answer
- 2. Triage: what the user actually reports
- 3. What the tool does
- Reading data that is, by definition, unreadable
- Stopping the crash
- Making the repair permanent
- Saying what actually went wrong
- 4. Gotchas that cost real time
- 5. Verifying a tool like this
- 6. Adapting it for a different corruption
- Provenance

## character-validation.md

_character-validation · character validation_

**topics:** built-character-representation, charter, computed, falsifiable, guard, history, intent, malformed, modernization, own-branch, pdf, proposal, realized, representation, retired, spec, test-suite-state, validation

- The intent worth keeping (do not lose this)
- History (verified)
- Why it can't be revived verbatim (verified)
- Charter — the modern replacement (PROPOSAL)
- Disposition of the broken test

## class-builder-extraction-plan.md

_class-builder-extraction-plan · class builder extraction plan_

**topics:** big-bang, builder, class-builder-first, class-builder-only, extract, extraction, grep, helpers, imports, incremental, mainline, monolith, moves, repoint, shared, starting-equipment, toolkit, toolkit-first

- Why this shape (the lesson from refactor/views-extraction)
- Starting reality (differs from the dead branch's assumption)
- What moves vs what it imports
- Circular-dependency rule (non-negotiable)
- Steps
- Sequencing decision to make at step 1
- What NOT to do
- Prereq

## class-feature-catalogue.md

_class-feature-catalogue · class feature catalogue_

**topics:** arcanum, arts, attack, aura, auto-features, bardic, destroy, divine, divinity, inspiration, lay, level, monk, multi-part, scaling, sorcery, summaries, undead

- The 12 classes (option fn line; distinct auto-features; notable shape)
- Cross-cutting findings — the "odd cases" the registry/compiler must handle
- What this means for the build order (refines the roadmap)
- NOT-EXPLORED (flagged)

## class-features-and-mechanization.md

_class-features-and-mechanization · class features and mechanization_

**topics:** attack, cfg, dice, feature, features, fighter, indomitable, pools, registry, rogue, roller, rolls, scaling, sneak, structured, surge, user-reported, wind

- How a class + its features are structured — VERIFIED (fighter, rogue read)
- Two kinds of feature "mechanics" — VERIFIED
- The rolling layer — VERIFIED (corrects an earlier wrong claim)
- Use/resource counters — VERIFIED
- The mechanization ceiling (where "make it real, not text" stops)
- The code-capture catch — VERIFIED (the thing that makes the registry non-trivial)
- Design direction for centralizing features — DESIGN (not built)
- NOT-EXPLORED / to verify before sizing

## claude-branch-triage.md

_claude-branch-triage · claude branch triage_

**topics:** ---, 171, 2026-01-17, 2026-02-02, 2026-06-10, 2026-09-12, branch, branches, byte-identical, definitions, defs, elsewhere, robe, salvage, shas, superseded, tip, unique

- How this was established, and why reachability was not enough
- Corrections to the starting premise
- The one finding worth acting on
- What is already fixed, so don't re-derive it
- Verdicts
- Already merged into integration — reachability says otherwise (4)
- Superseded — content verified elsewhere (5)
- Salvage — 0 matches anywhere (11)
- Salvage — partial matches, unique core (5 branches, 4 rows)
- Discard (12)
- What is not known
- Revisions

## cljs-headless-harness.md

_cljs-headless-harness · cljs headless harness_

**topics:** backend, case-sensitive, div, dom, driver, errors, failures, floating-asi, harness, headless, html, passing, per-test, race-builder, recipe, rooted, runs, totals

- Build it
- Two ways to run (they differ — pick deliberately)
- Known-good baseline — 2026-09-12: 370 tests / 1742 assertions, 0 failures, 0 errors
- Two things had the suite reporting nonsense, and neither was a test
- Running it from a code branch — four things that stop it (2026-09-13)
- Mode B did not emit the totals line; mode A did
- Gotchas worth remembering
- Full-app headless E2E — render and drive the REAL app UI (not the test build)
- Full content round-trip through the real UI (test/e2e/export-import-use.js)
- Driving interactions (done — committed as test/e2e/race-builder-asi.js)
- Driving the character builder — three gotchas that each cost a debugging pass

## code-comment-style.md

_code-comment-style · code comment style_

**topics:** abbreviations, backstory, categories, ceremony, codetags, coined, comments, concise, density, docstring, docstrings, jsdoc, justification, length, narration, non-obvious, resort, sentence

- Docstrings carry the what
- Inline ;; comments carry the why — but only when it's a constraint
- Density earns length — but only in genuinely confusing cases
- Relating scattered code
- Quick checklist

## content-extensibility-compatibility.md

_content-extensibility-compatibility · content extensibility compatibility_

**topics:** catalog, catalogs, characters, content-extensibility, contract, existing, exported, formats, hosted, invariant, invariants, keys, nets, non-additive, redesign, saved, selection, selection-key

- 1. Persisted formats (verified)
- 1a. orcbrew / plugins (homebrew libraries)
- 1b. Character (strict entity)
- 1c. localStorage
- 1d. Backend
- 2. Who owns what
- 3. Hard invariants (non-negotiable)
- 4. Proposal assessment against the invariants
- Layer 1 — content-type registry: compatibility-neutral
- Layer 2 — catalogs/grants: safe if derived, not reformatted
- 5. Specific risk surfaces (verified)
- 6. Existing safety nets (lean on these, don't reinvent)
- 7. Migration & rollback posture
- Related

## content-extensibility-decisions.md

_content-extensibility-decisions · content extensibility decisions_

**topics:** bespoke, boilerplate, catalog, catalogs, d12, d16, d17, d17b, d19, d23, existing, factories, grant, live, pool, re-derivation, readability, rejected

- Status at a glance
- Part 1 — How the thinking evolved (audit)
- Part 2 — Decision summary
- Part 3 — Late decisions (deflation; these scaled back D2/D3 — but were themselves RE-CENTERED by Part 4)
- Part 4 — Re-centering (these restore the capability D12–D16 over-deflated)
- Part 5 — Mechanization, class features, spell slots (the expansion)

## content-extensibility-direction.md

_content-extensibility-direction · content extensibility direction_

**topics:** allowlist, ancestry, bespoke, descriptor, field, ftd, grant, maintainability, metadata, page-map, parametric, per-type, pool, pools, primitive, registry, vocabulary, wiring

- Why the re-centering (don't misread the deflation)
- The one principle (a constraint, not a ceiling)
- The engine ALREADY supports mix-and-match. The gap is the AUTHORING layer.
- The spine: two words — POOL and GRANT
- Variants — designed in NOW, built LATER (a real pin with a real constraint)
- Maintainability — the GATING requirement (easier to add tooling, not harder)
- Sequencing — flat pools before rich pools
- Next steps (goal: STABILIZE while adding features)
- Validation against official expansion (Fizban's Treasury of Dragons, FTD)
- Builder FORMS are data, not "irreducible per-type work" (109b5dd0)
- Draconic-ancestry builder — DONE end-to-end (0aca6113)
- Foundation: registry DRIVES the layers (the real "fewer files" fix)
- NEXT levers (pick per value)
- PINS (designed-in-now, built-later — do not let these get refactored away)
- Landed since this doc's last revision (2026-09) — read these, don't re-derive
- What already stands (don't redo)
- Deferred — own branch (surface at branch close)

## content-extensibility-e2e.md

_content-extensibility-e2e · content extensibility e2e_

**topics:** appears, backend, boon, catalog, checklist, confirm, console, errors, gate, homebrew, loads, name-keyword, pact, phase, phases, read-seams, skips, spell-selection

- Setup (use the project's standard dev flow)
- Checks
- A. ClojureScript test suite (the JVM gate skips this)
- B. Catalog read-seams — behavior must be UNCHANGED (Phases 1–3b)
- C. name-keyword fix (merged in)
- D. Backward compatibility (non-negotiable — do not skip)
- Feedback format
- What is NOT in scope here

## content-extensibility-framework.md

_content-extensibility-framework · content extensibility framework_

**topics:** ancestry, builder-item, d22, draconic, entry, events, form, framework, generated, grant, hot, irreducible, loops, pool, pools, registry, routes, type

- 1. The mental model (start here)
- 2. The Builder Framework (registry-driven wiring)
- 2a. The single source: contenttypes
- 2b. What's generated from the registry — CURRENT STATUS
- 2c. The wiring HOFs (the trusted thick parts the loops compose)
- 2d. Conventions (agents: follow these exactly)
- 2e. HOW TO ADD A HOMEBREW CONTENT TYPE (current state)
- 3. The Composition layer (pool + grant)
- 3a. Pool
- 3b. Grant
- 3c. Mechanics as data
- 3c′. Where the cost lives — authored-data shape is never a runtime cost
- 3d. Worked example — draconic ancestry (the proven slice)
- 3e. How to add a pool / a grant
- 4. Invariants & gotchas (agents: violating these breaks user data or the framework)
- 5. Verifying changes
- 6. Map of the docs
- MEASURED: the macro does not affect reactivity
- Route trees: /pages/ vs root

## content-extensibility-plan.md

_content-extensibility-plan · content extensibility plan_

**topics:** catalog, compatibility, content-extensibility, content-extensibility-compatibility, content-extensibility-decisions, existing, gate, goal, golden, green, lineage, loosen, phase, phases, registry, revert, snapshots, stop

- Golden rules (read before doing anything)
- The verification gate (exact commands)
- Phase 0 — Build the safety net (no production code)
- Phase 1 — Generic option injector, proven on subraces
- Phase 2 — Migrate subclasses onto the injector
- Phase 3 — Boons and invocations onto grants (the risky migration)
- Phase 4 — Layer 1 content-type registry (independent track; micro-steps)
- Phase 5 — New capability: dragonborn lineage (only after 1–4)
- Stop-and-ask triggers (summary)
- Two standing rules for the catalog/grant phases (3c onward)
- Do NOT
- References

## content-extensibility.md

_content-extensibility · content extensibility_

**topics:** 8-input, 893, 945, boons, bucket-by-key, catalog, catalogs, content-extensibility-compatibility, content-extensibility-decisions, content-extensibility-plan, cross-links, homebrew-builders, parent, positional, route-registration, spa-routing-architecture, views-builders-split, warlock

- The problem
- Current cross-links (verified from code)
- Proposed direction (design — not implemented)
- Layer 1 — content-type registry
- Layer 2 — type-addressed option catalogs + grants
- Suggested next step
- Related

## content-tiers-and-key-resolution.md

_content-tiers-and-key-resolution · content tiers and key resolution_

**topics:** dedup, disable, disable-based, disabled, duplicate-key, example, fork, graduates, item-level, nondeterministic, nondeterministic-override, override, owned, per-account, prereq, same-key, variant, versioned

- 0. The one idea that ties it together
- 1. Duplicate-key behavior today (VERIFIED — summary; full map in key-collision-behavior.md)
- Should the pool types be "fixed" to dedup by key? (answer: no)
- 2. Duplicate-key resolution mechanism (DESIGN)
- 3. Content provenance tiers (DESIGN)
- 4. Example content: versioned, self-updating (DESIGN)
- Version reconciliation of a forked variant (DESIGN — build LAST)
- 5. Library-management UX (DESIGN — the "next important part")
- 6. Move / copy content between sources (DESIGN)
- 7. Suggested branch decomposition (DESIGN)
- Open decisions

## custom-content-lifecycle.md

_custom-content-lifecycle · custom content lifecycle_

**topics:** 2118, 2195, 264, 2745, 353, background, completely, content, custom, factory, inline, localstorage, magic, missing-content, resolves, server-backed, sets, store

- A — Inline "Custom" option (name-only, per-character)
- B — Full builders (real, reusable, exportable library entries)
- C — Magic items (server-backed, a third store)
- Missing-content reconciliation and why inline :custom was false-flagged
- Known weakness (follow-up)
- Code map

## data-safety-layers.md

_data-safety-layers · data safety layers_

**topics:** defensive, fan-out, garbage, guessing, hand-edited, harden, heal, healing, junk, malformed, meaningful, prevent, reintroduce, repair, robust, self-healing, skip, surface

- The four layers (preference order)
- The rule that picks between them
- Worked examples (this codebase)
- Anti-patterns
- Tracked follow-ups
- See also

## datomic-crash-analysis.md

_datomic-crash-analysis · datomic crash analysis_

**topics:** contention, crash, crashes, datomic, feb, flush, heartbeat, lock, log, memoryindex, minute, postgres, seconds, speculation, threshold, transactor, unvalidated, write

- Active transactor configuration (verified from log startup lines)
- Crash mechanism — verified
- GC role — verified not sufficient alone
- Crash frequency — verified
- Schema noHistory status — verified, no action needed
- writeConcurrency=4 is actively harmful with H2
- Increasing heartbeat interval — not recommended
- Recovery time
- Fix options
- What the error emails reveal about this (relation to P1–P18 analysis)

## decision-vocabulary.md

_decision-vocabulary · decision vocabulary_

**topics:** asi, caster, choice, choices, cross-silo, feat-only, grant, innate, non-caster, prereqs, prof, spell, spell-choice, spellcasting, subclass, sustainability, templates, vocabulary

- Compile paths (load-time: decision data → content), verified
- :props has TWO sides
- :ability-increases → ASI (fixed OR choice) — FEAT ✅
- :prereqs / :path-prereqs → feat-prereqs — FEAT, LIMITED vocab ✅
- :spells → spell-modifiers — FIXED known spells ✅
- :spellcasting → spellcasting-template — CLASSES, full caster, custom list ✅
- :level-modifiers → level-modifier — SECOND grant vocabulary (classes/subclasses), incl. :spell ✅
- ⚠️ CORRECTION (this doc was wrong before) — subclass spellcasting IS gated
- TWO PARALLEL grant vocabularies — overlapping, divergent (the real duplication) ⚠️
- :level-selections → level-selection — TEXT-trait choices only ⚠️
- Resources (Axis B) — NO homebrew data path ✅ (confirmed gap)
- Backward trace (the CORRECT method) — verified per silo: builder form → assembly fn
- Feat — feat-builder (views :5264) → feat-option-from-cfg (options.cljc:3396) ✅ rich
- Race — race-builder (views :6219) → race-option (options.cljc:2210) ✅ rich
- Subclass — subclass-builder (views :5946) → make-levels (spellsubs.cljs:382) ✅ rich
- Class — class-builder (views :5643) → level-option (options.cljc:2771) ✅ rich (with a plugin gap)
- Subrace — subrace-builder (views :6090) → subrace-option (options.cljc:1984) ✅ rich (≈ race)
- Background — background-builder (views :6368) → background-option (options.cljc:2456) ✅ minimal
- Simple types (boon/invocation/language/…) → simple-content-builder (views :6547) ✅ descriptive
- SHARPENED duplication finding — grant types live in up to FOUR places
- Cross-silo capability table — REBUILT from the backward builder→assembly trace ✅
- What's genuinely missing (the creator vision → gaps)
- Sustainability note
- Status / next cycles

## declarative-grant-vocabulary.md

_declarative-grant-vocabulary · declarative grant vocabulary_

**topics:** agreed, and-list, cantrips, choice, compound, creator, dependent, descriptive, filters, grant, idiomatic, layer-a, mis-attribution, progression, select, spell, two-level, vocabulary

- Two layers, kept separate (agreed)
- The vocabulary (DESIGN)
- Analysis — does it work for the real spell patterns?
- Compound grants — "pick 2 cantrips and a 1st-level spell" (the canonical case)
- Scope decisions
- Backward compatibility (hard requirement)
- Sequencing (agreed)
- Status of the earlier flags (updated)
- Idiomatic check (Clojure/Reagent)

## demo-content-tier.md

_demo-content-tier · demo content tier_

**topics:** base, content, content-lookup, copy, copy-on-edit, demo, diff, emitter, example, floor, frozen, golden, graduation, pack, recipe, tier, variant, viable

- Goal
- Builds on the current content model
- Decision: copy-on-edit + a provenance breadcrumb (NOT a live diff)
- Why copy, not a diff/override
- Status — Phase 1 built and verified
- Build mechanism (settled)
- Decided (were open)
- Still open
- Separate, bigger feature — variant rules (do NOT fuse this in)

## dev-tooling-decisions.md

_dev-tooling-decisions · dev tooling decisions_

**topics:** 2026-stack-modernization, 3449, 9500, best-practice, cli, config, consolidation, csp, datomic, dev-setup, figwheel, init, java, orchestrates, port, profile, repl, start

- user.clj Consolidation Pattern
- Current State (as of breaking/2026-stack-modernization)
- Planned Consolidation
- Leiningen Profile: :init-db
- dev-setup.sh
- Current State
- Decision
- config.clj as SSOT
- What It Contains
- Figwheel Port
- CI Java Version

## dmv-production-changes.md

_dmv-production-changes · dmv production changes_

**topics:** --------, analytics, backport, backport-worthy, branding, breaking, dmv, dmv-specific, email-preferences-implementation, fork, hotfix-integrations, license, matomo, meta, nginx, passwords, patron, production

- Summary
- Critical Issues to Flag to Admin
- 1. security.clj — Rate-limiting broken (PRODUCTION BUG)
- 2. newrelic.yml — License key committed in plaintext
- 3. deploy/transactor.properties — Hardcoded passwords
- 4. Minor bugs in DMV code
- Backport-Worthy Fixes (Priority Order)
- Must Fix
- Should Consider
- Document Only
- Recent Fixes Applied (dmv/hotfix-integrations branch)
- Auth Guard for API Subscriptions (5539953c)
- Fork Directory Reorganization (69eafaad)
- Email Preferences Feature (62381b85)
- Error Email Hardening (dmv/hotfix-integrations)
- Pedestal Logging Anti-Pattern (dmv/hotfix-integrations)
- Figwheel Codespaces Auto-Detection (37db84fb)
- 1. Docker Infrastructure
- Root Dockerfile (Dockerfile, new)
- Datomic Dockerfiles (docker/datomic/, new)
- Orcpub Dockerfile (docker/orcpub/Dockerfile, new)
- 2. Transactor Configuration
- deploy/transactor.properties (new, production config)
- 3. Nginx Configuration
- deploy/nginx-dev.conf (new)
- 4. Email / Auth Flow Changes (routes.clj)
- 5. Source Code Changes (CLJ/CLJS)
- Bug Fixes (backport-worthy)
- Features (evaluate for backport)
- DMV-Specific (don't backport)
- Bugs Introduced by DMV
- Dead/Unused Imports
- 6. Frontend Assets
- Branding (DMV-specific, don't backport)
- Monetization (DMV-specific)
- Potentially Useful
- 7. Monitoring / Vendored
- 8. Scripts / CI / Misc
- 9. Git / Workflow Gotchas
- Git Ref Namespace Collision
- Cherry-Picking Between dmv/ and breaking/

## docker-infrastructure.md

_docker-infrastructure · docker infrastructure_

**topics:** 128m, 1gb, 512m, alpine, build-time, busybox, datomic, docker, envsubst, hangs, healthcheck, peers, sed, subprocess, swarm, transactor, uberjar, wget

- Key Decisions
- host=datomic (not 0.0.0.0)
- Jetty binds 0.0.0.0 in prod
- Option C hybrid template for transactor.properties
- Healthcheck: /health + 127.0.0.1
- 3-step uberjar build
- DO NOT (verified by failure)
- File Inventory
- Verified Facts
- See Also

## docker-security-decisions.md

_docker-security-decisions · docker security decisions_

**topics:** bind, breaks, compose, container, datomic, decision, docker, dockerfile, healthcheck, host, mounts, nginx, ownership, password, port, reverted, sed, transactor

- Non-Root Containers (Entrypoint-Chown-Drop)
- sed Replacement Escaping
- Log Directory: /log Not /logs
- File Permissions (chmod 600)
- .dockerignore Secrets Exclusion
- Dynamic PORT Across All Services
- DATOMICURL Password Sync Validation
- CSPPOLICY / DEVMODE Passthrough
- VOLUME Declarations
- See Also

## docker-setup-flow.md

_docker-setup-flow · docker setup flow_

**topics:** auto, compose, deploy, docker, docker-compose, env, false, file-based, generate, interactive, modes, password, prompt, prompts, secrets, swarm, upgrade, yaml

- Flag Parsing (line ~548)
- Conflict Check (line ~583)
- Mode Execution Order (after refactor)
- 1. --check (line ~600)
- 2. --secrets standalone (line ~710)
- 3. --swarm standalone (line ~789)
- 4. --build alone (line ~898)
- 5. --up (line ~935)
- 6. --upgrade (line ~1013)
- 7. Fresh Install / Main (line ~1338)
- Composable Flag Combinations
- Shared Helpers (line ~33-502)
- generateenv() — unified auto/interactive
- switchtransactorhost(mode)
- read -rp + set -e
- Error Recovery
- Test Infrastructure

## docker-swarm-compat.md

_docker-swarm-compat · docker swarm compat_

**topics:** bind, cli, codespaces, compose, config, deploy, dns, docker, incompatibilities, nginx, outputs, specification, stack, startup, string, swarm, validator, yaml

- Context
- Solution
- Known Incompatibilities
- Why not use strict v3 format?
- docker stack config — not a solution
- Ports: keep quotes in compose.yaml
- Swarm Runtime Gotchas
- nginx upstream DNS
- Datomic ALTHOST
- Compose teardown before Swarm init
- Codespaces limitation
- Environment tested on

## docker-testing-guide.md

_docker-testing-guide · docker testing guide_

**topics:** admin, backup, codespaces, compose, daemon, deploy, docker, env, healthy, json, password, pipe, piped, prompt, script, secrets, swarm, wipe

- Automated Tests (No Docker Daemon)
- Fixtures (test/docker/fixtures/)
- Adding a test
- Reset Script
- H2 Database Prompt
- Manual Smoke Test — Compose Path
- Manual Smoke Test — Swarm Path
- Gotchas & Workarounds
- env -u DATOMICURL is required in Codespaces
- Port 443 not auto-forwarded in Codespaces
- Login is /login, not /api/login
- Character save is transit, not JSON
- H2 password lock-in
- docker compose up --build vs docker compose build + docker compose up
- --build --up without --swarm
- Swarm jq null-stripping must be last
- read -rp in piped/non-interactive mode
- docker secret create output leaks
- Transactor template has two host lines
- Test output capture pattern
- docker-user.sh init needs all containers healthy
- Swarm deploy needs jq
- Test Loop Checklist

## documentation-discipline.md

_documentation-discipline · documentation discipline_

**topics:** about, agent, audit, before-you-start, claim, confident, creep, docstrings, enforced, generator, hook, ledger, linked, operator, plan, push, stale, topic

- What earns a doc
- Verify, don't remember
- Claims must be proven, not asserted
- Update in place; record reversals separately
- Correct a wrong doc when you find it, not later
- The other trigger: docs your change just made wrong
- The exceptions — when "nearly always" is not "always"
- Structure: current truth first, audit trail last
- Docstrings are SPEC, not prose
- Index it or it is invisible
- What counts as a KB doc: one enumeration, shared
- Do not invent a section to home an orphan
- Say what is not known
- Arm the hooks
- Audit history
- 2026-09-05 — full KB audit (45 docs, ~7,700 lines)
- Surfacing review lessons where they are needed (2026-09-07)
- The two obvious objections, answered

## documentation-tenets.md

_documentation-tenets · documentation tenets_

**topics:** chose, commit, dense, dotfiles, instrument, investigation, narrate, person, readme, rediscovering, reminder, standing, tenets, tradeoff, tried, unioning, verification-discipline, window

- The tenets
- What belongs here
- The reminder hook
- Related

## dropdown-value-coercion.md

_dropdown-value-coercion · dropdown value coercion_

**topics:** asi, coerce, coercion, d32, floating-asi, forget, free, handed, index-round-trip, merge-base, numeric, occurrence, per-caller, primitive, prior, string, typed, widget

- The discrepancy (what bit us)
- Root cause (general, not specific to ASI)
- This bug class has bitten this branch TWICE (provenance, git-verified)
- The fix — :typed? (the template that makes the mistake impossible)
- Numbers already have a typed input — number-field
- Guard / convergence rule

## duplicate-key-durability-roadmap.md

_duplicate-key-durability-roadmap · duplicate key durability roadmap_

**topics:** abbreviation, bulk, characters, dash, derivation, item, key, match, rebind, relink, rename, renames, repair, rung, save, trailing, trim, trimming

- The problem, stated properly
- The invariant to establish
- The layers
- Build order
- Prerequisite
- Open questions
- Attempted 2026-09-07: trimming name-to-kw. Reverted. Read this first.
- The shim that unblocks it (user's design, not built)
- Where trimming belongs, and where it does not
- Fixing characters forward: lazy, with an eager count
- Built 2026-09-07 (integration 0790b3cd)
- What the paks turned out to contain
- Residual, accepted knowingly
- Built 2026-09-07 (integration 4b61904b, corrected in b4dcd595): layer C
- The rule this settled, worth keeping
- Remaining
- The resolution ladder
- Rung 2: former-keys (user's idea, not built)
- Rung 4: inline relink (designed in RECONCILIATION-LOG, not built)
- What this does to the count question
- Built 2026-09-08 (integration f0896b60): rung 2, former keys
- CLJS tests DO run, and the runner already existed
- Built 2026-09-08 (integration): A, the section 6 answer, and self-healing
- A -- source abbreviation into :key and :name
- Section 6's [UNVERIFIED]: detected, not repaired
- Heals are no longer silent or disposable
- :parked never existed
- class-binding-report

## e2e-logged-in-sessions.md

_e2e-logged-in-sessions · e2e logged in sessions_

**topics:** 2026-09-13, 394, cljs-headless-harness, credentials, e2e-boot, fast-browser-probes, helper, journey, logged-in, login, scenarios, script, seeding, seeds, server, suite, suites, testing-infrastructure

- The credentials
- The one command
- Which harness for what
- Logging in from a probe — use the one that already exists
- Why that helper is invisible: two different suites share one filename
- Naming convention for scripts/e2e/
- The four facts behind those seven lines
- An assertion trap, if you add checks
- Seeding a session in localStorage does not work
- What CLAUDE.md says about this is wrong
- Related
- Revisions

## edition-drift.md

_edition-drift · edition drift_

**topics:** 2014, 2024, 5etools, attack, crossbow, dual, edition, eligibility, feat, fighting, hand, light, melee, off-hand, property, two-weapon, weapon, wielder

- Two-weapon fighting, side by side
- What each difference costs us
- Two data traps found while checking
- The general lesson
- Provenance

## email-preferences-implementation.md

_email-preferences-implementation · email preferences implementation_

**topics:** 4-arg, added, datomic, dmv, echoing, email, emails, endpoint, jwt, marketing, preferences, re-read, response, token, transact, transaction, transactional, unsubscribe

- Architecture
- Datomic Schema
- JWT Unsubscribe Tokens
- How send-updates? Is Used
- User-Facing Controls
- DMV Arity Bug (Fixed)
- Fork File Organization
- Files That Required Require Updates (9 consumers)
- social-links-footer Pattern
- PUT /user Endpoint
- Gotcha: Re-Read After Transact
- Test Infrastructure

## empty-keyword-corruption.md

_empty-keyword-corruption · empty keyword corruption_

**topics:** 196-line, already-corrupt, apostrophes, character-rescue-console, cljs-http, defences, defends, detonation, docstring, edn, handoff, keyword-trap-name-repair, loader, readable, readme, rescued, throw, token

- The failure
- The two defences, and where they are
- If someone is hitting this right now
- Related but different
- Provenance
- Revisions

## entity-options-architecture.md

_entity-options-architecture · entity options architecture_

**topics:** autosave-fx, coast, equipment-subs, feat, feats, kw-path, multi-select, nested, nesting, plugin, plugins, quantity, rain-junkie, single-select, subscription, sword, vector, weapons

- Character Entity Structure
- Single-select → MAP (no vector index in path)
- Multi-select → VECTOR (indexed in path)
- What determines single vs multi?
- Path Generation via traverse-nested
- Namespace Load Order (core.cljs)
- autosave-fx Template Cache
- Content Source: SRD vs Plugins
- Feat Storage — Two Locations
- 1. Top-level :feats selection (multi-select vector)
- 2. Class-level :asi-or-feat (single-select under level)
- Content reconciliation detection
- Test Character (Datomic entity 17592186045779)
- Equipment Storage: Map vs Vector (sequential?)
- Impact: duplicate items overwrite
- Modifier types (modifiers.cljc)
- Quantity field exists but is underused
- Subscription merge assumes maps
- Selection Nesting: Template vs Builder Gap
- Verified: template layer supports arbitrary nesting
- Verified: homebrew builder doesn't expose nesting
- Key Files

## env-and-auth.md

_env-and-auth · env and auth_

**topics:** ---------------------, 500, auth, authenticated, clj, dev, dev-mode, env, jwt, overwrites, precedence, prod, profile, project, signature, token, var, vars

- How env vars are loaded
- 1. ./menu and ./scripts/start.sh
- 2. lein repl / lein run (direct)
- 2b. profiles.clj (local overrides, gitignored)
- 3. Docker / production
- SIGNATURE (JWT secret)
- Where it's read
- Dev default
- Bug history
- .lein-env Profile Switching and Prod Builds
- Profile :env maps in project.clj
- The overwrite chain
- Why java -jar target/orcpub.jar sometimes needs DEVMODE=false
- .env vs .lein-env — completely independent
- Correct prod launch chain
- lein build alias definition (project.clj)
- Auth flow
- Error diagnostics (routes.clj)
- Gotcha: (boolean "false") Is Truthy in Clojure
- Auth Token Canonical Path
- Key files
- The placeholder SIGNATURE is easy to leave in place (field report, 2026-09)

## equipment-option-picker.md

_equipment-option-picker · equipment option picker_

**topics:** 12-row, 306, arrow, cap, closed, combobox, control, desktop, dismiss, dom, highlight, light, mobile, mounted, native, nodes, popover, rows

- Measured
- Why the Popover API and not the hand-rolled popover
- Reversal: the 12-row cap was wrong
- Tried and rejected: prefetch some rows, expand after open
- Browsing
- Defects found while building it, and the fixes
- Decoration, and what was left out
- Depth
- The other pickers
- The option-menu that isn't here any more
- Which controls are kept
- Verify

## error-handling-import-validation.md

_error-handling-import-validation · error handling import validation_

**topics:** architecture, composite, css, decomposition, destructuring, extracted, findings, garden, handling, hof, instance, modal, panel, prs, re-frame, slide-out, subviews, validation

- Context
- What Was Built
- Core Features
- Architecture: handle-api-response HOF
- Architecture: Views Decomposition
- Architecture: Garden CSS Migration
- Review Findings Summary
- Redundant (str "literal") — 7 instances
- Bare destructuring outside let — 1 instance (bug)
- Test assertion argument order — 1 instance (bug)
- Silently non-testing test — 1 instance (upstream bug)
- Deferred Items
- Test Coverage
- Kondo Config

## extras-definitions.md

_extras-definitions · extras definitions_

**topics:** beneath, capabilities, companion, conditions, copy, copy-on-adopt, creature, disjoint, kind, npc, per-creature, pet, plan-companions-and-wild-shape, promotion, ribbon, sidekick, statblock, wolf

- One record, capabilities toggled
- Copy-on-adopt, not reference
- What each kind needs
- Where state lives
- Decided here
- Still open

## fail-soft-rendering.md

_fail-soft-rendering · fail soft rendering_

**topics:** app-root, bad, boundaries, boundary, coerce, comparator, culprit, datum, evasion, hunter, hunters, isolation, leave-one-out, non-string, sort, throw, throws, trace

- The goals, in priority order (this ordering drove the design)
- Layered error boundaries — why three, not one
- Fault isolation by re-execution — why, and the two levels
- Item level — isolate-culprit (used by guarded-feature-list, wired into actions-section)
- Selection level — isolate-culprit-selection (the actionable one)
- Safe building blocks (prevent the crash + surface it, in common.cljc)
- Verified facts / gotchas (so nobody re-derives them)
- Diagnosing a new occurrence
- Landing (done)

## fast-browser-probes.md

_fast-browser-probes · fast browser probes_

**topics:** 126s, 17s, 2-2s, 393s, 52s, cancel, measuring, nine, pdf, playwright, probe, probes, run, runner, sheets, sleeps, slow, timeout

- Where the time actually goes
- The rule: batch variables into ONE run
- The .lein-env trap, which will cost you two runs
- Keep the server up across probes
- Make a dead probe obvious
- Normalise to a control in the same run
- Screenshots
- Which Chromium a probe launches
- More traps
- Related
- Why characterimagecapture takes 393s
- The technique that found it, after four wrong answers

## feat-builder-audit.md

_feat-builder-audit · feat builder audit_

**topics:** arm, bool, compiler, feat, grant, hook, language, map-of-flags, modifiers, pool, pools, registry, scalar, select, ui-capped, verb, widget, widgets

- 1. Not "one verb per silo" — FIVE storage shapes for one question
- 1b. The hook that collapses those five shapes was ALREADY BUILT — and unwired
- ✅ LANDED — the registry (see content-extensibility-direction.md §4 for the full record)
- 2. The AC work landed a general vocabulary. The feat builder never got it.
- 3. Dead control
- 4. Hardcoded UI ranges the compiler does not impose
- 5. Full widget map
- 6. What this changes about sequencing
- 7. A THIRD grant vocabulary — the Custom Feat option list
- Corrections

## fighting-style-authoring.md

_fighting-style-authoring · fighting style authoring_

**topics:** authors, backfill-ledger, built-in, d29, d30, divvying, eligible, fighter, fighting-style, grant, mariner, paladin, per-class, pool, ranger, style, styles, watch-list

- Status — 2026-09-05: BUILT
- The divvying rule (decided) — which classes can take a homebrew style
- Verified findings — don't re-derive these
- Where it maps in the design record
- Phase B — in-app builder (remaining)
- References

## fighting-style-vocabulary-gap.md

_fighting-style-vocabulary-gap · fighting style vocabulary gap_

**topics:** archery, attack, blindsight, damage, dueling, fighting, great, interception, pool, predicate, prop, property, protection, style, styles, thrown, vocabulary, weapon

- The shapes, grouped
- :ranged? is a real flag, not the negation of :melee?
- The recurring need: a WIELDING predicate
- This is SHARED vocabulary work, not fighting-style work
- Order of work, cheapest first
- What driving the real app caught
- Verified end to end in the real app
- GAP: an imported style cannot be picked by the class that has the feature

## filtered-list-staleness.md

_filtered-list-staleness · filtered list staleness_

**topics:** 2026-09-13, cache, claude-branch-triage, computing, custom-content-lifecycle, declared, defect, event, filter, hunks, keystroke, plan-669-merge-verification, re-frame-subscribe-refactor, reframe-subscription-patterns, signals, staleness, subscribe-outside-reactive-context, subscription

- The mechanism
- The precondition — this is why it is not constantly obvious
- Why it is shaped this way — do not "fix" it back
- Why it is easy to miss in review
- The fix that exists
- The merge is cheap — measured, 2026-09-13
- What to actually test after merging
- Related

## folder-hardening.md

_folder-hardening · folder hardening_

**topics:** clause, client, entities, error, evolution, folder, folders, handler, hardening, http, optimistic, problem, re-fetch, request, rollback, server, solution, tempid

- Context
- Pattern: On-Failure Rollback via Server Re-Fetch
- Pattern: Client + Server Name Validation
- Pattern: Named Tempids
- Pattern: Interceptor Wrapping
- Pattern: Case Statement Default Clause
- Bug Found During Review

## fonts.md

_fonts · fonts_

**topics:** 118, 800, csp, cyrillic, external, font, fonts, google, greek, gstatic, hebrew, hosts, latin, latin-ext, licensed, sans, subset, subsets

- Why
- What is checked in
- Regenerating
- CSP

## fork-customization.md

_fork-customization · fork customization_

**topics:** adsense, api, bridge, cljs, dmv, domains, email, footer, hidden, integrations, matomo, neutral, override, privacy, production, public, stubs, views

- Override File Pattern
- Architecture
- Server → Client Config Bridge
- Branding (src/clj/orcpub/fork/branding.clj)
- Integrations — Server-side (fork/integrations.clj)
- Integrations — Client-side (fork/integrations.cljs)
- Lifecycle Hooks (no return value)
- UI Hooks (return hiccup or nil)
- Key patterns
- User Tier (fork/usertier.cljs)
- Adding a New Integration Hook
- Naming Conventions
- Cherry-Picking Between Branches
- Merge Strategy
- Cookie Consent

## frontend-redesign-parallel-work.md

_frontend-redesign-parallel-work · frontend redesign parallel work_

**topics:** accent, card, cards, chip, chips, chrome, css, menu, menus, mock, omv, popover, redesign, spacing, switcher, theme, title, tray

- What is on the branch (last commit 2026-07-15)
- The part that directly affects the builder forms
- What is NOT aligned yet, and is a real follow-up
- How the OMV elements meet the generated builder — the actual question
- select-menu replaces :enum, and removes a documented bug class
- option-menu covers :multi-enum at scale, and one blocking primitive
- :combo survives, narrowly
- Is OMV's markup better or worse for a generated form?
- :enum now uses select-menu — done 2026-09-06
- Section cards — optionmenuviews/card, done 2026-09-06
- What would have to give
- The strategic point
- Overlap worth reconciling before either branch merges
- How this was missed

## garden-inline-styles-harvest.md

_garden-inline-styles-harvest · garden inline styles harvest_

**topics:** 648, branch, compiled, conversion, css, exit, flyout, focus-within, harvested, harvesting, hunk, inline, integration, media-scoped, no-visual-change, phone, row, seeded

- Why the branch stalled
- The branch is healthy
- Why NOT merge it into integration
- What transfers: 72%
- The plan
- Check the utility classes exist before using them
- Do not automate the call-site mapping
- What is deliberately LEFT OUT, and why
- What IS taken and is still a real fix
- What this does NOT fix
- Check scope against the COMPILED stylesheet, not the source
- Verification
- Result of the first conversion batch (2c9c553d)
- Harness traps (cost several turns to rediscover)

## growable-option-menus.md

_growable-option-menus · growable option menus_

**topics:** 1440, accent, band, card, carded, carding, caret, child, collapse, dark-inset, family, header, intrinsic, layout, menus, per-menu, renderers, thumb

- 2026-09-06 — lifted onto feat/option-picker, then cut
- Goal
- Architecture
- Shared component — src/cljs/orcpub/dnd/e5/optionmenuviews.cljs (alias omv)
- Factories — views.cljs
- The two render-path families
- State model
- Validated lessons / gotchas (do not relearn these)
- Design vocabulary (from the designer)
- Per-builder status
- What's left (priority order)
- Verify live (headless)
- Commit map (25 commits, develop..HEAD)

## handoff-669-final-pass.md

_handoff-669-final-pass · handoff 669 final pass_

**topics:** 401, accounts, cljs-headless-harness, defect, e2e-logged-in-sessions, end, items, link-embedded, login, loop, merge, parents, pass, plan-669-merge-verification, resolving, suite, unit, units

- What the change is
- Already verified — do not spend time re-running
- What is NOT covered — this is your job
- Traps — each of these cost a cycle
- Commands that work
- Pass criteria
- If something fails
- Two things that are not this merge's job

## handoff-grant-rows.md

_handoff-grant-rows · handoff grant rows_

**topics:** acceptance, feat, fixed-class, frozen, grants, jvm, legacy, nested, normalization, normalize, pick, pool, registry, row, step, steps, strike, widgets

- Where you are
- The work, in order
- 1. Re-record the gallery baseline — 5 minutes
- 2. legacyshims.cljc — fixed-class keys normalize at import
- 3. The :ref decision — unblocks the choice class
- 4. grant-rows on the other silos — one line each
- 5. Effect kinds feat is missing
- 6. E3 proper — creatures and traits
- 7. content-builder :feat — the one-liner
- 8. Spells as a pool — last
- ⚠️ Read before step 5 — the template tier (added 2026-09-08)
- Rules that bit this session — do not re-learn them
- Don'ts
- Commands

## heartbeat.md

_heartbeat · heartbeat_

**topics:** 2026-09-14, beat, beats, clock, database, failing, hour, job, jobs, jumped, measure, outage, pruning, running, server, three-day, tick, unrecorded

- What it records
- Using it
- Limits
- Checks

## homebrew-class-spellcasting.md

_homebrew-class-spellcasting · homebrew class spellcasting_

**topics:** ------, --------, 435, cantrip, class, compilation, dropdown, generic, homebrew, homebrew-reference-web, level, magic, pact, pact-magic, slot, slots, spell, spellcasting

- Builder UI
- Not exposed in builder
- Data Model
- Custom spell list (homebrew only)
- Known modes
- Compilation Paths (homebrew class → entity template)
- Class option builder (options.cljc:~2970-2981)
- Subclass option builder (options.cljc:~2620-2633)
- Slot resolution (options.cljc:655)
- Spell Slot Calculation
- Hardcoded :warlock references
- What's generic (already works for any class)
- Pact Magic Slot Schedule
- Fix Plan: Enable Homebrew Pact Magic
- Open questions (not yet verified)
- Builder dropdown expansion (rides with #435 round-up fix)
- Related Issues
- Revisions

## homebrew-content-merge.md

_homebrew-content-merge · homebrew content merge_

**topics:** -commented, -ed, assembly, built-in, built-ins, concat, def, feat, feats, grappler, homebrew-extensible, merge, mostly, plugin, srd-minimal, static, sub, supported

- The trap, concretely (feats)
- The general pattern (applies to most content types)
- Verification recipe — "is homebrew X supported, and where does it merge?"
- TL;DR

## homebrew-fixes-persist.md

_homebrew-fixes-persist · homebrew fixes persist_

**topics:** anyway, app-db, aside, copy, damaged, dialog, entry, error-handling-import-validation, fast-browser-probes, homebrew-safety-net, library, load, needs-attention, repair, repaired, skipped, stored, writes

- The rule
- Where the writes happen
- Damaged shapes that get mended
- Proving a path persists
- Fixed in this pass
- Still open
- Checked and not a bug
- Related

## homebrew-override.md

_homebrew-override · homebrew override_

**topics:** constraints, enforcement, expressed, icon, mug, overridable, override, per-item, per-selection, per-thing, player, restriction, select, selection, switch, systematically, tortle, waives

- Where it is
- It is already per-thing
- The mechanism
- What it does NOT do — the part that matters for design
- Proposed extension: per-item overrides

## homebrew-reference-web.md

_homebrew-reference-web · homebrew reference web_

**topics:** clash-driven, class, executed, feat, item-to-item, key, link, links, membership, race, relocation, rename, spell, spells, stranded, strands, subclass, subrace

- 1. Why this page exists
- 2. The links
- Between homebrew items
- From outside the plugin map
- What is NOT a link (checked)
- 3. Spell lists, in full
- The subclass path, and a correction
- The cycle
- 4. Who walks the web, and what each one misses
- 4a. What a stranded link does to the player
- 5. Gaps, most harmful first
- 6. How this was established, and what was rejected
- What is proven, and what is only read

## homebrew-safety-net.md

_homebrew-safety-net · homebrew safety net_

**topics:** aside, cache, conversion, entity-options-architecture, entry, fail-soft-rendering, homebrew-fixes-persist, notice, realizing, repairs, restore, retries, retry, set-aside, startup, throw, throws, unrealized

- What it guarantees
- Startup reads storage and builds nothing
- Sorting never throws
- Repairs come first
- The guard
- Gotchas found on the way
- Tests
- Related

## http-fx-patterns.md

_http-fx-patterns · http fx patterns_

**topics:** 1698, 2026-02-22, conj, constructed, creation, dispatch, fire-and-forget, handler, http, interop, on-failure, on-success, optimistic, party, patterns, systemic, usages, vectors

- How the :http fx works
- on-success / on-failure MUST be dispatch vectors
- Bug: Eager JS calls in map literals
- Audit result (2026-02-22)
- Missing handlers
- Auth headers
- Key files

## icon-font-failure.md

_icon-font-failure · icon font failure_

**topics:** arrows, awesome, cause, fa5, font, fonts, glyph, hours, icon, locale-safety, methods, sent, separates, server, stylesheet, svg, webjar, width

- 1. The failure mode
- 2. Diagnosing it
- Detection methods that do NOT work
- 3. What actually broke it
- 4. What would have caught it in seconds
- 5. The durable fix is not a fallback
- 6. Order of questions, next time
- Provenance

## input-field-debounce.md

_input-field-debounce · input field debounce_

**topics:** 500ms, cleared, cljs, debounce, debounced, dispatches, edge, flicker, input-field, keystroke, keystrokes, lag, leading, rapid, settimeout, subscription, trailing, triggers

- Current Design (post-refactor)
- Data flow
- input-field (components.cljc)
- debounced-build-sub (subs.cljs)
- Why the Debounce Exists
- All input-field Callsites
- Historical: The Flickering Bug
- Design Decision: Why Leading+Trailing Edge

## key-collision-behavior.md

_key-collision-behavior · key collision behavior_

**topics:** built-in, coexist, combines, duplicate, import, item, key, membership, minted, override, plugin, predictable, rename, same-key, save, spell, winner, wins

- TL;DR
- The map (VERIFIED)
- Why the override is "plugin wins" (the load-bearing semantics) — VERIFIED by test
- The builder's own save gate (2026-09-12)
- The builder's save: a key is minted once (2026-09-13)
- What the save refuses
- The rename history — :former-keys (2026-09-12)
- Notes / boundaries

## keyword-trap-name-repair.md

_keyword-trap-name-repair · keyword trap name repair_

**topics:** -asdml, 2020, auto-coerce, auto-heal, digits, intent, invalid, junk, leading, least-destructive, mangled, number, quarantined, repair, restore, translator, unnamed, word

- Principle (why this exists)
- The repair chain (least-destructive first)
- Number→word translator (bounded on purpose)
- UI wiring — Manual vs Auto (DESIGN — not yet built)

## lein-uberjar-hang.md

_lein-uberjar-hang · lein uberjar hang_

**topics:** 7gb, attempts, bare-metal, classpath, cljsbuild, inclusion, jar, lein, lein-cljsbuild, prep-tasks, profile, profile-based, profiles, re-merge, resource-paths, subprocess, uberjar, wipes

- Summary
- Final Working Solution: Three-Step Docker Build
- Why step 3 doesn't re-compile
- project.clj profiles
- Bare-metal build alias
- resource-paths fix
- Key Facts for Agents
- VERIFIED (from source code, local testing, or CI evidence)
- UNVERIFIED (theories / assumptions)
- DO NOT (each verified by failure or local testing)
- Failed Approaches (Chronological)
- Attempts 1-7: Profile-based cljsbuild isolation (all failed)
- Attempt 8: Replace cljsbuild with figwheel-main
- Attempts 9-12: Final fix phase (post-cljsbuild removal)
- Related Files

## library-management-and-conflicts.md

_library-management-and-conflicts · library management and conflicts_

**topics:** already-loaded, card, conflict, conflicts, content, disable, dismissal, enabled, import, item, library, modal, nondeterministic, off, same-key, source, twin, winner

- Data model
- Why a duplicate key is a problem
- Enable / disable model
- Duplicate-key resolution
- Opinionated default (import) vs. the advanced panel
- Mutual exclusion — one enabled twin at a time
- Disable hierarchy
- Move / copy content between sources
- Health status — surfacing problems without a screen of red
- Where the code lives
- Related

## locale-safety.md

_locale-safety · locale safety_

**topics:** 7231, awesome, case-folding, dotless, english, english-locale, etag, formatter, linguistic, locale, locale-independent, machine, pedestal-csp-history, protocol, regional, spanish, turkish, webjar

- 1. The ETag crash
- Why only /assets/
- The fix
- 2. The Turkish-I problem in config
- 3. The rule
- 4. How this was found, and how long it took
- Provenance

## memoize-antipattern-scan.md

_memoize-antipattern-scan · memoize antipattern scan_

**topics:** arguments, cache, closure, dead, delete, factory, freeze, heap, large, measurably, memoize, perf-homebrew-builder-loop, react, reagent-architecture-tenets, retained, scan, sites, suites

- The defect
- Scan
- memoized-spell-option is measurably slower than no cache
- Plan (for a separate branch)
- Analysis: what could break
- Related

## merging-across-a-refactor.md

_merging-across-a-refactor · merging across a refactor_

**topics:** 669, bare, branch, call, conflict, cut, fetch, filtered-list-staleness, git, handoff-669-final-pass, inlines, merge, merging, newer, plan-669-merge-verification, refactor, side, sign-in

- Case 1: the clean merge that was already broken
- Case 2: the conflict whose obvious resolution restores a bug
- This one was self-inflicted, and that is the measurable part
- What to do about it
- Related
- Revisions

## modifier-vs-trait-slots.md

_modifier-vs-trait-slots · modifier vs trait slots_

**topics:** 1342, 1925, 2040, black-screen, breath, dragon, entity-options-architecture, evasion, evasions, features-tab, fiery, hunter, inert, monk, plain, rogue, trait, vector

- The mechanism
- Worked example: opt5e/evasion and the three Evasions
- How to tell which definition you're looking at (don't trust the page number)
- Verification recipe (live)

## monolith-decomposition-plan.md

_monolith-decomposition-plan · monolith decomposition plan_

**topics:** -------, 150, 2026-09-15, 623, 821, cljs, cohesive, domain, domain-based, extractions, incrementally, monolith, phase, split, splitting, tier, todo, views

- 1. Did the Builders Split Make Issues Easier to Find?
- 2. What Files Can or Should Be Broken Down?
- Tier 1: Data/logic separation (high impact, low risk) — proven, not merged
- What builder generation would actually take off views.cljs
- Tier 2: Domain decomposition (medium impact, medium risk)
- Tier 3: Not worth splitting
- 3. Order of Precedence
- Phase A: Data extraction (Tier 1) — proven on a branch, to be redone incrementally
- Phase B: Events decomposition
- Phase C: Remaining views + options
- 4. Branching Strategy
- Recommendation: One branch per tier, not per file
- Exception: If any split gets complicated
- 5. Pre-existing Issues Surfaced by Scanning

## multi-tab-character-contamination.md

_multi-tab-character-contamination · multi tab character contamination_

**topics:** -time, apparently-empty, characters, clone, defence, entity-options-architecture, event-layer, investigation, localstorage, lower-level, payloads, server-side, slot, spa-routing-architecture, sub-entity, symbols, tab, tabs

- The mechanism
- Where the notes actually live
- The shape of a fix — and why it is parked
- Provenance
- Related

## name-to-kw-audit.md

_name-to-kw-audit · name to kw audit_

**topics:** apostrophe-strip, derivation, derived-key, explicit, fallback, first-order, key, keys, keyword, map-key, migration-free, option, pain, second-order, snare, snares, srd, string

- 0. Reading conventions
- 1. What name-to-kw is
- 2. History — it is original, not recent
- 3. Why it exists — the design rationale
- 4. Classes specifically — they do not depend on name-to-kw
- 5. Call-site inventory
- 5.1 Core — the derivation default (3 sites)
- 5.2 SRD content-key derivation (~18 sites)
- 5.3 Homebrew / import / dedup (~13 sites)
- 5.4 Random-name generation — the only namespaced calls (4 sites)
- 6. Persistence — why "just change the keys" is dangerous
- 7. The snares — where the pain actually is
- 7.1 class-key-name returns two different types
- 7.2 ?prepare-spell-count re-derives a class key from its name
- 7.3 contentreconciliation hand-maintains mirrors of name-to-kw output
- 7.4 Dead, divergent duplicate in entity.cljc
- 8. Dependency trains (summary)
- 9. Options weighed
- Option A — Keep name-to-kw, change nothing
- Option B — Keep name-to-kw, harden the snares  ★ recommended
- Option C — Replace name-derivation with explicit :key everywhere
- 10. Recommendation
- 11. Appendix
- 11.1 [UNVERIFIED] items to close before any Option C work
- 11.2 Key references
- 11.3 Method

## namespace-architecture.md

_namespace-architecture · namespace architecture_

**topics:** 148, 2026-09-15, 638, anomalies, constants, corrections, crud, csp, handlers, helpers, html5history, http-fx-patterns, namespace, namespaces, nonce, previous, revision, split

- Directory Layout
- Entry Points
- Server Layer (.clj) — 27 namespaces, 12,079 lines
- Shared Layer (.cljc) — 85 namespaces, 44,857 lines
- Core primitives
- D&D 5e content namespaces
- D&D 5e logic namespaces
- Small spec-only namespaces
- Templates directory (16 files)
- Client Layer (.cljs) — 22 namespaces, 29,854 lines
- State management (re-frame)
- View layer
- Builder child modules
- Dependency Flow
- Simplified top-level chain
- View hierarchy
- Server dependency chain
- Known Anomalies
- Cross-references
- Corrections

## orcbrew-format-versioning.md

_orcbrew-format-versioning · orcbrew format versioning_

**topics:** boot-load, brew, builds, community, compat, compatibility, demo, envelope, extension, implemented, in-file, incompatible, pickers, poll, tag, version, versioning, won

- Why this exists
- The mechanism (three parts)
- Still open (besides the name)
- Related

## orcbrew-level-modifiers.md

_orcbrew-level-modifiers · orcbrew level modifiers_

**topics:** 120, arrive, audits, consumed, driven, editor, grant, integer, keyword, level, level-modifier, presence, race, races, subclass, subclasses, trait, warden

- Nothing validates the shape of a brew
- Two grant vocabularies, with different shapes
- The twelve :level-modifiers types
- :saving-throw-advantage computes and is then read by nothing
- :props — the feat vocabulary
- Ability keys are namespaced; nothing else is
- :traits
- Verified in the running app
- Races
- What is not known
- Revisions

## pdf-form-techniques.md

_pdf-form-techniques · pdf form techniques_

**topics:** 2026-09, appearance, artwork, attunement, bytes, card, cards, field, fields, foot, page, pages, pdfbox, rarity, sheet, stream, streams, widgets

- The one rule that explains most of the weirdness
- Cloning a page without copying its artwork
- Do NOT use these
- Pruning orphans must be per-widget, not per-field
- Text capacity: fields AUTO-SIZE, so the cutoff is legibility
- Modifier boxes clip at three characters
- Field names do not mean what they say
- PDFBox renders are not evidence about what users see
- The 0xAD hyphen
- A field with widgets on two pages is the same rule, hidden
- Place a patch by measuring the artwork, not by eye
- How many spellcasting sections a sheet carries
- Spell rows are sized by height, so they are wider than they look
- Computed marks: draw them, do not add fields
- If fields are unavoidable, share the appearance streams
- Why the same trick barely helps text fields
- What a field actually weighs
- PDFBox 3 already compresses
- The raster sheets are images, and their compression was leaving 25% behind
- What a character sheet export costs (2026-09)
- Churn is not footprint, and the difference is worth proving (2026-09)
- Most of it was work already done (2026-09)
- The floor is PDFBox's writer
- Bound the work, not the request (2026-09)
- The 503 is the page they are looking at (2026-09)
- Styling a page served outside the app (2026-09)
- Magic items hold enough to print as cards (2026-09)
- Verify card pages in a browser, not in PDFBox (2026-09)
- Drawing a card, and what a generated one owes nothing to a template (2026-09)
- Frame decoration is a ladder, not a set (2026-09)
- Measure the card, do not look at it (2026-09)
- The attunement badge, and centring the foot (2026-09)
- What a card page costs (2026-09)
- Card icons are SVG paths, not 32px rasters (2026-09)
- What the parser covers
- Draw it once per document, not once per card
- The whole cost, on all four axes
- Colour belongs at the draw site
- Where the bytes actually are, on all three surfaces (2026-09)
- Assets are per DOCUMENT, and the card functions each built their own
- The image guard's blind spots were the addresses Java has no predicate for (2026-09)
- The byte cap did not bound TIME
- Closing the resolve/connect gap

## pdf-generated-vs-uploaded-images.md

_pdf-generated-vs-uploaded-images · pdf generated vs uploaded images_

**topics:** 128k, 25000, advertises, art, bytes, ceiling, composed, decode, decoder, decoding, enforces, fitted, image, picture, png, portrait, private, refusal

- The collision
- Why the merge then dropped the portrait
- The fix, and the alternative that was rejected
- A latent bug this uncovered
- If you add another generated-image path

## pdf-generation-architecture.md

_pdf-generation-architecture · pdf generation architecture_

**topics:** byte, card, color, fillable, firefox, flatten, page, pdf, pdfbox, populated, reflection, rendering, spell, templates, viewer, walk, widget, widgets

- Overview
- Template Selection
- Spell Page Field Spec (pdfspec.cljc)
- Spell Card Rendering (add-spell-cards!)
- Lifecycle (critical for understanding blank page bugs)
- Silent Exception Swallowing
- PDFBox 3.x Migration
- Key 3.x differences for future work
- Known remaining risks
- PDF Field Filling
- Testing PDF Changes
- Fillable-by-default (2026-04-22)
- PDFBox 3.0.6 verified facts
- Known unknown — fix-widget-page-refs! mechanism
- Cross-browser PDF rendering

## pedestal-csp-history.md

_pedestal-csp-history · pedestal csp history_

**topics:** agent, blocked, csp, dev, enforcing, evaluates, figwheel, injects, interceptor, load-time, nonce, nonce-based, nonces, pedestal, presented, scripts, upgrade, violations

- Timeline
- Pedestal 0.5.1 (original project version)
- Pedestal 0.7.0 (upgrade target)
- CSP Level 3 Browser Behavior
- Why This Was Confusing
- Implementation Decision
- Why Nonces (Not Static Hashes)
- Dev Mode Strategy
- Configuration via CSPPOLICY env var
- Files
- Gotcha: Nonce Interceptor Load-Time Evaluation
- Corrections to UPGRADEPLAN.md

## perf-entity-build.md

_perf-entity-build · perf entity build_

**topics:** 500, browser, click, clock, cyclic, dags, divergences, doubling, frontier, graph, graphs, jvm, node, pre-rewrite, quadratic, sort, subtree, unwired

- 1. Is it actually slow in the browser? Yes.
- 2. Where the time goes (JVM phase split of apply-options)
- 3. How often does build run per user action? Twice.
- 4. Why the memoized wrappers were abandoned
- 5. The order produced by kahn-sort is load-bearing
- 6. The fix, and the trap in it
- How the order was verified
- 7. Measured after the fix
- 8. What is left, and what was left alone
- Ground rules (unchanged)
- Where the useful context lives

## perf-homebrew-builder-loop.md

_perf-homebrew-builder-loop · perf homebrew builder loop_

**topics:** ---, 130, builder-open, cache, casters, chunking, freeze, growth, heap, lazy, library, longest, mega-64, pack, paint, parse, per-source, task

- Method
- The fixtures
- What is established so far
- Import time scales with pack size
- The per-click character rebuild barely notices homebrew
- Template construction does NOT blow up with class/subclass count (JVM)
- The detail panels: state nobody asked for
- The per-class multiplier
- Why this is the promising cut
- Memory and freezes, measured
- What this says
- Spells on the Race tab
- It is all built up front, for levels the character will never reach
- Where it happens
- Correction: an earlier conclusion in this document was wrong
- What would fix it
- The hard ceiling: homebrew that cannot be saved
- Where a character change actually spends its time
- Per real race click (CPU profile, 6 clicks)
- Under real use: clicking around quickly
- On a machine that resembles a user's
- It is class browsing that accumulates, and it is unbounded
- What a bounded cache would cost, and why it is the wrong shape
- This is not Reagent failing to manage memory
- So do not hand-roll an LRU
- PHASE 0 RESULT: the builder-open block is EDN parsing, not template construction
- The confound I did not control for
- What this does to the plan
- Decision point: what to do after Phase 0 (analysis, pending owner decision)
- Why not the transit spike next
- Recommended order
- FINAL SPLIT of the cold builder-open block (mega-64, 3.87 MB, dev build)
- The go-forward plan, final
- The fix plan (revised — supersedes the original below)
- Done means
- Phase 0 — pick the shape by spiking it, not by arguing about it
- Phase 1 — the characterization net, before any structural change
- Phase 2 — implement the winner
- Phase 3 — measure, with the probes already committed
- Phase 4 — follow-ons, only if Phase 3 leaves them mattering
- Explicitly not doing
- What is left, in priority order
- The builder ran TWO character builds per click, and the first three diagnoses were wrong
- CORRECTION: the first version of this fix overreached
- The lesson, which cost most of the debugging time
- CORRECTION: class browsing is not an unbounded leak, and class switches are not slow
- What class-body work actually costs
- THE FREEZE, REPRODUCED: realising one class's spell options
- Why it took so long to find
- It is not GC, and not any builder function
- What it actually is
- It survives a PRODUCTION build — not a dev artifact
- ROOT CAUSE: memoize cache lookups that deep-compare every class in the library
- Five wrong diagnoses, and what killed each
- What would fix it (not yet measured, so candidates)
- Dead ends (recorded so they are not repeated)
- The port, and how it was checked here
- Track 1 spike: is chunked parsing worth a storage migration?
- CORRECTION (later): the "capped by the largest source" limit was not real
- Recommendation: worth it
- Why storage is one blob when import/export are already per-source

## plan-669-merge-verification.md

_plan-669-merge-verification · plan 669 merge verification_

**topics:** 2026-09-13, 669, counter, custom-items, differential, discarded, endpoint, expire, five, guard, items, login, scenario, sharing, stage, stages, subs, suite

- What is actually being merged
- Does any of this fix something broken on integration? Only P1.
- Pre-flight — done, with results
- Two traps found in pre-flight
- Two behaviour changes that are not the fix
- What the branch's own tests do and do not cover
- What has actually been run
- The discrimination check — the part that matters
- One of the branch's tests is vacuous — measured, not guessed
- What was added, and what each one is for
- The matrix
- Stage 1 — build ✅ done
- Stage 2 — cljs suite ✅ done
- Stage 3 — manual, logged in, with custom content
- Stage 4 — auth, the P5 risk surface
- Stage 5 — the orphaned chain (P4)
- Contingencies
- P5 differential — run on both trees, traces identical
- What this does and does not establish
- P4 is the one unit that should change shape before it lands
- Was remote-item meant for sharing between accounts? The endpoint says more than the comment does
- The open question this raises — for the owner, not for this merge
- The sharing feature changes what P1 and P5 need to cover
- Seeding — done, 2026-09-13
- What seeding the differential originally required
- Take it whole — decided
- What this plan cannot cover

## plan-chunked-library-storage.md

_plan-chunked-library-storage · plan chunked library storage_

**topics:** blob, capacity, chars, chunk, complexity, granularity, hydration, indexeddb, legacy, library, migration, one-time, per-key, phase, plan, quota, source, sources

- Read this first: why this plan is parked
- The problem, in one line
- The governing constraint
- Storage schema v2
- Why the write gets cheaper: dirty tracking
- Why the read gets faster: it must yield, not just split
- Auditing, quarantine and repair: what changes
- The migration hazard, measured — and why copy-then-delete is dead
- Incremental migration with a shrinking legacy blob
- Every intermediate state is valid, so interruption is not a failure
- CORRECTION: there is no un-migratable case — the chunk is not the source
- Batch the compaction, not the chunk
- What this fixes beyond migration
- One shape detail the data surfaced
- Stated plainly: this does not raise the ceiling
- Phases
- How each phase is proven
- Explicitly not in this plan

## plan-companions-and-wild-shape.md

_plan-companions-and-wild-shape · plan companions and wild shape_

**topics:** 2026-09-10, beast, card, cards, companion, conjure, creature, discarded, druid, familiar, foundry, master, monster, moon, per-form, summon, taxonomy, wild

- The domain map: seven kinds
- What this reveals
- Priority
- The druid flow, and what it maps onto
- The blocker: CR is two different types
- Circle of the Moon
- Merging druid onto beast
- What gets printed, and what gets a view
- The card
- The digital view
- What feature/grant-rows already built
- Homebrew: buckets A and B
- Base branch and dependency
- The specs permit it — but permitting is not the model
- The spec shapes
- A: bonded — a key on the granting feature
- B: conjured — count is a table, not a number
- The real risk is reference integrity, not schema
- What is not yet checked
- Slices: the Wild Shape half
- Slices: the companion half
- Companions are a second system
- Foundry's summon model — module/data/activity/summon-data.mjs
- The taxonomy a companion feature has to cover
- The SRD boundary decides where this ships
- Open questions
- Revisions

## plan-next.md

_plan-next · plan next_

**topics:** advanced, agent-dev-loop, armour, capabilities, contributor, damage, item, macro, natural-armor, number, picked, requirements, row, support-session-ledger, templates, tier, understood, warforged

- 0. Standing, do these first
- 0b. September support sessions — tracked separately
- 1. Grants on the remaining four silos
- 2. The mechanics the feat builder still can't author
- 3. Damage and attack bonuses reach the requirements registry
- 4. Convert the remaining builder forms
- 5. entity-spec / ?attr — analysis, not a rewrite
- 6. Vector rows, generalised
- Also open, smaller

## plan-npc-statblock-customizer.md

_plan-npc-statblock-customizer · plan npc statblock customizer_

**topics:** adjust, attack, averaged, bonuses, dcs, derivation, extras, initiative, npc, recompute, reskin, saves, score, scores, skills, statblock, str, wis

- The cascade
- What the app has
- Why it fits here
- Open

## pool-grant-map.md

_pool-grant-map · pool grant map_

**topics:** 2026-09-07, air, dependent, direction, discipline, entry, fighting, grant, grants, irregularity, membership, pieces, pool, pools, spells, styles, two-level, vector

- In four sentences
- The three layers
- REAL — built, tested, on this branch
- AIR — decided in a doc, not built
- How the open pieces constrain each other
- Spells: closer than it looks
- Provisional — set by one agent, not decided
- Where the confusion came from (so it does not recur)
- The path — four items, then wait for a case

## re-frame-subscribe-refactor.md

_re-frame-subscribe-refactor · re frame subscribe refactor_

**topics:** apis, autosave, cached, chain, component, direct, event-utils, extract, handler, low, methods, reactive, subscribe, subscription, subscriptions, verify, verify-user-session, watcher

- The Problem
- What actually happens when you subscribe in a handler
- Key Insight: Subscription Layers Matter
- Fix Patterns (Stable APIs Only)
- Pattern 1: Direct db read
- Pattern 2: Pass from component
- Pattern 3: Extract shared pure functions
- Pattern 4: Replace side-effecting subscribe with dispatch
- Pattern 11: reg-sub-raw token guard for API subscriptions
- Pattern 12: componentDidMount is NOT a reactive context
- What NOT To Do
- Don't use re-frame.flow or re-frame.alpha
- Don't use inject-sub cofx
- Don't assume you can "just read from db"
- OrcPub-Specific: Subscription Chain Reference
- Spells chain (compute-sorted-spells)
- Items chain (compute-sorted-items)
- Template chain (NOT replicated — too complex)
- character-interceptors and (path :character)
- Instances Fixed
- Last fix: Template cache via track! (12 of 12)
- Circular Dependency (Root Cause)
- Critical Bug Found and Fixed
- Files Changed
- Verification
- Risk: reagent.core/track! (new pattern)

## reagent-architecture-tenets.md

_reagent-architecture-tenets · reagent architecture tenets_

**topics:** 200, cache, caching, chunked, freeze, hand-rolled, hiccup, instant, keyed, memoize, paint, react, rebuilds, spell, subscription, task, tenets, virtualise

- 1. Build view markup in the view, never in the data layer
- 2. Derive lazily; do not materialise the world and filter afterwards
- 3. Keep subscriptions granular
- 4. Memoize on the smallest correct key, and bound the cache
- 5. Virtualise long lists
- 6. Optimise the longest task, not the total
- 7. Reagent: reactions dedupe on =, so returning fresh-but-equal values defeats caching
- 8. Load-bearing order must be pinned by a test, not assumed
- What to do instead of reaching for another cache
- When memoize IS right, so this is not read as "rip it out"
- Related

## reframe-subscription-patterns.md

_reframe-subscription-patterns · reframe subscription patterns_

**topics:** 401, api-backed, app-db, auth, auth-state-in-app-db, context, counter, decrement, deref, fire, guard, http, loading, non-reactive, reaction, reactive, redirect, subscriptions

- reg-sub-raw HTTP Pattern
- Lifecycle
- Critical: Auth Guard Placement
- Auth Token Path
- Loading Counter
- Why a counter?
- CLJS Truthiness Gotcha
- Reset on Login Redirect
- Implementation Location
- subscribe Context Rules
- Reactive context (components) — use subscribe
- Non-reactive context — read @app-db directly
- reg-sub vs reg-sub-raw
- Debugging Subscription Issues

## registry-before-after.md

_registry-before-after · registry before after_

**topics:** 000, adding, bits, boon, boon-like, builder-form, copy-pasted, damage-type, extra, fully-scattered, hand-built, input-field, per-type, plumbing, registry-driven, representative, spec-valid, type

- 1. Event wiring
- BEFORE — ~10 registrations, scattered across ~4,000 lines of events.cljs
- AFTER — nothing per type. One loop (written ONCE) wires every type:
- 2. DB draft state
- BEFORE — a def, a key, a fn, and a slot, in db.cljs
- AFTER — nothing per type. The slots generate from the registry:
- 3. The builder form
- BEFORE — a bespoke input-field wrapper + a hand-built form, in views.cljs
- AFTER — one line (the generic form is data):
- 4. So what do you actually WRITE to add a type now?

## remote-dev.md

_remote-dev · remote dev_

**topics:** 3449, codespaces, config, connections, dev, development, environments, figwheel, forwarding, gitpod, hot-reload, lein, port, remote, ssh, tunnels, visibility, websocket

- The Problem
- The Discovery: --fw-opts
- What Didn't Work: CLOSUREUNCOMPILEDDEFINES
- Codespaces Auto-Detection
- Port Visibility (Critical)
- Manual Override: FIGWHEELCONNECTURL
- Files Involved
- Gotchas

## requirements-registry.md

_requirements-registry · requirements registry_

**topics:** acquisition, alias, bonuses, channels, condition, context, contributors, distinguishable, effect, entries, fact, gates, macro, prereq, registry, requirement, spellings, trigger

- An entry
- Three-state, and unknown keys are ignored
- Why entries hold predicates, not condition forms
- Adding a requirement
- Scope today
- Not to be confused with prereqs
- Why: the same fact is hand-written in three places today
- The naming, and what lost
- Kinship with prereqs — same shape, different gate
- The blocker that was not one
- What landed
- The registry now REPLACES something (2026-09-08)
- Still to do

## rescued/half-caster-prepared-spells-handoff.md

_rescued/half-caster-prepared-spells-handoff · rescued/half caster prepared spells handoff_

**topics:** branch, caster, grant, half-caster, handoff, key-based, learns, one-line, paladin, phase, prepare, prepared, prepared-spells, reconciler, sibling, spellbook, spellcasting, spells

- The user's ask
- Branch state — important
- Why this work is small — verified blast-radius traces
- The plan — detailed
- Change 1: builder UI spell-acquisition-mode dropdown
- Change 2: mode-aware caster-level dropdown
- Change 3: one-line :all grant fix — honor :spell-list-kw
- Forward-looking: what an orcbrew :all class does today
- Pre-existing fragility (out of scope, but documented)
- :level-factor omission breaks the count
- ?prepare-spell-count name-to-kw at templatebase.cljc:275
- ::prepared-spells-by-class storage keyed by class display name
- Reasoning trail — decisions made and why
- Why the plan is UI-only + one-line bug fix, not an engine refactor
- Why we considered re-keying ::prepared-spells-by-class and decided against it
- Why :acquire mode forces :prepares-spells? true
- Why builder reuses the caster schedule as the :acquire minimum, not a Wizard-accurate table
- Why the subclass builder is out of scope
- Reconciliation with claude/fix-cantrips-selection-bug-CSwVv
- What the sibling has shipped (verified by reading the branch directly)
- Where the branches touch the same files
- Logical (non-code) overlap
- Recommended merge order
- File path collision: two web-handoff.md files
- Verification plan
- Reasoning traps to avoid
- Out of scope (deliberately, with rationale captured above)
- Where to start
- Open questions for the reconciler agent
- Key files

## rescued/ui-ux-evaluation.md

_rescued/ui-ux-evaluation · rescued/ui ux evaluation_

**topics:** alchemy, builder, buttons, char, cljs, ddb, focus, hover, navigation, orcpub, polish, responsive, search, sort, splash, transitions, views, visual

- Architecture Summary
- Key Files
- All Pages (from route map in core.cljs:33-76)
- Current Visual State — Specific Weaknesses
- Current UX Patterns
- What Works Well
- UX Gaps Found
- In-Flight Branches Affecting UI/UX
- D&D Beyond Comparison — Perception Gap
- Visual Polish Gap
- UX Design Pattern Gap (Beyond Visual Polish)
- What OrcPub Should NOT Copy
- OrcPub's Actual Strengths vs. Competitors

## rescued/ui-ux-plan.md

_rescued/ui-ux-plan · rescued/ui ux plan_

**topics:** ------, -------, char, clj, color-themes, core, css, descriptions, focus, polish, priority, splash, theme, themes, tier, veterans, views, visual

- Context
- In-Flight Branches (Critical Context)
- D&D Beyond Comparison
- Avenue 1: Visual Modernization — Three Tiers
- What's Dated (The Gap)
- Tier 1: CSS-Only Facelift
- Tier 2: Component-Level Refresh
- Tier 3: Full Design System
- Visual Implementation Caveat
- Avenue 2: UX Improvements (Full Site)
- Priority 1: Quick Wins
- Priority 2: Interaction Quality
- Priority 2.5: Contextual Help & Onboarding (New Users Without Annoying Veterans)
- Priority 3: Page-Level Polish
- Priority 4: Deep Work (From Existing Issue Triage)
- Branch Merge Sequencing
- Key Files
- Deliverables
- Testing Gap — Must Address
- Required: E2E Visual Verification Tests
- Tooling Options
- Test Creation Trigger Rule
- Verification (This Evaluation)

## roadmap.md

_roadmap · roadmap_

**topics:** 2026-09-05, bespoke, class-feature, cross-silo, d29, feat, grant, grant-authoring, node, phase, pool, pools, registry, remaining, round-trip, silo, substrate, track

- The arc (two phases — both real, one branch)
- Status ledger (anchored to commits; detail in the linked docs)
- BUILT — Phase 1 (verified by git + BRANCH.md)
- BUILT — Phase 2 (this session)
- BUILT — Pool registry + second grant silo (2026-09-07) — pool-grant-map.md
- BUILT — AC engine + authored mechanics (this branch, 2026-09-04/05) — armor-class-refactor.md
- BUILT — Content-library management (parallel branch feature/content-library-management, PR #30)
- DECIDED (design settled; don't re-litigate)
- OPEN — Phase 1 levers & pins (from direction.md)
- Tracks — Phase 2 (the expansion, layered on Phase 1)
- Flagged conflicts (need a call — do not silently resolve)
- Doc map (so there's one place to look)
- Critical path

## rules-override-layer.md

_rules-override-layer · rules override layer_

**topics:** armor, campaign, everyone, expressed, expressible, feat, feats, granted, layer, ledger, permission, permissions, rules, table-wide, tortle, wants, wear, writs

- What it is
- Why it can't just be "make a feat for it"
- Naming
- Design constraints, from the discussion that produced this
- It should ride the shared :props vocabulary
- The mechanical hook that already exists

## runtime-toggles-and-conditional-modifiers.md

_runtime-toggles-and-conditional-modifiers · runtime toggles and conditional modifiers_

**topics:** armor, benefit, bloodied, build-state, condition, deferred, entity, equipped, flag, modifiers, play-state, player, positioning, rage, recomputes, roll, sheet, toggle

- The mechanism
- Armor (build-state condition) works similarly but auto-evaluated
- What this means for conditional / "while active" features
- Boundaries (what this does NOT do)
- Design implication (the condition/benefit registry idea)

## secrets-in-boot-output.md

_secrets-in-boot-output · secrets in boot output_

**topics:** 7-bit, aggregator, alerting, banner, blanket, boot, column, credential, database, ex-info, leak, log, logs, parameter, print, query, redact, uri

- It was in four places, not one
- Redact at the boundary, and only what is logged
- The next leak goes in the startup banner
- Reporting configuration without lying about it
- Also

## share-bundle-dependency-extraction.md

_share-bundle-dependency-extraction · share bundle dependency extraction_

**topics:** cljc, cljs, closure, def, edges, extractor, index, keys, languages, plugins, recipient, reverse, selections, spell, spell-list, spells, subrace, subs

- Why this is not trivial
- The content types (master list)
- Important scope limit: magic items are NOT plugins
- How a character references content (direct edges)
- Transitive edges (the crux)
- Existing dangling-reference detection to reuse
- Extraction algorithm
- Risk areas
- Verdict
- Key files to implement against

## share-custom-items-plan.md

_share-custom-items-plan · share custom items plan_

**topics:** apply, by-key, custom, decode, defs, equipped, item, items, mi5e, name-derived, sanitize, serialize, shared-custom-items, sorted-items, subs, tooltip, untrusted, whitelist-bundle

- Headline finding (dissolves the hard part)
- Resolution path (where injected defs must live)
- The one gotcha (hardest part)
- Plan (mirrors the homebrew flow)
- Security

## share-links.md

_share-links · share links_

**topics:** caps, character, compressed, copy, deletes, homebrew, kaylee, link, links, opening, owner, page, party, server, share, sharing, token, upload

- What a link carries
- How it works
- When the stored copy changes
- Pruning
- Parties
- What the server knows
- Limits, and why these numbers
- The encrypted design, kept in history
- Open
- Traps hit building it
- Checks

## spa-routing-architecture.md

_spa-routing-architecture · spa routing architecture_

**topics:** 1289-1326, 1828-1838, 302, 33-75, client, component, entity-id, html, pages, password-reset-success, pedestal, redirected, route, routes, spa, unsubscribe-success, verify-success, watch-dirs

- Route Registration (3 places)
- 1. Route Map — src/cljc/orcpub/routemap.cljc
- 2. Server Index Pages — src/clj/orcpub/routes.clj → index-page-paths
- 3. Client Pages Map — web/cljs/orcpub/core.cljs → pages
- Bonus: Login Routes Set — src/cljs/orcpub/dnd/e5/events.cljs → login-routes
- Route Flow
- Server-Side Route Handlers vs SPA Pages
- user-for-email / first-user-by Returns Non-nil for Missing Users
- The pages Map in core.cljs

## spell-granting-across-silos.md

_spell-granting-across-silos · spell granting across silos_

**topics:** assembly, cast, castable, chain, class-gated, creator-declarable, magic-item, not-tested, per-silo, primitive, primitives, races, silo, spell, spells, sustainable, verified, wrapper

- The two core primitives (what every bespoke spell function wraps)
- Fixed spell — the chain per silo
- Spell choice — the chain per silo
- Why some work and some don't
- The sustainable fix (and the trap to avoid)
- A sixth spell "source": magic items — text-only (VERIFIED)
- Usage limits / "once per long rest" — fragmented, not creator-declarable (VERIFIED)
- Limitations / open

## spell-selection-source-fix.md

_spell-selection-source-fix · spell selection source fix_

**topics:** bom, built-ins, cantrips, codespace, continue, error-handling-import-validation, homebrew-class-spellcasting, name-to-kw-audit, on-branch, ooms, pak, phase, poisoned, reconciler, remediation, sorcerer, spell-selection, toggle

- The bug (one sentence)
- Feature changes
- Committed on-branch
- Fixed this session (were uncommitted at time of writing)
- Verified research (this session, e2e)
- Verified facts / corrections to prior docs
- E2E method (why it's not run headless in the codespace)
- Still open / deferred (not part of this fix)

## spell-slot-progression.md

_spell-slot-progression · spell slot progression_

**topics:** agreed, artificer, caster, factor, half-caster, integer, multiclass, multiclassing, normal, pact, per-level, slots, solo, sorcerer, spell-granting-across-silos, table, tables, warlock

- How slots are computed today — VERIFIED
- The overload — why Artificer can't be expressed — VERIFIED
- Warlock vs sorcerer when multiclassing — VERIFIED
- Agreed design — DESIGN (this thread, not built)
- Relation to other docs

## srd-2024-integration.md

_srd-2024-integration · srd 2024 integration_

**topics:** 2014, 2024, approaches, define, editions, extraction, fireball, granularity, introduce, logic, modifier, precedence, reorder, revised, shells, srd, srd-vs-plugin-content, versions

- Key Constraint: Mix-and-Match
- How Data Extraction Helps
- The Real Problem: Overlapping Keys
- Possible Approaches (not decided)
- What Needs Investigation
- Addendum 2026-09-11 — versioning granularity (UNVALIDATED THEORY)
- The case that constrains it
- What the engine already does
- The trap: filter, don't reorder
- Granularity for the UI
- Consequence to decide deliberately
- What would validate or kill this
- Related Files

## srd-vs-plugin-content.md

_srd-vs-plugin-content · srd vs plugin content_

**topics:** acolyte, base-class-options, battle, college, comes, content, discarded, folk, gnome, grappler, hardcoded, hero, master, non-srd, phb, plugins, races, srd

- The Distinction
- What's Hardcoded (SRD)
- Finding discarded content
- What Comes From Plugins
- Lesson Learned

## starting-equipment-override-ledger.md

_starting-equipment-override-ledger · starting equipment override ledger_

**topics:** addressing, base, bases, fill-in, free-text, frozen, gensym, group, groups, growable-option-menus, item-key, ledger, map-to-map, minted, option, srd, stable, sub-choice

- The shape (what a ledger addresses)
- The missing shape: groups/options have no stable id
- SRD bases are frozen forever
- Name fidelity is a key-stability prerequisite
- The op set (tiny)
- Shapes / edge cases to handle (the checklist)
- References

## starting-equipment.md

_starting-equipment · starting equipment_

**topics:** barbarian, choice, class, consumption, delta, detach, equipment, expand, export, grants, groups, ingestion, keys, pseudo-keys, serializable, srd, sub-selection, untouched

- The one thing that makes this cheap
- Two ways to express equipment on a class map
- 1. Shorthand keys — plain data, serializable, the UI target
- 2. The full "(a) or (b)+(c)" form — serializable, and IS a UI target
- How consumption works (what to expect on the character)
- Vocabulary a builder UI picks from
- Save / validation
- Builder UI (where it slots in)
- Start from an SRD class + the override delta
- On-disk delta format (data integrity)

## subscribe-diagnosis-techniques.md

_subscribe-diagnosis-techniques · subscribe diagnosis techniques_

**topics:** approach, atom, dispatch, during, function, infinite, inner, loading, namespace, patching, preload, property, recursion, stack, subscribe, trace, warning, warnings

- The Problem
- What Doesn't Work
- Patching re-frame.core/subscribe with set!
- Patching rf-subs/warn-when-not-reactive with set!
- re-frame.loggers/set-loggers! in the app entry namespace
- What Works
- Option 1: Monkey-patch re-frame.core/subscribe in the app entry ns
- Option 2: Figwheel Preload (catches namespace-loading warnings)
- Reading the Stack Trace
- Common Culprits
- Cleanup

## subscribe-refactor-phase2.md

_subscribe-refactor-phase2 · subscribe refactor phase2_

**topics:** caller, cljc, low, pdf, phase, plugin-data, prereq, pure, race-map, reactive, reg-sub-raw, shouldn, spec, ssot, subscribe, subscribes, user-imported, warnings

- Context
- Fix Patterns (New in Phase 2)
- Pattern 5: SSOT pure function with @re-frame.db/app-db
- Pattern 6: Thread parameter from caller
- Pattern 7: Plugin-data map parameter
- Pattern 8: reg-sub-raw for conditional subscription
- Pattern 9: Move subscribe from closure to render scope
- All 13 Fixes
- Files Changed
- Requires Added
- Verification
- Pattern 10: Top-level def with partial → defn
- Lessons

## support-session-ledger.md

_support-session-ledger · support session ledger_

**topics:** 2026-09-20, 695, bodies, booted, cherry-picked, commits, copilot, fa4, fork, github, hotfix, icon-font-failure, locale-safety, port, session, upstream, upstreamable, windows

- WHERE WE ARE — 2026-09-20, paused mid-review
- The Copilot review — 6 medium + 1 low, NOT yet triaged
- Why the comment bodies could not be read, and what would work
- Next actions, in order
- Not done, and not claimed
- Shipped
- About hotfix/locale-safety
- Built, in no branch
- Open — needs a decision, not just work
- Open — small, self-contained
- Verified on 2026-09-20 — previously listed as unverifiable
- Unverified — do not report these as done
- Corrections worth keeping

## test-suite-state.md

_test-suite-state · test suite state_

**topics:** 2016, assertions, cljs, debt, driver, e2e, errors, failures, harness, jvm, notice, pre-existing, run, spec, suite, theater, unresolved, unrun

- 0. Current measured state — 2026-09-12, feature/grant-rows (after the integration merge)
- 0.1 Earlier measured state — 2026-09-05, feature/fighting-style-authoring
- 1. What runs where (the gate reality)
- 2. Pre-existing cljs failures (10 failures / 3 errors)
- 3. The ::character spec / character-test.cljc saga (verified via unshallowed git)
- 4. The built/computed character has no validation spec (verified)
- 5. Open decisions / recommendations (so we don't re-litigate)

## testing-infrastructure.md

_testing-infrastructure · testing infrastructure_

**topics:** 2026-02-18, assertions, auto-run, classpath, cljs-compatible, cljs-headless-harness, conditionals, confuses, effects, extracting, handlers, jvm, re-frame, re-frame-test, reader, reg-event-db, testing, utilities

- Verified Facts
- Test Runners
- What lein fig:test Actually Does
- Directory Layout
- Library Truths (Verified, Not Assumed)
- re-frame.test Does NOT Exist in re-frame 1.4.4
- What You CAN Do Without re-frame-test
- What You CANNOT Do Without re-frame-test
- Testing reg-event-db vs reg-event-fx
- charactertest.cljc Is Not CLJS-Compatible (Fixed)
- CLJS Compiler Compiles Everything on Classpath
- Clojure/CLJS Gotchas Encountered
- (seq nil) Returns nil, Not ()
- .cljc File Location Matters
- Reader Conditionals for Browser APIs
- Namespace Architecture (Post-Refactor)
- Test Patterns
- Pattern: Testing a reg-event-db Handler
- Pattern: Testing a .cljc Pure Function
- Anti-Pattern: Garbage Assertions

## unsaved-knowledge-on-prunable-branches.md

_unsaved-knowledge-on-prunable-branches · unsaved knowledge on prunable branches_

**topics:** 2026-07-04, 2026-09-12, 217, absent, auth-state-in-app-db, branches, claude-branch-triage, conclusions, knowledge, multi-tab, nine, non-, prunable, ranked, stringification, tip, triage, unique

- The headline: agents/develop holds 70 of the repo's 128 KB docs
- Why the guard did not catch this
- A doc was deleted by the commit that was supposed to relocate it
- Lift these before deleting
- Drop these — superseded by better docs that already exist
- Recommendations
- What is not known
- Revisions

## verification-discipline.md

_verification-discipline · verification discipline_

**topics:** armed, baseline, bracers, caller, characterization, check, claims, confident, falsifiable, fixture, freeze, green, miss, perf-homebrew-builder-loop, proves, running, synthetic, tells

- Lessons (each with the concrete miss that taught it)
- A check that has never failed has never been tested
- The rule
- Making it mechanical
- Audit: which of this repo's guards actually discriminate
- Comparing the existing codebase to a proposed upgrade (the method)
- Search the dead/old code too, not just the live surface
- A green (or red) number proves nothing if the FIXTURE doesn't match real content
- A test whose contributors share a magnitude proves nothing
- A comparison is only as good as its baseline — verify the baseline by CONTENT
- Benchmark rules: warm up, and measure cost not proxies
- Reading the code tells you what could happen; only running it tells you what does
- Probe defects that produced confident wrong answers
- Measure the right thing
- Related

## views-builders-split.md

_views-builders-split · views builders split_

**topics:** builder, builders, child, classes, cljs, consistency, deps, helpers, imports, infrastructure, monster-only, move, race, race-only, shared, split, toolkit, truly

- Context
- Architecture
- Dependency Rule (Critical)
- What Stays in builders.cljs (~640 lines)
- What Moves to Child Files
- File Manifest
- Decisions & Gotchas
- 1. class is a JS reserved keyword
- 2. spell-selector and modifier-level-selector stay shared
- 3. option- naming convention — when to move
- 4. option-skill-expertise-choice and option-skill-proficiency-choice are defs
- 5. damage-dropdown-values is a def, not defn
- 6. Small files are OK for consistency
- 7. Boon + invocation share warlock.cljs
- 8. combat.cljs needs NO changes
- core.cljs Route Updates
- Verification

## weapon-data-model.md

_weapon-data-model · weapon data model_

**topics:** authored-tag, boolean, dart, deals, firearm, flags, handaxe, infer, javelin, mapping, melee, predicate, ranged, thrown, two-handed, versatile, weapon, weapons

- Fields
- Traps
- Invariants, verified against the data
- Authoring against these

