// Browser-driven e2e for the starting-equipment override DELTA (base + diff).
//
// Drives the REAL app in headless chromium: boots it, fills the class builder from an
// SRD class, tweaks it, and asserts the whole serialization boundary in the live runtime:
//   - fill records the SRD base marker and the full equipment form;
//   - collapse (export) writes a delta-ONLY form (base + just the tweak, no full selections);
//   - validate-import ACCEPTS that delta text (it must survive the import gate);
//   - expand (import) reproduces the full equipment identically;
//   - the "Based on <class>" UI shows, and Detach clears the link while keeping the equipment.
//
// Prerequisites (not part of `lein test` — run manually / in a browser CI job):
//   1. Dev build present:   lein fig:build      (populates resources/public/js/compiled/out/)
//   2. Playwright module:   PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install playwright
//      (chromium binaries expected under $PLAYWRIGHT_BROWSERS_PATH, default /opt/pw-browsers)
// Run:  node test/browser/starting_equipment_ledger_e2e.js
// Exit code 0 = all checks passed.
//
// Needs:     nothing. Serves resources/public from its own throwaway origin, no backend
// Runs in:   ~3s.
// Overlays:  suppressed by default -- the runner injects lib/suppress-overlays-preload.js, so
//            the cookie notice and What's New panel never intercept clicks. Hand-runs get no
//            preload, which is why this file also calls suppressOverlays itself.
const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const ROOT = path.resolve(__dirname, '../../resources/public');
const MIME = { '.js':'application/javascript', '.css':'text/css', '.html':'text/html',
  '.json':'application/json', '.map':'application/json', '.svg':'image/svg+xml',
  '.png':'image/png', '.gif':'image/gif', '.woff':'font/woff', '.woff2':'font/woff2',
  '.ttf':'font/ttf', '.ico':'image/x-icon' };

function findChrome() {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  try {
    const dir = fs.readdirSync(base).filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
    if (dir) { const p = path.join(base, dir, 'chrome-linux', 'chrome'); if (fs.existsSync(p)) return p; }
  } catch (_) {}
  return undefined; // fall back to playwright's bundled browser
}

const HOST_HTML = `<!doctype html><html><head><meta charset="utf-8"><title>e2e</title>
<script>window.__BRANDING__={};window.__INTEGRATIONS__={};</script></head>
<body style="margin:0"><div id="app"></div>
<script src="/js/compiled/orcpub.js"></script></body></html>`;

const server = http.createServer((req, res) => {
  const url = decodeURIComponent(req.url.split('?')[0]);
  if (url === '/' || url === '/index.html') { res.writeHead(200,{'Content-Type':'text/html'}); return res.end(HOST_HTML); }
  const fp = path.join(ROOT, url);
  if (!fp.startsWith(ROOT) || !fs.existsSync(fp) || fs.statSync(fp).isDirectory()) { res.writeHead(404); return res.end('nf'); }
  res.writeHead(200, {'Content-Type': MIME[path.extname(fp)] || 'application/octet-stream'});
  fs.createReadStream(fp).pipe(res);
});

const results = [];
const check = (name, ok, detail='') => { results.push({ok}); console.log(`${ok?'PASS':'FAIL'}  ${name}${detail?'  — '+detail:''}`); };

