// Does the shipped form actually look like the approved mockup?
//
// The gallery doc claims `styles/core.clj` implements `docs/kb/assets/builder-form-mockup.html`.
// Nothing enforced that, so it drifted — the claim was made by eye and was wrong. This renders
// BOTH, reads the computed style of each corresponding element, and prints the differences.
//
// It is a REPORT, not a pass/fail gate on every property: some divergence is correct, and saying
// which is the design work. Two categories are excluded by name below rather than silently:
//
//   - mockup scaffolding — `.panel`, `body`, the two-column `.cols`. The mockup is a standalone
//     page that had to draw its own idea of "the form area"; the real form area is the app page.
//     Importing the panel would make this one builder unlike every other builder in the app.
//   - app chrome — input/select background and border come from the app's own widget styles. The
//     mockup approximated them; where they differ the APP is right, not the mockup.
//
// A worked example of the third case, "the mockup is wrong": it reports the bonus input at
// font-weight 400 though `.num` asks for 700, because its own `input{font:inherit}` shorthand has
// higher specificity and resets it. The app renders 700 — what the mockup MEANT. Do not "fix" the
// app to match a mockup bug; fix the mockup or note it, as here.
//
// Prereqs:  lein garden once && lein fig:build && lein e2e-server
// Run:      node test/e2e/mockup-parity.js
const fs = require('fs');
const http = require('http');
const path = require('path');
const { chromium } = require('playwright');
const { BASE, findChrome, dismissCookieBar, fillEffectBonus } = require('./lib');

const MOCKUP = path.resolve(__dirname, '../../docs/kb/assets/builder-form-mockup.html');

// mockup selector -> app selector, and which properties are design rather than chrome
const PAIRS = [
  ['group box',    '.grp',            '.effect-row',           ['borderTopWidth', 'borderTopColor', 'borderRadius']],
  ['group header', '.grp > header',   '.effect-row-header',    ['backgroundColor', 'paddingTop', 'paddingLeft', 'borderBottomColor', 'borderBottomWidth']],
  ['group title',  '.grp > header b', '.effect-row-header span', ['color', 'fontSize', 'letterSpacing', 'textTransform']],
  ['add chip',     '.chip',           '.chip',                 ['borderStyle', 'borderColor', 'color', 'borderRadius', 'fontSize', 'fontWeight']],
  ['sub-heading',  '.whenlbl',        '.when-label',           ['color', 'fontSize', 'textTransform', 'letterSpacing']],
  ['tag label',    '.tag span',       '.tag-label',            ['fontSize', 'color', 'fontWeight']],
  ['tag select',   '.tag select',     '.tag select',           ['minWidth', 'fontSize', 'paddingTop', 'paddingLeft']],
  ['set tag',      '.tag select.set', '.tag select.set',       ['borderTopColor', 'color']],
  ['bonus input',  '.num',            '.row-lead-num input',   ['width', 'textAlign', 'fontWeight']],
];

const styleOf = (page, sel, props) => page.evaluate(({ s, p }) => {
  const el = document.querySelector(s);
  if (!el) return null;
  const cs = getComputedStyle(el);
  const out = {};
  p.forEach(k => { out[k] = cs[k]; });
  return out;
}, { s: sel, p: props });

(async () => {
  // serve the mockup so it renders with the same font stack resolution as a real page
  const srv = http.createServer((_q, r) => {
    r.writeHead(200, { 'Content-Type': 'text/html' });
    r.end(fs.readFileSync(MOCKUP));
  });
  await new Promise(r => srv.listen(0, r));
  const mockUrl = `http://localhost:${srv.address().port}/`;

  const browser = await chromium.launch({ executablePath: findChrome() });
  const mock = await browser.newPage({ viewport: { width: 1100, height: 1400 } });
  await mock.goto(mockUrl, { waitUntil: 'load' });

  const app = await browser.newPage({ viewport: { width: 1100, height: 1400 } });
  await app.goto(`${BASE}/pages/dnd/5e/fighting-style-builder`, { waitUntil: 'networkidle' });
  await app.waitForTimeout(1800);
  await dismissCookieBar(app);
  // the app's rows only exist once added, and `.set` only once a tag carries a value
  for (const t of ['AC Bonus', 'Attack Bonus']) {
    await app.evaluate((title) => {
      const b = [...document.querySelectorAll('button')].find(e => e.textContent.trim() === `+ ${title}`);
      if (b) b.click();
    }, t);
    await app.waitForTimeout(250);
  }
  for (const [l, v] of [['AC Bonus', '1'], ['Attack Bonus', '2']]) await fillEffectBonus(app, l, v);
  await app.evaluate(() => {
    const lbl = [...document.querySelectorAll('.f-w-b')].find(e => e.textContent.trim() === 'Ranged');
    const sel = lbl && lbl.parentElement.querySelector('select');
    if (!sel) return;
    const opt = [...sel.options].find(o => /^ranged (weapons )?only$/i.test(o.textContent.trim()));
    if (!opt) return;
    const set = Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, 'value').set;
    set.call(sel, opt.value);
    sel.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await app.waitForTimeout(500);

  let diffs = 0, missing = 0;
  for (const [name, mSel, aSel, props] of PAIRS) {
    const m = await styleOf(mock, mSel, props);
    const a = await styleOf(app, aSel, props);
    if (!m || !a) {
      missing++;
      console.log(`MISSING  ${name.padEnd(13)} ${!m ? 'mockup ' + mSel : 'app ' + aSel} not found`);
      continue;
    }
    const bad = props.filter(k => m[k] !== a[k]);
    if (!bad.length) { console.log(`ok       ${name}`); continue; }
    diffs += bad.length;
    console.log(`DIFF     ${name}`);
    bad.forEach(k => console.log(`           ${k.padEnd(16)} mockup ${String(m[k]).padEnd(24)} app ${a[k]}`));
  }
  await browser.close();
  srv.close();
  console.log(`\n${diffs} property differences, ${missing} elements not found`);
})().catch(e => { console.error(e); process.exit(2); });
