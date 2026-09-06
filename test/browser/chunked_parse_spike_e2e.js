// Track 1 spike: is per-source chunked EDN parsing (with a yield between sources) worth
// the localStorage migration it would require? See docs/kb/perf-homebrew-builder-loop.md,
// "Track 1 spike: is chunked parsing worth a storage migration?" for the writeup this
// script's numbers feed.
//
// THE QUESTION: cljs.reader/read-string on the whole stored "plugins" library is ~750ms
// of ONE unbroken main-thread task (measured in validation_cost.js / the KB doc). The
// library is one localStorage string keyed "plugins" (db.cljs:51, written db.cljs:267).
// If we instead stored it as N per-source strings and parsed them one at a time with a
// yield in between, would the freeze (longest single long-task) actually shrink, and by
// how much? That is worth knowing BEFORE paying for a storage-format migration.
//
// METHOD:
//   1. baseline  - read-string the whole stored string in one shot. Report longest
//                  longtask (PerformanceObserver, entryTypes:['longtask'], >=50ms).
//   2. chunked   - split the SAME stored string into per-source strings by reading it
//                  once (unavoidable — has to happen somewhere) then pr-str-ing each
//                  [source-name value] pair back to EDN text. This MODELS what per-source
//                  *storage* would hand the loader (N small strings instead of 1 big one).
//                  It is NOT the migration itself — seeing this modeling gap is part of
//                  the point. Then read-string each chunk in turn with
//                  `await new Promise(r => setTimeout(r, 0))` between chunks.
//   3. equivalence - cljs.core/= the merged chunked result against the baseline result.
//                  If unequal, the whole idea is invalid: say so loudly and stop.
//
// Warm up before timing (JIT); report min-of-N. Run at 1x and 4x CPU throttle via CDP
// (Emulation.setCPUThrottlingRate) — 4x is what a real user's laptop looks like, per
// CLAUDE.md's method rules for this investigation.
//
// Uses test/browser/lib/orcbrew-import.js's importPack + suppressCookieBanner — a real
// .orcbrew import opens a conflict-resolution modal that needs both to get past
// reliably (see that file's own comment; three prior probes were lost to it).
//
// Run: node test/browser/chunked_parse_spike_e2e.js [pack.orcbrew] [cpu]
//   pack defaults to dev-scratch/paks/mega-64.orcbrew (the 3.9MB primary fixture;
//     dev-scratch/paks/mega-raw.orcbrew — the same content BEFORE synthetic caster
//     duplication — is the more realistic per-source distribution, see the KB writeup)
//   cpu defaults to 1; pass 4 for the 4x throttle run
//
// Prereqs: lein e2e-server running on :8890 (any already-loaded homebrew library is
// fine — the script reads whatever ends up under localStorage 'plugins' after import).

const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const { importPack, suppressCookieBanner } = require('./lib/orcbrew-import');

function findChrome() {
  const b = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  try {
    const d = fs.readdirSync(b).filter(x => x.startsWith('chromium-') && !x.includes('headless')).sort().pop();
    if (d) { const p = path.join(b, d, 'chrome-linux', 'chrome'); if (fs.existsSync(p)) return p; }
  } catch (_) {}
  return undefined;
}

