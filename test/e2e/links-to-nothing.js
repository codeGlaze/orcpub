// A link to nothing never breaks a page (homebrew-keys-design.md, invariant I11).
//
// One item per kind of link in library-links/links, each pointing at a key nothing holds, seeded as
// a stored library. Then My Content, every seeded item's builder and the character builder are
// opened, and any uncaught error fails. The kinds are read from the running app, so a new kind of
// link without a row here fails too.
//
// Prereqs:  lein fig:build && lein garden once && lein e2e-server
const { chromium } = require('playwright');
const { BASE, findChrome, checker, dismissCookieBar, dismissWhatsNew } = require('./lib');

const PAK = 'Nothing Pak';
// link id -> [content type, item key, item EDN (the link points at a key nothing holds), display name]
const HOLDERS = {
  'subclass->class':      ['subclasses', 'ghost-sub', '{:name "Ghost Sub" :class :no-such-class}', 'Ghost Sub'],
  'subrace->race':        ['subraces', 'ghost-subrace', '{:name "Ghost Subrace" :race :no-such-race}', 'Ghost Subrace'],
  'spell->class-list':    ['spells', 'ghost-spell', '{:name "Ghost Spell" :level 1 :school "evocation" :spell-lists {:no-such-class true}}', 'Ghost Spell'],
  'class->borrowed-list': ['classes', 'ghost-borrower', '{:name "Ghost Borrower" :hit-die 8 :spellcasting {:level-factor 1 :ability :orcpub.dnd.e5.character/int :spell-list-kw :no-such-class}}', 'Ghost Borrower'],
  'class->own-list':      ['classes', 'ghost-caster', '{:name "Ghost Caster" :hit-die 8 :spellcasting {:level-factor 1 :ability :orcpub.dnd.e5.character/int :spell-list {1 #{:no-such-spell}}}}', 'Ghost Caster'],
  'paladin-spells':       ['subclasses', 'ghost-oath', '{:name "Ghost Oath" :class :paladin :paladin-spells {1 {0 :no-such-spell}}}', 'Ghost Oath'],
  'cleric-spells':        ['subclasses', 'ghost-domain', '{:name "Ghost Domain" :class :cleric :cleric-spells {1 {0 :no-such-spell}}}', 'Ghost Domain'],
  'warlock-spells':       ['subclasses', 'ghost-patron', '{:name "Ghost Patron" :class :warlock :warlock-spells {1 {0 :no-such-spell}}}', 'Ghost Patron'],
  'level-modifier-spell': ['classes', 'ghost-granter', '{:name "Ghost Granter" :hit-die 8 :level-modifiers [{:type :spell :level 1 :value {:key :no-such-spell}}]}', 'Ghost Granter'],
  'granted-spell':        ['races', 'ghost-race', '{:name "Ghost Race" :spells [{:level 1 :value {:key :no-such-spell :ability :orcpub.dnd.e5.character/int}}]}', 'Ghost Race'],
  'level-selection':      ['classes', 'ghost-chooser', '{:name "Ghost Chooser" :hit-die 8 :level-selections [{:type :no-such-selection :level 1}]}', 'Ghost Chooser'],
  'race-prerequisite':    ['feats', 'ghost-feat', '{:name "Ghost Feat" :path-prereqs {:race {:no-such-race true}}}', 'Ghost Feat'],
  'granted-language':     ['races', 'ghost-speaker', '{:name "Ghost Speaker" :props {:language {:no-such-language true}}}', 'Ghost Speaker'],
  'language-by-name':     ['races', 'ghost-talker', '{:name "Ghost Talker" :languages #{"No Such Language"}}', 'Ghost Talker'],
  'encounter->monster':   ['encounters', 'ghost-ambush', '{:name "Ghost Ambush" :creatures [{:type :monster :creature {:monster :no-such-monster :num 1}}]}', 'Ghost Ambush'],
  'language-choice':      ['races', 'ghost-linguist', '{:name "Ghost Linguist" :profs {:language-options {:choose 1 :options {:no-such-language true}}}}', 'Ghost Linguist'],
};

const library = () => {
  const byType = {};
  for (const [type, key, edn] of Object.values(HOLDERS)) {
    const item = edn.replace(/^\{/, `{:key :${key} :option-pack "${PAK}" `);
    (byType[type] = byType[type] || []).push(`:${key} ${item}`);
  }
  return `{"${PAK}" {` + Object.entries(byType)
    .map(([t, items]) => `:orcpub.dnd.e5/${t} {${items.join(' ')}}`).join(' ') + '}}';
};

const expandAll = async page => {
  for (let i = 0; i < 4; i++) {
    const n = await page.evaluate(() => {
      const b = [...document.querySelectorAll('#app button, #app span')].filter(e => e.textContent.trim() === 'expand');
      b.forEach(x => x.click());
      return b.length;
    });
    await page.waitForTimeout(400);
    if (!n) break;
  }
};

const editRow = (page, name) => page.evaluate(nm => {
  const rows = [...document.querySelectorAll('#app div')].filter(e =>
    (e.textContent || '').includes(nm) &&
    [...e.querySelectorAll('button')].some(b => b.textContent.trim() === 'edit'));
  const row = rows[rows.length - 1];
  if (!row) return false;
  [...row.querySelectorAll('button')].find(b => b.textContent.trim() === 'edit').click();
  return true;
}, name);

(async () => {
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 1000 } })).newPage();
  let errors = [];
  page.on('pageerror', e => errors.push(String(e).slice(0, 160)));
  const clean = (label) => { check(label, errors.length === 0, errors.slice(0, 2).join(' | ')); errors = []; };
  const myContent = async () => {
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
    await page.waitForTimeout(2000);
    await dismissWhatsNew(page);
  };
  try {
    await myContent();
    await dismissCookieBar(page);
    await page.evaluate(lib => {
      ['plugins', 'plugins:rev', 'plugins:pre-fix', 'plugins:pre-fix-at', 'plugins:repairs-dismissed',
       'plugins:rejected'].forEach(k => localStorage.removeItem(k));
      localStorage.setItem('plugins', lib);
    }, library());
    await myContent();

    const edn = await page.evaluate(() => window.cljs.core.pr_str(window.orcpub.dnd.e5.library_links.links));
    const kinds = [...edn.matchAll(/:id :([^\s,}]+)/g)].map(m => m[1]);
    const missing = kinds.filter(k => !HOLDERS[k]);
    check(`every kind of link the app knows has a row here (${kinds.length})`, kinds.length > 0 && missing.length === 0,
          missing.join(', '));

    const stored = await page.evaluate(() => localStorage.getItem('plugins:rejected') || '');
    check('no seeded item was set aside on load', stored === '', stored.slice(0, 200));

    await expandAll(page);
    clean('My Content, every category open');

    for (const [id, [, , , name]] of Object.entries(HOLDERS)) {
      await myContent();
      await expandAll(page);
      const opened = await editRow(page, name);
      await page.waitForTimeout(1500);
      check(`${id}: its builder opened`, opened);
      clean(`${id}: its builder`);
    }

    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'load' });
    await page.waitForTimeout(3000);
    clean('the character builder, with every seeded item on offer');
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
