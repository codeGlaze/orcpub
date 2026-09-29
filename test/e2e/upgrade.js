// Upgrade from the app on `integration` to this one, with data the old app made itself.
//
//   node test/e2e/upgrade.js old   # with the OLD app's bundle served: import packs, restore a
//                                  # quarantined pack, make an item, build and save characters,
//                                  # leave a builder draft open; record what the old app shows
//   node test/e2e/upgrade.js new   # same browser profile, NEW bundle: compare
//
// Both bundles are dev builds (`lein fig:build`); the footer check reads the build's out/orcpub/ver.js.
// Both phases need the seeded server (logged-in user, character endpoints) and one browser profile
// (UPGRADE_PROFILE) kept between them. The bundle under resources/public/js/compiled is swapped
// between phases; the server keeps running, so saved characters carry across.
//   DATOMIC_URL=datomic:mem://orcpub-e2e ORCPUB_ENV=dev SIGNATURE=e2e-test-signature PORT=8890 \
//   CSP_POLICY=none lein with-profile init-db run -m e2e-boot
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const { BASE, findChrome, checker, dbAt, fill, clickText, dismissCookieBar, dismissWhatsNew } = require('./lib');

const MODE = process.argv[2];
const OUT = process.env.UPGRADE_OUT || path.resolve(__dirname, '../../target/upgrade');
const PROFILE = process.env.UPGRADE_PROFILE || path.join(OUT, 'profile');
const FIX = path.resolve(__dirname, '../fixtures');
fs.mkdirSync(OUT, { recursive: true });
const shot = (page, n) => page.screenshot({ path: path.join(OUT, `${MODE}-${n}.png`), fullPage: true });
const wait = ms => new Promise(r => setTimeout(r, ms));

// ── what the app holds ───────────────────────────────────────────────────────

const storage = page => page.evaluate(() => {
  const o = {};
  for (let i = 0; i < localStorage.length; i++) { const k = localStorage.key(i); o[k] = localStorage.getItem(k); }
  return o;
});

// {type: {count, keys}} of the stored library, read with the app's own reader.
const librarySummary = (page, slot) => page.evaluate(slot => {
  const cc = window.cljs.core;
  const raw = localStorage.getItem(slot);
  if (!raw) return null;
  const lib = window.cljs.reader.read_string(raw);
  const out = {};
  for (const src of cc.into_array(cc.keys(lib) || cc.List.EMPTY)) {
    const plugin = cc.get(lib, src);
    if (!cc.map_QMARK_(plugin)) continue;
    for (const ct of cc.into_array(cc.keys(plugin) || cc.List.EMPTY)) {
      const items = cc.get(plugin, ct);
      if (!(cc.keyword_QMARK_(ct) && cc.map_QMARK_(items))) continue;
      const t = cc.name(ct);
      out[t] = out[t] || [];
      for (const k of cc.into_array(cc.keys(items) || cc.List.EMPTY)) out[t].push(src + '/' + cc.name(k));
    }
  }
  for (const t in out) out[t].sort();
  return out;
}, slot);

// The app's template: every homebrew option it offers (an option carrying a plugin source), each
// with the chain of [selection-key option-key max] from the top that reaches it.
const templateInfo = page => page.evaluate(() => {
  const cc = window.cljs.core;
  const K = (ns, n) => cc.keyword(ns, n);
  const db = cc.deref(window.re_frame.db.app_db);
  const tpl = cc.get(db, K('orcpub.dnd.e5.autosave-fx', 'cached-template'));
  const T = n => K('orcpub.template', n);
  const arr = s => s ? cc.into_array(s) : [];
  const nm = k => (k && cc.keyword_QMARK_(k)) ? cc.name(k) : null;
  const homebrew = [];
  const seen = new Set();
  const walk = (sels, trail, depth) => {
    if (depth > 8) return;
    for (const s of arr(sels)) {
      const sk = nm(cc.get(s, T('key')));
      const max = cc.get(s, T('max'));
      for (const o of arr(cc.get(s, T('options')))) {
        const ok = nm(cc.get(o, T('key')));
        const here = trail.concat([[sk, ok, max]]);
        if (ok && ok !== 'custom' && (cc.get(o, T('plugin-source')) != null || cc.get(o, T('edit-event')) != null))
          homebrew.push({ sel: sk, key: ok, name: cc.get(o, T('name')), path: here });
        const id = sk + '>' + ok + '@' + depth;
        if (seen.has(id)) continue;
        seen.add(id);
        walk(cc.get(o, T('selections')), here, depth + 1);
      }
    }
  };
  walk(cc.get(tpl, T('selections')), [], 0);
  return { ready: !!tpl, homebrew };
});

