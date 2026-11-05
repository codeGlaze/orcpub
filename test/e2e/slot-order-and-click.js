// Research, not a regression test. Two questions about how the builder stores picks, answered
// through the real builder UI:
//   CHECK 1  Does a shared (ref) slot store picks in the order chosen? The fighter's Fighting Style
//            is one ref slot ([:class :fighter :fighting-style]) fed by fighter 1 and Champion 10.
//            Run A picks Defense then Archery, run B Archery then Defense; then deselect/reselect
//            the first-chosen style, then save and read the saved copy back from the server.
//   CHECK 2  What clicking another option does in a one-pick slot:
//            (a) full, normal: fighter 1 Fighting Style = Defense, click Archery; and Hunter's
//                Defensive Tactics = Steel Will, click Escape the Horde.
//            (b) overfull: Champion 10 with Defense + Archery lowered to 9 ("select 1 · remove 1");
//                click Defense (selected); separately, from fresh state, click Dueling (unselected).
//
// It reports; it asserts nothing. Exit 0 unless a run could not be driven at all.
//
// Needs the seeded server (kaylee / serenity99):
//   DATOMIC_URL=datomic:mem://orcpub-e2e ORCPUB_ENV=dev SIGNATURE=e2e-test-signature PORT=8890 \
//   CSP_POLICY=none lein with-profile init-db run -m e2e-boot
// Prereqs: lein fig:build (and lein garden once). Run:
//   NODE_PATH=/opt/node22/lib/node_modules node test/e2e/slot-order-and-click.js
// RUNS=A,B,2a,2a-hunter,2b-defense,2b-dueling limits the run.
//
// State is set only by clicking. app-db and the saved character are READ to observe; nothing is
// dispatched.
const { chromium } = require('playwright');
const { BASE, findChrome, dbAt, readyPage, clickTab } = require('./lib');

const ONLY = (process.env.RUNS || '').split(',').filter(Boolean);
const log = (...a) => console.log(a.join(' '));

const FS_PATH = '[:character :orcpub.entity/options :class 0 :orcpub.entity/options :fighting-style]';
const keysOf = s => [...String(s).matchAll(/:orcpub\.entity\/key :([\w-]+)/g)].map(m => m[1]);

// Visible selection sections of the current tab (same DOM reading as hidden-pick-flows.js).
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
const fmt = s => `${s.parent ? s.parent + ' / ' : ''}${s.title} [${s.count}] ` +
  s.cards.map(c => (c.on ? '*' : '') + c.name + (c.ok ? '' : '(x)')).join(', ');

async function sectionText(page, title) {
  await clickTab(page, 'Class / Level');
  const ss = (await sectionsHere(page)).filter(s => s.title === title);
  return ss.length ? ss.map(fmt).join(' || ') : `no "${title}" section`;
}

async function pick(page, title, name, nth = 0) {
  await clickTab(page, 'Class / Level');
  const r = await page.evaluate(({ title, name, nth }) => {
    const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
    const secs = [...document.querySelectorAll('#app div.p-5.m-b-20')].filter(vis).filter(s => {
      const t = [...s.querySelectorAll('span.m-l-5.f-s-18.f-w-b')].find(e => e.closest('div.p-5.m-b-20') === s);
      return t && t.textContent.trim() === title;
    });
    const s = secs[nth];
    if (!s) return 'no section';
    const card = [...s.querySelectorAll('div.p-10.b-1.b-rad-5.m-5.b-orange')]
      .filter(c => c.closest('div.p-5.m-b-20') === s)
      .find(c => (c.querySelector('span.f-w-b.f-s-1') || {}).textContent.trim() === name);
    if (!card) return 'no card';
    card.click();
    return 'ok';
  }, { title, name, nth });
  await page.waitForTimeout(1200);
  log(`   click ${title} > ${name}: ${r}`);
  return r;
}

async function setLevel(page, n) {
  await clickTab(page, 'Class / Level');
  await page.locator('#app select.builder-option-dropdown.flex-grow-1').nth(0)
    .locator('xpath=..').locator('select.w-100').selectOption(`level-${n}`);
  await page.waitForTimeout(2000);
}
async function setClass(page, key) {
  await clickTab(page, 'Class / Level');
  await page.locator('#app select.builder-option-dropdown.flex-grow-1').nth(0).selectOption(key);
  await page.waitForTimeout(2000);
}

async function login(browser) {
  const page = await (await browser.newContext({ viewport: { width: 1400, height: 1000 } })).newPage();
  page.errors = [];
  page.on('pageerror', e => page.errors.push(String(e).slice(0, 160)));
  await page.goto(`${BASE}/pages/login-page`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input');
  await page.locator('input').nth(0).fill('kaylee');
  await page.locator('input').nth(1).fill('serenity99');
  await page.getByRole('button', { name: 'LOGIN' }).click({ force: true });
  await page.waitForTimeout(5000);
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'load' });
  await page.waitForTimeout(3000);
  await readyPage(page);
  return page;
}

