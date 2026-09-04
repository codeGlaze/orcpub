// Round-trip e2e: author a fighting style and a draconic ancestry in the REAL UI, save them,
// export a real .orcbrew download, and re-import it into a fresh browser context — asserting the
// :props and the numeric fields survive.
//
// This is the question the JVM suite cannot answer. render + compile were already covered; what
// was NOT covered is whether an authored item SAVES, EXPORTS and IMPORTS with its mechanics intact.
// Draconic ancestry is included because its number fields shared the render-builder-field
// double-parse bug, so it is the natural regression case for that fix.
//
// Prereqs:  lein fig:build && lein e2e-server   (port 8890)
// Run:      node test/browser/homebrew_roundtrip_e2e.js
const fs = require('fs');
const path = require('path');
const os = require('os');
const { chromium } = require('playwright');

const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const SHOTS = path.resolve(__dirname, '../../target/e2e-shots');

function findChrome() {
  const b = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  try {
    const d = fs.readdirSync(b).filter(x => x.startsWith('chromium-') && !x.includes('headless')).sort().pop();
    if (d) { const p = path.join(b, d, 'chrome-linux', 'chrome'); if (fs.existsSync(p)) return p; }
  } catch (_) {}
}
const results = [];
const check = (n, ok, d='') => { results.push({n, ok}); console.log(`${ok?'PASS':'FAIL'}  ${n}${d?'  — '+d:''}`); };

// READ app-db for assertions only. Driving is done through the real UI below.
const dbAt = (page, p) => page.evaluate((pp) => {
  try {
    const v = window.cljs.core.get_in.call(null, window.cljs.core.deref.call(null, window.re_frame.db.app_db),
              window.cljs.reader.read_string.call(null, pp));
    return window.cljs.core.pr_str.call(null, v);
  } catch (e) { return 'ERR ' + e.message; }
}, p);

