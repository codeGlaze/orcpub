# SETUP.md

## OrcPub Setup & Script Reference

---

### 1. **Devcontainer**

Create `.devcontainer/devcontainer.json`:

```json
{
  "name": "OrcPub Dev",
  "image": "mcr.microsoft.com/devcontainers/base:ubuntu-22.04",
  "features": {
    "ghcr.io/devcontainers/features/java:1": {
      "version": "8",
      "installMaven": false,
      "installGradle": false
    }
  },
  "onCreateCommand": "sudo curl -fsSL https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -o /usr/local/bin/lein && sudo chmod +x /usr/local/bin/lein && lein version && curl -L -O https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh && chmod +x linux-install.sh && sudo ./linux-install.sh && rm linux-install.sh && clojure -Ttools install-latest :lib io.github.bhauman/clojure-mcp :as mcp",
  "postCreateCommand": "lein deps",
  "customizations": {
    "vscode": {
      "extensions": [
        "betterthantomorrow.calva",
        "borkdude.clj-kondo",
        "github.copilot"
      ],
      "settings": {
        "calva.replConnectSequences": [
          {
            "name": "OrcPub REPL",
            "projectType": "Leiningen",
            "cljsType": "Figwheel Main"
          }
        ]
      }
    }
  },
  "forwardPorts": [8890, 4334, 3449, 7888],
  "portsAttributes": {
    "8890": { "label": "OrcPub Server" },
    "4334": { "label": "Datomic Transactor" },
    "3449": { "label": "Figwheel" },
    "7888": { "label": "nREPL (MCP)" }
  }
}
```

> **Note:** The Leiningen devcontainer feature is no longer available, so we install Leiningen and the Clojure CLI manually via `onCreateCommand`.

---

### 2. **MCP (Model Context Protocol) Integration**

