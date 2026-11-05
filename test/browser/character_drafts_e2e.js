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
//   R2  a draft that changes nothing is neither offered nor listed
//   R3  a draft older than the saved character is not offered, but is listed
//   R4  a leftover is offered in one line in the notice box, the builder showing the saved version
//   R5  offered once; afterwards listed in the builder and on the character list; Discard removes it
//   R6  Restore, then Undo and Redo
//   R7  the same character open in another tab is held until Edit here, its draft left alone
//   R8  drafts untouched for 30 days go at start-up, except a never-saved character's
//   a builder Save refused as out of date: the notice, Details, Use saved and Save mine
//
// Needs:     the real app at :8890 (lein fig:build, then lein e2e-server); E2E_CHARACTER_ID and
//            E2E_HOMEBREW_CHARACTER_ID, set by run.sh.
// Runs in:   ~3 min.
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

// Closes `page` the way a player closes a tab: unload handlers run, a leave-page prompt is accepted.
async function closeTab(page) {
  if (page.isClosed()) return;
  page.on('dialog', d => d.accept().catch(() => {}));
  await page.close({ runBeforeUnload: true });
  await new Promise(r => setTimeout(r, 800));
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
    // The old slot predates drafts having an account, so the copy carries none.
    return raw.replace(/(:orcpub\.dnd\.e5\.character\/character-name )"[^"]*"/, '$1"Legacy draft"')
      .replace(/,? ?:draft\/owner "[^"]*"/, '');
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

  // ---- Opening a character that has a draft: R2-R7 and the drafts list ----
  for (const p of [t1, t2, t3]) await closeTab(p);
  await ctx2.close();
  const tab = async () => { const p = await ctx.newPage(); p.on('pageerror', e => jsErrors.push(e.message)); return p; };
  // Every draft and live mark gone, so each case starts clean.
  const reset = async () => {
    const p = await tab();
    await p.goto(`${BASE}/pages/dnd/5e/spells`, { waitUntil: 'domcontentloaded' });
    await p.evaluate(() => Object.keys(localStorage)
      .filter(k => k.startsWith('character-draft/') || k.startsWith('draft-live/'))
      .forEach(k => localStorage.removeItem(k)));
    await p.close();
  };
  const notice = p => p.evaluate(() => (document.querySelector('.drafts-notices .bg-note') || {}).innerText || '');
  const listText = async (p) => {
    const details = p.locator('.drafts-list button', { hasText: 'Details' });
    if (!(await details.count())) return '';
    await details.first().click();
    await p.waitForTimeout(300);
    return p.evaluate(() => document.querySelector('.drafts-list').innerText);
  };
  // A missing control fails the checks that follow it rather than stopping the run.
  const clickIn = async (p, scope, label) => {
    try { await p.locator(`${scope} button`, { hasText: label }).first().click({ timeout: 5000 }); }
    catch (_) { check(`${label} is there to press`, false); }
    await p.waitForTimeout(500);
  };
  const leftover = async (id, name) => { const p = await tab(); await openAndRename(p, id, name); await closeTab(p); };
  const openIn = async (id) => {
    const p = await tab();
    await p.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'networkidle' });
    await ready(p);
    await p.waitForFunction(i => window.__get(`[:orcpub.dnd.e5.character/character-map ${i}]`) != null, id, { timeout: 30000 });
    await p.evaluate(i => window.__edit(i), id);
    await p.waitForTimeout(1200);
    return p;
  };
  const savedName = async (p, id) => p.evaluate(i => window.__str(
    `[:orcpub.dnd.e5.character/character-map ${i} :orcpub.entity/values :orcpub.dnd.e5.character/character-name]`), id);

  // R7: the same character open in another tab is held until Edit here.
  await reset();
  const live = await tab();
  await openAndRename(live, A, 'Live A');
  const other = await openIn(A);
  const held = await other.evaluate(() => ({
    held: !!document.querySelector('.builder-held[inert]'),
    draft: localStorage.getItem('character-draft/' + window.__id()) || '',
  }));
  check('R7: opening a character open in another tab says so', (await notice(other)).includes('Open in another tab'),
        await notice(other));
  check('R7: and holds editing until Edit here', held.held);
  check('R7: and leaves the other tab\'s draft alone', held.draft.includes('Live A'));
  await clickIn(other, '.drafts-notices', 'Edit here');
  const took = await other.evaluate(() => ({ name: window.__name(), held: !!document.querySelector('.builder-held') }));
  check('R7: Edit here continues from the other tab\'s latest changes', took.name === 'Live A' && !took.held,
        `${took.name}, held ${took.held}`);
  await closeTab(live); await closeTab(other);

  // R2: a draft with no real change is dropped without a word.
  await reset();
  const undoer = await tab();
  // The clone saved above has a stored name, so renaming it back is an exact undo.
  const C = Number(after.id);
  await openAndRename(undoer, C, 'Temporary');
  const original = await savedName(undoer, C);
  await undoer.evaluate(n => window.__d(`[:update-value-field :orcpub.dnd.e5.character/character-name ${JSON.stringify(n)}]`),
                        original || '');
  await undoer.waitForTimeout(300);
  await closeTab(undoer);
  const r2 = await openIn(C);
  check('R2: a draft that changes nothing is not offered', !(await notice(r2)).includes('Unsaved changes'), await notice(r2));
  check('R2: nor listed', !(await listText(r2)).includes('Unsaved drafts'));
  await closeTab(r2);

  // R3: a draft older than the saved character is not offered, but is listed.
  await reset();
  await leftover(A, 'Stale edit');
  const sheet = await tab();
  await sheet.goto(`${BASE}/pages/dnd/5e/characters/${A}`, { waitUntil: 'networkidle' });
  await ready(sheet);
  await sheet.waitForFunction(i => window.__get(`[:orcpub.dnd.e5.character/character-map ${i}]`) != null, A, { timeout: 30000 });
  const sheetSave = sheet.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/dnd/5e/characters',
                                          { timeout: 30000 });
  await sheet.evaluate(i => window.__d(`[:orcpub.dnd.e5.character/set-current-hit-points ${i} 11]`), A);
  await sheetSave;
  await sheet.waitForTimeout(800);
  await sheet.evaluate(i => window.__edit(i), A);
  await sheet.waitForTimeout(1200);
  const r3 = { notice: await notice(sheet), name: await sheet.evaluate(() => window.__name()), list: await listText(sheet) };
  check('R3: a draft older than the saved character is not offered over it',
        !r3.notice.includes('Unsaved changes') && r3.name !== 'Stale edit', `${r3.notice} / ${r3.name}`);
  check('R3: but it is in the drafts list', r3.list.includes('Stale edit'), r3.list.slice(0, 120));
  await closeTab(sheet);

  // R4, R6: a leftover is offered without replacing the saved version; Restore, Undo, Redo.
  await reset();
  await leftover(B, 'Leftover B');
  const r4 = await openIn(B);
  const savedB = await savedName(r4, B);
  check('R4: a leftover draft is offered in one line', (await notice(r4)).includes('Unsaved changes from'), await notice(r4));
  check('R4: and the builder shows the saved version until Restore', (await r4.evaluate(() => window.__name())) === savedB);
  await clickIn(r4, '.drafts-notices', 'Restore');
  check('R6: Restore brings the changes back', (await r4.evaluate(() => window.__name())) === 'Leftover B');
  check('R6: and says so, with Undo', (await notice(r4)).includes('restored'), await notice(r4));
  await clickIn(r4, '.drafts-notices', 'Undo');
  check('R6: Undo puts the saved version back', (await r4.evaluate(() => window.__name())) === savedB);
  check('R6: then offers Redo', (await notice(r4)).includes('Undone'), await notice(r4));
  await clickIn(r4, '.drafts-notices', 'Redo');
  check('R6: and Redo restores again', (await r4.evaluate(() => window.__name())) === 'Leftover B');
  await closeTab(r4);

  // R5: offered once; then only in the list (builder and character list), where Discard removes it.
  await reset();
  await leftover(B, 'Leftover B2');
  const first = await openIn(B);
  check('R5: a leftover is offered the first time', (await notice(first)).includes('Unsaved changes from'));
  await first.locator('.drafts-notices button[aria-label="Dismiss"]').first().click({ timeout: 5000 })
    .catch(() => check('the offer\'s close mark is there to press', false));
  await first.waitForTimeout(500);
  check('R5: closing the offer keeps it in the builder\'s list', (await listText(first)).includes('Leftover B2'));
  await closeTab(first);
  const again = await openIn(B);
  check('R5: and it is not offered again', !(await notice(again)).includes('Unsaved changes'), await notice(again));
  await closeTab(again);
  const listPage = await tab();
  await listPage.goto(`${BASE}/pages/dnd/5e/characters`, { waitUntil: 'networkidle' });
  await listPage.waitForTimeout(1500);
  check('the character list shows it too', (await listText(listPage)).includes('Leftover B2'));
  await clickIn(listPage, '.drafts-list', 'Discard');
  check('R5: Discard removes it', (await listPage.locator('.drafts-list').count()) === 0
        && (await listPage.evaluate(() => Object.keys(localStorage).filter(k => k.startsWith('character-draft/kept-')).length)) === 0);
  await listPage.close();

  // A builder Save refused because the character was saved elsewhere since (409): the notice says
  // so, Details lists what differs, and the player keeps theirs or takes the saved one.
  await reset();
  {
    const p = await openIn(A);
    const refuseOnce = async () => {
      let left = 1;
      await p.route('**/dnd/5e/characters', async r => {
        if (r.request().method() !== 'POST' || left-- <= 0) return r.continue();
        const real = await r.fetch();
        const stored = (await real.text()).replace(/(:orcpub\.dnd\.e5\.character\/character-name )"[^"]*"/, '$1"Theirs elsewhere"');
        await r.fulfill({ status: 409, contentType: 'application/edn', body: `{:error :stale-character :current ${stored}}` });
      });
    };
    const builderSave = () => p.evaluate(() => window.re_frame.core.dispatch(window.__c.vector(window.__kw('save-character'),
      window.__c.deref(window.re_frame.core.subscribe(window.__c.vector(window.__kw('built-character')))))));
    await p.evaluate(() => window.__d('[:update-value-field :orcpub.dnd.e5.character/character-name "Mine here"]'));
    await refuseOnce();
    await builderSave();
    await p.waitForTimeout(2000);
    check('a Save refused as out of date says so', /Saved somewhere else since you opened it/.test(await notice(p)), await notice(p));
    await clickIn(p, '.drafts-notices', 'Details');
    const details = await p.evaluate(() => (document.querySelector('.drafts-notices .callout-details') || {}).innerText || '');
    check('Details lists what differs, here and saved', /Character Name/.test(details) && /Mine here/.test(details)
          && /Theirs elsewhere/.test(details), details);
    await clickIn(p, '.drafts-notices', 'Use saved');
    check('Use saved loads the saved character', (await p.evaluate(() => window.__name())) === 'Theirs elsewhere');
    check('and keeps mine in Unsaved drafts', (await listText(p)).includes('Mine here'));
    await p.unroute('**/dnd/5e/characters');

    await p.evaluate(() => window.__d('[:update-value-field :orcpub.dnd.e5.character/character-name "Mine again"]'));
    await refuseOnce();
    await builderSave();
    await p.waitForTimeout(2000);
    const saved = p.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/dnd/5e/characters',
                                    { timeout: 20000 }).catch(() => null);
    await clickIn(p, '.drafts-notices', 'Save mine');
    const reply = await saved;
    check('Save mine saves my version over the other', reply && reply.status() === 200 && (await reply.text()).includes('Mine again'),
          reply ? reply.status() : 'no save');
    await p.unroute('**/dnd/5e/characters');
    await closeTab(p);
  }

  // R8: at start-up, drafts untouched for 30 days go, except a never-saved character's, which may be
  // the only copy of it.
  {
    const old = Date.now() - 31 * 24 * 60 * 60 * 1000, recent = Date.now() - 2 * 24 * 60 * 60 * 1000;
    const ctx3 = await newContext(browser);
    await ctx3.addInitScript(([old, recent]) => {
      if (localStorage.getItem('expiry-seeded')) return;
      const draft = (nm, t) => `{:orcpub.entity.strict/values {:orcpub.dnd.e5.character/character-name "${nm}"} :changed true :draft/touched ${t}}`;
      localStorage.setItem('character-draft/12345', draft('Old saved', old));
      localStorage.setItem('character-draft/kept-old', draft('Old kept', old));
      localStorage.setItem('character-draft/new-old', draft('Old never saved', old));
      localStorage.setItem('character-draft/23456', draft('Recent saved', recent));
      localStorage.setItem('expiry-seeded', '1');
    }, [old, recent]);
    const p = await ctx3.newPage();
    p.on('pageerror', e => jsErrors.push(e.message));
    await p.goto(`${BASE}/pages/dnd/5e/spells`, { waitUntil: 'networkidle' });
    await p.waitForTimeout(1000);
    const left = await p.evaluate(() => Object.keys(localStorage).filter(k => k.startsWith('character-draft/')).sort());
    check('R8: drafts untouched for 30 days are removed at start-up',
          !left.includes('character-draft/12345') && !left.includes('character-draft/kept-old'), JSON.stringify(left));
    check('R8: but never a never-saved character\'s, which may be its only copy',
          left.includes('character-draft/new-old'), JSON.stringify(left));
    check('R8: and recent ones stay', left.includes('character-draft/23456'), JSON.stringify(left));
    await ctx3.close();
  }

  check('no page errors', jsErrors.length === 0, jsErrors.slice(0, 3).join(' | '));
  await browser.close();
  console.log(failures ? `\n${failures} check(s) failed` : '\nall checks passed');
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
