# AGENTS.md

## OrcPub Agent Onboarding & Repo Expectations

This document explains the expectations, onboarding steps, and advanced workflows for coding agents and contributors. It covers how to bring a fresh clone of the `develop` branch up to full development parity, including devcontainer, helper scripts, and troubleshooting.



### 1. **Add Devcontainer Support**

Create `.devcontainer/devcontainer.json` as described in [SETUP.md](SETUP.md).


### 2. **Add Helper Scripts**

Copy the provided `start.sh` and `menu.sh` scripts from [SETUP.md](SETUP.md) into your project root.


### 3. **Add VS Code Tasks (Optional, but recommended)**

See [SETUP.md](SETUP.md) for a ready-to-use `.vscode/tasks.json` that launches Datomic, the server, and Figwheel in dedicated terminals.


### 4. **Follow the Detailed Setup**

See [SETUP.md](SETUP.md) for:


### 5. **Troubleshooting**

  Always run scripts from the project root. If `working-transactor.properties` is missing, run `start.sh` or copy the template as shown in [SETUP.md](SETUP.md).

  Java 8 is required. The devcontainer ensures this, but check with `java -version`.

  Install with `sudo apt-get install leiningen` or use the devcontainer.

  Make scripts executable:  
  `chmod +x start.sh menu.sh`

  If a service fails to start, check for running processes with  
  `lsof -i :8890` or `lsof -i :4334` and kill as needed.


**For all setup details and script examples, see [SETUP.md](SETUP.md).**


## 6. Documentation and Changelog Requirements

  - For every new feature, configuration, or setup change, update the relevant documentation files (such as `README.md`, `SETUP.md`, or inline code comments).
  - If you add or modify scripts, document their usage and options in `SETUP.md`.
  - Ensure all onboarding steps are clear for future contributors.

  - Record all significant changes, bug fixes, and enhancements in `CHANGELOG.md`.
  - Use clear, dated entries. Example format:
    ```
    ## [YYYY-MM-DD] - Added
    - Brief description of the change or feature.
    ```
  - For each pull request or merge, ensure the changelog is updated before completion.


## 7. Agent Guidelines: Avoiding Common Mistakes

### MCP Servers Are NOT Always npm Packages

**Mistake made:** Assumed MCP (Model Context Protocol) servers are distributed via npm and fabricated package names like `@anthropic/clojure-mcp`.

**Root cause:** Many MCP servers ARE npm packages (e.g., `@modelcontextprotocol/server-*`), so there was an incorrect pattern match. The agent generated plausible-sounding but non-existent package names.

**Critical failure:** The agent did NOT properly research before making assumptions. When asked about clojure-mcp:
1. Did not read the GitHub repo the user later provided
2. Fabricated a package name based on pattern-matching
3. Subagent "research" returned hallucinated information that was accepted without verification
4. Added non-existent packages to configuration files
5. Only discovered the error when npm returned 404

**Reality:** MCP servers can be written in **any language**:

**How to avoid this mistake:**
1. **When a user provides a GitHub URL, READ IT FIRST** - it's the source of truth
2. **Never fabricate package names** - verify they exist before adding to configs
3. **Check the repo's README for installation instructions** - don't assume
4. **If unsure, ask** - "Is this an npm package or something else?"
5. **Don't trust your own "research"** - if you can't link to a real source, you may be hallucinating

### Verify Before You Configure

Before adding any dependency, tool, or MCP server:

1. **Confirm the package/tool exists** at the specified registry (npm, clojars, pypi, etc.)
2. **Read the official installation docs** from the source repository
3. **Test the installation command** if possible before committing to config files
4. **Don't trust hallucinated research** - subagent responses can contain fabricated information

### Configuration Debugging

When a configuration fails:
1. **Read the error message carefully** - `npm error 404 Not Found` means the package doesn't exist
2. **Check the source** - go to the official repository
3. **Verify the installation method** - npm, clojure tools, pip, cargo, etc.

### clojure-mcp Integration Lessons

**Key Discoveries:**

1. **Java Version Isolation**  
   - OrcPub/Datomic requires Java 8
   - clojure-mcp requires Java 17+
   - Solution: Install both, override PATH/JAVA_HOME only for MCP process

2. **VS Code MCP Configuration (`.vscode/mcp.json`)**  
   ```json
   {
     "servers": {
       "clojure-mcp": {
         "command": "/bin/bash",
         "args": ["-c", "JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH clojure -Tmcp start :port 7888"]
       }
     }
   }
   ```

