// The full junk-pack audit: orcpub.dnd.e5.junk-pack-test with every content type, field and junk
// value (the everyday suite runs only its base-pack check). Takes minutes, longer while entries
// still throw, because each failing pack is rerun one field at a time to name the field.
//
//   lein fig:test && NODE_PATH=/opt/node22/lib/node_modules node test/e2e/junk-pack-audit.js
// Prints one JUNKFAIL line per failure and writes them to target/test/junk-audit.txt.
// Exit 0 = nothing threw.
const http = require('http'), fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const { findChrome } = require('../browser/lib/find-chrome');

const ROOT = path.resolve('target/test');
const RUNNER = '<body><div id="app-auto-testing"></div><script src="js/test-auto-testing.js"></script></body>';
const srv = http.createServer((q, r) => {
  const u = decodeURIComponent(q.url.split('?')[0]);
  const f = path.join(ROOT, u === '/' ? 'runner-all.html' : u);
  if (f === path.join(ROOT, 'runner-all.html') && !fs.existsSync(f)) {
    r.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' }); return r.end(RUNNER);
  }
  if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'Content-Type': f.endsWith('.js') ? 'application/javascript; charset=utf-8' : 'text/html; charset=utf-8' });
  fs.createReadStream(f).pipe(r);
});

(async () => {
  await new Promise(r => srv.listen(0, '127.0.0.1', r));
  const port = srv.address().port;
  const br = await chromium.launch({ executablePath: findChrome() });
  const pg = await br.newPage();
  const out = [];
  pg.on('console', m => out.push(m.text()));
  pg.on('pageerror', e => out.push('PAGEERROR ' + e));
  // the tests run while the page loads, so wait for the first byte only
  await pg.goto(`http://localhost:${port}/runner-all.html?junk-audit`, { waitUntil: 'commit', timeout: 600000 });
  let done = true;
  try {
    await pg.waitForFunction(() => /Totals\s+\d+ Tests/.test(document.body.innerText), null, { timeout: 7200000 });
  } catch (e) { done = false; }
  const fails = out.filter(l => l.startsWith('JUNKFAIL ')).map(l => l.slice(9));
  fails.forEach(f => console.log('JUNKFAIL ' + f));
  fs.writeFileSync('target/test/junk-audit.txt', fails.join('\n') + '\n');
  console.log(done ? `${fails.length} failure(s)` : 'TIMEOUT before the suite finished');
  process.exitCode = done && fails.length === 0 ? 0 : 1;
  await br.close(); srv.close();
})().catch(e => { console.error(e); process.exit(2); });
