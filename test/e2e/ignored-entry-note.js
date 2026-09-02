// harden → surface (guardrail 6): the compilers silently skip malformed :ability-increases entries
// (pool-entry?) for fan-out crash-safety. When a creator EDITS imported/hand-edited content that has
// such junk, the builder must (a) not crash, and (b) SURFACE that N entries will be ignored — rather
// than silently dropping them. Seeds a race whose :ability-increases has a good entry + a junk string
// + a nil-pool entry, opens it in the race-builder via My Content → edit, and asserts the note shows.
//
// Run: REPO=/abs/path/to/orcpub node test/e2e/ignored-entry-note.js   (exit 0 = pass)
const { chromium } = require('playwright');
const http=require('http'),fs=require('fs'),path=require('path');
const REPO=process.env.REPO||path.resolve(__dirname,'../..');
const ROOT=path.join(REPO,'resources/public'), PORT=Number(process.env.PORT||8889);
const HOST=`<!DOCTYPE html><html><head><meta charset="utf-8"><link rel="stylesheet" href="/css/compiled/styles.css"><link rel="stylesheet" href="/assets/font-awesome/5.13.1/css/all.min.css"></head><body><div id="app"></div><script src="/js/compiled/orcpub.js"></script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.png':'image/png','.svg':'image/svg+xml','.woff':'font/woff','.woff2':'font/woff2','.ttf':'font/ttf'};
const server=http.createServer((req,res)=>{const p=decodeURIComponent(req.url.split('?')[0]);const fp=path.join(ROOT,p);fs.readFile(fp,(e,d)=>{if(e){res.setHeader('Content-Type','text/html');res.end(HOST);return;}res.setHeader('Content-Type',mime[path.extname(fp)]||'application/octet-stream');res.end(d);});});
const PLUGINS=`{"Default Option Source" {} "BrokenPack" {:orcpub.dnd.e5/races {:broken {:name "BrokenRace" :key :broken :option-pack "BrokenPack" :size :medium :speed 30 :languages #{} :traits [] :ability-increases [[1 :cha] "junk" [:bad]]}}}}`;
const U=`http://localhost:${PORT}`; const results=[]; const check=(n,ok,x)=>{results.push(ok);console.log(`  ${ok?'PASS':'FAIL'}  ${n}${x?'  '+x:''}`);};
(async()=>{
  await new Promise(r=>server.listen(PORT,r));
  const b=await chromium.launch();const pg=await b.newPage(); const errs=[]; pg.on('pageerror',e=>errs.push((e.message||e).toString().split('\n')[0]));
  await pg.setViewportSize({width:1200,height:1400});
  await pg.goto(`${U}/dnd/5e/my-content`,{waitUntil:'load'}); await pg.evaluate(p=>localStorage.setItem('plugins',p),PLUGINS);
  await pg.goto(`${U}/dnd/5e/my-content`,{waitUntil:'load'}); await pg.waitForTimeout(1200);
  // expand BrokenPack, then every type section within it (the race section shows "1 Race", singular),
  // which surfaces the single edit button.
  const pack = pg.locator('#app .item-list-item',{hasText:'BrokenPack'}).first();
  await pack.locator(':text-is("expand")').first().click(); await pg.waitForTimeout(500);
  const sections = pack.locator(':text-is("expand")');
  for(let i=0;i<await sections.count();i++){ await sections.nth(i).click().catch(()=>{}); await pg.waitForTimeout(120); }
  await pg.waitForTimeout(300);
  const editBtn = pg.locator('#app button',{hasText:'edit'}).first();
  check('the malformed race is editable from My Content', await editBtn.count()>0);
  await editBtn.click(); await pg.waitForTimeout(1200);

  const txt = await pg.locator('#app').innerText();
  check('race-builder loaded (did NOT crash on the malformed data)', /Ability Score Increases/.test(txt));
  check('the ignored-entry note is surfaced (harden → surface)', /malformed and will be IGNORED/.test(txt),
        (/(\d+) ability-increase[^.\n|]*/.exec(txt)||['(none)'])[0]);
  check('no page errors editing malformed content', errs.length===0, errs.join(' | '));

  console.log('PAGEERRORS:', errs.join(' | ')||'none');
  const pass=results.every(Boolean);
  console.log(pass?'E2E PASS — malformed entries surfaced, builder survives':'E2E FAIL');
  await b.close();server.close(); process.exitCode=pass?0:1;
})().catch(e=>{console.error('ERR',e.message);process.exit(1);});
