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
