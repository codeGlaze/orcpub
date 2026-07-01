// Containment proof: a race AND a background each grant a floating +1 martial. They must render as
// TWO separate widgets (distinct source breadcrumbs), pick independently (per-container distinctness),
// and STACK on the same stat (cross-source) — proving each writes to its OWN container's entity path,
// not a shared one. This is the falsifiable answer to "if a race + subclass + feat each grant an ASI,
// are they properly contained within their containers and given out properly?" — containment is by the
// entity PATH the shared widget reads off each selection (::entity/path), orthogonal to the form.
//
// Run: REPO=/abs/path/to/orcpub node test/e2e/multi-container-asi.js   (exit 0 = pass)
const { chromium } = require('playwright');
const http=require('http'),fs=require('fs'),path=require('path');
const REPO=process.env.REPO||path.resolve(__dirname,'../..');
const ROOT=path.join(REPO,'resources/public'), PORT=Number(process.env.PORT||8875);
const HOST=`<!DOCTYPE html><html><head><meta charset="utf-8"><link rel="stylesheet" href="/css/compiled/styles.css"><link rel="stylesheet" href="/assets/font-awesome/5.13.1/css/all.min.css"></head><body><div id="app"></div><script src="/js/compiled/orcpub.js"></script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.png':'image/png','.svg':'image/svg+xml','.woff':'font/woff','.woff2':'font/woff2','.ttf':'font/ttf'};
const server=http.createServer((req,res)=>{const p=decodeURIComponent(req.url.split('?')[0]);const fp=path.join(ROOT,p);fs.readFile(fp,(e,d)=>{if(e){res.setHeader('Content-Type','text/html');res.end(HOST);return;}res.setHeader('Content-Type',mime[path.extname(fp)]||'application/octet-stream');res.end(d);});});
// race + background each grant +1 to a martial stat (separate containers, same pool)
const PLUGINS=`{"Default Option Source" {} "Pack" {:orcpub.dnd.e5/races {:tide {:name "Tide" :key :tide :option-pack "Pack" :size :medium :speed 30 :languages #{} :traits [] :ability-increases [[1 :martial]]}} :orcpub.dnd.e5/backgrounds {:sea {:name "Sea-Marked" :key :sea :option-pack "Pack" :ability-increases [[1 :martial]]}}}}`;
const U=`http://localhost:${PORT}`; const results=[]; const check=(n,ok,x)=>{results.push(ok);console.log(`  ${ok?'PASS':'FAIL'}  ${n}${x?'  '+x:''}`);};
const scores=async pg=>{const t=(await pg.locator('#app').innerText()).split('Ability Scores').pop().replace(/\n+/g,' ');const m={};for(const a of['STR','DEX','CON','INT','WIS','CHA']){const r=new RegExp(a+'\\s+(\\d+)').exec(t);if(r)m[a]=+r[1];}return m;};
(async()=>{
  await new Promise(r=>server.listen(PORT,r));
  const b=await chromium.launch();const pg=await b.newPage(); const errs=[]; pg.on('pageerror',e=>errs.push((e.message||e).toString().split('\n')[0]));
  await pg.setViewportSize({width:1280,height:1400});
  await pg.goto(`${U}/dnd/5e/my-content`,{waitUntil:'load'}); await pg.evaluate(p=>localStorage.setItem('plugins',p),PLUGINS);
  await pg.goto(`${U}/pages/dnd/5e/character-builder`,{waitUntil:'load'}); await pg.waitForTimeout(2500);
  await pg.locator('#app span:visible',{hasText:'Tide'}).first().click(); await pg.waitForTimeout(800);     // race
  await pg.locator('#app .f-s-10.m-b-2').filter({hasText:'Background'}).first().click(); await pg.waitForTimeout(800);
  await pg.locator('#app span:visible',{hasText:'Sea-Marked'}).first().click(); await pg.waitForTimeout(800);
  await pg.locator('#app :text("Ability Scores / Feats")').first().click(); await pg.waitForTimeout(1500);
  const abil=(await pg.locator('#app').innerText()).replace(/\n+/g,' | ');
  check('race ASI widget attributed to the race', /Improvement:[^|]*Race - Tide/.test(abil));
  check('background ASI widget attributed to the background', /Improvement:[^|]*Background - Sea-Marked/.test(abil));
  const sel=pg.locator('#app select'); const n=await sel.count(); const slots=[];
  for(let i=0;i<n;i++){const o=await sel.nth(i).locator('option').allInnerTexts(); if(o.some(x=>/choose/.test(x))) slots.push(i);}
  check('two independent bag slots (one per container)', slots.length===2, `(found ${slots.length})`);
  const before=await scores(pg);
  await sel.nth(slots[0]).selectOption({label:'Strength'}); await pg.waitForTimeout(400);
  const slot1opts=await sel.nth(slots[1]).locator('option').allInnerTexts();
  check('per-container distinctness: STR still offered in the OTHER container (cross-source allowed)', slot1opts.includes('Strength'));
  await sel.nth(slots[1]).selectOption({label:'Strength'});
  const want=before.STR+2;
  try{await pg.waitForFunction((w)=>{const m=/STR\s+(\d+)/.exec(document.querySelector('#app').innerText.split('Ability Scores').pop());return m&&+m[1]>=w;},want,{timeout:6000});}catch(e){}
  const after=await scores(pg);
  check('the two +1s STACK on STR (each from its own container)', after.STR===before.STR+2, `(${before.STR} -> ${after.STR})`);
  console.log('PAGEERRORS:', errs.join(' | ')||'none');
  const pass=results.every(Boolean)&&!errs.length;
  console.log(pass?'E2E PASS — multi-container ASI containment (race + background)':'E2E FAIL');
  await b.close();server.close(); process.exitCode=pass?0:1;
})().catch(e=>{console.error('ERR',e.message);process.exit(1);});
