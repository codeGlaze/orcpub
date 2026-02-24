# Namespace Architecture Map

Comprehensive reference for agents working on this codebase. Consult before scanning the repo.

## Directory Layout

```
src/
  clj/orcpub/                    — server-only (JVM Clojure)
  cljc/orcpub/                   — shared (JVM + CLJS)
    dnd/e5/                      — D&D 5e domain
    dnd/e5/templates/            — UA/SCAG source book content (16 files)
  cljs/orcpub/                   — client-only (ClojureScript)
    dnd/e5/views/                — extracted view modules
    dnd/e5/views/builders/       — 10 domain-specific builder pages

web/cljs/orcpub/core.cljs        — CLJS entry point + route dispatch

test/
  clj/orcpub/                    — server-only tests
  cljc/orcpub/                   — shared tests (run on JVM)
  cljs/orcpub/                   — CLJS tests
```

## Entry Points

| Role | Namespace | File | What it does |
|------|-----------|------|-------------|
| Server start | `orcpub.server` | `src/clj/.../server.clj` | `-main` → `component/start (system/system :prod)` |
| Component wiring | `orcpub.system` | `.../system.clj` | Assembles Datomic + Pedestal components |
| HTTP routes | `orcpub.routes` | `.../routes.clj` (1,443 lines) | All API endpoints + interceptor chains |
| HTML shell | `orcpub.index` | `.../index.clj` | Server-renders SPA container with CSP nonce |
| CLJS entry | `orcpub.core` | `web/cljs/.../core.cljs` | `[:initialize-db]`, mount reagent root, HTML5History routing |
| Route constants | `orcpub.route-map` | `.../route_map.cljc` | Bidi route table shared by server + client |

## Server Layer (.clj) — 19 namespaces

| Namespace | Lines | Purpose |
|-----------|-------|---------|
| `orcpub.server` | 8 | JVM entry point |
| `orcpub.system` | 75 | Stuart Sierra Component system |
| `orcpub.pedestal` | 120 | Interceptors: db injection, ETag, CSP nonce, static files |
| `orcpub.routes` | 1,443 | Full HTTP route table + handlers |
| `orcpub.routes.folder` | 69 | Folder CRUD handlers |
| `orcpub.routes.party` | 129 | Party CRUD handlers |
| `orcpub.config` | 84 | Env var reading, Datomic URI, CSP config |
| `orcpub.datomic` | 51 | Datomic connection lifecycle |
| `orcpub.db.schema` | 409 | Datomic attribute definitions |
| `orcpub.email` | 214 | SMTP: verification, password-reset, error-notify |
| `orcpub.security` | 92 | Login rate limiting (per-username, per-IP) |
| `orcpub.oauth` | 9 | OAuth URL helpers |
| `orcpub.pdf` | 673 | PDFBox 3.x: character sheets, spell cards, stat blocks |
| `orcpub.index` | 175 | Server-rendered HTML shell (hiccup + CSP nonce) |
| `orcpub.csp` | 45 | CSP nonce generation (128-bit SecureRandom) |
| `orcpub.favicon` | 26 | Favicon link tag |
| `orcpub.privacy` | 326 | Server-rendered privacy policy |
| `orcpub.time` | 177 | java.time wrappers (post clj-time migration) |
| `orcpub.styles.core` | 1,587 | Garden CSS-in-Clojure |
| `orcpub.tools.orcbrew` | 261 | CLI tool for .orcbrew inspection |

## Shared Layer (.cljc) — ~40K lines

### Core primitives

| Namespace | Lines | Purpose |
|-----------|-------|---------|
| `orcpub.entity` | 758 | Core entity model: options tree traversal, modifier accumulation, build |
| `orcpub.entity.strict` | 38 | Entity spec definitions |
| `orcpub.entity-spec` | 125 | `entity-val` accessor + `q` macro |
| `orcpub.template` | 156 | Template DSL specs: selection, option, derived-attribute, modifier |
| `orcpub.modifiers` | 124 | Modifier type constants + `modifier` macro |
| `orcpub.common` | 214 | String/keyword utilities, `name-to-kw` |
| `orcpub.dice` | 82 | Dice rolling: `die-roll`, `dice-roll`, `standard-roll` |
| `orcpub.components` | 83 | Reagent UI primitives: checkbox, labeled-checkbox |
| `orcpub.route-map` | 205 | Bidi route constants + `path-for` (shared client/server) |
| `orcpub.pdf-spec` | 637 | Character → flat PDF field map |
| `orcpub.registration` | 75 | Password/email/username validation |
| `orcpub.errors` | 169 | Error codes + `with-db-error-handling` macro |
| `orcpub.views-aux` | 139 | `option-selector-data` + option UI helpers |
| `orcpub.constants` | 3 | `header-height 227` |

