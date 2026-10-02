// Dark text on the amber buttons: the opt-in toggle beside "Light Theme".
//
//   ./scripts/e2e/run.sh dark-button-text.js
//
// Checks that the choice is off by default, applies to the buttons, survives a
// reload in the same browser without an account, works from the keyboard, hides in the light theme, and
// follows a logged-in user into a fresh browser through the account.
//
// SHOT=path.png saves the builder header with the toggle on.

const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const USER = process.env.E2E_USER || 'kaylee';
const PASS = process.env.E2E_PASS || 'serenity99';
const DARK = 'rgb(21, 32, 46)';
const WHITE = 'rgb(255, 255, 255)';

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
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${detail && !ok ? '  — ' + detail : ''}`);
}

async function builder(page) {
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
  const cookie = page.locator('#cookie-btn');
  if (await cookie.count()) await cookie.click({ timeout: 3000 }).catch(() => {});
  await page.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
  await page.locator('.form-button').first().waitFor();
}
const buttonColor = page => page.locator('.form-button').first().evaluate(el => getComputedStyle(el).color);
const toggle = page => page.locator('div.pointer', { hasText: 'Dark Button Text' }).first();

async function login(page) {
  await page.goto(`${BASE}/pages/login-page`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input');
  await page.locator('input').nth(0).fill(USER);
  await page.locator('input').nth(1).fill(PASS);
  await page.locator('button.form-button').first().click();
  await page.waitForFunction(() => { try { return /:token/.test(localStorage.getItem('user') || ''); } catch (_) { return false; } },
                             null, { timeout: 20000 });
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  console.log(`\ndark button text e2e -- ${BASE}\n`);

  // --- no account ---------------------------------------------------------
  {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    const page = await ctx.newPage();
    await builder(page);
    check('the toggle is there in the dark theme', await toggle(page).count() === 1);
    check('off by default: buttons keep white text', await buttonColor(page) === WHITE, await buttonColor(page));
    await toggle(page).click();
    await page.waitForTimeout(200);
    check('turning it on gives the buttons dark text', await buttonColor(page) === DARK, await buttonColor(page));
    if (process.env.SHOT) {
      await page.screenshot({ path: process.env.SHOT, clip: { x: 0, y: 150, width: 1280, height: 200 } });
      console.log(`  screenshot -> ${process.env.SHOT}`);
    }
    await builder(page);
    check('it survives a reload without an account', await buttonColor(page) === DARK, await buttonColor(page));
    // keyboard: the setting is a switch, reachable by Tab and flipped by Space or Enter
    await toggle(page).focus();
    await page.keyboard.press('Space'); await page.waitForTimeout(200);
    check('Space turns it off from the keyboard', await buttonColor(page) === WHITE, await buttonColor(page));
    check('and it says so to a screen reader', await toggle(page).getAttribute('aria-checked') === 'false');
    await page.keyboard.press('Enter'); await page.waitForTimeout(200);
    check('Enter turns it back on', await buttonColor(page) === DARK, await buttonColor(page));
    const ring = await toggle(page).evaluate(e => getComputedStyle(e).outlineStyle);
    check('keyboard focus shows a ring', ring === 'solid', ring);
    await page.locator('div.pointer', { hasText: 'Light Theme' }).first().click();
    await page.waitForTimeout(200);
    check('the light theme hides the toggle', await toggle(page).count() === 0);
    check('and keeps its white-on-slate buttons', await buttonColor(page) === WHITE, await buttonColor(page));
    await page.locator('div.pointer', { hasText: 'Light Theme' }).first().focus();
    await page.keyboard.press('Space'); await page.waitForTimeout(200);
    check('Light Theme switches back from the keyboard too', await toggle(page).count() === 1);
    await ctx.close();
  }

  // --- with an account ----------------------------------------------------
  {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    const page = await ctx.newPage();
    await login(page);
    await builder(page);
    let saved = null;
    page.on('request', r => { if (r.method() === 'PUT' && /\/user$/.test(r.url())) saved = r.postData(); });
    const before = await buttonColor(page);
    if (before !== DARK) { await toggle(page).click(); }
    await page.waitForTimeout(1500);
    check('logged in, turning it on saves it to the account', saved !== null && /dark-button-text\?/.test(saved), String(saved));
    await ctx.close();
  }
  {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    const page = await ctx.newPage();
    await login(page);
    await builder(page);
    check('a fresh browser picks it up from the account at login', await buttonColor(page) === DARK, await buttonColor(page));
    await toggle(page).click();                     // off again, for the next run
    await page.waitForTimeout(1500);
    await ctx.close();
  }
  {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    const page = await ctx.newPage();
    await login(page);
    await builder(page);
    check('turning it off follows the account too', await buttonColor(page) === WHITE, await buttonColor(page));
    await ctx.close();
  }

  await browser.close();
  console.log(`\n${failures ? failures + ' FAILED' : 'all passed'}`);
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
