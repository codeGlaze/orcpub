// THREE concurrent floating ASI sources of different shapes (race +2/+1, subrace +2/+1, background
// +1/+1/+1) — the multi-source case that exposed two things:
//   1. Containment: the three pools render as SEPARATE, source-breadcrumbed widgets (7 slots = 2+2+3),
//      never a merged pool.
//   2. Attribution: a CHOSEN floating pick must land in its source's ability-breakdown column, not the
//      orphaned level-up bucket. The tell: before the fix, picking the subrace's floating slot left the
//      "subrace" column ABSENT (the pick went to ?level-ability-increases). After the fix it uses the
//      silo's modifier, so ?subrace-ability-increases fills and the subrace column APPEARS.
//
// Run: REPO=/abs/path/to/orcpub node test/e2e/multi-source-floating-attribution.js   (exit 0 = pass)
const { chromium } = require('playwright');
const http=require('http'),fs=require('fs'),path=require('path');
const REPO=process.env.REPO||path.resolve(__dirname,'../..');
const ROOT=path.join(REPO,'resources/public'), PORT=Number(process.env.PORT||8887);
const HOST=`<!DOCTYPE html><html><head><meta charset="utf-8"><link rel="stylesheet" href="/css/compiled/styles.css"><link rel="stylesheet" href="/assets/font-awesome/5.13.1/css/all.min.css"></head><body><div id="app"></div><script src="/js/compiled/orcpub.js"></script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.png':'image/png','.svg':'image/svg+xml','.woff':'font/woff','.woff2':'font/woff2','.ttf':'font/ttf'};
const server=http.createServer((req,res)=>{const p=decodeURIComponent(req.url.split('?')[0]);const fp=path.join(ROOT,p);fs.readFile(fp,(e,d)=>{if(e){res.setHeader('Content-Type','text/html');res.end(HOST);return;}res.setHeader('Content-Type',mime[path.extname(fp)]||'application/octet-stream');res.end(d);});});
const PLUGINS=`{"Default Option Source" {} "M" {`
 +`:orcpub.dnd.e5/races {:ra {:name "RaceA" :key :ra :option-pack "M" :size :medium :speed 30 :languages #{} :traits [] :ability-increases [[2 :any] [1 :any]]}} `
 +`:orcpub.dnd.e5/subraces {:sa {:name "SubA" :key :sa :race :ra :option-pack "M" :traits [] :ability-increases [[2 :any] [1 :any]]}} `
 +`:orcpub.dnd.e5/backgrounds {:bg {:name "BgB" :key :bg :option-pack "M" :ability-increases [[1 :any] [1 :any] [1 :any]]}}`
 +`}}`;
const U=`http://localhost:${PORT}`; const results=[]; const check=(n,ok,x)=>{results.push(ok);console.log(`  ${ok?'PASS':'FAIL'}  ${n}${x?'  '+x:''}`);};
const imps = async pg => [...(await pg.locator('#app').innerText()).matchAll(/Improvement:\s*([^\n|]+)/g)].map(m=>m[1].trim());
// pick "Strength" in the first "— choose —" select of the widget whose breadcrumb contains `crumb`
async function pickStrIn(pg, crumb){
  const w = pg.locator('div', {has: pg.locator(`:text("${crumb}")`)}).filter({has: pg.locator('select')}).last();
  const sel = w.locator('select').filter({has: pg.locator('option',{hasText:'choose'})}).first();
  if(!await sel.count()) return false;
  await sel.selectOption({label:'Strength'}); await pg.waitForTimeout(500); return true;
}
(async()=>{
  await new Promise(r=>server.listen(PORT,r));
  const b=await chromium.launch();const pg=await b.newPage(); const errs=[]; pg.on('pageerror',e=>errs.push((e.message||e).toString().split('\n')[0]));
  await pg.setViewportSize({width:1400,height:1800});
  await pg.goto(`${U}/dnd/5e/my-content`,{waitUntil:'load'}); await pg.evaluate(p=>localStorage.setItem('plugins',p),PLUGINS);
  await pg.goto(`${U}/pages/dnd/5e/character-builder`,{waitUntil:'load'}); await pg.waitForTimeout(2500);
  await pg.locator('#app span:visible',{hasText:'RaceA'}).first().click(); await pg.waitForTimeout(700);
  await pg.locator('#app span:visible',{hasText:'SubA'}).first().click(); await pg.waitForTimeout(700);
  await pg.locator('#app .f-s-10.m-b-2').filter({hasText:'Background'}).first().click(); await pg.waitForTimeout(700);
  await pg.locator('#app span:visible',{hasText:'BgB'}).first().click(); await pg.waitForTimeout(700);
  await pg.locator('#app :text("Ability Scores / Feats")').first().click(); await pg.waitForTimeout(1500);

  const crumbs = await imps(pg);
  check('three SEPARATE source-breadcrumbed floating widgets (containment)',
        crumbs.some(c=>/Subrace - SubA/.test(c)) && crumbs.some(c=>/^Race - RaceA$/.test(c)) && crumbs.some(c=>/Background - BgB/.test(c)),
        JSON.stringify(crumbs));
  check('7 floating slots (2 race + 2 subrace + 3 background — pools not merged)',
        (await pg.locator('#app select').count())===7);
  check('before any pick, the subrace column is absent (floating unchosen contributes nothing)',
        (await pg.locator('#app :text-is("subrace")').count())===0);

  const ok = await pickStrIn(pg, 'Subrace - SubA');
  check('picked a floating slot in the SUBRACE widget', ok);
  check('the subrace column now APPEARS (floating pick attributes to its source, not the level bucket)',
        (await pg.locator('#app :text-is("subrace")').count())>0);

  console.log('PAGEERRORS:', errs.join(' | ')||'none');
  const pass=results.every(Boolean)&&!errs.length;
  console.log(pass?'E2E PASS — multi-source floating: contained + attributed to source':'E2E FAIL');
  await b.close();server.close(); process.exitCode=pass?0:1;
})().catch(e=>{console.error('ERR',e.message);process.exit(1);});
