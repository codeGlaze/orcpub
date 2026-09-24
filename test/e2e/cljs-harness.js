// per docs/kb/cljs-headless-harness.md — full-suite run (B)
const http=require('http'),fs=require('fs'),path=require('path');const {chromium}=require('playwright');
const {findChrome}=require('../browser/lib/find-chrome');
const ROOT=path.resolve('target/test');
const srv=http.createServer((q,r)=>{const u=decodeURIComponent(q.url.split('?')[0]);const f=path.join(ROOT,u==='/'?'runner-all.html':u);
 if(!f.startsWith(ROOT)||!fs.existsSync(f)||fs.statSync(f).isDirectory()){r.writeHead(404);return r.end();}
 r.writeHead(200,{'Content-Type':f.endsWith('.js')?'application/javascript; charset=utf-8':f.endsWith('.html')?'text/html; charset=utf-8':'application/octet-stream'});fs.createReadStream(f).pipe(r);});
(async()=>{await new Promise(r=>srv.listen(0,r));const port=srv.address().port;
 const br=await chromium.launch({executablePath:findChrome()});const pg=await br.newPage();const out=[];
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
 // Green only on a complete summary reading zero failures and zero errors; a
 // timeout or crash leaves no summary and must not pass.
 const last=tot.slice(-1)[0]||''; const clean=ran.length>0&&/^0 failures, 0 errors\./.test(last);
 process.exitCode=clean&&fails.length===0?0:1;
 await br.close();srv.close();})();
