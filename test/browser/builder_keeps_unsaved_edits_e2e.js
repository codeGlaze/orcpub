// Needs: dev bundle (reads the app's internals, which a production bundle compiles away).
// Needs: fresh server (levels up and edits the seeded character another suite opens).
// Edits survive saving: unsaved builder edits, sheet edits and edits made while a save is in flight.
//
// The builder and the character page ("the sheet": hit points, slots, notes, gear) hold separate
// copies of one character, and the sheet autosaves its copy. What comes back must not replace the
// builder's unsaved edits, nor an edit made while the save was in flight; neither may Level Up or
// a builder Save. An edit that is undone sends nothing. An autosave refused because the character
// was saved from another copy (409) re-applies its own change and saves again, once. While a save
// has not succeeded, closing the tab must still warn. Real server, real saves.
//
// Needs:     the real app at :8890 (lein fig:build, then lein e2e-server); E2E_CHARACTER_ID, set by run.sh.
// Runs in:   ~90s, most of it the 7.5s autosave wait.
// Run:       node test/browser/builder_keeps_unsaved_edits_e2e.js      exit 0 = all checks passed
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const { newContext, login } = require('../../scripts/e2e/lib.js');

const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const ID = Number(process.env.E2E_CHARACTER_ID);
const NAME = 'Bree the Unsaved';

