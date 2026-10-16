// Missing content is never cleared on its own. A saved character uses a homebrew race, feat and
// spell; the pack is switched off; the character is opened on its sheet and in the builder, and
// left past the settle and the autosave (7.5 s). The server's copy must still hold every homebrew
// pick: clearing one is something the player does (Choices), never the app.
//
// Runs against the seeded server (kaylee / serenity99):
//   DATOMIC_URL=datomic:mem://orcpub-e2e ORCPUB_ENV=dev SIGNATURE=e2e-test-signature PORT=8890 \
//   CSP_POLICY=none lein with-profile init-db run -m e2e-boot
// Prereqs:  lein fig:build && lein garden once
//   NODE_PATH=/opt/node22/lib/node_modules node test/e2e/missing-content-kept.js     Exit 0 = pass.
const path = require('path');
const fs = require('fs');
const os = require('os');
const { chromium } = require('playwright');
const { BASE, findChrome, checker, dbAt, readyPage, dismissWhatsNew, clickTab, login } = require('./lib');

const PAK = 'Tide Pak';
const ORCBREW = `{"${PAK}" {` +
  `:orcpub.dnd.e5/races {:tidefolk {:key :tidefolk :name "Tidefolk" :option-pack "${PAK}" :speed 30 :size :medium` +
  ` :description "People of the shallows."}}` +
  ` :orcpub.dnd.e5/feats {:tidebreaker {:key :tidebreaker :name "Tidebreaker" :option-pack "${PAK}"` +
  ` :description "You break the tide."}}` +
  ` :orcpub.dnd.e5/spells {:brine-lash {:key :brine-lash :name "Brine Lash" :option-pack "${PAK}" :level 1` +
  ` :school "evocation" :casting-time "1 action" :range "30 feet" :duration "Instantaneous"` +
  ` :components {:verbal true :somatic true} :description "A whip of seawater." :spell-lists {:wizard true}}}}}`;
const KEYS = ['tidefolk', 'tidebreaker', 'brine-lash'];
const wait = ms => new Promise(r => setTimeout(r, ms));
const holdsAll = s => KEYS.every(k => s.includes(':' + k));
const missing = s => KEYS.filter(k => !s.includes(':' + k)).join(', ') || 'none';

async function serverCopy(page, id) {
  return page.evaluate(async id => {
    const u = localStorage.getItem('user') || '';
    const m = u.match(/:token "([^"]+)"/) || u.match(/"token"\s*:\s*"([^"]+)"/);
    const r = await fetch(`/dnd/5e/characters/${id}`,
      { headers: { Authorization: 'Token ' + (m ? m[1] : ''), Accept: 'application/edn' } });
    return r.status + ' ' + (await r.text());
  }, id);
}
async function myContent(page) {
  await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
  await wait(2500);
  await dismissWhatsNew(page);
}
async function clickCard(page, name) {
  const card = page.locator('#app div.p-10.b-1.b-rad-5.m-5.b-orange')
    .filter({ has: page.locator('span.f-w-b.f-s-1', { hasText: new RegExp(`^(\\d+ - )?${name}$`) }) }).first();
  if (!(await card.count())) return false;
  await card.scrollIntoViewIfNeeded();
  await card.click();
  await wait(1200);
  return true;
}

(async () => {
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  try {
    const page = await login(browser);
    // The pack, through My Content's real file input.
    const file = path.join(os.tmpdir(), `${PAK}.orcbrew`);
    fs.writeFileSync(file, ORCBREW);
    await myContent(page);
    await page.locator('#app input[type=file]').first().setInputFiles(file);
    await wait(5000);
    const keep = page.getByRole('button', { name: /^keep “/i }).first();
    if (await keep.isVisible().catch(() => false)) { await keep.click(); await wait(3000); }
    check('the pack is imported', holdsAll(await dbAt(page, `[:plugins "${PAK}"]`)));

    // A wizard 4 using the race, the feat and the spell, built by clicks and saved.
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'load' });
    await wait(3000); await readyPage(page);
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
    await page.getByText(/^Save( New Character)?$/).first().click();
    await wait(4000);
    const id = (await dbAt(page, '[:character :db/id]')).trim();
    const saved = await serverCopy(page, id);
    if (!check('saved with all three homebrew picks', /^200/.test(saved) && holdsAll(saved), missing(saved))) return;

    // The pack switched off.
    await myContent(page);
    await page.locator('.item-list-item .dev-mode-switch:not(.compact)').first().click();
    await wait(2000);
    const ok = page.getByRole('button', { name: /turn off|disable|anyway|continue|yes/i }).first();
    if (await ok.isVisible().catch(() => false)) { await ok.click(); await wait(2500); }
    check('the pack is off', /true/.test(await dbAt(page, `[:plugins "${PAK}" :disabled?]`)));

    // Its sheet, then the builder, each left past the settle and the autosave.
    await page.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'load' });
    await wait(12000);
    let copy = await serverCopy(page, id);
    check('after the sheet, the server still holds every homebrew pick', holdsAll(copy), missing(copy));
    await page.getByText('Edit', { exact: true }).first().click();
    await wait(12000);
    copy = await serverCopy(page, id);
    check('after the builder, the server still holds every homebrew pick', holdsAll(copy), missing(copy));
    check('and the builder\'s copy holds them too',
      holdsAll(await dbAt(page, '[:character :orcpub.entity/options]')));
    await page.reload({ waitUntil: 'load' });
    await wait(12000);
    copy = await serverCopy(page, id);
    check('after a reload of the builder, still every pick', holdsAll(copy), missing(copy));
  } catch (e) {
    check('ran to completion', false, e.message.split('\n')[0]);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