This project includes [clojure-mcp](https://github.com/bhauman/clojure-mcp) by Bruce Hauman (creator of Figwheel) for AI-assisted Clojure development.

| MCP Server | Purpose |
|------------|---------|
| **clojure-mcp** | Connects AI to your running REPL for code evaluation, debugging, and exploration |

#### Installation

clojure-mcp is installed automatically in the devcontainer. To install manually:

```bash
# Install Clojure CLI first if needed
clojure -Ttools install-latest :lib io.github.bhauman/clojure-mcp :as mcp
```

#### Configuration

MCP settings are in `.vscode/mcp.json`. The REPL must be running on port `7888` for clojure-mcp to connect.

#### Using clojure-mcp

1. Start the REPL: `lein repl` (binds to port 7888)
2. The MCP server connects automatically when you use AI features
3. AI can now evaluate Clojure code, inspect namespaces, look up docs, and more

#### How clojure-mcp Works

Once the REPL is running, clojure-mcp enables AI assistants to:

| Capability | Description |
|------------|-------------|
| **Evaluate code** | Run Clojure expressions directly in your live REPL |
| **Explore namespaces** | List and browse available namespaces and their contents |
| **Look up documentation** | Retrieve docstrings for any function, macro, or var |
| **View source code** | Inspect the source of any loaded function |
| **Debug with context** | Get live stack traces and error information |

This is powerful because AI can **test and validate code changes in real-time** rather than just suggesting edits. For example, AI can:
- Verify a function works before suggesting it
- Check database state via Datomic queries
- Explore your data structures interactively

#### Java Version Requirements

**Important:** clojure-mcp requires Java 17+, but OrcPub/Datomic requires Java 8.

The devcontainer installs both versions:
- **Java 8** (default): Used by Leiningen, Datomic, and all OrcPub code
- **Java 17**: Used only by clojure-mcp via PATH override in `.vscode/mcp.json`

This isolation is achieved by setting `JAVA_HOME` and `PATH` only for the MCP process:
```json
{
  "command": "/bin/bash",
  "args": ["-c", "JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH clojure -Tmcp start :port 7888"]
}
```

#### Known Issues

| Issue | Status | Workaround |
|-------|--------|------------|
| **Codespaces browser crash** | Platform bug | Use VS Code Desktop connected to Codespace, or test via task |
| **MCP initialize timeout** | Related to above | The `clojure-mcp (Test)` task can verify the server works |

The Codespaces browser client crashes during the MCP stdio handshake. This is a VS Code Codespaces bug, not a configuration issue. The server itself works correctly (verified via terminal/task).

---

### 3. **Claude Code Attribution (Local)**

To prevent Claude Code from adding attribution lines, set local Claude settings:

```json
{
  "attribution": {
    "commit": "",
    "pr": ""
  }
}
```

If the setting is ignored, add a local git hook (not versioned). Create `.git/hooks/pre-commit` and make it executable:

```bash
#!/bin/sh
# remove Claude attribution lines from staged commit message
git diff --staged --name-only | grep -q . || exit 0
TMPMSG=$(mktemp)
git rev-parse --git-dir >/dev/null 2>&1 || exit 0
git status --porcelain >/dev/null 2>&1
# If commit-msg hook not available here, ensure staged message cleaned via filter-branch approach:
git diff --cached --name-only | while read f; do :; done
# fallback: rewrite last commit message if it contains Claude trailer (works when committing via claude)
MSG_FILE=".git/COMMIT_EDITMSG"
if [ -f "$MSG_FILE" ]; then
  sed -i.bak '/Co-Authored-By: Claude/d; /Generated with Claude Code/d' "$MSG_FILE"
fi
exit 0
```

Ensure it is executable:

```bash
chmod +x .git/hooks/pre-commit
```

---

### 4. **Prepare Datomic Properties**

```bash
cp lib/datomic-free-0.9.5703/config/samples/free-transactor-template.properties lib/datomic-free-0.9.5703/config/working-transactor.properties
```

Edit `working-transactor.properties` as needed for dev (set passwords, etc.).

---

### 5. **start.sh**

Copy the provided `start.sh` (see your attached file or above) into your project root and make it executable:

```bash
#!/usr/bin/env bash

while true; do
  echo ""
  echo "OrcPub Service Launcher"
  echo "----------------------"
  echo "1) Start Datomic Transactor"
  echo "2) Start Clojure REPL"
  echo "3) Start Figwheel (ClojureScript)"
  echo "4) Quit"
  echo ""
  read -p "Select a service to launch [1-4]: " choice

  case $choice in
    1)
      echo "Starting Datomic Transactor..."
      lib/datomic-free-0.9.5703/bin/transactor lib/datomic-free-0.9.5703/config/working-transactor.properties &
      ;;
    2)
      echo "Starting Clojure REPL..."
      lein repl &
      ;;
    3)
      echo "Starting Figwheel..."
      lein figwheel &
      ;;
    4)
      echo "Exiting."
      exit 0
      ;;
    *)
      echo "Invalid choice."
      ;;
  esac
  sleep 1
done
```

Make it executable:

```bash
chmod +x menu.sh
```

---

### 6. **VS Code Tasks**

Create `.vscode/tasks.json`:

```json
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "Datomic Transactor",
      "type": "shell",
      "command": "lib/datomic-free-0.9.5703/bin/transactor lib/datomic-free-0.9.5703/config/working-transactor.properties",
      "isBackground": true,
      "problemMatcher": []
    },
    {
      "label": "Clojure REPL",
      "type": "shell",
      "command": "lein repl",
      "isBackground": true,
      "problemMatcher": []
    },
    {
      "label": "Figwheel",
      "type": "shell",
      "command": "lein figwheel",
      "isBackground": true,
      "problemMatcher": []
    }
  ]
}
```

---

### 5. **Leiningen Profiles**

The project includes several useful profiles in `project.clj`:

| Profile | Purpose | Usage |
|---------|---------|-------|
| `+start-server` | Auto-starts web server when REPL launches | `lein with-profile +start-server repl` |
| `+css-watch` | Auto-recompiles CSS (Garden) on file changes | `lein with-profile +start-server,+css-watch repl` |
| `+dev` | Development mode with debugging tools | `lein with-profile +dev repl` |

**Calva (VSCode):** When using Jack-in, select these profiles at the prompt.

---

### 6. **CSS Compilation (Garden)**

Styles are written in Clojure using [Garden](https://github.com/noprompt/garden) in `src/clj/orcpub/styles/`.

```bash
# Compile CSS once (also runs as prep-task before builds)
lein garden once

# Watch for changes and auto-recompile
lein garden auto
```

Output: `resources/public/css/compiled/styles.css`

**Tip:** Use the `+css-watch` profile to start garden auto alongside your REPL.

---

**Refer back to AGENTS.md for troubleshooting and onboarding steps.**