# Changelog

All notable changes to this project will be documented in this file.

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
