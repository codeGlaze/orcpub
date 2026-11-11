// Probe (kept on agents/develop, scripts/research/e2e-probes): does loading, viewing or saving homebrew duplicate entries?
// Run: copy into a code branch's scripts/e2e/ (it uses that folder's helpers and test/fixtures), then E2E_BASE=http://localhost:<port> node scripts/e2e/probe-homebrew-dups.js against a running dev server.
// Needs: dev bundle
// Kind: probe
// Seeds a library needing cleanup (cruft-shapes, junk-bases, keyword-trap), then counts every entry in
// the stored library, the app's library and My Content after: load, reloads, Edit -> back (3 entries),
// Edit -> Save to Browser Storage -> back (3 entries), and a last reload.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const log = (...a) => console.log(...a);
const fx = f => fs.readFileSync(path.join(__dirname, '../../test/fixtures', f), 'utf8')
  .split('\n').filter(l => !/^\s*;;/.test(l)).join('\n').trim();
const inner = s => s.slice(s.indexOf('{') + 1, s.lastIndexOf('}'));
const LIBRARY = '{' + inner(fx('cruft-shapes.orcbrew')) + '\n' + inner(fx('junk-bases.orcbrew')) +
  '\n"Keyword Trap Pack" ' + fx('keyword-trap.orcbrew') + '}';

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
    await page.screenshot({ path: require('path').join(require('os').tmpdir(), 'dups-notready.png') });
    throw new Error('app not ready');
  }
  const r = await page.evaluate(() => {
    const c = window.cljs.core;
    const kw = n => c.keyword(n);
    const summarise = lib => {
      if (!lib) return { total: null };
      const out = { total: 0, byType: {}, dups: [] };
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
  log(`  duplicate names: stored ${s.dups.length ? s.dups.join(' | ') : 'none'}; app ${a.dups.length ? a.dups.join(' | ') : 'none'}`);
  return r;
}

async function settle(page, ms = 1500) {
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(ms);
}

async function expandAll(page) {
  for (let i = 0; i < 300; i++) {
    const e = page.locator('span.underline', { hasText: /^expand$/ }).first();
    if (!(await e.isVisible().catch(() => false))) break;
    await e.click();
    await page.waitForTimeout(150);
  }
}

async function myContent(page) {
  await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'networkidle', timeout: 120000 });
  await settle(page);
  await expandAll(page);
}

// Clicks the nth edit button on My Content; returns the entry's row text.
async function openEdit(page, n) {
  const btns = page.locator('button', { hasText: /^edit$/ });
  const count = await btns.count();
  if (!count) return null;
  const b = btns.nth(Math.min(n, count - 1));
  const row = await b.evaluate(el => (el.closest('div.flex') || el.parentElement).textContent.trim().slice(0, 60));
  await b.click();
  await settle(page);
  return row;
}

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.E2E_CHROMIUM || findChrome() });
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await ctx.addInitScript(lib => {
    try {
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', JSON.stringify('long-rest-update-2026'));
      if (!sessionStorage.getItem('seeded')) { localStorage.setItem('plugins', lib); sessionStorage.setItem('seeded', '1'); }
    } catch (e) {}
  }, LIBRARY);
  const page = await ctx.newPage();
  page.on('pageerror', e => log(`  pageerror: ${e.message.slice(0, 200)}`));
  const seenKeys = new Set();
  page.on('console', async m => {
    const t = m.text();
    if (/two children with the same key/.test(t)) {
      const args = await Promise.all(m.args().map(a => a.jsonValue().catch(() => '?')));
      const key = String(args[1]);
      const stack = String(args[2] || '').split('\n').map(x => x.trim()).filter(Boolean).filter(x => !/^at (div|span|label|button|i|p|a)$/.test(x)).slice(0, 8).join(' < ');
      const id = key + '|' + stack;
      if (!seenKeys.has(id)) { seenKeys.add(id); log(`  DUPKEY at ${page.url().replace(BASE, '')}: key "${key}" in ${stack}`); }
    } else if (/homebrew|duplicate|rekey|re-key/i.test(t)) log(`  console.${m.type()}: ${t.slice(0, 220)}`);
  });

  log(`seeded library: ${LIBRARY.length} chars`);
  await myContent(page);
  await snapshot(page, 'after load');
  await page.reload({ waitUntil: 'networkidle' }); await settle(page);
  await snapshot(page, 'after reload 1');
  await page.reload({ waitUntil: 'networkidle' }); await settle(page); await expandAll(page);
  const base = await snapshot(page, 'after reload 2');

  const picks = [0, Math.floor(base.editButtons / 2), base.editButtons - 1];
  for (const n of picks) {
    await myContent(page);
    const row = await openEdit(page, n);
    log(`  viewed #${n}: ${row} -> ${page.url()}`);
    if (n === 0) {
      const where = await page.evaluate(() => {
        const out = [];
        for (const el of document.querySelectorAll('body *')) {
          const own = Array.from(el.childNodes).filter(x => x.nodeType === 3).map(x => x.textContent.trim()).join('');
          if (own !== 'Test Cantrip' && !(el.value === 'Test Cantrip')) continue;
          const chain = [];
          for (let e = el; e && e !== document.body && chain.length < 9; e = e.parentElement)
            chain.push(e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + (e.className && typeof e.className === 'string' ? '.' + e.className.trim().split(/\s+/).slice(0, 3).join('.') : ''));
          const p = el.parentElement;
          out.push(`${chain.join(' < ')}  [parent has ${p ? p.children.length : 0} children]`);
        }
        return out;
      });
      where.forEach(w => log(`  WHERE ${w}`));
    }
    if (row !== null) await page.goBack({ waitUntil: 'networkidle' }).catch(() => {});
    await settle(page);
  }
  await snapshot(page, 'after Edit -> back x3');

  for (const n of picks) {
    await myContent(page);
    const row = await openEdit(page, n);
    const save = page.locator('[title="Save to Browser Storage"], button:has-text("Save to Browser Storage")').first();
    const saved = await save.count() ? await save.click().then(() => true, () => false) : false;
    await settle(page);
    log(`  saved #${n} unchanged (${saved ? 'saved' : 'no save button found'}): ${row} -> ${page.url()}`);
    if (row !== null) await page.goBack({ waitUntil: 'networkidle' }).catch(() => {});
    await settle(page);
  }
  await snapshot(page, 'after Edit -> Save -> back x3');
  await page.reload({ waitUntil: 'networkidle' }); await settle(page); await expandAll(page);
  await snapshot(page, 'after final reload');

  await browser.close();
  log('\nPROBE DONE');
})().catch(e => { console.error('PROBE THREW', e); process.exit(1); });
