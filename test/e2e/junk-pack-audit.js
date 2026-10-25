// The full junk-pack audit: orcpub.dnd.e5.junk-pack-test with every content type, field, junk
// value and way in (the everyday suite runs only its base-pack check). It loads a build of that
// test alone and splits the work across pages running in parallel; each failure is written as
// soon as it is found.
//
//   lein fig:junk && NODE_PATH=/opt/node22/lib/node_modules node test/e2e/junk-pack-audit.js [pages] [types]
// pages: how many run at once (default 4). types: a comma list such as races,feats (default all).
// JUNK_UNGUARDED=1 turns the shape check off, to measure what our own code does with junk.
// Failures go to target/junk-audit/junk-audit.txt, one per line, and each page's console to
// console-<n>.log beside it. Exit 0 = nothing threw.
const http = require('http'), fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const { findChrome } = require('../browser/lib/find-chrome');

const ROOT = path.resolve('target/junk-audit');
const OUT = path.join(ROOT, 'junk-audit.txt');
const PAGE = '<body><script src="js/junk-audit.js"></script></body>';
const srv = http.createServer((q, r) => {
  const u = decodeURIComponent(q.url.split('?')[0]);
  if (u === '/' || u === '/audit.html') { r.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' }); return r.end(PAGE); }
  const f = path.join(ROOT, u);
  if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'Content-Type': f.endsWith('.js') ? 'application/javascript; charset=utf-8' : 'application/octet-stream' });
  fs.createReadStream(f).pipe(r);
});

(async () => {
  const n = Number(process.argv[2] || 4);
  const types = (process.argv[3] ? `=${process.argv[3]}` : '') + (process.env.JUNK_UNGUARDED ? '&unguarded' : '');
  if (!fs.existsSync(path.join(ROOT, 'js/junk-audit.js'))) { console.error('Run lein fig:junk first.'); process.exit(2); }
  await new Promise(r => srv.listen(0, '127.0.0.1', r));
  const port = srv.address().port;
  const br = await chromium.launch({ executablePath: findChrome() });
  fs.writeFileSync(OUT, '');
  const t0 = Date.now();
  let fails = 0;
  const results = await Promise.all([...Array(n).keys()].map(async i => {
    const pg = await br.newPage();
    let summary = null;
    const log = path.join(ROOT, `console-${i + 1}.log`);
    fs.writeFileSync(log, '');
    const done = new Promise(res => pg.on('console', m => {
      const l = m.text();
      fs.appendFileSync(log, l + '\n');
      if (l.startsWith('JUNKFAIL ')) { fs.appendFileSync(OUT, l.slice(9) + '\n'); fails++; console.log(l); }
      if (l.startsWith('JUNKJOB ')) console.log(`page ${i + 1}: ${l.slice(8)}`);
      if (l.startsWith('JUNKDONE ')) { summary = l.slice(9); res(); }
    }));
    pg.on('pageerror', e => console.log(`page ${i + 1}: ${e.message.split('\n')[0]}`));
    await pg.goto(`http://localhost:${port}/audit.html?junk-audit${types}&part=${i}/${n}`, { waitUntil: 'commit' });
    await done;
    console.log(`page ${i + 1}/${n} done in ${Math.round((Date.now() - t0) / 1000)} s: ${summary}`);
    await pg.close();
    return summary;
  }));
  console.log(`${fails} failure(s) in ${Math.round((Date.now() - t0) / 1000)} s`);
  process.exitCode = fails === 0 && results.every(Boolean) ? 0 : 1;
  await br.close(); srv.close();
})().catch(e => { console.error(e); process.exit(2); });
