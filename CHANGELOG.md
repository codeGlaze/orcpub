# Changelog

All notable changes to this project will be documented in this file.

---

## [2026-01-11] - Added

### Devcontainer Improvements
- Fixed broken Leiningen devcontainer feature by installing via `onCreateCommand`
- Added Node.js feature for MCP server support
- Added port forwarding for nREPL (7888) to support MCP integration
- Added GitHub Copilot extension to recommended extensions

### MCP (Model Context Protocol) Integration
- Added `.vscode/mcp.json` with clojure-mcp and github-mcp configurations
- Updated `project.clj` with fixed nREPL port (7888) for MCP connectivity
- Added MCP documentation to SETUP.md

### Documentation
- Updated SETUP.md with new devcontainer configuration
- Added MCP integration section to SETUP.md
- Added note about deprecated Leiningen feature workaround

---
