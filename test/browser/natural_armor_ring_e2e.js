// Needs: dev bundle (reads the app's internals, which a production bundle compiles away).
// Does an equipped Ring of Protection reach the AC the builder shows, for natural armor too?
//
// The unit tests (robe_ac_test.clj) feed the engine a hand-written copy of the ring's modifier.
// This drives the real path: the Ring of Protection item, marked equipped, through
// deferred-magic-item-fn; homebrew races whose :props carry :lizardfolk-ac / :tortle-ac; and the
// ::char5e/current-armor-class sub the builder displays, which picks the best armor combination
// on its own. Each case also reads the :armor-class-with-armor formula directly; both must agree.
//
// Fighter, Dex 14 (+2). Rules checked against the race text: lizardfolk 13 + Dex; tortle a flat 17
// with no Dex and no benefit from armor. Expected AC, bare / with the ring:
//   no race 12 / 13    lizardfolk 15 / 16    tortle 17 / 18
//
// Needs:     server (`lein e2e-server`). No login: the build is client-side.
// Runs in:   ~15s.
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';

const PLUGINS = `{"AC Pak" {:orcpub.dnd.e5/races {
  :scaly  {:key :scaly  :name "Scaly"  :option-pack "AC Pak" :speed 30 :props {:lizardfolk-ac true}}
  :shelly {:key :shelly :name "Shelly" :option-pack "AC Pak" :speed 30 :props {:tortle-ac true}}}}}`;

const CASES = [
  ['no race', null, false, 12], ['no race, ring', null, true, 13],
  ['lizardfolk', 'scaly', false, 15], ['lizardfolk, ring', 'scaly', true, 16],
  ['tortle', 'shelly', false, 17], ['tortle, ring', 'shelly', true, 18],
];

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

// Loads a level 1 fighter of `race` (a plugin race key, or null) into the builder, with the
// Ring of Protection equipped when `ring`. Returns the ring's item key as EDN.
const load = (page, race, ring) => page.evaluate(([race, ring]) => {
  const rd = cljs.reader.read_string;
  const ringItem = cljs.core.first(cljs.core.filter(
    i => cljs.core.get(i, rd(':orcpub.dnd.e5.magic-items/name')) === 'Ring of Protection',
    orcpub.dnd.e5.magic_items.other_magic_items));
  const ringKey = cljs.core.pr_str(cljs.core.get(ringItem, rd(':key')));
  const s = 'orcpub.entity.strict', c = 'orcpub.dnd.e5.character';
  const strict = `{:${s}/selections [
    {:${s}/key :ability-scores :${s}/option {:${s}/key :standard-scores
      :${s}/map-value {:${c}/str 15 :${c}/dex 14 :${c}/con 13 :${c}/int 12 :${c}/wis 10 :${c}/cha 8}}}
    {:${s}/key :class :${s}/options [{:${s}/key :fighter
      :${s}/selections [{:${s}/key :levels :${s}/options [{:${s}/key :level-1}]}]}]}
    ${race ? `{:${s}/key :race :${s}/option {:${s}/key :${race}}}` : ''}
    ${ring ? `{:${s}/key :other-magic-items :${s}/options [{:${s}/key ${ringKey}
      :${s}/map-value {:${c}.equipment/quantity 1 :${c}.equipment/equipped? true}}]}` : ''}]}`;
  re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray(
    [rd(':set-character'), orcpub.dnd.e5.character.from_strict(rd(strict))], true));
  return ringKey;
}, [race, ring]);

// The shown AC, the formula's AC (no armor, no shield) and the loaded race key, as EDN.
const readAc = page => page.evaluate(() => {
  const rd = cljs.reader.read_string;
  const sub = v => cljs.core.deref(re_frame.core.subscribe(cljs.core.PersistentVector.fromArray(v, true)));
  const built = sub([rd(':built-character')]);
  return {
    shown: cljs.core.pr_str(sub([rd(':orcpub.dnd.e5.character/current-armor-class'), null])),
    formula: cljs.core.pr_str(orcpub.entity_spec.entity_val(built, rd(':armor-class-with-armor')).call(null, null, null)),
    race: cljs.core.pr_str(cljs.core.get_in(cljs.core.deref(re_frame.db.app_db),
                                            rd('[:character :orcpub.entity/options :race :orcpub.entity/key]'))),
  };
});

// The built character object the builder currently holds, kept on window so a later wait can tell
// when the builder has replaced it.
const markBuilt = page => page.evaluate(() => {
  window.__acBuilt = cljs.core.deref(re_frame.core.subscribe(
    cljs.core.PersistentVector.fromArray([cljs.reader.read_string(':built-character')], true)));
});

// GOTCHA: subs read straight after dispatch_sync still hold the previous character, and two
// animation frames are not always enough; wait until the built character is a new object.
const rebuilt = page => page.waitForFunction(() => cljs.core.deref(re_frame.core.subscribe(
  cljs.core.PersistentVector.fromArray([cljs.reader.read_string(':built-character')], true))) !== window.__acBuilt,
  null, { timeout: 15000 });

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await ctx.addInitScript(plugins => {
    try {
      localStorage.setItem('plugins', plugins);
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', '"summer-patch-2026"');
    } catch (e) {}
  }, PLUGINS);
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => {
    try { return cljs.core.contains_QMARK_(cljs.core.deref(re_frame.db.app_db), cljs.reader.read_string(':plugins')); }
    catch (e) { return false; }
  }, null, { timeout: 60000 });

  for (const [label, race, ring, want] of CASES) {
    await markBuilt(page);
    const ringKey = await load(page, race, ring);
    if (ringKey === 'nil') { check('the Ring of Protection is in the item list', false); break; }
    await rebuilt(page);
    const r = await readAc(page);
    check(`${label}: AC ${want}`,
          r.shown === String(want) && r.formula === String(want) && r.race === (race ? `:${race}` : 'nil'),
          `shown=${r.shown} formula=${r.formula} race=${r.race}`);
  }
  check('no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));

  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
