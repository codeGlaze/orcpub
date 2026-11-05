// Every item in the longest header menu (My Content) can be reached and clicked.
//
//   ./scripts/e2e/run.sh flyout-reach.js
//
// Opens My Content on desktop (900 and 720 tall, where the menu is capped and
// scrolls) and on 390px and 320px phones. For each item it scrolls the item into
// view inside the menu and checks the item is the topmost element at its centre:
// not the sticky button row, not the page. Then it clicks the middle item and the
// last item at their real positions and checks the page changes to them.

const { chromium, devices } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.E2E_BASE || 'http://localhost:8890';
function findChrome() {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  try {
    const dir = fs.readdirSync(base).filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
    const p = dir && path.join(base, dir, 'chrome-linux', 'chrome');
    if (p && fs.existsSync(p)) return p;
  } catch (_) {}
  return undefined;
}
let failures = 0;
function check(label, ok, detail) {
  if (!ok) failures++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${detail !== undefined && !ok ? '  — ' + detail : ''}`);
}

const SETUPS = [
  ['desktop 1280x900', { viewport: { width: 1280, height: 900 } }, false],
  ['desktop 1280x720', { viewport: { width: 1280, height: 720 } }, false],
  ['phone 390', { ...devices['iPhone 13'] }, true],
  ['phone 320', { ...devices['iPhone SE'] }, true],
];

async function openMenu(browser, context, touch) {
  const ctx = await browser.newContext(context);
  const page = await ctx.newPage();
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
  const cookie = page.locator('#cookie-btn');
  await cookie.waitFor({ timeout: 4000 }).then(() => cookie.click()).catch(() => {});
  await page.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
  const tab = page.locator('.header-tab').last();              // My Content
  if (touch) await tab.tap(); else await tab.hover();
  await page.waitForTimeout(400);
  return { ctx, page, menu: tab.locator('.header-flyout') };
}

// Scroll an item into view inside the menu and report whether it is the topmost
// element at its centre, plus that centre for a real click.
function reach(menu, i) {
  return menu.evaluate((m, i) => {
    const item = m.children[i];
    item.scrollIntoView({ block: 'nearest' });
    const r = item.getBoundingClientRect();
    const x = r.left + r.width / 2, y = r.top + r.height / 2;
    const hit = document.elementFromPoint(x, y);
    return { name: item.textContent.trim(), x, y, top: !!hit && item.contains(hit),
             onScreen: r.top >= 0 && r.bottom <= innerHeight,
             blocker: hit && !item.contains(hit) ? `${hit.tagName}.${hit.className}` : '' };
  }, i);
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  console.log(`\nflyout reach e2e -- ${BASE}\n`);
  for (const [name, context, touch] of SETUPS) {
    let { ctx, page, menu } = await openMenu(browser, context, touch);
    const n = await menu.evaluate(m => m.children.length);
    const capped = await menu.evaluate(m => m.scrollHeight > m.clientHeight + 1);
    const blocked = [];
    for (let i = 0; i < n; i++) {
      const r = await reach(menu, i);
      if (!r.top || !r.onScreen) blocked.push(`${r.name} (${r.blocker || 'off screen'})`);
    }
    check(`${name}: all ${n} items reachable${capped ? ' (menu capped, scrolls)' : ''}`, n >= 10 && !blocked.length, blocked.join('; '));
    await ctx.close();

    for (const which of [Math.floor(n / 2), n - 1]) {
      ({ ctx, page, menu } = await openMenu(browser, context, touch));
      const r = await reach(menu, which);
      const before = page.url();
      if (touch) await page.touchscreen.tap(r.x, r.y); else await page.mouse.click(r.x, r.y);
      await page.waitForTimeout(800);
      check(`${name}: clicking "${r.name}" (${which === n - 1 ? 'last' : 'middle'}) opens it`,
            page.url() !== before && !/character-builder$/.test(page.url()), page.url());
      await ctx.close();
    }
  }
  await browser.close();
  console.log(`\n${failures ? failures + ' FAILED' : 'all passed'}`);
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
