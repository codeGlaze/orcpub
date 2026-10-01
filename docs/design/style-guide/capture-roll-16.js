// Roll buttons at 16px (round19.html): the builder's ability row and skills on 390px and 320px
// phones and on desktop, white and dark text, and the sheet's skills and saves. Reports each button's size.
// Needs the app on :8890 (prod build) from feature/style-guide. Run from this folder:
//   NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt node capture-roll-16.js
const { chromium, devices } = require('playwright');
const BASE = 'http://localhost:8890';
async function open(b, context, darkText, path) {
  const ctx = await b.newContext({ ...context, deviceScaleFactor: 2 });
  await ctx.addInitScript(dk => { try { localStorage.setItem('user', `{:theme "dark-theme" :dark-button-text? ${dk}}`); } catch (_) {} }, darkText);
  await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
  const p = await ctx.newPage();
  await p.goto(BASE + path, { waitUntil: 'networkidle' });
  const c = p.locator('#cookie-btn');
  await c.waitFor({ timeout: 5000 }).then(() => c.click()).catch(() => {});
  await c.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
  await p.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
  await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(300);
  return { ctx, p };
}
async function report(p, label) {
  const info = await p.evaluate(() => {
    const bs = [...document.querySelectorAll('.roll-button')].filter(b => b.offsetParent);
    const over = bs.filter(b => b.scrollWidth > b.clientWidth + 1).length;
    const hs = [...new Set(bs.map(b => Math.round(b.getBoundingClientRect().height)))];
    return `${bs.length} buttons, ${getComputedStyle(bs[0]).fontSize}, heights ${hs.join('/')}, labels overflowing ${over}`;
  });
  console.log(label, info);
}
async function shoot(p, name, selector, above, h) {
  const el = p.locator(selector).first();
  await p.mouse.move(1, 1);
  await el.scrollIntoViewIfNeeded();
  await p.evaluate(() => scrollBy(0, -120)); await p.waitForTimeout(200);
  const r = await el.boundingBox();
  await p.screenshot({ path: `round19/${name}.png`, clip: { x: 0, y: Math.max(0, r.y - above), width: p.viewportSize().width, height: h } });
}
(async () => {
  const fs = require('fs'); const d = fs.readdirSync('/opt/pw-browsers').filter(x => x.startsWith('chromium-') && !x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  for (const [dev, ctxOpts] of [['390', devices['iPhone 13']], ['320', devices['iPhone SE']]]) {
    for (const dark of [false, true]) {
      const { ctx, p } = await open(b, ctxOpts, dark, '/pages/dnd/5e/character-builder');
      await p.getByText(/^details$/i).first().click(); await p.waitForTimeout(400);
      await report(p, `phone ${dev} ${dark ? 'dark' : 'white'}`);
      await shoot(p, `phone-${dev}-${dark ? 'dark' : 'white'}`, '.roll-button', 120, 560);
      await ctx.close();
    }
  }
  {
    const { ctx, p } = await open(b, { viewport: { width: 1280, height: 900 } }, false, '/pages/dnd/5e/character-builder');
    await report(p, 'desktop');
    await shoot(p, 'desktop', '.roll-button', 90, 420);
    await ctx.close();
  }
  {
    // The sheet's skills and saving throws: wider buttons, same label size.
    const { ctx, p } = await open(b, devices['iPhone 13'], false, '/pages/dnd/5e/character-builder');
    await p.getByText(/^details$/i).first().click(); await p.waitForTimeout(400);
    await shoot(p, 'phone-390-skills', 'text=Acrobatics', 60, 520);
    await ctx.close();
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
