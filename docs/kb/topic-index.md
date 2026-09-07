# Topic index — what has already been looked at

**GENERATED — do not edit.** `lein with-profile +tools run -m orcpub.topic-index`

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

## ability-increase-spreads.md

_ability-increase-spreads · ability increase spreads_

**topics:** asi, asis, authoring, background, con, fixed, floating, increment, mental, race, reader, released, rider, save, spread, standalone, terse, widget

- The format
- Examples
- Save proficiencies
- How it compiles (opt5e/compile-ability-increases, options.cljc)
- How it renders (ability-bag-assigner, characterbuilder.cljs)
- Authoring
- Backward compatibility (D9)
- Containment across silos (multi-source)
- Tests

## armor-class-computation.md

_armor-class-computation · armor class computation_

**topics:** ability, armor, armored, bonus, channel, channels, con, defense, dex, mail, max, medium, monk, scalar, shield, tie-break, unarmored, worn

- Verified behavior — TEST-BACKED (accharacterizationtest.clj, JVM)
- The model — VERIFIED (templatebase.cljc:35-88)
- The channels a feature can use — VERIFIED
- Where each kind of custom AC goes — VERIFIED mechanism
- Known friction — VERIFIED facts (the "issues" are analysis)
- Design proposal — DESIGN, not built

## armor-class-refactor.md

_armor-class-refactor · armor class refactor_

**topics:** armor, barbarian, bracers, calculation, channel, defense, leather, monk, natural, plate, prop, ring, scalar, shield, sweep, tie-break, unarmored, worn

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

## backfill-ledger.md

_backfill-ledger · backfill ledger_

**topics:** 2026-09-04, bespoke, converged, d29, d34, delete, deleted, deprecated, feat, grant, job, ledger, legacy, naive, outright, parity, re-pointed, sweep

