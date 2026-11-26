// Needs: production bundle.
// Is the site footer laid out for every width? Below 1100px the Help, Support and Legal rows stack,
// labels lined up; from 1100px they sit on one line spread edge to edge, matching the legal band
// under them. No width scrolls the page sideways.
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const { suppressOverlays } = require('./lib/orcbrew-import');

const BASE = process.env.E2E_BASE || process.env.ORCPUB_E2E_URL || 'http://localhost:8890';
const results = [];
// Records and prints one named check; detail is shown after the name.
const check = (name, ok, detail = '') => {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -- ' + detail : ''}`);
};

// Measures the footer's groups, labels and band, and the page's sideways overflow.
const measure = page => page.evaluate(() => {
  const box = el => { const r = el.getBoundingClientRect(); return { l: r.left, r: r.right, t: r.top, b: r.bottom, w: r.width }; };
  const groups = [...document.querySelectorAll('.site-footer-group')];
  return {
    labels: groups.map(g => (g.querySelector('.site-footer-label') || {}).textContent),
    groups: groups.map(box),
    labelW: groups.map(g => box(g.querySelector('.site-footer-label')).w),
    // each label beside its first link: same line, link to the right of the label
    beside: groups.map(g => {
      const l = box(g.querySelector('.site-footer-label')), a = box(g.querySelector('a'));
      return Math.abs((l.t + l.b) / 2 - (a.t + a.b) / 2) <= 6 && a.l >= l.r;
    }),
    band: box(document.querySelector('.site-footer .legal-footer')),
    overflow: document.documentElement.scrollWidth - window.innerWidth,
  };
});

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  for (const w of [320, 412, 1000, 1280, 1920]) {
    const ctx = await browser.newContext({ viewport: { width: w, height: 900 } });
    await suppressOverlays(ctx);
    const page = await ctx.newPage();
    await page.goto(BASE + '/dnd/5e/my-content', { waitUntil: 'load', timeout: 300000 });
    await page.waitForSelector('.site-footer-group', { timeout: 120000 });
    await page.waitForTimeout(500);
    const m = await measure(page);
    check(`${w}px: no sideways scroll`, m.overflow <= 0, `${m.overflow}px over`);
    check(`${w}px: each label sits beside its links`, m.beside.every(Boolean), m.beside.join());
    if (w === 412) {
      // The Support row shows only when a Patreon or developer tip link is set (support-pills).
      const support = await page.evaluate(() => {
        const s = (window.__BRANDING__ || {})['social-links'] || {};
        return Boolean(s.patreon || s['dev-tip']);
      });
      const want = support ? 'Help,Support,Legal' : 'Help,Legal';
      check(`the rows are labelled ${want.replace(/,/g, ', ')}`, m.labels.join() === want, m.labels.join());
    }
    if (w < 1100) {
      const stacked = m.groups.every((g, i) => i === 0 || g.t >= m.groups[i - 1].b - 1);
      check(`${w}px: the rows stack`, stacked, JSON.stringify(m.groups.map(g => Math.round(g.t))));
      check(`${w}px: the labels share one width`, new Set(m.labelW.map(Math.round)).size === 1, m.labelW.join());
    } else {
      const level = m.groups.every(g => Math.abs(g.t - m.groups[0].t) <= 3);
      check(`${w}px: the groups sit on one line`, level, JSON.stringify(m.groups.map(g => Math.round(g.t))));
      const first = m.groups[0], last = m.groups[m.groups.length - 1];
      check(`${w}px: the line spans the band, edge to edge`,
            first.r < last.l && Math.abs(first.l - m.band.l) <= 2 && Math.abs(last.r - m.band.r) <= 2,
            `groups ${Math.round(first.l)}..${Math.round(last.r)}, band ${Math.round(m.band.l)}..${Math.round(m.band.r)}`);
    }
    await ctx.close();
  }
  await browser.close();
  const failed = results.filter(r => !r.ok).length;
  console.log(`E2E RESULT: ${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error('FAILED', e.message); process.exit(1); });
