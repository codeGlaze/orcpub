const fs=require('fs'),path=require('path');
const { chromium } = require('playwright');
const { importPack, suppressCookieBanner } = require('./test/browser/lib/orcbrew-import');
function findChrome(){const b='/opt/pw-browsers';const d=fs.readdirSync(b).filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).sort().pop();return path.join(b,d,'chrome-linux','chrome');}
const OUT='/tmp/claude-0/-home-user-orcpub/7ad111e3-8c64-5089-bd73-39ac56dc21c4/scratchpad/shots';
async function run(browser, label, viewport, pack) {
  const ctx = await browser.newContext({ viewport });
  await suppressCookieBanner(ctx);
  const p = await ctx.newPage();
  await p.goto('http://localhost:8890/dnd/5e/my-content',{waitUntil:'networkidle',timeout:120000});
  await p.waitForTimeout(2500);
  await importPack(p, pack);
  await p.goto('http://localhost:8890/pages/dnd/5e/character-builder',{waitUntil:'load',timeout:900000});
  await p.waitForTimeout(14000);
  await p.locator('text="Equipment"').first().click({timeout:30000});
  await p.waitForTimeout(2500);
  await p.screenshot({ path: `${OUT}/${label}-1-closed.png` });
  const nodes = await p.evaluate(() => document.querySelectorAll('*').length);
  const height = await p.evaluate(() => document.body.scrollHeight);
  // open the first picker
  const btn = p.locator('.inv-picker-btn').first();
  await btn.click({ timeout: 20000 });
  await p.waitForTimeout(1000);
  await p.screenshot({ path: `${OUT}/${label}-2-open.png` });
  // search inside it
  await p.locator('.inv-picker-search').first().fill('long');
  await p.waitForTimeout(900);
  await p.screenshot({ path: `${OUT}/${label}-3-search.png` });
  const rows = await p.locator('.inv-picker-row').count();
  console.log(`${label}: DOM ${nodes} nodes, page ${height}px, ${rows} rows shown when searching`);
  await ctx.close();
}
(async () => {
  const b = await chromium.launch({ executablePath: findChrome() });
  await run(b, 'desktop', { width: 1500, height: 1000 }, process.argv[2]);
  await run(b, 'mobile',  { width: 390,  height: 844  }, process.argv[2]);
  console.log('done');
  await b.close();
})().catch(e=>{console.error('FAILED',e);process.exit(1);});
