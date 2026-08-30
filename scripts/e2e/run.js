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

// Console surveillance. Every page in every case reports what it logged, so a
// warning nobody would otherwise read becomes a failure. Noise the app does not
// control is filtered: the fonts.googleapis.com stylesheet cannot load in this
// sandbox, and the cookie-consent script is third-party.
const IGNORED = [
  /cookieconsent/i,
  /favicon/i,
];
const noise = t => IGNORED.some(re => re.test(t));

// Serve the web fonts locally instead of ignoring their failure. The sandbox
// cannot reach fonts.googleapis.com, and filtering the resulting console noise
// meant filtering a line Chromium logs WITHOUT a URL -- which would have hidden
// a genuine connection reset against our own server too. Fulfilling the request
// removes the noise at its source and leaves the console check honest.
//
// Test-harness only: nothing here changes what the app requests.
const shimFonts = async (page) => {
  await page.route(/fonts\.googleapis\.com/, route =>
    route.fulfill({ status: 200, contentType: 'text/css', body: '/* stubbed */' }));
  await page.route(/fonts\.gstatic\.com/, route =>
    route.fulfill({ status: 200, contentType: 'font/woff2', body: '' }));
};

const watchConsole = (page, label) => {
  const found = [];
  page.on('pageerror', e => found.push(`[${label}] pageerror: ${e && e.message}`));
  page.on('console', m => {
    const type = m.type();
    if (type !== 'error' && type !== 'warning') return;
    const text = m.text();
    if (!noise(text)) found.push(`[${label}] console.${type}: ${text}`);
  });
  page.on('requestfailed', r => {
    const url = r.url();
    if (!noise(url)) {
      found.push(`[${label}] requestfailed: ${r.method()} ${url} :: ${(r.failure() || {}).errorText}`);
    }
  });
  return found;
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

const newItem = async (p, name, type, { chooseSubtype = true } = {}) => {
  await p.goto(BASE + '/pages/dnd/5e/magic-item-builder', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('select.builder-option');
  await p.locator('input.input.h-40').first().fill(name);
  await p.locator('select.builder-option').first().selectOption(type);
  await p.waitForTimeout(500);
  // A weapon or armour item is not saveable until a type is chosen, so every
  // case that goes on to save one has to make that choice the way a user
  // would. Before the save was gated, these flows were quietly building items
  // with no damage die and no proficiency category -- the exact shape the gate
  // now refuses.
  if (chooseSubtype && (type === 'weapon' || type === 'armor')) {
    await p.getByText('Custom', { exact: true }).first().click();
    await p.waitForTimeout(700);
  }
};

// --------------------------------------------------------------------------

async function customItemOverridesSrd(p) {
  console.log('\ncustom item overrides the SRD item it is named after');
  await login(p);
  await newItem(p, 'Longsword', 'weapon');
  await (await kindSelect(p)).selectOption('mundane');
  await p.waitForTimeout(500);

  // Name the fields rather than counting selects: the Base Weapon Details block
  // legitimately adds four of its own, so a count is a proxy that breaks for
  // reasons that have nothing to do with what is being tested.
  const labels = await p.evaluate(() =>
    [...document.querySelectorAll('select.builder-option')].map(sel => {
      const box = sel.closest('div');
      return (box && box.parentElement && box.parentElement.innerText || '')
        .trim().split('\n')[0];
    }));
  check('mundane hides Rarity', !labels.some(l => /^Rarity$/i.test(l)),
        JSON.stringify(labels));
  const bodyText = await p.locator('body').innerText();
  check('and hides the magical property fields',
        !/Requires Attunement|Magical Attack Bonus/i.test(bodyText));

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

  // Re-open the item from the list. An in-page fetch of /dnd/5e/items carries
  // no token and comes back 401, so testing against its body asserted nothing
  // -- the check passed on the error string. Reading the reloaded builder goes
  // through the same path a user does.
  await p.goto(BASE + '/pages/dnd/5e/magic-items', { waitUntil: 'domcontentloaded' });
  await p.waitForTimeout(3500);
  await p.getByText('Retired Blade', { exact: false }).first().click();
  await p.waitForTimeout(3000);
  const reopened = await p.locator('body').innerText();
  check('the suspended-properties notice is gone after removing them',
        !/Magical properties kept but not applied/i.test(reopened));
  check('and the item itself survived', /Retired Blade/.test(reopened));
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

async function mistakesAreFlaggedAndRecoverable(p) {
  console.log('\nmistakes are flagged as you make them, and clear as you fix them');
  await login(p);

  const saveBtn = () =>
    p.locator('button.header-button[aria-label^="Save" i]:visible').first();
  const saveDisabled = async () =>
    /disabled/.test(await saveBtn().getAttribute('class') || '');
  // The cue is a class on the field, amber for missing and red for invalid.
  const nameCue = async () => {
    const cls = await p.locator('input.input.h-40').first().getAttribute('class');
    if (/builder-field-invalid/.test(cls || '')) return 'invalid';
    if (/builder-field-unfilled/.test(cls || '')) return 'unfilled';
    return 'none';
  };
  const notice = async () => await p.locator('body').innerText();

  await p.goto(BASE + '/pages/dnd/5e/magic-item-builder', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('select.builder-option');
  // NEW ITEM: the builder keeps editing whatever was loaded, so without this a
  // previous case's item is what gets tested.
  await clickButton(p, /NEW ITEM/i);
  await p.waitForTimeout(1200);

  // 1. A brand new item has no name yet.
  check('an unnamed item cannot be saved', await saveDisabled());
  check('the name field is flagged as unfilled', (await nameCue()) === 'unfilled',
        await nameCue());
  check('and the notice says what to do', /Give the item a name/.test(await notice()));

  // 2. Make it worse: a name the spec will reject.
  await p.locator('input.input.h-40').first().fill('9 Lives');
  await p.waitForTimeout(900);
  check('a name starting with a digit is still blocked', await saveDisabled());
  check('flagged invalid rather than unfilled', (await nameCue()) === 'invalid',
        await nameCue());
  // Match the notice's own wording, not "Name must start with a letter" --
  // that is the pre-existing valid-wel warning, which is present either way.
  // Without this distinction the check passed even with the rule removed.
  check('and the notice explains why',
        /has to start with a letter/.test(await notice()));

  // 3. Fix it.
  await p.locator('input.input.h-40').first().fill('Nine Lives');
  await p.waitForTimeout(900);
  check('correcting the name clears the flag', (await nameCue()) === 'none',
        await nameCue());
  check('the notice goes away', !/start with a letter|Give the item a name/.test(await notice()));
  check('and the save becomes available', !(await saveDisabled()));

  // 4. Break it again, the other way, to prove the cue is live and not one-shot.
  await p.locator('input.input.h-40').first().fill('');
  await p.waitForTimeout(900);
  check('emptying the name blocks it again', await saveDisabled());
  check('and flags it unfilled again', (await nameCue()) === 'unfilled', await nameCue());

  // 5. Fix and save for real.
  await p.locator('input.input.h-40').first().fill('Nine Lives');
  await p.waitForTimeout(900);
  const posts = [];
  p.on('response', r => {
    if (r.request().method() === 'POST' && /\/items/.test(r.url())) posts.push(r.status());
  });
  await saveBtn().click();
  await p.waitForTimeout(3000);
  check('the corrected item saves', posts.includes(200), JSON.stringify(posts));
  check('and the server did not reject it', !posts.includes(400), JSON.stringify(posts));
}

async function aWeaponAlwaysHasAType(p) {
  console.log('\na weapon item always has a type, and cannot be left without one');
  await login(p);
  await newItem(p, 'Typed Blade', 'weapon', { chooseSubtype: false });

  // The header is rendered twice -- a sticky copy and the in-content one --
  // so an unqualified .first() picks the hidden sticky button and every click
  // times out instead of testing anything.
  const saveBtn = () =>
    p.locator('button.header-button[aria-label^="Save" i]:visible').first();

  const posts = [];
  p.on('response', r => {
    if (r.request().method() === 'POST' && /\/items/.test(r.url())) posts.push(r.status());
  });

  // Choosing Type = weapon now seeds Custom, so the empty state is not
  // reachable by simply picking a type any more.
  // comps/checkbox is an <i class="fa fa-check ...">, ticked when it carries
  // "black" and unticked when it carries "transparent" -- there is no <input>
  // and no checked attribute to look for.
  const ticked = await p.evaluate(() => {
    const label = [...document.querySelectorAll('span')]
      .find(e => e.textContent.trim() === 'Custom');
    if (!label) return 'no Custom label found';
    const box = label.parentElement.querySelector('i.fa-check');
    if (!box) return 'no checkbox beside it';
    return box.className.includes('black') && !box.className.includes('transparent');
  });
  check('picking weapon seeds a type', ticked === true, String(ticked));
  check('so the save is available immediately',
        !/disabled/.test(await saveBtn().getAttribute('class') || ''));
  check('and no unfilled cue is shown',
        !/Choose a Weapon Type/.test(await p.locator('body').innerText()));

  // The other way into the empty state was unticking the last box.
  await p.getByText('Custom', { exact: true }).first().click();
  await p.waitForTimeout(900);
  check('unticking the only type leaves it in place',
        !/Choose a Weapon Type/.test(await p.locator('body').innerText()));
  check('and the save stays available',
        !/disabled/.test(await saveBtn().getAttribute('class') || ''));

  await saveBtn().click();
  await p.waitForTimeout(3000);
  check('and the item saves', posts.includes(200), JSON.stringify(posts));
}

async function suspendedMagicIsMarkedEverywhere(p) {
  console.log('\nan item holding suspended magic says so in the item list');
  await login(p);
  await newItem(p, 'Retired Blade', 'weapon');
  await p.locator('input.input[type="number"]').first().fill('2');
  await p.waitForTimeout(400);
  await (await kindSelect(p)).selectOption('mundane');
  await p.waitForTimeout(700);
  await clickButton(p, /^SAVE/i);
  await p.waitForTimeout(3000);

  await p.goto(BASE + '/pages/dnd/5e/magic-items', { waitUntil: 'domcontentloaded' });
  await p.waitForTimeout(3500);
  const list = await p.locator('body').innerText();
  // The list renders effective items, whose mechanics are already stripped --
  // so this only works via the ::items-holding-magic subscription. It silently
  // rendered nothing for the whole life of the branch before that.
  check('the My Items row is marked', /magic set aside/.test(list));
  check('and still reads as mundane', /Weapon, mundane/.test(list));

  // A title attribute does nothing on a touch screen, so the marker has to be
  // able to explain itself on tap.
  check('the explanation is not shown until asked for',
        !/switched off/.test(list));
  await p.getByText('magic set aside').first().click();
  await p.waitForTimeout(900);
  const opened = await p.locator('body').innerText();
  check('tapping the marker explains it', /switched off/.test(opened));

  // The row underneath is itself a click target; opening the explainer must
  // not also expand it.
  check('and does not expand the item row',
        !/Base Weapon|REMOVE FOR GOOD/.test(opened));
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

async function itemTextReachesTheCharacterSheet(p) {
  console.log('\nan equipped item carries its prose onto the character sheet');
  await login(p);

  // 1. Build and save a magic item whose magic is entirely prose.
  await newItem(p, 'Moon-Touched Sword', 'weapon');
  await p.locator('textarea').nth(0).fill('A plain-looking longsword.');
  await p.locator('textarea').nth(1).fill('Sheds dim light in a 5-foot radius.');
  await p.waitForTimeout(400);
  await clickButton(p, /^SAVE/i);
  await p.waitForTimeout(3000);

  // 2. Equip it on a character.
  await p.goto(BASE + '/pages/dnd/5e/character-builder', { waitUntil: 'domcontentloaded' });
  await p.waitForTimeout(4500);
  await p.getByText('Equipment', { exact: true }).first().click();
  await p.waitForTimeout(2500);

  // Use Playwright's selectOption rather than setting selectedIndex and
  // dispatching a synthetic event: React tracks the value through its own
  // property descriptor, so a hand-fired 'change' updates the DOM without the
  // app ever hearing about it -- the option appears chosen and nothing is
  // actually equipped.
  const sels = p.locator('select.builder-option-dropdown');
  const count = await sels.count();
  let picked = null;
  for (let i = 0; i < count; i++) {
    const labels = await sels.nth(i).evaluate(e => [...e.options].map(o => o.textContent.trim()));
    const match = labels.find(t => /^Moon-Touched Sword/.test(t));
    if (match) {
      await sels.nth(i).selectOption({ label: match });
      picked = match;
      break;
    }
  }
  check('the item is offered in a picker', picked !== null, String(picked));
  await p.waitForTimeout(2500);
  const equipped = await p.locator('body').innerText();
  check('and selecting it adds it to the character',
        /Moon-Touched Sword/.test(equipped));

  // 3. The sheet's magic-item rows expand to show the prose.
  const expandAndRead = async () => {
    // The sheet's tabs render lowercase in the DOM and are uppercased by CSS,
    // so match on the class and the real text, not on what the screen shows.
    await p.evaluate(() => {
      const tab = [...document.querySelectorAll('div.uppercase')]
        .find(e => e.textContent.trim().toLowerCase() === 'equipment');
      if (tab) tab.click();
    });
    await p.waitForTimeout(1500);
    // The item shows up twice: in the Weapons table (weapon stats) and under
    // Other Magic Items, which is the row that renders item-details. Expand
    // both rather than guessing which index is which.
    const rows = p.locator('tr', { hasText: 'Moon-Touched Sword' });
    const rc = await rows.count();
    for (let i = 0; i < rc; i++) {
      try { await rows.nth(i).click({ timeout: 5000 }); await p.waitForTimeout(900); }
      catch (e) { /* a row that will not expand is not this check's business */ }
    }
    return await p.locator('body').innerText();
  };

  let sheet = await expandAndRead();
  check('the sheet shows the description', /plain-looking longsword/.test(sheet));
  check('the sheet shows the magical properties', /Sheds dim light/.test(sheet));
  check('under its own label', /Magical Properties\./.test(sheet));
  await p.screenshot({ path: `${SHOTS}/sheet-before-save.png`, fullPage: true });

  // 4. Save the character, leave, come back.
  await clickButton(p, /SAVE NEW CHARACTER|SAVE CHARACTER/i);
  await p.waitForTimeout(4000);

  // 5. Reload the page from scratch. Nothing survives in memory, so what comes
  //    back has been through the database and the character load path.
  await p.reload({ waitUntil: 'domcontentloaded' });
  await p.waitForTimeout(5000);
  const reloaded = await expandAndRead();
  check('the reloaded character still has the item', /Moon-Touched Sword/.test(reloaded));
  check('and still shows the description', /plain-looking longsword/.test(reloaded));
  check('and still shows the magical properties', /Sheds dim light/.test(reloaded));
  await p.screenshot({ path: `${SHOTS}/sheet-after-reload.png`, fullPage: true });
}

// --------------------------------------------------------------------------

(async () => {
  const fs = require('fs');
  fs.mkdirSync(SHOTS, { recursive: true });
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  const cases = [customItemOverridesSrd, removeForGoodActuallyRemoves, signedOutSavePrompt,
                 magicalPropertiesField, itemTextReachesTheCharacterSheet,
                 suspendedMagicIsMarkedEverywhere, aWeaponAlwaysHasAType, mistakesAreFlaggedAndRecoverable];
  const consoleFindings = [];
  for (const c of cases) {
    const p = await browser.newPage({ viewport: { width: 1280, height: 900 } });
    // signedOutSavePrompt never logs in; the rest do. Both states are covered.
    const found = watchConsole(p, c.name);
    await shimFonts(p);
    try {
      await c(p);
    } catch (e) {
      console.log(`  FAIL ${c.name} threw: ${e.message.split('\n')[0]}`);
      failures++;
      await p.screenshot({ path: `${SHOTS}/${c.name}-error.png` }).catch(() => {});
    }
    await p.close();
    consoleFindings.push(...found);
  }
  await browser.close();

  console.log('\nthe console stays clean, signed in and signed out');
  const unique = [...new Set(consoleFindings)];
  check('no errors or warnings from the app', unique.length === 0);
  if (unique.length) {
    for (const line of unique.slice(0, 25)) console.log('       ' + line);
    if (unique.length > 25) console.log(`       ... and ${unique.length - 25} more`);
  }
  console.log(`\n${failures === 0 ? 'all checks passed' : failures + ' check(s) failed'}`);
  process.exit(failures === 0 ? 0 : 1);
})();
