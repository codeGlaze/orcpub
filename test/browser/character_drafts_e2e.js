// Needs: dev bundle (reads the app's internals, which a production bundle compiles away).
// Needs: fresh server (saves a clone of the seeded character).
// One builder draft per character: tabs on different characters keep their own unsaved work.
//
// Checks the rules in plan-character-drafts.md that the storage itself guarantees:
//   R0  two tabs on two characters each get their own character and edits back after a reload
//   R1  a reload restores that tab's own draft
//   R7  a new tab does not take a draft that another open tab is editing
//   first save moves a new character's draft from its browser key to its id
//   R9  the old single "character" slot is moved to its own key and removed, losing nothing
//
// Needs:     the real app at :8890 (lein fig:build, then lein e2e-server); E2E_CHARACTER_ID and
//            E2E_HOMEBREW_CHARACTER_ID, set by run.sh.
// Runs in:   ~60s.
// Run:       node test/browser/character_drafts_e2e.js      exit 0 = all checks passed
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const { newContext, login } = require('../../scripts/e2e/lib.js');

const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const A = Number(process.env.E2E_CHARACTER_ID);
const B = Number(process.env.E2E_HOMEBREW_CHARACTER_ID);

let failures = 0;
function check(label, ok, detail) {
  if (!ok) failures++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${detail && !ok ? '  -- ' + detail : ''}`);
}

const HELPERS = `
  window.__c = window.cljs.core;
  window.__kw = (s) => window.__c.keyword.apply(null, s.split('/'));
  window.__db = () => window.__c.deref(window.re_frame.db.app_db);
  window.__d = (edn) => window.re_frame.core.dispatch_sync(window.cljs.reader.read_string(edn));
  window.__get = (edn) => window.__c.get_in(window.__db(), window.cljs.reader.read_string(edn));
  window.__str = (edn) => { const v = window.__get(edn); return v == null ? null : String(v); };
  window.__edit = (id) => window.re_frame.core.dispatch_sync(window.__c.vector(
    window.__kw('edit-character'), window.__get('[:orcpub.dnd.e5.character/character-map ' + id + ']')));
  window.__name = () => window.__str('[:character :orcpub.entity/values :orcpub.dnd.e5.character/character-name]');
  window.__id = () => window.__str('[:character :db/id]');
  window.__drafts = () => Object.keys(localStorage).filter(k => k.startsWith('character-draft/')).sort();`;

// The app's internals, ready for the helpers above.
async function ready(page) {
  await page.waitForFunction(() => window.re_frame && window.cljs && window.cljs.reader, null, { timeout: 30000 });
  await page.addScriptTag({ content: HELPERS });
}

// Opens character `id` in the builder of `page`, as the Edit button does, and renames it `name` unsaved.
async function openAndRename(page, id, name) {
  await page.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'networkidle' });
  await ready(page);
  await page.waitForFunction(i => window.__get(`[:orcpub.dnd.e5.character/character-map ${i}]`) != null, id,
                             { timeout: 30000 });
  await page.evaluate(i => window.__edit(i), id);
  await page.waitForTimeout(800);
  await page.evaluate(n => window.__d(`[:update-value-field :orcpub.dnd.e5.character/character-name "${n}"]`), name);
  await page.waitForTimeout(300);
}

async function reloadBuilder(page) {
  await page.reload({ waitUntil: 'networkidle' });
  await ready(page);
  await page.waitForTimeout(800);
  return page.evaluate(() => ({ id: window.__id(), name: window.__name() }));
}

(async () => {
  if (!A || !B) { console.log('FAIL  E2E_CHARACTER_ID / E2E_HOMEBREW_CHARACTER_ID not set (run through scripts/e2e/run.sh)'); process.exit(1); }
  const browser = await chromium.launch({ executablePath: findChrome() });
  const jsErrors = [];
  const ctx = await newContext(browser);
  const t1 = await ctx.newPage();
  t1.on('pageerror', e => jsErrors.push(e.message));
  await login(t1, 'kaylee', 'serenity99');

  // R0, R1: two tabs, two characters, unsaved edits in both; B written last.
  await openAndRename(t1, A, 'Alpha unsaved');
  const t2 = await ctx.newPage();
  t2.on('pageerror', e => jsErrors.push(e.message));
  await openAndRename(t2, B, 'Beta unsaved');
  const back1 = await reloadBuilder(t1);
  check('R0: tab 1 gets its own character back after a reload', back1.id === String(A), `${back1.id} (${back1.name})`);
  check('R1: with its unsaved edit', back1.name === 'Alpha unsaved', back1.name);
  const back2 = await reloadBuilder(t2);
  check('R0: and so does tab 2', back2.id === String(B) && back2.name === 'Beta unsaved', `${back2.id} (${back2.name})`);

  // R7: a third tab must not take either of those drafts.
  const t3 = await ctx.newPage();
  t3.on('pageerror', e => jsErrors.push(e.message));
  await t3.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
  await ready(t3);
  await t3.waitForTimeout(800);
  const fresh = await t3.evaluate(() => ({ id: window.__id(), name: window.__name() }));
  check('R7: a new tab does not take a draft another open tab is editing',
        fresh.id !== String(A) && fresh.id !== String(B) && fresh.name !== 'Alpha unsaved' && fresh.name !== 'Beta unsaved',
        `${fresh.id} (${fresh.name})`);

  // First save: a clone has no id, so its draft has a browser key until it is saved.
  await t3.evaluate(() => window.__d('[:orcpub.dnd.e5.character/clone-character]'));
  await t3.waitForTimeout(800);
  // The clone is of whatever t3 holds; give it a name to find it by.
  await t3.evaluate(() => window.__d('[:update-value-field :orcpub.dnd.e5.character/character-name "Clone unsaved"]'));
  await t3.waitForTimeout(300);
  const newKeys = await t3.evaluate(() => window.__drafts().filter(k => k.startsWith('character-draft/new-')
    && localStorage.getItem(k).includes('Clone unsaved')));
  check('a never-saved character has its own draft under a browser key', newKeys.length === 1, JSON.stringify(newKeys));
  const saved = t3.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/dnd/5e/characters',
                                   { timeout: 30000 });
  await t3.evaluate(() => window.re_frame.core.dispatch(window.__c.vector(window.__kw('save-character'),
    window.__c.deref(window.re_frame.core.subscribe(window.__c.vector(window.__kw('built-character')))))));
  const status = (await saved).status();
  await t3.waitForTimeout(1000);
  const after = await t3.evaluate(() => ({ id: window.__id(), drafts: window.__drafts() }));
  check('its first save moves the draft to its new id',
        status === 200 && after.id && !after.drafts.some(k => newKeys.includes(k))
        && after.drafts.includes(`character-draft/${after.id}`),
        `status ${status}, id ${after.id}, drafts ${JSON.stringify(after.drafts)}`);

  // R9: a browser holding only the old single slot.
  const legacy = await t1.evaluate(a => {
    const raw = localStorage.getItem('character') || localStorage.getItem('character-draft/' + a) || '';
    return raw.replace(/(:orcpub\.dnd\.e5\.character\/character-name )"[^"]*"/, '$1"Legacy draft"');
  }, A);
  const ctx2 = await newContext(browser);
  await ctx2.addInitScript(v => {
    if (!localStorage.getItem('test-seeded')) { localStorage.setItem('character', v); localStorage.setItem('test-seeded', '1'); }
  }, legacy);
  const old = await ctx2.newPage();
  old.on('pageerror', e => jsErrors.push(e.message));
  await old.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
  await ready(old);
  await old.waitForTimeout(800);
  const moved = await old.evaluate(() => ({
    name: window.__name(),
    legacy: localStorage.getItem('character'),
    draft: window.__drafts().map(k => localStorage.getItem(k)).find(v => v.includes('Legacy draft')) || '',
  }));
  check('R9: the old draft opens as before', moved.name === 'Legacy draft', moved.name);
  check('R9: and is moved to its own key, with the old slot removed',
        moved.legacy === null && moved.draft.includes('Legacy draft'),
        `old slot ${moved.legacy === null ? 'gone' : 'still there'}, new key ${moved.draft ? 'present' : 'missing'}`);

  check('no page errors', jsErrors.length === 0, jsErrors.slice(0, 3).join(' | '));
  await browser.close();
  console.log(failures ? `\n${failures} check(s) failed` : '\nall checks passed');
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
