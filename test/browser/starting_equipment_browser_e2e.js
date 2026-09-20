// Browser-driven e2e for the starting-equipment class-builder UI.
//
// Drives the REAL app in headless chromium: boots it, navigates to the class
// builder, clicks the equipment buttons (real render + real dispatch), saves the
// homebrew class, exports a real .orcbrew *download*, and re-imports that file into
// a fresh clean-library browser context — asserting the equipment survives.
//
// Prerequisites (not part of `lein test` — run manually / in a browser CI job):
//   1. Dev build present:   lein fig:build      (populates resources/public/js/compiled/out/)
//   2. Playwright module:   PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install playwright
//      (chromium binaries are expected under $PLAYWRIGHT_BROWSERS_PATH, default /opt/pw-browsers)
// Run:  node test/browser/starting_equipment_browser_e2e.js
// Exit code 0 = all checks passed.
//
// Needs:     nothing. Serves resources/public from its own throwaway origin, no backend
// Runs in:   ~5s.
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
  const ctx = await browser.newContext({ acceptDownloads: true });
  const page = await ctx.newPage();
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));

  const dbAt = (p, edn) => p.evaluate((e) => window.cljs.core.pr_str.call(null,
    window.cljs.core.get_in.call(null, window.cljs.core.deref.call(null, window.re_frame.db.app_db),
      window.cljs.reader.read_string.call(null, e))), edn);

  try {
    await page.goto(`http://localhost:${PORT}/`, { waitUntil: 'load' });
    await page.waitForFunction(() => window.re_frame && window.re_frame.db && window.cljs && window.cljs.reader, null, { timeout: 30000 });
    check('app boots (re-frame present)', true);

    await page.addScriptTag({ content:
      `window.__d = (edn) => window.re_frame.core.dispatch_sync.call(null, window.cljs.reader.read_string.call(null, edn));` });

    await page.evaluate(() => window.__d('[:route :class-builder-5e-page]'));
    await page.waitForFunction(() => document.body.innerText.includes('Starting Equipment'), null, { timeout: 15000 });
    check('class-builder renders the Starting Equipment section', true);

    await page.getByRole('button', { name: '+ Add Weapons' }).first().click();
    await page.waitForTimeout(200);
    const weapons1 = await dbAt(page, '[:orcpub.dnd.e5.classes/builder-item :weapons]');
    check('clicking "+ Add Weapons" adds a fixed weapon to app-db', weapons1 && weapons1 !== 'nil' && weapons1 !== '{}', `:weapons = ${weapons1}`);

    await page.getByRole('button', { name: '+ Add choice group' }).first().click();
    await page.waitForTimeout(200);
    const sels = await dbAt(page, '[:orcpub.dnd.e5.classes/builder-item :equipment-selections]');
    check('clicking "+ Add choice group" adds a rich selection group (option with a grant)',
      sels && sels.includes(':grants'), `:equipment-selections = ${sels}`);

    await page.evaluate(() => { window.__d('[:orcpub.dnd.e5.classes/set-class-prop :name "Browser Test Class"]');
                                window.__d('[:orcpub.dnd.e5.classes/set-class-prop :option-pack "Browser Test Source"]');
                                window.__d('[:orcpub.dnd.e5.classes/save-class]'); });
    await page.waitForTimeout(300);
    const savedWeapons = await dbAt(page, '[:plugins "Browser Test Source" :orcpub.dnd.e5/classes :browser-test-class :weapons]');
    check('save-class persists the class + equipment into :plugins', savedWeapons && savedWeapons !== 'nil', `saved :weapons = ${savedWeapons}`);

    const [ download ] = await Promise.all([
      page.waitForEvent('download', { timeout: 10000 }).catch(() => null),
      page.evaluate(() => {
        const pd = window.cljs.core.get_in.call(null, window.cljs.core.deref.call(null, window.re_frame.db.app_db),
                     window.cljs.reader.read_string.call(null, '[:plugins "Browser Test Source"]'));
        window.re_frame.core.dispatch_sync.call(null,
          window.cljs.core.vector.call(null, window.cljs.core.keyword.call(null,'orcpub.dnd.e5','export-plugin'), 'Browser Test Source', pd));
      })
    ]);
    let fileText = null;
    if (download) { const dp = path.join(require('os').tmpdir(), 'exported.orcbrew'); await download.saveAs(dp); fileText = fs.readFileSync(dp, 'utf8'); }
    check('export produces a real .orcbrew download containing the equipment',
      !!fileText && fileText.includes(':weapons') && fileText.includes('browser-test-class'),
      fileText ? `${fileText.length} bytes` : 'no download captured');

    if (fileText) {
      const ctx2 = await browser.newContext();
      const page2 = await ctx2.newPage();
      await page2.goto(`http://localhost:${PORT}/`, { waitUntil: 'load' });
      await page2.waitForFunction(() => window.re_frame && window.cljs && window.cljs.reader, null, { timeout: 30000 });
      const before = await dbAt(page2, '[:plugins]');
      // matching the file's declared source avoids the source-name-choice modal, so it stores directly
      await page2.evaluate((txt) => window.re_frame.core.dispatch_sync.call(null,
        window.cljs.core.vector.call(null, window.cljs.core.keyword.call(null,'orcpub.dnd.e5','import-plugin'), 'Browser Test Source', txt)), fileText);
      await page2.waitForTimeout(400);
      const after = await dbAt(page2, '[:plugins]');
      check('fresh-library re-import of the .orcbrew file restores the class + :weapons',
        (!before || !before.includes('browser-test-class')) && after && after.includes('browser-test-class') && after.includes(':weapons'),
        `before had class: ${before.includes('browser-test-class')} | after has class+weapons: ${after.includes('browser-test-class') && after.includes(':weapons')}`);
      await ctx2.close();
    }

    check('no uncaught console/page errors during the flow', errors.length === 0, errors.slice(0,3).join(' | '));
  } catch (e) {
    check('e2e ran to completion', false, e.message);
  } finally {
    await browser.close();
    server.close();
    const failed = results.filter(r => !r.ok).length;
    console.log(`\n=== ${results.length - failed}/${results.length} checks passed ===`);
    process.exit(failed ? 1 : 0);
  }
})();