// Find a control by the label above it. Matches on a PREFIX of the bolded label, because several
// labels carry nested markup — "Option Source Name" is followed by an italic <span> of examples, so
// an exact-text match on a childless element finds nothing.
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
  await page.waitForTimeout(250);
  return true;
}
async function clickText(page, re) {
  // Visible only. With the real stylesheet loaded, several matches are hidden (collapsed rows,
  // off-screen nav) and clicking one hangs until timeout.
  const btn = await page.evaluateHandle((src) => {
    const rx = new RegExp(src, 'i');
    const visible = e => {
      const r = e.getBoundingClientRect();
      const st = getComputedStyle(e);
      return r.width > 0 && r.height > 0 && st.visibility !== 'hidden' && st.display !== 'none';
    };
    return [...document.querySelectorAll('button,a,div,span')]
      .filter(e => e.children.length <= 2 && rx.test(e.textContent.trim()) && visible(e))
      .sort((a,b) => a.textContent.length - b.textContent.length)[0] || null;
  }, re.source);
  const el = btn.asElement();
  if (!el) return false;
  await el.click();
  await page.waitForTimeout(500);
  return true;
}

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const browser = await chromium.launch({ executablePath: findChrome() });
  // downloads must be accepted explicitly for waitForEvent('download') to resolve
  const ctx = await browser.newContext({ acceptDownloads: true, viewport: { width: 1400, height: 1200 } });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));
  page.on('console', m => { if (m.type()==='error' && !/ERR_(CONNECTION|NAME|INTERNET)/.test(m.text())) errors.push(m.text()); });

  try {
    // ── 1. author a fighting style with real mechanics ───────────────────────────────────────
    await page.goto(`${BASE}/pages/dnd/5e/fighting-style-builder`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(2500);
    await fill(page, 'Name', 'Bulwark');
    check('found the Option Source field', await fill(page, 'Option Source Name', 'Roundtrip Source'));
    await fill(page, 'AC Bonus', '1');
    await fill(page, 'Attack Bonus', '2');
    const armorSel = await controlFor(page, 'Armor requirement');
    if (armorSel) { await armorSel.selectOption({ label: 'Only while wearing armor' }); await page.waitForTimeout(300); }
    await page.screenshot({ path: path.join(SHOTS, 'rt-style-filled.png'), fullPage: true });

    const draft = await dbAt(page, '[:orcpub.dnd.e5.classes/fighting-style-builder-item]');
    check('typed mechanics reach the builder draft', /:ac-bonus/.test(draft) && /:attack-bonus/.test(draft), draft.slice(0,150));

    check('clicked SAVE', await clickText(page, /save to browser storage/i));
    await page.waitForTimeout(700);
    const savedFs = await dbAt(page, '[:plugins "Roundtrip Source" :orcpub.dnd.e5/fighting-styles]');
    check('fighting style saves into :plugins WITH its props',
      /bulwark/i.test(savedFs) && /:ac-bonus/.test(savedFs) && /:attack-bonus/.test(savedFs), savedFs.slice(0,180));

    // ── 2. author a draconic ancestry (the :number regression case) ──────────────────────────
    await page.goto(`${BASE}/pages/dnd/5e/draconic-ancestry-builder`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);
    await fill(page, 'Name', 'Frost Wyrm');
    await fill(page, 'Option Source Name', 'Roundtrip Source');
    const dmg = await controlFor(page, 'Breath Weapon Damage Type');
    if (dmg) { await dmg.selectOption({ index: 1 }); await page.waitForTimeout(300); }
    const shape = await controlFor(page, 'Breath Weapon Shape');
    if (shape) { await shape.selectOption({ label: 'Line' }); await page.waitForTimeout(400); }
    const wrote = await fill(page, 'Line Width (ft.)', '5');
    check('draconic NUMBER field accepts input (the ISeqable regression)', wrote);
    const save = await controlFor(page, 'Breath Weapon Save');
    if (save) { await save.selectOption({ index: 1 }); await page.waitForTimeout(300); }
    const dDraft = await dbAt(page, '[:orcpub.dnd.e5.races/draconic-ancestry-builder-item]');
    check('the typed NUMBER reaches the draft (was silently dropped before the fix)',
      /:line-width 5/.test(dDraft), dDraft.slice(0,170));
    await page.screenshot({ path: path.join(SHOTS, 'rt-draconic-filled.png'), fullPage: true });

    await clickText(page, /save to browser storage/i);
    await page.waitForTimeout(700);
    const savedDa = await dbAt(page, '[:plugins "Roundtrip Source" :orcpub.dnd.e5/draconic-ancestries]');
    check('draconic ancestry saves WITH its numeric breath weapon',
      /frost-wyrm/i.test(savedDa) && /:line-width 5/.test(savedDa), savedDa.slice(0,190));

    // ── 3. export a real .orcbrew download ──────────────────────────────────────────────────
    // NOTE: My Content is NOT under /pages/ like the builders are — it hangs off the root tree.
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    // My Content's visible controls are "Move / copy", "Export All", "Delete…" — the per-source
    // expand/export in views.cljs:8817 is not what this page renders.
    const [dl] = await Promise.all([
      page.waitForEvent('download', { timeout: 15000 }).catch(() => null),
      clickText(page, /^export all$/i),
    ]);
    let fileText = null, dlPath = null;
    if (dl) { dlPath = path.join(os.tmpdir(), 'roundtrip.orcbrew'); await dl.saveAs(dlPath); fileText = fs.readFileSync(dlPath, 'utf8'); }
    check('export produces a real .orcbrew download', !!fileText, fileText ? `${fileText.length} bytes` : 'no download captured');
    if (fileText) {
      check('the exported file carries the fighting style AND its props',
        /bulwark/i.test(fileText) && /:ac-bonus/.test(fileText) && /:attack-bonus/.test(fileText));
      check('the exported file carries the draconic ancestry AND its number',
        /frost-wyrm/i.test(fileText) && /:line-width 5/.test(fileText));
    }

    // ── 4. re-import into a clean library via the REAL file input ───────────────────────────
    if (dlPath) {
      const ctx2 = await browser.newContext({ acceptDownloads: true });
      const p2 = await ctx2.newPage();
      await p2.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'networkidle' });
      await p2.waitForTimeout(2000);
      const before = await dbAt(p2, '[:plugins]');
      const fileInput = await p2.$('input[type=file]');
      check('My Content exposes a real file input for import', !!fileInput);
      if (fileInput) {
        await fileInput.setInputFiles(dlPath);
        await p2.waitForTimeout(2500);
        const after = await dbAt(p2, '[:plugins]');
        check('re-import restores the fighting style with its props',
          !/bulwark/i.test(before) && /bulwark/i.test(after) && /:ac-bonus/.test(after) && /:attack-bonus/.test(after),
          `before had it: ${/bulwark/i.test(before)}`);
        check('re-import restores the draconic ancestry with its number',
          /frost-wyrm/i.test(after) && /:line-width 5/.test(after));
        await p2.screenshot({ path: path.join(SHOTS, 'rt-after-import.png'), fullPage: true });
      }
      await ctx2.close();
    }
    check('no uncaught JS errors during the whole flow', errors.length === 0, errors.slice(0,3).join(' | '));
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  const failed = results.filter(r => !r.ok);
  console.log(`\nscreenshots: ${SHOTS}`);
  console.log(`${results.length - failed.length}/${results.length} checks passed`);
  process.exit(failed.length ? 1 : 0);
})();
