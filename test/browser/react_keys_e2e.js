// Needs: dev bundle (the suite was written and seen failing against one).
// Does Manage Content, with a pack expanded, log React's "Every element in a seq should have a
// unique :key" warning? Its rows key each branch's props map; `^{:key k}` on the `if` around them
// is dropped (before-you-start.md). The check also asserts the rows rendered, so an empty page cannot pass.
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const { suppressOverlays } = require('./lib/orcbrew-import');

const BASE = process.env.E2E_BASE || process.env.ORCPUB_E2E_URL || 'http://localhost:8890';
const KEY_WARNING = /unique :key|unique "key"/;
const PLUGINS = '{"Keys Pack" {:orcpub.dnd.e5/spells {' +
  ':keys-a {:option-pack "Keys Pack" :name "Keys Spell A" :level 1} ' +
  ':keys-b {:option-pack "Keys Pack" :name "Keys Spell B" :level 1} ' +
  ':keys-c {:option-pack "Keys Pack" :name "Keys Spell C" :level 2}}}}';

const results = [];
// Records and prints one named check; detail is shown after the name.
const check = (name, ok, detail = '') => {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -- ' + detail : ''}`);
};

// Opens a fresh context with overlays suppressed and every console message collected into `msgs`.
async function newPage(browser, msgs, plugins) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await suppressOverlays(ctx);
  if (plugins) {
    await ctx.addInitScript(v => { try { localStorage.setItem('plugins', v); } catch (e) {} }, plugins);
  }
  const page = await ctx.newPage();
  page.on('console', m => msgs.push(m.text()));
  page.on('pageerror', e => msgs.push('PAGEERROR ' + e));
  return { ctx, page };
}

// Reports whether any collected message is a React key warning.
const keyWarnings = msgs => msgs.filter(t => KEY_WARNING.test(t));

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome(), args: ['--no-sandbox', '--disable-dev-shm-usage'] });

  // ---- Manage Content: expand a pack's section so its entry rows render ----
  {
    const msgs = [];
    const { ctx, page } = await newPage(browser, msgs, PLUGINS);
    await page.goto(BASE + '/dnd/5e/my-content', { waitUntil: 'load', timeout: 300000 });
    await page.waitForFunction(() => document.body.innerText.includes('Keys Pack'), null, { timeout: 120000 });
    // Open every collapsed section ("expand" link) until the entry names are on screen.
    for (let i = 0; i < 6; i++) {
      if (await page.getByText('Keys Spell A').count()) break;
      const link = page.locator('.underline', { hasText: /^expand$/ }).first();
      if (!(await link.count())) { await page.getByText('Keys Pack').first().click().catch(() => {}); }
      else await link.click().catch(() => {});
      await page.waitForTimeout(700);
    }
    await page.waitForTimeout(1000);
    const rows = await page.locator('.f-w-b', { hasText: /Keys Spell [ABC]/ }).count();
    check('Manage Content: pack entry rows rendered', rows >= 3, `${rows} rows`);
    const w = keyWarnings(msgs);
    check('Manage Content: no unique-key warning', w.length === 0, w[0] ? w[0].slice(0, 200) : '');
    await ctx.close();
  }

  await browser.close();
  const failed = results.filter(r => !r.ok).length;
  console.log(`E2E RESULT: ${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error('FAILED', e.message); process.exit(1); });