- When something lands here
- Migration recipe (per item)
- Ledger
- Watch-list (candidates — pool/grant doesn't fully subsume them yet)

## before-you-start.md

_before-you-start · before you start_

**topics:** borrowing, check, control, css, finds, fires, green, grown, lesson, machine, one-off, page, relationship, review, screenshot, stale, stays, vanished

- Already enforced — you do not have to remember these
- Judgement calls — no test can catch these
- Before designing anything (a control, a palette, a layout)
- Before borrowing a value from a mock or another branch
- Before converting a builder
- Before believing a CSS change worked
- Before reporting a UI change as done
- How this page stays small

## builder-conversion-gallery.md

_builder-conversion-gallery · builder conversion gallery_

**topics:** ---, assets, beside, bespoke, builder-comparison, chip, chips, conversion, duration, form, heading, height, jpg, language, mockup, page, row, rows

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

## builder-form-schemas.md

_builder-form-schemas · builder form schemas_

**topics:** ---, background, builders, converting, creatures, fragment, framework, group, june, monster, node, row, schema, selectors, tier, titled, traits, widgets

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
- The important finding: 27% understates it badly
- Per builder: how much is already shared
- What this changes about the plan
- 6. Track E — the plan (pulled forward 2026-09-05; status lives in roadmap.md)
- The unifying observation
- The :rows node — BUILT 2026-09-05 (this section kept as the design record)
- The :rows node — original design
- Phases (E0–E5) — the roadmap carries status; the acceptance tests are here
- What "good UX" means here, concretely
- 7. What this does NOT solve
- 8. Open questions

## building-a-class-from-builders.md

_building-a-class-from-builders · building a class from builders_

**topics:** artificer, builders, companion, d28, dead, express, first-class, gap, infusions, int-mod, magic-item, multiselect, non-srd, piece, pool, profs, scaling, tool

- The capability witness — VERIFIED
- What a homebrew class/subclass can already express — VERIFIED
- Infusions ≈ the invocation/boon pool pattern — VERIFIED
- Feature-to-mechanism map (what's data entry vs a real gap)
- Validation rule (D28)

## built-character-representation.md

_built-character-representation · built character representation_

**topics:** abilities, accessor, blew, character-validation, computed, deferred, derived, fields, findable, flat, iterate, null, plain, realized, session, spec, test-suite-state, values

- One-liner
- How it actually works (verified)
- What this means for you
- Where it bit us (this session)
- Anchored in code

## character-validation.md

_character-validation · character validation_

**topics:** built-character-representation, charter, computed, early, guard, history, intent, malformed, modern, modernization, own-branch, pdf, realized, replacement, retired, spec, test-suite-state, validation

- The intent worth keeping (do not lose this)
- History (verified)
- Why it can't be revived verbatim (verified)
- Charter — the modern replacement (PROPOSAL)
- Disposition of the broken test

## class-feature-catalogue.md

_class-feature-catalogue · class feature catalogue_

**topics:** arcanum, arts, attack, aura, auto-features, bardic, divine, features, inspiration, jack, lay, level, level-schedule, monk, multi-part, scaling, summaries, undead

- The 12 classes (option fn line; distinct auto-features; notable shape)
- Cross-cutting findings — the "odd cases" the registry/compiler must handle
- What this means for the build order (refines the roadmap)
- NOT-EXPLORED (flagged)

## class-features-and-mechanization.md

_class-features-and-mechanization · class features and mechanization_

**topics:** attack, cfg, dice, feature, features, heal, indomitable, registry, rogue, roller, rolls, scaling, sneak, structured, summary, surge, user-reported, wind

- How a class + its features are structured — VERIFIED (fighter, rogue read)
- Two kinds of feature "mechanics" — VERIFIED
- The rolling layer — VERIFIED (corrects an earlier wrong claim)
- Use/resource counters — VERIFIED
- The mechanization ceiling (where "make it real, not text" stops)
- The code-capture catch — VERIFIED (the thing that makes the registry non-trivial)
- Design direction for centralizing features — DESIGN (not built)
- NOT-EXPLORED / to verify before sizing

## cljs-headless-harness.md

_cljs-headless-harness · cljs headless harness_

**topics:** auth, backend, captures, case-sensitive, chromium, cljs, div, errors, headless, hidden, navigates, per-test, race-builder, recipe, rooted, runs, tabs, totals

- Build it
- Two ways to run (they differ — pick deliberately)
- Known-good baseline (as of this branch)
- Gotchas worth remembering
- Full-app headless E2E — render and drive the REAL app UI (not the test build)
- Full content round-trip through the real UI (test/e2e/export-import-use.js)
- Driving interactions (done — committed as test/e2e/race-builder-asi.js)
- Driving the character builder — three gotchas that each cost a debugging pass

## content-extensibility-compatibility.md

_content-extensibility-compatibility · content extensibility compatibility_

**topics:** assessment, catalog, catalogs, characters, constrains, content-extensibility, contract, exported, hosted, invariant, invariants, keys, nets, non-additive, safe, safety, section, selection

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

**topics:** bespoke, catalog, constraint, d12, d16, d17, d17b, d19, decision, factories, grant, hof, indirection, live, part, pool, readability, rejected

- Status at a glance
- Part 1 — How the thinking evolved (audit)
- Part 2 — Decision summary
- Part 3 — Late decisions (deflation; these scaled back D2/D3 — but were themselves RE-CENTERED by Part 4)
- Part 4 — Re-centering (these restore the capability D12–D16 over-deflated)
- Part 5 — Mechanization, class features, spell slots (the expansion)

## content-extensibility-direction.md

_content-extensibility-direction · content extensibility direction_

**topics:** adding, allowlist, ancestry, breath-weapon, choose, event, flight, ftd, gem, grant, modifiers, openness, pool, pools, registry, schema, variant, variants

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

**topics:** appears, backend, checklist, checks, confirm, console, datomic, dev, environment, errors, fail, loads, name-keyword, phase, read-seams, setup, skips, spell-selection

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

**topics:** ancestry, builder-item, component, conventions, d22, draconic, draft, events, framework, generated, irreducible, loops, page, pool, registry, routes, schema, spa

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
- 3d. Worked example — draconic ancestry (the proven slice)
- 3e. How to add a pool / a grant
- 4. Invariants & gotchas (agents: violating these breaks user data or the framework)
- 5. Verifying changes
- 6. Map of the docs
- MEASURED: the macro does not affect reactivity
- Route trees: /pages/ vs root

## content-extensibility-plan.md

_content-extensibility-plan · content extensibility plan_

**topics:** catalog, commands, compatibility, content-extensibility, content-extensibility-compatibility, content-extensibility-decisions, existing, gate, goal, golden, green, phase, phases, require, revert, snapshots, steps, stop

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

**topics:** 8-input, 945, boons, bucket-by-key, catalog, catalogs, child, content-extensibility-compatibility, content-extensibility-plan, entity-options-architecture, homebrew-builders, parent, positional, route-registration, spa-routing-architecture, srd-vs-plugin-content, views-builders-split, warlock

- The problem
- Current cross-links (verified from code)
- Proposed direction (design — not implemented)
- Layer 1 — content-type registry
- Layer 2 — type-addressed option catalogs + grants
- Suggested next step
- Related

## content-tiers-and-key-resolution.md

_content-tiers-and-key-resolution · content tiers and key resolution_

**topics:** dedup, disable, disable-based, disabled, duplicate-key, example, fork, independent, library, override, owned, per-account, same-key, sources, user, variant, versioned, warn

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

**topics:** 2118, 2195, 264, 2745, 353, background, completely, custom, factory, inline, library, missing-content, resolves, server, server-backed, sets, store, upload

- A — Inline "Custom" option (name-only, per-character)
- B — Full builders (real, reusable, exportable library entries)
- C — Magic items (server-backed, a third store)
- Missing-content reconciliation and why inline :custom was false-flagged
- Known weakness (follow-up)
- Code map

## data-safety-layers.md

_data-safety-layers · data safety layers_

**topics:** defensive, drop, garbage, guessing, harden, heal, healing, junk, malformed, meaningful, prevent, reintroduce, repair, robust, self-healing, skip, surface, unambiguous

- The four layers (preference order)
- The rule that picks between them
- Worked examples (this codebase)
- Anti-patterns
- Tracked follow-ups
- See also

## decision-vocabulary.md

_decision-vocabulary · decision vocabulary_

**topics:** asi, caster, choices, custom, expanded, feat-only, fixed, innate, non-caster, prereqs, skill, spell, spell-choice, spellcasting, subclass, sustainability, templates, vocab

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

**topics:** agreed, cantrips, cha, compound, creator, dependent, filters, grant, idiomatic, layer-a, multiple, nested, rows, select, spell, spells, two-level, vocabulary

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

**topics:** committed, content, content-lookup, copy, copy-on-edit, demo, diff, emitter, example, floor, frozen, graduation, pack, read-only, recipe, tier, variant, viable

- Goal
- Builds on the current content model
- Decision: copy-on-edit + a provenance breadcrumb (NOT a live diff)
- Why copy, not a diff/override
- Status — Phase 1 built and verified
- Build mechanism (settled)
- Decided (were open)
- Still open
- Separate, bigger feature — variant rules (do NOT fuse this in)

## documentation-discipline.md

_documentation-discipline · documentation discipline_

**topics:** agent, appended, audit, before-you-start, check, css, deletions, directory, dotfiles, goes, indexed, irreducible, learned, push, reminder, scripts, session, stale

- Write a doc when the work produced knowledge
- Update in place; record reversals separately
- Structure: current truth first, audit trail last
- Claims must be proven, not asserted
- The push reminder hook
- Audit history
- 2026-09-05 — full KB audit (45 docs, ~7,700 lines)
- Surfacing review lessons where they are needed (2026-09-07)
- The two obvious objections, answered

## dropdown-value-coercion.md

_dropdown-value-coercion · dropdown value coercion_

**topics:** asi, bug, cleanup, coerce, coercion, dropdown, forget, free, index-round-trip, merge-base, mistake, numeric, occurrence, per-caller, prior, string, typed, widget

- The discrepancy (what bit us)
- Root cause (general, not specific to ASI)
- This bug class has bitten this branch TWICE (provenance, git-verified)
- The fix — :typed? (the template that makes the mistake impossible)
- Numbers already have a typed input — number-field
- Guard / convergence rule

## fighting-style-authoring.md

_fighting-style-authoring · fighting style authoring_

**topics:** authors, backfill-ledger, built-in, d29, d30, divvying, eligible, fighter, fighting-style, mariner, paladin, per-class, pool, ranger, style, styles, tick, whitelist

- Status — 2026-09-05: BUILT
- The divvying rule (decided) — which classes can take a homebrew style
- Verified findings — don't re-derive these
- Where it maps in the design record
- Phase B — in-app builder (remaining)
- References

## fighting-style-vocabulary-gap.md

_fighting-style-vocabulary-gap · fighting style vocabulary gap_

**topics:** archery, attack, damage, dueling, end, fighting, great, predicate, prop, property, protection, style, styles, tag, thrown, unarmed, warrior, weapon

- The shapes, grouped
- :ranged? is a real flag, not the negation of :melee?
- The recurring need: a WIELDING predicate
- This is SHARED vocabulary work, not fighting-style work
- Order of work, cheapest first
- What driving the real app caught
- Verified end to end in the real app
- GAP: an imported style cannot be picked by the class that has the feature

## fonts.md

_fonts · fonts_

**topics:** blocks, browser, csp, cyrillic, external, font, fonts, google, hosts, latin, latin-ext, ofl, re-add, sans, subset, subsets, trip, visitor

- Why
- What is checked in
- Regenerating
- CSP

## frontend-redesign-parallel-work.md

_frontend-redesign-parallel-work · frontend redesign parallel work_

**topics:** accent, card, cards, chip, chips, chrome, css, dark, header, menu, menus, mock, omv, popover, redesign, switcher, theme, workaround

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

## homebrew-content-merge.md

_homebrew-content-merge · homebrew content merge_

**topics:** -commented, -ed, assembly, built-in, built-ins, concat, def, feats, grappler, homebrew-extensible, merge, mostly, nearly, plugin, srd-minimal, static, sub, supported

- The trap, concretely (feats)
- The general pattern (applies to most content types)
- Verification recipe — "is homebrew X supported, and where does it merge?"
- TL;DR

## homebrew-override.md

_homebrew-override · homebrew override_

**topics:** attached, attaches, constraints, enforcement, icon, legal, mug, overridable, override, per-item, player, select, selection, suppressed, switch, tooltip, tortle, waives

- Where it is
- It is already per-thing
- The mechanism
- What it does NOT do — the part that matters for design
- Proposed extension: per-item overrides

## key-collision-behavior.md

_key-collision-behavior · key collision behavior_

**topics:** appear, built-in, coexist, combines, keys, last-wins, membership, override, plugin, predictable, reduces, same-key, spell, union, unique, winner, wins, within

- TL;DR
- The map (VERIFIED)
- Why the override is "plugin wins" (the load-bearing semantics) — VERIFIED by test
- Notes / boundaries

## keyword-trap-name-repair.md

_keyword-trap-name-repair · keyword trap name repair_

**topics:** -asdml, 2020, auto-coerce, auto-heal, chain, digits, invalid, junk, leading, least-destructive, mangled, number, quarantine, repair, restore, translator, unnamed, word

- Principle (why this exists)
- The repair chain (least-destructive first)
- Number→word translator (bounded on purpose)
- UI wiring — Manual vs Auto (DESIGN — not yet built)

## library-management-and-conflicts.md

_library-management-and-conflicts · library management and conflicts_

**topics:** already-loaded, card, conflict, conflicts, copy, disable, dismissal, enabled, global, import, item, library, modal, off, overlay, same-key, twin, winner

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

## orcbrew-format-versioning.md

_orcbrew-format-versioning · orcbrew format versioning_

**topics:** boot-load, brew, builds, community, compatibility, demo, envelope, extension, implemented, in-file, incompatible, name, pickers, poll, tag, version, versioning, won

- Why this exists
- The mechanism (three parts)
- Still open (besides the name)
- Related

## registry-before-after.md

_registry-before-after · registry before after_

**topics:** 000, adding, bits, boon-like, builder-form, copy-pasted, damage-type, fully-scattered, hand-built, handlers, identically, input-field, plumbing, registry-driven, representative, spec-valid, type, wires

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

## roadmap.md

_roadmap · roadmap_

**topics:** 2026-09-05, background, bucket, class-feature, d29, detail, feat, grant, grant-authoring, hook, node, phase, pool, pools, proven, round-trip, spread, track

- The arc (two phases — both real, one branch)
- Status ledger (anchored to commits; detail in the linked docs)
- BUILT — Phase 1 (verified by git + BRANCH.md)
- BUILT — Phase 2 (this session)
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

**topics:** armor, campaign, computation, everyone, feat, granted, ledger, party, permission, permissions, prop, rules, scope, table-wide, tortle, wants, wear, writs

- What it is
- Why it can't just be "make a feat for it"
- Naming
- Design constraints, from the discussion that produced this
- It should ride the shared :props vocabulary
- The mechanical hook that already exists

## runtime-toggles-and-conditional-modifiers.md

_runtime-toggles-and-conditional-modifiers · runtime toggles and conditional modifiers_

**topics:** armor, benefit, bloodied, build-state, condition, deferred, entity, equipped, flag, modifiers, play-state, player, positioning, roll, rolling, sheet, static, toggle

- The mechanism
- Armor (build-state condition) works similarly but auto-evaluated
- What this means for conditional / "while active" features
- Boundaries (what this does NOT do)
- Design implication (the condition/benefit registry idea)

## spell-granting-across-silos.md

_spell-granting-across-silos · spell granting across silos_

**topics:** cast, chain, class-gated, creator-declarable, data, differ, fixed, magic-item, not-tested, per-silo, primitive, silo, spell, spells, sustainable, unreachable, wrapper, wrappers

- The two core primitives (what every bespoke spell function wraps)
- Fixed spell — the chain per silo
- Spell choice — the chain per silo
- Why some work and some don't
- The sustainable fix (and the trap to avoid)
- A sixth spell "source": magic items — text-only (VERIFIED)
- Usage limits / "once per long rest" — fragmented, not creator-declarable (VERIFIED)
- Limitations / open

## spell-slot-progression.md

_spell-slot-progression · spell slot progression_

**topics:** agreed, artificer, caster, factor, factors, half-caster, integer, multiclass, multiclassing, normal, pact, per-level, slots, solo, sorcerer, table, tables, warlock

- How slots are computed today — VERIFIED
- The overload — why Artificer can't be expressed — VERIFIED
- Warlock vs sorcerer when multiclassing — VERIFIED
- Agreed design — DESIGN (this thread, not built)
- Relation to other docs

## starting-equipment.md

_starting-equipment · starting equipment_

**topics:** choice, consumption, delta, detach, equipment, expand, export, fail-soft, grants, groups, ingestion, keys, nested, pseudo-keys, serializable, srd, sub-selection, untouched

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

## test-suite-state.md

_test-suite-state · test suite state_

**topics:** 2016, assertions, cljs, crashes, debt, diagnosis, errors, failing, failures, figwheel, harness, run, spec, suite, suites, theater, unresolved, unrun

- 0. Current measured state — 2026-09-05, feature/fighting-style-authoring
- 1. What runs where (the gate reality)
- 2. Pre-existing cljs failures (10 failures / 3 errors)
- 3. The ::character spec / character-test.cljc saga (verified via unshallowed git)
- 4. The built/computed character has no validation spec (verified)
- 5. Open decisions / recommendations (so we don't re-litigate)

## verification-discipline.md

_verification-discipline · verification discipline_

**topics:** baseline, bracers, callers, characterization, checking, claim, claims, compare, concluded, confident, faster, integration, miss, number, optimisation, session, unverified, upgrade

- Lessons (each with the concrete miss that taught it)
- Comparing the existing codebase to a proposed upgrade (the method)
- Search the dead/old code too, not just the live surface
- A green (or red) number proves nothing if the FIXTURE doesn't match real content
- The rule
- A test whose contributors share a magnitude proves nothing
- A comparison is only as good as its baseline — verify the baseline by CONTENT
- Benchmark rules: warm up, and measure cost not proxies

## weapon-data-model.md

_weapon-data-model · weapon data model_

**topics:** boolean, cosmetic, flags, mapping, melee, mistype, mutually, neither, not-melee, predicate, ranged, synonym, thrown, truthiness, two-handed, versatile, weapon, weapons

- Fields
- Traps
- Invariants, verified against the data
- Authoring against these

