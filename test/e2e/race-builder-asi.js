// Full-app headless E2E: drive the REAL race-builder UI and assert the floating-ASI
// authoring flow persists correctly-typed data to localStorage.
//
// This is the click-through counterpart to the JVM/harness tests:
//   - test/cljc/.../ability_increase_grant_test.clj    (compiled output lands on a character)
//   - test/cljs/.../ability_increase_grant_cljs_test.cljs (data flows through the races sub)
// Those dispatch events with already-correct keyword/int values. Only a real browser driving
// the actual <select> widgets exercises the view's string->value coercion — and that is exactly
// the layer that had the bug this test was written to catch (raw "cha"/"martial"/"1" strings).
//
// Prereqs (see docs/kb/cljs-headless-harness.md):
//   1. lein fig:build           -> resources/public/js/compiled/orcpub.js   (the app build)
//   2. lein garden once         -> resources/public/css/compiled/styles.css (optional; layout only)
//   3. cd test/e2e && npm i playwright && npx playwright install chromium
// Run:   REPO=/abs/path/to/orcpub node test/e2e/race-builder-asi.js
// Exit 0 = pass, non-zero = fail.

const { chromium } = require('playwright');
const http = require('http'), fs = require('fs'), path = require('path');

const REPO = process.env.REPO || path.resolve(__dirname, '../..');
const ROOT = path.join(REPO, 'resources/public');
const PORT = Number(process.env.PORT || 8816);
const HOST = `<!DOCTYPE html><html><head><meta charset="utf-8">
<link rel="stylesheet" href="/css/compiled/styles.css">
<link rel="stylesheet" href="/assets/font-awesome/5.13.1/css/all.min.css"></head>
<body><div id="app"></div><script src="/js/compiled/orcpub.js"></script></body></html>`;
const mime = {'.js':'text/javascript','.css':'text/css','.html':'text/html','.png':'image/png',
  '.svg':'image/svg+xml','.woff':'font/woff','.woff2':'font/woff2','.ttf':'font/ttf','.map':'application/json'};

// Static server rooted at resources/public, with SPA fallback (deep route -> host page).
const server = http.createServer((req, res) => {
  const p = decodeURIComponent(req.url.split('?')[0]);
  const fp = path.join(ROOT, p);
  fs.readFile(fp, (e, d) => {
    if (e) { res.setHeader('Content-Type', 'text/html'); res.end(HOST); return; }
    res.setHeader('Content-Type', mime[path.extname(fp)] || 'application/octet-stream');
    res.end(d);
  });
});

(async () => {
  await new Promise(r => server.listen(PORT, r));
  const browser = await chromium.launch();
  const pg = await browser.newPage();
  const errs = [];
  pg.on('pageerror', e => errs.push((e.message || e).toString().split('\n')[0]));
  await pg.setViewportSize({ width: 1280, height: 1000 });
  await pg.goto(`http://localhost:${PORT}/pages/dnd/5e/race-builder`, { waitUntil: 'load', timeout: 30000 });

  // The visible text inputs carry class `input h-40`; input[0] is Name. (input[1] is the
  // Orcacle SEARCH box, placeholder="search" — typing there opens an autofill suggestions
  // overlay that intercepts clicks. Target by class, not index-into-all-inputs.)
  await pg.waitForSelector('#app input.input.h-40', { timeout: 20000 });
  await pg.locator('#app input.input.h-40').nth(0).fill('Tide Touched');                 // Name
  await pg.locator('#app input[placeholder="Default Option Source"]').fill('E2E Pack');   // Option source

  // Author a spread: "+2 CHA (fixed), +1 to any martial (floating)" via two [amount, To] rows.
  await pg.locator('#app button').filter({ hasText: 'Add increase' }).first().click();
  await pg.locator('#app button').filter({ hasText: 'Add increase' }).first().click();
  await pg.waitForTimeout(300);

  const section = pg.locator('#app div.m-b-20').filter({ hasText: 'Ability Score Increases' });
  const rows = section.locator('> div.m-b-5');
  // reagent re-renders async after each dispatch; pause so each <select> on-change closure
  // sees the latest :ability-increases vector (otherwise a stale closure clobbers a prior pick).
  const setSel = async (l, label) => { await l.selectOption({ label }); await pg.waitForTimeout(150); };
  await setSel(rows.nth(0).locator('select').nth(0), '+2');                       // row 0 amount
  await setSel(rows.nth(0).locator('select').nth(1), 'Charisma');                 // row 0 target (fixed)
  await setSel(rows.nth(1).locator('select').nth(0), '+1');                       // row 1 amount
  await setSel(rows.nth(1).locator('select').nth(1), 'Martial (Str/Dex/Con)');   // row 1 target (floating)
  await pg.waitForTimeout(200);

  // There are TWO "Save to Browser Storage" buttons in the DOM (responsive: a hidden mobile
  // twin + the visible desktop one). `:visible` picks the real one.
  await pg.locator('#app button:visible').filter({ hasText: 'Save to Browser Storage' }).first().click();
  await pg.waitForTimeout(800);

  const ls = await pg.evaluate(() => {
    const o = {}; for (let i = 0; i < localStorage.length; i++) { const k = localStorage.key(i); o[k] = localStorage.getItem(k); } return o;
  });
  const hit = Object.entries(ls).find(([, v]) => /Tide Touched/.test(v) && /ability-increases/.test(v));

  console.log('PAGEERRORS:', errs.join(' | ') || 'none');
  let pass = false;
  if (hit) {
    const v = hit[1], i = v.indexOf('ability-increases'), slice = v.slice(i - 4, i + 60);
    console.log('persisted ASI:', slice);
    const checks = {
      'fixed +2 CHA as terse pair [2 :cha]':       /\[2 :cha\]/.test(slice),
      'floating +1 martial as terse pair [1 :martial]': /\[1 :martial\]/.test(slice),
    };
    pass = Object.values(checks).every(Boolean);
    for (const [k, ok] of Object.entries(checks)) console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${k}`);
  } else {
    console.log('FAIL  no saved race with :ability-increases found in localStorage');
  }
  console.log(pass ? 'E2E PASS' : 'E2E FAIL');

  await browser.close(); server.close();
  process.exitCode = pass ? 0 : 1;
})().catch(e => { console.error('ERR', e.message); process.exit(1); });