// Nested character options built from template paths: {sel: {key, max, opts}}.
function addPath(tree, p) {
  let node = tree;
  for (const [sel, opt, max] of p) {
    node[sel] = node[sel] || { max, picks: {} };
    if (sel === 'levels') {
      const n = Number(opt.replace('level-', ''));
      for (let i = 1; i <= n; i++) node[sel].picks['level-' + i] = node[sel].picks['level-' + i] || {};
    } else {
      node[sel].picks[opt] = node[sel].picks[opt] || {};
    }
    node = node[sel].picks[opt];
  }
}
function edn(tree) {
  const entries = Object.entries(tree).map(([sel, { max, picks }]) => {
    const one = ([k, sub]) => `{:orcpub.entity/key :${k}` +
      (Object.keys(sub).length ? ` :orcpub.entity/options ${edn(sub)}` : '') + '}';
    const list = Object.entries(picks).map(one);
    return `:${sel} ` + (max === 1 && list.length === 1 ? list[0] : `[${list.join(' ')}]`);
  });
  return `{${entries.join(' ')}}`;
}
const characterEdn = (name, tree) => '{:orcpub.entity/options ' + edn(tree).replace(/\}$/, '') +
  ' :ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value' +
  ' {:orcpub.dnd.e5.character/str 12 :orcpub.dnd.e5.character/dex 12 :orcpub.dnd.e5.character/con 12' +
  ' :orcpub.dnd.e5.character/int 12 :orcpub.dnd.e5.character/wis 12 :orcpub.dnd.e5.character/cha 12}}}' +
  ` :orcpub.entity/values {:orcpub.dnd.e5.character/character-name "${name}"}}`;

// The footer line the served bundle must show: its version, compile-time build date and
// description, read from the bundle on disk (resources/public/js/compiled/out/orcpub/ver.js), or
// null when the served bundle has no such file (a production build).
function expectedFooter() {
  const file = path.resolve(__dirname, '../../resources/public/js/compiled/out/orcpub/ver.js');
  if (!fs.existsSync(file)) return null;
  const src = fs.readFileSync(file, 'utf8');
  const fn = name => (src.match(new RegExp(`orcpub\\.ver\\.${name} = \\(function[^{]*\\{\\s*return "([^"]*)"`)) || [])[1];
  const [version, date, description] = ['version', 'date', 'description'].map(fn);
  return `Version ${version} (${date}) ${description} edition`;
}

// The page's footer shows exactly one version line, and it is the served bundle's.
async function checkFooter(page, check) {
  const lines = (await page.locator('#app').innerText()).split('\n').map(l => l.trim()).filter(l => /^Version \d/.test(l));
  const want = expectedFooter();
  check('the footer names the bundle being served', want !== null && lines.length === 1 && lines[0] === want,
        want === null ? 'the served bundle has no out/orcpub/ver.js: serve dev builds (lein fig:build)'
                      : `shown: ${lines.join(' | ') || 'none'}  expected: ${want}`);
  return lines[0];
}

// What a person sees on a saved character's page, with the lines that change between loads removed.
// The version line changes with every build; checkFooter checks it.
async function sheetText(page, id) {
  await page.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'load' });
  await wait(5000);
  const t = await page.locator('#app').innerText().catch(() => '');
  return t.split('\n').map(s => s.trim()).filter(Boolean)
    .filter(s => !/cookie|what's new|^\d+ (seconds?|minutes?) ago$|^Version \d/i.test(s));
}

