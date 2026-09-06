// Screenshot every homebrew builder page in the real app.
//
// Its job is comparison: run it, convert a builder, run it again into a different label, and the
// two directories are a before/after gallery. That is the only honest way to picture a conversion
// — both halves are the real UI, not a mock, and nothing has to be kept alive in parallel to pose
// for the photo.
//
// Prereqs:  lein fig:build && lein garden once && lein e2e-server   (port 8890)
// Run:      LABEL=before node test/e2e/builder-gallery.js
//           LABEL=after  node test/e2e/builder-gallery.js
//           ONLY=background,fighting-style node test/e2e/builder-gallery.js   (subset)
// Output:   target/e2e-shots/gallery-<label>/<segment>.png
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, dismissCookieBar } = require('./lib');

// Every :route-seg in content_types.cljc that has a builder page.
const BUILDERS = [
  'language-builder', 'boon-builder', 'invocation-builder', 'draconic-ancestry-builder',
  'fighting-style-builder', 'background-builder', 'feat-builder', 'selection-builder',
  'spell-builder', 'encounter-builder', 'monster-builder', 'race-builder',
  'subrace-builder', 'subclass-builder', 'class-builder',
];

const LABEL = process.env.LABEL || 'current';

// Full-page JPEG. These are documentation assets that get committed, and a full-page PNG of a
// dark UI is ~800KB; the same frame as JPEG is a tenth of that and loses nothing that matters
// for reading a form layout.
const shot = (page, file) => page.screenshot({ path: file, fullPage: true, type: 'jpeg', quality: 72 });

const ONLY = (process.env.ONLY || '').split(',').filter(Boolean);

(async () => {
  const dir = path.join(SHOTS, `gallery-${LABEL}`);
  fs.mkdirSync(dir, { recursive: true });
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await browser.newPage({ viewport: { width: 1200, height: 1000 } });
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));

  const rows = [];
  for (const seg of BUILDERS) {
    if (ONLY.length && !ONLY.some(o => seg.includes(o))) continue;
    const before = errors.length;
    await page.goto(`${BASE}/pages/dnd/5e/${seg}`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await dismissCookieBar(page);
    // How many controls the form puts in front of an author, which is the number the comparison
    // is actually about.
    const controls = await page.evaluate(() =>
      [...document.querySelectorAll('#app input, #app select, #app textarea')]
        .filter(e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; }).length);
    // HEIGHT, not just control count. A conversion that stacks every field into one page-wide
    // column shows the same controls and reads far worse — the generated spell form ran 100px
    // TALLER than the hand-written one while showing one control FEWER, and a count-only gallery
    // reported that as an improvement. Height is what catches lost cohesion.
    const height = await page.evaluate(() => document.querySelector('#app').scrollHeight);
    await shot(page, path.join(dir, `${seg}.jpg`));
    const broke = errors.length > before;
    rows.push({ seg, controls, height, broke });
    console.log(`${broke ? 'ERR ' : '    '}${seg.padEnd(28)} ${String(controls).padStart(3)} controls  ${String(height).padStart(5)}px`);
  }
  await browser.close();

  fs.writeFileSync(path.join(dir, 'index.json'), JSON.stringify({ label: LABEL, rows }, null, 2));
  console.log(`\n${rows.length} builders -> ${dir}`);
  process.exit(rows.some(r => r.broke) ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
