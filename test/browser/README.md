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
- `homebrew_rebuild_scaling_e2e.js` — microbenchmarks the character-rebuild internals as
  homebrew volume grows (real `.orcbrew` upload through the page's own file input). Needs
  packs from `dev/scale_orcbrew_pack.clj`.
- `homebrew_spellcaster_load_e2e.js` — how much spell machinery gets built for homebrew
  spellcasting classes when you are on the Race tab and have not opened Spells. Needs packs
  from `dev/spellcaster_pack.clj`.
- `homebrew_render_split_e2e.js` — CPU-profiles a real race click and splits it into the
  rebuild path vs the render path, so the two can be told apart as homebrew grows.
- `spell_layout_pdf_e2e.js` — builds a Warlock 5 / Sorcerer 5, exports every sheet style under
  both spell-sheet layouts, and checks the packed one comes out shorter.
- `sticky_header_e2e.js` — one sticky header, not a fixed copy above an inline one, in a desktop
  and a phone viewport.
- `tab_switch_freeze_e2e.js` — the builder freeze: longest single task per Race<->Class
  switch under CPU throttle, plus heap, counters and stacks. `SKIP_CONTROL=1` omits the
  positive control, which otherwise realises the expensive content up front and suppresses
  the very freeze being measured.
- `class_body_cost_e2e.js` — what class bodies cost at builder open and per class switch,
  with retained heap. `NOMEMO=1` A/Bs the spell-option memoize.
- `freeze_cpu_profile_e2e.js` — CPU-profiles the freezing switch and ranks by INCLUSIVE
  time. Self time is useless on allocation-heavy code; it parks in `(program)` and GC.
- `builds_per_interaction_e2e.js` — `entity/build` calls per click, with the gaps between
  them (a few ms means fan-in or separate subscriptions; ~500 ms means the debounce).
- `class_handlers_functional_e2e.js` — pass/fail: drives set-class, set-class-level,
  add-class and delete-class and asserts app-db. Neither suite clicks anything, so this is
  the only coverage those handlers have.
- `storage_shape_e2e.js` — what is actually in localStorage after a real import.
- `localstorage_ceiling_e2e.js` — the real quota, and whether it counts chars or bytes.
- `library_chunk_granularity_e2e.js` — how finely a library can be split.

**The cookie banner is position-fixed at the bottom of the page and overlays whatever is
under it** — including the import conflict modal's buttons, which makes a click fail with
"subtree intercepts pointer events" and look like an app bug. `cookies.js` now takes an
explicit opt-out: set `localStorage['orcpub:no-cookie-banner'] = '1'` before the first
navigation (`suppressCookieBanner(context)` in `lib/orcbrew-import.js` does exactly that),
or append `?no-cookie-banner=1` when driving a browser by hand. Prefer suppressing it over
dismissing it — a dismissal is one more thing to get wrong on every new page.

All three homebrew probes import through `lib/orcbrew-import.js`, which drives the
**conflict-resolution modal**. A real pack with overlapping keys makes the app open that
modal and wait; a probe that only polls app-db sees the plugin count stay put and wrongly
concludes the import failed. Three long runs were lost to exactly that. A fixed sleep before
clicking is not enough either — a bigger pack parses slower and the click finds no button —
so the helper races both outcomes until one lands.

The homebrew probes are perf instruments, not pass/fail tests — they print numbers.
`:optimizations :none` makes LOAD-time numbers from the dev build meaningless; only their
runtime numbers are usable.
Before writing a new probe, know the defects that have produced confident wrong answers
here: a control that suppresses what it measures, instrumentation that cannot intercept, a
dead probe reporting silence, truncated stacks, self time on allocation-heavy code.


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
