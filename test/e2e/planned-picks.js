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
// 9 a rogue's skills through rogue -> wizard -> rogue.
//
// Runs against the seeded server (see hidden-pick-flows.js for the command). Prereqs: lein fig:build.
// Run: NODE_PATH=<dir with playwright> node test/e2e/planned-picks.js     Exit 0 = pass.
//
// State is set only by clicking, except flow 4, which writes the old-style draft it opens.
// app-db and the browser's saved draft are READ to assert.
const { chromium } = require('playwright');
const { findChrome, checker, dbAt, readyPage, pick, setClass, setLevel, login, addClass, deleteClass } = require('./lib');

const { check, report } = checker();
const CLASS0 = '[:character :orcpub.entity/options :class 0 :orcpub.entity/options';
const TACTIC = `${CLASS0} :levels 2 :orcpub.entity/options :ranger-archetype :orcpub.entity/options :defensive-tactics :orcpub.entity/key]`;
const STYLES = `${CLASS0} :fighting-style]`;
const HELD = '[:orcpub.dnd.e5/planned-picks]';
const heldCount = async page => (await dbAt(page, HELD)).match(/:address/g)?.length || 0;
const styles = async page => [...(await dbAt(page, STYLES)).matchAll(/:orcpub\.entity\/key :([\w-]+)/g)].map(m => m[1]);
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
};

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  for (const n of Object.keys(flows)) {
    console.log(`\n== flow ${n}`);
    const page = await login(browser);
    try { await flows[n](page); } catch (e) { check(`flow ${n} could be driven`, false, e.message.split('\n')[0]); }
    check(`flow ${n}: no page errors`, page.errors.length === 0, page.errors.slice(0, 3).join(' | '));
    await page.context().close();
  }
  await browser.close();
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
