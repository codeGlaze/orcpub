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

- Monitoring options:
  - **Interactive menu (recommended)**: Run `././menu`
    - Provides status display, quick actions (start Datomic, init DB, stop all)
    - Submenus for start/stop individual services, tmux, utilities
    - Utilities include tail logs and open in VS Code
  - **VS Code Tasks**: Use Command Palette → Tasks: Run Task
    - Tasks like **Dev: Start Datomic**, **Dev: Tail Datomic Log**, **Dev: Open Menu**
  - **tmux** (optional): Available through menu option 7 (Tmux →)
    - Start services in tmux, attach/detach, kill session
    - Detach: `Ctrl-b d`, Attach: `tmux attach -t orcpub`

- Starting/Stopping Datomic (quick CLI):
  ```bash
  ././menu start datomic   # start
  ././menu stop datomic    # stop
  ././menu status          # check status
  ```
- Other options:
  - `--check` to validate prerequisites: `./scripts/start.sh --check`
  - `--idempotent` for automation: `././menu start datomic --idempotent`
- Logs are written to `./logs/` (datomic.log, server.log, figwheel.log)
