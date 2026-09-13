// Homebrew that breaks the character options is set aside, and the app says so.
//
// Drives the REAL app against `lein e2e-server` on :8890.
//
// WHAT IT PINS: damage that no repair knows about. The probe makes the race converter
// throw for one entry, "Poison Race", which stands in for any bad data nobody
// anticipated. With that entry stored, the app must still draw, every other entry must
// still load, the entry must be set aside (in storage, so a reload keeps it aside) and a
// notice must name it and link to My Content. Restoring it from My Content, and
// importing it, must each catch it again without opening the builder. A second case
// hides the damage in a lazy part of the option, so it only throws while the page draws
// it: the failed page must check homebrew, set the entry aside and draw again.
//
// Before the safety net, a throw like this stopped the builder's options from building:
// the page showed "something went wrong", and a reload hit the same stored data again.
//
// Needs:     the real app at :8890 (lein fig:build, then lein e2e-server)
// Run:       node test/browser/homebrew_safety_net_e2e.js      exit 0 = all checks passed
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const fs = require('fs'), os = require('os'), path = require('path');

const BASE = process.env.ORCPUB_E2E_URL || 'http://localhost:8890';
const OUT = process.env.PROBE_OUT || fs.mkdtempSync(path.join(os.tmpdir(), 'safety-net-'));
const race = (key, name, src) => `:${key} {:key :${key} :option-pack "${src}" :name "${name}" :speed 30}`;
const PAK = `{"Net Pak" {:orcpub.dnd.e5/races {${race('poison-race', 'Poison Race', 'Net Pak')} ${race('fine-race', 'Fine Race', 'Net Pak')}}}}`;

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass: !!pass });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + String(detail).slice(0, 200) + ']' : ''));
}

// Runs in the page before the bundle: wrap the race converter as soon as it exists.
function lazyPoison() {
  const arm = () => {
    const o = window.orcpub && orcpub.dnd && orcpub.dnd.e5 && orcpub.dnd.e5.options;
    if (!o || typeof o.race_option !== 'function') return setTimeout(arm, 5);
    if (o.race_option.__lazy) return;
    const real = o.race_option, c = cljs.core;
    const wrapped = function (...args) {
      const opt = real.apply(this, args);
      const r = args[args.length - 1];
      if (r && c.get(r, c.keyword('name')) !== 'Lazy Poison Race') return opt;
      return c.assoc(opt, c.keyword('orcpub.template', 'name'),
                     c.map(() => { throw new Error('lazy damage (probe)'); }, c.vector(1)));
    };
    wrapped.__lazy = true;
    o.race_option = wrapped;
  };
  arm();
}

function poison() {
  const arm = () => {
    const o = window.orcpub && orcpub.dnd && orcpub.dnd.e5 && orcpub.dnd.e5.options;
    if (!o || typeof o.race_option !== 'function') return setTimeout(arm, 5);
    if (o.race_option.__poisoned) return;
    const real = o.race_option;
    const wrapped = function (...args) {
      const r = args[args.length - 1];
      if (r && cljs.core.get(r, cljs.core.keyword('name')) === 'Poison Race') throw new Error('unknown damage (probe)');
      return real.apply(this, args);
    };
    wrapped.__poisoned = true;
    o.race_option = wrapped;
  };
  arm();
}

// True once the app has drawn; false (not a throw) when it never does, so a page stuck
// on the spinner or the error fallback is a failed check rather than a crashed probe.
const appReady = (page, ms = 45000) => page.waitForFunction(() => {
  try { return !!document.querySelector('.app-header-bar') && cljs.core.count(cljs.core.deref(re_frame.db.app_db)) > 0; }
  catch (e) { return false; }
}, null, { timeout: ms }).then(() => true, () => false);
const shows = page => page.evaluate(() => document.body.innerText.replace(/\s+/g, ' ').slice(0, 160)).catch(() => '');

