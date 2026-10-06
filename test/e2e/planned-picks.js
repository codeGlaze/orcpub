// The planned hold through the real builder: a pick that stops applying leaves the character and
// comes back when it applies again; a reload forgets it. decision-gate-hidden-picks.md,
// "Current decision".
//
// 1 ranger Hunter 7 with Steel Will, 7 -> 6 -> 7; 2 the same, 7 -> 6, reload, -> 7;
// 3 fighter Champion 10 with Defense + Archery, 10 -> 9 -> 10.
//
// Runs against the seeded server (see hidden-pick-flows.js for the command). Prereqs: lein fig:build.
// Run: NODE_PATH=<dir with playwright> node test/e2e/planned-picks.js     Exit 0 = pass.
//
// State is set only by clicking; app-db and the browser's saved draft are READ to assert.
const { chromium } = require('playwright');
const { findChrome, checker, dbAt, readyPage, pick, setClass, setLevel, login } = require('./lib');

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
