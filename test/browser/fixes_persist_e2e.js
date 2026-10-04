// Every fix the app applies to homebrew or a character has to be SAVED, not re-applied
// in memory on each load.
//
// Drives the REAL app against `lein e2e-server` on :8890.
//
// WHAT IT PINS: for each path that repairs something, the repair is in localStorage
// straight away, it is still there after a reload, the reload has nothing left to
// repair, and an export carries it. A fix that only lives in app-db looks correct on
// screen and reverts on refresh, which is how "fixed" problems kept coming back.
//
// Paths: import cleanups, an entry with no source included; the loader's repairs,
// source names and set-asides; a library stored as
// text; a stored value that is not a library; conflict renames; Auto-name & Restore
// in the needs-attention panel; Export & Auto-Fix; Save anyway (spell and selection);
// a character repaired on refresh.
//
// Each path gets its own browser context. Storage is seeded ONCE per context behind
// a sentinel: an init script runs on every document, and an unconditional seed would
// undo on reload exactly what this probe is checking was saved.
//
// Needs:     the real app at :8890 (lein fig:build, then lein e2e-server)
// Runs in:   ~2 min, most of it page loads.
// Run:       node test/browser/fixes_persist_e2e.js      exit 0 = all checks passed
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const fs = require('fs'), os = require('os'), path = require('path');

const BASE = process.env.ORCPUB_E2E_URL || 'http://localhost:8890';
const OUT = process.env.PROBE_OUT || fs.mkdtempSync(path.join(os.tmpdir(), 'fixes-persist-'));
const PAGE = '/dnd/5e/my-content';

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass: !!pass });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + String(detail).slice(0, 200) + ']' : ''));
}

async function session(browser, seed) {
  const ctx = await browser.newContext({ acceptDownloads: true, viewport: { width: 1280, height: 900 } });
  await ctx.addInitScript(seed => {
    try {
      if (!localStorage.getItem('probe:seeded')) {
        for (const [k, v] of Object.entries(seed)) localStorage.setItem(k, v);
        localStorage.setItem('probe:seeded', '1');
      }
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', '"summer-patch-2026"');
    } catch (e) {}
  }, seed || {});
  const page = await ctx.newPage();
  const s = { ctx, page, console: [], errors: [] };
  page.on('console', m => s.console.push(m.text()));
  page.on('pageerror', e => s.errors.push(e.message));
  return s;
}

// Not "has :plugins": a store that loads nothing leaves that key out.
const appReady = page => page.waitForFunction(() => {
  try {
    return !!document.querySelector('.app-header-bar')
      && cljs.core.count(cljs.core.deref(re_frame.db.app_db)) > 0;
  } catch (e) { return false; }
}, null, { timeout: 90000 });

async function load(s, how) {
  s.console.length = 0;
  if (how === 'reload') await s.page.reload({ waitUntil: 'domcontentloaded' });
  else await s.page.goto(BASE + PAGE, { waitUntil: 'domcontentloaded' });
  await appReady(s.page);
  await s.page.waitForTimeout(700);
}

// Follow-up events go out on :dispatch-n, a tick later than dispatch_sync returns.
const settle = s => s.page.waitForTimeout(900);

// An event as EDN, with any trailing JS strings conj'ed on as CLJS strings (file text).
const dispatch = (s, ednEvent, ...strings) => s.page.evaluate(([e, xs]) => {
  let ev = cljs.reader.read_string(e);
  for (const x of xs) ev = cljs.core.conj(ev, x);
  re_frame.core.dispatch_sync(ev);
}, [ednEvent, strings]);

const dbAt = (s, pathEdn) => s.page.evaluate(p =>
  cljs.core.pr_str(cljs.core.get_in(cljs.core.deref(re_frame.db.app_db), cljs.reader.read_string(p))), pathEdn);

const stored = (s, key) => s.page.evaluate(k => localStorage.getItem(k), key);

// One path out of a stored EDN value, as EDN text; null when the key is absent.
const storedAt = (s, key, pathEdn) => s.page.evaluate(([k, p]) => {
  const v = localStorage.getItem(k);
  if (v == null) return null;
  return cljs.core.pr_str(cljs.core.get_in(cljs.reader.read_string(v), cljs.reader.read_string(p)));
}, [key, pathEdn]);

async function download(s, label, ednEvent) {
  const [dl] = await Promise.all([
    s.page.waitForEvent('download', { timeout: 20000 }),
    dispatch(s, ednEvent),
  ]);
  const file = path.join(OUT, label + '-' + dl.suggestedFilename());
  await dl.saveAs(file);
  return fs.readFileSync(file, 'utf8');
}

