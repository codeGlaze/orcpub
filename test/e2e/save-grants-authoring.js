// Authoring the save tools in the REAL race-builder UI (the new, coercion-prone rendered surface):
//   - the per-row "+ save prof" checkbox on an ASI increment emits the [amount pool :save] RIDER,
//   - the standalone "Saving Throw Proficiencies" widget emits :save-proficiencies [[count pool]].
// Both must round-trip through the actual <select>/checkbox coercion to the terse stored form.
//
// (Application is proven elsewhere: JVM ability_increase_grant_test builds a character and reads the
//  ?saving-throws set for both the rider and the standalone tool; the rendered bag-assigner records a
//  pick as a SELECTED OPTION (events.cljs increase-ability-value), so the option's save modifier fires
//  with its bump — the same entity shape the JVM test uses. The standalone selection reuses the
//  long-standing generic save-choice selection renderer.)
//
// Run: REPO=/abs/path/to/orcpub node test/e2e/save-grants-authoring.js   (exit 0 = pass)
const { chromium } = require('playwright');
const http=require('http'),fs=require('fs'),path=require('path');
const REPO=process.env.REPO||path.resolve(__dirname,'../..');
const ROOT=path.join(REPO,'resources/public'), PORT=Number(process.env.PORT||8881);
const HOST=`<!DOCTYPE html><html><head><meta charset="utf-8">
<link rel="stylesheet" href="/css/compiled/styles.css">
<link rel="stylesheet" href="/assets/font-awesome/5.13.1/css/all.min.css"></head>
<body><div id="app"></div><script src="/js/compiled/orcpub.js"></script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.png':'image/png','.svg':'image/svg+xml','.woff':'font/woff','.woff2':'font/woff2','.ttf':'font/ttf','.map':'application/json'};
const server=http.createServer((req,res)=>{const p=decodeURIComponent(req.url.split('?')[0]);const fp=path.join(ROOT,p);fs.readFile(fp,(e,d)=>{if(e){res.setHeader('Content-Type','text/html');res.end(HOST);return;}res.setHeader('Content-Type',mime[path.extname(fp)]||'application/octet-stream');res.end(d);});});
const errs=[]; const U=`http://localhost:${PORT}`;
const results=[]; const check=(n,ok,x)=>{results.push(ok);console.log(`  ${ok?'PASS':'FAIL'}  ${n}${x?'  '+x:''}`);};
const lsPlugins=async pg=>pg.evaluate(()=>localStorage.getItem('plugins')||'');
(async()=>{
  await new Promise(r=>server.listen(PORT,r));
  const b=await chromium.launch();const pg=await b.newPage();
  pg.on('pageerror',e=>errs.push((e.message||e).toString().split('\n')[0]));
  await pg.setViewportSize({width:1280,height:1200});
  await pg.goto(`${U}/pages/dnd/5e/race-builder`,{waitUntil:'load',timeout:30000});
  await pg.waitForSelector('#app input.input.h-40',{timeout:20000});

  await pg.locator('#app input.input.h-40').nth(0).fill('Saver');
  await pg.locator('#app input[placeholder="Default Option Source"]').fill('Save Pack');
  const set=async(l,label)=>{await l.selectOption({label});await pg.waitForTimeout(150);};

  // 1) ASI RIDER: +1 to a martial stat, then tick "+ save prof"
  const asiSection = pg.locator('#app div.m-b-20').filter({hasText:'Ability Score Increases'});
  await asiSection.locator('button').filter({hasText:'Add increase'}).first().click();
  await pg.waitForTimeout(250);
  const asiRow = asiSection.locator('> div.flex.flex-wrap.align-items-c.m-b-5').first();
  await set(asiRow.locator('select').nth(0),'+1');
  await set(asiRow.locator('select').nth(1),'Martial (Str/Dex/Con)');
  check('"+ save prof" toggle is present on the ASI row', await asiRow.locator('div.pointer', {hasText:'save prof'}).count()>0);
  await asiRow.locator('div.pointer').filter({hasText:'save prof'}).first().click();   // turn the rider on
  await pg.waitForTimeout(250);

  // 2) STANDALONE saves: choose 1 mental save
  const spSection = pg.locator('#app div.m-b-20').filter({hasText:'Saving Throw Proficiencies'});
  check('standalone "Saving Throw Proficiencies" widget present', await spSection.count()>0);
  await spSection.locator('button').filter({hasText:'Add save'}).first().click();
  await pg.waitForTimeout(250);
  const spRow = spSection.locator('> div.flex.flex-wrap.align-items-c.m-b-5').first();
  await set(spRow.locator('select').nth(0),'1');
  await set(spRow.locator('select').nth(1),'Mental (Int/Wis/Cha)');

  await pg.locator('#app button:visible').filter({hasText:'Save to Browser Storage'}).first().click();
  await pg.waitForTimeout(800);

  const ls = await lsPlugins(pg);
  check('ASI rider persisted as [[1 :martial :save]]', /:ability-increases \[\[1 :martial :save\]\]/.test(ls),
        (/:ability-increases [^\]]*\]\]/.exec(ls)||['(none)'])[0].slice(0,40));
  check('standalone saves persisted as [[1 :mental]]', /:save-proficiencies \[\[1 :mental\]\]/.test(ls),
        (/:save-proficiencies [^\]]*\]\]/.exec(ls)||['(none)'])[0].slice(0,40));

  // 3) WARN-AND-EXPLAIN: martial save-rider + a mental standalone do NOT overlap (no warning yet).
  //    Switch the standalone From -> Martial: now both draw from the martial pool -> a warning appears.
  check('no coverage warning while pools are disjoint (martial vs mental)', !/overlapping pools/.test(await pg.locator('#app').innerText()));
  await set(spRow.locator('select').nth(1),'Martial (Str/Dex/Con)');
  await pg.waitForTimeout(300);
  check('authoring an overlap surfaces the warn-and-explain note', /overlapping pools/.test(await pg.locator('#app').innerText()));

  console.log('PAGEERRORS:', errs.join(' | ')||'none');
  const pass=results.every(Boolean)&&errs.length===0;
  console.log(pass?'E2E PASS — save rider + standalone save authoring':'E2E FAIL');
  await b.close();server.close(); process.exitCode=pass?0:1;
})().catch(e=>{console.error('ERR',e.message);process.exit(1);});
