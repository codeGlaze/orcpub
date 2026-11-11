// Probe (kept on agents/develop, scripts/research/e2e-probes): does re-importing a pack update it in place or add renamed copies?
// Run: copy into a code branch's scripts/e2e/ (it uses that folder's helpers and test/fixtures), then E2E_BASE=http://localhost:<port> node scripts/e2e/probe-reimport.js against a running dev server.
// Needs: dev bundle
// Kind: probe
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const PACKS = '/tmp/claude-1000/-home-codeglaze-projects-orcpub/d68857f5-0f1c-42fe-ad8b-0f0ddcce8a28/scratchpad/reimport';
const log = (...a) => console.log(...a);
function findChrome() {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  try {
    const dir = fs.readdirSync(base).filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
    const p = dir && path.join(base, dir, 'chrome-linux', 'chrome');
    if (p && fs.existsSync(p)) return p;
  } catch (_) {}
  return undefined;
}
// Counts in the stored library, the app's library and My Content; and every entry whose name
// appears under more than one key in the same source and type.
async function snapshot(page, label) {
  const ready = await page.waitForFunction(() => window.cljs && window.cljs.core && window.re_frame && window.cljs.reader,
                                           null, { timeout: 90000 }).then(() => true, () => false);
  if (!ready) {
    log(`\n[${label}] the app never exposed its internals: title "${await page.title()}", url ${page.url()}`);
    await page.screenshot({ path: '/tmp/claude-1000/-home-codeglaze-projects-orcpub/d68857f5-0f1c-42fe-ad8b-0f0ddcce8a28/scratchpad/dups-notready.png' });
    throw new Error('app not ready');
  }
  const r = await page.evaluate(() => {
    const c = window.cljs.core;
    const kw = n => c.keyword(n);
    const summarise = lib => {
      if (!lib) return { total: null };
      const out = { total: 0, byType: {}, dups: [], keys: {} };
      for (const src of c.clj__GT_js(c.keys(lib)) || []) {
        const content = c.get(lib, src);
        if (!c.map_QMARK_(content)) continue;
        c.doall(c.map(t => {
          const items = c.get(content, t);
          if (!c.map_QMARK_(items)) return null;
          const tn = c.name(t);
          const byName = {};
          c.doall(c.map(k => {
            const it = c.get(items, k);
            const nm = c.map_QMARK_(it) ? String(c.get(it, kw('name'))) : '?';
            (byName[nm] = byName[nm] || []).push(c.str(k));
            (out.keys[tn] = out.keys[tn] || []).push(src + c.str(k));
            out.total++;
            out.byType[tn] = (out.byType[tn] || 0) + 1;
            return null;
          }, c.keys(items)));
          for (const [nm, ks] of Object.entries(byName)) if (ks.length > 1) out.dups.push(`${src} / ${tn} / "${nm}": ${ks.join(' ')}`);
          return null;
        }, c.keys(content)));
      }
      return out;
    };
    let stored = null;
    try { stored = window.cljs.reader.read_string(localStorage.getItem('plugins') || '{}'); } catch (e) { stored = null; }
    const db = c.get(c.deref(window.re_frame.db.app_db), kw('plugins'));
    const editButtons = Array.from(document.querySelectorAll('button')).filter(b => b.textContent.trim() === 'edit').length;
    return { stored: summarise(stored), app: summarise(db), editButtons, path: location.pathname };
  });
  const s = r.stored, a = r.app;
  log(`\n[${label}] at ${r.path}`);
  log(`  stored total ${s.total}  app total ${a.total}  My Content edit buttons ${r.editButtons}`);
  log(`  stored by type ${JSON.stringify(s.byType)}`);
  if (JSON.stringify(s.byType) !== JSON.stringify(a.byType)) log(`  app by type    ${JSON.stringify(a.byType)}`);
  log(`  stored keys ${JSON.stringify(s.keys)}`);
  log(`  duplicate names: stored ${s.dups.length ? s.dups.join(' | ') : 'none'}; app ${a.dups.length ? a.dups.join(' | ') : 'none'}`);
  return r;
}

async function settle(page, ms = 1500) {
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(ms);
}

// Imports `file` through My Content's Import, takes each default the import offers, and logs what it said.
async function importFile(page, file) {
  await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'networkidle', timeout: 120000 });
  await settle(page);
  await page.setInputFiles('input[type=file]', path.join(PACKS, file));
  await page.waitForTimeout(2500);
  for (let round = 0; round < 4; round++) {
    const said = await page.evaluate(() => (document.body.innerText.match(/(duplicate key|renamed|already exist|turned off|harmless duplicate|conflict|replace|update)[^\n]{0,140}/gi) || []).slice(0, 6));
    if (said.length) log(`  import ${file} says: ${said.join(' | ')}`);
    let clicked = null;
    for (const label of ['Import with default fixes', 'Import with these fixes', 'Apply & Import', 'Apply', 'Import', 'Confirm', 'OK', 'Done', 'Close']) {
      const b = page.locator('button', { hasText: new RegExp('^' + label.replace(/[&]/g, '\\$&') + '$') }).last();
      if (await b.isVisible().catch(() => false)) { await b.click().catch(() => {}); clicked = label; break; }
    }
    if (!clicked) break;
    log(`  import ${file}: pressed "${clicked}"`);
    await page.waitForTimeout(2000);
  }
  await settle(page);
}

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.E2E_CHROMIUM || findChrome() });
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await ctx.addInitScript(() => {
    try {
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', JSON.stringify('long-rest-update-2026'));
    } catch (e) {}
  });
  const page = await ctx.newPage();
  page.on('pageerror', e => log(`  pageerror: ${e.message.slice(0, 200)}`));
  for (const [file, label] of [['A.orcbrew', 'import A'], ['A.orcbrew', 'import A again (same)'],
                               ['A2.orcbrew', 'import A2 (Alchemist text edited)'],
                               ['B.orcbrew', 'import B (same keys, another source)']]) {
    await importFile(page, file);
    await snapshot(page, label);
  }
  await page.reload({ waitUntil: 'networkidle' }); await settle(page);
  await snapshot(page, 'after reload');
  await browser.close();
  log('\nPROBE DONE');
})().catch(e => { console.error('PROBE THREW', e); process.exit(1); });
