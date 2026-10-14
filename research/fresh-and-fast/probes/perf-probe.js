// Kind: probe
// Perceived-speed baseline: load metrics per route, cold vs warm cache, desktop vs throttled phone,
// plus interaction latency on the builder. Prints JSON lines; writes perf-results.json.
const { chromium } = require('playwright');
const fs = require('fs');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const OUT = process.env.PERF_OUT || 'perf-results.json';
const RUNS = +(process.env.PERF_RUNS || 3);

const ROUTES = [
  ['home', '/'],
  ['builder', '/pages/dnd/5e/character-builder'],
  ['spells', '/pages/dnd/5e/spells'],
];
// Lighthouse's mobile preset: 4x CPU slowdown, 150ms RTT, ~1.6Mbps down.
const PROFILES = {
  desktop: { cpu: 1, net: null, viewport: { width: 1366, height: 900 } },
  phone: { cpu: 4, net: { latency: 150, downloadThroughput: 1.6e6 / 8, uploadThroughput: 750e3 / 8 },
           viewport: { width: 390, height: 844 } },
};

const OBSERVE = () => {
  window.__perf = { lcp: 0, cls: 0, long: [], events: [] };
  new PerformanceObserver(l => l.getEntries().forEach(e => { window.__perf.lcp = e.startTime; }))
    .observe({ type: 'largest-contentful-paint', buffered: true });
  new PerformanceObserver(l => l.getEntries().forEach(e => { if (!e.hadRecentInput) window.__perf.cls += e.value; }))
    .observe({ type: 'layout-shift', buffered: true });
  new PerformanceObserver(l => l.getEntries().forEach(e => window.__perf.long.push([e.startTime, e.duration])))
    .observe({ type: 'longtask', buffered: true });
  new PerformanceObserver(l => l.getEntries().forEach(e =>
    window.__perf.events.push({ name: e.name, dur: e.duration, start: e.startTime,
      input: e.processingStart - e.startTime, proc: e.processingEnd - e.processingStart })))
    .observe({ type: 'event', buffered: true, durationThreshold: 16 });
  // The moment the app replaced the server markup: the boot-rescue node is removed once it has rendered.
  const t0 = performance.now();
  const mo = new MutationObserver(() => {
    const app = document.getElementById('app');
    if (!window.__perf.mounted && app && !app.querySelector('.boot-spinner, .spinner, [class*=spinner]') &&
        app.children.length && document.readyState !== 'loading') {
      window.__perf.mounted = performance.now();
    }
  });
  document.addEventListener('DOMContentLoaded', () => mo.observe(document.getElementById('app') || document.body,
    { childList: true, subtree: true }));
};

async function profileCtx(browser, prof) {
  const ctx = await browser.newContext({ viewport: prof.viewport });
  await ctx.addInitScript(() => {
    try {
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', JSON.stringify('summer-patch-2026'));
    } catch (e) {}
  });
  await ctx.addInitScript(OBSERVE);
  return ctx;
}

async function throttle(page, prof) {
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: prof.cpu });
  if (prof.net) await cdp.send('Network.emulateNetworkConditions', { offline: false, ...prof.net });
  return cdp;
}

async function measure(page, url) {
  const t = Date.now();
  await page.goto(BASE + url, { waitUntil: 'load', timeout: 120000 });
  await page.waitForTimeout(2500); // let LCP / long tasks after load settle
  const m = await page.evaluate(() => {
    const nav = performance.getEntriesByType('navigation')[0];
    const paint = Object.fromEntries(performance.getEntriesByType('paint').map(p => [p.name, p.startTime]));
    const res = performance.getEntriesByType('resource');
    const by = {};
    for (const r of res) {
      const k = r.initiatorType + (r.name.includes('fonts.g') ? ':gfonts' : '');
      by[k] = by[k] || { n: 0, xfer: 0, body: 0 };
      by[k].n++; by[k].xfer += r.transferSize; by[k].body += r.decodedBodySize;
    }
    const long = window.__perf.long;
    return {
      ttfb: nav.responseStart, dcl: nav.domContentLoadedEventEnd, load: nav.loadEventEnd,
      fcp: paint['first-contentful-paint'], lcp: window.__perf.lcp, cls: +window.__perf.cls.toFixed(3),
      mounted: window.__perf.mounted || null,
      longTasks: long.length, longMax: Math.round(Math.max(0, ...long.map(l => l[1]))),
      tbt: Math.round(long.reduce((s, l) => s + Math.max(0, l[1] - 50), 0)),
      xferKB: Math.round((nav.transferSize + res.reduce((s, r) => s + r.transferSize, 0)) / 1024),
      resources: by,
    };
  });
  m.wall = Date.now() - t;
  for (const k of ['ttfb', 'dcl', 'load', 'fcp', 'lcp', 'mounted']) if (m[k] != null) m[k] = Math.round(m[k]);
  return m;
}

