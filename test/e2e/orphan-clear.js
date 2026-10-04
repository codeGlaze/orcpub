// RESEARCH (reports, asserts nothing): can a player clear a pick whose homebrew is gone?
//
// A small pack ("Tide Pak": race Tidefolk, feat Tidebreaker, wizard spell Brine Lash) is imported
// through My Content's real file input. A level-4 wizard is built by clicks only: the race, the
// feat through Ability Score Improvement or Feat, the spell in the wizard's spell picks. It is
// saved. Then the pack is made unavailable the way a player would:
//   phase A  - the source's on/off switch in My Content (disable)
//   phase B  - the source's "delete" button (a second character, built the same way)
// The character is reopened through its page and Edit. For race, feat and spell the script
// reports what the builder shows (Missing Content warning text and what it offers; whether the
// pick renders as a card), then tries every UI way of clearing that pick, and after each attempt
// reads app-db and the saved copy (saved by the Save button, read back from the server).
//
// app-db, localStorage and the server copy are READ only; every change is a click, a select or
// the file input.
//
// Needs a logged-in user, so it runs against the seeded server (kaylee / serenity99):
//   DATOMIC_URL=datomic:mem://orcpub-e2e ORCPUB_ENV=dev SIGNATURE=e2e-test-signature PORT=8890 \
//   CSP_POLICY=none lein with-profile init-db run -m e2e-boot
// Prereqs:  lein fig:build && lein garden once
//   NODE_PATH=/opt/node22/lib/node_modules node test/e2e/orphan-clear.js
const path = require('path');
const fs = require('fs');
const os = require('os');
const { chromium } = require('playwright');
const { BASE, findChrome, dbAt, readyPage, dismissWhatsNew, clickTab } = require('./lib');

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

const log = (...a) => console.log(a.join(' '));
const wait = ms => new Promise(r => setTimeout(r, ms));

// ---------------------------------------------------------------- reading (observe only)
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
const opts = page => dbAt(page, '[:character :orcpub.entity/options]');
const has = (s, k) => s.includes(':' + k);
async function state(page, label) {
  const o = await opts(page);
  const ls = await page.evaluate(() => localStorage.getItem('character') || '');
  const line = Object.entries(KEYS).map(([n, k]) => `${n}:${has(o, k) ? 'HELD' : 'gone'}`).join(' ');
  const lsl = Object.entries(KEYS).map(([n, k]) => `${n}:${has(ls, k) ? 'HELD' : 'gone'}`).join(' ');
  log(`   [${label}] app-db ${line} | localStorage draft ${lsl}`);
  if (/built|reopened/.test(label)) {
    for (const [n, k] of Object.entries(KEYS)) {
      const i = o.indexOf(':' + k);
      if (i >= 0) log(`      ${n} stored at: ...${o.slice(Math.max(0, i - 110), i + k.length + 1).replace(/\s+/g, ' ')}`);
    }
  }
  return o;
}
async function savedCopy(page, id) {
  const txt = await page.evaluate(async id => {
    const u = localStorage.getItem('user') || '';
    const m = u.match(/:token "([^"]+)"/) || u.match(/"token"\s*:\s*"([^"]+)"/);
    const r = await fetch(`/dnd/5e/characters/${id}`, { headers: { Authorization: 'Token ' + (m ? m[1] : ''), Accept: 'application/edn' } });
    return r.status + ' ' + (await r.text());
  }, id);
  const line = Object.entries(KEYS).map(([n, k]) => `${n}:${has(txt, k) ? 'HELD' : 'gone'}`).join(' ');
  log(`   saved copy (${txt.slice(0, 3)}): ${line}`);
  return txt;
}
async function pageDump(page, tab) {
  await clickTab(page, tab);
  await page.evaluate(HELPERS);
  const d = await page.evaluate(() => window.__dump());
  log(`   -- ${tab}:`);
  d.forEach(l => log('      ' + l));
  const names = await page.evaluate(() => window.__cards().map(window.__name).filter(n => /tide|brine/i.test(n)));
  log(`      cards naming the pack: ${names.length ? names.join(', ') : 'none'}`);
  const txt = await page.locator('#app').innerText();
  const hits = txt.split('\n').filter(l => /tide|brine/i.test(l)).slice(0, 6);
  log(`      any text naming the pack: ${hits.length ? hits.map(h => JSON.stringify(h.trim())).join(' ') : 'none'}`);
}
async function missingWarning(page) {
  const w = page.locator('#missing-content-warning');
  if (!(await w.count())) { log('   Missing Content warning: NOT SHOWN'); return []; }
  const head = (await w.locator('div.flex.align-items-c.pointer').first().innerText()).trim();
  if (!(await page.locator('#missing-content-details').count())) {
    await w.locator('div.flex.align-items-c.pointer').first().click();
    await wait(600);
  }
  const body = (await page.locator('#missing-content-details').innerText()).replace(/\n+/g, ' | ');
  log(`   Missing Content warning: "${head}"`);
  log(`      expanded: ${body}`);
  const buttons = await page.locator('#missing-content-details button').allInnerTexts();
  log(`      buttons offered: ${buttons.length ? buttons.join(', ') : 'none'}`);
  return buttons;
}

