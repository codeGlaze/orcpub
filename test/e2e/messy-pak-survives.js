// Guardrail 8: prove the feature in the REAL app against MALFORMED content the way real paks are messy
// — not happy-path input. A homebrew pack holds a well-formed race AND a "cursed" race whose ASI/save
// data is junk (a stray string, a [:bad] amount, an empty vector, a stringy save entry). The builder
// must LOAD the pack, keep the good race fully working, and NOT crash on the malformed one — the app
// tolerates the mess (per guardrail 5 it doesn't silently drop the good data either).
//
// Run: REPO=/abs/path/to/orcpub node test/e2e/messy-pak-survives.js   (exit 0 = pass)
const { chromium } = require('playwright');
const http=require('http'),fs=require('fs'),path=require('path');
const REPO=process.env.REPO||path.resolve(__dirname,'../..');
const ROOT=path.join(REPO,'resources/public'), PORT=Number(process.env.PORT||8886);
const HOST=`<!DOCTYPE html><html><head><meta charset="utf-8"><link rel="stylesheet" href="/css/compiled/styles.css"><link rel="stylesheet" href="/assets/font-awesome/5.13.1/css/all.min.css"></head><body><div id="app"></div><script src="/js/compiled/orcpub.js"></script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.png':'image/png','.svg':'image/svg+xml','.woff':'font/woff','.woff2':'font/woff2','.ttf':'font/ttf'};
const server=http.createServer((req,res)=>{const p=decodeURIComponent(req.url.split('?')[0]);const fp=path.join(ROOT,p);fs.readFile(fp,(e,d)=>{if(e){res.setHeader('Content-Type','text/html');res.end(HOST);return;}res.setHeader('Content-Type',mime[path.extname(fp)]||'application/octet-stream');res.end(d);});});
// one well-formed race + one with deliberately malformed ASI/save data (valid EDN, junk VALUES)
const PLUGINS=`{"Default Option Source" {} "Messy Pak" {:orcpub.dnd.e5/races {`
 +`:warden {:name "Warden" :key :warden :option-pack "Messy Pak" :size :medium :speed 30 :languages #{} :traits [] :ability-increases [[1 :martial :save]] :save-proficiencies [[1 :mental]]} `
 +`:cursed {:name "Cursed" :key :cursed :option-pack "Messy Pak" :size :medium :speed 30 :languages #{} :traits [] :ability-increases [[1 :martial :save] "junk" [:bad] []] :save-proficiencies [[1 :con] "x" [:bad]]}}}}`;
const U=`http://localhost:${PORT}`; const results=[]; const check=(n,ok,x)=>{results.push(ok);console.log(`  ${ok?'PASS':'FAIL'}  ${n}${x?'  '+x:''}`);};
(async()=>{
  await new Promise(r=>server.listen(PORT,r));
  const b=await chromium.launch();const pg=await b.newPage(); const errs=[]; pg.on('pageerror',e=>errs.push((e.message||e).toString().split('\n')[0]));
  await pg.setViewportSize({width:1280,height:1400});
  await pg.goto(`${U}/dnd/5e/my-content`,{waitUntil:'load'}); await pg.evaluate(p=>localStorage.setItem('plugins',p),PLUGINS);

  // the messy pack must appear in My Content (one bad entry didn't poison the whole pack read).
  // (My Content lists packs collapsed; the individual races load + work is proven in the builder below.)
  await pg.goto(`${U}/dnd/5e/my-content`,{waitUntil:'load'}); await pg.waitForTimeout(1200);
  check('the messy pack loads, not quarantined', /Messy Pak/.test(await pg.locator('#app').innerText()));

  await pg.goto(`${U}/pages/dnd/5e/character-builder`,{waitUntil:'load'}); await pg.waitForTimeout(2500);
  check('builder rendered content despite the messy pack (no white-screen)', (await pg.locator('#app').innerText()).length>200);

  // the WELL-FORMED race still works end to end
  await pg.locator('#app span:visible',{hasText:'Warden'}).first().click(); await pg.waitForTimeout(800);
  await pg.locator('#app :text("Ability Scores / Feats")').first().click(); await pg.waitForTimeout(1200);
  check('good race Warden: ASI widget still renders amid the mess',
        /Improvement:[^\n]*Race - Warden/.test((await pg.locator('#app').innerText()).replace(/\n+/g,' ')));

  // selecting the MALFORMED race must not crash the app
  await pg.locator('#app .f-s-10.m-b-2').filter({hasText:'Race'}).first().click(); await pg.waitForTimeout(600);
  await pg.locator('#app span:visible',{hasText:'Cursed'}).first().click(); await pg.waitForTimeout(1000);
  check('malformed race Cursed is selectable without crashing', (await pg.locator('#app').innerText()).length>200);
  await pg.locator('#app :text("Ability Scores / Feats")').first().click(); await pg.waitForTimeout(1200);
  check('the ability tab still renders with the malformed race active (no throw)',
        /Ability/.test(await pg.locator('#app').innerText()));

  check('NO page errors across loading + using the messy pack', errs.length===0, errs.join(' | '));

  console.log('PAGEERRORS:', errs.join(' | ')||'none');
  const pass=results.every(Boolean);
  console.log(pass?'E2E PASS — messy pak survives in the real app':'E2E FAIL');
  await b.close();server.close(); process.exitCode=pass?0:1;
})().catch(e=>{console.error('ERR',e.message);process.exit(1);});
