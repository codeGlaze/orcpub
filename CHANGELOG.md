# Changelog

All notable changes to this project will be documented in this file.

---

## [2026-02-16] - Fixed

### Magic Item Builder — Custom Weapon Defaults
- Fixed `apply-subtype-toggle` writing defaults to `::mi/` namespace keys instead of `::weapons5e/` keys, causing custom weapon properties (damage die, type, melee/ranged, damage type) to be silently dropped during serialization
- Fixed `remove-custom-weapon-fields` wrapping the entire case expression, stripping defaults even for the `:other` (Custom) path
- Added `::weapons5e/damage-type` default (`:bludgeoning`) when toggling to Custom

### Magic Item Builder — Save Without Reload
- Fixed `item-save-success` to update `::mi/custom-items` in app-db so saved items appear in the item list immediately without a page reload

## [2026-02-16] - Added

### Weapon Properties — Special and Loading (#276)
- Added "Special?" checkbox to custom weapon builder (UI, event, subscription; schema and serialization already existed)
- Added "Loading?" property end-to-end: DB schema, serialization, subscription, event handler, UI checkbox
- Tagged existing weapons with `::loading? true` per 5e PHB: crossbow (light, hand, heavy), blowgun, firearm (hand, long)
- Added round-trip serialization tests verifying custom weapon properties survive the `apply-subtype-toggle` → `from-internal-item` pipeline

---

## [2026-01-11] - Updated

### Java Version Isolation for clojure-mcp
- clojure-mcp requires Java 17+, OrcPub/Datomic requires Java 8
- Updated devcontainer to install both Java 8 (default) and Java 17 (for MCP only)
- MCP config uses PATH/JAVA_HOME override to isolate Java 17 to MCP process

### Known Issues Documented
- Codespaces browser client crashes during MCP initialize handshake (platform bug)
- Workaround: Use VS Code Desktop connected to Codespace
- Added `clojure-mcp (Test)` task for manual verification

### Documentation Updates
- Updated SETUP.md with Java version requirements table
- Added Known Issues section with workarounds
- Updated AGENTS.md with clojure-mcp integration lessons learned
- Documented that SSE transport requires Clojure deps, not Maven Central artifacts

---

## [2026-01-11] - Added

### Devcontainer Improvements
- Fixed broken Leiningen devcontainer feature by installing via `onCreateCommand`
- Added Clojure CLI installation for clojure-mcp support
- Added port forwarding for nREPL (7888) to support MCP integration
- Added GitHub Copilot extension to recommended extensions
- Removed unnecessary Node.js feature (clojure-mcp is Clojure-based, not npm)

### MCP (Model Context Protocol) Integration
- Added `.vscode/mcp.json` with [clojure-mcp](https://github.com/bhauman/clojure-mcp) configuration
- clojure-mcp by Bruce Hauman enables AI to evaluate code in your live REPL
- Updated `project.clj` with fixed nREPL port (7888) for MCP connectivity
- Added MCP documentation to SETUP.md

### Documentation
- Updated SETUP.md with new devcontainer configuration
- Added MCP integration section with installation and usage instructions
- Added "How clojure-mcp Works" documentation

---
