// Run the ClojureScript suite headlessly and print its output.
//
// `lein fig:test` compiles the suite but reports results in a browser. This
// loads the compiled build and relays the console, so the suite is usable from
// a terminal or CI.
//
//   lein fig:test
//   (cd target/test && python3 -m http.server 8899 &)
//   node test/browser/run_cljs_suite.js
//
// target/test/index.html is written on first run if missing.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..', 'target', 'test');
const PORT = process.env.PORT || 8899;

if (!fs.existsSync(path.join(ROOT, 'index.html'))) {
  fs.writeFileSync(path.join(ROOT, 'index.html'),
    '<!doctype html><html><head><meta charset="utf-8"></head><body>' +
    '<script src="js/test.js"></script></body></html>');
}

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || undefined });
  const page = await browser.newPage();
  const lines = [];
  page.on('console', m => lines.push(m.text()));
  page.on('pageerror', e => lines.push('PAGEERROR ' + e.message));
  await page.goto(`http://localhost:${PORT}/index.html`,
                  { waitUntil: 'networkidle', timeout: 180000 });
  // The suite includes async (timer-driven) tests; give them room to finish.
  await page.waitForTimeout(25000);
  await browser.close();

  const out = lines.join('\n');
  console.log(out);
  const m = out.match(/(\d+) failures, (\d+) errors/);
  if (!m || m[1] !== '0' || m[2] !== '0') process.exit(1);
})().catch(e => { console.error('FAILED', e); process.exit(1); });
