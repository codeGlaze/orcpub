// Shared setup for the character-rescue e2e scripts (character-rescue.md): the "Tide Pak" homebrew
// (race Tidefolk, feat Tidebreaker, wizard spell Brine Lash), a level-4 wizard built with it by
// clicks only, and login. Every change goes through the UI; app-db is only read.
const path = require('path');
const fs = require('fs');
const os = require('os');
const { BASE, dbAt, readyPage, dismissWhatsNew, clickTab } = require('./lib');

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


module.exports = { PAK, KEYS, wait, log, HELPERS, clickCard, save, myContent, importPak, build, logIn };
