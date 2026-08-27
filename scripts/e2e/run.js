// Browser end-to-end checks for the custom-item flows.
//
// These drive the real app against a real server -- the components that put
// items on screen (inventory-adder, comps/selection-adder, the item builder's
// own buttons) are not reachable from the ClojureScript test suite, and every
// bug in this area so far has lived in exactly that gap.
//
//   ./scripts/e2e/run.sh
//
// Exits non-zero on the first failed expectation, so it is usable as a gate.

const { chromium } = require('playwright');

const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const USER = process.env.E2E_USER || 'kaylee';
const PASS = process.env.E2E_PASS || 'serenity99';
const SHOTS = process.env.E2E_SHOTS || '/tmp/e2e-shots';

let failures = 0;
const check = (name, ok, detail) => {
  console.log(`  ${ok ? 'ok  ' : 'FAIL'} ${name}${ok || detail === undefined ? '' : `  (${detail})`}`);
  if (!ok) failures++;
};

const buttonLabels = async p => {
  const b = p.locator('button.form-button:visible');
  const n = await b.count();
  const out = [];
  for (let i = 0; i < n; i++) out.push((await b.nth(i).innerText()).trim());
  return out;
};

const clickButton = async (p, re) => {
  const labels = await buttonLabels(p);
  for (let i = 0; i < labels.length; i++) {
    if (re.test(labels[i])) {
      await p.locator('button.form-button:visible').nth(i).click();
      return labels[i];
    }
  }
  throw new Error(`no visible button matching ${re}; saw ${JSON.stringify(labels)}`);
};

