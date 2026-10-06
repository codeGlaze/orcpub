// The character data page (/pages/dnd/5e/characters/<id>/data): it lists a saved character's
// stored choices with the app bundle BLOCKED, so it works when the app cannot open the character.
//
// A level-4 wizard is built by clicks with a homebrew race, feat and spell ("Tide Pak", the
// orphan-clear.js setup), saved, and the pack is switched off in My Content, the measured
// trapped-picks case (character-rescue.md). Then, with every request for orcpub.js aborted:
//   the owner sees the three choices on /data and on /repair, and Copy for support copies them;
//   another account and a logged-out visitor see "Only the owner can open this";
//   an unknown id says so; this browser's draft lists the same choices.
// Every change is a click, a select or the file input; app-db is never written.
//
// Needs the seeded server (kaylee / serenity99, zoe / washburne7) and both bundles:
//   lein fig:build && lein fig:ledger && lein garden once
//   DATOMIC_URL=datomic:mem://orcpub-e2e ORCPUB_ENV=dev SIGNATURE=e2e-test-signature PORT=8890 \
//   CSP_POLICY=none lein with-profile init-db run -m e2e-boot
//   NODE_PATH=/opt/node22/lib/node_modules node test/e2e/character-data.js
const path = require('path');
const fs = require('fs');
const os = require('os');
const { chromium } = require('playwright');
const { BASE, findChrome, checker, dbAt, readyPage, dismissWhatsNew, clickTab } = require('./lib');

const PAK = 'Tide Pak';
const ORCBREW = `{"${PAK}" {` +
  `:orcpub.dnd.e5/races {:tidefolk {:key :tidefolk :name "Tidefolk" :option-pack "${PAK}" :speed 30 :size :medium` +
  ` :description "People of the shallows."}}` +
  ` :orcpub.dnd.e5/feats {:tidebreaker {:key :tidebreaker :name "Tidebreaker" :option-pack "${PAK}"` +
  ` :description "You break the tide."}}` +
  ` :orcpub.dnd.e5/spells {:brine-lash {:key :brine-lash :name "Brine Lash" :option-pack "${PAK}" :level 1` +
  ` :school "evocation" :casting-time "1 action" :range "30 feet" :duration "Instantaneous"` +
  ` :components {:verbal true :somatic true} :description "A whip of seawater." :spell-lists {:wizard true}}}}}`;
const KEYS = { race: 'tidefolk', feat: 'tidebreaker', spell: 'brine-lash' };
const has = (s, k) => s.includes(':' + k);
const wait = ms => new Promise(r => setTimeout(r, ms));
const log = (...a) => console.log(a.join(' '));

const HELPERS = () => {
  const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  window.__vis = vis;
  window.__cards = () => [...document.querySelectorAll('#app div.p-10.b-1.b-rad-5.m-5.b-orange')].filter(vis);
  window.__name = c => ((c.querySelector('span.f-w-b.f-s-1') || {}).textContent || '').trim();
  window.__secs = () => [...document.querySelectorAll('#app div.p-5.m-b-20')].filter(vis);
  window.__title = s => ((s.querySelector('span.m-l-5.f-s-18.f-w-b') || {}).textContent || '').trim();
  // Every section on the page: title, counter text, selected cards; plus any text naming the pack.
  window.__dump = () => window.__secs().map(s => {
    const q = s.querySelector('div.p-5.f-s-16');
    const own = [...s.querySelectorAll('div.p-10.b-1.b-rad-5.m-5.b-orange')].filter(c => c.closest('div.p-5.m-b-20') === s && vis(c));
    const sel = own.filter(c => c.classList.contains('b-w-5')).map(window.__name);
    return `${window.__title(s)} [${q ? q.textContent.replace(/\s+/g, ' ').trim() : ''}] cards=${own.length} selected={${sel.join(', ')}}`;
  });
};

