// Typed picture links never request our own server.
//
// A player pasted prose into a picture-link field; the app used the text as an <img src> on every
// keystroke, it resolved under /pages/dnd/5e/, and each edit was a 404 on our server, which got them
// banned. Checks, signed in on the builder at 1920 px: typing prose, partial and malformed addresses
// into the Faction Image URL field and the picture drawer's link field sends no request to our
// origin and shows no picture; a valid https address still previews (served by a routed fake host);
// editing it into prose removes the picture; prose stored as the character's picture, saved and
// opened on its own sheet, sends no request carrying it and draws no image of it.
// Plan: BRANCH.md on agents/develop, "the picture-link hotfix".
//
// Needs:     server (`./scripts/e2e/run.sh test/browser/image_link_requests_e2e.js`). Signs in as
//            the seeded user.
// Runs in:   ~70s.
const path = require('path');
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const { SetupFailed, BASE, checker, quietOverlays, topLevelId } = require('./lib/toolbox');
const builder = require('./places/builder');
const lib = require(path.join(process.cwd(), 'scripts/e2e/lib.js'));

const { check, attempt, must, watch, finish } = checker();
const PROSE = 'Vecna causes necrotic magic to engulf one creature he can see';
const IMAGE = 'https://img.test/picture.png';
// A 1x1 transparent PNG, served for the fake image host so a valid address loads.
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=', 'base64');

/**
 * Collects every request the page sends to our own origin, in order.
 * @param {import('playwright').Page} page
 * @returns {string[]} the live list of request URLs (method and path)
 */
function ownRequests(page) {
  const seen = [];
  const origin = new URL(BASE).origin;
  page.on('request', r => { const u = new URL(r.url()); if (u.origin === origin) seen.push(`${r.method()} ${u.pathname}`); });
  return seen;
}

/**
 * Clears `field` and types `text` one key at a time at about a player's speed (faster, the
 * builder's text fields drop keys), then lets the page settle.
 * @param {import('playwright').Locator} field
 * @param {string} text
 */
async function typeInto(field, text) {
  await field.fill('');
  await field.pressSequentially(text, { delay: 60 });
  await field.page().waitForTimeout(600);
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  await quietOverlays(ctx);
  await ctx.route('https://img.test/**', r => r.fulfill({ status: 200, contentType: 'image/png', body: PNG }));
  const page = await ctx.newPage();
  watch(page, 'builder');
  await must('sign in as the seeded user', () => lib.login(page, 'kaylee', 'serenity99'));
  await must('open the builder', () => page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' }));
  await must('open the Description tab', () => builder.tab(page, 'Description').click({ timeout: 20000 }));
  const own = ownRequests(page);
  const faction = builder.factionImageUrl(page);
  await must('see the Faction Image URL field', () => faction.waitFor({ timeout: 10000 }));

  {
    const before = own.length;
    await attempt('paste prose into Faction Image URL', async () => { await faction.fill(PROSE); await page.waitForTimeout(600); });
    check('faction field, pasted prose: no request to our server', own.length === before, own.slice(before).join(', '));
    check('faction field, pasted prose: no picture shown', await builder.factionThumbnail(page).count() === 0);
  }
  for (const [label, text] of [['prose', PROSE], ['a partial scheme', 'htt'], ['a scheme with no host', 'https:/'],
                               ['an empty host', 'http://'], ['a path on our site', '/api/x']]) {
    const before = own.length;
    await attempt(`type ${label} into Faction Image URL`, () => typeInto(faction, text));
    const sent = own.slice(before);
    check(`faction field, ${label}: no request to our server`, sent.length === 0, sent.join(', '));
    check(`faction field, ${label}: no picture shown`, await builder.factionThumbnail(page).count() === 0);
  }

  let before = own.length;
  // Pasted, as players do: typed key by key, each prefix (https://i, https://im...) is itself a valid
  // outside address the browser tries, which the spec accepts but would fill the console with DNS errors.
  await attempt('paste a valid https address', async () => {
    await faction.fill(IMAGE);
    await builder.factionThumbnail(page).first().waitFor({ timeout: 10000 });
  });
  check('faction field, valid address: no request to our server', own.length === before, own.slice(before).join(', '));
  const shown = await builder.factionThumbnail(page).first()
    .evaluate(i => new Promise(done => {
      const report = () => done({ src: i.getAttribute('src'), width: i.naturalWidth });
      if (i.complete) report(); else { i.onload = report; i.onerror = report; setTimeout(report, 5000); }
    }))
    .catch(e => ({ error: String(e.message).split('\n')[0] }));
  check('faction field, valid address: the picture previews', shown.src === IMAGE && shown.width > 0, JSON.stringify(shown));
  before = own.length;
  await attempt('edit the valid address into prose', () => typeInto(faction, PROSE));
  check('faction field, valid edited to prose: the old picture is gone', await builder.factionThumbnail(page).count() === 0);
  check('faction field, valid edited to prose: no request to our server', own.length === before, own.slice(before).join(', '));
  await attempt('clear the faction field', () => faction.fill(''));

  await must('open the picture drawer', () => builder.changePicture(page).click({ timeout: 10000 }));
  await must('switch it to an image from the web', () => builder.pictureLinkMode(page).click({ timeout: 10000 }));
  const link = builder.pictureLink(page);
  await must('see the image address field', () => link.waitFor({ timeout: 10000 }));
  before = own.length;
  await attempt('type prose into the image address field', () => typeInto(link, PROSE));
  check('picture link, prose: no request to our server', own.length === before, own.slice(before).join(', '));
  check('picture link, prose: the drawer shows no picture', await page.locator('.pl-link-preview img').count() === 0);
  await must('use the prose as the picture', () => builder.useImage(page).click({ timeout: 10000 }));
  await page.waitForTimeout(800);
  check('picture link stored as prose: no request to our server', own.length === before, own.slice(before).join(', '));

  await must('open the Options tab', () => builder.tab(page, 'Options').click({ timeout: 10000 }));
  const saved = page.waitForResponse(r => r.request().method() === 'POST'
    && new URL(r.url()).pathname === '/dnd/5e/characters', { timeout: 30000 });
  await must('save the character', () => page.locator('button:has-text("Save"), div.pointer:has-text("Save")').first().click());
  const id = topLevelId(await (await saved).text());
  check('the character was saved', !!id, String(id));
  if (id) {
    const sheet = await ctx.newPage();
    watch(sheet, 'saved sheet');
    const sheetOwn = ownRequests(sheet);
    await attempt('open the saved character', () => sheet.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'networkidle' }));
    await sheet.waitForTimeout(1500);
    const carrying = sheetOwn.filter(u => decodeURIComponent(u).includes('Vecna'));
    check('saved sheet: no request carries the stored prose', carrying.length === 0, carrying.join(', '));
    check('saved sheet: no image is drawn from the prose',
          await sheet.evaluate(() => [...document.images].every(i => !(i.getAttribute('src') || '').includes('Vecna'))));
  }

  await browser.close();
  process.exit(finish());
})().catch(e => {
  if (e instanceof SetupFailed) { console.log('STOP  ' + e.message); process.exit(finish()); }
  console.error(e); process.exit(2);
});
