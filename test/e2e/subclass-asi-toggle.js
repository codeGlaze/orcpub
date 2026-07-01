// Opt-in toggle + subclass ASI authoring, in the real subclass-builder.
// Non-standard fields (subclass ASI) sit behind a toggle so the builder isn't crammed by default:
//   - the "Ability Score Increases (non-standard)" section is COLLAPSED on a fresh subclass,
//   - clicking it reveals the shared ability-increase-choices widget,
//   - authoring a spread persists it as the terse [[amount pool]] form.
// (The subclass-side compile/wiring is cljs-harness-proven; the ASI applies via the same nested-:asi
//  mechanism already rendered-proven for races and backgrounds.)
//
// Run: REPO=/abs/path/to/orcpub node test/e2e/subclass-asi-toggle.js   (exit 0 = pass)
const { chromium } = require('playwright');
const http=require('http'),fs=require('fs'),path=require('path');
const REPO=process.env.REPO||path.resolve(__dirname,'../..');
const ROOT=path.join(REPO,'resources/public'), PORT=Number(process.env.PORT||8868);
const HOST=`<!DOCTYPE html><html><head><meta charset="utf-8">
<link rel="stylesheet" href="/css/compiled/styles.css">
<link rel="stylesheet" href="/assets/font-awesome/5.13.1/css/all.min.css"></head>
<body><div id="app"></div><script src="/js/compiled/orcpub.js"></script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.png':'image/png','.svg':'image/svg+xml','.woff':'font/woff','.woff2':'font/woff2','.ttf':'font/ttf','.map':'application/json'};
const server=http.createServer((req,res)=>{const p=decodeURIComponent(req.url.split('?')[0]);const fp=path.join(ROOT,p);fs.readFile(fp,(e,d)=>{if(e){res.setHeader('Content-Type','text/html');res.end(HOST);return;}res.setHeader('Content-Type',mime[path.extname(fp)]||'application/octet-stream');res.end(d);});});
const errs=[]; const U=`http://localhost:${PORT}`;
const results=[]; const check=(n,ok,extra)=>{results.push(ok);console.log(`  ${ok?'PASS':'FAIL'}  ${n}${extra?'  '+extra:''}`);};
(async()=>{
  await new Promise(r=>server.listen(PORT,r));
  const b=await chromium.launch();const pg=await b.newPage();
  pg.on('pageerror',e=>errs.push((e.message||e).toString().split('\n')[0]));
  await pg.setViewportSize({width:1280,height:1100});
  await pg.goto(`${U}/pages/dnd/5e/subclass-builder`,{waitUntil:'load',timeout:30000});
  await pg.waitForSelector('#app input.input.h-40',{timeout:20000});

  const sect = '#app :text("Ability Score Increases (non-standard)")';
  const txt0 = await pg.locator('#app').innerText();
  check('opt-in ASI section present', new RegExp('Ability Score Increases \\(non-standard\\)').test(txt0));
  check('collapsed by default (shows "click to add", widget hidden)',
        /click to add/.test(txt0) && !/Add increase/.test(txt0));

  await pg.locator(sect).first().click();   // toggle on
  await pg.waitForTimeout(400);
  const txt1 = await pg.locator('#app').innerText();
  check('toggle reveals the shared ASI widget ("Add increase")', /Add increase/.test(txt1));

  // author a fixed +2 CHA via the revealed widget
  await pg.locator('#app input.input.h-40').nth(0).fill('Tide Knight');                  // Name
  await pg.locator('#app input[placeholder="Default Option Source"]').fill('SC Pack');    // Option source
  await pg.locator('#app button').filter({hasText:'Add increase'}).first().click();
  await pg.waitForTimeout(300);
  const rows = pg.locator('#app div.flex.flex-wrap.align-items-c.m-b-5');   // ability-increase-choices rows
  const set=async(l,label)=>{await l.selectOption({label});await pg.waitForTimeout(150);};
  await set(rows.nth(0).locator('select').nth(0),'+2');
  await set(rows.nth(0).locator('select').nth(1),'Charisma');
  await pg.locator('#app button:visible').filter({hasText:'Save to Browser Storage'}).first().click();
  await pg.waitForTimeout(800);

  const ls = await pg.evaluate(()=>localStorage.getItem('plugins')||'');
  check('authored subclass spread persisted as terse pairs', /Tide Knight[\s\S]*:ability-increases \[\[2 :cha\]\]/.test(ls),
        (/ability-increases [^}]*/.exec(ls)||['(none)'])[0].slice(0,40));

  console.log('PAGEERRORS:', errs.join(' | ')||'none');
  const pass=results.every(Boolean)&&errs.length===0;
  console.log(pass?'E2E PASS — opt-in toggle + subclass ASI authoring':'E2E FAIL');
  await b.close();server.close(); process.exitCode=pass?0:1;
})().catch(e=>{console.error('ERR',e.message);process.exit(1);});
