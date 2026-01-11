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
    },
    "ghcr.io/devcontainers/features/node:1": {
      "version": "lts"
    }
  },
  "onCreateCommand": "sudo curl -fsSL https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -o /usr/local/bin/lein && sudo chmod +x /usr/local/bin/lein && lein version",
  "postCreateCommand": "lein deps && npm install -g @anthropic/clojure-mcp @anthropic/github-mcp",
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

> **Note:** The Leiningen devcontainer feature (`ghcr.io/devcontainers/features/leiningen:1`) is no longer available, so we install it manually via `onCreateCommand`.

---

### 2. **MCP (Model Context Protocol) Integration**

This project includes MCP support for AI-assisted development. The following MCPs are configured:

| MCP Server | Purpose |
|------------|---------|
| **clojure-mcp** | Connects AI to your running REPL for code evaluation, debugging, and exploration |
| **github-mcp** | GitHub integration for issues, PRs, and repository operations |

#### Configuration

MCP settings are in `.vscode/mcp.json`. The REPL must be running on port `7888` for clojure-mcp to connect.

#### Using clojure-mcp

1. Start the REPL: `lein repl` (will bind to port 7888)
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

---

### 3. **Prepare Datomic Properties**

```bash
cp lib/datomic-free-0.9.5703/config/samples/free-transactor-template.properties lib/datomic-free-0.9.5703/config/working-transactor.properties
```

Edit `working-transactor.properties` as needed for dev (set passwords, etc.).

---

### 4. **start.sh**

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

### 4. **VS Code Tasks**

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

**Refer back to AGENTS.md for troubleshooting and onboarding steps.**