### D&D 5e data namespaces (MONOLITH CANDIDATES)

These are the large data-heavy files targeted for Tier 1 decomposition (extract SRD data to `*_data.cljc` siblings):

| Namespace | Lines | Content |
|-----------|-------|---------|
| `dnd.e5.monsters` | **9,272** | SRD monster stat blocks + spec + display helpers |
| `dnd.e5.spells` | **4,229** | SRD spell data + spec + query helpers |
| `dnd.e5.options` | **3,483** | Option builder helpers: `option-cfg`, `spell-option`, `feat-option` |
| `dnd.e5.magic-items` | **3,214** | SRD magic items + spec + modifier builders |
| `dnd.e5.classes` | **3,147** | SRD class/subclass data + spec + homebrew handlers |
| `dnd.e5.character.random` | 2,462 | Name tables (all D&D races) + `random-name-result` |
| `dnd.e5.template` | 1,567 | Full 5e character template tree (SRD races, classes, backgrounds) |

### D&D 5e logic namespaces

| Namespace | Lines | Purpose |
|-----------|-------|---------|
| `dnd.e5.character` | 872 | Character spec + computed accessors (ability scores, saves, etc.) |
| `dnd.e5.modifiers` | 657 | D&D modifier constructors: `cls`, `subclass`, `trait-cfg`, `action` |
| `dnd.e5.weapons` | 438 | Weapon data + helpers |
| `dnd.e5.equipment` | 378 | Tools, instruments, packs, vehicles |
| `dnd.e5.template-base` | 328 | Warlock slot schedule, base template blocks |
| `dnd.e5.spell-lists` | 305 | Spell list maps by class and level |
| `dnd.e5.display` | 148 | Source abbreviations, weapon display strings |
| `dnd.e5.event-handlers` | 145 | Pure character mutation fns (used by events.cljs) |
| `dnd.e5.armor` | 104 | Armor data + types |
| `dnd.e5.skills` | 103 | Skill list + `skills-map` |
| `dnd.e5.event-utils` | 97 | HTTP/URL utils: `url-for-route`, `auth-headers`, `handle-api-response` |
| `dnd.e5.compute` | 84 | Pure subscription replicas for use in event handlers |
| `dnd.e5.units` | 77 | Distance unit constructors (`ft`, `ft-5`, etc.) |
| `dnd.e5.char-decision-tree` | 980 | Guided character creation decision tree |
| `dnd.e5.char-filter` | 32 | Character list filtering by name/level/class |

### Small spec-only namespaces

These just define specs for homebrew content types (SRD data lives in template.cljc):

`dnd.e5.backgrounds` (9), `dnd.e5.feats` (10), `dnd.e5.races` (15), `dnd.e5.languages` (9), `dnd.e5.selections` (30), `dnd.e5.encounters` (9), `dnd.e5.combat` (13), `dnd.e5.party` (10), `dnd.e5.folder` (8), `dnd.e5.damage-types` (16)

### Templates directory (16 files)

`src/cljc/.../dnd/e5/templates/` — Unearthed Arcana + SCAG source book content. Largest is `ua_mystic.cljc` (1,461 lines). Most are commented-out pending content status review. All are leaf dependencies (imported by `ua_base.cljc` which is imported by `template.cljc`).

## Client Layer (.cljs) — ~22K lines

### State management (re-frame)

| Namespace | Lines | Purpose |
|-----------|-------|---------|
| `dnd.e5.events` | **4,726** | ALL event handlers: character CRUD, plugin import/export, auth, party, combat |
| `dnd.e5.subs` | 1,518 | Core subscriptions: character, template, party, auth state |
| `dnd.e5.spell-subs` | 1,375 | Spell subscriptions + plugin content aggregation |
| `dnd.e5.equipment-subs` | 332 | Equipment subscriptions + custom item API loading |
| `dnd.e5.import-validation` | 1,554 | .orcbrew import validation: schema checks, unicode normalization |
| `dnd.e5.content-reconciliation` | 244 | Missing homebrew content detection |
| `dnd.e5.db` | 308 | re-frame initial DB shape, localStorage helpers |
| `dnd.e5.autosave-fx` | 79 | Throttled save (7.5s debounce) + `init-template-cache!` |

