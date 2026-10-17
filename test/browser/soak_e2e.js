// Kind: on demand
// Needs: fresh server (creates characters and homebrew for the seeded users).
// The paths players take through characters and homebrew, logged out and logged in, driven only
// through the page as a player would. Run it before a release or after changing how characters,
// drafts or homebrew are saved; run-all.sh leaves it out.
//
//   1. Logged out: build a character, Save, log in from that prompt, Save again: it is stored.
//   2. Logged out: one well-filled entry from every homebrew builder except magic items.
//   3. Logged out: a character using that homebrew, saved through the same login.
//   4. Logged out: import a homebrew file, a character using it, saved through login.
//   5. Logged in: the same homebrew and import; then delete a character, New, open an old one,
//      New again, each time checking the builder holds the character it should.
//   6. A shared computer: one account's unsaved character is not shown to the next account.
//   7. Tabs: two characters in two tabs keep their own after reloads; the same character in a
//      second tab is held until Edit here.
//   8. Things going wrong: a save while the server is unreachable keeps the edit and the leave
//      warning; the item builder logged out asks to log in; a homebrew character opened in a
//      browser without that homebrew still loads; Save after deleting the open character; the
//      logged-out path again at phone width.
//   9. Offline: a loaded character stays usable with no connection; a sheet edit made offline
//      and a builder edit saved once back online both reach the server.
//  10. PDF export is never stopped by something missing: a blank new character, a broken picture
//      address, and a homebrew character in a browser without that homebrew all produce a PDF.
//
// Every step also fails on a page error or an error banner. A step that cannot finish says
// which, saves a screenshot to OUT, and the run carries on with the next independent part.
//
// Needs:     the real app at :8890 (lein e2e-server); run through scripts/e2e/run.sh.
// Runs in:   ~16 min.
// Run:       ./scripts/e2e/run.sh test/browser/soak_e2e.js      exit 0 = all checks passed
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const { newContext, login } = require('../../scripts/e2e/lib.js');
const { importPack } = require('./lib/orcbrew-import');
const fs = require('fs'), os = require('os'), path = require('path');

const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const OUT = process.env.PROBE_OUT || fs.mkdtempSync(path.join(os.tmpdir(), 'soak-'));
const PACK = 'Soak Pack';

