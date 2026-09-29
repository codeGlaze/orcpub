const { chromium } = require('playwright');
const { suppressOverlays } = require(require('path').resolve(process.cwd(), 'test/browser/lib/orcbrew-import'));
const OUT = process.argv[2];
function findChrome(){const b=process.env.PLAYWRIGHT_BROWSERS_PATH||'/opt/pw-browsers';
  const d=require('fs').readdirSync(b).filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).sort().pop();
  return require('path').join(b,d,'chrome-linux','chrome');}

// colour tokens per theme, set on the drawer root so each ornament reads them
const BASE = `
.pl-root { --oc: rgba(240,161,0,.38); --of: rgba(255,255,255,.16); }
.pl-root.light-theme { --oc: rgba(51,101,138,.42); --of: rgba(20,30,45,.18); }`;
const corner = v => `align-self:center; width:fit-content; padding:8px 12px;
  background:
   linear-gradient(${v},${v}) top left/7px 1px no-repeat, linear-gradient(${v},${v}) top left/1px 7px no-repeat,
   linear-gradient(${v},${v}) top right/7px 1px no-repeat, linear-gradient(${v},${v}) top right/1px 7px no-repeat,
   linear-gradient(${v},${v}) bottom left/7px 1px no-repeat, linear-gradient(${v},${v}) bottom left/1px 7px no-repeat,
   linear-gradient(${v},${v}) bottom right/7px 1px no-repeat, linear-gradient(${v},${v}) bottom right/1px 7px no-repeat;`;
const V = [
  ['01', 'Nothing', ''],
  ['02', 'Hairline above', `.pl-attribution{border-top:1px solid var(--oc);padding-top:9px}`],
  ['03', 'Tapered rule', `.pl-attribution{position:relative;padding-top:9px}
    .pl-attribution::before{content:'';position:absolute;top:0;left:0;right:0;height:1px;
      background:linear-gradient(90deg,transparent,var(--oc) 22%,var(--oc) 78%,transparent)}`],
  ['04', 'Corner caps', `.pl-attribution{${corner('var(--oc)')}}`],
  ['05', 'Corner caps, faint', `.pl-attribution{${corner('var(--of)')}}`],
  ['06', 'End ticks', `.pl-attribution::before,.pl-attribution::after{content:'';width:1px;height:11px;background:var(--oc)}`],
  ['07', 'Diamond ends', `.pl-attribution::before,.pl-attribution::after{content:'';width:4px;height:4px;background:var(--oc);transform:rotate(45deg)}`],
  ['08', 'Broken rule', `.pl-attribution{flex-wrap:nowrap}
    .pl-attribution::before,.pl-attribution::after{content:'';flex:1;height:1px;min-width:8px}
    .pl-attribution::before{background:linear-gradient(90deg,transparent,var(--oc))}
    .pl-attribution::after{background:linear-gradient(90deg,var(--oc),transparent)}`],
  ['09', 'Rule above and below', `.pl-attribution{border-top:1px solid var(--oc);border-bottom:1px solid var(--oc);padding:9px 0}`],
  ['10', 'Underscore only', `.pl-attribution{border-bottom:1px solid var(--of);padding-bottom:9px}`],
];

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ viewport:{width:1280,height:900}, deviceScaleFactor: 2 });
  await suppressOverlays(ctx);
  const page = await ctx.newPage();
  await page.goto('http://localhost:8890/pages/dnd/5e/character-builder',{waitUntil:'networkidle'});
  await page.waitForSelector('#app');
  const c=page.locator('#cookie-btn'); if(await c.count()) await c.click().catch(()=>{});
  const d=page.locator('.builder-tab',{hasText:/^Description$/}).first();
  if(await d.count()){ await d.click(); await page.waitForTimeout(300); }

  for (const theme of ['dark','light']) {
    if (theme === 'light') {
      if (await page.locator('.pl-drawer').count()) {
        await page.locator('.pl-drawer-close').click(); await page.waitForTimeout(300);
      }
      await page.locator('div.pointer', { hasText: 'Light Theme' }).first().click();
      await page.waitForTimeout(400);
    }
    await page.locator('.pl-launcher').first().click();
    await page.locator('.pl-drawer').waitFor({state:'visible'});
    if (!(await page.locator('.pl-artist').count())) {
      await page.getByRole('button',{name:/randomize/i}).first().click();
    }
    await page.waitForTimeout(800);
    // the canvas column, from under the header to the colour strip
    const col = await page.locator('.pl-canvas-side').boundingBox();
    const clip = { x: col.x, y: col.y, width: col.width, height: 540 };
    for (const [id, , css] of V) {
      await page.evaluate(({base, css}) => {
        let s = document.getElementById('orn-try');
        if (!s) { s = document.createElement('style'); s.id = 'orn-try'; document.head.appendChild(s); }
        s.textContent = base + css;
      }, { base: BASE, css });
      await page.waitForTimeout(120);
      await page.screenshot({ path: `${OUT}/${theme}-${id}.png`, clip });
    }
  }
  console.log('done', V.length * 2, 'shots');
  await browser.close();
})();
