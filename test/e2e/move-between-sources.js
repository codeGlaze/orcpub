// Retyping Option Source Name MOVES the item. It used to copy it, leaving one key answering in two
// sources — which then made both copies uneditable.
//
// Drives the real path: author in A, reopen from My Content's edit button, retype the source, save.
//
// Prereqs:  lein fig:build && lein garden once && lein e2e-server
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, checker, dbAt, fill, clickText,
        dismissCookieBar, dismissWhatsNew } = require('./lib');

const A = 'Move From Pak';
const B = 'Move To Pak';
const ct = ':orcpub.dnd.e5/languages';

const openFromMyContent = async (page, name) => {
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
  return page.evaluate(nm => {
    const rows = [...document.querySelectorAll('#app div')].filter(e =>
      new RegExp(nm).test(e.textContent || '') &&
      [...e.querySelectorAll('button')].some(b => b.textContent.trim() === 'edit'));
    const innermost = rows[rows.length - 1];
    if (!innermost) return false;
    [...innermost.querySelectorAll('button')].find(b => b.textContent.trim() === 'edit').click();
    return true;
  }, name);
};

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await browser.newPage({ viewport: { width: 1200, height: 1000 } });
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));

  try {
    await page.goto(`${BASE}/pages/dnd/5e/language-builder`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1600);
    await dismissCookieBar(page);
    await dismissWhatsNew(page);

    await fill(page, 'Name', 'Tideward');
    await fill(page, 'Option Source Name', A);
    check('authored it in the first source', await clickText(page, /save to browser storage/i));
    await page.waitForTimeout(1000);
    check('it is in that source', /tideward/i.test(await dbAt(page, `[:plugins "${A}" ${ct}]`)));

    check('reopened it from My Content', await openFromMyContent(page, 'Tideward'));
    await page.waitForTimeout(1600);
    check('and the form has it', await page.evaluate(() =>
      [...document.querySelectorAll('#app input')].some(i => i.value === 'Tideward')));

    // A refresh between opening the item and moving it: the address the builder fetched it from
    // is persisted beside the draft, so the save still knows this is a move rather than a guess.
    await page.reload({ waitUntil: 'networkidle' });
    await page.waitForTimeout(1600);
    await dismissWhatsNew(page);
    check('the form survived a reload', await page.evaluate(() =>
      [...document.querySelectorAll('#app input')].some(i => i.value === 'Tideward')));

    await fill(page, 'Option Source Name', B);
    check('retyped the source and saved', await clickText(page, /save to browser storage/i));
    await page.waitForTimeout(1200);

    const from = await dbAt(page, `[:plugins "${A}" ${ct}]`);
    const to = await dbAt(page, `[:plugins "${B}" ${ct}]`);
    check('it ARRIVED in the new source', /tideward/i.test(to), to.slice(0, 200));
    check('and LEFT the old one — a move, not a copy', !/tideward/i.test(from), `old source now: ${from}`);

    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
    await page.waitForTimeout(1600);
    await dismissWhatsNew(page);
    const listing = await page.locator('#app').innerText();
    check('My Content lists the source it moved TO', listing.includes(B));
    // the source it left is empty now, and My Content hides a source with no content — the same
    // as deleting its last item
    check('and no longer lists the one it left', !listing.includes(A),
          listing.replace(/\n+/g, ' | ').slice(0, 300));

    check('no uncaught JS errors', errors.length === 0, errors.slice(0, 2).join(' | '));
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
