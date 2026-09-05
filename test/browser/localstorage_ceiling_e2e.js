// Measure the REAL localStorage ceiling instead of repeating the "5 MB" folklore.
//
// WHY: the chunked-storage plan first proposed migrating by writing v2 alongside the legacy
// blob and deleting legacy afterwards. That doubles peak usage. Whether it is safe depends
// on a number nobody had measured, and on whether the quota counts characters or UTF-8
// bytes — the library is measured in characters (see storage_shape_e2e.js).
//
// Result in Chromium:
//   5,177,344 chars, IDENTICAL for an ASCII fill and a CJK fill.
//   Identical means the quota counts UTF-16 code units, not UTF-8 bytes, so a library's
//   character count compares directly against it.
//   navigator.storage.estimate().quota reported 916,414,672 — that is IndexedDB/
//   CacheStorage origin quota, NOT localStorage. ~180x more room, which is the capacity
//   argument for IndexedDB.
//
// Consequence: copy-then-delete migration works below ~2.58 M chars and fails above it.
// Real users are already past that, so the plan moves one source at a time and shrinks the
// legacy blob as it goes. See docs/kb/plan-chunked-library-storage.md.
//
// Run: lein e2e-server, then  node test/browser/localstorage_ceiling_e2e.js
const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || undefined });
  const page = await browser.newPage();
  await page.goto('http://localhost:8890/dnd/5e/my-content',
                  { waitUntil: 'domcontentloaded', timeout: 120000 });

  const r = await page.evaluate(async () => {
    const out = {};
    // Fill with 64k chunks until the quota throws. Two alphabets: if the ceilings differ,
    // the quota counts encoded bytes; if they match, it counts UTF-16 units.
    const fill = (mkChunk) => {
      localStorage.clear();
      let chars = 0;
      const CH = 65536;
      try {
        for (let i = 0; i < 2000; i++) { localStorage.setItem('k' + i, mkChunk(CH)); chars += CH; }
      } catch (e) { out.err = e.name; }
      return chars;
    };
    out.asciiChars = fill(n => 'a'.repeat(n));
    out.cjkChars   = fill(n => '中'.repeat(n));
    localStorage.clear();
    if (navigator.storage && navigator.storage.estimate) {
      const e = await navigator.storage.estimate();
      out.estimateQuota = e.quota;   // NOT localStorage — IndexedDB/CacheStorage
    }
    return out;
  });

  console.log(JSON.stringify(r, null, 2));
  console.log('counts UTF-16 units (not bytes):', r.asciiChars === r.cjkChars);
  console.log('ceiling:', r.asciiChars, 'chars');
  console.log('largest library migratable by copy-then-delete:', Math.floor(r.asciiChars / 2), 'chars');
  await browser.close();
})().catch(e => { console.error('FAILED', e); process.exit(1); });
