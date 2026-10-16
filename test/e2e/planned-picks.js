// The planned hold through the real builder: a pick that stops applying leaves the character and
// comes back when it applies again; a reload forgets it. decision-gate-hidden-picks.md,
// "Current decision".
//
// 1 ranger Hunter 7 with Steel Will, 7 -> 6 -> 7; 2 the same, 7 -> 6, reload, -> 7;
// 3 fighter Champion 10 with Defense + Archery, 10 -> 9 -> 10; 4 a draft saved at 6 that still
// stores Steel Will (the state before this fix) is settled on opening, before any edit; 5 fighter
// first, rogue 3 second with its multiclass skill: deleting the fighter keeps the rogue's levels
// and holds that skill; 6 a held pick survives the character's first save; picks an edit removes
// with their entry are held: 7 ranger 7 -> 2 -> 7, 8 a Dwarf's subrace through Dwarf -> Elf -> Dwarf,
// 9 a rogue's skills through rogue -> wizard -> rogue; 10 the Planned marker on Champion 9's Fighting Style,
// what tapping it says, and Clear; 11 the line under the class row for picks whose section is closed.
// 12 the restore notice for picks that come back out of view, with Undo, Redo and its close mark.
// 13 the "1st" badge: a rogue's starting-class skills follow the starting class, rogue -> fighter -> rogue.
//
// Runs against the seeded server (see hidden-pick-flows.js for the command). Prereqs: lein fig:build.
// Run: NODE_PATH=<dir with playwright> node test/e2e/planned-picks.js     Exit 0 = pass.
// FLOWS=13 (or 4,13) runs only those flows.
//
// State is set only by clicking, except flow 4, which writes the old-style draft it opens.
// app-db and the browser's saved draft are READ to assert.
const { chromium } = require('playwright');
const { findChrome, checker, dbAt, readyPage, pick, setClass, setLevel, login, addClass, deleteClass, clickTab } = require('./lib');

const { check, report } = checker();
const CLASS0 = '[:character :orcpub.entity/options :class 0 :orcpub.entity/options';
const TACTIC = `${CLASS0} :levels 2 :orcpub.entity/options :ranger-archetype :orcpub.entity/options :defensive-tactics :orcpub.entity/key]`;
const STYLES = `${CLASS0} :fighting-style]`;
const HELD = '[:orcpub.dnd.e5/planned-picks]';
const heldCount = async page => (await dbAt(page, HELD)).match(/:address/g)?.length || 0;
const styles = async page => [...(await dbAt(page, STYLES)).matchAll(/:orcpub\.entity\/key :([\w-]+)/g)].map(m => m[1]);
// The open Planned panel's rows, each as "name|when|Clear" ("" for a cell the line already says).
const plannedRows = page => page.evaluate(() => [...document.querySelectorAll('.planned-row')]
  .map(r => { const c = [...r.children].map(e => e.textContent.trim());
              return (c.length === 3 ? c : r.querySelector('span.planned-aside') ? ['', ...c] : [c[0], '', c[1]]).join('|'); }));
const draft = page => page.evaluate(() => localStorage.getItem('character') || '');

async function hunter7(page) {
  await setClass(page, 0, 'ranger');
  await setLevel(page, 0, 3);
  await pick(page, 'Class / Level', 'Ranger Archetype', 'Hunter');
  await pick(page, 'Class / Level', "Hunter's Prey", 'Colossus Slayer');
  await setLevel(page, 0, 7);
  await pick(page, 'Class / Level', 'Defensive Tactics', 'Steel Will');
  return check('ranger 7 has Steel Will', (await dbAt(page, TACTIC)) === ':steel-will');
}

