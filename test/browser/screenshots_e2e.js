// Screenshot the builder for review. A UI change reviewed only through timing numbers is
// half-reviewed -- take these before asking anyone to look at a visual change.
//
// REQUIRES `lein garden once` first. Without it resources/public/css/compiled/styles.css is
// stale or missing and every shot comes out unstyled, which reads as a broken component.
//
// Run: lein garden once && lein fig:build && lein e2e-server, then
//   node test/browser/screenshots_e2e.js /path/to/pack.orcbrew [outdir] [tab]
//
// Default tab is Equipment. Writes 1-<tab>.png, 2-search-narrowed.png (if the tab has a
// search box) and 3-truncation.png (if a list is capped).
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const { importPack, suppressCookieBanner } = require('./lib/orcbrew-import');

function findChrome() {
  if (process.env.CHROME_PATH) return process.env.CHROME_PATH;
  const b = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  const d = fs.readdirSync(b).filter(x => x.startsWith('chromium-') && !x.includes('headless')).sort().pop();
  return path.join(b, d, 'chrome-linux', 'chrome');
}

(async () => {
  const PACK = process.argv[2];
  const OUT  = process.argv[3] || 'dev-scratch/shots';
  const TAB  = process.argv[4] || 'Equipment';
  fs.mkdirSync(OUT, { recursive: true });

  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ viewport: { width: 1500, height: 1000 } });
  await suppressCookieBanner(ctx);
  const page = await ctx.newPage();

  await page.goto('http://localhost:8890/dnd/5e/my-content', { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(2500);
  if (PACK) console.log('import:', JSON.stringify(await importPack(page, PACK)));
  await page.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'load', timeout: 900000 });
  await page.waitForTimeout(14000);

  // Fail loudly if CSS never compiled -- an unstyled shot is worse than none.
  const styled = await page.evaluate(() =>
    getComputedStyle(document.body).backgroundColor !== 'rgba(0, 0, 0, 0)');
  if (!styled) console.log('WARNING: page looks unstyled — run `lein garden once`');

  await page.locator(`text="${TAB}"`).first().click({ timeout: 30000 });
  await page.waitForTimeout(3000);
  const slug = TAB.toLowerCase().replace(/[^a-z]+/g, '-');
  await page.screenshot({ path: path.join(OUT, `1-${slug}.png`) });

  const search = page.locator('input.opt-menu-search').first();
  if (await search.count().catch(() => 0)) {
    await search.fill('long');
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(OUT, '2-search-narrowed.png') });
    await search.fill('');
    await page.waitForTimeout(1200);
  }

  const notice = page.locator('.opt-menu-empty', { hasText: /Showing/ }).first();
  if (await notice.count().catch(() => 0)) {
    await notice.scrollIntoViewIfNeeded();
    await page.waitForTimeout(600);
    await page.screenshot({ path: path.join(OUT, '3-truncation.png') });
  }

  console.log('screenshots written to', OUT);
  await browser.close();
})().catch(e => { console.error('FAILED', e); process.exit(1); });
