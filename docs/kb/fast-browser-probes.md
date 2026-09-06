# Fast browser probes

*Measured 2026-09-06. Timings from `scripts/test/run-browser-probes.js` and
`test/browser/*_e2e.js` runs against `lein e2e-server`.*

How to not spend 40 minutes measuring one thing. Written after a session that did exactly
that; every number below is from this repo.

## Where the time actually goes

```
lein test           69 s     both suites together are ~2 min
lein fig:test       65 s     NOT the bottleneck
lein fig:build      ~7-11 s  warm; ~60 s cold
lein garden once    ~3 s
lein fig:prod       ~45 s

ONE browser probe   ~150-180 s
  megapack import      ~60 s
  builder page load    ~14 s  (hardcoded wait; :optimizations :none is slow)
  the measurement      ~20 s
  everything else      setup, teardown, navigation
```

**The suites are cheap. Browser probes are not.** A 40-minute loop is ~12 probe runs, and it
is almost always the same setup paid 12 times.

## The rule: batch variables into ONE run

Measuring five cap values as five runs is 15 minutes. As one run that imports once and loops
over the values, it is 4. The setup — import, page load, throttle — is the cost; the
measurement is seconds.

```js
// one import, one page load, N measurements
for (const variant of VARIANTS) {
  await applyVariant(page, variant);          // in-page where possible
  await page.evaluate(() => { window.__spy = {}; });
  ...measure...
}
```

Where the variant needs a rebuild (a source change), still batch — build, measure, build,
measure in a single script — but **check the server is alive between builds** (see below).

## The `.lein-env` trap, which will cost you two runs

lein-environ writes a single `.lein-env` per invocation. Running any `lein` command while
`lein e2e-server` is starting clobbers the `:e2e` profile's `:datomic-url`, and the server
silently boots against `datomic:dev://localhost:4334` — a transactor that is not there.

The symptom is a probe dying with `ERR_CONNECTION_REFUSED`, which reads as a broken probe,
not a config collision. Start the server so it cannot be raced:

```
DATOMIC_URL="datomic:mem://orcpub" lein with-profile +e2e run
```

An env var beats `.lein-env` in environ's precedence, so a concurrent build cannot touch it.
And assert liveness before each measurement, so a dead server never masquerades as a slow
tab:

```bash
curl -sf -o /dev/null http://localhost:8890/ || { echo "server down"; exit 1; }
```

## Keep the server up across probes

Restarting it costs ~30 s and re-importing costs ~60 s. Leave it running for a whole
session of measurements; only the compiled JS needs rebuilding between source changes, and
the server serves that off disk without a restart.

## Make a dead probe obvious

Counters that never armed look exactly like "nothing ran" — that mistake cost several runs
here. Always print what armed:

```js
const armed = await page.evaluate(() => Object.keys(window.__spy || {}));
console.log('spy armed for:', armed.join(', ') || 'NOTHING — counts below are meaningless');
```

Same for an empty output section: check *why* it is empty before reading it as a result. A
missing module produced a blank "AFTER" block that nearly passed as data.

## Normalise to a control in the same run

Absolute numbers drift between server restarts — a Race-tab baseline moved 160→271 ms across
runs here with no code change. Measure a control interaction in the same run and compare
ratios, or you will attribute machine noise to your change.

## Screenshots

`test/browser/screenshots_e2e.js` — takes them for you:

```
lein garden once && lein fig:build && lein e2e-server
node test/browser/screenshots_e2e.js dev-scratch/paks/mega-64.orcbrew dev-scratch/shots Equipment
```

**Run `lein garden once` first.** Skip it and `styles.css` is stale or missing, every shot
comes out unstyled, and it reads as a broken component. The probe warns when the page looks
unstyled, but the fix is upstream.

A UI change reviewed only through timing numbers is half-reviewed. Take the screenshots
before asking anyone to look — this session shipped a visible control change and reported
only milliseconds until the owner asked for pictures.

## More traps

`docs/kb/verification-discipline.md` lists the probe defects that have produced confident
wrong answers in this repo: a control that suppressed the thing it measured, instrumentation
that could not intercept, `with-redefs` unwinding before async work, V8's 10-frame stack
cap, self time on allocation-heavy code, and a model that was not the app.

## Related

- [verification-discipline.md](verification-discipline.md)
- [perf-homebrew-builder-loop.md](perf-homebrew-builder-loop.md)
- [README.md](README.md)


## Why `character_image_capture` takes 393s

Not blind sleeps — it has only 17s of `waitForTimeout` across 14 calls, and 2 page loads.
The cost is spread evenly across its own checks, 30-80s each:

```
+34.1s  the export carries the bytes, not just the address
+39.8s  an oversized picture is shrunk under the 128k ceiling
+45.0s  the upload prompt goes once the file is read
+79.2s  a pasted picture supplies the bytes no host would give
total=393s   max silence between outputs 79.2s
```

**⚠️ UNVALIDATED SPECULATION -- inferred from the timing shape, not from instrumenting the fetches.** The probe drives the app at `https://i.imgur.com/aBcDeF.png` and
a Pinterest URL, both fake, and outbound HTTPS here goes through the agent proxy — a request
to a host that will not answer can hang a long time before failing. `spell_layout_pdf` does
20 checks in 52s, so PDF export is not the slow part, which points at the network rather than
the work. Confirming it means instrumenting the app's fetches; nobody has. Worth settling
before anyone tries to speed this probe up, because if it holds then it is slow *here* rather
than slow everywhere.

The 79.2s figure is load-bearing regardless: it is what sizes the runner's 180s silence
timeout.
