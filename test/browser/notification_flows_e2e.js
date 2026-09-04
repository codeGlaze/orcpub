// Browser-driven e2e for the notification view components (orcpub.dnd.e5.views.notifications).
//
// After lifting the transient message banner and the callout into views/notifications.cljs,
// this drives the REAL flows that produce notifications and asserts each moved component
// still renders where it's mounted:
//   - message banner (notifications/message) in the app header: red on an import parse error,
//     orange on a valid import's warning, green on a direct save message, and click-to-close;
//   - callout (notifications/callout) on the class builder: the "Based on <class>" box + Detach.
//
// Prerequisites (not part of `lein test` — run manually / in a browser CI job):
//   1. Dev build present:   lein fig:build
//   2. Styles present:      lein garden once   (the .form-button uppercase transform matters —
//                           match button text case-insensitively)
//   3. Playwright module:   PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install playwright
// Run:  node test/browser/notification_flows_e2e.js
// Exit code 0 = all checks passed.
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
  return undefined;
}

const HOST_HTML = `<!doctype html><html><head><meta charset="utf-8"><title>e2e</title>
<link rel="stylesheet" href="/css/compiled/styles.css">
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

  const d = (edn) => page.evaluate((e) => window.__d(e), edn);
  const dbAt = (edn) => page.evaluate((e) => window.cljs.core.pr_str.call(null,
    window.cljs.core.get_in.call(null, window.cljs.core.deref.call(null, window.re_frame.db.app_db),
      window.cljs.reader.read_string.call(null, e))), edn);
  const banner = () => page.evaluate(() => { const el = document.querySelector('.message'); return el ? el.className : null; });

  try {
    await page.goto(`http://localhost:${PORT}/`, { waitUntil: 'load' });
    await page.waitForFunction(() => window.re_frame && window.re_frame.db && window.cljs && window.cljs.reader, null, { timeout: 30000 });
    await page.addScriptTag({ content:
      `window.__d = (edn) => window.re_frame.core.dispatch_sync.call(null, window.cljs.reader.read_string.call(null, edn));` });

    // A page whose header mounts the message banner.
    await d('[:route :my-content-5e-page]');
    await page.waitForTimeout(400);

    // Import malformed content — real import flow → error message (red).
    await d('[:hide-message]');
    await d('[:orcpub.dnd.e5/import-plugin "Bad Import" "{:oops (unbalanced"]');
    await page.waitForTimeout(500);
    check('import malformed → error message', (await dbAt('[:message-shown?]')) === 'true' && (await dbAt('[:message-type]')) === ':error');
    check('  error banner renders red (notifications/message)', /bg-red/.test(await banner() || ''));

    // Import valid content — real import flow → message.
    await d('[:hide-message]'); await page.waitForTimeout(100);
    await d('[:orcpub.dnd.e5/import-plugin "Good Import" "{\\"Good Import\\" {:orcpub.dnd.e5/classes {:e2e-imp {:name \\"E2E Imp\\" :key :e2e-imp :option-pack \\"Good Import\\" :hit-die 8}}}}"]');
    await page.waitForTimeout(500);
    check('import valid → message shown', (await dbAt('[:message-shown?]')) === 'true');
    check('  banner renders (notifications/message)', /bg-(orange|green)/.test(await banner() || ''));

    // Direct success message → green (third severity).
    await d('[:hide-message]'); await page.waitForTimeout(100);
    await d('[:show-message "Saved."]'); await page.waitForTimeout(200);
    check('success message renders green', /bg-green/.test(await banner() || ''));
    await page.evaluate(() => { const el = document.querySelector('.message'); if (el) el.parentElement.click(); });
    await page.waitForTimeout(200);
    check('clicking the banner closes it', (await dbAt('[:message-shown?]')) === 'false');

    // Callout (notifications/callout) on the class builder.
    await d('[:route :class-builder-5e-page]');
    await page.waitForFunction(() => document.body.innerText.includes('Starting Equipment'), null, { timeout: 15000 });
    await d('[:orcpub.dnd.e5.classes/set-class-prop :name "Notify Probe"]');
    await d('[:orcpub.dnd.e5.classes/fill-starting-equipment :cleric]');
    await page.waitForTimeout(300);
    const callout = await page.evaluate(() => { const el = document.querySelector('.bg-warning'); return el ? el.innerText : null; });
    check('starting-equipment callout renders (.bg-warning via notifications/callout)', !!callout && /based on the cleric/i.test(callout));
    // .form-button is uppercased by CSS — match the label case-insensitively.
    const detachClicked = await page.evaluate(() => { const b = Array.from(document.querySelectorAll('button')).find(b => /detach/i.test(b.innerText)); if (b) { b.click(); return true; } return false; });
    let cleared = false;
    try { await page.waitForFunction(() => window.cljs.core.pr_str.call(null, window.cljs.core.get_in.call(null, window.cljs.core.deref.call(null, window.re_frame.db.app_db), window.cljs.reader.read_string.call(null, '[:orcpub.dnd.e5.classes/builder-item :starting-equipment-base]'))) === 'nil', null, { timeout: 3000 }); cleared = true; } catch (e) {}
    check('callout Detach clears the base', detachClicked && cleared);

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
