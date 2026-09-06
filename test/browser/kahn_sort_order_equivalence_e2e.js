// kahn-sort order equivalence, in the ClojureScript runtime.
//
// WHY THIS EXISTS AND WHY `lein test` IS NOT ENOUGH:
// entity/kahn-sort's output order decides modifier application order (apply-options ->
// order-modifiers), so a rewrite of it must reproduce the order exactly, not merely
// produce some valid topological order. The frontier is a set and the next node is
// (first s) — so the SET'S ITERATION ORDER picks it. On the JVM that order is a pure
// function of the set's contents. In ClojureScript a set of <= 8 elements is
// PersistentArrayMap-backed and iterates in INSERTION order. So two constructions that
// are indistinguishable under `lein test` can diverge in the browser: an earlier draft of
// the rewrite passed every JVM check and diverged on 159 of these 808 graphs.
//
// This runs the PRE-REWRITE kahn-sort (ported below over cljs.core, same calls in the
// same order) against the shipped orcpub.entity.kahn_sort, in the page, on the live
// character's real dependency graph plus 808 generated graphs. Also prints both
// implementations' timings and the crossover curve.
//
// Prerequisites:
//   lein fig:build        # compile the dev CLJS build
//   lein e2e-server       # full stack on :8890
//   PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install playwright
// Run:  node test/browser/kahn_sort_order_equivalence_e2e.js
// Exit 0 = every order identical.
//
// RE-RUN THIS ANY TIME kahn-sort CHANGES.
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
function findChrome(){const base=process.env.PLAYWRIGHT_BROWSERS_PATH||'/opt/pw-browsers';try{const d=fs.readdirSync(base).filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).sort().pop();if(d){const p=path.join(base,d,'chrome-linux','chrome');if(fs.existsSync(p))return p;}}catch(_){}return undefined;}
(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await browser.newPage({ viewport:{width:1400,height:1000} });
  await page.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil:'networkidle', timeout:120000 });
  await page.waitForTimeout(6000);
  const res = await page.evaluate(() => {
    const c = window.cljs.core, S = window.clojure.set, ent = window.orcpub.entity;
    const union = S.union, difference = S.difference, intersection = S.intersection;

    // --- verbatim port of the pre-rewrite implementation ---
    const without = (s, x) => difference(s, c.set(c.PersistentVector.fromArray([x], true)));
    const noIncoming = (g) => {
      const nodes = c.set(c.keys(g));
      const haveIncoming = c.apply(union, c.vals(g));
      return difference(nodes, haveIncoming);
    };
    const normalize = (g) => {
      const haveIncoming = c.apply(union, c.vals(g));
      return c.reduce((m, x) => c.get(m, x) ? m : c.assoc(m, x, c.PersistentHashSet.EMPTY), g, haveIncoming);
    };
    const refSort = (g0) => {
      let g = normalize(g0), l = c.PersistentVector.EMPTY, s = noIncoming(g0);
      for (;;) {
        if (c.empty_QMARK_(s)) return c.every_QMARK_(c.empty_QMARK_, c.vals(g)) ? l : null;
        const n = c.first(s);
        const s1 = without(s, n);
        const m = c.get(g, n);
        let g2 = g;
        c.doall(c.map(x => { g2 = c.update_in(g2, c.PersistentVector.fromArray([n], true), without, x); return null; }, m));
        g = g2;
        l = c.conj(l, n);
        s = union(s1, intersection(noIncoming(g), m));
      }
    };

    // --- seeded RNG + random graph builders (mirrors the JVM test) ---
    let seedState = 0;
    const srand = (s) => { seedState = s >>> 0; };
    const rnd = () => { seedState = (seedState * 1664525 + 1013904223) >>> 0; return seedState / 4294967296; };
    const kwv = (i, p) => c.keyword(null, p + i);
    const randDag = (n, density, seed) => {
      srand(seed); let g = c.PersistentArrayMap.EMPTY;
      for (let i = 0; i < n; i++) {
        let outs = [];
        for (let j = i + 1; j < n; j++) if (rnd() < density) outs.push(kwv(j, 'n'));
        g = c.assoc(g, kwv(i, 'n'), c.set(c.PersistentVector.fromArray(outs, true)));
      }
      return g;
    };
    const randCyclic = (n, seed) => {
      srand(seed + 7777); let g = c.PersistentArrayMap.EMPTY;
      for (let i = 0; i < n; i++)
        g = c.assoc(g, kwv(i, 'c'), c.set(c.PersistentVector.fromArray([kwv(Math.floor(rnd()*n), 'c')], true)));
      return g;
    };

    const bad = [];
    let checked = 0;
    for (let seed = 0; seed < 500; seed++) {
      const g = randDag(2 + (seed % 40), (1 + (seed % 5)) / 12.0, seed);
      const a = refSort(g), b = ent.kahn_sort(g);
      checked++;
      if (!c._EQ_(a, b)) bad.push(['dag', seed, c.pr_str(a), c.pr_str(b)]);
    }
    for (let seed = 0; seed < 300; seed++) {
      const g = randCyclic(2 + (seed % 30), seed);
      const a = refSort(g), b = ent.kahn_sort(g);
      checked++;
      if (!c._EQ_(a, b)) bad.push(['cyc', seed, c.pr_str(a), c.pr_str(b)]);
    }
    // degenerate shapes
    const mk = (pairs) => c.reduce((m, [k, vs]) =>
      c.assoc(m, c.keyword(null,k), c.set(c.PersistentVector.fromArray(vs.map(v=>c.keyword(null,v)), true))),
      c.PersistentArrayMap.EMPTY, pairs);
    const degen = [[], [['a',[]]], [['a',['b']]], [['a',['a']]], [['a',['b']],['b',['a']]],
                   [['a',['b','c']],['b',['c']]], [['a',['b']],['c',['d']]],
                   [['a',['b','c','d']],['b',['d']],['c',['d']],['d',[]]]];
    for (const d of degen) {
      const g = mk(d); const a = refSort(g), b = ent.kahn_sort(g); checked++;
      if (!c._EQ_(a, b)) bad.push(['degen', JSON.stringify(d), c.pr_str(a), c.pr_str(b)]);
    }

    // timing on the live build graph, both implementations
    const kw = (ns,n)=>c.keyword(ns,n);
    const character = window.re_frame.core.subscribe(c.PersistentVector.fromArray([c.keyword(null,"character")], true)).state;
    const tmpl = window.re_frame.core.subscribe(c.PersistentVector.fromArray([c.keyword(null,"built-template")], true)).state;
    const flat = ent.flatten_options(c.get(character, kw("orcpub.entity","options")));
    const mods = c.sort_by(x => c.get(x, kw("orcpub.modifiers","order")), ent.collect_modifiers_2(character, flat, tmpl));
    let deps = c.PersistentArrayMap.EMPTY;
    c.doall(c.map(m => { const k=c.get(m,kw("orcpub.modifiers","key")), d=c.get(m,kw("orcpub.modifiers","deps"));
      if (d && c.seq(d)) { const cur=c.get(deps,k); deps=c.assoc(deps,k,cur?union(cur,d):d);} return null; }, mods));
    const base = c.merge(c.get(tmpl, kw("orcpub.template","base")), c.get(character, kw("orcpub.entity","values")));
    const allDeps = c.merge_with(union, deps, c.get(base, kw("orcpub.entity-spec","deps")));
    const bench = (f, warm, n, reps) => { for(let i=0;i<warm;i++) f(); let best=Infinity;
      for(let r=0;r<reps;r++){const s=performance.now();for(let i=0;i<n;i++)f();const t=(performance.now()-s)/n;if(t<best)best=t;} return best; };

    const liveEqual = c._EQ_(refSort(allDeps), ent.kahn_sort(allDeps));
    const crossover = [];
    for (const n of [2,4,8,16,32,64,128,256]) {
      let g = c.PersistentArrayMap.EMPTY;
      for (let i=0;i<n;i++) g = c.assoc(g, kwv(i,'x'), i<n-1 ? c.set(c.PersistentVector.fromArray([kwv(i+1,'x')],true)) : c.PersistentHashSet.EMPTY);
      crossover.push({n, old: bench(()=>refSort(g), 5, 10, 3), neu: bench(()=>ent.kahn_sort(g), 50, 100, 3)});
    }
    return { checked, bad: bad.slice(0,5), badCount: bad.length, liveEqual,
             liveNodes: c.count(allDeps),
             tOld: bench(()=>refSort(allDeps), 5, 10, 5),
             tNew: bench(()=>ent.kahn_sort(allDeps), 50, 100, 5),
             tBuild: bench(()=>ent.build(character, tmpl), 20, 40, 5),
             crossover };
  });
  console.log(`\n=== CLJS ORDER EQUIVALENCE (in-browser) ===`);
  console.log(`graphs checked: ${res.checked}   mismatches: ${res.badCount}`);
  if (res.badCount) console.log(JSON.stringify(res.bad, null, 2));
  console.log(`live build graph (${res.liveNodes} nodes) order identical: ${res.liveEqual}`);
  console.log(`\n=== CLJS TIMING (live graph) ===`);
  console.log(`  kahn-sort pre-rewrite : ${res.tOld.toFixed(3)} ms`);
  console.log(`  kahn-sort now         : ${res.tNew.toFixed(3)} ms   (${(res.tOld/res.tNew).toFixed(1)}x)`);
  console.log(`  entity.build now      : ${res.tBuild.toFixed(3)} ms`);
  console.log(`\n=== CLJS CROSSOVER (chain graphs) ===`);
  for (const r of res.crossover)
    console.log(`  n=${String(r.n).padStart(4)}  pre-rewrite ${r.old.toFixed(4).padStart(9)} ms   now ${r.neu.toFixed(4).padStart(8)} ms   ${(r.old/r.neu).toFixed(2)}x`);
  await browser.close();
  process.exit(res.badCount || !res.liveEqual ? 1 : 0);
})().catch(e => { console.error('FAILED', e); process.exit(1); });
