# OrcPub UI/UX Evaluation & Improvement Plan

## Context

OrcPub is a D&D 5e toolset. Users value the clean, functional desktop experience — especially the character builder. But it shows its age vs. D&D Beyond. Not because of missing features, but missing **visual polish** and **UX affordances**. This plan covers both: visual modernization (3 tiers) and UX improvements (prioritized). This is an **evaluation-only** deliverable — no code changes.

Full research findings: `docs/kb/ui-ux-evaluation.md`

### In-Flight Branches (Critical Context)

| Branch | Status | Impact on This Plan |
|--------|--------|-------------------|
| `refactor/garden-inline-styles` | ~60% | **Must land first.** Converting inline styles → Garden classes (320+ new lines in core.clj, 755 lines changed in views.cljs). All Tier 1 line numbers reference develop — they'll shift after this merges. |
| `claude/add-color-themes-gyRhI` | ~40%, **stale** | Adds `styles/themes.clj` (739 lines), `styles/colors.clj`, CSS variables (`--header-icon-color`), 6 themes. **Known issue**: Nord variants have visual bugs from iteration that were lost in a pruned codespace. Infrastructure (CSS vars, theme defs) is solid; visual application needs rework. Tier 2/3 should extend the infrastructure, but Nord themes need fresh visual QA before shipping. |
| `refactor/views-extraction` | ~70% | Breaking 6271-line views.cljs into modular components. Makes UX changes much easier to target. |
| `refactor/data-extraction` | ~80% | SRD data into separate files. Build perf. |
| `agents/develop` KB | Active | Has `docs/issues/application-ux.md` — 38 triaged GitHub issues. UX items below cross-reference these. |

### D&D Beyond Comparison

