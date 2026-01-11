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

**Tip:**  
Before submitting changes, review documentation and the changelog to ensure they accurately reflect the current state of the project.