let failures = 0;
function check(label, ok, detail) {
  if (!ok) failures++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${detail && !ok ? '  -- ' + String(detail).slice(0, 200) : ''}`);
}

// Runs `fn`; a throw fails the step, with a screenshot, instead of ending the run.
async function step(page, name, fn) {
  try { return await fn(); }
  catch (e) {
    const file = path.join(OUT, name.replace(/[^a-z0-9]+/gi, '-').slice(0, 60) + '.png');
    await page.screenshot({ path: file, fullPage: true }).catch(() => {});
    check(`${name} (could not finish)`, false, `${e.message.split('\n')[0]}; screenshot ${file}`);
    return undefined;
  }
}

// A page that records page errors and error banners, for a final check per context.
async function watchedPage(ctx, errors) {
  const page = await ctx.newPage();
  await page.setViewportSize({ width: 1280, height: 1000 });
  page.on('pageerror', e => errors.push(e.message));
  return page;
}

const settle = page => page.waitForTimeout(700);
const errorBanner = page => page.evaluate(() => {
  const m = document.querySelector('.bg-red, .error-message, .message-error');
  return m && m.offsetParent ? m.innerText.trim().slice(0, 120) : '';
});

// ---- the character builder ----

async function openBuilder(page) {
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.builder-tab', { timeout: 30000 });
  await settle(page);
}

async function section(page, name) {
  const re = new RegExp('^' + name.replace(/[/]/g, '\\/') + '$');
  await page.locator('div.f-s-10', { hasText: re }).first().click({ timeout: 10000 });
  await settle(page);
}

// Clicks the option row named `name` on the open builder page.
async function pick(page, name) {
  const row = page.locator('div.b-orange', { has: page.locator('span.f-w-b', { hasText: optionName(name) }) });
  await row.first().click({ timeout: 10000 });
  await settle(page);
}

// An option's name as a row shows it; spells are listed with their level ("1 - Charm Person").
const optionName = name => new RegExp('^(\\d+ - )?' + name + '$');
const hasOption = (page, name) => page.locator('div.b-orange span.f-w-b', { hasText: optionName(name) }).count();

// Checks that `name` is offered on the open page; when it is not, says what is, with a screenshot.
async function offered(page, label, name) {
  const ok = (await hasOption(page, name)) > 0;
  let detail = '';
  if (!ok) {
    const file = path.join(OUT, label.replace(/[^a-z0-9]+/gi, '-').slice(0, 60) + '.png');
    await page.screenshot({ path: file, fullPage: true }).catch(() => {});
    const names = await page.evaluate(() => [...document.querySelectorAll('div.b-orange span.f-w-b')].map(e => e.innerText));
    detail = `page offers: ${names.slice(0, 25).join(', ')}; screenshot ${file}`;
  }
  check(label, ok, detail);
  return ok;
}

async function chooseClass(page, cls, level) {
  const classSelect = page.locator('select', { has: page.locator('option', { hasText: /^Warlock$/ }) }).first();
  await classSelect.selectOption({ label: cls });
  await settle(page);
  const levelSelect = page.locator('select', { has: page.locator('option', { hasText: /^20$/ }) }).first();
  await levelSelect.selectOption({ label: String(level) });
  await settle(page);
}

async function builderTab(page, name) {
  await page.locator('.builder-tab', { hasText: name }).first().click({ timeout: 10000 });
  await settle(page);
}

// The input or text area that follows the visible caption `label`.
async function fieldByLabel(page, label) {
  return page.locator('.personality-label:visible', { hasText: new RegExp('^' + label + '$') }).first()
    .locator('xpath=following::*[self::input or self::textarea][1]');
}

async function nameCharacter(page, name) {
  await builderTab(page, 'DESCRIPTION');
  await (await fieldByLabel(page, 'Character Name')).fill(name);
  await settle(page);
  await builderTab(page, 'OPTIONS');
}

async function standardScores(page) {
  await section(page, 'Ability Scores / Feats');
  await pick(page, 'Standard Scores');
}

const characterSaves = page => page.waitForResponse(
  r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/dnd/5e/characters', { timeout: 30000 });

async function pressSave(page) {
  await page.locator('button, .form-button', { hasText: /^save( new character)?$/i }).first().click({ timeout: 10000 });
}

// Saves while logged out: expects the login page, logs in there, and expects the builder back
// with `name` intact; then saves again and returns that response's status.
async function saveThroughLogin(page, name, user, pass) {
  await pressSave(page);
  await page.waitForURL(/login-page/, { timeout: 20000 });
  check(`${name}: Save while logged out asks to log in`, true);
  await page.locator('input').nth(0).fill(user);
  await page.locator('input').nth(1).fill(pass);
  await page.locator('button.form-button').click();
  await page.waitForURL(/character-builder/, { timeout: 20000 });
  await page.waitForSelector('.builder-tab', { timeout: 30000 });
  await settle(page);
  await builderTab(page, 'DESCRIPTION');
  const kept = await (await fieldByLabel(page, 'Character Name')).inputValue();
  await builderTab(page, 'OPTIONS');
  check(`${name}: logging in returns to the builder with the character intact`, kept === name, kept);
  const saved = characterSaves(page);
  await pressSave(page);
  const status = (await saved).status();
  check(`${name}: Save after logging in stores it`, status === 200, status);
  return status;
}

async function characterListed(page, name) {
  await page.goto(`${BASE}/pages/dnd/5e/characters`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1500);
  return page.evaluate(n => document.body.innerText.includes(n), name);
}

// ---- the homebrew builders ----

async function fill(page, label, value) {
  const f = await fieldByLabel(page, label);
  await f.fill(value);
}

// Fills every empty visible text field on a builder page, so the entry is well filled.
async function stuff(page, tag) {
  const fields = page.locator('#app .field input[type=text]:visible, #app .field textarea:visible, #app .field input:not([type]):visible');
  const n = await fields.count();
  for (let i = 0; i < n; i++) {
    const f = fields.nth(i);
    if ((await f.getAttribute('id')) === 'plugins-choice') continue;
    if (await f.inputValue().catch(() => 'x')) continue;
    await f.fill(`${tag} detail ${i + 1}`).catch(() => {});
  }
  const numbers = page.locator('#app .field input[type=number]:visible');
  for (let i = 0; i < await numbers.count(); i++) await numbers.nth(i).fill('3').catch(() => {});
}

async function builderSave(page, kind, name) {
  await page.locator('#plugins-choice').fill(PACK);
  await settle(page);
  await page.locator('button, .form-button', { hasText: /save to browser storage/i }).first().click({ timeout: 10000 });
  await page.waitForTimeout(1200);
  const stored = await page.evaluate(n => (localStorage.getItem('plugins') || '').includes(n), name);
  const flagged = await page.evaluate(() => [...document.querySelectorAll('.builder-field-invalid, .builder-field-unfilled')]
    .map(el => { const l = el.closest('div') && el.closest('div').parentElement && el.closest('div').parentElement.querySelector('.personality-label, .f-w-b');
                 return (l ? l.innerText : el.tagName).trim().slice(0, 30); }));
  check(`${kind} builder: "${name}" is saved to this browser`, stored,
        flagged.length ? `flagged: ${flagged.join(', ')}` : 'not in storage');
}

async function openHomebrewBuilder(page, kind) {
  await page.goto(`${BASE}/pages/dnd/5e/${kind}-builder`, { waitUntil: 'networkidle' });
  await page.waitForSelector('#plugins-choice', { timeout: 30000 });
  await settle(page);
}

// One stuffed entry from each builder; returns the names made.
async function buildHomebrew(page, prefix) {
  const made = {};
  const simple = [['language', 'Tongue'], ['invocation', 'Invocation'], ['boon', 'Boon'], ['feat', 'Feat'],
                  ['background', 'Background'], ['selection', 'Selection'], ['monster', 'Monster']];
  // Before saving: a background that lets the character choose a language; a monster's hit dice.
  const extras = {
    background: async () => {
      await page.locator('div.f-s-24', { hasText: /^Languages$/ }).locator('xpath=..')
        .getByText('Any 1', { exact: true }).click({ timeout: 10000 });
    },
    monster: async () => {
      const after = label => page.getByText(label, { exact: true }).first().locator('xpath=following::select[1]');
      await after('HP Die Count').selectOption({ label: '2' });
      await after('HP Die').selectOption({ label: '8' });
    },
  };
  for (const [kind, noun] of simple) {
    await step(page, `${kind} builder`, async () => {
      await openHomebrewBuilder(page, kind);
      const name = `${prefix} ${noun}`;
      await fill(page, 'Name', name);
      await stuff(page, `${prefix} ${noun}`);
      if (extras[kind]) await extras[kind]();
      await builderSave(page, kind, name);
      made[kind] = name;
    });
  }
  await step(page, 'spell builder', async () => {
    await openHomebrewBuilder(page, 'spell');
    const name = `${prefix} Spell`;
    await fill(page, 'Name', name);
    await page.locator('select', { has: page.locator('option', { hasText: /^1st-level$/ }) }).first().selectOption({ label: '1st-level' });
    await stuff(page, name);
    await builderSave(page, 'spell', name);
    made.spell = name;
  });
  await step(page, 'race builder', async () => {
    await openHomebrewBuilder(page, 'race');
    const name = `${prefix} Race`;
    await fill(page, 'Name', name);
    await stuff(page, name);
    await builderSave(page, 'race', name);
    made.race = name;
  });
  await step(page, 'subrace builder', async () => {
    await openHomebrewBuilder(page, 'subrace');
    const name = `${prefix} Subrace`;
    await fill(page, 'Name', name);
    await page.locator('select', { has: page.locator('option', { hasText: /^Dwarf$/ }) }).first()
      .selectOption({ label: made.race || `${prefix} Race` });
    await stuff(page, name);
    await builderSave(page, 'subrace', name);
    made.subrace = name;
  });
  await step(page, 'subclass builder', async () => {
    await openHomebrewBuilder(page, 'subclass');
    const name = `${prefix} Patron`;
    await fill(page, 'Name', name);
    await page.locator('select', { has: page.locator('option', { hasText: /^Barbarian$/ }) }).first().selectOption({ label: 'Warlock' });
    await stuff(page, name);
    await builderSave(page, 'subclass', name);
    made.subclass = name;
  });
  await step(page, 'class builder', async () => {
    await openHomebrewBuilder(page, 'class');
    const name = `${prefix} Class`;
    await fill(page, 'Name', name);
    await stuff(page, name);
    await builderSave(page, 'class', name);
    made.class = name;
  });
  await step(page, 'encounter builder', async () => {
    await openHomebrewBuilder(page, 'encounter');
    const name = `${prefix} Encounter`;
    await fill(page, 'Name', name);
    await builderSave(page, 'encounter', name);
    made.encounter = name;
  });
  return made;
}

// A Warlock 4 using the homebrew in `made`; each use is its own check.
async function homebrewCharacter(page, made, name) {
  await openBuilder(page);
  await page.locator('button, .form-button', { hasText: /^new$/i }).first().click().catch(() => {});
  await settle(page);
  await nameCharacter(page, name);
  await step(page, `${name}: race`, async () => {
    await section(page, 'Race');
    if (await offered(page, `${name}: the homebrew race is offered`, made.race)) await pick(page, made.race);
    if (await offered(page, `${name}: and its subrace`, made.subrace)) await pick(page, made.subrace);
  });
  await step(page, `${name}: class`, async () => {
    await section(page, 'Class / Level');
    await chooseClass(page, 'Warlock', 4);
    for (const kind of ['subclass', 'boon']) {
      if (await offered(page, `${name}: the homebrew ${kind} is offered`, made[kind])) await pick(page, made[kind]);
    }
  });
  await step(page, `${name}: abilities and feat`, async () => {
    await standardScores(page);
    if (await offered(page, `${name}: the homebrew feat is offered`, made.feat)) await pick(page, made.feat);
  });
  await step(page, `${name}: background`, async () => {
    await section(page, 'Background');
    if (await offered(page, `${name}: the homebrew background is offered`, made.background)) await pick(page, made.background);
  });
  await step(page, `${name}: invocation`, async () => {
    await section(page, 'Spells');
    if (await offered(page, `${name}: the homebrew invocation is offered`, made.invocation)) await pick(page, made.invocation);
  });
  await step(page, `${name}: spell`, async () => {
    await section(page, 'Class / Level');
    if (!(await hasOption(page, made.spell))) await section(page, 'Spells');
    if (await offered(page, `${name}: the homebrew spell is offered`, made.spell)) await pick(page, made.spell);
  });
  await step(page, `${name}: language`, async () => {
    for (const s of ['Race', 'Background', 'Proficiencies']) {
      await section(page, s);
      if (await hasOption(page, made.language)) { await pick(page, made.language); check(`${name}: the homebrew language is offered (${s})`, true); return; }
    }
    await offered(page, `${name}: the homebrew language is offered somewhere`, made.language);
  });
}

// The first race the fixture pack defines, read from the file, so the test follows the fixture.
const PAK = path.join(__dirname, '../fixtures/test-pak.orcbrew');
const pakRace = (() => {
  const text = fs.readFileSync(PAK, 'utf8');
  const at = text.indexOf(':orcpub.dnd.e5/races');
  return (text.slice(at).match(/:name "([^"]+)"/) || [])[1];
})();

async function importFixture(page) {
  await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'networkidle' });
  await importPack(page, PAK);
  await settle(page);
  check('the homebrew file imports', await page.evaluate(n => (localStorage.getItem('plugins') || '').includes(n), pakRace),
        `${pakRace} not in storage`);
}

// A plain Fighter named `name` using race `race`, left unsaved in the builder.
async function plainCharacter(page, name, race = 'Dwarf') {
  await openBuilder(page);
  await page.locator('button, .form-button', { hasText: /^new$/i }).first().click().catch(() => {});
  await settle(page);
  await nameCharacter(page, name);
  await section(page, 'Race');
  await pick(page, race);
  await section(page, 'Class / Level');
  await chooseClass(page, 'Fighter', 1);
  await standardScores(page);
}

// Exports the character open in the builder of `page`'s context through Export -> sheet style ->
// Create PDF, and returns the PDF request's status and content type ('' when none was sent).
async function exportPdf(ctx, page) {
  let result = { status: 0, type: '' };
  const seen = r => { if (r.url().includes('/character.pdf') && r.request().method() === 'POST')
    result = { status: r.status(), type: r.headers()['content-type'] || '' }; };
  ctx.on('response', seen);
  const visible = async loc => { for (let i = 0; i < await loc.count(); i++) if (await loc.nth(i).isVisible()) return loc.nth(i);
                                 throw new Error('no visible ' + loc); };
  await (await visible(page.locator('button:has-text("Export")'))).click();
  await page.waitForTimeout(1200);
  await (await visible(page.locator('select'))).selectOption('1');
  await page.waitForTimeout(600);
  await (await visible(page.locator('button:has-text("Create PDF")'))).click();
  for (let t = 0; t < 40 && !result.status; t++) await page.waitForTimeout(500);
  ctx.off('response', seen);
  return result;
}

async function page2Name(page, name) {
  await page.waitForSelector('.builder-tab', { timeout: 30000 });
  await builderTab(page, 'DESCRIPTION');
  await (await fieldByLabel(page, 'Character Name')).fill(name);
  await settle(page);
  await builderTab(page, 'OPTIONS');
}

async function builderName(page) {
  await builderTab(page, 'DESCRIPTION');
  const v = await (await fieldByLabel(page, 'Character Name')).inputValue();
  await builderTab(page, 'OPTIONS');
  return v;
}

async function saveLoggedIn(page, name) {
  const saved = characterSaves(page);
  await pressSave(page);
  const status = (await saved).status();
  check(`${name}: saves`, status === 200, status);
}

// Opens `name`'s row on the character list and presses `button` (EDIT, DELETE...).
async function rowAction(page, name, button) {
  await page.goto(`${BASE}/pages/dnd/5e/characters`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1200);
  await page.getByText(name, { exact: true }).first().click();
  await settle(page);
  await page.locator('button, .form-button', { hasText: new RegExp('^' + button + '$', 'i') }).first().click({ timeout: 10000 });
  await settle(page);
}

// Presses the confirming link of an open "are you sure" (delete, create new, clone, random).
async function confirmIfAsked(page) {
  const yes = page.locator('.link-button', { hasText: /^(delete|create new character|create clone|generate random character)$/i }).last();
  if (await yes.isVisible().catch(() => false)) { await yes.click(); await settle(page); }
}

async function logOut(page) {
  await page.goto(`${BASE}/pages/dnd/5e/characters`, { waitUntil: 'networkidle' });
  await page.locator('text=/^(kaylee|zoe)$/').first().click({ timeout: 10000 });
  await settle(page);
  await page.locator('text=/^log ?out$/i').first().click({ timeout: 10000 });
  await page.waitForTimeout(1500);
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });

  // ---- 1. Logged out: a plain character, saved through login ----
  {
    const errors = [];
    const ctx = await newContext(browser);
    const page = await watchedPage(ctx, errors);
    await step(page, 'logged-out plain character', async () => {
      await openBuilder(page);
      await nameCharacter(page, 'Soak Plain');
      await section(page, 'Race');
      await pick(page, 'Dwarf');
      await section(page, 'Class / Level');
      await chooseClass(page, 'Fighter', 1);
      await standardScores(page);
      await saveThroughLogin(page, 'Soak Plain', 'kaylee', 'serenity99');
      check('Soak Plain: is on the character list', await characterListed(page, 'Soak Plain'));
    });
    check('part 1: no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));
    await ctx.close();
  }

  // ---- 2, 3. Logged out: homebrew from every builder, then a character using it ----
  {
    const errors = [];
    const ctx = await newContext(browser);
    const page = await watchedPage(ctx, errors);
    const made = await buildHomebrew(page, 'Soak');
    await homebrewCharacter(page, made, 'Soak Brewed');
    await step(page, 'logged-out homebrew character save', async () => {
      await saveThroughLogin(page, 'Soak Brewed', 'kaylee', 'serenity99');
      check('Soak Brewed: is on the character list', await characterListed(page, 'Soak Brewed'));
    });
    check('parts 2-3: no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));
    await ctx.close();
  }

  // ---- 4. Logged out: an imported homebrew file, a character using it, saved through login ----
  {
    const errors = [];
    const ctx = await newContext(browser);
    const page = await watchedPage(ctx, errors);
    await step(page, 'logged-out import', async () => {
      await importFixture(page);
      await plainCharacter(page, 'Soak Imported', pakRace);
      await saveThroughLogin(page, 'Soak Imported', 'kaylee', 'serenity99');
      check('Soak Imported: is on the character list', await characterListed(page, 'Soak Imported'));
    });
    check('part 4: no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));
    await ctx.close();
  }

  // ---- 5. Logged in: homebrew, import, and moving between characters ----
  {
    const errors = [];
    const ctx = await newContext(browser);
    const page = await watchedPage(ctx, errors);
    await login(page, 'kaylee', 'serenity99');
    const made = await buildHomebrew(page, 'Logged');
    await homebrewCharacter(page, made, 'Logged Brewed');
    await step(page, 'logged-in homebrew character save', async () => {
      await saveLoggedIn(page, 'Logged Brewed');
      check('Logged Brewed: is on the character list', await characterListed(page, 'Logged Brewed'));
    });
    await step(page, 'logged-in import', async () => {
      await importFixture(page);
      await plainCharacter(page, 'Logged Imported', pakRace);
      await saveLoggedIn(page, 'Logged Imported');
    });
    await step(page, 'delete a character', async () => {
      await plainCharacter(page, 'Doomed One');
      await saveLoggedIn(page, 'Doomed One');
      await rowAction(page, 'Doomed One', 'DELETE');
      await confirmIfAsked(page);
      await page.reload({ waitUntil: 'networkidle' });
      await page.waitForTimeout(1200);
      check('a deleted character leaves the list', !(await page.evaluate(() => document.body.innerText.includes('Doomed One'))));
    });
    await step(page, 'new, old, new', async () => {
      await plainCharacter(page, 'Fresh First');
      check('New: the builder holds the new character', (await builderName(page)) === 'Fresh First');
      await saveLoggedIn(page, 'Fresh First');
      await rowAction(page, 'Logged Brewed', 'EDIT');
      await page.waitForSelector('.builder-tab', { timeout: 30000 });
      check('opening an old character shows it', (await builderName(page)) === 'Logged Brewed', await builderName(page));
      await page.locator('button, .form-button', { hasText: /^new$/i }).first().click();
      await settle(page);
      await confirmIfAsked(page);
      check('New again: an empty character, not the old one', (await builderName(page)) === '', await builderName(page));
      await rowAction(page, 'Fresh First', 'EDIT');
      await page.waitForSelector('.builder-tab', { timeout: 30000 });
      check('and the first new one opens as saved', (await builderName(page)) === 'Fresh First', await builderName(page));
    });
    check('part 5: no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));
    await ctx.close();
  }

  // ---- 6. A shared computer: one account's unsaved work is not shown to the next ----
  {
    const errors = [];
    const ctx = await newContext(browser);
    const page = await watchedPage(ctx, errors);
    await step(page, 'shared computer', async () => {
      await login(page, 'kaylee', 'serenity99');
      await plainCharacter(page, 'Kaylee Private Draft');
      await logOut(page);
      await login(page, 'zoe', 'washburne7');
      await openBuilder(page);
      const shown = await builderName(page);
      check('after logging out, the next account\'s builder does not hold the last account\'s unsaved character',
            shown !== 'Kaylee Private Draft', shown);
      await page.goto(`${BASE}/pages/dnd/5e/characters`, { waitUntil: 'networkidle' });
      await page.waitForTimeout(1500);
      check('nor lists it among its unsaved drafts',
            !(await page.evaluate(() => document.body.innerText.includes('Kaylee Private Draft'))));
    });
    check('part 6: no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));
    await ctx.close();
  }

  // ---- 7. Tabs ----
  {
    const errors = [];
    const ctx = await newContext(browser);
    const one = await watchedPage(ctx, errors);
    await login(one, 'kaylee', 'serenity99');
    const two = await watchedPage(ctx, errors);
    await step(one, 'two tabs, two characters', async () => {
      await rowAction(one, 'Ash Marlow', 'EDIT');
      await page2Name(one, 'Ash, tab one');
      await rowAction(two, 'Wren Holloway', 'EDIT');
      await page2Name(two, 'Wren, tab two');
      for (const [p, want] of [[one, 'Ash, tab one'], [two, 'Wren, tab two']]) {
        await p.reload({ waitUntil: 'networkidle' });
        await p.waitForSelector('.builder-tab', { timeout: 30000 });
        check(`after a reload, a tab keeps its own unsaved character (${want})`, (await builderName(p)) === want, await builderName(p));
      }
    });
    await step(two, 'the same character in two tabs', async () => {
      await rowAction(two, 'Ash Marlow', 'EDIT');
      const notice = await two.evaluate(() => (document.querySelector('.drafts-notices') || {}).innerText || '');
      check('opening a character another tab is editing says so and waits for Edit here', /Open in another tab/i.test(notice), notice);
      await two.locator('.drafts-notices button', { hasText: /edit here/i }).first().click({ timeout: 10000 });
      await settle(two);
      check('Edit here continues the other tab\'s changes', (await builderName(two)) === 'Ash, tab one', await builderName(two));
    });
    check('part 7: no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));
    await ctx.close();
  }

  // ---- 8. Things going wrong ----
  {
    const errors = [];
    const ctx = await newContext(browser);
    const page = await watchedPage(ctx, errors);
    await step(page, 'item builder logged out', async () => {
      await page.goto(`${BASE}/pages/dnd/5e/magic-item-builder`, { waitUntil: 'networkidle' });
      await fill(page, 'Item Name', 'Soak Item').catch(() => page.locator('#app input[type=text]').nth(1).fill('Soak Item'));
      await page.locator('button, .form-button', { hasText: /^save item$/i }).first().click();
      await page.waitForTimeout(2500);
      const url = page.url(), banner = await errorBanner(page);
      check('saving a magic item logged out asks to log in', /login-page/.test(url) || /log ?in/i.test(banner), `${url} ${banner}`);
    });
    await login(page, 'kaylee', 'serenity99');
    await step(page, 'save while the server is unreachable', async () => {
      await plainCharacter(page, 'Offline Edit');
      await page.route('**/dnd/5e/characters', r => r.request().method() === 'POST' ? r.abort() : r.continue());
      await pressSave(page);
      await page.waitForTimeout(2500);
      await page.unroute('**/dnd/5e/characters');
      check('a failed save keeps the edit', (await builderName(page)) === 'Offline Edit');
      await page.goto(`${BASE}/pages/dnd/5e/spells`, { waitUntil: 'networkidle' });
      await openBuilder(page);
      check('and it is still there after leaving the page and coming back', (await builderName(page)) === 'Offline Edit',
            await builderName(page));
    });
    await step(page, 'save after deleting the open character', async () => {
      await plainCharacter(page, 'Gone While Open');
      await saveLoggedIn(page, 'Gone While Open');
      const other = await watchedPage(ctx, errors);
      await rowAction(other, 'Gone While Open', 'DELETE');
      await confirmIfAsked(other);
      await other.close();
      await page.bringToFront();
      await builderTab(page, 'DESCRIPTION');
      await (await fieldByLabel(page, 'Character Name')).fill('Gone While Open, edited');
      await builderTab(page, 'OPTIONS');
      const saved = characterSaves(page);
      await pressSave(page);
      const status = (await saved).status();
      const banner = await errorBanner(page);
      check('saving a character deleted elsewhere is refused or saved anew, with nothing lost silently',
            status === 200 || banner.length > 0, `status ${status}, banner "${banner}"`);
    });
    check('part 8: no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));
    await ctx.close();
  }
  {
    const errors = [];
    const ctx = await newContext(browser);
    const page = await watchedPage(ctx, errors);
    await login(page, 'kaylee', 'serenity99');
    await step(page, 'a homebrew character in a browser without the homebrew', async () => {
      await rowAction(page, 'Soak Brewed', 'EDIT');
      await page.waitForSelector('.builder-tab', { timeout: 30000 });
      await page.waitForTimeout(2000);
      check('it opens in the builder, not a blank page', (await builderName(page)) === 'Soak Brewed', await builderName(page));
      const text = await page.evaluate(() => document.body.innerText);
      check('and says its homebrew is missing', /missing content/i.test(text), 'no Missing Content notice');
    });
    check('part 8b: no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));
    await ctx.close();
  }
  // ---- 9. Offline: keep using a loaded character, and nothing is lost when the connection returns ----
  {
    const errors = [];
    const ctx = await newContext(browser);
    const page = await watchedPage(ctx, errors);
    await login(page, 'kaylee', 'serenity99');
    const hpInput = () => page.getByText('Max Hit Points', { exact: true }).first().locator('xpath=following::input[1]');
    await step(page, 'offline sheet edit', async () => {
      // A character the walkthrough saved itself: the seeded ones keep their name only in the list.
      await rowAction(page, 'Soak Plain', 'VIEW');
      await page.waitForTimeout(2000);
      await ctx.setOffline(true);
      await hpInput().fill('3');
      await page.waitForTimeout(9000);
      check('offline, the sheet keeps the edit on screen', (await hpInput().inputValue()) === '3');
      await ctx.setOffline(false);
      await page.waitForTimeout(12000);
      await page.reload({ waitUntil: 'networkidle' });
      await page.waitForTimeout(2000);
      check('back online, the sheet edit made offline is saved without another edit',
            (await hpInput().inputValue()) === '3', await hpInput().inputValue());
    });
    await step(page, 'offline builder edit', async () => {
      await rowAction(page, 'Soak Plain', 'EDIT');
      await page.waitForSelector('.builder-tab', { timeout: 30000 });
      await ctx.setOffline(true);
      await page2Name(page, 'Plain, edited offline');
      await pressSave(page).catch(() => {});
      await page.waitForTimeout(2500);
      check('offline, a failed Save keeps the builder edit', (await builderName(page)) === 'Plain, edited offline');
      await ctx.setOffline(false);
      await saveLoggedIn(page, 'Plain, edited offline');
      check('and Save once back online stores it', await characterListed(page, 'Plain, edited offline'));
    });
    check('part 9: no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));
    await ctx.close();
  }
  // ---- 10. PDF export is never stopped by something missing ----
  {
    const errors = [];
    const ctx = await newContext(browser);
    const page = await watchedPage(ctx, errors);
    const isPdf = r => r.status === 200 && /pdf/.test(r.type);
    await step(page, 'PDF of a blank new character', async () => {
      await openBuilder(page);
      await page.locator('button, .form-button', { hasText: /^new$/i }).first().click().catch(() => {});
      await confirmIfAsked(page);
      const r = await exportPdf(ctx, page);
      check('a blank new character exports a PDF', isPdf(r), JSON.stringify(r));
    });
    await step(page, 'PDF with a broken picture address', async () => {
      await plainCharacter(page, 'Broken Picture');
      await builderTab(page, 'DESCRIPTION');
      await (await fieldByLabel(page, 'Image URL .*')).fill('https://example.invalid/missing.png');
      await builderTab(page, 'OPTIONS');
      const r = await exportPdf(ctx, page);
      check('a character with a picture that will not load exports a PDF', isPdf(r), JSON.stringify(r));
    });
    await login(page, 'kaylee', 'serenity99');
    await step(page, 'PDF of a homebrew character without its homebrew', async () => {
      await rowAction(page, 'Soak Brewed', 'EDIT');
      await page.waitForSelector('.builder-tab', { timeout: 30000 });
      const r = await exportPdf(ctx, page);
      check('a homebrew character in a browser without that homebrew exports a PDF', isPdf(r), JSON.stringify(r));
    });
    check('part 10: no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));
    await ctx.close();
  }

  {
    const errors = [];
    const ctx = await browser.newContext({
      userAgent: 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
      viewport: { width: 400, height: 860 } });
    await ctx.addInitScript(() => { try { localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', JSON.stringify('summer-patch-2026')); } catch (e) {} });
    const page = await ctx.newPage();
    page.on('pageerror', e => errors.push(e.message));
    await step(page, 'phone: logged-out character saved through login', async () => {
      await openBuilder(page);
      // Section tabs show no names on a phone; Random makes a complete character to save.
      await page.locator('button, .form-button', { hasText: /^random$/i }).first().click({ timeout: 10000 });
      await page.waitForTimeout(2000);
      await nameCharacter(page, 'Soak Phone');
      await saveThroughLogin(page, 'Soak Phone', 'kaylee', 'serenity99');
    });
    check('phone: no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));
    await ctx.close();
  }

  await browser.close();
  console.log(failures ? `\n${failures} check(s) failed; screenshots in ${OUT}` : '\nall checks passed');
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
