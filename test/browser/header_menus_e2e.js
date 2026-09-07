// Browser-driven e2e for the header dropdown menus.
//
// Drives the REAL app against `lein e2e-server` on :8890.
//
// WHAT IT PINS: every item in every header flyout is not just visible but
// HITTABLE. A flyout that renders under the sticky button row looks fine in a
// screenshot and cannot be clicked, so visibility alone is not the check —
// document.elementFromPoint at each item's centre has to come back as that item.
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
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
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

  const tabs = page.locator('.header-tab:has(.header-flyout)');
  const tabCount = await tabs.count();
  check('the header has flyout menus to test', tabCount > 0, `${tabCount} tabs`);

  let shot = false;
  for (let i = 0; i < tabCount; i++) {
    const tab = tabs.nth(i);
    const label = ((await tab.locator('.title').first().innerText().catch(() => '')) || `tab ${i}`).trim();
    await tab.hover();
    const flyout = tab.locator('.header-flyout');
    await flyout.waitFor({ state: 'visible', timeout: 3000 });

    if (!shot) {
      await page.screenshot({ path: path.join(OUT, '1-menu-open.png') });
      shot = true;
    }

    const items = flyout.locator('a, [class*=pointer], div');
    const itemCount = await items.count();
    const blocked = [];
    for (let j = 0; j < itemCount; j++) {
      const item = items.nth(j);
      const text = ((await item.innerText().catch(() => '')) || '').trim();
      // Only leaf rows carry a single label; skip the wrappers around them.
      if (!text || text.includes('\n')) continue;
      const box = await item.boundingBox();
      if (!box) continue;
      const x = Math.round(box.x + box.width / 2);
      const y = Math.round(box.y + box.height / 2);
      const covered = await page.evaluate(([x, y]) => {
        const el = document.elementFromPoint(x, y);
        if (!el) return 'nothing at that point';
        return el.closest('.header-flyout') ? null : (el.className || el.tagName);
      }, [x, y]);
      if (covered) blocked.push(`${text} (covered by ${covered})`);
    }
    check(`every item in "${label}" is clickable`, blocked.length === 0,
          blocked.slice(0, 3).join(' | '));
  }

  // Scrolled down, the button row is stuck to the top of the viewport — the state
  // the screenshot in the bug report was taken in.
  await page.mouse.wheel(0, 600);
  await page.waitForTimeout(600);
  await page.mouse.wheel(0, -600);
  await page.waitForTimeout(600);
  const lastTab = tabs.nth(tabCount - 1);
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
  await browser.close();

  const failed = results.filter(r => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  console.log(`screenshots: ${OUT}`);
  process.exit(failed.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
