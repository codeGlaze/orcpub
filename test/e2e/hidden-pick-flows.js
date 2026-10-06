// Research, not a regression test: what a player actually ends up with when a pick's gate closes
// after the pick was made. A selection's prereq-fn decides only whether the builder SHOWS the pick;
// the build applies whatever is stored. The JVM research tests show the build honouring stored
// picks; this drives the five flows a player could use to reach those stored states, through the
// real builder UI only, and prints what the built character and the stored selections hold.
//
// It reports; it does not assert a fix. Exit 0 unless a flow could not be driven at all.
//
// Flows: 1 delete the first class; 2 the controls for class order and the first slot;
// 3 level drop below a subclass pick (Hunter, Defensive Tactics); 4 level drop under the shared
// fighting-style pool (Champion); 5 delete the first class when the second has its own picks.
//
// Needs a logged-in user, so it runs against the seeded server:
//   DATOMIC_URL=datomic:mem://orcpub-e2e ORCPUB_ENV=dev SIGNATURE=e2e-test-signature PORT=8890 \
//   CSP_POLICY=none lein with-profile init-db run -m e2e-boot      (seeds kaylee / serenity99)
// Prereqs: lein fig:build. Run: NODE_PATH=<dir with playwright> node test/e2e/hidden-pick-flows.js
// FLOWS=1,5 limits the run to those flows.
//
// State is set only by clicking. app-db, the :built-character subscription and the saved character
// are READ to observe; nothing is dispatched.
const { chromium } = require('playwright');
const { BASE, findChrome, dbAt, clickTab, TABS, pick: pickAny, setClass, setLevel, login, classRows, addClass, deleteClass } = require('./lib');

const ONLY = (process.env.FLOWS || '').split(',').filter(Boolean);
const out = [];
const log = (...a) => { const s = a.join(' '); out.push(s); console.log(s); };
const pick = (page, tab, title, name, opts = {}) => pickAny(page, tab, title, name, { ...opts, log });

// Every visible selection section of the current tab: its parent title (the class it belongs to),
// its title, the "select N / remaining" line, and its option cards with selected/selectable state.
const sectionsHere = page => page.evaluate(() => {
  const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  return [...document.querySelectorAll('#app div.p-5.m-b-20')].filter(vis).map(s => {
    const own = e => e.closest('div.p-5.m-b-20') === s;
    const t = [...s.querySelectorAll('span.m-l-5.f-s-18.f-w-b')].find(own);
    const p = [...s.querySelectorAll('span.i.f-s-14.f-w-n')].find(own);
    const q = [...s.querySelectorAll('div.p-5.f-s-16')].find(own);
    const cards = [...s.querySelectorAll('div.p-10.b-1.b-rad-5.m-5.b-orange')].filter(own).map(c => {
      const n = c.querySelector('span.f-w-b.f-s-1');
      return { name: n ? n.textContent.trim() : '?', on: c.classList.contains('b-w-5'), ok: c.classList.contains('pointer') };
    });
    return { parent: p ? p.textContent.trim() : '', title: t ? t.textContent.trim() : '',
             count: q ? [...q.querySelectorAll('span')].map(x => x.textContent.trim()).join(' ') : '', cards };
  });
});

const fmtSection = s => `${s.parent ? s.parent + ' / ' : ''}${s.title} [${s.count}] ` +
  s.cards.map(c => (c.on ? '*' : '') + c.name + (c.ok ? '' : '(x)')).join(', ');

// Every section on every builder tab whose parent or title matches rx.
async function scanAll(page, rx) {
  const found = [];
  for (const t of TABS) {
    if (!(await clickTab(page, t))) continue;
    for (const s of await sectionsHere(page)) {
      if (rx.test(s.parent) || rx.test(s.title)) found.push(`${t}: ${fmtSection(s)}`);
    }
  }
  await clickTab(page, 'Class / Level');
  return found;
}

// READ the built character through the builder's own subscription.
const built = (page, f) => page.evaluate((fn) => {
  const c = window.cljs.core;
  const b = c.deref(window.re_frame.core.subscribe(c.PersistentVector.fromArray([c.keyword('built-character')], true)));
  try { return c.pr_str(window.orcpub.dnd.e5.character[fn](b)); } catch (e) { return 'ERR ' + e.message; }
}, f);
const names = s => [...s.matchAll(/:name "([^"]+)"/g)].map(m => m[1]);
const keysOf = s => [...s.matchAll(/:orcpub\.entity\/key :([\w-]+)/g)].map(m => m[1]);
const sheetAC = async page => ((await page.locator('#app').innerText()).match(/Armor Class\s*\n\s*(\d+)/) || [])[1];

