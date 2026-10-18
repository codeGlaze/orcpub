// The whole life of homebrew that uses ability score increases and save grants, on the seeded
// server, by clicks: author a race, background and subclass in their builders; import a subrace and
// a feat from a file (explicit-set pools and feat spreads have no form); build a fighter from all of
// it, placing every floating increase; check the totals and that all six saves are proficient; save;
// reload and reopen; export both packs (the v2 envelope); delete them, check the character keeps
// its picks; import the exports back and check it is whole again; refuse a file from a newer
// format; delete the character.
//
//   DATOMIC_URL=datomic:mem://orcpub-e2e ORCPUB_ENV=dev SIGNATURE=e2e-test-signature PORT=8890 \
//   CSP_POLICY=none lein with-profile init-db run -m e2e-boot
// Prereqs:  lein fig:build && lein garden once
//   NODE_PATH=/opt/node22/lib/node_modules node test/e2e/asi-journey.js     Exit 0 = pass.
const path = require('path');
const fs = require('fs');
const os = require('os');
const { chromium } = require('playwright');
const { BASE, findChrome, checker, dbAt, readyPage, dismissWhatsNew, clickTab, login, pick, setClass,
        setLevel } = require('./lib');

const PAK = 'Journey Pack';
const EXTRAS = 'Journey Extras';
const DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'asi-journey-'));
const wait = ms => new Promise(r => setTimeout(r, ms));
const ABILS = ['STR', 'DEX', 'CON', 'INT', 'WIS', 'CHA'];
const LONG = ['Strength', 'Dexterity', 'Constitution', 'Intelligence', 'Wisdom', 'Charisma'];

// A subrace of the authored race, with an explicit-set pool and a fixed save; a feat with a spread
// and a :save rider on a group. Neither shape has a form, so they arrive as a file. The race's key
// is read back from the library: the builder tags it with the source (:journey-kin-jypk).
const extrasFile = raceKey => `{"${EXTRAS}" {` +
  `:orcpub.dnd.e5/subraces {:journey-deepborn {:key :journey-deepborn :name "Deepborn"` +
  ` :race ${raceKey} :option-pack "${EXTRAS}"` +
  ` :ability-increases [[1 #{:str :int}]] :save-proficiencies [[1 :int]]}}` +
  ` :orcpub.dnd.e5/feats {:journey-gift {:key :journey-gift :name "Journey Gift" :option-pack "${EXTRAS}"` +
  ` :description "A gift." :ability-increases [[1 :any] [1 :mental :save]]}}}}`;

async function goto(page, route) {
  await page.goto(`${BASE}${route}`, { waitUntil: 'load' });
  await wait(2500);
  await dismissWhatsNew(page);
}
const text = page => page.locator('#app').innerText();
const plugins = page => page.evaluate(() => localStorage.getItem('plugins') || '');

// One ability-increase or save row in the shared widget: [amount-or-count, target].
async function addRows(page, button, header, rows) {
  for (let i = 0; i < rows.length; i++) {
    await page.locator('#app button', { hasText: button }).first().click();
    await wait(300);
  }
  const box = page.locator('#app div.m-b-20', { has: page.locator(`div.f-s-18.f-w-b:text-is("${header}")`) }).last();
  const rs = box.locator('> div.flex.flex-wrap');
  for (let i = 0; i < rows.length; i++) {
    const [amount, target, save] = rows[i];
    await rs.nth(i).locator('select').nth(0).selectOption({ label: amount });
    await wait(150);
    await rs.nth(i).locator('select').nth(1).selectOption({ label: target });
    await wait(150);
    if (save) { await rs.nth(i).getByText('+ save prof').click(); await wait(200); }
  }
}
async function saveBuilder(page) {
  await page.locator('#app button:visible', { hasText: 'Save to Browser Storage' }).first().click();
  await wait(1500);
}
async function name(page, value) {
  await page.locator('#app input.input.h-40').nth(0).fill(value);
  await page.locator('#app input[placeholder="Default Option Source"]').fill(PAK);
}

