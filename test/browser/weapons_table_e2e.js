// Needs: dev bundle (reads the app's internals, which a production bundle compiles away).
// The Weapons table on a 390px phone, dark and light: does it fit, line up, and do what it shows?
//
// Loads a level 1 wizard (proficient with daggers, not longswords) carrying a dagger, longsword,
// net and longbow plus a Flame Tongue, Holy Avenger, Frost Brand and Dwarven Thrower, opens the
// Combat tab and checks: the table fits with no sideways scroll; each heading is within 2px of its
// tiles; tiles are 54x54 and 98x54; names stay on one line; names and tiles are 44px tap targets;
// "no prof" and "hit" strips; the Net's no-roll tile; Frost Brand's +1d6 on its tile; Holy
// Avenger's "vs fiends and undead" line; Ablaze adding +2d6 fire to the tile and the roll; 2H
// showing the longsword's 1d10; and the opened row's details. Then the shield rules (spec rules
// 14-17): with the off hand free the longsword's 1H | 2H switch pulses until touched; with a Shield
// carried a gold "Shield in off hand" pill shows, 1H | 2H is greyed and says why, the two-handed
// longbow's tiles turn to outlines, say "Needs both hands" and add it to the roll; freeing the off
// hand brings the pulse back. Last, a main-hand and an off-hand weapon each get a hand pill on
// their name line ("main", "off"), read as "main hand" / "off hand", and no other row does.
// The + menu: only weapons with something conditional get one; Tab reaches it after the damage
// tile, Enter opens it on "Roll damage", Down moves, Esc closes and returns to the +; right-click
// opens it; "Roll vs" rolls the bonus; a once-per-dawn use rolls, then reads "used"; on the last row
// the menu opens upward. Last, Weapon Mastery with made-up 5.5 data (this branch has none): a
// trained longsword shows the Topple band, an untrained dagger shows none and says so when opened.
// Then desktop at 1280px: Weapon, Details, To hit, Damage; properties in the Details column; 58x54
// and 102x54 tiles under their headings; the ctrl/shift tooltip kept.
//
// Needs:     server (`./scripts/e2e/run.sh test/browser/weapons_table_e2e.js`). No login.
// Runs in:   ~30s. SHOT_DIR=<dir> saves weapons-dark.png and weapons-light.png.
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const SHOT_DIR = process.env.SHOT_DIR;

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

// Puts a level 1 wizard carrying the test weapons into the builder, and a Shield when `shield`.
const load = (page, shield) => page.evaluate(shield => {
  const rd = cljs.reader.read_string;
  const s = 'orcpub.entity.strict', c = 'orcpub.dnd.e5.character';
  const held = `{:${c}.equipment/quantity 1 :${c}.equipment/equipped? true}`;
  const opts = ks => ks.map(k => `{:${s}/key ${k} :${s}/map-value ${held}}`).join(' ');
  const strict = `{:${s}/selections [
    {:${s}/key :ability-scores :${s}/option {:${s}/key :standard-scores
      :${s}/map-value {:${c}/str 15 :${c}/dex 14 :${c}/con 13 :${c}/int 12 :${c}/wis 10 :${c}/cha 8}}}
    {:${s}/key :class :${s}/options [{:${s}/key :wizard
      :${s}/selections [{:${s}/key :levels :${s}/options [{:${s}/key :level-1}]}]}]}
    {:${s}/key :weapons :${s}/options [${opts([':dagger', ':longsword', ':net', ':longbow'])}]}
    {:${s}/key :magic-weapons :${s}/options [${opts([':flame-tongue-longsword', ':holy-avenger-longsword',
                                                      ':frost-brand-rapier', ':dwarven-thrower'])}]}
    ${shield ? `{:${s}/key :armor :${s}/options [${opts([':shield'])}]}` : ''}]}`;
  re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray(
    [rd(':set-character'), orcpub.dnd.e5.character.from_strict(rd(strict))], true));
}, !!shield);