The perception gap isn't features — it's polish. DDB invests in transitions, focus states, depth, and loading feedback. OrcPub invests in information density. Both valid, but the absence of polish reads as "dated." What NOT to copy from DDB: heavy JS animations, complex nav (OrcPub's directness is a strength), subscription-gated patterns, content-heavy pages.

---

## Avenue 1: Visual Modernization — Three Tiers

### What's Dated (The Gap)

| What | Current State | Modern Expectation |
|------|--------------|-------------------|
| Focus indicators | `*:focus {outline:0}` removes ALL (core.clj:948) | `focus-visible` ring on every interactive element |
| Transitions | 3 total in stylesheet | Every hover/click 0.15-0.3s |
| Depth | 1 box-shadow, flat cards | Multi-layer shadows, graduated surfaces |
| Border radius | 3-5px | 6-8px+ |
| Form inputs | `border: 1px solid white`, no focus | Focus glow, placeholder styling, validation states |
| Spacing | Arbitrary (margin-top: 0-9,10,15,20,**21**,25,30,40) | Consistent 4/8px grid |
| Loading | `spiral.gif` in dark overlay | CSS spinner + text, skeleton screens |
| Colors | Duplicate defs (core.clj:8, views.cljs:63) | Single source via CSS vars (partially done on themes branch) |

### Tier 1: CSS-Only Facelift

**Scope**: `src/clj/orcpub/styles/core.clj` only. Zero ClojureScript. Zero layout risk.
**Prerequisite**: Land `refactor/garden-inline-styles` first (otherwise editing styles under active refactor).

| # | Change | Where (on develop) | Impact |
|---|--------|-------------------|--------|
| 1 | Replace `*:focus {outline:0}` with `*:focus-visible` ring (orange, 2px) | line 948 | Accessibility + polish |
| 2 | `transition: all 0.15s ease` on interactive elements | `.builder-option` (1017), `.form-button` (1131), `.link-button` (1168), `.builder-tab` (1032), `.header-tab`, `.item-list-item` | Perceived smoothness |
| 3 | Multi-layer box-shadow + subtle bg tint on cards | `.builder-option` (1017) | Visual hierarchy |
| 4 | Input focus glow: `box-shadow: 0 0 0 2px rgba(240,161,0,0.3)` | `.input` (1192), `.builder-option-dropdown` (1068) | Modern form feel |
| 5 | Button `:active` pressed state (darken + slight scale) | `.form-button` (1131) | Tactile feedback |
| 6 | Border-radius 3-5px → 6-8px globally | `.b-rad-5`, `.input`, `.form-button` | Biggest single "modern" shift |
| 7 | Tooltip arrow + backdrop blur | lines 461-489 | Polish existing tooltips |
| 8 | Smooth opacity transitions on hover | `.hover-opacity-full`, `.opacity-5` | Fluid vs. snapping |
| 9 | `scroll-behavior: smooth` on html | new rule | Smooth scrolling |

### Tier 2: Component-Level Refresh

Everything in Tier 1, plus ClojureScript changes. Builds on garden-inline-styles AND color-themes branches.

| # | Change | Files | Depends On |
|---|--------|-------|-----------|
| 1 | Extend CSS custom properties (design tokens) | core.clj `:root` block | Themes branch already has `--header-icon-color`. Extend pattern for spacing, colors, radii. |
| 2 | CSS loading spinner + text replacing spiral.gif | views.cljs:1549-1552, core.clj (`@keyframes`) | — |
| 3 | Checkbox animation upgrade | components.cljc:5-10 | — |
| 4 | Remaining-indicator pulse animation | character_builder.cljs:524, core.clj | — |
| 5 | Card elevation classes (`.card-1/2/3`) | core.clj, character_builder.cljs:487 | Garden-inline-styles (cards currently inline) |
| 6 | Button variant system (primary/secondary/tertiary) | core.clj | — |
| 7 | Typography scale (heading-1/2/3, body, caption) | core.clj replacing arbitrary f-s-* | — |

**Note**: Nord-elevated theme already implements graduated surfaces — that pattern is the foundation for card elevation, not something to reinvent.

### Tier 3: Full Design System

Everything above, plus:

| # | Change | Scope | Notes |
|---|--------|-------|-------|
| 1 | Semantic color tokens (`--surface-1/2/3`, `--danger`, `--success`) | Extend themes.clj | Nord-elevated already has surface layering |
| 2 | 4/8px spacing scale | Replace utility generation in core.clj:26-48 | Breaking change to utility classes |
| 3 | Icon upgrade (FA5 → FA6 or Lucide) | index.clj CDN + all icon refs | Large surface area |
| 4 | Motion system (standardized easing/duration tokens) | core.clj + components | — |
| 5 | Multi-layer dark theme surfaces | Build on Nord-elevated pattern | Partially done on themes branch |
| 6 | Light theme completion | core.clj:1298-1371 + themes.clj | Current override incomplete |
| 7 | Touch targets 44px min on mobile | core.clj media queries | — |
| 8 | Skeleton loading screens | New components | Easier after views-extraction lands |

**Tier 3 prerequisite**: `refactor/views-extraction` merged — editing modular component files vs. monolithic 6271-line views.cljs.

### Modern UX Pattern Gap (Beyond Visual)

Visual polish is only half the gap. Modern character builders have converged on UX patterns OrcPub completely lacks. See `docs/kb/ui-ux-evaluation.md` "UX Design Pattern Gap" section for the full comparison table. Key gaps:

- **Contextual onboarding** — no guidance for new users (DDB has guided tooltips)
- **Visual feedback on cascading changes** — picking a race doesn't highlight affected ability scores
- **Search within builder sections** — no way to search spells/feats/equipment within the builder
- **Progress indicators** — no overall "you're 60% done building" signal
- **Undo** — deleting a class is immediately destructive with no recovery

These are addressed in Priority 2.5 (Contextual Help) and Priority 4 (Deep Work) below.

### Visual Implementation Caveat

AI can generate technically valid CSS but cannot reliably judge whether the result looks good in context. The difference between "polished" and "gaudy" is often a 0.1s timing change or 2px of shadow. Specific risks:
- Shadow values that look skeuomorphic instead of modern
- Border-radius too large for OrcPub's information density (bubbly/childish)
- Transition durations that feel sluggish or twitchy
- Color combinations that clash in the dark theme

**Required approach for visual changes:**
1. Apply ONE change at a time, review in browser, tune before moving on
2. Use a concrete reference (screenshot, design system, or site) as a visual target — not abstract descriptions
3. If adopting design tokens from an existing system (Material, Radix, Shadcn, etc.), apply their tested values rather than inventing new ones
4. Human approval per visual change, not batch review

---

## Avenue 2: UX Improvements (Full Site)

Cross-references `agents/develop:docs/issues/application-ux.md` (38 triaged GitHub issues) where overlap exists.

### Priority 1: Quick Wins

| ID | What | Where | GH # |
|----|------|-------|------|
| UX-1 | **Bottom-of-list add buttons** — `selection-adder` only at top of lists. User explicitly flagged. | character_builder.cljs:461. Duplicate after item list when >5 items. "Add Levels in Another Class" (line 291) already at bottom — replicate pattern. | new |
| UX-2 | **Empty state messaging** — 6+ pages show nothing when empty | char list (views:8352), parties (views:8448), my content (views:7715), combat tracker (views:6959), inventory (char_builder:462), filter-no-results on all list pages | new |
| UX-3 | **Search placeholder text** | Spell (views:8756), monster (views:8683), item (views:8835) — none have placeholder | new |
| UX-4 | **Active filter count badge** | Monster filter toggle (views:8662) — show "(3 active)" when checked | new |

### Priority 2: Interaction Quality

| ID | What | Where | GH # |
|----|------|-------|------|
| UX-5 | **Destructive action confirmation in builder** — class delete, item remove dispatch immediately. Confirmation system exists but is underused. | Class delete (char_builder:183), item remove (char_builder:380). Reuse `:show-confirmation` (views:1008). Char delete (views:8110) already has it. | new |
| UX-6 | **Expand tooltip usage** — component exists (views:2001), handles mobile, only used on roll buttons | Ability +/- (char_builder:874), equipped checkbox (char_builder:320), tab icons on mobile (char_builder:1576), sort toggles (views:8605) | new |
| UX-7 | **Spell list filters/sort** — monster list has sort + filters, spell list has only text search | views:8748. Model: monster list (views:8673) already has `sort-toggle` + filter checkboxes | #254 |
| UX-8 | **Builder step indicator** — no progress between Back/Next | char_builder:1669. Add "3 of 7 — Ability Scores". Pages array already has names. | #165 |
| UX-9 | **Dropdown default-value fix** — first option visually selected but not dispatched | Dropdown component init logic | #548 |

### Priority 2.5: Contextual Help & Onboarding (New Users Without Annoying Veterans)

This is a distinct UX avenue — not just tooltips, but a system for progressive guidance that fades for experienced users.

**Patterns that work:**

| Pattern | For New Users | For Veterans | Effort |
|---------|--------------|-------------|--------|
| **Page explainer banners** — Collapsible intro text at top of each major page. Dismissable + remembered in localStorage. | "The Character Builder walks you through creating a D&D character step by step." | Dismissed once, never returns. | Low |
| **First-use hints** — One-time callouts on key interactions (pulsing dot or subtle highlight). | Draws attention to non-obvious features like "show info" buttons, section tabs, equipped checkbox. | Gone after first interaction. Track in localStorage. | Medium |
| **Inline helper text** — Subtle gray text below section headers explaining what a section does. Always visible but unobtrusive. | "Choose your character's race. This affects ability scores, languages, and racial traits." | Scannable, doesn't interrupt flow. Already used for some labels (e.g., `personality-label`). | Low |
| **"What's this?" links** — Small `?` icons next to non-obvious UI elements that open help. | Links to contextual explanation (inline expand or panel). | Ignorable, doesn't take space. | Low-Med |
| **Splash page descriptions** — 1-line descriptions under each tool on the landing page. | "Build and manage D&D 5e characters" under Character Builder. | Helps even veterans discover less-used tools. | Low |
| **Newb builder already exists** — `routes/dnd-e5-newb-char-builder-route` routes to a separate simplified builder. | Already available. | Not in the way. | Done |

**Specific locations to add contextual help:**

| Where | What to Add | File |
|-------|------------|------|
| Character builder options column | Brief intro text above section tabs: "Select options for your character. Red badges show remaining choices." | char_builder.cljs:1665 |
| Ability scores section | Inline explanation of point buy vs. standard array vs. roll | char_builder.cljs:~960 |
| Equipment section | "Check the box to mark items as carried. Quantity can be adjusted." | char_builder.cljs:~463 |
| Spell list page | "Browse all SRD spells. Use the search bar to filter by name." (dismissable banner) | views.cljs:8748 |
| Combat tracker | Setup guidance: "Add parties and encounters to begin tracking combat." | views.cljs:6959 |
| My Content page | "Import .orcbrew files to add homebrew content. Exported files from other OrcPub instances work here." | views.cljs:7745 |
| Splash page | 1-line descriptions per tool button | views_2.cljc:81-148 |

**Implementation approach:**
- Dismissable banners: Store `#{:dismissed-builder-intro :dismissed-spell-intro ...}` in localStorage via existing `reg-local-store-cofx` pattern (db.cljs)
- Inline helper text: Pure render — just add `[:div.f-s-12.opacity-5.m-b-10 "..."]` below headers. No state needed.
- First-use hints: More complex — needs a "seen hints" set in localStorage + conditional rendering. Save for later.

**Priority**: Page explainer banners + inline helper text are low-effort, high-value, and completely non-annoying to veterans. First-use hints are medium effort but powerful. All independent of visual tiers.

### Priority 3: Page-Level Polish

| ID | What | Where | GH # |
|----|------|-------|------|
| UX-10 | **Splash page hierarchy** — flat grid, no visual priority, no descriptions | views_2.cljc:55-158. Group primary/secondary tiers. Add 1-line descriptions. | #165 |
| UX-11 | **Keyboard nav foundation** — focus fix (Tier 1 CSS) then tabindex + keydown handlers | core.clj:948, char_builder:487 (option selectors), char_builder:1874 (tabs) | new |
| UX-12 | **My Content page** — raw `<input type="file">`, "Delete All" too prominent | views:7745. Style as drop zone, move "Delete All", add explanation text. | new |
| UX-13 | **Inline validation** — int-field no visual feedback, selection validation only in header | components.cljc:74, char_builder:531 | new |
| UX-14 | **Item sort/filter in equipment tab** | Equipment tab in builder | #254 |
| UX-15 | **Content source attribution** — show SRD vs homebrew origin per option | Throughout builder views | #198, #320 |

### Priority 4: Deep Work (From Existing Issue Triage)

| What | GH # | Notes |
|------|------|-------|
| Combat tracker overhaul (search, notes, player view) | #193, #433, #534 | Least polished page |
| Hit dice tracking + generic resource counters | #442, #441, #247 | Core play-mode enhancement |
| Performance with large homebrew datasets | #621 | Critical — UI freezing |
| Option source toggling (SRD on/off) | #475, #206 | Also helps #621 perf |
| Starting packages for quick char creation | #111 | New user onboarding |
| Homebrew builder consistency | #165 | Standardize layout across 10+ builders |

---

## Branch Merge Sequencing

The visual and UX work has dependencies on in-flight branches:

```
refactor/garden-inline-styles ──┐
                                ├──► Tier 1 CSS (single file, safe)
claude/add-color-themes ────────┘
                                     │
refactor/views-extraction ───────────┼──► UX changes + Tier 2/3
                                     │
refactor/data-extraction ────────────┘    (build perf for iteration)
```

**Recommended order**:
1. Land `garden-inline-styles` (removes inline style debt — Tier 1 targets this file)
2. Land `add-color-themes` infrastructure (CSS vars, theme defs) — but **visually QA Nord themes** before shipping; known visual bugs from lost iteration work
3. Tier 1 CSS polish on top (low risk, single file)
4. UX Priority 1-2 items (independent of visual tiers, can start now on develop)
5. Land `views-extraction` (modular components for targeted work)
6. Tier 2/3 + UX Priority 3-4

**Exception**: UX Priority 1 items (empty states, search placeholders, bottom-of-list buttons) have NO dependency on any branch and can be done immediately on develop.

---

## Key Files

| File | Role |
|------|------|
| `src/clj/orcpub/styles/core.clj` | Tier 1 CSS (after garden-inline-styles merge) |
| `src/clj/orcpub/styles/themes.clj` | Tier 2/3 tokens (on color-themes branch) |
| `src/clj/orcpub/styles/colors.clj` | Color palette (on color-themes branch) |
| `src/cljs/orcpub/character_builder.cljs` | UX-1, UX-2, UX-5, UX-6, UX-8, UX-11 |
| `src/cljs/orcpub/dnd/e5/views.cljs` | UX-2, UX-3, UX-4, UX-6, UX-7, UX-9, UX-12 |
| `src/cljc/orcpub/dnd/e5/views_2.cljc` | UX-10 (splash page) |
| `src/cljc/orcpub/components.cljc` | UX-13 (validation), checkbox upgrade |
| `web/cljs/orcpub/core.cljs` | Route map (reference only) |

## Deliverables

This is evaluation-only. Produces:
1. `docs/kb/ui-ux-evaluation.md` — Research findings (architecture, patterns, gaps, line numbers)
2. This plan — 3 visual tiers + prioritized UX recommendations
3. Updated `docs/kb/README.md` — KB index entry

## Testing Gap — Must Address

**No visual/UI tests exist.** This is a critical gap for any visual modernization work. Changes to styles/core.clj affect every page — manual verification across 30+ routes and 3 breakpoints is unsustainable.

### Required: E2E Visual Verification Tests

Before or alongside Tier 1 CSS work, create E2E tests that:

1. **Screenshot baseline** — Capture reference screenshots of key pages across breakpoints (desktop, tablet, mobile):
   - Splash page
   - Character builder (options tab, description tab, details tab)
   - Character list (empty + populated)
   - Spell list, Monster list, Item list
   - Combat tracker
   - Login/Registration
2. **Theme verification** — When color-themes lands, E2E tests per theme ensuring:
   - No text-on-same-color-background (contrast check)
   - No invisible elements (icons, borders)
   - Header icons visible across all themes
3. **Regression detection** — Compare screenshots before/after CSS changes

### Tooling Options

| Tool | Fit | Notes |
|------|-----|-------|
| Playwright | Best | Built-in screenshot comparison, cross-browser, headless. `agents/develop:SETUP.md` mentions devcontainer — Playwright works in containers. |
| BackstopJS | Good | Purpose-built visual regression. Lighter than Playwright for pure screenshot comparison. |
| Cypress | OK | Heavy but has screenshot plugin. |

**Recommendation**: Playwright. It can also drive functional E2E tests (click builder tabs, fill forms, verify state) alongside visual checks. The `testing/develop` branch on `agents/develop` is already set up for testing infrastructure.

### Test Creation Trigger Rule

**Any change to `styles/core.clj`, `styles/themes.clj`, or `styles/colors.clj` should require corresponding E2E visual tests.** This should be enforced as a convention (and eventually a CI check).

## Verification (This Evaluation)

- Cross-reference against `agents/develop:docs/issues/application-ux.md` for consistency
- Validate branch dependency assumptions by checking merge status of listed branches
- When implementing: `lein garden once` for CSS, `lein cljsbuild once` for CLJS
- Manual browser testing across breakpoints until E2E tests are in place
