// RESEARCH (asserts today's behaviour): can the app make a pick whose key starts with a digit? The
// save route refuses such keys (save_replace_research_test r9), so a character holding one could
// not be saved. Here the real app: a pack whose only feat is keyed :1st-strike is imported through
// My Content, then a level-4 wizard looks for it among the feats. TODAY the loader sets the item
// aside (library/invalid-keys), so it is never offered, and a save without it succeeds.
// Every change is a click, a select or the file input; app-db is read only.
//
// Needs the seeded server (kaylee / serenity99), `lein fig:build` and `lein garden once`:
//   NODE_PATH=/opt/node22/lib/node_modules node test/e2e/digit-key-save.js
const path = require('path');
const fs = require('fs');
const os = require('os');
const { chromium } = require('playwright');
const { BASE, findChrome, checker, dbAt, readyPage, dismissWhatsNew, clickTab } = require('./lib');

const PAK = 'Digit Pak';
const ORCBREW = `{"${PAK}" {:orcpub.dnd.e5/feats {:1st-strike {:key :1st-strike :name "1st Strike" :option-pack "${PAK}" :description "First."}}}}`;
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

async function myContent(page) {
  await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
  await wait(2500);
  await dismissWhatsNew(page);
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

(async () => {
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 1000 } })).newPage();
  const saves = [];
  page.on('response', r => { if (r.request().method() === 'POST' && /\/dnd\/5e\/characters$/.test(r.url())) saves.push(r.status()); });
  try {
    await logIn(page, 'kaylee', 'serenity99');
    const file = path.join(os.tmpdir(), 'Digit Pak.orcbrew');
    fs.writeFileSync(file, ORCBREW);
    await myContent(page);
    await page.locator('#app input[type=file]').first().setInputFiles(file);
    await wait(5000);
    const keep = page.getByRole('button', { name: /^keep “/i }).first();
    if (await keep.isVisible().catch(() => false)) { await keep.click(); await wait(3000); }
    const lib = await dbAt(page, `[:plugins "${PAK}"]`);
    check('TODAY: the digit-keyed feat is not in the library', !lib.includes(':1st-strike'), lib.slice(0, 120));
    const quarantined = await page.evaluate(() => localStorage.getItem('plugins:rejected') || '');
    check('TODAY: it was set aside in quarantine, not lost', quarantined.includes('1st-strike'), quarantined.slice(0, 120));

    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'load' });
    await wait(3000);
    await dismissWhatsNew(page);
    await clickTab(page, 'Class / Level');
    const row = page.locator('#app select.builder-option-dropdown.flex-grow-1').first();
    await row.selectOption('wizard'); await wait(2000);
    await row.locator('xpath=..').locator('select.w-100').selectOption('level-4'); await wait(2500);
    for (const tab of ['Class / Level', 'Ability Scores / Feats']) {
      await clickTab(page, tab);
      if (await clickCard(page, 'Feat')) break;
    }
    check('TODAY: the feat is never offered', !(await clickCard(page, '1st Strike')));
    await page.getByText(/^Save( New Character)?$/).first().click();
    await wait(5000);
    check('and the character saves without it', saves.length > 0 && saves.every(s => s === 200), JSON.stringify(saves));
  } catch (e) {
    check('the run finished', false, e.message.split('\n')[0]);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})();
