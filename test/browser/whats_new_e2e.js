// Browser-driven e2e for the What's New panel.
//
// Drives the REAL app against `lein e2e-server` on :8890. The release content and
// the id that gates the panel are read out of src/cljc/orcpub/whats_new.cljc, so
// this stays honest when the highlights change.
//
// WHAT IT PINS: the panel opens once per browser, the stamp is what suppresses it
// (a reload with the stamp stays quiet, a fresh profile shows it again), and both
// footer entry points reopen it afterwards.
//
// Prerequisites:
//   lein fig:build
//   lein garden once
//   lein e2e-server        (port 8890 free)
// Run:  node test/browser/whats_new_e2e.js
// Exit code 0 = all checks passed.
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');
const { suppressCookieBanner } = require('./lib/orcbrew-import');

const BASE = process.env.ORCPUB_E2E_URL || 'http://localhost:8890';
const OUT = process.env.ORCPUB_E2E_OUT || fs.mkdtempSync(path.join(os.tmpdir(), 'whats-new-'));

function findChrome() {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  try {
    const dir = fs.readdirSync(base)
      .filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
    if (dir) {
      const p = path.join(base, dir, 'chrome-linux', 'chrome');
      if (fs.existsSync(p)) return p;
    }
  } catch (_) {}
  return undefined;
}

