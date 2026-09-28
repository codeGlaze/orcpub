// After an import renames one of the library's existing items, a saved character that used it is
// asked once, by a banner in the builder, which one it meant (owner's decision Q3,
// homebrew-keys-design.md step 7).
//
// Needs a logged-in user, so it runs against the seeded server, not `lein e2e-server`:
//   DATOMIC_URL=datomic:mem://orcpub-e2e ORCPUB_ENV=dev SIGNATURE=e2e-test-signature PORT=8890 \
//   CSP_POLICY=none lein with-profile init-db run -m e2e-boot      (seeds kaylee / serenity99)
// CSP_POLICY=none lets that server load the dev build, whose names the setup and the app-db reader
// use; the production bundle renames them.
// Prereqs:  lein fig:build && lein garden once
const path = require('path');
const fs = require('fs');
const os = require('os');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, checker, dbAt, dismissCookieBar, dismissWhatsNew } = require('./lib');

const HOME = '{"Home Pak" {:orcpub.dnd.e5/races {:tidefolk {:name "Tidefolk" :key :tidefolk :option-pack "Home Pak"' +
  ' :description "The library\'s own."}}}}';
const IMPORT = '{"Tide Pak" {:orcpub.dnd.e5/races {:tidefolk {:name "Tidefolk" :key :tidefolk :option-pack "Tide Pak"' +
  ' :description "The imported one."}}}}';
const CHARACTER = '{:orcpub.entity/options {:race {:orcpub.entity/key :tidefolk}' +
  ' :ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value' +
  ' {:orcpub.dnd.e5.character/str 10 :orcpub.dnd.e5.character/dex 10 :orcpub.dnd.e5.character/con 10' +
  ' :orcpub.dnd.e5.character/int 10 :orcpub.dnd.e5.character/wis 10 :orcpub.dnd.e5.character/cha 10}}}' +
  ' :orcpub.entity/values {:orcpub.dnd.e5.character/character-name "Tide Tester"}}';
const raceOf = page => dbAt(page, '[:character :orcpub.entity/options :race :orcpub.entity/key]');

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 1000 } })).newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(String(e).slice(0, 160)));
  try {
    await page.goto(`${BASE}/pages/login-page`, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('input');
    await page.locator('input').nth(0).fill('kaylee');
    await page.locator('input').nth(1).fill('serenity99');
    await page.getByRole('button', { name: 'LOGIN' }).click({ force: true });
    await page.waitForTimeout(5000);
    check('signed in', await page.evaluate(() => !!localStorage.getItem('user')));

    // What the browser holds: a library with a race, and a character draft using it.
    await page.evaluate(([lib, ch]) => {
      localStorage.setItem('plugins', lib);
      ['plugins:rev', 'plugins:relinks', 'plugins:pre-fix', 'plugins:pre-fix-at'].forEach(k => localStorage.removeItem(k));
      const strict = window.orcpub.dnd.e5.character.to_strict(window.cljs.reader.read_string(ch));
      localStorage.setItem('character', window.cljs.core.pr_str(strict));
    }, [HOME, CHARACTER]);
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'load' });
    await page.waitForTimeout(3000);
    await dismissWhatsNew(page);
    check('the draft uses the library\'s race', (await raceOf(page)) === ':tidefolk', await raceOf(page));
    await page.getByText('Save New Character', { exact: true }).first().click();
    await page.waitForTimeout(4000);
    const id = (await dbAt(page, '[:character :db/id]')).trim();
    check('the character was saved', /^\d+$/.test(id), id);

    // Import a pack holding its own race under the same key; rename the library's one.
    const file = path.join(os.tmpdir(), 'Tide Pak.orcbrew');
    fs.writeFileSync(file, IMPORT);
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
    await page.waitForTimeout(2000);
    await page.locator('#app input[type=file]').first().setInputFiles(file);
    await page.waitForTimeout(2500);
    let option = page.getByText(/rename your existing one to/i).first();
    if (!(await option.isVisible().catch(() => false))) {
      await page.getByRole('button', { name: /review \/ change/i }).click().catch(() => {});
      await page.waitForTimeout(800);
      option = page.getByText(/rename your existing one to/i).first();
    }
    check('the conflict screen offers renaming the existing one', await option.isVisible().catch(() => false));
    await option.click();
    await page.getByRole('button', { name: /apply/i }).first().click();
    await page.waitForTimeout(2500);
    const relinks = await page.evaluate(() => localStorage.getItem('plugins:relinks') || '');
    const renamedTo = (relinks.match(/:to :([^\s,}]+)/) || [])[1];
    check('the rename of the existing race is recorded', !!renamedTo, relinks.slice(0, 200));
    await page.screenshot({ path: path.join(SHOTS, 'relink-1-imported.png'), fullPage: true });

    // Open the saved character: it is asked once.
    await page.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'load' });
    await page.waitForTimeout(4000);
    await page.getByText('Edit', { exact: true }).first().click();
    await page.waitForTimeout(3000);
    const QUESTION = 'This character uses \u201cTidefolk\u201d, and importing \u201cTide Pak\u201d added a different one.';
    check('the builder asks which one it meant', (await page.locator('#app').innerText()).includes(QUESTION));
    await page.screenshot({ path: path.join(SHOTS, 'relink-2-asked.png'), fullPage: true });
    await page.getByRole('button', { name: /switch it to yours/i }).click();
    await page.waitForTimeout(1500);
    check('switching points it at the renamed race', (await raceOf(page)) === `:${renamedTo}`, await raceOf(page));

    await page.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'load' });
    await page.waitForTimeout(4000);
    await page.getByText('Edit', { exact: true }).first().click();
    await page.waitForTimeout(3000);
    check('and it is asked only once', !(await page.locator('#app').innerText()).includes(QUESTION));
    check('no uncaught JS errors', errors.length === 0, errors.slice(0, 2).join(' | '));
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