// The ability grid's six totals, and which saves the sheet marks proficient.
async function totals(page) {
  await clickTab(page, 'Ability Scores / Feats');
  const t = (await text(page)).replace(/\n+/g, ' ');
  return (t.match(/total\s+(\d+)/g) || []).slice(0, 6).map(s => +s.replace(/\D/g, ''));
}
async function proficientSaves(page) {
  return page.evaluate(abbrs => abbrs.filter(a => {
    const rows = [...document.querySelectorAll('#app tr')].filter(tr =>
      [...tr.querySelectorAll('span.saving-throw-name')].some(s => s.textContent.trim() === a));
    const row = rows[rows.length - 1];
    return row && /f-w-b/.test(row.className) && !/opacity/.test(row.className);
  }), ABILS);
}
// Place a floating increase: the first unpicked dropdown in the widget breadcrumbed `crumb`.
async function place(page, crumb, ability) {
  const w = page.locator('div', { has: page.locator(`:text("Improvement: ${crumb}")`) })
    .filter({ has: page.locator('select') }).last();
  const sel = w.locator('select').filter({ has: page.locator('option', { hasText: 'choose' }) })
    .filter({ hasNot: page.locator('option[selected]:not([value=""])') });
  for (let i = 0; i < await sel.count(); i++) {
    const s = sel.nth(i);
    if ((await s.evaluate(e => e.options[e.selectedIndex].textContent)).includes('choose')) {
      await s.selectOption({ label: ability });
      await wait(1200);
      return true;
    }
  }
  return false;
}
async function serverCopy(page, id) {
  return page.evaluate(async id => {
    const u = localStorage.getItem('user') || '';
    const m = u.match(/:token "([^"]+)"/);
    const r = await fetch(`/dnd/5e/characters/${id}`,
      { headers: { Authorization: 'Token ' + (m ? m[1] : ''), Accept: 'application/edn' } });
    return r.status + ' ' + (await r.text());
  }, id);
}
async function exportPack(page, pak) {
  await goto(page, '/dnd/5e/my-content');
  const item = page.locator('#app .item-list-item').filter({ hasText: pak }).first();
  await item.locator('text=expand').first().click().catch(() => {});
  await wait(600);
  const [dl] = await Promise.all([
    page.waitForEvent('download', { timeout: 15000 }),
    page.locator('#app button:visible').filter({ hasText: /^export$/i }).first().click(),
  ]);
  const file = path.join(DIR, dl.suggestedFilename());
  await dl.saveAs(file);
  return file;
}
async function deletePack(page, pak) {
  await goto(page, '/dnd/5e/my-content');
  const item = page.locator('#app .item-list-item').filter({ hasText: pak }).first();
  await item.locator('text=expand').first().click().catch(() => {});
  await wait(600);
  await item.locator('.mc-source-actions button', { hasText: /^delete$/i }).first().click();
  await wait(1200);
  if (process.env.DEBUG) console.log('DEBUG delete prompt:', (await text(page)).replace(/\n+/g, ' ').slice(0, 400));
  const ok = page.getByRole('button', { name: /^(delete|yes|continue)/i })
    .filter({ hasNotText: /all sources/i }).last();
  if (await ok.isVisible().catch(() => false)) { await ok.click(); await wait(2000); }
}
async function importFile(page, file) {
  await goto(page, '/dnd/5e/my-content');
  await page.locator('#app input[type=file]').first().setInputFiles(file);
  await wait(5000);
  const keep = page.getByRole('button', { name: /^keep “/i }).first();
  if (await keep.isVisible().catch(() => false)) { await keep.click(); await wait(3000); }
}