const login = async p => {
  await p.goto(BASE + '/pages/login-page', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('input');
  await p.locator('input').nth(0).fill(USER);
  await p.locator('input').nth(1).fill(PASS);
  await p.locator('button.form-button').click();
  await p.waitForTimeout(3500);
};

// The Magic Item? dropdown is the one offering magical/mundane; find it by its
// options rather than by position, which shifts as fields show and hide.
const kindSelect = async p => {
  const sels = p.locator('select.builder-option');
  const n = await sels.count();
  for (let i = 0; i < n; i++) {
    const vals = await sels.nth(i).evaluate(e => [...e.options].map(o => o.value));
    if (vals.includes('mundane')) return sels.nth(i);
  }
  throw new Error('no Magic Item? dropdown found');
};

const newItem = async (p, name, type) => {
  await p.goto(BASE + '/pages/dnd/5e/magic-item-builder', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('select.builder-option');
  await p.locator('input.input.h-40').first().fill(name);
  await p.locator('select.builder-option').first().selectOption(type);
  await p.waitForTimeout(500);
};

// --------------------------------------------------------------------------

async function customItemOverridesSrd(p) {
  console.log('\ncustom item overrides the SRD item it is named after');
  await login(p);
  await newItem(p, 'Longsword', 'weapon');
  await (await kindSelect(p)).selectOption('mundane');
  await p.waitForTimeout(500);

  const visible = await p.locator('select.builder-option').count();
  check('mundane hides the magical-only fields', visible < 4, `${visible} selects still shown`);

  await clickButton(p, /^SAVE/i);
  await p.waitForTimeout(3000);

  await p.goto(BASE + '/pages/dnd/5e/character-builder', { waitUntil: 'domcontentloaded' });
  await p.waitForTimeout(4000);
  await p.getByText('Equipment', { exact: true }).first().click();
  await p.waitForTimeout(2500);

  const weapons = await p.evaluate(() => {
    for (const sel of document.querySelectorAll('select.builder-option-dropdown')) {
      const opts = [...sel.options].map(o => o.textContent.trim());
      if (opts.some(t => /^Longsword$|^Longsword \(/.test(t))) {
        return { total: opts.length, longswords: opts.filter(t => /^Longsword/.test(t)) };
      }
    }
    return null;
  });
  check('the Weapons picker was found', weapons !== null);
  if (weapons) {
    check('exactly one Longsword row', weapons.longswords.length === 1,
          JSON.stringify(weapons.longswords));
    check('and it is labelled as yours',
          /\(your version\)/.test(weapons.longswords[0] || ''), weapons.longswords[0]);
  }
  await p.screenshot({ path: `${SHOTS}/override.png` });
}

async function removeForGoodActuallyRemoves(p) {
  console.log('\n"remove for good" clears the properties in the database');
  await login(p);
  await newItem(p, 'Retired Blade', 'weapon');
  // Give it a magical bonus, then demote it to mundane so the notice appears.
  const bonus = p.locator('input.input[type="number"]').first();
  await bonus.fill('2');
  await p.waitForTimeout(400);
  await (await kindSelect(p)).selectOption('mundane');
  await p.waitForTimeout(600);

  const notice = await p.getByText(/Magical properties kept but not applied/i).count();
  check('the suspended-properties notice appears', notice > 0);

  await p.getByText('remove for good', { exact: true }).first().click();
  await p.waitForTimeout(500);
  await clickButton(p, /^SAVE/i);
  await p.waitForTimeout(3000);

  // Re-read from the server, not from the page's own state.
  const stored = await p.evaluate(async () => {
    const r = await fetch('/dnd/5e/items', {
      headers: { Accept: 'application/transit+json' },
    });
    return r.status === 200 ? await r.text() : 'status ' + r.status;
  });
  const mentionsBonus = /magical-attack-bonus|magical-damage-bonus/.test(stored)
                        && /Retired Blade/.test(stored);
  check('the server no longer holds the magical bonuses', !mentionsBonus,
        stored.slice(0, 160));
}

async function signedOutSavePrompt(p) {
  console.log('\nsigned out, the save button asks for a login instead of failing');
  const posts = [];
  p.on('response', r => { if (r.request().method() === 'POST') posts.push(r.status()); });
  await newItem(p, 'Work In Progress', 'weapon');
  const labels = await buttonLabels(p);
  check('the button asks for a login', labels.some(l => /LOG IN/i.test(l)),
        JSON.stringify(labels));
  check('it does not claim browser storage',
        !labels.some(l => /BROWSER STORAGE/i.test(l)), JSON.stringify(labels));

  await clickButton(p, /LOG IN/i);
  await p.waitForTimeout(2500);
  check('no doomed request was sent', posts.length === 0, `POST statuses ${posts}`);
  check('it lands on the login page', /login-page/.test(p.url()), p.url());

  // The in-progress item is held in app-db, not in the form, so going back to
  // the builder should still show it.
  await p.goto(BASE + '/pages/dnd/5e/magic-item-builder', { waitUntil: 'domcontentloaded' });
  await p.waitForTimeout(2500);
  const nameValue = await p.locator('input.input.h-40').first().inputValue();
  check('the in-progress item survives the bounce', nameValue === 'Work In Progress',
        `name field is ${JSON.stringify(nameValue)}`);
}

async function magicalPropertiesField(p) {
  console.log('\nMagical Properties is its own field, gated behind Magic Item?');
  await login(p);
  await newItem(p, 'Moon-Touched Sword', 'weapon');

  // base-builder-field puts the label in the grandparent of the textarea.
  const labelled = async () => await p.evaluate(() =>
    [...document.querySelectorAll('textarea')].map(t =>
      (t.parentElement.parentElement.innerText || '').trim().split('\n')[0]));

  let labels = await labelled();
  check('the field is offered on a magic item',
        labels.some(l => /Magical Properties/i.test(l)), JSON.stringify(labels));
  check('Description is still separate',
        labels.some(l => /^Description$/i.test(l)), JSON.stringify(labels));

  await (await kindSelect(p)).selectOption('mundane');
  await p.waitForTimeout(600);
  labels = await labelled();
  check('and hidden on a mundane one',
        !labels.some(l => /Magical Properties/i.test(l)), JSON.stringify(labels));

  await (await kindSelect(p)).selectOption('magical');
  await p.waitForTimeout(600);

  // Type into it and save; the whitelist in from-internal-item is the thing
  // most likely to silently drop a newly added attribute.
  const ta = p.locator('textarea').nth(1);
  await ta.fill('Sheds dim light in a 5-foot radius.');
  await p.waitForTimeout(400);
  await clickButton(p, /^SAVE/i);
  await p.waitForTimeout(3000);

  // Re-open the item from My Items rather than reading app-db: this is the
  // whole point, since from-internal-item's whitelist silently drops any
  // attribute missing from it and the round trip is where that shows up.
  await p.goto(BASE + '/pages/dnd/5e/magic-items', { waitUntil: 'domcontentloaded' });
  await p.waitForTimeout(3500);
  await p.getByText('Moon-Touched Sword', { exact: false }).first().click();
  await p.waitForTimeout(3000);
  const shown = await p.locator('body').innerText();
  check('the prose survived the save round trip',
        /Sheds dim light in a 5-foot radius/.test(shown));
  check('and renders under its own label', /Magical Properties\./.test(shown));
}

// --------------------------------------------------------------------------

(async () => {
  const fs = require('fs');
  fs.mkdirSync(SHOTS, { recursive: true });
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  const cases = [customItemOverridesSrd, removeForGoodActuallyRemoves, signedOutSavePrompt,
                 magicalPropertiesField];
  for (const c of cases) {
    const p = await browser.newPage({ viewport: { width: 1280, height: 900 } });
    try {
      await c(p);
    } catch (e) {
      console.log(`  FAIL ${c.name} threw: ${e.message.split('\n')[0]}`);
      failures++;
      await p.screenshot({ path: `${SHOTS}/${c.name}-error.png` }).catch(() => {});
    }
    await p.close();
  }
  await browser.close();
  console.log(`\n${failures === 0 ? 'all checks passed' : failures + ' check(s) failed'}`);
  process.exit(failures === 0 ? 0 : 1);
})();
