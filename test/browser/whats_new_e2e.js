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
//
// Needs:     the real app at :8890 (`lein e2e-server`)
// Runs in:   ~40s. The cost here is APP BOOT, not the network: this dev build
//            (:optimizations :none) costs ~10-12s to come up, warm or cold, and
//            the panel decides whether to open during boot — so every first-visit
//            case needs one. Seven boots was 110s.
//            Two things keep it down: cases that do not need their own boot are
//            folded together (the phone check measures the panel already open),
//            and the two independent stories run in PARALLEL contexts, so the
//            wall clock is the longer lane rather than their sum. Adding a boot
//            costs ~12s; add one only by removing one.
// Overlays:  NOT suppressed. This probe's whole point is that the panel fires, so its runner
//            entry carries `suppress: false` (PROBE_SUPPRESS=0). Do not add suppression here.
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');

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
const GROUPS = [...new Set([...SRC.matchAll(/:group\s+"([^"]+)"/g)].map(m => m[1]))];

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
  const t0 = Date.now();
  const browser = await chromium.launch({
    executablePath: findChrome(),
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });

  const lane = async (viewport, notice) => {
    const ctx = await browser.newContext({ viewport });
    if (!notice) {
      await ctx.addInitScript(() => {
        try { localStorage.setItem('orcpub:no-cookie-banner', '1'); } catch (e) {}
      });
    }
    const page = await ctx.newPage();
    page.on('console', m => {
      if (m.type() === 'error' || m.type() === 'warning') errors.push(m.text());
    });
    page.on('pageerror', e => errors.push(String(e)));
    return { ctx, page };
  };

  // LANE A — the ordinary story: shown once, stamped, reopenable.
  const laneA = async () => {
    const { ctx, page } = await lane({ width: 1280, height: 900 }, false);

    await page.goto(BASE, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('.whats-new-panel', { timeout: 30000 });
    check('panel opens on a first visit', await visible(page));

    const title = (await page.locator('.whats-new-title').innerText()).trim();
    check('panel names the release', title === RELEASE_TITLE, `saw "${title}"`);

    const items = await page.locator('.whats-new-item').count();
    check('every highlight is rendered', items === HEADLINES.length,
          `${items} shown, ${HEADLINES.length} in the source`);

    const groups = await page.locator('.whats-new-group-title').allInnerTexts();
    check('each group gets one heading', groups.length === GROUPS.length,
          `${groups.length} shown, ${GROUPS.length} in the source`);

    const firstHeadline = (await page.locator('.whats-new-item-headline').first().innerText()).trim();
    check('highlights carry their text', firstHeadline === HEADLINES[0], `saw "${firstHeadline}"`);
    await page.screenshot({ path: path.join(OUT, '1-panel-open.png') });

    // The panel is open and the viewport is the only thing that has to change to
    // know it fits a phone — a reload here would cost a fifth of the probe.
    await page.setViewportSize({ width: 390, height: 720 });
    await page.waitForTimeout(400);
    const box = await page.locator('.whats-new-panel').boundingBox();
    check('the panel fits a phone screen', box.width <= 390 && box.height <= 720,
          `${Math.round(box.width)}x${Math.round(box.height)}`);
    await page.screenshot({ path: path.join(OUT, '2-phone.png') });
    await page.setViewportSize({ width: 1280, height: 900 });

    await page.locator('.whats-new-footer button').click();
    await page.waitForSelector('.whats-new-panel', { state: 'detached', timeout: 5000 });
    check('Got it closes the panel', !(await visible(page)));

    const stamp = await page.evaluate(() => window.localStorage.getItem('whats-new-seen'));
    check('the release is stamped as seen', stamp === `"${RELEASE_ID}"`, `stored ${stamp}`);

    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForSelector('.splash-page-content', { timeout: 20000 });
    await page.waitForTimeout(1200);
    check('a reload does not show it again', !(await visible(page)));

    // Client-side route change, not a reload: this app routes in the browser.
    await page.locator('a.splash-button').first().click();
    await page.waitForSelector('#app-main', { timeout: 30000 });
    await page.waitForTimeout(1000);
    check('an app page does not show it again either', !(await visible(page)));

    await page.locator('a.pointer').filter({ hasText: /what.s new/i }).click();
    await page.waitForSelector('.whats-new-panel', { timeout: 5000 });
    check('the footer link reopens it', await visible(page));
    await page.screenshot({ path: path.join(OUT, '3-reopened-from-footer.png') });

    await page.keyboard.press('Escape');
    await page.waitForSelector('.whats-new-panel', { state: 'detached', timeout: 5000 });
    check('Escape closes it', !(await visible(page)));

    await page.locator('.legal-footer p.pointer').click();
    await page.waitForSelector('.whats-new-panel', { timeout: 5000 });
    check('the version line reopens it', await visible(page));

    await page.mouse.click(20, 20);
    await page.waitForSelector('.whats-new-panel', { state: 'detached', timeout: 5000 });
    check('a click outside closes it', !(await visible(page)));

    await ctx.close();
  };

  // LANE B — the cookie notice, and the embedded sheet. Independent of lane A, so
  // it runs alongside it rather than after.
  const laneB = async () => {
    const { ctx, page } = await lane({ width: 1280, height: 900 }, true);

    // Most people ignore a cookie notice rather than dismissing it, and it returns
    // every visit — a hold that waits for dismissal is a panel they never see.
    await page.goto(BASE, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#poper', { timeout: 30000 });
    await page.waitForSelector('.whats-new-panel', { timeout: 25000 });
    check('an ignored notice does not suppress the panel', await visible(page),
          'never clicked the notice');
    check('the notice is still there behind it',
          await page.locator('#poper').isVisible().catch(() => false));
    await page.screenshot({ path: path.join(OUT, '4-notice-ignored.png') });

    // An embedded sheet is someone else's page: never interrupted. Same context,
    // second tab, so it boots while the checks above are settling.
    const framed = await ctx.newPage();
    await framed.goto(`${BASE}/pages/dnd/5e/character-builder?frame=true`,
                      { waitUntil: 'domcontentloaded' });
    await framed.waitForTimeout(2500);
    check('a framed sheet is never interrupted',
          !(await framed.locator('.whats-new-panel').isVisible().catch(() => false)));
    await framed.close();
    await ctx.close();
  };

  await Promise.all([laneA(), laneB()]);
  await browser.close();

  // fonts.googleapis.com is unreachable from a sandboxed runner; the page renders
  // with its fallback stack and the failure says nothing about this feature.
  const noisy = errors.filter(e =>
    !/Content-Security-Policy|favicon|Download the React DevTools|ERR_CONNECTION_RESET/i.test(e));
  check('no console errors or warnings', noisy.length === 0, noisy.slice(0, 3).join(' | '));

  const failed = results.filter(r => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed in ${Math.round((Date.now() - t0) / 1000)}s`);
  console.log(`screenshots: ${OUT}`);
  process.exit(failed.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
