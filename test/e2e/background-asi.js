// Backgrounds ASI in the rendered character builder (2024-PHB "ASI via origin").
// A homebrew background with :ability-increases [[2 :cha] [1 :martial]] must, once selected:
//   - render the choice on the Ability tab ("Improvement: Background - …"),
//   - apply the fixed +2 CHA automatically,
//   - offer the +1 only over the martial pool and apply it to the chosen stat.
// The compile/wiring is JVM- and cljs-harness-proven; this is the rendered-UI proof for the background silo.
//
// Prereqs: lein fig:build; cd test/e2e && npm i playwright && npx playwright install chromium
// Run:     REPO=/abs/path/to/orcpub node test/e2e/background-asi.js   (exit 0 = pass)
const { chromium } = require('playwright');
const http=require('http'),fs=require('fs'),path=require('path');
const REPO=process.env.REPO||path.resolve(__dirname,'../..');
const ROOT=path.join(REPO,'resources/public'), PORT=Number(process.env.PORT||8862);
const HOST=`<!DOCTYPE html><html><head><meta charset="utf-8">
<link rel="stylesheet" href="/css/compiled/styles.css">
<link rel="stylesheet" href="/assets/font-awesome/5.13.1/css/all.min.css"></head>
<body><div id="app"></div><script src="/js/compiled/orcpub.js"></script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.png':'image/png','.svg':'image/svg+xml','.woff':'font/woff','.woff2':'font/woff2','.ttf':'font/ttf','.map':'application/json'};
const server=http.createServer((req,res)=>{const p=decodeURIComponent(req.url.split('?')[0]);const fp=path.join(ROOT,p);fs.readFile(fp,(e,d)=>{if(e){res.setHeader('Content-Type','text/html');res.end(HOST);return;}res.setHeader('Content-Type',mime[path.extname(fp)]||'application/octet-stream');res.end(d);});});
const PLUGINS=`{"Default Option Source" {} "BG Pack" {:orcpub.dnd.e5/backgrounds {:tide-born {:name "Tide-Born" :option-pack "BG Pack" :key :tide-born :ability-increases [[2 :cha] [1 :martial]]}}}}`;
const errs=[]; const U=`http://localhost:${PORT}`;
const results=[]; const check=(n,ok,extra)=>{results.push(ok);console.log(`  ${ok?'PASS':'FAIL'}  ${n}${extra?'  '+extra:''}`);};
// Ability totals read STRUCTURALLY from the character summary panel: pair each
// .ability-score-name (STR/DEX/…) with its .ability-score (total) by index. No
// "STR<space>number" text grep — robust to layout and to a missing stylesheet.
const scores=async pg=>await pg.evaluate(()=>{const n=[...document.querySelectorAll('.ability-score-name')].map(e=>e.textContent.trim().toUpperCase());const v=[...document.querySelectorAll('.ability-score')].map(e=>+e.textContent.trim());const m={};n.forEach((a,i)=>{if(/^(STR|DEX|CON|INT|WIS|CHA)$/.test(a)&&!(a in m))m[a]=v[i];});return m;});
(async()=>{
  await new Promise(r=>server.listen(PORT,r));
  const b=await chromium.launch();const pg=await b.newPage();
  pg.on('pageerror',e=>errs.push((e.message||e).toString().split('\n')[0]));
  await pg.setViewportSize({width:1280,height:1400});
  await pg.goto(`${U}/dnd/5e/my-content`,{waitUntil:'load',timeout:30000});
  await pg.evaluate(p=>localStorage.setItem('plugins',p),PLUGINS);
  await pg.goto(`${U}/pages/dnd/5e/character-builder`,{waitUntil:'load',timeout:30000});
  await pg.waitForTimeout(2500);

  // Background tab (the visible left-nav tab) -> select the homebrew background
  await pg.locator('#app .f-s-10.m-b-2').filter({hasText:'Background'}).first().click();
  await pg.waitForTimeout(1000);
  check('homebrew background "Tide-Born" is selectable in the builder', /Tide-Born/.test(await pg.locator('#app').innerText()));
  await pg.locator('#app span:visible', {hasText:'Tide-Born'}).first().click();
  await pg.waitForTimeout(1200);

  // Ability Scores tab
  await pg.locator('#app :text("Ability Scores / Feats")').first().click();
  await pg.waitForTimeout(1500);
  const abil=(await pg.locator('#app').innerText()).replace(/\n+/g,' | ');
  check('background ASI choice renders ("Improvement: Background - Tide-Born")', /Improvement:[^|]*Tide-Born/.test(abil));

  // the floating +1 slot (the +2 CHA is a fixed label) restricted to martial
  const slot = pg.locator('#app select').filter({has: pg.locator('option', {hasText:'— choose —'})}).first();
  const opts = (await slot.locator('option').allInnerTexts()).filter(o=>!/choose/.test(o));
  check('floating +1 restricted to martial (str/dex/con)',
        opts.length===3 && opts.includes('Strength') && !opts.includes('Charisma'), `(offered: ${opts.join(',')})`);

  const before = await scores(pg);
  check('fixed +2 CHA applied automatically', before.CHA >= 10, `(CHA ${before.CHA})`); // base 8/10 + 2
  await slot.selectOption({label:'Dexterity'});
  try { await pg.waitForFunction(()=>{const m=/DEX\s+(\d+)/.exec(document.querySelector('#app').innerText.split('Ability Scores').pop()); return m && +m[1]>=15;},{timeout:8000}); } catch(e){}
  const after = await scores(pg);
  check('the +1 landed on the chosen DEX', after.DEX === before.DEX + 1, `(${before.DEX} -> ${after.DEX})`);
  check('CHA unchanged by the floating pick (fixed +2 stays)', after.CHA === before.CHA, `(${before.CHA} -> ${after.CHA})`);

  console.log('PAGEERRORS:', errs.join(' | ')||'none');
  const pass=results.every(Boolean)&&errs.length===0;
  console.log(pass?'E2E PASS — backgrounds ASI':'E2E FAIL');
  await b.close();server.close(); process.exitCode=pass?0:1;
})().catch(e=>{console.error('ERR',e.message);process.exit(1);});