// Puts a level 1 wizard carrying only these magic weapons into the builder.
const loadMagic = (page, keys) => page.evaluate(keys => {
  const rd = cljs.reader.read_string;
  const s = 'orcpub.entity.strict', c = 'orcpub.dnd.e5.character';
  const held = `{:${c}.equipment/quantity 1 :${c}.equipment/equipped? true}`;
  const strict = `{:${s}/selections [
    {:${s}/key :ability-scores :${s}/option {:${s}/key :standard-scores
      :${s}/map-value {:${c}/str 15 :${c}/dex 14 :${c}/con 13 :${c}/int 12 :${c}/wis 10 :${c}/cha 8}}}
    {:${s}/key :class :${s}/options [{:${s}/key :wizard
      :${s}/selections [{:${s}/key :levels :${s}/options [{:${s}/key :level-1}]}]}]}
    {:${s}/key :magic-weapons :${s}/options [${keys.map(k => `{:${s}/key ${k} :${s}/map-value ${held}}`).join(' ')}]}]}`;
  re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray(
    [rd(':set-character'), orcpub.dnd.e5.character.from_strict(rd(strict))], true));
}, keys);

// Layout numbers for the section: widths, heading offsets, tile sizes, one-line names, tap targets.
const measure = page => page.evaluate(() => {
  const section = [...document.querySelectorAll('.weapons-section')].find(e => e.offsetParent);
  const box = section.getBoundingClientRect();
  const cx = e => { const b = e.getBoundingClientRect(); return b.left + b.width / 2; };
  const heads = [...section.querySelectorAll('.weapon-head > span')];
  const rows = [...section.querySelectorAll('.weapon-row')];
  const hit = rows.map(r => r.querySelector('.weapon-hit-tile'));
  const dmg = rows.map(r => r.querySelector('.weapon-damage-tile, .weapon-no-roll'));
  const size = t => { const b = t.getBoundingClientRect(); return `${Math.round(b.width)}x${Math.round(b.height)}`; };
  return {
    rows: rows.length, fits: section.scrollWidth <= section.clientWidth,
    pageFits: document.documentElement.scrollWidth <= window.innerWidth,
    hitOff: Math.max(...hit.map(t => Math.abs(cx(t) - cx(heads[1])))),
    dmgOff: Math.max(...dmg.map(t => Math.abs(cx(t) - cx(heads[2])))),
    hitSizes: [...new Set(hit.map(size))], dmgSizes: [...new Set(dmg.map(size))],
    oneLine: rows.every(r => { const n = r.querySelector('.weapon-name'); return n.scrollHeight <= n.clientHeight + 1; }),
    small: [...section.querySelectorAll('.weapon-cell-name, .weapon-tile')]
      .filter(e => { const b = e.getBoundingClientRect(); return b.height < 44 || b.width < 44; }).length,
    outside: [...section.querySelectorAll('.weapon-row *')]
      .filter(e => { const b = e.getBoundingClientRect(); return b.width && (b.right > box.right + 0.5 || b.left < box.left - 0.5); }).length,
  };
});