async function clickCard(page, name) {
  await page.evaluate(HELPERS);
  // Spell cards are titled "<level> - <name>".
  const r = await page.evaluate(n => {
    const c = window.__cards().find(c => window.__name(c) === n || window.__name(c).endsWith(' - ' + n));
    if (!c) return 'no card';
    c.scrollIntoView({ block: 'center' });
    return 'ok';
  }, name);
  if (r !== 'ok') { log(`   click "${name}": ${r}`); return false; }
  await page.locator('#app div.p-10.b-1.b-rad-5.m-5.b-orange').filter({ has: page.locator('span.f-w-b.f-s-1', { hasText: new RegExp(`^(\\d+ - )?${name}$`) }) }).first().click();
  await wait(1200);
  log(`   click "${name}": ok`);
  return true;
}

async function save(page) {
  const b = page.getByText(/^Save( New Character)?$/).first();
  await b.click();
  await wait(4000);
  return (await dbAt(page, '[:character :db/id]')).trim();
}

async function myContent(page) {
  await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
  await wait(2500);
  await dismissWhatsNew(page);
}

async function importPak(page) {
  const file = path.join(os.tmpdir(), 'Tide Pak.orcbrew');
  fs.writeFileSync(file, ORCBREW);
  await myContent(page);
  await page.locator('#app input[type=file]').first().setInputFiles(file);
  await wait(5000);
  const keep = page.getByRole('button', { name: /^keep “/i }).first();
  if (await keep.isVisible().catch(() => false)) { await keep.click(); await wait(3000); }
  const lib = await dbAt(page, `[:plugins "${PAK}"]`);
  log(`   imported: race ${has(lib, KEYS.race)} feat ${has(lib, KEYS.feat)} spell ${has(lib, KEYS.spell)}`);
}

async function build(page) {
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'load' });
  await wait(3000);
  await dismissWhatsNew(page);
  // Start from a blank character through the header's New (confirming if it asks).
  const nw = page.getByText('New', { exact: true }).first();
  if (await nw.isVisible().catch(() => false)) {
    await nw.click(); await wait(1000);
    const c = page.getByText(/^create new character$/i).first();
    if (await c.isVisible().catch(() => false)) { await c.click(); await wait(2000); }
  }
  await clickTab(page, 'Race');
  await clickCard(page, 'Tidefolk');
  await clickTab(page, 'Class / Level');
  const row = page.locator('#app select.builder-option-dropdown.flex-grow-1').first();
  await row.selectOption('wizard'); await wait(2000);
  await row.locator('xpath=..').locator('select.w-100').selectOption('level-4'); await wait(2500);
  for (const tab of ['Class / Level', 'Ability Scores / Feats']) {
    await clickTab(page, tab);
    if (await clickCard(page, 'Feat')) break;
  }
  await clickCard(page, 'Tidebreaker');
  await clickTab(page, 'Spells');
  await clickCard(page, 'Brine Lash');
  const id = await save(page);
  log(`   saved as ${id}`);
  return id;
}

