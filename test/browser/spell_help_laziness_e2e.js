// Is the spell peek REALLY only built when opened?
//
// Deferring :help to a thunk is worthless if something forces it during ordinary rendering.
// That regression is easy to introduce and invisible to the JVM suite: an earlier version of
// this change forced inside the wrapper that option-selector-base rebuilds on EVERY render
// of EVERY visible option card, which is worse than building it once at template time.
//
// So: count spell-help invocations while a spell list renders and while options are clicked
// through, with no peek opened. Expected 0. Then open one peek and expect it to fire.
//
// Prerequisites: lein fig:build, lein garden once, lein e2e-server, npm install playwright.
// Run: node test/browser/spell_help_laziness_e2e.js
const fs=require('fs'),path=require('path');const {chromium}=require('playwright');
const { suppressOverlays } = require('./lib/orcbrew-import');
function findChrome(){const b=process.env.PLAYWRIGHT_BROWSERS_PATH||'/opt/pw-browsers';try{const d=fs.readdirSync(b).filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).sort().pop();if(d){const p=path.join(b,d,'chrome-linux','chrome');if(fs.existsSync(p))return p;}}catch(_){}return undefined;}

const COUNT_SPELL_HELP = `
window.__helpCalls = 0;
(function arm(){
  try {
    var o = window.orcpub && window.orcpub.dnd && window.orcpub.dnd.e5 && window.orcpub.dnd.e5.options;
    if (!o || typeof o.spell_help !== 'function') return setTimeout(arm, 5);
    var f = o.spell_help;
    o.spell_help = function(){ window.__helpCalls++; return f.apply(this, arguments); };
  } catch(e) { setTimeout(arm, 5); }
})();
`;
const results=[];
const check=(name,ok,detail='')=>{results.push(ok);console.log(`${ok?'PASS':'FAIL'}  ${name}${detail?'  — '+detail:''}`);};

(async()=>{
  const browser=await chromium.launch({executablePath:findChrome()});
  const ctx=await browser.newContext(); await suppressOverlays(ctx);
  const page=await ctx.newPage(); await page.setViewportSize({width:1500,height:1100});
  await page.addInitScript(COUNT_SPELL_HELP);
  await page.goto('http://localhost:8890/pages/dnd/5e/character-builder',{waitUntil:'load',timeout:600000});
  await page.waitForFunction(()=>document.body.innerText.includes('CLICK HERE TO ADD A RACE'),null,{timeout:600000,polling:250});
  await page.waitForTimeout(2500);
  const calls=()=>page.evaluate(()=>window.__helpCalls);
  const click=async t=>{await page.locator(`text="${t}"`).first().click({timeout:20000});};

  check('template build does not build any spell peek', (await calls()) === 0, `calls=${await calls()}`);

  await click('Class / Level'); await page.waitForTimeout(1000);
  await page.locator('select').nth(0).selectOption({label:'Wizard'}); await page.waitForTimeout(1500);
  await click('Spells'); await page.waitForTimeout(2500);
  const afterList = await calls();
  const toggles = await page.locator('text="show info"').count();
  check('rendering a full spell list builds no peeks', afterList === 0, `${toggles} spells listed, calls=${afterList}`);

  // click through options quickly - the case that would re-force on every render
  for (let i=0;i<6;i++){ try{ await page.locator('text="show info"').nth(i%3).hover(); }catch(e){} }
  await page.mouse.wheel(0, 1200); await page.waitForTimeout(400);
  await page.mouse.wheel(0, -1200); await page.waitForTimeout(400);
  const afterChurn = await calls();
  check('hovering/scrolling the list builds no peeks', afterChurn === 0, `calls=${afterChurn}`);

  if (toggles) { await page.locator('text="show info"').first().click({timeout:15000}); await page.waitForTimeout(1200); }
  const afterOpen = await calls();
  check('opening one peek builds exactly that peek', afterOpen >= 1, `calls=${afterOpen}`);
  const shown = await page.evaluate(()=>/School/.test(document.body.innerText));
  check('the opened peek actually shows content', shown);

  await page.screenshot({path:'dev-scratch/spell-peek-styled.png'});
  await browser.close();
  process.exit(results.every(Boolean)?0:1);
})().catch(e=>{console.error('FAILED',e.message);process.exit(1);});