const stored = (page, key) => page.evaluate(k => localStorage.getItem(k), key);
const noticeText = page => page.evaluate(() => [...document.querySelectorAll('.message')].map(m => m.innerText).join(' | '));
async function waitForNotice(page, text, ms = 6000) {
  const until = Date.now() + ms;
  while (Date.now() < until) {
    if ((await noticeText(page)).includes(text)) return true;
    await page.waitForTimeout(250);
  }
  return false;
}
const dispatch = (page, edn, ...strings) => page.evaluate(([e, xs]) => {
  let ev = cljs.reader.read_string(e);
  for (const x of xs) ev = cljs.core.conj(ev, x);
  re_frame.core.dispatch_sync(ev);
}, [edn, strings]);

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await ctx.addInitScript(pak => {
    try {
      if (!localStorage.getItem('probe:seeded')) { localStorage.setItem('plugins', pak); localStorage.setItem('probe:seeded', '1'); }
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', '"summer-patch-2026"');
    } catch (e) {}
  }, PAK);
  await ctx.addInitScript(poison);
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message.split('\n')[0]));

  // ── The builder, with the broken entry stored ───────────────────────────────
  await page.goto(BASE + '/pages/dnd/5e/character-builder', { waitUntil: 'domcontentloaded' });
  const drew = await appReady(page);
  const noticed = await waitForNotice(page, 'Poison Race', 10000);
  check('the app draws with the broken entry stored', drew && await page.evaluate(() => !!document.querySelector('.app-header-bar')),
        drew ? '' : 'page shows: ' + await shows(page));
  check('a notice names the entry that was set aside', noticed, await noticeText(page));
  await page.screenshot({ path: path.join(OUT, '1-builder-notice.png') });
  check('the rest of the source still loads in the builder',
        (await page.evaluate(() => document.body.innerText)).includes('Fine Race'));
  check('the entry is out of the stored library', !((await stored(page, 'plugins')) || '').includes('poison-race'));
  check('and in the stored set-aside copy', ((await stored(page, 'plugins:rejected')) || '').includes('poison-race'));
  check('no page error escaped', errors.length === 0, errors[0]);

  // ── The notice's link, and My Content ───────────────────────────────────────
  const link = page.locator('.message').getByText('Fix or discard it in My Content');
  if (await link.count()) await link.first().click({ timeout: 3000 }).catch(() => {});
  else await page.goto(BASE + '/dnd/5e/my-content', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1500);
  check('the notice links to My Content, which lists the entry',
        page.url().includes('my-content') && (await page.evaluate(() => document.body.innerText)).match(/Poison Race|poison-race/),
        page.url());
  await page.screenshot({ path: path.join(OUT, '2-my-content.png') });

  // ── A reload has nothing left to set aside ──────────────────────────────────
  await page.reload({ waitUntil: 'domcontentloaded' });
  await appReady(page, 20000);
  check('after a reload there is nothing to set aside again', !(await waitForNotice(page, 'Poison Race', 3000)));
  check('and the entry is still set aside', ((await stored(page, 'plugins:rejected')) || '').includes('poison-race'));

  // ── Restore puts it back into the same check ────────────────────────────────
  await dispatch(page, '[:orcpub.dnd.e5/repair-quarantined-source "Net Pak" {} false]');
  check('restoring it from My Content catches it again', await waitForNotice(page, 'Poison Race', 6000), await noticeText(page));
  check('and sets it aside again', ((await stored(page, 'plugins:rejected')) || '').includes('poison-race'));
  await dispatch(page, '[:hide-message]');

  // ── An import catches it without opening the builder ────────────────────────
  await dispatch(page, '[:orcpub.dnd.e5/import-plugin "Net Pak Two"]',
    `{"Net Pak Two" {:orcpub.dnd.e5/races {${race('poison-two', 'Poison Race', 'Net Pak Two')}}}}`);
  check('an import catches it on My Content', await waitForNotice(page, 'Poison Race', 6000), await noticeText(page));
  check('and sets it aside', ((await stored(page, 'plugins:rejected')) || '').includes('poison-two'));
  await page.screenshot({ path: path.join(OUT, '3-import-notice.png') });

  // ── Damage that only throws while the page draws it ─────────────────────────
  {
    const LAZY = `{"Lazy Pak" {:orcpub.dnd.e5/races {${race('lazy-race', 'Lazy Poison Race', 'Lazy Pak')}}}}`;
    const ctx2 = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    await ctx2.addInitScript(pak => {
      try {
        if (!localStorage.getItem('probe:seeded')) { localStorage.setItem('plugins', pak); localStorage.setItem('probe:seeded', '1'); }
        localStorage.setItem('orcpub:no-cookie-banner', '1');
        localStorage.setItem('whats-new-seen', '"summer-patch-2026"');
      } catch (e) {}
    }, LAZY);
    await ctx2.addInitScript(lazyPoison);
    const p2 = await ctx2.newPage();
    await p2.goto(BASE + '/pages/dnd/5e/character-builder', { waitUntil: 'domcontentloaded' });
    await appReady(p2);
    const named = await waitForNotice(p2, 'Lazy Poison Race', 15000);
    await p2.waitForTimeout(1500);
    const body = await p2.evaluate(() => document.body.innerText);
    check('a page that breaks while drawing homebrew recovers on its own',
          !body.includes('Something went wrong on this page') && body.includes('Race'), body.slice(0, 120));
    check('the notice names that entry', named, await noticeText(p2));
    check('and it is set aside in storage', ((await stored(p2, 'plugins:rejected')) || '').includes('lazy-race'));
    await p2.screenshot({ path: path.join(OUT, '4-lazy-recovered.png') });
    await ctx2.close();
  }

  await browser.close();
  const failed = results.filter(r => !r.pass);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  console.log(`screenshots: ${OUT}`);
  process.exit(failed.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
