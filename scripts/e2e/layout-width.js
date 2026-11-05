// Layout follows the window width, not just the device.
//
//   ./scripts/e2e/run.sh layout-width.js
//
// A desktop browser narrowed to phone width gets the phone layout, and gets the
// desktop one back when widened; a phone gets the phone layout at any width. The builder keeps its tab across the switch, and
// the roll buttons' ctrl/shift tip still follows the device.
//
// SHOTS=dir saves the builder at each step.

const { chromium, devices } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const SHOTS = process.env.SHOTS;

function findChrome() {
  if (process.env.ORCPUB_CHROME) return process.env.ORCPUB_CHROME;
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

async function open(browser, context) {
  const ctx = await browser.newContext(context);
  await ctx.addInitScript(() => { try { localStorage.setItem('user', '{:theme "dark-theme"}'); } catch (_) {} });
  const page = await ctx.newPage();
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
  const cookie = page.locator('#cookie-btn');
  if (await cookie.count()) await cookie.click({ timeout: 3000 }).catch(() => {});
  await page.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
  await page.locator('.builder-tab').first().waitFor();
  return { ctx, page };
}
const tabs = page => page.locator('.builder-tab-text').allInnerTexts().then(t => t.map(s => s.trim().toLowerCase()).join(','));
const selected = page => page.locator('.selected-builder-tab').innerText().then(s => s.trim().toLowerCase());
const overflow = page => page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
const rollTip = page => page.locator('.roll-button').first().evaluate(b => !!b.closest('.tooltip'));
async function shot(page, name) {
  if (!SHOTS) return;
  fs.mkdirSync(SHOTS, { recursive: true });
  await page.screenshot({ path: path.join(SHOTS, `${name}.png`) });
}
async function resize(page, width, height) {
  await page.setViewportSize({ width, height });
  await page.waitForTimeout(400);
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  console.log(`\nlayout width e2e -- ${BASE}\n`);
  const PHONE = 'options,description,portrait,details', WIDE = 'options,description,portrait';

  // --- a desktop browser, resized ------------------------------------------
  {
    const { ctx, page } = await open(browser, { viewport: { width: 1280, height: 900 } });
    check('desktop at 1280: the desktop builder', await tabs(page) === WIDE, await tabs(page));
    check('desktop: roll buttons carry the ctrl/shift tip', await rollTip(page));
    const gap = await page.locator('div.pointer', { hasText: 'Dark Button Text' }).first()
      .evaluate(e => innerWidth - e.getBoundingClientRect().right);
    check('desktop: the toggles keep off the right edge', gap >= 10, gap);
    await shot(page, 'desktop-1280');
    await page.locator('.builder-tab', { hasText: 'Description' }).click();
    await page.waitForTimeout(200);
    await resize(page, 390, 844);
    check('narrowed to 390: the phone builder', await tabs(page) === PHONE, await tabs(page));
    check('narrowed: still on Description', await selected(page) === 'description', await selected(page));
    check('narrowed: nothing runs off the right edge', await overflow(page) <= 0, await overflow(page));
    await shot(page, 'desktop-narrowed-390');
    await page.locator('.builder-tab', { hasText: 'Details' }).click();
    await page.waitForTimeout(300);
    check('narrowed: a keyboard is still there, so the tip stays', await rollTip(page));
    const last = await page.locator('.roll-button').nth(5).evaluate(b => b.getBoundingClientRect().right);
    check('narrowed: all six ability buttons fit', last <= 390, last);
    const w = await page.locator('.roll-button').first().evaluate(b => b.getBoundingClientRect().width);
    check('narrowed: the ability buttons fill their columns', w >= 50, w);
    await resize(page, 1280, 900);
    check('widened again: the desktop builder', await tabs(page) === WIDE, await tabs(page));
    check('widened from Details: lands on Description', await selected(page) === 'description', await selected(page));
    await ctx.close();
  }
  {
    const { ctx, page } = await open(browser, { viewport: { width: 390, height: 844 } });
    check('desktop opened at 390: the phone builder from the start', await tabs(page) === PHONE, await tabs(page));
    await ctx.close();
  }

  // --- a phone ----------------------------------------------------------------
  {
    const { ctx, page } = await open(browser, { ...devices['iPhone 13'] });
    check('phone: the phone builder', await tabs(page) === PHONE, await tabs(page));
    check('phone: nothing runs off the right edge', await overflow(page) <= 0, await overflow(page));
    await page.locator('.builder-tab', { hasText: 'Details' }).click();
    await page.waitForTimeout(300);
    check('phone: no ctrl/shift tip on the roll buttons', !(await rollTip(page)));
    await shot(page, 'phone-390');
    await ctx.close();
  }
  for (const dev of ['iPhone SE', 'iPhone 13']) {
    const { ctx, page } = await open(browser, { ...devices[dev] });
    const h = await page.evaluate(() => {
      const bar = document.querySelector('.app-header-bar .w-100-p');
      const tabs = [...document.querySelectorAll('.header-tab')].map(t => t.getBoundingClientRect());
      const gaps = tabs.slice(1).map((r, i) => Math.round(r.left - tabs[i].right));
      const ink = e => { const x = document.createRange(); x.selectNodeContents(e); return x.getBoundingClientRect().left; };
      const h1 = document.querySelector('h1');
      const lefts = [bar.querySelector('img').getBoundingClientRect().left, tabs[0].left, ink(h1),
                     h1.closest('.flex-wrap').children[1].firstElementChild.getBoundingClientRect().left,
                     document.querySelector('.builder-tabs').parentElement.getBoundingClientRect().left].map(Math.round);
      return { lefts, loginR: Math.round(innerWidth - bar.lastElementChild.getBoundingClientRect().right),
               login: innerWidth - bar.lastElementChild.getBoundingClientRect().right,
               title: innerWidth - document.querySelector('h1').getBoundingClientRect().right,
               left: tabs[0].left, right: innerWidth - tabs[tabs.length - 1].right, gaps };
    });
    const w = page.viewportSize().width;
    check(`${w}px phone: the login button keeps off the edge`, h.login >= 10, h.login);
    check(`${w}px phone: logo, tabs, title, buttons and builder share the 10px gutter`,
          h.lefts.every(x => x === 10) && h.loginR === 10 && Math.round(h.right) === 10, JSON.stringify(h));
    check(`${w}px phone: the page title keeps off the edge`, h.title >= 10, h.title);
    // every tab's menu opens fully on screen, not clipped by the header
    const menus = [];
    for (let i = 0; i < await page.locator('.header-tab').count(); i++) {
      const tab = page.locator('.header-tab').nth(i);
      if (!(await tab.locator('.header-flyout').count())) continue;
      await tab.focus(); await page.waitForTimeout(250);
      menus.push(await tab.locator('.header-flyout').evaluate(f => {
        const b = f.getBoundingClientRect(), hit = document.elementFromPoint(b.left + b.width / 2, b.bottom - 4);
        return b.left >= 0 && b.right <= innerWidth && !!hit && f.contains(hit);
      }));
      await page.evaluate(() => document.activeElement.blur());
    }
    check(`${w}px phone: every header tab's menu opens fully on screen`, menus.length > 0 && menus.every(Boolean), JSON.stringify(menus));
    check(`${w}px phone: the header tabs sit evenly, close together`,
          h.gaps.every(g => g === h.gaps[0] && g <= 8) && Math.abs(h.left - h.right) <= 1, JSON.stringify(h));
    await ctx.close();
  }
  {
    // Sideways, an iPhone 13 is 750 wide: still under the breakpoint.
    const { ctx, page } = await open(browser, { ...devices['iPhone 13 landscape'] });
    check('phone sideways at 750: still the phone builder', await tabs(page) === PHONE, await tabs(page));
    await ctx.close();
  }
  {
    // A bigger phone sideways is wider than the breakpoint; width only ever asks
    // for the smaller layout, so it keeps the phone one.
    const { ctx, page } = await open(browser, { ...devices['iPhone 13 landscape'], viewport: { width: 932, height: 430 } });
    check('phone wider than the breakpoint: still the phone builder', await tabs(page) === PHONE, await tabs(page));
    await ctx.close();
  }

  await browser.close();
  console.log(`\n${failures ? failures + ' FAILED' : 'all passed'}`);
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
