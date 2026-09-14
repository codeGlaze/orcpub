// A source's own key tag, set from My Content.
//
// The derivation is a guess and nobody can correct it for an author's own source, so the source
// row carries an optional tag. Asserts: the derived value is the PLACEHOLDER (not stored), a typed
// tag normalizes and mints the next key, and keys already minted do not move.
//
// Prereqs:  lein fig:build && lein garden once && lein e2e-server
const os = require('os'), fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, checker, dbAt, fill, clickText,
        dismissCookieBar, dismissWhatsNew } = require('./lib');

const SOURCE = 'Tidewater Curios';            // derives TrCs
const ct = ':orcpub.dnd.e5/languages';

const authorLanguage = async (page, name) => {
  await page.goto(`${BASE}/pages/dnd/5e/language-builder`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1200);
  await dismissWhatsNew(page);
  await clickText(page, /^new language$/i).catch(() => {});
  await page.waitForTimeout(400);
  // GOTCHA: plugin-datalist holds the source name in a component-local atom that NEW does not
  // reset, so the field still SHOWS the previous source while the item has none — and filling it
  // with the same string fires no change. Reload so the component mounts against the cleared item.
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);
  await dismissWhatsNew(page);
  await fill(page, 'Name', name);
  await fill(page, 'Option Source Name', SOURCE);
  await clickText(page, /save to browser storage/i);
  await page.waitForTimeout(900);
};

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await browser.newPage({ viewport: { width: 1200, height: 1000 } });
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));

  try {
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
    await page.waitForTimeout(1500);
    await dismissCookieBar(page);
    await dismissWhatsNew(page);

    await authorLanguage(page, 'Tideward');
    check('the first key is minted from the DERIVED tag',
          /:tideward-trcs/.test(await dbAt(page, `[:plugins "${SOURCE}" ${ct}]`)),
          (await dbAt(page, `[:plugins "${SOURCE}" ${ct}]`)).slice(0, 160));

    // the source row
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
    await page.waitForTimeout(1800);
    await dismissWhatsNew(page);
    const row = page.locator('#app .item-list-item').filter({ hasText: SOURCE }).first();
    if (await row.locator('text=expand').count()) {
      await row.locator('text=expand').first().click();
      await page.waitForTimeout(800);
    }
    const rowText = await page.locator('.bf-meta').first().innerText();
    check('the source row shows the tag its keys get, at rest', /key tag\s+TrCs/.test(rowText),
          JSON.stringify(rowText));
    check('and says nothing else — the explanation is behind the ?', /\?/.test(rowText),
          JSON.stringify(rowText));
    check('nothing is stored until an author sets one',
          !/abbreviation/.test(await dbAt(page, `[:plugins "${SOURCE}"]`)));

    await page.evaluate(() => {
      const e = [...document.querySelectorAll('.bf-meta span')].find(x => x.textContent.trim() === '?');
      e.click();
    });
    await page.waitForTimeout(300);
    check('the ? opens the explanation beneath',
          /Keys already minted keep the tag they have/.test(await page.locator('#app').innerText()));
    await page.screenshot({ path: path.join(SHOTS, 'source-key-tag-help.png'),
                            clip: { x: 0, y: Math.max(0, (await page.locator('.bf-meta').first().boundingBox()).y - 130),
                                    width: 1200, height: 260 } });

    await page.evaluate(() => {
      const e = [...document.querySelectorAll('.bf-meta span')].find(x => x.textContent.trim() === 'change');
      e.click();
    });
    await page.waitForTimeout(300);
    const field = page.locator('.bf-meta input').first();
    check('change opens an input seeded with the current tag',
          (await field.inputValue()) === 'TrCs', await field.inputValue());
    await field.fill('twc');
    await page.evaluate(() => {
      const e = [...document.querySelectorAll('.bf-meta span')].find(x => x.textContent.trim() === 'save');
      e.click();
    });
    await page.waitForTimeout(700);
    check('a typed tag is normalized on the way in',
          /:abbreviation "TWC"/.test(await dbAt(page, `[:plugins "${SOURCE}"]`)),
          (await dbAt(page, `[:plugins "${SOURCE}"]`)).slice(0, 120));
    await page.screenshot({ path: path.join(SHOTS, 'source-key-tag.png'),
                            clip: { x: 0, y: Math.max(0, (await page.locator('.bf-meta').first().boundingBox()).y - 130),
                                    width: 1200, height: 240 } });

    await authorLanguage(page, 'Tidewall');
    const stored = await dbAt(page, `[:plugins "${SOURCE}" ${ct}]`);
    check('the NEXT key is minted with the author tag', /:tidewall-twc/.test(stored), stored.slice(0, 220));
    check('and the key already minted does not move', /:tideward-trcs/.test(stored));

    check('no uncaught JS errors', errors.length === 0, errors.slice(0, 2).join(' | '));
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
