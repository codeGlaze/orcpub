# AGENTS.md

## OrcPub Agent Onboarding

This document explains how to bring a fresh clone of the `develop` branch up to full development parity, including devcontainer, scripts, and troubleshooting.

---

### 1. **Add Devcontainer Support**

Create `.devcontainer/devcontainer.json` as described in [SETUP.md](SETUP.md).

---

### 2. **Add Helper Scripts**

Copy the provided `start.sh` and `menu.sh` scripts from [SETUP.md](SETUP.md) into your project root.

---

### 3. **Add VS Code Tasks (Optional, but recommended)**

See [SETUP.md](SETUP.md) for a ready-to-use `.vscode/tasks.json` that launches Datomic, the server, and Figwheel in dedicated terminals.

---

### 4. **Follow the Detailed Setup**

See [SETUP.md](SETUP.md) for:
- How to prepare Datomic config files
- How to use the scripts
- How to customize your environment

---

### 5. **Troubleshooting**

- **File Not Found:**  
  Always run scripts from the project root. If `working-transactor.properties` is missing, run `start.sh` or copy the template as shown in [SETUP.md](SETUP.md).

- **Java Version:**  
  Java 8 is required. The devcontainer ensures this, but check with `java -version`.

- **Leiningen Not Found:**  
  Install with `sudo apt-get install leiningen` or use the devcontainer.

- **Permissions:**  
  Make scripts executable:  
  `chmod +x start.sh menu.sh`

- **Port Conflicts:**  
  If a service fails to start, check for running processes with  
  `lsof -i :8890` or `lsof -i :4334` and kill as needed.

---

**For all setup details and script examples, see [SETUP.md](SETUP.md).**

---

## 6. Documentation and Changelog Requirements

- **Documentation:**  
  - For every new feature, configuration, or setup change, update the relevant documentation files (such as `README.md`, `SETUP.md`, or inline code comments).
  - If you add or modify scripts, document their usage and options in `SETUP.md`.
  - Ensure all onboarding steps are clear for future contributors.

- **Changelog:**  
  - Record all significant changes, bug fixes, and enhancements in `CHANGELOG.md`.
  - Use clear, dated entries. Example format:
    ```
    ## [YYYY-MM-DD] - Added
    - Brief description of the change or feature.
    ```
  - For each pull request or merge, ensure the changelog is updated before completion.

---

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
- **Clojure:** [bhauman/clojure-mcp](https://github.com/bhauman/clojure-mcp) - installed via `clojure -Ttools install-latest`
- **Python:** Many MCP servers use `uvx` or `pip`
- **Node.js:** Use `npx` or `npm install -g`
- **Go, Rust, etc.:** Native binaries

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

---

**Tip:**  
Before submitting changes, review documentation and the changelog to ensure they accurately reflect the current state of the project.