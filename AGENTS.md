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
  If a service fails to start, use the built-in kill subcommands:
  ```bash
  ./start.sh kill-all --yes        # Kill all OrcPub processes
  ./start.sh kill-port 8890 --yes  # Kill specific port
  ```
  Or check manually with `lsof -i :8890` or `lsof -i :4334`.

---

**For all setup details and script examples, see [SETUP.md](SETUP.md).**

---

## 6. Process Management with start.sh

The `start.sh` script includes kill/stop subcommands for cleanly stopping stray nREPL, CLJS servers, or OrcPub processes without guessing PIDs.

### Available Subcommands

| Command | Description |
|---------|-------------|
| `./start.sh` | Start Datomic, server, and REPL (default) |
| `./start.sh kill-repl` | Kill nREPL processes (port 7888) |
| `./start.sh kill-server` | Kill OrcPub server (port 8890) |
| `./start.sh kill-datomic` | Kill Datomic transactor (port 4334) |
| `./start.sh kill-port <port>` | Kill process on a specific port |
| `./start.sh kill-name <pattern>` | Kill processes matching a pattern |
| `./start.sh kill-all` | Kill all OrcPub-related processes |
| `./start.sh help` | Show help message |

### Options

- `--yes` or `-y`: Skip the confirmation prompt
- `--force` or `-f`: Send SIGKILL if SIGTERM doesn't stop the process

### Examples

```bash
# Interactive - shows what will be killed and asks for confirmation
./start.sh kill-repl

# No prompt, use SIGTERM
./start.sh kill-repl --yes

# No prompt, escalate to SIGKILL if needed
./start.sh kill-repl --yes --force

# Kill a specific port
./start.sh kill-port 7888 --yes

# Kill by process name pattern
./start.sh kill-name "lein run" --yes

# Clean slate - kill everything OrcPub-related
./start.sh kill-all --yes --force
```

### Environment Variables

You can override the default ports:
- `NREPL_PORT` (default: 7888)
- `SERVER_PORT` (default: 8890)
- `DATOMIC_PORT` (default: 4334)

### How It Works

1. **Process Discovery**: Uses `lsof` to find PIDs by port, with fallback to `ss`/`netstat`. Uses `pgrep` to find PIDs by name pattern, with fallback to `ps`.
2. **Confirmation**: Shows exactly which processes will be killed (PID, user, command) and asks for confirmation (skip with `--yes`).
3. **Graceful Termination**: Sends SIGTERM first and waits 3 seconds.
4. **Force Kill**: If `--force` is specified and processes survive SIGTERM, escalates to SIGKILL.

---

## 7. Documentation and Changelog Requirements

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