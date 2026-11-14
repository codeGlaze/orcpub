// Needs: dev bundle (puts a character into the builder through the app's own re-frame store, which a production bundle compiles away).
// Two small contrast and size fixes: the library-health banner's text, and the Weapons table caret.
//
// The banner: the `.health-card` markup is injected as `library-health-status` renders it (views.cljs)
// and the computed colours of its message (`.health-msg strong`) and its detail line are read. Both
// sit on a #171d27 card, so each colour channel must be above 200; the browser's default black
// read dark on dark.
// The caret: a level 1 wizard carrying a dagger is loaded, the Combat tab opened at 1280px, and the
// `.weapon-caret` on the dagger's row must be a FontAwesome `<i>` icon (fa-caret-down while closed,
// fa-caret-up once the row is opened) with a computed font size of at least 15px; it was the text
// character "▾" at 12px.
//
// Needs:     server (`./scripts/e2e/run.sh test/browser/health_caret_e2e.js`). No login.
// Runs in:   ~15s.
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

// Puts a level 1 wizard carrying a dagger into the builder.
const load = page => page.evaluate(() => {
  const rd = cljs.reader.read_string;
  const s = 'orcpub.entity.strict', c = 'orcpub.dnd.e5.character';
  const held = `{:${c}.equipment/quantity 1 :${c}.equipment/equipped? true}`;
  const strict = `{:${s}/selections [
    {:${s}/key :ability-scores :${s}/option {:${s}/key :standard-scores
      :${s}/map-value {:${c}/str 15 :${c}/dex 14 :${c}/con 13 :${c}/int 12 :${c}/wis 10 :${c}/cha 8}}}
    {:${s}/key :class :${s}/options [{:${s}/key :wizard
      :${s}/selections [{:${s}/key :levels :${s}/options [{:${s}/key :level-1}]}]}]}
    {:${s}/key :weapons :${s}/options [{:${s}/key :dagger :${s}/map-value ${held}}]}]}`;
  re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray(
    [rd(':set-character'), orcpub.dnd.e5.character.from_strict(rd(strict))], true));
});

// Adds a health card, in the markup `library-health-status` renders, to the top of the app, and
// returns the computed colours of its message and its detail line.
const healthColours = page => page.evaluate(() => {
  const card = document.createElement('div');
  card.id = 'health-probe';
  card.className = 'health-card m-b-10';
  card.innerHTML = '<div class="health-row health-broken"><span class="health-rail"></span>'
    + '<span class="health-ico"><i class="fa fa-exclamation-triangle"></i></span>'
    + '<span class="health-msg"><strong>2 sources can\'t export — invalid data</strong>'
    + '<div class="f-s-12 opacity-7 m-t-2">Probe Source · Other Source</div></span>'
    + '<span class="health-act">review</span></div>';
  (document.querySelector('.app') || document.body).prepend(card);
  const rgb = el => (getComputedStyle(el).color.match(/[\d.]+/g) || []).slice(0, 3).map(Number);
  return { message: rgb(card.querySelector('.health-msg strong')),
           detail: rgb(card.querySelector('.health-msg div')) };
});

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await ctx.addInitScript(() => {
    try { localStorage.setItem('orcpub:no-cookie-banner', '1'); localStorage.setItem('whats-new-seen', '"long-rest-update-2026"'); } catch (e) {}
  });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => { try { return !!re_frame.core.dispatch_sync; } catch (e) { return false; } },
                             null, { timeout: 60000 });

  const col = await healthColours(page);
  const light = c => c.length === 3 && c.every(v => v > 200);
  check('health banner: the message text is light on the dark card', light(col.message), `rgb(${col.message})`);
  check('health banner: the detail line is light on the dark card', light(col.detail), `rgb(${col.detail})`);

  await load(page);
  await page.getByText('combat', { exact: true }).first().click({ timeout: 10000 }).catch(() => {});
  const section = page.locator('.weapons-section.desktop').filter({ visible: true }).first();
  if (!await section.waitFor({ timeout: 20000 }).then(() => true, () => false)) {
    check('the Combat tab shows the Weapons table', false, 'no .weapons-section.desktop');
  } else {
    const caret = section.locator('.weapon-row .weapon-caret').first();
    const info = () => caret.evaluate(e => ({ tag: e.tagName, cls: e.className,
                                              size: parseFloat(getComputedStyle(e).fontSize) }));
    const closed = await info();
    check('weapon caret: an <i> icon, fa-caret-down while the row is closed',
          closed.tag === 'I' && /\bfa-caret-down\b/.test(closed.cls), `${closed.tag} ${closed.cls}`);
    check('weapon caret: font size is at least 15px', closed.size >= 15, `${closed.size}px`);
    await section.locator('.weapon-row .weapon-cell-name').first().click({ timeout: 5000 }).catch(() => {});
    // The re-render follows the click; wait for it (bounded) before reading the class.
    await section.locator('.weapon-row .weapon-caret.fa-caret-up').first()
      .waitFor({ timeout: 5000 }).catch(() => {});
    const opened = await caret.evaluate(e => e.className).catch(() => '');
    check('weapon caret: fa-caret-up once the row is opened', /\bfa-caret-up\b/.test(opened), opened);
  }
  check('no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));

  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