async function headers(browser) {
  const ctx = await browser.newContext(); const page = await ctx.newPage();
  const seen = {};
  page.on('response', r => {
    const u = r.url();
    if (/orcpub\.js|styles\.css|all\.min\.css|woff2?|cookies\.js|fonts\.googleapis/.test(u) || u === BASE + '/')
      seen[u.replace(BASE, '')] = { status: r.status(), cache: r.headers()['cache-control'] || null,
        etag: !!r.headers()['etag'], enc: r.headers()['content-encoding'] || null };
  });
  await page.goto(BASE + '/pages/dnd/5e/character-builder', { waitUntil: 'load' });
  await ctx.close();
  return seen;
}

// Interaction latency: click through the builder's tabs and record Event Timing durations.
async function interactions(browser, profName) {
  const prof = PROFILES[profName];
  const ctx = await profileCtx(browser, prof); const page = await ctx.newPage();
  await throttle(page, { ...prof, net: null });
  await page.goto(BASE + '/pages/dnd/5e/character-builder', { waitUntil: 'load', timeout: 120000 });
  await page.waitForTimeout(3000);
  const tabs = await page.$$eval('.builder-tab',
    els => els.map((e, i) => [i, (e.textContent || e.getAttribute('title') || '').trim().slice(0, 20)]));
  const out = [];
  const loc = page.locator('.builder-tab');
  const n = Math.min(await loc.count(), 10);
  for (let i = 0; i < n; i++) {
    await page.evaluate(() => { window.__perf.events = []; });
    const t = Date.now();
    try { await loc.nth(i).click({ timeout: 5000 }); } catch (e) { continue; }
    await page.waitForTimeout(1200);
    const ev = await page.evaluate(() => window.__perf.events.filter(e => /click|pointer/.test(e.name)));
    out.push({ tab: tabs[i] && tabs[i][1], maxEventMs: Math.round(Math.max(0, ...ev.map(e => e.dur))),
      procMs: Math.round(Math.max(0, ...ev.map(e => e.proc))) });
  }
  // A real edit: pick a race on the Race tab, which rebuilds the character.
  await loc.nth(0).click();
  await page.waitForTimeout(1000);
  for (const race of ['Dwarf', 'Elf', 'Halfling']) {
    await page.evaluate(() => { window.__perf.events = []; });
    try { await page.getByText(race, { exact: true }).first().click({ timeout: 5000 }); } catch (e) { continue; }
    await page.waitForTimeout(1500);
    const ev = await page.evaluate(() => window.__perf.events.filter(e => /click|pointer/.test(e.name)));
    out.push({ pick: race, maxEventMs: Math.round(Math.max(0, ...ev.map(e => e.dur))),
      procMs: Math.round(Math.max(0, ...ev.map(e => e.proc))) });
  }
  await ctx.close();
  return { profile: profName, tabsFound: tabs.length, clicks: out };
}

const median = a => { const s = [...a].sort((x, y) => x - y); return s[Math.floor(s.length / 2)]; };

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.E2E_CHROMIUM || undefined });
  const results = { base: BASE, when: new Date().toISOString(), headers: await headers(browser), loads: [], interactions: [] };
  console.log(JSON.stringify({ headers: results.headers }));
  for (const [pname, prof] of Object.entries(PROFILES)) {
    for (const [rname, url] of ROUTES) {
      const cold = [], warm = [];
      for (let r = 0; r < RUNS; r++) {
        const ctx = await profileCtx(browser, prof); const page = await ctx.newPage();
        await throttle(page, prof);
        cold.push(await measure(page, url));
        warm.push(await measure(page, url)); // same context: HTTP cache primed
        await ctx.close();
      }
      for (const [kind, arr] of [['cold', cold], ['warm', warm]]) {
        const row = { profile: pname, route: rname, cache: kind };
        for (const k of ['ttfb', 'fcp', 'lcp', 'mounted', 'dcl', 'load', 'tbt', 'longMax', 'longTasks', 'xferKB', 'cls'])
          row[k] = median(arr.map(m => m[k] == null ? NaN : m[k]));
        row.resources = arr[0].resources;
        results.loads.push(row);
        const { resources, ...brief } = row;
        console.log(JSON.stringify(brief));
      }
    }
  }
  for (const p of ['desktop', 'phone']) {
    const r = await interactions(browser, p); results.interactions.push(r); console.log(JSON.stringify(r));
  }
  fs.writeFileSync(OUT, JSON.stringify(results, null, 2));
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