async function stored(page, label, title = 'Fighting Style', path = FS_PATH) {
  const raw = await dbAt(page, path);
  log(`   [${label}] stored keys: ${JSON.stringify(keysOf(raw))}`);
  log(`   [${label}] stored raw: ${raw.replace(/orcpub\.entity\//g, '')}`);
  log(`   [${label}] UI: ${await sectionText(page, title)}`);
}

async function champion10(page) {
  await setClass(page, 'fighter');
  await setLevel(page, 3);
  await pick(page, 'Martial Archetype', 'Champion');
  await setLevel(page, 10);
}

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
  const at = body.indexOf('strict/key :fighting-style');
  const seg = at < 0 ? '' : body.slice(at, at + 600);
  log(`   saved id ${id}; server status ${body.slice(0, 3)}`);
  log(`   saved copy :fighting-style keys in order: ${JSON.stringify([...seg.matchAll(/strict\/key :([\w-]+)/g)].map(m => m[1]).slice(1))}`);
  log(`   saved copy raw: ${seg.replace(/:db\/id \d+, /g, '').replace(/orcpub\.entity\.strict\//g, '').slice(0, 400)}`);
  log(`   app-db after save: ${JSON.stringify(keysOf(await dbAt(page, FS_PATH)))}`);
}

async function orderRun(page, first, second) {
  await champion10(page);
  await stored(page, 'Champion 10, nothing picked');
  await pick(page, 'Fighting Style', first);
  await stored(page, `after ${first}`);
  await pick(page, 'Fighting Style', second);
  await stored(page, `after ${first} then ${second}`);
  await pick(page, 'Fighting Style', first);
  await stored(page, `after deselecting ${first}`);
  await pick(page, 'Fighting Style', first);
  await stored(page, `after re-selecting ${first}`);
  await saveAndReadBack(page);
}

async function overfull(page) {
  await champion10(page);
  await pick(page, 'Fighting Style', 'Defense');
  await pick(page, 'Fighting Style', 'Archery');
  await stored(page, 'Champion 10, Defense + Archery');
  await setLevel(page, 9);
  await stored(page, 'lowered to 9');
}

const runs = {
  async A(page) { await orderRun(page, 'Defense', 'Archery'); },
  async B(page) { await orderRun(page, 'Archery', 'Defense'); },
  async '2a'(page) {
    await setClass(page, 'fighter');
    await pick(page, 'Fighting Style', 'Defense');
    await stored(page, 'fighter 1, Defense');
    await pick(page, 'Fighting Style', 'Archery');
    await stored(page, 'after clicking Archery');
    await pick(page, 'Fighting Style', 'Defense');
    await stored(page, 'after clicking Defense (the selected one)');
    await pick(page, 'Fighting Style', 'Archery');
    await stored(page, 'after clicking Archery again');
  },
  async '2a-hunter'(page) {
    // Defensive Tactics lives under the Hunter option of the class's archetype selection; read the
    // whole class entry and print the defensive-tactics part of it.
    const CLS = '[:character :orcpub.entity/options :class 0]';
    const dt = async label => {
      const raw = (await dbAt(page, CLS)).replace(/orcpub\.entity\//g, '');
      const at = raw.indexOf(':defensive-tactics ');
      const seg = at < 0 ? 'none' : raw.slice(at, at + 60);
      log(`   [${label}] stored defensive-tactics: ${seg}`);
      log(`   [${label}] UI: ${await sectionText(page, 'Defensive Tactics')}`);
    };
    await setClass(page, 'ranger');
    await setLevel(page, 3);
    await pick(page, 'Ranger Archetype', 'Hunter');
    await setLevel(page, 7);
    await pick(page, 'Defensive Tactics', 'Steel Will');
    const raw = (await dbAt(page, CLS)).replace(/orcpub\.entity\//g, '');
    log(`   class entry: ${raw.slice(0, 900)}`);
    await dt('ranger 7, Steel Will');
    await pick(page, 'Defensive Tactics', 'Escape the Horde');
    await dt('after clicking Escape the Horde');
    await pick(page, 'Defensive Tactics', 'Escape the Horde');
    await dt('after clicking Escape the Horde again (selected)');
  },
  async '2b-defense'(page) {
    await overfull(page);
    await pick(page, 'Fighting Style', 'Defense');
    await stored(page, 'level 9, after clicking Defense');
  },
  async '2b-dueling'(page) {
    await overfull(page);
    await pick(page, 'Fighting Style', 'Dueling');
    await stored(page, 'level 9, after clicking Dueling');
  },
};

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  let broken = 0;
  for (const n of Object.keys(runs)) {
    if (ONLY.length && !ONLY.includes(n)) continue;
    log(`\n===== RUN ${n} =====`);
    let page;
    try {
      page = await login(browser);
      await runs[n](page);
    } catch (e) {
      broken++;
      log(`   RUN ${n} could not be driven: ${e.message.split('\n')[0]}`);
    }
    if (page) {
      log(`   page errors: ${page.errors.length ? page.errors.slice(0, 4).join(' | ') : 'none'}`);
      await page.context().close();
    }
  }
  await browser.close();
  process.exit(broken ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