// The saved draft with its first class one level lower and every pick kept: what a level drop
// left before the hold existed.
const dropLevelInDraft = page => page.evaluate(() => {
  const c = window.cljs.core, read = window.cljs.reader.read_string, e5 = window.orcpub.dnd.e5.character;
  const ch = e5.from_strict(read(localStorage.getItem('character')));
  const at = read('[:orcpub.entity/options :class 0 :orcpub.entity/options :levels]');
  localStorage.setItem('character', c.pr_str(e5.to_strict(c.update_in(ch, at, c.pop))));
});

const flows = {
  async 1(page) {
    if (!(await hunter7(page))) return;
    await setLevel(page, 0, 6);
    check('at 6 Steel Will is out of the character', (await dbAt(page, TACTIC)) === 'nil');
    check('at 6 it is held', (await heldCount(page)) === 1);
    check('at 6 the saved draft lacks it', !/steel-will/.test(await draft(page)));
    await setLevel(page, 0, 7);
    check('back at 7 Steel Will returns', (await dbAt(page, TACTIC)) === ':steel-will');
    check('in view, so no notice', !(await visibleText(page, 'Steel Will restored')));
    check('back at 7 nothing is held', (await heldCount(page)) === 0);
    check('back at 7 the saved draft has it', /steel-will/.test(await draft(page)));
  },

  async 2(page) {
    if (!(await hunter7(page))) return;
    await setLevel(page, 0, 6);
    await page.reload({ waitUntil: 'load' });
    await page.waitForTimeout(3000);
    await readyPage(page);
    check('after a reload nothing is held', (await heldCount(page)) === 0);
    await setLevel(page, 0, 7);
    check('raised to 7 after a reload, Steel Will stays gone', (await dbAt(page, TACTIC)) === 'nil');
  },

  async 3(page) {
    await setClass(page, 0, 'fighter');
    await pick(page, 'Class / Level', 'Fighting Style', 'Defense');
    await setLevel(page, 0, 3);
    await pick(page, 'Class / Level', 'Martial Archetype', 'Champion');
    await setLevel(page, 0, 10);
    await pick(page, 'Class / Level', 'Fighting Style', 'Archery');
    if (!check('Champion 10 has Defense and Archery', `${await styles(page)}` === 'defense,archery')) return;
    await setLevel(page, 0, 9);
    check('at 9 the newest style, Archery, is held', `${await styles(page)}` === 'defense' && (await heldCount(page)) === 1);
    await setLevel(page, 0, 10);
    check('back at 10 both styles, in order', `${await styles(page)}` === 'defense,archery' && (await heldCount(page)) === 0);
  },

  async 4(page) {
    if (!(await hunter7(page))) return;
    await dropLevelInDraft(page);
    check('the draft is at 6 and still stores Steel Will', /steel-will/.test(await draft(page)));
    await page.reload({ waitUntil: 'load' });
    await page.waitForTimeout(3000);
    await readyPage(page);
    check('opened at 6, Steel Will is out of the character', (await dbAt(page, TACTIC)) === 'nil');
    check('and held', (await heldCount(page)) === 1);
    check('and the saved draft lacks it', !/steel-will/.test(await draft(page)));
    check('a notice says one choice does not apply', await visibleText(page, "1 choice doesn't apply right now"));
    const toggle = page.locator('button[aria-expanded]', { hasText: 'Details' });
    check('its details toggle starts closed', (await toggle.getAttribute('aria-expanded')) === 'false');
    await toggle.click();
    await page.waitForTimeout(400);
    check('and opens on a tap', (await toggle.getAttribute('aria-expanded')) === 'true');
    check('its details say when it returns', await page.evaluate(() => [...document.querySelectorAll('.callout-detail')]
      .some(r => r.children[0].textContent === 'Steel Will' && r.children[1].textContent === 'auto-fills at level 7')));
    check('and its arrow points up', await toggle.locator('i.fa-angle-up').count() === 1);
    await page.locator('[aria-label="Dismiss"]').first().click();
    await page.waitForTimeout(400);
    check('its close mark dismisses it', !(await visibleText(page, "1 choice doesn't apply right now")));
    await setLevel(page, 0, 7);
    check('raised to 7, Steel Will returns', (await dbAt(page, TACTIC)) === ':steel-will');
  },

  async 5(page) {
    await setClass(page, 0, 'fighter');
    await addClass(page, 'rogue');
    await setLevel(page, 1, 3);
    await pick(page, 'Proficiencies', 'Skill Proficiency', 'Stealth', { parent: 'Rogue' });
    const multi = i => dbAt(page, `[:character :orcpub.entity/options :class ${i} :orcpub.entity/options :multiclass-skill-proficiency]`);
    const levels = () => dbAt(page, `${CLASS0} :levels]`);
    if (!check('rogue second, with Stealth as its multiclass skill', /:stealth/.test(await multi(1)))) return;
    await deleteClass(page, 0);
    check('the fighter is deleted; the rogue is first', (await dbAt(page, '[:character :orcpub.entity/options :class 0 :orcpub.entity/key]')) === ':rogue');
    check('the rogue keeps its 3 levels', ((await levels()).match(/:level-\d/g) || []).length === 3);
    check('its multiclass skill is out of the character', !/:stealth/.test(await multi(0)));
    check('and held', /:multiclass-skill-proficiency :stealth/.test(await dbAt(page, HELD)));
  },

  async 6(page) {
    if (!(await hunter7(page))) return;
    await setLevel(page, 0, 6);
    await page.getByText('Save New Character', { exact: true }).first().click();
    await page.waitForTimeout(4000);
    check('saved: the character has an id', /^\d+$/.test((await dbAt(page, '[:character :db/id]')).trim()));
    check('Steel Will is still held', (await heldCount(page)) === 1);
    await setLevel(page, 0, 7);
    check('raised to 7 after the save, Steel Will returns', (await dbAt(page, TACTIC)) === ':steel-will');
  },

  async 7(page) {
    if (!(await hunter7(page))) return;
    await setLevel(page, 0, 2);
    check('at 2, no subclass', (await dbAt(page, `${CLASS0} :levels 2]`)) === 'nil');
    await setLevel(page, 0, 7);
    check('raised to 7, Hunter and Steel Will return', (await dbAt(page, TACTIC)) === ':steel-will');
  },

  // The SRD ranger has one subclass, so a replaced choice is driven with a race: its subrace is
  // stored inside it, as a subclass's picks are.
  async 8(page) {
    const SUBRACE = '[:character :orcpub.entity/options :race :orcpub.entity/options :subrace :orcpub.entity/key]';
    await pick(page, 'Race', 'Race', 'Dwarf');
    const first = await page.evaluate(() => {
      const s = [...document.querySelectorAll('#app div.p-5.m-b-20')].find(x =>
        [...x.querySelectorAll('span.m-l-5.f-s-18.f-w-b')].some(t => t.textContent.trim() === 'Subrace'));
      const n = s && s.querySelector('div.p-10.b-1.b-rad-5.m-5.b-orange span.f-w-b.f-s-1');
      return n ? n.textContent.trim() : null;
    });
    if (!check('a Dwarf subrace is offered', first, first || 'none')) return;
    await pick(page, 'Race', 'Subrace', first);
    const sub = await dbAt(page, SUBRACE);
    if (!check(`Dwarf with ${first}`, /^:/.test(sub), sub)) return;
    await pick(page, 'Race', 'Race', 'Elf');
    check('changed to Elf', /:elf/.test(await dbAt(page, '[:character :orcpub.entity/options :race :orcpub.entity/key]')));
    await pick(page, 'Race', 'Race', 'Dwarf');
    check(`back to Dwarf, ${first} returns`, (await dbAt(page, SUBRACE)) === sub);
  },

  async 9(page) {
    await setClass(page, 0, 'rogue');
    for (const sk of ['Acrobatics', 'Deception', 'Perception', 'Stealth']) await pick(page, 'Proficiencies', 'Skill Proficiency', sk, { parent: 'Rogue' });
    const skills = async () => [...(await dbAt(page, `${CLASS0} :skill-proficiency]`)).matchAll(/:orcpub\.entity\/key :([\w-]+)/g)].map(m => m[1]).sort().join(',');
    if (!check('rogue with four skills', (await skills()) === 'acrobatics,deception,perception,stealth')) return;
    await setClass(page, 0, 'wizard');
    check('changed to wizard', (await dbAt(page, '[:character :orcpub.entity/options :class 0 :orcpub.entity/key]')) === ':wizard');
    await setClass(page, 0, 'rogue');
    check('rogue again, its four skills return', (await skills()) === 'acrobatics,deception,perception,stealth');
  },

  async 10(page) {
    await setClass(page, 0, 'fighter');
    await pick(page, 'Class / Level', 'Fighting Style', 'Defense');
    await setLevel(page, 0, 3);
    await pick(page, 'Class / Level', 'Martial Archetype', 'Champion');
    await setLevel(page, 0, 10);
    await pick(page, 'Class / Level', 'Fighting Style', 'Archery');
    await setLevel(page, 0, 9);
    check('at 9 the Fighting Style row shows the marker', await visibleText(page, 'Planned: Archery (L10)'));
    await clickTab(page, 'Class / Level');
    check('once: not again under the class row', (await countVisible(page, 'Planned: Archery (L10)')) === 1);
    await clickVisible(page, 'Planned: Archery (L10)');
    await page.waitForTimeout(400);
    check('tapping it says when Archery returns, without its name again', `${await plannedRows(page)}` === '|auto-fills at level 10|Clear');
    check('and the line says it is open', (await page.locator('button.planned-toggle[aria-expanded="true"]').count()) === 1);
    await clickVisible(page, 'Clear');
    await page.waitForTimeout(600);
    check('Clear removes the marker', !(await visibleText(page, 'Planned: Archery (L10)')));
    check('and the held pick', (await heldCount(page)) === 0);
    await setLevel(page, 0, 10);
    check('back at 10 Archery does not return', `${await styles(page)}` === 'defense');
  },

  async 13(page) {
    await setClass(page, 0, 'rogue');
    for (const sk of ['Acrobatics', 'Deception', 'Perception', 'Stealth']) await pick(page, 'Proficiencies', 'Skill Proficiency', sk, { parent: 'Rogue' });
    await addClass(page, 'fighter');
    await clickTab(page, 'Class / Level');
    const classAt = i => dbAt(page, `[:character :orcpub.entity/options :class ${i} :orcpub.entity/key]`);
    const rogueSkills = async () => {
      const i = (await classAt(0)) === ':rogue' ? 0 : 1;
      return [...(await dbAt(page, `[:character :orcpub.entity/options :class ${i} :orcpub.entity/options :skill-proficiency]`))
        .matchAll(/:orcpub\.entity\/key :([\w-]+)/g)].map(m => m[1]).sort().join(',');
    };
    check('rogue first with four skills, a fighter second, each row marked "1st" or not',
      (await rogueSkills()) === 'acrobatics,deception,perception,stealth' &&
      (await page.locator('span.class-first-badge.on').count()) === 1 &&
      (await page.locator('button.class-first-badge').count()) === 1);
    await page.locator('button.class-first-badge').first().click();
    await page.waitForTimeout(1500);
    check('tapping the fighter\'s "1st" makes it the starting class', (await classAt(0)) === ':fighter');
    check('the rogue\'s starting-class skills leave it', (await rogueSkills()) === '');
    check('and its line says they wait on it', await visibleText(page, '4 planned if Rogue is your starting class'));
    await page.locator('button.class-first-badge').first().click();
    await page.waitForTimeout(1500);
    check('tapping the rogue\'s "1st" puts it back first', (await classAt(0)) === ':rogue');
    check('and its skills return', (await rogueSkills()) === 'acrobatics,deception,perception,stealth');
  },

  async 12(page) {
    await setClass(page, 0, 'rogue');
    for (const sk of ['Acrobatics', 'Deception', 'Perception', 'Stealth']) await pick(page, 'Proficiencies', 'Skill Proficiency', sk, { parent: 'Rogue' });
    const skills = async () => [...(await dbAt(page, `${CLASS0} :skill-proficiency]`)).matchAll(/:orcpub\.entity\/key :([\w-]+)/g)].map(m => m[1]).sort().join(',');
    await setClass(page, 0, 'wizard');
    await setClass(page, 0, 'rogue');
    check('the skills come back on another tab, so a notice says so', await visibleText(page, 'Rogue picks restored (4)'));
    await clickVisible(page, 'Undo');
    await page.waitForTimeout(800);
    check('Undo takes them out', (await skills()) === '');
    check('and says so, with Redo', await visibleText(page, 'Undone') && await visibleText(page, 'Redo'));
    await clickVisible(page, 'Redo');
    await page.waitForTimeout(800);
    check('Redo puts them back', (await skills()) === 'acrobatics,deception,perception,stealth');
    await page.locator('[aria-label="Dismiss"]').first().click();
    await page.waitForTimeout(500);
    check('the close mark dismisses it', !(await visibleText(page, 'Rogue picks restored (4)')));
  },

  async 11(page) {
    if (!(await hunter7(page))) return;
    await setLevel(page, 0, 6);
    check('at 6 the class row says what is planned', await visibleText(page, 'Planned: Steel Will (L7)'));
    await clickVisible(page, 'Planned: Steel Will (L7)');
    await page.waitForTimeout(400);
    check('tapping it says when Steel Will returns', `${await plannedRows(page)}` === '|auto-fills at level 7|Clear');
    await setLevel(page, 0, 2);
    check('at 2 the line counts both', await visibleText(page, '2 planned at higher levels'));
    const rows = await plannedRows(page);
    check('Hunter returns at 3', rows.includes('Hunter|level 3|Clear'), rows);
    check('Steel Will still at 7, not 3', rows.includes('Steel Will|level 7|Clear'), rows);
    await setLevel(page, 0, 7);
    check('at 7 the line is gone and Steel Will is back',
      !(await visibleText(page, '2 planned at higher levels')) && (await dbAt(page, TACTIC)) === ':steel-will');
  },
};

