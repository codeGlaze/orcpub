// The full junk-pack audit: orcpub.dnd.e5.junk-pack-test with every content type, field, junk
// value and way in (the everyday suite runs only its base-pack check). One page per content type,
// so a slow type does not hold up the rest; each failure is written as soon as it is found.
//
//   lein fig:test && NODE_PATH=/opt/node22/lib/node_modules node test/e2e/junk-pack-audit.js [types…]
// types: spells monsters encounters backgrounds languages invocations boons selections feats
//        races subraces subclasses classes (default: all). Failures go to target/test/junk-audit.txt,
// one per line. Exit 0 = nothing threw.
const http = require('http'), fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const { findChrome } = require('../browser/lib/find-chrome');

const TYPES = ['spells', 'monsters', 'encounters', 'backgrounds', 'languages', 'invocations', 'boons',
  'selections', 'feats', 'races', 'subraces', 'subclasses', 'classes'];
const ROOT = path.resolve('target/test');
const OUT = path.join(ROOT, 'junk-audit.txt');
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
  const types = process.argv.slice(2).length ? process.argv.slice(2) : TYPES;
  await new Promise(r => srv.listen(0, '127.0.0.1', r));
  const port = srv.address().port;
  const br = await chromium.launch({ executablePath: findChrome() });
  fs.writeFileSync(OUT, '');
  let fails = 0, unfinished = [];
  for (const t of types) {
    const pg = await br.newPage();
    pg.on('console', m => {
      const l = m.text();
      if (l.startsWith('JUNKFAIL ')) { fs.appendFileSync(OUT, l.slice(9) + '\n'); fails++; console.log(l); }
    });
    const t0 = Date.now();
    // the tests run while the page loads, so wait for the first byte only
    await pg.goto(`http://localhost:${port}/runner-all.html?junk-audit=${t}`, { waitUntil: 'commit', timeout: 600000 });
    try {
      await pg.waitForFunction(() => /Totals\s+\d+ Tests/.test(document.body.innerText), null, { timeout: 0, polling: 5000 });
      console.log(`${t}: done in ${Math.round((Date.now() - t0) / 60000)} min`);
    } catch (e) { unfinished.push(t); console.log(`${t}: did not finish (${e.message.split('\n')[0]})`); }
    await pg.close();
  }
  console.log(`${fails} failure(s)` + (unfinished.length ? `; unfinished: ${unfinished.join(' ')}` : ''));
  process.exitCode = fails === 0 && !unfinished.length ? 0 : 1;
  await br.close(); srv.close();
})().catch(e => { console.error(e); process.exit(2); });