3. **Codespaces Browser Bug**  
   - MCP stdio handshake crashes the VS Code browser client
   - clojure-mcp works perfectly when run manually via terminal/task
   - Workaround: Use VS Code Desktop connected to Codespace

4. **SSE Transport is NOT a Simple Alternative**  
   - SSE mode requires running clojure-mcp as HTTP server (`-Sdeps` with specific config)
   - clojure-mcp is distributed via Clojure tools, NOT Maven Central
   - Don't fabricate Maven coordinates - they won't work

5. **Testing MCP Server Independently**  
   - Create a VS Code task to run the MCP command
   - Verify JSON-RPC output appears: `{"jsonrpc":"2.0","method":"notifications/..."}`
   - If task works but MCP integration doesn't, the issue is client-side


**Tip:**
Before submitting changes, review documentation and the changelog to ensure they accurately reflect the current state of the project.

---

## 8. Why Documentation Matters

> **This is a living document.** Update it as conventions evolve or new patterns emerge. When you establish a new pattern or discover a better approach, document it here.

**Documentation saves tokens and prevents re-learning.**

- Context is lost on compact/new sessions - docs preserve it
- Lessons learned once shouldn't need re-learning (expensive)
- Large files can't be read into context - document the analysis technique instead
- Handoffs between agents/sessions need documented state
- The code is truth, but docs explain *why*

**Update docs when you:**
- Learn something non-obvious
- Solve a tricky problem (document the solution)
- Discover a pattern or anti-pattern
- Complete significant work

**Keep docs concise** - verbose docs waste tokens too.

---

## 9. First Steps

**Before doing anything else, read [`docs/CODEBASE.md`](./docs/CODEBASE.md).**

That document contains the architecture, key patterns, and learnings from previous work.

**Update `docs/CODEBASE.md`** when you learn something new about the codebase.

---

## 10. Development Setup

### Quick Start (Local)

1. **Java 8** required (not newer)
2. **Leiningen** for build/REPL
3. **Datomic transactor** running:
   ```bash
   # Windows (use bundled version due to path length bug)
   bin\transactor config/samples/free-transactor-template.properties

   # Mac/Linux
   bin/transactor config/samples/free-transactor-template.properties
   ```
4. **Backend REPL**: `lein with-profile +start-server repl`
   ```clojure
   (init-database)  ; only once per fresh DB
   (start-server)
   ```
5. **Frontend**: `lein figwheel` (hot reloads)

### Using Dev Container

The `.devcontainer/` setup handles all dependencies automatically.

### Using start.sh

The `start.sh` script automates the above steps with dependency checking.

---

## 11. Code Conventions

### Error Handling

Use the DRY macros from `src/cljc/orcpub/errors.cljc`:

```clojure
(require '[orcpub.errors :as errors])

;; Database operations
(errors/with-db-error-handling :operation-failed {:user-id id} "Failed to save"
  (d/transact conn tx-data))

;; Email operations
(errors/with-email-error-handling :email-failed {:to email} "Failed to send"
  (postal/send-message msg))

;; Parsing/validation
(errors/with-validation :invalid-input {:field "id"} "Invalid ID format"
  (Long/parseLong id-string))
```

See `docs/ERROR_HANDLING.md` for full documentation.

### File Versioning

**Every file you touch should have a version comment.** This helps track changes across sessions.

**Format** (place after the `ns` declaration):
```clojure
;; =============================================================================
;; Version: X.YY - Brief description of changes
;; =============================================================================
```

**Version numbering:**
- Pre-existing files: start at `1.01` (or continue from existing version)
- New files created by agents: start at `0.01`
- Increment minor version (`.01` → `.02`) for each significant change
- Increment major version (`1.xx` → `2.xx`) only for major rewrites

**When to update:**
- Always update the version when making functional changes
- Update the description to reflect what changed
- Check for existing version comment before adding a new one

**Example:**
```clojure
(ns orcpub.dnd.e5.events
  (:require ...))

;; =============================================================================
;; Version: 1.06 - Add export warning modal events, required field validation
;; =============================================================================
```

**Track versions in `docs/progress.md`** under "Version Summary" table.

---

## 12. Working on This Repo

### Before Making Changes

