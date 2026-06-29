// Multi-silo export -> CLEAR browser -> import -> use, through the real UI.
// multi-container-asi.js proves containment when the pack is injected into localStorage; this proves
// the EXPORT path emits BOTH silos (race + background) in one .orcbrew, and a genuinely cleared browser
// (localStorage.clear()) re-imports BOTH and they still render, stay attributed to their own container,
// and stack. i.e. "exported and re-imported into a fresh browser, are they properly handled?"
//
// Steps:
//   1. seed a pack with a race AND a background, each granting +1 martial  -> localStorage "plugins"
//   2. My Content: export via the real button, capture the .orcbrew, assert it carries BOTH silos
//   3. localStorage.clear()  (a machine that never had the pack)
//   4. import the downloaded file via the real <input type=file>, assert BOTH silos return
//   5. builder: select race + background, assert BOTH ASI widgets render (attributed) and stack
//
// Run: REPO=/abs/path/to/orcpub node test/e2e/multi-container-roundtrip.js   (exit 0 = pass)
const { chromium } = require('playwright');
const http=require('http'),fs=require('fs'),path=require('path');
const REPO=process.env.REPO||path.resolve(__dirname,'../..');
const ROOT=path.join(REPO,'resources/public'), PORT=Number(process.env.PORT||8878);
const HOST=`<!DOCTYPE html><html><head><meta charset="utf-8"><link rel="stylesheet" href="/css/compiled/styles.css"><link rel="stylesheet" href="/assets/font-awesome/5.13.1/css/all.min.css"></head><body><div id="app"></div><script src="/js/compiled/orcpub.js"></script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.png':'image/png','.svg':'image/svg+xml','.woff':'font/woff','.woff2':'font/woff2','.ttf':'font/ttf','.map':'application/json'};
const server=http.createServer((req,res)=>{const p=decodeURIComponent(req.url.split('?')[0]);const fp=path.join(ROOT,p);fs.readFile(fp,(e,d)=>{if(e){res.setHeader('Content-Type','text/html');res.end(HOST);return;}res.setHeader('Content-Type',mime[path.extname(fp)]||'application/octet-stream');res.end(d);});});
const PLUGINS=`{"Default Option Source" {} "RT Pack" {:orcpub.dnd.e5/races {:tide {:name "Tide" :key :tide :option-pack "RT Pack" :size :medium :speed 30 :languages #{} :traits [] :ability-increases [[1 :martial]]}} :orcpub.dnd.e5/backgrounds {:sea {:name "Sea-Marked" :key :sea :option-pack "RT Pack" :ability-increases [[1 :martial]]}}}}`;
const errs=[]; const U=`http://localhost:${PORT}`;
const lsPlugins=async pg=>pg.evaluate(()=>localStorage.getItem('plugins')||'');
const results=[]; const check=(n,ok,x)=>{results.push(ok);console.log(`  ${ok?'PASS':'FAIL'}  ${n}${x?'  '+x:''}`);};
const scores=async pg=>{const t=(await pg.locator('#app').innerText()).split('Ability Scores').pop().replace(/\n+/g,' ');const m={};for(const a of['STR','DEX','CON','INT','WIS','CHA']){const r=new RegExp(a+'\\s+(\\d+)').exec(t);if(r)m[a]=+r[1];}return m;};
(async()=>{
  await new Promise(r=>server.listen(PORT,r));
  const b=await chromium.launch();const pg=await b.newPage({acceptDownloads:true});
  pg.on('pageerror',e=>errs.push((e.message||e).toString().split('\n')[0]));
  await pg.setViewportSize({width:1280,height:1400});

  // 1. seed the two-silo pack
  await pg.goto(`${U}/dnd/5e/my-content`,{waitUntil:'load',timeout:30000});
  await pg.evaluate(p=>localStorage.setItem('plugins',p),PLUGINS);

  // 2. export via the real button; capture the real download
  await pg.goto(`${U}/dnd/5e/my-content`,{waitUntil:'load',timeout:30000});
  await pg.waitForTimeout(1200);
  await pg.locator('#app .item-list-item').filter({hasText:'RT Pack'}).first().locator('text=expand').first().click();
  await pg.waitForTimeout(400);
  const [download]=await Promise.all([
    pg.waitForEvent('download',{timeout:15000}),
    pg.locator('#app button:visible').filter({hasText:/^export$/i}).first().click(),
  ]);
  const suggested=download.suggestedFilename();
  check('export filename is the pack name (RT Pack.orcbrew)', suggested==='RT Pack.orcbrew');
  const dlPath=path.join('/tmp', suggested);
  await download.saveAs(dlPath);
  const exp=fs.readFileSync(dlPath,'utf8');
  check('exported .orcbrew carries the RACE silo with its spread',
        /orcpub\.dnd\.e5\/races/.test(exp) && /:tide/.test(exp));
  check('exported .orcbrew carries the BACKGROUND silo with its spread',
        /orcpub\.dnd\.e5\/backgrounds/.test(exp) && /:sea/.test(exp));
  check('both silos carry the terse [1 :martial] spread', (exp.match(/\[1 :martial\]/g)||[]).length>=2);

  // 3. fully clear the browser (a machine that never had the pack)
  await pg.evaluate(()=>localStorage.clear());
  await pg.goto(`${U}/dnd/5e/my-content`,{waitUntil:'load',timeout:30000});
  await pg.waitForTimeout(1000);
  check('pack gone after localStorage.clear()', !/RT Pack/.test(await pg.locator('#app').innerText()));

  // 4. import the downloaded file via the real <input type=file>
  await pg.locator('#app input[type=file]').setInputFiles(dlPath);
  await pg.waitForTimeout(1800);
  const ls=await lsPlugins(pg);
  check('import restored the RACE silo', /orcpub\.dnd\.e5\/races[\s\S]*:tide/.test(ls));
  check('import restored the BACKGROUND silo', /orcpub\.dnd\.e5\/backgrounds[\s\S]*:sea/.test(ls));
  check('both restored spreads are still the terse pairs', (ls.match(/\[1 :martial\]/g)||[]).length>=2);

  // 5. USE both in the builder out of the freshly-imported pack
  await pg.goto(`${U}/pages/dnd/5e/character-builder`,{waitUntil:'load',timeout:30000});
  await pg.waitForTimeout(2500);
  await pg.locator('#app span:visible',{hasText:'Tide'}).first().click(); await pg.waitForTimeout(800);     // race
  await pg.locator('#app .f-s-10.m-b-2').filter({hasText:'Background'}).first().click(); await pg.waitForTimeout(800);
  await pg.locator('#app span:visible',{hasText:'Sea-Marked'}).first().click(); await pg.waitForTimeout(800);
  await pg.locator('#app :text("Ability Scores / Feats")').first().click(); await pg.waitForTimeout(1500);
  const abil=(await pg.locator('#app').innerText()).replace(/\n+/g,' | ');
  check('imported race ASI widget renders, attributed to the race', /Improvement:[^|]*Race - Tide/.test(abil));
  check('imported background ASI widget renders, attributed to the background', /Improvement:[^|]*Background - Sea-Marked/.test(abil));
  const sel=pg.locator('#app select'); const n=await sel.count(); const slots=[];
  for(let i=0;i<n;i++){const o=await sel.nth(i).locator('option').allInnerTexts(); if(o.some(x=>/choose/.test(x))) slots.push(i);}
  check('two independent bag slots after import (one per container)', slots.length===2, `(found ${slots.length})`);
  const before=await scores(pg);
  await sel.nth(slots[0]).selectOption({label:'Strength'}); await pg.waitForTimeout(400);
  await sel.nth(slots[1]).selectOption({label:'Strength'});
  const want=before.STR+2;
  try{await pg.waitForFunction((w)=>{const m=/STR\s+(\d+)/.exec(document.querySelector('#app').innerText.split('Ability Scores').pop());return m&&+m[1]>=w;},want,{timeout:6000});}catch(e){}
  const after=await scores(pg);
  check('the two imported +1s STACK on STR (each from its own container)', after.STR===before.STR+2, `(${before.STR} -> ${after.STR})`);

  console.log('PAGEERRORS:', errs.join(' | ')||'none');
  const pass=results.every(Boolean)&&!errs.length;
  console.log(pass?'E2E PASS — multi-silo export -> clear -> import -> use':'E2E FAIL');
  await b.close();server.close(); process.exitCode=pass?0:1;
})().catch(e=>{console.error('ERR',e.message);process.exit(1);});