// The text of the visible element exactly matching `text`, or null; and a click on it.
const visibleText = (page, text) => page.evaluate(t => {
  const el = [...document.querySelectorAll('#app span, #app button, #app div')]
    .find(e => e.textContent.trim() === t && e.getBoundingClientRect().width > 0);
  return el ? el.textContent.trim() : null;
}, text);
const countVisible = (page, text) => page.evaluate(t => [...document.querySelectorAll('#app span, #app button')]
  .filter(e => e.textContent.trim() === t && e.getBoundingClientRect().width > 0).length, text);
const clickVisible = (page, text) => page.evaluate(t => {
  const el = [...document.querySelectorAll('#app span, #app button')]
    .find(e => e.textContent.trim() === t && e.getBoundingClientRect().width > 0);
  if (el) el.click(); return !!el;
}, text);

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  // FLOWS=13,4 runs only those flows.
  const only = process.env.FLOWS && process.env.FLOWS.split(',');
  for (const n of Object.keys(flows).filter(k => !only || only.includes(k))) {
    console.log(`\n== flow ${n}`);
    const page = await login(browser);
    try { await flows[n](page); } catch (e) { check(`flow ${n} could be driven`, false, e.message.split('\n')[0]); }
    check(`flow ${n}: no page errors`, page.errors.length === 0, page.errors.slice(0, 3).join(' | '));
    await page.context().close();
  }
  await browser.close();
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
