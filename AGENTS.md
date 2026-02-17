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

### Dev Server Startup: nREPL ≠ Web Server

**Mistake made:** Started `lein repl` (nREPL on port 7888) and assumed the web server was running. The app server on port 8890 was never started.

**Root cause:** `menu.sh` lists "Start Clojure REPL" as step 2 without mentioning that `(start-server)` must be called inside the REPL. The nREPL is just a protocol endpoint — the Pedestal/Jetty web server is a separate component.

**Additional pitfall:** Running `lein repl` in a non-interactive background shell causes it to exit immediately. Use `lein repl :headless :port 7888` instead.

**Correct startup sequence (back to front):**

1. **Datomic Transactor** (port 4334):
   ```bash
   lib/datomic-free-0.9.5703/bin/transactor lib/datomic-free-0.9.5703/config/working-transactor.properties &
   ```
2. **Headless nREPL** (port 7888):
   ```bash
   lein repl :headless :port 7888 &
   ```
3. **App Server** (port 8890) — connect to nREPL and call `start-server`:
   ```bash
   echo '(start-server)' | lein repl :connect 7888
   ```
4. **Figwheel** (port 3449):
   ```bash
   lein figwheel &
   ```

**Verify all four ports:**
```bash
ss -tlnp | grep -E '4334|7888|8890|3449'
```

**Key files:**
- `src/clj/orcpub/system.clj` — port 8890 defined in `dev-service-map-overrides`
- `dev/user.clj` — `start-server`, `stop-server`, `verify-new-user`

---

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

---

**Tip:**  
Before submitting changes, review documentation and the changelog to ensure they accurately reflect the current state of the project.