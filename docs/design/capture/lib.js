// What every design capture shares: a browser, and a view of one app page that is themed, has
// its fonts and no overlays, and can be restyled, measured and shot. Rounds (rounds.js) use only
// this. Shots go under OUT (default docs/design/capture/out/<round>), which git ignores.
require('../../../test/browser/lib/suppress-overlays-preload.js');
const { chromium, devices } = require('playwright');
const { findChrome } = require('../../../test/browser/lib/find-chrome.js');
const { login } = require('../../../scripts/e2e/lib.js');
const fs = require('fs');
const path = require('path');

const BASE = process.env.ORCPUB_BASE || 'http://localhost:8890';

// A view of one page. Restyle swaps the round's variant CSS; clips are page rectangles for shoot.
class View {
  constructor(ctx, page, out) { this.ctx = ctx; this.page = page; this.out = out; }

  async go(url) {
    await this.page.goto(BASE + url, { waitUntil: 'networkidle' });
    await this.page.evaluate(() => document.fonts.ready);
    await this.page.waitForTimeout(300);
  }

  async addStyle(css) { await this.page.addStyleTag({ content: css }); }

  async restyle(css) {
    await this.page.evaluate(c => {
      document.querySelectorAll('style[data-variant]').forEach(e => e.remove());
      const e = document.createElement('style'); e.dataset.variant = ''; e.textContent = c || '';
      document.head.appendChild(e);
    }, css);
    await this.page.waitForTimeout(120);
  }

  // Load extra Open Sans weights from Google Fonts (the site ships 400 only).
  async addFontWeights(weights) {
    const href = `https://fonts.googleapis.com/css2?family=Open+Sans:wght@${weights.join(';')}&display=block`;
    await this.page.evaluate(h => new Promise((ok, no) => {
      const l = document.createElement('link'); l.rel = 'stylesheet'; l.href = h; l.onload = ok; l.onerror = no;
      document.head.appendChild(l);
    }), href);
    await this.page.evaluate(ws => Promise.all(ws.map(w => document.fonts.load(`${w} 14px "Open Sans"`))), weights);
  }

  // The rectangle around `target` (a selector, a locator or a box), grown by `pad`.
  async clipAround(target, pad = 0) {
    const box = typeof target === 'string' ? await this.page.locator(target).first().boundingBox()
      : target.boundingBox ? await target.boundingBox() : target;
    return { x: Math.max(0, box.x - pad), y: Math.max(0, box.y - pad), width: box.width + 2 * pad, height: box.height + 2 * pad };
  }

  // The rectangle from the first `selector` match to its `last`-th (0-based), grown by `pad`.
  async clipSpan(selector, last, pad = 8) {
    const a = await this.page.locator(selector).first().boundingBox();
    const b = await this.page.locator(selector).nth(last).boundingBox();
    return { x: a.x - pad, y: a.y - pad, width: b.x + b.width - a.x + 2 * pad, height: a.height + 2 * pad };
  }

  // The rectangle of the parent of the first `selector` match: the header's tab row, say.
  async clipParent(selector, pad = 0) {
    const r = await this.page.locator(selector).first().evaluate(el => {
      const q = el.parentElement.getBoundingClientRect(); return { x: q.x, y: q.y, width: q.width, height: q.height };
    });
    return this.clipAround(r, pad);
  }

  // The rectangle of the first `selector` match in page coordinates, for a shot taller than the
  // window (an element shot scrolls, and the sticky header then lands on it).
  async clipPage(selector) {
    const r = await this.page.evaluate(s => {
      const q = document.querySelector(s).getBoundingClientRect();
      return { x: q.left + scrollX, y: q.top + scrollY, width: q.width, height: q.height };
    }, selector);
    return { ...r, page: true };
  }

  // Save a shot as <name>.png: of a clip, of an element (selector or locator), or of the viewport.
  async shoot(name, what) {
    const file = path.join(this.out, `${name}.png`);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    if (typeof what === 'string') await this.page.locator(what).first().screenshot({ path: file });
    else if (what && what.screenshot) await what.screenshot({ path: file });
    else if (what) {
      const { page, ...clip } = what;
      await this.page.screenshot({ path: file, clip, fullPage: !!page });
    } else await this.page.screenshot({ path: file });
    return file;
  }

  // One line on the first `selector` match: box, background, position, stacking and display.
  async describe(selector) {
    return this.page.evaluate(sel => {
      const e = document.querySelector(sel); if (!e) return `${sel}: missing`;
      const s = getComputedStyle(e), r = e.getBoundingClientRect();
      return `${sel}: ${Math.round(r.left)},${Math.round(r.top)} ${Math.round(r.width)}x${Math.round(r.height)} ` +
        `bg ${s.backgroundColor} pos ${s.position} z ${s.zIndex} display ${s.display}`;
    }, selector);
  }

  // Replace the content of the first `selector` match with `html`, under class `className`.
  async replace(selector, className, html) {
    await this.page.evaluate(([s, c, h]) => {
      const el = document.querySelector(s); el.className = c; el.innerHTML = h;
    }, [selector, className, html]);
    await this.page.waitForTimeout(120);
  }

  // The builder's portrait drawer open on a random portrait, so its artist credit shows.
  async openCredit() {
    await this.go('/pages/dnd/5e/character-builder');
    const desc = this.page.locator('.builder-tab', { hasText: /^Description$/ }).first();
    if (await desc.count()) { await desc.click(); await this.page.waitForTimeout(300); }
    await this.page.locator('.pl-launcher').click();
    await this.page.locator('.pl-drawer').waitFor({ state: 'visible' });
    await this.page.locator('.pl-btn-primary', { hasText: 'Randomize' }).click();
    await this.page.mouse.move(5, 5);
    await this.page.waitForTimeout(500);
  }

  async close() { await this.ctx.close(); }
}

// A browser for one round, writing under `out`. open() gives a View of `url`; options:
// theme ('dark' | 'light'), darkText, device (a Playwright device name), viewport, dpr, login.
class Capture {
  static async launch(out) {
    const c = new Capture();
    c.browser = await chromium.launch({ executablePath: findChrome() });
    c.out = out;
    return c;
  }

  async open(url, { theme = 'dark', darkText, device, viewport, dpr = 2, login: asUser = false } = {}) {
    const ctx = await this.browser.newContext({
      ...(device ? devices[device] : {}), ...(viewport ? { viewport } : {}),
      ...(device || viewport ? {} : { viewport: { width: 1280, height: 900 } }), deviceScaleFactor: dpr });
    const user = `{:theme "${theme}-theme"${darkText === undefined ? '' : ` :dark-button-text? ${darkText}`}}`;
    // Only when unset, so a login's token survives the next page load.
    await ctx.addInitScript(u => { try { if (!localStorage.getItem('user')) localStorage.setItem('user', u); } catch (_) {} }, user);
    // Google Fonts through Node, which trusts the sandbox proxy's CA; the browser does not.
    await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
    const view = new View(ctx, await ctx.newPage(), this.out);
    if (asUser) await login(view.page, 'kaylee', 'serenity99'); // seeded by dev/e2e_boot.clj
    if (url) await view.go(url);
    return view;
  }

  async close() { await this.browser.close(); }
}

// A data: URI for `<name>.svg` in the candidate-icon folder ICONS (kept outside the repo).
function iconUri(name) {
  if (!process.env.ICONS) throw new Error('set ICONS to the folder of candidate icon SVGs');
  return 'data:image/svg+xml;base64,' + fs.readFileSync(path.join(process.env.ICONS, name + '.svg')).toString('base64');
}

module.exports = { Capture, View, iconUri, BASE };