async function layout(page, theme) {
  const m = await measure(page);
  check(`${theme}: one row per weapon`, m.rows === 8, `${m.rows}`);
  check(`${theme}: the table fits, no sideways scroll`, m.fits && m.pageFits);
  check(`${theme}: headings within 2px of their tiles`, m.hitOff <= 2 && m.dmgOff <= 2, `${m.hitOff}, ${m.dmgOff}`);
  check(`${theme}: to-hit tiles are 54x54`, m.hitSizes.join() === '54x54', m.hitSizes.join());
  check(`${theme}: damage tiles are 98x54`, m.dmgSizes.join() === '98x54', m.dmgSizes.join());
  check(`${theme}: names stay on one line`, m.oneLine);
  check(`${theme}: names and tiles are 44px tap targets`, m.small === 0, `${m.small} smaller`);
  check(`${theme}: nothing sticks out of the section`, m.outside === 0, `${m.outside}`);
  if (SHOT_DIR) await page.locator('.weapons-section').screenshot({ path: `${SHOT_DIR}/weapons-${theme}.png` });
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({
    viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true,
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1' });
  await ctx.addInitScript(() => {
    try {
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', '"summer-patch-2026"');
    } catch (e) {}
  });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => { try { return !!re_frame.core.dispatch_sync; } catch (e) { return false; } },
                             null, { timeout: 60000 });
  await load(page);
  // a phone shows the sheet under the Details tab, its sections behind icon tabs
  await page.locator('.builder-tab', { hasText: 'Details' }).first().click({ timeout: 20000 }).catch(() => {});
  await page.waitForFunction(() => [...document.querySelectorAll('div.b-b-2.pointer')]
    .some(d => d.offsetParent && d.innerHTML.includes('sword-clash')), null, { timeout: 20000 }).catch(() => {});
  await page.evaluate(() => {
    const t = [...document.querySelectorAll('div.b-b-2.pointer')].find(d => d.offsetParent && d.innerHTML.includes('sword-clash'));
    if (t) t.click();
  });
  const section = page.locator('.weapons-section');
  if (!await section.waitFor({ timeout: 20000 }).then(() => true, () => false)) {
    check('the Combat tab shows the Weapons table', false, 'no .weapons-section');
  } else {
    const row = name => page.locator('.weapon-row')
      .filter({ has: page.locator('.weapon-name', { hasText: new RegExp(`^${name}$`) }) });
    await layout(page, 'dark');
    check('no reason pill while the off hand is free', await page.locator('.weapons-reason').count() === 0);

    const strip = async name => (await row(name).locator('.weapon-hit-tile .weapon-strip').innerText()).trim();
    check('a weapon the wizard is not proficient with says "no prof"', await strip('Longsword') === 'no prof');
    check('one it is proficient with says "hit"', await strip('Dagger') === 'hit');
    check('the Net gets the no-roll tile', await row('Net').locator('.weapon-no-roll').count() === 1);
    check('Frost Brand shows its +1d6 on the damage tile',
          /\+1d6/.test(await row('Frost Brand, Rapier').locator('.weapon-damage-tile').innerText()));
    check('Holy Avenger has a "vs fiends and undead" line with +2d10 radiant',
          /vs fiends and undead[\s\S]*\+2d10 radiant/i.test(await row('Holy Avenger, Longsword').innerText()));

    const flame = row('Flame Tongue, Longsword');
    check('Flame Tongue starts unlit: nothing extra on its tile',
          !/\+2d6/.test(await flame.locator('.weapon-damage-tile').innerText()));
    await flame.getByRole('radio', { name: 'Ablaze' }).click();
    const shows = (loc, text) => loc.filter({ hasText: text }).waitFor({ timeout: 5000 }).then(() => true, () => false);
    check('Ablaze puts +2d6 on the tile', await shows(flame.locator('.weapon-damage-tile'), '+2d6'));
    await flame.locator('.weapon-damage-tile').click();
    const msg = await page.locator('.message').first().innerText({ timeout: 5000 }).catch(() => '');
    check('and the damage roll includes the fire', /1d8\+2 slashing:[\s\S]*2d6 fire:/.test(msg), msg.replace(/\s+/g, ' ').slice(0, 140));

    const plain = row('Longsword');
    check('with the off hand free, the longsword\'s 1H | 2H switch pulses',
          await plain.locator('.weapon-switch.pulse').count() === 1);
    await plain.getByRole('radio', { name: '1H' }).click();  // already chosen: a touch that changes nothing else
    check('touching it stops the pulse',
          await plain.locator('.weapon-switch.pulse').waitFor({ state: 'detached', timeout: 5000 }).then(() => true, () => false));
    await plain.getByRole('radio', { name: '2H' }).click();
    check('2H shows the longsword\'s two-handed 1d10', await shows(plain.locator('.weapon-damage-tile'), '1d10+2'));
    await plain.getByRole('radio', { name: '1H' }).click();  // back to 1H: the pulse only offers 2H

    const holy = row('Holy Avenger, Longsword');
    await holy.locator('.weapon-name-line').click();
    const opened = await holy.locator('.weapon-opened').waitFor({ timeout: 5000 }).then(() => true, () => false);
    check('tapping the name opens the row with its properties and rules',
          opened && await shows(holy.locator('.weapon-opened'), 'Legendary (requires attunement) \u00B7 Martial melee \u00B7 Versatile (1d10)')
          && await shows(holy.locator('.weapon-opened'), 'When you hit a fiend or an undead with it'));

    // the shield rules
    await load(page, true);
    const pill = page.locator('.weapons-reason');
    check('a Shield shows the "Shield in off hand" pill',
          await pill.waitFor({ timeout: 5000 }).then(() => true, () => false) && (await pill.innerText()).trim() === 'Shield in off hand');
    const ls = row('Longsword');
    check('1H | 2H is greyed and 2H cannot be picked',
          await ls.locator('.weapon-switch.disabled').count() === 1 && await ls.getByRole('radio', { name: '2H' }).isDisabled());
    check('and the longsword is back to one-handed dice', /1d8\+2/.test(await ls.locator('.weapon-damage-tile').innerText()));
    check('no pulse while the off hand holds a shield', await ls.locator('.weapon-switch.pulse').count() === 0);
    await ls.locator('.weapon-name-line').click();
    check('the opened longsword says why', await shows(ls, 'Two hands: not while your off hand holds a Shield.'));
    const bow = row('Longbow');
    check('the two-handed longbow\'s tiles become outlines', await bow.locator('.needs-hands').count() === 2);
    await bow.locator('.weapon-name-line').click();
    check('the opened longbow says it needs both hands', await shows(bow, 'Needs both hands: your off hand holds a Shield.'));
    await bow.locator('.weapon-hit-tile').click();
    check('and its roll says so', await shows(page.locator('.message'), 'Longbow attack (needs both hands):'));
    await load(page, false);
    check('freeing the off hand removes the pill',
          await pill.waitFor({ state: 'detached', timeout: 5000 }).then(() => true, () => false));
    check('and brings the pulse back', await row('Longsword').locator('.weapon-switch.pulse').waitFor({ timeout: 5000 }).then(() => true, () => false));

    // which weapon is in which hand
    // one hand at a time: with no saved character these events queue a :set-character built from the
    // character as it was, so a second event before the first lands would overwrite it
    const wield = ev => page.evaluate(ev => re_frame.core.dispatch_sync(cljs.reader.read_string(ev)), ev);
    const handPill = r => r.locator('.weapon-name-line .weapon-hand-pill');
    await wield('[:orcpub.dnd.e5.character/wield-main-hand-weapon nil :dagger]');
    check('the main-hand weapon says "main" on its name line', await shows(handPill(row('Dagger')), 'main')
          && await handPill(row('Dagger')).getAttribute('aria-label') === 'main hand');
    await wield('[:orcpub.dnd.e5.character/wield-off-hand-weapon nil :flame-tongue-longsword]');
    check('the off-hand weapon says "off", its hand mirrored',
          await shows(handPill(row('Flame Tongue, Longsword')), 'off')
          && await handPill(row('Flame Tongue, Longsword')).locator('.fa-flip-horizontal').count() === 1
          && await handPill(row('Flame Tongue, Longsword')).getAttribute('aria-label') === 'off hand');
    check('no other weapon has a hand pill', await page.locator('.weapon-hand-pill').count() === 2);

    // the + menu
    const plus = r => r.locator('.weapon-plus');
    check('a weapon with nothing conditional has no +', await plus(row('Frost Brand, Rapier')).count() === 0);
    const holyRow = row('Holy Avenger, Longsword');
    check('a weapon with a target has a +, announced as a menu',
          await plus(holyRow).getAttribute('aria-haspopup', { timeout: 3000 }).catch(() => null) === 'menu');
    await holyRow.locator('.weapon-damage-tile').focus({ timeout: 5000 }).catch(() => {});
    await page.keyboard.press('Tab');
    const isFocused = loc => loc.evaluate(e => e === document.activeElement, null, { timeout: 3000 }).catch(() => false);
    check('Tab goes from the damage tile to its +', await isFocused(plus(holyRow)));
    await page.keyboard.press('Enter');
    const focused = () => page.evaluate(() => document.activeElement && document.activeElement.textContent);
    check('Enter opens the menu on Roll damage',
          await holyRow.locator('.weapon-menu').waitFor({ timeout: 5000 }).then(() => true, () => false)
          && /^Roll damage/.test(await focused()));
    await page.keyboard.press('ArrowDown');
    check('Down moves to the next item', /^Roll vs fiends and undead/.test(await focused()));
    await page.keyboard.press('Escape');
    const closed = await holyRow.locator('.weapon-menu').waitFor({ state: 'detached', timeout: 5000 }).then(() => true, () => false);
    check('Esc closes the menu and returns to the +',
          closed && await isFocused(plus(holyRow)),
          await page.evaluate(() => document.activeElement && document.activeElement.className));
    await holyRow.locator('.weapon-damage-tile').click({ button: 'right' });
    check('right-click on the tile opens the menu',
          await holyRow.locator('.weapon-menu').waitFor({ timeout: 5000 }).then(() => true, () => false));
    await holyRow.getByRole('menuitem', { name: /Roll vs fiends and undead/ }).click({ timeout: 5000 }).catch(() => {});
    check('Roll vs rolls the base and the bonus',
          await shows(page.locator('.message'), 'roll vs fiends and undead: 1d8+5 slashing') &&
          await shows(page.locator('.message'), '2d10 radiant'));

    await loadMagic(page, [':holy-avenger-longsword', ':javelin-of-lightning']);
    const jav = row('Javelin of Lightning');
    await jav.waitFor({ timeout: 5000 }).catch(() => {});
    await plus(jav).click({ timeout: 5000 }).catch(() => {});
    const use = jav.getByRole('menuitem', { name: /\+4d6 lightning/ });
    check('the last row\'s menu opens upward',
          await jav.locator('.weapon-menu.up').waitFor({ timeout: 5000 }).then(() => true, () => false));
    await use.click({ timeout: 5000 }).catch(() => {});
    check('a once-per-dawn use rolls', await shows(page.locator('.message'), '+4d6 lightning: 1d6+'));
    await plus(jav).click({ timeout: 5000 }).catch(() => {});
    check('and then reads "used"', await shows(use, 'used')
          && await use.getAttribute('aria-disabled', { timeout: 3000 }).catch(() => null) === 'true');
    await page.keyboard.press('Escape');
    await load(page, false);
    await page.waitForFunction(() => document.querySelectorAll('.weapon-row').length === 8, null, { timeout: 5000 }).catch(() => {});

    await page.locator('div.pointer', { hasText: 'Light Theme' }).first().click();
    await page.waitForTimeout(300);
    await layout(page, 'light');
  }
  // Weapon Mastery (writing-browser-tests.md rule 6): made-up weapons enter as custom items, the
  // app's own path into the shared weapons map; only the table's own subscriptions are stood in
  await page.evaluate(() => {
    const rd = cljs.reader.read_string, rf = re_frame.core, m = 'orcpub.dnd.e5.magic-items', w = 'orcpub.dnd.e5.weapons';
    rf.dispatch_sync(rd(`[:${m}/set-custom-items
      [{:db/id 900001 :${m}/name "Test Blade" :${m}/type :weapon :${m}/item-subtype :longsword :${w}/mastery :topple}
       {:db/id 900002 :${m}/name "Test Knife" :${m}/type :weapon :${m}/item-subtype :dagger :${w}/mastery :nick}]]`));
    rf.reg_sub(rd(':orcpub.dnd.e5.weapons/mastery-definitions'),
               () => rd('{:topple {:name "Topple" :trigger :hit :text "Topple rule."} :nick {:name "Nick" :text "Nick rule."}}'));
    rf.reg_sub(rd(':orcpub.dnd.e5.character/weapon-masteries'), () => rd('#{:longsword}'));
  });
  await loadMagic(page, [':test-blade', ':test-knife']);
  const tab = name => page.evaluate(name => {
    const rd = cljs.reader.read_string;
    re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray(
      [rd(':orcpub.dnd.e5.character/set-selected-display-tab'), name], true));
  }, name);
  const tabIs = name => page.waitForFunction(name => {
    const rd = cljs.reader.read_string;
    return cljs.core.deref(re_frame.core.subscribe(rd('[:orcpub.dnd.e5.character/selected-display-tab]'))) === name;
  }, name, { timeout: 5000 }).then(() => true, () => false);
  await tab('summary');
  const left = await tabIs('summary') && await page.locator('.weapons-section').waitFor({ state: 'detached', timeout: 5000 }).then(() => true, () => false);
  await tab('combat');
  check('the sheet leaves the Combat tab and comes back (remounting the table)', left && await tabIs('combat'));
  if (await page.locator('.weapons-section').waitFor({ timeout: 20000 }).then(() => true, () => false)) {
    const mrow = name => page.locator('.weapon-row')
      .filter({ has: page.locator('.weapon-name', { hasText: new RegExp(`^${name}$`) }) });
    const band = mrow('Test Blade').locator('.weapon-mastery-band');
    check('a trained longsword shows its Topple band',
          await band.waitFor({ timeout: 5000 }).then(() => true, () => false)
          && /TOPPLE\s*ON HIT\s*DC 12 Con or prone/i.test(await band.innerText()));
    if (SHOT_DIR) {
      // a roll's message bar and the sticky header sit over the table: close one, centre the row
      await page.locator('.message').first().click({ timeout: 2000 }).catch(() => {});
      await mrow('Test Blade').evaluate(e => e.scrollIntoView({ block: 'center' }));
      await page.waitForTimeout(300);
      await mrow('Test Blade').screenshot({ path: `${SHOT_DIR}/weapons-mastery-band.png` });
    }
    check('an untrained dagger shows no band', await mrow('Test Knife').locator('.weapon-mastery-band').count() === 0);
    await mrow('Test Knife').locator('.weapon-name-line').click({ timeout: 5000 }).catch(() => {});
    check('and its opened row says it is not trained',
          await mrow('Test Knife').locator('.weapon-opened', { hasText: 'Mastery: Nick (not trained)' })
            .waitFor({ timeout: 5000 }).then(() => true, () => false));
  } else {
    check('the table comes back for the mastery checks', false);
  }
  // desktop
  const desk = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await desk.addInitScript(() => {
    try { localStorage.setItem('orcpub:no-cookie-banner', '1'); localStorage.setItem('whats-new-seen', '"summer-patch-2026"'); } catch (e) {}
  });
  const dp = await desk.newPage();
  dp.on('pageerror', e => errors.push(e.message));
  await dp.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
  await dp.waitForFunction(() => { try { return !!re_frame.core.dispatch_sync; } catch (e) { return false; } }, null, { timeout: 60000 });
  await load(dp, false);
  await dp.getByText('combat', { exact: true }).first().click({ timeout: 10000 }).catch(() => {});
  const dsec = dp.locator('.weapons-section.desktop').filter({ visible: true }).first();
  if (await dsec.waitFor({ timeout: 20000 }).then(() => true, () => false)) {
    const dm = await dsec.evaluate(sec => {
      const heads = Object.fromEntries([...sec.querySelectorAll('.weapon-head > span')].map(h => [h.textContent, h]));
      const cx = e => { const b = e.getBoundingClientRect(); return b.left + b.width / 2; };
      const rows = [...sec.querySelectorAll('.weapon-row')];
      const size = t => { const b = t.getBoundingClientRect(); return `${Math.round(b.width)}x${Math.round(b.height)}`; };
      const hit = rows.map(r => r.querySelector('.weapon-hit-tile'));
      const dmg = rows.map(r => r.querySelector('.weapon-damage-tile, .weapon-no-roll'));
      return { heads: Object.keys(heads), fits: sec.scrollWidth <= sec.clientWidth,
        off: Math.max(...hit.map(t => Math.abs(cx(t) - cx(heads['To hit']))), ...dmg.map(t => Math.abs(cx(t) - cx(heads['Damage'])))),
        hitSizes: [...new Set(hit.map(size))], dmgSizes: [...new Set(dmg.map(size))],
        details: heads['Details'] ? rows.every(r => Math.abs(r.querySelector('.weapon-grey-line').getBoundingClientRect().left
                                                   - heads['Details'].getBoundingClientRect().left) < 2) : false,
        tooltips: sec.querySelectorAll('.tooltip .weapon-hit-tile').length };
    });
    check('desktop: Weapon, Details, To hit, Damage', dm.heads.join() === 'Weapon,Details,To hit,Damage', dm.heads.join());
    check('desktop: properties sit in the Details column', dm.details);
    check('desktop: tiles are 58x54 and 102x54', dm.hitSizes.join() === '58x54' && dm.dmgSizes.join() === '102x54',
          `${dm.hitSizes} ${dm.dmgSizes}`);
    check('desktop: headings within 2px of their tiles, and it fits', dm.off <= 2 && dm.fits, `${dm.off}`);
    check('desktop: the ctrl/shift tooltip stays', dm.tooltips > 0);
  } else {
    check('desktop: the table uses the desktop layout', false);
  }
  await desk.close();

  check('no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));

  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
