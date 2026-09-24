# Namespace Architecture Map

Comprehensive reference for agents working on this codebase. Consult before scanning the repo.

**Line counts measured 2026-09-15 on `agents/develop`.** They drift every commit — treat a figure
here as an order of magnitude and re-measure before acting on one. A count that disagrees with
`wc -l` is this doc's bug, not the code's.

> **Corrected 2026-09-15.** 69 of the 89 counts in the previous revision were wrong, several by an
> order of magnitude, and two sections described refactors as complete that have never been merged
> here. What changed and why is at the bottom, under *Corrections*.

## Directory Layout

```
src/
  clj/orcpub/                    — server-only (JVM Clojure)
  cljc/orcpub/                   — shared (JVM + CLJS)
    dnd/e5/                      — D&D 5e domain
    dnd/e5/templates/            — UA/SCAG source book content (16 files)
  cljs/orcpub/                   — client-only (ClojureScript)
    dnd/e5/views/                — 4 extracted view modules (the rest is views.cljs)

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
| HTTP routes | `orcpub.routes` | `.../routes.clj` (2,015 lines) | All API endpoints + interceptor chains |
| HTML shell | `orcpub.index` | `.../index.clj` | Server-renders SPA container with CSP nonce |
| CLJS entry | `orcpub.core` | `web/cljs/.../core.cljs` | `[:initialize-db]`, mount reagent root, HTML5History routing |
| Route constants | `orcpub.route-map` | `.../route_map.cljc` | Bidi route table shared by server + client |

## Server Layer (.clj) — 27 namespaces, 12,079 lines

| Namespace | Lines | Purpose |
|---|---|---|
| `orcpub.server` | 9 | JVM entry point |
| `orcpub.system` | 107 | Stuart Sierra Component system |
| `orcpub.pedestal` | 144 | Interceptors: db injection, ETag, CSP nonce, static files |
| `orcpub.routes` | 2,015 | Full HTTP route table + handlers |
| `orcpub.routes.folder` | 69 | Folder CRUD handlers |
| `orcpub.routes.party` | 129 | Party CRUD handlers |
| `orcpub.config` | 426 | Env var reading, Datomic URI, CSP config |
| `orcpub.datomic` | 59 | Datomic connection lifecycle |
| `orcpub.db.schema` | 418 | Datomic attribute definitions |
| `orcpub.email` | 465 | SMTP: verification, password-reset, error-notify |
| `orcpub.security` | 98 | Login rate limiting (per-username, per-IP) |
| `orcpub.oauth` | 10 | OAuth URL helpers |
| `orcpub.pdf` | 3,638 | PDFBox 3.x: character sheets, spell cards, stat blocks |
| `orcpub.index` | 288 | Server-rendered HTML shell (hiccup + CSP nonce) |
| `orcpub.csp` | 52 | CSP nonce generation (128-bit SecureRandom) |
| `orcpub.favicon` | 26 | Favicon link tag |
| `orcpub.privacy` | 264 | Server-rendered privacy policy |
| `orcpub.time` | 177 | java.time wrappers (post clj-time migration) |
| `orcpub.styles.core` | 3,006 | Garden CSS-in-Clojure |
| `orcpub.tools.orcbrew` | 261 | CLI tool for .orcbrew inspection |
| `orcpub.fork.integrations` | 315 | Optional third-party `<head>` integrations, env-configured; all ship commented out |
| `orcpub.fork.branding` | 216 | Branding config. Server is source of truth; `index.clj` injects it as `window.__BRANDING__` |
| `orcpub.fork.privacy-content` | 78 | Fork-specific privacy policy copy |
| `orcpub.fork.user-data` | 30 | User enrichment hooks — API response fields + registration defaults. Pass-through by default |
| `orcpub.fork.auth` | 25 | Session length and login-tracking config |
| `orcpub.build.demo-emit` | 74 | Build-time emitter for the bundled demo pack (`lein gen-demo`); output is committed |
| `orcpub.dnd.e5.modifier-macros` | 35 | JVM macros for the modifier DSL (`skill-proficiency-2` and friends) |

## Shared Layer (.cljc) — 85 namespaces, 44,857 lines

### Core primitives

| Namespace | Lines | Purpose |
|---|---|---|
| `orcpub.entity` | 825 | Core entity model: options tree traversal, modifier accumulation, `build` |
| `orcpub.entity.strict` | 38 | Entity spec definitions |
| `orcpub.entity-spec` | 131 | The cell engine: `entity-val` accessor + `q` macro |
| `orcpub.template` | 157 | Template vocabulary: `selection-cfg`, `option-cfg`, `make-modifier-map` |
| `orcpub.modifiers` | 124 | Modifier type constants + `modifier` macro |
| `orcpub.common` | 601 | String/keyword utilities, `name-to-kw` |
| `orcpub.dice` | 82 | Dice rolling: `die-roll`, `dice-roll`, `standard-roll` |
| `orcpub.components` | 83 | Reagent UI primitives: checkbox, labeled-checkbox |
| `orcpub.route-map` | 206 | Bidi route constants + `path-for` (shared client/server) |
| `orcpub.pdf-spec` | 865 | Character -> flat PDF field map |
| `orcpub.registration` | 75 | Password/email/username validation |
| `orcpub.errors` | 170 | Error codes + `with-db-error-handling` macro |
| `orcpub.views-aux` | 148 | `option-selector-data` + option UI helpers |
| `orcpub.image-url` | 153 | What can be told about a picture's address without asking anyone |
| `orcpub.whats-new` | 89 | Release highlights for the What's New panel |
| `orcpub.constants` | 3 | `header-height 320` |

### D&D 5e content namespaces

The largest are data with a thin spec/helper shell on top — the obvious split candidates. They
have been split on a branch, but **not here**; see *Corrections*.

| Namespace | Lines | Content |
|---|---|---|
| `dnd.e5.monsters` | 9,273 | SRD monster stat blocks + specs + display helpers |
| `dnd.e5.spells` | 4,331 | School constants, alphabetical spell vectors, derived lookups |
| `dnd.e5.options` | 4,091 | **The assembly layer.** Authored content -> template pieces (`race-option`, `class-option`, the `*-selection` builders) |
| `dnd.e5.magic-items` | 3,210 | Magic item data, predicates, internal-format converters |
| `dnd.e5.classes` | 3,177 | 12 class option builders + `class-level` + local helpers |
| `dnd.e5.character.random` | 2,462 | Name tables (all D&D races) + `random-name-result` |
| `dnd.e5.template` | 1,588 | The 5e selection tree, assembled. **Data, not logic** — 9 functions in 1,588 lines |
| `dnd.e5.template-base` | 331 | The blank character every build starts from |

How these fit together — authored content to a built character — is covered by
`content-to-character-pipeline.md` (written on `feature/grant-rows`; arrives here when that
branch merges).

### D&D 5e logic namespaces

| Namespace | Lines | Purpose |
|---|---|---|
| `dnd.e5.character` | 890 | Character spec + computed accessors (ability scores, saves, etc.) |
| `dnd.e5.char-decision-tree` | 980 | Guided character creation decision tree |
| `dnd.e5.modifiers` | 735 | D&D modifier constructors: `cls`, `subclass`, `trait-cfg`, `action` |
| `dnd.e5.weapons` | 472 | Weapon data + helpers |
| `dnd.e5.spell-packing` | 443 | Which spell level goes in which box of a printed spell page |
| `dnd.e5.equipment` | 378 | Tools, instruments, packs, vehicles |
| `dnd.e5.share-bundle` | 323 | The homebrew a character depends on, bundled for sharing |
| `dnd.e5.builder-fields` | 307 | Utilities over a builder FIELD SCHEMA |
| `dnd.e5.spell-lists` | 305 | Spell list maps by class and level |
| `dnd.e5.content-types` | 172 | Single source of truth describing each homebrew content type |
| `dnd.e5.orcbrew-format` | 171 | `.orcbrew` constants, serialization and format-versioning (JVM + browser) |
| `dnd.e5.views-2` | 158 | Reader-conditional views shared with the server-rendered shell |
| `dnd.e5.srd-starting-equipment` | 154 | Derives a class's starting equipment from the live class definition |
| `dnd.e5.display` | 148 | Source abbreviations, weapon display strings |
| `dnd.e5.event-handlers` | 145 | Pure character mutation fns (used by events.cljs) |
| `dnd.e5.grant-pools` | 128 | THE registry of grantable pools |
| `dnd.e5.starting-equipment-ledger` | 120 | A homebrew class's starting equipment as an SRD base plus changes |
| `dnd.e5.demo-content` | 120 | The demo content pack as a declarative recipe, proofed by the build |
| `dnd.e5.armor` | 104 | Armor data + types |
| `dnd.e5.skills` | 103 | Skill list + `skills-map` |
| `dnd.e5.event-utils` | 97 | HTTP/URL utils: `url-for-route`, `auth-headers`, `handle-api-response` |
| `dnd.e5.compute` | 92 | Pure subscription replicas for use in event handlers |
| `dnd.e5.armor-class` | 84 | The Armor Class engine |
| `dnd.e5.content-specs` | 80 | Ties SAVE and LOAD validation together per content type |
| `dnd.e5.units` | 78 | Distance unit constructors (`ft`, `ft-5`, etc.) |
| `dnd.e5.spell-annotations` | 60 | The marks printed beside a spell's name on a sheet |
| `dnd.e5.requirements` | 35 | Registry of facts an effect can gate on: `:dual-wielding?`, `:armor?` |
| `dnd.e5.page-map` | 35 | Route -> view-fn bindings for builder pages, generated at COMPILE TIME |
| `dnd.e5.content-pools` | 26 | The POOL half of the content-extensibility spine |
| `dnd.e5.char-filter` | 32 | Character list filtering by name/level/class |

### Small spec-only namespaces

Mostly specs for homebrew content types (the SRD data itself lives in the content namespaces
above):

`dnd.e5.backgrounds` (9), `dnd.e5.feats` (10), `dnd.e5.races` (40), `dnd.e5.languages` (46), `dnd.e5.selections` (30), `dnd.e5.encounters` (9), `dnd.e5.combat` (13), `dnd.e5.party` (20), `dnd.e5.folder` (8), `dnd.e5.damage-types` (16), `dnd.e5.field-schemas` (14), `dnd.e5.character-props` (10), `dnd.e5.character.equipment` (32), `dnd.e5.common` (4)

### Templates directory (16 files)

`src/cljc/.../dnd/e5/templates/` — Unearthed Arcana + SCAG source book content. Largest is `ua_mystic.cljc` (1,461 lines). Mostly inert: 12 of the 16 have no live top-level
defs, pending content status review. All are leaf dependencies (imported by `ua_base.cljc` which is imported by `template.cljc`).

## Client Layer (.cljs) — 22 namespaces, 29,854 lines

### State management (re-frame)

| Namespace | Lines | Purpose |
|---|---|---|
| `dnd.e5.events` | 6,687 | ALL event handlers: character CRUD, plugin import/export, auth, party, combat |
| `dnd.e5.orcbrew-validation` | 2,293 | `.orcbrew` import/export validation: schema checks, unicode normalization |
| `dnd.e5.subs` | 1,697 | Core subscriptions: character, template, party, auth state |
| `dnd.e5.spell-subs` | 1,594 | Spell subscriptions + plugin content aggregation. **Compiles plugin `:props` into modifiers** before content reaches `options.cljc` |
| `dnd.e5.db` | 574 | re-frame initial DB shape, localStorage helpers |
| `dnd.e5.content-reconciliation` | 574 | Missing homebrew content detection |
| `dnd.e5.equipment-subs` | 355 | Equipment subscriptions + custom item API loading |
| `dnd.e5.share-url` | 172 | Codec: homebrew bundle -> URL-safe fragment payload and back |
| `dnd.e5.http-safe` | 90 | Drop-in replacement for the `cljs-http.client` request fns |
| `dnd.e5.autosave-fx` | 79 | Throttled save (7.5s debounce) + `init-template-cache!` |

### View layer

`views.cljs` is the repo's biggest file and holds the builder pages too — the split into
`views/builders/*` described in the previous revision of this doc is **not on this branch**; see
*Corrections*.

| Namespace | Lines | Purpose |
|---|---|---|
| `dnd.e5.views` | 10,820 | Character detail page, search/Orcacle, `content-page` scaffold, **and every homebrew builder page** — the largest file in the repo |
| `character-builder` | 2,724 | Guided character builder: class/level selectors, option trees, inventory |
| `dnd.e5.views.conflict-resolution` | 570 | Conflict resolution + export-warning modals |
| `dnd.e5.views.import-log` | 334 | Import log slide-out panel |
| `dnd.e5.views.notifications` | 148 | Message banner, callout box, contextual banners |
| `dnd.e5.views.whats-new` | 101 | What's New panel |
| `orcpub.image-capture` | 260 | Reads a character's picture in the browser so an export can carry the bytes |
| `orcpub.core` | 158 | Entry point: initialize-db, mount reagent root, HTML5History routing |
| `orcpub.dnd.e5` | 208 | Plugin specs. A `.cljc` in the `cljs/` tree — see Anomalies |
| `orcpub.user-agent` | 50 | Browser, device, platform detection |
| `orcpub.fork.user-tier` | 16 | User tier abstraction for feature gating |
| `orcpub.ver` | 16 | Build version constant |

### Builder child modules

**Does not exist on this branch.** See *Corrections*.

## Dependency Flow

### Simplified top-level chain

```
core.cljs (entry)
  → character-builder + views (UI)
  → subs → events → entity → template → options → character (state)
  → spell-subs → equipment-subs (subscription chains)
  → db, autosave-fx (infrastructure)
```

### View hierarchy

```
views.notifications   ← leaf
views.whats-new       ← leaf
  ↑
views (main)          ← character detail, search, AND all builder pages
views.conflict-resolution
views.import-log
  ↑
character-builder     ← depends on views + subs + events
  ↑
core.cljs             ← requires views, views-2, character-builder; dispatches routes
```

### Server dependency chain

```
server → system → {pedestal, datomic, config}
pedestal → routes → {character, template, spells, magic-items, pdf-spec, schema, email, security}
pdf → pdf-spec → character
index → views-2 (cljc, reader-conditional)
schema → {modifiers, entity.strict, character, units, party, folder, magic-items,
          weapons, spells, common, character.equipment}
```

## Known Anomalies

1. **`src/cljs/orcpub/dnd/e5.cljc`** — the `orcpub.dnd.e5` namespace (plugin specs) lives in the
   `cljs/` source tree but is a `.cljc` file with reader conditionals. Effectively client-only.

2. **`src/cljc/orcpub/dnd/e5/party.clj`** — a server-only `.clj` alongside `party.cljc`. Both
   declare `orcpub.dnd.e5.party` (10 lines each). The `.clj` has server-facing logic; the `.cljc`
   has the shared spec.

3. **Subscription loading order** — `core.cljs` side-effect requires `subs` and `equipment-subs`;
   `subs.cljs` in turn side-effect requires `spell-subs`, so every `reg-sub` fires before use.

4. **`orcpub.dnd.e5.modifier-macros` is a `.clj` under `src/clj/`** despite being 5e domain code.
   It has to be: macros are expanded on the JVM.

## Cross-references

- Entity/options model: [entity-options-architecture.md](entity-options-architecture.md)
- Decomposition roadmap (what to split next): [monolith-decomposition-plan.md](monolith-decomposition-plan.md)
- Builders split architecture: [views-builders-split.md](views-builders-split.md) — describes the
  unmerged branch, not this one
- Subscribe patterns: [re-frame-subscribe-refactor.md](re-frame-subscribe-refactor.md)
- HTTP/event patterns: [http-fx-patterns.md](http-fx-patterns.md)
- Environment/auth: [env-and-auth.md](env-and-auth.md)

## Corrections

**2026-09-15.** Every line count was re-measured against `agents/develop`. 69 of the 89 in the
previous revision were wrong. Most had simply drifted, but four claims were wrong in kind:

| Previous revision said | Actually |
| --- | --- |
| "D&D 5e data namespaces (**Tier 1 extraction DONE**)", listing `monsters-data` (9,226), `spells-data` (4,187), `magic-items-data` (2,721), `classes-data` (3,137) | No `*_data.cljc` file exists on this branch. The extraction lives only on `refactor/data-extraction`, unmerged. `dnd.e5.monsters` is one 9,273-line file |
| A `views/builders/` directory of 10 builder modules, and `views.common` / `views.lists` / `views.builders` / `views.combat` / `views.content` / `views.auth` / `views.header` | None exist here. The split lives only on `feature/cross-platform-scripts`, which is 1,200 commits behind `agents/develop`. `dnd.e5.views` is one 10,820-line file holding all of it |
| `dnd.e5.import-validation` (1,554) | Renamed. It is `dnd.e5.orcbrew-validation` (2,293) |
| `orcpub.constants` — `header-height 227` | `header-height 320` |

The worst drift, for calibration: `orcpub.pdf` 673 -> 3,638, `dnd.e5.views` 2,733 -> 10,820,
`orcpub.styles.core` 1,587 -> 3,006, `dnd.e5.events` 4,726 -> 6,687.

Roughly 25 namespaces that existed were missing from the tables entirely, among them the whole
`orcpub.fork.*` family, `grant-pools`, `content-types`, `content-specs`, `share-bundle` and
`orcbrew-format`.

Lesson worth keeping: a doc that states a refactor is DONE, when it is done only on the author's
branch, is worse than one that says nothing. Say which branch.
