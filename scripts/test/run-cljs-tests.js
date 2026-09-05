// Runs the compiled ClojureScript test build in headless Chromium and exits
// non-zero on any failure. `lein fig:test` only COMPILES the build -- it is a
// browser target, so it cannot run under node -- and this is the runner
// docs/CONTRIBUTING.md means by "the node runner".
//
//   lein fig:test && node scripts/test/run-cljs-tests.js
//
// Serves target/test as a static site, because the build is :optimizations :none
// and loads its namespaces from js/out relative to the page, and reads what
// cljs.test prints to the console.
const http = require('http'), fs = require('fs'), path = require('path');
const { chromium } = require('playwright');

const ROOT = path.resolve(process.argv[2] || 'target/test');
const MIME = { '.js': 'application/javascript', '.html': 'text/html',
               '.map': 'application/json', '.json': 'application/json' };

function findChrome() {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  try {
    const dir = fs.readdirSync(base)
      .filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
    if (dir) {
      const p = path.join(base, dir, 'chrome-linux', 'chrome');
      if (fs.existsSync(p)) return p;
    }
  } catch (_) {}
  return undefined;
}

// cljs-test-display, which the auto-testing main reports through, renders into
// this element and asserts it exists.
const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body>
<div id="app-auto-testing"></div>
<script src="js/test.js"></script>
<script src="js/test-auto-testing.js"></script>
</body></html>`;

const server = http.createServer((req, res) => {
  const url = decodeURIComponent(req.url.split('?')[0]);
  if (url === '/' || url === '/index.html') {
    res.writeHead(200, { 'Content-Type': 'text/html' });
    return res.end(HTML);
  }
  const fp = path.join(ROOT, url);
  if (!fp.startsWith(ROOT) || !fs.existsSync(fp) || fs.statSync(fp).isDirectory()) {
    res.writeHead(404);
    return res.end();
  }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(fp)] || 'application/octet-stream' });
  fs.createReadStream(fp).pipe(res);
});

(async () => {
  if (!fs.existsSync(path.join(ROOT, 'js/test.js'))) {
    console.error(`No compiled test build at ${ROOT}/js/test.js -- run lein fig:test first.`);
    process.exit(2);
  }
  await new Promise(r => server.listen(0, r));
  const port = server.address().port;
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await browser.newPage();
  // The page runs the suite TWICE: orcpub.test-runner/-main runs the namespaces
  // it names, then figwheel's auto-testing main runs every test namespace the
  // build loaded. Both runs count, so every summary is kept and each has to be
  // clean. Lines are matched anywhere in the message: the display prefixes some.
  const failures = [];
  const pageErrors = [];
  const summaries = [], totals = [];
  page.on('console', m => {
    const t = m.text();
    if (process.env.CLJS_TEST_VERBOSE) console.log('| ' + t);
    if (/(FAIL|ERROR) in \(/.test(t)) failures.push(t.trim().split('\n')[0]);
    const s = t.match(/Ran (\d+) tests containing (\d+) assertions/);
    if (s) summaries.push(s[0]);
    const f = t.match(/(\d+) failures?, (\d+) errors?/);
    if (f) totals.push({ failures: +f[1], errors: +f[2] });
  });
  // The build is compiled with figwheel's dev harness in it, which tries to
  // open a websocket back to a figwheel server that is not running here. That
  // is noise, not a test failure; anything else on the page is reported.
  page.on('pageerror', e => { if (!/WebSocket|figwheel/i.test(e.message)) pageErrors.push(e.message); });
  await page.goto(`http://localhost:${port}/`);
  // The first summary is orcpub.test-runner/-main's, over the namespaces the
  // project names, and its own failure and error counts decide the exit code.
  // figwheel's auto-testing sweep runs every test namespace under test/ as well,
  // including ones the runner leaves out on purpose, interleaved with the named
  // run -- so failure lines are printed for the reader but not counted here;
  // cljs.test's summary already counted the ones that belong to it.
  const start = Date.now();
  while (!totals.length && Date.now() - start < 600000) await new Promise(r => setTimeout(r, 250));
  const pageSeen = pageErrors.length;
  await new Promise(r => setTimeout(r, 4000));
  if (!summaries.length) console.log('no "Ran N tests" line seen -- the build did not run');
  else console.log(`${summaries[0]}; ${totals[0].failures} failures, ${totals[0].errors} errors`);
  if (failures.length) {
    console.log(`${failures.length} failure line(s) on the page, across both runs:`);
    for (const f of failures) console.log('  ' + f);
  }
  pageErrors.forEach((e, i) => console.log(`page error${i >= pageSeen ? ' (during the sweep, not scored)' : ''}: ${e || '(no message)'}`));
  await browser.close();
  server.close();
  const t = totals[0];
  const ok = !!t && t.failures === 0 && t.errors === 0 && pageSeen === 0;
  if (!ok) console.log(`FAILED: ${!t ? 'no totals' : t.failures + ' failures, ' + t.errors + ' errors'}, ${pageSeen} page error(s) before the summary`);
  process.exit(ok ? 0 : 1);
})();
