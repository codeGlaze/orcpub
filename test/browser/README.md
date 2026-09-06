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

## Run them all at once

```
node scripts/test/run-browser-probes.js
```

Runs every **asserting** probe and exits non-zero if any fails. Neither `lein test` nor the
CLJS runner invokes anything in this directory, so without it "both suites green" says
nothing about these files — `equipment_add_functional_e2e.js` once sat failing three
assertions and exiting 1 for several commits because nothing ran it.

```
ORCBREW_PACK=/path/to/pack.orcbrew   # enables the two probes that need imported homebrew
BUSY_SERVER=1                        # with `lein e2e-server-busy`, enables the export probe
JOBS=3                               # run N at once (default 1, see the caveat below)
ONLY=equipment,sticky                # substring filter
STRICT=1                             # a probe that could not run counts as a failure
```

A probe that cannot run is reported as `SKIP` with its reason, loudly. Silence is how the
stale one hid.

**The probes do not all want the same world.** Running them as though they did is wrong in
both directions — it was, on the runner's first outing:

| `needs` | what it wants |
| --- | --- |
| `server` | the real app at `:8890` (`lein e2e-server`) |
| `standalone` | serves `resources/public` itself and expects **no** usable backend; it treats connection-refused as benign |
| `busy-server` | `:8890` under `lein e2e-server-busy`, the profile that holds every export slot so the busy page appears |

So the server is not a blanket precondition: it is required only when a selected probe needs
one, and the standalone probes run either way. `export_busy_retry` against the ordinary
server fails all six of its checks, and `notifications_acceptance` fails *because* a server
is up — its XHR gets CORS-blocked instead of refused. Both are preconditions, not bugs.

A full pass is about 12.5 minutes sequentially. `character_image_capture` is 397s of that
and `sticky_header` 131s; the rest are seconds. `JOBS=N` runs several at once, but **that is
not validated** — the `server` probes share one in-memory backend, so concurrent runs can in
principle see each other's saved characters. Default is 1 for that reason; raise it when you
want speed over certainty.

## Two ways a probe lies, and what catches them

**It stops asserting.** A control renamed out from under an `if (await x.count())` guard
takes its checks with it, and the probe still exits 0 reporting everything it *did* run as
passing. `scripts/test/probe-baseline.json` records how many assertions each probe is
expected to run; the runner fails one that runs fewer and says so. Regenerate with
`UPDATE_BASELINE=1` — and only when you meant to change the count.

**It sits there.** Probes carry 15-minute navigation timeouts and blind `waitForTimeout`
sleeps, so a stuck one looks exactly like a slow one until the whole run ends. The runner
prints a heartbeat every 60s saying how long the probe has been *silent* and its last line
of output, and kills it at `PROBE_TIMEOUT_S` (default 600) as `STUCK`, showing the last
output before it stopped. A probe still printing is working; one quiet for minutes is where
it is stuck.

Write assertions that can fail. `after <= opened` for a check named "filtering narrows the
list" passes when filtering does nothing — it was in this directory. And a missing control
is the failure, not a reason to print SKIP and stop asserting.

Only asserting probes are in the runner. The measurement probes (`tab_switch_freeze`,
`freeze_cpu_profile`, `combobox_scroll`, `select_option_census`, …) report numbers rather
than pass/fail and are run by hand — putting them in would turn timing noise into build
failures.

**These probes go stale silently.** One that finds its control by class name breaks the
moment that control is swapped, and only an *unguarded* assertion tells you: a guarded
`if (await x.count())` degrades to doing nothing at all. When you change a control, grep
this directory for its class names.

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
runtime numbers are usable. See `docs/kb/perf-homebrew-builder-loop.md`.
Before writing a new probe, read `docs/kb/verification-discipline.md` — it lists the probe
defects that have produced confident wrong answers here (a control that suppresses what it
measures, instrumentation that cannot intercept, a dead probe reporting silence, truncated
stacks, self time on allocation-heavy code).


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
