// The place census: every mapped place (test/browser/places/*.js `census`) is found, counted, visible
// and hittable in the state its descriptor declares. It diagnoses the shared map only; journey
// assertions in the suites stay the guard for behaviour. One line per place, and a failing place
// says which kind of failure it is, checked in this order: setup failed, missing, wrong count,
// hidden, covered. Plan: plan-browser-toolbox.md (agents/develop), step 6.
//
// `node test/browser/place_census_e2e.js --map` prints the map (each place, its route, its
// preconditions, its locator) without a browser or a server.
//
// Needs:     server (`./scripts/e2e/run.sh test/browser/place_census_e2e.js`). No login.
// Runs in:   ~15s. One browser, one context per viewport, one page load per route and state.
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const { SetupFailed, BASE, checker, quietOverlays, phoneContext } = require('./lib/toolbox');

const PLACES_DIR = path.join(__dirname, 'places');

/**
 * Every census descriptor of every places module, named and in file order.
 * @returns {{name: string, module: string, route: string, locator: Function,
 *            preconditions: object, expectedCount: number}[]}
 */
function loadPlaces() {
  return fs.readdirSync(PLACES_DIR).filter(f => f.endsWith('.js')).sort().flatMap(f => {
    const census = require(path.join(PLACES_DIR, f)).census || {};
    return Object.entries(census).map(([name, d]) => ({ name, module: f, ...d }));
  });
}

// What a precondition's value means, and how the census sets it up. Values not listed here fail as
// "setup failed" naming the missing recipe; recipes for sign-in and fixtures arrive with step 7.
const VIEWPORTS = {
  desktop: browser => browser.newContext({ viewport: { width: 1920, height: 1080 } }),
  phone: browser => phoneContext(browser),
};
// An opener returns once its state has landed: re-frame renders a selection a frame after the click.
const OPENERS = {
  'builder.details': async page => {
    const details = require('./places/builder').tab(page, 'Details');
    await details.click({ timeout: 10000 });
    await page.waitForFunction(el => el.getAttribute('aria-selected') === 'true', await details.elementHandle(),
                               { timeout: 10000 });
    await page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))));
  },
};

/**
 * Why `d`'s preconditions cannot be set up here, or null when they can.
 * @param {object} d a descriptor
 * @returns {string|null}
 */
function unsupported(d) {
  const p = d.preconditions || {};
  if (!VIEWPORTS[p.viewport || 'desktop']) return `no viewport "${p.viewport}"`;
  if (p.auth && p.auth !== 'signed-out') return `no recipe for auth "${p.auth}" yet`;
  if (p.fixture) return `no recipe for fixture "${p.fixture}" yet`;
  if (p.open && !OPENERS[p.open]) return `no recipe to open "${p.open}"`;
  return null;
}

/**
 * How `d` is located, as the chain of calls its locator function makes, read without a browser: the
 * function runs against a stand-in page that records each call (getByRole(...), locator(...),
 * first()) instead of finding anything.
 * @param {object} d a descriptor
 * @returns {string}
 */
function how(d) {
  const TEXT = Symbol('text');
  const show = v => (v && v[TEXT] !== undefined ? v[TEXT]
    : v instanceof RegExp ? String(v) : typeof v === 'object' && v ? `{ ${Object.entries(v).map(([k, x]) => `${k}: ${show(x)}`).join(', ')} }`
    : JSON.stringify(v));
  const recorder = text => new Proxy(() => {}, {
    get: (_, key) => (key === TEXT ? text : (...args) => recorder(`${text}${text ? '.' : ''}${String(key)}(${args.map(show).join(', ')})`)),
  });
  try { return show(d.locator(recorder(''))); } catch (e) { return `(locator threw: ${e.message})`; }
}

/**
 * Waits until the app has replaced the server's spinner in #app, as the boot check does.
 * @param {import('playwright').Page} page
 */
async function appReady(page) {
  await page.waitForFunction(() => {
    const app = document.getElementById('app');
    return app && app.childElementCount > 0 && !app.querySelector('img[src*="spiral.gif"], [data-spinner]');
  }, null, { timeout: 30000 });
}

/**
 * Judges one place on a page already in its state: the first failing kind, or null when it passes.
 * Covered means the element's centre hit test returns neither it nor something inside it.
 * @param {import('playwright').Locator} loc
 * @param {number} expected
 * @returns {Promise<string|null>}
 */
async function judge(loc, expected) {
  const n = await loc.count();
  if (n === 0) return `missing: 0 matches (expected ${expected})`;
  if (n !== expected) return `wrong count: ${n} matches (expected ${expected})`;
  let target = null;
  for (let i = 0; i < n; i++) {
    const el = loc.nth(i);
    const box = await el.boundingBox();
    if (await el.isVisible() && box && box.width > 0 && box.height > 0) { target = el; break; }
  }
  if (!target) return 'hidden: matched but not visible';
  await target.scrollIntoViewIfNeeded({ timeout: 2000 }).catch(() => {});
  const cover = await target.evaluate(el => {
    const b = el.getBoundingClientRect();
    const hit = document.elementFromPoint(b.left + b.width / 2, b.top + b.height / 2);
    if (!hit || hit === el || el.contains(hit)) return null;
    const cls = typeof hit.className === 'string' && hit.className.trim()
      ? '.' + hit.className.trim().split(/\s+/).join('.') : '';
    return hit.tagName.toLowerCase() + (hit.id ? '#' + hit.id : '') + cls;
  });
  return cover ? `covered: centre hit by ${cover}` : null;
}

(async () => {
  const places = loadPlaces();
  if (process.argv.includes('--map')) {
    for (const d of places) {
      console.log(`${d.name}\n  ${how(d)}\n  route ${d.route}  preconditions ${JSON.stringify(d.preconditions || {})}`
        + `  expected ${d.expectedCount}  (places/${d.module})`);
    }
    console.log(`\n${places.length} places`);
    return;
  }
  const { check, watch, finish } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  // Group by viewport, then by route and opener, so each page state is loaded once.
  const byViewport = new Map();
  for (const d of places) {
    const why = unsupported(d);
    if (why) { check(d.name, false, `setup failed: ${why}`); continue; }
    const p = d.preconditions || {};
    const vp = p.viewport || 'desktop';
    const key = `${d.route} ${p.open || ''}`;
    if (!byViewport.has(vp)) byViewport.set(vp, new Map());
    const groups = byViewport.get(vp);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(d);
  }
  for (const [vp, groups] of byViewport) {
    const ctx = await VIEWPORTS[vp](browser);
    await quietOverlays(ctx);
    for (const group of groups.values()) {
      const { route, preconditions: { open } = {} } = group[0];
      const page = await ctx.newPage();
      watch(page, `${route} ${vp}${open ? ' ' + open : ''}`);
      let setup = null;
      try {
        await page.goto(`${BASE}${route}`, { waitUntil: 'domcontentloaded' });
        await appReady(page);
        await page.waitForLoadState('networkidle', { timeout: 15000 });
        if (open) await OPENERS[open](page);
      } catch (e) { setup = String(e.message).split('\n')[0]; }
      for (const d of group) {
        const fault = setup ? `setup failed: ${setup}` : await judge(d.locator(page), d.expectedCount);
        check(d.name, !fault, fault ? `${fault} on ${route} (${vp}); ${how(d)}` : undefined);
      }
      await page.close();
    }
    await ctx.close();
  }
  await browser.close();
  process.exit(finish());
})().catch(e => {
  if (e instanceof SetupFailed) { console.log('STOP  ' + e.message); process.exit(1); }
  console.error(e); process.exit(2);
});
