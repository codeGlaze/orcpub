// Full export -> import -> use round-trip, driven entirely through the real browser UI.
// This is the UI-level counterpart to the function-level round-trip tests
// (ability_increase_grant_test / _cljs_test, which call (str plugin)/validate-import and
// char5e/to-strict directly). Only a real browser exercises the Export button's
// Blob/saveAs download, the <input type=file>/FileReader import, and the builder consuming
// the imported content.
//
// Steps:
//   1. author + save a floating-ASI race (race-builder)               -> localStorage "plugins"
//   2. My Content (/dnd/5e/my-content): expand the pack, click the real "export" button,
//      capture the ACTUAL downloaded .orcbrew, assert it carries :ability-increases
//   3. wipe the pack from localStorage (simulate a machine that never had it)
//   4. import the downloaded file via the real <input type=file>, assert the pack returns
//   5. open the character builder and assert the imported race is selectable
//
// NOTE (behaviour this E2E pins): import derives the PACK NAME from the file name
// (import-file: nm = split(filename, ".orcbrew")), so the download's suggested name
// ("<pack>.orcbrew") must be preserved or the pack is re-created under the wrong name.
//
// Prereqs: lein fig:build (+ optional lein garden once); cd test/e2e && npm i playwright && npx playwright install chromium
// Run:     REPO=/abs/path/to/orcpub node test/e2e/export-import-use.js   (exit 0 = pass)
const { chromium } = require('playwright');
const http=require('http'),fs=require('fs'),path=require('path');
const REPO=process.env.REPO||path.resolve(__dirname,'../..');
const ROOT=path.join(REPO,'resources/public'), PORT=Number(process.env.PORT||8831);
const HOST=`<!DOCTYPE html><html><head><meta charset="utf-8">
<link rel="stylesheet" href="/css/compiled/styles.css">
<link rel="stylesheet" href="/assets/font-awesome/5.13.1/css/all.min.css"></head>
<body><div id="app"></div><script src="/js/compiled/orcpub.js"></script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.png':'image/png','.svg':'image/svg+xml','.woff':'font/woff','.woff2':'font/woff2','.ttf':'font/ttf','.map':'application/json'};
const server=http.createServer((req,res)=>{const p=decodeURIComponent(req.url.split('?')[0]);const fp=path.join(ROOT,p);fs.readFile(fp,(e,d)=>{if(e){res.setHeader('Content-Type','text/html');res.end(HOST);return;}res.setHeader('Content-Type',mime[path.extname(fp)]||'application/octet-stream');res.end(d);});});
const errs=[]; const U=`http://localhost:${PORT}`;
const lsPlugins=async pg=>pg.evaluate(()=>localStorage.getItem('plugins')||'');
const results=[]; const check=(name,ok)=>{results.push(ok);console.log(`  ${ok?'PASS':'FAIL'}  ${name}`);};
(async()=>{
  await new Promise(r=>server.listen(PORT,r));
  const b=await chromium.launch();const pg=await b.newPage({acceptDownloads:true});
  pg.on('pageerror',e=>errs.push((e.message||e).toString().split('\n')[0]));
  await pg.setViewportSize({width:1280,height:1000});

  // 1. author + save
  await pg.goto(`${U}/pages/dnd/5e/race-builder`,{waitUntil:'load',timeout:30000});
  await pg.waitForSelector('#app input.input.h-40',{timeout:20000});
  await pg.locator('#app input.input.h-40').nth(0).fill('Tide Touched');
  await pg.locator('#app input[placeholder="Default Option Source"]').fill('E2E Pack');
  await pg.locator('#app button').filter({hasText:'Add fixed'}).first().click();
  await pg.locator('#app button').filter({hasText:'Add floating'}).first().click();
  await pg.waitForTimeout(300);
  const rows=pg.locator('#app div.m-b-20').filter({hasText:'Choice / Floating Ability Increases'}).locator('> div.m-b-5');
  const set=async(l,label)=>{await l.selectOption({label});await pg.waitForTimeout(150);};
  await set(rows.nth(0).locator('select').nth(0),'Charisma');
  await set(rows.nth(0).locator('select').nth(1),'+2');
  await set(rows.nth(1).locator('select').nth(0),'Martial (Str/Dex/Con)');
  await set(rows.nth(1).locator('select').nth(1),'+1');
  await pg.locator('#app button:visible').filter({hasText:'Save to Browser Storage'}).first().click();
  await pg.waitForTimeout(800);
  check('authored pack saved with :ability-increases', /E2E Pack[\s\S]*ability-increases/.test(await lsPlugins(pg)));

  // 2. export via the real button; capture the real download
  await pg.goto(`${U}/dnd/5e/my-content`,{waitUntil:'load',timeout:30000});
  await pg.waitForTimeout(1200);
  await pg.locator('#app .item-list-item').filter({hasText:'E2E Pack'}).first().locator('text=expand').first().click();
  await pg.waitForTimeout(400);
  const [download]=await Promise.all([
    pg.waitForEvent('download',{timeout:15000}),
    pg.locator('#app button:visible').filter({hasText:/^export$/i}).first().click(),
  ]);
  const suggested=download.suggestedFilename();
  check('export filename is the pack name (E2E Pack.orcbrew)', suggested==='E2E Pack.orcbrew');
  const dlPath=path.join('/tmp', suggested);
  await download.saveAs(dlPath);
  const exportedText=fs.readFileSync(dlPath,'utf8');
  check('exported .orcbrew has :ability-increases + qualified cha',
        /ability-increases/.test(exportedText) && /orcpub\.dnd\.e5\.character\/cha/.test(exportedText));

  // 3. wipe the pack
  await pg.evaluate(()=>localStorage.setItem('plugins','{"Default Option Source" {}}'));
  await pg.goto(`${U}/dnd/5e/my-content`,{waitUntil:'load',timeout:30000});
  await pg.waitForTimeout(1000);
  check('pack is gone before import', !/E2E Pack/.test(await pg.locator('#app').innerText()));

  // 4. import via the real <input type=file>
  await pg.locator('#app input[type=file]').setInputFiles(dlPath);
  await pg.waitForTimeout(1500);
  check('import restored the pack to plugins with :ability-increases',
        /E2E Pack[\s\S]*ability-increases/.test(await lsPlugins(pg)));
  await pg.goto(`${U}/dnd/5e/my-content`,{waitUntil:'load',timeout:30000});
  let reappeared=true;
  try { await pg.waitForFunction(()=>document.querySelector('#app').innerText.includes('E2E Pack'),{timeout:12000}); }
  catch(e){ reappeared=false; }
  check('pack visible in My Content after import', reappeared);

  // 5. use the imported content in the character builder
  await pg.goto(`${U}/pages/dnd/5e/character-builder`,{waitUntil:'load',timeout:30000});
  await pg.waitForTimeout(2000);
  check('imported race "Tide Touched" is selectable in the character builder', /Tide Touched/.test(await pg.locator('#app').innerText()));

  console.log('PAGEERRORS:', errs.join(' | ')||'none');
  const pass=results.every(Boolean) && errs.length===0;
  console.log(pass?'E2E PASS — export, import, and use-in-builder via the real UI':'E2E FAIL');
  await b.close();server.close();
  process.exitCode=pass?0:1;
})().catch(e=>{console.error('ERR',e.message);process.exit(1);});