// ---------------------------------------------------------------- driving (clicks only)
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
async function openCharacter(page, id) {
  await page.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'load' });
  await wait(4000);
  await dismissWhatsNew(page);
  await page.getByText('Edit', { exact: true }).first().click();
  await wait(3500);
}
async function myContent(page) {
  await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
  await wait(2500);
  await dismissWhatsNew(page);
}
// Anything the app pops to ask (a "still used" confirmation, a conflict screen): report it, and
// answer with the button whose label matches `rx`.
async function answerDialog(page, rx, label) {
  const txt = await page.evaluate(() => {
    const d = [...document.querySelectorAll('.decision-callout, .modal, [role=dialog], .mc-confirmbar, .still-used')]
      .filter(e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; });
    return d.map(e => e.innerText.replace(/\s+/g, ' ').trim()).join(' || ');
  });
  log(`   ${label} dialog: ${txt || 'none'}`);
  if (!txt) return;
  const b = page.getByRole('button', { name: rx }).first();
  if (await b.isVisible().catch(() => false)) { log(`   answered: "${(await b.innerText()).trim()}"`); await b.click(); await wait(2500); }
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
  await state(page, 'built');
  for (const t of ['Race', 'Ability Scores / Feats', 'Spells']) await pageDump(page, t);
  const id = await save(page);
  log(`   saved as ${id}`);
  await savedCopy(page, id);
  return id;
}

