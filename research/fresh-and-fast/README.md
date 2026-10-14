# Fresh and fast: research and experiments (2026-10-14)

Measured against `integration` at `b8035afe`, production (`:advanced`) bundle, served by
`scripts/e2e/server.sh`. Write-up with screenshots: https://claude.ai/artifact/VDjwZLDBWtmoDbMVZTm9Jk

**Belongs on `agents/develop`** (`docs/kb/` and `scripts/`). It is parked here only because this
session can push to this branch alone. Move it, index it in `docs/kb/README.md`, and delete it here.

## Findings

- **Download size is not the gap.** Our bundle is 3,162 KB raw / 794 KB gzip. D&D Beyond's
  character app is about 5.8 MB / 1.59 MB gzip (`main.<hash>.js`, React 19, MUI, Redux, Fastly).
- **Startup CPU is.** On a warm load at 4× CPU, evaluating `orcpub.js` is one 2.15 s task
  (about 0.6 s compile, 1.3 s top-level run) on every route, home included. Then about 0.75 s
  goes to the template build and first render.
- **Startup work by area** (4× CPU, nearest `orcpub.*` frame, one sampled run): option trees in
  `options` about 445 ms, `library/offered-keys` + `choice-tags` about 395 ms, `template`
  `option-cfg`/`selection-cfg` about 368 ms, re-frame handler registration about 230 ms,
  `common/name->kw` about 117 ms, `magic_items` top level about 106 ms.
- **On a fresh builder, clicks are fast** (tab switch and race pick, 16–80 ms Event Timing). The
  slow clicks are the large-library ones in `perf-homebrew-builder-loop.md`.
- **Warm loads paint later than cold ones on desktop** (builder FCP 708 ms vs 92 ms). The script
  tag is not deferred (`index.clj:251`), so a cached script runs before the first paint.
- **Caching cannot be added in the route handlers.** Pedestal's `::http/resource-path "/public"`
  (`system.clj:70`) serves `/js`, `/css` and the rest before routes run. Nothing static sends
  `Cache-Control`, and `deploy/nginx.conf.template` adds none and does not enable HTTP/2.
- **The desktop header art** (`/image/header-background.jpg`) is 377 KB, on every interior page.
  Font Awesome ships in full (59 KB CSS plus an 80 KB woff2) for about 72 icon names in source.

## Experiments

| | Change | Result |
| --- | --- | --- |
| 1 | `defer` on the bundle (`experiments/e1-fast-shell.diff`) | Mixed. Warm FCP improves (phone builder 2,464 → 280 ms), but the app still mounts at about 3.9 s; some cold desktop FCPs got later. The `Cache-Control` half of the diff has no effect (see above) |
| 2 | `offered-keys`/`choice-tags` moved to `requestIdleCallback` (`e2-idle-offered.diff`) | No change. `dispatch` already runs it as its own later task; the long tasks are script evaluation and template build plus render |
| 3 | CSS-only visual directions (`mocks/`) | A, compact hero; B, quiet app. Heading starts at about 140 px instead of 320 px on desktop. In both mocks the phone nav row cuts off its last items |

## Running the probes

Copy `probes/*.js` into `scripts/e2e/` on a code branch (they use its `node_modules`), start
`scripts/e2e/server.sh start`, then:

```bash
E2E_CHROMIUM=/opt/pw-browsers/chromium node perf-probe.js   # loads, cold/warm, desktop/phone; clicks
node boot-trace.js /pages/dnd/5e/character-builder 4         # main-thread time by trace event, 4x CPU
lein run -m figwheel.main -- --build-once pseudo             # with probes/pseudo.cljs.edn at the root
CPU=4 node boot-profile.js target/pseudo/orcpub.js           # CPU profile charged to namespaces
node mocks/mock-shot.js <dir-holding-mocks/>                 # screenshots of the visual directions
```

The pre-installed Chromium is older than the `playwright` that `scripts/e2e` installs, so pass
its path. `lein deps` hit Maven Central 429s on `test.check-1.1.1.jar`; the Google mirror
(`maven-central.storage-download.googleapis.com/maven2/`) served it with a matching SHA-1.
