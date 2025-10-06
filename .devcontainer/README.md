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
- If you use another Leiningen alias for dev, adjust `lein figwheel` accordingly.- Add more ports to forward as needed in `devcontainer.json`.