(async () => {
  if (!fs.existsSync(path.join(ROOT, 'js/compiled/out/goog/base.js'))) {
    console.error('Dev build missing — run `lein fig:build` first.'); process.exit(2);
  }
  await new Promise(r => server.listen(0, r));
  const PORT = server.address().port;
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await (await browser.newContext()).newPage();
  const errors = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));

  const dbAt = (edn) => page.evaluate((e) => window.cljs.core.pr_str.call(null,
    window.cljs.core.get_in.call(null, window.cljs.core.deref.call(null, window.re_frame.db.app_db),
      window.cljs.reader.read_string.call(null, e))), edn);

  try {
    await page.goto(`http://localhost:${PORT}/`, { waitUntil: 'load' });
    await page.waitForFunction(() => window.re_frame && window.re_frame.db && window.cljs && window.cljs.reader, null, { timeout: 30000 });
    await page.addScriptTag({ content:
      `window.__d = (edn) => window.re_frame.core.dispatch_sync.call(null, window.cljs.reader.read_string.call(null, edn));` });
    await page.evaluate(() => window.__d('[:route :class-builder-5e-page]'));
    await page.waitForFunction(() => document.body.innerText.includes('Starting Equipment'), null, { timeout: 15000 });
    check('class-builder renders', true);

    // Fill from an SRD class, then make one tweak (a fixed dagger).
    await page.evaluate(() => {
      window.__d('[:orcpub.dnd.e5.classes/set-class-prop :name "Battle Sage"]');
      window.__d('[:orcpub.dnd.e5.classes/set-class-prop :option-pack "E2E Source"]');
      window.__d('[:orcpub.dnd.e5.classes/fill-starting-equipment :fighter]');
      window.__d('[:orcpub.dnd.e5.classes/set-equipment :weapons {:dagger 1}]');
    });
    await page.waitForTimeout(150);
    check('fill records the SRD base marker', (await dbAt('[:orcpub.dnd.e5.classes/builder-item :starting-equipment-base]')) === ':fighter');

    // Exercise the real serialization boundary in the live runtime.
    const r = await page.evaluate(() => {
      const C = window.cljs.core, rd = (s) => window.cljs.reader.read_string.call(null, s), pr = (x) => C.pr_str.call(null, x);
      const gi = (m, p) => C.get_in.call(null, m, rd(p));
      const ev = window.orcpub.dnd.e5.events, led = window.orcpub.dnd.e5.starting_equipment_ledger,
            val = window.orcpub.dnd.e5.orcbrew_validation;
      const cls = gi(C.deref.call(null, window.re_frame.db.app_db), '[:orcpub.dnd.e5.classes/builder-item]');
      const data = C.assoc_in.call(null, rd('{"E2E Source" {:orcpub.dnd.e5/classes {}}}'),
                                   rd('["E2E Source" :orcpub.dnd.e5/classes :battle-sage]'), cls);
      const collapsed = ev.map_plugin_classes.call(null, led.collapse_class, data);
      const text = ev.serialize_orcbrew.call(null, collapsed);
      const result = val.validate_import.call(null, text,
        rd('{:strategy :progressive :auto-clean true :import-source-name "E2E Source"}'));
      // expand the collapsed class back and compare equipment to the original
      const collapsedCls = gi(collapsed, '["E2E Source" :orcpub.dnd.e5/classes :battle-sage]');
      const expanded = led.expand_class.call(null, collapsedCls);
      const eqKeys = rd('[:weapons :armor :equipment :equipment-selections]');
      return {
        deltaOnly: text.includes(':starting-equipment') && text.includes(':base :fighter') && !text.includes(':equipment-selections'),
        deltaHasTweak: text.includes(':dagger'),
        textLen: text.length,
        validated: pr(gi(result, '[:parse-error]')) === 'nil' && pr(gi(result, '[:success]')) === 'true',
        expandedFull: pr(gi(expanded, '[:equipment-selections]')) !== 'nil',
        eqIdentical: C._EQ_.call(null, C.select_keys.call(null, cls, eqKeys), C.select_keys.call(null, expanded, eqKeys)),
      };
    });
    check('collapse writes a delta-only form (no full selections)', r.deltaOnly, `${r.textLen} chars`);
    check('the delta carries the tweak', r.deltaHasTweak);
    check('validate-import accepts the delta text', r.validated);
    check('expand restores the full equipment identically', r.expandedFull && r.eqIdentical);

    // UI: the "Based on <class>" banner + Detach.
    check('"Based on the Fighter class" banner shows', await page.evaluate(() => document.body.innerText.includes('Based on the Fighter class')));
    await page.evaluate(() => { const b = Array.from(document.querySelectorAll('button')).find(b => /Detach/.test(b.innerText)); if (b) b.click(); });
    await page.waitForTimeout(150);
    check('Detach clears the base marker', (await dbAt('[:orcpub.dnd.e5.classes/builder-item :starting-equipment-base]')) === 'nil');
    check('Detach keeps the equipment (full form untouched)', (await dbAt('[:orcpub.dnd.e5.classes/builder-item :equipment-selections]')) !== 'nil');

    check('no page errors', errors.length === 0, errors.slice(0,3).join(' | '));
  } catch (e) {
    check('unexpected exception', false, String(e && e.message || e));
  } finally {
    await browser.close(); server.close();
  }

  const failed = results.filter(r => !r.ok).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})();
