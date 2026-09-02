// RENDERED USE of the save tools in the real builder. A homebrew race grants +1 to a martial stat
// WITH its save (the :save rider) AND lets the player choose 1 mental save (standalone
// :save-proficiencies). We prove, on the rendered Saving Throws table (proficient row = .f-w-b, not
// .opacity-7), that:
//   - picking the martial bump (Dexterity) turns the DEX SAVE proficient — the rider rides the choice
//     (DEX was not proficient until the pick), and
//   - the standalone "Saving Throw Proficiency" choice renders on the Proficiencies tab and picking
//     Wisdom turns the WIS save proficient.
// (Browser counterpart to the JVM ?saving-throws tests; the bag-assigner records a pick as a selected
//  option, so the option's save modifier fires with its bump.)
//
// Run: REPO=/abs/path/to/orcpub node test/e2e/save-grants-use.js   (exit 0 = pass)
const { chromium } = require('playwright');
const http=require('http'),fs=require('fs'),path=require('path');
const REPO=process.env.REPO||path.resolve(__dirname,'../..');
const ROOT=path.join(REPO,'resources/public'), PORT=Number(process.env.PORT||8883);
const HOST=`<!DOCTYPE html><html><head><meta charset="utf-8"><link rel="stylesheet" href="/css/compiled/styles.css"><link rel="stylesheet" href="/assets/font-awesome/5.13.1/css/all.min.css"></head><body><div id="app"></div><script src="/js/compiled/orcpub.js"></script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.png':'image/png','.svg':'image/svg+xml','.woff':'font/woff','.woff2':'font/woff2','.ttf':'font/ttf'};
const server=http.createServer((req,res)=>{const p=decodeURIComponent(req.url.split('?')[0]);const fp=path.join(ROOT,p);fs.readFile(fp,(e,d)=>{if(e){res.setHeader('Content-Type','text/html');res.end(HOST);return;}res.setHeader('Content-Type',mime[path.extname(fp)]||'application/octet-stream');res.end(d);});});
const PLUGINS=`{"Default Option Source" {} "SavePack" {:orcpub.dnd.e5/races {:warden {:name "Warden" :key :warden :option-pack "SavePack" :size :medium :speed 30 :languages #{} :traits [] :ability-increases [[1 :martial :save]] :save-proficiencies [[1 :mental]]}}}}`;
const U=`http://localhost:${PORT}`; const results=[]; const check=(n,ok,x)=>{results.push(ok);console.log(`  ${ok?'PASS':'FAIL'}  ${n}${x?'  '+x:''}`);};
// proficient? read the live Saving Throws summary table (.last() = the live panel; a proficient row is
// .f-w-b without .opacity-7).
const prof=async(pg,abbr)=>{const row=pg.locator('#app tr', {has: pg.locator(`span.saving-throw-name:text-is("${abbr}")`)}).last();
  if(!await row.count()) return null; const cls=(await row.getAttribute('class'))||''; return /f-w-b/.test(cls)&&!/opacity-7/.test(cls);};
(async()=>{
  await new Promise(r=>server.listen(PORT,r));
  const b=await chromium.launch();const pg=await b.newPage(); const errs=[]; pg.on('pageerror',e=>errs.push((e.message||e).toString().split('\n')[0]));
  await pg.setViewportSize({width:1280,height:1400});
  await pg.goto(`${U}/dnd/5e/my-content`,{waitUntil:'load'}); await pg.evaluate(p=>localStorage.setItem('plugins',p),PLUGINS);
  await pg.goto(`${U}/pages/dnd/5e/character-builder`,{waitUntil:'load'}); await pg.waitForTimeout(2500);
  await pg.locator('#app span:visible',{hasText:'Warden'}).first().click(); await pg.waitForTimeout(800);   // race
  await pg.locator('#app :text("Ability Scores / Feats")').first().click(); await pg.waitForTimeout(1500);

  // RIDER: DEX is not proficient until we pick it as the martial bump
  check('baseline: DEX save not proficient', (await prof(pg,'DEX'))===false);
  await pg.locator('#app select').first().selectOption({label:'Dexterity'}); await pg.waitForTimeout(900);
  check('rider: picking the DEX bump made the DEX SAVE proficient', (await prof(pg,'DEX'))===true);

  // STANDALONE: the mental save choice lives on the Proficiencies tab (tagged :profs)
  await pg.locator('#app .f-s-10.m-b-2').filter({hasText:'Proficiencies'}).first().click(); await pg.waitForTimeout(1200);
  check('standalone "Saving Throw Proficiency" choice renders on the Proficiencies tab',
        /Saving Throw Proficiency/.test(await pg.locator('#app').innerText()));
  check('baseline: WIS save not proficient', (await prof(pg,'WIS'))===false);
  await pg.locator('#app :text("Wisdom")').first().click(); await pg.waitForTimeout(900);
  check('standalone: picking the Wisdom save made the WIS SAVE proficient', (await prof(pg,'WIS'))===true);

  console.log('PAGEERRORS:', errs.join(' | ')||'none');
  const pass=results.every(Boolean)&&!errs.length;
  console.log(pass?'E2E PASS — save rider + standalone saves applied in the rendered builder':'E2E FAIL');
  await b.close();server.close(); process.exitCode=pass?0:1;
})().catch(e=>{console.error('ERR',e.message);process.exit(1);});