### View layer

| Namespace | Lines | Purpose |
|-----------|-------|---------|
| `dnd.e5.views` | **2,733** | Character detail page, search/Orcacle, `content-page` scaffold |
| `character-builder` | **2,165** | Guided character builder: class/level selectors, option trees, inventory |
| `dnd.e5.views.common` | 549 | Shared UI atoms: `svg-icon`, `builder-field`, `labeled-dropdown`, `character-display-name` |
| `dnd.e5.views.lists` | 896 | Character/party/monster/spell/item list browsers |
| `dnd.e5.views.builders` | 647 | Shared builder toolkit: `builder-page`, `plugin-datalist`, `option-*` helpers |
| `dnd.e5.views.combat` | 626 | Initiative tracker, encounter builder |
| `dnd.e5.views.content` | 484 | My Content pages, My Account page |
| `dnd.e5.views.auth` | 390 | Login, registration, password-reset, email-verification |
| `dnd.e5.views.header` | 359 | App header bar, nav tabs with dropdowns |
| `dnd.e5.views.import-log` | 271 | Import log slide-out panel |
| `dnd.e5.views.conflict-resolution` | 204 | Conflict resolution + export-warning modals |

### Builder child modules

All in `views/builders/`, all import from `views.builders` (shared toolkit) only. No child→child deps.

| Namespace | Lines | Domain |
|-----------|-------|--------|
| `views.builders.classes` | 740 | Class + subclass builder, modifier/level system |
| `views.builders.item` | 541 | Magic item builder |
| `views.builders.race` | 387 | Race + subrace builder |
| `views.builders.feat` | 362 | Feat builder |
| `views.builders.monster` | 305 | Monster builder |
| `views.builders.background` | 207 | Background builder |
| `views.builders.selection` | 131 | Selection (named option list) builder |
| `views.builders.spell` | 120 | Spell builder |
| `views.builders.warlock` | 74 | Invocation + pact boon builders |
| `views.builders.language` | 40 | Language builder |

## Dependency Flow

### Simplified top-level chain

```
core.cljs (entry)
  → views.* + character-builder (UI)
  → subs → events → entity → template → options → character (state)
  → spell-subs → equipment-subs (subscription chains)
  → db, autosave-fx (infrastructure)
```

### View hierarchy

```
views.common          ← leaf, no sibling deps
  ↑
views.header          ← depends on views.common only
views.auth            ← depends on views.common only
  ↑
views.builders        ← depends on views.common + views (for content-page)
  ↑
views.builders/*      ← depend on views.builders only (NO child→child)
  ↑
views (main)          ← depends on everything (character detail, search)
views.combat          ← depends on views + views.builders + views.common
views.content         ← depends on views + views.builders + views.common
views.lists           ← depends on views + views.common
  ↑
character-builder     ← depends on views + views.common + subs + events
  ↑
core.cljs             ← requires ALL views.*, dispatches routes
```

### Server dependency chain

```
server → system → {pedestal, datomic, config}
pedestal → routes → {character, template, spells, magic-items, pdf-spec, schema, email, security}
pdf → pdf-spec → character
index → views-2 (cljc, reader-conditional)
schema → {character, entity.strict, modifiers}
```

## Known Anomalies

1. **`src/cljs/orcpub/dnd/e5.cljc`** — the `orcpub.dnd.e5` namespace (plugin specs) lives in the `cljs/` source tree but is a `.cljc` file with reader conditionals. Effectively client-only.

2. **`src/cljc/orcpub/dnd/e5/party.clj`** — a server-only `.clj` alongside `party.cljc`. Both declare the same namespace. The `.clj` has server-facing logic; the `.cljc` has the shared spec.

3. **Subscription loading order** — `spell-subs` and `equipment-subs` are side-effect loaded in `core.cljs` and `subs.cljs` to ensure all `reg-sub` calls fire before use.

## Cross-references

- Builders split architecture: [views-builders-split.md](views-builders-split.md)
- Decomposition roadmap (what to split next): [monolith-decomposition-plan.md](monolith-decomposition-plan.md)
- Entity/options model: [entity-options-architecture.md](entity-options-architecture.md)
- Subscribe patterns: [re-frame-subscribe-refactor.md](re-frame-subscribe-refactor.md)
- HTTP/event patterns: [http-fx-patterns.md](http-fx-patterns.md)
- Environment/auth: [env-and-auth.md](env-and-auth.md)
