// The whole chain, through the real app: author a pack of homebrew across every content type this
// branch can drive, export it with the real Export button, wipe it, import the real file back, and
// then USE as much of it as a character can actually reach.
//
// Storage round-trips are already covered elsewhere. What this adds is BREADTH — several content
// types in one pack, including the two schema shapes that are easy to get wrong:
//   * conditional fields   (draconic ancestry: Line vs Cone reveals different numbers)
//   * the :rows form       (fighting styles: effects added on demand, tags, :classes chips)
// and then the last mile that a round-trip cannot prove: the imported content being selectable on
// a character and changing the numbers on the sheet.
//
// Prereqs:  lein garden once && lein fig:build && lein e2e-server   (port 8890)
// Run:      node test/e2e/homebrew-grand-tour.js
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, checker, dbAt, controlFor, fill, clickText,
        fillEffectBonus, dismissCookieBar, clickTab, pickFromAnySelect, pickOption } = require('./lib');

const PACK = 'Grand Tour';
const shot = (page, name) =>
  page.screenshot({ path: path.join(SHOTS, 'grand-tour', name), fullPage: true, type: 'jpeg', quality: 72 });

// ── small drivers over the real UI ────────────────────────────────────────────────
const save = (page) => clickText(page, /save to browser storage/i);

async function openBuilder(page, seg) {
  await page.goto(`${BASE}/pages/dnd/5e/${seg}`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1400);
  await dismissCookieBar(page);
}

async function head(page, name) {
  await fill(page, 'Name', name);
  await fill(page, 'Option Source Name', PACK);
  await fill(page, 'Description', `${name}, authored by the grand tour.`);
}

const addEffect = (page, title) => page.evaluate((t) => {
  const b = [...document.querySelectorAll('button')].find(e => e.textContent.trim() === `+ ${t}`);
  if (!b) return false;
  b.click();
  return true;
}, title);

const toggleChip = (page, text) => page.evaluate((t) => {
  const b = [...document.querySelectorAll('.chip-row .chip')].find(e => e.textContent.trim() === t);
  if (!b) return false;
  b.click();
  return true;
}, text);

const pageHas = async (page, rx) => rx.test(await page.locator('#app').innerText());

