# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

**Note:** See `BRANCH_SUMMARY.md` for testing checklist and merge readiness.
Known issues documented in `TODO.md` are out of scope for this release.

### Added
- Nord color themes based on [Nord palette](https://github.com/nordtheme/nord)
  - `nord-theme`: Dark theme using Polar Night (nord0-3) and Aurora colors
  - `nord-light-theme`: Light theme with Snow Storm backgrounds and Aurora accents
  - `nord-theme-elevated`: Dark theme with elevated surfaces and gradient borders
  - `nord-light-theme-elevated`: Light theme with elevated surfaces and Aurora gradients
- CSS mask-image approach for SVG icon theming
  - Enables true color control of SVG icons without inline HTML
  - Icons inherit theme colors via `currentColor`
  - Fallback support for browsers without CSS mask
- Theme-aware wrapper classes for icons (`.svg-icon-light`, `.svg-icon-dark`)
- Comprehensive documentation for SVG icon theming approach in styles/core.clj
- Version comments (0.1.01) for tracking theme-related changes

### Changed
- Updated theme schema to support 6 themes (2 original + 4 Nord variants)
- Enhanced theme toggle to cycle through all available themes
- Replaced hardcoded `.white` class with `.main-text-color` in 14 locations for theme adaptability:
  - info-block function
  - app-header, dice-roll-result, magic-item-result, name-result, tavern-name-result
  - spell-result, spell-results, monster-results, monster-result
  - orcacle, account info section, and button text
- Improved `svg-icon` function with defensive guards:
  - Handles nil, empty strings, and keyword icon names
  - Treats empty string theme override as "use subscription theme"
  - Moves theme class to wrapper element for correct CSS mask coloring

### Fixed
- SVG icons now properly adapt colors in light and dark themes
  - Fixed white-on-white icons in light theme
  - Fixed dark-on-dark icons in dark theme
- Info blocks and UI text now readable on light backgrounds
- Theme class placement ensures CSS mask receives correct color context
- Icon name parameter validation prevents crashes from nil/empty values

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

## Version History

### 0.1.01 - 2026-01-20
Theme-aware text and icon color fixes across all Nord themes
