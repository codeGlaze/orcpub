# Changelog

All notable changes to this project will be documented in this file.

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

### Added
- **Import Changelog Panel**: Slide-in UI showing all auto-fixes applied during orcbrew import
  - Collapsible sections for Errors, Skipped Items, Auto-Fixes
  - Always-visible button (bottom-right) with badge count
  - Auto-expands after import if changes were made
- **Two-phase orcbrew cleaning**: String-level (syntax) then data-level (semantic)
- **Field-specific nil handling**: Preserve semantic nils, remove accidental nils, replace with defaults
- **Agent onboarding system**: CLAUDE.md, /onboard, /onboard-deep, /new-task skills
- **Comprehensive error handling**: DRY macros for DB, email, validation errors
- **Progressive import strategy**: Imports valid items, skips invalid ones
- **Unicode→ASCII normalization**: Automatically converts smart quotes, em-dashes, special spaces, etc. to ASCII equivalents during import and homebrew save (40+ character mappings)
- **Lein prettify-orcbrew tool**: CLI tool for analyzing/debugging orcbrew files (`lein with-profile +tools prettify-orcbrew file.orcbrew --analyze`)
- **:tools profile**: Skip Garden compilation for faster CLI tool execution
- **Required field validation**:
  - On import: auto-fills missing required fields (`:name`, `:level`, `:school`, etc.) with placeholder data like "[Missing Name]"
  - On export: shows warning modal listing items with missing fields, with "Export Anyway" button
  - Supports all content types: classes, subclasses, races, subraces, backgrounds, feats, spells, monsters, invocations, languages, selections, encounters
  - Also validates traits for missing `:name` fields

### Fixed
- Delete-all-plugins now instant (was doing full page reload)
- Multi-plugin file item count (was showing 0)
- Hyphenated field regex matching (`:spell-list-kw` etc.)
- Duplicate key error from string-level regex manipulation
- **Root cause of `nil nil` corruption**: `set-class-path-prop` was calling `(assoc-in class nil nil)` when optional path args not provided; now uses `cond->` to skip nil paths
- **PDF nil crashes**: Added nil guards throughout PDF generation code (`pdf_spec.cljc`, `pdf.clj`) to prevent crashes on missing/malformed data; uses fallback strings like "(unknown)" instead of crashing

### Changed
- Import log icons use neutral colors (gray/blue) instead of red for non-errors
- Chevron-right close button indicates panel slides away

### Documentation
- CLAUDE.md: Auto-loading context for Claude Code
- AGENTS.md: Model-neutral guidelines, branching workflow
- docs/CODEBASE.md: Architecture, patterns, learnings
- docs/progress.md: Session state, handoff notes
- docs/ORCBREW_FILE_VALIDATION.md: Unicode normalization, lein tool docs
- .claude/settings.json: Onboarding skills

---

## [Previous]

See git history for changes prior to this changelog.
# Changelog

All notable changes to this project will be documented in this file.

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
