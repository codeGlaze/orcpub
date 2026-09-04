# Browser e2e tests

These drive a real headless browser (Chromium via Playwright) against the **running app**.

## Run them

```
lein fig:build          # compile the dev CLJS build
lein garden once        # compile CSS (needed if you screenshot)
lein e2e-server         # boot the full stack (Pedestal + in-memory Datomic) on :8890
node test/browser/<name>.js
```

`lein e2e-server` needs port 8890 free — kill any prior server first
(`fuser -k 8890/tcp`) or you'll get a bind failure.

## The rule: real server, real UI

Drive the actual UI against `http://localhost:8890` — navigate, click, upload `.orcbrew`
files, use the real dropdowns. The backend is live, so real imports/saves/conflicts happen,
which is the whole point (a static-file server + `dispatch_sync` can't surface an
import-conflict modal, and it misled a previous pass into a false "modal is unmounted"
conclusion).

Prefer real interactions (`setInputFiles`, `selectOption`, clicking) over dispatching
re-frame events. Routing via the app's own router is fine for navigation.

## Tests here

- `starting_equipment_ledger_e2e.js` — the base+delta round-trip and the "Based on <class>" callout.
- `notification_flows_e2e.js` — message toasts render red/orange/green via `views.notifications`.
- `notifications_acceptance_e2e.js` — toasts, confirmation dialog, callout, shared-content-banner.
- `starting_equipment_browser_e2e.js` — the starting-equipment builder round-trip.
- `fighting_style_builder_e2e.js` — the fighting-style builder renders and its `:when` conditional
  fields work; guards the `render-builder-field` number regression.
- `homebrew_roundtrip_e2e.js` — author a fighting style AND a draconic ancestry in the real UI,
  save, export a real `.orcbrew` download, re-import into a clean library, assert the `:props` and
  numeric fields survive.

## Gotchas these tests paid for

- **`lein garden once` is not optional.** Without it `/css/compiled/styles.css` 404s, everything
  renders visible, and click helpers that pick "the shortest matching text" happily click hidden
  elements. Symptoms change once CSS loads.
- **Click only VISIBLE elements.** With real CSS several matches are collapsed or off-screen;
  clicking one hangs until the 30s timeout.
- **Not every page is under `/pages/`.** The builders are (`/pages/dnd/5e/<x>-builder`), but My
  Content is `/dnd/5e/my-content`. A wrong prefix returns the server's plain `Not Found`, which
  looks like a broken page rather than a bad URL.
- **`browser.newContext({ acceptDownloads: true })`** — without it `waitForEvent('download')` never
  resolves and an export looks broken.
- **Match labels by prefix, not exact text.** "Option Source Name" is followed by an italic `<span>`
  of examples, so an exact match on a childless element finds nothing.

Some of these still boot a static server (an older pattern being migrated to `lein e2e-server`).
