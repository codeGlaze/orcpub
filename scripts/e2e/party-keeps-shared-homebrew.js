// Does a character added to a party from a share link keep showing its homebrew on the party page?
//
//   ./scripts/e2e/run.sh party-keeps-shared-homebrew.js
//
// kaylee shares the seeded character that knows a homebrew language (E2E_HOMEBREW_CHARACTER_ID). zoe,
// signed in and without that homebrew, opens the link and presses Add to Party, which makes a new party.
// On zoe's parties page the party must list the share token for that character, and opening its row must
// load the homebrew. After kaylee presses New link, zoe's party must list no token and opening the row
// must load nothing. That only characters can join a party is covered by party_test.clj.
const { chromium } = require('playwright');
const { BASE, EXECUTABLE, HOMEBREW, newContext, login, waitForButton, copyLink, sharedHomebrew,
        checker } = require('./lib');

const ID = process.env.E2E_HOMEBREW_CHARACTER_ID;
const NAME = 'Wren Holloway';

// The share token zoe's parties list for the character, from the parties the page loaded.
const listedToken = page => page.evaluate(id => {
  const c = window.cljs.core;
  const parties = c.get(window.re_frame.db.app_db.state, c.keyword('orcpub.dnd.e5.character', 'parties'));
  const tokens = [];
  c.run_BANG_(party => c.run_BANG_(ch => {
    if (c.get(ch, c.keyword(null, 'db/id')) === parseInt(id) || c.get(ch, c.keyword('db', 'id')) === parseInt(id)) {
      tokens.push(c.get(ch, c.keyword('orcpub.party-share', 'token')) || null);
    }
  }, c.get(party, c.keyword('orcpub.dnd.e5.party', 'character-ids'))), parties || c.PersistentVector.EMPTY);
  return tokens;
}, ID);

async function openPartyRow(page) {
  await page.goto(`${BASE}/pages/dnd/5e/parties`, { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(2000);
  const tokens = await listedToken(page);
  await page.locator(`text=${NAME}`).first().click();
  await page.waitForTimeout(4000);
  return { tokens, ...(await sharedHomebrew(page)) };
}

(async () => {
  if (!ID) {
    console.error('No E2E_HOMEBREW_CHARACTER_ID: run this through scripts/e2e/run.sh, which reads it from e2e-boot.');
    process.exit(2);
  }
  const { check, failures } = checker();
  const browser = await chromium.launch({ executablePath: EXECUTABLE });

  console.log('kaylee shares her character:');
  const kaylee = await newContext(browser, HOMEBREW.library('Spoken only by test suites.'));
  const owner = await kaylee.newPage();
  owner.on('dialog', d => d.accept());
  await login(owner, 'kaylee', 'serenity99');
  await owner.goto(`${BASE}/pages/dnd/5e/characters/${ID}`, { waitUntil: 'networkidle', timeout: 120000 });
  await waitForButton(owner, 'Share link');
  await owner.locator('button', { hasText: 'Share link' }).first().click();
  const link = await copyLink(owner);
  check(/#s=[A-Za-z0-9_-]{22}$/.test(link), 'a short link', link);

  console.log('\nzoe opens it and adds the character to a party:');
  const zoe = await newContext(browser, null);
  const page = await zoe.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await login(page, 'zoe', 'washburne7');
  await page.goto(link, { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(4000);
  check((await sharedHomebrew(page)).sources.includes(HOMEBREW.source), 'the link loaded the homebrew');
  const add = page.locator('button.form-button', { hasText: /^ADD$/ });
  check(await add.count() > 0, 'the page offers Add to Party');
  await add.first().click();
  await page.waitForTimeout(2500);

  console.log('\nzoe opens the party page:');
  const kept = await openPartyRow(page);
  check(kept.tokens.length === 1 && kept.tokens[0] === link.split('#s=')[1], 'the party keeps the link\'s token', JSON.stringify(kept.tokens));
  check(kept.sources.includes(HOMEBREW.source), 'opening the row loads the homebrew', JSON.stringify(kept.sources));

  console.log('\nkaylee presses New link, and zoe opens the party page again:');
  await copyLink(owner);
  await owner.locator('button', { hasText: 'New link' }).first().click();
  await owner.waitForTimeout(3000);
  const revoked = await openPartyRow(page);
  check(revoked.tokens.length === 1 && revoked.tokens[0] === null, 'the party lists no token', JSON.stringify(revoked.tokens));
  check(revoked.sources.length === 0, 'opening the row loads nothing', JSON.stringify(revoked.sources));
  check(errors.length === 0, 'no uncaught page errors on zoe\'s pages', errors.join(' | '));

  await kaylee.close();
  await zoe.close();
  await browser.close();
  console.log(failures() ? `\nFAILURES: ${failures()}` : '\nall checks passed');
  process.exit(failures() ? 1 : 0);
})();
