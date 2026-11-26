// "New" in a homebrew builder keeps the Option Source, as data and on screen. The box used to keep
// its own copy of the source: after New it still showed the last one while the new draft had none,
// and Save then asked for a source the box seemed to have. Owner's decision, 2026-09-18: the box
// shows the item's own source, and New carries it forward as real data.
//
// For each of the 13 builders with the box: type a source, press New, and check the box and the
// draft agree. In four, also name the new item and save it without touching the box. One builder
// is then given a different source after New, to check the box still edits.
//
//   DATOMIC_URL=datomic:mem://orcpub-e2e ORCPUB_ENV=dev SIGNATURE=e2e-test-signature PORT=8890 \
//   CSP_POLICY=none lein with-profile init-db run -m e2e-boot
// Prereqs:  lein fig:build && lein garden once
//   NODE_PATH=/opt/node22/lib/node_modules node test/e2e/new-keeps-source.js     Exit 0 = pass.
const { chromium } = require('playwright');
const { BASE, findChrome, checker, readyPage } = require('./lib');

const SRC = 'Keep Pack';
const wait = ms => new Promise(r => setTimeout(r, ms));
// [route segment, the New button's title, the draft's localStorage key, save a named item too?]
const BUILDERS = [
  ['spell', 'Spell', 'spell'], ['monster', 'Monster', 'monster'],
  ['encounter', 'Encounter', 'encounter'], ['background', 'Background', 'background', true],
  ['language', 'Language', 'language', true], ['invocation', 'Eldritch Invocation', 'invocation'],
  ['boon', 'Pact Boon', 'boon'], ['selection', 'Selection', 'selection'],
  ['feat', 'Feat', 'feat', true], ['race', 'Race', 'race', true], ['subrace', 'Subrace', 'subrace'],
  ['subclass', 'Subclass', 'subclass'], ['class', 'Class', 'class'],
];
const box = page => page.locator('#app input[placeholder="Default Option Source"]').first();
const draftSource = (page, key) => page.evaluate(k => {
  const m = (localStorage.getItem(k) || '').match(/:option-pack "([^"]*)"/);
  return m ? m[1] : null;
}, key);

(async () => {
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  try {
    const page = await (await browser.newContext({ viewport: { width: 1400, height: 1000 } })).newPage();
    for (const [seg, title, key, save] of BUILDERS) {
      await page.goto(`${BASE}/pages/dnd/5e/${seg}-builder`, { waitUntil: 'load' });
      await wait(2000);
      await readyPage(page);
      await box(page).fill(SRC);
      await wait(400);
      await page.locator('#app button:visible', { hasText: new RegExp(`^New ${title}$`, 'i') }).first().click();
      await wait(1000);
      const shown = await box(page).inputValue();
      const held = await draftSource(page, key);
      check(`${title}: after New, the box and the draft both say "${SRC}"`,
            shown === SRC && held === SRC, `box "${shown}", draft ${JSON.stringify(held)}`);
      if (save) {
        const name = `Kept ${title}`;
        await page.locator('#app input.input.h-40').nth(0).fill(name);
        await wait(300);
        await page.locator('#app button:visible', { hasText: 'Save to Browser Storage' }).first().click();
        await wait(1500);
        const lib = await page.evaluate(() => localStorage.getItem('plugins') || '');
        check(`${title}: the new item saves under "${SRC}" without touching the box`,
              new RegExp(`"${SRC}" \\{[\\s\\S]*:name "${name}"`).test(lib));
      }
    }
    // the box still edits after New
    await page.goto(`${BASE}/pages/dnd/5e/feat-builder`, { waitUntil: 'load' });
    await wait(2000);
    await page.locator('#app button:visible', { hasText: /^New Feat$/i }).first().click();
    await wait(800);
    await box(page).fill('Other Pack');
    await wait(400);
    check('after New, typing a different source changes the draft',
          (await draftSource(page, 'feat')) === 'Other Pack' && (await box(page).inputValue()) === 'Other Pack');
  } catch (e) {
    check('ran to completion', false, e.message.split('\n')[0]);
  } finally {
    await browser.close();
    process.exit(report() ? 1 : 0);
  }
})().catch(e => { console.error(e); process.exit(2); });