1. Read `docs/CODEBASE.md` for context
2. Understand the entity/template/modifier system if touching character logic
3. Check existing patterns in similar files

### After Making Changes

1. Run relevant tests
2. Update `docs/CODEBASE.md` if you learned something new
3. Update this file if you established new conventions

### Branching Workflow

**Always branch from `agents/develop`:**
```bash
git fetch origin
git checkout agents/develop && git pull
git checkout -b your-task-name
```

- `agents/develop` is the **clean base** for agent work
- Create a new branch for each task/feature
- Agent branches may accumulate cruft (debug code, experiments, etc.)
- **Before PR to main**: Scrub the branch or create a clean branch with cherry-picked commits
- `agents/develop` only gets: docs updates, agent settings, clean utilities
- Never PR agent cruft to main or develop

### Commit Style

Follow conventional commits (e.g., `feat:`, `fix:`, `docs:`, `refactor:`).

---

## 13. Key Documentation

| Document | Purpose |
|----------|---------|
| [`CLAUDE.md`](./CLAUDE.md) | Quick context (auto-loads in Claude Code) |
| [`CHANGELOG.md`](./CHANGELOG.md) | What's changed (features, fixes, breaking changes) |
| [`docs/progress.md`](./docs/progress.md) | Session history, current state, handoff notes |
| [`docs/CODEBASE.md`](./docs/CODEBASE.md) | **Start here.** Architecture, patterns, learnings |
| [`docs/ERROR_HANDLING.md`](./docs/ERROR_HANDLING.md) | Error handling utilities and patterns |
| [`docs/ORCBREW_FILE_VALIDATION.md`](./docs/ORCBREW_FILE_VALIDATION.md) | File import/export validation |
| [`README.md`](./README.md) | Setup, deployment, contributing |

---

## 14. Notes for Future Agents

### Codebase Quick Facts
- The modifier system uses `?symbol` syntax - this is intentional DSL, not a typo
- Datomic Free on Windows requires the bundled version (path length bug)
- Frontend changes hot-reload; backend changes need REPL reload
- `clojure.edn/read-string` is safe; `clojure.core/read-string` is not - we use the safe one

### Clojure/ClojureScript Gotchas

**Forward declarations**: Functions must be defined before use. Use `(declare fn-name)` at top of file if needed.

**Destructuring keywords**: `:db/id` becomes local binding `id`:
```clojure
(fn [{:keys [:db/id ::se/owner]}]
  (println id owner))  ; id and owner are local bindings
```

**Re-frame subscriptions**: Lazy and cached. If nil, check: Is data in app-db? Is subscription registered?

### Common Agent Mistakes

1. **Modifying test fixtures to make tests pass** - Use fixtures AS-IS to detect real bugs
2. **Assuming UI elements are clickable tiles** - Classes use `<select>` dropdown, not tiles
3. **Looking for "Yes/Confirm" buttons** - OrcPub uses `.link-button` with "delete" text
4. **Expecting `lein run` to compile CLJS** - Always `lein cljsbuild once dev` first
5. **Creating new fixtures** - Use existing ones in `test/` directory
6. **"Helping" by cleaning up state** - Sometimes broken state is intentional (e.g., for warnings)
7. **Assuming bugs are regressions** - Use `git blame` - many bugs are old code never triggered before

### Validation Philosophy

**Import = Permissive, Export = Strict**

This is a core design principle for data handling:

- **On Import**: Accept as much as possible, auto-fix issues silently
  - Fill missing required fields with placeholder data like `[Missing Name]`
  - Normalize Unicode to ASCII
  - Clean up nil values, fix syntax issues
  - Log changes but don't block import
  - Goal: recover user's data even if corrupted

- **On Export**: Warn about issues, let user decide
  - Show modal listing all problems
  - Provide "Cancel" (go fix it) and "Export Anyway" (fill placeholders)
  - Log issues to console for debugging
  - Goal: help users produce clean files, but don't block them

### Placeholder Text Convention

Use square brackets for auto-filled placeholder data:
- `[Missing Name]`
- `[Missing Trait Name]`

This makes placeholders obvious and easily searchable in exported files.

### Debug Files

The `debug-examples/` directory contains test files:
- `serakat-all-content.orcbrew` - Large file (3.5MB) with 1598 smart quotes, 36 nil nil patterns
- Use `lein with-profile +tools prettify-orcbrew <file> --analyze` to analyze without loading into context
