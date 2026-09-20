// Does the owner's page say that a share link expired, and keep saying so until she acts on it?
//
//   ./scripts/e2e/run.sh share-link-expired-note.js
//
// e2e-boot seeds a character for kaylee that knows the homebrew language and whose last share link expired,
// as the pruner leaves it: a note of the character and the date. run.sh passes its id on as
// E2E_EXPIRED_CHARACTER_ID. The share line's status must say the link expired, with Share link beside it, on
// her character page and again after a reload. The character list row has only its Copy link button, so
// the status stays on the page. Dismiss must remove it for good without storing a share.
const { chromium } = require('playwright');
const { BASE, EXECUTABLE, HOMEBREW, newContext, login, watchShares, waitForButton, tokenStatus,
        checker } = require('./lib');

const ID = process.env.E2E_EXPIRED_CHARACTER_ID;
const NOTE = 'Link expired';

// The status text once it shows, or null when it does not within 30 seconds. innerText is what is on
// screen, so a hidden status does not count; the comparison ignores case in case a container sets
// capitals.
async function note(page) {
  const shown = await page.waitForFunction(t => document.body && document.body.innerText.toLowerCase().includes(t),
    NOTE.toLowerCase(), { timeout: 30000 }).then(() => true, () => false);
  if (!shown) return null;
  return page.evaluate(t => [...document.querySelectorAll('span')].map(s => s.textContent).find(x => x.includes(t)), NOTE);
}

const noteShowing = page => page.evaluate(t => document.body.innerText.toLowerCase().includes(t), NOTE.toLowerCase());

(async () => {
  if (!ID) {
    console.error('No E2E_EXPIRED_CHARACTER_ID: run this through scripts/e2e/run.sh, which reads it from e2e-boot.');
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
  const characterPage = `${BASE}/pages/dnd/5e/characters/${ID}`;

  console.log('kaylee opens the character:');
  await page.goto(characterPage, { waitUntil: 'networkidle', timeout: 120000 });
  const text = await note(page);
  check(Boolean(text), 'the page says the link expired', text);
  check(/\b20\d\d\b/.test(text || ''), 'with the date', text);
  await waitForButton(page, 'Share link');
  check(await tokenStatus(page, ID) === 410, 'Share link is offered beside it, and the server still has the note');

  console.log('\nshe reloads and does nothing:');
  await page.reload({ waitUntil: 'networkidle', timeout: 120000 });
  check(Boolean(await note(page)), 'the note is still there');

  console.log('\nshe opens the character list:');
  await page.goto(`${BASE}/pages/dnd/5e/characters`, { waitUntil: 'networkidle', timeout: 120000 });
  await page.evaluate(id => {
    const c = window.cljs.core;
    window.re_frame.core.dispatch(c.PersistentVector.fromArray(
      [c.keyword(null, 'toggle-character-expanded'), parseInt(id)], true));
  }, ID);
  await waitForButton(page, 'Copy link');
  check(!(await noteShowing(page)), "the character's row has its Copy link button and leaves the status to the page");

  console.log('\nshe dismisses it:');
  await page.goto(characterPage, { waitUntil: 'networkidle', timeout: 120000 });
  await waitForButton(page, 'Dismiss');
  await page.locator('button', { hasText: 'Dismiss' }).first().click();
  await page.waitForFunction(t => !document.body.innerText.includes(t), NOTE, { timeout: 15000 }).catch(() => {});
  check(!(await noteShowing(page)), 'the note goes');
  await page.reload({ waitUntil: 'networkidle', timeout: 120000 });
  await waitForButton(page, 'Share link');
  await page.waitForTimeout(2000);
  check(!(await noteShowing(page)), 'and stays gone after a reload');
  check(await tokenStatus(page, ID) === 404, 'the server keeps no share and no note');
  check(!requests.some(x => x.startsWith('PUT')), 'nothing was stored', requests.join(', ') || 'no uploads');
  check(errors.length === 0, 'no uncaught page errors', errors.join(' | '));

  await browser.close();
  console.log(failures() ? `\nFAILURES: ${failures()}` : '\nall checks passed');
  process.exit(failures() ? 1 : 0);
})();
