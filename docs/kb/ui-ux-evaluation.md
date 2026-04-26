# UI/UX Evaluation — Research Findings

Date: 2026-04-02

## Architecture Summary

- ClojureScript + Reagent 2.0.1 (React 18) + Re-frame 1.4.4
- No component library — all custom Hiccup components
- Garden CSS (`src/clj/orcpub/styles/core.clj`, ~1600 lines) → utility classes
- Inline `:style` maps for dynamic styling throughout views
- Font: Open Sans | Icons: Font Awesome 5.13
- Colors: Orange `#f0a100` (primary), Red `#9a031e`, Green `#70a800`
- Dark theme, white text | Responsive: xs/sm/md/lg breakpoints
- Max-width 1440px container

## Key Files

| File | Lines | Role |
|------|-------|------|
| `src/clj/orcpub/styles/core.clj` | ~1600 | Garden CSS, all utility classes, colors, breakpoints |
| `src/cljs/orcpub/character_builder.cljs` | ~2174 | Character builder UI |
| `src/cljs/orcpub/dnd/e5/views.cljs` | ~8800 | Main views: header, char display, all list pages, builders, combat tracker |
| `src/cljc/orcpub/components.cljc` | ~84 | Shared: checkbox, input-field, int-field, selection-adder, selection |
| `src/cljc/orcpub/dnd/e5/views_2.cljc` | ~158 | Splash page, splash-page-button |
| `src/cljc/orcpub/fork/splash.cljc` | ~31 | Fork-specific splash config |
| `web/cljs/orcpub/core.cljs` | ~136 | Route map (30+ pages), main-view dispatch |

## All Pages (from route map in core.cljs:33-76)

- Splash/Landing page
- Character: Builder, Newb Builder, List, Page, Parties
- Databases: Spell List, Monster List, Item List (+ individual pages)
- Homebrew Builders: Spell, Monster, Encounter, Item, Class, Subclass, Race, Subrace, Background, Feat, Language, Invocation, Boon, Selection
- Tools: Combat Tracker, Encounter Builder
- My Content (homebrew import/manage)
- Auth: Register, Login, Password Reset flows, Verify flows
- Orcacle (search results)
- My Account

## Current Visual State — Specific Weaknesses

1. **No focus indicators**: `*:focus { outline: 0 }` at styles/core.clj:948, no replacement
2. **No transitions**: Only 3 specialized transitions in entire stylesheet (abilities polygon line 1124, sticky header line 1261, toggle switch line 1532)
3. **Flat depth**: One box-shadow (`hover-shadow` line 223). No card elevation or layered surfaces
4. **Arbitrary spacing**: Pixel utilities from random lists (margin-top includes `0-9,10,15,20,21,25,30,40`)
5. **Basic forms**: `.input` (line 1192) = `border: 1px solid white`, transparent bg. No focus/placeholder/validation states
6. **No hover feedback**: Builder option cards, tabs lack transition effects
7. **Duplicate colors**: `orange` defined in styles/core.clj:8 AND views.cljs:63
8. **Small border-radius**: 3-5px throughout; modern apps use 6-8px+
9. **Loading**: Just `spiral.gif` in dark overlay (views.cljs:1552)
10. **Light theme incomplete**: Override at styles/core.clj:1298-1371 has gaps

## Current UX Patterns

### What Works Well
- Character builder split-pane desktop layout (options left, sheet right)
- Clean, information-dense interface
- Section tabs with remaining-count badges
- Expand/collapse "show info" pattern on builder options
- Back/Next wrap-around navigation in builder
- Responsive mobile/tablet/desktop detection and layout switching

### UX Gaps Found

#### Lists & Adding Items
- `selection-adder` (components.cljc:24-42) dropdown only at TOP of lists
- Inventory adder at character_builder.cljs:461 is above the item list
- "Add Levels in Another Class" (line 291) IS at bottom — good pattern, not replicated elsewhere
- "Add Custom Item" (line 478-482) is at bottom — also good, inconsistent

#### Empty States — None Exist
- Inventory sections (character_builder.cljs:462): renders nothing when empty
- Character list (views.cljs:8352): no "create first" prompt
- Spell/Monster/Item lists: filter-no-results shows blank
- Parties (views.cljs:8448): empty when none
- My Content (views.cljs:7715): empty `.item-list`
- Combat tracker (views.cljs:6959): no setup guidance

#### Tooltips — Severely Underused
- Only on roll buttons (views.cljs:2001-2013)
- Missing on: ability +/- buttons (char_builder:874), equipped checkboxes (char_builder:320), section tab icons on mobile (char_builder:1576), header nav icons on mobile (views:282), sort toggles (views:8605)

#### Destructive Actions Without Confirmation
- Class deletion (character_builder.cljs:183): dispatches immediately
- Inventory removal (character_builder.cljs:380): no confirm
- Custom item removal (character_builder.cljs:398): no confirm
- Note: character deletion (views.cljs:8110) and magic item deletion (views.cljs:7964) DO have confirmation — inconsistent

#### Search/Filter Inconsistency
- Monster list: text search + sort toggles (name, CR) + filter checkboxes (size, type, subtype)
- Spell list: text search only. No level/school filters, no sort
- Item list: text search + "New Item" button. No sort, no filters
- Character list: text filter + folders. No sort
- None have search placeholder text

#### Navigation
- No breadcrumbs anywhere
- No step/progress indicator in builder (just tab dots)
- Builder Back/Next (char_builder:1669-1686) are small, same style as form buttons
- No keyboard shortcuts

