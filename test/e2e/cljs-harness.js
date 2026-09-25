// per docs/kb/cljs-headless-harness.md — full-suite run (B)
const http=require('http'),fs=require('fs'),path=require('path');const {chromium}=require('playwright');
const ROOT=path.resolve('target/test');
// The runner page is WRITTEN HERE rather than kept in target/, which is build
// output: `lein fig:test && node test/e2e/cljs-harness.js` used to depend on an
// untracked hand-made file, so it failed on any clean checkout (and after a
// `rm -rf target/test`) with a bare Playwright navigation error.
//
// It loads the suite and mirrors console.log into the DOM, because cljs.test
// reports through println and the wait below keys on document.body text. The
// hook is installed before the suite script so no report line is missed.
const RUNNER=`<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>orcpub cljs suite</title></head>
<body>
<script>
  (function () {
    var orig = console.log;
    console.log = function () {
      orig.apply(console, arguments);
      try {
        var d = document.createElement('div');
        d.textContent = Array.prototype.join.call(arguments, ' ');
        document.body.appendChild(d);
      } catch (_) {}
    };
  })();
<\/script>
<script src="js/test.js"><\/script>
</body></html>
`;
if(!fs.existsSync(path.join(ROOT,'js','test.js'))){
 console.error('target/test/js/test.js is missing — run `lein fig:test` first.');process.exit(1);}
fs.writeFileSync(path.join(ROOT,'runner-all.html'),RUNNER);
function fc(){const b=process.env.PLAYWRIGHT_BROWSERS_PATH||'/opt/pw-browsers';try{const d=fs.readdirSync(b).filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).sort().pop();if(d){const p=path.join(b,d,'chrome-linux','chrome');if(fs.existsSync(p))return p;}}catch(_){}}
const srv=http.createServer((q,r)=>{const u=decodeURIComponent(q.url.split('?')[0]);const f=path.join(ROOT,u==='/'?'runner-all.html':u);
 if(!f.startsWith(ROOT)||!fs.existsSync(f)||fs.statSync(f).isDirectory()){r.writeHead(404);return r.end();}
 r.writeHead(200,{'Content-Type':f.endsWith('.js')?'application/javascript; charset=utf-8':f.endsWith('.html')?'text/html; charset=utf-8':'application/octet-stream'});fs.createReadStream(f).pipe(r);});
(async()=>{await new Promise(r=>srv.listen(0,r));const port=srv.address().port;
 const br=await chromium.launch({executablePath:fc()});const pg=await br.newPage();const out=[];
 pg.on('console',m=>out.push(m.text()));pg.on('pageerror',e=>out.push('PAGEERROR '+e));
 await pg.goto(`http://localhost:${port}/runner-all.html`);
 try{await pg.waitForFunction(()=>/Ran \d+ tests/.test(document.body.innerText),null,{timeout:240000});}catch(e){out.push('TIMEOUT waiting for Ran N tests');}
 const body=await pg.evaluate(()=>document.body.innerText);
 const all=out.join('\n')+'\n'+body;
 const ran=all.match(/Ran \d+ tests containing \d+ assertions\./g)||[]; const tot=all.match(/\d+ failures?, \d+ errors?\./g)||[];
 console.log('SUMMARY:',ran.slice(-1)[0]||'(none)',tot.slice(-1)[0]||'');
 const fails=[...new Set((all.match(/(FAIL|ERROR) in \([^)]*\)/g)||[]))];
 console.log(`distinct FAIL/ERROR: ${fails.length}`); fails.slice(0,40).forEach(f=>console.log('  '+f));
 fs.writeFileSync('target/test/cljs-run.log',all);
 await br.close();srv.close();})();
