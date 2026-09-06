# Fast browser probes

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

## Screenshots need CSS

`lein garden once` compiles `resources/public/css/compiled/styles.css`. Skip it and every
screenshot is unstyled — easy to mistake for a broken component.

## More traps

`docs/kb/verification-discipline.md` lists the probe defects that have produced confident
wrong answers in this repo: a control that suppressed the thing it measured, instrumentation
that could not intercept, `with-redefs` unwinding before async work, V8's 10-frame stack
cap, self time on allocation-heavy code, and a model that was not the app.

## Related

- [verification-discipline.md](verification-discipline.md)
- [perf-homebrew-builder-loop.md](perf-homebrew-builder-loop.md)
- [README.md](README.md)
