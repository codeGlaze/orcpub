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
    for (const c of document.querySelectorAll('input, select, textarea')) {
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

module.exports = { BASE, SHOTS, findChrome, checker, dbAt, controlFor, fill, clickText };
