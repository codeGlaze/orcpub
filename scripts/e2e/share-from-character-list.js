// Can a character be shared from the character list, where there is no room for the share line?
//
//   ./scripts/e2e/run.sh share-from-character-list.js
//
// kaylee's homebrew character (E2E_HOMEBREW_CHARACTER_ID) has never been shared. Its row in the character list
// has one Copy link button: a single press must make the share, send the homebrew to the server, copy a short
// link and say the character is now shared. Pressing it again copies the same link, and the character page
// then shows the character as shared, with that link.
const { chromium } = require('playwright');
const { BASE, EXECUTABLE, HOMEBREW, newContext, login, watchShares, waitForButton, copyLink, tokenStatus,
        checker } = require('./lib');

const ID = process.env.E2E_HOMEBREW_CHARACTER_ID;

(async () => {
  if (!ID) {
    console.error('No E2E_HOMEBREW_CHARACTER_ID: run this through scripts/e2e/run.sh, which reads it from e2e-boot.');
    process.exit(2);
  }
  const { check, failures } = checker();
  const browser = await chromium.launch({ executablePath: EXECUTABLE });
  const ctx = await newContext(browser, HOMEBREW.library('Spoken only by test suites.'));
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  const requests = watchShares(page);
  await login(page, 'kaylee', 'serenity99');

  console.log('kaylee opens the character list:');
  await page.goto(`${BASE}/pages/dnd/5e/characters`, { waitUntil: 'networkidle', timeout: 120000 });
  check(await tokenStatus(page, ID) === 404, 'the character starts unshared');
  await page.evaluate(id => {
    const c = window.cljs.core;
    window.re_frame.core.dispatch(c.PersistentVector.fromArray(
      [c.keyword(null, 'toggle-character-expanded'), parseInt(id)], true));
  }, ID);
  // The message is a toast that closes itself, so watch for it from before the press.
  const noticed = page.waitForFunction(() => document.body && document.body.innerText.includes('now shared'), null,
    { timeout: 30000 }).then(() => true, () => false);
  await waitForButton(page, 'Copy link');
  await page.evaluate(() => { window.__copied = ''; });
  await page.locator('button', { hasText: 'Copy link' }).first().click();
  await page.waitForFunction(() => window.__copied, null, { timeout: 30000 }).catch(() => {});
  const link = await page.evaluate(() => window.__copied || '');
  check(/#s=[A-Za-z0-9_-]{22}$/.test(link), 'one press makes a short link and copies it', link);
  check(requests.includes('PUT 200'), 'the homebrew went to the server', requests.join(', '));
  check(await noticed, 'the page says the character is now shared');
  check(await tokenStatus(page, ID) === 200, 'the server has the share');

  console.log('\nshe presses it again:');
  await page.waitForTimeout(2500);
  const again = await copyLink(page);
  check(again === link, 'the same link is copied', again);

  console.log('\nshe opens the character page:');
  await page.goto(`${BASE}/pages/dnd/5e/characters/${ID}`, { waitUntil: 'networkidle', timeout: 120000 });
  await waitForButton(page, 'Stop sharing').catch(() => {});
  check(await page.evaluate(() => [...document.querySelectorAll('.share-pill')].some(p => p.textContent.trim() === 'Shared')),
    'the page shows it as shared');
  check(await copyLink(page) === link, 'with the same link');
  check(errors.length === 0, 'no uncaught page errors', errors.join(' | '));

  await browser.close();
  console.log(failures() ? `\nFAILURES: ${failures()}` : '\nall checks passed');
  process.exit(failures() ? 1 : 0);
})();
