// The builders at PHONE width. Every picture in the gallery so far is 1200px wide, and nobody had
// looked at either the hand-written or the generated form on a phone.
//
// Reports, per builder, the two things that actually break on a narrow screen:
//   * horizontal overflow — content wider than the viewport, i.e. a sideways scroll
//   * controls narrower than 44px, the usual floor for a touch target
// plus the page height, since a phone is where vertical cost is felt most.
//
// Prereqs:  lein garden once && lein fig:build && lein e2e-server
// Run:      LABEL=generated node test/e2e/mobile-compare.js
//           ONLY=spell-builder LABEL=bespoke node test/e2e/mobile-compare.js
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, dismissCookieBar } = require('./lib');

// iPhone 12/13/14 CSS pixels — the narrow end of what people actually use.
const VIEWPORT = { width: 390, height: 844 };
const LABEL = process.env.LABEL || 'current';
const ONLY = (process.env.ONLY || '').split(',').filter(Boolean);
const BUILDERS = ONLY.length ? ONLY
  : ['language-builder', 'draconic-ancestry-builder', 'fighting-style-builder', 'spell-builder',
     'feat-builder', 'race-builder', 'monster-builder'];

(async () => {
  const dir = path.join(SHOTS, `mobile-${LABEL}`);
  fs.mkdirSync(dir, { recursive: true });
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await browser.newPage({ viewport: VIEWPORT, deviceScaleFactor: 2, isMobile: true,
                                       hasTouch: true });

  const rows = [];
  for (const seg of BUILDERS) {
    await page.goto(`${BASE}/pages/dnd/5e/${seg}`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1600);
    await dismissCookieBar(page);
    const m = await page.evaluate((vw) => {
      const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
      const app = document.querySelector('#app');
      // scope overflow to the FORM, not the page: the site header's banner art is deliberately
      // wider than the viewport and clipped, and counting it reported 32 overflowing elements on
      // every builder — a number that says nothing about the form.
      const form = document.querySelector('#app .p-20.main-text-color') || app;
      const controls = [...form.querySelectorAll('input, select, textarea, .chip, .select-menu-btn')].filter(vis);
      // anything sticking out past the viewport is a sideways scroll on a phone
      const over = [...form.querySelectorAll('*')].filter(e => {
        const r = e.getBoundingClientRect();
        return r.width > 0 && r.right > vw + 1;
      });
      const tiny = controls.filter(c => c.getBoundingClientRect().height < 44);
      return {
        height: app.scrollHeight,
        scrollW: Math.round(app.scrollWidth),
        overflow: over.length,
        overflowWorst: over.length ? Math.round(Math.max(...over.map(e => e.getBoundingClientRect().right)) - vw) : 0,
        controls: controls.length,
        under44: tiny.length,
      };
    }, VIEWPORT.width);
    await page.screenshot({ path: path.join(dir, `${seg}.jpg`), fullPage: true, type: 'jpeg', quality: 72 });
    rows.push({ seg, ...m });
    console.log(`${seg.padEnd(28)} h=${String(m.height).padStart(5)}px  scrollW=${String(m.scrollW).padStart(4)}` +
                `  overflowing=${String(m.overflow).padStart(3)}${m.overflow ? ` (worst +${m.overflowWorst}px)` : ''}` +
                `  controls=${String(m.controls).padStart(3)}  under-44px=${m.under44}`);
  }
  await browser.close();
  fs.writeFileSync(path.join(dir, 'index.json'), JSON.stringify({ label: LABEL, viewport: VIEWPORT, rows }, null, 2));
  console.log(`\n${rows.length} builders at ${VIEWPORT.width}px -> ${dir}`);
})().catch(e => { console.error(e); process.exit(2); });