// Every UI way of clearing each pick, in order of least side effect. After each: app-db, Save,
// and the server's copy.
async function attempts(page, id) {
  const after = async label => { await state(page, label); await save(page); await savedCopy(page, id); };

  log('\n   RACE');
  await pageDump(page, 'Race');
  await clickCard(page, 'Tidefolk');                 // deselect by clicking it (if it renders)
  await clickCard(page, 'Human');                    // pick something else
  await after('race: after picking Human');

  log('\n   FEAT');
  await pageDump(page, 'Ability Scores / Feats');
  await clickCard(page, 'Tidebreaker');              // deselect (if it renders)
  const other = await page.evaluate(() => {
    const s = window.__secs().find(s => window.__title(s) === 'Feats');
    const c = s && [...s.querySelectorAll('div.p-10.b-1.b-rad-5.m-5.b-orange')].find(window.__vis);
    return c ? window.__name(c) + ' {' + c.className + '}' : null;
  }).then(r => { if (r) log(`   the other feat on offer: ${r}`); return r && r.split(' {')[0]; });
  if (other) await clickCard(page, other);           // pick a different feat in the same slot
  await after(`feat: after clicking ${other}`);
  log(`      ${other} stored: ${(await opts(page)).includes(':' + (other || '').toLowerCase())}; [:feats] = ${await dbAt(page, '[:character :orcpub.entity/options :feats]')}`);
  await pageDump(page, 'Ability Scores / Feats');
  // Switch the whole slot from Feat to the ability increase, then back to Feat.
  await clickCard(page, 'Ability Score Improvement');
  await after('feat: after switching the slot to Ability Score Improvement');
  log(`      the slot now holds: ${(await opts(page)).match(/:asi-or-feat \{[^}]*\}/)}`);
  await clickCard(page, 'Feat');
  await after('feat: after switching back to Feat');
  await pageDump(page, 'Ability Scores / Feats');

  log('\n   SPELL');
  await pageDump(page, 'Spells');
  await clickCard(page, 'Brine Lash');               // deselect (if it renders)
  await clickCard(page, 'Magic Missile');            // pick a different spell
  await after('spell: after clicking Magic Missile');
  // Fill the rest of the slot: does the orphan hold one of its places?
  const spellCount = () => page.evaluate(() => {
    const s = window.__secs().find(s => window.__title(s) === 'Wizard Spells Known');
    return s ? s.querySelector('div.p-5.f-s-16').textContent.replace(/\s+/g, ' ').trim() : 'no section';
  });
  let n = 0;
  for (let i = 0; i < 20; i++) {
    const c = await spellCount();
    if (!/remaining/.test(c)) break;
    const nm = await page.evaluate(() => {
      const s = window.__secs().find(s => window.__title(s) === 'Wizard Spells Known');
      const c = [...s.querySelectorAll('div.p-10.b-1.b-rad-5.m-5.b-orange')].find(c => window.__vis(c) && !c.classList.contains('b-w-5') && c.classList.contains('pointer'));
      return c ? window.__name(c).replace(/^\d+ - /, '') : null;
    });
    if (!nm) break;
    await clickCard(page, nm); n++;
  }
  log(`   clicked ${n} more spells; counter now: "${await spellCount()}"`);
  const free = await page.evaluate(() => {
    const s = window.__secs().find(s => window.__title(s) === 'Wizard Spells Known');
    const all = [...s.querySelectorAll('div.p-10.b-1.b-rad-5.m-5.b-orange')].filter(window.__vis);
    return `${all.filter(c => c.classList.contains('b-w-5')).length} selected cards, ${all.filter(c => !c.classList.contains('b-w-5') && c.classList.contains('pointer')).length} unselected still clickable, ${all.filter(c => c.classList.contains('opacity-5')).length} dimmed`;
  });
  log(`   slot: ${free}`);
  await after('spell: slot filled around the orphan');
  await pageDump(page, 'Spells');
  // Lower the class to 1 and back: does the spell slot shed the orphan?
  await clickTab(page, 'Class / Level');
  const row = page.locator('#app select.builder-option-dropdown.flex-grow-1').first();
  await row.locator('xpath=..').locator('select.w-100').selectOption('level-1'); await wait(2500);
  await after('spell: after lowering wizard to 1');
  await row.locator('xpath=..').locator('select.w-100').selectOption('level-4'); await wait(2500);
  await after('spell: after raising wizard back to 4');
  // Replace the class: does the spell (and the class's feat slot) go with it?
  await row.selectOption('fighter'); await wait(2500);
  await after('class swapped wizard -> fighter');
  log('\n   warning at the end:');
  await missingWarning(page);
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 1000 } })).newPage();
  page.on('pageerror', e => log('   pageerror:', String(e).slice(0, 160)));
  page.on('console', m => { if (/Missing content/i.test(m.text())) log('   console:', m.text().slice(0, 200)); });
  try {
    await page.goto(`${BASE}/pages/login-page`, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('input');
    await page.locator('input').nth(0).fill('kaylee');
    await page.locator('input').nth(1).fill('serenity99');
    await page.getByRole('button', { name: 'LOGIN' }).click({ force: true });
    await wait(5000);
    await readyPage(page);
    log('== import');
    await importPak(page);

    for (const phase of ['disable', 'delete']) {
      log(`\n================ PHASE ${phase.toUpperCase()}`);
      if (phase === 'delete') {
        await myContent(page);
        const on = await dbAt(page, `[:plugins "${PAK}" :disabled?]`);
        if (/true/.test(on)) { await page.locator('.item-list-item .dev-mode-switch:not(.compact)').first().click(); await wait(2000); }
        log(`   pack re-enabled: disabled? = ${await dbAt(page, `[:plugins "${PAK}" :disabled?]`)}`);
      }
      const id = await build(page);
      await myContent(page);
      if (phase === 'disable') {
        await page.locator('.item-list-item .dev-mode-switch:not(.compact)').first().click();
        await wait(2000);
        await answerDialog(page, /turn off|disable|anyway|continue|yes/i, 'disable');
        log(`   library: disabled? = ${await dbAt(page, `[:plugins "${PAK}" :disabled?]`)}`);
      } else {
        let del = page.locator('.mc-source-actions button', { hasText: /^delete$/i }).first();
        if (!(await del.isVisible().catch(() => false))) {
          log(`   (delete not visible; visible buttons: ${(await page.locator('#app button:visible').allInnerTexts()).join(' / ').slice(0, 300)})`);
          await page.getByText(PAK, { exact: true }).first().click();
          await wait(1000);
          del = page.locator('.mc-source-actions button', { hasText: /^delete$/i }).first();
        }
        await del.click();
        await wait(2000);
        await answerDialog(page, /delete|anyway|continue|yes/i, 'delete');
        log(`   library holds the pack: ${(await dbAt(page, `[:plugins "${PAK}"]`)) !== 'nil'}`);
      }
      await openCharacter(page, id);
      await page.evaluate(HELPERS);
      await state(page, 'reopened');
      await missingWarning(page);
      await attempts(page, id);
    }
  } catch (e) {
    log('ABORTED:', e.message.split('\n')[0]);
  } finally {
    await browser.close();
  }
})().catch(e => { console.error(e); process.exit(2); });