let failures = 0;
function check(label, ok, detail) {
  if (!ok) failures++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${detail && !ok ? '  -- ' + detail : ''}`);
}

// The next character save's response, set up before the action that causes it.
const nextSave = page => page.waitForResponse(
  r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/dnd/5e/characters',
  { timeout: 30000 });

(async () => {
  if (!ID) { console.log('FAIL  E2E_CHARACTER_ID is not set (run through scripts/e2e/run.sh)'); process.exit(1); }
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await newContext(browser);
  const page = await ctx.newPage();
  const jsErrors = [];
  page.on('pageerror', e => jsErrors.push(e.message));
  await login(page, 'kaylee', 'serenity99');

  await page.goto(`${BASE}/pages/dnd/5e/characters/${ID}`, { waitUntil: 'networkidle' });
  await page.waitForFunction(() => window.re_frame && window.cljs && window.cljs.reader, null, { timeout: 30000 });
  await page.addScriptTag({ content: `
    window.__c = window.cljs.core;
    window.__kw = (s) => window.__c.keyword.apply(null, s.split('/'));
    window.__db = () => window.__c.deref(window.re_frame.db.app_db);
    window.__d = (edn) => window.re_frame.core.dispatch_sync(window.cljs.reader.read_string(edn));
    window.__get = (edn) => window.__c.get_in(window.__db(), window.cljs.reader.read_string(edn));
    window.__str = (edn) => { const v = window.__get(edn); return v == null ? null : String(v); };
    // The class's level count in the builder's copy.
    window.__levels = () => window.__c.count(window.__get(
      '[:character :orcpub.entity/options :class 0 :orcpub.entity/options :levels]'));
    // Open the stored copy in the builder, as the sheet's Edit button does.
    window.__edit = (id) => window.re_frame.core.dispatch_sync(window.__c.vector(
      window.__kw('edit-character'),
      window.__get('[:orcpub.dnd.e5.character/character-map ' + id + ']')));` });
  await page.waitForFunction(id => window.__get(`[:orcpub.dnd.e5.character/character-map ${id}]`) != null,
                             ID, { timeout: 30000 });

  // 1. An unsaved builder edit, then a sheet edit to the same character, then its autosave.
  await page.evaluate(id => window.__edit(id), ID);
  await page.waitForTimeout(1000);
  await page.evaluate(n => window.__d(`[:update-value-field :orcpub.dnd.e5.character/character-name "${n}"]`), NAME);
  let saved = nextSave(page);
  await page.evaluate(id => window.__d(`[:orcpub.dnd.e5.character/set-current-hit-points ${id} 7]`), ID);
  check('the sheet edit is saved', (await saved).status() === 200);
  await page.waitForTimeout(800);
  const after = await page.evaluate(() => ({
    name: window.__str('[:character :orcpub.entity/values :orcpub.dnd.e5.character/character-name]'),
    changed: window.__get('[:character :changed]'),
    hp: window.__str('[:character :orcpub.entity/values :orcpub.dnd.e5.character/current-hit-points]'),
    draft: localStorage.getItem('character-draft/' + sessionStorage.getItem('builder-draft-key')) || '',
  }));
  check('the builder keeps its unsaved name after the sheet\'s save comes back', after.name === NAME, after.name);
  check('and still counts it as unsaved', after.changed === true, String(after.changed));
  check('and shows the sheet\'s hit points too', after.hp === '7', after.hp);
  check('and its draft survives a reload', after.draft.includes(NAME), 'draft lacks the name');

  // 2. Level Up from the sheet while the builder has unsaved edits.
  const levelsBefore = await page.evaluate(() => window.__levels());
  saved = nextSave(page);
  await page.evaluate(id => window.__d(`[:orcpub.dnd.e5.character/level-up "${id}"]`), ID);
  await page.waitForTimeout(800);
  const leveled = await page.evaluate(() => ({
    name: window.__str('[:character :orcpub.entity/values :orcpub.dnd.e5.character/character-name]'),
    levels: window.__levels(),
  }));
  check('Level Up keeps the builder\'s unsaved name', leveled.name === NAME, leveled.name);
  check('and the builder shows the new level', leveled.levels === levelsBefore + 1,
        `${levelsBefore} -> ${leveled.levels}`);
  await saved;
  await page.waitForTimeout(800);
  const afterLevelSave = await page.evaluate(() => ({
    name: window.__str('[:character :orcpub.entity/values :orcpub.dnd.e5.character/character-name]'),
    levels: window.__levels(),
  }));
  check('and both survive the level\'s autosave coming back',
        afterLevelSave.name === NAME && afterLevelSave.levels === levelsBefore + 1,
        `${afterLevelSave.name}, ${afterLevelSave.levels} levels`);

  // Every character save's response body from here on, in order.
  const bodies = [], statuses = [];
  page.on('response', async r => {
    if (r.request().method() === 'POST' && new URL(r.url()).pathname === '/dnd/5e/characters') {
      statuses.push(r.status());
      bodies.push(await r.text().catch(() => ''));
    }
  });
  // Waits until `n` more save replies than `from` have arrived, or 20 s.
  const savesAfter = async (from, n) => {
    for (let t = 0; t < 40 && statuses.length < from + n; t++) await page.waitForTimeout(500);
    return statuses.slice(from);
  };
  const storedHas = (text) => bodies.length > 0 && bodies[bodies.length - 1].includes(text);
  const HP_KEY = ':orcpub.dnd.e5.character/current-hit-points';
  const mapHp = () => page.evaluate(id => window.__str(
    `[:orcpub.dnd.e5.character/character-map ${id} :orcpub.entity/values :orcpub.dnd.e5.character/current-hit-points]`), ID);

  // 3. A builder Save made before the sheet's autosave has gone out: neither save may undo the other.
  const NAME2 = 'Bree Saved Twice';
  await page.evaluate(n => window.__d(`[:update-value-field :orcpub.dnd.e5.character/character-name "${n}"]`), NAME2);
  await page.evaluate(id => window.__d(`[:orcpub.dnd.e5.character/set-current-hit-points ${id} 9]`), ID);
  saved = nextSave(page);
  await page.evaluate(() => window.re_frame.core.dispatch(window.__c.vector(
    window.__kw('save-character'),
    window.__c.deref(window.re_frame.core.subscribe(window.__c.vector(window.__kw('built-character')))))));
  await saved;
  await page.waitForTimeout(10000);  // past the autosave wait, whether or not it sends
  check('a builder Save soon after a sheet edit stores both, and the autosave does not undo it',
        storedHas(NAME2) && storedHas(`${HP_KEY} 9`), bodies.length ? bodies[bodies.length - 1].slice(0, 160) : 'no save');

  // 4. An edit that is undone before the autosave goes out sends nothing.
  const before = bodies.length;
  await page.evaluate(id => window.__d(`[:orcpub.dnd.e5.character/set-current-hit-points ${id} 12]`), ID);
  await page.evaluate(id => window.__d(`[:orcpub.dnd.e5.character/set-current-hit-points ${id} 9]`), ID);
  await page.waitForTimeout(10000);
  check('an undone edit sends no save', bodies.length === before, `${bodies.length - before} sent`);

  // 5. An edit made while the save is in flight survives the reply and is saved next.
  let release;
  const held = new Promise(r => { release = r; });
  let holdOne = true;
  await page.route('**/dnd/5e/characters', async r => {
    if (r.request().method() === 'POST' && holdOne) { holdOne = false; await held; }
    await r.continue();
  });
  const sent = page.waitForRequest(r => r.method() === 'POST' && new URL(r.url()).pathname === '/dnd/5e/characters',
                                    { timeout: 30000 });
  await page.evaluate(id => window.__d(`[:orcpub.dnd.e5.character/set-current-hit-points ${id} 3]`), ID);
  await sent;
  await page.evaluate(id => window.__d(`[:orcpub.dnd.e5.character/set-current-hit-points ${id} 4]`), ID);
  saved = nextSave(page);
  release();
  await saved;
  await page.waitForTimeout(800);
  const hpAfterReply = await mapHp();
  check('an edit made while a save is in flight survives its reply', hpAfterReply === '4', hpAfterReply);
  await page.waitForTimeout(10000);
  check('and is saved next', storedHas(`${HP_KEY} 4`),
        bodies.length ? bodies[bodies.length - 1].match(/current-hit-points \d+/)?.[0] : 'no save');
  await page.unroute('**/dnd/5e/characters');

  // 6. The server refuses an autosave because the character was saved from another copy (409):
  // the sheet's own change is put onto the stored character and saved again, once. The 409 is
  // made here from the server's real reply, with the name changed as if another tab had saved it.
  const refuse = async (times) => {
    let left = times;
    await page.route('**/dnd/5e/characters', async r => {
      if (r.request().method() !== 'POST' || left <= 0) return r.continue();
      left--;
      const real = await r.fetch();
      // As another tab's save would leave it: renamed, and without this tab's hit points.
      const stored = (await real.text())
        .replace(/(:orcpub\.dnd\.e5\.character\/character-name )"[^"]*"/, '$1"Renamed elsewhere"')
        .replace(/(:orcpub\.dnd\.e5\.character\/current-hit-points )\d+/, '$12');
      await r.fulfill({ status: 409, contentType: 'application/edn',
                        body: `{:error :stale-character :current ${stored}}` });
    });
  };
  await refuse(1);
  let from = statuses.length;
  await page.evaluate(id => window.__d(`[:orcpub.dnd.e5.character/set-current-hit-points ${id} 13]`), ID);
  const once = await savesAfter(from, 2);
  await page.waitForTimeout(800);
  check('a refused autosave is saved again', once.join(' ') === '409 200', once.join(' ') || 'no save');
  check('onto the character as stored, with only its own change put back',
        storedHas('"Renamed elsewhere"') && storedHas(`${HP_KEY} 13`),
        bodies.length ? bodies[bodies.length - 1].slice(0, 160) : 'no save');
  await page.unroute('**/dnd/5e/characters');

  await refuse(2);
  from = statuses.length;
  await page.evaluate(id => window.__d(`[:orcpub.dnd.e5.character/set-current-hit-points ${id} 14]`), ID);
  const twice = await savesAfter(from, 2);
  await page.waitForTimeout(3000);
  check('a refused retry is not retried again', statuses.length - from === 2, statuses.slice(from).join(' '));
  const shown = await page.evaluate(() => ({ type: window.__str('[:message-type]'), msg: window.__str('[:message]') || '' }));
  check('a second refusal in a row is shown, not retried again', shown.type === ':error' && shown.msg.includes('changed somewhere else'),
        `${shown.type} ${shown.msg.slice(0, 80)}`);
  await page.unroute('**/dnd/5e/characters');

  // 7. A sheet edit whose save fails: closing the tab must still warn.
  await page.route('**/dnd/5e/characters', r =>
    r.request().method() === 'POST' ? r.fulfill({ status: 500, body: '' }) : r.continue());
  await page.mouse.click(5, 5);  // a user gesture, without which Chromium shows no leave-page prompt
  saved = nextSave(page);
  await page.evaluate(id => window.__d(`[:orcpub.dnd.e5.character/set-current-hit-points ${id} 5]`), ID);
  check('the failing save was sent', (await saved).status() === 500);
  await page.waitForTimeout(500);
  let warned = false;
  page.on('dialog', d => { if (d.type() === 'beforeunload') warned = true; d.dismiss(); });
  await page.close({ runBeforeUnload: true });
  await new Promise(r => setTimeout(r, 1500));
  check('closing the tab after a failed save warns that changes are unsaved', warned);

  check('no page errors', jsErrors.length === 0, jsErrors.slice(0, 3).join(' | '));
  await browser.close();
  console.log(failures ? `\n${failures} check(s) failed` : '\nall checks passed');
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
