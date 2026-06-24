// Drive the exact-spread (bag) ability-increase widget in the rendered character builder.
// A homebrew race with :ability-increases [{:select {:from :any :amounts [2 1]}}] must:
//   - render one assign-from-bag picker per amount (a "+2" slot and a "+1" slot),
//   - enforce one-amount-per-ability (a stat chosen in one slot is excluded from the other),
//   - apply the assigned amounts to the chosen distinct stats (+2 STR, +1 DEX).
// This is the rendered-UI proof for the exact-spread feature; the compile/apply layer is JVM-proven
// in ability_increase_grant_test (bag-spread-* deftests).
//
// Prereqs: lein fig:build; cd test/e2e && npm i playwright && npx playwright install chromium
// Run:     REPO=/abs/path/to/orcpub node test/e2e/exact-spread-asi.js   (exit 0 = pass)
const { chromium } = require('playwright');
const http=require('http'),fs=require('fs'),path=require('path');
const REPO=process.env.REPO||path.resolve(__dirname,'../..');
const ROOT=path.join(REPO,'resources/public'), PORT=Number(process.env.PORT||8842);
const HOST=`<!DOCTYPE html><html><head><meta charset="utf-8">
<link rel="stylesheet" href="/css/compiled/styles.css">
<link rel="stylesheet" href="/assets/font-awesome/5.13.1/css/all.min.css"></head>
<body><div id="app"></div><script src="/js/compiled/orcpub.js"></script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.png':'image/png','.svg':'image/svg+xml','.woff':'font/woff','.woff2':'font/woff2','.ttf':'font/ttf','.map':'application/json'};
const server=http.createServer((req,res)=>{const p=decodeURIComponent(req.url.split('?')[0]);const fp=path.join(ROOT,p);fs.readFile(fp,(e,d)=>{if(e){res.setHeader('Content-Type','text/html');res.end(HOST);return;}res.setHeader('Content-Type',mime[path.extname(fp)]||'application/octet-stream');res.end(d);});});
// :from :martial restricts the floating pool to str/dex/con (also guards pool-restriction)
const PLUGINS=`{"Default Option Source" {} "Bag Pack" {:orcpub.dnd.e5/races {:bag-touched {:size :medium, :speed 30, :languages #{}, :traits [], :name "Bag Touched", :option-pack "Bag Pack", :key :bag-touched, :ability-increases [{:select {:from :martial, :amounts [2 1]}}]}}}}`;
const errs=[]; const U=`http://localhost:${PORT}`;
const results=[]; const check=(n,ok,extra)=>{results.push(ok);console.log(`  ${ok?'PASS':'FAIL'}  ${n}${extra?'  '+extra:''}`);};
const scores=async pg=>{const t=(await pg.locator('#app').innerText()).split('Ability Scores').pop().replace(/\n+/g,' ');
  const m={}; for(const a of ['STR','DEX','CON','INT','WIS','CHA']){const r=new RegExp(a+'\\s+(\\d+)').exec(t); if(r)m[a]=+r[1];} return m;};
(async()=>{
  await new Promise(r=>server.listen(PORT,r));
  const b=await chromium.launch();const pg=await b.newPage();
  pg.on('pageerror',e=>errs.push((e.message||e).toString().split('\n')[0]));
  await pg.setViewportSize({width:1280,height:1400});
  await pg.goto(`${U}/dnd/5e/my-content`,{waitUntil:'load',timeout:30000});
  await pg.evaluate(p=>localStorage.setItem('plugins',p),PLUGINS);
  await pg.goto(`${U}/pages/dnd/5e/character-builder`,{waitUntil:'load',timeout:30000});
  await pg.waitForTimeout(2500);
  await pg.locator('#app :text("Bag Touched")').first().click();
  await pg.waitForTimeout(1000);
  await pg.locator('#app :text("Ability Scores / Feats")').first().click();
  await pg.waitForTimeout(1500);

  const abil=(await pg.locator('#app').innerText()).replace(/\n+/g,' | ');
  check('bag widget renders ("Improvement: Race - Bag Touched")', /Improvement: Race - Bag Touched/.test(abil));

  const allSel = pg.locator('#app select');
  const n = await allSel.count();
  const slotIdx = [];
  for (let i=0;i<n;i++){ const opts=await allSel.nth(i).locator('option').allInnerTexts(); if(opts.some(o=>/choose/.test(o))) slotIdx.push(i); }
  check('two assign-from-bag slot pickers render', slotIdx.length===2, `(found ${slotIdx.length})`);
  if (slotIdx.length!==2){ console.log('PAGEERRORS:', errs.join(' | ')||'none'); await b.close(); server.close(); process.exit(1); }

  // pool restriction: a :martial bag must offer ONLY str/dex/con (not int/wis/cha)
  const slot0opts = await allSel.nth(slotIdx[0]).locator('option').allInnerTexts();
  const offered = slot0opts.filter(o=>!/choose/.test(o));
  check('floating pool restricted to :martial (str/dex/con only)',
        offered.length===3 && offered.includes('Strength') && offered.includes('Constitution') && !offered.includes('Charisma'),
        `(offered: ${offered.join(',')})`);

  const before = await scores(pg);                                  // slot order = bag order [2,1]
  await allSel.nth(slotIdx[0]).selectOption({label:'Strength'}); await pg.waitForTimeout(400);
  const slot1opts = await allSel.nth(slotIdx[1]).locator('option').allInnerTexts();
  check('uniqueness: a stat chosen in one slot is excluded from the other', !slot1opts.includes('Strength'));
  await allSel.nth(slotIdx[1]).selectOption({label:'Dexterity'});
  try { await pg.waitForFunction(()=>{const m=/DEX\s+(\d+)/.exec(document.querySelector('#app').innerText.split('Ability Scores').pop()); return m && +m[1]>=15;},{timeout:8000}); } catch(e){}

  const after = await scores(pg);
  check('the +2 landed on STR', after.STR === before.STR + 2, `(${before.STR} -> ${after.STR})`);
  check('the +1 landed on DEX', after.DEX === before.DEX + 1, `(${before.DEX} -> ${after.DEX})`);
  check('an unchosen stat is unchanged', after.CON === before.CON, `(CON ${before.CON} -> ${after.CON})`);

  console.log('PAGEERRORS:', errs.join(' | ')||'none');
  const pass=results.every(Boolean)&&errs.length===0;
  console.log(pass?'E2E PASS — exact-spread bag widget':'E2E FAIL');
  await b.close();server.close(); process.exitCode=pass?0:1;
})().catch(e=>{console.error('ERR',e.message);process.exit(1);});
