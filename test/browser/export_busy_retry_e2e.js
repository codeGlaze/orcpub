// Export busy page, driven through the real builder UI.
//
// Clicks Export -> picks a sheet style -> clicks Create PDF while every export
// slot is held, so the new tab lands on the busy page, then watches that page
// retry itself and deliver the sheet once the rush passes.
//
//   lein e2e-server-busy          # one sheet at a time, 250ms wait, 2 retries
//   node test/browser/export_busy_retry_e2e.js
//
// Set ORCPUB_SHOT=path.png to save a picture of the busy page.
//
// Exits non-zero on the first failed check.

const { chromium } = require('playwright');

const BASE = process.env.ORCPUB_BASE || 'http://localhost:8890';
const MAX_RETRIES = parseInt(process.env.ORCPUB_PDF_MAX_RETRIES || '2', 10);

let failures = 0;
function check(label, ok, detail) {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${detail ? '  -- ' + detail : ''}`);
  if (!ok) failures++;
}

// The builder renders some controls twice, only one copy visible.
async function visible(locator) {
  for (let i = 0; i < await locator.count(); i++) {
    if (await locator.nth(i).isVisible()) return locator.nth(i);
  }
  throw new Error('no visible match');
}

// Hold every export slot until `until`, so an export arriving now is turned away.
// The load uses a six-caster sheet on purpose: a trivial one finishes in ~90ms
// and frees its slot between requests, which lets the export under test slip
// through and the run fail for no real reason.
function saturate(until) {
  const spec = '{' + [
    ':character-name "Load"', ':class-level "X"',
    ...[1, 2, 3, 4, 5, 6].map(i => `:spellcasting-class-${i} "Wizard"`),
    ...[1, 2, 3, 4, 5, 6].flatMap(i =>
      [0, 1, 2, 3].flatMap(lvl =>
        Array.from({ length: 12 }, (_, j) =>
          `:spells-${lvl}-${j + 1}-${i} "Protection from Energy"`)))
  ].join(' ') + '}';
  const one = async () => {
    while (Date.now() < until) {
      await fetch(BASE + '/character.pdf', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'body=' + encodeURIComponent(spec)
      }).catch(() => {});
    }
  };
  return Promise.all(Array.from({ length: 8 }, one));
}

(async () => {
  const browser = await chromium.launch({
    executablePath: process.env.PLAYWRIGHT_CHROMIUM || '/opt/pw-browsers/chromium'
  });
  const ctx = await browser.newContext({ viewport: { width: 1500, height: 1100 } });
  const page = await ctx.newPage();

  const errors = [];
  page.on('pageerror', e => errors.push('builder: ' + e));

  await page.goto(BASE + '/', { waitUntil: 'networkidle', timeout: 60000 });
  await page.waitForTimeout(1200);
  const cookie = page.locator('text=Got it!');
  if (await cookie.count()) await cookie.first().click().catch(() => {});

  await page.click('text=D&D 5e Character Builder / Sheet');
  await page.waitForTimeout(4000);

  (await visible(page.locator('button:has-text("Export")'))).click();
  await page.waitForTimeout(1200);
  // Create PDF stays pointer-events:none until a sheet style is chosen.
  (await visible(page.locator('select'))).selectOption('1');
  await page.waitForTimeout(600);

  const load = saturate(Date.now() + 10000);
  await page.waitForTimeout(500);

  const popup = ctx.waitForEvent('page', { timeout: 30000 });
  (await visible(page.locator('button:has-text("Create PDF")'))).click();
  const tab = await popup;
  tab.on('pageerror', e => errors.push('busy tab: ' + e));
  await tab.waitForLoadState('domcontentloaded').catch(() => {});
  await tab.waitForTimeout(1500);

  const navigations = [];
  tab.on('framenavigated', f => { if (f === tab.mainFrame()) navigations.push(f.url()); });

  const heading = await tab.textContent('h1').catch(() => null);
  check('a real Export lands on the busy page',
        heading && /sheets are being made/i.test(heading), heading);

  const countdown = await tab.textContent('#countdown').catch(() => null);
  check('it says when it will try again',
        countdown && /trying again in \d+ second/i.test(countdown), countdown);

  check('a manual escape is offered too',
        (await tab.locator('#retry-form button').count()) === 1);

  check('it wears the site header and logo',
        (await tab.locator('.app-header-bar img').count()) === 1);

  if (process.env.ORCPUB_SHOT) await tab.screenshot({ path: process.env.ORCPUB_SHOT, fullPage: true });

  const sheets = await tab.evaluate(() =>
    [...document.styleSheets].map(s => s.href).filter(Boolean)
      .map(h => h.replace(location.origin, '')));
  check('it loads the site stylesheets',
        sheets.includes('/css/compiled/styles.css'), sheets.join(', '));

  await page.waitForTimeout(9000);
  await load;
  await tab.waitForTimeout(8000);

  check('it retries itself without being touched', navigations.length > 0,
        `${navigations.length} self-submission(s)`);
  check('it stops at the configured limit', navigations.length <= MAX_RETRIES,
        `${navigations.length} vs max ${MAX_RETRIES}`);

  const stillBusy = await tab.locator('#countdown').count();
  check('once the rush passes the sheet is delivered', stillBusy === 0,
        stillBusy ? 'still on the busy page' : 'left the busy page');

  check('no script errors anywhere', errors.length === 0, errors.join(' | '));

  await browser.close();
  console.log(failures ? `\n${failures} check(s) failed` : '\nall checks passed');
  process.exit(failures ? 1 : 0);
})();
