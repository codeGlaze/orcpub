// Browser-driven e2e for the sticky page header.
//
// The header used to be rendered twice -- a fixed copy above an inline one, the
// fixed one shown and hidden by a scroll listener -- so every control in it
// existed twice in the DOM. It is now one element with position: sticky, and an
// IntersectionObserver on a sentinel above it adds .stuck once it reaches the
// top. This checks, in a desktop and a phone viewport:
//
//   * the header exists exactly once
//   * it is not stuck at rest, and is stuck once scrolled past
//   * it stays on screen while scrolled
//   * the page does not scroll sideways (the old fixed header is why .app
//     clipped horizontal overflow at all)
//
// Prerequisites:
//   lein fig:build && lein garden once && lein e2e-server
// Run:  node test/browser/sticky_header_e2e.js
//
// Needs:     the real app at :8890 (`lein e2e-server`)
// Runs in:   ~130-190s. It runs the whole pass twice, desktop and phone.
// Overlays:  suppressed by default -- the runner injects lib/suppress-overlays-preload.js, so
//            the cookie notice and What's New panel never intercept clicks. Hand-runs get no
//            preload, which is why this file also calls suppressOverlays itself.
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium, devices } = require('playwright');

const BASE = process.env.ORCPUB_E2E_URL || 'http://localhost:8890';
const OUT = process.env.ORCPUB_E2E_OUT || fs.mkdtempSync(path.join(os.tmpdir(), 'sticky-header-'));

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

// A real device descriptor for the phone, not a narrow viewport. The app picks
// its mobile layout off the USER AGENT (user-agent/device-type, a Closure sniff),
// so a desktop UA at 390px gets the desktop layout crammed into a phone width --
// two builder columns side by side, the summary overflowing -- which is not
// what any phone shows and is not what this is meant to check.
const VIEWPORTS = [
  { name: 'desktop', context: { viewport: { width: 1500, height: 1000 } } },
  { name: 'phone', context: { ...devices['iPhone 13'] } },
];

const PAGES = [
  ['builder', '/pages/dnd/5e/character-builder'],
  ['spells', '/pages/dnd/5e/spells'],
];

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  try {
    for (const vp of VIEWPORTS) {
      const ctx = await browser.newContext(vp.context);
      const page = await ctx.newPage();
      const errors = [];
      page.on('console', m => {
        if (m.type() === 'error' && !/ERR_CONNECTION_RESET/.test(m.text())) errors.push(m.text());
      });
      page.on('pageerror', e => errors.push('pageerror: ' + e.message));

      for (const [label, url] of PAGES) {
        await page.goto(BASE + url, { waitUntil: 'networkidle' });
        await page.waitForTimeout(2500);
        await page.getByText('Got it!').click().catch(() => {});
        await page.waitForTimeout(400);

        const headers = await page.locator('.sticky-header').count();
        check(`${vp.name} ${label}: one header, not two`, headers === 1, `${headers} found`);

        const buttons = await page.getByText(/^export$/i).count();
        check(`${vp.name} ${label}: header buttons are not duplicated`, buttons <= 1,
              `${buttons} Export buttons`);

        check(`${vp.name} ${label}: not stuck at rest`,
              await page.locator('.sticky-header.stuck').count() === 0);
        await page.screenshot({ path: path.join(OUT, `${vp.name}-${label}-top.png`) });

        await page.evaluate(() => window.scrollTo(0, 1200));
        await page.waitForTimeout(700);
        check(`${vp.name} ${label}: stuck once scrolled past`,
              await page.locator('.sticky-header.stuck').count() === 1);

        // Stuck means still on screen, not merely wearing the class.
        const top = await page.locator('.sticky-header').boundingBox();
        check(`${vp.name} ${label}: header stays on screen`, top && top.y < 5 && top.y > -5,
              `y=${top && Math.round(top.y)}`);
        await page.screenshot({ path: path.join(OUT, `${vp.name}-${label}-scrolled.png`) });

        const overflows = await page.evaluate(
          () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1);
        check(`${vp.name} ${label}: no sideways scroll`, !overflows);

        await page.evaluate(() => window.scrollTo(0, 0));
        await page.waitForTimeout(600);
        check(`${vp.name} ${label}: unsticks on the way back up`,
              await page.locator('.sticky-header.stuck').count() === 0);
      }

      check(`${vp.name}: no console errors`, errors.length === 0, errors.join(' | '));
      await ctx.close();
    }
  } catch (e) {
    check('run completed', false, e.message);
  } finally {
    console.log('Screenshots in ' + OUT);
    await browser.close();
  }
  process.exit(results.every(r => r.ok) ? 0 : 1);
})();
