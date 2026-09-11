// Proof that a typo'd condition tag is SURFACED, not silently swallowed.
//
// weapons/matches? and requirements/meets-all? both IGNORE a tag they do not recognise — deliberate
// forward-compat, so a pack from a newer build still applies what this build understands. The cost
// is that it fails OPEN: `:armour?` (British spelling) is not a tag, so the bonus applies with NO
// condition at all rather than erroring. Nothing at runtime can tell a typo from a future tag, so
// the guard has to be at authoring time.
//
// Drives the real app: import a pack carrying the typo, export it, and assert the warning reaches
// BOTH surfaces — the console detail and the on-screen toast.
//
// Prereqs:  lein fig:build && lein e2e-server
// Run:      node test/e2e/unknown-tag-warning.js
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, checker, dbAt, clickText, dismissCookieBar } = require('./lib');

const PACK = 'Typo Pack';

// Two feats: one correct, one with :armour? where :armor? was meant. Both are otherwise valid, so
// nothing else can be what trips the warning.
const ORCBREW = `{"${PACK}"
 {:orcpub.dnd.e5/feats
  {:sound-feat  {:name "Sound Feat"  :key :sound-feat  :option-pack "${PACK}"
                 :props {:ac-bonus {:bonus 2 :armor? true}}}
   :typo-feat   {:name "Typo Feat"   :key :typo-feat   :option-pack "${PACK}"
                 :props {:ac-bonus {:bonus 2 :armour? true}}}}}}`;

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const { check, report } = checker();
  const file = path.join(os.tmpdir(), 'typo-pack.orcbrew');
  fs.writeFileSync(file, ORCBREW);

  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ acceptDownloads: true, viewport: { width: 1300, height: 1000 } });
  const page = await ctx.newPage();
  const consoleWarnings = [];
  page.on('console', m => { if (m.type() === 'warning') consoleWarnings.push(m.text()); });

  try {
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
    await page.waitForTimeout(1500);
    await dismissCookieBar(page);

    const input = await page.$('input[type=file]');
    check('found the import input', !!input);
    await input.setInputFiles(file);
    await page.waitForTimeout(2500);

    const stored = await dbAt(page, `[:plugins "${PACK}"]`);
    check('the pack imported', /typo-feat/.test(stored), stored.slice(0, 160));
    check('and the typo is still in the data — import does not silently repair it',
          /:armour\?/.test(stored));

    // Export through the real button
    await page.locator('#app .item-list-item').filter({ hasText: PACK }).first()
              .locator('text=expand').first().click();
    await page.waitForTimeout(400);
    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: 20000 }),
      page.locator('#app button:visible').filter({ hasText: /^export$/i }).first().click(),
    ]);
    await download.saveAs(path.join(os.tmpdir(), download.suggestedFilename()));
    await page.waitForTimeout(1200);

    // ── the two surfaces ──────────────────────────────────────────────────────
    const detail = consoleWarnings.find(t => /unrecognised tag/i.test(t)) || '';
    check('the console carries the detail', !!detail, detail.slice(0, 220));
    check('  naming the offending tag', /:armour\?/.test(detail));
    check('  and the item it is on',    /typo-feat/.test(detail));
    check('  and saying what goes wrong — it applies with NO condition',
          /IGNORED|no\s+condition/i.test(detail));
    check('  while the CORRECT feat is not flagged', !/sound-feat/.test(detail), detail.slice(0, 220));

    const body = await page.locator('#app').innerText();
    check('and the screen says so too (not console-only)',
          /exported with warnings/i.test(body), body.slice(0, 200));

    await page.screenshot({ path: path.join(SHOTS, 'unknown-tag-warning.jpg'),
                            fullPage: true, type: 'jpeg', quality: 75 });
    console.log('\n--- console warning, verbatim ---\n' + detail + '\n');
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
