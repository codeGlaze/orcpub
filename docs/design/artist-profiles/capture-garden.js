// Captures the artist pages in both themes, desktop and phone, for comparing the page
// before and after its stylesheet moved into Garden. Needs the app at :8890.
// Run: NODE_EXTRA_CA_CERTS=<bundle> \
//        NODE_OPTIONS=--require=../../../test/browser/lib/suppress-overlays-preload.js \
//        node capture-garden.js <before|after>
// The preload keeps the cookie notice and What's New panel out of the shots.
const { chromium, devices } = require('playwright');
const path = require('path');

const BASE = 'http://localhost:8890';
const tag = process.argv[2] || 'shot';
const OUT = path.join(__dirname, 'garden');
const pages = [['profile', '/artists/fusspot'], ['index', '/artists']];
const sizes = [['desktop', { viewport: { width: 1280, height: 900 } }],
               ['phone', devices['iPhone 13']]];

(async () => {
  const browser = await chromium.launch();
  for (const theme of ['dark', 'light']) {
    for (const [size, device] of sizes) {
      const ctx = await browser.newContext({ ...device, reducedMotion: 'reduce' });
      // Google Fonts through Node, which trusts the sandbox's proxy CA; the browser does not.
      await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
      await ctx.addInitScript(t => {
        try { localStorage.setItem('user', `{:theme "${t}"}`); } catch (_) {}
      }, theme === 'light' ? 'light-theme' : 'dark-theme');
      const page = await ctx.newPage();
      for (const [name, url] of pages) {
        await page.goto(BASE + url, { waitUntil: 'networkidle' });
        await page.waitForSelector('.ap-card');
        await page.evaluate(() => document.fonts.ready);
        await page.waitForTimeout(500);
        // A full-page shot clipped to the page body: an element shot scrolls, and the
        // sticky header then lands on top of the card.
        const box = await page.evaluate(() => {
          const r = document.querySelector('.ap-root').getBoundingClientRect();
          return { x: r.left + scrollX, y: r.top + scrollY, width: r.width, height: r.height };
        });
        await page.screenshot({ path: path.join(OUT, `${tag}-${name}-${theme}-${size}.png`),
                                fullPage: true, clip: box });
      }
      await ctx.close();
    }
  }
  await browser.close();
})();