async function login(page) {
  await page.goto(`${BASE}/pages/login-page`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input');
  await page.locator('input').nth(0).fill('kaylee');
  await page.locator('input').nth(1).fill('serenity99');
  await page.getByRole('button', { name: 'LOGIN' }).click({ force: true });
  await wait(5000);
}

async function myContent(page) {
  await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
  await wait(2500);
  await dismissWhatsNew(page);
}

async function importFile(page, file) {
  await myContent(page);
  await page.locator('#app input[type=file]').first().setInputFiles(file);
  await wait(6000);
  // "Which source name?": keep the source the content names.
  const keep = page.getByRole('button', { name: /^keep “/i }).first();
  if (await keep.isVisible().catch(() => false)) { await keep.click(); await wait(4000); }
  // A conflict screen, if the file clashes with itself or the library: take its defaults.
  const apply = page.getByRole('button', { name: /^apply/i }).first();
  if (await apply.isVisible().catch(() => false)) { await apply.click(); await wait(3000); return 'conflict screen: applied defaults'; }
  return 'imported';
}

const expandAll = async page => {
  for (let i = 0; i < 6; i++) {
    const n = await page.evaluate(() => {
      const b = [...document.querySelectorAll('#app button, #app span')].filter(e => e.textContent.trim() === 'expand');
      b.forEach(x => x.click());
      return b.length;
    });
    await wait(400);
    if (!n) break;
  }
};

// ── phases ───────────────────────────────────────────────────────────────────

async function oldPhase(page, check, errors) {
  await login(page);
  check('signed in', await page.evaluate(() => !!localStorage.getItem('user')));
  await dismissCookieBar(page);

  const log = {};
  log.importPak = await importFile(page, path.join(FIX, 'test-pak.orcbrew'));
  await shot(page, '1-pak');
  log.importTrap = await importFile(page, path.join(FIX, 'keyword-trap.orcbrew'));
  const restore = page.getByRole('button', { name: /auto-name & restore/i }).first();
  log.autoNameRestore = await restore.isVisible().catch(() => false);
  if (log.autoNameRestore) { await restore.click(); await wait(3000); }
  await shot(page, '2-restored');

  // An item made in a builder.
  await page.goto(`${BASE}/pages/dnd/5e/language-builder`, { waitUntil: 'load' });
  await wait(2000);
  await fill(page, 'Name', 'Tidetongue');
  await fill(page, 'Option Source Name', 'My Pak');
  log.madeLanguage = await clickText(page, /save to browser storage|^save$/i);
  await wait(1500);

  // Characters, from what the old builder offers: each pick is an option a player could click.
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'load' });
  await wait(6000);
  const { homebrew } = await templateInfo(page);
  const at = sel => homebrew.filter(h => h.sel === sel && h.path.length === 1);
  const hbRaces = at('race'), hbClasses = at('class'), hbBackgrounds = at('background'), hbFeats = at('feats');
  const hbRaceKeys = hbRaces.map(h => h.key), hbClassKeys = hbClasses.map(h => h.key);
  const subraces = homebrew.filter(h => h.sel === 'subrace');
  const subclasses = homebrew.filter(h => h.path.some(([sel]) => sel === 'levels') && h.path.length === 3 &&
                                          !/spells-known|cantrips|metamagic|invocation|hit-points|asi|feat/.test(h.sel));
  log.offered = { races: hbRaces.map(h => h.key), classes: hbClassKeys, backgrounds: hbBackgrounds.length,
                  feats: hbFeats.length, subraces: subraces.map(h => h.path.map(x => x[1]).join('>')),
                  subclasses: subclasses.slice(0, 12).map(h => h.path.map(x => x[1]).join('>')) };
  check('the old builder offers homebrew races, classes, backgrounds, feats, subraces and subclasses',
        hbRaces.length && hbClasses.length && hbBackgrounds.length && hbFeats.length && subraces.length && subclasses.length,
        JSON.stringify(log.offered).slice(0, 400));

  const plans = [];
  const level = (t, cls, n) => addPath(t, [['class', cls, null], ['levels', 'level-' + n, null]]);
  { // 1: homebrew race and its homebrew subrace, homebrew background, homebrew feat
    const t = {};
    const sr = subraces.find(h => hbRaceKeys.includes(h.path[0][1]));
    if (sr) addPath(t, sr.path); else addPath(t, hbRaces[0].path);
    addPath(t, hbBackgrounds[0].path);
    addPath(t, hbFeats[0].path);
    plans.push(['Upgrade One', t]);
  }
  { // 2: homebrew subrace of a built-in race; homebrew subclass of a built-in class
    const t = {};
    const sr = subraces.find(h => !hbRaceKeys.includes(h.path[0][1]));
    if (sr) addPath(t, sr.path);
    const sc = subclasses.find(h => !hbClassKeys.includes(h.path[0][1]));
    if (sc) addPath(t, sc.path);
    addPath(t, hbBackgrounds[1 % hbBackgrounds.length].path);
    plans.push(['Upgrade Two', t]);
  }
  { // 3: a homebrew class to level 3 with its homebrew subclass if it has one; another feat
    const t = {};
    const cls = hbClassKeys[hbClassKeys.length - 1];
    const sc = subclasses.find(h => h.path[0][1] === cls);
    if (sc) addPath(t, sc.path); else level(t, cls, 3);
    addPath(t, hbFeats[1 % hbFeats.length].path);
    plans.push(['Upgrade Three', t]);
  }

  log.characters = [];
  for (const [name, tree] of plans) {
    const ednStr = characterEdn(name, tree);
    await page.evaluate(ch => {
      const strict = window.orcpub.dnd.e5.character.to_strict(window.cljs.reader.read_string(ch));
      localStorage.setItem('character', window.cljs.core.pr_str(strict));
    }, ednStr);
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'load' });
    await wait(5000);
    await dismissWhatsNew(page);
    await page.getByText('Save New Character', { exact: true }).first().click().catch(() => {});
    await wait(5000);
    const id = (await dbAt(page, '[:character :db/id]')).trim();
    const options = await dbAt(page, '[:character :orcpub.entity/options]');
    check(`${name}: saved`, /^\d+$/.test(id), id);
    log.characters.push({ name, id, edn: ednStr, options });
  }
  for (const c of log.characters) {
    c.sheet = await sheetText(page, c.id);
    await shot(page, `3-sheet-${c.name.replace(/ /g, '-')}`);
  }
  log.footer = await checkFooter(page, check);

  // A builder left open on a library item, with an unsaved change.
  await myContent(page);
  await expandAll(page);
  const names = homebrew.map(h => h.name);
  const feat = hbFeats.find(h => names.filter(n => n === h.name).length === 1) || hbFeats[0];
  log.draftFeat = feat;
  log.openedDraft = await page.evaluate(nm => {
    const rows = [...document.querySelectorAll('#app div')].filter(e =>
      (e.textContent || '').includes(nm) && [...e.querySelectorAll('button')].some(b => b.textContent.trim() === 'edit'));
    const row = rows[rows.length - 1];
    if (!row) return false;
    [...row.querySelectorAll('button')].find(b => b.textContent.trim() === 'edit').click();
    return true;
  }, feat.name);
  await wait(2000);
  log.draftUrl = page.url();
  log.draftEdited = await fill(page, 'Description', 'Edited before the upgrade.').then(() => true, () => false);
  await wait(1000);

  log.storage = await storage(page);
  log.library = await librarySummary(page, 'plugins');
  log.rejected = await librarySummary(page, 'plugins:rejected');
  log.errors = errors.slice();
  fs.writeFileSync(path.join(OUT, 'old.json'), JSON.stringify(log, null, 1));
  check('no uncaught JS errors in the old app', errors.length === 0, errors.slice(0, 3).join(' | '));
}

