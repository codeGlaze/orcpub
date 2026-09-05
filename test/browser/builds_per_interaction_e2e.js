// How many times does entity/build actually run for ONE click, in the REAL app?
//
// WHY THIS EXISTS: built_character_debounce_test.cljs pins the same thing in a synthetic
// harness — two live reactions over a shared source. That harness is a model, and a model
// can be wrong about the app: in production `built-template` returns the SAME template
// object every time (its body is commented out; it ignores selected-plugin-options), so a
// mechanism the harness demonstrates need not be the mechanism the app exhibits. This probe
// settles it against the live backend, real homebrew and real clicks.
//
// Measured on the pre-fix dev build with MegaPak loaded: a single race click ran
// entity/build TWICE. The build gaps printed here say which cause it was — a few ms apart
// is same-tick fan-in from the two watches; ~500 ms apart is the debounce's leading and
// trailing edges firing for two separate character changes.
//
// Instrumentation note: this wraps orcpub.dnd.e5.subs.built_character, which works only
// because debounced-build-sub calls it through the namespace var. Wrapping a function that
// a caller captured at definition time silently intercepts nothing — that mistake has been
// made in this repo before and reported a confident wrong number.
//
// Run: lein fig:build && lein e2e-server, then
//   node test/browser/builds_per_interaction_e2e.js /path/to/pack.orcbrew
const { chromium } = require('playwright');
const { importPack, suppressCookieBanner } = require('./lib/orcbrew-import.js');

const PACK = process.argv[2];

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || undefined });
  const ctx = await browser.newContext();
  await suppressCookieBanner(ctx);
  const page = await ctx.newPage();
  page.on('pageerror', e => console.log('PAGEERROR', e.message));

  await page.goto('http://localhost:8890/dnd/5e/my-content',
                  { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(2500);
  console.log('import:', JSON.stringify(await importPack(page, PACK)));

  await page.goto('http://localhost:8890/pages/dnd/5e/character-builder',
                  { waitUntil: 'load', timeout: 900000 });
  await page.waitForTimeout(12000);

  const wrapped = await page.evaluate(() => {
    const ns = window.orcpub && window.orcpub.dnd && window.orcpub.dnd.e5 && window.orcpub.dnd.e5.subs;
    if (!ns || typeof ns.built_character !== 'function') return false;
    window.__builds = 0; window.__at = []; window.__ctors = 0; window.__stacks = [];
    const orig = ns.built_character;
    ns.built_character = function (c, t) {
      window.__builds++; window.__at.push(Math.round(performance.now()));
      // WHO called? Guessing the caller from reading the code failed three times
      // (fan-in, sub churn, a direct call on another route); the stack is the
      // only thing that actually answers it.
      window.__stacks.push((new Error()).stack.split('\n').slice(1, 9).join('\n'));
      return orig.call(this, c, t);
    };
    // Constructing the sub builds ONCE in its own let, bypassing the debounce.
    // If the sub is disposed and re-created during a re-render, that is a build
    // no amount of debouncing or coalescing can prevent.
    const ctor = ns.debounced_build_sub;
    if (typeof ctor === 'function') {
      ns.debounced_build_sub = function (c, t) { window.__ctors++; return ctor.call(this, c, t); };
    }
    return true;
  });
  console.log('instrumented:', wrapped);
  if (!wrapped) { await browser.close(); process.exit(1); }

  const click = (t) => page.locator(`text="${t}"`).first().click({ timeout: 25000 });
  const round = async (label, fn) => {
    await page.evaluate(() => { window.__builds = 0; window.__at = []; window.__ctors = 0; window.__stacks = []; });
    try { await fn(); } catch (e) { console.log(label.padEnd(28), 'click failed:', e.message.split('\n')[0]); return; }
    await page.waitForTimeout(1500);            // past the 500 ms debounce
    const r = await page.evaluate(() => ({ n: window.__builds, at: window.__at, c: window.__ctors, st: window.__stacks }));
    const gaps = r.at.slice(1).map((t, i) => t - r.at[i]);
    console.log(label.padEnd(28), 'builds =', r.n, ' subs re-created =', r.c,
                gaps.length ? '  gaps(ms) = ' + gaps.join(',') : '');
    if (process.env.STACKS) r.st.forEach((st, i) => console.log(`--- build ${i + 1} ---\n${st}`));
  };

  await round('race Half-Orc', () => click('Half-Orc'));
  await round('tab Class / Level', () => click('Class / Level'));
  await page.waitForTimeout(2000);
  for (const c of ['Wizard', 'Cleric', 'Druid']) await round('class ' + c, () => click(c));

  await browser.close();
})().catch(e => { console.error('FAILED', e); process.exit(1); });