async function logIn(page, user, pass) {
  await page.goto(`${BASE}/pages/login-page`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input');
  await page.locator('input').nth(0).fill(user);
  await page.locator('input').nth(1).fill(pass);
  await page.getByRole('button', { name: 'LOGIN' }).click({ force: true });
  await wait(5000);
  await readyPage(page);
}

// A page that may not load the app: every request for orcpub.js is aborted and counted.
async function noAppPage(context) {
  const page = await context.newPage();
  page.appRequests = 0;
  await page.route('**/js/compiled/orcpub.js', r => { page.appRequests++; return r.abort(); });
  page.on('pageerror', e => log('   pageerror:', String(e).slice(0, 160)));
  return page;
}

// The data page's table as [section, choice, stored key] rows, once it has rendered.
async function dataRows(page, url) {
  await page.goto(url, { waitUntil: 'load' });
  await page.waitForFunction(() => !/^Loading/.test(document.getElementById('ledger').textContent), null, { timeout: 15000 });
  return page.evaluate(() => [...document.querySelectorAll('#ledger tbody tr')]
    .map(tr => [...tr.children].slice(0, 3).map(td => td.textContent.trim())));
}
const listed = (rows, section, choice) => rows.some(r => r[0] === section && r[1] === choice);
const ledgerText = page => page.evaluate(() => document.getElementById('ledger').textContent);

(async () => {
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  try {
    const owner = await browser.newContext({ viewport: { width: 1280, height: 1000 } });
    await owner.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: BASE });
    const app = await owner.newPage();
    await logIn(app, 'kaylee', 'serenity99');
    await importPak(app);
    const id = await build(app);
    check('the character was saved', /^\d+$/.test(id), id);
    await myContent(app);
    await app.locator('.item-list-item .dev-mode-switch:not(.compact)').first().click();
    await wait(2000);
    check('the pack is switched off', /true/.test(await dbAt(app, `[:plugins "${PAK}" :disabled?]`)));

    const page = await noAppPage(owner);
    for (const route of ['data', 'repair']) {
      const rows = await dataRows(page, `${BASE}/pages/dnd/5e/characters/${id}/${route}`);
      check(`/${route} lists the homebrew race`, listed(rows, 'Race', 'Tidefolk'));
      check(`/${route} lists the homebrew feat`, listed(rows, 'Feats', 'Tidebreaker'));
      check(`/${route} lists the homebrew spell`, listed(rows, 'Wizard Spells Known', 'Brine Lash'));
      check(`/${route} lists the class's levels under it`, listed(rows, 'Levels', 'Level 4'));
    }
    await page.getByRole('button', { name: 'Copy for support' }).click();
    await wait(500);
    const copied = await page.evaluate(() => navigator.clipboard.readText());
    check('Copy for support names the character', copied.startsWith(`Character ${id}`), copied.split('\n')[0]);
    check('Copy for support lists the feat by its path', copied.split('\n').includes('Feats › Tidebreaker'));

    await page.goto(`${BASE}/pages/dnd/5e/characters/999999/data`, { waitUntil: 'load' });
    await page.waitForFunction(() => !/^Loading/.test(document.getElementById('ledger').textContent));
    check('an unknown id says so', /no saved character with this number/.test(await ledgerText(page)));

    const draftRows = await dataRows(page, `${BASE}/pages/dnd/5e/character-data`);
    check("this browser's draft lists the same feat", listed(draftRows, 'Feats', 'Tidebreaker'));
    check('the app bundle was never requested', page.appRequests === 0,
          `${page.appRequests} request(s) for orcpub.js, each aborted`);
    const html = await (await page.request.get(`${BASE}/pages/dnd/5e/characters/${id}/data`)).text();
    check('the page itself does not ask for the app bundle', !/orcpub\.js/.test(html));

    const zoe = await browser.newContext();
    const zoeApp = await zoe.newPage();
    await logIn(zoeApp, 'zoe', 'washburne7');
    const zoePage = await noAppPage(zoe);
    const zoeRows = await dataRows(zoePage, `${BASE}/pages/dnd/5e/characters/${id}/data`);
    check('another account sees no rows', zoeRows.length === 0, `${zoeRows.length} rows`);
    check('another account is told only the owner can open it', /Only the owner can open this/.test(await ledgerText(zoePage)));

    const anon = await browser.newContext();
    const anonPage = await noAppPage(anon);
    const anonRows = await dataRows(anonPage, `${BASE}/pages/dnd/5e/characters/${id}/data`);
    check('a logged-out visitor sees no rows', anonRows.length === 0);
    check('a logged-out visitor is offered Log in', await anonPage.getByRole('link', { name: 'Log in' }).isVisible());
  } catch (e) {
    check('the run finished', false, e.message.split('\n')[0]);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})();
