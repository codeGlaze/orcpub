// Can you BROWSE the combobox, or only search it?
//
// Characterization + A/B. Opens the largest inventory section, then asks:
//   - how many rows actually render
//   - is the list scrollable, and does scrolling reach the last item
//   - what does opening cost (longest task, 4x CPU throttle)
//   - does the keyboard walk the list
//
// Run: node test/browser/combobox_scroll_e2e.js /path/to/pack.orcbrew
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const { importPack, suppressCookieBanner } = require('./lib/orcbrew-import');

function findChrome() {
  if (process.env.CHROME_PATH) return process.env.CHROME_PATH;
  const b = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  const d = fs.readdirSync(b).filter(x => x.startsWith('chromium-') && !x.includes('headless')).sort().pop();
  return path.join(b, d, 'chrome-linux', 'chrome');
}

const OBSERVE = `
  window.__long = [];
  new PerformanceObserver(l => { for (const e of l.getEntries()) window.__long.push(Math.round(e.duration)); })
    .observe({ entryTypes: ['longtask'] });
`;

(async () => {
  const PACK = process.argv[2];
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ viewport: { width: 1500, height: 1000 } });
  await suppressCookieBanner(ctx);
  const page = await ctx.newPage();
  await page.goto('http://localhost:8890/dnd/5e/my-content', { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(2500);
  if (PACK) console.log('import:', JSON.stringify(await importPack(page, PACK)));
  await page.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'load', timeout: 900000 });
  await page.waitForTimeout(14000);
  await page.locator('text="Equipment"').first().click({ timeout: 30000 });
  await page.waitForTimeout(3000);

  // Biggest section is the interesting one -- a 39-item list hides the problem.
  const idx = await page.evaluate(() => {
    const ins = [...document.querySelectorAll('input.inv-combo-input')];
    const n = i => { const m = (i.placeholder || '').match(/\((\d+)\)/); return m ? +m[1] : 0; };
    let best = 0; ins.forEach((i, k) => { if (n(i) > n(ins[best])) best = k; });
    return { i: best, count: n(ins[best]) };
  });
  console.log(`largest section: index ${idx.i}, ${idx.count} items`);

  // 4x throttle models a laptop also running the server.
  const cdp = await ctx.newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
  await page.evaluate(OBSERVE);

  const input = page.locator('input.inv-combo-input').nth(idx.i);
  await input.scrollIntoViewIfNeeded();
  await page.waitForTimeout(400);
  await page.evaluate(() => { window.__long = []; });
  await input.click();
  await page.waitForTimeout(1200);

  const r = await page.evaluate(() => {
    const pop = document.querySelector('.inv-combo-pop:popover-open');
    if (!pop) return { open: false };
    const list = pop.querySelector('.inv-combo-list');
    const rows = pop.querySelectorAll('.inv-combo-row');
    const more = pop.querySelector('.inv-combo-more');
    return {
      open: true,
      rows: rows.length,
      first: rows[0] && rows[0].textContent.trim(),
      last: rows[rows.length - 1] && rows[rows.length - 1].textContent.trim(),
      scrollH: list ? list.scrollHeight : 0,
      clientH: list ? list.clientHeight : 0,
      moreText: more ? more.textContent.trim() : null,
      longest: Math.max(0, ...window.__long),
    };
  });
  if (!r.open) { console.log('popover did not open'); await browser.close(); return; }

  console.log(`rows rendered      ${r.rows} of ${idx.count}`);
  console.log(`list scrollable    ${r.scrollH > r.clientH} (scrollHeight ${r.scrollH} vs clientHeight ${r.clientH})`);
  console.log(`reachable by scroll ${r.rows} of ${idx.count}  -> ${r.rows < idx.count ? 'CANNOT browse the rest without typing' : 'full list browsable'}`);
  console.log(`first / last row   "${r.first}" .. "${r.last}"`);
  console.log(`footer             ${r.moreText === null ? '(none)' : '"' + r.moreText + '"'}`);
  console.log(`longest task open  ${r.longest} ms (4x throttle)`);

  // Scroll to the bottom of the list and see what the last visible row is.
  const bottom = await page.evaluate(() => {
    const list = document.querySelector('.inv-combo-pop:popover-open .inv-combo-list');
    if (!list) return null;
    list.scrollTop = list.scrollHeight;
    const rows = list.querySelectorAll('.inv-combo-row');
    return rows[rows.length - 1] && rows[rows.length - 1].textContent.trim();
  });
  console.log(`after scrolling to bottom, last row is "${bottom}"`);
  await page.screenshot({ path: 'dev-scratch/combo/9-scrolled-bottom.png' });

  // Keyboard: does ArrowDown walk the list?
  await page.keyboard.press('ArrowDown');
  await page.waitForTimeout(300);
  const kb = await page.evaluate(() => ({
    active: document.activeElement && document.activeElement.className,
    highlighted: document.querySelectorAll('.inv-combo-row.active, .inv-combo-row[aria-selected="true"]').length,
  }));
  console.log(`ArrowDown -> focus "${kb.active}", highlighted rows ${kb.highlighted}` +
              (kb.highlighted ? '' : '  -> NO keyboard navigation'));

  // Arrow well past the visible window: the highlight must be scrolled into view, not
  // left somewhere above or below the list's clipping box.
  for (let i = 0; i < 20; i++) await page.keyboard.press('ArrowDown');
  await page.waitForTimeout(500);
  const vis = await page.evaluate(() => {
    const list = document.querySelector('.inv-combo-pop:popover-open .inv-combo-list');
    const act = list && list.querySelector('.inv-combo-row.active');
    if (!act) return null;
    const l = list.getBoundingClientRect(), a = act.getBoundingClientRect();
    return { name: act.textContent.trim(), inView: a.top >= l.top - 1 && a.bottom <= l.bottom + 1,
             scrollTop: Math.round(list.scrollTop) };
  });
  console.log(vis
    ? `after 21x ArrowDown: "${vis.name}", scrollTop=${vis.scrollTop}, highlight in view: ${vis.inView}`
    : 'after 21x ArrowDown: no active row');

  await page.screenshot({ path: 'dev-scratch/combo/8-browse-keyboard.png' });

  // Enter must pick the highlighted row. A picked item leaves the available list, so the
  // section's own count is the assertion -- a page-wide :checked count was not.
  const count = i => page.evaluate(k => {
    const el = document.querySelectorAll('input.inv-combo-input')[k];
    const m = (el.placeholder || '').match(/\((\d+)\)/);
    return m ? +m[1] : -1;
  }, i);
  const target = vis && vis.name;
  const before = await count(idx.i);
  await page.keyboard.press('Enter');
  await page.waitForTimeout(1500);
  const after = await count(idx.i);
  const open = await page.evaluate(() => document.querySelectorAll('.inv-combo-pop:popover-open').length);
  console.log(`Enter on "${target}" -> section count ${before} -> ${after} ` +
              `(${after === before - 1 ? 'ADDED' : 'NOT added'}), popover open ${open}`);

  await browser.close();
})().catch(e => { console.error('FAILED', e); process.exit(1); });