(async () => {
  const pak = process.argv[2] || 'dev-scratch/paks/mega-64.orcbrew';
  const cpu = Number(process.argv[3] || 1);

  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext();
  await suppressCookieBanner(ctx);
  const page = await ctx.newPage();
  await page.setViewportSize({ width: 1500, height: 1100 });

  // Longtask observer must be installed before any of the work we're measuring runs.
  await page.addInitScript(() => {
    window.__longtasks = [];
    try {
      new PerformanceObserver(list => {
        for (const e of list.getEntries()) window.__longtasks.push(Math.round(e.duration));
      }).observe({ entryTypes: ['longtask'] });
    } catch (e) {}
  });

  const cdp = await page.context().newCDPSession(page);
  if (cpu > 1) await cdp.send('Emulation.setCPUThrottlingRate', { rate: cpu });

  await page.goto('http://localhost:8890/dnd/5e/my-content', { waitUntil: 'networkidle', timeout: 300000 });
  await page.waitForTimeout(3000);

  const r = await importPack(page, path.resolve(pak));
  if (!r.ok) throw new Error('import failed: ' + JSON.stringify(r.diag || {}).slice(0, 300));
  await page.waitForTimeout(1500);

  const result = await page.evaluate(async () => {
   try {
    const raw = localStorage.getItem('plugins');
    if (!raw) return { err: 'no plugins in localStorage after import' };

    const read = window.cljs.reader.read_string;
    const c = window.cljs.core;
    const clearLT = () => { window.__longtasks = []; };
    const maxLT = () => Math.max(0, ...window.__longtasks);
    const yieldToBrowser = () => new Promise(res => setTimeout(res, 0));
    // PerformanceObserver delivery is not guaranteed within a single setTimeout(0) tick —
    // observed empirically (one baseline run in three showed longest=0 despite a ~700ms
    // parse). Flush with a short real wait so every run's entries are actually queued
    // before we read window.__longtasks; this wait happens AFTER the timed work, so it
    // does not touch the reported wall/longtask numbers.
    const flushLongtasks = () => new Promise(res => setTimeout(res, 100));

    // ---- warm up the reader (JIT) on the real string itself; discard results.
    // (A truncated slice is not valid EDN — cutting a string literal mid-token throws an
    // EOF reader error — so warm up on the full string, which is always well-formed.)
    // This also PRIMES the longtask observer: verified empirically (dev-scratch/lt_debug*.js)
    // that Chrome's Long Tasks API silently drops the very first long task reported after a
    // fresh page load/idle period — every task after that first one is reported normally.
    // Without a throwaway long task before the real measurement starts, run #1 of every
    // timed loop below would read longest=0 regardless of how long it actually blocked.
    for (let i = 0; i < 2; i++) read(raw);
    await flushLongtasks();

    // ---- 1. BASELINE: one-shot read-string of the whole library, min of 3 ----
    // PerformanceObserver delivers longtask entries asynchronously (never within the
    // same synchronous task that created them), so we must yield once after the parse
    // before reading window.__longtasks, or the entry for the parse itself is missed.
    const baselineRuns = [];
    let baselineParsed = null;
    for (let i = 0; i < 3; i++) {
      clearLT();
      const t0 = performance.now();
      const parsed = read(raw);
      const wall = performance.now() - t0;
      await flushLongtasks();
      baselineRuns.push({ wall, longest: maxLT() });
      baselineParsed = parsed; // same every time; keep the last
    }
    const baseline = baselineRuns.reduce((a, b) => (b.longest < a.longest ? b : a));

    // ---- split into per-source EDN strings (models per-source storage) ----
    // Parse once (unavoidable to get at the source map at all), then pr-str each
    // [source-name value] pair back into standalone EDN text.
    const chunkStrings = [];
    c.doall(c.map(kv => {
      const pair = c.vector(c.key(kv), c.val(kv));
      chunkStrings.push(c.pr_str.call(null, pair));
      return null;
    }, baselineParsed));

    // ---- 2. CHUNKED: parse each chunk in turn, yielding between each ----
    async function runChunked() {
      clearLT();
      const t0 = performance.now();
      const merged = [];
      for (const s of chunkStrings) {
        const pair = read(s);
        merged.push(pair);
        await yieldToBrowser();
      }
      const wall = performance.now() - t0;
      await flushLongtasks(); // make sure the final chunk's longtask entry (if any) is delivered
      // rebuild a map the same shape as baselineParsed: {source-name -> value}
      const m = c.into.call(null, c.hash_map(), merged);
      return { wall, longest: maxLT(), map: m };
    }
    // warm up chunked path once (JIT), discard
    await runChunked();
    const chunkedRuns = [];
    let chunkedMap = null;
    for (let i = 0; i < 3; i++) {
      const res = await runChunked();
      chunkedRuns.push({ wall: res.wall, longest: res.longest });
      chunkedMap = res.map;
    }
    const chunked = chunkedRuns.reduce((a, b) => (b.longest < a.longest ? b : a));

    // ---- 3. EQUIVALENCE: chunked merged result must = baseline result ----
    // cljs.core/= is IEEE-754-correct: NaN = NaN is false, same as everywhere else in JS/
    // Clojure. Real homebrew content in this library legitimately contains ##NaN (e.g. a
    // monster stat block with a malformed skill bonus) — reader/read-string produces the
    // *identical* NaN value in both the baseline and the chunked path, but c._EQ_ reports
    // any structure containing one as unequal to itself. That is not evidence the two
    // parses differ; it is a property of NaN. So equivalence here means "same data,
    // NaN-for-NaN", checked with a deep-equal that treats two NaNs as equal, exactly as
    // pr-str would print them identically.
    function deepEq(a, b) {
      if (c._EQ_.call(null, a, b)) return true;
      if (typeof a === 'number' && typeof b === 'number' && Number.isNaN(a) && Number.isNaN(b)) return true;
      if (c.map_QMARK_(a) && c.map_QMARK_(b)) {
        if (c.count(a) !== c.count(b)) return false;
        let ok = true;
        c.doall(c.map(kv => {
          const k = c.key(kv), v = c.val(kv);
          if (!c.contains_QMARK_.call(null, b, k) || !deepEq(v, c.get.call(null, b, k))) ok = false;
          return null;
        }, a));
        return ok;
      }
      if ((c.sequential_QMARK_(a)) && (c.sequential_QMARK_(b))) {
        const av = c.vec(a), bv = c.vec(b);
        if (c.count(av) !== c.count(bv)) return false;
        for (let i = 0; i < c.count(av); i++) if (!deepEq(c.nth.call(null, av, i), c.nth.call(null, bv, i))) return false;
        return true;
      }
      return false; // sets containing NaN are not expected in this data; not special-cased
    }

    const equal = deepEq(baselineParsed, chunkedMap);
    let diagnosis = null;
    if (!equal) {
      // find which source(s) differ, and how, so a FAIL is actionable rather than opaque.
      const mismatches = [];
      c.doall(c.map(kv => {
        const k = c.key(kv), v = c.val(kv);
        const v2 = c.get.call(null, chunkedMap, k);
        if (!deepEq(v, v2)) {
          mismatches.push({
            source: c.pr_str.call(null, k),
            baselineCount: c.map_QMARK_(v) ? c.count(v) : null,
            chunkedCount: c.map_QMARK_(v2) ? c.count(v2) : null,
          });
        }
        return null;
      }, baselineParsed));
      diagnosis = { mismatchedSources: mismatches.length, sample: mismatches.slice(0, 5) };
    }

    return {
      bytes: raw.length,
      chunkCount: chunkStrings.length,
      baselineRuns, baseline,
      chunkedRuns, chunked,
      equal, diagnosis,
    };
   } catch (e) {
     return { err: 'js-exception: ' + (e && (e.message || e.toString ? e.toString() : String(e))) + (e && e.stack ? (' | stack: ' + e.stack) : '') };
   }
  });

  if (result.err) { console.log('FAILED:', result.err); await browser.close(); process.exit(1); }

  console.log(`\n=== chunked_parse_spike: ${path.basename(pak)} @ ${cpu}x cpu ===`);
  console.log(`library         ${(result.bytes / 1048576).toFixed(2)} MB, ${result.chunkCount} chunks (sources)`);
  console.log(`baseline runs   ` + result.baselineRuns.map(x => `${x.wall.toFixed(0)}ms(lt${x.longest})`).join('  '));
  console.log(`baseline (best-longtask run)  total ${result.baseline.wall.toFixed(0)} ms   longest task ${result.baseline.longest} ms`);
  console.log(`chunked runs    ` + result.chunkedRuns.map(x => `${x.wall.toFixed(0)}ms(lt${x.longest})`).join('  '));
  console.log(`chunked  (best-longtask run)  total ${result.chunked.wall.toFixed(0)} ms   longest task ${result.chunked.longest} ms   chunks ${result.chunkCount}`);
  console.log(`equivalence     ${result.equal ? 'PASS (chunked = baseline)' : '*** FAIL — RESULTS NOT EQUAL, IDEA INVALID ***'}`);
  if (!result.equal) console.log('diagnosis      ', JSON.stringify(result.diagnosis));
  console.log(`longest-task reduction   ${result.baseline.longest} ms -> ${result.chunked.longest} ms`);

  await browser.close();
  if (!result.equal) process.exit(1);
})().catch(e => { console.error('FAILED', e && e.stack || e); process.exit(1); });
