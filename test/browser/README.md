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

Some of these still boot a static server (an older pattern being migrated to `lein e2e-server`).