(async () => {
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  let page;
  try {
    page = await login(browser);
    if (process.env.DEBUG) page.on('console', async m => {
      if (/two children with the same key/.test(m.text())) {
        const args = await Promise.all(m.args().map(a => a.jsonValue().catch(() => '?')));
        console.log('DEBUG warn:', JSON.stringify(args).slice(0, 1500));
      }
    });
    await page.context().setDefaultTimeout(20000);

    // ── 1. Author in the builders ──────────────────────────────────────────────────────────
    await goto(page, '/pages/dnd/5e/race-builder');
    await name(page, 'Journey Kin');
    await addRows(page, 'Add increase', 'Ability Score Increases',
                  [['+2', 'Constitution'], ['+1', 'Any (all six)', true]]);
    await addRows(page, 'Add save', 'Saving Throw Proficiencies', [['1', 'Mental (Int/Wis/Cha)']]);
    await saveBuilder(page);

    await goto(page, '/pages/dnd/5e/background-builder');
    await name(page, 'Journey Origin');
    await addRows(page, 'Add increase', 'Ability Score Increases',
                  [['+2', 'Any (all six)'], ['+1', 'Any (all six)']]);
    await saveBuilder(page);

    await goto(page, '/pages/dnd/5e/subclass-builder');
    await page.locator('#app :text("Ability Score Increases (non-standard)")').first().click();
    await wait(400);
    await name(page, 'Journey Path');
    const classSel = page.locator('#app select').filter({ has: page.locator('option', { hasText: 'Fighter' }) }).first();
    await classSel.selectOption({ label: 'Fighter' });
    await wait(400);
    await addRows(page, 'Add increase', 'Ability Score Increases', [['+1', 'Wisdom']]);
    await addRows(page, 'Add save', 'Saving Throw Proficiencies', [['1', 'Charisma']]);
    await saveBuilder(page);

    const lib = await plugins(page);
    check('race stored as a terse spread with the rider, and its save choice',
          /:ability-increases \[\[2 :con\] \[1 :any :save\]\]/.test(lib) && /:save-proficiencies \[\[1 :mental\]\]/.test(lib));
    check('background stored as [[2 :any] [1 :any]]', /:ability-increases \[\[2 :any\] \[1 :any\]\]/.test(lib));
    check('subclass stored with its increase and its save',
          /:ability-increases \[\[1 :wis\]\]/.test(lib) && /:save-proficiencies \[\[1 :cha\]\]/.test(lib));

    // ── 2. The shapes without a form, from a file ─────────────────────────────────────────────
    const raceKey = (lib.match(/:orcpub\.dnd\.e5\/races \{(:journey-kin[a-z-]*)/) || [])[1];
    check('the race has a key', !!raceKey, raceKey);
    const extrasIn = path.join(DIR, `${EXTRAS}.orcbrew`);
    fs.writeFileSync(extrasIn, extrasFile(raceKey));
    await importFile(page, extrasIn);
    check('the extras file imports (subrace and feat)', /journey-deepborn[\s\S]*journey-gift|journey-gift[\s\S]*journey-deepborn/.test(await plugins(page)));

    // ── 3. A fighter built from all of it ─────────────────────────────────────────────────────
    await goto(page, '/pages/dnd/5e/character-builder');
    await readyPage(page);
    await setClass(page, 0, 'fighter');
    await setLevel(page, 0, 3);
    const base = await totals(page);
    check('baseline totals read', base.length === 6, JSON.stringify(base));

    check('race picked', await pick(page, 'Race', 'Race', 'Journey Kin'));
    check('subrace picked', await pick(page, 'Race', 'Subrace', 'Deepborn'));
    check('background picked', await pick(page, 'Background', 'Background', 'Journey Origin'));
    check('subclass picked', await pick(page, 'Class / Level', 'Martial Archetype', 'Journey Path'));
    await clickTab(page, 'Ability Scores / Feats');
    await page.locator('#app span.pointer', { has: page.locator('svg, img') }).first().click(); // homebrew mug
    await wait(800);
    const feat = page.locator('#app div.b-orange', { hasText: 'Journey Gift' }).first();
    await feat.scrollIntoViewIfNeeded();
    await feat.click();
    await wait(1500);

    if (process.env.DEBUG) console.log('DEBUG crumbs:', JSON.stringify([...(await text(page)).matchAll(/Improvement:\s*([^\n|]+)/g)].map(m => m[1].trim())));
    const placed = [
      await place(page, 'Race - Journey Kin - Subrace - Deepborn', 'Strength'),
      await place(page, 'Race - Journey Kin', 'Dexterity'),
      await place(page, 'Background - Journey Origin', 'Wisdom'),
      await place(page, 'Background - Journey Origin', 'Charisma'),
      await place(page, 'Feats - Journey Gift', 'Intelligence'),
      await place(page, 'Feats - Journey Gift', 'Charisma'),
    ];
    check('every floating increase placed (6)', placed.every(Boolean), JSON.stringify(placed));
    // the race's save choice is an ordinary selection on Proficiencies
    const saveChoice = await pick(page, 'Proficiencies', 'Saving Throw Proficiency', 'Wisdom');
    check('the race save choice picked (Wisdom)', saveChoice);
    await wait(1500);

    // STR +1 sub, DEX +1 race, CON +2 race, INT +1 feat, WIS +2 bg +1 subclass, CHA +1 bg +1 feat
    const want = [1, 1, 2, 1, 3, 2];
    let got = await totals(page);
    for (let i = 0; i < 10 && JSON.stringify(got.map((v, k) => v - base[k])) !== JSON.stringify(want); i++) {
      await wait(500); got = await totals(page);
    }
    const delta = got.map((v, k) => v - base[k]);
    check('every increase lands where it was placed (+1 +1 +2 +1 +3 +2)',
          JSON.stringify(delta) === JSON.stringify(want), `${JSON.stringify(base)} -> ${JSON.stringify(got)}`);
    let saves = await proficientSaves(page);
    check('all six saves proficient (fighter STR CON; DEX rider; WIS choice; INT subrace; CHA subclass and feat)',
          saves.length === 6, saves.join(' '));

    // ── 4. Save, reload, reopen ───────────────────────────────────────────────────────────────
    await page.getByText(/^Save( New Character)?$/).first().click();
    await wait(4000);
    const id = (await dbAt(page, '[:character :db/id]')).trim();
    const saved = await serverCopy(page, id);
    // stored picks: race asi-1-dex, subrace asi-0-str, background asi-0-wis asi-1-cha, feat asi-0-int
    // asi-1-cha, the race's save choice save-0-wis. A subrace pick is stored by name (:deepborn).
    const PICKS = ['asi-1-dex', 'asi-0-str', 'asi-0-wis', 'asi-0-int', 'asi-1-cha', 'save-0-wis',
                   'deepborn', 'journey-gift', 'journey-path'];
    check('saved to the server with every pick', /^200/.test(saved) && PICKS.every(k => saved.includes(':' + k)),
          PICKS.filter(k => !saved.includes(':' + k)).join(' ') || id);

    await page.reload({ waitUntil: 'load' });
    await wait(4000);
    got = await totals(page);
    check('after a reload of the builder, the same totals', JSON.stringify(got.map((v, k) => v - base[k])) === JSON.stringify(want), JSON.stringify(got));
    await goto(page, `/pages/dnd/5e/characters/${id}`);
    await wait(2000);
    const sheet = (await text(page)).replace(/\n+/g, ' ');
    check('the sheet opens with no missing-content notice', !/missing|not loaded|isn.t loaded/i.test(sheet));
    await page.getByText('Edit', { exact: true }).first().click();
    await wait(4000);
    got = await totals(page);
    check('reopened from the server, the same totals', JSON.stringify(got.map((v, k) => v - base[k])) === JSON.stringify(want), JSON.stringify(got));
    saves = await proficientSaves(page);
    check('reopened, all six saves still proficient', saves.length === 6, saves.join(' '));

    // ── 5. Export both packs ──────────────────────────────────────────────────────────────────
    const out1 = await exportPack(page, PAK);
    const out2 = await exportPack(page, EXTRAS);
    const t1 = fs.readFileSync(out1, 'utf8'), t2 = fs.readFileSync(out2, 'utf8');
    check('exports keep the .orcbrew name', /\.orcbrew$/.test(out1) && /\.orcbrew$/.test(out2), `${path.basename(out1)}, ${path.basename(out2)}`);
    check('the authored pack exports in the v2 envelope, naming what it needs',
          /:orcbrew\/format-version 2/.test(t1) && /:ability-increase-spread/.test(t1) && /:save-proficiencies/.test(t1));
    check('the extras pack exports in the v2 envelope', /:orcbrew\/format-version 2/.test(t2));

    // ── 6. Delete both packs: the character keeps its picks ────────────────────────────────────
    // the extras first: deleting the pack a subrace points at asks before it leaves it dangling
    await deletePack(page, EXTRAS);
    await deletePack(page, PAK);
    const after = await plugins(page);
    if (process.env.DEBUG) console.log('DEBUG after delete:', (after.match(/"[^"]+" \{/g) || []).join(' '), (await text(page)).replace(/\n+/g, ' ').slice(0, 600));
    check('both packs are gone from the library', !after.includes('journey-kin') && !after.includes('journey-gift'));
    await goto(page, `/pages/dnd/5e/characters/${id}`);
    await wait(8000);
    const kept = await serverCopy(page, id);
    check('with the packs gone, the server copy still holds every pick',
          [...PICKS, 'journey-kin', 'journey-origin'].every(k => kept.includes(':' + k)));

    // ── 7. Import the exports back ────────────────────────────────────────────────────────────
    await importFile(page, out1);
    await importFile(page, out2);
    const back = await plugins(page);
    check('both packs are back, unwrapped (no envelope stored)',
          back.includes('journey-kin') && back.includes('journey-gift') && !back.includes('format-version'));
    await goto(page, `/pages/dnd/5e/characters/${id}`);
    await page.getByText('Edit', { exact: true }).first().click();
    await wait(4000);
    got = await totals(page);
    check('after deleting and reimporting, the same totals', JSON.stringify(got.map((v, k) => v - base[k])) === JSON.stringify(want), JSON.stringify(got));
    saves = await proficientSaves(page);
    check('and all six saves', saves.length === 6, saves.join(' '));

    check('no uncaught errors', page.errors.filter(e => !/401|404/.test(e)).length === 0,
          page.errors.join(' | ').slice(0, 300));

    // ── 8. A file from a newer format is refused, not loaded ───────────────────────────────────
    const future = path.join(DIR, 'Future Pack.orcbrew');
    fs.writeFileSync(future, `{:orcbrew/format-version 99 :orcbrew/requires [:something-new] :orcbrew/content {"Future Pack" {:orcpub.dnd.e5/races {:x {:key :x :name "X" :option-pack "Future Pack"}}}}}`);
    await importFile(page, future);
    const ft = (await text(page)).replace(/\n+/g, ' ');
    check('a newer-format file is refused with a message', /newer format/i.test(ft) && !(await plugins(page)).includes('Future Pack'));

    // ── 9. Delete the character ───────────────────────────────────────────────────────────────
    await goto(page, '/pages/dnd/5e/characters');
    const row = page.locator('#app .item-list-item', { hasText: 'Journey Kin' }).first();
    await row.locator('.list-character-summary').first().click();
    await wait(1500);
    await row.locator('button', { hasText: /^delete$/i }).first().click();
    await wait(800);
    await row.locator('.link-button', { hasText: /^delete$/i }).first().click();
    await wait(3000);
    check('the character is deleted', !/^200/.test(await serverCopy(page, id)));
  } catch (e) {
    check('ran to completion', false, e.message.split('\n')[0]);
    if (page) await page.screenshot({ path: path.join(DIR, 'abort.png'), fullPage: true }).catch(() => {});
    console.log('screens in', DIR);
  } finally {
    await browser.close();
    process.exit(report() ? 1 : 0);
  }
})().catch(e => { console.error(e); process.exit(2); });
