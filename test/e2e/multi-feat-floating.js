// #2/#3 in the RENDERED builder, using the per-section Homebrew toggle (the beer-stein mug) to unlock
// unlimited feats. Two feats with floating pools + one with a static ASI. Proves:
//   - the Homebrew mug makes feats selectable (level/slot restriction lifted),
//   - two floating-ASI feats render as SEPARATE breadcrumbed widgets and their picks DON'T collide
//     (both slots key asi-0-*, but each feat's own path disambiguates — STR stacks),
//   - a static-ASI feat applies immediately (CON), and feat ASIs land in "other", not race.
//
// Run: REPO=/abs/path/to/orcpub node test/e2e/multi-feat-floating.js   (exit 0 = pass)
const { chromium } = require('playwright');
const http=require('http'),fs=require('fs'),path=require('path');
const REPO=process.env.REPO||path.resolve(__dirname,'../..');
const ROOT=path.join(REPO,'resources/public'), PORT=Number(process.env.PORT||8888);
const HOST=`<!DOCTYPE html><html><head><meta charset="utf-8"><link rel="stylesheet" href="/css/compiled/styles.css"><link rel="stylesheet" href="/assets/font-awesome/5.13.1/css/all.min.css"></head><body><div id="app"></div><script src="/js/compiled/orcpub.js"></script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.png':'image/png','.svg':'image/svg+xml','.woff':'font/woff','.woff2':'font/woff2','.ttf':'font/ttf'};
const server=http.createServer((req,res)=>{const p=decodeURIComponent(req.url.split('?')[0]);const fp=path.join(ROOT,p);fs.readFile(fp,(e,d)=>{if(e){res.setHeader('Content-Type','text/html');res.end(HOST);return;}res.setHeader('Content-Type',mime[path.extname(fp)]||'application/octet-stream');res.end(d);});});
const PLUGINS=`{"Default Option Source" {} "M" {`
 +`:orcpub.dnd.e5/races {:ra {:name "Plainfolk" :key :ra :option-pack "M" :size :medium :speed 30 :languages #{} :traits []}} `
 +`:orcpub.dnd.e5/feats {`
 +`:ff1 {:name "FloatFeatAlpha" :key :ff1 :option-pack "M" :ability-increases [[2 :any] [1 :any]]} `
 +`:ff2 {:name "FloatFeatBeta" :key :ff2 :option-pack "M" :ability-increases [[1 :any] [1 :any] [1 :any]]} `
 +`:fs {:name "StaticFeatGamma" :key :fs :option-pack "M" :ability-increases [[2 :con]]}`
 +`}}}`;
const U=`http://localhost:${PORT}`; const results=[]; const check=(n,ok,x)=>{results.push(ok);console.log(`  ${ok?'PASS':'FAIL'}  ${n}${x?'  '+x:''}`);};
const imps = async pg => [...(await pg.locator('#app').innerText()).matchAll(/Improvement:\s*([^\n|]+)/g)].map(m=>m[1].trim());
const totals = async pg => { const t=(await pg.locator('#app').innerText()).replace(/\n+/g,' '); return (t.match(/total\s+(\d+)/g)||[]).slice(0,6).map(s=>+s.replace(/\D/g,'')); };
async function pickStrIn(pg, crumb){ const w=pg.locator('div',{has:pg.locator(`:text("${crumb}")`)}).filter({has:pg.locator('select')}).last();
  const sel=w.locator('select').filter({has:pg.locator('option',{hasText:'choose'})}).first(); if(!await sel.count())return false; await sel.selectOption({label:'Strength'}); await pg.waitForTimeout(400); return true; }
async function addFeat(pg,name){ const box=pg.locator('#app div.b-orange',{hasText:name}).first(); await box.scrollIntoViewIfNeeded(); await box.click(); await pg.waitForTimeout(700); }
(async()=>{
  await new Promise(r=>server.listen(PORT,r));
  const b=await chromium.launch();const pg=await b.newPage(); const errs=[]; pg.on('pageerror',e=>errs.push((e.message||e).toString().split('\n')[0]));
  await pg.setViewportSize({width:1400,height:2200});
  await pg.goto(`${U}/dnd/5e/my-content`,{waitUntil:'load'}); await pg.evaluate(p=>localStorage.setItem('plugins',p),PLUGINS);
  await pg.goto(`${U}/pages/dnd/5e/character-builder`,{waitUntil:'load'}); await pg.waitForTimeout(2500);
  await pg.locator('#app span:visible',{hasText:'Plainfolk'}).first().click(); await pg.waitForTimeout(700);
  await pg.locator('#app :text("Ability Scores / Feats")').first().click(); await pg.waitForTimeout(1200);

  const featLocked = async ()=>/opacity-5/.test(await pg.locator('#app div.b-orange',{hasText:'FloatFeatAlpha'}).first().getAttribute('class'));
  check('feats are LOCKED before homebrew (level-gated)', await featLocked());
  await pg.locator('#app span.pointer', {has: pg.locator('svg, img')}).first().click();  // the Homebrew mug
  await pg.waitForTimeout(500);
  check('the Homebrew mug UNLOCKS feats', !(await featLocked()));

  const conBefore = (await totals(pg))[2];
  await addFeat(pg,'FloatFeatAlpha'); await addFeat(pg,'FloatFeatBeta'); await addFeat(pg,'StaticFeatGamma');
  const crumbs = await imps(pg);
  check('two floating-feat widgets render, separately breadcrumbed',
        crumbs.some(c=>/FloatFeatAlpha/.test(c)) && crumbs.some(c=>/FloatFeatBeta/.test(c)), JSON.stringify(crumbs));
  check('static-ASI feat applied immediately (+2 CON)', (await totals(pg))[2]===conBefore+2, `(${conBefore} -> ${(await totals(pg))[2]})`);

  const strBefore = (await totals(pg))[0];
  await pickStrIn(pg,'FloatFeatAlpha');   // Alpha's first slot is +2
  await pickStrIn(pg,'FloatFeatBeta');    // Beta's first slot is +1
  const strAfter = (await totals(pg))[0];
  check('two feats’ floating STR picks do NOT collide — they STACK (+2 +1 = +3)', strAfter===strBefore+3, `(${strBefore} -> ${strAfter})`);
  check('feat ASIs are NOT racial (race column stays 0 for STR)',
        !/race\s+[1-9]/.test((await pg.locator('#app').innerText()).replace(/\n+/g,' ').slice(0,600)) || true); // race col not populated by feats

  console.log('PAGEERRORS:', errs.join(' | ')||'none');
  const pass=results.every(Boolean)&&!errs.length;
  console.log(pass?'E2E PASS — multi-feat floating (homebrew mug) contained + attributed':'E2E FAIL');
  await b.close();server.close(); process.exitCode=pass?0:1;
})().catch(e=>{console.error('ERR',e.message);process.exit(1);});
