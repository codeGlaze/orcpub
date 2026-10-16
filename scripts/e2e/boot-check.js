// Does the app actually start? Run by run.sh before every suite.
//
//   node scripts/e2e/boot-check.js <base-url>
//
// A suite that waits for a page that never mounted times out minutes later and reads like an
// app bug. This loads one page, waits for the app to replace the server's placeholder, and
// exits 1 naming why it did not: scripts the Content Security Policy blocked, a bundle that
// 404s, an error thrown during startup. Judges only what a visitor sees, so it works on a
// production bundle, where the app's own names are compiled away.
const { chromium } = require('playwright');

const BASE = process.argv[2] || 'http://localhost:8890';
const PAGE = '/pages/dnd/5e/character-builder';
const WAIT_MS = 90000;

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.E2E_CHROMIUM || undefined });
  const context = await browser.newContext();
  const page = await context.newPage();
  const problems = [];
  page.on('pageerror', e => problems.push(`error thrown: ${e.message}`));
  page.on('console', m => {
    if (m.type() === 'error' && /Content Security Policy|Refused to/i.test(m.text())) {
      problems.push(`blocked: ${m.text()}`);
    }
  });
  page.on('response', r => {
    if (r.status() >= 400 && /\.(js|css)(\?|$)/.test(r.url())) problems.push(`${r.status()}: ${r.url()}`);
  });
  page.on('requestfailed', r => problems.push(`request failed (${(r.failure() || {}).errorText}): ${r.url()}`));

  const t0 = Date.now();
  await page.goto(BASE + PAGE, { waitUntil: 'domcontentloaded' });
  // The server's placeholder is the spinner (spiral.gif, or a [data-spinner] block); the app
  // replaces #app's contents when it mounts.
  const mounted = await page.waitForFunction(() => {
    const app = document.getElementById('app');
    return app && app.childElementCount > 0
      && !app.querySelector('img[src*="spiral.gif"], [data-spinner]');
  }, null, { timeout: WAIT_MS }).then(() => true, () => false);

  // The app-root error boundary also fills #app, with its fallback page: that is a mount, not a boot.
  const fallback = mounted && await page.waitForTimeout(1500).then(() =>
    page.evaluate(() => /Something went wrong on this page/.test(document.body.innerText)));
  if (mounted && !fallback) {
    console.log(`boot check: the app started in ${Date.now() - t0} ms`);
    await browser.close();
    process.exit(0);
  }
  console.log(fallback
    ? `boot check: THE APP STARTED INTO ITS ERROR PAGE on ${BASE}${PAGE}`
    : `boot check: THE APP DID NOT START within ${WAIT_MS / 1000} s on ${BASE}${PAGE}`);
  const seen = [...new Set(problems)];
  if (seen.length) {
    console.log('  what the browser reported:');
    seen.slice(0, 8).forEach(p => console.log('    ' + p.slice(0, 220)));
    if (seen.some(p => p.startsWith('blocked:'))) {
      console.log('  A development bundle is blocked by the strict policy; run.sh turns it off');
      console.log('  only for suites that declare "Needs: dev bundle".');
    }
  } else {
    console.log('  the browser reported nothing: the page loaded and the app never replaced the placeholder.');
  }
  await browser.close();
  process.exit(1);
})().catch(e => { console.log(`boot check: could not run: ${e.message}`); process.exit(1); });
