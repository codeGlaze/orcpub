// per docs/kb/cljs-headless-harness.md — full-suite run (B)
const http=require('http'),fs=require('fs'),path=require('path');const {chromium}=require('playwright');
const {findChrome}=require('../browser/lib/find-chrome');
const ROOT=path.resolve('target/test');
// fig:test writes only the scripts; the page that runs them is served from here unless one exists.
const RUNNER='<body><div id="app-auto-testing"></div><script src="js/test-auto-testing.js"></script></body>';
const srv=http.createServer((q,r)=>{const u=decodeURIComponent(q.url.split('?')[0]);const f=path.join(ROOT,u==='/'?'runner-all.html':u);
 if(f===path.join(ROOT,'runner-all.html')&&!fs.existsSync(f)){r.writeHead(200,{'Content-Type':'text/html; charset=utf-8'});return r.end(RUNNER);}
 if(!f.startsWith(ROOT)||!fs.existsSync(f)||fs.statSync(f).isDirectory()){r.writeHead(404);return r.end();}
 r.writeHead(200,{'Content-Type':f.endsWith('.js')?'application/javascript; charset=utf-8':f.endsWith('.html')?'text/html; charset=utf-8':'application/octet-stream'});fs.createReadStream(f).pipe(r);});
(async()=>{await new Promise(r=>srv.listen(0,r));const port=srv.address().port;
 const br=await chromium.launch({executablePath:findChrome()});const pg=await br.newPage();const out=[];
 pg.on('console',m=>out.push(m.text()));pg.on('pageerror',e=>out.push('PAGEERROR '+e));
 await pg.goto(`http://localhost:${port}/runner-all.html`);
 // Figwheel's auto-testing page reports a "Totals" block, not cljs.test's "Ran N tests" line.
 try{await pg.waitForFunction(()=>/Ran \d+ tests|Totals\s+\d+ Tests/.test(document.body.innerText),null,{timeout:240000});}catch(e){out.push('TIMEOUT waiting for the totals');}
 const body=await pg.evaluate(()=>document.body.innerText);
 const all=out.join('\n')+'\n'+body;
 const ran=all.match(/Ran \d+ tests containing \d+ assertions\./g)||[]; const tot=all.match(/\d+ failures?, \d+ errors?\./g)||[];
 const lines=body.split('\n').map(l=>l.trim()).filter(Boolean);const ti=lines.indexOf('Totals');
 const totals=ti>=0?lines.slice(ti+1,lines.indexOf('Hide/Show Passing',ti)).join(', '):'';
 const verdict=ti>0?lines[ti-1]:'';
 console.log('SUMMARY:',ran.slice(-1)[0]||(totals?`${verdict}: ${totals}`:'(none)'),tot.slice(-1)[0]||'');
 // Figwheel says "All Tests Passed" for zero tests too (none found or loaded), so a pass needs a count.
 const passed=ran.length?/ 0 failures, 0 errors/.test(' '+(tot.slice(-1)[0]||'')):verdict==='All Tests Passed'&&/\b[1-9]\d*\s+Tests\b/.test(totals);
 process.exitCode=passed?0:1;
 // The auto-testing page marks each failed assertion with a .test-fail node inside its test's node.
 const shown=await pg.evaluate(()=>[...document.querySelectorAll('.test-fail')].map(n=>
   (n.parentElement.innerText.split('\n')[0]+' :: '+n.innerText.replace(/\s+/g,' ')).slice(0,300)));
 const fails=[...new Set([...(all.match(/(FAIL|ERROR) in \([^)]*\)/g)||[]),...shown])];
 console.log(`distinct FAIL/ERROR: ${fails.length}`); fails.slice(0,40).forEach(f=>console.log('  '+f));
 fs.writeFileSync('target/test/cljs-run.log',all);
 // Green only on a complete summary reading zero failures and zero errors; a
 // timeout or crash leaves no summary and must not pass.
 process.exitCode=passed&&fails.length===0?0:1;
 await br.close();srv.close();})();