const keysOf = edn => [...edn.matchAll(/:([a-z0-9][a-z0-9-]*) \{/g)].map(m => m[1]);
const letterFirst = k => /^[a-z]/.test(k);

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });

  // ── Import cleanups ──────────────────────────────────────────────────────────
  {
    const s = await session(browser, { plugins: '{}' });
    await load(s);
    await dispatch(s, '[:orcpub.dnd.e5/import-plugin "Clean Pak"]',
      '{"Clean Pak" {:orcpub.dnd.e5/spells {:quoted {:key :quoted :option-pack "Clean Pak" :name "" '
      + ':level 1 :school "evocation" :description "An “odd” one" :disabled? nil} '
      + ':nosrc {:key :nosrc :name "No Src" :level 1 :school "evocation"}}}}');
    await settle(s);
    const at = '["Clean Pak" :orcpub.dnd.e5/spells :quoted]';
    const saved = await storedAt(s, 'plugins', at);
    check('import: the filled-in name is saved', saved && !/:name ""/.test(saved) && /:name "/.test(saved), saved);
    check('import: straightened quotes are saved', saved && !/[“”]/.test(saved), saved);
    check('import: an entry with no source is saved under the source it came in',
          (await storedAt(s, 'plugins', '["Clean Pak" :orcpub.dnd.e5/spells :nosrc :option-pack]')) === '"Clean Pak"');
    await load(s, 'reload');
    const reloaded = (await dbAt(s, '[:plugins "Clean Pak" :orcpub.dnd.e5/spells :quoted]')) || '';
    check('import: both survive a reload', !/:name ""/.test(reloaded) && /:name "/.test(reloaded) && !/[“”]/.test(reloaded), reloaded);
    const file = await download(s, 'import', '[:orcpub.dnd.e5/export-plugin "Clean Pak" nil]');
    check('import: the export carries them', !/:name ""/.test(file) && !/[“”]/.test(file) && /An "odd" one|An \\"odd\\" one/.test(file));
    await s.ctx.close();
  }

  // ── The loader's repairs and set-asides ─────────────────────────────────────
  {
    const s = await session(browser, {
      plugins: '{"Mend Pak" {:orcpub.dnd.e5/spells [{:name "Bolt" :option-pack "Mend Pak" :level 1 :school "evocation"}] '
        + ':orcpub.dnd.e5/feats "" '
        + ':orcpub.dnd.e5/backgrounds {:no-src {:key :no-src :name "No Src"} :broken "not an entry" :sage {:key :sage :option-pack "Mend Pak" :name "Sage"}}}}',
    });
    await load(s);
    check('load: the list section loads as entries', (await dbAt(s, '[:plugins "Mend Pak" :orcpub.dnd.e5/spells]')).includes(':bolt'));
    const first = await stored(s, 'plugins');
    check('load: the repair is saved', first && !/:orcpub.dnd.e5\/spells \[/.test(first) && !first.includes(':broken'), first);
    check('load: the entry with no source gets its source name, saved',
          (await storedAt(s, 'plugins', '["Mend Pak" :orcpub.dnd.e5/backgrounds :no-src :option-pack]')) === '"Mend Pak"');
    check('load: the broken entry is set aside in storage',
          ((await stored(s, 'plugins:rejected')) || '').includes(':broken'));
    await load(s, 'reload');
    const again = s.console.filter(t => /Repaired .* damaged homebrew|Set aside newly-invalid|Gave .* with no source/.test(t));
    check('load: a reload has nothing left to repair, fill or set aside', again.length === 0, again[0]);
    check('load: and leaves the stored library as it was', (await stored(s, 'plugins')) === first);
    await s.ctx.close();
  }

  // ── A library stored as text ────────────────────────────────────────────────
  {
    const lib = '{"Text Pak" {:orcpub.dnd.e5/feats {:tough {:key :tough :option-pack "Text Pak" :name "Tough"}}}}';
    const asText = '"' + lib.replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"';
    const s = await session(browser, { plugins: asText });
    await load(s);
    check('text library: it loads', (await dbAt(s, '[:plugins "Text Pak" :orcpub.dnd.e5/feats]')).includes(':tough'));
    check('text library: it is saved as the library itself',
          ((await storedAt(s, 'plugins', '["Text Pak" :orcpub.dnd.e5/feats :tough :name]')) || '') === '"Tough"',
          (await stored(s, 'plugins') || '').slice(0, 80));
    await s.ctx.close();
  }

  // ── A stored value that is not a library ────────────────────────────────────
  {
    const s = await session(browser, { plugins: '[:not :a :library]' });
    await load(s);
    check('not a library: a copy is kept for recovery', (await stored(s, 'plugins:corrupt')) === '[:not :a :library]');
    check('not a library: the active slot is cleared', (await stored(s, 'plugins')) === null, await stored(s, 'plugins'));
    await load(s, 'reload');
    const again = s.console.filter(t => /were not a map/.test(t));
    check('not a library: a reload does not handle it again', again.length === 0, again[0]);
    await s.ctx.close();
  }

  // ── Conflict renames ────────────────────────────────────────────────────────
  {
    const bolt = src => `{:bolt {:key :bolt :option-pack "${src}" :name "Bolt" :level 1 :school "evocation"}}`;
    const s = await session(browser, { plugins: `{"Pack A" {:orcpub.dnd.e5/spells ${bolt('Pack A')}}}` });
    await load(s);
    await dispatch(s, '[:orcpub.dnd.e5/import-plugin "Pack B"]', `{"Pack B" {:orcpub.dnd.e5/spells ${bolt('Pack B')}}}`);
    await settle(s);
    check('conflict: the import stops to ask', (await dbAt(s, '[:conflict-resolution :active?]')) === 'true');
    await dispatch(s, '[:rename-all-conflicts]');
    await dispatch(s, '[:apply-conflict-resolutions]');
    await settle(s);
    const renamed = keysOf((await storedAt(s, 'plugins', '["Pack B" :orcpub.dnd.e5/spells]')) || '').filter(k => k !== 'bolt');
    check('conflict: the renamed copy is saved', renamed.length === 1 && letterFirst(renamed[0]), renamed.join(','));
    await load(s, 'reload');
    check('conflict: a reload keeps both copies',
          (await dbAt(s, '[:plugins "Pack A" :orcpub.dnd.e5/spells]')).includes(':bolt ')
          && (await dbAt(s, '[:plugins "Pack B" :orcpub.dnd.e5/spells]')).includes(':' + renamed[0] + ' '));
    const file = await download(s, 'conflict', '[:orcpub.dnd.e5/export-plugin "Pack B" nil]');
    check('conflict: the export carries the new key', file.includes(':' + renamed[0]));
    await s.ctx.close();
  }

  // ── Auto-name & Restore in the needs-attention panel ────────────────────────
  {
    const s = await session(browser, {
      plugins: '{}',
      'plugins:rejected': '{"Q Pak" {:orcpub.dnd.e5/spells {:9-lives {:key :9-lives :option-pack "Q Pak" :name "9 Lives" :level 1 :school "evocation"}}}}',
    });
    await load(s);
    await dispatch(s, '[:orcpub.dnd.e5/repair-quarantined-source "Q Pak" {} true]');
    await settle(s);
    const keys = keysOf((await storedAt(s, 'plugins', '["Q Pak" :orcpub.dnd.e5/spells]')) || '');
    check('restore: the renamed entry is saved to the library', keys.length === 1 && letterFirst(keys[0]), keys.join(','));
    check('restore: and taken out of the set-aside copy', !((await stored(s, 'plugins:rejected')) || '').includes('9-lives'));
    await load(s, 'reload');
    check('restore: a reload keeps it restored',
          (await dbAt(s, '[:plugins "Q Pak" :orcpub.dnd.e5/spells]')).includes(':' + keys[0] + ' ')
          && !((await stored(s, 'plugins:rejected')) || '').includes('9-lives'));
    const file = await download(s, 'restore', '[:orcpub.dnd.e5/export-plugin "Q Pak" nil]');
    check('restore: the export carries it', file.includes(':' + keys[0]));
    await s.ctx.close();
  }

  // ── Export & Auto-Fix ───────────────────────────────────────────────────────
  {
    const s = await session(browser, {
      plugins: '{"Fix Pak" {:orcpub.dnd.e5/spells {:blank-one {:key :blank-one :option-pack "Fix Pak" :name "" :level 1 :school "evocation"}}}}',
    });
    await load(s);
    await dispatch(s, '[:orcpub.dnd.e5/export-plugin "Fix Pak" nil]');
    await settle(s);
    check('auto-fix: the missing name stops the export', (await dbAt(s, '[:export-warning :active?]')) === 'true');
    const file = await download(s, 'autofix', '[:export-with-auto-fix]');
    await settle(s);
    const saved = (await storedAt(s, 'plugins', '["Fix Pak" :orcpub.dnd.e5/spells]')) || '';
    check('auto-fix: the file has a name', !/:name ""/.test(file) && /:name "[^"]+"/.test(file));
    check('auto-fix: so does the library', saved && !/:name ""/.test(saved), saved);
    await load(s, 'reload');
    await dispatch(s, '[:orcpub.dnd.e5/export-plugin "Fix Pak" nil]').catch(() => {});
    await settle(s);
    check('auto-fix: after a reload the export no longer stops', (await dbAt(s, '[:export-warning :active?]')) !== 'true');
    await s.ctx.close();
  }

  // ── Save anyway: a spell, and a selection ───────────────────────────────────
  {
    const s = await session(browser, { plugins: '{}' });
    await load(s);
    await dispatch(s, '[:orcpub.dnd.e5.spells/set-spell {:name "9 Lives" :option-pack ""}]');
    await dispatch(s, '[:orcpub.dnd.e5.spells/save-spell-anyway]');
    await settle(s);
    await s.page.evaluate(() => cljs.core.swap_BANG_.call(null, re_frame.db.app_db, cljs.core.assoc,
      cljs.reader.read_string(':orcpub.dnd.e5.selections/builder-item'),
      cljs.reader.read_string('{:name "9 Lives" :option-pack "" :options [{:name "First"}]}')));
    await dispatch(s, '[:orcpub.dnd.e5.selections/save-selection-anyway]');
    await settle(s);
    const at = ct => `["Default Option Source" :orcpub.dnd.e5/${ct}]`;
    const spellKeys = keysOf((await storedAt(s, 'plugins', at('spells'))) || '');
    const selKeys = keysOf((await storedAt(s, 'plugins', at('selections'))) || '');
    check('save anyway: the spell is saved under a key a load keeps', spellKeys.length === 1 && letterFirst(spellKeys[0]), spellKeys.join(','));
    check('save anyway: so is the selection', selKeys.length === 1 && letterFirst(selKeys[0]), selKeys.join(','));
    await load(s, 'reload');
    check('save anyway: a reload keeps both in the library',
          spellKeys.length === 1 && selKeys.length === 1
          && (await dbAt(s, '[:plugins "Default Option Source" :orcpub.dnd.e5/spells]')).includes(':' + spellKeys[0] + ' ')
          && (await dbAt(s, '[:plugins "Default Option Source" :orcpub.dnd.e5/selections]')).includes(':' + selKeys[0] + ' '));
    check('save anyway: nothing was set aside', !((await stored(s, 'plugins:rejected')) || '').includes('Default Option Source'),
          await stored(s, 'plugins:rejected'));
    await s.ctx.close();
  }

  // ── A character repaired on refresh ─────────────────────────────────────────
  {
    const s = await session(browser, {
      plugins: '{"Heal Pak" {:orcpub.dnd.e5/races {:half-elf-ua {:key :half-elf-ua :name "Half-Elf (UA)" :option-pack "Heal Pak" :former-key :half-elf-phb}}}}',
    });
    s.page.goto(BASE + '/pages/dnd/5e/character-builder', { waitUntil: 'domcontentloaded' });
    await appReady(s.page);
    await s.page.waitForTimeout(700);
    // The broken draft is built from the app's own default character, pointing at the old key.
    await s.page.evaluate(() => {
      const rd = cljs.reader.read_string, db = cljs.core.deref(re_frame.db.app_db);
      const broken = cljs.core.assoc_in(cljs.core.get(db, rd(':character')),
        rd('[:orcpub.entity/options :race]'), rd('{:orcpub.entity/key :half-elf-phb}'));
      localStorage.setItem('character', cljs.core.pr_str(orcpub.dnd.e5.character.to_strict(broken)));
    });
    await s.page.reload({ waitUntil: 'domcontentloaded' });
    await appReady(s.page);
    await s.page.waitForTimeout(700);
    const draft = (await stored(s, 'character')) || '';
    check('character: the repaired draft is saved', draft.includes(':half-elf-ua') && !draft.includes(':half-elf-phb'));
    await s.page.reload({ waitUntil: 'domcontentloaded' });
    await appReady(s.page);
    await s.page.waitForTimeout(700);
    check('character: a second reload has nothing to repair', (await dbAt(s, '[:character-healed]')) === 'nil',
          await dbAt(s, '[:character-healed]'));
    check('character: and leaves the draft as it was', (await stored(s, 'character')) === draft);
    await s.ctx.close();
  }

  await browser.close();
  const failed = results.filter(r => !r.pass);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  console.log(`downloads: ${OUT}`);
  process.exit(failed.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
