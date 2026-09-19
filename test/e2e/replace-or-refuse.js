// A save that lands on a taken key: what the author is told, and what they can do about it.
//
// Two different problems, two different messages:
//   :occupied   — something is at THIS address in THIS source. Named, visible, and the author's
//                 to discard, so the banner offers "Replace it".
//   :elsewhere  — the key answers in ANOTHER source. Nothing here is in the way, so there is
//                 nothing to replace and saying yes would only make the duplicate. No offer.
//
// Also pins that replacing MOVES rather than copies, and that a refusal loses nothing.
//
// Prereqs:  lein fig:build && lein garden once && lein e2e-server
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, checker, dbAt, fill, clickText,
        dismissCookieBar, dismissWhatsNew } = require('./lib');

// Two sources whose abbreviations DERIVE the same tag ("Te"+"Pk" from each two-word name), so a
// name authored in both mints one key -- which is how a cross-source collision still happens now
// that every minted key carries its source's tag.
const A = 'Tide Pak';
const B = 'Tide Park';
const ct = ':orcpub.dnd.e5/languages';

// The banner itself, not the page: this is a check on what it SAYS, and a full page shot at this
// viewport is mostly hero art.
const shot = async (page, name) => {
  const p = path.join(SHOTS, name);
  const el = await page.$('#app .message');
  if (!el) { console.log('      shot: NO BANNER for ' + name); return; }
  await el.screenshot({ path: p });
  console.log('      shot: ' + p);
};

// The banner's actions are inline spans inside a full-width detail row, and the row carries the
// same text -- clicking by text alone lands on the row, which only dismisses. Click the span.
const clickBannerAction = (page, label) => page.evaluate(txt => {
  const el = [...document.querySelectorAll('#app .message span.pointer')]
    .find(e => e.textContent.trim() === txt);
  if (!el) return false;
  el.click();
  return true;
}, label);

const bannerText = page => page.evaluate(() => {
  const m = document.querySelector('#app .message');
  return m ? m.innerText.replace(/\s+/g, ' ').trim() : '';
});

// The real New: My Content's per-source "add", which is also what clears the builder's origin.
const addFrom = async (page, source) => {
  await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
  await page.waitForTimeout(1600);
  await dismissWhatsNew(page);
  for (let i = 0; i < 3; i++) {
    const n = await page.evaluate(() => {
      const b = [...document.querySelectorAll('#app button, #app span')]
        .filter(e => e.textContent.trim() === 'expand');
      b.forEach(x => x.click());
      return b.length;
    });
    await page.waitForTimeout(500);
    if (!n) break;
  }
  const ok = await page.evaluate(src => {
    const cards = [...document.querySelectorAll('#app div')].filter(e =>
      (e.textContent || '').includes(src) &&
      [...e.querySelectorAll('button')].some(b => /^add language$/.test(b.textContent.trim())));
    const innermost = cards[cards.length - 1];
    if (!innermost) return false;
    [...innermost.querySelectorAll('button')]
      .find(b => /^add language$/.test(b.textContent.trim())).click();
    return true;
  }, source);
  await page.waitForTimeout(1500);
  return ok;
};

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await browser.newPage({ viewport: { width: 1200, height: 1000 } });
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));

  try {
    // ---- author the first one -------------------------------------------------
    await page.goto(`${BASE}/pages/dnd/5e/language-builder`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1600);
    await dismissCookieBar(page);
    await dismissWhatsNew(page);

    await fill(page, 'Name', 'Tideward');
    await fill(page, 'Description', 'the first one');
    await fill(page, 'Option Source Name', A);
    check('authored "Tideward" in the first source', await clickText(page, /save to browser storage/i));
    await page.waitForTimeout(1200);
    const first = await dbAt(page, `[:plugins "${A}" ${ct}]`);
    check('it is stored there', /the first one/.test(first), first.slice(0, 160));

    // ---- :occupied -- the same source, the same key ---------------------------
    check('started a NEW language in that source', await addFrom(page, A));
    await fill(page, 'Name', 'Tideward');
    await fill(page, 'Description', 'the second one');
    await clickText(page, /save to browser storage/i);
    await page.waitForTimeout(1200);

    const occupied = await bannerText(page);
    check('the save was refused', /already has a language called/.test(occupied), occupied.slice(0, 200));
    check('the banner NAMES what is in the way', /"Tide Pak" already has a language called "Tideward"/.test(occupied));
    check('and offers the way through', /Replace it/.test(occupied));
    check('in one line plus the offer', occupied.replace(/\s*✕?\s*$/, '').length < 110,
          `${occupied.length} chars`);
    await shot(page, 'collision-occupied.png');

    const held = await dbAt(page, `[:plugins "${A}" ${ct}]`);
    check('nothing was written while it was refused', /the first one/.test(held) && !/the second one/.test(held),
          held.slice(0, 160));

    // ---- the author says yes ---------------------------------------------------
    check('clicked "Replace it"', await clickBannerAction(page, 'Replace it'));
    await page.waitForTimeout(1200);
    const replaced = await dbAt(page, `[:plugins "${A}" ${ct}]`);
    check('the new one took the address', /the second one/.test(replaced), replaced.slice(0, 200));
    check('and the old one is gone', !/the first one/.test(replaced));
    check('one entry, not two', (replaced.match(/:option-pack/g) || []).length === 1);
    await shot(page, 'collision-replaced.png');

    // ---- :elsewhere -- another source answers to the key ----------------------
    check('started another NEW language', await addFrom(page, A));
    await fill(page, 'Name', 'Tideward');
    await fill(page, 'Option Source Name', B);            // different source, SAME derived tag
    await clickText(page, /save to browser storage/i);
    await page.waitForTimeout(1200);

    const elsewhere = await bannerText(page);
    check('refused as a cross-source duplicate', /already uses the key/.test(elsewhere), elsewhere.slice(0, 220));
    check('it names the source holding the key', /"Tide Pak" already uses the key :tideward-tepk/.test(elsewhere));
    check('and says what to do about it', /Rename this one, or change its key/.test(elsewhere));
    check('and offers NO replace button — a yes here would only make the duplicate',
          !/Replace it/.test(elsewhere));
    check('two lines, not a lecture', elsewhere.replace(/\s*✕?\s*$/, '').length < 140,
          `${elsewhere.length} chars`);
    await shot(page, 'collision-elsewhere.png');

    const nothingInB = await dbAt(page, `[:plugins "${B}" ${ct}]`);
    check('nothing was written to the other source', /^nil$|^\{\}$/.test(nothingInB.trim()), nothingInB.slice(0, 120));

    check('no page errors', errors.length === 0, errors.join(' | ').slice(0, 300));
  } catch (e) {
    check('script ran to completion', false, String(e).slice(0, 300));
  }

  const failed = report();
  await browser.close();
  process.exit(failed ? 1 : 0);
})();
