// Shared helpers for the full-app E2E scripts. Extracted because every script had re-typed them
// and they had already drifted (three different label matchers, two of which miss any label with
// nested markup). New scripts should require this; the older ones can adopt it as they're touched.
const fs = require('fs');
const path = require('path');

const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const SHOTS = path.resolve(__dirname, '../../target/e2e-shots');

// Playwright's bundled Chromium is not downloaded in this container; use the preinstalled one.
function findChrome() {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  try {
    const dir = fs.readdirSync(base)
      .filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
    if (dir) {
      const p = path.join(base, dir, 'chrome-linux', 'chrome');
      if (fs.existsSync(p)) return p;
    }
  } catch (_) {}
  return undefined;                    // fall back to Playwright's own resolution
}

// A tiny check recorder: `const {check, report} = checker()`.
function checker() {
  const results = [];
  const check = (name, ok, detail = '') => {
    results.push({ name, ok: !!ok });
    console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`);
    return !!ok;
  };
  const report = () => {
    const failed = results.filter(r => !r.ok);
    console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
    return failed.length;
  };
  return { check, report, results };
}

// READ app-db for assertions only — driving must go through the real UI, or the test proves
// nothing about the UI (see CLAUDE.md).
const dbAt = (page, p) => page.evaluate((pp) => {
  try {
    const v = window.cljs.core.get_in.call(
      null,
      window.cljs.core.deref.call(null, window.re_frame.db.app_db),
      window.cljs.reader.read_string.call(null, pp));
    return window.cljs.core.pr_str.call(null, v);
  } catch (e) { return 'ERR ' + e.message; }
}, p);

// Find a control by the bolded label above it. PREFIX match: several labels carry nested markup
// ("Option Source Name" is followed by an italic <span> of examples), so an exact-text match on a
// childless element finds nothing.
async function controlFor(page, labelPrefix) {
  const h = await page.evaluateHandle((pfx) => {
    const norm = t => t.replace(/\s+/g, ' ').trim().toLowerCase();
    const want = norm(pfx);
    // .select-menu-btn is an :enum now — a button+popover, not a <select>. Included here so a
    // "field present" check keeps meaning the same thing across the representation change.
    for (const c of document.querySelectorAll('input, select, textarea, .select-menu-btn')) {
      let n = c;
      for (let k = 0; k < 6 && n; k++, n = n.parentElement) {
        const d = n.querySelector('.f-w-b');
        if (d && d.textContent.trim()) {
          if (norm(d.textContent).startsWith(want)) return c;
          break;                       // this control's label is not the one we want
        }
      }
    }
    return null;
  }, labelPrefix);
  return h.asElement();
}

async function fill(page, label, value) {
  const c = await controlFor(page, label);
  if (!c) return false;
  await c.fill(String(value));
  await c.dispatchEvent('change');
  await page.waitForTimeout(250);
  return true;
}

// Click the shortest VISIBLE element whose text matches. Visibility matters: once the real
// stylesheet is loaded several matches are hidden (collapsed rows, off-screen nav) and clicking
// one hangs until timeout.
async function clickText(page, re) {
  const btn = await page.evaluateHandle((src) => {
    const rx = new RegExp(src, 'i');
    const visible = e => {
      const r = e.getBoundingClientRect();
      const st = getComputedStyle(e);
      return r.width > 0 && r.height > 0 && st.visibility !== 'hidden' && st.display !== 'none';
    };
    return [...document.querySelectorAll('button,a,div,span')]
      .filter(e => e.children.length <= 2 && rx.test(e.textContent.trim()) && visible(e))
      .sort((a, b) => a.textContent.length - b.textContent.length)[0] || null;
  }, re.source);
  const el = btn.asElement();
  if (!el) return false;
  await el.click();
  await page.waitForTimeout(500);
  return true;
}

// Fill the lead number of one effect, in EITHER form shape. In the grouped form the control lives
// inside a titled row and is labelled just "Bonus"; in the flat form it is labelled with the effect
// name. Scripts that must run against both builds go through this.
async function fillEffectBonus(page, kindTitle, value) {
  const ok = await page.evaluate(({ t, v }) => {
    const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
    const hdr = [...document.querySelectorAll('span')]
      .filter(e => e.textContent.trim() === t && vis(e) && e.parentElement.querySelector('i.fa-times'))[0];
    if (!hdr) return false;                       // not the grouped form
    const row = hdr.parentElement.parentElement;
    const input = row.querySelector('input[type=number], input');
    if (!input) return false;
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
    setter.call(input, v);
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    return true;
  }, { t: kindTitle, v: String(value) });
  if (ok) { await page.waitForTimeout(400); return true; }
  return fill(page, kindTitle, value);           // flat form: the label IS the effect name
}

// The cookie consent bar is fixed to the bottom of the viewport and covers part of any full-page
// screenshot. Dismiss it before capturing documentation assets.
async function dismissCookieBar(page) {
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('a,button,div,span')]
      .find(e => e.children.length === 0 && /^got it!?$/i.test(e.textContent.trim()));
    if (b) b.click();
  });
  await page.waitForTimeout(400);
}

// Click a character-builder TAB by its exact name. Distinct from clickText: a tab's label also
// appears inside the panel it opens, and clickText's shortest-match would sometimes find that
// instead. Anchored regex, visible elements only.
//
// CASE-SENSITIVE, deliberately. The builder's tabs are Capitalised ("Spells"), while the sheet's
// own tabs on the right and the site header nav are lowercase ("spells"). A case-insensitive match
// picks the shortest — the header link — and silently navigates out of the character builder,
// after which every later check fails for the wrong reason. That cost a debugging pass.
async function clickTab(page, name) {
  const ok = await page.evaluate((n) => {
    const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
    const rx = new RegExp(`^${n}$`);
    const el = [...document.querySelectorAll('div,span,button')]
      .filter(e => e.children.length <= 2 && rx.test(e.textContent.trim().replace(/\s+/g, ' ')) && vis(e))
      .sort((a, b) => a.textContent.length - b.textContent.length)[0];
    if (!el) return false;
    el.click();
    return true;
  }, name);
  if (ok) await page.waitForTimeout(2000);
  return ok;
}

// Pick an option by its visible text from whichever <select> on the page offers it. The character
// builder's Class is a dropdown, not the clickable cards that races and fighting styles use, and
// it has no label a control-finder can anchor to.
async function pickFromAnySelect(page, rx) {
  for (const sel of await page.$$('select')) {
    const opts = await sel.evaluate(el => [...el.options].map(o => o.textContent.trim()));
    const want = opts.find(o => rx.test(o));
    if (want) { await sel.selectOption({ label: want }); await page.waitForTimeout(2000); return true; }
  }
  return false;
}

// Toggles are CHIPS: a button whose text is its label, carrying `chip-on` when set. Replaces the
// old glyph-checkbox reading (an <i class="fa-check"> whose colour classes encoded the state).
async function chipIsOn(page, label) {
  return page.evaluate((t) => {
    const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
    const b = [...document.querySelectorAll('.chip')].find(e => e.textContent.trim() === t && vis(e));
    if (!b) return null;
    return b.classList.contains('chip-on');
  }, label);
}

async function chipClick(page, label) {
  const ok = await page.evaluate((t) => {
    const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
    const b = [...document.querySelectorAll('.chip')].find(e => e.textContent.trim() === t && vis(e));
    if (!b) return false;
    b.click();
    return true;
  }, label);
  if (ok) await page.waitForTimeout(400);
  return ok;
}

// Choose an option from an :enum. It is a button+popover (ported OMV select-menu), so this opens
// the menu and clicks the option rather than setting a <select>'s value. Returns false if the
// field or the option is not found.
async function pickOption(page, label, rx) {
  const btn = await controlFor(page, label);
  if (!btn) return false;
  await btn.click();
  await page.waitForTimeout(250);
  const ok = await page.evaluate(({ src }) => {
    const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
    const opt = [...document.querySelectorAll('.select-menu-pop .select-menu-opt')]
      .filter(vis).find(e => new RegExp(src, 'i').test(e.textContent.trim()));
    if (!opt) return false;
    opt.click();
    return true;
  }, { src: rx.source });
  await page.waitForTimeout(300);
  return ok;
}

// What an :enum currently shows, and what it offers when opened.
async function optionsOf(page, label) {
  const btn = await controlFor(page, label);
  if (!btn) return null;
  const shown = (await btn.evaluate(e => e.textContent.trim()));
  await btn.click();
  await page.waitForTimeout(250);
  const opts = await page.evaluate(() => {
    const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
    return [...document.querySelectorAll('.select-menu-pop .select-menu-opt')]
      .filter(vis).map(e => e.textContent.trim());
  });
  await btn.click();                    // close it again
  await page.waitForTimeout(150);
  return { shown, options: opts };
}

module.exports = { BASE, SHOTS, findChrome, checker, dbAt, controlFor, fill, clickText, clickTab, fillEffectBonus, dismissCookieBar, pickFromAnySelect, chipIsOn, chipClick, pickOption, optionsOf };
