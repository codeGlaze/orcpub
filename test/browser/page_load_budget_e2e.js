// Does each page load only what it shows? Every request a page makes while it loads, before
// anyone touches it, must be in that page's allowance (ALLOW below), each entry with its reason.
// Lazy loads happen on use (opening the Orcacle, hovering a rule) and are not part of a page's load.
//
// PAGE_LOAD_AUDIT=1 records and prints every request instead of judging: the audit that the
// allowances are written from. AUDIT_OUT=<file> also writes the audit as JSON.
//
// Needs:     server (`lein e2e-server`); a seeded character (E2E_CHARACTER_ID, set by run.sh).
// Runs in:   ~60s.
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const { login } = require('../../scripts/e2e/lib.js');
const fs = require('fs');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const AUDIT = process.env.PAGE_LOAD_AUDIT === '1';
const CHAR = process.env.E2E_CHARACTER_ID || '';
// Compressed size of the production app bundle: 820,778 bytes on 2026-10-15, plus 5%.
const BUNDLE_GZIP_BUDGET = 861817;

const PAGES = [
  ['home', '/'],
  ['character builder', '/pages/dnd/5e/character-builder'],
  ['character list', '/pages/dnd/5e/characters'],
  ['character sheet', `/pages/dnd/5e/characters/${CHAR}`],
  ['parties', '/pages/dnd/5e/parties'],
  ['spells', '/pages/dnd/5e/spells'],
  ['magic items', '/pages/dnd/5e/magic-items'],
  ['monsters', '/pages/dnd/5e/monsters'],
  ['rules', '/pages/dnd/5e/rules'],
  ['conditions', '/pages/dnd/5e/conditions'],
  ['orcacle', '/pages/dnd/5e/orcacle'],
  ['encounter builder', '/pages/dnd/5e/encounter-builder'],
  ['combat tracker', '/pages/dnd/5e/combat-tracker'],
  ['spell builder', '/pages/dnd/5e/spell-builder'],
  ['class builder', '/pages/dnd/5e/class-builder'],
  ['my content', '/dnd/5e/my-content'],
  ['my account', '/pages/my-account'],
  ['login', '/pages/login-page'],
];

// Every page: the app shell. [pattern over "kind METHOD path", reason].
const SHELL = [
  ['^script GET /js/compiled/orcpub\\.js$', 'the app'],
  ['^script GET /js/cookies\\.js$', 'the cookie notice'],
  ['^stylesheet GET /css/(compiled/styles|cookiestyles)\\.css$', 'the site and cookie-notice styles'],
  ['^(stylesheet|font) GET /assets/font-awesome/', 'Font Awesome, the interface icons'],
  ['^font GET /fonts/open-sans/open-sans-[a-z-]+\\.woff2$', 'Open Sans, served from this site'],
  ['^image GET /image/[a-z0-9-]+\\.(svg|gif|jpg|png)$', "the page's own icons and artwork"],
  ['^other GET /favicon/', 'the site icon'],
  ['^xhr GET /user$', 'the session check, signed in only'],
];

// Per page, beyond the shell: the data that page shows. Written from the audit (2026-10-15).
const ALLOW = {
  'character builder': [['^xhr GET /dnd/5e/items$', 'your items, offered as equipment']],
  'character list': [['^xhr GET /dnd/5e/(character-summaries|folders)$', 'your characters and folders']],
  'character sheet': [['^xhr GET /dnd/5e/characters/:id$', 'the character shown'],
                      ['^xhr GET /dnd/5e/items$', 'your items, which the sheet can show']],
  'parties': [['^xhr GET /dnd/5e/parties$', 'your parties']],
  'magic items': [['^xhr GET /dnd/5e/items$', 'your items, listed with the SRD ones']],
  'class builder': [['^xhr GET /dnd/5e/items$', 'your items, offered as starting equipment']],
  'rules': [['^fetch GET /srd/2014/rules\\.edn$', 'the rules shown']],
  'conditions': [['^fetch GET /srd/2014/conditions\\.edn$', 'the conditions shown']],
  'combat tracker': [['^xhr GET /dnd/5e/(character-summaries|parties)$',
                      'your characters and parties, listed by its always-visible add dropdowns']],
};

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