(async () => {
  fs.mkdirSync(path.join(SHOTS, 'grand-tour'), { recursive: true });
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ acceptDownloads: true, viewport: { width: 1300, height: 1200 } });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));
  page.on('console', m => { if (m.type() === 'error' && !/ERR_(CONNECTION|NAME|INTERNET)/.test(m.text())) errors.push(m.text()); });

  const authored = [];
  try {
    // ═══════ 1. AUTHOR ═══════════════════════════════════════════════════════════
    // tier 1 — name / source / description, the whole type
    for (const [seg, name] of [['language-builder', 'Dockside Cant'],
                               ['boon-builder', 'Boon of the Tideborn'],
                               ['invocation-builder', 'Eyes of the Deep']]) {
      await openBuilder(page, seg);
      await head(page, name);
      check(`authored ${seg.replace('-builder', '')}: ${name}`, await save(page));
      authored.push(name);
    }

    // conditional schema — Line reveals width/length, Cone reveals a different number
    await openBuilder(page, 'draconic-ancestry-builder');
    await head(page, 'Frost Wyrm');
    check('breath weapon: chose a damage type', await pickOption(page, 'Breath Weapon Damage Type', /^cold$/));
    check('the Line-only numbers are hidden before a shape is chosen',
          (await controlFor(page, 'Line Width')) === null);
    check('breath weapon: chose Line', await pickOption(page, 'Breath Weapon Shape', /^line$/));
    await page.waitForTimeout(400);
    check(':when revealed the Line-only numbers', !!(await controlFor(page, 'Line Width')));
    check('and the Cone-only number stays hidden', (await controlFor(page, 'Cone Length')) === null);
    await fill(page, 'Line Width', '5');
    await fill(page, 'Line Length', '30');
    check('breath weapon: chose a save', await pickOption(page, 'Breath Weapon Save', /dexterity/));
    await shot(page, '01-draconic.jpg');
    check('authored draconic ancestry: Frost Wyrm', await save(page));
    authored.push('Frost Wyrm');

    // the :rows form — two styles, exercising both effect kinds, a set tag, and :classes
    await openBuilder(page, 'fighting-style-builder');
    await head(page, 'Bulwark');
    check('added the AC Bonus row', await addEffect(page, 'AC Bonus'));
    await page.waitForTimeout(300);
    check('typed the AC bonus', await fillEffectBonus(page, 'AC Bonus', 1));
    check('authored fighting style: Bulwark (open to every class)', await save(page));
    authored.push('Bulwark');

    await openBuilder(page, 'fighting-style-builder');
    await head(page, 'Sharpshot');
    check('added the Attack Bonus row', await addEffect(page, 'Attack Bonus'));
    await page.waitForTimeout(300);
    check('typed the attack bonus', await fillEffectBonus(page, 'Attack Bonus', 2));
    check('restricted it to ranged weapons', await pickOption(page, 'Ranged', /^ranged (weapons )?only$/));
    check('and to the Fighter class', await toggleChip(page, 'Fighter'));
    await page.waitForTimeout(300);
    await shot(page, '02-fighting-style.jpg');
    check('authored fighting style: Sharpshot', await save(page));
    authored.push('Sharpshot');

    // ═══════ 2. EVERYTHING LANDED ════════════════════════════════════════════════
    const plugins = await dbAt(page, `[:plugins "${PACK}"]`);
    for (const [label, rx] of [['language', /dockside cant/i], ['boon', /tideborn/i],
                               ['invocation', /eyes of the deep/i], ['draconic ancestry', /frost wyrm/i],
                               ['fighting styles', /bulwark[\s\S]*sharpshot|sharpshot[\s\S]*bulwark/i]]) {
      check(`${label} is in :plugins under "${PACK}"`, rx.test(plugins));
    }
    check('the conditional breath weapon kept its numbers', /:line-width 5/.test(plugins) && /:line-length 30/.test(plugins));
    check('the restricted style kept its tag and its class', /:ranged\? true/.test(plugins) && /:classes #\{:fighter\}/.test(plugins));

    // ═══════ 3. EXPORT — the real button, the real download ══════════════════════
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
    await page.waitForTimeout(1500);
    await dismissCookieBar(page);
    await shot(page, '03-my-content.jpg');
    await page.locator('#app .item-list-item').filter({ hasText: PACK }).first()
              .locator('text=expand').first().click();
    await page.waitForTimeout(400);
    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: 20000 }),
      page.locator('#app button:visible').filter({ hasText: /^export$/i }).first().click(),
    ]);
    const file = path.join(os.tmpdir(), download.suggestedFilename());
    await download.saveAs(file);
    const text = fs.readFileSync(file, 'utf8');
    check(`exported ${download.suggestedFilename()}`, /\.orcbrew$/.test(file));
    for (const [label, rx] of [['languages', /:orcpub\.dnd\.e5\/languages/], ['boons', /boons/],
                               ['invocations', /invocations/], ['ancestries', /draconic-ancestries/],
                               ['fighting styles', /fighting-styles/]]) {
      check(`the file carries ${label}`, rx.test(text));
    }

    // ═══════ 4. WIPE, then IMPORT the real file ══════════════════════════════════
    await page.evaluate(() => localStorage.setItem('plugins', '{"Default Option Source" {}}'));
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
    await page.waitForTimeout(1200);
    check('the pack is gone before importing', !(await pageHas(page, new RegExp(PACK))));

    const input = await page.$('input[type=file]');
    check('found the import input', !!input);
    await input.setInputFiles(file);
    await page.waitForTimeout(2500);
    const back = await dbAt(page, `[:plugins "${PACK}"]`);
    for (const [label, rx] of [['language', /dockside cant/i], ['boon', /tideborn/i],
                               ['invocation', /eyes of the deep/i], ['ancestry', /frost wyrm/i],
                               ['both styles', /bulwark/i]]) {
      check(`${label} survived the round trip`, rx.test(back));
    }
    check('and so did the details that are easy to drop',
          /:line-width 5/.test(back) && /:ranged\? true/.test(back) && /:classes #\{:fighter\}/.test(back),
          back.slice(0, 200));

    // ═══════ 5. USE IT ON A CHARACTER ════════════════════════════════════════════
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(2500);
    await dismissCookieBar(page);

    // a Dragonborn Fighter reaches TWO of the imported types: the ancestry and the style.
    // The builder opens on the Race tab and seeds a default class.
    check('on the Race tab', await clickTab(page, 'Race'));
    check('picked the Dragonborn race', await clickText(page, /^Dragonborn$/));
    await page.waitForTimeout(1800);
    check('the imported draconic ancestry is offered to a Dragonborn',
          await pageHas(page, /frost wyrm/i));
    check('picked the imported ancestry', await clickText(page, /^Frost Wyrm$/));
    await page.waitForTimeout(1200);
    await shot(page, '04-ancestry-picked.jpg');

    check('navigated to Class / Level', await clickTab(page, 'Class\\s*/\\s*Level'));
    // Class is a <select>; races and fighting styles are clickable cards. Not a distinction
    // worth papering over in a helper — the page really does use two different controls.
    check('picked the Fighter class', await pickFromAnySelect(page, /^fighter$/i));
    await page.waitForTimeout(2000);

    check('the open homebrew style is offered to the Fighter', await pageHas(page, /bulwark/i));
    check('and so is the one restricted to :classes #{:fighter}', await pageHas(page, /sharpshot/i));

    const acBefore = await page.evaluate(() => {
      const l = [...document.querySelectorAll('div,span')]
        .find(e => e.children.length === 0 && /^armor class$/i.test(e.textContent.trim()));
      if (!l) return null;
      for (let n = l.parentElement, i = 0; n && i < 4; n = n.parentElement, i++) {
        const m = n.textContent.replace(/armor class/i, '').match(/\d+/);
        if (m) return Number(m[0]);
      }
      return null;
    });
    check('picked the imported fighting style', await clickText(page, /^Bulwark$/));
    await page.waitForTimeout(2000);
    const acAfter = await page.evaluate(() => {
      const l = [...document.querySelectorAll('div,span')]
        .find(e => e.children.length === 0 && /^armor class$/i.test(e.textContent.trim()));
      if (!l) return null;
      for (let n = l.parentElement, i = 0; n && i < 4; n = n.parentElement, i++) {
        const m = n.textContent.replace(/armor class/i, '').match(/\d+/);
        if (m) return Number(m[0]);
      }
      return null;
    });
    check('picking the imported style moves the sheet AC by the authored +1',
          acBefore !== null && acAfter === acBefore + 1, `${acBefore} -> ${acAfter}`);

    const chosen = await dbAt(page, '[:character :orcpub.entity/options :class]');
    check('and the character records both imported picks',
          /:bulwark/.test(chosen), chosen.slice(0, 200));
    const race = await dbAt(page, '[:character :orcpub.entity/options :race]');
    check('including the homebrew draconic ancestry on the race side',
          /frost-wyrm/i.test(race), race.slice(0, 200));
    await shot(page, '05-character.jpg');

    // ═══════ 6. A SECOND CHARACTER — the types a Fighter cannot reach ════════════
    // Invocations and boons are Warlock features (level 2 and 3), so no single character reaches
    // every type. That is the game's shape, not a limitation of the tour.
    // NEW raises a confirm when there are unsaved changes. Leaving it unanswered does NOT start a
    // new character — it leaves you editing the first one, and every later assertion still passes
    // while quietly describing the wrong thing. (The screenshot is what caught this: the header
    // still read "Dragonborn Warlock".)
    check('clicked NEW', await clickText(page, /^new$/i));
    await page.waitForTimeout(800);
    check('confirmed the new character', await clickText(page, /^create new character$/i));
    await page.waitForTimeout(2500);
    const fresh = await dbAt(page, '[:character :orcpub.entity/options :race]');
    check('it really is a second character, not the first one edited',
          !/dragonborn/i.test(fresh), fresh.slice(0, 120));
    check('on the Class / Level tab', await clickTab(page, 'Class\\s*/\\s*Level'));
    check('picked the Warlock class', await pickFromAnySelect(page, /^warlock$/i));
    // level 3: invocations arrive at 2, the pact boon at 3
    const levelled = await page.evaluate(() => {
      for (const sel of document.querySelectorAll('select')) {
        const opts = [...sel.options].map(o => o.textContent.trim());
        if (opts.includes('1') && opts.includes('3') && opts.length < 25) {
          const set = Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, 'value').set;
          const o = [...sel.options].find(x => x.textContent.trim() === '3');
          set.call(sel, o.value);
          sel.dispatchEvent(new Event('change', { bubbles: true }));
          return true;
        }
      }
      return false;
    });
    check('took the Warlock to level 3', levelled);
    await page.waitForTimeout(2500);

    // Eldritch Invocations carry :tags #{:spells} (options.cljc), so the selection renders on the
    // SPELLS tab, not Class / Level — a warlock's invocations are not on its class panel.
    check('on the Spells tab', await clickTab(page, 'Spells'));
    await page.waitForTimeout(1800);
    check('the imported invocation is offered at level 2', await pageHas(page, /eyes of the deep/i));
    check('picked the imported invocation', await clickText(page, /^Eyes of the Deep$/));
    await page.waitForTimeout(1500);
    check('back on Class / Level for the pact boon', await clickTab(page, 'Class\\s*/\\s*Level'));
    await page.waitForTimeout(1500);
    check('the imported boon is offered as a Pact Boon at level 3',
          await pageHas(page, /boon of the tideborn/i));
    check('picked the imported boon', await clickText(page, /^Boon of the Tideborn$/));
    await page.waitForTimeout(1500);
    await shot(page, '06-warlock.jpg');

    const warlock = await dbAt(page, '[:character :orcpub.entity/options :class]');
    check('the Warlock records both imported picks',
          /eyes-of-the-deep/i.test(warlock) && /tideborn/i.test(warlock), warlock.slice(0, 260));

    // the language: a background's language choice is where a homebrew language surfaces
    check('on the Background tab', await clickTab(page, 'Background'));
    await page.waitForTimeout(1500);
    // A language choice only exists once a background granting one is chosen. The SRD list the
    // builder offers here is short — Acolyte, a demo background, and Custom — and Acolyte grants
    // two languages.
    check('picked a background that grants languages', await clickText(page, /^Acolyte$/));
    await page.waitForTimeout(2000);
    const langOffered = await page.evaluate(() => {
      for (const sel of document.querySelectorAll('select')) {
        if ([...sel.options].some(o => /dockside cant/i.test(o.textContent))) return true;
      }
      return /dockside cant/i.test(document.querySelector('#app').innerText);
    });
    // Languages carry :tags #{:profs :language-profs}, so a background's language choice renders on
    // the PROFICIENCIES tab — the third selection in this tour to live somewhere other than where
    // the thing granting it was chosen. Worth remembering: a tag decides the tab, not the grantor.
    check('on the Proficiencies tab', await clickTab(page, 'Proficiencies'));
    await page.waitForTimeout(1800);
    const langOnProfs = await page.evaluate(() => /dockside cant/i.test(document.querySelector('#app').innerText));
    check('the imported language is reachable on a character', langOffered || langOnProfs);
    await shot(page, '07-language.jpg');

    check('no uncaught JS errors during the whole tour', errors.length === 0, errors.slice(0, 3).join(' | '));
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  console.log(`\nauthored: ${authored.join(', ')}`);
  console.log(`screenshots: ${path.join(SHOTS, 'grand-tour')}`);
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
