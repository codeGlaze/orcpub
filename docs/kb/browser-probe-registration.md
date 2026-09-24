# Registering a browser probe: what the runner actually requires

Adding a file to `PROBES` in `scripts/test/run-browser-probes.js` makes the runner *launch*
it. It does not make it a probe the runner can protect. Three separate conventions have to
be met as well, and none of them fails loudly when it is not — the probe passes, and the
guard it was supposed to be under is simply off.

Found 2026-09-20 registering three portrait probes. All three were green by hand and green
in the sweep while two of the runner's safeguards did nothing for them.

## 1. The browser: `findChrome()`, not an env var

The image ships a pinned Chromium under `PLAYWRIGHT_BROWSERS_PATH` (`/opt/pw-browsers`),
and its build number will not match the one the npm `playwright` package expects. On
2026-09-20 the image had `chromium-1194` and the pinned package wanted build `1243`:

```
browserType.launch: Executable doesn't exist at
  /opt/pw-browsers/chromium_headless_shell-1243/chrome-headless-shell-linux64/chrome-headless-shell
```

Every existing probe resolves it with a local `findChrome()` — newest `chromium-*`
directory, `chrome-linux/chrome` inside it. It is duplicated in 27 files; that is the
convention, not an accident waiting to be factored out.

A probe that instead reads its own env var (`ORCPUB_CHROME`) works when a human exports it
and dies the moment the runner spawns it, because the runner passes no such variable. Keep
the env var as an override if you like, but resolve through `findChrome()` by default.

## 2. The output format is the assertion count

`run-browser-probes.js` counts checks by matching output lines:

```js
const fails  = (out.match(/^\s*FAIL/gm) || []).length;
const passes = (out.match(/^\s*PASS/gm) || []).length;
```

So a probe that prints `  ok   <label>` for a pass reports **one** check — its trailing
summary line — no matter how many assertions it ran. Three probes running 32, 24 and 10
assertions each reported `1 checks`.

Emit `PASS  <label>` / `FAIL  <label>`, the shape `header_menus_e2e.js` uses:

```js
console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`);
```

And make sure the final summary line does **not** start with `PASS` or `FAIL`, or it
inflates the count by one. Exit code is what decides pass/fail; the summary is for humans.

## 3. No baseline entry means the shortfall guard is off

This is the one worth caring about. `probe-baseline.json` exists to catch a probe that has
quietly stopped asserting — the failure mode where a control gets renamed out from under an
`if (await x.count())` guard, the checks inside vanish, and the probe still exits 0.

The comparison is:

```js
const want = baseline[probe.file] || {};
r.shortfall = (want.checks && r.code === 0 && ran < want.checks) ? want.checks : 0;
```

`want.checks` is `undefined` for an unregistered probe, so `r.shortfall` is always `0`. A
probe with no baseline entry can drop every assertion it has and still report PASS. The
guard is not weakened; it is absent.

`want.seconds` is equally absent, so the probe gets a default timeout rather than one sized
to its observed runtime.

Regenerate entries with:

```bash
ONLY=<substring> UPDATE_BASELINE=1 node scripts/test/run-browser-probes.js
```

`UPDATE_BASELINE` merges (`{ ...baseline, ...observed }`), so `ONLY=` touches only the
probes it ran. **Check the numbers it wrote.** Run it before fixing conventions 1 and 2 and
it will happily record `checks: 0` or `checks: 1`, which is worse than no entry — it makes
the guard permanently vacuous while looking configured. That happened here; the file had to
be reverted and regenerated.

## Two more the runner tells you about, if you read its output

**Untimed clicks.** `run-browser-probes.js` greps every file in `test/browser/` for
`.click().catch(` and warns. An untimed click swallowed by `.catch()` waits playwright's
full 30s default and discards the failure — invisible, because every assertion still passes.
Use a timed helper:

```js
async function clickIfVisible(locator, { timeout = 2500 } = {}) {
  try { await locator.click({ timeout }); return true; }
  catch (_) { return false; }
}
```

**Overlay suppression is belt and braces.** The runner injects
`test/browser/lib/suppress-overlays-preload.js` via `NODE_OPTIONS` for the sweep, but a
probe run by hand gets no preload. Existing probes also call `suppressOverlays(ctx)` from
`lib/orcbrew-import.js` themselves. A probe that relies only on the runner works in the
sweep and fails by hand on a What's New backdrop it has nothing to do with.

## What good looks like

```
PASS  portrait_compositor_e2e.js  (32 checks, 0 failing, 9s)
PASS  portrait_tab_e2e.js         (24 checks, 0 failing, 11s)
PASS  portrait_pdf_export_e2e.js  (10 checks, 0 failing, 41s)
```

Real counts, not `1`. If a newly registered probe reports one or two checks, it is not
asserting once — it is printing in a format the runner cannot read.
