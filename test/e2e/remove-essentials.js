// RESEARCH (reports, asserts nothing): what does the app do with a character that has lost its
// class, or its ability scores? The character data page lets the owner remove any stored choice
// (character-rescue.md), so before it warns about these, measure what each removal leads to.
//
// Two plain characters (Human wizard 4) are built and saved by clicks. Through the data page,
// with orcpub.js blocked, one loses its class and the other its ability scores, and each is saved.
// Each is then opened in the app: its character page, then Edit. The script reports page errors,
// what the page shows, what the builder offers, and what Save does before and after the builder's
// own way back (Add Levels in Another Class; picking an ability score method).
// Every change is a click, a select or the file input; app-db is read only.
//
// Needs the seeded server (kaylee / serenity99) and both bundles (lein fig:build, lein fig:ledger):
//   NODE_PATH=/opt/node22/lib/node_modules node test/e2e/remove-essentials.js
const { chromium } = require('playwright');
const { BASE, findChrome, dbAt, readyPage, dismissWhatsNew, clickTab } = require('./lib');

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

async function logIn(page, user, pass) {
  await page.goto(`${BASE}/pages/login-page`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input');
  await page.locator('input').nth(0).fill(user);
  await page.locator('input').nth(1).fill(pass);
  await page.getByRole('button', { name: 'LOGIN' }).click({ force: true });
  await wait(5000);
  await readyPage(page);
}

async function buildPlain(page) {
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'load' });
  await wait(3000);
  await dismissWhatsNew(page);
  const nw = page.getByText('New', { exact: true }).first();
  if (await nw.isVisible().catch(() => false)) {
    await nw.click(); await wait(1000);
    const c = page.getByText(/^create new character$/i).first();
    if (await c.isVisible().catch(() => false)) { await c.click(); await wait(2000); }
  }
  await clickTab(page, 'Race');
  await clickCard(page, 'Human');
  await clickTab(page, 'Class / Level');
  const row = page.locator('#app select.builder-option-dropdown.flex-grow-1').first();
  await row.selectOption('wizard'); await wait(2000);
  await row.locator('xpath=..').locator('select.w-100').selectOption('level-4'); await wait(2500);
  return save(page);
}

async function removeOnDataPage(page, id, choice) {
  await page.goto(`${BASE}/pages/dnd/5e/characters/${id}/data`, { waitUntil: 'load' });
  await page.waitForFunction(() => !/^Loading/.test(document.getElementById('ledger').textContent), null, { timeout: 15000 });
  const btn = page.locator('#ledger tbody tr', { has: page.locator('td', { hasText: new RegExp(`^${choice}$`) }) }).locator('button');
  log(`   data page: ${await btn.innerText()} (${choice})`);
  await btn.click();
  await page.getByRole('button', { name: 'Save' }).click();
  await page.waitForFunction(() => /Saved\./.test(document.getElementById('ledger').textContent), null, { timeout: 15000 });
  const saved = await page.evaluate(async id => (await fetch(`/dnd/5e/characters/${id}`, { headers: { Accept: 'application/edn' } })).text(), id);
  log(`   saved copy: ${saved.length} chars; still has :class ${/:class/.test(saved)}, :ability-scores ${/:ability-scores/.test(saved)}`);
}

async function openInApp(page, id, errors, recover) {
  errors.length = 0;
  await page.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'load' });
  await wait(5000);
  await dismissWhatsNew(page);
  const loadPanel = await page.getByText(/won.t load|could not be read|couldn.t be read/i).count();
  const text = (await page.locator('#app').innerText().catch(() => '')).replace(/\s+/g, ' ');
  log(`   character page: won't-load panel ${loadPanel > 0}; page errors ${errors.length}; text: ${text.slice(0, 220)}`);
  const edit = page.getByText('Edit', { exact: true }).first();
  if (!(await edit.isVisible().catch(() => false))) { log('   no Edit button'); return; }
  await edit.click();
  await wait(4000);
  const btext = (await page.locator('#app').innerText().catch(() => '')).replace(/\s+/g, ' ');
  log(`   builder: page errors ${errors.length}${errors.length ? ' (' + errors.slice(0, 2).join(' || ') + ')' : ''}`);
  log(`   builder text: ${btext.slice(0, 260)}`);
  await clickTab(page, 'Class / Level').catch(() => {});
  const classes = await page.locator('#app select.builder-option-dropdown.flex-grow-1').count();
  log(`   Class / Level tab: ${classes} class selector(s); app-db classes ${await dbAt(page, '[:character :orcpub.entity/options :class]')}`);
  await clickTab(page, 'Ability Scores / Feats').catch(() => {});
  log(`   app-db ability scores ${(await dbAt(page, '[:character :orcpub.entity/options :ability-scores]')).slice(0, 120)}`);
  const saves = [];
  const onResp = r => { if (r.request().method() === 'POST' && /\/dnd\/5e\/characters$/.test(r.url())) saves.push(r.status()); };
  page.on('response', onResp);
  const told = async () => ((await page.locator('#app').innerText().catch(() => ''))
    .match(/You must provide values for all ability scores|Saved “[^”]*”|couldn't be saved[^.]*\./) || ['nothing'])[0];
  const clickSave = async label => {
    saves.length = 0;
    await page.getByText(/^Save$/).first().click();
    await wait(5000);
    log(`   ${label}: save requests ${JSON.stringify(saves)}; told: ${await told()}; page errors ${errors.length}`);
  };
  await clickSave('Save as opened');
  if (recover === 'class') {
    await clickTab(page, 'Class / Level');
    const add = page.getByText('Add Levels in Another Class').first();
    log(`   offered "Add Levels in Another Class": ${await add.isVisible().catch(() => false)}`);
    if (await add.isVisible().catch(() => false)) { await add.click(); await wait(2500); }
    log(`   classes after: ${(await dbAt(page, '[:character :orcpub.entity/options :class]')).slice(0, 120)}`);
  } else {
    await clickTab(page, 'Ability Scores / Feats');
    const names = await page.evaluate(() => { window.__h && 0; return [...document.querySelectorAll('#app span.f-w-b.f-s-1')].map(e => e.textContent.trim()).slice(0, 12); });
    log(`   ability tab offers: ${names.join(', ')}`);
    await clickCard(page, 'Standard Scores');
    log(`   ability scores after: ${(await dbAt(page, '[:character :orcpub.entity/options :ability-scores]')).slice(0, 120)}`);
  }
  await clickSave('Save after the fix in the builder');
  page.off('response', onResp);
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 1000 } });
  const app = await ctx.newPage();
  const errors = [];
  app.on('pageerror', e => errors.push(String(e).slice(0, 160)));
  const page = await ctx.newPage();
  await page.route('**/js/compiled/orcpub.js', r => r.abort());
  try {
    await logIn(app, 'kaylee', 'serenity99');
    for (const [label, choice, recover] of [['NO CLASS', 'Wizard', 'class'], ['NO ABILITY SCORES', 'Standard Scores', 'scores']]) {
      log(`\n================ ${label}`);
      const id = await buildPlain(app);
      log(`   built and saved as ${id}`);
      await app.goto('about:blank');
      await removeOnDataPage(page, id, choice);
      await openInApp(app, id, errors, recover);
      await app.goto('about:blank');
    }
  } catch (e) {
    log('ABORTED:', e.message.split('\n')[0]);
  } finally {
    await browser.close();
  }
})();