// The requests `url` makes from navigation until the network has been quiet, as
// {kind, method, path} with ids and query strings folded so pages compare across runs.
async function load(ctx, url) {
  const page = await ctx.newPage();
  const seen = [];
  page.on('request', r => {
    const u = new URL(r.url());
    if (r.isNavigationRequest() && r.frame() === page.mainFrame() && u.pathname === new URL(url, BASE).pathname) return;
    const same = u.origin === new URL(BASE).origin;
    const path = (same ? '' : u.origin) + u.pathname.replace(/\/\d{6,}(?=\/|$)/g, '/:id');
    seen.push({ kind: r.resourceType(), method: r.method(), path });
  });
  const response = await page.goto(BASE + url, { waitUntil: 'networkidle', timeout: 60000 });
  await page.waitForTimeout(1000);
  await page.close();
  const key = s => `${s.kind} ${s.method} ${s.path}`;
  const counts = {};
  for (const s of seen) counts[key(s)] = (counts[key(s)] || 0) + 1;
  // An error page loads little, so it would pass the allowance; its status is checked first.
  return { counts, status: response ? response.status() : 0 };
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const audit = {};
  for (const [who, signIn] of [['signed out', false], ['signed in', true]]) {
    const ctx = await browser.newContext();
    if (signIn) {
      const p = await ctx.newPage();
      await login(p, 'kaylee', 'serenity99');
      // A failed login would test the signed-out pages twice under the signed-in name.
      check('signed in for the signed-in pages',
            /:token/.test(await p.evaluate(() => localStorage.getItem('user') || '')));
      await p.close();
    }
    for (const [name, url] of PAGES) {
      const { counts, status } = await load(ctx, url);
      const label = `${name} (${who})`;
      check(`${label} loads`, status >= 200 && status < 300, `HTTP ${status}`);
      audit[label] = counts;
      if (AUDIT) {
        console.log(`\n== ${label}  ${url}`);
        for (const [k, n] of Object.entries(counts).sort()) console.log(`   ${n > 1 ? n + 'x ' : '   '}${k}`);
        check(`audited ${label}`, true);
      } else {
        // Each allowance is [pattern, reason, most times per load (1 unless given)].
        const allowed = [...SHELL, ...(ALLOW[name] || [])].map(([p, , most]) => [new RegExp(p), most || 1]);
        const extra = Object.keys(counts).filter(k => !allowed.some(([re]) => re.test(k)));
        const repeated = Object.entries(counts)
          .filter(([k, n]) => { const rule = allowed.find(([re]) => re.test(k)); return rule && n > rule[1]; })
          .map(([k, n]) => `${n}x ${k}`);
        check(`${label} makes no allowed request more often than allowed`, repeated.length === 0, repeated.join(' | '));
        check(`${label} loads only its allowance`, extra.length === 0, extra.join(' | '));
      }
    }
    await ctx.close();
  }
  // Lazy loads: the rules and conditions load when the Orcacle opens, once, never before.
  {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    const srd = {};
    page.on('request', r => {
      const m = new URL(r.url()).pathname.match(/^\/srd\/2014\/([a-z]+)\.edn$/);
      if (m) srd[m[1]] = (srd[m[1]] || 0) + 1;
    });
    await page.goto(BASE + '/pages/dnd/5e/spells', { waitUntil: 'networkidle' });
    check('the spells page loads no rules or conditions', Object.keys(srd).length === 0, JSON.stringify(srd));
    // The header's magnifying glass; the nested div.pointer.flex around it would match first.
    const opener = page.locator('[style*="magnifying-glass"], [src*="magnifying-glass"]').first();
    // Wait for the requests themselves: waitForLoadState('networkidle') returns at once on a page
    // that has already been idle, before the click's fetch starts.
    const loaded = Promise.all([/\/srd\/2014\/rules\.edn$/, /\/srd\/2014\/conditions\.edn$/]
      .map(re => page.waitForResponse(r => re.test(new URL(r.url()).pathname), { timeout: 15000 })));
    await opener.click({ timeout: 10000 });
    const replies = await loaded.catch(() => []);
    check('the rules and conditions both load successfully',
          replies.length === 2 && replies.every(r => r.ok()), replies.map(r => r.status()).join(' ') || 'no reply');
    check('opening the Orcacle loads the rules and conditions once each',
          srd.rules === 1 && srd.conditions === 1, JSON.stringify(srd));
    // Forced: the open Orcacle may cover the button. Proving nothing more loads needs a window;
    // the first load's request started within 60 ms of its click, so one second is ample.
    await opener.click({ force: true, timeout: 10000 });
    await page.waitForTimeout(1000);
    check('opening it again loads nothing more', srd.rules === 1 && srd.conditions === 1, JSON.stringify(srd));
    await ctx.close();
  }

  // The app bundle every first visit downloads: grows only by decision. Raise BUNDLE_GZIP_BUDGET
  // in the same change that needs it, saying why.
  {
    const bundle = fs.readFileSync(require('path').join(__dirname, '../../resources/public/js/compiled/orcpub.js'));
    const gz = require('zlib').gzipSync(bundle).length;
    check(`the app bundle stays within its budget (${(BUNDLE_GZIP_BUDGET / 1024).toFixed(0)} KB compressed)`,
          gz <= BUNDLE_GZIP_BUDGET, `${(gz / 1024).toFixed(0)} KB`);
  }

  // Every static file must say how long to keep it. A missing header is not caught by watching a
  // repeat visit: browsers guess a lifetime from Last-Modified, so the visit passes anyway.
  {
    const ctx = await browser.newContext();
    const first = await ctx.newPage();
    const uncached = [];
    first.on('response', r => {
      if (!['script', 'stylesheet', 'font', 'image'].includes(r.request().resourceType())) return;
      if (new URL(r.url()).origin !== new URL(BASE).origin) return;
      if (!/max-age=\d+/.test(r.headers()['cache-control'] || '')) uncached.push(new URL(r.url()).pathname);
    });
    await first.goto(BASE + '/pages/dnd/5e/character-builder', { waitUntil: 'networkidle' });
    check('every static file the builder loads says how long to keep it',
          uncached.length === 0, `${uncached.length} without: ${uncached.slice(0, 4).join(', ')}`);
    await ctx.close();
  }

  // A self-hosted font that fails to load falls back silently; check the browser really has it.
  {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await page.goto(BASE + '/pages/dnd/5e/spells', { waitUntil: 'networkidle' });
    const weights = await page.evaluate(async () => {
      await document.fonts.ready;
      return Promise.all(['400', '600', '700'].map(async w => {
        const faces = await document.fonts.load(`${w} 16px "Open Sans"`);
        return faces.length > 0 && faces.every(f => f.status === 'loaded');
      }));
    });
    check('Open Sans loads from this site at 400, 600 and 700', weights.every(Boolean), weights.join(' '));
    await ctx.close();
  }
  if (process.env.AUDIT_OUT) fs.writeFileSync(process.env.AUDIT_OUT, JSON.stringify(audit, null, 1));
  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
