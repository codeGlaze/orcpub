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
    // input/select/textarea AND the glyph checkboxes. The app draws a toggle as an <i> with colour
    // classes, not an <input>, so a count of form elements missed every checkbox — the three spell
    // components vanished from the page and the count still read 10.
    const controls = await page.evaluate(() => {
      const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
      const fields = [...document.querySelectorAll('#app input, #app select, #app textarea, #app .select-menu-btn')].filter(vis);
      // toggles are drawn two ways: a glyph checkbox (the hand-written builders) and a chip (the
      // generated ones). Count both, or the metric reports a representation change as controls
      // disappearing — it did, 24 -> 11.
      const toggles = [...document.querySelectorAll('#app i.fa-check, #app .chip')].filter(vis);
      return fields.length + toggles.length;
    });
    // HEIGHT, not just control count. A conversion that stacks every field into one page-wide
    // column shows the same controls and reads far worse — the generated spell form ran 100px
    // TALLER than the hand-written one while showing one control FEWER, and a count-only gallery
    // reported that as an improvement. Height is what catches lost cohesion.
    // LABELS, not just controls. A control count is written against one rendering and goes blind
    // the moment that rendering changes: checkbox -> chip and <select> -> popover each made a
    // counter miss controls entirely, and twice the number did not move while controls actually
    // DISAPPEARED. A field's label survives its control changing shape, so this is the measure that
    // catches a vanished field. See docs/kb/before-you-start.md.
    const labels = await page.evaluate(() => {
      const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
      return [...document.querySelectorAll('#app .p-20 .f-w-b, #app .p-20 .opt-section-title')]
        .filter(e => vis(e) && e.children.length === 0 && e.textContent.trim()).length;
    });
    const height = await page.evaluate(() => document.querySelector('#app').scrollHeight);
    await shot(page, path.join(dir, `${seg}.jpg`));
    const broke = errors.length > before;
    rows.push({ seg, controls, labels, height, broke });
    console.log(`${broke ? 'ERR ' : '    '}${seg.padEnd(28)} ${String(controls).padStart(3)} controls  ` +
                `${String(labels).padStart(3)} labels  ${String(height).padStart(5)}px`);
  }
  await browser.close();

  fs.writeFileSync(path.join(dir, 'index.json'), JSON.stringify({ label: LABEL, rows }, null, 2));

  // Diff against the committed baseline. A field that disappears shows up here as a label drop even
  // when the control count cannot see it — which is the case this exists for.
  const basePath = path.join(__dirname, 'builder-baseline.json');
  if (fs.existsSync(basePath)) {
    const base = JSON.parse(fs.readFileSync(basePath, 'utf8'));
    const byseg = Object.fromEntries(base.rows.map(r => [r.seg, r]));
    let drift = 0;
    for (const r of rows) {
      const b = byseg[r.seg];
      if (!b) { console.log(`NEW      ${r.seg}`); continue; }
      if (b.labels !== r.labels || b.controls !== r.controls) {
        drift++;
        console.log(`DRIFT    ${r.seg.padEnd(26)} labels ${b.labels}->${r.labels}   controls ${b.controls}->${r.controls}`);
      }
    }
    console.log(drift ? `\n${drift} builder(s) drifted from the baseline. If deliberate, re-record it:\n` +
                        `  cp ${path.join(dir, 'index.json')} test/e2e/builder-baseline.json`
                      : '\nno drift from the baseline');
  }
  console.log(`\n${rows.length} builders -> ${dir}`);
  process.exit(rows.some(r => r.broke) ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
