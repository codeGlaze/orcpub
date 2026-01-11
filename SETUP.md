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
    "ghcr.io/devcontainers/features/java:1": { "version": "8" },
    "ghcr.io/devcontainers/features/leiningen:1": {}
  },
  "postCreateCommand": "lein --version"
}
```

---

### 2. **Prepare Datomic Properties**

```bash
cp lib/datomic-free-0.9.5703/config/samples/free-transactor-template.properties lib/datomic-free-0.9.5703/config/working-transactor.properties
```

Edit `working-transactor.properties` as needed for dev (set passwords, etc.).

---

### 3. **start.sh**

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