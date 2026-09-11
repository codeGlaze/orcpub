// Browser-driven e2e for the header dropdown menus.
//
// Drives the REAL app against `lein e2e-server` on :8890.
//
// WHAT IT PINS: every item in every header flyout is not just visible but
// HITTABLE. A flyout that renders under the sticky button row looks fine in a
// screenshot and cannot be clicked, so visibility alone is not the check —
// document.elementFromPoint at each item's centre has to come back as that item.
//
// Run at three window heights. My Content is the tall one — eleven rows — and it
// ran off the bottom of a 720-tall window with its last two items unreachable: a
// hover menu closes the moment you move the pointer away to the page scrollbar,
// so anything past the fold is simply gone. Each row is scrolled into view INSIDE
// its menu before the hit test, which is what a user's wheel does over an open
// menu, and the count of rows tested has to match the count in the DOM so a
// skipped row cannot pass as a clean menu.
//
// Prerequisites:
//   lein fig:build
//   lein garden once
//   lein e2e-server        (port 8890 free)
// Run:  node test/browser/header_menus_e2e.js
// Exit code 0 = all checks passed.
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');
const { suppressCookieBanner } = require('./lib/orcbrew-import');

const BASE = process.env.ORCPUB_E2E_URL || 'http://localhost:8890';
const OUT = process.env.ORCPUB_E2E_OUT || fs.mkdtempSync(path.join(os.tmpdir(), 'header-menus-'));

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

// A tall desktop, the commonest laptop, and a short window. The bug only shows
// itself below ~800px of height.
const VIEWPORTS = [
  { width: 1280, height: 900 },
  { width: 1366, height: 720 },
  { width: 1280, height: 620 },
];

const results = [];
const check = (name, ok, detail = '') => {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`);
};

(async () => {
  const browser = await chromium.launch({
    executablePath: findChrome(),
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });
  const ctx = await browser.newContext({ viewport: VIEWPORTS[0] });
  await suppressCookieBanner(ctx);
  // The release panel is a full-screen overlay; stamp it seen so it isn't what
  // covers the menus in this run.
  await ctx.addInitScript(() => {
    try { localStorage.setItem('whats-new-seen', '"summer-patch-2026"'); } catch (e) {}
  });
  const page = await ctx.newPage();

  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#app-main', { timeout: 30000 });
  await page.waitForTimeout(1500);

  async function testMenus(page, label) {
    const tabs = page.locator('.header-tab:has(.header-flyout)');
    const tabCount = await tabs.count();
    check(`${label}: the header has flyout menus to test`, tabCount > 0, `${tabCount} tabs`);

    for (let i = 0; i < tabCount; i++) {
      const tab = tabs.nth(i);
      const name = ((await tab.locator('.title').first().innerText().catch(() => '')) || `tab ${i}`).trim();
      const t0 = Date.now();
      await tab.hover();
      const flyout = tab.locator('.header-flyout');
      await flyout.waitFor({ state: 'visible', timeout: 3000 });
      await page.waitForTimeout(120); // fit-flyout! measures in a frame

      const rows = flyout.locator(':scope > *');
      const rowCount = await rows.count();
      const blocked = [];
      let tested = 0;
      const scrollYBefore = await page.evaluate(() => window.scrollY);
      for (let j = 0; j < rowCount; j++) {
        const row = rows.nth(j);
        // Scroll INSIDE the menu only — what a wheel over an open menu does when the
        // menu can scroll. scrollIntoView would scroll the PAGE instead, which no
        // user can do here (the pointer leaves the menu and it closes), and that
        // made an earlier version of this probe pass against the unfixed build.
        await flyout.evaluate((f, j) => {
          const el = f.children[j];
          if (!el) return;
          f.scrollTop = Math.max(0, el.offsetTop - (f.clientHeight - el.offsetHeight) / 2);
        }, j);
        const box = await row.boundingBox();
        if (!box) { blocked.push(`row ${j} has no box`); continue; }
        const x = Math.round(box.x + box.width / 2);
        const y = Math.round(box.y + box.height / 2);
        const covered = await page.evaluate(([x, y]) => {
          if (y < 0 || y > window.innerHeight) return 'off the bottom of the window';
          const el = document.elementFromPoint(x, y);
          if (!el) return 'nothing at that point';
          return el.closest('.header-flyout') ? null : (el.className || el.tagName);
        }, [x, y]);
        tested++;
        if (covered) {
          const text = ((await row.innerText().catch(() => '')) || `row ${j}`).replace(/\n/g, ' ').trim();
          blocked.push(`${text} (${covered})`);
        }
      }
      const scrolled = (await page.evaluate(() => window.scrollY)) !== scrollYBefore;
      if (scrolled) blocked.push('the PAGE scrolled — the menu was not what moved');
      check(`${label}: every item in "${name}" is reachable (${tested}/${rowCount})`,
            blocked.length === 0 && tested === rowCount,
            blocked.length ? blocked.slice(0, 3).join(' | ') : `${Date.now() - t0} ms`);
    }
  }

  await testMenus(page, `${VIEWPORTS[0].height}px`);
  await page.screenshot({ path: path.join(OUT, '1-menu-open.png') });

  // Scrolled down, the button row is stuck to the top of the viewport — the state
  // the screenshot in the bug report was taken in.
  await page.mouse.wheel(0, 600);
  await page.waitForTimeout(600);
  await page.mouse.wheel(0, -600);
  await page.waitForTimeout(600);
  const lastTab = page.locator('.header-tab:has(.header-flyout)').last();
  await lastTab.hover();
  await lastTab.locator('.header-flyout').waitFor({ state: 'visible', timeout: 3000 });
  const firstItem = lastTab.locator('.header-flyout a, .header-flyout div').first();
  const b = await firstItem.boundingBox();
  const coveredAfterScroll = await page.evaluate(([x, y]) => {
    const el = document.elementFromPoint(x, y);
    return el && el.closest('.header-flyout') ? null : (el ? (el.className || el.tagName) : 'nothing');
  }, [Math.round(b.x + b.width / 2), Math.round(b.y + b.height / 2)]);
  check('menus still open over a stuck button row', !coveredAfterScroll, String(coveredAfterScroll));
  await page.screenshot({ path: path.join(OUT, '2-menu-over-stuck-header.png') });
  await ctx.close();

  // The shorter windows, where a tall menu runs past the bottom of the screen.
  for (const vp of VIEWPORTS.slice(1)) {
    const short = await browser.newContext({ viewport: vp });
    await suppressCookieBanner(short);
    await short.addInitScript(() => {
      try { localStorage.setItem('whats-new-seen', '"summer-patch-2026"'); } catch (e) {}
    });
    const shortPage = await short.newPage();
    await shortPage.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
    await shortPage.waitForSelector('#app-main', { timeout: 30000 });
    await shortPage.waitForTimeout(1500);
    await testMenus(shortPage, `${vp.height}px`);
    await shortPage.screenshot({ path: path.join(OUT, `3-menus-at-${vp.height}.png`) });
    await short.close();
  }
  await browser.close();

  const failed = results.filter(r => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  console.log(`screenshots: ${OUT}`);
  process.exit(failed.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
