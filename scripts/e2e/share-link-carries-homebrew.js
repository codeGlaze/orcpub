// Does a short share link carry a character's homebrew to someone who does not have it, and keep
// carrying the current homebrew?
//
//   ./scripts/e2e/run.sh share-link-carries-homebrew.js
//
// e2e-boot seeds a character for kaylee that knows a homebrew language, and run.sh passes its id on as
// E2E_HOMEBREW_CHARACTER_ID. kaylee's browser gets that language in its local library, where an import
// would leave it. Signed in, her character page must store nothing until she presses Share link, which
// sends the homebrew to the server and makes a link that carries only a token; the character list must
// then give the same link. The link is opened logged out and as zoe, neither of whom has the homebrew,
// and both must load it; a wrong token must load nothing and say so. Then kaylee changes the language's
// description and opens her character again: the link must stay the same and show the new description.
// Last, she presses New link: the old link loads nothing and the new one works.
const { chromium } = require('playwright');
const { BASE, EXECUTABLE, HOMEBREW, newContext, login, watchShares, waitForButton, copyLink,
        sharedHomebrew, checker } = require('./lib');

const ID = process.env.E2E_HOMEBREW_CHARACTER_ID;
const FIRST = 'Spoken only by test suites.';
const SECOND = 'Spoken only by test suites, and now with verbs.';

// kaylee, signed in, on her character's page, with the language described as `description`.
async function ownerSession(browser, description) {
  const ctx = await newContext(browser, HOMEBREW.library(description));
  const page = await ctx.newPage();
  page.on('dialog', d => d.accept());
  const requests = watchShares(page);
  await login(page, 'kaylee', 'serenity99');
  await page.goto(`${BASE}/pages/dnd/5e/characters/${ID}`, { waitUntil: 'networkidle', timeout: 120000 });
  return { ctx, page, requests };
}

async function open(browser, link, who) {
  const ctx = await newContext(browser, null);
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  const requests = watchShares(page);
  if (who) await login(page, who.user, who.pass);
  await page.goto(link, { waitUntil: 'domcontentloaded', timeout: 120000 });
  // A failure notice is a toast that closes itself, so watch for it from the start.
  const noticed = page.waitForFunction(() => document.body && document.body.innerText.includes('could not be loaded'),
    null, { timeout: 15000 }).then(() => 1, () => 0);
  await page.waitForLoadState('networkidle', { timeout: 120000 });
  await page.waitForTimeout(5000);
  const loaded = await sharedHomebrew(page);
  const banner = await page.locator('text=Shared with 1 homebrew piece').count();
  const failedNotice = await noticed;
  await ctx.close();
  return { ...loaded, banner, failedNotice, requests, errors };
}

(async () => {
  if (!ID) {
    console.error('No E2E_HOMEBREW_CHARACTER_ID: run this through scripts/e2e/run.sh, which reads it from e2e-boot.');
    process.exit(2);
  }
  const { check, failures } = checker();
  const browser = await chromium.launch({ executablePath: EXECUTABLE });

  console.log('kaylee makes the link:');
  const first = await ownerSession(browser, FIRST);
  const beforeSharing = await first.page.evaluate(async url => {
    const c = window.cljs.core;
    const login = c.get_in(window.re_frame.db.app_db.state,
      c.PersistentVector.fromArray([c.keyword(null, 'user-data'), c.keyword(null, 'token')], true));
    return (await fetch(url, { headers: { Authorization: `Token ${login}` } })).status;
  }, `${BASE}/dnd/5e/characters/${ID}/share-token`);
  check(beforeSharing === 404 && first.requests.length === 0, 'nothing is stored before Share link is pressed',
    `token route ${beforeSharing}; ${first.requests.join(', ') || 'no uploads'}`);
  await waitForButton(first.page, 'Share link');
  await first.page.locator('button', { hasText: 'Share link' }).first().click();
  const link = await copyLink(first.page);
  const match = link.match(/#s=([A-Za-z0-9_-]{22})$/);
  check(Boolean(match), 'the link carries a share token', link);
  check(link.length < 120, 'the link is short', `${link.length} characters`);
  check(first.requests.includes('PUT 200'), 'the homebrew went to the server', first.requests.join(', '));

  // The character list's Copy link is the same button, which once packed whatever character was open in
  // the builder; in this fresh browser that is none, so its link must match the character page's.
  console.log('\nkaylee copies it from the character list:');
  await first.page.goto(`${BASE}/pages/dnd/5e/characters`, { waitUntil: 'networkidle', timeout: 120000 });
  await first.page.evaluate(id => {
    const c = window.cljs.core;
    window.re_frame.core.dispatch(c.PersistentVector.fromArray(
      [c.keyword(null, 'toggle-character-expanded'), parseInt(id)], true));
  }, ID);
  let listLink;
  try { listLink = await copyLink(first.page); } catch (e) { listLink = `(no Copy link in the row: ${e.message.split('\n')[0]})`; }
  check(Boolean(match) && listLink === link, "the list row's link is the character page's link", listLink);
  await first.ctx.close();

  if (match) {
    for (const who of [null, { user: 'zoe', pass: 'washburne7' }]) {
      console.log(`\n${who ? who.user : 'logged out'} opens it:`);
      const r = await open(browser, link, who);
      check(r.sources.includes(HOMEBREW.source), 'the homebrew loaded', JSON.stringify(r.sources));
      check(r.description === FIRST, 'with its description', r.description);
      check(r.banner > 0, 'the page says what was shared');
      check(!r.requests.some(x => x.startsWith('PUT')), 'nothing was uploaded', r.requests.join(', '));
      check(r.errors.length === 0, 'no uncaught page errors', r.errors.join(' | '));
    }

    console.log('\nthe same link with a wrong token:');
    const wrong = await open(browser, link.replace(/#s=[A-Za-z0-9_-]{22}$/, '#s=' + 'A'.repeat(22)), null);
    check(wrong.sources.length === 0, 'loads nothing', JSON.stringify(wrong.sources));
    check(wrong.failedNotice > 0, 'says the homebrew could not be loaded');

    console.log('\nkaylee changes the description and opens her character again:');
    const second = await ownerSession(browser, SECOND);
    check(await copyLink(second.page) === link, 'the link stays the same');
    check(second.requests.includes('PUT 200'), 'the changed homebrew went to the server', second.requests.join(', '));
    await second.ctx.close();
    const updated = await open(browser, link, null);
    check(updated.description === SECOND, 'the same link shows the new description', updated.description);

    console.log('\nkaylee makes a new link:');
    const third = await ownerSession(browser, SECOND);
    await copyLink(third.page);
    await third.page.locator('button', { hasText: 'New link' }).first().click();
    let fresh = link;
    for (let i = 0; i < 20 && fresh === link; i++) { await third.page.waitForTimeout(1000); fresh = await copyLink(third.page); }
    check(/#s=[A-Za-z0-9_-]{22}$/.test(fresh) && fresh !== link, 'New link gives a different link', fresh);
    await third.ctx.close();
    const old = await open(browser, link, null);
    check(old.sources.length === 0 && old.failedNotice > 0, 'the old link loads nothing, and says so', JSON.stringify(old.sources));
    const renewed = await open(browser, fresh, null);
    check(renewed.description === SECOND, 'the new link loads the homebrew', renewed.description);
  }

  await browser.close();
  console.log(failures() ? `\nFAILURES: ${failures()}` : '\nall checks passed');
  process.exit(failures() ? 1 : 0);
})();
