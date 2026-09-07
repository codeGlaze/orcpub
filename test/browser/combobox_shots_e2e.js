// Screenshot the Equipment filter-and-pick combobox open, filtered and on mobile.
//
// The popover lives in the browser's top layer but is still real DOM, so unlike a
// <datalist>'s suggestion list it appears in a page screenshot. That is the point of
// this probe -- the datalist dropdown cannot be reviewed visually at all.
//
// REQUIRES `lein garden once` && `lein fig:build` && `lein e2e-server`.
// Run: node test/browser/combobox_shots_e2e.js /path/to/pack.orcbrew [outdir]
const fs = require('fs'), path = require('path');
const { chromium, devices } = require('playwright');
const { importPack, suppressOverlays } = require('./lib/orcbrew-import');

function findChrome() {
  if (process.env.CHROME_PATH) return process.env.CHROME_PATH;
  const b = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  const d = fs.readdirSync(b).filter(x => x.startsWith('chromium-') && !x.includes('headless')).sort().pop();
  return path.join(b, d, 'chrome-linux', 'chrome');
}

// device-type is User-Agent based, so the mobile builder only appears under a mobile UA.
// It shows one section at a time and steps with Next -- there is no tab row to click.
async function toEquipmentMobile(page) {
  for (let i = 0; i < 8; i++) {
    if (await page.locator('input.inv-combo-input').count()) return true;
    const next = page.locator('text="Next"').first();
    if (!(await next.count())) return false;
    await next.click({ timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(1800);
  }
  return await page.locator('input.inv-combo-input').count() > 0;
}

// Prove the anchored geometry rather than eyeballing a screenshot: the dropdown should
// line up with its input's left edge and match its width.
async function geometry(page, label) {
  const g = await page.evaluate(() => {
    const i = document.querySelector('input.inv-combo-input');
    const p = document.querySelector('.inv-combo-pop:popover-open');
    if (!i || !p) return null;
    const a = i.getBoundingClientRect(), b = p.getBoundingClientRect();
    return { dx: Math.round(b.left - a.left), dw: Math.round(b.width - a.width),
             below: Math.round(b.top - a.bottom), offscreen: Math.round(b.bottom - innerHeight) };
  });
  if (!g) { console.log(`${label}: popover not open`); return null; }
  console.log(`${label.padEnd(22)} leftDelta=${g.dx}px widthDelta=${g.dw}px gapBelowInput=${g.below}px overflowsViewportBy=${g.offscreen > 0 ? g.offscreen + 'px' : 'no'}`);
  return g;
}

async function stats(page, label) {
  const s = await page.evaluate(() => ({
    nodes: document.getElementsByTagName('*').length,
    height: document.documentElement.scrollHeight,
    open: document.querySelectorAll('.inv-combo-pop:popover-open').length,
    rows: document.querySelectorAll('.inv-combo-row').length,
  }));
  console.log(`${label.padEnd(22)} nodes=${s.nodes}  page=${s.height}px  popoverOpen=${s.open}  rows=${s.rows}`);
  return s;
}

(async () => {
  const PACK = process.argv[2];
  const OUT  = process.argv[3] || 'dev-scratch/combo';
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ executablePath: findChrome() });

  // ---------- desktop ----------
  const ctx = await browser.newContext({ viewport: { width: 1500, height: 1000 } });
  await suppressOverlays(ctx);
  const page = await ctx.newPage();
  await page.goto('http://localhost:8890/dnd/5e/my-content', { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(2500);
  if (PACK) console.log('import:', JSON.stringify(await importPack(page, PACK)));
  await page.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'load', timeout: 900000 });
  await page.waitForTimeout(14000);
  if (!await page.evaluate(() => getComputedStyle(document.body).backgroundColor !== 'rgba(0, 0, 0, 0)'))
    console.log('WARNING: page looks unstyled -- run `lein garden once`');

  await page.locator('text="Equipment"').first().click({ timeout: 30000 });
  await page.waitForTimeout(3000);
  const input = page.locator('input.inv-combo-input').first();
  await input.scrollIntoViewIfNeeded();
  await page.waitForTimeout(500);
  await stats(page, 'desktop closed');
  await page.screenshot({ path: path.join(OUT, '1-closed.png') });

  await input.click();
  await page.waitForTimeout(900);
  const hintOpen = await page.evaluate(() => {
    const h = document.querySelector('.inv-combo-pop:popover-open .inv-combo-hint');
    return h && h.firstElementChild.textContent;
  });
  console.log(`hint line unfiltered: "${hintOpen}"`);
  await stats(page, 'desktop open');
  await geometry(page, 'desktop geom');
  await page.screenshot({ path: path.join(OUT, '2-open.png') });

  // Derive the filter term from a row that is actually in THIS section -- a hardcoded
  // term matched nothing here and made a working filter look broken.
  const term = (await page.locator('.inv-combo-row').first().textContent()).trim().slice(0, 4);
  await input.fill(term);
  await page.waitForTimeout(900);
  await stats(page, `desktop filtered "${term}"`);
  const hits = await page.evaluate(() => {
    const h = [...document.querySelectorAll('.inv-combo-hit')];
    return { n: h.length, sample: h[0] && h[0].textContent };
  });
  console.log(`match highlighting: ${hits.n} hit spans, first "${hits.sample}"`);
  const hint = await page.evaluate(() => {
    const h = document.querySelector('.inv-combo-pop:popover-open .inv-combo-hint');
    if (!h) return null;
    return { count: h.firstElementChild && h.firstElementChild.textContent,
             keys: h.querySelector('.inv-combo-keys') && h.querySelector('.inv-combo-keys').textContent };
  });
  console.log(hint ? `hint line: "${hint.count}" | "${hint.keys}"` : 'hint line: MISSING');
  await page.screenshot({ path: path.join(OUT, '3-filtered.png') });

  // A broad term on the biggest section, so match highlighting shows across many rows
  // rather than the single hit a narrow term produces.
  const big = await page.evaluate(() => {
    const ins = [...document.querySelectorAll('input.inv-combo-input')];
    const n = i => { const m = (i.placeholder || '').match(/\((\d+)\)/); return m ? +m[1] : 0; };
    let b = 0; ins.forEach((i, k) => { if (n(i) > n(ins[b])) b = k; });
    return b;
  });
  const bigInput = page.locator('input.inv-combo-input').nth(big);
  await bigInput.scrollIntoViewIfNeeded();
  await bigInput.click();
  await page.waitForTimeout(500);
  await bigInput.fill('+1');
  await page.waitForTimeout(900);
  // Hover a row so the accent bar and hover tint appear in the shot.
  await page.locator('.inv-combo-row').nth(2).hover().catch(() => {});
  await page.waitForTimeout(400);
  const many = await page.evaluate(() => document.querySelectorAll('.inv-combo-hit').length);
  console.log(`broad filter "+1" on largest section: ${many} highlighted rows`);
  await page.screenshot({ path: path.join(OUT, '10-highlighting.png') });
  await bigInput.fill('');
  await page.waitForTimeout(500);
  await page.keyboard.press('Escape');
  await page.waitForTimeout(400);

  // Light dismiss: click the page background. No handler of ours runs -- the browser closes it.
  await page.mouse.click(1400, 200);
  await page.waitForTimeout(700);
  const afterDismiss = await stats(page, 'after light-dismiss');
  console.log(afterDismiss.open === 0 ? 'light dismiss: OK' : 'light dismiss: FAILED (still open)');

  // Pick a row and confirm it lands in the inventory.
  await input.click();
  await page.waitForTimeout(300);
  await input.fill(term);
  await page.waitForTimeout(800);
  const before = await page.evaluate(() => document.querySelectorAll('.inv-combo-row').length);
  const row = page.locator('.inv-combo-row').first();
  const picked = await row.textContent();
  await row.click();
  await page.waitForTimeout(1500);
  const gone = await page.evaluate(() => document.querySelectorAll('.inv-combo-pop:popover-open').length);
  console.log(`pick "${picked.trim()}" from ${before} rows -> popover open after pick = ${gone}`);
  await page.screenshot({ path: path.join(OUT, '4-after-pick.png') });
  await ctx.close();

  // ---------- mobile ----------
  const mctx = await browser.newContext({ ...devices['Pixel 5'] });
  await suppressOverlays(mctx);
  const mpage = await mctx.newPage();
  await mpage.goto('http://localhost:8890/dnd/5e/my-content', { waitUntil: 'networkidle', timeout: 120000 });
  await mpage.waitForTimeout(2500);
  if (PACK) await importPack(mpage, PACK);
  await mpage.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'load', timeout: 900000 });
  await mpage.waitForTimeout(14000);
  if (!await toEquipmentMobile(mpage)) { console.log('mobile: never reached Equipment'); }
  else {
    const minput = mpage.locator('input.inv-combo-input').first();
    await minput.scrollIntoViewIfNeeded();
    await mpage.waitForTimeout(500);
    await stats(mpage, 'mobile closed');
    await mpage.screenshot({ path: path.join(OUT, '5-mobile-closed.png') });
    await minput.click();
    await mpage.waitForTimeout(900);
    await stats(mpage, 'mobile open');
    await geometry(mpage, 'mobile geom');
    await mpage.screenshot({ path: path.join(OUT, '6-mobile-open.png') });
    const mterm = (await mpage.locator('.inv-combo-row').first().textContent()).trim().slice(0, 4);
    await minput.fill(mterm);
    await mpage.waitForTimeout(900);
    await stats(mpage, 'mobile filtered');
    await mpage.screenshot({ path: path.join(OUT, '7-mobile-filtered.png') });
  }
  await browser.close();
  console.log('screenshots written to', OUT);
})().catch(e => { console.error('FAILED', e); process.exit(1); });
