// Headless runner for the existing CLJS unit suite (orcpub.test-runner).
// Serves the :none test build from target/test and loads it in chromium; the
// test-runner runs cljs.test at load and prints results to the console, which we
// capture. Exits non-zero if any test fails or the suite doesn't run.
//
//   lein fig:test && node e2e/run-cljs-tests.js
const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require('@playwright/test');

const ROOT = process.env.CLJS_TEST_ROOT || path.join(__dirname, '..', 'target', 'test');
const PORT = process.env.CLJS_TEST_PORT ? parseInt(process.env.CLJS_TEST_PORT) : 8901;
const HTML = '<!doctype html><html><head><meta charset="utf-8"></head>' +
             '<body><script src="/js/test.js"></script></body></html>';
const MIME = { '.js': 'application/javascript; charset=utf-8', '.map': 'application/json', '.edn': 'text/plain; charset=utf-8' };

const server = http.createServer((req, res) => {
  const p = decodeURIComponent(req.url.split('?')[0]);
  const fp = path.join(ROOT, p);
  if (p !== '/' && fp.startsWith(ROOT) && fs.existsSync(fp) && fs.statSync(fp).isFile()) {
    res.writeHead(200, { 'Content-Type': MIME[path.extname(fp)] || 'application/octet-stream' });
    fs.createReadStream(fp).pipe(res);
  } else {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' }); res.end(HTML);
  }
});

(async () => {
  if (!fs.existsSync(path.join(ROOT, 'js', 'test.js'))) {
    console.log('No build at', ROOT, '- run `lein fig:test` first.'); process.exit(2);
  }
  await new Promise(r => server.listen(PORT, 'localhost', r));
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const lines = [];
  page.on('console', m => lines.push(m.text()));
  page.on('pageerror', e => lines.push('PAGEERROR: ' + e.message));

  await page.goto(`http://localhost:${PORT}/`, { waitUntil: 'load' });

  let summary = null;
  for (let i = 0; i < 120 && !summary; i++) {
    summary = lines.find(l => /Ran \d+ tests containing \d+ assertions/.test(l));
    if (!summary) await page.waitForTimeout(500);
  }

  const counts = lines.find(l => /\d+ failures?, \d+ errors?/.test(l)) || '';
  // Surface ERROR in (uncaught exceptions) too — they used to be filtered out,
  // which hid a crashing test behind a bare "N errors" count.
  console.log(lines.filter(l => /Ran \d+ tests|FAIL in|ERROR in|failures?,|PAGEERROR/.test(l)).join('\n'));

  await browser.close();
  server.close();

  if (!summary) { console.log('NO SUMMARY — suite did not run (load error?)'); process.exit(2); }
  const m = counts.match(/(\d+) failures?, (\d+) errors?/);
  const failed = m ? (parseInt(m[1]) + parseInt(m[2])) : 1;
  console.log(failed === 0 ? 'CLJS SUITE: PASS' : `CLJS SUITE: ${failed} failing (${counts.trim()})`);
  process.exit(failed === 0 ? 0 : 1);
})();
