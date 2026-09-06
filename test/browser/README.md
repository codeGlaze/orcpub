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
- `spell_layout_pdf_e2e.js` — builds a Warlock 5 / Sorcerer 5, exports every sheet style under
  both spell-sheet layouts, and checks the packed one comes out shorter.
- `sticky_header_e2e.js` — one sticky header, not a fixed copy above an inline one, in a desktop
  and a phone viewport.

Some of these still boot a static server (an older pattern being migrated to `lein e2e-server`).

## character_image_capture_e2e.js

Both routes a character's picture can take into the PDF. A second origin on :8899
serves the test picture and decides whether to send `Access-Control-Allow-Origin`;
the run turns on that header.

The proof rests on the server refusing loopback addresses: `/character.pdf`
*cannot* fetch from 127.0.0.1, so an image that reaches the PDF from that origin
can only have arrived as bytes the browser read. With the header, the export
carries the bytes and the sheet is drawn. Without it, nothing is sent, the sheet
prints without the picture, and the builder offers the upload that supplies it.

    node test/browser/character_image_capture_e2e.js

## Reaching the real internet from a browser test

Chromium in this environment cannot complete a TLS 1.3 handshake through the
egress relay -- the tunnel closes mid-exchange and the page sees
`ERR_CONNECTION_RESET`, while curl and Playwright's own Node stack work fine.
Launch with `--ssl-version-max=tls1.2` and set the proxy explicitly:

    chromium.launch({
      args: ['--ssl-version-max=tls1.2'],
      proxy: { server: process.env.HTTPS_PROXY, bypass: 'localhost,127.0.0.1' },
    })

Bypass loopback, or requests to the app under test go out to the relay as well.
