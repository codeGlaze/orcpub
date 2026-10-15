# Page load budget: what each page loads, and what it costs

The rule (AGENTS.md): nothing loads until something on the page needs it; then once, and cached.
This page holds the measurements behind it. The browser check that enforces it is
`test/browser/page_load_budget_e2e.js` on branch `test/page-load-budget`.

## Why it exists

The portrait branch fetched the artist credits from a start-up step, so every page on the site made
the request, including pages with no portrait. Nothing noticed until an unrelated test, which serves
the app without a back end, logged the 404 (2026-10-14). The credits now load when a view that shows
one first subscribes (`:portrait/artist-overrides`, a `reg-sub-raw`).

## Audit of integration (2026-10-15, b8035afe, production bundle)

18 pages, signed out and signed in, every request from navigation until the network went quiet.

- **Every page:** the app bundle, styles.css, Font Awesome (CSS and one font), the cookie notice's
  script and styles, the logo, the favicon, and Google Fonts (a stylesheet from fonts.googleapis.com
  and a font from fonts.gstatic.com), which sent each visitor's IP address to Google.
- **Data, all of it shown by the page that fetches it:** `/user` on every page when signed in (the
  session check); the character on its sheet; your items on the builder, the sheet, magic items and
  the class builder (offered as equipment); characters and folders on the list; parties on parties;
  `rules.edn` and `conditions.edn` on their own pages only (they otherwise load when the Orcacle
  opens or a link preview is hovered).
- **Combat tracker:** loads your characters and parties on open, even when the tracker is empty,
  because its always-visible "add" rows are dropdowns of exactly those lists.
- **Icons:** one request per icon; about 45 on the builder and 48 on the character sheet.

## Cost on a slow phone connection (2026-10-15)

Simulated 150 ms latency, 1.6 Mbps, production bundle, median of three cold loads.

| Page | Load | With icon requests answered instantly | Icons' share |
|---|---|---|---|
| Builder | 8.5 s | 7.8 s | 0.7 s |
| Character sheet | 8.8 s | 8.1 s | 0.7 s |

- The app bundle dominates a first visit: 3.09 MB, 0.78 MB compressed, about 4 s at that speed.
- Combining icons into sheets could save at most 0.7 s, on a first visit only.
- **No caching headers on static files.** A repeat visit moments later downloaded 54 of the
  builder's 55 files again in full (57 of 58 on the sheet), with no 304 revalidations. Long-lived
  caching (with version-stamped bundle and stylesheet addresses so a deploy still reaches everyone)
  removes nearly all of them, on every page.

## Related

- [e2e-runner.md](e2e-runner.md): how browser checks run and declare what they need.

## The browser test server runs in dev mode (2026-10-15)

`scripts/e2e/run.sh` starts the server with the default lein profiles, and `:dev-config` sets
`:dev-mode "true"` (project.clj), even for a production-bundle run. Anything that behaves
differently outside dev mode (the static caching interceptor and content-stamped asset links on
`test/page-load-budget`) cannot be seen by a browser check: a repeat visit to the builder still
re-downloaded 54 static files, because the new code was correctly doing nothing. A suite that needs
production behaviour has to ask the runner for `DEV_MODE=false` (decision pending: a per-suite
header, or every production-bundle run).
