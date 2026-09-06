const fs=require('fs'),path=require('path');
const { chromium } = require('playwright');
const { importPack, suppressCookieBanner } = require('./test/browser/lib/orcbrew-import');
function findChrome(){const b='/opt/pw-browsers';const d=fs.readdirSync(b).filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).sort().pop();return path.join(b,d,'chrome-linux','chrome');}
const OUT='/tmp/claude-0/-home-user-orcpub/7ad111e3-8c64-5089-bd73-39ac56dc21c4/scratchpad/shots';
(async () => {
  const b = await chromium.launch({ executablePath: findChrome() });
  const ctx = await b.newContext({ viewport: { width: 1500, height: 1000 } });
  await suppressCookieBanner(ctx);
  const p = await ctx.newPage();
  await p.goto('http://localhost:8890/dnd/5e/my-content',{waitUntil:'networkidle',timeout:120000});
  await p.waitForTimeout(2500);
  await importPack(p, process.argv[2]);
  await p.goto('http://localhost:8890/pages/dnd/5e/character-builder',{waitUntil:'load',timeout:900000});
  await p.waitForTimeout(14000);
  await p.locator('text="Equipment"').first().click({timeout:30000});
  await p.waitForTimeout(3000);
  await p.screenshot({ path: `${OUT}/1-equipment-picker.png` });

  // search
  const s = p.locator('input.opt-menu-search').first();
  await s.fill('long');
  await p.waitForTimeout(1500);
  await p.screenshot({ path: `${OUT}/2-search-narrowed.png` });

  // the truncation notice + show all
  await s.fill('');
  await p.waitForTimeout(1200);
  const notice = p.locator('.opt-menu-empty', { hasText: /Showing/ }).first();
  if (await notice.count()) {
    await notice.scrollIntoViewIfNeeded();
    await p.waitForTimeout(600);
    await p.screenshot({ path: `${OUT}/3-cap-and-show-all.png` });
  }
  // A-Z layout, the browse mode that renders one letter
  const azToggle = p.locator('.opt-menu-layout-btn, [class*="layout"]').first();
  if (await azToggle.count()) { try { await azToggle.click({timeout:5000}); await p.waitForTimeout(1200);
    await p.screenshot({ path: `${OUT}/4-layout-toggle.png` }); } catch(e){} }
  console.log('shots written');
  await b.close();
})().catch(e=>{console.error('FAILED',e);process.exit(1);});
