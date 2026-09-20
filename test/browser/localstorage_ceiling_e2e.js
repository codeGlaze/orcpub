// The real localStorage ceiling, not the "5 MB" folklore.
//
// Fills in 64k chunks until the quota throws, with two alphabets: matching ceilings mean
// the quota counts UTF-16 units (so a library's char count compares directly against it),
// differing ones mean encoded bytes.
//
// Chromium: 5,177,344 chars, identical for ASCII and CJK. navigator.storage.estimate()
// reported 916,414,672 -- that is IndexedDB/CacheStorage, not localStorage.
//
// Consequence: a copy-then-delete storage migration fits only below ~2.58 M chars, which
// real libraries already exceed.
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