const results = [];
const check = (name, ok, detail = '') => {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`);
};

// The release the app will show, read from the source of truth rather than copied.
const SRC = fs.readFileSync('src/cljc/orcpub/whats_new.cljc', 'utf8');
const RELEASE_ID = (SRC.match(/:id\s+"([^"]+)"/) || [])[1];
const RELEASE_TITLE = (SRC.match(/:title\s+"([^"]+)"/) || [])[1];
const HEADLINES = [...SRC.matchAll(/:headline\s+"([^"]+)"/g)].map(m => m[1]);

async function newPage(browser, errors, { cookieBanner = false } = {}) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  // The notice is position-fixed over the footer, so a run that leaves it up
  // cannot click the footer entry points. One case below deliberately keeps it.
  if (!cookieBanner) await suppressCookieBanner(ctx);
  const page = await ctx.newPage();
  page.on('console', m => {
    if (m.type() === 'error' || m.type() === 'warning') errors.push(m.text());
  });
  page.on('pageerror', e => errors.push(String(e)));
  return { ctx, page };
}

const visible = page => page.locator('.whats-new-panel').isVisible().catch(() => false);

(async () => {
  if (!RELEASE_ID || !RELEASE_TITLE || HEADLINES.length === 0) {
    console.error('Could not read the release out of src/cljc/orcpub/whats_new.cljc');
    process.exit(1);
  }
  const errors = [];
  const browser = await chromium.launch({
    executablePath: findChrome(),
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });

  // 1. A browser that has never seen this release gets the panel, unasked.
  let { ctx, page } = await newPage(browser, errors);
  await page.goto(BASE, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.whats-new-panel', { timeout: 20000 });
  check('panel opens on a first visit', await visible(page));

  const title = (await page.locator('.whats-new-title').innerText()).trim();
  check('panel names the release', title === RELEASE_TITLE, `saw "${title}"`);

  const items = await page.locator('.whats-new-item').count();
  check('every highlight is rendered', items === HEADLINES.length,
        `${items} shown, ${HEADLINES.length} in the source`);

  const firstHeadline = (await page.locator('.whats-new-item-headline').first().innerText()).trim();
  check('highlights carry their text', firstHeadline === HEADLINES[0], `saw "${firstHeadline}"`);

  await page.screenshot({ path: path.join(OUT, '1-panel-open.png') });

  // 2. Closing it stamps the release, and the stamp is what keeps it shut.
  await page.locator('.whats-new-footer button').click();
  await page.waitForSelector('.whats-new-panel', { state: 'detached', timeout: 5000 });
  check('Got it closes the panel', !(await visible(page)));

  const stamp = await page.evaluate(() => window.localStorage.getItem('whats-new-seen'));
  check('the release is stamped as seen', stamp === `"${RELEASE_ID}"`, `stored ${stamp}`);

  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.splash-page-content', { timeout: 20000 });
  await page.waitForTimeout(1500);
  check('a reload does not show it again', !(await visible(page)));
  await page.screenshot({ path: path.join(OUT, '2-after-dismiss.png') });

  // 3. On an app page it stays shut, and the footer link brings it back.
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#app-main', { timeout: 30000 });
  await page.waitForTimeout(1500);
  check('an app page does not show it again either', !(await visible(page)));

  await page.locator('a:has-text("What\'s New")').click();
  await page.waitForSelector('.whats-new-panel', { timeout: 5000 });
  check('the footer link reopens it', await visible(page));
  await page.screenshot({ path: path.join(OUT, '3-reopened-from-footer.png') });

  await page.keyboard.press('Escape');
  await page.waitForSelector('.whats-new-panel', { state: 'detached', timeout: 5000 });
  check('Escape closes it', !(await visible(page)));

  // 4. The version line in the legal footer is the second way in.
  await page.locator('.legal-footer p.pointer').click();
  await page.waitForSelector('.whats-new-panel', { timeout: 5000 });
  check('the version line reopens it', await visible(page));

  // Clicking the backdrop is the third way out.
  await page.mouse.click(20, 20);
  await page.waitForSelector('.whats-new-panel', { state: 'detached', timeout: 5000 });
  check('a click outside closes it', !(await visible(page)));

  // An embedded sheet is someone else's page: never interrupted.
  const framed = await ctx.newPage();
  await framed.goto(`${BASE}/pages/dnd/5e/character-builder?frame=true`, { waitUntil: 'domcontentloaded' });
  await framed.waitForTimeout(2500);
  check('a framed sheet is never interrupted',
        !(await framed.locator('.whats-new-panel').isVisible().catch(() => false)));
  await framed.close();
  await ctx.close();

  // 5. A different browser has its own stamp, so it still gets shown once.
  ({ ctx, page } = await newPage(browser, errors));
  await page.goto(BASE, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.whats-new-panel', { timeout: 20000 });
  check('a fresh browser is shown it once', await visible(page));
  await ctx.close();

  // 6. One overlay at a time: a visitor who still has the cookie notice in front
  // of them gets the release panel on their next visit, not stacked on top of it.
  ({ ctx, page } = await newPage(browser, errors, { cookieBanner: true }));
  await page.goto(BASE, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#poper', { timeout: 20000 });
  await page.waitForTimeout(1500);
  check('the panel waits while the cookie notice is up', !(await visible(page)));
  await page.screenshot({ path: path.join(OUT, '4-cookie-notice-first.png') });

  await page.locator('#cookie-btn').click();
  await page.waitForTimeout(500);
  await page.goto(BASE, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.whats-new-panel', { timeout: 20000 });
  check('and gets it on the visit after', await visible(page));
  await ctx.close();

  // 7. A phone-sized viewport: the panel has to fit and scroll, not overflow.
  const phone = await browser.newContext({ viewport: { width: 390, height: 720 } });
  await suppressCookieBanner(phone);
  const phonePage = await phone.newPage();
  await phonePage.goto(BASE, { waitUntil: 'domcontentloaded' });
  await phonePage.waitForSelector('.whats-new-panel', { timeout: 20000 });
  const box = await phonePage.locator('.whats-new-panel').boundingBox();
  check('the panel fits a phone screen', box.width <= 390 && box.height <= 720,
        `${Math.round(box.width)}x${Math.round(box.height)}`);
  await phonePage.screenshot({ path: path.join(OUT, '5-phone.png') });
  await phone.close();

  await browser.close();

  // fonts.googleapis.com is unreachable from a sandboxed runner; the page renders
  // with its fallback stack and the failure says nothing about this feature.
  const noisy = errors.filter(e =>
    !/Content-Security-Policy|favicon|Download the React DevTools|ERR_CONNECTION_RESET/i.test(e));
  check('no console errors or warnings', noisy.length === 0, noisy.slice(0, 3).join(' | '));

  const failed = results.filter(r => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  console.log(`screenshots: ${OUT}`);
  process.exit(failed.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
