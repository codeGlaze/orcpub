# Codespace Devcontainer for Orcpub (ClojureScript)

## Quick Start in Codespaces

1. Open this repo in GitHub Codespaces.
2. Wait for setup to finish (Java, Clojure, Node, shadow-cljs, Calva, Copilot preinstalled).
3. Run your dev server (e.g.):
   ```
   npx shadow-cljs watch app
   ```
   or (if using Figwheel):
   ```
   clojure -A:fig:build
   ```
4. Open forwarded port (likely 9630, 3449, or 3000) in the Codespaces browser tab.

## Features

- Clojure, ClojureScript, Java, Node.js
- Calva, Copilot, and Copilot Chat VS Code extensions
- Hot reloading and live REPLs for ClojureScript web apps

## Troubleshooting

- If you use a different build tool (Leiningen, Figwheel), adjust the Dockerfile and postCreateCommand.
- Add more ports to forward as needed in `devcontainer.json`.
