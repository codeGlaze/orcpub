// Browser-driven acceptance test for the notification surfaces (orcpub.dnd.e5.views.notifications
// and neighbours). Drives the real app and asserts each surface mounts and renders WITHOUT page
// errors or unexpected console warnings:
//   - message toasts (notifications/message): error / warning / success, in the app header
//   - the confirmation dialog (also in the header)
//   - the starting-equipment callout (notifications/callout) on the class builder
//   - shared-content-banner produces callout output when its state is present
//   - the export-warning modal's state can be set (its host is mounted elsewhere in the app)
//
// Screenshots (best-effort) go to $SHOT_DIR if set, else a tmp dir — a dev aid, not required.
//
// Known-benign console noise in this headless, backend-less harness is filtered out:
//   - "Subscribe … outside of a reactive context" (induced by driving subs via dispatch_sync)
//   - connection-refused / "fetch character" (no backend running)
//
// Prereqs (not part of `lein test`): lein fig:build; lein garden once; playwright installed.
// Run:  node test/browser/notifications_acceptance_e2e.js       Exit 0 = all checks passed.
const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const { chromium } = require('playwright');

const ROOT = path.resolve(__dirname, '../../resources/public');
const SHOTS = process.env.SHOT_DIR || fs.mkdtempSync(path.join(os.tmpdir(), 'notif-shots-'));
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
<style>#app{background:#1a2130;min-height:100vh}body{font-family:'Open Sans',sans-serif}</style>
<link rel="stylesheet" href="/css/compiled/styles.css">
<script>window.__BRANDING__={};window.__INTEGRATIONS__={};</script></head>
<body style="margin:0;background:#1a2130"><div id="app"></div>
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
const check = (n, ok, d='') => { results.push({ok}); console.log(`${ok?'PASS':'FAIL'}  ${n}${d?'  — '+d:''}`); };

(async () => {
  if (!fs.existsSync(path.join(ROOT, 'js/compiled/out/goog/base.js'))) {
    console.error('Dev build missing — run `lein fig:build` first.'); process.exit(2);
  }
  await new Promise(r => server.listen(0, r));
  const PORT = server.address().port;
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await (await browser.newContext({ viewport: {width:1000, height:760}, deviceScaleFactor: 2 })).newPage();
  const consoleMsgs = [], pageErrs = [];
  page.on('console', m => { const t = m.type(); if (t==='error' || t==='warning') consoleMsgs.push(`[${t}] ${m.text().slice(0,140)}`); });
  page.on('pageerror', e => pageErrs.push(String(e).slice(0,140)));

  const d = (e) => page.evaluate((x) => window.__d(x), e);
  const has = (sel) => page.evaluate((s) => !!document.querySelector(s), sel);
  const clip = (name, h) => page.screenshot({ path: path.join(SHOTS, name+'.png'), clip: {x:0, y:0, width:1000, height:h} }).catch(()=>{});
  const shotEl = (name, sel) => page.$(sel).then(el => el && el.screenshot({ path: path.join(SHOTS, name+'.png') }).catch(()=>{}));

  try {
    await page.goto(`http://localhost:${PORT}/`, { waitUntil: 'load' });
    await page.waitForFunction(() => window.re_frame && window.re_frame.db && window.cljs && window.cljs.reader, null, { timeout: 30000 });
    await page.addScriptTag({ content:
      `window.__d = (e) => window.re_frame.core.dispatch_sync.call(null, window.cljs.reader.read_string.call(null, e));` });

    // Header surfaces (My Content page): toasts + confirmation.
    await d('[:route :my-content-5e-page]'); await page.waitForTimeout(500);
    for (const [tag, edn, cls] of [
      ['toast-error',   '[:show-error-message "Import failed: parse error on line 3."]', '.message.bg-red'],
      ['toast-warning', '[:show-warning-message "Imported — 2 entries set aside."]',      '.message.bg-orange'],
      ['toast-success', '[:show-message "Your class has been saved."]',                    '.message.bg-green']]) {
      await d('[:hide-message]'); await d(edn); await page.waitForTimeout(400);
      check(tag + ' renders', await has(cls));
      await clip(tag, 640);
    }
    await d('[:hide-message]');
    await d('[:show-confirmation {:question "Delete this character?" :confirm-button-text "Delete" :event [:hide-confirmation]}]');
    await page.waitForTimeout(300);
    check('confirmation dialog renders', await page.evaluate(() => document.body.innerText.includes('Delete this character?')));
    await clip('confirmation', 640);
    await d('[:hide-confirmation]');

    // Starting-equipment callout on the class builder.
    await d('[:route :class-builder-5e-page]');
    await page.waitForFunction(() => document.body.innerText.includes('Starting Equipment'), null, { timeout: 15000 });
    await d('[:orcpub.dnd.e5.classes/set-class-prop :name "Acceptance Probe"]');
    await d('[:orcpub.dnd.e5.classes/fill-starting-equipment :bard]');
    await page.waitForTimeout(400);
    check('starting-equipment callout renders', await has('.bg-warning'));
    await shotEl('callout-starting-equipment', '.bg-warning');

    // Moved banner: render-smoke via the component fn (needs heavy host state to mount visually).
    const shared = await page.evaluate(() => {
      const C = window.cljs.core, rd = (s) => window.cljs.reader.read_string.call(null, s), pr = (x) => C.pr_str.call(null, x), db = window.re_frame.db.app_db;
      C.reset_BANG_.call(null, db, C.assoc.call(null, C.deref.call(null, db), rd(':shared-content-info'), rd('{:count 3 :item-count 1 :collisions [{:name "Bob"}]}')));
      const scb = window.orcpub.dnd.e5.views.notifications.shared_content_banner.call(null, 1);
      return C.vector_QMARK_.call(null, scb) === true && pr(scb).includes('callout');
    });
    check('shared-content-banner produces callout output', shared);

    // Export-warning modal: its state can be set (host is mounted elsewhere in the app root).
    await d('[:route :my-content-5e-page]'); await page.waitForTimeout(300);
    await d('[:show-export-warning-modal {:mode :single :plugins [] :warnings ["A representative export warning."]}]');
    await page.waitForTimeout(300);
    check('export-warning modal state set', await page.evaluate(() =>
      window.cljs.core.pr_str.call(null, window.cljs.core.get_in.call(null, window.cljs.core.deref.call(null, window.re_frame.db.app_db),
        window.cljs.reader.read_string.call(null, '[:export-warning :active?]'))) === 'true'));

    // Real app problems vs known harness noise.
    const benign = /reactive context|ERR_CONNECTION_REFUSED|Unhandled HTTP status|fetch character|Failed to load resource/i;
    const unexpected = consoleMsgs.filter(m => !benign.test(m));
    if (unexpected.length) { console.log('UNEXPECTED console messages:'); unexpected.forEach(m => console.log('  ' + m)); }
    if (pageErrs.length)   { console.log('PAGE ERRORS:'); pageErrs.forEach(m => console.log('  ' + m)); }
    check('no page errors', pageErrs.length === 0, pageErrs.length + ' errors');
    check('no unexpected console warnings/errors', unexpected.length === 0,
          `${consoleMsgs.length - unexpected.length} benign, ${unexpected.length} unexpected`);
  } catch (e) {
    check('unexpected exception', false, String(e && e.message || e));
  } finally {
    await browser.close(); server.close();
  }

  const failed = results.filter(r => !r.ok).length;
  console.log(`\nscreenshots -> ${SHOTS}`);
  console.log(`${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})();