#### Splash Page
- views_2.cljc:55-158: Flat grid of identical buttons, no visual hierarchy
- No descriptions of what tools do
- No onboarding for new users
- Primary features (Character Builder) same visual weight as Language Builder

#### Other Gaps
- Loading overlay: dark screen + spiral.gif, no text (views.cljs:1549-1552)
- No skeleton loading states
- Section completion badges are small/subtle (char_builder:524)
- My Content import: raw `<input type="file">` unstyled (views.cljs:7752)
- Combat tracker: least polished page, no guidance, manual everything

## In-Flight Branches Affecting UI/UX

Fetched from remote 2026-04-02. Any agent working on UI/UX must account for these:

| Branch | What It Does | Status |
|--------|-------------|--------|
| `refactor/garden-inline-styles` | Converts inline `:style` maps → Garden utility classes. +320 lines in core.clj, -542/+539 in views.cljs. Removes hardcoded style objects, adds responsive fixes. | ~60% done, 30+ commits |
| `claude/add-color-themes-gyRhI` | Theme system: `styles/themes.clj` (739 lines), `styles/colors.clj` (Nord palette), CSS variables, 6 themes. SVG icon theming via CSS masks. | ~40%, **stale** — Nord variants have known visual bugs from lost iteration work. Infrastructure solid, visual application needs rework. |
| `refactor/views-extraction` | Breaking monolithic views.cljs into modules: `views/auth.cljs`, `views/builders.cljs`, `views/combat.cljs`, `views/common.cljs`, `views/content.cljs`, `views/header.cljs`, `views/lists.cljs` + builder submodules. 121 files changed. | ~70% done |
| `refactor/data-extraction` | SRD data (classes, spells, monsters, items) into separate `_data.cljc` files. Build perf. | ~80% done |

**Dependency chain**: garden-inline-styles → color-themes → visual polish. views-extraction enables targeted UX work on modular components.

## D&D Beyond Comparison — Perception Gap

The core difference: D&D Beyond invests in **perceived polish** while OrcPub invests in **information density**. Neither is wrong, but the gap creates an "age" perception.

### Visual Polish Gap

DDB patterns OrcPub lacks:
- Multi-layered box-shadows for depth (`0 1px 3px ..., 0 4px 12px ...`)
- Transitions on ALL interactive elements (0.15-0.3s)
- Focus rings on every input
- Larger border-radius (8px+)
- Typography scale with letter-spacing variation
- Consistent 4px/8px spacing grid
- Skeleton loading screens
- Toast notifications for actions
- Button states: default/hover/active/disabled/loading
- Empty states with illustrations and CTAs
- Breadcrumb navigation for context

### UX Design Pattern Gap (Beyond Visual Polish)

Modern character creation tools (DDB, DiceCloud, Shard, Alchemy RPG) have converged on patterns that go deeper than CSS:

| Pattern | Modern Standard | OrcPub Status |
|---------|----------------|---------------|
| **Contextual onboarding** — dismissable intro banners, first-use hints that fade after interaction | Standard. DDB has guided tooltips on first builder visit. | None. Newb builder exists as separate route but main builder has zero guidance. |
| **Inline contextual help** — "what does this stat do?" next to controls. Critical for new D&D players. | DDB shows stat descriptions inline. Alchemy has ? icons everywhere. | `show-info` buttons exist on options but are easy to miss. No help on ability scores, no explanation of point-buy vs standard array. |
| **Smart defaults / templates** — pre-fill sensible choices, let user override | DDB suggests "quick build" options per class from PHB. | Blank slate only. No starting packages (#111). |
| **Visual feedback on cascading changes** — when you pick a race, ability scores highlight to show what changed | DDB highlights affected fields. Modern form builders pulse or animate changed values. | Changes happen silently. User must mentally track what a race/class choice affects. |
| **Undo/redo** — make exploration safe | DDB doesn't fully support this either, but modern form builders (Typeform, Notion) do. | No undo at all. Deleting a class is immediately destructive. |
| **Progress indicators** — show completion state across the full builder flow | DDB shows a checklist sidebar. Alchemy shows a progress bar. | Red remaining-count badges on section tabs (subtle, easy to miss). No overall progress. |
| **Search within builder** — find options by name without scrolling | DDB has search within spell selection, feat selection, etc. | No search within builder sections. Only global Orcacle search. |
| **Responsive context panel** — detail pane that updates as you hover/focus options | DDB shows stat block preview as you hover class/race options. | "show info" requires explicit click per item. |
| **Collaborative/sharing** — share character sheets, party views | DDB has sharing links, campaign integration. | Characters are per-account. Parties exist but no sharing URL. |

### What OrcPub Should NOT Copy

- Heavy JavaScript animations (keep CSS-only for Garden compatibility)
- Overly complex navigation (OrcPub's directness is a strength)
- Subscription-gated UI patterns
- Content-heavy pages (OrcPub's density is valued by power users)
- DDB's multi-step wizard (OrcPub's tab-based builder is more flexible for experienced users)

### OrcPub's Actual Strengths vs. Competitors

Worth preserving and building on:
- **Information density** — power users see more at once, less clicking
- **Split-pane builder** — options + live sheet side-by-side is better than DDB's separate "preview"
- **Homebrew system** — `.orcbrew` import/export is more open than DDB's locked ecosystem
- **Offline capability** — localStorage-backed, works without constant server calls
- **Direct navigation** — fewer clicks to get anywhere vs. DDB's deep menus