async function newPhase(page, check, errors) {
  const old = JSON.parse(fs.readFileSync(path.join(OUT, 'old.json'), 'utf8'));
  const log = {};
  await myContent(page);
  await wait(3000);
  const st = await storage(page);
  log.slotsAdded = Object.keys(st).filter(k => !(k in old.storage));
  log.library = await librarySummary(page, 'plugins');
  const same = JSON.stringify(log.library) === JSON.stringify(old.library);
  const diffTypes = Object.keys({ ...old.library, ...log.library }).filter(t =>
    JSON.stringify((old.library || {})[t]) !== JSON.stringify((log.library || {})[t]));
  check('every library item is still stored under the same source and key', same, diffTypes.join(', '));
  check('nothing new is set aside', JSON.stringify(await librarySummary(page, 'plugins:rejected')) === JSON.stringify(old.rejected));
  log.tidied = st['plugins'] !== old.storage['plugins'];
  if (log.tidied) {
    check('the library was tidied, and the copy kept is exactly the old one',
          st['plugins:pre-fix'] === old.storage['plugins'], (st['plugins:pre-fix'] || 'none').slice(0, 80));
  }
  await expandAll(page);
  const body = await page.locator('#app').innerText();
  log.repairsPanel = (body.match(/\d+ links? to fix[\s\S]{0,400}/) || [''])[0];
  log.danglingMarks = (body.match(/link to nothing/gi) || []).length;
  check('no repair is suggested for a library the old app made', !log.repairsPanel, log.repairsPanel.slice(0, 200));
  log.danglingNote = log.danglingMarks ? `${log.danglingMarks} "link to nothing" marks` : 'none';
  await shot(page, '1-my-content');

  log.characters = [];
  let footerChecked = false;
  for (const c of old.characters) {
    const now = await sheetText(page, c.id);
    if (!footerChecked) { log.footer = await checkFooter(page, check); footerChecked = true; }
    const gone = c.sheet.filter(l => !now.includes(l));
    const added = now.filter(l => !c.sheet.includes(l));
    await shot(page, `2-sheet-${c.name.replace(/ /g, '-')}`);
    check(`${c.name}: its page shows what it showed before`, gone.length === 0 && added.length === 0,
          `lost: ${gone.slice(0, 6).join(' | ')}  new: ${added.slice(0, 6).join(' | ')}`);
    await page.getByText('Edit', { exact: true }).first().click().catch(() => {});
    await wait(4000);
    const b = await page.locator('#app').innerText();
    const missing = /missing content|content is missing|not available/i.test(b);
    const report = await page.evaluate(() => {
      const cc = window.cljs.core;
      return cc.pr_str(cc.deref(window.re_frame.core.subscribe(cc.PersistentVector.fromArray(
        [cc.keyword('orcpub.dnd.e5.character', 'missing-content-report')], true))));
    });
    const panel = (b.match(/Missing Content \(\d+\)[\s\S]{0,600}/) || [''])[0].replace(/\n+/g, ' | ');
    const asked = /did this character mean/i.test(b);
    check(`${c.name}: the builder reports no missing content and asks nothing`, !missing && !asked,
          (b.match(/.{0,80}(missing content|not available|did this character mean).{0,120}/i) || [''])[0]);
    const options = await dbAt(page, '[:character :orcpub.entity/options]');
    log.characters.push({ name: c.name, id: c.id, gone, added, optionsChanged: options !== c.options, missing, asked,
                          report, panel, options });
  }

  // The builder draft left open before the upgrade.
  if (old.draftUrl) {
    await page.goto(old.draftUrl, { waitUntil: 'load' });
    await wait(3000);
    const type = old.draftUrl.match(/\/([a-z]+)-builder/)[1] + 's';
    const before = (log.library[type] || []).length;
    log.draftStillThere = /Edited before the upgrade/.test(await page.locator('#app').innerHTML());
    await clickText(page, /save to browser storage|^save$/i);
    await wait(2500);
    log.draftMessage = (await page.locator('#app .message').first().innerText().catch(() => '')).trim();
    const after = ((await librarySummary(page, 'plugins'))[type] || []).length;
    check('the draft open before the upgrade saves without making a second copy', after === before,
          `${type} ${before} -> ${after}; ${log.draftMessage.slice(0, 160)}`);
    await shot(page, '3-draft-saved');
  }
  log.errors = errors.slice();
  fs.writeFileSync(path.join(OUT, 'new.json'), JSON.stringify(log, null, 1));
  check('no uncaught JS errors in the new app', errors.length === 0, errors.slice(0, 3).join(' | '));
}

(async () => {
  if (!['old', 'new'].includes(MODE)) { console.error('usage: upgrade.js old|new'); process.exit(2); }
  const { check, report } = checker();
  const ctx = await chromium.launchPersistentContext(PROFILE, { executablePath: findChrome(), viewport: { width: 1280, height: 1000 } });
  const page = ctx.pages()[0] || await ctx.newPage();
  const cdp = await ctx.newCDPSession(page);
  await cdp.send('Network.setCacheDisabled', { cacheDisabled: true });
  const errors = [];
  page.on('pageerror', e => errors.push(String(e).slice(0, 200)));
  try {
    await (MODE === 'old' ? oldPhase : newPhase)(page, check, errors);
  } catch (e) {
    check('ran to completion', false, e.stack.split('\n').slice(0, 3).join(' '));
  } finally {
    await ctx.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
