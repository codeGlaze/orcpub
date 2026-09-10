// Does a character that points at renamed homebrew heal in the real app, and say so?
//
// The resolution ladder is unit-tested end to end in CLJS and had never been watched
// working. This drives the real server on :8890 through the two ways a stored
// character reaches the builder:
//
//   OPEN   ::char5e/open-character, the event behind opening a saved character. It
//          goes through :set-character, so the reconcilers run.
//   RELOAD the builder restoring its in-progress character from localStorage at boot.
//          :initialize-db assocs that straight into :character, so this is the path to
//          watch: a heal that only happens on OPEN leaves a reloaded character broken.
//
// The claim is never "no crash". It is: the stored key is REWRITTEN to the live item,
// the option renders bound, the toast says what happened, and the save button carries
// the standing cue. Fail-soft once passed "no black screen" while every Features tab
// was empty, because its own boundary caught its own crash.
//
// Needs:     server (`lein e2e-server`). No login: the heal is client-side, and saving
//            needs a verified account the in-memory server does not seed, so the
//            save-clears-the-cue half is covered by events_test, not here.
// Runs in:   ~20s.
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const BASE = 'http://localhost:8890';
const OUT = process.env.PROBE_OUT || '/tmp/character-heal';

// A homebrew race renamed by an import conflict: it records the key it used to have.
// Its name derives its key, as common/disambiguated guarantees for a real rename.
const OLD_KEY = 'half-elf-phb';
const NEW_KEY = 'half-elf-ua';
const RACE_NAME = 'Half-Elf (UA)';
const PLUGINS = `{"Heal Pak" {:orcpub.dnd.e5/races {:${NEW_KEY} {:key :${NEW_KEY} :name "${RACE_NAME}" :option-pack "Heal Pak" :former-key :${OLD_KEY}}}}}`;

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

async function context(browser, character) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await ctx.addInitScript(([plugins, ch]) => {
    try {
      localStorage.setItem('plugins', plugins);
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', '"summer-patch-2026"');
      if (ch) localStorage.setItem('character', ch);
    } catch (e) {}
  }, [PLUGINS, character || null]);
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  return { ctx, page, errors };
}

const appReady = page => page.waitForFunction(() => {
  try {
    const db = cljs.core.deref(re_frame.db.app_db);
    return cljs.core.contains_QMARK_(db, cljs.reader.read_string(':plugins'));
  } catch (e) { return false; }
}, null, { timeout: 60000 });

// Read one path out of app-db as EDN text, so the probe never has to walk CLJS data.
const dbAt = (page, pathEdn) => page.evaluate(p => {
  const db = cljs.core.deref(re_frame.db.app_db);
  return cljs.core.pr_str(cljs.core.get_in(db, cljs.reader.read_string(p)));
}, pathEdn);

const saveButton = page => page.evaluate(() => {
  const b = [...document.querySelectorAll('button')].find(x => /save/i.test(x.textContent));
  return b ? { cls: b.className, text: b.textContent.trim() } : null;
});

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch(process.env.CHROME ? { executablePath: process.env.CHROME } : {});

  // ── OPEN ────────────────────────────────────────────────────────────────────
  const a = await context(browser);
  await a.page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
  await appReady(a.page);

  check('the renamed homebrew race loaded as a plugin, not quarantined',
        (await dbAt(a.page, '[:plugins "Heal Pak" :orcpub.dnd.e5/races]')).includes(NEW_KEY));

  // Build the broken character from the app's own default, pointing at the OLD key,
  // and keep its strict storage form for the RELOAD half.
  const brokenStrict = await a.page.evaluate(oldKey => {
    const rd = cljs.reader.read_string;
    const db = cljs.core.deref(re_frame.db.app_db);
    const broken = cljs.core.assoc_in(cljs.core.get(db, rd(':character')),
                                      rd('[:orcpub.entity/options :race]'),
                                      rd(`{:orcpub.entity/key :${oldKey}}`));
    window.__broken = broken;
    return cljs.core.pr_str(orcpub.dnd.e5.character.to_strict(broken));
  }, OLD_KEY);

  await a.page.evaluate(() => re_frame.core.dispatch_sync(
    cljs.core.conj(cljs.reader.read_string('[:orcpub.dnd.e5.character/open-character]'), window.__broken)));
  await a.page.waitForTimeout(1500);

  const openKey = await dbAt(a.page, '[:character :orcpub.entity/options :race :orcpub.entity/key]');
  check('OPEN: the stored race key is rewritten to the live item', openKey === `:${NEW_KEY}`, openKey);
  const openHealed = await dbAt(a.page, '[:character-healed :rewrote]');
  check('OPEN: the repair is recorded', openHealed.includes(`:from :${OLD_KEY}`) && openHealed.includes(`:to :${NEW_KEY}`), openHealed);

  const toast = await a.page.evaluate(() => (document.querySelector('.message') || {}).textContent || '');
  check('OPEN: a toast says what was reconnected', /Reconnected 1 reference/.test(toast), toast.slice(0, 120));

  const btn = await saveButton(a.page);
  check('OPEN: the save button carries the standing cue', !!btn && /\bsave-healed\b/.test(btn.cls), btn && btn.cls);

  const pageText = await a.page.evaluate(() => document.body.innerText);
  check('OPEN: the race renders bound, by its name', pageText.includes(RACE_NAME));
  check('OPEN: nothing reports the old key as missing', !pageText.includes(OLD_KEY));
  await a.page.screenshot({ path: path.join(OUT, 'open-healed.png') });
  check('OPEN: no page errors', a.errors.length === 0, a.errors.slice(0, 2).join(' | '));
  await a.ctx.close();

  // ── RELOAD ──────────────────────────────────────────────────────────────────
  const b = await context(browser, brokenStrict);
  await b.page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
  await appReady(b.page);
  await b.page.waitForTimeout(1500);

  const reloadKey = await dbAt(b.page, '[:character :orcpub.entity/options :race :orcpub.entity/key]');
  check('RELOAD: the restored character is rewritten to the live item', reloadKey === `:${NEW_KEY}`, reloadKey);
  const reloadHealed = await dbAt(b.page, '[:character-healed]');
  check('RELOAD: the repair is recorded', reloadHealed !== 'nil', reloadHealed);
  const toastB = await b.page.evaluate(() => (document.querySelector('.message') || {}).textContent || '');
  check('RELOAD: a toast says what was reconnected', /Reconnected 1 reference/.test(toastB), toastB.slice(0, 120));
  const btnB = await saveButton(b.page);
  check('RELOAD: the save button carries the standing cue', !!btnB && /\bsave-healed\b/.test(btnB.cls), btnB && btnB.cls);
  await b.page.screenshot({ path: path.join(OUT, 'reload.png') });
  check('RELOAD: no page errors', b.errors.length === 0, b.errors.slice(0, 2).join(' | '));
  await b.ctx.close();

  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
