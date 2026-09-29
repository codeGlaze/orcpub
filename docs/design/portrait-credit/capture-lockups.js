const { chromium } = require('playwright');
const { suppressOverlays } = require(require('path').resolve(process.cwd(), 'test/browser/lib/orcbrew-import'));
const OUT = process.argv[2];
function findChrome(){const b=process.env.PLAYWRIGHT_BROWSERS_PATH||'/opt/pw-browsers';
  const d=require('fs').readdirSync(b).filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).sort().pop();
  return require('path').join(b,d,'chrome-linux','chrome');}

const mark = (icon, color, label, url) =>
  `<a class="lk-mark" href="${url}" aria-label="Fusspot on ${label}" style="color:${color}">` +
  `<span class="lk-ico" style="-webkit-mask-image:url(/image/social/${icon}.svg);mask-image:url(/image/social/${icon}.svg)"></span></a>`;
const SITE = mark('site', '#f0a100', 'her site', 'https://fusspot.rip/');
const TW   = mark('twitch', '#9146ff', 'Twitch', 'https://www.twitch.tv/fusspot');
const NAME = `<a class="lk-name" href="https://fusspot.rip/">Fusspot</a>`;
const RULE_L = `<span class="lk-rule l"></span>`, RULE_R = `<span class="lk-rule r"></span>`;

const CSS = `
.pl-root { --lk-rule: rgba(240,161,0,.40); --lk-name: #ebeef4; --lk-dim: #7b8494; }
.pl-root.light-theme { --lk-rule: rgba(51,101,138,.45); --lk-name: #33465c; --lk-dim: #858c96; }
.pl-attribution.lk { display:flex; flex-direction:column; align-items:stretch; gap:7px; margin:9px 0 3px; }
.lk-row { display:flex; align-items:center; justify-content:center; gap:10px; }
.lk-rule { flex:1; height:1px; min-width:10px; }
.lk-rule.l { background:linear-gradient(90deg,transparent,var(--lk-rule)); }
.lk-rule.r { background:linear-gradient(90deg,var(--lk-rule),transparent); }
.lk-name { font:italic 14px/1 'Vollkorn',Georgia,serif; color:var(--lk-name); text-decoration:none; white-space:nowrap; }
.lk-by { font:italic 13px/1 'Vollkorn',Georgia,serif; color:var(--lk-dim); white-space:nowrap; }
.lk-cap { font:600 8.5px/1 'Open Sans',system-ui,sans-serif; letter-spacing:.16em; text-transform:uppercase; color:var(--lk-dim); text-align:center; }
.lk-mark { display:inline-flex; }
.lk-ico { display:block; width:12px; height:12px; background-color:currentColor;
  -webkit-mask-size:contain; mask-size:contain; -webkit-mask-repeat:no-repeat; mask-repeat:no-repeat; }
.lk-marks { display:flex; justify-content:center; gap:14px; }
`;

const L = [
  ['A', 'What #08 looks like today',
   null],   // keep the real DOM, only add the broken rule
  ['B', 'One typeface, one line',
   `<div class="lk-row">${RULE_L}<span class="lk-by">art by</span>${NAME}${SITE}${TW}${RULE_R}</div>`],
  ['C', 'Two-line lockup',
   `<div class="lk-row">${RULE_L}<span class="lk-by">art by</span>${NAME}${RULE_R}</div>
    <div class="lk-marks">${SITE}${TW}</div>`],
  ['D', 'Marks as the rule ends',
   `<div class="lk-cap">Art by</div>
    <div class="lk-row">${RULE_L}${SITE}${NAME}${TW}${RULE_R}</div>`],
  ['E', 'Marks as the rule ends, no label',
   `<div class="lk-row">${RULE_L}${SITE}${NAME}${TW}${RULE_R}</div>`],
  ['F', 'Label above, marks below',
   `<div class="lk-cap">Art by</div>
    <div class="lk-row">${RULE_L}${NAME}${RULE_R}</div>
    <div class="lk-marks">${SITE}${TW}</div>`],
];
const BROKEN08 = `
.pl-root { --oc: rgba(240,161,0,.38); } .pl-root.light-theme { --oc: rgba(51,101,138,.42); }
.pl-attribution:not(.lk){flex-wrap:nowrap}
.pl-attribution:not(.lk)::before,.pl-attribution:not(.lk)::after{content:'';flex:1;height:1px;min-width:8px}
.pl-attribution:not(.lk)::before{background:linear-gradient(90deg,transparent,var(--oc))}
.pl-attribution:not(.lk)::after{background:linear-gradient(90deg,var(--oc),transparent)}`;

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
      await page.locator('.pl-drawer-close').click(); await page.waitForTimeout(300);
      await page.locator('div.pointer', { hasText: 'Light Theme' }).first().click();
      await page.waitForTimeout(400);
    }
    await page.locator('.pl-launcher').first().click();
    await page.locator('.pl-drawer').waitFor({state:'visible'});
    if (!(await page.locator('.pl-artist').count()))
      await page.getByRole('button',{name:/randomize/i}).first().click();
    await page.waitForTimeout(800);
    const original = await page.locator('.pl-attribution').first().evaluate(el => el.innerHTML);
    const col = await page.locator('.pl-canvas-side').boundingBox();
    const clip = { x: col.x, y: col.y, width: col.width, height: 540 };
    for (const [id, , html] of L) {
      await page.evaluate(({css, html, original}) => {
        let s = document.getElementById('lk-try');
        if (!s) { s = document.createElement('style'); s.id = 'lk-try'; document.head.appendChild(s); }
        s.textContent = css;
        const el = document.querySelector('.pl-attribution');
        if (html === null) { el.classList.remove('lk'); el.innerHTML = original; }
        else { el.classList.add('lk'); el.innerHTML = html; }
      }, { css: CSS + BROKEN08, html, original });
      await page.waitForTimeout(150);
      await page.screenshot({ path: `${OUT}/${theme}-${id}.png`, clip });
    }
  }
  console.log('done');
  await browser.close();
})();
