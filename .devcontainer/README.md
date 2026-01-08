# Orcpub Codespace Devcontainer

This Codespace is designed for Clojure/ClojureScript development using Leiningen and Figwheel.

## How to Use

1. Open this repo in GitHub Codespaces.
2. Wait for the container to build and dependencies to download.
3. **To start the hot-reloading dev server:**
   ```
   lein figwheel
   ```
   - The site will be available at https://`<your-codespace>`-8890.githubpreview.dev
   - Figwheel websocket port 3449 and nREPL 7888 are forwarded for REPL tooling.
4. Use Calva (VS Code extension) to jack-in to nREPL if you want interactive REPL or in-browser eval.

## Notes

- Ports 8890, 3449, and 7888 are pre-forwarded for Figwheel and nREPL.
- If you use another Leiningen alias for dev, adjust `lein figwheel` accordingly.
- Add more ports to forward as needed in `devcontainer.json`.

### Devcontainer build details

- Build context: `devcontainer.json` sets `"build": { "context": ".." }` so the Docker build runs from the repository root; this is required so the Dockerfile can `COPY project.clj` and other repo files during the image build.
- Leiningen: The Dockerfile now installs Leiningen and runs it once so `lein` is available for editor/LSP tasks (e.g., `lein with-profile +test,+dev classpath`). After changes to the devcontainer, rebuild the container (`Dev Containers: Rebuild Container`) to pick up the change.
- Apt install uses `--no-install-recommends` and clears `apt` lists to avoid pulling unnecessary recommended packages (e.g., `python3`) and to keep the image small.
- Vendored snapshot: This project vendors `org.apache.pdfbox:pdfbox:2.1.0-SNAPSHOT` in `lib/` because the vendor no longer publishes that snapshot. The Dockerfile copies `lib/` into the image before running `lein deps`, and CI copies `lib/` into `~/.m2/repository` to make the artifact available during builds and tests.
- Fonts & X libs: The container now installs font and X libraries (e.g., `libfreetype6`, `fontconfig`, `fonts-dejavu-core`, and related `libx*` packages) to support Java AWT/Swing (needed by some tools like Calva during a GUI-based jack-in). If you hit errors like `UnsatisfiedLinkError: libfreetype.so.6` or `NoClassDefFoundError: Could not initialize class sun.font.SunFontManager`, rebuild the devcontainer (`Dev Containers: Rebuild Container`) or install the packages inside the running container and restart your editor session.
- Dev setup helper: Use `./scripts/dev-setup.sh` to perform idempotent setup (start datomic, wait for it, run `lein deps`, and run the idempotent DB init).
  - Examples:
    - `bash ./scripts/dev-setup.sh --no-start` (recommended for `postCreateCommand`)
    - `bash ./scripts/dev-setup.sh --start` (also starts the backend & figwheel in the background)

- Monitoring options (tmux vs VS Code Tasks):
  - **tmux (recommended for terminal users / remote shells)**
    - Run `bash ./scripts/dev-monitor.sh` to open a tmux session with windows for the server, Figwheel, REPL, and combined logs.
    - Pros: single place to see everything, works in plain terminals, detachable (`Ctrl-b d`).
    - Cons: requires `tmux` (installed in the devcontainer Dockerfile) and some familiarity with tmux navigation.
  - **VS Code Tasks (recommended for GUI/editor users)**
    - Use Command Palette → Tasks: Run Task → choose tasks like **Dev: Start Figwheel**, **Dev: Tail Figwheel Log**, etc. The tasks open dedicated terminals in the editor.
    - Pros: editor-native, easy to run/stop, good for less terminal-savvy contributors.
    - Cons: requires VS Code / Codespaces UI and task configuration.

  **When to pick which:**
  - Use **tmux** if you prefer full-screen terminal monitoring, detaching/re-attaching sessions, or when working over SSH/Codespaces terminals.
  - Use **VS Code Tasks** if you want editor-integrated terminals with buttons and easy task execution.

  Additions:
  - `Makefile` target `dev-monitor` runs the tmux monitor script.
  - `tmux`, `make`, and `git-lfs` are installed in the devcontainer so `dev-monitor` and `make` targets work inside Codespaces after rebuilding the container. The image also runs `git lfs install --system` during build so LFS files are available to the workspace.
- On container creation, the `postCreateCommand` runs `bash ./scripts/dev-verify.sh` which executes the idempotent setup (`scripts/dev-setup.sh --no-start`) and verifies presence of `make`, `git-lfs`, `tmux`, `lsof` (for port checks), `ss` (from `iproute2`), and `lein` (prints diagnostics but does not fail the build).

- Starting Datomic without Docker (optional):
  - The repository includes the Datomic Free tarball under `lib/datomic-free-0.9.5703.tar.gz`.
  - To start a local transactor inside your Codespace/devcontainer without Docker run:
    - `bash ./scripts/start-datomic-local.sh` (creates `.datomic/`, prepares transactor.properties, starts transactor, waits for port 4334)
    - The start script will check for an existing transactor or any process holding the configured port (default 4334). When run interactively it will show matching processes and prompt to either kill them (TERM then KILL escalation) or abort the start.
    - Use `--check` (`bash ./scripts/start-datomic-local.sh --check`) to validate the Datomic layout and config without launching the service.
  - To stop the local transactor run:
    - `bash ./scripts/stop-datomic-local.sh` or `make datomic-stop`
  - To prefer local Datomic when using `dev-setup`, run:
    - `bash ./scripts/dev-setup.sh --no-start --local-datomic`
  - Use `make datomic-start` / `make datomic-stop` as convenient aliases.