async function snapshot(page, label, { traitRx } = {}) {
  log(` -- ${label}`);
  await clickTab(page, 'Class / Level');
  log(`   class rows: ${JSON.stringify((await classRows(page)).map(r => [r.cls, r.level]))}`);
  log(`   stored :class: ${await dbAt(page, '[:character :orcpub.entity/options :class]')}`);
  for (const k of ['weapons', 'armor', 'equipment']) {
    log(`   stored :${k}: ${keysOf(await dbAt(page, `[:character :orcpub.entity/options :${k}]`)).join(', ') || '-'}`);
  }
  log(`   built skill profs: ${await built(page, 'skill_proficiencies')}`);
  log(`   built tool profs: ${await built(page, 'tool_proficiencies')}`);
  for (const f of ['normal_weapons_inventory', 'normal_armor_inventory', 'normal_equipment_inventory']) {
    log(`   built ${f}: ${await built(page, f)}`);
  }
  const tr = names(await built(page, 'traits'));
  log(`   built traits${traitRx ? ' matching ' + traitRx : ''}: ${(traitRx ? tr.filter(n => traitRx.test(n)) : tr).join('; ') || '-'}`);
  log(`   sheet AC: ${await sheetAC(page)}`);
}

// Save through the UI, then read the character back from the server the way the app does.
async function saveAndReadBack(page) {
  await page.getByText('Save New Character', { exact: true }).first().click();
  await page.waitForTimeout(4000);
  const id = (await dbAt(page, '[:character :db/id]')).trim();
  const body = await page.evaluate(async (id) => {
    const u = localStorage.getItem('user') || '';
    const tok = (u.match(/:token "([^"]+)"/) || [])[1];
    const r = await fetch(`/dnd/5e/characters/${id}`, { headers: { Authorization: 'Token ' + tok, Accept: 'application/edn' } });
    return r.status + ' ' + (await r.text());
  }, id);
  const at = body.indexOf('strict/key :class');
  log(`   saved id ${id}; server status ${body.slice(0, 3)}; its :class selection: ` +
    (at < 0 ? 'none' : body.slice(at, at + 1500).replace(/:db\/id \d+, /g, '').replace(/orcpub\.entity\.strict\//g, '')));
}

const flows = {
  // 1. Rogue first with its first-class picks, fighter second, then delete the rogue.
  async 1(page) {
    await setClass(page, 0, 'rogue');
    for (const s of ['Acrobatics', 'Deception', 'Perception', 'Stealth']) await pick(page, 'Proficiencies', 'Skill Proficiency', s, { parent: 'Rogue' });
    await pick(page, 'Equipment', 'Starting Equipment: Melee Weapon', 'Rapier', { parent: 'Rogue' });
    await pick(page, 'Equipment', 'Starting Equipment: Additional Weapon', 'Shortbow, Quiver, 20 Arrows', { parent: 'Rogue' });
    await pick(page, 'Equipment', "Starting Equipment: Equipment Pack", "Burglar's Pack", { parent: 'Rogue' });
    await addClass(page, 'fighter');
    log('   offered for Fighter (second): \n     ' + (await scanAll(page, /fighter|fighting/i)).join('\n     '));
    await snapshot(page, 'before deleting rogue');
    await deleteClass(page, 0);
    await snapshot(page, 'after deleting rogue (first class)');
    log('   offered now for Fighter: \n     ' + (await scanAll(page, /fighter|fighting|starting/i)).join('\n     '));
    log('   anything still offered for Rogue: ' + ((await scanAll(page, /rogue/i)).join(' | ') || 'none'));
    await saveAndReadBack(page);
  },

  // 5. Fighter first with first-class picks, rogue second with its multiclass skill; delete the fighter.
  async 5(page) {
    await setClass(page, 0, 'fighter');
    for (const s of ['Athletics', 'Intimidation']) await pick(page, 'Proficiencies', 'Skill Proficiency', s, { parent: 'Fighter' });
    await pick(page, 'Class / Level', 'Fighting Style', 'Defense');
    log('   fighter equipment sections: \n     ' + (await scanAll(page, /starting equipment/i)).join('\n     '));
    await addClass(page, 'rogue');
    log('   offered for Rogue (second): \n     ' + (await scanAll(page, /rogue/i)).join('\n     '));
    const rogueSkill = (await scanAll(page, /rogue/i)).find(s => /Skill Proficiency/.test(s));
    if (rogueSkill) await pick(page, 'Proficiencies', 'Skill Proficiency', 'Stealth', { parent: 'Rogue' });
    await snapshot(page, 'before deleting fighter', { traitRx: /fighting|style|defense/i });
    await deleteClass(page, 0);
    await snapshot(page, 'after deleting fighter (first class)', { traitRx: /fighting|style|defense/i });
    log('   offered now for Rogue: \n     ' + (await scanAll(page, /rogue|starting/i)).join('\n     '));
    await saveAndReadBack(page);
  },

  // 3. Ranger, Hunter at 3, level 7, Steel Will; then ranger to level 6.
  async 3(page) {
    await setClass(page, 0, 'ranger');
    await setLevel(page, 0, 3);
    log('   ranger sections at 3: \n     ' + (await scanAll(page, /archetype|hunter|ranger/i)).join('\n     '));
    await pick(page, 'Class / Level', 'Ranger Archetype', 'Hunter');
    await pick(page, 'Class / Level', "Hunter's Prey", 'Colossus Slayer');
    await setLevel(page, 0, 7);
    await pick(page, 'Class / Level', 'Defensive Tactics', 'Steel Will');
    await snapshot(page, 'ranger 7, Steel Will picked', { traitRx: /steel|defensive|colossus|hunter/i });
    log('   shown at 7: ' + (await scanAll(page, /defensive/i)).join(' | '));
    await setLevel(page, 0, 6);
    await snapshot(page, 'ranger lowered to 6', { traitRx: /steel|defensive|colossus|hunter/i });
    log('   shown at 6: ' + ((await scanAll(page, /defensive/i)).join(' | ') || 'no Defensive Tactics section'));
    await saveAndReadBack(page);
  },

  // 2. Which controls decide class order; change the first slot's class on a rogue+fighter.
  async 2(page) {
    await setClass(page, 0, 'rogue');
    for (const s of ['Acrobatics', 'Deception', 'Perception', 'Stealth']) await pick(page, 'Proficiencies', 'Skill Proficiency', s, { parent: 'Rogue' });
    await pick(page, 'Equipment', 'Starting Equipment: Melee Weapon', 'Rapier', { parent: 'Rogue' });
    await addClass(page, 'fighter');
    const rows = await classRows(page);
    rows.forEach((r, i) => log(`   row ${i}: ${r.cls} ${r.level}; controls ${r.controls.join(' ')}; class choices ${r.classChoices.join(' ')}`));
    log('   drag/reorder affordances in the class section: ' + await page.evaluate(() => {
      const sec = document.querySelector('#app select.builder-option-dropdown.flex-grow-1').closest('div.p-5.m-b-20') || document;
      return [...sec.querySelectorAll('[draggable=true], .fa-arrow-up, .fa-arrow-down, .fa-sort, .fa-chevron-up, .fa-bars, .fa-arrows')].length;
    }));
    await snapshot(page, 'rogue+fighter, rogue picks made');
    await setClass(page, 0, 'wizard');
    await snapshot(page, 'first slot changed rogue -> wizard');
    log('   offered now: \n     ' + (await scanAll(page, /wizard|fighter|rogue|starting/i)).join('\n     '));
  },

  // 4. Fighter 10 Champion with Defense (level 1) and Archery (Champion 10); lower to 9.
  async 4(page) {
    await setClass(page, 0, 'fighter');
    await pick(page, 'Equipment', 'Starting Equipment: Armor', 'Chain Mail', { parent: 'Fighter' });
    log(`   sheet AC in chain mail, no style yet: ${await sheetAC(page)}`);
    await pick(page, 'Class / Level', 'Fighting Style', 'Defense');
    log(`   sheet AC in chain mail with Defense: ${await sheetAC(page)}`);
    await setLevel(page, 0, 3);
    await pick(page, 'Class / Level', 'Martial Archetype', 'Champion');
    await setLevel(page, 0, 10);
    log('   fighting-style sections at 10: \n     ' + (await scanAll(page, /fighting/i)).join('\n     '));
    await pick(page, 'Class / Level', 'Fighting Style', 'Archery');
    log('   after picking Archery: \n     ' + (await scanAll(page, /fighting/i)).join('\n     '));
    await snapshot(page, 'fighter 10 Champion, Defense + Archery', { traitRx: /fighting|style|defense|archery/i });
    log(`   stored fighting styles: ${await dbAt(page, '[:character :orcpub.entity/options :class 0 :orcpub.entity/options :fighting-style]')}`);
    await setLevel(page, 0, 9);
    await snapshot(page, 'lowered to 9', { traitRx: /fighting|style|defense|archery/i });
    log('   offered at 9: \n     ' + ((await scanAll(page, /fighting/i)).join('\n     ') || 'none'));
    log(`   stored fighting styles: ${await dbAt(page, '[:character :orcpub.entity/options :class 0 :orcpub.entity/options :fighting-style]')}`);
  },
};

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  let broken = 0;
  for (const n of ['1', '5', '3', '2', '4']) {
    if (ONLY.length && !ONLY.includes(n)) continue;
    log(`\n===== FLOW ${n} =====`);
    let page;
    try {
      page = await login(browser);
      await flows[n](page);
    } catch (e) {
      broken++;
      log(`   FLOW ${n} could not be driven: ${e.message.split('\n')[0]}`);
    }
    if (page) {
      log(`   page errors: ${page.errors.length ? page.errors.slice(0, 4).join(' | ') : 'none'}`);
      await page.context().close();
    }
  }
  await browser.close();
  process.exit(broken ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
